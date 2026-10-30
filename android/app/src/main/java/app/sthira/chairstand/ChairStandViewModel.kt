package app.sthira.chairstand

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.sthira.engine.CounterResult
import app.sthira.engine.PoseFrame
import app.sthira.pose.CameraStatus
import app.sthira.pose.FrameClock
import app.sthira.pose.Lens
import app.sthira.pose.PoseSample
import app.sthira.record.CaptureInfo
import app.sthira.record.CaptureIssue
import app.sthira.record.RecordingFormat
import app.sthira.record.RecordingStore
import app.sthira.record.SaveResult
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
import java.time.Instant

/**
 * Chair-stand screen. Every change to the run goes through one inbox processed by a single
 * coroutine, so camera frames (MediaPipe thread), clock ticks and taps never race.
 * Nothing about landmarks or the participant is logged.
 */
class ChairStandViewModel(app: Application) : AndroidViewModel(app) {
    private val store = RecordingStore(app.filesDir)
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
        data class Export(val fileName: String, val target: Uri) : Msg
    }

    private val inbox = Channel<Msg>(Channel.UNLIMITED)

    // Confined to the inbox coroutine.
    private var run = RunState()
    private val recordedFrames = ArrayList<PoseFrame>()
    private var startedAt: String? = null

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
        inbox.trySend(Msg.Ui(ChairStandEvent.DismissMessage))
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
    fun onSample(sample: PoseSample) {
        _overlay.value = sample
        inbox.trySend(Msg.Sample(sample, SystemClock.elapsedRealtimeNanos()))
    }

    fun export(fileName: String, target: Uri) {
        inbox.trySend(Msg.Export(fileName, target))
    }

    override fun onCleared() {
        inbox.close()
        super.onCleared()
    }

    private fun handle(msg: Msg) {
        val m = machine ?: return
        val before = run
        when (msg) {
            is Msg.Sample -> onFrame(m, msg)
            is Msg.Tick -> if (run.isRunning) run = m.onTick(run, msg.nowNs) else return
            is Msg.Ui -> onUi(m, msg.event)
            is Msg.Export -> onExport(msg)
        }
        if (before.phase.stage != Stage.Done && run.phase.stage == Stage.Done) finishRun()
        publish()
    }

    private fun onFrame(m: RunMachine, msg: Msg.Sample) {
        val wasStarted = run.originNs != null
        val out = m.onFrame(run, msg.sample, msg.nowNs)
        run = out.state
        if (!wasStarted && run.originNs != null) startedAt = Instant.now().toString()
        val frame = out.frame ?: return
        if (_state.value.session?.recording == null) return
        if (recordedFrames.size >= RecordingFormat.MAX_FRAMES) {
            run = m.interrupt(run, msg.nowNs, CaptureIssue.FrameLimit)
            return
        }
        recordedFrames.add(frame)
    }

    private fun onUi(m: RunMachine, event: ChairStandEvent) {
        val now = SystemClock.elapsedRealtimeNanos()
        val ui = _state.value
        when (event) {
            is ChairStandEvent.FormChanged -> _state.update { it.copy(form = event.edit(it.form)) }
            ChairStandEvent.Submit -> submit()
            ChairStandEvent.EditSession -> if (!run.isRunning) {
                run = m.reset(run)
                _state.update { it.copy(session = null, result = null, recording = RecordingView.Off, camera = CameraStatus.Starting) }
            }
            is ChairStandEvent.Camera -> onCamera(m, event.status, now)
            ChairStandEvent.SwitchLens -> if (!run.isRunning) {
                run = run.copy(clock = FrameClock())
                _state.update { it.copy(lens = if (it.lens == Lens.Back) Lens.Front else Lens.Back) }
            }
            ChairStandEvent.StartTest -> if (ui.session != null && ui.camera is CameraStatus.Live && !run.isRunning) {
                recordedFrames.clear()
                startedAt = null
                run = m.requestStart(m.reset(run))
                _state.update { it.copy(result = null, recording = RecordingView.Off) }
            }
            ChairStandEvent.Stop -> run = m.stop(run, now)
            ChairStandEvent.RedoCalibration -> run = m.redoCalibration(run, now)
            ChairStandEvent.Cancel -> {
                run = m.cancel(run)
                recordedFrames.clear()
            }
            ChairStandEvent.RunAgain -> if (!run.phase.stage.isActive) {
                run = m.reset(run)
                recordedFrames.clear()
                _state.update { it.copy(result = null, recording = RecordingView.Off) }
            }
            ChairStandEvent.AppHidden -> if (run.isRunning) run = m.interrupt(run, now, CaptureIssue.AppHidden)
            is ChairStandEvent.Delete -> delete(event.fileName)
            ChairStandEvent.DismissMessage -> _state.update { it.copy(message = null, savedRecordings = store.list()) }
        }
    }

    private fun onCamera(m: RunMachine, status: CameraStatus, now: Long) {
        if (status is CameraStatus.Starting && !run.isRunning) run = run.copy(clock = FrameClock())
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

    private fun delete(fileName: String) {
        val deleted = store.delete(fileName)
        _state.update { s ->
            val recording = (s.recording as? RecordingView.Saved)?.takeIf { it.recording.fileName == fileName }
            s.copy(
                savedRecordings = store.list(),
                recording = if (recording != null) RecordingView.Off else s.recording,
                message = if (deleted) "Recording deleted from this phone." else "That recording was already gone.",
            )
        }
    }

    private fun onExport(msg: Msg.Export) {
        val ok = store.export(msg.fileName, getApplication<Application>().contentResolver, msg.target)
        val text = if (ok) "Exported. Delete the copy on this phone when you no longer need it." else "Export failed. Nothing was written."
        _state.update { it.copy(message = text) }
    }

    private fun finishRun() {
        val cfg = config ?: return
        val session = _state.value.session ?: return
        val result = ChairStandResults.resultFor(run.live.setup, cfg, session)
        val meta = session.recording
        if (meta == null) {
            _state.update { it.copy(result = result, recording = RecordingView.Off) }
            return
        }
        _state.update { it.copy(result = result, recording = RecordingView.Saving) }
        val capture = CaptureInfo(startedAt ?: Instant.now().toString(), run.imageWidth, run.imageHeight, run.issues.toList())
        val recording = when (val saved = store.save(meta, capture, run.phase.phases, recordedFrames.toList())) {
            is SaveResult.Saved -> RecordingView.Saved(saved.recording)
            is SaveResult.Invalid -> RecordingView.Invalid(saved.errors)
            is SaveResult.Failed -> RecordingView.Failed(saved.message)
        }
        recordedFrames.clear()
        _state.update { it.copy(recording = recording, savedRecordings = store.list()) }
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
                remainingS = elapsed?.let { kotlin.math.ceil(cfg.protocol.chairStandDurationS - it).toInt().coerceAtLeast(0) },
                count = ChairStandResults.countView(r.live),
                stats = LiveStats(r.fps.fps, r.personDetected, recordedFrames.size),
                issues = r.issues.toList(),
            )
        }
    }

    private fun initialState(load: ConfigLoad): ChairStandUiState = when (load) {
        is ConfigLoad.Unavailable -> ChairStandUiState(unavailable = load.errors)
        is ConfigLoad.Ok -> ChairStandUiState(
            counter = when (val c = load.config.counter) {
                is CounterResult.Ready -> CounterNotice.Ready
                is CounterResult.Untuned -> CounterNotice.Untuned(c.missing)
                is CounterResult.Invalid -> CounterNotice.Invalid(c.errors)
            },
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
