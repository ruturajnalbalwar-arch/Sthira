"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { usePhaseMachine } from "@/hooks/usePhaseMachine";
import { useLeaveGuard } from "@/hooks/useLeaveGuard";
import { usePoseRecorder } from "@/hooks/usePoseRecorder";
import { describePhase, machineConfigFor } from "@/lib/record/phaseMachine";
import type { SessionMeta } from "@/lib/record/schema";
import { EMPTY_SESSION_FORM, sessionToForm, type SessionFormValues } from "@/lib/record/sessionForm";
import type { ProtocolConstants } from "@/lib/sthira/params/protocol";
import { CameraStage } from "./CameraStage";
import { CaptureAlerts } from "./CaptureAlerts";
import { CaptureControls } from "./CaptureControls";
import { PhaseBanner } from "./PhaseBanner";
import { buildReview, type Review } from "./review";
import { SaveRecording, type SavedFile } from "./SaveRecording";
import { SessionForm } from "./SessionForm";
import { PrivacyBanner, SessionLine } from "./StudioChrome";

type Props = { constants: ProtocolConstants };

/** Countdown/elapsed display refresh (~10 Hz). Display only; phase boundaries are exact. */
const COUNTDOWN_REFRESH_MS = 100;
const IDLE_HINT_LIVE = "Check the whole body, head to feet, and the chair are in frame, sitting and standing. Then press Start recording.";
const IDLE_HINT_OFF = "Press Start camera. The browser will ask for camera permission.";
const CANCEL_CONFIRM = "Cancel this recording? The video and frames recorded so far will be discarded.";

export function RecordStudio({ constants }: Props) {
  const [formValues, setFormValues] = useState<SessionFormValues>(EMPTY_SESSION_FORM);
  const [meta, setMeta] = useState<SessionMeta | null>(null);
  const [review, setReview] = useState<Review | null>(null);
  const [savedFiles, setSavedFiles] = useState<readonly SavedFile[]>([]);
  const [isPreparing, setIsPreparing] = useState(false);
  const [nowS, setNowS] = useState(0);

  const config = useMemo(() => machineConfigFor(constants, meta?.trial ?? "chair-stand-30s"), [constants, meta]);
  const { state: phase, send, current } = usePhaseMachine(config);
  const finishRef = useRef<() => void>(() => undefined);
  const finishingRef = useRef(false);

  /** Frame times (and the 10 Hz timer) drive the flow; t = 0 is the first recorded frame. */
  const handleRecordingTime = useCallback(
    (t: number) => {
      if (current().stage === "idle") send({ type: "start", t: 0 });
      const { previous, next } = send({ type: "tick", t });
      if (previous.stage !== "done" && next.stage === "done") finishRef.current();
    },
    [send, current],
  );

  const recorderOptions = useMemo(
    () => ({ onRecordingTime: handleRecordingTime, onCaptureMustEnd: () => finishRef.current() }),
    [handleRecordingTime],
  );
  const recorder = usePoseRecorder(recorderOptions);
  const { startCamera, stopCamera, startRecording, stopRecording, discardRecording, elapsedS, isRecording } = recorder;

  const finish = useCallback(async () => {
    if (finishingRef.current) return;
    finishingRef.current = true;
    send({ type: "interrupt", t: elapsedS() ?? 0 });
    setIsPreparing(true);
    const capture = await stopRecording();
    finishingRef.current = false;
    setIsPreparing(false);
    if (capture === null || meta === null) return;
    setSavedFiles([]);
    setReview(buildReview(meta, capture, current().phases));
  }, [send, elapsedS, stopRecording, meta, current]);

  useEffect(() => {
    finishRef.current = () => void finish();
  }, [finish]);

  useEffect(() => {
    if (!isRecording) return;
    const id = setInterval(() => {
      const t = elapsedS();
      if (t === null) return;
      setNowS(t);
      handleRecordingTime(t);
    }, COUNTDOWN_REFRESH_MS);
    return () => clearInterval(id);
  }, [isRecording, elapsedS, handleRecordingTime]);

  useLeaveGuard(isRecording || (review !== null && savedFiles.length < 2));

  const handleSessionSubmit = (next: SessionMeta) => {
    setMeta(next);
    setFormValues(sessionToForm(next));
    setReview(null);
  };

  const backToForm = () => {
    stopCamera();
    send({ type: "reset" });
    setReview(null);
    setMeta(null);
  };

  const handleStartRecording = () => {
    setNowS(0);
    startRecording();
  };

  const handleStop = () => {
    const t = elapsedS();
    if (t === null) return;
    const { previous, next } = send({ type: "stop", t });
    if (previous.stage !== "done" && next.stage === "done") void finish();
  };

  const handleRedo = () => {
    const t = elapsedS();
    if (t !== null) send({ type: "redo-calibration", t });
  };

  const handleCancel = () => {
    if (!window.confirm(CANCEL_CONFIRM)) return;
    discardRecording();
    send({ type: "cancel" });
  };

  const handleRetry = () => {
    setReview(null);
    send({ type: "reset" });
  };

  const handleFileSaved = (kind: SavedFile) => setSavedFiles((prev) => (prev.includes(kind) ? prev : [...prev, kind]));

  const view = describePhase(phase, nowS, config);
  const canEdit = phase.stage === "idle" && !isRecording && recorder.status !== "starting";

  return (
    <main className="mx-auto flex w-full max-w-5xl flex-col gap-4 px-4 py-6 sm:py-8">
      <header className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold sm:text-3xl">Sthira record mode</h1>
        <p className="text-muted">Pilot recordings of the CDC STEADI tests. Use participant codes only, never names.</p>
      </header>
      <PrivacyBanner />
      {meta === null ? (
        <SessionForm initialValues={formValues} onSubmit={handleSessionSubmit} />
      ) : (
        <>
          <SessionLine meta={meta} canEdit={canEdit} onEdit={backToForm} />
          <div className="grid gap-4 lg:grid-cols-2 lg:items-start">
            <div className="flex flex-col gap-4">
              <PhaseBanner stage={phase.stage} view={view} idleHint={recorder.status === "live" ? IDLE_HINT_LIVE : IDLE_HINT_OFF} />
              <CaptureControls
                stage={phase.stage}
                cameraStatus={recorder.status}
                isRecording={isRecording}
                isPreparing={isPreparing}
                canRetry={review === null}
                onStartCamera={() => void startCamera()}
                onStartRecording={handleStartRecording}
                onStop={handleStop}
                onRedoCalibration={handleRedo}
                onCancel={handleCancel}
                onRetry={handleRetry}
              />
              <CaptureAlerts error={recorder.error} issues={recorder.issues} isRecording={isRecording} secondsToFrameLimit={recorder.stats.secondsToFrameLimit} />
              {review !== null && <SaveRecording capture={review.capture} result={review.result} onFileSaved={handleFileSaved} onNext={backToForm} />}
            </div>
            <CameraStage
              videoRef={recorder.videoRef}
              canvasRef={recorder.canvasRef}
              status={recorder.status}
              stats={recorder.stats}
              delegate={recorder.delegate}
              isRecording={isRecording}
            />
          </div>
        </>
      )}
    </main>
  );
}
