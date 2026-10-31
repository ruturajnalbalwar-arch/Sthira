package app.sthira.timers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
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
import app.sthira.engine.timers.TugVerdict
import app.sthira.engine.timers.balanceVerdict
import app.sthira.engine.timers.tugVerdict
import app.sthira.ui.common.ActionButton
import app.sthira.ui.common.Body
import app.sthira.ui.common.BulletList
import app.sthira.ui.common.CheckRow
import app.sthira.ui.common.Heading
import app.sthira.ui.common.Muted
import app.sthira.ui.common.ScreenScaffold
import app.sthira.ui.common.Tone
import app.sthira.ui.common.asString
import app.sthira.ui.theme.Space

/** The start and stop controls are taller than a normal big button: they are pressed while watching the person. */
private val CONTROL_MIN_HEIGHT = 72.dp

@Composable
private fun seconds(value: Double): String = TimerTexts.seconds(value).asString()

@Composable
fun BalanceScreen(onBack: () -> Unit, vm: BalanceViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val norms = state.norms
    val title = stringResource(R.string.timer_balance_title)
    if (norms == null) {
        ScreenScaffold(title, onBack) { TimerError(state.errors.orEmpty()) }
        return
    }
    ScreenScaffold(title, onBack, bottomBar = { BalanceActions(state, norms, vm::onEvent) }) {
        StageStepper(norms.stages, BalanceSteps.statuses(state.test, norms.stages.size))
        val index = state.test.current?.index
        if (index != null) {
            val stage = norms.stages[index]
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Heading(stringResource(R.string.timer_stage_heading, stage.stage, TimerTexts.stageName(stage).asString()), size = 22)
                Body(TimerTexts.stageInstruction(stage).asString(), size = 20)
            }
            BigTime(state.heldS, stringResource(R.string.timer_hold_for, seconds(norms.stageDurationS)))
            Body(stringResource(R.string.timer_balance_lost_hint))
        }
        StageResults(state.test.results)
        BalanceVerdictCard(balanceVerdict(state.test, norms), norms)
        BulletList(
            listOf(
                stringResource(R.string.timer_balance_safety),
                stringResource(R.string.timer_balance_allowed_movement),
                stringResource(R.string.timer_balance_progression, seconds(norms.stageDurationS)),
            ),
        )
    }
}

@Composable
private fun BalanceActions(state: BalanceUiState, norms: BalanceNorms, onEvent: (BalanceUiEvent) -> Unit) {
    val control = Modifier.fillMaxWidth().heightIn(min = CONTROL_MIN_HEIGHT)
    val wide = Modifier.fillMaxWidth()
    val startOver = stringResource(R.string.timer_start_over)
    val index = state.test.current?.index
    when {
        index == null -> ActionButton(startOver, { onEvent(BalanceUiEvent.Reset) }, control, big = true, icon = Icons.Filled.Refresh)
        state.running -> {
            ActionButton(
                stringResource(R.string.timer_lost_position), { onEvent(BalanceUiEvent.Lost) }, control,
                tone = Tone.Danger, big = true, icon = Icons.Filled.Close,
            )
            ActionButton(stringResource(R.string.timer_restart_stage), { onEvent(BalanceUiEvent.CancelStage) }, wide, tone = Tone.Secondary)
        }
        else -> {
            ActionButton(
                stringResource(R.string.timer_start_stage, norms.stages[index].stage), { onEvent(BalanceUiEvent.StartStage) }, control,
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
    ScreenScaffold(title, onBack, bottomBar = { TugActions(state.run, vm::onEvent) }) {
        BigTime(state.shownSeconds, title)
        CheckRow(stringResource(R.string.timer_used_aid), state.usedAid, { vm.onEvent(TugUiEvent.UsedAid(it)) })
        val done = state.run as? TugRun.Done
        AnimatedVisibility(done != null) {
            if (done == null) return@AnimatedVisibility
            val below = tugVerdict(done.seconds, norms) == TugVerdict.BELOW
            ResultBanner(below, TimerTexts.tugResultTitle(done.seconds, norms).asString()) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    if (state.usedAid) Body(stringResource(R.string.timer_aid_was_used), bold = true)
                    CdcNote(norms.source)
                }
            }
        }
        Heading(stringResource(R.string.timer_tug_how_heading), size = 20)
        val metres = norms.walkDistanceMeters.toInt()
        BulletList(
            listOf(
                pluralStringResource(R.plurals.timer_tug_step_line, metres, metres),
                stringResource(R.string.timer_tug_step_sit),
                stringResource(R.string.timer_tug_step_go),
                stringResource(R.string.timer_tug_step_walk),
                stringResource(R.string.timer_tug_step_stop),
            ),
        )
        Muted(stringResource(R.string.timer_tug_footwear))
    }
}

@Composable
private fun TugActions(run: TugRun, onEvent: (TugUiEvent) -> Unit) {
    val control = Modifier.fillMaxWidth().heightIn(min = CONTROL_MIN_HEIGHT)
    when (run) {
        is TugRun.Running -> ActionButton(
            stringResource(R.string.timer_tug_stop), { onEvent(TugUiEvent.SatDown) }, control,
            tone = Tone.Danger, big = true, icon = Icons.Filled.Close,
        )
        is TugRun.Done -> ActionButton(
            stringResource(R.string.timer_tug_run_again), { onEvent(TugUiEvent.Go) }, control, big = true, icon = Icons.Filled.Refresh,
        )
        TugRun.Idle -> ActionButton(stringResource(R.string.timer_tug_go), { onEvent(TugUiEvent.Go) }, control, big = true, icon = Icons.Filled.PlayArrow)
    }
}
