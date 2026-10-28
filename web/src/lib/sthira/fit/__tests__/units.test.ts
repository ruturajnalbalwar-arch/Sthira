import { describe, expect, it } from 'vitest';
import type { Recording } from '../../../record/schema';
import { loadParams } from '../../params/loader';
import { readProtocolConstants } from '../../params/protocol';
import paramsJson from '../../params/params.json';
import { coveringFrames } from '../../replay';
import { makeFrame, TEST_PARAMS } from '../../__tests__/fixtures';
import { L1, L2, labelEvents, pilotFiles, repTrain, synthLabels, synthRecording } from '../../__tests__/syntheticPilot';
import { loadPilot, type Trial } from '../data';
import { fractionGrid, linearGrid, quantileGrid, thresholdGrid } from '../grid';
import { measureTrial, type TrialMeasure } from '../measure';
import { groupMetrics, metricsByClothing } from '../metrics';
import { compareLoss, lossOf, scoreTrial } from '../objective';
import { buildParamsFile, buildTuneReport, evaluateHeldOut, FIT_SCRIPT, tunedOnSplit } from '../output';
import { fitParams, leaveOneSubjectOut } from '../search';
import { buildSpace } from '../space';

const PROTOCOL = { chairStandDurationS: 30, calibrationSitS: 5, calibrationStandS: 5 };
const OPTIONS = { gridPoints: 7, maxRounds: 3 };
const REPS = repTrain(5, 1, 5, { 1: { peak: 0.5 }, 2: { armsUncrossed: true } });

function trialFor(participant: string, edit: (r: Recording) => Recording = (r) => r): Trial {
  const recording = synthRecording({ participant, reps: REPS, startedAt: `2026-10-23T1${participant.slice(-1)}:00:00.000Z` });
  const events = labelEvents(REPS);
  const data = loadPilot(pilotFiles(recording, [synthLabels(recording, events, L1), synthLabels(recording, events, L2)]), PROTOCOL);
  if (data.trials.length !== 1) throw new Error(data.exclusions.map((e) => e.reason).join('; '));
  return { ...data.trials[0], recording: edit(data.trials[0].recording) };
}

const okMeasure = (trial: Trial): TrialMeasure => {
  const result = measureTrial(trial, PROTOCOL);
  if (!result.ok) throw new Error(result.reason);
  return result.measure;
};

describe('grids', () => {
  it('builds quantile, threshold, linear and fraction grids', () => {
    expect(quantileGrid([], 3)).toEqual([]);
    expect(quantileGrid([4, 0, 2], 3)).toEqual([0, 2, 4]);
    expect(thresholdGrid([0, 0, 1, 1], 2)).toEqual([0, 0.5, 1]);
    expect(linearGrid(1, 2, 3)).toEqual([1, 1.5, 2]);
    expect(fractionGrid(4)).toEqual([0, 0.25, 0.5, 0.75]);
  });
});

describe('coveringFrames', () => {
  const frames = [1, 2, 3].map((t) => makeFrame(t, 300));
  it('starts at the first frame when none precedes the phase and ends at the last when none follows', () => {
    expect(coveringFrames(frames, 0, 9).map((f) => f.t)).toEqual([1, 2, 3]);
    expect(coveringFrames(frames, 1.5, 2.5).map((f) => f.t)).toEqual([1, 2, 3]);
    expect(coveringFrames([], 0, 1)).toEqual([]);
  });
});

describe('measureTrial', () => {
  it('measures the gates the engine applies', () => {
    const m = okMeasure(trialFor('P01'));
    expect(m.frameRate).toBeCloseTo(10, 0);
    expect(m.fullPeakMin).toBeGreaterThan(0.9);
    expect(m.attemptIntervals.every((s) => s > 4)).toBe(true);
  });

  it('fails on missing phases, short calibration and an inverted range', () => {
    const noTest = trialFor('P01', (r) => ({ ...r, phases: r.phases.filter((p) => p.phase !== 'test') }));
    expect(measureTrial(noTest, PROTOCOL)).toMatchObject({ ok: false, reason: 'no test phase' });
    const longer = { ...PROTOCOL, calibrationSitS: 60 };
    expect(measureTrial(trialFor('P01'), longer)).toMatchObject({ ok: false, reason: expect.stringMatching(/cover/) });
    const swapped = trialFor('P01', (r) => ({
      ...r,
      phases: r.phases.map((p) => (p.phase === 'calibration-sit' ? { ...p, startT: 0, endT: 5 } : p.phase === 'calibration-stand' ? { ...p, startT: 5, endT: 10 } : p)),
    }));
    expect(measureTrial(swapped, PROTOCOL)).toMatchObject({ ok: false, reason: expect.stringMatching(/standing head/) });
  });
});

