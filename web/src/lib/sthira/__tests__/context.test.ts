import { describe, expect, it } from 'vitest';
import { makeContext } from '../context';
import { createCounter } from '../counter';
import { initialState, step, summarize } from '../chairStand';
import shippedParams from '../params/params.json';
import { buildSession, CALIB, GEOMETRY, paramsFile, TEST_PARAMS } from './fixtures';

const errorsOf = (result: ReturnType<typeof makeContext>) => (result.ok ? [] : result.errors);

describe('makeContext', () => {
  it('accepts valid geometry, calibration and params', () => {
    expect(makeContext(GEOMETRY, CALIB, TEST_PARAMS).ok).toBe(true);
  });

  it.each([
    [{ width: 0, height: 480 }, CALIB, /geometry/],
    [{ width: 640, height: Number.NaN }, CALIB, /geometry/],
    [GEOMETRY, { ...CALIB, seatedHeadY: Number.NaN }, /finite/],
    [GEOMETRY, { ...CALIB, seatedHeadY: CALIB.standingHeadY }, /range > 0/],
    [GEOMETRY, { ...CALIB, bodyScalePx: 0 }, /body scale/],
  ])('rejects bad input %#', (geometry, calib, pattern) => {
    expect(errorsOf(makeContext(geometry, calib, TEST_PARAMS)).join(' ')).toMatch(pattern);
  });

  it('applies the loader ordering rules to hand-built params', () => {
    const result = makeContext(GEOMETRY, CALIB, { ...TEST_PARAMS, leaveSeatFraction: 0.9 });
    expect(errorsOf(result)).toContain('params: leaveSeatFraction must be below reachFraction');
  });

  it('rejects non-finite params by name', () => {
    const result = makeContext(GEOMETRY, CALIB, { ...TEST_PARAMS, maxGapS: Number.POSITIVE_INFINITY });
    expect(errorsOf(result).join(' ')).toMatch(/maxGapS/);
  });
});

describe('createCounter', () => {
  it('refuses to run on the shipped (untuned) params', () => {
    const counter = createCounter(shippedParams);
    expect(counter.status).toBe('untuned');
    expect('makeContext' in counter).toBe(false);
  });

  it('reports invalid params files', () => {
    expect(createCounter({ nope: true }).status).toBe('invalid');
  });

  it('builds a working context from a tuned params file', () => {
    const counter = createCounter(paramsFile());
    if (counter.status !== 'ready') throw new Error(`expected ready, got ${counter.status}`);
    const made = counter.makeContext(GEOMETRY, CALIB);
    if (!made.ok) throw new Error(made.errors.join('; '));
    const state = buildSession(30, 10, [{ startS: 2 }]).reduce((s, f) => step(s, f, made.ctx), initialState());
    expect(summarize(state, made.ctx).full).toBe(1);
  });
});
