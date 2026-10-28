import type { TugNorms } from "./norms";

export type TugVerdict = "below" | "at-or-above";

/** Compares a TUG time with the single CDC reference value for older adults. */
export function tugVerdict(seconds: number, norms: TugNorms): TugVerdict {
  return seconds >= norms.atRiskIfAtLeastS ? "at-or-above" : "below";
}
