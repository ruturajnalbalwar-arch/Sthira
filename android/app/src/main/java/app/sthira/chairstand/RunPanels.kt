package app.sthira.chairstand

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sthira.pose.CameraStatus
import app.sthira.ui.common.ActionButton
import app.sthira.ui.common.Banner
import app.sthira.ui.common.BannerKind
import app.sthira.ui.common.BannerText
import app.sthira.ui.common.BulletList
import app.sthira.ui.common.CardShape
import app.sthira.ui.common.LinkButton
import app.sthira.ui.common.Tone
import app.sthira.ui.theme.Sthira

/** One primary action per step. Redo is offered before Go, and after Go only when the count cannot run. */
@Composable
fun RunControls(state: ChairStandUiState, onEvent: (ChairStandEvent) -> Unit) {
    val wide = Modifier.fillMaxWidth()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            state.stage == Stage.Done -> Unit
            !state.isRunning -> ActionButton(
                "Start calibration and test", { onEvent(ChairStandEvent.StartTest) }, wide,
                enabled = state.camera is CameraStatus.Live, big = true,
            )
            else -> {
                if (state.stage == Stage.Test) ActionButton("Stop", { onEvent(ChairStandEvent.Stop) }, wide, tone = Tone.Danger, big = true)
                val canRedo = state.stage != Stage.Test ||
                    state.count is CountView.CalibrationFailed || state.count is CountView.ContextFailed
                if (canRedo && state.stage.isActive) {
                    ActionButton("Redo calibration", { onEvent(ChairStandEvent.RedoCalibration) }, wide, tone = Tone.Secondary)
                }
                ActionButton("Cancel test", { onEvent(ChairStandEvent.Cancel) }, wide, tone = Tone.Secondary)
            }
        }
    }
}

/** Live problems during a run, in words the physiotherapist can act on at once. */
@Composable
fun RunAlerts(state: ChairStandUiState) {
    val summary = (state.count as? CountView.Counting)?.summary
    if (state.personLost) {
        Banner(BannerKind.Danger) { BannerText("Cannot see the person. Move the camera or chair so the whole body, head to feet, is in view.") }
    }
    if (summary?.unreliable == true) Banner(BannerKind.Danger) { BannerText("Count not reliable: too many stands were unclear on camera.") }
    if (summary?.lowFps == true) {
        Banner(BannerKind.Warn) {
            BannerText("The camera is running slowly (${"%.1f".format(summary.effectiveFps)} fps), so stands may be missed. Close other apps.")
        }
    }
    val camera = state.camera
    if (camera is CameraStatus.Failed) Banner(BannerKind.Danger) { BannerText(camera.message) }
    if (state.issues.isNotEmpty() && state.stage == Stage.Done) {
        Banner(BannerKind.Warn) { BulletList(state.issues.map { it.text }) }
    }
}

/** Says plainly when the counter cannot count. Shown before and during a run. */
@Composable
fun CounterNoticeUi(notice: CounterNotice) {
    when (notice) {
        CounterNotice.Ready -> Unit
        is CounterNotice.Invalid -> Banner(BannerKind.Danger) {
            BannerText("Counter settings are invalid")
            Text("params.json did not pass validation, so no stands are counted.", fontWeight = FontWeight.SemiBold)
            BulletList(notice.errors, mono = true)
        }
        is CounterNotice.Untuned -> Banner(BannerKind.Warn) {
            var open by remember { mutableStateOf(false) }
            BannerText("Counter not tuned yet")
            Text(
                "The camera, pose overlay and guided calibration run so the setup can be rehearsed, but no stands are " +
                    "counted and no score is shown until the detector settings are fitted from pilot recordings.",
                fontWeight = FontWeight.SemiBold,
            )
            LinkButton(if (open) "Hide missing settings" else "Missing from params.json (${notice.missing.size})", { open = !open })
            if (open) BulletList(notice.missing, mono = true)
        }
    }
}

@Composable
fun LiveStatusRow(state: ChairStandUiState) {
    val camera = state.camera
    val live = camera is CameraStatus.Live
    val items = listOf(
        "Frame rate" to if (live) state.stats.fps?.let { "%.1f fps".format(it) } ?: "-" else "-",
        "Person" to if (!live) "-" else if (state.stats.personDetected) "Yes" else "No",
        "Pose engine" to ((camera as? CameraStatus.Live)?.delegate?.label ?: "-"),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items.forEach { (term, value) ->
            Surface(
                color = Sthira.colors.surface, shape = CardShape, modifier = Modifier.weight(1f)
                    .semantics(mergeDescendants = true) { contentDescription = "$term: $value" },
            ) {
                Column(Modifier.padding(8.dp)) {
                    Text(term, fontSize = 12.sp, color = Sthira.colors.muted, fontWeight = FontWeight.SemiBold)
                    Text(value, fontSize = 18.sp, color = Sthira.colors.text, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
