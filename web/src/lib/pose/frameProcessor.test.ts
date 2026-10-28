import { describe, expect, it, vi } from "vitest";
import { POSE_LANDMARK_COUNT } from "../record/schema";
import { createTimestampGate } from "./frameLoop";
import { processFrame, type FrameProcessorDeps } from "./frameProcessor";
import { createRecordingClock } from "./recordingClock";
import type { LandmarkLike } from "./toFrame";

const person: LandmarkLike[] = Array.from({ length: POSE_LANDMARK_COUNT }, () => ({ x: 0.5, y: 0.5, z: 0, visibility: 1 }));

function deps(overrides: Partial<FrameProcessorDeps<LandmarkLike[]>> = {}): FrameProcessorDeps<LandmarkLike[]> {
  return {
    detect: vi.fn(() => [person]),
    draw: vi.fn(),
    gate: createTimestampGate(),
    clock: createRecordingClock(),
    maxFrames: 3,
    ...overrides,
  };
}

describe("processFrame", () => {
  it("detects with the capture time when present and shows a preview before recording", () => {
    const d = deps();
    const outcome = processFrame({ callbackMs: 20, captureMs: 12 }, 0, d);
    expect(d.detect).toHaveBeenCalledWith(12);
    expect(d.draw).toHaveBeenCalledWith([person]);
    expect(outcome).toEqual({ kind: "preview", personDetected: true });
  });

  it("detects with the callback time (taken before detection) when there is no capture time", () => {
    const d = deps();
    processFrame({ callbackMs: 20, captureMs: null }, 0, d);
    expect(d.detect).toHaveBeenCalledWith(20);
  });

  it("skips a frame whose detection timestamp did not advance", () => {
    const d = deps();
    processFrame({ callbackMs: 20, captureMs: 12 }, 0, d);
    expect(processFrame({ callbackMs: 30, captureMs: 12 }, 0, d)).toEqual({ kind: "skipped" });
    expect(d.detect).toHaveBeenCalledTimes(1);
  });

  it("records a frame stamped by the recording clock once armed", () => {
    const d = deps();
    d.clock.arm();
    const first = processFrame({ callbackMs: 20, captureMs: 12 }, 0, d);
    const second = processFrame({ callbackMs: 53, captureMs: 45 }, 1, d);
    expect(first).toMatchObject({ kind: "recorded", frame: { t: 0 } });
    expect(second).toMatchObject({ kind: "recorded", personDetected: true });
    if (second.kind === "recorded") expect(second.frame.t).toBeCloseTo(0.033);
  });

  it("records an empty landmark list when nobody is in frame", () => {
    const d = deps({ detect: vi.fn(() => []) });
    d.clock.arm();
    expect(processFrame({ callbackMs: 20, captureMs: 12 }, 0, d)).toEqual({ kind: "recorded", personDetected: false, frame: { t: 0, landmarks: [] } });
  });

  it("reports the frame limit instead of recording past it", () => {
    const d = deps();
    d.clock.arm();
    expect(processFrame({ callbackMs: 20, captureMs: 12 }, 3, d)).toEqual({ kind: "limit", personDetected: true });
  });

  it("reports a detection failure without drawing", () => {
    const d = deps({
      detect: vi.fn(() => {
        throw new Error("gpu lost");
      }),
    });
    expect(processFrame({ callbackMs: 20, captureMs: 12 }, 0, d)).toEqual({ kind: "failed" });
    expect(d.draw).not.toHaveBeenCalled();
  });
});
