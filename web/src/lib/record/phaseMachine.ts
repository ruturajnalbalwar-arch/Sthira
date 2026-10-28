import type { ProtocolConstants } from "../sthira/params/protocol";
import type { PhaseSpan, SessionMeta } from "./schema";
import { closePhase, openPhase } from "./session";

/**
 * Guided recording flow:
 * idle -> calibration-stand -> calibration-sit -> test -> done.
 * Standing is calibrated first so the person is seated at "Go", as the CDC 30-second chair stand requires;
 * the counter starts SEATED. The start of `test` is the CDC "Go". All times are seconds since the recording started.
 */
export type Stage = "idle" | "calibration-sit" | "calibration-stand" | "test" | "done";

export interface PhaseState {
  readonly stage: Stage;
  /** When the current stage began (seconds since recording start). */
  readonly stageStartT: number;
  readonly phases: readonly PhaseSpan[];
  /** When the test ended; null until done. */
  readonly endT: number | null;
}

export type PhaseEvent =
  | { readonly type: "start"; readonly t: number }
  | { readonly type: "tick"; readonly t: number }
  | { readonly type: "stop"; readonly t: number }
  | { readonly type: "redo-calibration"; readonly t: number }
  /** The capture ended early (camera lost, tracking failed, frame limit): close the open phase. */
  | { readonly type: "interrupt"; readonly t: number }
  | { readonly type: "cancel" }
  | { readonly type: "reset" };

export interface MachineConfig {
  readonly calibrationSitS: number;
  readonly calibrationStandS: number;
  /** Fixed test length (only the 30 s chair stand); null = runs until Stop. */
  readonly testDurationS: number | null;
  readonly isChairStand: boolean;
}

export const INITIAL_PHASE_STATE: PhaseState = { stage: "idle", stageStartT: 0, phases: [], endT: null };

const CHAIR_STAND_TRIAL: SessionMeta["trial"] = "chair-stand-30s";

/** Every duration comes from params.json (protocol constants); nothing is hardcoded here. */
export function machineConfigFor(constants: ProtocolConstants, trial: SessionMeta["trial"]): MachineConfig {
  const isChairStand = trial === CHAIR_STAND_TRIAL;
  return {
    calibrationSitS: constants.calibrationSitS,
    calibrationStandS: constants.calibrationStandS,
    testDurationS: isChairStand ? constants.chairStandDurationS : null,
    isChairStand,
  };
}

function enterStage(state: PhaseState, stage: "calibration-sit" | "calibration-stand" | "test", t: number): PhaseState {
  return { ...state, stage, stageStartT: t, phases: openPhase(state.phases, stage, t) };
}

function finish(state: PhaseState, t: number): PhaseState {
  return { ...state, stage: "done", phases: closePhase(state.phases, t), endT: t };
}

function startCalibration(t: number): PhaseState {
  return enterStage(INITIAL_PHASE_STATE, "calibration-stand", t);
}

/** Length of the current stage, or null if it has no fixed length. */
function stageDuration(stage: Stage, config: MachineConfig): number | null {
  if (stage === "calibration-sit") return config.calibrationSitS;
  if (stage === "calibration-stand") return config.calibrationStandS;
  if (stage === "test") return config.testDurationS;
  return null;
}

/**
 * Advances through every stage whose countdown has elapsed by time `t`. Boundaries are placed at
 * the exact protocol time (stage start + duration), not at the late tick that noticed them.
 */
function advance(state: PhaseState, t: number, config: MachineConfig): PhaseState {
  const duration = stageDuration(state.stage, config);
  if (duration === null) return state;
  const boundary = state.stageStartT + duration;
  if (t < boundary) return state;
  if (state.stage === "calibration-stand") return advance(enterStage(state, "calibration-sit", boundary), t, config);
  if (state.stage === "calibration-sit") return advance(enterStage(state, "test", boundary), t, config);
  return finish(state, boundary);
}

const ACTIVE: readonly Stage[] = ["calibration-sit", "calibration-stand", "test"];

/** Pure reducer. Events that do not apply to the current stage return the same state object. */
export function phaseReducer(state: PhaseState, event: PhaseEvent, config: MachineConfig): PhaseState {
  const active = ACTIVE.includes(state.stage);
  switch (event.type) {
    case "start":
      return state.stage === "idle" ? startCalibration(event.t) : state;
    case "tick":
      return active ? advance(state, event.t, config) : state;
    case "stop":
      return state.stage === "test" ? finish(state, event.t) : state;
    case "redo-calibration":
      return active ? startCalibration(event.t) : state;
    case "interrupt":
      return active ? finish(state, Math.max(event.t, state.stageStartT)) : state;
    case "cancel":
      return active ? INITIAL_PHASE_STATE : state;
    case "reset":
      return state.stage === "done" ? INITIAL_PHASE_STATE : state;
  }
}

export interface PhaseView {
  /** Short instruction readable from 3 m. */
  readonly title: string;
  /** Whole seconds left in a calibration countdown; null outside calibration. */
  readonly count: number | null;
  /** Whole seconds since Go; null outside the test. */
  readonly elapsedS: number | null;
  /** Fixed test length in seconds (chair stand only); null otherwise. */
  readonly limitS: number | null;
}

const CHAIR_STAND_GO = "Go! Stand up and sit down as many times as you can";

function countdown(state: PhaseState, t: number, duration: number): number {
  const elapsed = Math.max(0, t - state.stageStartT);
  return Math.max(0, Math.ceil(duration - elapsed));
}

/** What the banner shows at time `t`. */
export function describePhase(state: PhaseState, t: number, config: MachineConfig): PhaseView {
  const none = { count: null, elapsedS: null, limitS: null };
  switch (state.stage) {
    case "idle":
      return { title: "Ready", ...none };
    case "calibration-sit":
      return { ...none, title: "Sit down and keep still", count: countdown(state, t, config.calibrationSitS) };
    case "calibration-stand":
      return { ...none, title: "Stand up tall and keep still", count: countdown(state, t, config.calibrationStandS) };
    case "test":
      return {
        title: config.isChairStand ? CHAIR_STAND_GO : "Go",
        count: null,
        elapsedS: Math.floor(Math.max(0, t - state.stageStartT)),
        limitS: config.testDurationS,
      };
    case "done":
      return { title: "Stop. Recording finished", ...none };
  }
}
