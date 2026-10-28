import type { BalanceNorms } from "./norms";

export type StageResult = { readonly stage: number; readonly heldS: number; readonly completed: boolean };

export interface BalanceState {
  readonly results: readonly StageResult[];
  /** Stage being run (index into the CDC stages); startT is null while waiting for the physio to start it. */
  readonly current: { readonly index: number; readonly startT: number | null } | null;
  readonly finished: boolean;
}

export type BalanceEvent =
  | { readonly type: "start-stage"; readonly t: number }
  | { readonly type: "tick"; readonly t: number }
  | { readonly type: "lost"; readonly t: number }
  | { readonly type: "cancel-stage" }
  | { readonly type: "reset" };

export const BALANCE_START: BalanceState = { results: [], current: { index: 0, startT: null }, finished: false };

function record(state: BalanceState, heldS: number, completed: boolean, norms: BalanceNorms): BalanceState {
  if (state.current === null) return state;
  const { index } = state.current;
  const results = [...state.results, { stage: norms.stages[index].stage, heldS, completed }];
  const next = index + 1;
  if (!completed || next >= norms.stages.length) return { results, current: null, finished: true };
  return { results, current: { index: next, startT: null }, finished: false };
}

/** CDC 4-stage balance flow: hold each stage for the protocol time; stop at the first stage not held. */
export function balanceReducer(state: BalanceState, event: BalanceEvent, norms: BalanceNorms): BalanceState {
  if (event.type === "reset") return BALANCE_START;
  const current = state.current;
  if (current === null) return state;
  if (event.type === "start-stage") return current.startT === null ? { ...state, current: { ...current, startT: event.t } } : state;
  if (current.startT === null) return state;
  if (event.type === "cancel-stage") return { ...state, current: { ...current, startT: null } };
  const elapsed = Math.min(Math.max(event.t - current.startT, 0), norms.stageDurationS);
  if (event.type === "lost") return record(state, elapsed, false, norms);
  return elapsed >= norms.stageDurationS ? record(state, norms.stageDurationS, true, norms) : state;
}

export type BalanceVerdict =
  | { readonly kind: "not-reached" }
  | { readonly kind: "stopped-before"; readonly stoppedAtStage: number }
  | { readonly kind: "below" | "meets"; readonly heldS: number };

/** Compares the CDC reference stage (tandem) with the CDC statement. A test that stopped earlier never reached it. */
export function balanceVerdict(state: BalanceState, norms: BalanceNorms): BalanceVerdict {
  const result = state.results.find((r) => r.stage === norms.riskStage);
  const last = state.results[state.results.length - 1];
  if (result === undefined) return state.finished && last !== undefined ? { kind: "stopped-before", stoppedAtStage: last.stage } : { kind: "not-reached" };
  return { kind: result.heldS < norms.riskHoldLessThanS ? "below" : "meets", heldS: result.heldS };
}
