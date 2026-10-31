import type { ChairStandSummary } from "../sthira/chairStand";
import type { NormLookup } from "./norms";

/** Why a finished run is not compared with the CDC table. Each one is shown to the physiotherapist. */
export const NO_COMPARISON_TEXT = {
  stoppedEarly: "Test stopped early: not a CDC score. The CDC score needs the full timed window.",
  unreliable: "Count not reliable: too many stands were unclear on camera. Repeat the test.",
  lowFps: "The camera ran too slowly for a dependable count. Close other apps and repeat the test.",
} as const;

export type Verdict =
  | {
      readonly kind: "compared";
      readonly full: number;
      readonly belowAverage: boolean;
      /** The CDC cut-off: fewer full stands than this is "below average". */
      readonly cutoff: number;
      readonly sentence: string;
    }
  | { readonly kind: "not-compared"; readonly reasons: readonly string[] };

const SEX_WORD = { female: "women", male: "men" } as const;

function comparisonSentence(full: number, lookup: Extract<NormLookup, { status: "available" }>, below: boolean): string {
  const { entry } = lookup;
  const group = `${SEX_WORD[entry.sex]} aged ${entry.ageMin}-${entry.ageMax}`;
  const position = below ? "Below the CDC average" : "At or above the CDC average";
  return `${position} for ${group}: the CDC sheet marks fewer than ${entry.belowAverageIfLessThan} stands as below average; this run scored ${full}.`;
}

/**
 * Puts a finished count next to the CDC reference value. It never interprets the score further:
 * a run that is incomplete, unreliable or too slow is not compared at all.
 */
export function chairStandVerdict(summary: ChairStandSummary, lookup: NormLookup): Verdict {
  const reasons = [
    summary.windowComplete ? null : NO_COMPARISON_TEXT.stoppedEarly,
    summary.unreliable ? NO_COMPARISON_TEXT.unreliable : null,
    summary.lowFps ? NO_COMPARISON_TEXT.lowFps : null,
    lookup.status === "not-available" ? `No CDC comparison: ${lookup.reason}` : null,
  ].filter((reason): reason is string => reason !== null);
  if (reasons.length > 0 || lookup.status !== "available") return { kind: "not-compared", reasons };
  const cutoff = lookup.entry.belowAverageIfLessThan;
  const belowAverage = summary.full < cutoff;
  return { kind: "compared", full: summary.full, belowAverage, cutoff, sentence: comparisonSentence(summary.full, lookup, belowAverage) };
}
