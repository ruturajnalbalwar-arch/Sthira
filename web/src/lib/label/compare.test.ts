import { describe, expect, it } from "vitest";
import type { RepEvent, RepKind } from "../sthira/chairStand";
import { compareLabelFiles, compareReps, compareSpans, compareWithEngine, parseToleranceSeconds } from "./compare";
import type { RepMark, SpanMark } from "./reducer";
import { LABELS_SCHEMA, type LabelEvent, type LabelsFile, type RecordingRef } from "./schema";

const rep = (kind: RepKind, t: number, id = `${kind}@${t}`): RepMark => ({ id, kind, t });
const results = (rows: ReturnType<typeof compareReps>["rows"]) => rows.map((r) => r.result);

describe("compareReps", () => {
  it("agrees when both labellers mark the same attempts with the same keys", () => {
    const out = compareReps([rep("full", 12), rep("half", 16)], [rep("full", 12.1), rep("half", 15.9)], null);
    expect(results(out.rows)).toEqual(["agree", "agree"]);
    expect(out.fullCountMatch).toBe(true);
    expect(out.attemptCountMatch).toBe(true);
    expect(out.sameKeyAttempts).toBe(2);
    expect(out.totalAttempts).toBe(2);
    expect(out.disagreements).toEqual([]);
    expect(out.rows[0].timeDiffS).toBeCloseTo(0.1);
  });

  it("flags a different key for the same attempt", () => {
    const out = compareReps([rep("full", 12), rep("full", 16)], [rep("full", 12), rep("arm-assisted", 16.2)], null);
    expect(results(out.rows)).toEqual(["agree", "different-key"]);
    expect(out.fullCountMatch).toBe(false);
    expect(out.countMatch).toEqual({ full: false, half: true, "arm-assisted": false, uncertain: true });
    expect(out.disagreements).toHaveLength(1);
  });

  it("keeps later attempts aligned when one labeller missed an attempt in the middle", () => {
    const a = [rep("full", 12), rep("full", 16), rep("full", 20)];
    const b = [rep("full", 12.1), rep("full", 20.1)];
    const out = compareReps(a, b, null);
    expect(results(out.rows)).toEqual(["agree", "only-a", "agree"]);
    expect(out.attemptCountMatch).toBe(false);
    expect(out.totalAttempts).toBe(3);
    expect(out.sameKeyAttempts).toBe(2);
  });

  it("reports extra marks from B as only-b", () => {
    const out = compareReps([rep("full", 12)], [rep("full", 12), rep("half", 18)], null);
    expect(results(out.rows)).toEqual(["agree", "only-b"]);
  });

  it("applies the caller's pairing gap only when one is given", () => {
    const far = compareReps([rep("full", 3)], [rep("full", 20)], null);
    expect(results(far.rows)).toEqual(["agree"]);
    const gated = compareReps([rep("full", 3)], [rep("full", 20)], 2);
    expect(results(gated.rows)).toEqual(["only-a", "only-b"]);
  });

  it("refuses a negative or non-finite gap", () => {
    expect(() => compareReps([], [], -1)).toThrow(RangeError);
    expect(() => compareReps([], [], Number.POSITIVE_INFINITY)).toThrow(RangeError);
  });

  it("handles two empty lists", () => {
    const out = compareReps([], [], null);
    expect(out.rows).toEqual([]);
    expect(out.fullCountMatch).toBe(true);
  });
});

const span = (startT: number, endT: number | null, ending: SpanMark["ending"] = "end"): SpanMark => ({
  startId: `s${startT}`,
  startT,
  endId: endT === null ? null : `e${endT}`,
  endT,
  ending: endT === null ? null : ending,
  durationS: endT === null ? null : endT - startT,
});

describe("compareSpans", () => {
  it("pairs spans in order and reports the differences", () => {
    const [row] = compareSpans([span(11, 21)], [span(11.5, 21)], null);
    expect(row.startDiffS).toBeCloseTo(0.5);
    expect(row.endDiffS).toBe(0);
    expect(row.durationDiffS).toBeCloseTo(-0.5);
    expect(row.sameEnding).toBe(true);
    expect(row.agree).toBeNull();
  });

  it("decides agreement only with the tolerance fixed before comparing", () => {
    expect(compareSpans([span(11, 21)], [span(11.5, 21)], 1)[0].agree).toBe(true);
    expect(compareSpans([span(11, 21)], [span(11.5, 21)], 0.2)[0].agree).toBe(false);
    expect(compareSpans([span(11, 21)], [span(11, 21, "lost")], 1)[0].agree).toBe(false);
  });

  it("lists a span that only one labeller marked", () => {
    const rows = compareSpans([span(11, 21)], [], 1);
    expect(rows).toHaveLength(1);
    expect(rows[0].b).toBeNull();
    expect(rows[0].agree).toBe(false);
  });
});

