import type { RecordedFrame } from "../record/schema";
import type { FrameTiming, TimestampGate } from "./frameLoop";
import type { RecordingClock } from "./recordingClock";
import { toFrame, type LandmarkLike } from "./toFrame";

export interface FrameProcessorDeps<P extends readonly LandmarkLike[]> {
  /** Runs the landmarker for this video frame (throws on failure). */
  readonly detect: (timestampMs: number) => P[];
  readonly draw: (poses: P[]) => void;
  /** Keeps detectForVideo timestamps strictly increasing. */
  readonly gate: TimestampGate;
  readonly clock: RecordingClock;
  /** Largest frame count the recording schema accepts. */
  readonly maxFrames: number;
}

export type FrameOutcome =
  | { readonly kind: "skipped" }
  | { readonly kind: "failed" }
  | { readonly kind: "preview"; readonly personDetected: boolean }
  | { readonly kind: "recorded"; readonly personDetected: boolean; readonly frame: RecordedFrame }
  | { readonly kind: "limit"; readonly personDetected: boolean };

/**
 * One video frame: detect (with the capture time when known, else the pre-detection callback time),
 * draw the overlay, and turn it into a recorded frame if a recording is running.
 */
export function processFrame<P extends readonly LandmarkLike[]>(timing: FrameTiming, recordedCount: number, deps: FrameProcessorDeps<P>): FrameOutcome {
  const timestampMs = timing.captureMs ?? timing.callbackMs;
  if (!deps.gate.accept(timestampMs)) return { kind: "skipped" };
  let poses: P[];
  try {
    poses = deps.detect(timestampMs);
  } catch {
    return { kind: "failed" };
  }
  deps.draw(poses);
  const personDetected = poses.length > 0;
  const t = deps.clock.stamp(timing);
  if (t === null) return { kind: "preview", personDetected };
  if (recordedCount >= deps.maxFrames) return { kind: "limit", personDetected };
  return { kind: "recorded", personDetected, frame: toFrame({ landmarks: poses }, t) };
}
