"use client";

import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/Button";
import type { CaptureResult } from "@/hooks/usePoseRecorder";
import type { AssembleResult } from "@/lib/record/assemble";
import { CAPTURE_ISSUE_TEXT } from "@/lib/record/captureIssues";
import { RECORDER_ERROR_TEXT } from "@/lib/record/recorderErrors";
import { recordingFileNames, releaseDownloadUrls, saveBlob, type SaveOutcome } from "@/lib/record/saveFile";
import { summarizeRecording } from "@/lib/record/summary";
import { extensionForMime } from "@/lib/record/videoMime";

export type SavedFile = "landmarks" | "video";

type Props = {
  capture: CaptureResult;
  result: AssembleResult;
  onFileSaved: (kind: SavedFile) => void;
  onNext: () => void;
};

type SaveState = "idle" | "saving" | SaveOutcome | "failed";

const VIDEO_FOLDER = "C:\\SthiraPilot\\videos\\";
const JSON_FOLDER = "the project's pilot-data folder";
const FOLDER: Record<SavedFile, string> = { landmarks: JSON_FOLDER, video: VIDEO_FOLDER };
const KINDS: readonly SavedFile[] = ["landmarks", "video"];

function statusText(kind: SavedFile, state: SaveState): string {
  if (state === "saved") return "Saved.";
  if (state === "downloaded") return `Download started. Check your Downloads folder, then move the file to ${FOLDER[kind]}.`;
  if (state === "cancelled") return "Not saved yet. Press the button again to choose a folder.";
  if (state === "failed") return RECORDER_ERROR_TEXT["save-failed"];
  return state === "saving" ? "Saving..." : "";
}

// A started download is not proof it finished, but the page cannot check further.
const isDone = (state: SaveState) => state === "saved" || state === "downloaded";

function SummaryList({ capture, result }: Pick<Props, "capture" | "result">) {
  const summary = summarizeRecording(result.ok ? result.recording : result.raw);
  const seconds = (value: number | null) => (value === null ? "-" : `${value.toFixed(2)} s`);
  const rows: [string, string][] = [
    ["Test duration (Go to Stop)", seconds(summary.testDurationS)],
    ["Recording length", seconds(summary.recordingLengthS)],
    ["Frames", String(summary.frameCount)],
    ["Frames with a person", `${summary.framesWithPerson} of ${summary.frameCount}`],
    ["Effective frame rate", `${summary.effectiveFps.toFixed(1)} fps`],
    ["Largest gap between frames", seconds(summary.maxFrameGapS)],
    ["Frame clock", capture.frameClock],
    ["Interrupted", capture.issues.length === 0 ? "No" : `Yes: ${capture.issues.join(", ")}`],
  ];
  return (
    <dl className="grid gap-2 sm:grid-cols-2">
      {rows.map(([term, value]) => (
        <div key={term} className="flex flex-col rounded-lg border border-border bg-bg px-3 py-2">
          <dt className="text-sm text-muted">{term}</dt>
          <dd className="text-lg font-bold tabular-nums">{value}</dd>
        </div>
      ))}
    </dl>
  );
}

