import type { PhaseState, Stage } from "../record/phaseMachine";
import type { PhaseSpan } from "../record/schema";
import { calibrate } from "../sthira/calibration";
import { finalize, initialState, step, summarize, type ChairStandState, type ChairStandSummary } from "../sthira/chairStand";
import { makeContext, type StepContext } from "../sthira/context";
import { coveringFrames } from "../sthira/replay";
import type { PoseFrame, VideoGeometry } from "../sthira/types";
import type { CounterStatus } from "./counterStatus";

/**
 * Live chair-stand session, driven by the guided phase machine and the recorded frames:
 * calibration frames are collected, the test start (Go) runs calibrate + makeContext, every test
 * frame is stepped, and the end of the test finalizes the count. Pure: every event returns a new state.
 */
export type CountSetup =
  /** Before Go: calibration frames are being collected. */
  | { readonly kind: "waiting" }
  /**
   * Go has passed; calibration runs on the first frame at or after Go, so a frame on the
   * stand/test boundary (delivered after the tick that moved the phase) still counts for calibration.
   */
  | { readonly kind: "starting"; readonly testStartT: number; readonly phases: readonly PhaseSpan[]; readonly geometry: VideoGeometry }
  /** params.json is not tuned (or invalid): the counter never runs. */
  | { readonly kind: "not-tuned" }
  | { readonly kind: "calibration-failed"; readonly reasons: readonly string[] }
  | { readonly kind: "context-failed"; readonly errors: readonly string[] }
  | { readonly kind: "counting"; readonly ctx: StepContext; readonly chair: ChairStandState; readonly testStartT: number }
  | { readonly kind: "finished"; readonly ctx: StepContext; readonly summary: ChairStandSummary };

export interface LiveCountState {
  readonly stage: Stage;
  readonly stageStartT: number;
  readonly calibrationFrames: readonly PoseFrame[];
  readonly setup: CountSetup;
}

export type LiveCountEvent =
  /** Send after every phase-machine change, with the camera's video size. */
  | { readonly type: "phase"; readonly phase: PhaseState; readonly geometry: VideoGeometry }
  /** Send for every recorded frame, after the phase machine has seen its t. */
  | { readonly type: "frame"; readonly frame: PoseFrame };

export const INITIAL_LIVE_COUNT: LiveCountState = { stage: "idle", stageStartT: 0, calibrationFrames: [], setup: { kind: "waiting" } };

const lastSpan = (phases: readonly PhaseSpan[], phase: PhaseSpan["phase"]): PhaseSpan | undefined =>
  phases.filter((span) => span.phase === phase).at(-1);

/** Frames inside a phase span, both ends included. */
/** Same selection as the fitter's replay (the phase plus the nearest frame on each side), so live and fitted calibration agree. */
export function framesInSpan(frames: readonly PoseFrame[], span: PhaseSpan | undefined): PoseFrame[] {
  return span === undefined ? [] : coveringFrames(frames, span.startT, span.endT);
}

type Starting = Extract<CountSetup, { kind: "starting" }>;

function beginTest(frames: readonly PoseFrame[], start: Starting, counter: CounterStatus): CountSetup {
  if (counter.status !== "ready") return { kind: "not-tuned" };
  const sit = framesInSpan(frames, lastSpan(start.phases, "calibration-sit"));
  const stand = framesInSpan(frames, lastSpan(start.phases, "calibration-stand"));
  const calibration = calibrate(sit, stand, start.geometry, counter.params);
  if (!calibration.ok) return { kind: "calibration-failed", reasons: calibration.reasons };
  const context = makeContext(start.geometry, calibration.calib, counter.params);
  if (!context.ok) return { kind: "context-failed", errors: context.errors };
  return { kind: "counting", ctx: context.ctx, chair: initialState(start.testStartT), testStartT: start.testStartT };
}

function finish(setup: Extract<CountSetup, { kind: "counting" }>): CountSetup {
  const chair = finalize(setup.chair, setup.ctx);
  return { kind: "finished", ctx: setup.ctx, summary: summarize(chair, setup.ctx) };
}

