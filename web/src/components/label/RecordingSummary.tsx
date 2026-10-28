"use client";

import type { ReactNode } from "react";
import { formatSeconds } from "@/lib/label/reducer";
import { videoMatchesRecording } from "@/lib/label/files";
import type { RecordingRef } from "@/lib/label/schema";
import type { Recording } from "@/lib/record/schema";
import { readLength, useVideoValue } from "./useVideoValue";

const FRAME_CLOCK_TEXT: Readonly<Record<Recording["capture"]["frameClock"], string>> = {
  "capture-time": "camera capture time",
  "callback-time": "callback time (timing less precise)",
};

function Item({ term, children }: { term: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-muted">{term}</dt>
      <dd className="font-mono">{children}</dd>
    </div>
  );
}

/** Video length and landmark span side by side, with no threshold: the labeller judges the match. */
function LengthItems({ video, recordingRef }: { video: HTMLVideoElement | null; recordingRef: RecordingRef }) {
  const length = useVideoValue(video, readLength, "loading");
  const text = length === "loading" ? "loading…" : length === "unknown" ? "unknown" : formatSeconds(length);
  return (
    <>
      <Item term="Video length">{text}</Item>
      <Item term="Landmark span">{formatSeconds(recordingRef.lastT - recordingRef.firstT)}</Item>
      {length === "unknown" && (
        <p className="col-span-full rounded-lg border border-warn bg-warn-bg p-2 text-warn">
          This video file stores no length (common for Chrome WebM recordings). It still plays; seeking may be slow
          and land near a frame rather than on it.
        </p>
      )}
    </>
  );
}

interface SummaryProps {
  recording: Recording;
  recordingRef: RecordingRef;
  fps: number;
  video: HTMLVideoElement | null;
  videoName: string;
}

export function RecordingSummary({ recording, recordingRef, fps, video, videoName }: SummaryProps) {
  const { meta } = recording;
  const matches = videoMatchesRecording(videoName, recordingRef.fileBase);
  return (
    <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm sm:grid-cols-4">
      <Item term="Participant">{meta.participantCode}</Item>
      <Item term="Trial">{meta.trial}</Item>
      <Item term="Clothing">{meta.clothing}</Item>
      <Item term="Go (test start)">{formatSeconds(recordingRef.goT)}</Item>
      <Item term="Landmark frames">{recordingRef.frameCount} · {fps.toFixed(1)} fps</Item>
      <Item term="Frame times">{FRAME_CLOCK_TEXT[recording.capture.frameClock]}</Item>
      <LengthItems video={video} recordingRef={recordingRef} />
      <div className="col-span-full">
        <dt className="text-muted">Video file</dt>
        <dd className="break-all font-mono">
          {videoName}
          {!matches && <span className="ml-2 font-sans text-warn">(name differs from the recording {recordingRef.fileBase})</span>}
        </dd>
      </div>
    </dl>
  );
}
