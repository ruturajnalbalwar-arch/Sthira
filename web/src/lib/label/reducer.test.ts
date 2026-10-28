import { describe, expect, it } from "vitest";
import {
  deriveReps,
  deriveSpans,
  initLabelState,
  labelReducer,
  nearestFrameT,
  outcomeMessage,
  snapToFrame,
  stepFrameT,
  tallyReps,
  type LabelAction,
  type LabelState,
} from "./reducer";
import type { LabelEvent, LabelKey, RecordingRef } from "./schema";

const CHAIR: RecordingRef = {
  fileBase: "P03_chair-stand-30s_saree_20261023T101500Z",
  participantCode: "P03",
  trial: "chair-stand-30s",
  clothing: "saree",
  frameCount: 1201,
  firstT: 0,
  lastT: 40,
  goT: 10,
};
const BALANCE: RecordingRef = { ...CHAIR, trial: "balance-tandem" };
const TUG: RecordingRef = { ...CHAIR, trial: "tug" };

const press = (key: LabelKey, t: number): LabelAction => ({ type: "press", key, t, videoT: t });
const run = (ref: RecordingRef, actions: readonly LabelAction[]): LabelState =>
  actions.reduce(labelReducer, initLabelState(ref));
const shape = (state: LabelState) => state.events.map((e) => [e.type, e.t]);

describe("press", () => {
  it("adds a rep at the current time and reports it", () => {
    const state = run(CHAIR, [press("F", 12.4)]);
    expect(shape(state)).toEqual([["full", 12.4]]);
    expect(state.outcome).toMatchObject({ kind: "added" });
    expect(outcomeMessage(state.outcome)).toBe("Full stand marked at 12.4 s");
  });

  it("keeps events in time order when keys are pressed out of order", () => {
    const state = run(CHAIR, [press("F", 20), press("H", 15), press("A", 25)]);
    expect(shape(state)).toEqual([
      ["half", 15],
      ["full", 20],
      ["arm-assisted", 25],
    ]);
  });

  it("ignores a duplicate keypress on the same frame", () => {
    const once = run(CHAIR, [press("F", 12)]);
    const twice = labelReducer(once, press("F", 12));
    expect(twice.events).toBe(once.events);
    expect(twice.outcome).toEqual({ kind: "ignored", reason: "duplicate" });
  });

  it("rejects a different key on a frame that already has a mark", () => {
    const state = run(CHAIR, [press("F", 12), press("H", 12)]);
    expect(shape(state)).toEqual([["full", 12]]);
    expect(state.outcome).toMatchObject({ kind: "rejected", reason: "occupied" });
  });

  it("rejects keys pressed before Go (calibration phases) and after the last frame", () => {
    expect(run(CHAIR, [press("F", 4)]).outcome).toMatchObject({ kind: "rejected", reason: "before-go" });
    expect(run(CHAIR, [press("F", 41)]).outcome).toMatchObject({ kind: "rejected", reason: "outside-recording" });
    expect(run(CHAIR, [press("F", 4)]).events).toEqual([]);
  });

  it("accepts a key exactly at Go and exactly at the last frame", () => {
    expect(shape(run(CHAIR, [press("F", 10), press("F", 40)]))).toEqual([
      ["full", 10],
      ["full", 40],
    ]);
  });

  it("rejects a key pressed where the video has no landmark data, saying so", () => {
    const state = run(CHAIR, [{ type: "press", key: "F", t: null, videoT: 41.83 }]);
    expect(state.events).toEqual([]);
    expect(state.outcome).toMatchObject({ kind: "rejected", reason: "no-landmarks" });
    expect(outcomeMessage(state.outcome)).toBe("No landmark data at 41.8 s: this mark was not added.");
  });

  it("says the video is not ready when there is no video time at all", () => {
    const state = run(CHAIR, [{ type: "press", key: "F", t: null, videoT: Number.NaN }]);
    expect(state.outcome).toMatchObject({ kind: "rejected", reason: "not-finite" });
    expect(outcomeMessage(state.outcome)).toMatch(/not added/);
  });

  it("rejects keys from another test and non-finite times", () => {
    expect(run(CHAIR, [press("B", 12)]).outcome).toMatchObject({ kind: "rejected", reason: "wrong-trial" });
    expect(run(BALANCE, [press("F", 12)]).outcome).toMatchObject({ kind: "rejected", reason: "wrong-trial" });
    expect(run(CHAIR, [press("F", Number.NaN)]).outcome).toMatchObject({ kind: "rejected", reason: "not-finite" });
  });
});

