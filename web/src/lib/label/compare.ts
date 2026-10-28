import type { RepEvent } from "../sthira/chairStand";
import { deriveReps, deriveSpans, tallyReps, type RepMark, type RepTally, type SpanMark } from "./reducer";
import { REP_KINDS, TRIAL_FAMILY, sameRecording, type LabelsFile } from "./schema";

/**
 * Agreement between two labellers (labelling-rules.md, "Comparing the two labellers") and,
 * later, between a labeller and the engine. Nothing here invents a tolerance: every numeric
 * limit is a required argument whose value must come from params.json / the rules file.
 */

export type RowResult = "agree" | "different-key" | "only-a" | "only-b";

export interface RepRow {
  readonly a: RepMark | null;
  readonly b: RepMark | null;
  /** b.t - a.t when both sides have the attempt. */
  readonly timeDiffS: number | null;
  readonly result: RowResult;
}

export interface RepComparison {
  readonly rows: readonly RepRow[];
  readonly countsA: RepTally;
  readonly countsB: RepTally;
  readonly countMatch: Readonly<Record<RepMark["kind"], boolean>>;
  /** Rules: "the number of trials with the same full-stand count". */
  readonly fullCountMatch: boolean;
  readonly attemptCountMatch: boolean;
  /** Rules: "percent of attempts with the same key" = sameKeyAttempts / totalAttempts. */
  readonly sameKeyAttempts: number;
  readonly totalAttempts: number;
  readonly disagreements: readonly RepRow[];
}

function checkGap(maxGapS: number | null): void {
  if (maxGapS !== null && !(Number.isFinite(maxGapS) && maxGapS >= 0)) {
    throw new RangeError("A time limit must be a finite number of seconds >= 0, or null when none is set");
  }
}

/** Index of the mark nearest to t (earliest wins a tie), or -1 for an empty list. */
function nearestIndex(marks: readonly RepMark[], t: number): number {
  return marks.reduce((best, m, i) => (best < 0 || Math.abs(m.t - t) < Math.abs(marks[best].t - t) ? i : best), -1);
}

const rowTime = (row: RepRow): number => Math.min(row.a?.t ?? Infinity, row.b?.t ?? Infinity);

function makeRow(a: RepMark | null, b: RepMark | null): RepRow {
  if (a === null) return { a, b, timeDiffS: null, result: "only-b" };
  if (b === null) return { a, b, timeDiffS: null, result: "only-a" };
  return { a, b, timeDiffS: b.t - a.t, result: a.kind === b.kind ? "agree" : "different-key" };
}

/** Rows for paired indices plus unpaired marks from either side, in time order. */
function buildRows(a: readonly RepMark[], b: readonly RepMark[], pairs: ReadonlyMap<number, number>): RepRow[] {
  const pairedB = new Set(pairs.values());
  const rows = [
    ...a.map((mark, i) => {
      const j = pairs.get(i);
      return makeRow(mark, j === undefined ? null : b[j]);
    }),
    ...b.flatMap((mark, j) => (pairedB.has(j) ? [] : [makeRow(null, mark)])),
  ];
  return rows.sort((x, y) => rowTime(x) - rowTime(y));
}

function summarise(a: readonly RepMark[], b: readonly RepMark[], rows: readonly RepRow[]): RepComparison {
  const countsA = tallyReps(a.map((m) => ({ id: m.id, type: m.kind, t: m.t })));
  const countsB = tallyReps(b.map((m) => ({ id: m.id, type: m.kind, t: m.t })));
  const countMatch = Object.fromEntries(REP_KINDS.map((k) => [k, countsA[k] === countsB[k]])) as Record<
    RepMark["kind"],
    boolean
  >;
  return {
    rows,
    countsA,
    countsB,
    countMatch,
    fullCountMatch: countMatch.full,
    attemptCountMatch: a.length === b.length,
    sameKeyAttempts: rows.filter((r) => r.result === "agree").length,
    totalAttempts: rows.length,
    disagreements: rows.filter((r) => r.result !== "agree"),
  };
}

/**
 * Pair two labellers' attempts as mutual nearest neighbours in time: A's mark and B's mark are
 * the same attempt when each is the other's closest mark. On a time line such pairs never
 * cross, so the order of attempts is kept and one missed attempt does not shift the rest.
 *
 * maxPairGapS: optional extra limit on |b.t - a.t| for a pair. It has no default on purpose:
 * pass null (no limit) until a value is fitted and stored in params.json.
 */
export function compareReps(a: readonly RepMark[], b: readonly RepMark[], maxPairGapS: number | null): RepComparison {
  checkGap(maxPairGapS);
  const pairs = new Map<number, number>();
  a.forEach((mark, i) => {
    const j = nearestIndex(b, mark.t);
    if (j < 0 || nearestIndex(a, b[j].t) !== i) return;
    if (maxPairGapS !== null && Math.abs(b[j].t - mark.t) > maxPairGapS) return;
    pairs.set(i, j);
  });
  return summarise(a, b, buildRows(a, b, pairs));
}

