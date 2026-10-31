package app.sthira.chairstand

import app.sthira.engine.ChairStandSummary
import app.sthira.engine.norms.Verdict
import app.sthira.pose.CameraStatus
import app.sthira.pose.Lens
import app.sthira.ui.common.UiText

/** What the board shows about the count right now. */
sealed interface CountView {
    data object Waiting : CountView
    data object NotTuned : CountView
    data object Checking : CountView
    data class CalibrationFailed(val reasons: List<String>) : CountView
    data class ContextFailed(val errors: List<String>) : CountView
    data class Counting(val summary: ChairStandSummary) : CountView
    data class Finished(val summary: ChairStandSummary) : CountView
}

/**
 * The result card after the run. [noCount] explains a run without a score. [norm] is the CDC row used
 * (or why none applies) and [notComparedReasons] says why a [Verdict.NotCompared] run is not compared.
 */
data class ResultView(
    val summary: ChairStandSummary?,
    val verdict: Verdict?,
    val noCount: UiText?,
    val norm: NormChoice? = null,
    val notComparedReasons: List<UiText> = emptyList(),
)

/** [fps] is rounded to whole frames per second, so the screen state does not change on every frame. */
data class LiveStats(val fps: Double?, val personDetected: Boolean) {
    /** Kept so the debug font-scale previews still build; nothing is recorded any more. */
    @Suppress("UNUSED_PARAMETER")
    constructor(fps: Double?, personDetected: Boolean, framesRecorded: Int) : this(fps, personDetected)
}

data class ChairStandUiState(
    val unavailable: List<UiText>? = null,
    val durationS: Double = 0.0,
    val normsNote: String? = null,
    val form: SetupForm = SetupForm(),
    val session: Session? = null,
    val lens: Lens = Lens.Back,
    val camera: CameraStatus = CameraStatus.Starting,
    val stage: Stage = Stage.Idle,
    /** Run time the current stage began; changes when calibration is redone, so the cue is spoken again. */
    val stageStartT: Double = 0.0,
    val startRequested: Boolean = false,
    val countdownS: Int? = null,
    val elapsedS: Int? = null,
    val remainingS: Int? = null,
    val count: CountView = CountView.Waiting,
    val stats: LiveStats = LiveStats(null, false),
    val issues: List<CaptureIssue> = emptyList(),
    val result: ResultView? = null,
) {
    val isRunning: Boolean get() = startRequested || stage.isActive
    val personLost: Boolean get() = stage.isActive && camera is CameraStatus.Live && !stats.personDetected

    /** Start was tapped but nobody is in view yet: calibration waits for a detected person. */
    val waitingForPerson: Boolean get() = startRequested && stage == Stage.Idle && !stats.personDetected

    /** Which of the three screens is shown: about the person, the camera, or the result. */
    val step: FlowStep
        get() = when {
            session == null -> FlowStep.Person
            stage == Stage.Done -> FlowStep.Result
            else -> FlowStep.Camera
        }
}

/** The three steps of the chair-stand flow, in order; [number] is shown as "Step n of 3". */
enum class FlowStep(val number: Int) {
    Person(1),
    Camera(2),
    Result(3),
}

sealed interface ChairStandEvent {
    class FormChanged(val edit: SetupEdit) : ChairStandEvent
    data object Submit : ChairStandEvent
    data object EditSession : ChairStandEvent
    /** Back to step 1 with an empty form, for the next person. */
    data object NewPerson : ChairStandEvent
    data class Camera(val status: CameraStatus) : ChairStandEvent
    data object SwitchLens : ChairStandEvent
    data object StartTest : ChairStandEvent
    data object Stop : ChairStandEvent
    data object RedoCalibration : ChairStandEvent
    data object Cancel : ChairStandEvent
    data object RunAgain : ChairStandEvent
    data object AppHidden : ChairStandEvent
}
