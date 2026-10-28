import { POSE_LANDMARK_COUNT, type RecordedFrame } from "../record/schema";

/** The fields of a MediaPipe NormalizedLandmark that a recording keeps. */
export interface LandmarkLike {
  readonly x: number;
  readonly y: number;
  readonly z: number;
  readonly visibility: number;
}

/** Structural subset of `PoseLandmarkerResult`, so this module never imports the WASM library. */
export interface PoseResultLike {
  readonly landmarks: readonly (readonly LandmarkLike[])[];
}

type StoredLandmark = RecordedFrame["landmarks"][number];

/**
 * The library itself writes 0 when the model gives no visibility score
 * (`visibility: Or(r, 4) ?? 0` in vision_bundle.mjs), so we apply the same rule.
 */
const LIBRARY_DEFAULT_VISIBILITY = 0;

function toStored(landmark: LandmarkLike): StoredLandmark | null {
  const visibility = landmark.visibility ?? LIBRARY_DEFAULT_VISIBILITY;
  const values = [landmark.x, landmark.y, landmark.z, visibility];
  if (!values.every(Number.isFinite)) return null;
  return { x: landmark.x, y: landmark.y, z: landmark.z, visibility };
}

function firstPerson(result: PoseResultLike): StoredLandmark[] {
  const person = result.landmarks[0];
  if (person === undefined || person.length !== POSE_LANDMARK_COUNT) return [];
  const stored = person.map(toStored);
  // A frame the schema would reject is stored as "no person" instead of breaking the whole file.
  return stored.every((landmark): landmark is StoredLandmark => landmark !== null) ? stored : [];
}

/**
 * Converts one PoseLandmarker result into a recorded frame. Only the first person is kept
 * (the landmarker runs with numPoses: 1). Pure: the input is not modified.
 */
export function toFrame(result: PoseResultLike, elapsedS: number): RecordedFrame {
  if (!Number.isFinite(elapsedS) || elapsedS < 0) {
    throw new RangeError(`Frame time must be a finite number of seconds >= 0, got ${elapsedS}`);
  }
  return { t: elapsedS, landmarks: firstPerson(result) };
}
