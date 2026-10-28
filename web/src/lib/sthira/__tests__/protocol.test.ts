import { describe, expect, test } from 'vitest';
import shippedParams from '../params/params.json';
import { readProtocolConstants } from '../params/protocol';

const entry = (value: number, cls = 'protocol-constant', unit = 's') => ({
  value,
  class: cls,
  unit,
  source: 'test',
  date: '2026-10-22',
});

const file = (params: Record<string, unknown>) => ({ version: '1', generatedAt: '2026-10-22', params });

describe('readProtocolConstants', () => {
  test('reads the shipped protocol constants even though no parameter is fitted yet', () => {
    const result = readProtocolConstants(shippedParams);
    expect(result.ok).toBe(true);
  });

  test('reports a missing constant by name', () => {
    const result = readProtocolConstants(file({ chairStandDurationS: entry(30), calibrationSitS: entry(5) }));
    expect(result).toEqual({ ok: false, errors: ['calibrationStandS: missing'] });
  });

  test('rejects a constant that is not declared as a protocol constant', () => {
    const result = readProtocolConstants(
      file({ chairStandDurationS: entry(30, 'fitted'), calibrationSitS: entry(5), calibrationStandS: entry(5) }),
    );
    expect(result.ok).toBe(false);
  });

  test('rejects a non-positive duration', () => {
    const result = readProtocolConstants(
      file({ chairStandDurationS: entry(0), calibrationSitS: entry(5), calibrationStandS: entry(5) }),
    );
    expect(result.ok).toBe(false);
  });

  test('rejects malformed input without throwing', () => {
    expect(readProtocolConstants({ nope: true }).ok).toBe(false);
  });
});
