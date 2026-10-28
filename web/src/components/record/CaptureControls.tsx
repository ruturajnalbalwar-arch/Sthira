import { Button } from "@/components/ui/Button";
import type { CameraStatus } from "@/hooks/usePoseRecorder";
import type { Stage } from "@/lib/record/phaseMachine";

type Props = {
  stage: Stage;
  cameraStatus: CameraStatus;
  /** Recording requested; waiting for the recorder to start and the first frame to arrive. */
  isRecording: boolean;
  /** True after Stop while the files are being assembled. */
  isPreparing: boolean;
  canRetry: boolean;
  onStartCamera: () => void;
  onStartRecording: () => void;
  onStop: () => void;
  onRedoCalibration: () => void;
  onCancel: () => void;
  onRetry: () => void;
};

const ROW = "flex flex-wrap gap-3";

function IdleControls({ cameraStatus, isRecording, onStartCamera, onStartRecording, onCancel }: Props) {
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
  if (isRecording) {
    return (
      <div className={ROW}>
        <Button disabled>Starting recording...</Button>
        <Button variant="secondary" onClick={onCancel}>
          Cancel recording
        </Button>
      </div>
    );
  }
  return (
    <div className={ROW}>
      <Button onClick={onStartRecording}>Start recording</Button>
    </div>
  );
}

/** One primary action per moment of the flow; secondary actions sit beside it. */
export function CaptureControls(props: Props) {
  const { stage } = props;
  if (stage === "idle") return <IdleControls {...props} />;

  if (stage === "calibration-sit" || stage === "calibration-stand" || stage === "test") {
    return (
      <div className={ROW}>
        {stage === "test" ? (
          <Button variant="danger" onClick={props.onStop} className="min-w-32">
            Stop
          </Button>
        ) : (
          // Redo is only offered before Go: once the test runs, a redo would silently restart it.
          <Button variant="secondary" onClick={props.onRedoCalibration}>
            Redo calibration
          </Button>
        )}
        <Button variant="secondary" onClick={props.onCancel}>
          Cancel recording
        </Button>
      </div>
    );
  }

  if (props.isPreparing) {
    return (
      <p role="status" className="font-semibold">
        Preparing the files...
      </p>
    );
  }
  return props.canRetry ? (
    <div className={ROW}>
      <Button onClick={props.onRetry}>Record this trial again</Button>
    </div>
  ) : null;
}
