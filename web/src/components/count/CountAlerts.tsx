import type { LiveStats } from "@/hooks/usePoseRecorder";
import type { LiveCountView } from "@/lib/count/liveCount";
import type { Stage } from "@/lib/record/phaseMachine";

type Props = {
  stage: Stage;
  countView: LiveCountView;
  stats: LiveStats;
  isRecording: boolean;
};

const DANGER = "rounded-lg border-2 border-danger bg-surface p-3 text-lg font-bold text-danger";
const WARN = "rounded-lg border-2 border-warn bg-warn-bg p-3 text-lg font-bold text-warn";
const ACTIVE: readonly Stage[] = ["calibration-sit", "calibration-stand", "test"];

/** Live problems during a run, in words the physiotherapist can act on at once. */
export function CountAlerts({ stage, countView, stats, isRecording }: Props) {
  const lostPerson = isRecording && ACTIVE.includes(stage) && !stats.personDetected;
  const summary = countView.kind === "counting" ? countView.summary : null;
  return (
    <>
      {lostPerson && (
        <p role="alert" className={DANGER}>
          Cannot see the person. Move the camera or chair so the whole body, head to feet, is in view.
        </p>
      )}
      {summary?.unreliable === true && (
        <p role="alert" className={DANGER}>
          Count not reliable: too many stands were unclear on camera.
        </p>
      )}
      {summary?.lowFps === true && (
        <p role="status" className={WARN}>
          The camera is running slowly ({summary.effectiveFps.toFixed(1)} fps), so stands may be missed. Close other apps.
        </p>
      )}
    </>
  );
}
