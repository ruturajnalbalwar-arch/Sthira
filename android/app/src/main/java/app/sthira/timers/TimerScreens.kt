package app.sthira.timers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.sthira.R
import app.sthira.engine.timers.BalanceNorms
import app.sthira.engine.timers.BalanceVerdict
import app.sthira.engine.timers.StageResult
import app.sthira.engine.timers.TugNorms
import app.sthira.engine.timers.TugVerdict
import app.sthira.engine.timers.balanceVerdict
import app.sthira.engine.timers.tugVerdict
import app.sthira.ui.common.ActionButton
import app.sthira.ui.common.Body
import app.sthira.ui.common.BulletList
import app.sthira.ui.common.CheckRow
import app.sthira.ui.common.Heading
import app.sthira.ui.common.ScreenScaffold
import app.sthira.ui.common.Tone
import app.sthira.ui.common.asString
import app.sthira.ui.theme.Space
import app.sthira.ui.theme.Sthira
import app.sthira.ui.theme.SthiraType

/** The start and stop controls are taller than a normal big button: they are pressed while watching the person. */
private val CONTROL_MIN_HEIGHT = 72.dp

@Composable
private fun seconds(value: Double): String = TimerTexts.seconds(value).asString()

private val controlModifier: Modifier get() = Modifier.fillMaxWidth().heightIn(min = CONTROL_MIN_HEIGHT)

@Composable
fun BalanceScreen(onBack: () -> Unit, vm: BalanceViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val norms = state.norms
    val title = stringResource(R.string.timer_balance_title)
    if (norms == null) {
        ScreenScaffold(title, onBack) { TimerError(state.errors.orEmpty()) }
        return
    }
    val voice = rememberTimerVoice()
    BalanceVoiceCues(state, norms, voice)
    val onEvent: (BalanceUiEvent) -> Unit = { event ->
        if (event == BalanceUiEvent.CancelStage || event == BalanceUiEvent.Reset) voice.stop()
        vm.onEvent(event)
    }
    val phase = state.phase
    ScreenScaffold(title, onBack, bottomBar = { BalanceActions(state, onEvent) }) {
        val index = state.test.current?.index
        val shown = if (phase == BalancePhase.Ready || phase == BalancePhase.Running) index else null
        StageStepper(norms.stages, BalanceSteps.statuses(state.test, norms.stages.size), shown)
        when (phase) {
            BalancePhase.Ready, BalancePhase.Running -> if (index != null) CurrentStage(state, norms, index)
            BalancePhase.StageDone -> state.test.results.lastOrNull()?.let { StageOutcome(it) }
            BalancePhase.Finished -> FinishedBalance(state, norms)
        }
        HowToRun {
            BulletList(
                listOf(
                    stringResource(R.string.timer_balance_safety),
                    stringResource(R.string.timer_balance_allowed_movement),
                    stringResource(R.string.timer_balance_lost_hint),
                    stringResource(R.string.timer_balance_progression, seconds(norms.stageDurationS)),
                ),
            )
        }
    }
}

/** Says the stage instruction when a stage starts and "Stop" when it ends (held or lost). */
@Composable
private fun BalanceVoiceCues(state: BalanceUiState, norms: BalanceNorms, voice: TimerVoice) {
    val current = state.test.current
    val instruction = current?.let { norms.stages.getOrNull(it.index) }?.let { TimerTexts.stageInstruction(it).asString() }.orEmpty()
    SpeakOnChange(voice, current?.startT, instruction)
    SpeakOnChange(voice, state.test.results.size.takeIf { it > 0 }, stringResource(R.string.timer_voice_stop))
}

/** One stage, full screen: its name, a picture of the feet, one short instruction and the big timer. */
@Composable
private fun CurrentStage(state: BalanceUiState, norms: BalanceNorms, index: Int) {
    val stage = norms.stages[index]
    Text(TimerTexts.stageName(stage).asString(), style = SthiraType.headline, color = Sthira.colors.text)
    BalanceSteps.footPosition(stage)?.let { FeetPicture(it) }
    Body(TimerTexts.stageInstruction(stage).asString(), size = 22)
    BigTime(state.heldS, stringResource(R.string.timer_hold_for, seconds(norms.stageDurationS)))
}

@Composable
private fun FinishedBalance(state: BalanceUiState, norms: BalanceNorms) {
    state.test.results.lastOrNull()?.let { StageOutcome(it) }
    BalanceVerdictCard(balanceVerdict(state.test, norms), norms)
    StageResults(state.test.results)
}

