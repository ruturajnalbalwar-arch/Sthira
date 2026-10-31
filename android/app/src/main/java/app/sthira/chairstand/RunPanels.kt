package app.sthira.chairstand

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
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

/** Bottom-bar actions for the run. One primary action per step; Redo appears only when it helps. */
@Composable
fun RunControls(state: ChairStandUiState, onEvent: (ChairStandEvent) -> Unit) {
    val wide = Modifier.fillMaxWidth()
    when {
        state.stage == Stage.Done -> ActionButton(
            stringResource(R.string.cs_run_again), { onEvent(ChairStandEvent.RunAgain) }, wide, big = true, icon = Icons.Filled.Refresh,
        )
        !state.isRunning -> {
            if (state.camera !is CameraStatus.Live) Body(stringResource(R.string.cs_waiting_camera), size = 16)
            ActionButton(
                stringResource(R.string.cs_start), { onEvent(ChairStandEvent.StartTest) }, wide,
                enabled = state.camera is CameraStatus.Live, big = true, icon = Icons.Filled.PlayArrow,
            )
        }
        else -> {
            if (state.stage == Stage.Test) {
                ActionButton(stringResource(R.string.cs_stop), { onEvent(ChairStandEvent.Stop) }, wide, tone = Tone.Danger, big = true)
            }
            val canRedo = state.stage != Stage.Test ||
                state.count is CountView.CalibrationFailed || state.count is CountView.ContextFailed
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                if (canRedo && state.stage.isActive) {
                    ActionButton(
                        stringResource(R.string.cs_redo_calibration), { onEvent(ChairStandEvent.RedoCalibration) },
                        Modifier.weight(1f), tone = Tone.Secondary,
                    )
                }
                ActionButton(stringResource(R.string.cs_cancel_test), { onEvent(ChairStandEvent.Cancel) }, Modifier.weight(1f), tone = Tone.Secondary)
            }
        }
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
