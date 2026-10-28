package app.sthira.engine

import app.sthira.engine.signal.mean
import app.sthira.engine.signal.median
import app.sthira.engine.signal.std

/** Per-person calibration (mirror of calibration.ts). bodyScalePx is the median per-frame body scale. */
data class Calibration(val seatedHeadY: Double, val standingHeadY: Double, val bodyScalePx: Double)

sealed interface CalibrationResult {
    data class Ok(val calib: Calibration) : CalibrationResult
    data class Failed(val reasons: List<String>) : CalibrationResult
}

/** Keep frames with a person fully in view and strictly increasing, finite t. */
private fun usableFrames(frames: List<PoseFrame>): List<PoseFrame> =
    frames.fold(emptyList()) { kept, frame ->
        val last = kept.lastOrNull()
        if (frame.t.isFinite() && (last == null || frame.t > last.t) && inFrame(frame)) kept + frame else kept
    }

private fun heightReasons(
    seatedY: List<Double>,
    standingY: List<Double>,
    bodyScale: Double,
    params: ChairStandParams,
): List<String> {
    val range = median(seatedY) - median(standingY)
    if (!(range > 0)) return listOf("Stand fully upright, taller than your seated position")
    return buildList {
        if (!(bodyScale > 0) || range / bodyScale < params.calibMinRangeToBodyScaleRatio) {
            add("Stand fully upright so we can see your full height change")
        }
        if (std(seatedY) / range > params.calibStillnessMaxRatio) add("Please sit still")
        if (std(standingY) / range > params.calibStillnessMaxRatio) add("Please stand still")
    }
}

/** Duration and frame-count gate on the usable frames; says whether the camera or the person is the problem. */
private fun postureReasons(
    raw: List<PoseFrame>,
    kept: List<PoseFrame>,
    durationS: Double,
    label: String,
    params: ChairStandParams,
): List<String> {
    val neededFrames = params.minFps * durationS
    if (kept.size >= neededFrames) {
        val spanS = kept.last().t - kept.first().t
        return if (spanS >= durationS) emptyList() else listOf("Please stay $label for the full countdown")
    }
    return if (raw.size >= neededFrames) {
        listOf("We lost sight of you while $label: keep your whole body in view")
    } else {
        listOf("The camera is too slow: close other apps and try again")
    }
}

/** Gate and compute the per-person calibration. Never guesses: failures list reasons. */
fun calibrate(
    sitFrames: List<PoseFrame>,
    standFrames: List<PoseFrame>,
    geometry: VideoGeometry,
    params: ChairStandParams,
): CalibrationResult {
    val sit = usableFrames(sitFrames)
    val stand = usableFrames(standFrames)
    if (sit.isEmpty() || stand.isEmpty()) return CalibrationResult.Failed(listOf("Move so your whole body is visible"))

    val all = sit + stand
    val lowLight = mean(all.map(::keyVisibility)) < params.visibilityMin
    val seatedY = sit.map { headY(it, geometry) }
    val standingY = stand.map { headY(it, geometry) }
    val bodyScale = median(all.map { bodyScalePx(it, geometry) })
    val reasons = postureReasons(sitFrames, sit, params.calibrationSitS, "seated", params) +
        postureReasons(standFrames, stand, params.calibrationStandS, "standing", params) +
        (if (lowLight) listOf("Move to better light so your face, shoulders and hands are clearly visible") else emptyList()) +
        heightReasons(seatedY, standingY, bodyScale, params)
    if (reasons.isNotEmpty()) return CalibrationResult.Failed(reasons)

    return CalibrationResult.Ok(Calibration(median(seatedY), median(standingY), bodyScale))
}
