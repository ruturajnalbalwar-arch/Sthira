import {
  CAPTURE_ISSUES,
  FRAME_CLOCKS,
  RECORDING_SCHEMA,
  recordingSchema,
  type PhaseSpan,
  type RecordedFrame,
  type Recording,
  type SessionMeta,
} from "./schema";

export interface CaptureInfo {
  startedAt: string;
  videoWidth: number;
  videoHeight: number;
  model: string;
  tasksVisionVersion: string;
  frameClock: (typeof FRAME_CLOCKS)[number];
  issues: readonly (typeof CAPTURE_ISSUES)[number][];
}

/** Closes the currently open phase (if any) at time `t` and opens `phase` at `t`. Returns a new array. */
export function openPhase(phases: readonly PhaseSpan[], phase: PhaseSpan["phase"], t: number): PhaseSpan[] {
  return [...closePhase(phases, t), { phase, startT: t, endT: t }];
}

/** Sets the end time of the last phase to `t`. Returns a new array. */
export function closePhase(phases: readonly PhaseSpan[], t: number): PhaseSpan[] {
  if (phases.length === 0) {
    return [];
  }
  const last = phases[phases.length - 1];
  return [...phases.slice(0, -1), { ...last, endT: t }];
}

/** Assembles and validates a recording. Throws if any part is invalid. */
export function buildRecording(
  meta: SessionMeta,
  capture: CaptureInfo,
  phases: readonly PhaseSpan[],
  frames: readonly RecordedFrame[],
): Recording {
  return recordingSchema.parse({ schema: RECORDING_SCHEMA, meta, capture, phases, frames });
}

/** File name base: participant code, trial, clothing and a compact UTC timestamp. No personal data. */
export function recordingFileBase(meta: SessionMeta, startedAtIso: string): string {
  const stamp = startedAtIso.replace(/[-:]/g, "").replace(/\.\d+Z$/, "Z");
  return [meta.participantCode, meta.trial, meta.clothing, stamp].join("_");
}

/** Average frames per second over the whole recording, from timestamps (shared with the engine). */
export { effectiveFps } from "../sthira/signal/stats";
