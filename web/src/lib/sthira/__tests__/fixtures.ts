import type { Calibration } from '../calibration';
import { makeContext, type StepContext } from '../context';
import { PARAM_NAMES, REQUIRED_PARAMS, type ChairStandParams, type ParamName } from '../params/required';
import type { Landmark, PoseFrame, VideoGeometry } from '../types';

export const GEOMETRY: VideoGeometry = { width: 640, height: 480 };
export const SEATED_Y = 300;
export const STANDING_Y = 150;
export const SHOULDER_WIDTH = 100;
const CENTER_X = 320;
const NOSE_ABOVE_HEAD = 20;
const SHOULDER_BELOW_HEAD = 20;
const WRIST_DROP = 150;
const WRIST_SPREAD = 120;
/** Shoulder midpoint to hip midpoint; larger than SHOULDER_WIDTH, so it is the body scale. */
const TORSO = 120;
export const BODY_SCALE = Math.max(SHOULDER_WIDTH, TORSO);

export const TEST_PARAMS: ChairStandParams = {
  chairStandDurationS: 30,
  calibrationSitS: 5,
  calibrationStandS: 5,
  leaveSeatFraction: 0.15,
  reachFraction: 0.85,
  seatFraction: 0.1,
  minStandDwellS: 0.2,
  minSitDwellS: 0.2,
  smoothingCutoffHz: 3,
  armsCrossedMaxRatio: 0.8,
  armsViolationMinS: 0.3,
  visibilityMin: 0.5,
  uncertainFrameMaxFraction: 0.2,
  calibStillnessMaxRatio: 0.05,
  calibMinRangeToBodyScaleRatio: 0.5,
  minFps: 8,
  timeUpCountFraction: 0.5,
  unreliableUncertainFraction: 0.5,
  maxGapS: 1,
};

export const CALIB: Calibration = {
  seatedHeadY: SEATED_Y,
  standingHeadY: STANDING_Y,
  bodyScalePx: BODY_SCALE,
};

/** A validated StepContext for the synthetic fixtures (throws if the fixtures are inconsistent). */
export function makeCtx(params: ChairStandParams = TEST_PARAMS): StepContext {
  const result = makeContext(GEOMETRY, CALIB, params);
  if (!result.ok) throw new Error(result.errors.join('; '));
  return result.ctx;
}

/** A complete, valid params.json built from TEST_PARAMS. */
export const paramEntry = (name: ParamName, value: number) => ({
  value,
  class: REQUIRED_PARAMS[name].class,
  unit: REQUIRED_PARAMS[name].unit,
  source: 'test fixture',
  script: 'fit-params.test',
  date: '2026-10-22',
});

export const paramsFile = (overrides: Record<string, unknown> = {}) => ({
  version: '1',
  generatedAt: '2026-10-22T00:00:00Z',
  params: {
    ...Object.fromEntries(PARAM_NAMES.map((n) => [n, paramEntry(n, TEST_PARAMS[n])])),
    ...overrides,
  },
});

/** Synthetic calibration captures run at 10 fps; this many frames span exactly 5 s. */
export const CALIB_FPS = 10;
export const CALIB_FRAMES = 51;

export interface FrameOptions {
  readonly armsUncrossed?: boolean;
  readonly visibility?: number;
  readonly offsetX?: number;
  /** Multiplies every body dimension, e.g. leaning towards the camera. */
  readonly scale?: number;
}

const filler: Landmark = { x: 0.5, y: 0.5, z: 0, visibility: 1 };

/** Build a 33-landmark frame whose head proxy sits at headYPx pixels. */
export function makeFrame(t: number, headYPx: number, options: FrameOptions = {}): PoseFrame {
  const { armsUncrossed = false, visibility = 1, offsetX = 0, scale = 1 } = options;
  const { width, height } = GEOMETRY;
  const cx = CENTER_X + offsetX;
  const halfShoulder = (SHOULDER_WIDTH / 2) * scale;
  const shoulderY = headYPx + SHOULDER_BELOW_HEAD * scale;
  const wristY = armsUncrossed ? shoulderY + WRIST_DROP * scale : shoulderY;
  const spread = armsUncrossed ? WRIST_SPREAD * scale : halfShoulder;
  const hipY = shoulderY + TORSO * scale;
  const at = (x: number, y: number): Landmark => ({ x: x / width, y: y / height, z: 0, visibility });
  const landmarks = Array.from({ length: 33 }, () => filler);
  const place = (index: number, lm: Landmark): void => {
    landmarks[index] = lm;
  };
  place(0, at(cx, headYPx - NOSE_ABOVE_HEAD * scale));
  place(11, at(cx - halfShoulder, shoulderY));
  place(12, at(cx + halfShoulder, shoulderY));
  // Crossed: each wrist rests on the opposite shoulder.
  place(15, at(cx + spread, wristY));
  place(16, at(cx - spread, wristY));
  place(23, at(cx - halfShoulder, hipY));
  place(24, at(cx + halfShoulder, hipY));
  return { t, landmarks };
}

export interface RepSpec {
  readonly startS: number;
  /** Fraction of the calibrated range reached (1 = fully upright). */
  readonly peak?: number;
  readonly riseS?: number;
  readonly holdS?: number;
  readonly fallS?: number;
  readonly armsUncrossed?: boolean;
  readonly lowVisibility?: boolean;
}

const DEFAULTS = { peak: 1, riseS: 1, holdS: 0.5, fallS: 1 };

function progressAt(t: number, rep: RepSpec): number {
  const { peak, riseS, holdS, fallS } = { ...DEFAULTS, ...rep };
  const local = t - rep.startS;
  if (local < 0 || local > riseS + holdS + fallS) return 0;
  if (local < riseS) return peak * (local / riseS);
  if (local < riseS + holdS) return peak;
  return peak * (1 - (local - riseS - holdS) / fallS);
}

const repEnd = (rep: RepSpec): number => {
  const { riseS, holdS, fallS } = { ...DEFAULTS, ...rep };
  return rep.startS + riseS + holdS + fallS;
};

/** Deterministic sub-pixel jitter that depends only on time, so it is identical at any fps. */
const jitter = (t: number): number => Math.sin(t * 37) * 0.4;

export function buildSession(fps: number, durationS: number, reps: readonly RepSpec[]): PoseFrame[] {
  const count = Math.floor(durationS * fps) + 1;
  return Array.from({ length: count }, (_, i) => {
    const t = i / fps;
    const p = reps.reduce((best, rep) => Math.max(best, progressAt(t, rep)), 0);
    const active = reps.find((rep) => t >= rep.startS && t <= repEnd(rep));
    const y = SEATED_Y - p * (SEATED_Y - STANDING_Y) + jitter(t);
    return makeFrame(t, y, {
      armsUncrossed: active?.armsUncrossed ?? false,
      visibility: active?.lowVisibility ? 0.1 : 1,
    });
  });
}

/** Static posture capture for calibration tests. */
export function buildStatic(headYPx: number, frames: number, options: FrameOptions = {}): PoseFrame[] {
  return Array.from({ length: frames }, (_, i) => makeFrame(i / CALIB_FPS, headYPx, options));
}
