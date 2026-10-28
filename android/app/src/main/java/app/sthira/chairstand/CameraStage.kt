package app.sthira.chairstand

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import app.sthira.engine.RepEvent
import app.sthira.engine.RepKind
import app.sthira.pose.CameraPane
import app.sthira.pose.CameraStatus
import app.sthira.pose.PoseSample
import app.sthira.ui.common.BulletList
import app.sthira.ui.common.Glass
import app.sthira.ui.common.GlassSurface
import app.sthira.ui.theme.DisplaySize
import app.sthira.ui.theme.Space
import app.sthira.ui.theme.Sthira
import app.sthira.ui.theme.SthiraType
import java.util.Locale

/** The instruction for each step, read out by the physio and readable from about 3 m. */
fun stageTitle(stage: Stage, startRequested: Boolean): String = when (stage) {
    Stage.Idle -> if (startRequested) "Starting..." else "Ready"
    Stage.CalibrationStand -> "Stand up tall and keep still"
    Stage.CalibrationSit -> "Sit down and keep still"
    Stage.Test -> "Go! Stand up and sit down as many times as you can"
    Stage.Done -> "Stop. Test finished"
}

private const val IDLE_HINT = "Whole body, head to feet, and the chair in view, standing and sitting"

/** Giant digits are sized in dp: they already fill the panel, so font scaling must not push them off screen. */
@Composable
private fun display(size: Dp): TextUnit = with(LocalDensity.current) { size.toSp() }

/** Full-bleed camera with the pose skeleton and glass overlays for the step, the count and problems. */
@Composable
fun CameraStage(state: ChairStandUiState, overlay: PoseSample?, onSample: (PoseSample) -> Unit, onStatus: (CameraStatus) -> Unit) {
    CameraPane(lens = state.lens, overlay = overlay, onSample = onSample, onStatus = onStatus) { CameraOverlays(state) }
}

/** Everything drawn over the camera; stateless so it can be previewed without a camera. */
@Composable
fun BoxScope.CameraOverlays(state: ChairStandUiState) {
    StatusPill(state, Modifier.align(Alignment.TopStart).padding(12.dp))
    WarningPills(state, Modifier.align(Alignment.TopCenter).padding(top = 60.dp, start = 12.dp, end = 12.dp))
    StagePanel(state)
}

@Composable
private fun StatusPill(state: ChairStandUiState, modifier: Modifier) {
    val camera = state.camera
    val text = when (camera) {
        is CameraStatus.Live -> listOfNotNull(
            state.stats.fps?.let { String.format(Locale.US, "%.0f fps", it) }, camera.delegate.label,
            if (state.stats.personDetected) "person in view" else "no person",
        ).joinToString(" · ")
        CameraStatus.Starting -> "Starting camera..."
        is CameraStatus.Failed -> "Camera stopped"
    }
    GlassSurface(modifier, shape = Glass.ChipShape, elevation = 0.dp) {
        Text(text, style = SthiraType.label, color = Sthira.colors.text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

@Composable
private fun WarningPills(state: ChairStandUiState, modifier: Modifier) {
    val summary = (state.count as? CountView.Counting)?.summary
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.s), horizontalAlignment = Alignment.CenterHorizontally) {
        Pill(state.personLost, "Cannot see the person: whole body, head to feet, in view", Sthira.colors.danger, assertive = true)
        Pill(summary?.unreliable == true, "Count not reliable: too many unclear stands", Sthira.colors.danger, assertive = true)
        val fps = summary?.effectiveFps ?: 0.0
        Pill(summary?.lowFps == true, String.format(Locale.US, "Camera slow (%.1f fps): stands may be missed", fps), Sthira.colors.warn)
    }
}

@Composable
private fun Pill(visible: Boolean, text: String, tone: Color, assertive: Boolean = false) {
    AnimatedVisibility(visible, enter = fadeIn(Sthira.motion()), exit = fadeOut(Sthira.motion())) {
        GlassSurface(shape = Glass.CardShape, elevation = 0.dp) {
            Text(
                text, style = SthiraType.titleSmall, color = tone, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).semantics {
                    liveRegion = if (assertive) LiveRegionMode.Assertive else LiveRegionMode.Polite
                },
            )
        }
    }
}

