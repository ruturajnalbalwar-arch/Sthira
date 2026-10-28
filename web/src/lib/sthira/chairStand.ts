import type { StepContext } from './context';
import { armsCrossedRatio, headY, isUsableFrame, keyVisibility } from './features';
import type { ChairStandParams } from './params/required';
import { smoothStep } from './signal/smoothing';
import { rateFromCount } from './signal/stats';
import type { PoseFrame } from './types';

export type Phase = 'SEATED' | 'RISING' | 'STANDING';
export type RepKind = 'full' | 'half' | 'arm-assisted' | 'uncertain';

export interface RepEvent {
  readonly kind: RepKind;
  readonly startT: number;
  /** Time STANDING was reached, or null for reps that never stood. */
  readonly standingT: number | null;
  readonly endT: number;
}

interface RepTracker {
  readonly startT: number;
  readonly repS: number;
  readonly lowVisibilityS: number;
  readonly armsViolationS: number;
  readonly standingT: number | null;
}

export interface ChairStandState {
  readonly phase: Phase;
  /** Start of the timed test; frames before it are skipped, frames after windowStartT + duration are ignored. */
  readonly windowStartT: number | null;
  readonly firstT: number | null;
  readonly lastT: number | null;
  readonly frameCount: number;
  readonly droppedFrames: number;
  readonly gaps: number;
  readonly smoothedHeadY: number | null;
  readonly progress: number | null;
  readonly reachSince: number | null;
  readonly seatSince: number | null;
  readonly rep: RepTracker | null;
  readonly events: readonly RepEvent[];
}

export interface ChairStandSummary {
  readonly full: number;
  readonly half: number;
  readonly armAssisted: number;
  readonly uncertain: number;
  readonly events: readonly RepEvent[];
  readonly effectiveFps: number;
  readonly lowFps: boolean;
  /** True when uncertain reps exceed unreliableUncertainFraction of all reps. */
  readonly unreliable: boolean;
  readonly droppedFrames: number;
  readonly gaps: number;
  /** False when the recording stopped before the timed window ended: the count is not a CDC score. */
  readonly windowComplete: boolean;
}

/** testStartT anchors the timed window to the recording's test phase; default is the first frame. */
export const initialState = (testStartT?: number): ChairStandState => ({
  phase: 'SEATED',
  windowStartT: testStartT ?? null,
  firstT: null,
  lastT: null,
  frameCount: 0,
  droppedFrames: 0,
  gaps: 0,
  smoothedHeadY: null,
  progress: null,
  reachSince: null,
  seatSince: null,
  rep: null,
  events: [],
});

const freshRep = (startT: number): RepTracker => ({
  startT,
  repS: 0,
  lowVisibilityS: 0,
  armsViolationS: 0,
  standingT: null,
});

function trackRep(rep: RepTracker, frame: PoseFrame, dtS: number, ctx: StepContext): RepTracker {
  const lowVisibility = keyVisibility(frame) < ctx.params.visibilityMin;
  const ratio = armsCrossedRatio(frame, ctx.geometry);
  const uncrossed = !lowVisibility && ratio > ctx.params.armsCrossedMaxRatio;
  return {
    ...rep,
    repS: rep.repS + dtS,
    lowVisibilityS: rep.lowVisibilityS + (lowVisibility ? dtS : 0),
    armsViolationS: rep.armsViolationS + (uncrossed ? dtS : 0),
  };
}

function classify(rep: RepTracker, params: ChairStandParams): RepKind {
  const lowFraction = rep.repS > 0 ? rep.lowVisibilityS / rep.repS : 0;
  if (lowFraction > params.uncertainFrameMaxFraction) return 'uncertain';
  if (rep.armsViolationS >= params.armsViolationMinS) return 'arm-assisted';
  return rep.standingT === null ? 'half' : 'full';
}

function finishRep(state: ChairStandState, rep: RepTracker, endT: number, ctx: StepContext) {
  const event: RepEvent = {
    kind: classify(rep, ctx.params),
    startT: rep.startT,
    standingT: rep.standingT,
    endT,
  };
  const events = [...state.events, event];
  return { ...state, phase: 'SEATED' as const, rep: null, reachSince: null, seatSince: null, events };
}

function checkSeatReturn(state: ChairStandState, p: number, t: number, ctx: StepContext) {
  if (state.rep === null) return state;
  if (p > ctx.params.seatFraction) return { ...state, seatSince: null };
  const since = state.seatSince ?? t;
  if (t - since >= ctx.params.minSitDwellS) return finishRep(state, state.rep, t, ctx);
  return { ...state, seatSince: since };
}

function checkReach(state: ChairStandState, p: number, t: number, ctx: StepContext) {
  if (state.rep === null) return state;
  if (p < ctx.params.reachFraction) return { ...state, reachSince: null };
  const since = state.reachSince ?? t;
  if (t - since < ctx.params.minStandDwellS) return { ...state, reachSince: since };
  const rep = { ...state.rep, standingT: t };
  return { ...state, phase: 'STANDING' as const, reachSince: null, seatSince: null, rep };
}

function advancePhase(state: ChairStandState, frame: PoseFrame, p: number, ctx: StepContext) {
  if (state.phase === 'SEATED') {
    if (p <= ctx.params.leaveSeatFraction) return state;
    return { ...state, phase: 'RISING' as const, rep: trackRep(freshRep(frame.t), frame, 0, ctx) };
  }
  if (state.phase === 'RISING') {
    const reached = checkReach(state, p, frame.t, ctx);
    return reached.phase === 'RISING' ? checkSeatReturn(reached, p, frame.t, ctx) : reached;
  }
  return checkSeatReturn(state, p, frame.t, ctx);
}

