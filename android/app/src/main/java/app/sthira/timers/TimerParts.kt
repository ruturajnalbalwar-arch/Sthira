package app.sthira.timers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.sthira.R
import app.sthira.engine.timers.BalanceStage
import app.sthira.engine.timers.CdcSource
import app.sthira.engine.timers.StageResult
import app.sthira.ui.common.Banner
import app.sthira.ui.common.BannerKind
import app.sthira.ui.common.BannerText
import app.sthira.ui.common.BulletList
import app.sthira.ui.common.Muted
import app.sthira.ui.common.UiText
import app.sthira.ui.common.asString
import app.sthira.ui.theme.DisplaySize
import app.sthira.ui.theme.Space
import app.sthira.ui.theme.Sthira
import app.sthira.ui.theme.SthiraType

private val STEP_DOT_SIZE = 40.dp
private val STEP_ICON_SIZE = 22.dp
private val FOLD_MIN_HEIGHT = 56.dp
private val PANEL_SHAPE = RoundedCornerShape(16.dp)
private val PANEL_BORDER = 2.dp

/** A flat panel with a strong border, matching the flat high-contrast style. */
@Composable
internal fun FlatPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = Sthira.colors
    Box(modifier.fillMaxWidth().background(c.surface, PANEL_SHAPE).border(PANEL_BORDER, c.borderStrong, PANEL_SHAPE)) { content() }
}

/** The running time, readable across the room. TalkBack hears the label, not every tenth of a second. */
@Composable
fun BigTime(seconds: Double, label: String) {
    val size = with(LocalDensity.current) { DisplaySize.timer.toSp() }
    FlatPanel {
        Column(
            Modifier.fillMaxWidth().padding(vertical = Space.l, horizontal = Space.m).clearAndSetSemantics { contentDescription = label },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = SthiraType.titleSmall, color = Sthira.colors.text)
            Text(
                stringResource(R.string.timer_seconds_value, seconds),
                style = SthiraType.numeric.copy(fontSize = size, lineHeight = size), color = Sthira.colors.text,
            )
        }
    }
}

@Composable
fun ResultBanner(meets: Boolean, title: String, content: @Composable () -> Unit) {
    Banner(if (meets) BannerKind.Success else BannerKind.Warn) {
        BannerText(title)
        content()
    }
}

/** The passed or stopped line after a stage. */
@Composable
fun StageOutcome(result: StageResult) {
    Banner(if (result.completed) BannerKind.Success else BannerKind.Danger) {
        BannerText(TimerTexts.stageOutcome(result).asString(), size = 22)
    }
}

/** The CDC source line as plain text: the app is offline, so it names the reference instead of linking. */
@Composable
fun CdcNote(source: CdcSource) {
    // The CDC sheet title is a document name, so it stays in English.
    Muted(stringResource(R.string.timer_cdc_reference, source.title, source.url))
    Muted(stringResource(R.string.timer_cdc_caveat))
}

@Composable
fun TimerError(errors: List<UiText>) {
    Banner(BannerKind.Danger) {
        BannerText(stringResource(R.string.timer_norms_invalid))
        BulletList(errors.map { it.asString() }, mono = true)
    }
}

/** "Stage 2 of 4" above a row of dots. Each dot says its state with an icon or number and to TalkBack in words. */
@Composable
fun StageStepper(stages: List<BalanceStage>, statuses: List<StepStatus>, shownIndex: Int? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        shownIndex?.let { stages.getOrNull(it) }?.let { stage ->
            Text(TimerTexts.stageOf(stage, stages.size).asString(), style = SthiraType.title, color = Sthira.colors.text)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            stages.forEachIndexed { i, stage ->
                val status = statuses.getOrElse(i) { StepStatus.Upcoming }
                val name = TimerTexts.stageName(stage).asString()
                val spoken = stringResource(R.string.timer_stepper_cd, stage.stage, stages.size, name, stringResource(status.spoken))
                Box(Modifier.semantics { contentDescription = spoken }) { StepDot(stage.stage, status) }
            }
        }
    }
}

@Composable
private fun StepDot(number: Int, status: StepStatus) {
    val c = Sthira.colors
    val (fill, fg, ring) = when (status) {
        StepStatus.Held -> Triple(c.success, c.bg, c.success)
        StepStatus.Lost -> Triple(c.danger, c.onDanger, c.danger)
        StepStatus.Current -> Triple(c.primary, c.onPrimary, c.primary)
        StepStatus.Upcoming, StepStatus.Skipped -> Triple(c.surface, c.text, c.borderStrong)
    }
    // A minimum, not a fixed size, so the number never clips at a large text zoom.
    Box(
        Modifier.sizeIn(minWidth = STEP_DOT_SIZE, minHeight = STEP_DOT_SIZE).background(fill, CircleShape).border(PANEL_BORDER, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            StepStatus.Held -> Icon(Icons.Filled.Check, contentDescription = null, tint = fg, modifier = Modifier.size(STEP_ICON_SIZE))
            StepStatus.Lost -> Icon(Icons.Filled.Close, contentDescription = null, tint = fg, modifier = Modifier.size(STEP_ICON_SIZE))
            else -> Text("$number", Modifier.padding(horizontal = Space.xs), style = SthiraType.titleSmall, color = fg)
        }
    }
}

/** The "How to run it" section, folded by default so the screen shows only what is needed now. */
@Composable
fun HowToRun(content: @Composable () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val c = Sthira.colors
    val state = stringResource(if (open) R.string.timer_fold_open else R.string.timer_fold_closed)
    FlatPanel {
        Column {
            Row(
                Modifier.fillMaxWidth().heightIn(min = FOLD_MIN_HEIGHT)
                    .clickable(role = Role.Button) { open = !open }
                    .semantics { stateDescription = state }
                    .padding(horizontal = Space.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = c.text)
                Text(stringResource(R.string.timer_how_heading), Modifier.weight(1f), style = SthiraType.titleSmall, color = c.text)
                Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = c.text)
            }
            AnimatedVisibility(open) {
                Column(
                    Modifier.fillMaxWidth().padding(start = Space.m, end = Space.m, bottom = Space.m),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) { content() }
            }
        }
    }
}
