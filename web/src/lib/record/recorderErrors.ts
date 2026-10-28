/** Every way record mode can fail, each with text a student can act on. */
export const RECORDER_ERROR_TEXT = {
  "permission-denied":
    "Camera permission was refused. Click the camera icon in the address bar, allow the camera, then press Start camera again.",
  "no-camera": "No camera was found. Connect a webcam (or use a device with a camera) and try again.",
  "camera-busy": "The camera is being used by another app (Zoom, Teams, the Camera app). Close it and try again.",
  "camera-failed": "The camera could not be started. Reload the page and try again.",
  unsupported:
    "This browser cannot use the camera here. Open the app in Chrome or Edge, at an https:// or localhost address.",
  "model-load":
    "The pose model could not be loaded. Check that the app was built with `npm run assets`, then reload the page.",
  "camera-lost": "The camera stopped (unplugged, or taken by another app). Press Try camera again.",
  detection: "Pose tracking stopped unexpectedly. Press Try camera again; reload the page if it happens again.",
  recorder: "Video recording failed in this browser. Try Chrome or Edge, or record again.",
  "save-failed": "The file could not be saved. Choose another folder or check the disk has space, then try again.",
  "invalid-recording":
    "The recording could not be assembled into a valid file. Record this trial again; do not reuse this one.",
} as const;

export type RecorderErrorKind = keyof typeof RECORDER_ERROR_TEXT;

export interface RecorderError {
  readonly kind: RecorderErrorKind;
  readonly message: string;
}

export function recorderError(kind: RecorderErrorKind): RecorderError {
  return { kind, message: RECORDER_ERROR_TEXT[kind] };
}

const CAMERA_ERROR_NAMES: Readonly<Record<string, RecorderErrorKind>> = {
  NotAllowedError: "permission-denied",
  SecurityError: "permission-denied",
  NotFoundError: "no-camera",
  OverconstrainedError: "no-camera",
  NotReadableError: "camera-busy",
  AbortError: "camera-busy",
  NotSupportedError: "unsupported",
};

/** Maps a getUserMedia rejection (a DOMException) to a user-facing error kind. */
export function cameraErrorKind(error: unknown): RecorderErrorKind {
  if (error instanceof Error) {
    return CAMERA_ERROR_NAMES[error.name] ?? "camera-failed";
  }
  return "camera-failed";
}
