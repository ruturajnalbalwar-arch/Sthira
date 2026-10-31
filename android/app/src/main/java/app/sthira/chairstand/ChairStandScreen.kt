package app.sthira.chairstand

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import app.sthira.R
import app.sthira.pose.CameraPermissionGate
import app.sthira.settings.LocalAppSettings
import app.sthira.ui.common.Banner
import app.sthira.ui.common.BannerKind
import app.sthira.ui.common.BannerText
import app.sthira.ui.common.Body
import app.sthira.ui.common.BulletList
import app.sthira.ui.common.ScreenScaffold
import app.sthira.ui.common.UiText
import app.sthira.ui.common.asString
import app.sthira.ui.theme.Sthira
import app.sthira.ui.theme.SthiraType
import app.sthira.voice.rememberVoice

/**
 * The chair-stand test as three steps, each with one main action at the bottom:
 * 1 about the person (Next), 2 camera (Start, then Stop), 3 result (Run again).
 */
@Composable
fun ChairStandScreen(onBack: () -> Unit, vm: ChairStandViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    BackHandler(enabled = state.isRunning) { vm.onEvent(ChairStandEvent.Cancel) }
    val leave = {
        if (state.isRunning) vm.onEvent(ChairStandEvent.Cancel)
        onBack()
    }
    VoiceCues(state)
    val title = stringResource(R.string.cs_title)
    val unavailable = state.unavailable
    val session = state.session
    when {
        unavailable != null -> ScreenScaffold(title, leave) { Unavailable(unavailable) }
        session == null -> PersonStep(title, state, vm, leave)
        state.step == FlowStep.Result -> ResultStep(title, state, vm, leave)
        else -> CameraStep(title, state, session, vm, leave)
    }
}

@Composable
private fun PersonStep(title: String, state: ChairStandUiState, vm: ChairStandViewModel, leave: () -> Unit) {
    ScreenScaffold(
        title, leave,
        bottomBar = { ContinueBar(state.form) { vm.onEvent(ChairStandEvent.Submit) } },
        fullBleed = { StepHeader(FlowStep.Person) },
    ) {
        PrivacyRow()
        SetupFormUi(state.form) { vm.onEvent(ChairStandEvent.FormChanged(it)) }
    }
}

/** The overlay stream is handed down, not collected here, so camera frames do not recompose this screen. */
@Composable
private fun CameraStep(title: String, state: ChairStandUiState, session: Session, vm: ChairStandViewModel, leave: () -> Unit) {
    KeepScreenOn(state.isRunning)
    AppHiddenWatcher { vm.onEvent(ChairStandEvent.AppHidden) }
    CameraPermissionGate(
        denied = { card -> ScreenScaffold(title, leave, fullBleed = { StepHeader(FlowStep.Camera) }) { card() } },
    ) {
        ScreenScaffold(
            title, leave,
            bottomBar = { RunControls(state) { vm.onEvent(it) } },
            fullBleed = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    StepHeader(FlowStep.Camera)
                    CameraStage(
                        state, vm.tracker, vm.overlay,
                        onSwitchLens = { vm.onEvent(ChairStandEvent.SwitchLens) },
                        onStatus = { vm.onEvent(ChairStandEvent.Camera(it)) },
                    )
                }
            },
        ) {
            RunAlerts(state)
            DetailsRow(session, canEdit = !state.isRunning) { vm.onEvent(ChairStandEvent.EditSession) }
        }
    }
}

@Composable
private fun ResultStep(title: String, state: ChairStandUiState, vm: ChairStandViewModel, leave: () -> Unit) {
    ScreenScaffold(
        title, leave,
        bottomBar = { ResultControls { vm.onEvent(it) } },
        fullBleed = { StepHeader(FlowStep.Result) },
    ) {
        // The same words the physio reads out (and the voice says) when the test ends.
        Text(
            stringResource(stageTitle(Stage.Done, false)), style = SthiraType.title, color = Sthira.colors.text,
            textAlign = TextAlign.Start, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        RunAlerts(state)
        val result = state.result
        if (result != null) ResultCard(result, state.durationS, state.normsNote)
    }
}

/**
 * Speaks each step's instruction once, when the step changes, if the voice setting is on and the
 * phone has a voice for the app language. Redoing calibration starts the stand step again, so it is
 * said again. Nothing is spoken while the app is hidden, and speech stops when the screen closes.
 */
@Composable
private fun VoiceCues(state: ChairStandUiState) {
    val voiceOn = LocalAppSettings.current.voiceOn
    val voice = rememberVoice()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val cue = spokenCue(state)
    val text = cue?.let { stringResource(it) }
    LaunchedEffect(cue, state.stage, state.stageStartT) {
        when {
            text == null -> voice.stop()
            voiceOn && voice.available && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) -> voice.speak(text)
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) voice.stop() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            voice.stop()
        }
    }
}

@Composable
private fun Unavailable(errors: List<UiText>) {
    Banner(BannerKind.Danger) {
        BannerText(stringResource(R.string.cs_unavailable_title))
        Body(stringResource(R.string.cs_unavailable_body))
        BulletList(errors.map { it.asString() }, mono = true)
    }
}

/** Keeps the display on during a run, since the phone sits on a tripod untouched. */
@Composable
private fun KeepScreenOn(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(on) {
        view.keepScreenOn = on
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun AppHiddenWatcher(onHidden: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) onHidden() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
