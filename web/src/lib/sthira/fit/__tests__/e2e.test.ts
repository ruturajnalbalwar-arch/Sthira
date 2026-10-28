import { describe, expect, it } from 'vitest';
import type { RepSpec } from '../../__tests__/fixtures';
import { L1, L2, labelEvents, pilotFiles, repTrain, synthLabels, synthRecording, type NamedText } from '../../__tests__/syntheticPilot';
import { loadPilot } from '../data';
import { metricsByClothing } from '../metrics';
import { scoreTrial } from '../objective';
import { fitParams, leaveOneSubjectOut } from '../search';
import { initSplit, partition } from '../split';

const PROTOCOL = { chairStandDurationS: 30, calibrationSitS: 5, calibrationStandS: 5 };
const OPTIONS = { gridPoints: 9, maxRounds: 4 };
const RULES = { heldOutCount: 4, minLooseHeldOut: 2, looseClothing: ['saree', 'dhoti'] as const, minTuning: 2 };

type Clothing = Parameters<typeof synthRecording>[0]['clothing'];

/** Person i: a 30 s trial at a person-specific pace with one half, one arm-assisted and one hidden (uncertain) attempt, plus a half-stand trial. */
function personFiles(i: number, clothing: Clothing): NamedText[] {
  const participant = `P0${i}`;
  const every = 3.6 + (i % 3) * 0.4;
  const count = Math.floor(27 / every);
  const cs = repTrain(count, 0.8 + (i % 2) * 0.3, every, { [1 + (i % 2)]: { peak: 0.45 }, [3]: { armsUncrossed: true }, [4]: { lowVisibility: true } });
  const half: RepSpec[] = repTrain(3, 1, 5, { 0: { peak: 0.5 }, 1: { peak: 0.6 }, 2: { peak: 0.4 } });
  return [
    { trial: 'chair-stand-30s' as const, reps: cs, startedAt: `2026-10-23T1${i % 10}:00:00.000Z` },
    { trial: 'half-stands' as const, reps: half, startedAt: `2026-10-23T1${i % 10}:30:00.000Z` },
  ].flatMap(({ trial, reps, startedAt }) => {
    const recording = synthRecording({ participant, clothing, trial, reps, startedAt });
    const events = labelEvents(reps);
    return pilotFiles(recording, [synthLabels(recording, events, L1), synthLabels(recording, events, L2)]);
  });
}

const CLOTHING: Clothing[] = ['saree', 'dhoti', 'track-pants', 'saree', 'track-pants', 'dhoti', 'track-pants', 'track-pants'];
const FILES = CLOTHING.flatMap((c, i) => personFiles(i + 1, c));

describe('fitter end to end on synthetic pilot data', () => {
  const data = loadPilot(FILES, PROTOCOL);
  const split = initSplit(data.people, 'P01', RULES, 'e2e', '2026-10-24T09:00:00.000Z');
  if (!split.ok) throw new Error(split.problems.join('; '));
  const parts = partition(data.trials, split.split);

  it('loads every synthetic trial', () => {
    expect(data.exclusions).toEqual([]);
    expect(data.trials).toHaveLength(16);
  });

  it('tunes on the tuning people only and recovers held-out counts', () => {
    const fit = fitParams(parts.tuning, PROTOCOL, OPTIONS);
    if (!fit.ok) throw new Error(fit.errors.join('; '));
    expect(fit.people.some((p) => split.split.heldOut.includes(p))).toBe(false);
    expect(fit.loss[0]).toBe(0);
    const scores = parts.heldOut.map((t) => scoreTrial(t, fit.params));
    const all = metricsByClothing(scores).at(-1);
    expect(all).toMatchObject({ countMAE: 0, exactMatchRate: 1, falseCounts: 0, halfRejection: 1, armRejection: 1, abstainRate: 0 });
  });

  it('runs leave-one-subject-out over the tuning people', () => {
    const folds = leaveOneSubjectOut(parts.tuning, PROTOCOL, OPTIONS);
    expect(folds.map((f) => f.participant)).toEqual(split.split.tuning);
    expect(folds.every((f) => f.errors.length === 0)).toBe(true);
    expect(folds.flatMap((f) => f.scores).every((s) => s.countError === 0)).toBe(true);
  });
});
