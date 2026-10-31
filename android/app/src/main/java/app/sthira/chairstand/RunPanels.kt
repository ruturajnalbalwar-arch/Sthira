package app.sthira.chairstand

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.sthira.R
import app.sthira.pose.CameraStatus
import app.sthira.ui.common.ActionButton
import app.sthira.ui.common.Banner
import app.sthira.ui.common.BannerKind
import app.sthira.ui.common.BannerText
import app.sthira.ui.common.Body
import app.sthira.ui.common.BulletList
import app.sthira.ui.common.Tone
import app.sthira.ui.common.asString
import app.sthira.ui.theme.Space
import app.sthira.ui.theme.Sthira
import app.sthira.ui.theme.SthiraType

/**
 * Bottom bar of step 2: ONE big button whose meaning follows the run (Start, then Stop). Stop during
 * calibration goes back to Ready; Stop during the test ends it and shows the result. Redo calibration
 * and Cancel are small outlined buttons below it, shown only when they help.
 */
@Composable
fun RunControls(state: ChairStandUiState, onEvent: (ChairStandEvent) -> Unit) {
    val wide = Modifier.fillMaxWidth()
    if (!state.isRunning) {
        if (state.camera !is CameraStatus.Live) Body(stringResource(R.string.cs_waiting_camera), size = 16)
        ActionButton(
            stringResource(R.string.cs_start), { onEvent(ChairStandEvent.StartTest) }, wide,
            enabled = state.camera is CameraStatus.Live, big = true, icon = Icons.Filled.PlayArrow,
        )
        return
    }
    val testing = state.stage == Stage.Test
    ActionButton(
        stringResource(R.string.cs_stop), { onEvent(if (testing) ChairStandEvent.Stop else ChairStandEvent.Cancel) }, wide,
        tone = Tone.Danger, big = true, icon = Icons.Filled.Close,
    )
    val canRedo = state.stage.isCalibrating ||
        (testing && (state.count is CountView.CalibrationFailed || state.count is CountView.ContextFailed))
    if (!canRedo && !testing) return
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = wide) {
        if (canRedo) {
            ActionButton(
                stringResource(R.string.cs_redo_calibration), { onEvent(ChairStandEvent.RedoCalibration) },
                Modifier.weight(1f), tone = Tone.Secondary, icon = Icons.Filled.Refresh,
            )
        }
        // During calibration Stop already cancels; in the test Cancel drops the run without a result.
        if (testing) {
            ActionButton(stringResource(R.string.cs_cancel_test), { onEvent(ChairStandEvent.Cancel) }, Modifier.weight(1f), tone = Tone.Secondary)
        }
    }
}

/** Bottom bar of step 3: Run again (big, primary) and New person (secondary). */
@Composable
fun ResultControls(onEvent: (ChairStandEvent) -> Unit) {
    val wide = Modifier.fillMaxWidth()
    ActionButton(stringResource(R.string.cs_run_again), { onEvent(ChairStandEvent.RunAgain) }, wide, big = true, icon = Icons.Filled.Refresh)
    ActionButton(
        stringResource(R.string.cs_new_person), { onEvent(ChairStandEvent.NewPerson) }, wide,
        tone = Tone.Secondary, icon = Icons.Filled.Person,
    )
}

/** The answers from step 1 and a small Edit details button, shown on step 2 while no run is active. */
@Composable
fun DetailsRow(session: Session, canEdit: Boolean, onEdit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        val line = stringResource(R.string.cs_details_line, stringResource(session.ageBand.label), stringResource(session.sex.labelRes))
        Column(Modifier.weight(1f).padding(end = Space.s)) { Body(line, bold = true) }
        ActionButton(stringResource(R.string.cs_edit_session), onEdit, tone = Tone.Secondary, enabled = canEdit, icon = Icons.Filled.Edit)
    }
}

/** Problems that outlive the camera overlays: a stopped camera and the capture issues of a finished run. */
@Composable
fun RunAlerts(state: ChairStandUiState) {
    val camera = state.camera
    AnimatedVisibility(camera is CameraStatus.Failed) {
        Banner(BannerKind.Danger) { BannerText((camera as? CameraStatus.Failed)?.message?.asString().orEmpty()) }
    }
    AnimatedVisibility(state.issues.isNotEmpty() && state.stage == Stage.Done) {
        Banner(BannerKind.Warn) {
            BannerText(stringResource(R.string.cs_issues_title))
            BulletList(state.issues.map { stringResource(it.text) })
        }
    }
}

@Composable
fun PrivacyRow() {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = Sthira.colors.success, modifier = Modifier.size(20.dp))
        Text(stringResource(R.string.cs_privacy_row), style = SthiraType.body, color = Sthira.colors.text)
    }
}
