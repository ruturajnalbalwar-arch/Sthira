package app.sthira.engine

/** State and result types of the chair-stand reducer (mirror of chairStand.ts). */
enum class Phase { SEATED, RISING, STANDING }

enum class RepKind(val wire: String) {
    FULL("full"),
    HALF("half"),
    ARM_ASSISTED("arm-assisted"),
    UNCERTAIN("uncertain");

    companion object {
        fun fromWire(wire: String): RepKind? = entries.firstOrNull { it.wire == wire }
    }
}

/** standingT is the time STANDING was reached, or null for reps that never stood. */
data class RepEvent(val kind: RepKind, val startT: Double, val standingT: Double?, val endT: Double)

/** Accumulated time inside the open rep. */
data class RepTracker(
    val startT: Double,
    val repS: Double,
    val lowVisibilityS: Double,
    val armsViolationS: Double,
    val standingT: Double?,
)

/**
 * windowStartT is the start of the timed test; frames before it are skipped, frames after
 * windowStartT + chairStandDurationS are ignored. progress is the smoothed head position as a
 * fraction of the calibrated range (0 = seated, 1 = standing); null before the first usable frame.
 */
data class ChairStandState(
    val phase: Phase,
    val windowStartT: Double?,
    val firstT: Double?,
    val lastT: Double?,
    val frameCount: Int,
    val droppedFrames: Int,
    val gaps: Int,
    val smoothedHeadY: Double?,
    val progress: Double?,
    val reachSince: Double?,
    val seatSince: Double?,
    val rep: RepTracker?,
    val events: List<RepEvent>,
)

/**
 * unreliable: uncertain reps exceed unreliableUncertainFraction of all reps.
 * windowComplete: false when the recording stopped before the timed window ended (not a CDC score).
 */
data class ChairStandSummary(
    val full: Int,
    val half: Int,
    val armAssisted: Int,
    val uncertain: Int,
    val events: List<RepEvent>,
    val effectiveFps: Double,
    val lowFps: Boolean,
    val unreliable: Boolean,
    val droppedFrames: Int,
    val gaps: Int,
    val windowComplete: Boolean,
)

/** testStartT anchors the timed window to the recording's test phase; default is the first frame. */
fun initialState(testStartT: Double? = null): ChairStandState = ChairStandState(
    phase = Phase.SEATED,
    windowStartT = testStartT,
    firstT = null,
    lastT = null,
    frameCount = 0,
    droppedFrames = 0,
    gaps = 0,
    smoothedHeadY = null,
    progress = null,
    reachSince = null,
    seatSince = null,
    rep = null,
    events = emptyList(),
)
