import type { CaptureIssue } from "../record/captureIssues";
import type { RecordedFrame } from "../record/schema";
import { browserRecorderFactory, startVideoRecorder, type RecorderFactory } from "../record/videoRecorder";
import { createRecordingClock, type FrameClock, type RecordingClock } from "./recordingClock";

export interface CaptureData {
  readonly frames: readonly RecordedFrame[];
  readonly video: Blob;
  readonly videoMime: string;
  readonly startedAtIso: string;
  readonly frameClock: FrameClock;
  readonly issues: readonly CaptureIssue[];
}

export interface CaptureSession {
  readonly clock: RecordingClock;
  addFrame(frame: RecordedFrame): void;
  frameCount(): number;
  flag(issue: CaptureIssue): void;
  hasIssue(issue: CaptureIssue): boolean;
  issues(): readonly CaptureIssue[];
  /** Stops the video and hands over every frame and chunk. Never rejects. */
  finish(): Promise<CaptureData>;
  abort(): void;
}

export interface CaptureSessionOptions {
  readonly timesliceMs: number;
  readonly eventTimeoutMs: number;
  readonly onFailure: (afterStart: boolean) => void;
}

/**
 * One recording: the video recorder, the shared clock, the frames and anything that went wrong.
 * The clock is armed from the recorder's "start" event (see recordingClock.ts for the alignment).
 */
export function createCaptureSession(
  stream: MediaStream,
  options: CaptureSessionOptions,
  factory: RecorderFactory | null = browserRecorderFactory(),
): CaptureSession | null {
  const clock = createRecordingClock();
  // Appended in place (~30 per second): copying the array every frame would be O(n^2).
  const frames: RecordedFrame[] = [];
  let issues: readonly CaptureIssue[] = [];
  let startedAtIso = new Date().toISOString();
  let finishing: Promise<CaptureData> | null = null;
  const video = startVideoRecorder(
    stream,
    {
      ...options,
      onStart: () => {
        startedAtIso = new Date().toISOString();
        clock.arm();
      },
    },
    factory,
  );
  if (video === null) return null;
  return {
    clock,
    addFrame: (frame) => {
      // Closed once finish() starts, so the data handed over never changes afterwards.
      if (finishing === null) frames.push(frame);
    },
    frameCount: () => frames.length,
    flag: (issue) => {
      if (!issues.includes(issue)) issues = [...issues, issue];
    },
    hasIssue: (issue) => issues.includes(issue),
    issues: () => issues,
    finish: () => {
      finishing ??= video.stop().then((blob) => ({
        frames,
        video: blob,
        videoMime: blob.type,
        startedAtIso,
        // With no frame at all the clock never chose; the label is then irrelevant (no timestamps).
        frameClock: clock.kind() ?? "callback-time",
        issues,
      }));
      return finishing;
    },
    abort: () => video.abort(),
  };
}
