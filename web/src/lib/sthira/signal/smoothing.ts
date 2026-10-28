import { TAU } from '../mathConstants';

export interface TimedValue {
  readonly t: number;
  readonly v: number;
}

/** One exponential low-pass step; alpha = 1 - exp(-dt/tau) is exact for irregular dt. */
export function smoothStep(previous: number, value: number, dtS: number, cutoffHz: number): number {
  if (!(cutoffHz > 0)) throw new RangeError('cutoffHz must be > 0');
  if (!(dtS > 0)) return previous;
  const tau = 1 / (TAU * cutoffHz);
  const alpha = 1 - Math.exp(-dtS / tau);
  return previous + alpha * (value - previous);
}

/** Time-based low-pass over a series. Returns a new array; input is untouched. */
export function smoothSeries(points: readonly TimedValue[], cutoffHz: number): TimedValue[] {
  if (!(cutoffHz > 0)) throw new RangeError('cutoffHz must be > 0');
  const out: TimedValue[] = [];
  for (const point of points) {
    const last = out[out.length - 1];
    const v = last === undefined ? point.v : smoothStep(last.v, point.v, point.t - last.t, cutoffHz);
    out.push({ t: point.t, v });
  }
  return out;
}
