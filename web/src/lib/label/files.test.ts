import { describe, expect, it } from "vitest";
import { recordingSchema } from "../record/schema";
import {
  LABELS_MAX_BYTES,
  RECORDING_MAX_BYTES,
  VIDEO_EXTENSIONS,
  WORST_FRAME_BYTES,
  fileSizeProblem,
  isVideoFile,
  maxRecordingFrames,
  videoMatchesRecording,
} from "./files";

describe("size caps derived from the schemas", () => {
  it("reads the frame cap from recordingSchema itself", () => {
    const max = maxRecordingFrames();
    const frames = (n: number) => Array.from({ length: n }, (_, i) => ({ t: i, landmarks: [] }));
    expect(recordingSchema.shape.frames.safeParse(frames(max)).success).toBe(true);
    expect(recordingSchema.shape.frames.safeParse(frames(max + 1)).success).toBe(false);
  });

  it("allows a full-length recording plus a header of one frame's budget", () => {
    expect(RECORDING_MAX_BYTES).toBe((maxRecordingFrames() + 1) * WORST_FRAME_BYTES);
  });

  it("fits a header with long notes and the most phases inside one frame's budget", () => {
    const header = {
      schema: "sthira.recording.v1",
      meta: { participantCode: "P123", ageBand: "18-29", clothing: "salwar-kameez", trial: "balance-side-by-side", viewAngle: "45-degree", cameraHeightCm: 123.456, cameraDistanceCm: 345.678, room: "r".repeat(80), notes: "n".repeat(500), consentConfirmed: true },
      capture: { startedAt: "s".repeat(200), videoWidth: 1920, videoHeight: 1080, model: "m".repeat(200), tasksVisionVersion: "v".repeat(200), frameClock: "callback-time" },
      phases: Array.from({ length: 16 }, () => ({ phase: "calibration-stand", startT: -1.2345678901234568e-300, endT: -1.2345678901234568e-300 })),
      frames: [],
    };
    expect(new TextEncoder().encode(JSON.stringify(header)).length).toBeLessThan(WORST_FRAME_BYTES);
  });

  it("bounds a label file at the event cap", () => {
    expect(LABELS_MAX_BYTES).toBeGreaterThan(0);
    expect(Number.isFinite(LABELS_MAX_BYTES)).toBe(true);
  });

  it("explains an oversized file and accepts one at the cap", () => {
    expect(fileSizeProblem({ size: RECORDING_MAX_BYTES }, RECORDING_MAX_BYTES, "a Sthira recording")).toBeNull();
    expect(fileSizeProblem({ size: RECORDING_MAX_BYTES + 1 }, RECORDING_MAX_BYTES, "a Sthira recording")).toMatch(
      /larger than a Sthira recording can be/,
    );
  });
});

describe("video files", () => {
  it("accepts by MIME type or, when Windows gives no type, by the recorder's extensions", () => {
    expect(VIDEO_EXTENSIONS).toEqual(expect.arrayContaining(["webm", "mp4", "mkv"]));
    expect(isVideoFile("clip.bin", "video/webm")).toBe(true);
    expect(isVideoFile("P03_tug_saree_20261023T101500Z.WEBM", "")).toBe(true);
    expect(isVideoFile("P03.mkv", "")).toBe(true);
    expect(isVideoFile("notes.txt", "text/plain")).toBe(false);
    expect(isVideoFile("recording.json", "")).toBe(false);
  });

  it("matches the video name stem to the recording's file base", () => {
    const base = "P03_tug_saree_20261023T101500Z";
    expect(videoMatchesRecording(`${base}.webm`, base)).toBe(true);
    expect(videoMatchesRecording(`${base}.mp4`, base)).toBe(true);
    expect(videoMatchesRecording("P04_tug_saree_20261023T101500Z.webm", base)).toBe(false);
    expect(videoMatchesRecording(`${base} (1).webm`, base)).toBe(false);
  });
});
