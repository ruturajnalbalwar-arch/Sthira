"use client";

import { useCallback, useEffect, useRef, useState, type RefObject } from "react";
import { createPoseLandmarker, type PoseDelegate, type PoseEngine } from "@/lib/pose/createPoseLandmarker";
import { cameraErrorKind, recorderError, type RecorderError, type RecorderErrorKind } from "@/lib/record/recorderErrors";

export type CameraStatus = "off" | "starting" | "live" | "error";

export interface CameraOptions {
  /** The camera is live and the model is loaded: start the frame loop. */
  readonly onLive: (video: HTMLVideoElement, canvas: HTMLCanvasElement, engine: PoseEngine) => void;
  /** The browser ended the video track (camera unplugged or taken by another app). */
  readonly onTrackEnded: () => void;
}

export interface Camera {
  readonly videoRef: RefObject<HTMLVideoElement | null>;
  readonly canvasRef: RefObject<HTMLCanvasElement | null>;
  readonly status: CameraStatus;
  readonly error: RecorderError | null;
  readonly delegate: PoseDelegate | null;
  start(): Promise<void>;
  stop(): void;
  /** Stops the camera and shows `kind`; Start camera works again afterwards. */
  fail(kind: RecorderErrorKind): void;
  stream(): MediaStream | null;
}

async function loadEngine(): Promise<PoseEngine | null> {
  try {
    return await createPoseLandmarker();
  } catch {
    return null;
  }
}

async function attachStream(video: HTMLVideoElement, canvas: HTMLCanvasElement, stream: MediaStream): Promise<void> {
  video.srcObject = stream;
  video.muted = true;
  video.playsInline = true;
  await video.play();
  canvas.width = video.videoWidth;
  canvas.height = video.videoHeight;
}

function stopTracks(stream: MediaStream | null): void {
  stream?.getTracks().forEach((track) => track.stop());
}

/**
 * Camera stream + pose model lifecycle. Every start gets a generation number; stop(), fail() and
 * unmount bump it, and each await in the start sequence checks it, so a camera that finishes
 * opening after the user left (e.g. pressed Edit session) is stopped instead of being orphaned.
 */
export function useCamera(options: CameraOptions): Camera {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [status, setStatus] = useState<CameraStatus>("off");
  const [error, setError] = useState<RecorderError | null>(null);
  const [delegate, setDelegate] = useState<PoseDelegate | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const engineRef = useRef<PoseEngine | null>(null);
  const generationRef = useRef(0);
  const startingRef = useRef(false);
  const mountedRef = useRef(true);
  const optionsRef = useRef(options);

  useEffect(() => {
    optionsRef.current = options;
  }, [options]);

  const release = useCallback(() => {
    generationRef.current += 1;
    startingRef.current = false;
    stopTracks(streamRef.current);
    streamRef.current = null;
    if (videoRef.current !== null) videoRef.current.srcObject = null;
  }, []);

  const stop = useCallback(() => {
    release();
    if (mountedRef.current) setStatus("off");
  }, [release]);

  const fail = useCallback(
    (kind: RecorderErrorKind) => {
      release();
      if (!mountedRef.current) return;
      setError(recorderError(kind));
      setStatus("error");
    },
    [release],
  );

  const keepEngine = useCallback((engine: PoseEngine | null) => {
    if (engine === null || engineRef.current === engine) return;
    if (mountedRef.current && engineRef.current === null) engineRef.current = engine;
    else engine.landmarker.close();
  }, []);

  const watchTracks = useCallback((stream: MediaStream) => {
    stream.getVideoTracks().forEach((track) =>
      // track.stop() by us does not fire "ended"; only the browser/device ending it does.
      track.addEventListener("ended", () => {
        if (streamRef.current === stream) optionsRef.current.onTrackEnded();
      }, { once: true }),
    );
  }, []);

  const open = useCallback(
    async (generation: number, video: HTMLVideoElement, canvas: HTMLCanvasElement) => {
      const isCurrent = () => mountedRef.current && generation === generationRef.current;
      const enginePromise = engineRef.current === null ? loadEngine() : Promise.resolve(engineRef.current);
      let stream: MediaStream;
      try {
        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "user" }, audio: false });
      } catch (cause: unknown) {
        void enginePromise.then(keepEngine);
        if (isCurrent()) fail(cameraErrorKind(cause));
        return;
      }
      const engine = await enginePromise;
      keepEngine(engine);
      if (!isCurrent()) return stopTracks(stream);
      if (engine === null) {
        stopTracks(stream);
        return fail("model-load");
      }
      streamRef.current = stream;
      watchTracks(stream);
      try {
        await attachStream(video, canvas, stream);
      } catch {
        if (isCurrent()) fail("camera-failed");
        return;
      }
      if (!isCurrent()) return;
      startingRef.current = false;
      setDelegate(engine.delegate);
      setStatus("live");
      optionsRef.current.onLive(video, canvas, engine);
    },
    [fail, keepEngine, watchTracks],
  );

  /** Asks for camera permission (only now, on the user's click) and starts the camera. */
  const start = useCallback(async () => {
    const video = videoRef.current;
    const canvas = canvasRef.current;
    if (video === null || canvas === null || streamRef.current !== null || startingRef.current) return;
    if (typeof navigator.mediaDevices?.getUserMedia !== "function") return fail("unsupported");
    generationRef.current += 1;
    startingRef.current = true;
    setStatus("starting");
    setError(null);
    await open(generationRef.current, video, canvas);
  }, [fail, open]);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      release();
      engineRef.current?.landmarker.close();
      engineRef.current = null;
    };
  }, [release]);

  const stream = useCallback(() => streamRef.current, []);

  return { videoRef, canvasRef, status, error, delegate, start, stop, fail, stream };
}
