import type { RefObject } from "react";
import type { CameraStatus, LiveStats } from "@/hooks/usePoseRecorder";
import type { PoseDelegate } from "@/lib/pose/createPoseLandmarker";

type Props = {
  videoRef: RefObject<HTMLVideoElement | null>;
  canvasRef: RefObject<HTMLCanvasElement | null>;
  status: CameraStatus;
  stats: LiveStats;
  delegate: PoseDelegate | null;
  isRecording: boolean;
};

const PLACEHOLDER: Record<CameraStatus, string> = {
  off: "Camera is off. Nothing is recorded until you press Start recording.",
  starting: "Starting the camera and loading the pose model...",
  live: "",
  error: "Camera stopped.",
};

function StatusItem({ term, value, tone = "plain" }: { term: string; value: string; tone?: "plain" | "good" | "warn" }) {
  const toneClass = tone === "good" ? "bg-success-bg text-success" : tone === "warn" ? "bg-warn-bg text-warn" : "bg-surface text-text";
  return (
    <div className={`flex flex-col rounded-lg border border-border px-3 py-2 ${toneClass}`}>
      <dt className="text-xs font-semibold uppercase tracking-wide">{term}</dt>
      <dd className="text-lg font-bold tabular-nums">{value}</dd>
    </div>
  );
}

function RecordingBadge() {
  return (
    <p className="absolute top-2 left-2 flex items-center gap-2 rounded-full bg-danger px-3 py-1 text-sm font-bold text-on-danger">
      <svg aria-hidden="true" viewBox="0 0 10 10" className="size-3 motion-safe:animate-pulse">
        <circle cx="5" cy="5" r="5" fill="currentColor" />
      </svg>
      Recording
    </p>
  );
}

function LiveStatus({ status, stats, delegate }: Pick<Props, "status" | "stats" | "delegate">) {
  const live = status === "live";
  const person = !live ? "-" : stats.personDetected ? "Yes" : "No";
  return (
    <dl className="grid grid-cols-2 gap-2 sm:grid-cols-4" aria-label="Live tracking status">
      <StatusItem term="Frame rate" value={live && stats.fps !== null ? `${stats.fps.toFixed(1)} fps` : "-"} />
      <StatusItem term="Person detected" value={person} tone={!live ? "plain" : stats.personDetected ? "good" : "warn"} />
      <StatusItem term="Pose engine" value={delegate ?? "-"} />
      <StatusItem term="Frames recorded" value={String(stats.frameCount)} />
    </dl>
  );
}

/**
 * Live camera preview with the pose overlay. The box keeps a fixed aspect ratio so nothing shifts
 * when the camera starts; video and canvas share intrinsic size and object-fit, so they line up.
 * Both are mirrored like a selfie view; the saved video and landmarks are not mirrored.
 */
export function CameraStage({ videoRef, canvasRef, status, stats, delegate, isRecording }: Props) {
  const media = "absolute inset-0 h-full w-full -scale-x-100 object-contain";
  return (
    <section aria-labelledby="camera-heading" className="flex flex-col gap-3">
      <h2 id="camera-heading" className="sr-only">
        Camera
      </h2>
      <div className="relative aspect-[4/3] w-full overflow-hidden rounded-xl border border-border-strong bg-surface">
        <video ref={videoRef} className={media} muted playsInline />
        <canvas ref={canvasRef} className={`${media} pointer-events-none`} aria-hidden="true" />
        {status !== "live" && (
          <p className="absolute inset-0 flex items-center justify-center p-6 text-center font-medium text-muted">{PLACEHOLDER[status]}</p>
        )}
        {isRecording && <RecordingBadge />}
      </div>
      <LiveStatus status={status} stats={stats} delegate={delegate} />
    </section>
  );
}