@Composable
private fun BoxScope.StagePanel(state: ChairStandUiState) {
    val calibrating = state.stage.isCalibrating
    val align = if (calibrating || state.count is CountView.CalibrationFailed || state.count is CountView.ContextFailed) {
        Alignment.Center
    } else {
        Alignment.BottomCenter
    }
    val spec = Sthira.motion<Float>()
    AnimatedContent(
        targetState = state.stage,
        transitionSpec = { fadeIn(spec) togetherWith fadeOut(spec) },
        modifier = Modifier.align(align).padding(12.dp),
        label = "stagePanel",
    ) { stage ->
        when (stage) {
            Stage.Idle -> Panel { PanelText(if (state.startRequested) "Starting..." else IDLE_HINT, SthiraType.titleSmall) }
            Stage.CalibrationStand, Stage.CalibrationSit -> CountdownPanel(stage, state.countdownS)
            Stage.Test -> TestPanel(state)
            Stage.Done -> Panel { PanelText(stageTitle(Stage.Done, false), SthiraType.title) }
        }
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    GlassSurface(Modifier.fillMaxWidth(), elevation = 0.dp) {
        Column(
            Modifier.fillMaxWidth().padding(Space.m),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) { content() }
    }
}

@Composable
private fun PanelText(text: String, style: androidx.compose.ui.text.TextStyle, color: Color = Sthira.colors.text) {
    Text(text, style = style, color = color, textAlign = TextAlign.Center)
}

@Composable
private fun CountdownPanel(stage: Stage, seconds: Int?) {
    Panel {
        Text(
            stageTitle(stage, false), style = SthiraType.headline, color = Sthira.colors.text, textAlign = TextAlign.Center,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        if (seconds != null) {
            Text(
                "$seconds", style = SthiraType.numeric.copy(fontSize = display(DisplaySize.countdown), lineHeight = display(DisplaySize.countdown)),
                color = Sthira.colors.primary,
                modifier = Modifier.clearAndSetSemantics { contentDescription = "$seconds seconds" },
            )
        }
    }
}

@Composable
private fun TestPanel(state: ChairStandUiState) {
    when (val count = state.count) {
        is CountView.Counting -> LivePanel(count, state.remainingS)
        is CountView.CalibrationFailed -> Problem("Calibration did not pass. Not counting.", count.reasons)
        is CountView.ContextFailed -> Problem("The counter could not start.", count.errors)
        CountView.NotTuned -> Panel {
            state.elapsedS?.let {
                Text(
                    "$it / ${state.durationS.toInt()} s", style = SthiraType.numeric.copy(fontSize = display(DisplaySize.timer)),
                    color = Sthira.colors.text, modifier = Modifier.clearAndSetSemantics { },
                )
            }
            PanelText("Counting is off: the counter is not tuned yet.", SthiraType.titleSmall)
        }
        else -> Panel { PanelText("Checking calibration...", SthiraType.title) }
    }
}

@Composable
private fun LivePanel(count: CountView.Counting, remainingS: Int?) {
    val full = count.summary.full
    val word = if (full == 1) "stand" else "stands"
    Panel {
        Row(
            verticalAlignment = Alignment.Bottom,
            // One polite announcement per new full stand; the ticking seconds are not read out.
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = "$full full $word"
                liveRegion = LiveRegionMode.Polite
            },
        ) {
            Text(
                "$full", style = SthiraType.numeric.copy(fontSize = display(DisplaySize.liveCount), lineHeight = display(DisplaySize.liveCount)),
                color = Sthira.colors.text,
            )
            Text(" $word", style = SthiraType.title, color = Sthira.colors.text, modifier = Modifier.padding(bottom = 22.dp))
        }
        remainingS?.let {
            Text("$it s left", style = SthiraType.title, color = Sthira.colors.muted, modifier = Modifier.clearAndSetSemantics { })
        }
        RepChips(count.summary.events, compact = true)
    }
}

@Composable
private fun Problem(title: String, items: List<String>) {
    Panel {
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Assertive }, verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Text(title, style = SthiraType.title, color = Sthira.colors.danger)
            BulletList(items, color = Sthira.colors.text)
            Text("Press Redo calibration, or Cancel.", style = SthiraType.body, color = Sthira.colors.muted)
        }
    }
}

/** Text and an icon, not colour alone, say whether each attempt counted. */
@Composable
fun RepChips(events: List<RepEvent>, modifier: Modifier = Modifier, compact: Boolean = false) {
    if (events.isEmpty()) return
    val c = Sthira.colors
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        events.forEachIndexed { i, e ->
            val counted = e.kind == RepKind.FULL
            val label = when (e.kind) {
                RepKind.FULL -> "Full"
                RepKind.HALF -> "Half"
                RepKind.ARM_ASSISTED -> "Arms used"
                RepKind.UNCERTAIN -> "Unclear"
            }
            val tone = when (e.kind) {
                RepKind.FULL -> c.success
                RepKind.HALF, RepKind.ARM_ASSISTED -> c.warn
                RepKind.UNCERTAIN -> c.muted
            }
            val spoken = "Attempt ${i + 1}: $label, ${if (counted) "counted" else "not counted"}"
            GlassSurface(shape = Glass.ChipShape, elevation = 0.dp, modifier = Modifier.clearAndSetSemantics { contentDescription = spoken }) {
                Row(
                    Modifier.heightIn(min = if (compact) 36.dp else 44.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(if (counted) Icons.Filled.Check else Icons.Filled.Close, contentDescription = null, tint = tone, modifier = Modifier.size(18.dp))
                    Text("${i + 1} $label", style = SthiraType.label, color = tone)
                }
            }
        }
    }
}
