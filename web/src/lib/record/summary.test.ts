import { describe, expect, it } from "vitest";
import { POSE_LANDMARK_COUNT, type PhaseSpan, type RecordedFrame } from "./schema";
import { summarizeRecording } from "./summary";

const person = Array.from({ length: POSE_LANDMARK_COUNT }, () => ({ x: 0.5, y: 0.5, z: 0, visibility: 1 }));

function frames(count: number, fps: number, withPersonEvery = 1): RecordedFrame[] {
  return Array.from({ length: count }, (_, i) => ({ t: i / fps, landmarks: i % withPersonEvery === 0 ? person : [] }));
}

const phases: PhaseSpan[] = [
  { phase: "calibration-sit", startT: 0, endT: 5 },
  { phase: "calibration-stand", startT: 5, endT: 10 },
  { phase: "test", startT: 10, endT: 40 },
];

describe("summarizeRecording", () => {
  it("reports test duration, recording length, frame counts and effective fps", () => {
    const summary = summarizeRecording({ phases, frames: frames(1201, 30, 2) });
    expect(summary.testDurationS).toBe(30);
    expect(summary.recordingLengthS).toBeCloseTo(40);
    expect(summary.frameCount).toBe(1201);
    expect(summary.framesWithPerson).toBe(601);
    expect(summary.effectiveFps).toBeCloseTo(30);
    expect(summary.maxFrameGapS).toBeCloseTo(1 / 30);
  });

  it("reports the largest gap between consecutive frames (e.g. while the tab was hidden)", () => {
    const withGap: RecordedFrame[] = [{ t: 0, landmarks: [] }, { t: 0.04, landmarks: [] }, { t: 2.54, landmarks: [] }, { t: 2.58, landmarks: [] }];
    expect(summarizeRecording({ phases, frames: withGap }).maxFrameGapS).toBeCloseTo(2.5);
  });

  it("handles a recording with no test phase and no frames", () => {
    const summary = summarizeRecording({ phases: phases.slice(0, 1), frames: [] });
    expect(summary.testDurationS).toBeNull();
    expect(summary.recordingLengthS).toBe(0);
    expect(summary.effectiveFps).toBe(0);
    expect(summary.maxFrameGapS).toBeNull();
  });
});
