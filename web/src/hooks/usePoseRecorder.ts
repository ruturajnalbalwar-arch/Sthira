"use client";

import { useCallback, useEffect, useRef, useState, type RefObject } from "react";
import { useCamera, type CameraStatus } from "@/hooks/useCamera";
import { createCaptureSession, type CaptureData, type CaptureSession } from "@/lib/pose/captureSession";
import type { OverlayStyle, PoseDelegate, PoseEngine } from "@/lib/pose/createPoseLandmarker";
import { animationFrameScheduler, createFpsMeter, createTimestampGate, startFrameLoop, type FrameLoop, type FrameTiming } from "@/lib/pose/frameLoop";
import { processFrame, type FrameOutcome } from "@/lib/pose/frameProcessor";
import { createRecordingClock } from "@/lib/pose/recordingClock";
import { isFatalIssue, type CaptureIssue } from "@/lib/record/captureIssues";
import { recorderError, type RecorderError, type RecorderErrorKind } from "@/lib/record/recorderErrors";
import { MAX_FRAMES } from "@/lib/record/schema";
import { holdScreenWakeLock, type WakeLockHandle } from "@/lib/record/wakeLock";

export type { CameraStatus };

export interface LiveStats {
  readonly fps: number | null;
  readonly personDetected: boolean;
  readonly frameCount: number;
  /** Seconds until the schema's frame limit at the current frame rate; null when unknown. */
  readonly secondsToFrameLimit: number | null;
}

export interface CaptureResult extends CaptureData {
  readonly videoWidth: number;
  readonly videoHeight: number;
}

export interface PoseRecorderOptions {
  /** Called on every recorded frame with its t (drives the phase machine). */
  readonly onRecordingTime?: (t: number) => void;
  /** The capture has to end now (camera lost, tracking failed, frame limit): call stopRecording. */
  readonly onCaptureMustEnd?: (issue: CaptureIssue) => void;
}

export interface PoseRecorder {
  readonly videoRef: RefObject<HTMLVideoElement | null>;
  readonly canvasRef: RefObject<HTMLCanvasElement | null>;
  readonly status: CameraStatus;
  readonly error: RecorderError | null;
  readonly delegate: PoseDelegate | null;
  readonly stats: LiveStats;
  readonly isRecording: boolean;
  readonly issues: readonly CaptureIssue[];
  startCamera(): Promise<void>;
  stopCamera(): void;
  startRecording(): boolean;
  /** Ends the capture and hands over all data (never loses frames or video). Null if not recording. */
  stopRecording(): Promise<CaptureResult | null>;
  discardRecording(): void;
  /** Seconds since frame t = 0, read now; null before the first recorded frame. */
  elapsedS(): number | null;
}

/** UI refresh period for the live readout (display only, not a detection setting). */
const STATS_INTERVAL_MS = 250;
const FPS_WINDOW_MS = 1000;
/** MediaRecorder chunk length: data is flushed every second so a crash keeps most of the video. */
const VIDEO_TIMESLICE_MS = 1000;
/** How long to wait for MediaRecorder's start/stop events before treating it as failed (UI timeout). */
const RECORDER_EVENT_TIMEOUT_MS = 3000;
const IDLE_STATS: LiveStats = { fps: null, personDetected: false, frameCount: 0, secondsToFrameLimit: null };
/** Never armed, so preview frames (no recording) are never stamped. */
const PREVIEW_CLOCK = createRecordingClock();

const ISSUE_ERROR: Partial<Record<CaptureIssue, RecorderErrorKind>> = {
  "detection-failed": "detection",
  "camera-ended": "camera-lost",
  "recorder-failed": "recorder",
};

function overlayStyle(): OverlayStyle {
  const css = getComputedStyle(document.documentElement);
  return { lineColor: css.getPropertyValue("--primary").trim(), pointColor: css.getPropertyValue("--on-primary").trim() };
}

function liveStats(fps: number | null, personDetected: boolean, session: CaptureSession | null): LiveStats {
  const frameCount = session?.frameCount() ?? 0;
  const secondsToFrameLimit = session !== null && fps !== null && fps > 0 ? (MAX_FRAMES - frameCount) / fps : null;
  return { fps, personDetected, frameCount, secondsToFrameLimit };
}

