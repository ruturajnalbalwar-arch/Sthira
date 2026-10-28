import { describe, expect, it } from 'vitest';
import shippedParams from '../params/params.json';
import { loadParams } from '../params/loader';
import { paramEntry as entry, paramsFile as fullFile } from './fixtures';

describe('loadParams', () => {
  it('returns untuned for the shipped params.json and lists every fitted name', () => {
    const result = loadParams(shippedParams);
    expect(result.status).toBe('untuned');
    if (result.status === 'untuned') {
      expect(result.missing).toContain('leaveSeatFraction');
      expect(result.missing).toContain('maxGapS');
      expect(result.missing).not.toContain('chairStandDurationS');
    }
  });

  it('ships only protocol constants, with the CDC source on the 30 s duration', () => {
    const entries = Object.values(shippedParams.params);
    expect(entries.every((e) => e.class === 'protocol-constant')).toBe(true);
    expect(shippedParams.params.chairStandDurationS.source).toMatch(/CDC STEADI/);
  });

  it('returns ready with a flat params object when everything is present', () => {
    const result = loadParams(fullFile());
    expect(result.status).toBe('ready');
    if (result.status === 'ready') expect(result.params.reachFraction).toBe(0.85);
  });

  it.each([null, undefined, 42, 'text', [], {}, { version: '1' }])('returns invalid for malformed input %j', (input) => {
    expect(loadParams(input).status).toBe('invalid');
  });

  it('returns invalid when a unit does not match', () => {
    const bad = { ...entry('minStandDwellS', 0.2), unit: 'frames' };
    expect(loadParams(fullFile({ minStandDwellS: bad })).status).toBe('invalid');
  });

  it('returns invalid when a class does not match', () => {
    const bad = { ...entry('chairStandDurationS', 30), class: 'fitted' };
    expect(loadParams(fullFile({ chairStandDurationS: bad })).status).toBe('invalid');
  });

  it('returns invalid for negative values, fractions above 1 and fitted values without a script', () => {
    expect(loadParams(fullFile({ minFps: entry('minFps', -1) })).status).toBe('invalid');
    expect(loadParams(fullFile({ visibilityMin: entry('visibilityMin', 1.5) })).status).toBe('invalid');
    expect(loadParams(fullFile({ minFps: { ...entry('minFps', 8), script: undefined } })).status).toBe('invalid');
  });

  it.each([
    ['leaveSeatFraction', 0.9, 'leaveSeatFraction must be below reachFraction'],
    ['seatFraction', 0.9, 'seatFraction must be below reachFraction'],
    ['seatFraction', 0.2, 'seatFraction must be <= leaveSeatFraction'],
    ['uncertainFrameMaxFraction', 1, 'uncertainFrameMaxFraction must be < 1'],
    ['unreliableUncertainFraction', 1, 'unreliableUncertainFraction must be < 1'],
    ['timeUpCountFraction', 0.05, 'timeUpCountFraction must be between seatFraction and 1'],
    ['smoothingCutoffHz', 0, 'smoothingCutoffHz must be > 0'],
    ['maxGapS', 0, 'maxGapS must be > 0'],
    ['minFps', 0, 'minFps must be > 0'],
    ['maxGapS', 0.1, 'maxGapS must be longer than one frame at minFps'],
  ] as const)('rejects inconsistent %s = %d', (name, value, message) => {
    const result = loadParams(fullFile({ [name]: entry(name, value) }));
    expect(result.status).toBe('invalid');
    if (result.status === 'invalid') expect(result.errors).toContain(message);
  });

  it('ignores unknown extra entries', () => {
    const extra = { value: -5, class: 'cited-reference', unit: 'x', source: 's', date: 'd' };
    expect(loadParams(fullFile({ somethingElse: extra })).status).toBe('ready');
  });
});
