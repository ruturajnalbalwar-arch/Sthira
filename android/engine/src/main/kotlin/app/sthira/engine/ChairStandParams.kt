package app.sthira.engine

/** Validated, flat view of the parameters the detector reads (mirror of ChairStandParams in required.ts). */
data class ChairStandParams(
    val chairStandDurationS: Double,
    val calibrationSitS: Double,
    val calibrationStandS: Double,
    val leaveSeatFraction: Double,
    val reachFraction: Double,
    val seatFraction: Double,
    val minStandDwellS: Double,
    val minSitDwellS: Double,
    val smoothingCutoffHz: Double,
    val armsCrossedMaxRatio: Double,
    val armsViolationMinS: Double,
    val visibilityMin: Double,
    val uncertainFrameMaxFraction: Double,
    val calibStillnessMaxRatio: Double,
    val calibMinRangeToBodyScaleRatio: Double,
    val minFps: Double,
    val timeUpCountFraction: Double,
    val unreliableUncertainFraction: Double,
    val maxGapS: Double,
) {
    operator fun get(name: ParamName): Double = when (name) {
        ParamName.CHAIR_STAND_DURATION_S -> chairStandDurationS
        ParamName.CALIBRATION_SIT_S -> calibrationSitS
        ParamName.CALIBRATION_STAND_S -> calibrationStandS
        ParamName.LEAVE_SEAT_FRACTION -> leaveSeatFraction
        ParamName.REACH_FRACTION -> reachFraction
        ParamName.SEAT_FRACTION -> seatFraction
        ParamName.MIN_STAND_DWELL_S -> minStandDwellS
        ParamName.MIN_SIT_DWELL_S -> minSitDwellS
        ParamName.SMOOTHING_CUTOFF_HZ -> smoothingCutoffHz
        ParamName.ARMS_CROSSED_MAX_RATIO -> armsCrossedMaxRatio
        ParamName.ARMS_VIOLATION_MIN_S -> armsViolationMinS
        ParamName.VISIBILITY_MIN -> visibilityMin
        ParamName.UNCERTAIN_FRAME_MAX_FRACTION -> uncertainFrameMaxFraction
        ParamName.CALIB_STILLNESS_MAX_RATIO -> calibStillnessMaxRatio
        ParamName.CALIB_MIN_RANGE_TO_BODY_SCALE_RATIO -> calibMinRangeToBodyScaleRatio
        ParamName.MIN_FPS -> minFps
        ParamName.TIME_UP_COUNT_FRACTION -> timeUpCountFraction
        ParamName.UNRELIABLE_UNCERTAIN_FRACTION -> unreliableUncertainFraction
        ParamName.MAX_GAP_S -> maxGapS
    }

    /** Values in ParamName order. */
    fun toMap(): Map<ParamName, Double> = ParamName.entries.associateWith { get(it) }

    companion object {
        /** Builds params from a complete map; null when any of the 19 names is missing. */
        fun fromMap(values: Map<ParamName, Double>): ChairStandParams? {
            if (!ParamName.entries.all(values::containsKey)) return null
            val v = { name: ParamName -> values.getValue(name) }
            return ChairStandParams(
                v(ParamName.CHAIR_STAND_DURATION_S), v(ParamName.CALIBRATION_SIT_S), v(ParamName.CALIBRATION_STAND_S),
                v(ParamName.LEAVE_SEAT_FRACTION), v(ParamName.REACH_FRACTION), v(ParamName.SEAT_FRACTION),
                v(ParamName.MIN_STAND_DWELL_S), v(ParamName.MIN_SIT_DWELL_S), v(ParamName.SMOOTHING_CUTOFF_HZ),
                v(ParamName.ARMS_CROSSED_MAX_RATIO), v(ParamName.ARMS_VIOLATION_MIN_S), v(ParamName.VISIBILITY_MIN),
                v(ParamName.UNCERTAIN_FRAME_MAX_FRACTION), v(ParamName.CALIB_STILLNESS_MAX_RATIO),
                v(ParamName.CALIB_MIN_RANGE_TO_BODY_SCALE_RATIO), v(ParamName.MIN_FPS),
                v(ParamName.TIME_UP_COUNT_FRACTION), v(ParamName.UNRELIABLE_UNCERTAIN_FRACTION), v(ParamName.MAX_GAP_S),
            )
        }
    }
}
