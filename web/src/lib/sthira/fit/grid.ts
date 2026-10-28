import { HALF } from '../mathConstants';

/** Small, exact grid helpers for the fitter. */

/** Sorted, de-duplicated copy. */
export const uniqueSorted = (values: readonly number[]): number[] => [...new Set(values)].sort((a, b) => a - b);

/** Linear-interpolation quantile (Hyndman and Fan type 7, the R and NumPy default) of sorted values. */
function quantileSorted(sorted: readonly number[], p: number): number {
  const h = (sorted.length - 1) * p;
  const lo = Math.floor(h);
  const hi = Math.min(lo + 1, sorted.length - 1);
  return sorted[lo] + (h - lo) * (sorted[hi] - sorted[lo]);
}

/** `points` evenly spaced probabilities from 0 to 1 inclusive. */
const probabilities = (points: number): number[] => Array.from({ length: points }, (_, k) => k / (points - 1));

/** Quantiles of the data at `points` evenly spaced probabilities (min and max included), de-duplicated. */
export function quantileGrid(values: readonly number[], points: number): number[] {
  if (values.length === 0) return [];
  const sorted = [...values].sort((a, b) => a - b);
  return uniqueSorted(probabilities(points).map((p) => quantileSorted(sorted, p)));
}

/**
 * Threshold candidates: the quantile grid plus the midpoint between each pair of neighbours, so a
 * gap between two clusters of observed values (e.g. arms crossed vs not) always has a candidate.
 */
export function thresholdGrid(values: readonly number[], points: number): number[] {
  const q = quantileGrid(values, points);
  return uniqueSorted([...q, ...q.slice(1).map((v, i) => midpointOf(q[i], v))]);
}

const midpointOf = (a: number, b: number): number => a + (b - a) * HALF;

/** `points` evenly spaced values from lo to hi inclusive. */
export const linearGrid = (lo: number, hi: number, points: number): number[] =>
  uniqueSorted(probabilities(points).map((p) => lo + (hi - lo) * p));

/** `points` evenly spaced fractions in [0, 1): k / points for k = 0 .. points - 1. */
export const fractionGrid = (points: number): number[] => Array.from({ length: points }, (_, k) => k / points);