function Problems({ capture, result }: Pick<Props, "capture" | "result">) {
  if (capture.issues.length === 0 && result.ok) return null;
  return (
    <div role="alert" className="flex flex-col gap-2 rounded-lg border-2 border-danger bg-surface p-3 text-danger">
      {capture.issues.map((issue) => (
        <p key={issue} className="text-lg font-bold">
          {CAPTURE_ISSUE_TEXT[issue]}
        </p>
      ))}
      {capture.issues.length > 0 && <p>The files can still be saved; the landmarks file records the interruption, so it will be left out of tuning.</p>}
      {!result.ok && (
        <>
          <p className="font-bold">
            The landmarks did not pass validation ({result.errorCount} problem{result.errorCount === 1 ? "" : "s"}). Save the raw frames file and the
            video, and tell the coordinator.
          </p>
          <ul className="list-disc pl-5 font-mono text-sm break-all">
            {result.errors.map((message) => (
              <li key={message}>{message}</li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}

export function SaveRecording({ capture, result, onFileSaved, onNext }: Props) {
  const [states, setStates] = useState<Record<SavedFile, SaveState>>({ landmarks: "idle", video: "idle" });
  const headingRef = useRef<HTMLHeadingElement>(null);
  const meta = result.ok ? result.recording.meta : result.raw.meta;
  const names = recordingFileNames(meta, capture.startedAtIso, capture.videoMime);
  const jsonName = result.ok ? names.landmarks : names.landmarks.replace(/\.json$/, ".raw.json");
  const videoType = capture.videoMime.split(";")[0] || "video/webm";

  useEffect(() => {
    headingRef.current?.focus();
    return releaseDownloadUrls;
  }, []);

  const save = async (kind: SavedFile) => {
    setStates((prev) => ({ ...prev, [kind]: "saving" }));
    const json = JSON.stringify(result.ok ? result.recording : result.raw);
    const blob = kind === "landmarks" ? new Blob([json], { type: "application/json" }) : capture.video;
    const options =
      kind === "landmarks"
        ? { suggestedName: jsonName, description: "Sthira landmarks (JSON)", mimeType: "application/json", extension: ".json" }
        : { suggestedName: names.video, description: "Sthira video", mimeType: videoType, extension: `.${extensionForMime(videoType)}` };
    let outcome: SaveState;
    try {
      outcome = await saveBlob(blob, options);
    } catch {
      outcome = "failed";
    }
    setStates((prev) => ({ ...prev, [kind]: outcome }));
    if (isDone(outcome)) onFileSaved(kind);
  };

  const handleNext = () => {
    const unsaved = KINDS.filter((kind) => !isDone(states[kind]));
    if (unsaved.length > 0 && !window.confirm(`Not saved yet: ${unsaved.join(" and ")}. Discard and record the next trial?`)) return;
    onNext();
  };

  const primary: SavedFile | "next" = KINDS.find((kind) => !isDone(states[kind])) ?? "next";
  const variant = (target: SavedFile | "next") => (primary === target ? "primary" : "secondary");
  const jsonLabel = result.ok ? "Save landmarks (.json)" : "Save raw frames (.json)";

  return (
    <section aria-labelledby="save-heading" className="flex flex-col gap-4 rounded-xl border border-border bg-surface p-4 sm:p-6">
      <h2 id="save-heading" ref={headingRef} tabIndex={-1} className="text-xl font-bold">
        3. Save this recording
      </h2>
      <Problems capture={capture} result={result} />
      <SummaryList capture={capture} result={result} />
      <div className="rounded-lg border-2 border-warn bg-warn-bg p-3 text-warn">
        <p className="font-semibold">Where to save</p>
        <ul className="list-disc pl-5">
          <li>
            Video: <span className="font-mono font-semibold break-all">{VIDEO_FOLDER}</span> only. Never OneDrive, phones, chats or cloud folders
            (consent form).
          </li>
          <li>Landmarks (.json): {JSON_FOLDER}.</li>
        </ul>
      </div>
      {KINDS.map((kind) => (
        <div key={kind} className="flex flex-col gap-1">
          <Button variant={variant(kind)} disabled={states[kind] === "saving"} onClick={() => void save(kind)} className="self-start">
            {kind === "landmarks" ? jsonLabel : "Save video"}
          </Button>
          <p className="text-sm break-all text-muted">{kind === "landmarks" ? jsonName : names.video}</p>
          <p role="status" className={`text-sm font-semibold ${states[kind] === "failed" ? "text-danger" : "text-text"}`}>
            {statusText(kind, states[kind])}
          </p>
        </div>
      ))}
      <Button variant={variant("next")} onClick={handleNext} className="self-start">
        Record the next trial
      </Button>
    </section>
  );
}