describe("balance and TUG pairs", () => {
  it("B alternates start and end", () => {
    const state = run(BALANCE, [press("B", 11), press("B", 21), press("B", 25)]);
    expect(shape(state)).toEqual([
      ["balance-start", 11],
      ["balance-end", 21],
      ["balance-start", 25],
    ]);
  });

  it("L ends the open stage and is refused without one", () => {
    expect(run(BALANCE, [press("L", 12)]).outcome).toMatchObject({ reason: "no-open-stage" });
    const state = run(BALANCE, [press("B", 11), press("L", 14), press("B", 16)]);
    expect(shape(state)).toEqual([
      ["balance-start", 11],
      ["balance-lost", 14],
      ["balance-start", 16],
    ]);
  });

  it("refuses a B that would land inside or before an existing stage", () => {
    const stage = run(BALANCE, [press("B", 11), press("B", 21)]);
    expect(labelReducer(stage, press("B", 15)).outcome).toMatchObject({ reason: "pairing" });
    expect(labelReducer(stage, press("B", 10.5)).outcome).toMatchObject({ reason: "pairing" });
  });

  it("T alternates TUG start and end", () => {
    expect(shape(run(TUG, [press("T", 11), press("T", 19)]))).toEqual([
      ["tug-start", 11],
      ["tug-end", 19],
    ]);
  });
});

describe("undo and delete", () => {
  it("undo removes the most recently added mark, not the latest in time", () => {
    const state = run(CHAIR, [press("F", 20), press("H", 15), { type: "undo" }]);
    expect(shape(state)).toEqual([["full", 20]]);
    expect(state.outcome).toMatchObject({ kind: "removed", via: "undo" });
  });

  it("undo with nothing left is ignored", () => {
    const state = run(CHAIR, [press("F", 20), { type: "undo" }, { type: "undo" }]);
    expect(state.events).toEqual([]);
    expect(state.outcome).toEqual({ kind: "ignored", reason: "nothing-to-undo" });
  });

  it("undo skips marks that were already deleted", () => {
    const added = run(CHAIR, [press("F", 12), press("F", 16)]);
    const deleted = labelReducer(added, { type: "delete", id: added.events[1].id });
    expect(shape(labelReducer(deleted, { type: "undo" }))).toEqual([]);
  });

  it("delete refuses to orphan the end of a stage and an unknown id", () => {
    const stage = run(BALANCE, [press("B", 11), press("B", 21)]);
    expect(labelReducer(stage, { type: "delete", id: stage.events[0].id }).outcome).toMatchObject({
      reason: "pairing",
    });
    expect(labelReducer(stage, { type: "delete", id: "nope" }).outcome).toMatchObject({ reason: "not-found" });
    expect(shape(labelReducer(stage, { type: "delete", id: stage.events[1].id }))).toEqual([["balance-start", 11]]);
  });

  it("undoing a B end lets the next B end the stage again", () => {
    const state = run(BALANCE, [press("B", 11), press("B", 21), { type: "undo" }, press("B", 22)]);
    expect(shape(state)).toEqual([
      ["balance-start", 11],
      ["balance-end", 22],
    ]);
  });
});

describe("nudge", () => {
  const two = run(CHAIR, [press("F", 12), press("F", 16)]);

  it("moves a mark and keeps the list ordered", () => {
    const moved = labelReducer(two, { type: "nudge", id: two.events[0].id, deltaS: 5 });
    expect(shape(moved)).toEqual([
      ["full", 16],
      ["full", 17],
    ]);
    expect(moved.outcome).toMatchObject({ kind: "moved", fromT: 12 });
  });

  it("refuses to land on another mark, before Go or on an unknown id", () => {
    const id = two.events[0].id;
    expect(labelReducer(two, { type: "nudge", id, deltaS: 4 }).outcome).toMatchObject({ reason: "occupied" });
    expect(labelReducer(two, { type: "nudge", id, deltaS: -3 }).outcome).toMatchObject({ reason: "before-go" });
    expect(labelReducer(two, { type: "nudge", id: "x", deltaS: 1 }).outcome).toMatchObject({ reason: "not-found" });
  });

  it("refuses to move a stage end before its start", () => {
    const stage = run(BALANCE, [press("B", 11), press("B", 21)]);
    const nudged = labelReducer(stage, { type: "nudge", id: stage.events[1].id, deltaS: -10.5 });
    expect(nudged.outcome).toMatchObject({ reason: "pairing" });
  });
});

