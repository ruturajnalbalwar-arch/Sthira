package app.sthira.chairstand

import app.sthira.engine.CalibrationResult
import app.sthira.engine.ChairStandState
import app.sthira.engine.ChairStandSummary
import app.sthira.engine.ContextResult
import app.sthira.engine.CounterResult
import app.sthira.engine.PoseFrame
import app.sthira.engine.StepContext
import app.sthira.engine.VideoGeometry
import app.sthira.engine.calibrate
import app.sthira.engine.finalize
import app.sthira.engine.initialState
import app.sthira.engine.step
import app.sthira.engine.summarize
import app.sthira.engine.coveringFrames

/**
 * Live chair-stand counting driven by the phase machine and the camera frames (mirrors
 * web/src/lib/count/liveCount.ts). Calibration frames are collected; at Go the first frame at or
 * after Go is added too (it is the "nearest frame after" the sit phase) and calibrate + makeContext
 * run; every later frame is stepped. Pure: every call returns a new state.
 */
sealed interface CountSetup {
    data object Waiting : CountSetup
    data class Starting(val testStartT: Double, val phases: List<PhaseSpan>, val geometry: VideoGeometry) : CountSetup
    data object NotTuned : CountSetup
    data class CalibrationFailed(val reasons: List<String>) : CountSetup
    data class ContextFailed(val errors: List<String>) : CountSetup
    data class Counting(val ctx: StepContext, val chair: ChairStandState, val testStartT: Double) : CountSetup
    data class Finished(val summary: ChairStandSummary) : CountSetup
}

data class LiveCountState(
    val stage: Stage = Stage.Idle,
    val stageStartT: Double = 0.0,
    val calibrationFrames: List<PoseFrame> = emptyList(),
    val setup: CountSetup = CountSetup.Waiting,
)

object LiveCount {
    val INITIAL = LiveCountState()

    /** Call after every phase-machine change, with the analysed image size. */
    fun onPhase(state: LiveCountState, phase: PhaseState, geometry: VideoGeometry, counter: CounterResult): LiveCountState {
        if (phase.stage == Stage.Idle) return INITIAL
        val base = if (isNewCalibration(state, phase)) INITIAL else state
        val synced = base.copy(stage = phase.stage, stageStartT = phase.stageStartT)
        val testSpan = PhaseMachine.lastSpan(phase.phases, Stage.Test)
        val atGo = if (synced.setup is CountSetup.Waiting && testSpan != null) {
            synced.copy(setup = CountSetup.Starting(testSpan.startT, phase.phases, geometry))
        } else {
            synced
        }
        if (phase.stage != Stage.Done) return atGo
        val setup = atGo.setup
        val begun = if (setup is CountSetup.Starting) startTest(atGo, setup, counter) else atGo
        val counting = begun.setup
        return if (counting is CountSetup.Counting) begun.copy(setup = finish(counting)) else begun
    }

    /** Call for every frame, after the phase machine has seen its time. */
    tailrec fun onFrame(state: LiveCountState, frame: PoseFrame, counter: CounterResult): LiveCountState {
        when (val setup = state.setup) {
            is CountSetup.Counting -> return state.copy(setup = setup.copy(chair = step(setup.chair, frame, setup.ctx)))
            is CountSetup.Starting -> {
                val collected = state.copy(calibrationFrames = state.calibrationFrames + frame)
                if (frame.t < setup.testStartT) return collected
                val started = startTest(collected, setup, counter)
                // The frame at or after Go is both the last calibration frame and the first test frame.
                return if (started.setup is CountSetup.Counting) onFrame(started, frame, counter) else started
            }
            CountSetup.Waiting -> {
                if (!state.stage.isCalibrating) return state
                return state.copy(calibrationFrames = state.calibrationFrames + frame)
            }
            else -> return state
        }
    }

    /** A (re)started calibration opens a fresh phase list whose first span starts at the stage start. */
    private fun isNewCalibration(state: LiveCountState, phase: PhaseState): Boolean {
        val first = phase.phases.firstOrNull() ?: return false
        val restarted = first.startT == phase.stageStartT
        return restarted && (state.stage != phase.stage || state.stageStartT != phase.stageStartT)
    }

    private fun startTest(state: LiveCountState, start: CountSetup.Starting, counter: CounterResult): LiveCountState =
        state.copy(calibrationFrames = emptyList(), setup = beginTest(state.calibrationFrames, start, counter))

    private fun framesIn(frames: List<PoseFrame>, span: PhaseSpan?): List<PoseFrame> =
        if (span == null) emptyList() else coveringFrames(frames, span.startT, span.endT)

    private fun beginTest(frames: List<PoseFrame>, start: CountSetup.Starting, counter: CounterResult): CountSetup {
        if (counter !is CounterResult.Ready) return CountSetup.NotTuned
        val sit = framesIn(frames, PhaseMachine.lastSpan(start.phases, Stage.CalibrationSit))
        val stand = framesIn(frames, PhaseMachine.lastSpan(start.phases, Stage.CalibrationStand))
        val calib = when (val result = calibrate(sit, stand, start.geometry, counter.params)) {
            is CalibrationResult.Failed -> return CountSetup.CalibrationFailed(result.reasons)
            is CalibrationResult.Ok -> result.calib
        }
        return when (val context = counter.makeContext(start.geometry, calib)) {
            is ContextResult.Failed -> CountSetup.ContextFailed(context.errors)
            is ContextResult.Ok -> CountSetup.Counting(context.ctx, initialState(start.testStartT), start.testStartT)
        }
    }

    private fun finish(setup: CountSetup.Counting): CountSetup =
        CountSetup.Finished(summarize(finalize(setup.chair, setup.ctx), setup.ctx))

    /** Live summary while counting (credits a stand once standing is reached). */
    fun liveSummary(state: LiveCountState): ChairStandSummary? = when (val s = state.setup) {
        is CountSetup.Counting -> summarize(s.chair, s.ctx)
        is CountSetup.Finished -> s.summary
        else -> null
    }
}
