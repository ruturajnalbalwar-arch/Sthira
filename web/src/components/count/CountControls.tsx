import { Button } from "@/components/ui/Button";
import type { CameraStatus } from "@/hooks/usePoseRecorder";
import type { LiveCountView } from "@/lib/count/liveCount";
import type { Stage } from "@/lib/record/phaseMachine";

type Props = {
  stage: Stage;
  countView: LiveCountView;
  cameraStatus: CameraStatus;
  isRecording: boolean;
  isPreparing: boolean;
  onStartCamera: () => void;
  onStartTest: () => void;
  onStop: () => void;
  onRedoCalibration: () => void;
  onCancel: () => void;
};

const ROW = "flex flex-wrap gap-3";

function IdleControls({ cameraStatus, isRecording, onStartCamera, onStartTest, onCancel }: Props) {
  if (cameraStatus !== "live") {
    const label = cameraStatus === "starting" ? "Starting camera..." : cameraStatus === "error" ? "Try camera again" : "Start camera";
    return (
      <div className={ROW}>
        <Button onClick={onStartCamera} disabled={cameraStatus === "starting"}>
          {label}
        </Button>
      </div>
    );
  }
  return (
    <div className={ROW}>
      <Button onClick={onStartTest} disabled={isRecording} className="text-lg">
        {isRecording ? "Starting..." : "Start calibration and test"}
      </Button>
      {isRecording && (
        <Button variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
      )}
    </div>
  );
}

/** One primary action per step. Redo is offered before Go, and after Go only when the count cannot run. */
export function CountControls(props: Props) {
  const { stage, countView } = props;
  if (stage === "idle") return <IdleControls {...props} />;
  if (stage === "done") {
    return props.isPreparing ? (
      <p role="status" className="font-semibold">
        Preparing the recording files...
      </p>
    ) : null;
  }
  const canRedo = stage !== "test" || countView.kind === "calibration-failed" || countView.kind === "context-failed";
  return (
    <div className={ROW}>
      {stage === "test" && (
        <Button variant="danger" onClick={props.onStop} className="min-w-32">
          Stop
        </Button>
      )}
      {canRedo && (
        <Button variant={stage === "test" ? "primary" : "secondary"} onClick={props.onRedoCalibration}>
          Redo calibration
        </Button>
      )}
      <Button variant="secondary" onClick={props.onCancel}>
        Cancel test
      </Button>
    </div>
  );
}
