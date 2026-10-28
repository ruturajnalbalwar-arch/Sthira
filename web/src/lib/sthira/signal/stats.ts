import { HALF, midpoint } from '../mathConstants';

export function mean(values: readonly number[]): number {
  if (values.length === 0) return Number.NaN;
  return values.reduce((sum, v) => sum + v, 0) / values.length;
}

export function median(values: readonly number[]): number {
  if (values.length === 0) return Number.NaN;
  const sorted = [...values].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length * HALF);
  const isEven = Number.isInteger(sorted.length * HALF);
  return isEven ? midpoint(sorted[mid - 1], sorted[mid]) : sorted[mid];
}

/** Population standard deviation. */
export function std(values: readonly number[]): number {
  const m = mean(values);
  return Math.sqrt(mean(values.map((v) => (v - m) * (v - m))));
}

/** Frames per second from a frame count and the time they span. */
export function rateFromCount(count: number, spanS: number): number {
  return count > 1 && spanS > 0 ? (count - 1) / spanS : 0;
}

/** Effective fps over frames with finite, strictly increasing t (others are ignored). */
export function effectiveFps(frames: readonly { readonly t: number }[]): number {
  let count = 0;
  let first = Number.NaN;
  let last = Number.NEGATIVE_INFINITY;
  for (const frame of frames) {
    if (!Number.isFinite(frame.t) || frame.t <= last) continue;
    if (count === 0) first = frame.t;
    count += 1;
    last = frame.t;
  }
  return rateFromCount(count, last - first);
}
