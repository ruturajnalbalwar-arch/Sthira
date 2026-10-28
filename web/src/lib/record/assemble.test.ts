import { describe, expect, it } from "vitest";
import { RAW_EXPORT_SCHEMA, assembleRecording } from "./assemble";
import { POSE_LANDMARK_COUNT, RECORDING_SCHEMA, type RecordedFrame, type SessionMeta } from "./schema";
import type { CaptureInfo } from "./session";

const meta: SessionMeta = {
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
const capture: CaptureInfo = {
  startedAt: "2026-10-23T10:00:00.000Z",
  videoWidth: 1280,
  videoHeight: 720,
  model: "pose_landmarker_full",
  tasksVisionVersion: "1.1.1",
  frameClock: "capture-time",
  issues: [],
};
const person = Array.from({ length: POSE_LANDMARK_COUNT }, () => ({ x: 0.5, y: 0.5, z: 0, visibility: 1 }));
const frames: RecordedFrame[] = [
  { t: 0, landmarks: person },
  { t: 0.033, landmarks: [] },
];
const phases = [{ phase: "test" as const, startT: 0, endT: 0.033 }];

describe("assembleRecording", () => {
  it("returns a validated recording when everything is valid", () => {
    const result = assembleRecording(meta, capture, phases, frames);
    expect(result.ok).toBe(true);
    if (result.ok) expect(result.recording.schema).toBe(RECORDING_SCHEMA);
  });

  it("never throws: an invalid capture becomes a raw export that keeps every frame", () => {
    const broken = [...frames, { t: 0.01, landmarks: [] }];
    const result = assembleRecording(meta, capture, phases, broken);
    expect(result.ok).toBe(false);
    if (result.ok) return;
    expect(result.errors.join(" ")).toMatch(/strictly increase/);
    expect(result.raw.schema).toBe(RAW_EXPORT_SCHEMA);
    expect(result.raw.frames).toHaveLength(3);
    expect(result.raw.validationErrors).toEqual(result.errors);
    expect(result.raw.meta).toEqual(meta);
  });

  it("caps the listed errors but reports the total", () => {
    const many = Array.from({ length: 50 }, () => ({ t: 0, landmarks: [] }));
    const result = assembleRecording(meta, capture, phases, many);
    if (result.ok) throw new Error("expected failure");
    expect(result.errors.length).toBeLessThanOrEqual(11);
    expect(result.errorCount).toBeGreaterThanOrEqual(1);
  });
});
