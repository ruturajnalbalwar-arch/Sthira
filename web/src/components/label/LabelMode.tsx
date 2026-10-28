"use client";

import { useEffect, useId, useRef, useState } from "react";
import { Button } from "@/components/ui/Button";
import { recordingSchema, type Recording } from "@/lib/record/schema";
import { LABELS_MAX_BYTES, RECORDING_MAX_BYTES, isVideoFile, videoMatchesRecording } from "@/lib/label/files";
import {
  labellerSchema,
  labelsSchema,
  parseJsonWith,
  recordingRef as buildRef,
  sameRecording,
  type LabelEvent,
  type RecordingRef,
} from "@/lib/label/schema";
import { FileField, readCapped } from "./FileField";
import { LabelWorkspace } from "./LabelWorkspace";

interface LoadedRecording {
  recording: Recording;
  ref: RecordingRef;
  fileName: string;
}

interface Resume {
  events: readonly LabelEvent[];
  notes: string;
  fileName: string;
}

interface Session {
  loaded: LoadedRecording;
  resume: Resume | null;
  labeller: { code: string; isExternal: boolean };
  key: number;
}

/** Shown errors (already capped) and the total number of problems. */
interface FieldErrors {
  list: string[];
  total: number;
}

const NO_ERRORS: FieldErrors = { list: [], total: 0 };
const one = (message: string): FieldErrors => ({ list: [message], total: 1 });

async function readRecording(file: File): Promise<{ loaded: LoadedRecording } | { errors: FieldErrors }> {
  const read = await readCapped(file, RECORDING_MAX_BYTES, "a Sthira recording");
  if ("error" in read) return { errors: one(read.error) };
  const parsed = parseJsonWith(recordingSchema, read.text);
  if (!parsed.ok) return { errors: { list: parsed.errors, total: parsed.totalErrors } };
  const ref = buildRef(parsed.data);
  return ref.ok ? { loaded: { recording: parsed.data, ref: ref.ref, fileName: file.name } } : { errors: one(ref.error) };
}

/** Owns a video object URL and revokes it when replaced or unmounted. Nothing leaves the device. */
function useVideoUrl() {
  const [video, setVideo] = useState<{ url: string; name: string } | null>(null);
  useEffect(() => () => {
    if (video !== null) URL.revokeObjectURL(video.url);
  }, [video]);
  const choose = (file: File): FieldErrors => {
    if (!isVideoFile(file.name, file.type)) return one(`${file.name} is not a video file (expected .webm, .mp4 or .mkv).`);
    setVideo({ url: URL.createObjectURL(file), name: file.name });
    return NO_ERRORS;
  };
  return { video, choose };
}

interface LabellerFieldsProps {
  code: string;
  isExternal: boolean;
  showError: boolean;
  onCode: (code: string) => void;
  onExternal: (value: boolean) => void;
}

function LabellerFields({ code, isExternal, showError, onCode, onExternal }: LabellerFieldsProps) {
  const id = useId();
  const check = labellerSchema.shape.code.safeParse(code);
  const error = showError && !check.success ? check.error.issues[0]?.message : undefined;
  return (
    <fieldset className="flex flex-col gap-3">
      <legend className="font-semibold">Labeller</legend>
      <div className="flex flex-col gap-1">
        <label htmlFor={id} className="font-semibold">Labeller code</label>
        <p id={`${id}-hint`} className="text-sm text-muted">L1, L2, … Never a name.</p>
        <input
          id={id}
          value={code}
          onChange={(event) => onCode(event.target.value)}
          autoComplete="off"
          aria-invalid={error !== undefined}
          aria-describedby={error === undefined ? `${id}-hint` : `${id}-hint ${id}-error`}
          className="min-h-11 w-40 rounded-lg border border-border-strong bg-surface px-3 font-mono text-base"
        />
        {error !== undefined && <p id={`${id}-error`} className="text-sm text-danger">{error}</p>}
      </div>
      <label className="flex min-h-11 cursor-pointer items-center gap-3">
        <input type="checkbox" checked={isExternal} onChange={(event) => onExternal(event.target.checked)} className="size-5 accent-primary" />
        I am the outside labeller (not on the student team)
      </label>
    </fieldset>
  );
}

