package app.sthira.chairstand

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import app.sthira.R
import app.sthira.pose.CameraPermissionGate
import app.sthira.ui.common.ActionButton
import app.sthira.ui.common.Banner
import app.sthira.ui.common.BannerKind
import app.sthira.ui.common.BannerText
import app.sthira.ui.common.Body
import app.sthira.ui.common.BulletList
import app.sthira.ui.common.Muted
import app.sthira.ui.common.ScreenScaffold
import app.sthira.ui.common.Tone
import app.sthira.ui.common.UiText
import app.sthira.ui.common.asString
import app.sthira.ui.theme.Space

@Composable
fun ChairStandScreen(onBack: () -> Unit, vm: ChairStandViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    BackHandler(enabled = state.isRunning) { vm.onEvent(ChairStandEvent.Cancel) }
    val leave = {
        if (state.isRunning) vm.onEvent(ChairStandEvent.Cancel)
        onBack()
    }
    val title = stringResource(R.string.cs_title)
    val unavailable = state.unavailable
    val session = state.session
    when {
        unavailable != null -> ScreenScaffold(title, leave) { Unavailable(unavailable) }
        session == null -> ScreenScaffold(
            title, leave,
            bottomBar = { ContinueBar(state.form) { vm.onEvent(ChairStandEvent.Submit) } },
        ) {
            Muted(stringResource(R.string.cs_setup_intro))
            PrivacyRow()
            SetupFormUi(state.form) { vm.onEvent(ChairStandEvent.FormChanged(it)) }
        }
        else -> RunScreen(title, state, session, vm, leave)
    }
}

/** The overlay stream is handed down, not collected here, so camera frames do not recompose this screen. */
@Composable
private fun RunScreen(title: String, state: ChairStandUiState, session: Session, vm: ChairStandViewModel, leave: () -> Unit) {
    KeepScreenOn(state.isRunning)
    AppHiddenWatcher { vm.onEvent(ChairStandEvent.AppHidden) }
    CameraPermissionGate(
        denied = { card -> ScreenScaffold(title, leave) { card() } },
    ) {
        ScreenScaffold(
            title, leave,
            bottomBar = { RunControls(state) { vm.onEvent(it) } },
            fullBleed = {
                CameraStage(
                    state, vm.tracker, vm.overlay,
                    onSwitchLens = { vm.onEvent(ChairStandEvent.SwitchLens) },
                    onStatus = { vm.onEvent(ChairStandEvent.Camera(it)) },
                )
            },
        ) {
            RunAlerts(state)
            val result = state.result
            if (state.stage == Stage.Done && result != null) ResultCard(result, state.durationS, state.normsNote)
            SessionLine(session, canEdit = !state.isRunning) { vm.onEvent(ChairStandEvent.EditSession) }
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

@Composable
private fun SessionLine(session: Session, canEdit: Boolean, onEdit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        val line = stringResource(
            R.string.cs_session_line, session.participantCode, stringResource(session.ageBand.label), stringResource(session.sex.labelRes),
        )
        Column(Modifier.weight(1f).padding(end = Space.s)) { Body(line, bold = true) }
        ActionButton(stringResource(R.string.cs_edit_session), onEdit, tone = Tone.Secondary, enabled = canEdit)
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
