import type { Calibration } from '../calibration';
import { armsCrossedRatio, bodyScalePx, headY, inFrame, isUsableFrame, keyVisibility } from '../features';
import type { ProtocolConstants } from '../params/protocol';
import { coveringFrames, findPhases } from '../replay';
import { effectiveFps, mean, median, std } from '../signal/stats';
import type { PoseFrame } from '../types';
import type { Trial } from './data';

/**
 * What one tuning trial says about the parameter ranges. Every statistic mirrors the engine's own
 * computation (calibration.ts, features.ts) on the same frames replay feeds it, so a gate derived
 * from these values is exactly the gate the engine applies.
 */
export interface TrialMeasure {
  readonly calib: Calibration;
  /** max(std(seated head y), std(standing head y)) / calibrated range: calibration.ts stillness check. */
  readonly stillness: number;
  /** Calibrated range / body scale: calibration.ts range check. */
  readonly rangeRatio: number;
  /** Mean key-landmark visibility over the calibration frames: calibration.ts visibility check. */
  readonly calibVisibility: number;
  /** Largest minFps this trial passes: calibration frame counts per protocol second, and test-window fps. */
  readonly frameRate: number;
  /** Longest gap between consecutive frames, calibration start to window end. */
  readonly maxGapS: number;
  /** Raw head progress of usable test-window frames strictly inside the calibrated range (0, 1). */
  readonly progress: readonly number[];
  readonly armRatios: readonly number[];
  readonly visibilities: readonly number[];
  /** Highest head progress while seated still (frames strictly inside calibration-sit): seated noise. */
  readonly seatedNoise: number;
  /** Lowest head progress at a labelled full stand (frame nearest the label), or null without one. */
  readonly fullPeakMin: number | null;
  /** Seconds between consecutive labelled attempts. */
  readonly attemptIntervals: readonly number[];
}

export type MeasureResult = { readonly ok: true; readonly measure: TrialMeasure } | { readonly ok: false; readonly reason: string };

const nearestFrame = (frames: readonly PoseFrame[], t: number): PoseFrame =>
  frames.reduce((best, f) => (Math.abs(f.t - t) < Math.abs(best.t - t) ? f : best));
const gaps = (frames: readonly PoseFrame[]): number[] => frames.slice(1).map((f, i) => f.t - frames[i].t);
const spanS = (frames: readonly PoseFrame[]): number => (frames.length > 0 ? frames[frames.length - 1].t - frames[0].t : 0);

function windowSeries(frames: readonly PoseFrame[], calib: Calibration, geometry: { width: number; height: number }) {
  const usable = frames.filter(isUsableFrame);
  const range = calib.seatedHeadY - calib.standingHeadY;
  return {
    progress: usable.map((f) => (calib.seatedHeadY - headY(f, geometry)) / range).filter((p) => p > 0 && p < 1),
    armRatios: usable.map((f) => armsCrossedRatio(f, geometry)),
    visibilities: usable.map(keyVisibility),
  };
}

export function measureTrial(trial: Trial, protocol: ProtocolConstants): MeasureResult {
  const fail = (reason: string): MeasureResult => ({ ok: false, reason });
  const phases = findPhases(trial.recording);
  if (!phases.ok) return fail(phases.reasons.join('; '));
  const { sit, stand, test } = phases;
  const frames: readonly PoseFrame[] = trial.recording.frames;
  const geometry = { width: trial.recording.capture.videoWidth, height: trial.recording.capture.videoHeight };
  const sitFrames = coveringFrames(frames, sit.startT, sit.endT).filter(inFrame);
  const standFrames = coveringFrames(frames, stand.startT, stand.endT).filter(inFrame);
  if (spanS(sitFrames) < protocol.calibrationSitS || spanS(standFrames) < protocol.calibrationStandS) {
    return fail('calibration frames do not cover the protocol calibration time');
  }
  const seatedY = sitFrames.map((f) => headY(f, geometry));
  const standingY = standFrames.map((f) => headY(f, geometry));
  const range = median(seatedY) - median(standingY);
  if (!(range > 0)) return fail('standing head is not above seated head in calibration');
  const all = [...sitFrames, ...standFrames];
  const calib: Calibration = { seatedHeadY: median(seatedY), standingHeadY: median(standingY), bodyScalePx: median(all.map((f) => bodyScalePx(f, geometry))) };

  const windowEnd = test.startT + protocol.chairStandDurationS;
  const testFrames = frames.filter((f) => f.t >= test.startT && f.t <= windowEnd);
  const covered = frames.filter((f) => f.t >= sitFrames[0].t && f.t <= windowEnd);
  const marks = trial.marks.map((m) => m.t);
  const progressOf = (f: PoseFrame): number => (calib.seatedHeadY - headY(f, geometry)) / range;
  const seatedStill = sitFrames.filter((f) => f.t > sit.startT && f.t < sit.endT);
  const fullPeaks = trial.marks.filter((m) => m.kind === 'full').map((m) => progressOf(nearestFrame(testFrames, m.t)));
  return {
    ok: true,
    measure: {
      calib,
      stillness: Math.max(std(seatedY) / range, std(standingY) / range),
      rangeRatio: range / calib.bodyScalePx,
      calibVisibility: mean(all.map(keyVisibility)),
      frameRate: Math.min(sitFrames.length / protocol.calibrationSitS, standFrames.length / protocol.calibrationStandS, effectiveFps(testFrames)),
      maxGapS: Math.max(...gaps(covered)),
      seatedNoise: Math.max(...seatedStill.map(progressOf)),
      fullPeakMin: fullPeaks.length > 0 ? Math.min(...fullPeaks) : null,
      attemptIntervals: marks.slice(1).map((t, i) => t - marks[i]),
      ...windowSeries(testFrames, calib, geometry),
    },
  };
}
