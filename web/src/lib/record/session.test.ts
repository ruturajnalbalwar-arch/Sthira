import { describe, expect, test } from "vitest";
import type { PoseFrame } from "@/lib/sthira/types";
import { MAX_PARTICIPANT_CODE, POSE_LANDMARK_COUNT, RECORDING_SCHEMA, recordedFrameSchema, recordingSchema, sessionMetaSchema, type RecordedFrame, type SessionMeta } from "./schema";
import { buildRecording, closePhase, effectiveFps, openPhase, recordingFileBase } from "./session";

const validMeta: SessionMeta = {
  participantCode: "P07",
  ageBand: "18-29",
  clothing: "saree",
  trial: "chair-stand-30s",
  viewAngle: "45-degree",
  cameraHeightCm: 75,
  cameraDistanceCm: 300,
  room: "hostel common room",
  notes: "",
  consentConfirmed: true,
};

const capture = {
  startedAt: "2026-10-23T10:00:00.000Z",
  videoWidth: 1280,
  videoHeight: 720,
  model: "pose_landmarker_full",
  tasksVisionVersion: "1.1.1",
  frameClock: "capture-time" as const,
  issues: [],
};

describe("session metadata validation", () => {
  test("accepts a complete, consented session", () => {
    expect(sessionMetaSchema.safeParse(validMeta).success).toBe(true);
  });

  test("accepts any participant code of letters, digits, dash and underscore", () => {
    for (const code of ["A11", "P07", "p7", "W3-B12", "7", "KOL_2026-014", "x".repeat(MAX_PARTICIPANT_CODE)]) {
      expect(sessionMetaSchema.safeParse({ ...validMeta, participantCode: code }).success, code).toBe(true);
    }
  });

  test("rejects a code that cannot be part of a file name, an empty code and an over-long code", () => {
    for (const code of ["A 11", "A/11", "..", "a:b", "A.11", "A\\11", "", "   ", "x".repeat(MAX_PARTICIPANT_CODE + 1)]) {
      expect(sessionMetaSchema.safeParse({ ...validMeta, participantCode: code }).success, JSON.stringify(code)).toBe(false);
    }
  });

  test("rejects recording without signed consent", () => {
    const result = sessionMetaSchema.safeParse({ ...validMeta, consentConfirmed: false });
    expect(result.success).toBe(false);
  });

  test("rejects an unmeasured camera position", () => {
    const result = sessionMetaSchema.safeParse({ ...validMeta, cameraDistanceCm: 0 });
    expect(result.success).toBe(false);
  });
});

describe("phase tracking", () => {
  test("opening a phase closes the previous one at the same time", () => {
    // Arrange
    const afterSit = openPhase([], "calibration-sit", 0);

    // Act
    const afterStand = openPhase(afterSit, "calibration-stand", 5);

    // Assert
    expect(afterStand).toEqual([
      { phase: "calibration-sit", startT: 0, endT: 5 },
      { phase: "calibration-stand", startT: 5, endT: 5 },
    ]);
    expect(afterSit).toEqual([{ phase: "calibration-sit", startT: 0, endT: 0 }]);
  });

  test("closing the last phase sets its end time without mutating the input", () => {
    const open = openPhase([], "test", 10);
    const closed = closePhase(open, 42);
    expect(closed.at(-1)?.endT).toBe(42);
    expect(open.at(-1)?.endT).toBe(10);
  });
});

