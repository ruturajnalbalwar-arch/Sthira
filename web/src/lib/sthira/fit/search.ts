import { orderingErrors } from '../params/loader';
import type { ProtocolConstants } from '../params/protocol';
import type { ChairStandParams } from '../params/required';
import type { Exclusion, Trial } from './data';
import { measureTrial, type TrialMeasure } from './measure';
import { compareLoss, lossOf, scoreTrial, type Loss, type TrialScore } from './objective';
import { SEARCHED, buildSpace, type SearchSpace } from './space';

/** Methodological choices; the CLI names and documents both. */
export interface FitOptions {
  /** Grid resolution for every searched parameter. */
  readonly gridPoints: number;
  /** Upper bound on coordinate-descent rounds; it normally stops earlier, when a round changes nothing. */
  readonly maxRounds: number;
}

export interface FitSuccess {
  readonly ok: true;
  readonly params: ChairStandParams;
  readonly loss: Loss;
  readonly rounds: number;
  readonly converged: boolean;
  readonly space: SearchSpace;
  readonly trials: number;
  readonly people: readonly string[];
  /** Trials whose calibration capture cannot be measured; they cannot set ranges and are not scored. */
  readonly skipped: readonly Exclusion[];
}

export type FitResult = FitSuccess | { readonly ok: false; readonly errors: readonly string[]; readonly skipped: readonly Exclusion[] };

function measureAll(trials: readonly Trial[], protocol: ProtocolConstants) {
  const usable: Trial[] = [];
  const measures: TrialMeasure[] = [];
  const skipped: Exclusion[] = [];
  for (const trial of trials) {
    const result = measureTrial(trial, protocol);
    if (result.ok) {
      usable.push(trial);
      measures.push(result.measure);
    } else {
      skipped.push({ file: trial.fileBase, reason: `calibration capture unusable: ${result.reason}` });
    }
  }
  return { usable, measures, skipped };
}

/** Loss of a parameter set over the trials, memoised on the searched values (everything else is fixed per fit). */
function makeEvaluator(trials: readonly Trial[]) {
  const memo = new Map<string, Loss>();
  return (params: ChairStandParams): Loss => {
    const key = SEARCHED.map((name) => params[name]).join(',');
    const known = memo.get(key);
    if (known !== undefined) return known;
    const loss = lossOf(trials.map((t) => scoreTrial(t, params)));
    memo.set(key, loss);
    return loss;
  };
}

/**
 * Coordinate descent: each round scans every searched parameter over all of its candidates
 * (keeping the others fixed) and moves only on a strict improvement, so ties keep the current
 * value and the result is deterministic. Candidates that break the loader's ordering rules are skipped.
 */
function descend(trials: readonly Trial[], start: ChairStandParams, space: SearchSpace, maxRounds: number) {
  const evaluate = makeEvaluator(trials);
  let current = start;
  let currentLoss = evaluate(current);
  let rounds = 0;
  let converged = false;
  while (rounds < maxRounds && !converged) {
    rounds += 1;
    const before = current;
    for (const name of SEARCHED) {
      for (const value of space.candidates[name]) {
        const candidate = { ...current, [name]: value };
        if (orderingErrors(candidate).length > 0) continue;
        const loss = evaluate(candidate);
        if (compareLoss(loss, currentLoss) < 0) {
          current = candidate;
          currentLoss = loss;
        }
      }
    }
    converged = current === before;
  }
  return { params: current, loss: currentLoss, rounds, converged };
}

/** Fit every non-protocol parameter on these trials only. */
export function fitParams(trials: readonly Trial[], protocol: ProtocolConstants, options: FitOptions): FitResult {
  const { usable, measures, skipped } = measureAll(trials, protocol);
  const built = buildSpace(measures, options.gridPoints);
  if (!built.ok) return { ok: false, errors: built.errors, skipped };
  const { space } = built;
  const start: ChairStandParams = { ...protocol, ...space.derived, ...space.init };
  const startErrors = orderingErrors(start);
  if (startErrors.length > 0) return { ok: false, errors: startErrors, skipped };
  const result = descend(usable, start, space, options.maxRounds);
  const people = [...new Set(usable.map((t) => t.participant))].sort();
  return { ok: true, ...result, space, trials: usable.length, people, skipped };
}

export interface Fold {
  readonly participant: string;
  readonly scores: readonly TrialScore[];
  readonly errors: readonly string[];
}

/**
 * Leave-one-subject-out: for each tuning participant, fit on everyone else (ranges and gates
 * included, so nothing about the left-out person leaks in) and score that person's trials.
 */
export function leaveOneSubjectOut(trials: readonly Trial[], protocol: ProtocolConstants, options: FitOptions): Fold[] {
  const people = [...new Set(trials.map((t) => t.participant))].sort();
  return people.map((participant) => {
    const fit = fitParams(trials.filter((t) => t.participant !== participant), protocol, options);
    if (!fit.ok) return { participant, scores: [], errors: fit.errors };
    const own = trials.filter((t) => t.participant === participant);
    return { participant, scores: own.map((t) => scoreTrial(t, fit.params)), errors: [] };
  });
}
