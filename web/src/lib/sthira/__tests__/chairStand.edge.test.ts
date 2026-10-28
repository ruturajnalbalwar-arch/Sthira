import { describe, expect, it } from 'vitest';
import { finalize, initialState, step, summarize, type ChairStandState } from '../chairStand';
import { KEY_LANDMARKS, NOSE } from '../landmarks';
import type { Landmark, PoseFrame } from '../types';
import { buildSession, makeCtx, TEST_PARAMS, type RepSpec } from './fixtures';

const CTX = makeCtx();
const FPS = 30;
const SESSION_S = 40;

const run = (frames: readonly PoseFrame[], start = initialState(), ctx = CTX): ChairStandState =>
  frames.reduce((state, frame) => step(state, frame, ctx), start);

const during = (from: number, to: number) => (frame: PoseFrame) => frame.t >= from && frame.t <= to;

/** Replace key landmarks on matching frames. */
function patchKeys(
  frames: readonly PoseFrame[],
  match: (frame: PoseFrame) => boolean,
  patch: (lm: Landmark) => Landmark,
  keys: readonly number[] = KEY_LANDMARKS,
): PoseFrame[] {
  return frames.map((frame) =>
    match(frame)
      ? { ...frame, landmarks: frame.landmarks.map((lm, i) => (keys.includes(i) ? patch(lm) : lm)) }
      : frame,
  );
}

const oneRep: RepSpec[] = [{ startS: 2 }, { startS: 6 }];

describe('time-up (CDC: a stand more than halfway up at 30 s counts)', () => {
  const lateRep: RepSpec[] = [{ startS: 2 }, { startS: 29.2, peak: 0.6, riseS: 0.5, holdS: 5 }];

  it('counts an open rep past timeUpCountFraction at time-up', () => {
    const summary = summarize(run(buildSession(FPS, SESSION_S, lateRep)), CTX);
    expect(summary.full).toBe(2);
    expect(summary.events.at(-1)?.endT).toBeCloseTo(TEST_PARAMS.chairStandDurationS, 5);
  });

  it('drops an open rep below timeUpCountFraction instead of calling it a half-stand', () => {
    const strict = makeCtx({ ...TEST_PARAMS, timeUpCountFraction: 0.7 });
    const summary = summarize(run(buildSession(FPS, SESSION_S, lateRep), initialState(), strict), strict);
    expect(summary.full).toBe(1);
    expect(summary.half).toBe(0);
    expect(summary.events).toHaveLength(1);
  });

  it('counts a person still standing at time-up', () => {
    const standing: RepSpec[] = [{ startS: 28, holdS: 5 }];
    expect(summarize(run(buildSession(FPS, SESSION_S, standing)), CTX).full).toBe(1);
  });

  it('is a no-op on a state with no open rep', () => {
    const state = run(buildSession(FPS, 10, oneRep));
    expect(finalize(state, CTX)).toBe(state);
    expect(finalize(initialState(), CTX)).toEqual(initialState());
  });
});

describe('robustness to bad landmarks', () => {
  it('treats NaN or missing visibility as low visibility (rep becomes uncertain)', () => {
    const base = buildSession(FPS, SESSION_S, oneRep);
    const nan = patchKeys(base, during(2, 4.5), (lm) => ({ ...lm, visibility: Number.NaN }));
    const missing = patchKeys(base, during(2, 4.5), (lm) => ({ x: lm.x, y: lm.y, z: lm.z }) as unknown as Landmark);
    for (const frames of [nan, missing]) {
      const summary = summarize(run(frames), CTX);
      expect(summary.events.map((e) => e.kind)).toEqual(['uncertain', 'full']);
    }
  });

  it('never lets a NaN coordinate poison the smoothed head signal', () => {
    const frames = patchKeys(buildSession(FPS, SESSION_S, oneRep), during(2.5, 3.5), (lm) => ({ ...lm, x: Number.NaN }), [NOSE]);
    const state = run(frames);
    expect(Number.isFinite(state.smoothedHeadY)).toBe(true);
    expect(summarize(state, CTX).events.map((e) => e.kind)).toEqual(['uncertain', 'full']);
  });

  it('counts frames with no person inside a rep as low visibility', () => {
    const frames = buildSession(FPS, SESSION_S, oneRep).map((f) => (during(2.5, 3.5)(f) ? { t: f.t, landmarks: [] } : f));
    expect(summarize(run(frames), CTX).events[0]?.kind).toBe('uncertain');
  });

  it('weights the uncertain share by time, not by frame count', () => {
    // A 0.8 s hole (below maxGapS) followed by one low-visibility frame: 1 frame of ~50, but ~1/3 of the rep's time.
    const holed = buildSession(FPS, SESSION_S, oneRep).filter((f) => !(f.t > 2.3 && f.t < 3.1));
    const firstAfter = holed.findIndex((f) => f.t >= 3.1);
    const marked = holed.map((f, i) => (i === firstAfter ? patchKeys([f], () => true, (lm) => ({ ...lm, visibility: 0 }))[0] : f));
    expect(summarize(run(holed), CTX).events[0]?.kind).toBe('full');
    expect(summarize(run(marked), CTX).events[0]?.kind).toBe('uncertain');
  });
});