const REF: RecordingRef = {
  fileBase: "P03_chair-stand-30s_saree_20261023T101500Z",
  participantCode: "P03",
  trial: "chair-stand-30s",
  clothing: "saree",
  frameCount: 100,
  firstT: 0,
  lastT: 40,
  goT: 10,
};
const labels = (code: string, events: LabelEvent[], recording = REF): LabelsFile => ({
  schema: LABELS_SCHEMA,
  recording,
  labeller: { code, isExternal: code === "L2" },
  createdAt: "2026-10-23T10:15:00.000Z",
  notes: "",
  events,
});
const OPTS = { maxPairGapS: null, spanToleranceS: null };

describe("compareLabelFiles", () => {
  it("compares chair-stand files by attempt", () => {
    const out = compareLabelFiles(
      labels("L1", [{ id: "e1", type: "full", t: 12 }]),
      labels("L2", [{ id: "e1", type: "half", t: 12.2 }]),
      OPTS,
    );
    expect(out.ok && out.family).toBe("rep");
    if (out.ok && out.family === "rep") expect(out.reps.disagreements).toHaveLength(1);
  });

  it("refuses files from different recordings and warns about the same labeller", () => {
    const other = { ...REF, frameCount: 99 };
    expect(compareLabelFiles(labels("L1", []), labels("L2", [], other), OPTS).ok).toBe(false);
    const same = compareLabelFiles(labels("L1", []), labels("L1", []), OPTS);
    expect(same.ok && same.sameLabeller).toBe(true);
  });

  it("compares balance files by stage", () => {
    const bal = { ...REF, trial: "balance-tandem" as const };
    const a = labels("L1", [{ id: "e1", type: "balance-start", t: 11 }, { id: "e2", type: "balance-end", t: 21 }], bal);
    const b = labels("L2", [{ id: "e1", type: "balance-start", t: 11 }, { id: "e2", type: "balance-lost", t: 15 }], bal);
    const out = compareLabelFiles(a, b, { maxPairGapS: null, spanToleranceS: 1 });
    expect(out.ok && out.family).toBe("balance");
    if (out.ok && out.family !== "rep") expect(out.spans[0].sameEnding).toBe(false);
  });
});

describe("compareWithEngine", () => {
  const engine: RepEvent[] = [
    { kind: "full", startT: 11, standingT: 12, endT: 13.5 },
    { kind: "half", startT: 15, standingT: null, endT: 16.5 },
    { kind: "full", startT: 18, standingT: 19, endT: 20 },
  ];

  it("matches a label to the engine rep whose time span contains it", () => {
    const out = compareWithEngine([rep("full", 12.2), rep("full", 15.8), rep("full", 25)], engine);
    expect(results(out.rows)).toEqual(["agree", "different-key", "only-b", "only-a"]);
  });

  it("leaves a second label inside the same engine rep unmatched", () => {
    const out = compareWithEngine([rep("full", 11.5), rep("full", 13)], engine.slice(0, 1));
    expect(results(out.rows)).toEqual(["agree", "only-a"]);
  });
});

describe("parseToleranceSeconds", () => {
  it("treats empty as not decided and accepts plain decimals", () => {
    expect(parseToleranceSeconds("")).toEqual({ value: null, error: null });
    expect(parseToleranceSeconds(" 0.5 ")).toEqual({ value: 0.5, error: null });
    expect(parseToleranceSeconds("2")).toEqual({ value: 2, error: null });
    expect(parseToleranceSeconds(".25")).toEqual({ value: 0.25, error: null });
  });

  it("refuses hex, exponents, signs and words", () => {
    for (const text of ["0x10", "1e3", "-1", "+1", "Infinity", "1.2.3", "one"]) {
      expect(parseToleranceSeconds(text).error).not.toBeNull();
    }
  });
});