describe('buildSpace', () => {
  const m = okMeasure(trialFor('P01'));

  it('refuses without trials or without two labelled attempts', () => {
    expect(buildSpace([], OPTIONS.gridPoints)).toMatchObject({ ok: false, errors: ['no tuning trials'] });
    expect(buildSpace([{ ...m, attemptIntervals: [] }], OPTIONS.gridPoints)).toMatchObject({ ok: false });
  });

  it('refuses perfectly regular frames, too-fast attempts and motionless data', () => {
    const regular = buildSpace([{ ...m, maxGapS: 1 / m.frameRate }], OPTIONS.gridPoints);
    expect(regular.ok || regular.errors.join(' ')).toMatch(/perfectly regular/);
    const fast = buildSpace([{ ...m, attemptIntervals: [0.1] }], OPTIONS.gridPoints);
    expect(fast.ok || fast.errors.join(' ')).toMatch(/Nyquist/);
    const still = buildSpace([{ ...m, progress: [0.5], visibilities: [] }], OPTIONS.gridPoints);
    expect(still.ok || still.errors.join(' ')).toMatch(/fewer than two distinct/);
    expect(still.ok || still.errors.join(' ')).toMatch(/no data-derived candidates for visibilityMin/);
  });

  it('starts reach at the highest candidate when no labelled full stand exists', () => {
    const built = buildSpace([{ ...m, fullPeakMin: null, seatedNoise: 2 }], OPTIONS.gridPoints);
    if (!built.ok) throw new Error(built.errors.join('; '));
    const reach = built.space.candidates.reachFraction;
    expect(built.space.init.reachFraction).toBe(reach[reach.length - 1]);
    expect(built.space.init.seatFraction).toBe(reach[0]);
  });
});

describe('scoring', () => {
  it('scores a calibration failure as an abstention that missed every labelled stand', () => {
    const score = scoreTrial(trialFor('P01'), { ...TEST_PARAMS, calibStillnessMaxRatio: 0 });
    expect(score).toMatchObject({ engineFull: null, countError: 3, disagreements: 5, abstained: true, flagMismatch: false });
    expect(score.failure).toMatch(/^calibration:/);
  });

  it('orders losses lexicographically', () => {
    expect(compareLoss([0, 5, 5], [1, 0, 0])).toBeLessThan(0);
    expect(compareLoss([1, 2, 3], [1, 2, 3])).toBe(0);
    expect(lossOf([])).toEqual([0, 0, 0]);
  });

  it('reports n/a rates when there is nothing to divide', () => {
    expect(groupMetrics('none', [])).toMatchObject({ countMAE: null, halfRejection: null, armRejection: null, abstainRate: null });
  });
});

describe('fitParams failures', () => {
  it('skips unmeasurable trials and reports why it cannot fit', () => {
    const broken = trialFor('P01', (r) => ({ ...r, phases: r.phases.filter((p) => p.phase !== 'calibration-sit') }));
    const fit = fitParams([broken], PROTOCOL, OPTIONS);
    expect(fit).toMatchObject({ ok: false, errors: ['no tuning trials'] });
    expect(fit.skipped[0].reason).toMatch(/calibration capture unusable/);
  });

  it('records a failed fold instead of throwing', () => {
    const lone = { ...trialFor('P02'), marks: [] };
    const folds = leaveOneSubjectOut([trialFor('P01'), lone], PROTOCOL, OPTIONS);
    expect(folds.find((f) => f.participant === 'P01')?.errors.join(' ')).toMatch(/two labelled attempts/);
  });
});

describe('output', () => {
  const trials = [trialFor('P01'), trialFor('P02'), trialFor('P03')];
  const fit = fitParams(trials, PROTOCOL, OPTIONS);
  if (!fit.ok) throw new Error(fit.errors.join('; '));
  const current = paramsJson as Parameters<typeof buildParamsFile>[0];
  const prov = { splitHash: 'abc123', date: '2026-10-28', generatedAt: '2026-10-28T10:00:00.000Z' };

  it('writes a params.json that loads, keeps protocol constants and records provenance', () => {
    const out = buildParamsFile(current, fit, prov);
    if (!out.ok) throw new Error(out.errors.join('; '));
    expect(loadParams(out.file).status).toBe('ready');
    expect(readProtocolConstants(out.file)).toMatchObject({ ok: true, constants: PROTOCOL });
    expect(out.file.params.chairStandDurationS).toEqual(current.params.chairStandDurationS);
    expect(out.file.params.reachFraction).toMatchObject({ class: 'fitted', unit: 'fraction', script: FIT_SCRIPT, date: '2026-10-28' });
    expect(out.file.params.reachFraction.source).toMatch(/3 trials from 3 tuning participants, split abc123/);
    expect(tunedOnSplit(out.file, 'abc123')).toBe(true);
    expect(tunedOnSplit(out.file, 'other')).toBe(false);
    expect(tunedOnSplit(current, 'abc123')).toBe(false);
  });

  it('refuses params that would not load', () => {
    const broken = { ...fit, params: { ...fit.params, reachFraction: fit.params.seatFraction } };
    expect(buildParamsFile(current, broken, prov)).toMatchObject({ ok: false });
    const noProtocol = { ...current, params: {} };
    expect(buildParamsFile(noProtocol, fit, prov)).toMatchObject({ ok: false, errors: expect.arrayContaining(['missing chairStandDurationS']) });
  });

  it('builds the tune report and the held-out evaluation record', () => {
    const folds = leaveOneSubjectOut(trials, PROTOCOL, OPTIONS);
    const report = buildTuneReport({ fit, folds, options: OPTIONS, splitHash: 'abc123', generatedAt: prov.generatedAt, exclusions: [], addedAfterLock: [] });
    expect(report.loso.scores).toHaveLength(3);
    expect(report.loso.metrics.at(-1)?.group).toBe('all');
    const record = evaluateHeldOut({ trials, params: fit.params, heldOutPeople: ['P01'], splitHash: 'abc123', paramsSha256: 'x', generatedAt: prov.generatedAt, exclusions: [] });
    expect(record.metrics).toEqual(metricsByClothing(record.scores));
  });
});
