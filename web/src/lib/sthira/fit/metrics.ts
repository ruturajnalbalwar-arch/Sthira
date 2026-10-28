import type { TrialScore } from './objective';

/**
 * Evaluation table per clothing type (council verdict, section c). Count metrics use 30 s
 * chair-stand trials where the engine gave a count; rejection rates use every labelled half or
 * arm-assisted attempt; abstain rate uses every trial. A rate with nothing to divide is null.
 */
export interface GroupMetrics {
  readonly group: string;
  readonly trials: number;
  readonly people: number;
  /** 30 s chair-stand trials with an engine count. */
  readonly countTrials: number;
  readonly countMAE: number | null;
  readonly exactMatchRate: number | null;
  readonly falseCounts: number;
  /** False counts per 30 s chair-stand trial with a count. */
  readonly falseCountRate: number | null;
  readonly halfRejection: number | null;
  readonly armRejection: number | null;
  readonly abstainRate: number | null;
}

export const ALL_GROUP = 'all';
const CHAIR_STAND_30S: TrialScore['trial'] = 'chair-stand-30s';

const sum = (values: readonly number[]): number => values.reduce((a, v) => a + v, 0);
const rate = (part: number, whole: number): number | null => (whole > 0 ? part / whole : null);

export function groupMetrics(group: string, scores: readonly TrialScore[]): GroupMetrics {
  const counted = scores.filter((s) => s.trial === CHAIR_STAND_30S && s.engineFull !== null);
  const halfLabelled = sum(scores.map((s) => s.halfLabelled));
  const armLabelled = sum(scores.map((s) => s.armLabelled));
  const falseCounts = sum(counted.map((s) => s.falseCounts));
  return {
    group,
    trials: scores.length,
    people: new Set(scores.map((s) => s.participant)).size,
    countTrials: counted.length,
    countMAE: rate(sum(counted.map((s) => s.countError)), counted.length),
    exactMatchRate: rate(counted.filter((s) => s.countError === 0).length, counted.length),
    falseCounts,
    falseCountRate: rate(falseCounts, counted.length),
    halfRejection: rate(halfLabelled - sum(scores.map((s) => s.halfCountedFull)), halfLabelled),
    armRejection: rate(armLabelled - sum(scores.map((s) => s.armCountedFull)), armLabelled),
    abstainRate: rate(scores.filter((s) => s.abstained).length, scores.length),
  };
}

/** One row per clothing type (alphabetical), then all trials together. */
export function metricsByClothing(scores: readonly TrialScore[]): GroupMetrics[] {
  const clothing = [...new Set(scores.map((s) => s.clothing))].sort();
  return [
    ...clothing.map((c) => groupMetrics(c, scores.filter((s) => s.clothing === c))),
    groupMetrics(ALL_GROUP, scores),
  ];
}