@Composable
private fun BalanceActions(state: BalanceUiState, onEvent: (BalanceUiEvent) -> Unit) {
    val wide = Modifier.fillMaxWidth()
    val startOver = stringResource(R.string.timer_start_over)
    when (state.phase) {
        BalancePhase.Finished ->
            ActionButton(startOver, { onEvent(BalanceUiEvent.Reset) }, controlModifier, big = true, icon = Icons.Filled.Refresh)
        BalancePhase.Running -> {
            ActionButton(
                stringResource(R.string.timer_lost_position), { onEvent(BalanceUiEvent.Lost) }, controlModifier,
                tone = Tone.Danger, big = true, icon = Icons.Filled.Close,
            )
            ActionButton(stringResource(R.string.timer_restart_stage), { onEvent(BalanceUiEvent.CancelStage) }, wide, tone = Tone.Secondary)
        }
        BalancePhase.StageDone -> {
            ActionButton(
                stringResource(R.string.timer_next_stage), { onEvent(BalanceUiEvent.NextStage) }, controlModifier,
                big = true, icon = Icons.Filled.ArrowForward,
            )
            ActionButton(startOver, { onEvent(BalanceUiEvent.Reset) }, wide, tone = Tone.Secondary)
        }
        BalancePhase.Ready -> {
            ActionButton(
                stringResource(R.string.timer_start), { onEvent(BalanceUiEvent.StartStage) }, controlModifier,
                big = true, icon = Icons.Filled.PlayArrow,
            )
            if (state.test.results.isNotEmpty()) {
                ActionButton(startOver, { onEvent(BalanceUiEvent.Reset) }, wide, tone = Tone.Secondary)
            }
        }
    }
}

@Composable
private fun StageResults(results: List<StageResult>) {
    if (results.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Heading(stringResource(R.string.timer_stage_results), size = 20)
        results.forEach { r ->
            val outcome = stringResource(if (r.completed) R.string.timer_outcome_held else R.string.timer_outcome_lost)
            val held = seconds(r.heldS)
            val spoken = stringResource(R.string.timer_stage_result_cd, r.stage, held, outcome)
            Row(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = spoken },
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                Body(stringResource(R.string.timer_stage_label, r.stage), Modifier.weight(1f), bold = true)
                Body(held, Modifier.weight(1f))
                Body(outcome, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BalanceVerdictCard(verdict: BalanceVerdict, norms: BalanceNorms) {
    val title = TimerTexts.balanceVerdictTitle(verdict, norms) ?: return
    ResultBanner(verdict is BalanceVerdict.Meets, title.asString()) { CdcNote(norms.source) }
}

@Composable
fun TugScreen(onBack: () -> Unit, vm: TugViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val norms = state.norms
    val title = stringResource(R.string.timer_tug_title)
    if (norms == null) {
        ScreenScaffold(title, onBack) { TimerError(state.errors.orEmpty()) }
        return
    }
    val voice = rememberTimerVoice()
    SpeakOnChange(voice, (state.run as? TugRun.Running)?.startT, stringResource(R.string.timer_tug_go))
    ScreenScaffold(title, onBack, bottomBar = { TugActions(state.run, vm::onEvent) }) {
        BigTime(state.shownSeconds, title)
        val done = state.run as? TugRun.Done
        AnimatedVisibility(done != null) {
            if (done == null) return@AnimatedVisibility
            TugResult(done.seconds, state.usedAid, norms)
        }
        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            listOf(R.string.timer_tug_step_sit, R.string.timer_tug_step_go, R.string.timer_tug_step_stop).forEachIndexed { i, id ->
                Body(stringResource(R.string.timer_tug_numbered, i + 1, stringResource(id)), size = 20)
            }
        }
        CheckRow(stringResource(R.string.timer_used_aid), state.usedAid, { vm.onEvent(TugUiEvent.UsedAid(it)) })
        HowToRun {
            val metres = norms.walkDistanceMeters.toInt()
            BulletList(
                listOf(
                    pluralStringResource(R.plurals.timer_tug_step_line, metres, metres),
                    stringResource(R.string.timer_tug_step_walk),
                    stringResource(R.string.timer_tug_footwear),
                ),
            )
        }
    }
}

@Composable
private fun TugResult(seconds: Double, usedAid: Boolean, norms: TugNorms) {
    val below = tugVerdict(seconds, norms) == TugVerdict.BELOW
    ResultBanner(below, TimerTexts.tugResultTitle(seconds, norms).asString()) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            if (usedAid) Body(stringResource(R.string.timer_aid_was_used), bold = true)
            CdcNote(norms.source)
        }
    }
}

/** One big button that walks through Go, then Sat back down: stop, then Run again. */
@Composable
private fun TugActions(run: TugRun, onEvent: (TugUiEvent) -> Unit) {
    when (run) {
        is TugRun.Running -> ActionButton(
            stringResource(R.string.timer_tug_stop), { onEvent(TugUiEvent.SatDown) }, controlModifier,
            tone = Tone.Danger, big = true, icon = Icons.Filled.Close,
        )
        is TugRun.Done -> ActionButton(
            stringResource(R.string.timer_tug_run_again), { onEvent(TugUiEvent.Go) }, controlModifier, big = true, icon = Icons.Filled.Refresh,
        )
        TugRun.Idle -> ActionButton(stringResource(R.string.timer_tug_go), { onEvent(TugUiEvent.Go) }, controlModifier, big = true, icon = Icons.Filled.PlayArrow)
    }
}
