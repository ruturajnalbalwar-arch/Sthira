package app.sthira.engine

/** Per-frame features (mirror of features.ts). */
private data class Point(val x: Double, val y: Double)

private fun pixelPoint(frame: PoseFrame, index: Int, geometry: VideoGeometry): Point {
    val landmark = frame.landmarks[index]
    return Point(landmark.x * geometry.width, landmark.y * geometry.height)
}

private fun distance(a: Point, b: Point): Double = jsHypot(a.x - b.x, a.y - b.y)

private fun hasFiniteXY(landmark: Landmark?): Boolean =
    landmark != null && landmark.x.isFinite() && landmark.y.isFinite()

/** True when a person is present: every key landmark exists with finite x and y. */
fun isUsableFrame(frame: PoseFrame): Boolean {
    if (frame.landmarks.size < POSE_LANDMARK_COUNT) return false
    return KEY_LANDMARKS.all { hasFiniteXY(frame.landmarks.getOrNull(it)) }
}

/** Pixel y of mean(nose, shoulder midpoint): a head proxy (MediaPipe has no neck). */
fun headY(frame: PoseFrame, geometry: VideoGeometry): Double {
    val nose = pixelPoint(frame, NOSE, geometry)
    val left = pixelPoint(frame, LEFT_SHOULDER, geometry)
    val right = pixelPoint(frame, RIGHT_SHOULDER, geometry)
    return midpoint(nose.y, midpoint(left.y, right.y))
}

fun shoulderWidthPx(frame: PoseFrame, geometry: VideoGeometry): Double =
    distance(pixelPoint(frame, LEFT_SHOULDER, geometry), pixelPoint(frame, RIGHT_SHOULDER, geometry))

/** Larger of shoulder width and torso length; hips that are not finite fall back to shoulder width. */
fun bodyScalePx(frame: PoseFrame, geometry: VideoGeometry): Double {
    val width = shoulderWidthPx(frame, geometry)
    val hips = listOf(frame.landmarks.getOrNull(LEFT_HIP), frame.landmarks.getOrNull(RIGHT_HIP))
    if (!hips.all(::hasFiniteXY)) return width
    val left = pixelPoint(frame, LEFT_SHOULDER, geometry)
    val right = pixelPoint(frame, RIGHT_SHOULDER, geometry)
    val leftHip = pixelPoint(frame, LEFT_HIP, geometry)
    val rightHip = pixelPoint(frame, RIGHT_HIP, geometry)
    val shoulderMid = Point(midpoint(left.x, right.x), midpoint(left.y, right.y))
    val hipMid = Point(midpoint(leftHip.x, rightHip.x), midpoint(leftHip.y, rightHip.y))
    return maxOf(width, distance(shoulderMid, hipMid))
}

/** Larger value means wrists are further from the opposite shoulder (arms not crossed), per body scale. */
fun armsCrossedRatio(frame: PoseFrame, geometry: VideoGeometry): Double {
    val leftWrist = pixelPoint(frame, LEFT_WRIST, geometry)
    val rightWrist = pixelPoint(frame, RIGHT_WRIST, geometry)
    val leftShoulder = pixelPoint(frame, LEFT_SHOULDER, geometry)
    val rightShoulder = pixelPoint(frame, RIGHT_SHOULDER, geometry)
    val worst = maxOf(distance(leftWrist, rightShoulder), distance(rightWrist, leftShoulder))
    return worst / bodyScalePx(frame, geometry)
}

/** Minimum visibility over key landmarks. Missing or non-finite visibility counts as 0 (low). */
fun keyVisibility(frame: PoseFrame): Double =
    KEY_LANDMARKS
        .map { index -> frame.landmarks.getOrNull(index)?.visibility?.takeIf { it.isFinite() } ?: 0.0 }
        .reduce { a, b -> minOf(a, b) }

/** True when every key landmark lies inside the image. */
fun inFrame(frame: PoseFrame): Boolean {
    if (!isUsableFrame(frame)) return false
    return KEY_LANDMARKS.all { index ->
        val lm = frame.landmarks[index]
        lm.x >= 0 && lm.x <= 1 && lm.y >= 0 && lm.y <= 1
    }
}
