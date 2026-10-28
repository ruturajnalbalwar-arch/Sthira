/** MediaPipe VIDEO mode requires strictly increasing timestamps for the landmarker's whole life. */
export interface TimestampGate {
  accept(nowMs: number): boolean;
}

export function createTimestampGate(): TimestampGate {
  let last = Number.NEGATIVE_INFINITY;
  return {
    accept(nowMs) {
      if (!(nowMs > last)) return false;
      last = nowMs;
      return true;
    },
  };
}

/**
 * When a frame happened, in the performance.now() time base. `callbackMs` is the time the frame
 * callback was scheduled for (taken before any detection work). `captureMs` is the camera's own
 * capture time (requestVideoFrameCallback metadata.captureTime) when the browser provides it.
 */
export interface FrameTiming {
  readonly callbackMs: number;
  readonly captureMs: number | null;
}

interface VideoFrameMetadataLike {
  readonly captureTime?: number;
}

/** The part of HTMLVideoElement this loop uses; the frame-callback methods are missing in some browsers. */
export interface FrameSource {
  readonly currentTime?: number;
  requestVideoFrameCallback?: (callback: (now: number, metadata: VideoFrameMetadataLike) => void) => number;
  cancelVideoFrameCallback?: (handle: number) => void;
}

export interface FrameScheduler {
  request(callback: (now: number) => void): number;
  cancel(handle: number): void;
}

export interface FrameLoopOptions {
  /** Used when the video element has no requestVideoFrameCallback (requestAnimationFrame in the browser). */
  readonly fallback: FrameScheduler;
}

export interface FrameLoop {
  stop(): void;
}

type OnFrame = (timing: FrameTiming) => boolean | void;

function finiteOrNull(value: number | undefined): number | null {
  return value !== undefined && Number.isFinite(value) ? value : null;
}

function videoFrameLoop(source: Required<Pick<FrameSource, "requestVideoFrameCallback" | "cancelVideoFrameCallback">>, onFrame: OnFrame): FrameLoop {
  let stopped = false;
  let handle = 0;
  const step = (now: number, metadata: VideoFrameMetadataLike): void => {
    if (stopped) return;
    if (onFrame({ callbackMs: now, captureMs: finiteOrNull(metadata?.captureTime) }) === false) {
      stopped = true;
      return;
    }
    handle = source.requestVideoFrameCallback(step);
  };
  handle = source.requestVideoFrameCallback(step);
  return {
    stop() {
      stopped = true;
      source.cancelVideoFrameCallback(handle);
    },
  };
}

/** Animation frames repeat the same video frame on fast displays; only new video frames are passed on. */
function animationFrameLoop(source: FrameSource, onFrame: OnFrame, scheduler: FrameScheduler): FrameLoop {
  let stopped = false;
  let handle = 0;
  let lastMediaTime: number | undefined;
  const step = (now: number): void => {
    if (stopped) return;
    const mediaTime = source.currentTime;
    const isNewFrame = mediaTime === undefined || mediaTime !== lastMediaTime;
    lastMediaTime = mediaTime;
    if (isNewFrame && onFrame({ callbackMs: now, captureMs: null }) === false) {
      stopped = true;
      return;
    }
    handle = scheduler.request(step);
  };
  handle = scheduler.request(step);
  return {
    stop() {
      stopped = true;
      scheduler.cancel(handle);
    },
  };
}

/** Calls `onFrame` once per new video frame. Returning false from `onFrame` ends the loop. */
export function startFrameLoop(source: FrameSource, onFrame: OnFrame, options: FrameLoopOptions): FrameLoop {
  const request = source.requestVideoFrameCallback;
  const cancel = source.cancelVideoFrameCallback;
  if (typeof request === "function" && typeof cancel === "function") {
    return videoFrameLoop(
      { requestVideoFrameCallback: (cb) => request.call(source, cb), cancelVideoFrameCallback: (h) => cancel.call(source, h) },
      onFrame,
    );
  }
  return animationFrameLoop(source, onFrame, options.fallback);
}

/** requestAnimationFrame-based scheduler for the browser. */
export const animationFrameScheduler: FrameScheduler = {
  request: (callback) => requestAnimationFrame(callback),
  cancel: (handle) => cancelAnimationFrame(handle),
};

/** Live frame-rate readout for the UI: frames counted over windows of `windowMs` (a display choice). */
export interface FpsMeter {
  /** Records one frame; returns the fps of the window that just closed, or null mid-window. */
  frame(nowMs: number): number | null;
}

export function createFpsMeter(windowMs: number): FpsMeter {
  let windowStart: number | null = null;
  let count = 0;
  return {
    frame(nowMs) {
      if (windowStart === null) {
        windowStart = nowMs;
        return null;
      }
      count += 1;
      const span = nowMs - windowStart;
      if (span < windowMs) return null;
      const fps = (count * 1000) / span;
      windowStart = nowMs;
      count = 0;
      return fps;
    },
  };
}