describe("initLabelState from a saved file", () => {
  it("re-ids saved events and lets undo remove the last one in time", () => {
    const saved: LabelEvent[] = [
      { id: "x9", type: "full", t: 12 },
      { id: "x3", type: "half", t: 15 },
    ];
    const state = labelReducer(initLabelState(CHAIR, saved), { type: "undo" });
    expect(shape(state)).toEqual([["full", 12]]);
    const added = labelReducer(state, press("F", 18));
    expect(new Set(added.events.map((e) => e.id)).size).toBe(2);
  });
});

describe("derived views", () => {
  it("lists reps one per attempt and tallies them by kind", () => {
    const state = run(CHAIR, [press("F", 12), press("A", 16), press("U", 19), press("F", 22)]);
    expect(deriveReps(state.events).map((r) => [r.kind, r.t])).toEqual([
      ["full", 12],
      ["arm-assisted", 16],
      ["uncertain", 19],
      ["full", 22],
    ]);
    expect(tallyReps(state.events)).toEqual({ full: 2, half: 0, "arm-assisted": 1, uncertain: 1 });
  });

  it("pairs balance stages with their end, lost and open outcomes", () => {
    const state = run(BALANCE, [press("B", 11), press("B", 21), press("B", 22), press("L", 25), press("B", 30)]);
    expect(deriveSpans(state.events, "balance")).toEqual([
      expect.objectContaining({ startT: 11, endT: 21, ending: "end", durationS: 10 }),
      expect.objectContaining({ startT: 22, endT: 25, ending: "lost", durationS: 3 }),
      expect.objectContaining({ startT: 30, endT: null, ending: null, durationS: null }),
    ]);
  });
});

describe("frame helpers", () => {
  const ts = [0, 0.1, 0.2, 0.35];

  it("snaps to the nearest recorded frame time", () => {
    expect(nearestFrameT(ts, 0.16)).toBe(0.2);
    expect(nearestFrameT(ts, -4)).toBe(0);
    expect(nearestFrameT(ts, 9)).toBe(0.35);
    expect(nearestFrameT([], 3)).toBe(3);
  });

  it("snapToFrame refuses times beyond half the largest frame gap outside the recording", () => {
    // Largest gap is 0.15 s, so the data reaches 0.075 s past either end.
    expect(snapToFrame(ts, 0.16)).toBe(0.2);
    expect(snapToFrame(ts, -0.07)).toBe(0);
    expect(snapToFrame(ts, 0.42)).toBe(0.35);
    expect(snapToFrame(ts, -0.08)).toBeNull();
    expect(snapToFrame(ts, 0.43)).toBeNull();
    expect(snapToFrame(ts, Number.NaN)).toBeNull();
    expect(snapToFrame([], 1)).toBeNull();
    expect(snapToFrame([2], 2)).toBe(2);
    expect(snapToFrame([2], 2.01)).toBeNull();
  });

  it("stepFrameT moves to the neighbouring frame from exactly on, or a hair off, a frame", () => {
    expect(stepFrameT(ts, 0.2, 1)).toBe(0.35);
    expect(stepFrameT(ts, 0.2, -1)).toBe(0.1);
    expect(stepFrameT(ts, 0.2 - 1e-9, 1)).toBe(0.35);
    expect(stepFrameT(ts, 0.2 + 1e-9, -1)).toBe(0.1);
  });

  it("stepFrameT inside a long gap goes to the frames on either side, never skipping one", () => {
    const gappy = [0, 0.1, 0.2, 2.2, 2.3];
    expect(stepFrameT(gappy, 0.5, -1)).toBe(0.2);
    expect(stepFrameT(gappy, 0.5, 1)).toBe(2.2);
  });

  it("stepFrameT returns null at the first and last frame", () => {
    expect(stepFrameT(ts, 0.35, 1)).toBeNull();
    expect(stepFrameT(ts, 0, -1)).toBeNull();
    expect(stepFrameT(ts, -1, 1)).toBe(0);
    expect(stepFrameT([], 0, 1)).toBeNull();
  });
});

describe("contract round trip", () => {
  it("exports reducer output that labelsSchema accepts unchanged", async () => {
    const { LABELS_SCHEMA, labelsSchema } = await import("./schema");
    const state = run(BALANCE, [press("B", 11), press("L", 14), press("B", 16), press("B", 26)]);
    const file = {
      schema: LABELS_SCHEMA,
      recording: BALANCE,
      labeller: { code: "L2", isExternal: true },
      createdAt: "2026-10-23T10:15:00.000Z",
      notes: "",
      events: state.events,
    };
    const parsed = labelsSchema.safeParse(JSON.parse(JSON.stringify(file)));
    expect(parsed.success).toBe(true);
    if (parsed.success) expect(parsed.data.events).toEqual(state.events);
  });
});
