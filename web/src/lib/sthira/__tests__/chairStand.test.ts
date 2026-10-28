import { describe, expect, it } from 'vitest';
import { initialState, step, summarize, type ChairStandState } from '../chairStand';
import type { PoseFrame } from '../types';
import { buildSession, makeCtx, makeFrame, SEATED_Y, TEST_PARAMS, type RepSpec } from './fixtures';

const CTX = makeCtx();

const run = (frames: readonly PoseFrame[], ctx = CTX): ChairStandState =>
  frames.reduce((state, frame) => step(state, frame, ctx), initialState());

const SESSION_S = 40;
const fiveReps = (extra: Partial<RepSpec> = {}): RepSpec[] =>
  [2, 6, 10, 14, 18].map((startS) => ({ startS, ...extra }));

describe('chair stand reducer', () => {
  it('counts every full rep as full', () => {
    const summary = summarize(run(buildSession(30, SESSION_S, fiveReps())), CTX);
    expect(summary.full).toBe(5);
    expect(summary.half + summary.armAssisted + summary.uncertain).toBe(0);
  });

  it('rejects half-stands', () => {
    const reps: RepSpec[] = [{ startS: 2 }, { startS: 6, peak: 0.5 }, { startS: 10 }];
    const summary = summarize(run(buildSession(30, SESSION_S, reps)), CTX);
    expect(summary.full).toBe(2);
    expect(summary.half).toBe(1);
  });

  it('rejects arm-assisted stands even when fully upright', () => {
    const reps: RepSpec[] = [{ startS: 2 }, { startS: 6, armsUncrossed: true }];
    const summary = summarize(run(buildSession(30, SESSION_S, reps)), CTX);
    expect(summary.full).toBe(1);
    expect(summary.armAssisted).toBe(1);
  });

  it('flags reps with low landmark visibility as uncertain, taking precedence over arm use', () => {
    const reps: RepSpec[] = [{ startS: 2, lowVisibility: true, armsUncrossed: true }, { startS: 6 }];
    const summary = summarize(run(buildSession(30, SESSION_S, reps)), CTX);
    expect(summary.uncertain).toBe(1);
    expect(summary.armAssisted).toBe(0);
    expect(summary.full).toBe(1);
  });

  it('gives identical rep sequences at 10, 15 and 30 fps (decimation invariance)', () => {
    const reps: RepSpec[] = [
      { startS: 2 },
      { startS: 6, peak: 0.5 },
      { startS: 10, armsUncrossed: true },
      { startS: 14, lowVisibility: true },
      { startS: 18 },
    ];
    const kinds = [10, 15, 30].map((fps) =>
      summarize(run(buildSession(fps, SESSION_S, reps)), CTX).events.map((e) => e.kind),
    );
    expect(kinds[0]).toEqual(['full', 'half', 'arm-assisted', 'uncertain', 'full']);
    expect(kinds[1]).toEqual(kinds[0]);
    expect(kinds[2]).toEqual(kinds[0]);
  });

  it('ignores reps that start too late to get anywhere before the protocol duration ends', () => {
    const reps: RepSpec[] = [{ startS: 2 }, { startS: 29.8 }, { startS: 33 }];
    const summary = summarize(run(buildSession(30, SESSION_S, reps)), CTX);
    expect(summary.events).toHaveLength(1);
    expect(summary.full).toBe(1);
  });

  it('ignores a sub-threshold wobble while seated', () => {
    const frames = Array.from({ length: 60 }, (_, i) => makeFrame(i / 30, SEATED_Y - 5));
    expect(run(frames).events).toHaveLength(0);
  });

  it('does not treat a brief touch of the standing height as standing (dwell)', () => {
    const reps: RepSpec[] = [{ startS: 2, riseS: 0.5, holdS: 0, fallS: 0.5 }];
    const summary = summarize(run(buildSession(30, SESSION_S, reps)), CTX);
    expect(summary.full).toBe(0);
    expect(summary.half).toBe(1);
  });

  it('drops out-of-order timestamps, counting them, without touching anything else', () => {
    const frames = buildSession(30, 5, []);
    const base = run(frames);
    const stale = step(base, { ...frames[1] }, CTX);
    expect(stale).toEqual({ ...base, droppedFrames: base.droppedFrames + 1 });
  });

  it('advances time on a frame with no person but leaves the head signal alone', () => {
    const frames = buildSession(30, 5, []);
    const base = run(frames);
    const empty = step(base, { t: (base.lastT ?? 0) + 1 / 30, landmarks: [] }, CTX);
    expect(empty.frameCount).toBe(base.frameCount + 1);
    expect(empty.smoothedHeadY).toBe(base.smoothedHeadY);
    expect(empty.events).toEqual([]);
  });

  it('does not mutate the previous state', () => {
    const frames = buildSession(30, 5, [{ startS: 1 }]);
    const before = run(frames.slice(0, 40));
    const snapshot = JSON.stringify(before);
    step(before, frames[41], CTX);
    expect(JSON.stringify(before)).toBe(snapshot);
  });
});

describe('summarize', () => {
  it('reports effective fps and the low-fps banner flag', () => {
    const fast = summarize(run(buildSession(30, 5, [])), CTX);
    const slow = summarize(run(buildSession(5, 5, [])), CTX);
    expect(fast.effectiveFps).toBeCloseTo(30, 5);
    expect(fast.lowFps).toBe(false);
    expect(slow.effectiveFps).toBeCloseTo(5, 5);
    expect(slow.lowFps).toBe(true);
  });

  it('handles an empty session', () => {
    const summary = summarize(initialState(), CTX);
    expect(summary.effectiveFps).toBe(0);
    expect(summary.unreliable).toBe(false);
    expect(summary.full).toBe(0);
  });

  it('marks the count unreliable when uncertain reps exceed unreliableUncertainFraction', () => {
    const lowVis = { lowVisibility: true };
    const mostlyUncertain = run(buildSession(30, SESSION_S, [{ startS: 2, ...lowVis }, { startS: 6, ...lowVis }, { startS: 10 }]));
    const oneInThree = run(buildSession(30, SESSION_S, [{ startS: 2, ...lowVis }, { startS: 6 }, { startS: 10 }]));
    const oneInTwo = run(buildSession(30, SESSION_S, [{ startS: 2, ...lowVis }, { startS: 6 }]));
    expect(summarize(mostlyUncertain, CTX).unreliable).toBe(true);
    expect(summarize(oneInThree, CTX).unreliable).toBe(false);
    expect(summarize(oneInTwo, CTX).unreliable).toBe(false);
  });

  it('reads the unreliable cut-off from params, not from a constant', () => {
    const lowVis = { lowVisibility: true };
    const oneInThree = buildSession(30, SESSION_S, [{ startS: 2, ...lowVis }, { startS: 6 }, { startS: 10 }]);
    const strict = makeCtx({ ...TEST_PARAMS, unreliableUncertainFraction: 0.2 });
    expect(summarize(run(oneInThree, strict), strict).unreliable).toBe(true);
  });
});
