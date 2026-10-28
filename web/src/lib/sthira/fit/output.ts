import { loadParams } from '../params/loader';
import { PARAM_NAMES, REQUIRED_PARAMS, type ChairStandParams, type ParamName } from '../params/required';
import type { ParamsFile } from '../params/schema';
import type { Exclusion, Trial } from './data';
import { metricsByClothing, type GroupMetrics } from './metrics';
import { scoreTrial, type TrialScore } from './objective';
import type { FitOptions, FitSuccess, Fold } from './search';
import { PARAM_ORIGIN, type DerivedName, type SearchedName } from './space';

/** The script named in every fitted entry's provenance. */
export const FIT_SCRIPT = 'web/scripts/fit-params.ts';

export interface Provenance {
  readonly splitHash: string;
  /** YYYY-MM-DD. */
  readonly date: string;
  /** ISO timestamp. */
  readonly generatedAt: string;
}

const FITTED: readonly ParamName[] = PARAM_NAMES.filter((name) => REQUIRED_PARAMS[name].class === 'fitted');

export type ParamsOutput = { readonly ok: true; readonly file: ParamsFile } | { readonly ok: false; readonly errors: readonly string[] };

/**
 * New params.json content: entries that are not fitted (the protocol constants) are copied from
 * the current file untouched; every fitted entry records value, unit, script, data and date.
 * The result must load as 'ready', or nothing is returned.
 */
export function buildParamsFile(current: ParamsFile, fit: FitSuccess, prov: Provenance): ParamsOutput {
  const data = `${fit.trials} trials from ${fit.people.length} tuning participants, split ${prov.splitHash}`;
  const fitted = Object.fromEntries(
    FITTED.map((name) => [
      name,
      {
        value: fit.params[name],
        class: 'fitted' as const,
        unit: REQUIRED_PARAMS[name].unit,
        source: `Pilot tuning set (${data}): ${PARAM_ORIGIN[name as DerivedName | SearchedName]}`,
        script: FIT_SCRIPT,
        date: prov.date,
      },
    ]),
  );
  const kept = Object.fromEntries(Object.entries(current.params).filter(([name]) => !FITTED.includes(name as ParamName)));
  const file: ParamsFile = { version: current.version, generatedAt: prov.generatedAt, params: { ...kept, ...fitted } };
  const loaded = loadParams(file);
  if (loaded.status === 'ready') return { ok: true, file };
  return { ok: false, errors: loaded.status === 'untuned' ? loaded.missing.map((m) => `missing ${m}`) : loaded.errors };
}

export interface TuneReport {
  readonly kind: 'sthira.fit-report.v1';
  readonly generatedAt: string;
  readonly splitHash: string;
  readonly options: FitOptions;
  readonly tuningPeople: readonly string[];
  readonly addedAfterLock: readonly string[];
  readonly exclusions: readonly Exclusion[];
  readonly final: {
    readonly params: ChairStandParams;
    readonly loss: FitSuccess['loss'];
    readonly rounds: number;
    readonly converged: boolean;
    readonly candidates: FitSuccess['space']['candidates'];
    readonly derived: FitSuccess['space']['derived'];
  };
  readonly loso: {
    readonly folds: readonly { readonly participant: string; readonly errors: readonly string[] }[];
    readonly metrics: readonly GroupMetrics[];
    readonly scores: readonly TrialScore[];
  };
}

export function buildTuneReport(input: {
  fit: FitSuccess;
  folds: readonly Fold[];
  options: FitOptions;
  splitHash: string;
  generatedAt: string;
  exclusions: readonly Exclusion[];
  addedAfterLock: readonly string[];
}): TuneReport {
  const { fit, folds } = input;
  const scores = folds.flatMap((f) => f.scores);
  return {
    kind: 'sthira.fit-report.v1',
    generatedAt: input.generatedAt,
    splitHash: input.splitHash,
    options: input.options,
    tuningPeople: fit.people,
    addedAfterLock: input.addedAfterLock,
    exclusions: [...input.exclusions, ...fit.skipped],
    final: {
      params: fit.params,
      loss: fit.loss,
      rounds: fit.rounds,
      converged: fit.converged,
      candidates: fit.space.candidates,
      derived: fit.space.derived,
    },
    loso: {
      folds: folds.map((f) => ({ participant: f.participant, errors: f.errors })),
      metrics: metricsByClothing(scores),
      scores,
    },
  };
}

export interface EvaluationRecord {
  readonly kind: 'sthira.evaluation.v1';
  readonly generatedAt: string;
  readonly splitHash: string;
  /** sha256 of the params.json text that was evaluated. */
  readonly paramsSha256: string;
  readonly heldOutPeople: readonly string[];
  readonly exclusions: readonly Exclusion[];
  readonly metrics: readonly GroupMetrics[];
  readonly scores: readonly TrialScore[];
}

/** Score the held-out trials with fixed params. Calibration failures count as abstentions, never as exclusions. */
export function evaluateHeldOut(input: {
  trials: readonly Trial[];
  params: ChairStandParams;
  heldOutPeople: readonly string[];
  splitHash: string;
  paramsSha256: string;
  generatedAt: string;
  exclusions: readonly Exclusion[];
}): EvaluationRecord {
  const scores = input.trials.map((t) => scoreTrial(t, input.params));
  return {
    kind: 'sthira.evaluation.v1',
    generatedAt: input.generatedAt,
    splitHash: input.splitHash,
    paramsSha256: input.paramsSha256,
    heldOutPeople: input.heldOutPeople,
    exclusions: input.exclusions,
    metrics: metricsByClothing(scores),
    scores,
  };
}

/** True when params.json's fitted entries were tuned on this split (their source names its hash). */
export function tunedOnSplit(file: ParamsFile, splitHash: string): boolean {
  return FITTED.every((name) => file.params[name]?.source.includes(`split ${splitHash}`) ?? false);
}
