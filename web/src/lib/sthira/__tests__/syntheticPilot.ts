/**
 * Synthetic pilot recordings and label files, built from the engine fixtures. Tests only: the
 * fitter never tunes on these (pilot clips are the only tuning data).
 */
import { LABELS_SCHEMA, recordingRef, type LabelEvent, type LabelsFile } from '../../label/schema';
import type { CaptureIssue } from '../../record/captureIssues';
import { RECORDING_SCHEMA, recordingSchema, type Recording, type SessionMeta } from '../../record/schema';
import type { RepKind } from '../chairStand';
import { buildSession, GEOMETRY, makeFrame, SEATED_Y, STANDING_Y, type RepSpec } from './fixtures';

/** Protocol order: stand calibration first, then sit, so the person is seated at Go. */
export const STAND_END_S = 5;
export const GO_S = 10;
export const TEST_S = 30;

export interface SynthSpec {
  readonly participant: string;
  readonly clothing?: SessionMeta['clothing'];
  readonly trial?: SessionMeta['trial'];
  readonly fps?: number;
  /** Rep times are relative to Go. */
  readonly reps: readonly RepSpec[];
  readonly issues?: readonly CaptureIssue[];
  readonly startedAt?: string;
  /** Seconds of test phase to record (default the full 30 s). */
  readonly testS?: number;
}

/** Odd frames arrive a fifth of a frame late: real cameras are never perfectly regular. */
const frameTime = (i: number, fps: number): number => (i + (i % 2 === 1 ? 0.2 : 0)) / fps;
const jitterPx = (t: number): number => Math.sin(t * 37) * 0.4;

function calibrationFrames(fps: number) {
  const count = Math.round(GO_S * fps);
  return Array.from({ length: count }, (_, i) => {
    const t = frameTime(i, fps);
    const y = (t < STAND_END_S ? STANDING_Y : SEATED_Y) + jitterPx(t);
    return makeFrame(t, y);
  });
}

function testFrames(fps: number, testS: number, reps: readonly RepSpec[]) {
  const offset = Math.round(GO_S * fps);
  return buildSession(fps, testS, reps).map((frame, i) => ({ ...frame, t: frameTime(offset + i, fps) }));
}

export function synthRecording(spec: SynthSpec): Recording {
  const fps = spec.fps ?? 10;
  const testS = spec.testS ?? TEST_S;
  const frames = [...calibrationFrames(fps), ...testFrames(fps, testS, spec.reps)].map((f) => ({
    t: f.t,
    landmarks: f.landmarks.map((l) => ({ ...l })),
  }));
  return recordingSchema.parse({
    schema: RECORDING_SCHEMA,
    meta: {
      participantCode: spec.participant,
      ageBand: '18-29',
      clothing: spec.clothing ?? 'track-pants',
      trial: spec.trial ?? 'chair-stand-30s',
      viewAngle: '45-degree',
      cameraHeightCm: 90,
      cameraDistanceCm: 300,
      room: 'lab',
      notes: '',
      consentConfirmed: true,
    },
    capture: {
      startedAt: spec.startedAt ?? '2026-10-23T10:00:00.000Z',
      videoWidth: GEOMETRY.width,
      videoHeight: GEOMETRY.height,
      model: 'pose_landmarker_lite',
      tasksVisionVersion: '0.10.0',
      frameClock: 'capture-time',
      issues: spec.issues ?? [],
    },
    phases: [
      { phase: 'calibration-stand', startT: 0, endT: STAND_END_S },
      { phase: 'calibration-sit', startT: STAND_END_S, endT: GO_S },
      { phase: 'test', startT: GO_S, endT: GO_S + testS },
    ],
    frames,
  });
}

/** The label a careful labeller gives each synthetic rep, at the middle of its highest point. */
export function kindOf(rep: RepSpec, reachPeak = 1): RepKind {
  if (rep.lowVisibility) return 'uncertain';
  if (rep.armsUncrossed) return 'arm-assisted';
  return (rep.peak ?? 1) < reachPeak ? 'half' : 'full';
}

export function labelEvents(reps: readonly RepSpec[]): LabelEvent[] {
  return reps.map((rep, i) => {
    const riseS = rep.riseS ?? 1;
    const holdS = rep.holdS ?? 0.5;
    return { id: `e${i + 1}`, type: kindOf(rep), t: GO_S + rep.startS + riseS + holdS / 2 };
  });
}

export function synthLabels(
  recording: Recording,
  events: readonly LabelEvent[],
  labeller: { code: string; isExternal: boolean },
): LabelsFile {
  const ref = recordingRef(recording);
  if (!ref.ok) throw new Error(ref.error);
  return {
    schema: LABELS_SCHEMA,
    recording: ref.ref,
    labeller,
    createdAt: '2026-10-24T10:00:00.000Z',
    notes: '',
    events: [...events],
  };
}

export const L1 = { code: 'L1', isExternal: false };
export const L2 = { code: 'L2', isExternal: true };

/** Evenly spaced reps every `everyS` seconds from `firstS`, with per-rep overrides. */
export function repTrain(count: number, firstS: number, everyS: number, overrides: Record<number, Partial<RepSpec>> = {}): RepSpec[] {
  return Array.from({ length: count }, (_, i) => ({ startS: firstS + i * everyS, ...overrides[i] }));
}

/** Named files as the CLI would read them from pilot-data/. */
export interface NamedText {
  readonly name: string;
  readonly text: string;
}

export function pilotFiles(recording: Recording, labelSets: readonly LabelsFile[]): NamedText[] {
  const ref = recordingRef(recording);
  if (!ref.ok) throw new Error(ref.error);
  const base = ref.ref.fileBase;
  return [
    { name: `${base}.json`, text: JSON.stringify(recording) },
    ...labelSets.map((l) => ({ name: `${base}_labels_${l.labeller.code}.json`, text: JSON.stringify(l) })),
  ];
}
