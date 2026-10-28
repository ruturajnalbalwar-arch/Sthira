package app.sthira.engine

/** Parameter classes allowed in params.json (mirror of params/schema.ts PARAM_CLASSES). */
enum class ParamClass(val wire: String) {
    PROTOCOL_CONSTANT("protocol-constant"),
    PER_PERSON_CALIBRATION("per-person-calibration"),
    FITTED("fitted"),
    CITED_REFERENCE("cited-reference");

    companion object {
        fun fromWire(wire: String): ParamClass? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * Every parameter the chair-stand detector reads, with required class and unit, in the
 * same order as REQUIRED_PARAMS in params/required.ts (the order of "missing" lists).
 */
enum class ParamName(val key: String, val requiredClass: ParamClass, val unit: String) {
    CHAIR_STAND_DURATION_S("chairStandDurationS", ParamClass.PROTOCOL_CONSTANT, "s"),
    CALIBRATION_SIT_S("calibrationSitS", ParamClass.PROTOCOL_CONSTANT, "s"),
    CALIBRATION_STAND_S("calibrationStandS", ParamClass.PROTOCOL_CONSTANT, "s"),
    LEAVE_SEAT_FRACTION("leaveSeatFraction", ParamClass.FITTED, "fraction"),
    REACH_FRACTION("reachFraction", ParamClass.FITTED, "fraction"),
    SEAT_FRACTION("seatFraction", ParamClass.FITTED, "fraction"),
    MIN_STAND_DWELL_S("minStandDwellS", ParamClass.FITTED, "s"),
    MIN_SIT_DWELL_S("minSitDwellS", ParamClass.FITTED, "s"),
    SMOOTHING_CUTOFF_HZ("smoothingCutoffHz", ParamClass.FITTED, "Hz"),
    ARMS_CROSSED_MAX_RATIO("armsCrossedMaxRatio", ParamClass.FITTED, "ratio"),
    ARMS_VIOLATION_MIN_S("armsViolationMinS", ParamClass.FITTED, "s"),
    VISIBILITY_MIN("visibilityMin", ParamClass.FITTED, "fraction"),
    UNCERTAIN_FRAME_MAX_FRACTION("uncertainFrameMaxFraction", ParamClass.FITTED, "fraction"),
    CALIB_STILLNESS_MAX_RATIO("calibStillnessMaxRatio", ParamClass.FITTED, "ratio"),
    CALIB_MIN_RANGE_TO_BODY_SCALE_RATIO("calibMinRangeToBodyScaleRatio", ParamClass.FITTED, "ratio"),
    MIN_FPS("minFps", ParamClass.FITTED, "fps"),
    TIME_UP_COUNT_FRACTION("timeUpCountFraction", ParamClass.FITTED, "fraction"),
    UNRELIABLE_UNCERTAIN_FRACTION("unreliableUncertainFraction", ParamClass.FITTED, "fraction"),
    MAX_GAP_S("maxGapS", ParamClass.FITTED, "s");

    companion object {
        fun fromKey(key: String): ParamName? = entries.firstOrNull { it.key == key }
    }
}
