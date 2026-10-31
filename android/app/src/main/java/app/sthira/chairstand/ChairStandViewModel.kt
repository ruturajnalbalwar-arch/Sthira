package app.sthira.chairstand

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.sthira.R
import app.sthira.pose.CameraStatus
import app.sthira.pose.FrameClock
import app.sthira.pose.Lens
import app.sthira.pose.PoseSample
import app.sthira.pose.PoseTracker
import app.sthira.ui.common.UiText
import app.sthira.ui.common.uiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.math.ceil
import kotlin.math.round

/**
 * Chair-stand screen. Every change to the run goes through one inbox processed by a single
 * coroutine, so camera frames (MediaPipe thread), clock ticks and taps never race.
 * Nothing about landmarks or the participant is logged or saved.
 */
class ChairStandViewModel(app: Application) : AndroidViewModel(app) {
    private val config: ChairStandConfig?
    private val machine: RunMachine?

    private val _state: MutableStateFlow<ChairStandUiState>
    val state: StateFlow<ChairStandUiState>

    /** The skeleton overlay changes every frame, so it is a separate stream from the screen state. */
    private val _overlay = MutableStateFlow<PoseSample?>(null)
    val overlay: StateFlow<PoseSample?> = _overlay.asStateFlow()

    private sealed interface Msg {
        data class Sample(val sample: PoseSample, val nowNs: Long) : Msg
        data class Tick(val nowNs: Long) : Msg
        data class Ui(val event: ChairStandEvent) : Msg
    }

    private val inbox = Channel<Msg>(Channel.UNLIMITED)

    /**
     * Created with the screen, so the pose model loads while the setup form is open and the skeleton
     * shows on the first camera frame. Shared by both lenses; released in [onCleared].
     */
    val tracker = PoseTracker(app, ::onSample) {
        onEvent(ChairStandEvent.Camera(CameraStatus.Failed(uiText(R.string.pose_error_tracking_stopped), trackingStopped = true)))
    }

    // Confined to the inbox coroutine.
    private var run = RunState()

    init {
        val load = loadConfig(app)
        config = (load as? ConfigLoad.Ok)?.config
        machine = config?.let { RunMachine(it.phaseConfig, it.counter) }
        _state = MutableStateFlow(initialState(load))
        state = _state.asStateFlow()
        viewModelScope.launch(Dispatchers.Default) { for (msg in inbox) handle(msg) }
        viewModelScope.launch {
            while (isActive) {
                delay(TICK_MS)
                inbox.trySend(Msg.Tick(SystemClock.elapsedRealtimeNanos()))
            }
        }
    }

    fun onEvent(event: ChairStandEvent) {
        // Form edits only touch the form: apply them at once, atomically, to the latest form (a queued
        // whole-form copy could be built from a stale form and undo an earlier edit).
        if (event is ChairStandEvent.FormChanged) {
            _state.update { it.copy(form = event.edit(it.form)) }
            return
        }
        inbox.trySend(Msg.Ui(event))
    }

    /** Called on the MediaPipe result thread. */
    private fun onSample(sample: PoseSample) {
        _overlay.value = sample
        inbox.trySend(Msg.Sample(sample, SystemClock.elapsedRealtimeNanos()))
    }

    override fun onCleared() {
        inbox.close()
        tracker.close()
        super.onCleared()
    }

    private fun handle(msg: Msg) {
        val m = machine ?: return
        val before = run
        when (msg) {
            is Msg.Sample -> run = m.onFrame(run, msg.sample, msg.nowNs)
            is Msg.Tick -> if (run.isRunning) run = m.onTick(run, msg.nowNs) else return
            is Msg.Ui -> onUi(m, msg.event)
        }
        if (before.phase.stage != Stage.Done && run.phase.stage == Stage.Done) finishRun()
        publish()
    }

