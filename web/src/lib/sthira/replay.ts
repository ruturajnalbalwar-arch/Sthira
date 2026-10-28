import type { PhaseSpan, Recording } from '../record/schema';
import { calibrate, type Calibration } from './calibration';
import { initialState, step, summarize, type ChairStandSummary } from './chairStand';
import { makeContext } from './context';
import type { ChairStandParams } from './params/required';
import type { PoseFrame } from './types';

/**
 * Replay a saved sthira.recording.v1 through the same engine the browser runs:
 * calibrate -> makeContext -> initialState(Go) -> step per frame -> summarize (which finalizes).
 * Pure and browser-safe, so the fitter (Node) and the app share one code path.
 */
export type ReplayResult =
  | { readonly ok: true; readonly summary: ChairStandSummary; readonly calib: Calibration }
  | { readonly ok: false; readonly stage: 'phases' | 'calibration' | 'context'; readonly reasons: readonly string[] };

type PhaseName = PhaseSpan['phase'];
const REQUIRED_PHASES: readonly PhaseName[] = ['calibration-sit', 'calibration-stand', 'test'];

/** A redone calibration restarts the phase list, so the last span of each name is the one that counts. */
const lastPhase = (phases: readonly PhaseSpan[], name: PhaseName): PhaseSpan | undefined =>
  phases.findLast((p) => p.phase === name);

/**
 * Frames covering [startT, endT]: those inside it plus the nearest frame on each side. Phase
 * boundaries sit at exact protocol times, which rarely coincide with a frame, so frames strictly
 * inside a 5 s phase span less than 5 s and the calibration duration gate would always fail.
 */
export function coveringFrames(frames: readonly PoseFrame[], startT: number, endT: number): PoseFrame[] {
  const before = frames.findLastIndex((f) => f.t <= startT);
  const firstInside = frames.findIndex((f) => f.t >= startT);
  const from = before >= 0 ? before : firstInside;
  const after = frames.findIndex((f) => f.t >= endT);
  const to = after >= 0 ? after : frames.length - 1;
  return from < 0 ? [] : frames.slice(from, to + 1);
}

export type PhasesResult =
  | { readonly ok: true; readonly sit: PhaseSpan; readonly stand: PhaseSpan; readonly test: PhaseSpan }
  | { readonly ok: false; readonly reasons: readonly string[] };

/** The calibration-sit, calibration-stand and test spans (the last of each), or what is missing. */
export function findPhases(recording: Recording): PhasesResult {
  const spans = REQUIRED_PHASES.map((name) => lastPhase(recording.phases, name));
  const missing = REQUIRED_PHASES.filter((_, i) => spans[i] === undefined);
  if (missing.length > 0) return { ok: false, reasons: missing.map((name) => `no ${name} phase`) };
  const [sit, stand, test] = spans as PhaseSpan[];
  return { ok: true, sit, stand, test };
}

export function replayRecording(recording: Recording, params: ChairStandParams): ReplayResult {
  const phases = findPhases(recording);
  if (!phases.ok) return { ok: false, stage: 'phases', reasons: phases.reasons };
  const { sit, stand, test } = phases;

  const frames: readonly PoseFrame[] = recording.frames;
  const geometry = { width: recording.capture.videoWidth, height: recording.capture.videoHeight };
  const calibration = calibrate(
    coveringFrames(frames, sit.startT, sit.endT),
    coveringFrames(frames, stand.startT, stand.endT),
    geometry,
    params,
  );
  if (!calibration.ok) return { ok: false, stage: 'calibration', reasons: calibration.reasons };

  const context = makeContext(geometry, calibration.calib, params);
  if (!context.ok) return { ok: false, stage: 'context', reasons: context.errors };
  const { ctx } = context;
  const final = frames.reduce((state, frame) => step(state, frame, ctx), initialState(test.startT));
  return { ok: true, summary: summarize(final, ctx), calib: calibration.calib };
}
