package app.sthira.chairstand

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import androidx.annotation.StringRes
import app.sthira.R
import app.sthira.engine.RepEvent
import app.sthira.engine.RepKind
import app.sthira.pose.CameraPane
import app.sthira.pose.CameraStatus
import app.sthira.pose.Lens
import app.sthira.pose.PoseTracker
import app.sthira.pose.PoseSample
import app.sthira.ui.common.BulletList
import app.sthira.ui.common.Neu
import app.sthira.ui.common.NeuSurface
import app.sthira.ui.theme.DisplaySize
import app.sthira.ui.theme.Space
import app.sthira.ui.theme.Sthira
import app.sthira.ui.theme.SthiraType
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/**
 * The instruction for each step, read out by the physio and readable from about 3 m. After Start,
 * [personDetected] false means calibration is waiting for someone to step into view.
 */
@StringRes
fun stageTitle(stage: Stage, startRequested: Boolean, personDetected: Boolean = true): Int = when (stage) {
    Stage.Idle -> when {
        !startRequested -> R.string.cs_stage_ready
        personDetected -> R.string.cs_stage_starting
        else -> R.string.cs_step_into_view
    }
    Stage.CalibrationStand -> R.string.cs_stage_stand
    Stage.CalibrationSit -> R.string.cs_stage_sit
    Stage.Test -> R.string.cs_stage_go
    Stage.Done -> R.string.cs_stage_done
}

/**
 * The instruction read aloud when the voice setting is on: the same text the screen shows for the
 * step, plus "Step into view" while calibration waits for a person. Null when nothing is to be said.
 */
@StringRes
fun spokenCue(state: ChairStandUiState): Int? = when {
    state.waitingForPerson -> R.string.cs_step_into_view
    state.stage == Stage.Idle -> null
    else -> stageTitle(state.stage, state.startRequested)
}

/** Giant digits are sized in dp: they already fill the panel, so font scaling must not push them off screen. */
@Composable
private fun display(size: Dp): TextUnit = with(LocalDensity.current) { size.toSp() }

/** Full-bleed camera with the pose skeleton and soft overlay panels for the step, the count and problems. */
@Composable
fun CameraStage(
    state: ChairStandUiState,
    tracker: PoseTracker,
    overlay: StateFlow<PoseSample?>,
    onSwitchLens: () -> Unit,
    onStatus: (CameraStatus) -> Unit,
) {
    CameraPane(lens = state.lens, tracker = tracker, overlay = overlay, onStatus = onStatus) { CameraOverlays(state, onSwitchLens) }
}

/** Everything drawn over the camera; stateless so it can be previewed without a camera. */
@Composable
fun BoxScope.CameraOverlays(state: ChairStandUiState, onSwitchLens: () -> Unit = {}) {
    StatusPill(state, Modifier.align(Alignment.TopStart).padding(12.dp))
    if (!state.isRunning) LensSwitch(state.lens, onSwitchLens, Modifier.align(Alignment.TopEnd).padding(12.dp))
    WarningPills(state, Modifier.align(Alignment.TopCenter).padding(top = 60.dp, start = 12.dp, end = 12.dp))
    StagePanel(state)
}

/** Large round front/back switch on the camera view, with a visible label; hidden while a run is active. */
@Composable
private fun LensSwitch(lens: Lens, onSwitch: () -> Unit, modifier: Modifier) {
    val description = stringResource(if (lens == Lens.Back) R.string.cs_switch_to_front else R.string.cs_switch_to_back)
    Column(
        modifier
            .clickable(role = Role.Button, onClick = onSwitch)
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        NeuSurface(Modifier.size(LENS_BUTTON), shape = CircleShape, elevation = 4.dp, overlay = true) {
            Icon(
                Icons.Filled.Refresh, contentDescription = null, tint = Sthira.colors.text,
                modifier = Modifier.size(32.dp).align(Alignment.Center),
            )
        }
        NeuSurface(shape = Neu.ChipShape, elevation = 3.dp, overlay = true) {
            Text(
                stringResource(R.string.cs_switch_camera), style = SthiraType.label, color = Sthira.colors.text,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp).clearAndSetSemantics { },
            )
        }
    }
}

private val LENS_BUTTON = 64.dp

