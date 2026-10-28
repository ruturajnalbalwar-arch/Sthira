package app.sthira.engine

/**
 * MediaPipe Pose Landmarker 33-landmark model indices (mirror of landmarks.ts).
 * Source: https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker
 */
const val POSE_LANDMARK_COUNT = 33

const val NOSE = 0
const val LEFT_SHOULDER = 11
const val RIGHT_SHOULDER = 12
const val LEFT_WRIST = 15
const val RIGHT_WRIST = 16
const val LEFT_HIP = 23
const val RIGHT_HIP = 24
const val LEFT_KNEE = 25
const val RIGHT_KNEE = 26
const val LEFT_ANKLE = 27
const val RIGHT_ANKLE = 28

/** Landmarks the chair-stand method depends on. */
val KEY_LANDMARKS: List<Int> = listOf(NOSE, LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_WRIST, RIGHT_WRIST)
