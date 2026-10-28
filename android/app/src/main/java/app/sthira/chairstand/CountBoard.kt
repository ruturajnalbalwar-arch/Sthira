package app.sthira.chairstand

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sthira.engine.RepEvent
import app.sthira.engine.RepKind
import app.sthira.ui.common.BulletList
import app.sthira.ui.common.CardShape
import app.sthira.ui.common.MinTarget
import app.sthira.ui.theme.Sthira

/** What the physio reads out (and the participant reads from about 3 m) at each step. */
private fun title(stage: Stage, startRequested: Boolean): String = when (stage) {
    Stage.Idle -> if (startRequested) "Starting..." else "Ready"
    Stage.CalibrationStand -> "Stand up tall and keep still"
    Stage.CalibrationSit -> "Sit down and keep still"
    Stage.Test -> "Go! Stand up and sit down as many times as you can"
    Stage.Done -> "Stop. Test finished"
}

private const val IDLE_HINT =
    "Check that the whole body, head to feet, and the chair are in view, standing and sitting. Then start."

/** Giant digits are sized in dp: they already fill the width, so the font scale must not push them off screen. */
@Composable
private fun giant(size: Dp): TextUnit = with(LocalDensity.current) { size.toSp() }

@Composable
fun CountBoard(state: ChairStandUiState) {
    val c = Sthira.colors
    val (bg, fg) = when (state.stage) {
        Stage.CalibrationStand, Stage.CalibrationSit -> c.warnBg to c.warn
        Stage.Test -> c.primary to c.onPrimary
        Stage.Done -> c.successBg to c.success
        Stage.Idle -> c.surface to c.text
    }
    val heading = title(state.stage, state.startRequested)
    Surface(color = bg, contentColor = fg, shape = CardShape, border = BorderStroke(2.dp, fg), modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp).heightIn(min = 160.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Text(
                heading, textAlign = TextAlign.Center, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
            )
            BoardDetail(state, fg)
        }
    }
}

@Composable
private fun BoardDetail(state: ChairStandUiState, fg: Color) {
    when (state.stage) {
        Stage.Idle -> if (!state.startRequested) Text(IDLE_HINT, textAlign = TextAlign.Center, fontSize = 17.sp)
        Stage.CalibrationStand, Stage.CalibrationSit -> state.countdownS?.let {
            Text(
                "$it", fontSize = giant(120.dp), fontWeight = FontWeight.Black, lineHeight = giant(124.dp),
                modifier = Modifier.clearAndSetSemantics { contentDescription = "$it seconds" },
            )
        }
        Stage.Test -> TestDetail(state, fg)
        Stage.Done -> Unit
    }
}

@Composable
private fun TestDetail(state: ChairStandUiState, fg: Color) {
    when (val count = state.count) {
        is CountView.Counting -> LiveCount(count, state.remainingS)
        is CountView.CalibrationFailed -> Problem("Calibration did not pass. Not counting.", count.reasons)
        is CountView.ContextFailed -> Problem("The counter could not start.", count.errors)
        CountView.NotTuned -> {
            Elapsed(state)
            Text("Counting is off: the counter is not tuned yet.", fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        else -> Text("Checking calibration...", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
private fun Elapsed(state: ChairStandUiState) {
    val elapsed = state.elapsedS ?: return
    Text(
        "$elapsed / ${state.durationS.toInt()} s", fontSize = giant(56.dp), fontWeight = FontWeight.Black,
        modifier = Modifier.clearAndSetSemantics { },
    )
}

@Composable
private fun LiveCount(count: CountView.Counting, remainingS: Int?) {
    val full = count.summary.full
    val word = if (full == 1) "stand" else "stands"
    Row(
        verticalAlignment = Alignment.Bottom,
        // One polite announcement per new full stand; the changing seconds are not read out.
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "$full full $word"
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        Text("$full", fontSize = giant(150.dp), lineHeight = giant(150.dp), fontWeight = FontWeight.Black)
        Text(" $word", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))
    }
    remainingS?.let {
        Text("$it s left", fontSize = giant(40.dp), fontWeight = FontWeight.ExtraBold, modifier = Modifier.clearAndSetSemantics { })
    }
    Surface(color = Sthira.colors.surface, contentColor = Sthira.colors.text, shape = CardShape) {
        RepChips(count.summary.events, Modifier.padding(8.dp))
    }
}

@Composable
private fun Problem(title: String, items: List<String>) {
    Surface(color = Sthira.colors.surface, contentColor = Sthira.colors.danger, shape = CardShape, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp).semantics { liveRegion = LiveRegionMode.Assertive }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            BulletList(items, color = Sthira.colors.danger)
            Text("Press Redo calibration, or Cancel.", color = Sthira.colors.text, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Text, not colour alone, says whether each attempt counted. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepChips(events: List<RepEvent>, modifier: Modifier = Modifier) {
    if (events.isEmpty()) return
    val c = Sthira.colors
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        events.forEachIndexed { i, e ->
            val (label, fg, bg) = when (e.kind) {
                RepKind.FULL -> Triple("Full stand", c.success, c.successBg)
                RepKind.HALF -> Triple("Half: not counted", c.warn, c.warnBg)
                RepKind.ARM_ASSISTED -> Triple("Arms used: not counted", c.warn, c.warnBg)
                RepKind.UNCERTAIN -> Triple("Unclear: not counted", c.muted, c.surface)
            }
            Surface(color = bg, contentColor = fg, shape = CardShape, border = BorderStroke(2.dp, fg)) {
                Row(Modifier.heightIn(min = MinTarget).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}  $label", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}