/** A person is in view: update the smoothed head signal and advance the state machine. */
function stepPresent(state: ChairStandState, frame: PoseFrame, dtRawS: number, dtS: number, ctx: StepContext) {
  const raw = headY(frame, ctx.geometry);
  const smoothed =
    state.smoothedHeadY === null
      ? raw
      : smoothStep(state.smoothedHeadY, raw, dtRawS, ctx.params.smoothingCutoffHz);
  const progress = (ctx.calib.seatedHeadY - smoothed) / (ctx.calib.seatedHeadY - ctx.calib.standingHeadY);
  const rep = state.rep === null ? null : trackRep(state.rep, frame, dtS, ctx);
  return advancePhase({ ...state, smoothedHeadY: smoothed, progress, rep }, frame, progress, ctx);
}

/** No usable person: time advances and an open rep abstains (low visibility); the head signal is untouched. */
function stepAbsent(state: ChairStandState, dtS: number): ChairStandState {
  return { ...state, rep: addUnseen(state.rep, dtS), reachSince: null, seatSince: null };
}

const dropFrame = (state: ChairStandState): ChairStandState => ({
  ...state,
  droppedFrames: state.droppedFrames + 1,
});

/** Unseen time inside an open rep counts as low visibility, so a camera stall makes the rep less certain, not more. */
const addUnseen = (rep: RepTracker | null, dtS: number): RepTracker | null =>
  rep === null ? null : { ...rep, repS: rep.repS + dtS, lowVisibilityS: rep.lowVisibilityS + dtS };

/**
 * Pure reducer. Frames with a non-finite or non-increasing t are dropped (and counted).
 * A gap longer than maxGapS resets dwell timers and counts as unseen time for an open rep.
 */
export function step(state: ChairStandState, frame: PoseFrame, ctx: StepContext): ChairStandState {
  if (!Number.isFinite(frame.t)) return dropFrame(state);
  if (state.lastT !== null && frame.t <= state.lastT) return dropFrame(state);
  if (state.windowStartT !== null && frame.t < state.windowStartT) return state;
  const windowStartT = state.windowStartT ?? frame.t;
  if (frame.t - windowStartT > ctx.params.chairStandDurationS) return state;

  const dtRawS = state.lastT === null ? 0 : frame.t - state.lastT;
  const isGap = dtRawS > ctx.params.maxGapS;
  const base: ChairStandState = {
    ...state,
    windowStartT,
    firstT: state.firstT ?? frame.t,
    lastT: frame.t,
    frameCount: state.frameCount + 1,
    gaps: state.gaps + (isGap ? 1 : 0),
    reachSince: isGap ? null : state.reachSince,
    seatSince: isGap ? null : state.seatSince,
    rep: isGap ? addUnseen(state.rep, dtRawS) : state.rep,
  };
  const dtS = isGap ? 0 : dtRawS;
  return isUsableFrame(frame) ? stepPresent(base, frame, dtRawS, dtS, ctx) : stepAbsent(base, dtS);
}

/**
 * Close the open rep at the last frame (time-up). CDC 30-Second Chair Stand: "if the patient is
 * over halfway to a standing position at the end of 30 seconds, count it as a stand". We do not
 * hardcode "halfway": it is the fitted parameter timeUpCountFraction of the calibrated range.
 * A rep still below that fraction is cut off by time, not a half-stand, so it is dropped.
 * The "more than halfway" credit only applies when the timed window really ran out; a recording
 * stopped early credits only reps that already reached standing (CDC counts full stands).
 */
export function finalize(state: ChairStandState, ctx: StepContext): ChairStandState {
  if (state.rep === null || state.lastT === null) return state;
  const timeUpCredit = isWindowComplete(state, ctx) && (state.progress ?? 0) >= ctx.params.timeUpCountFraction;
  const reached = state.rep.standingT !== null || timeUpCredit;
  if (!reached) return { ...state, phase: 'SEATED', rep: null, reachSince: null, seatSince: null };
  const rep = { ...state.rep, standingT: state.rep.standingT ?? state.lastT };
  return finishRep(state, rep, state.lastT, ctx);
}

/** The last frame lies within one maxGapS of the end of the timed window. */
export function isWindowComplete(state: ChairStandState, ctx: StepContext): boolean {
  if (state.windowStartT === null || state.lastT === null) return false;
  return state.lastT - state.windowStartT >= ctx.params.chairStandDurationS - ctx.params.maxGapS;
}

export function summarize(state: ChairStandState, ctx: StepContext): ChairStandSummary {
  const closed = finalize(state, ctx);
  const events = closed.events;
  const count = (kind: RepKind): number => events.filter((e) => e.kind === kind).length;
  const fps =
    closed.firstT === null || closed.lastT === null
      ? 0
      : rateFromCount(closed.frameCount, closed.lastT - closed.firstT);
  return {
    full: count('full'),
    half: count('half'),
    armAssisted: count('arm-assisted'),
    uncertain: count('uncertain'),
    events,
    effectiveFps: fps,
    lowFps: fps < ctx.params.minFps,
    unreliable: events.length > 0 && count('uncertain') / events.length > ctx.params.unreliableUncertainFraction,
    droppedFrames: closed.droppedFrames,
    gaps: closed.gaps,
    windowComplete: isWindowComplete(state, ctx),
  };
}
