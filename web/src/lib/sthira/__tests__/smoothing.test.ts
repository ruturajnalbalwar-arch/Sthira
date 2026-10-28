import { describe, expect, it } from 'vitest';
import { smoothSeries, smoothStep } from '../signal/smoothing';
import { mean, median, std } from '../signal/stats';

const CUTOFF_HZ = 2;
const TAU = 1 / (Math.PI * 2 * CUTOFF_HZ);

const stepInput = (fps: number, durationS: number) =>
  Array.from({ length: Math.round(durationS * fps) + 1 }, (_, i) => ({ t: i / fps, v: i === 0 ? 0 : 1 }));

describe('smoothSeries', () => {
  it.each([10, 15, 30])('matches the analytic step response at %i fps', (fps) => {
    const out = smoothSeries(stepInput(fps, 1), CUTOFF_HZ);
    const last = out[out.length - 1];
    expect(last.v).toBeCloseTo(1 - Math.exp(-last.t / TAU), 9);
  });

  it('gives the same value at the same time regardless of frame rate', () => {
    const at = (fps: number) => smoothSeries(stepInput(fps, 1), CUTOFF_HZ).at(-1)?.v ?? Number.NaN;
    expect(at(10)).toBeCloseTo(at(30), 9);
    expect(at(15)).toBeCloseTo(at(30), 9);
  });

  it('handles irregular time steps', () => {
    const out = smoothSeries([{ t: 0, v: 0 }, { t: 0.05, v: 1 }, { t: 0.3, v: 1 }], CUTOFF_HZ);
    const first = 1 - Math.exp(-0.05 / TAU);
    const second = first + (1 - Math.exp(-0.25 / TAU)) * (1 - first);
    expect(out[2].v).toBeCloseTo(second, 9);
  });

  it('returns a new array and leaves the input untouched', () => {
    const input = [{ t: 0, v: 5 }, { t: 1, v: 9 }];
    const out = smoothSeries(input, CUTOFF_HZ);
    expect(out).not.toBe(input);
    expect(input[1].v).toBe(9);
  });

  it('returns an empty array for empty input and holds value on non-positive dt', () => {
    expect(smoothSeries([], CUTOFF_HZ)).toEqual([]);
    expect(smoothStep(3, 9, 0, CUTOFF_HZ)).toBe(3);
  });

  it('rejects a non-positive cutoff', () => {
    expect(() => smoothSeries([{ t: 0, v: 1 }], 0)).toThrow(RangeError);
    expect(() => smoothStep(0, 1, 1, -1)).toThrow(RangeError);
  });
});

describe('stats', () => {
  it('computes mean, median and population std', () => {
    expect(mean([1, 2, 3, 4])).toBe(2.5);
    expect(median([5, 1, 3])).toBe(3);
    expect(median([4, 1, 3, 2])).toBe(2.5);
    expect(std([2, 4, 4, 4, 5, 5, 7, 9])).toBe(2);
  });

  it('returns NaN for empty input', () => {
    expect(mean([])).toBeNaN();
    expect(median([])).toBeNaN();
  });
});
