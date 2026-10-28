import { describe, expect, it } from "vitest";
import type { ChairStandSummary } from "../sthira/chairStand";
import type { NormLookup } from "./norms";
import { chairStandVerdict, NO_COMPARISON_TEXT } from "./verdict";

const SUMMARY: ChairStandSummary = {
  full: 9,
  half: 1,
  armAssisted: 0,
  uncertain: 0,
  events: [],
  effectiveFps: 30,
  lowFps: false,
  unreliable: false,
  droppedFrames: 0,
  gaps: 0,
  windowComplete: true,
};

const SOURCE = { url: "https://www.cdc.gov/x.pdf", title: "STEADI" };
const FEMALE_70: NormLookup = {
  status: "available",
  entry: { sex: "female", ageMin: 70, ageMax: 74, belowAverageIfLessThan: 10, source: SOURCE },
};

describe("chairStandVerdict", () => {
  it("reports a score below the CDC cut-off without any risk or diagnostic wording", () => {
    const verdict = chairStandVerdict(SUMMARY, FEMALE_70);
    expect(verdict).toMatchObject({ kind: "compared", belowAverage: true, cutoff: 10, full: 9 });
    if (verdict.kind === "compared") {
      expect(verdict.sentence).toMatch(/^Below the CDC average for women aged 70-74/);
      expect(verdict.sentence).not.toMatch(/risk|diagnos|fall/i);
    }
  });

  it("reports a score at the cut-off as at or above average", () => {
    const verdict = chairStandVerdict({ ...SUMMARY, full: 10 }, FEMALE_70);
    expect(verdict).toMatchObject({ kind: "compared", belowAverage: false });
    if (verdict.kind === "compared") expect(verdict.sentence).toMatch(/^At or above the CDC average/);
  });

  it("does not compare a test that stopped early", () => {
    expect(chairStandVerdict({ ...SUMMARY, windowComplete: false }, FEMALE_70)).toEqual({
      kind: "not-compared",
      reasons: [NO_COMPARISON_TEXT.stoppedEarly],
    });
  });

  it("does not compare an unreliable or low-fps count, and lists every reason", () => {
    const verdict = chairStandVerdict({ ...SUMMARY, unreliable: true, lowFps: true }, FEMALE_70);
    expect(verdict).toEqual({ kind: "not-compared", reasons: [NO_COMPARISON_TEXT.unreliable, NO_COMPARISON_TEXT.lowFps] });
  });

  it("explains a missing CDC value", () => {
    const verdict = chairStandVerdict(SUMMARY, { status: "not-available", reason: "No row." });
    expect(verdict).toEqual({ kind: "not-compared", reasons: ["No CDC comparison: No row."] });
  });
});
