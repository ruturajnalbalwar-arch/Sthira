import { HALF } from '../mathConstants';
import type { ParamName } from '../params/required';
import { fractionGrid, linearGrid, thresholdGrid } from './grid';
import type { TrialMeasure } from './measure';

/**
 * The search space, derived only from the tuning trials. Calibration and frame-timing gates are
 * set directly from the data (the tightest value every tuning trial passes); the detector
 * parameters are searched over grids whose ends come from the data. `gridPoints` is the only
 * methodological number, and the caller supplies it.
 */
export const DERIVED = ['minFps', 'maxGapS', 'calibStillnessMaxRatio', 'calibMinRangeToBodyScaleRatio'] as const;
/** Search order: thresholds that decide whether a rep exists come before those that classify it. */
export const SEARCHED = [
  'reachFraction',
  'leaveSeatFraction',
  'seatFraction',
  'minStandDwellS',
  'minSitDwellS',
  'smoothingCutoffHz',
  'timeUpCountFraction',
  'armsCrossedMaxRatio',
  'armsViolationMinS',
  'visibilityMin',
  'uncertainFrameMaxFraction',
  'unreliableUncertainFraction',
] as const satisfies readonly ParamName[];

export type DerivedName = (typeof DERIVED)[number];
export type SearchedName = (typeof SEARCHED)[number];

/** How each fitted parameter is obtained; written into params.json `source` and docs/pilot/fitting.md. */
export const PARAM_ORIGIN: Readonly<Record<DerivedName | SearchedName, string>> = {
  minFps: 'set to the lowest frame rate at which every tuning trial passes the calibration frame-count and test fps checks',
  maxGapS: 'set to the longest inter-frame gap seen in any clean tuning trial, so no normal frame counts as a stall',
  calibStillnessMaxRatio: 'set to the largest calibration stillness ratio among tuning trials (tightest gate all pass)',
  calibMinRangeToBodyScaleRatio: 'set to the smallest calibration range/body-scale ratio among tuning trials (tightest gate all pass)',
  reachFraction: 'searched over quantiles (and midpoints between them) of observed head progress (0..1 of each calibrated range)',
  leaveSeatFraction: 'searched over quantiles (and midpoints between them) of observed head progress',
  seatFraction: 'searched over quantiles (and midpoints between them) of observed head progress',
  timeUpCountFraction: 'searched over quantiles (and midpoints between them) of observed head progress (at least seatFraction)',
  minStandDwellS: 'searched over an even grid from 0 to the shortest interval between labelled attempts',
  minSitDwellS: 'searched over an even grid from 0 to the shortest interval between labelled attempts',
  armsViolationMinS: 'searched over an even grid from 0 to the shortest interval between labelled attempts',
  smoothingCutoffHz: 'searched over an even grid from the fastest labelled attempt rate to the Nyquist frequency of minFps',
  armsCrossedMaxRatio: 'searched over quantiles (and midpoints between them) of observed wrist-to-opposite-shoulder / body-scale ratios',
  visibilityMin: 'searched over quantiles (and midpoints between them) of observed key-landmark visibility, capped so every tuning calibration passes',
  uncertainFrameMaxFraction: 'searched over an even grid on [0, 1)',
  unreliableUncertainFraction: 'searched over an even grid on [0, 1)',
};

export interface SearchSpace {
  readonly derived: Readonly<Record<DerivedName, number>>;
  readonly candidates: Readonly<Record<SearchedName, readonly number[]>>;
  readonly init: Readonly<Record<SearchedName, number>>;
}

export type SpaceResult = { readonly ok: true; readonly space: SearchSpace } | { readonly ok: false; readonly errors: readonly string[] };

const pool = (measures: readonly TrialMeasure[], pick: (m: TrialMeasure) => readonly number[]): number[] => measures.flatMap(pick);
const minOf = (values: readonly number[]): number => Math.min(...values);
const maxOf = (values: readonly number[]): number => Math.max(...values);

function deriveGates(measures: readonly TrialMeasure[]): Record<DerivedName, number> {
  return {
    minFps: minOf(measures.map((m) => m.frameRate)),
    maxGapS: maxOf(measures.map((m) => m.maxGapS)),
    calibStillnessMaxRatio: maxOf(measures.map((m) => m.stillness)),
    calibMinRangeToBodyScaleRatio: minOf(measures.map((m) => m.rangeRatio)),
  };
}