    private fun onUi(m: RunMachine, event: ChairStandEvent) {
        val now = SystemClock.elapsedRealtimeNanos()
        val ui = _state.value
        when (event) {
            is ChairStandEvent.FormChanged -> _state.update { it.copy(form = event.edit(it.form)) }
            ChairStandEvent.Submit -> submit()
            ChairStandEvent.EditSession -> if (!run.isRunning) {
                run = m.reset(run)
                _state.update { it.copy(session = null, result = null, camera = CameraStatus.Starting) }
            }
            is ChairStandEvent.Camera -> onCamera(m, event.status, now)
            ChairStandEvent.SwitchLens -> if (!run.isRunning) {
                // The other lens has its own capture clock and its own picture: start both afresh.
                run = run.copy(clock = FrameClock(), personDetected = false)
                _overlay.value = null
                _state.update { it.copy(lens = if (it.lens == Lens.Back) Lens.Front else Lens.Back) }
            }
            ChairStandEvent.StartTest -> if (ui.session != null && ui.camera is CameraStatus.Live && !run.isRunning) {
                run = m.requestStart(m.reset(run))
                _state.update { it.copy(result = null) }
            }
            ChairStandEvent.Stop -> run = m.stop(run, now)
            ChairStandEvent.RedoCalibration -> run = m.redoCalibration(run, now)
            ChairStandEvent.Cancel -> run = m.cancel(run)
            ChairStandEvent.RunAgain -> if (!run.phase.stage.isActive) {
                run = m.reset(run)
                _state.update { it.copy(result = null) }
            }
            ChairStandEvent.AppHidden -> if (run.isRunning) run = m.interrupt(run, now, CaptureIssue.AppHidden)
        }
    }

    private fun onCamera(m: RunMachine, status: CameraStatus, now: Long) {
        if (status is CameraStatus.Starting && !run.isRunning) run = run.copy(clock = FrameClock())
        // A stopped or restarting camera must not leave its last skeleton on screen.
        if (status !is CameraStatus.Live) _overlay.value = null
        if (status is CameraStatus.Failed && run.isRunning) {
            val issue = if (status.trackingStopped) CaptureIssue.DetectionFailed else CaptureIssue.CameraEnded
            run = m.interrupt(run, now, issue)
        }
        _state.update { it.copy(camera = status) }
    }

    private fun submit() {
        when (val result = SessionSetup.validate(_state.value.form)) {
            is SetupResult.Invalid -> _state.update { it.copy(formErrors = result.errors) }
            is SetupResult.Ok -> _state.update { it.copy(formErrors = emptyMap(), session = result.session) }
        }
    }

    private fun finishRun() {
        val cfg = config ?: return
        val session = _state.value.session ?: return
        val result = ChairStandResults.resultFor(run.live.setup, cfg, session)
        _state.update { it.copy(result = result) }
    }

    private fun publish() {
        val cfg = config ?: return
        val r = run
        val testStart = PhaseMachine.lastSpan(r.phase.phases, Stage.Test)?.startT
        val elapsed = if (r.phase.stage == Stage.Test && testStart != null) maxOf(0.0, r.nowT - testStart) else null
        _state.update { s ->
            s.copy(
                stage = r.phase.stage,
                startRequested = r.startRequested,
                countdownS = PhaseMachine.countdown(r.phase, r.nowT, cfg.phaseConfig),
                elapsedS = elapsed?.toInt(),
                remainingS = elapsed?.let { ceil(cfg.protocol.chairStandDurationS - it).toInt().coerceAtLeast(0) },
                count = ChairStandResults.countView(r.live),
                // Whole fps only (as displayed), so an unchanged screen is not re-emitted on every frame.
                stats = LiveStats(r.fps.fps?.let { round(it) }, r.personDetected),
                issues = r.issues.toList(),
            )
        }
    }

    private fun initialState(load: ConfigLoad): ChairStandUiState = when (load) {
        is ConfigLoad.Unavailable -> ChairStandUiState(unavailable = load.errors.map { UiText.Raw(it) })
        is ConfigLoad.Ok -> ChairStandUiState(
            durationS = load.config.protocol.chairStandDurationS,
            normsNote = load.config.normsNote,
        )
    }

    private companion object {
        const val TICK_MS = 100L

        fun loadConfig(app: Application): ConfigLoad = try {
            val read = { name: String -> app.assets.open(name).bufferedReader().use { it.readText() } }
            ChairStandConfigLoader.load(read("params.json"), read("norms.json"))
        } catch (e: IOException) {
            ConfigLoad.Unavailable(listOf("params.json or norms.json is missing from the app."))
        }
    }
}
