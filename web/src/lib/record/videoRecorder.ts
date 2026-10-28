import { pickVideoMime } from "./videoMime";

/** The MediaRecorder surface this module uses (a fake in tests). */
export interface RecorderLike extends EventTarget {
  readonly state: string;
  readonly mimeType: string;
  start(timeslice?: number): void;
  stop(): void;
}

export interface RecorderFactory {
  isTypeSupported(type: string): boolean;
  create(stream: MediaStream, mimeType: string | null): RecorderLike;
}

export interface VideoRecorderOptions {
  /** Chunk length: data is flushed while recording, so a crash mid-trial still leaves most of the video. */
  readonly timesliceMs: number;
  /** How long to wait for the recorder's start/stop events before giving up (a UI timeout). */
  readonly eventTimeoutMs: number;
  readonly onStart: () => void;
  /** `afterStart` false: the recorder never started (discard). true: it failed mid-recording (keep data). */
  readonly onFailure: (afterStart: boolean) => void;
}

export interface VideoRecorder {
  /** Stops and resolves with every chunk recorded. Never rejects: on error or timeout it resolves with what it has. */
  stop(): Promise<Blob>;
  snapshot(): Blob;
  abort(): void;
}

const FALLBACK_MIME = "video/webm";

export function browserRecorderFactory(): RecorderFactory | null {
  if (typeof MediaRecorder === "undefined") return null;
  return {
    isTypeSupported: (type) => MediaRecorder.isTypeSupported(type),
    create: (stream, mimeType) => (mimeType === null ? new MediaRecorder(stream) : new MediaRecorder(stream, { mimeType })),
  };
}

function create(stream: MediaStream, factory: RecorderFactory): { recorder: RecorderLike; mime: string | null } | null {
  const mime = pickVideoMime((type) => factory.isTypeSupported(type));
  try {
    return { recorder: factory.create(stream, mime), mime };
  } catch {
    return null;
  }
}

function whenStopped(recorder: RecorderLike, timeoutMs: number): Promise<void> {
  if (recorder.state === "inactive") return Promise.resolve();
  return new Promise((resolve) => {
    const timer = setTimeout(resolve, timeoutMs);
    const done = () => {
      clearTimeout(timer);
      resolve();
    };
    recorder.addEventListener("stop", done, { once: true });
    recorder.addEventListener("error", done, { once: true });
    try {
      recorder.stop();
    } catch {
      done();
    }
  });
}

/**
 * Starts a MediaRecorder on the camera stream. Note: Chrome's WebM output has no duration and no
 * cues (seek index), so players may show an unknown length and seek slowly; the frames are all there.
 */
export function startVideoRecorder(stream: MediaStream, options: VideoRecorderOptions, factory: RecorderFactory | null = browserRecorderFactory()): VideoRecorder | null {
  const created = factory === null ? null : create(stream, factory);
  if (created === null) return null;
  const { recorder, mime } = created;
  const chunks: Blob[] = [];
  let started = false;
  const snapshot = () => new Blob([...chunks], { type: recorder.mimeType || mime || FALLBACK_MIME });
  // abort() only runs after startTimer below has been created.
  const abort = () => {
    clearTimeout(startTimer);
    if (recorder.state !== "inactive") recorder.stop();
  };
  const startTimer = setTimeout(() => {
    if (started) return;
    abort();
    options.onFailure(false);
  }, options.eventTimeoutMs);

  recorder.addEventListener("dataavailable", (event) => {
    const data = (event as Event & { data?: Blob }).data;
    if (data !== undefined && data.size > 0) chunks.push(data);
  });
  recorder.addEventListener("start", () => {
    started = true;
    clearTimeout(startTimer);
    options.onStart();
  }, { once: true });
  recorder.addEventListener("error", () => options.onFailure(started));
  try {
    recorder.start(options.timesliceMs);
  } catch {
    clearTimeout(startTimer);
    return null;
  }
  return { stop: () => whenStopped(recorder, options.eventTimeoutMs).then(snapshot), snapshot, abort };
}