describe('timing', () => {
  it('anchors the 30 s window to testStartT, skipping earlier frames', () => {
    const reps: RepSpec[] = [{ startS: 2 }, { startS: 10 }, { startS: 36 }];
    const summary = summarize(run(buildSession(FPS, SESSION_S + 5, reps), initialState(5)), CTX);
    expect(summary.events).toHaveLength(1);
    expect(summary.events[0]?.startT).toBeGreaterThan(10);
  });

  it('counts dropped frames: non-increasing and non-finite timestamps', () => {
    const frames = buildSession(FPS, 5, []);
    const bad = [...frames.slice(0, 10), frames[3], { ...frames[10], t: Number.NaN }, ...frames.slice(10)];
    const summary = summarize(run(bad), CTX);
    expect(summary.droppedFrames).toBe(2);
    expect(summary.effectiveFps).toBeCloseTo(FPS, 5);
  });

  it('treats a camera stall inside a rep as unseen time, making the rep uncertain', () => {
    const stalled = buildSession(FPS, SESSION_S, [{ startS: 2, holdS: 2 }]).filter((f) => !(f.t > 3.2 && f.t < 4.8));
    expect(summarize(run(stalled), CTX).events[0]?.kind).toBe('uncertain');
  });

  it('does not give the time-up credit when the recording stops before the window ends', () => {
    const rising: RepSpec[] = [{ startS: 10.5, peak: 0.6, riseS: 0.5, holdS: 5 }];
    const stopped = buildSession(FPS, 12, rising);
    const summary = summarize(run(stopped), CTX);
    expect(summary.windowComplete).toBe(false);
    expect(summary.events).toHaveLength(0);
  });

  it('still counts a stand that reached standing before an early stop, and flags the window as incomplete', () => {
    const summary = summarize(run(buildSession(FPS, 12, [{ startS: 10, holdS: 5 }])), CTX);
    expect(summary.full).toBe(1);
    expect(summary.windowComplete).toBe(false);
    expect(summarize(run(buildSession(FPS, SESSION_S, oneRep)), CTX).windowComplete).toBe(true);
  });

  it('resets dwell timers after a gap longer than maxGapS and counts the gap as unseen rep time', () => {
    const frames = buildSession(FPS, SESSION_S, [{ startS: 2, holdS: 2 }]);
    let state = initialState();
    let index = 0;
    while (!(state.phase === 'RISING' && state.reachSince !== null)) state = step(state, frames[index++], CTX);
    const lastT = state.lastT ?? 0;
    const gapS = TEST_PARAMS.maxGapS * 2;
    const after = step(state, { ...frames[index], t: lastT + gapS }, CTX);
    expect(after.gaps).toBe(1);
    expect(after.phase).toBe('RISING');
    expect(after.reachSince).toBe(after.lastT);
    expect(after.rep?.repS).toBeCloseTo((state.rep?.repS ?? Number.NaN) + gapS, 9);
    expect(after.rep?.lowVisibilityS).toBeCloseTo((state.rep?.lowVisibilityS ?? Number.NaN) + gapS, 9);
  });
});
