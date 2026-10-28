package app.sthira.chairstand

/**
 * Guided chair-stand flow (mirrors web/src/lib/record/phaseMachine.ts):
 * Idle -> CalibrationStand -> CalibrationSit -> Test -> Done.
 * Standing is calibrated first so the person is seated at "Go", as the CDC test requires.
 * All times are seconds since the run started. Every duration comes from params.json.
 */
enum class Stage(val wireName: String?) {
    Idle(null),
    CalibrationStand("calibration-stand"),
    CalibrationSit("calibration-sit"),
    Test("test"),
    Done(null),
    ;

    val isActive: Boolean get() = this == CalibrationStand || this == CalibrationSit || this == Test
    val isCalibrating: Boolean get() = this == CalibrationStand || this == CalibrationSit
}

/** A span of a phase. [phase] is CalibrationStand, CalibrationSit or Test. */
data class PhaseSpan(val phase: Stage, val startT: Double, val endT: Double)

data class PhaseConfig(val calibrationStandS: Double, val calibrationSitS: Double, val testDurationS: Double)

data class PhaseState(
    val stage: Stage = Stage.Idle,
    val stageStartT: Double = 0.0,
    val phases: List<PhaseSpan> = emptyList(),
    val endT: Double? = null,
)

sealed interface PhaseEvent {
    data class Start(val t: Double) : PhaseEvent
    data class Tick(val t: Double) : PhaseEvent
    data class Stop(val t: Double) : PhaseEvent
    data class RedoCalibration(val t: Double) : PhaseEvent
    /** The capture ended early (camera lost): close the open phase. */
    data class Interrupt(val t: Double) : PhaseEvent
    data object Cancel : PhaseEvent
    data object Reset : PhaseEvent
}

object PhaseMachine {
    val INITIAL = PhaseState()

    fun reduce(state: PhaseState, event: PhaseEvent, config: PhaseConfig): PhaseState {
        val active = state.stage.isActive
        return when (event) {
            is PhaseEvent.Start -> if (state.stage == Stage.Idle) startCalibration(event.t) else state
            is PhaseEvent.Tick -> if (active) advance(state, event.t, config) else state
            is PhaseEvent.Stop -> if (state.stage == Stage.Test) finish(state, event.t) else state
            is PhaseEvent.RedoCalibration -> if (active) startCalibration(event.t) else state
            is PhaseEvent.Interrupt -> if (active) finish(state, maxOf(event.t, state.stageStartT)) else state
            PhaseEvent.Cancel -> if (active) INITIAL else state
            PhaseEvent.Reset -> if (state.stage == Stage.Done) INITIAL else state
        }
    }

    /** The last span of [phase], if any. */
    fun lastSpan(phases: List<PhaseSpan>, phase: Stage): PhaseSpan? = phases.lastOrNull { it.phase == phase }

    /** Whole seconds left in a calibration countdown; null outside calibration. */
    fun countdown(state: PhaseState, t: Double, config: PhaseConfig): Int? {
        val duration = when (state.stage) {
            Stage.CalibrationStand -> config.calibrationStandS
            Stage.CalibrationSit -> config.calibrationSitS
            else -> return null
        }
        val elapsed = maxOf(0.0, t - state.stageStartT)
        return maxOf(0, kotlin.math.ceil(duration - elapsed).toInt())
    }

    private fun startCalibration(t: Double): PhaseState = enter(INITIAL, Stage.CalibrationStand, t)

    private fun enter(state: PhaseState, stage: Stage, t: Double): PhaseState =
        state.copy(stage = stage, stageStartT = t, phases = closeLast(state.phases, t) + PhaseSpan(stage, t, t))

    private fun finish(state: PhaseState, t: Double): PhaseState =
        state.copy(stage = Stage.Done, phases = closeLast(state.phases, t), endT = t)

    private fun closeLast(phases: List<PhaseSpan>, t: Double): List<PhaseSpan> {
        val last = phases.lastOrNull() ?: return phases
        return phases.dropLast(1) + last.copy(endT = t)
    }

    private fun duration(stage: Stage, config: PhaseConfig): Double? = when (stage) {
        Stage.CalibrationStand -> config.calibrationStandS
        Stage.CalibrationSit -> config.calibrationSitS
        Stage.Test -> config.testDurationS
        Stage.Idle, Stage.Done -> null
    }

    /** Boundaries are placed at the exact protocol time, not at the late tick that noticed them. */
    private tailrec fun advance(state: PhaseState, t: Double, config: PhaseConfig): PhaseState {
        val d = duration(state.stage, config) ?: return state
        val boundary = state.stageStartT + d
        if (t < boundary) return state
        return when (state.stage) {
            Stage.CalibrationStand -> advance(enter(state, Stage.CalibrationSit, boundary), t, config)
            Stage.CalibrationSit -> advance(enter(state, Stage.Test, boundary), t, config)
            else -> finish(state, boundary)
        }
    }
}