describe("building a recording", () => {
  test("produces a schema-valid recording with phases and frames", () => {
    const frames = [
      { t: 0, landmarks: [] },
      { t: 0.1, landmarks: Array.from({ length: POSE_LANDMARK_COUNT }, () => ({ x: 0.5, y: 0.5, z: 0, visibility: 0.9 })) },
    ];
    const phases = closePhase(openPhase([], "test", 0), 0.1);

    const recording = buildRecording(validMeta, capture, phases, frames);

    expect(recording.schema).toBe(RECORDING_SCHEMA);
    expect(recording.frames).toHaveLength(2);
  });

  test("refuses a frame with a partial landmark list", () => {
    const frame = { t: 0, landmarks: [{ x: 0.5, y: 0.5, z: 0, visibility: 0.9 }] };
    expect(recordedFrameSchema.safeParse(frame).success).toBe(false);
  });

  test("refuses repeated timestamps", () => {
    const frames = [
      { t: 1, landmarks: [] },
      { t: 1, landmarks: [] },
    ];
    expect(() => buildRecording(validMeta, capture, [], frames)).toThrow(/strictly increase/);
  });

  test("refuses a phase that ends before it starts", () => {
    const phases = [{ phase: "test" as const, startT: 5, endT: 2 }];
    expect(() => buildRecording(validMeta, capture, phases, [])).toThrow();
  });

  test("recorded frames stay assignable to the engine's PoseFrame type", () => {
    const frame: RecordedFrame = { t: 0, landmarks: [] };
    const asEngineFrame: PoseFrame = frame;
    expect(asEngineFrame.t).toBe(0);
  });

  test("refuses frames whose timestamps go backwards", () => {
    const frames = [
      { t: 1, landmarks: [] },
      { t: 0.5, landmarks: [] },
    ];
    expect(() => buildRecording(validMeta, capture, [], frames)).toThrow(/timestamps/);
  });
});

describe("file naming and frame rate", () => {
  test("names files by participant, trial and clothing only", () => {
    expect(recordingFileBase(validMeta, "2026-10-23T10:00:00.000Z")).toBe(
      "P07_chair-stand-30s_saree_20261023T100000Z",
    );
  });

  test("computes effective frame rate from timestamps", () => {
    const frames = [0, 0.1, 0.2, 0.3, 0.4].map((t) => ({ t, landmarks: [] }));
    expect(effectiveFps(frames)).toBeCloseTo(10);
  });

  test("returns 0 fps when there are too few frames", () => {
    expect(effectiveFps([{ t: 0 }])).toBe(0);
  });
});

describe("recording schema (direct parse, as the fitter will use it)", () => {
  const recording = (overrides: Record<string, unknown> = {}) => ({
    schema: RECORDING_SCHEMA,
    meta: validMeta,
    capture,
    phases: [],
    frames: [{ t: 0, landmarks: [] }, { t: 0.1, landmarks: [] }],
    ...overrides,
  });

  test("accepts a valid recording", () => {
    expect(recordingSchema.safeParse(recording()).success).toBe(true);
  });

  test("rejects non-increasing timestamps without going through buildRecording", () => {
    const frames = [{ t: 0.1, landmarks: [] }, { t: 0.1, landmarks: [] }];
    expect(recordingSchema.safeParse(recording({ frames })).success).toBe(false);
  });

  test("rejects a device fingerprint such as userAgent in the capture info", () => {
    const withAgent = { ...capture, userAgent: "Mozilla/5.0" };
    expect(recordingSchema.safeParse(recording({ capture: withAgent })).success).toBe(false);
  });

  test.each([Number.NaN, Number.POSITIVE_INFINITY])("rejects a non-finite timestamp %s", (t) => {
    expect(recordedFrameSchema.safeParse({ t, landmarks: [] }).success).toBe(false);
  });

  test("uses CDC 5-year age bands above 60", () => {
    expect(sessionMetaSchema.safeParse({ ...validMeta, ageBand: "65-69" }).success).toBe(true);
    expect(sessionMetaSchema.safeParse({ ...validMeta, ageBand: "60-69" }).success).toBe(false);
  });
});

describe("capture issues", () => {
  test("records interruptions in a typed field so the fitter can exclude the trial", () => {
    const parsed = recordingSchema.safeParse({
      schema: RECORDING_SCHEMA,
      meta: validMeta,
      capture: { ...capture, issues: ["tab-hidden", "camera-ended"] },
      phases: [],
      frames: [],
    });
    expect(parsed.success && parsed.data.capture.issues).toEqual(["tab-hidden", "camera-ended"]);
  });

  test("rejects an unknown issue", () => {
    const bad = { schema: RECORDING_SCHEMA, meta: validMeta, capture: { ...capture, issues: ["gremlins"] }, phases: [], frames: [] };
    expect(recordingSchema.safeParse(bad).success).toBe(false);
  });
});
