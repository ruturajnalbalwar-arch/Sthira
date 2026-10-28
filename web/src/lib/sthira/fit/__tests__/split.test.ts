import { describe, expect, it } from 'vitest';
import type { Person } from '../data';
import { initSplit, parseSplit, partition, splitHash, type SplitRules } from '../split';

const RULES: SplitRules = { heldOutCount: 4, minLooseHeldOut: 2, looseClothing: ['saree', 'dhoti'], minTuning: 2 };
const NOW = '2026-10-24T09:00:00.000Z';

const person = (code: string, ...clothing: Person['clothing'][number][]): Person => ({ code, clothing });
const PEOPLE: Person[] = [
  person('P01', 'track-pants'),
  person('P02', 'saree', 'track-pants'),
  person('P03', 'dhoti', 'track-pants'),
  person('P04', 'saree', 'track-pants'),
  person('P05', 'track-pants'),
  person('P06', 'lungi', 'track-pants'),
  person('P07', 'track-pants'),
];

describe('initSplit', () => {
  it('holds out the demo performer plus enough people in a saree or dhoti', () => {
    const result = initSplit(PEOPLE, 'P01', RULES, 'seed-a', NOW);
    if (!result.ok) throw new Error(result.problems.join('; '));
    const { heldOut, tuning } = result.split;
    expect(heldOut).toHaveLength(4);
    expect(heldOut).toContain('P01');
    const loose = heldOut.filter((c) => ['P02', 'P03', 'P04'].includes(c));
    expect(loose.length).toBeGreaterThanOrEqual(2);
    expect([...heldOut, ...tuning].sort()).toEqual(PEOPLE.map((p) => p.code));
  });

  it('is deterministic for a seed', () => {
    expect(initSplit(PEOPLE, 'P01', RULES, 'seed-a', NOW)).toEqual(initSplit(PEOPLE, 'P01', RULES, 'seed-a', NOW));
  });

  it('counts a demo performer in a saree towards the loose-clothing quota', () => {
    const result = initSplit(PEOPLE, 'P02', RULES, 'seed-a', NOW);
    expect(result.ok && result.split.heldOut.filter((c) => ['P02', 'P03', 'P04'].includes(c)).length).toBeGreaterThanOrEqual(2);
  });

  it('refuses when there are not enough people, loose clothing or the demo performer', () => {
    const result = initSplit([person('P01', 'track-pants'), person('P02', 'track-pants')], 'P09', RULES, 's', NOW);
    expect(result.ok).toBe(false);
    const text = !result.ok ? result.problems.join('\n') : '';
    expect(text).toMatch(/2 participants recorded; the split needs at least 6/);
    expect(text).toMatch(/demo performer P09/);
    expect(text).toMatch(/0 participants recorded in saree or dhoti/);
  });
});

describe('parseSplit', () => {
  const locked = initSplit(PEOPLE, 'P01', RULES, 'seed-a', NOW);
  if (!locked.ok) throw new Error('fixture split failed');

  it('round-trips a locked split', () => {
    expect(parseSplit(JSON.stringify(locked.split))).toEqual({ ok: true, split: locked.split });
  });

  it('detects edits after locking, bad JSON, bad shape and overlaps', () => {
    const edited = { ...locked.split, heldOut: [...locked.split.heldOut.slice(1), 'P07'] };
    expect(parseSplit(JSON.stringify(edited))).toMatchObject({ ok: false, problems: [expect.stringMatching(/edited/)] });
    expect(parseSplit('{')).toMatchObject({ ok: false });
    expect(parseSplit('{}')).toMatchObject({ ok: false });
    const overlap = initSplit(PEOPLE, 'P01', RULES, 'seed-a', NOW);
    if (!overlap.ok) throw new Error('fixture split failed');
    const core = { ...overlap.split, tuning: [...overlap.split.tuning, overlap.split.heldOut[0]] };
    const rest = { ...core, hash: undefined };
    expect(parseSplit(JSON.stringify({ ...rest, hash: splitHash(rest) }))).toMatchObject({ ok: false, problems: [expect.stringMatching(/both/)] });
  });
});

describe('partition', () => {
  it('never puts a held-out participant in tuning and lists people added after locking', () => {
    const locked = initSplit(PEOPLE, 'P01', RULES, 'seed-a', NOW);
    if (!locked.ok) throw new Error('fixture split failed');
    const items = [...PEOPLE.map((p) => ({ participant: p.code })), { participant: 'P08' }];
    const parts = partition(items, locked.split);
    expect(parts.heldOut.map((i) => i.participant).sort()).toEqual(locked.split.heldOut);
    expect(parts.tuning.some((i) => locked.split.heldOut.includes(i.participant))).toBe(false);
    expect(parts.addedAfterLock).toEqual(['P08']);
  });
});