function buildCandidates(measures: readonly TrialMeasure[], derived: Record<DerivedName, number>, points: number, shortestS: number) {
  const progress = thresholdGrid(pool(measures, (m) => m.progress), points);
  const timeGrid = linearGrid(0, shortestS, points);
  const visibilityCap = minOf(measures.map((m) => m.calibVisibility));
  return {
    reachFraction: progress,
    leaveSeatFraction: progress,
    seatFraction: progress,
    timeUpCountFraction: progress,
    minStandDwellS: timeGrid,
    minSitDwellS: timeGrid,
    armsViolationMinS: timeGrid,
    smoothingCutoffHz: linearGrid(1 / shortestS, derived.minFps * HALF, points),
    armsCrossedMaxRatio: thresholdGrid(pool(measures, (m) => m.armRatios), points),
    visibilityMin: thresholdGrid(pool(measures, (m) => m.visibilities), points).filter((v) => v <= visibilityCap),
    uncertainFrameMaxFraction: fractionGrid(points),
    unreliableUncertainFraction: fractionGrid(points),
  } satisfies Record<SearchedName, number[]>;
}

const first = (values: readonly number[]): number => values[0];
const last = (values: readonly number[]): number => values[values.length - 1];

/**
 * Start from the least restrictive detector and let the search add restrictions only when they
 * lower the loss: no dwell, least smoothing, no arm or visibility rejection, no abstaining. The
 * hysteresis band is anchored to the data: leave and return to the seat just above the seated
 * noise seen in calibration, and reach the lowest labelled full-stand peak.
 */
function initialValues(measures: readonly TrialMeasure[], c: Record<SearchedName, readonly number[]>): Record<SearchedName, number> {
  const noise = maxOf(measures.map((m) => m.seatedNoise));
  const peaks = measures.flatMap((m) => (m.fullPeakMin === null ? [] : [m.fullPeakMin]));
  const seat = c.seatFraction.find((v) => v > noise) ?? first(c.seatFraction);
  const belowPeaks = c.reachFraction.filter((v) => v > seat && (peaks.length === 0 || v <= minOf(peaks)));
  const reach = belowPeaks.length > 0 ? last(belowPeaks) : last(c.reachFraction);
  return {
    seatFraction: seat,
    leaveSeatFraction: seat,
    reachFraction: reach,
    timeUpCountFraction: reach,
    minStandDwellS: first(c.minStandDwellS),
    minSitDwellS: first(c.minSitDwellS),
    armsViolationMinS: first(c.armsViolationMinS),
    smoothingCutoffHz: last(c.smoothingCutoffHz),
    armsCrossedMaxRatio: last(c.armsCrossedMaxRatio),
    visibilityMin: first(c.visibilityMin),
    uncertainFrameMaxFraction: last(c.uncertainFrameMaxFraction),
    unreliableUncertainFraction: last(c.unreliableUncertainFraction),
  };
}

function spaceErrors(derived: Record<DerivedName, number>, candidates: Record<SearchedName, readonly number[]>, shortestS: number): string[] {
  const errors: string[] = [];
  if (!(derived.maxGapS * derived.minFps > 1)) {
    errors.push('frame timing is perfectly regular, so maxGapS cannot exceed one frame at minFps; check the frame clock');
  }
  if (!(1 / shortestS < derived.minFps * HALF)) {
    errors.push('the fastest labelled attempt rate is above the Nyquist frequency of the slowest trial: frame rate too low');
  }
  const thin = SEARCHED.filter((name) => candidates[name].length === 0);
  if (thin.length > 0) errors.push(`no data-derived candidates for ${thin.join(', ')}`);
  if (candidates.reachFraction.length === 1) {
    errors.push('head progress takes fewer than two distinct values: nobody moved between the calibrated postures');
  }
  return errors;
}

export function buildSpace(measures: readonly TrialMeasure[], points: number): SpaceResult {
  if (measures.length === 0) return { ok: false, errors: ['no tuning trials'] };
  const intervals = pool(measures, (m) => m.attemptIntervals).filter((s) => s > 0);
  if (intervals.length === 0) return { ok: false, errors: ['no trial has two labelled attempts, so no time scale can be derived'] };
  const shortestS = minOf(intervals);
  const derived = deriveGates(measures);
  const candidates = buildCandidates(measures, derived, points, shortestS);
  const errors = spaceErrors(derived, candidates, shortestS);
  if (errors.length > 0) return { ok: false, errors };
  return { ok: true, space: { derived, candidates, init: initialValues(measures, candidates) } };
}
