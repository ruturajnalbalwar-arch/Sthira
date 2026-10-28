import type { CaptureResult } from "@/hooks/usePoseRecorder";
import { POSE_MODEL_NAME, TASKS_VISION_VERSION } from "@/lib/pose/createPoseLandmarker";
import { assembleRecording, type AssembleResult } from "@/lib/record/assemble";
import type { PhaseSpan, SessionMeta } from "@/lib/record/schema";

/** A finished capture and its validated sthira.recording.v1 file, ready for SaveRecording. */
export type Review = { capture: CaptureResult; result: AssembleResult };

export function buildReview(meta: SessionMeta, capture: CaptureResult, phases: readonly PhaseSpan[]): Review {
  const info = {
    startedAt: capture.startedAtIso,
    videoWidth: capture.videoWidth,
    videoHeight: capture.videoHeight,
    model: POSE_MODEL_NAME,
    tasksVisionVersion: TASKS_VISION_VERSION,
    frameClock: capture.frameClock,
    issues: capture.issues,
  };
  return { capture, result: assembleRecording(meta, info, phases, capture.frames) };
}
