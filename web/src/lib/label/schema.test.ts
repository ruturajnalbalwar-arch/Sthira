import { describe, expect, it } from "vitest";
import type { Recording } from "../record/schema";
import {
  LABELS_SCHEMA,
  eventProblems,
  labelFileName,
  labelsSchema,
  parseJsonWith,
  recordingRef,
  sameRecording,
  type LabelEvent,
  type LabelsFile,
  type RecordingRef,
} from "./schema";

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

const ev = (id: string, type: LabelEvent["type"], t: number): LabelEvent => ({ id, type, t });

const file = (events: LabelEvent[], recording: RecordingRef = REF): LabelsFile => ({
  schema: LABELS_SCHEMA,
  recording,
  labeller: { code: "L1", isExternal: false },
  createdAt: "2026-10-23T10:15:00.000Z",
  notes: "",
  events,
});

const recording = (overrides: Partial<Recording> = {}): Recording => ({
  schema: "sthira.recording.v1",
  meta: {
    participantCode: "P03",
    ageBand: "65-69",
    clothing: "saree",
    trial: "chair-stand-30s",
    viewAngle: "45-degree",
    cameraHeightCm: 90,
    cameraDistanceCm: 250,
    room: "hall",
    notes: "",
    consentConfirmed: true,
  },
  capture: {
    startedAt: "2026-10-23T10:15:00.123Z",
    videoWidth: 640,
    videoHeight: 480,
    model: "lite",
    tasksVisionVersion: "1.1.1",
    frameClock: "capture-time",
    issues: [],
  },
  phases: [
    { phase: "calibration-sit", startT: 0, endT: 5 },
    { phase: "calibration-stand", startT: 5, endT: 10 },
    { phase: "test", startT: 10, endT: 40 },
  ],
  frames: [0, 20, 40].map((t) => ({ t, landmarks: [] })),
  ...overrides,
});

describe("labelsSchema", () => {
  it("accepts a well-formed chair-stand label file", () => {
    const result = labelsSchema.safeParse(file([ev("e1", "full", 12), ev("e2", "half", 15)]));
    expect(result.success).toBe(true);
  });

  it("rejects a labeller name instead of a code", () => {
    const bad = { ...file([]), labeller: { code: "Asha", isExternal: true } };
    expect(labelsSchema.safeParse(bad).success).toBe(false);
  });

  it("rejects events that are not strictly ordered in time", () => {
    const result = labelsSchema.safeParse(file([ev("e1", "full", 15), ev("e2", "full", 15)]));
    expect(result.success).toBe(false);
  });

  it("rejects events before Go or after the last frame", () => {
    expect(labelsSchema.safeParse(file([ev("e1", "full", 9)])).success).toBe(false);
    expect(labelsSchema.safeParse(file([ev("e1", "full", 41)])).success).toBe(false);
  });

  it("rejects an event type that does not belong to the trial", () => {
    expect(labelsSchema.safeParse(file([ev("e1", "balance-start", 12)])).success).toBe(false);
  });

  it("rejects duplicate event ids", () => {
    expect(labelsSchema.safeParse(file([ev("e1", "full", 12), ev("e1", "full", 14)])).success).toBe(false);
  });

  it("rejects an unknown schema tag", () => {
    expect(labelsSchema.safeParse({ ...file([]), schema: "sthira.labels.v0" }).success).toBe(false);
  });

  it("rejects a recording span that ends before it starts", () => {
    expect(labelsSchema.safeParse(file([], { ...REF, firstT: 5, lastT: 4, goT: 4 })).success).toBe(false);
  });
});

describe("eventProblems pairing", () => {
  const BAL: RecordingRef = { ...REF, trial: "balance-tandem" };
  const TUG: RecordingRef = { ...REF, trial: "tug" };

  it("accepts start then end, start then lost, and a trailing open stage", () => {
    const events = [
      ev("a", "balance-start", 11),
      ev("b", "balance-end", 21),
      ev("c", "balance-start", 22),
      ev("d", "balance-lost", 25),
      ev("e", "balance-start", 30),
    ];
    expect(eventProblems(events, BAL)).toEqual([]);
  });

  it("rejects an end without a start and two starts in a row", () => {
    expect(eventProblems([ev("a", "balance-end", 11)], BAL)).not.toEqual([]);
    expect(eventProblems([ev("a", "tug-start", 11), ev("b", "tug-start", 12)], TUG)).not.toEqual([]);
  });

  it("rejects B pressed after L for the same stage", () => {
    const events = [ev("a", "balance-start", 11), ev("b", "balance-lost", 13), ev("c", "balance-end", 14)];
    expect(eventProblems(events, BAL)).not.toEqual([]);
  });
});

describe("recordingRef", () => {
  it("takes the fingerprint and Go time from the recording", () => {
    const result = recordingRef(recording());
    expect(result).toEqual({
      ok: true,
      ref: { ...REF, fileBase: "P03_chair-stand-30s_saree_20261023T101500Z", frameCount: 3 },
    });
  });

  it("refuses a recording without frames or without a test phase", () => {
    expect(recordingRef(recording({ frames: [] })).ok).toBe(false);
    expect(recordingRef(recording({ phases: [] })).ok).toBe(false);
  });

  it("refuses a test phase that starts after the last frame", () => {
    expect(recordingRef(recording({ phases: [{ phase: "test", startT: 50, endT: 60 }] })).ok).toBe(false);
  });
});

describe("parseJsonWith error cap", () => {
  it("formats only the first few issues and reports the total", () => {
    const events = Array.from({ length: 30 }, (_, i) => ({ id: `e${i}`, type: "full", t: -1 - i }));
    const bad = parseJsonWith(labelsSchema, JSON.stringify(file(events as LabelEvent[])), 3);
    expect(bad.ok).toBe(false);
    if (!bad.ok) {
      expect(bad.errors).toHaveLength(3);
      expect(bad.totalErrors).toBeGreaterThan(3);
    }
  });
});

describe("helpers", () => {
  it("names the export after the recording and the labeller code", () => {
    expect(labelFileName(REF, "L2")).toBe("P03_chair-stand-30s_saree_20261023T101500Z_labels_L2.json");
  });

  it("matches label files to the same recording only", () => {
    expect(sameRecording(REF, { ...REF })).toBe(true);
    expect(sameRecording(REF, { ...REF, frameCount: 99 })).toBe(false);
  });

  it("parses JSON text into readable errors", () => {
    expect(parseJsonWith(labelsSchema, "{not json")).toMatchObject({ ok: false });
    const bad = parseJsonWith(labelsSchema, JSON.stringify({ ...file([]), labeller: { code: "x" } }));
    expect(bad.ok).toBe(false);
    if (!bad.ok) expect(bad.errors.some((e) => e.startsWith("labeller.code"))).toBe(true);
    const good = parseJsonWith(labelsSchema, JSON.stringify(file([ev("e1", "full", 12)])));
    expect(good.ok).toBe(true);
  });
});
