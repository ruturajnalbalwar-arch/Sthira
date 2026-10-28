/** Shared data types. x and y are normalised 0..1 with y pointing DOWN. */
export interface Landmark {
  readonly x: number;
  readonly y: number;
  readonly z: number;
  readonly visibility: number;
}

export interface PoseFrame {
  /** Seconds, strictly increasing within a session. */
  readonly t: number;
  /** 33 MediaPipe pose landmarks. */
  readonly landmarks: readonly Landmark[];
}

/** Video size in pixels. Normalised coordinates are converted before any distance. */
export interface VideoGeometry {
  readonly width: number;
  readonly height: number;
}