export function usePoseRecorder(options: PoseRecorderOptions = {}): PoseRecorder {
  const [stats, setStats] = useState<LiveStats>(IDLE_STATS);
  const [isRecording, setIsRecording] = useState(false);
  const [issues, setIssues] = useState<readonly CaptureIssue[]>([]);
  const [captureError, setCaptureError] = useState<RecorderError | null>(null);
  const sessionRef = useRef<CaptureSession | null>(null);
  const loopRef = useRef<FrameLoop | null>(null);
  const gateRef = useRef(createTimestampGate());
  const wakeLockRef = useRef<WakeLockHandle | null>(null);
  const fatalRef = useRef<CaptureIssue | null>(null);
  const optionsRef = useRef(options);
  const cameraFailRef = useRef<(kind: RecorderErrorKind) => void>(() => undefined);

  useEffect(() => {
    optionsRef.current = options;
  }, [options]);

  const stopLoop = useCallback(() => {
    loopRef.current?.stop();
    loopRef.current = null;
  }, []);

  /** Flags an issue; a fatal or limit issue asks the studio to end the capture (once). */
  const raise = useCallback(
    (issue: CaptureIssue) => {
      const session = sessionRef.current;
      if (isFatalIssue(issue)) stopLoop();
      if (session === null) {
        if (isFatalIssue(issue)) cameraFailRef.current(ISSUE_ERROR[issue] ?? "camera-failed");
        return;
      }
      if (session.hasIssue(issue)) return;
      session.flag(issue);
      setIssues(session.issues());
      if (isFatalIssue(issue)) fatalRef.current = issue;
      if (issue !== "tab-hidden") optionsRef.current.onCaptureMustEnd?.(issue);
    },
    [stopLoop],
  );

  const record = useCallback((session: CaptureSession | null, outcome: FrameOutcome) => {
    if (session === null) return;
    if (outcome.kind === "limit") return raise("frame-limit");
    if (outcome.kind !== "recorded") return;
    session.addFrame(outcome.frame);
    optionsRef.current.onRecordingTime?.(outcome.frame.t);
  }, [raise]);

  const startLoop = useCallback(
    (video: HTMLVideoElement, canvas: HTMLCanvasElement, engine: PoseEngine) => {
      stopLoop();
      const context = canvas.getContext("2d");
      const draw = context === null ? null : engine.createDrawer(context);
      const style = overlayStyle();
      const fps = createFpsMeter(FPS_WINDOW_MS);
      let lastStats = Number.NEGATIVE_INFINITY;
      let lastFps: number | null = null;
      const onFrame = (timing: FrameTiming): boolean => {
        if (video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA) return true;
        const session = sessionRef.current;
        const outcome = processFrame(timing, session?.frameCount() ?? 0, {
          detect: (ms) => engine.landmarker.detectForVideo(video, ms).landmarks,
          draw: (poses) => draw?.(poses, style),
          gate: gateRef.current,
          clock: session?.clock ?? PREVIEW_CLOCK,
          maxFrames: MAX_FRAMES,
        });
        if (outcome.kind === "skipped") return true;
        if (outcome.kind === "failed") {
          raise("detection-failed");
          return false;
        }
        record(session, outcome);
        lastFps = fps.frame(timing.callbackMs) ?? lastFps;
        if (timing.callbackMs - lastStats >= STATS_INTERVAL_MS) {
          lastStats = timing.callbackMs;
          setStats(liveStats(lastFps, outcome.personDetected, session));
        }
        return true;
      };
      loopRef.current = startFrameLoop(video, onFrame, { fallback: animationFrameScheduler });
    },
    [raise, record, stopLoop],
  );

  const camera = useCamera({ onLive: startLoop, onTrackEnded: () => raise("camera-ended") });
  const { stream: cameraStream, stop: cameraStop, fail: cameraFail, videoRef } = camera;
  useEffect(() => {
    cameraFailRef.current = cameraFail;
  }, [cameraFail]);

  const endSession = useCallback(() => {
    sessionRef.current = null;
    wakeLockRef.current?.release();
    wakeLockRef.current = null;
    setIsRecording(false);
  }, []);

  const discardRecording = useCallback(() => {
    sessionRef.current?.abort();
    endSession();
    setIssues([]);
  }, [endSession]);

  const stopCamera = useCallback(() => {
    discardRecording();
    stopLoop();
    cameraStop();
    setStats(IDLE_STATS);
  }, [cameraStop, discardRecording, stopLoop]);

  const startRecording = useCallback((): boolean => {
    const stream = cameraStream();
    if (stream === null || sessionRef.current !== null) return false;
    setCaptureError(null);
    fatalRef.current = null;
    const session = createCaptureSession(stream, {
      timesliceMs: VIDEO_TIMESLICE_MS,
      eventTimeoutMs: RECORDER_EVENT_TIMEOUT_MS,
      onFailure: (afterStart) => {
        if (sessionRef.current !== session) return;
        if (afterStart) return raise("recorder-failed");
        discardRecording();
        setCaptureError(recorderError("recorder"));
      },
    });
    if (session === null) {
      setCaptureError(recorderError("recorder"));
      return false;
    }
    sessionRef.current = session;
    wakeLockRef.current = holdScreenWakeLock();
    setIssues([]);
    setIsRecording(true);
    return true;
  }, [cameraStream, discardRecording, raise]);

  const stopRecording = useCallback(async (): Promise<CaptureResult | null> => {
    const session = sessionRef.current;
    const video = videoRef.current;
    if (session === null) return null;
    const data = await session.finish();
    // Read the size before a fatal teardown detaches the stream (the size would then read 0).
    const size = { videoWidth: video?.videoWidth ?? 0, videoHeight: video?.videoHeight ?? 0 };
    endSession();
    const fatal = fatalRef.current;
    if (fatal !== null) {
      stopLoop();
      cameraFail(ISSUE_ERROR[fatal] ?? "camera-failed");
    }
    return { ...data, ...size };
  }, [cameraFail, endSession, stopLoop, videoRef]);

  const elapsedS = useCallback(() => sessionRef.current?.clock.elapsedS(performance.now()) ?? null, []);

  useEffect(() => {
    if (!isRecording) return;
    const onVisibility = () => {
      if (document.visibilityState === "hidden") raise("tab-hidden");
    };
    document.addEventListener("visibilitychange", onVisibility);
    return () => document.removeEventListener("visibilitychange", onVisibility);
  }, [isRecording, raise]);

  useEffect(
    () => () => {
      loopRef.current?.stop();
      sessionRef.current?.abort();
      wakeLockRef.current?.release();
    },
    [],
  );

  return {
    videoRef,
    canvasRef: camera.canvasRef,
    status: camera.status,
    error: captureError ?? camera.error,
    delegate: camera.delegate,
    stats,
    isRecording,
    issues,
    startCamera: camera.start,
    stopCamera,
    startRecording,
    stopRecording,
    discardRecording,
    elapsedS,
  };
}
