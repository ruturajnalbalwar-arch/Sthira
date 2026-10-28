package app.sthira.chairstand

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import app.sthira.pose.CameraPane
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
import app.sthira.ui.common.Tone
import app.sthira.ui.common.ScreenScaffold

@Composable
fun ChairStandScreen(onBack: () -> Unit, vm: ChairStandViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    BackHandler(enabled = state.isRunning) { vm.onEvent(ChairStandEvent.Cancel) }
    val export = rememberExporter { name, uri -> vm.export(name, uri) }
    val leave = {
        if (state.isRunning) vm.onEvent(ChairStandEvent.Cancel)
        onBack()
    }
    ScreenScaffold("30-second chair stand", leave) {
        val unavailable = state.unavailable
        if (unavailable != null) {
            Banner(BannerKind.Danger) {
                BannerText("The chair-stand test is not available")
                Body("The protocol durations in params.json are missing or invalid, so the test cannot be timed.")
                BulletList(unavailable, mono = true)
            }
            return@ScreenScaffold
        }
        Muted(
            "CDC STEADI test, counted from head and shoulder height, so loose clothing over the knees is fine. " +
                "Use participant codes only, never names.",
        )
        Banner(BannerKind.Success) { BannerText("Nothing leaves this phone. Pose tracking runs on the device; the app has no internet access.", size = 16) }
        CounterNoticeUi(state.counter)
        state.message?.let { msg ->
            Banner(BannerKind.Plain) {
                Body(msg, bold = true)
                LinkButton("Dismiss", { vm.onEvent(ChairStandEvent.DismissMessage) })
            }
        }
        val session = state.session
        if (session == null) {
            SetupFormUi(state.form, state.formErrors, { vm.onEvent(ChairStandEvent.FormChanged(it)) }, { vm.onEvent(ChairStandEvent.Submit) })
            SavedRecordingsList(state.savedRecordings, export) { vm.onEvent(ChairStandEvent.Delete(it)) }
        } else {
            RunArea(state, session, vm, export)
        }
    }
}

@Composable
private fun RunArea(state: ChairStandUiState, session: Session, vm: ChairStandViewModel, export: (String) -> Unit) {
    KeepScreenOn(state.isRunning)
    AppHiddenWatcher { vm.onEvent(ChairStandEvent.AppHidden) }
    val overlay by vm.overlay.collectAsState()
    SessionLine(session, canEdit = !state.isRunning) { vm.onEvent(ChairStandEvent.EditSession) }
    CameraPermissionGate {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CountBoard(state)
            RunControls(state) { vm.onEvent(it) }
            RunAlerts(state)
            val result = state.result
            if (state.stage == Stage.Done && result != null) {
                ResultCard(result, state.durationS, state.normsNote) { vm.onEvent(ChairStandEvent.RunAgain) }
            }
            RecordingCard(state.recording, export) { vm.onEvent(ChairStandEvent.Delete(it)) }
            CameraPane(
                lens = state.lens,
                overlay = overlay,
                onSample = vm::onSample,
                onStatus = { vm.onEvent(ChairStandEvent.Camera(it)) },
            )
            LiveStatusRow(state)
            ActionButton(
                if (state.lens == Lens.Back) "Switch to front camera" else "Switch to back camera",
                { vm.onEvent(ChairStandEvent.SwitchLens) }, Modifier.fillMaxWidth(),
                tone = Tone.Secondary, enabled = !state.isRunning,
            )
        }
    }
}

@Composable
private fun SessionLine(session: Session, canEdit: Boolean, onEdit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        val recording = if (session.recording != null) " · recording on" else ""
        Body("${session.participantCode} · ${session.ageBand.label} · ${session.sex.label}$recording", Modifier.weight(1f), bold = true)
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
