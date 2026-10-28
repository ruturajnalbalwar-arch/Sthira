package app.sthira.chairstand

import app.sthira.engine.ChairStandSummary
import app.sthira.engine.norms.Verdict
import app.sthira.pose.CameraStatus
import app.sthira.pose.Lens
import app.sthira.record.CaptureIssue
import app.sthira.record.SavedRecording

/** How the counter can run with the shipped params.json. */
sealed interface CounterNotice {
    data object Ready : CounterNotice
    data class Untuned(val missing: List<String>) : CounterNotice
    data class Invalid(val errors: List<String>) : CounterNotice
}

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

/** The result card after the run. [noCount] explains a run without a score. */
data class ResultView(val summary: ChairStandSummary?, val verdict: Verdict?, val noCount: String?)

sealed interface RecordingView {
    data object Off : RecordingView
    data object Saving : RecordingView
    data class Saved(val recording: SavedRecording) : RecordingView
    data class Invalid(val errors: List<String>) : RecordingView
    data class Failed(val message: String) : RecordingView
}

data class LiveStats(val fps: Double?, val personDetected: Boolean, val framesRecorded: Int)

data class ChairStandUiState(
    val unavailable: List<String>? = null,
    val counter: CounterNotice = CounterNotice.Ready,
    val durationS: Double = 0.0,
    val normsNote: String? = null,
    val form: SetupForm = SetupForm(),
    val formErrors: Map<SetupField, String> = emptyMap(),
    val session: Session? = null,
    val lens: Lens = Lens.Back,
    val camera: CameraStatus = CameraStatus.Starting,
    val stage: Stage = Stage.Idle,
    val startRequested: Boolean = false,
    val countdownS: Int? = null,
    val elapsedS: Int? = null,
    val remainingS: Int? = null,
    val count: CountView = CountView.Waiting,
    val stats: LiveStats = LiveStats(null, false, 0),
    val issues: List<CaptureIssue> = emptyList(),
    val result: ResultView? = null,
    val recording: RecordingView = RecordingView.Off,
    val savedRecordings: List<SavedRecording> = emptyList(),
    val message: String? = null,
) {
    val isRunning: Boolean get() = startRequested || stage.isActive
    val personLost: Boolean get() = stage.isActive && camera is CameraStatus.Live && !stats.personDetected
}

sealed interface ChairStandEvent {
    data class FormChanged(val form: SetupForm) : ChairStandEvent
    data object Submit : ChairStandEvent
    data object EditSession : ChairStandEvent
    data class Camera(val status: CameraStatus) : ChairStandEvent
    data object SwitchLens : ChairStandEvent
    data object StartTest : ChairStandEvent
    data object Stop : ChairStandEvent
    data object RedoCalibration : ChairStandEvent
    data object Cancel : ChairStandEvent
    data object RunAgain : ChairStandEvent
    data object AppHidden : ChairStandEvent
    data class Delete(val fileName: String) : ChairStandEvent
    data object DismissMessage : ChairStandEvent
}
