"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { buildReview, type Review } from "@/components/record/review";
import { useLiveCount } from "@/hooks/useLiveCount";
import { usePhaseMachine } from "@/hooks/usePhaseMachine";
import { usePoseRecorder, type PoseRecorder } from "@/hooks/usePoseRecorder";
import type { CounterStatus } from "@/lib/count/counterStatus";
import { describeLiveCount, type LiveCountView } from "@/lib/count/liveCount";
import { machineConfigFor, type PhaseEvent, type PhaseState, type PhaseView, describePhase } from "@/lib/record/phaseMachine";
import type { RecordedFrame, SessionMeta } from "@/lib/record/schema";
import type { ProtocolConstants } from "@/lib/sthira/params/protocol";
import type { VideoGeometry } from "@/lib/sthira/types";

/** Countdown and live-count refresh (~10 Hz). Display only; phase boundaries and the count use frame times. */
const DISPLAY_REFRESH_MS = 100;
const CHAIR_STAND = "chair-stand-30s";

export interface CountSession {
  readonly recorder: PoseRecorder;
  readonly phase: PhaseState;
  readonly phaseView: PhaseView;
  readonly countView: LiveCountView;
  readonly review: Review | null;
  readonly isPreparing: boolean;
  startTest(): void;
  stop(): void;
  redoCalibration(): void;
  cancel(): void;
  /** Back to idle after a finished run, keeping the session details. */
  reset(): void;
}

const videoGeometry = (video: HTMLVideoElement | null): VideoGeometry => ({ width: video?.videoWidth ?? 0, height: video?.videoHeight ?? 0 });

/**
 * One guided 30 s chair-stand run: phase machine + pose recorder + live counter. Every recorded
 * frame drives the phases first (frame t), then the counter, so both use the recording's own clock.
 */
export function useCountSession(constants: ProtocolConstants, counter: CounterStatus, meta: SessionMeta | null): CountSession {
  const config = useMemo(() => machineConfigFor(constants, CHAIR_STAND), [constants]);
  const { state: phase, send, current } = usePhaseMachine(config);
  const live = useLiveCount(counter);
  const { dispatch: dispatchLive, refresh: refreshLive } = live;
  const [review, setReview] = useState<Review | null>(null);
  const [isPreparing, setIsPreparing] = useState(false);
  const [nowS, setNowS] = useState(0);
  const finishRef = useRef<() => void>(() => undefined);
  const finishingRef = useRef(false);
  const geometryRef = useRef<() => VideoGeometry>(() => videoGeometry(null));

  const sendPhase = useCallback(
    (event: PhaseEvent) => {
      const transition = send(event);
      if (transition.next !== transition.previous) dispatchLive({ type: "phase", phase: transition.next, geometry: geometryRef.current() });
      return transition;
    },
    [send, dispatchLive],
  );

  const handleRecordingTime = useCallback(
    (t: number) => {
      if (current().stage === "idle") sendPhase({ type: "start", t: 0 });
      const { previous, next } = sendPhase({ type: "tick", t });
      if (previous.stage !== "done" && next.stage === "done") finishRef.current();
    },
    [sendPhase, current],
  );

  const recorderOptions = useMemo(
    () => ({
      onRecordingTime: handleRecordingTime,
      onFrame: (frame: RecordedFrame) => dispatchLive({ type: "frame", frame }),
      onCaptureMustEnd: () => finishRef.current(),
    }),
    [handleRecordingTime, dispatchLive],
  );
  const recorder = usePoseRecorder(recorderOptions);
  const { startRecording, stopRecording, discardRecording, elapsedS, isRecording, videoRef } = recorder;

  useEffect(() => {
    geometryRef.current = () => videoGeometry(videoRef.current);
  }, [videoRef]);

  const finish = useCallback(async () => {
    if (finishingRef.current) return;
    finishingRef.current = true;
    sendPhase({ type: "interrupt", t: elapsedS() ?? 0 });
    setIsPreparing(true);
    const capture = await stopRecording();
    finishingRef.current = false;
    setIsPreparing(false);
    if (capture === null || meta === null) return;
    setReview(buildReview(meta, capture, current().phases));
  }, [sendPhase, elapsedS, stopRecording, meta, current]);

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
      refreshLive();
    }, DISPLAY_REFRESH_MS);
    return () => clearInterval(id);
  }, [isRecording, elapsedS, handleRecordingTime, refreshLive]);

  const actions = useCountActions({ sendPhase, finish, elapsedS, startRecording, discardRecording, setNowS, setReview });

  return {
    recorder,
    phase,
    phaseView: describePhase(phase, nowS, config),
    countView: describeLiveCount(live.state, nowS),
    review,
    isPreparing,
    ...actions,
  };
}

type ActionDeps = {
  sendPhase: (event: PhaseEvent) => { previous: PhaseState; next: PhaseState };
  finish: () => Promise<void>;
  elapsedS: () => number | null;
  startRecording: () => boolean;
  discardRecording: () => void;
  setNowS: (t: number) => void;
  setReview: (review: Review | null) => void;
};

const CANCEL_CONFIRM = "Cancel this test? The video and frames recorded so far will be discarded.";

/** Button handlers; they only read the latest state when pressed. */
function useCountActions({ sendPhase, finish, elapsedS, startRecording, discardRecording, setNowS, setReview }: ActionDeps) {
  return useMemo(() => {
    const startTest = () => {
      setNowS(0);
      startRecording();
    };
    const stop = () => {
      const t = elapsedS();
      if (t === null) return;
      const { previous, next } = sendPhase({ type: "stop", t });
      if (previous.stage !== "done" && next.stage === "done") void finish();
    };
    const redoCalibration = () => {
      const t = elapsedS();
      if (t !== null) sendPhase({ type: "redo-calibration", t });
    };
    const cancel = () => {
      if (!window.confirm(CANCEL_CONFIRM)) return;
      discardRecording();
      sendPhase({ type: "cancel" });
    };
    const reset = () => {
      setReview(null);
      sendPhase({ type: "reset" });
    };
    return { startTest, stop, redoCalibration, cancel, reset };
  }, [sendPhase, finish, elapsedS, startRecording, discardRecording, setNowS, setReview]);
}