@Composable
private fun StatusPill(state: ChairStandUiState, modifier: Modifier) {
    val camera = state.camera
    val separator = stringResource(R.string.cs_status_separator)
    val fps = state.stats.fps?.let { pluralStringResource(R.plurals.pose_fps, it.roundToInt(), it.roundToInt()) }
    val person = stringResource(if (state.stats.personDetected) R.string.pose_person_in_view else R.string.pose_no_person)
    val text = when (camera) {
        // The delegate name (GPU or CPU) is a technical term and is not translated.
        is CameraStatus.Live -> listOfNotNull(fps, camera.delegate.label, person).joinToString(separator)
        CameraStatus.Starting -> stringResource(R.string.pose_starting_camera)
        is CameraStatus.Failed -> stringResource(R.string.pose_camera_stopped)
    }
    NeuSurface(modifier, shape = Neu.ChipShape, elevation = 3.dp, overlay = true) {
        Text(text, style = SthiraType.label, color = Sthira.colors.text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

@Composable
private fun WarningPills(state: ChairStandUiState, modifier: Modifier) {
    val summary = (state.count as? CountView.Counting)?.summary
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.s), horizontalAlignment = Alignment.CenterHorizontally) {
        Pill(state.personLost, stringResource(R.string.cs_pill_person_lost), Sthira.colors.danger, assertive = true)
        Pill(summary?.unreliable == true, stringResource(R.string.cs_pill_unreliable), Sthira.colors.danger, assertive = true)
        val fps = summary?.effectiveFps ?: 0.0
        Pill(summary?.lowFps == true, stringResource(R.string.cs_pill_slow, fps), Sthira.colors.warn)
    }
}

@Composable
private fun Pill(visible: Boolean, text: String, tone: Color, assertive: Boolean = false) {
    AnimatedVisibility(visible, enter = fadeIn(Sthira.motion()), exit = fadeOut(Sthira.motion())) {
        NeuSurface(shape = Neu.CardShape, elevation = 4.dp, overlay = true) {
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
            Stage.Idle -> Panel {
                if (state.waitingForPerson) {
                    // Calibration waits here, tracking live, until the landmarker finds a person.
                    Text(
                        stringResource(R.string.cs_step_into_view), style = SthiraType.title, color = Sthira.colors.warn,
                        textAlign = TextAlign.Center, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    )
                } else {
                    PanelText(stringResource(if (state.startRequested) R.string.cs_stage_starting else R.string.cs_camera_checklist), SthiraType.titleSmall)
                }
            }
            Stage.CalibrationStand, Stage.CalibrationSit -> CountdownPanel(stage, state.countdownS)
            Stage.Test -> TestPanel(state)
            Stage.Done -> Panel { PanelText(stringResource(stageTitle(Stage.Done, false)), SthiraType.title) }
        }
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    NeuSurface(Modifier.fillMaxWidth(), elevation = 4.dp, overlay = true) {
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
            stringResource(stageTitle(stage, false)), style = SthiraType.headline, color = Sthira.colors.text, textAlign = TextAlign.Center,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        if (seconds != null) {
            val spoken = pluralStringResource(R.plurals.cs_seconds, seconds, seconds)
            Text(
                "$seconds", style = SthiraType.numeric.copy(fontSize = display(DisplaySize.countdown), lineHeight = display(DisplaySize.countdown)),
                color = Sthira.colors.primary,
                modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
            )
        }
    }
}

@Composable
private fun TestPanel(state: ChairStandUiState) {
    when (val count = state.count) {
        is CountView.Counting -> LivePanel(count, state.remainingS)
        // The reasons come from the engine and stay in English.
        is CountView.CalibrationFailed -> Problem(stringResource(R.string.cs_calibration_failed), count.reasons)
        is CountView.ContextFailed -> Problem(stringResource(R.string.cs_counter_failed), count.errors)
        CountView.NotTuned -> Panel {
            state.elapsedS?.let {
                Text(
                    stringResource(R.string.cs_elapsed_of_total, it, state.durationS.toInt()), style = SthiraType.numeric.copy(fontSize = display(DisplaySize.timer)),
                    color = Sthira.colors.text, modifier = Modifier.clearAndSetSemantics { },
                )
            }
            PanelText(stringResource(R.string.cs_counting_off), SthiraType.titleSmall)
        }
        else -> Panel { PanelText(stringResource(R.string.cs_checking_calibration), SthiraType.title) }
    }
}

@Composable
private fun LivePanel(count: CountView.Counting, remainingS: Int?) {
    val full = count.summary.full
    val word = pluralStringResource(R.plurals.cs_stands_word, full)
    val spoken = pluralStringResource(R.plurals.cs_full_stands_spoken, full, full)
    Panel {
        Row(
            verticalAlignment = Alignment.Bottom,
            // One polite announcement per new full stand; the ticking seconds are not read out.
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = spoken
                liveRegion = LiveRegionMode.Polite
            },
        ) {
            Text(
                "$full", style = SthiraType.numeric.copy(fontSize = display(DisplaySize.liveCount), lineHeight = display(DisplaySize.liveCount)),
                color = Sthira.colors.text,
            )
            Text(stringResource(R.string.cs_stands_word_spaced, word), style = SthiraType.title, color = Sthira.colors.text, modifier = Modifier.padding(bottom = 22.dp))
        }
        remainingS?.let {
            Text(pluralStringResource(R.plurals.cs_seconds_left, it, it), style = SthiraType.title, color = Sthira.colors.muted, modifier = Modifier.clearAndSetSemantics { })
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
            Text(stringResource(R.string.cs_problem_hint), style = SthiraType.body, color = Sthira.colors.muted)
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
            val label = stringResource(
                when (e.kind) {
                    RepKind.FULL -> R.string.cs_rep_full
                    RepKind.HALF -> R.string.cs_rep_half
                    RepKind.ARM_ASSISTED -> R.string.cs_rep_arms
                    RepKind.UNCERTAIN -> R.string.cs_rep_unclear
                },
            )
            val tone = when (e.kind) {
                RepKind.FULL -> c.success
                RepKind.HALF, RepKind.ARM_ASSISTED -> c.warn
                RepKind.UNCERTAIN -> c.muted
            }
            val verdict = stringResource(if (counted) R.string.cs_rep_counted else R.string.cs_rep_not_counted)
            val spoken = stringResource(R.string.cs_rep_spoken, i + 1, label, verdict)
            NeuSurface(shape = Neu.ChipShape, elevation = 3.dp, overlay = true, modifier = Modifier.clearAndSetSemantics { contentDescription = spoken }) {
                Row(
                    Modifier.heightIn(min = if (compact) 36.dp else 44.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(if (counted) Icons.Filled.Check else Icons.Filled.Close, contentDescription = null, tint = tone, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.cs_rep_chip, i + 1, label), style = SthiraType.label, color = tone)
                }
            }
        }
    }
}
