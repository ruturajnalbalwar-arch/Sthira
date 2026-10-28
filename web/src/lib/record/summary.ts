import type { PhaseSpan, RecordedFrame } from "./schema";
import { effectiveFps } from "./session";

export interface RecordingSummary {
  /** Length of the (last) test phase, Go to Stop; null if the test never started. */
  readonly testDurationS: number | null;
  /** Time of the last captured frame. */
  readonly recordingLengthS: number;
  readonly frameCount: number;
  readonly framesWithPerson: number;
  readonly effectiveFps: number;
  /** Largest time between consecutive frames (shows hidden-tab or stalled stretches); null under 2 frames. */
  readonly maxFrameGapS: number | null;
}

export interface SummarySource {
  readonly phases: readonly PhaseSpan[];
  readonly frames: readonly RecordedFrame[];
}

function maxGap(frames: readonly RecordedFrame[]): number | null {
  if (frames.length < 2) return null;
  return frames.slice(1).reduce((gap, frame, i) => Math.max(gap, frame.t - frames[i].t), 0);
}

/** Numbers shown after Stop so the student can see at once whether the capture is usable. */
export function summarizeRecording({ phases, frames }: SummarySource): RecordingSummary {
  const test = phases.findLast((span) => span.phase === "test");
  return {
    testDurationS: test === undefined ? null : test.endT - test.startT,
    recordingLengthS: frames.at(-1)?.t ?? 0,
    frameCount: frames.length,
    framesWithPerson: frames.filter((frame) => frame.landmarks.length > 0).length,
    effectiveFps: effectiveFps(frames),
    maxFrameGapS: maxGap(frames),
  };
}
