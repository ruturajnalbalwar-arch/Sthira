import { bodyScalePx, headY, inFrame, keyVisibility } from './features';
import type { ChairStandParams } from './params/required';
import { mean, median, std } from './signal/stats';
import type { PoseFrame, VideoGeometry } from './types';

export interface Calibration {
  readonly seatedHeadY: number;
  readonly standingHeadY: number;
  /** Median per-frame body scale (see features.bodyScalePx). */
  readonly bodyScalePx: number;
}

export type CalibrationResult =
  | { readonly ok: true; readonly calib: Calibration }
  | { readonly ok: false; readonly reasons: readonly string[] };

const fail = (...reasons: string[]): CalibrationResult => ({ ok: false, reasons });

/** Keep frames with a person fully in view and strictly increasing, finite t. */
function usableFrames(frames: readonly PoseFrame[]): PoseFrame[] {
  const kept: PoseFrame[] = [];
  for (const frame of frames) {
    const last = kept[kept.length - 1];
    if (Number.isFinite(frame.t) && (last === undefined || frame.t > last.t) && inFrame(frame)) kept.push(frame);
  }
  return kept;
}

function heightReasons(
  seatedY: readonly number[],
  standingY: readonly number[],
  bodyScale: number,
  params: ChairStandParams,
): string[] {
  const range = median(seatedY) - median(standingY);
  if (!(range > 0)) return ['Stand fully upright, taller than your seated position'];
  const reasons: string[] = [];
  if (!(bodyScale > 0) || range / bodyScale < params.calibMinRangeToBodyScaleRatio) {
    reasons.push('Stand fully upright so we can see your full height change');
  }
  if (std(seatedY) / range > params.calibStillnessMaxRatio) reasons.push('Please sit still');
  if (std(standingY) / range > params.calibStillnessMaxRatio) reasons.push('Please stand still');
  return reasons;
}

/** Duration and frame-count gate on the usable frames; says whether the camera or the person is the problem. */
function postureReasons(
  raw: readonly PoseFrame[],
  kept: readonly PoseFrame[],
  durationS: number,
  label: string,
  params: ChairStandParams,
): string[] {
  const neededFrames = params.minFps * durationS;
  if (kept.length >= neededFrames) {
    const spanS = kept[kept.length - 1].t - kept[0].t;
    return spanS >= durationS ? [] : [`Please stay ${label} for the full countdown`];
  }
  return raw.length >= neededFrames
    ? [`We lost sight of you while ${label}: keep your whole body in view`]
    : ['The camera is too slow: close other apps and try again'];
}

/** Gate and compute the per-person calibration. Never guesses: failures list reasons. */
export function calibrate(
  sitFrames: readonly PoseFrame[],
  standFrames: readonly PoseFrame[],
  geometry: VideoGeometry,
  params: ChairStandParams,
): CalibrationResult {
  const sit = usableFrames(sitFrames);
  const stand = usableFrames(standFrames);
  if (sit.length === 0 || stand.length === 0) return fail('Move so your whole body is visible');

  const reasons: string[] = [
    ...postureReasons(sitFrames, sit, params.calibrationSitS, 'seated', params),
    ...postureReasons(standFrames, stand, params.calibrationStandS, 'standing', params),
  ];
  const all = [...sit, ...stand];
  if (mean(all.map(keyVisibility)) < params.visibilityMin) {
    reasons.push('Move to better light so your face, shoulders and hands are clearly visible');
  }
  const seatedY = sit.map((f) => headY(f, geometry));
  const standingY = stand.map((f) => headY(f, geometry));
  const bodyScale = median(all.map((f) => bodyScalePx(f, geometry)));
  reasons.push(...heightReasons(seatedY, standingY, bodyScale, params));
  if (reasons.length > 0) return { ok: false, reasons };

  return { ok: true, calib: { seatedHeadY: median(seatedY), standingHeadY: median(standingY), bodyScalePx: bodyScale } };
}
