/**
 * MediaPipe Pose Landmarker 33-landmark model indices.
 * Source: https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker
 * This file is exempt from the numeric-literal lint.
 */
export const POSE_LANDMARK_COUNT = 33;

export const NOSE = 0;
export const LEFT_SHOULDER = 11;
export const RIGHT_SHOULDER = 12;
export const LEFT_WRIST = 15;
export const RIGHT_WRIST = 16;
export const LEFT_HIP = 23;
export const RIGHT_HIP = 24;
export const LEFT_KNEE = 25;
export const RIGHT_KNEE = 26;
export const LEFT_ANKLE = 27;
export const RIGHT_ANKLE = 28;

/** Landmarks the chair-stand method depends on. */
export const KEY_LANDMARKS: readonly number[] = [
  NOSE,
  LEFT_SHOULDER,
  RIGHT_SHOULDER,
  LEFT_WRIST,
  RIGHT_WRIST,
];
