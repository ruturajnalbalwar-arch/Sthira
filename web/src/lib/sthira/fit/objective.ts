import { compareWithEngine, type RepRow } from '../../label/compare';
import type { RepKind } from '../chairStand';
import type { ChairStandParams } from '../params/required';
import { replayRecording } from '../replay';
import type { Trial } from './data';

/** Replay of one trial against its consensus labels. */
export interface TrialScore {
  readonly fileBase: string;
  readonly participant: string;
  readonly clothing: Trial['clothing'];
  readonly trial: Trial['trial'];
  /** null when the engine produced no count (calibration or context failed). */
  readonly engineFull: number | null;
  readonly labelledFull: number;
  /** |engine full - labelled full|; with no count, the whole labelled count is missed. */
  readonly countError: number;
  /** Engine full stands that are not a labelled full stand. */
  readonly falseCounts: number;
  readonly halfLabelled: number;
  readonly halfCountedFull: number;
  readonly armLabelled: number;
  readonly armCountedFull: number;
  readonly attempts: number;
  /** Attempts where engine and labels differ (different kind, or only one side has it). */
  readonly disagreements: number;
  /** The engine declined to give a usable count: calibration failed, low fps, unreliable, or a cut-short 30 s window. */
  readonly abstained: boolean;
  /** Abstain flag and count correctness disagree: abstained on a right count, or answered a wrong one. */
  readonly flagMismatch: boolean;
  readonly failure: string | null;
}

const CHAIR_STAND_30S: Trial['trial'] = 'chair-stand-30s';

const countKind = (rows: readonly RepRow[], labelled: RepKind): number => rows.filter((r) => r.a?.kind === labelled).length;
const countedFull = (rows: readonly RepRow[], labelled: RepKind): number =>
  rows.filter((r) => r.a?.kind === labelled && r.b?.kind === 'full').length;

function base(trial: Trial) {
  const labelledFull = trial.marks.filter((m) => m.kind === 'full').length;
  return { fileBase: trial.fileBase, participant: trial.participant, clothing: trial.clothing, trial: trial.trial, labelledFull };
}

function noCount(trial: Trial, failure: string): TrialScore {
  const b = base(trial);
  const kinds = (k: RepKind): number => trial.marks.filter((m) => m.kind === k).length;
  return {
    ...b,
    engineFull: null,
    countError: b.labelledFull,
    falseCounts: 0,
    halfLabelled: kinds('half'),
    halfCountedFull: 0,
    armLabelled: kinds('arm-assisted'),
    armCountedFull: 0,
    attempts: trial.marks.length,
    disagreements: trial.marks.length,
    abstained: true,
    flagMismatch: b.labelledFull === 0,
    failure,
  };
}

export function scoreTrial(trial: Trial, params: ChairStandParams): TrialScore {
  const result = replayRecording(trial.recording, params);
  if (!result.ok) return noCount(trial, `${result.stage}: ${result.reasons.join('; ')}`);
  const { summary } = result;
  const b = base(trial);
  const rows = compareWithEngine(trial.marks, summary.events).rows;
  const countError = Math.abs(summary.full - b.labelledFull);
  const cutShort = trial.trial === CHAIR_STAND_30S && !summary.windowComplete;
  const abstained = summary.unreliable || summary.lowFps || cutShort;
  return {
    ...b,
    engineFull: summary.full,
    countError,
    falseCounts: rows.filter((r) => r.b?.kind === 'full' && r.a?.kind !== 'full').length,
    halfLabelled: countKind(rows, 'half'),
    halfCountedFull: countedFull(rows, 'half'),
    armLabelled: countKind(rows, 'arm-assisted'),
    armCountedFull: countedFull(rows, 'arm-assisted'),
    attempts: rows.length,
    disagreements: rows.filter((r) => r.result !== 'agree').length,
    abstained,
    flagMismatch: abstained !== countError > 0,
    failure: null,
  };
}

/**
 * Lexicographic loss, so no invented weights: first the total full-stand count error (the CDC
 * score), then attempt-level disagreements (false counts, missed half/arm rejections, missed
 * uncertain flags), then trials whose abstain flag disagrees with whether the count was wrong.
 */
export type Loss = readonly [countError: number, disagreements: number, flagMismatches: number];

const sum = (values: readonly number[]): number => values.reduce((a, v) => a + v, 0);

export function lossOf(scores: readonly TrialScore[]): Loss {
  return [
    sum(scores.map((s) => s.countError)),
    sum(scores.map((s) => s.disagreements)),
    scores.filter((s) => s.flagMismatch).length,
  ];
}

/** Negative when a is better (smaller) than b. */
export function compareLoss(a: Loss, b: Loss): number {
  const i = a.findIndex((v, k) => v !== b[k]);
  return i < 0 ? 0 : a[i] - b[i];
}
