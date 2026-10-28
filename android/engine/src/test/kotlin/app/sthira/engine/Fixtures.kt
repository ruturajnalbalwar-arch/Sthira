package app.sthira.engine

import kotlin.math.floor
import kotlin.math.sin

/** Synthetic frames, ported from web/src/lib/sthira/__tests__/fixtures.ts (test data only). */
internal object Fixtures {
    val GEOMETRY = VideoGeometry(640.0, 480.0)
    const val SEATED_Y = 300.0
    const val STANDING_Y = 150.0
    private const val SHOULDER_WIDTH = 100.0
    private const val CENTER_X = 320.0
    private const val NOSE_ABOVE_HEAD = 20.0
    private const val SHOULDER_BELOW_HEAD = 20.0
    private const val TORSO = 120.0
    private const val JITTER_FREQ = 37.0
    private const val JITTER_PX = 0.4
    private const val LOW_VISIBILITY = 0.1

    val TEST_PARAMS = ChairStandParams(
        chairStandDurationS = 30.0, calibrationSitS = 5.0, calibrationStandS = 5.0,
        leaveSeatFraction = 0.15, reachFraction = 0.85, seatFraction = 0.1,
        minStandDwellS = 0.2, minSitDwellS = 0.2, smoothingCutoffHz = 3.0,
        armsCrossedMaxRatio = 0.8, armsViolationMinS = 0.3, visibilityMin = 0.5,
        uncertainFrameMaxFraction = 0.2, calibStillnessMaxRatio = 0.05, calibMinRangeToBodyScaleRatio = 0.5,
        minFps = 8.0, timeUpCountFraction = 0.5, unreliableUncertainFraction = 0.5, maxGapS = 1.0,
    )

    val CALIB = Calibration(SEATED_Y, STANDING_Y, TORSO)

    fun ctx(params: ChairStandParams = TEST_PARAMS): StepContext =
        when (val result = makeContext(GEOMETRY, CALIB, params)) {
            is ContextResult.Ok -> result.ctx
            is ContextResult.Failed -> error(result.errors.joinToString("; "))
        }

    data class Rep(val startS: Double, val peak: Double = 1.0, val riseS: Double = 1.0, val holdS: Double = 0.5, val fallS: Double = 1.0)

    fun makeFrame(t: Double, headYPx: Double, visibility: Double = 1.0): PoseFrame {
        val half = SHOULDER_WIDTH / 2
        val shoulderY = headYPx + SHOULDER_BELOW_HEAD
        val at = { x: Double, y: Double -> Landmark(x / GEOMETRY.width, y / GEOMETRY.height, 0.0, visibility) }
        val filler = Landmark(0.5, 0.5, 0.0, 1.0)
        val placed = mapOf(
            NOSE to at(CENTER_X, headYPx - NOSE_ABOVE_HEAD),
            LEFT_SHOULDER to at(CENTER_X - half, shoulderY),
            RIGHT_SHOULDER to at(CENTER_X + half, shoulderY),
            LEFT_WRIST to at(CENTER_X + half, shoulderY),
            RIGHT_WRIST to at(CENTER_X - half, shoulderY),
            LEFT_HIP to at(CENTER_X - half, shoulderY + TORSO),
            RIGHT_HIP to at(CENTER_X + half, shoulderY + TORSO),
        )
        return PoseFrame(t, List(POSE_LANDMARK_COUNT) { placed[it] ?: filler })
    }

    private fun progressAt(t: Double, rep: Rep): Double {
        val local = t - rep.startS
        return when {
            local < 0 || local > rep.riseS + rep.holdS + rep.fallS -> 0.0
            local < rep.riseS -> rep.peak * (local / rep.riseS)
            local < rep.riseS + rep.holdS -> rep.peak
            else -> rep.peak * (1 - (local - rep.riseS - rep.holdS) / rep.fallS)
        }
    }

    fun session(fps: Double, durationS: Double, reps: List<Rep>, lowVisibility: Boolean = false): List<PoseFrame> {
        val count = floor(durationS * fps).toInt() + 1
        return List(count) { i ->
            val t = i / fps
            val p = reps.fold(0.0) { best, rep -> maxOf(best, progressAt(t, rep)) }
            val y = SEATED_Y - p * (SEATED_Y - STANDING_Y) + sin(t * JITTER_FREQ) * JITTER_PX
            makeFrame(t, y, if (lowVisibility) LOW_VISIBILITY else 1.0)
        }
    }

    fun run(frames: List<PoseFrame>, ctx: StepContext = ctx(), start: ChairStandState = initialState()): ChairStandState =
        frames.fold(start) { s, f -> step(s, f, ctx) }
}
