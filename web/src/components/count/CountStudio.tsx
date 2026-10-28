"use client";

import { useState } from "react";
import { CameraStage } from "@/components/record/CameraStage";
import { CaptureAlerts } from "@/components/record/CaptureAlerts";
import { SaveRecording, type SavedFile } from "@/components/record/SaveRecording";
import { SessionForm } from "@/components/record/SessionForm";
import { PrivacyBanner, SessionLine } from "@/components/record/StudioChrome";
import { useCountSession } from "@/hooks/useCountSession";
import { useLeaveGuard } from "@/hooks/useLeaveGuard";
import type { CounterStatus } from "@/lib/count/counterStatus";
import type { NormSex, NormsParseResult } from "@/lib/count/norms";
import { TRIAL_LABELS } from "@/lib/record/labels";
import type { SessionMeta } from "@/lib/record/schema";
import { EMPTY_SESSION_FORM, sessionToForm, type SessionFormValues } from "@/lib/record/sessionForm";
import type { ProtocolConstants } from "@/lib/sthira/params/protocol";
import { CountAlerts } from "./CountAlerts";
import { CountBoard } from "./CountBoard";
import { CountControls } from "./CountControls";
import { CounterNotice } from "./CounterNotice";
import { CountResult } from "./CountResult";
import { SexField } from "./SexField";

type Props = {
  constants: ProtocolConstants;
  counter: CounterStatus;
  norms: NormsParseResult;
};

const CHAIR_STAND = "chair-stand-30s";
const TRIAL_OPTIONS = { [CHAIR_STAND]: TRIAL_LABELS[CHAIR_STAND] };
const INITIAL_FORM: SessionFormValues = { ...EMPTY_SESSION_FORM, trial: CHAIR_STAND };
const IDLE_HINT_LIVE = "Check that the whole body, head to feet, and the chair are in view, sitting and standing. Then start.";
const IDLE_HINT_OFF = "Press Start camera. The browser will ask for camera permission.";
const SEX_REQUIRED = "Choose one, or Not recorded";
const RUN_AGAIN_CONFIRM = "This run's files are not saved yet. Discard them and run again?";

export function CountStudio({ constants, counter, norms }: Props) {
  const [formValues, setFormValues] = useState<SessionFormValues>(INITIAL_FORM);
  const [meta, setMeta] = useState<SessionMeta | null>(null);
  const [sex, setSex] = useState<NormSex | "">("");
  const [sexError, setSexError] = useState<string | undefined>(undefined);
  const [savedFiles, setSavedFiles] = useState<readonly SavedFile[]>([]);
  const session = useCountSession(constants, counter, meta);
  const { recorder, phase, review } = session;

  useLeaveGuard(recorder.isRecording || (review !== null && savedFiles.length < 2));

  const handleSessionSubmit = (next: SessionMeta) => {
    if (sex === "") {
      setSexError(SEX_REQUIRED);
      document.getElementById("normSex")?.focus();
      return;
    }
    setSexError(undefined);
    setMeta(next);
    setFormValues(sessionToForm(next));
  };

  const backToForm = () => {
    recorder.stopCamera();
    session.reset();
    setSavedFiles([]);
    setMeta(null);
  };

  const runAgain = () => {
    if (review !== null && savedFiles.length < 2 && !window.confirm(RUN_AGAIN_CONFIRM)) return;
    setSavedFiles([]);
    session.reset();
  };

  const handleFileSaved = (kind: SavedFile) => setSavedFiles((prev) => (prev.includes(kind) ? prev : [...prev, kind]));

  const canEdit = phase.stage === "idle" && !recorder.isRecording && recorder.status !== "starting";
  const sexField = <SexField value={sex} error={sexError} onChange={setSex} />;

  return (
    <main className="mx-auto flex w-full max-w-6xl flex-col gap-4 px-4 py-6 sm:py-8">
      <header className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold sm:text-3xl">30-second chair stand</h1>
        <p className="text-muted">
          CDC STEADI test, counted from head and shoulder height, so loose clothing over the knees is fine. Use participant codes only, never names.
        </p>
      </header>
      <PrivacyBanner />
      <CounterNotice counter={counter} />
      {meta === null || sex === "" ? (
        <SessionForm initialValues={formValues} onSubmit={handleSessionSubmit} trialOptions={TRIAL_OPTIONS} extra={sexField} />
      ) : (
        <>
          <SessionLine meta={meta} canEdit={canEdit} onEdit={backToForm} />
          <div className="grid gap-4 lg:grid-cols-[3fr_2fr] lg:items-start">
            <div className="flex flex-col gap-4">
              <CountBoard
                stage={phase.stage}
                phaseView={session.phaseView}
                countView={session.countView}
                idleHint={recorder.status === "live" ? IDLE_HINT_LIVE : IDLE_HINT_OFF}
              />
              <CountControls
                stage={phase.stage}
                countView={session.countView}
                cameraStatus={recorder.status}
                isRecording={recorder.isRecording}
                isPreparing={session.isPreparing}
                onStartCamera={() => void recorder.startCamera()}
                onStartTest={session.startTest}
                onStop={session.stop}
                onRedoCalibration={session.redoCalibration}
                onCancel={session.cancel}
              />
              <CountAlerts stage={phase.stage} countView={session.countView} stats={recorder.stats} isRecording={recorder.isRecording} />
              <CaptureAlerts error={recorder.error} issues={recorder.issues} isRecording={recorder.isRecording} secondsToFrameLimit={recorder.stats.secondsToFrameLimit} />
              {phase.stage === "done" && !session.isPreparing && (
                <CountResult
                  countView={session.countView}
                  norms={norms}
                  ageBand={meta.ageBand}
                  sex={sex}
                  durationS={constants.chairStandDurationS}
                  onRunAgain={runAgain}
                />
              )}
              {review !== null && (
                <SaveRecording
                  key={review.capture.startedAtIso}
                  capture={review.capture} result={review.result} onFileSaved={handleFileSaved} onNext={backToForm} nextLabel="Start a new session" />
              )}
            </div>
            <CameraStage
              videoRef={recorder.videoRef}
              canvasRef={recorder.canvasRef}
              status={recorder.status}
              stats={recorder.stats}
              delegate={recorder.delegate}
              isRecording={recorder.isRecording}
            />
          </div>
        </>
      )}
    </main>
  );
}
