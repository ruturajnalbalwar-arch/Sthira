import { CAPTURE_ISSUE_TEXT, type CaptureIssue } from "@/lib/record/captureIssues";
import type { RecorderError } from "@/lib/record/recorderErrors";

type Props = {
  error: RecorderError | null;
  issues: readonly CaptureIssue[];
  isRecording: boolean;
  /** Seconds left before the file's frame limit at the current frame rate. */
  secondsToFrameLimit: number | null;
};

/** Show the frame-limit warning this long before the limit (a UI notice, not a data threshold). */
const FRAME_LIMIT_WARNING_S = 60;

const ALERT = "rounded-lg border-2 border-danger bg-surface p-3 font-semibold text-danger";

/** Errors and capture problems, shown right under the controls so they are seen at once. */
export function CaptureAlerts({ error, issues, isRecording, secondsToFrameLimit }: Props) {
  const nearLimit = isRecording && secondsToFrameLimit !== null && secondsToFrameLimit <= FRAME_LIMIT_WARNING_S;
  return (
    <>
      {error !== null && (
        <p role="alert" className={ALERT}>
          {error.message}
        </p>
      )}
      {isRecording &&
        issues.map((issue) => (
          <p key={issue} role="alert" className={`${ALERT} text-lg`}>
            {CAPTURE_ISSUE_TEXT[issue]}
          </p>
        ))}
      {nearLimit && (
        <p role="status" className="rounded-lg border-2 border-warn bg-warn-bg p-3 font-semibold text-warn">
          About {Math.max(0, Math.floor(secondsToFrameLimit))} s left before this file reaches its size limit; it will stop automatically.
        </p>
      )}
    </>
  );
}
