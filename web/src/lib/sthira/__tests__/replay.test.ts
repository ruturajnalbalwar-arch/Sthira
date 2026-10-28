import { describe, expect, it } from 'vitest';
import type { Recording } from '../../record/schema';
import { replayRecording } from '../replay';
import { TEST_PARAMS } from './fixtures';
import { GO_S, repTrain, synthRecording } from './syntheticPilot';

const reps = repTrain(5, 1, 5, { 1: { peak: 0.5 }, 2: { armsUncrossed: true } });
/**
 * Each calibration phase is covered by one frame of the neighbouring posture (see coveringFrames),
 * so the stillness ratio is larger than the fixture default; the fitter derives this gate from the
 * same covering frames.
 */
const PARAMS = { ...TEST_PARAMS, calibStillnessMaxRatio: 0.3 };

describe('replayRecording', () => {
  it('calibrates from the recorded phases and counts the test phase', () => {
    const result = replayRecording(synthRecording({ participant: 'P01', reps }), PARAMS);
    if (!result.ok) throw new Error(result.reasons.join('; '));
    expect(result.summary.events.map((e) => e.kind)).toEqual(['full', 'half', 'arm-assisted', 'full', 'full']);
    expect(result.summary.windowComplete).toBe(true);
    expect(result.calib.seatedHeadY).toBeGreaterThan(result.calib.standingHeadY);
  });

  it('anchors the timed window at the test phase start (Go), not the first frame', () => {
    const result = replayRecording(synthRecording({ participant: 'P01', reps }), PARAMS);
    if (!result.ok) throw new Error('replay failed');
    expect(result.summary.events[0].startT).toBeGreaterThanOrEqual(GO_S);
  });

  it('gives the same rep sequence at 10, 15 and 30 fps', () => {
    const kinds = [10, 15, 30].map((fps) => {
      const result = replayRecording(synthRecording({ participant: 'P01', reps, fps }), PARAMS);
      return result.ok ? result.summary.events.map((e) => e.kind) : result.reasons;
    });
    expect(kinds[1]).toEqual(kinds[0]);
    expect(kinds[2]).toEqual(kinds[0]);
  });

  it('reports a missing phase instead of guessing', () => {
    const recording = synthRecording({ participant: 'P01', reps });
    const noStand: Recording = { ...recording, phases: recording.phases.filter((p) => p.phase !== 'calibration-stand') };
    const result = replayRecording(noStand, PARAMS);
    expect(result).toMatchObject({ ok: false, stage: 'phases' });
    expect(!result.ok && result.reasons.join(' ')).toMatch(/calibration-stand/);
  });

  it('reports a calibration failure with the gate reasons', () => {
    const strict = { ...PARAMS, calibStillnessMaxRatio: 0 };
    const result = replayRecording(synthRecording({ participant: 'P01', reps }), strict);
    expect(result).toMatchObject({ ok: false, stage: 'calibration' });
    expect(!result.ok && result.reasons.join(' ')).toMatch(/still/);
  });

  it('reports invalid params at the context stage', () => {
    const bad = { ...PARAMS, reachFraction: PARAMS.seatFraction };
    const result = replayRecording(synthRecording({ participant: 'P01', reps }), bad);
    expect(result).toMatchObject({ ok: false, stage: 'context' });
  });

  it('uses the last calibration when it was redone', () => {
    const recording = synthRecording({ participant: 'P01', reps });
    const redo: Recording = { ...recording, phases: [{ phase: 'calibration-stand', startT: 0, endT: 0 }, ...recording.phases] };
    expect(replayRecording(redo, PARAMS).ok).toBe(true);
  });

  it('covers a phase whose boundaries fall between frames', () => {
    const recording = synthRecording({ participant: 'P01', reps });
    const shifted: Recording = {
      ...recording,
      phases: recording.phases.map((p) => (p.phase === 'calibration-sit' ? { ...p, startT: 5.05, endT: 10.05 } : p)),
    };
    expect(replayRecording(shifted, PARAMS).ok).toBe(true);
  });
});