/**
 * Labels (side A) against engine reps (side B). A label is the engine rep's attempt when its
 * time lies inside the rep's [startT, endT] (time overlap, no tolerance). The first such label
 * pairs; any other label inside the same rep is left unmatched. The engine mark's time is
 * standingT, or startT for reps that never stood.
 */
export function compareWithEngine(labels: readonly RepMark[], engine: readonly RepEvent[]): RepComparison {
  const engineMarks: RepMark[] = engine.map((e, j) => ({ id: `engine-${j}`, kind: e.kind, t: e.standingT ?? e.startT }));
  const pairs = new Map<number, number>();
  engine.forEach((rep, j) => {
    const i = labels.findIndex((l, idx) => !pairs.has(idx) && l.t >= rep.startT && l.t <= rep.endT);
    if (i >= 0) pairs.set(i, j);
  });
  return summarise(labels, engineMarks, buildRows(labels, engineMarks, pairs));
}

export interface SpanRow {
  readonly a: SpanMark | null;
  readonly b: SpanMark | null;
  readonly startDiffS: number | null;
  readonly endDiffS: number | null;
  readonly durationDiffS: number | null;
  /** Both ended the same way (B/T end vs L lost vs still open). */
  readonly sameEnding: boolean;
  /** null until the team has fixed the tolerance in labelling-rules.md. */
  readonly agree: boolean | null;
}

const diff = (x: number | null | undefined, y: number | null | undefined): number | null =>
  x === null || x === undefined || y === null || y === undefined ? null : y - x;

/**
 * Balance stages or TUG runs, paired in order (rules: compare the lists in order).
 * toleranceS is the "agree if they differ by no more than ___ s" value from labelling-rules.md;
 * it has no default. With null, differences are reported and agree stays null.
 */
export function compareSpans(a: readonly SpanMark[], b: readonly SpanMark[], toleranceS: number | null): SpanRow[] {
  checkGap(toleranceS);
  const n = Math.max(a.length, b.length);
  return Array.from({ length: n }, (_, i) => {
    const x = a[i] ?? null;
    const y = b[i] ?? null;
    const durationDiffS = diff(x?.durationS, y?.durationS);
    const sameEnding = x !== null && y !== null && x.ending === y.ending;
    const agree =
      toleranceS === null ? null : sameEnding && durationDiffS !== null && Math.abs(durationDiffS) <= toleranceS;
    return { a: x, b: y, startDiffS: diff(x?.startT, y?.startT), endDiffS: diff(x?.endT, y?.endT), durationDiffS, sameEnding, agree };
  });
}

export interface CompareOptions {
  /** See compareReps. No default: null until fitted. */
  readonly maxPairGapS: number | null;
  /** See compareSpans. No default: null until written into labelling-rules.md. */
  readonly spanToleranceS: number | null;
}

export type FileComparison =
  | { readonly ok: false; readonly error: string }
  | {
      readonly ok: true;
      readonly sameLabeller: boolean;
      readonly family: "rep";
      readonly reps: RepComparison;
    }
  | {
      readonly ok: true;
      readonly sameLabeller: boolean;
      readonly family: "balance" | "tug";
      readonly spans: readonly SpanRow[];
    };

/** Compare two validated label files for the same recording. */
export function compareLabelFiles(a: LabelsFile, b: LabelsFile, options: CompareOptions): FileComparison {
  if (!sameRecording(a.recording, b.recording)) {
    return { ok: false, error: "The two label files are for different recordings (frame count, times or names differ)." };
  }
  const sameLabeller = a.labeller.code === b.labeller.code;
  const family = TRIAL_FAMILY[a.recording.trial];
  if (family === "rep") {
    return { ok: true, sameLabeller, family, reps: compareReps(deriveReps(a.events), deriveReps(b.events), options.maxPairGapS) };
  }
  const spans = compareSpans(deriveSpans(a.events, family), deriveSpans(b.events, family), options.spanToleranceS);
  return { ok: true, sameLabeller, family, spans };
}

export interface ToleranceInput {
  /** null = not decided yet. */
  readonly value: number | null;
  readonly error: string | null;
}

/** Plain decimal seconds only ("0.5", ".25", "2"); no signs, exponents, hex or words. Empty = not decided. */
export function parseToleranceSeconds(text: string): ToleranceInput {
  const trimmed = text.trim();
  if (trimmed === "") return { value: null, error: null };
  if (!/^(\d+(\.\d*)?|\.\d+)$/.test(trimmed)) return { value: null, error: "Enter seconds as a plain number, e.g. 0.5." };
  return { value: Number(trimmed), error: null };
}
