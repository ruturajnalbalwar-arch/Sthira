import { CAPTURE_ISSUES } from "./schema";

/** Things that can go wrong during a capture (stored in capture.issues). The files are still kept and saveable. */
export { CAPTURE_ISSUES };
export type CaptureIssue = (typeof CAPTURE_ISSUES)[number];

export const CAPTURE_ISSUE_TEXT: Readonly<Record<CaptureIssue, string>> = {
  "tab-hidden": "The tab was hidden during capture, so frames are missing: redo this trial.",
  "detection-failed": "Pose tracking stopped during capture: redo this trial.",
  "camera-ended": "The camera stopped during capture (unplugged or taken by another app): redo this trial.",
  "recorder-failed": "Video recording failed during capture; the video may be incomplete. Redo this trial.",
  "frame-limit": "The recording reached the largest size a file may hold and was stopped at the limit.",
};

const FATAL: readonly CaptureIssue[] = ["detection-failed", "camera-ended", "recorder-failed"];

/** Fatal issues end the capture at once and stop the camera; the others only flag the trial. */
export function isFatalIssue(issue: CaptureIssue): boolean {
  return FATAL.includes(issue);
}
