package app.sthira.chairstand

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.sthira.pose.CameraStatus
import app.sthira.ui.common.ActionButton
import app.sthira.ui.common.Banner
import app.sthira.ui.common.BannerKind
import app.sthira.ui.common.BannerText
import app.sthira.ui.common.Body
import app.sthira.ui.common.BulletList
import app.sthira.ui.common.LinkButton
import app.sthira.ui.common.Tone
import app.sthira.ui.theme.Space
import app.sthira.ui.theme.Sthira
import app.sthira.ui.theme.SthiraType

/** Bottom-bar actions for the run. One primary action per step; Redo appears only when it helps. */
@Composable
fun RunControls(state: ChairStandUiState, onEvent: (ChairStandEvent) -> Unit) {
    val wide = Modifier.fillMaxWidth()
    when {
        state.stage == Stage.Done -> ActionButton(
            "Run again", { onEvent(ChairStandEvent.RunAgain) }, wide, big = true, icon = Icons.Filled.Refresh,
        )
        !state.isRunning -> {
            if (state.camera !is CameraStatus.Live) Body("Waiting for the camera...", size = 16)
            ActionButton(
                "Start calibration and test", { onEvent(ChairStandEvent.StartTest) }, wide,
                enabled = state.camera is CameraStatus.Live, big = true, icon = Icons.Filled.PlayArrow,
            )
        }
        else -> {
            if (state.stage == Stage.Test) ActionButton("Stop", { onEvent(ChairStandEvent.Stop) }, wide, tone = Tone.Danger, big = true)
            val canRedo = state.stage != Stage.Test ||
                state.count is CountView.CalibrationFailed || state.count is CountView.ContextFailed
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                if (canRedo && state.stage.isActive) {
                    ActionButton("Redo calibration", { onEvent(ChairStandEvent.RedoCalibration) }, Modifier.weight(1f), tone = Tone.Secondary)
                }
                ActionButton("Cancel test", { onEvent(ChairStandEvent.Cancel) }, Modifier.weight(1f), tone = Tone.Secondary)
            }
        }
    }
}

/** Problems that outlive the camera overlays: a stopped camera and the capture issues of a finished run. */
@Composable
fun RunAlerts(state: ChairStandUiState) {
    val camera = state.camera
    AnimatedVisibility(camera is CameraStatus.Failed) {
        Banner(BannerKind.Danger) { BannerText((camera as? CameraStatus.Failed)?.message.orEmpty()) }
    }
    AnimatedVisibility(state.issues.isNotEmpty() && state.stage == Stage.Done) {
        Banner(BannerKind.Warn) {
            BannerText("This run had capture problems")
            BulletList(state.issues.map { it.text })
        }
    }
}

/** Compact counter status: one line and one sentence, details behind "Why?". Nothing when ready. */
@Composable
fun CounterStatusCard(notice: CounterNotice) {
    if (notice == CounterNotice.Ready) return
    var open by rememberSaveable { mutableStateOf(false) }
    val invalid = notice is CounterNotice.Invalid
    Banner(if (invalid) BannerKind.Danger else BannerKind.Warn) {
        BannerText(if (invalid) "Counter settings are invalid" else "Counter not tuned yet")
        Body(
            if (invalid) "No stands are counted until params.json is fixed."
            else "Camera and calibration can be rehearsed, but no stands are counted yet.",
        )
        LinkButton(if (open) "Hide details" else "Why?", { open = !open })
        AnimatedVisibility(open) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                when (notice) {
                    is CounterNotice.Untuned -> {
                        Body(
                            "The detector settings are fitted from pilot recordings and none are invented. Until they are " +
                                "fitted, no count or score is shown. Missing from params.json (${notice.missing.size}):",
                        )
                        BulletList(notice.missing, mono = true)
                    }
                    is CounterNotice.Invalid -> {
                        Body("params.json did not pass validation:")
                        BulletList(notice.errors, mono = true)
                    }
                    CounterNotice.Ready -> Unit
                }
            }
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
        Text("Nothing leaves this phone: pose tracking runs on the device.", style = SthiraType.body, color = Sthira.colors.text)
    }
}
