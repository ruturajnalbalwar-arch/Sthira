import { describe, expect, it } from "vitest";
import normsJson from "../sthira/norms/norms.json";
import { BALANCE_START, balanceReducer, balanceVerdict, type BalanceState } from "./balance";
import { readTimerNorms } from "./norms";
import { tugVerdict } from "./tug";

const loaded = readTimerNorms(normsJson);
if (!loaded.ok) throw new Error(loaded.errors.join("; "));
const norms = loaded.norms;
const hold = norms.balance.stageDurationS;

function run(events: Parameters<typeof balanceReducer>[1][]): BalanceState {
  return events.reduce((state, event) => balanceReducer(state, event, norms.balance), BALANCE_START);
}

describe("readTimerNorms", () => {
  it("reads the CDC balance and TUG values from norms.json", () => {
    expect(norms.balance.stages.map((s) => s.stage)).toEqual([1, 2, 3, 4]);
    expect(norms.balance.riskStage).toBe(3);
    expect(norms.tug.atRiskIfAtLeastS).toBeGreaterThan(0);
    expect(norms.tug.source.url).toMatch(/^https:\/\/www\.cdc\.gov\//);
  });

  it("rejects a file without the balance section", () => {
    const result = readTimerNorms({ tug: normsJson.tug });
    expect(result.ok).toBe(false);
  });
});

describe("balanceReducer", () => {
  it("moves to the next stage when a stage is held for the full time", () => {
    const state = run([
      { type: "start-stage", t: 0 },
      { type: "tick", t: hold },
    ]);
    expect(state.results).toEqual([{ stage: 1, heldS: hold, completed: true }]);
    expect(state.current).toEqual({ index: 1, startT: null });
    expect(state.finished).toBe(false);
  });

  it("stops the test when the position is lost", () => {
    const state = run([
      { type: "start-stage", t: 0 },
      { type: "tick", t: hold },
      { type: "start-stage", t: 20 },
      { type: "lost", t: 20 + hold / 2 },
    ]);
    expect(state.results[1]).toEqual({ stage: 2, heldS: hold / 2, completed: false });
    expect(state.finished).toBe(true);
    expect(state.current).toBeNull();
  });

  it("clamps a late tick to the stage duration", () => {
    const state = run([
      { type: "start-stage", t: 0 },
      { type: "tick", t: hold * 3 },
    ]);
    expect(state.results[0].heldS).toBe(hold);
  });

  it("finishes after the last stage", () => {
    const events = norms.balance.stages.flatMap((_, i) => [
      { type: "start-stage" as const, t: i * 100 },
      { type: "tick" as const, t: i * 100 + hold },
    ]);
    const state = run(events);
    expect(state.results).toHaveLength(norms.balance.stages.length);
    expect(state.finished).toBe(true);
  });

  it("ignores events that do not apply", () => {
    expect(run([{ type: "lost", t: 1 }])).toEqual(BALANCE_START);
    expect(run([{ type: "tick", t: 1 }])).toEqual(BALANCE_START);
    const started = run([{ type: "start-stage", t: 0 }]);
    expect(balanceReducer(started, { type: "start-stage", t: 1 }, norms.balance)).toBe(started);
  });

  it("cancel-stage returns the current stage to waiting", () => {
    const state = run([
      { type: "start-stage", t: 0 },
      { type: "cancel-stage" },
    ]);
    expect(state.current).toEqual({ index: 0, startT: null });
    expect(state.results).toEqual([]);
  });

  it("reset starts over", () => {
    expect(run([{ type: "start-stage", t: 0 }, { type: "reset" }])).toEqual(BALANCE_START);
  });
});

describe("balanceVerdict", () => {
  it("is pending until the CDC reference stage has a result", () => {
    expect(balanceVerdict(BALANCE_START, norms.balance).kind).toBe("not-reached");
  });

  it("is below the reference when the tandem stand is not held", () => {
    const state = run([
      { type: "start-stage", t: 0 },
      { type: "tick", t: hold },
      { type: "start-stage", t: 20 },
      { type: "tick", t: 20 + hold },
      { type: "start-stage", t: 40 },
      { type: "lost", t: 41 },
    ]);
    expect(balanceVerdict(state, norms.balance)).toEqual({ kind: "below", heldS: 1 });
  });

  it("meets the reference when the tandem stand is held", () => {
    const state = run(
      [0, 1, 2].flatMap((i) => [
        { type: "start-stage" as const, t: i * 20 },
        { type: "tick" as const, t: i * 20 + hold },
      ]),
    );
    expect(balanceVerdict(state, norms.balance)).toEqual({ kind: "meets", heldS: hold });
  });

  it("reports the stage where the test stopped when it never reached the tandem stage", () => {
    const state = run([
      { type: "start-stage", t: 0 },
      { type: "lost", t: 2 },
    ]);
    expect(balanceVerdict(state, norms.balance)).toEqual({ kind: "stopped-before", stoppedAtStage: 1 });
  });
});

describe("tugVerdict", () => {
  it("compares the time with the CDC TUG reference", () => {
    const ref = norms.tug.atRiskIfAtLeastS;
    expect(tugVerdict(ref, norms.tug)).toBe("at-or-above");
    expect(tugVerdict(ref / 2, norms.tug)).toBe("below");
  });
});
