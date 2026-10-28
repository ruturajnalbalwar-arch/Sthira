package app.sthira.chairstand

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import app.sthira.pose.CameraPermissionGate
import app.sthira.pose.Lens
import app.sthira.ui.common.ActionButton
import app.sthira.ui.common.Banner
import app.sthira.ui.common.BannerKind
import app.sthira.ui.common.BannerText
import app.sthira.ui.common.Body
import app.sthira.ui.common.BulletList
import app.sthira.ui.common.LinkButton
import app.sthira.ui.common.Muted
import app.sthira.ui.common.ScreenScaffold
import app.sthira.ui.common.Tone
import app.sthira.ui.theme.Space

private const val TITLE = "30-second chair stand"

@Composable
fun ChairStandScreen(onBack: () -> Unit, vm: ChairStandViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    BackHandler(enabled = state.isRunning) { vm.onEvent(ChairStandEvent.Cancel) }
    val export = rememberExporter { name, uri -> vm.export(name, uri) }
    val leave = {
        if (state.isRunning) vm.onEvent(ChairStandEvent.Cancel)
        onBack()
    }
    val unavailable = state.unavailable
    val session = state.session
    when {
        unavailable != null -> ScreenScaffold(TITLE, leave) { Unavailable(unavailable) }
        session == null -> ScreenScaffold(
            TITLE, leave,
            bottomBar = { ContinueBar(state.form) { vm.onEvent(ChairStandEvent.Submit) } },
        ) {
            Muted("Counted from head and shoulder height, so loose clothing is fine. Use participant codes, never names.")
            CounterStatusCard(state.counter)
            PrivacyRow()
            Message(state.message) { vm.onEvent(ChairStandEvent.DismissMessage) }
            SetupFormUi(state.form) { vm.onEvent(ChairStandEvent.FormChanged(it)) }
            SavedRecordingsList(state.savedRecordings, export) { vm.onEvent(ChairStandEvent.Delete(it)) }
        }
        else -> RunScreen(state, session, vm, export, leave)
    }
}

@Composable
private fun RunScreen(state: ChairStandUiState, session: Session, vm: ChairStandViewModel, export: (String) -> Unit, leave: () -> Unit) {
    KeepScreenOn(state.isRunning)
    AppHiddenWatcher { vm.onEvent(ChairStandEvent.AppHidden) }
    val overlay by vm.overlay.collectAsState()
    CameraPermissionGate(
        denied = { card -> ScreenScaffold(TITLE, leave) { card() } },
    ) {
        ScreenScaffold(
            TITLE, leave,
            bottomBar = { RunControls(state) { vm.onEvent(it) } },
            fullBleed = {
                CameraStage(state, overlay, vm::onSample) { vm.onEvent(ChairStandEvent.Camera(it)) }
            },
        ) {
            RunAlerts(state)
            val result = state.result
            if (state.stage == Stage.Done && result != null) ResultCard(result, state.durationS, state.normsNote)
            RecordingCard(state.recording, export) { vm.onEvent(ChairStandEvent.Delete(it)) }
            Message(state.message) { vm.onEvent(ChairStandEvent.DismissMessage) }
            CounterStatusCard(state.counter)
            SessionLine(session, canEdit = !state.isRunning) { vm.onEvent(ChairStandEvent.EditSession) }
            ActionButton(
                if (state.lens == Lens.Back) "Switch to front camera" else "Switch to back camera",
                { vm.onEvent(ChairStandEvent.SwitchLens) }, Modifier.fillMaxWidth(),
                tone = Tone.Secondary, enabled = !state.isRunning, icon = Icons.Filled.Refresh,
            )
        }
    }
}

@Composable
private fun Unavailable(errors: List<String>) {
    Banner(BannerKind.Danger) {
        BannerText("The chair-stand test is not available")
        Body("The protocol durations in params.json are missing or invalid, so the test cannot be timed.")
        BulletList(errors, mono = true)
    }
}

@Composable
private fun Message(message: String?, onDismiss: () -> Unit) {
    if (message == null) return
    Banner(BannerKind.Plain) {
        Body(message)
        LinkButton("Dismiss", onDismiss)
    }
}

@Composable
private fun SessionLine(session: Session, canEdit: Boolean, onEdit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        val recording = if (session.recording != null) " · recording on" else ""
        Column(Modifier.weight(1f).padding(end = Space.s)) {
            Body("${session.participantCode} · ${session.ageBand.label} · ${session.sex.label}$recording", bold = true)
        }
        ActionButton("Edit session", onEdit, tone = Tone.Secondary, enabled = canEdit)
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
