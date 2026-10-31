package app.sthira.timers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.sthira.R
import app.sthira.engine.timers.BalanceStage
import app.sthira.engine.timers.CdcSource
import app.sthira.ui.common.Banner
import app.sthira.ui.common.BannerKind
import app.sthira.ui.common.BannerText
import app.sthira.ui.common.BulletList
import app.sthira.ui.common.Neu
import app.sthira.ui.common.NeuDepth
import app.sthira.ui.common.NeuSurface
import app.sthira.ui.common.Muted
import app.sthira.ui.common.UiText
import app.sthira.ui.common.asString
import app.sthira.ui.theme.DisplaySize
import app.sthira.ui.theme.Space
import app.sthira.ui.theme.Sthira
import app.sthira.ui.theme.SthiraType

private val STEP_DOT_SIZE = 36.dp
private val STEP_ICON_SIZE = 20.dp

/** The running time, readable across the room. TalkBack hears the label, not every tenth of a second. */
@Composable
fun BigTime(seconds: Double, label: String) {
    val size = with(LocalDensity.current) { DisplaySize.timer.toSp() }
    NeuSurface(Modifier.fillMaxWidth(), shape = Neu.CardShape, depth = NeuDepth.Inset, elevation = 6.dp) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = Space.l, horizontal = Space.m).clearAndSetSemantics { contentDescription = label },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = SthiraType.label, color = Sthira.colors.muted)
            Text(stringResource(R.string.timer_seconds_value, seconds), style = SthiraType.numeric.copy(fontSize = size, lineHeight = size))
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

/** 1-4 stage stepper. Each dot says its state with an icon or number and to TalkBack in words. */
@Composable
fun StageStepper(stages: List<BalanceStage>, statuses: List<StepStatus>) {
    val c = Sthira.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        stages.forEachIndexed { i, stage ->
            val status = statuses.getOrElse(i) { StepStatus.Upcoming }
            val name = TimerTexts.stageName(stage).asString()
            val spoken = stringResource(R.string.timer_stepper_cd, stage.stage, stages.size, name, stringResource(status.spoken))
            Column(
                Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = spoken },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Connector(visible = i > 0, Modifier.weight(1f))
                    StepDot(stage.stage, status)
                    Connector(visible = i < stages.lastIndex, Modifier.weight(1f))
                }
                // A zero-width space after each hyphen lets "semi-tandem" wrap as "semi- / tandem", never mid-word.
                Text(
                    name.replace("-", "-\u200B"), style = SthiraType.label, textAlign = TextAlign.Center,
                    color = if (status == StepStatus.Current) c.text else c.muted,
                )
            }
        }
    }
}

@Composable
private fun Connector(visible: Boolean, modifier: Modifier) {
    Box(modifier.height(2.dp).background(if (visible) Sthira.colors.border else Color.Transparent))
}

@Composable
private fun StepDot(number: Int, status: StepStatus) {
    val c = Sthira.colors
    val (fill, fg, ring) = when (status) {
        StepStatus.Held -> Triple(c.success, c.bg, c.success)
        StepStatus.Lost -> Triple(c.danger, c.onDanger, c.danger)
        StepStatus.Current -> Triple(c.primary, c.onPrimary, c.primary)
        StepStatus.Upcoming, StepStatus.Skipped -> Triple(c.surface, c.muted, c.muted)
    }
    // A minimum, not a fixed size, so the number never clips at a large text zoom.
    Box(
        Modifier.sizeIn(minWidth = STEP_DOT_SIZE, minHeight = STEP_DOT_SIZE).background(fill, CircleShape).border(1.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            StepStatus.Held -> Icon(Icons.Filled.Check, contentDescription = null, tint = fg, modifier = Modifier.size(STEP_ICON_SIZE))
            StepStatus.Lost -> Icon(Icons.Filled.Close, contentDescription = null, tint = fg, modifier = Modifier.size(STEP_ICON_SIZE))
            else -> Text("$number", Modifier.padding(horizontal = Space.xs), style = SthiraType.titleSmall, color = fg)
        }
    }
}