/** A (re)started calibration opens a fresh phase list, so its first span starts at the current stage start. */
const isNewCalibration = (state: LiveCountState, phase: PhaseState): boolean => {
  const first = phase.phases.at(0);
  const restarted = first !== undefined && first.startT === phase.stageStartT;
  return restarted && (state.stage !== phase.stage || state.stageStartT !== phase.stageStartT);
};

function syncPhase(state: LiveCountState, phase: PhaseState, geometry: VideoGeometry, counter: CounterStatus): LiveCountState {
  if (phase.stage === "idle") return INITIAL_LIVE_COUNT;
  const base = isNewCalibration(state, phase) ? INITIAL_LIVE_COUNT : state;
  const synced: LiveCountState = { ...base, stage: phase.stage, stageStartT: phase.stageStartT };
  const testSpan = lastSpan(phase.phases, "test");
  const atGo: LiveCountState =
    synced.setup.kind === "waiting" && testSpan !== undefined
      ? { ...synced, setup: { kind: "starting", testStartT: testSpan.startT, phases: phase.phases, geometry } }
      : synced;
  if (phase.stage !== "done") return atGo;
  const begun = atGo.setup.kind === "starting" ? startTest(atGo, atGo.setup, counter) : atGo;
  return begun.setup.kind === "counting" ? { ...begun, setup: finish(begun.setup) } : begun;
}

const startTest = (state: LiveCountState, start: Starting, counter: CounterStatus): LiveCountState => ({
  ...state,
  calibrationFrames: [],
  setup: beginTest(state.calibrationFrames, start, counter),
});

function addFrame(state: LiveCountState, frame: PoseFrame, counter: CounterStatus): LiveCountState {
  const { setup } = state;
  if (setup.kind === "counting") return { ...state, setup: { ...setup, chair: step(setup.chair, frame, setup.ctx) } };
  const calibrating = state.stage === "calibration-sit" || state.stage === "calibration-stand";
  // While starting, the frame that reaches Go is kept too: it is the sit phase's covering frame (see framesInSpan).
  const collecting = setup.kind === "starting" || (setup.kind === "waiting" && calibrating);
  const collected = collecting ? { ...state, calibrationFrames: [...state.calibrationFrames, frame] } : state;
  if (setup.kind !== "starting" || frame.t < setup.testStartT) return collected;
  return addFrame(startTest(collected, setup, counter), frame, counter);
}

export function liveCountReducer(state: LiveCountState, event: LiveCountEvent, counter: CounterStatus): LiveCountState {
  return event.type === "phase" ? syncPhase(state, event.phase, event.geometry, counter) : addFrame(state, event.frame, counter);
}

export type LiveCountView =
  | { readonly kind: "waiting" | "not-tuned" }
  | { readonly kind: "calibration-failed"; readonly reasons: readonly string[] }
  | { readonly kind: "context-failed"; readonly errors: readonly string[] }
  | { readonly kind: "counting"; readonly summary: ChairStandSummary; readonly elapsedS: number; readonly remainingS: number }
  | { readonly kind: "finished"; readonly summary: ChairStandSummary };

/** What the screen shows at recording time `nowT`. The live count credits a stand once standing is reached. */
export function describeLiveCount(state: LiveCountState, nowT: number): LiveCountView {
  const { setup } = state;
  if (setup.kind === "counting") {
    const durationS = setup.ctx.params.chairStandDurationS;
    const elapsedS = Math.min(durationS, Math.max(0, nowT - setup.testStartT));
    return { kind: "counting", summary: summarize(setup.chair, setup.ctx), elapsedS, remainingS: durationS - elapsedS };
  }
  if (setup.kind === "finished") return { kind: "finished", summary: setup.summary };
  if (setup.kind === "calibration-failed") return { kind: "calibration-failed", reasons: setup.reasons };
  if (setup.kind === "context-failed") return { kind: "context-failed", errors: setup.errors };
  return { kind: setup.kind === "not-tuned" ? "not-tuned" : "waiting" };
}
