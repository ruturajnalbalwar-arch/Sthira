"use client";

import { useState, type ReactNode } from "react";
import { formatSeconds } from "@/lib/label/reducer";
import {
  FrameBackIcon,
  FrameForwardIcon,
  PauseIcon,
  PlayIcon,
  SecondBackIcon,
  SecondForwardIcon,
} from "./Icons";
import { readPlaying, readRate, readTime, useVideoValue } from "./useVideoValue";

/** Playback speeds offered by `,` and `.` (a UI choice for slowing down disputed moments). */
export const PLAYBACK_RATES = [0.25, 0.5, 1, 1.5, 2] as const;

interface ControlProps {
  label: string;
  shortcut: string;
  onClick: () => void;
  children: ReactNode;
}

function Control({ label, shortcut, onClick, children }: ControlProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      aria-keyshortcuts={shortcut}
      title={`${label} (${shortcut})`}
      className="inline-flex min-h-11 min-w-11 cursor-pointer items-center justify-center rounded-lg border border-border-strong bg-surface text-text transition-colors duration-150 hover:bg-bg"
    >
      {children}
    </button>
  );
}

/** Re-renders with the video; kept small so the rest of the workspace does not. */
function PlayButton({ video, onToggle }: { video: HTMLVideoElement | null; onToggle: () => void }) {
  const playing = useVideoValue(video, readPlaying, false);
  return (
    <Control label={playing ? "Pause" : "Play"} shortcut="Space" onClick={onToggle}>
      {playing ? <PauseIcon /> : <PlayIcon />}
    </Control>
  );
}

function SpeedReadout({ video }: { video: HTMLVideoElement | null }) {
  const rate = useVideoValue(video, readRate, 1);
  return (
    <span className="min-w-14 text-center font-mono">
      <span className="sr-only">Speed </span>
      {rate}×
    </span>
  );
}

function TimeReadout({ video, goT }: { video: HTMLVideoElement | null; goT: number }) {
  const t = useVideoValue(video, readTime, 0, true);
  const sinceGo = t - goT;
  return (
    <p className="ml-auto font-mono text-sm">
      <span className="sr-only">Video time </span>
      {formatSeconds(t)}
      <span className="text-muted">
        {" "}
        · Go {sinceGo >= 0 ? "+" : "−"}
        {formatSeconds(Math.abs(sinceGo))}
      </span>
    </p>
  );
}

/** MediaError.code values from the HTML spec (the MediaError global does not exist during server render). */
const MEDIA_ERROR_TEXT: Readonly<Record<number, string>> = {
  1: "loading was stopped",
  2: "the file could not be read",
  3: "the video data is damaged or uses an unsupported codec",
  4: "this browser cannot play this file type",
};

interface VideoPlayerProps {
  video: HTMLVideoElement | null;
  onVideoElement: (video: HTMLVideoElement | null) => void;
  src: string;
  width: number;
  height: number;
  goT: number;
  onTogglePlay: () => void;
  onStepFrame: (direction: -1 | 1) => void;
  onStepSecond: (direction: -1 | 1) => void;
  onChangeSpeed: (direction: -1 | 1) => void;
}

/** Video with custom keyboard-equivalent controls. Space is reserved from the recording's own size. */
export function VideoPlayer({
  video,
  onVideoElement,
  src,
  width,
  height,
  goT,
  onTogglePlay,
  onStepFrame,
  onStepSecond,
  onChangeSpeed,
}: VideoPlayerProps) {
  const [error, setError] = useState<string | null>(null);
  const onError = (element: HTMLVideoElement) => {
    const code = element.error?.code ?? 0;
    setError(`Can't play this video: ${MEDIA_ERROR_TEXT[code] ?? "unknown error"}. Try Chrome or Edge, or check the file.`);
  };
  return (
    <section aria-label="Video" className="flex flex-col gap-3">
      <div className="w-full overflow-hidden rounded-lg border border-border bg-surface" style={{ aspectRatio: `${width} / ${height}` }}>
        <video
          ref={onVideoElement}
          src={src}
          playsInline
          preload="auto"
          onError={(event) => onError(event.currentTarget)}
          onLoadedMetadata={() => setError(null)}
          className="h-full w-full object-contain"
        >
          <track kind="captions" />
        </video>
      </div>
      {error !== null && (
        <p role="alert" className="rounded-lg border border-danger p-3 text-danger">
          {error}
        </p>
      )}
      <div className="flex flex-wrap items-center gap-2">
        <PlayButton video={video} onToggle={onTogglePlay} />
        <Control label="Back 1 second" shortcut="Shift+ArrowLeft" onClick={() => onStepSecond(-1)}>
          <SecondBackIcon />
        </Control>
        <Control label="Previous frame" shortcut="ArrowLeft" onClick={() => onStepFrame(-1)}>
          <FrameBackIcon />
        </Control>
        <Control label="Next frame" shortcut="ArrowRight" onClick={() => onStepFrame(1)}>
          <FrameForwardIcon />
        </Control>
        <Control label="Forward 1 second" shortcut="Shift+ArrowRight" onClick={() => onStepSecond(1)}>
          <SecondForwardIcon />
        </Control>
        <Control label="Slower" shortcut="," onClick={() => onChangeSpeed(-1)}>
          <span aria-hidden="true" className="font-mono font-bold">−</span>
        </Control>
        <SpeedReadout video={video} />
        <Control label="Faster" shortcut="." onClick={() => onChangeSpeed(1)}>
          <span aria-hidden="true" className="font-mono font-bold">+</span>
        </Control>
        <TimeReadout video={video} goT={goT} />
      </div>
    </section>
  );
}