/** All load-step state: labeller, the three files, their inline errors and the open session. */
function useLoadStep() {
  const [code, setCode] = useState("");
  const [isExternal, setIsExternal] = useState(false);
  const [loaded, setLoaded] = useState<LoadedRecording | null>(null);
  const [resume, setResume] = useState<Resume | null>(null);
  const [errors, setErrors] = useState({ recording: NO_ERRORS, video: NO_ERRORS, resume: NO_ERRORS });
  const [session, setSession] = useState<Session | null>(null);
  const { video, choose } = useVideoUrl();
  const setField = (field: keyof typeof errors, value: FieldErrors) => setErrors((e) => ({ ...e, [field]: value }));
  const onRecording = async (file: File) => {
    const result = await readRecording(file);
    setResume(null);
    setLoaded("loaded" in result ? result.loaded : null);
    setErrors((e) => ({ ...e, resume: NO_ERRORS, recording: "errors" in result ? result.errors : NO_ERRORS }));
  };
  const onResume = async (file: File) => {
    const read = await readCapped(file, LABELS_MAX_BYTES, "a label file");
    if ("error" in read) return setField("resume", one(read.error));
    const parsed = parseJsonWith(labelsSchema, read.text);
    if (!parsed.ok) return setField("resume", { list: parsed.errors, total: parsed.totalErrors });
    if (loaded === null || !sameRecording(parsed.data.recording, loaded.ref)) {
      return setField("resume", one("This labels file belongs to a different recording."));
    }
    setResume({ events: parsed.data.events, notes: parsed.data.notes, fileName: file.name });
    setCode(parsed.data.labeller.code);
    setIsExternal(parsed.data.labeller.isExternal);
    setField("resume", NO_ERRORS);
  };
  const codeOk = labellerSchema.shape.code.safeParse(code).success;
  const start = () => {
    if (!codeOk || loaded === null || video === null) return false;
    setSession({ loaded, resume, labeller: { code: code.trim(), isExternal }, key: Date.now() });
    return true;
  };
  const close = () => {
    setSession(null);
    setResume(null);
  };
  const onVideo = (file: File) => setField("video", choose(file));
  const missing = [loaded === null && "the recording file", video === null && "the video file", !codeOk && "a labeller code"];
  return { code, setCode, isExternal, setIsExternal, loaded, resume, errors, session, video, onRecording, onResume, onVideo, start, close, missing: missing.filter((m): m is string => m !== false) };
}

/** Back from the workspace: put focus on this step's heading (not on first page load). */
function useReturnFocus() {
  const headingRef = useRef<HTMLHeadingElement>(null);
  const pending = useRef(false);
  useEffect(() => {
    if (!pending.current) return;
    pending.current = false;
    headingRef.current?.focus();
  });
  return {
    headingRef,
    focusOnReturn: () => {
      pending.current = true;
    },
  };
}

export function LabelMode({ active }: { active: boolean }) {
  const step = useLoadStep();
  const { session, video, loaded, resume, errors } = step;
  const [triedStart, setTriedStart] = useState(false);
  const { headingRef, focusOnReturn } = useReturnFocus();
  if (session !== null && video !== null) {
    return (
      <LabelWorkspace
        key={session.key}
        recording={session.loaded.recording}
        recordingRef={session.loaded.ref}
        videoUrl={video.url}
        videoName={video.name}
        labeller={session.labeller}
        initialEvents={session.resume?.events ?? []}
        initialNotes={session.resume?.notes ?? ""}
        active={active}
        onClose={() => {
          step.close();
          focusOnReturn();
        }}
      />
    );
  }
  const nameMismatch = loaded !== null && video !== null && !videoMatchesRecording(video.name, loaded.ref.fileBase);
  return (
    <section aria-labelledby="load-heading" className="flex max-w-2xl flex-col gap-6">
      <h2 id="load-heading" ref={headingRef} tabIndex={-1} className="text-lg font-semibold">1. Load a recording</h2>
      <p className="text-muted">Both files stay on this computer. Nothing is uploaded.</p>
      <LabellerFields code={step.code} isExternal={step.isExternal} showError={triedStart || step.code !== ""} onCode={step.setCode} onExternal={step.setIsExternal} />
      <FileField
        label="Recording file (.json)"
        hint="The landmark recording saved by Sthira record mode (sthira.recording.v1)."
        accept="application/json,.json"
        errors={errors.recording.list}
        totalErrors={errors.recording.total}
        loaded={loaded === null ? null : `Loaded ${loaded.fileName}: ${loaded.ref.participantCode}, ${loaded.ref.trial}, ${loaded.ref.frameCount} frames.`}
        onFile={(file) => void step.onRecording(file)}
      />
      <FileField
        label="Video file"
        hint="The video recorded together with that recording (.webm, .mp4 or .mkv)."
        accept="video/*,.webm,.mp4,.mkv"
        errors={errors.video.list}
        loaded={video === null ? null : `Loaded ${video.name}.`}
        onFile={step.onVideo}
      />
      {nameMismatch && (
        <p className="rounded-lg border border-warn bg-warn-bg p-3 text-sm text-warn">
          The video name does not match the recording ({loaded.ref.fileBase}). Record mode saves both under the same name. Check that this is the right video; you can still continue.
        </p>
      )}
      <FileField
        label="Continue from a saved labels file (optional)"
        hint={loaded === null ? "Load the recording first." : "Only if you already exported labels for this recording and want to keep working on them."}
        accept="application/json,.json"
        errors={errors.resume.list}
        totalErrors={errors.resume.total}
        loaded={resume === null ? null : `Continuing ${resume.fileName} (${resume.events.length} marks).`}
        disabled={loaded === null}
        onFile={(file) => void step.onResume(file)}
      />
      <div className="flex flex-col gap-2">
        <Button onClick={() => setTriedStart(!step.start())} className="self-start">Start labelling</Button>
        {triedStart && step.missing.length > 0 && <p role="alert" className="text-sm text-danger">Still needed: {step.missing.join(", ")}.</p>}
      </div>
    </section>
  );
}
