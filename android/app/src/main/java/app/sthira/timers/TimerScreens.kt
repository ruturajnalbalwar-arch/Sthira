package app.sthira.timers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewmodel.compose.viewModel
import app.sthira.engine.timers.BalanceNorms
import app.sthira.engine.timers.BalanceVerdict
import app.sthira.engine.timers.StageResult
import app.sthira.engine.timers.TugVerdict
import app.sthira.engine.timers.balanceVerdict
import app.sthira.engine.timers.tugVerdict
import app.sthira.ui.common.ActionButton
import app.sthira.ui.common.Body
import app.sthira.ui.common.CheckRow
import app.sthira.ui.common.Heading
import app.sthira.ui.common.Muted
import app.sthira.ui.common.ScreenScaffold
import app.sthira.ui.common.Tone
import app.sthira.ui.theme.Space

private const val BALANCE_TITLE = "4-stage balance"
private const val TUG_TITLE = "Timed Up and Go"

@Composable
fun BalanceScreen(onBack: () -> Unit, vm: BalanceViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val norms = state.norms
    if (norms == null) {
        ScreenScaffold(BALANCE_TITLE, onBack) { TimerError(state.errors.orEmpty()) }
        return
    }
    ScreenScaffold(BALANCE_TITLE, onBack, bottomBar = { BalanceActions(state, norms, vm::onEvent) }) {
        StageStepper(norms.stages, BalanceSteps.statuses(state.test, norms.stages.size))
        val index = state.test.current?.index
        if (index != null) {
            val stage = norms.stages[index]
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Heading("Stage ${stage.stage}: ${stage.name}", size = 20)
                Body("“${stage.cdcText}”", size = 18)
            }
            BigTime(state.heldS, "Hold for ${formatSeconds(norms.stageDurationS)}")
            Muted("Press Lost position if the feet move or the person needs support. ${norms.allowedMovement}")
        }
        StageResults(state.test.results)
        BalanceVerdictCard(balanceVerdict(state.test, norms), norms)
        Muted("Stand beside the person, ready to steady them. No cane or walker, eyes open. ${norms.progressionRule}")
    }
}

@Composable
private fun BalanceActions(state: BalanceUiState, norms: BalanceNorms, onEvent: (BalanceUiEvent) -> Unit) {
    val wide = Modifier.fillMaxWidth()
    val index = state.test.current?.index
    when {
        index == null -> ActionButton("Start over", { onEvent(BalanceUiEvent.Reset) }, wide, big = true, icon = Icons.Filled.Refresh)
        state.running -> {
            ActionButton("Lost position", { onEvent(BalanceUiEvent.Lost) }, wide, tone = Tone.Danger, big = true)
            ActionButton("Not ready, restart stage", { onEvent(BalanceUiEvent.CancelStage) }, wide, tone = Tone.Secondary)
        }
        else -> {
            ActionButton(
                "Start stage ${norms.stages[index].stage}", { onEvent(BalanceUiEvent.StartStage) }, wide, big = true,
                icon = Icons.Filled.PlayArrow,
            )
            if (state.test.results.isNotEmpty()) {
                ActionButton("Start over", { onEvent(BalanceUiEvent.Reset) }, wide, tone = Tone.Secondary)
            }
        }
    }
}

@Composable
private fun StageResults(results: List<StageResult>) {
    if (results.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Heading("Stage results", size = 18)
        results.forEach { r ->
            val outcome = if (r.completed) "Held" else "Lost position"
            Row(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                    contentDescription = "Stage ${r.stage}: ${formatSeconds(r.heldS)}, $outcome"
                },
            ) {
                Body("Stage ${r.stage}", Modifier.weight(1f), bold = true)
                Body(formatSeconds(r.heldS), Modifier.weight(1f))
                Body(outcome, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BalanceVerdictCard(verdict: BalanceVerdict, norms: BalanceNorms) {
    val limit = formatSeconds(norms.riskHoldLessThanS)
    val (meets, title) = when (verdict) {
        BalanceVerdict.NotReached -> return
        is BalanceVerdict.StoppedBefore ->
            false to "Stopped at stage ${verdict.stoppedAtStage}, before the tandem stand: below the CDC reference of $limit in tandem"
        is BalanceVerdict.Below -> false to "Tandem stand held ${formatSeconds(verdict.heldS)}: below the CDC reference of $limit"
        is BalanceVerdict.Meets -> true to "Tandem stand held ${formatSeconds(verdict.heldS)}: meets the CDC reference of $limit"
    }
    ResultBanner(meets, title) { CdcNote(norms.source) }
}

@Composable
fun TugScreen(onBack: () -> Unit, vm: TugViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val norms = state.norms
    if (norms == null) {
        ScreenScaffold(TUG_TITLE, onBack) { TimerError(state.errors.orEmpty()) }
        return
    }
    ScreenScaffold(TUG_TITLE, onBack, bottomBar = { TugActions(state.run, vm::onEvent) }) {
        BigTime(state.shownSeconds, "Timed Up and Go")
        CheckRow("Used a walking aid", state.usedAid, { vm.onEvent(TugUiEvent.UsedAid(it)) })
        val done = state.run as? TugRun.Done
        AnimatedVisibility(done != null) {
            if (done == null) return@AnimatedVisibility
            val below = tugVerdict(done.seconds, norms) == TugVerdict.BELOW
            val limit = formatSeconds(norms.atRiskIfAtLeastS)
            ResultBanner(below, "${formatSeconds(done.seconds)}: ${if (below) "under" else "at or above"} the CDC reference of $limit") {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    if (state.usedAid) Body("A walking aid was used.", bold = true)
                    CdcNote(norms.source)
                }
            }
        }
        Heading("How to run it", size = 18)
        Muted(
            "Mark a line ${norms.walkDistanceMeters.toInt()} m from the chair. The person sits back in the chair. Press Go as you " +
                "say “Go”; they stand, walk to the line at a normal pace, turn, walk back and sit down. ${norms.timingRule}",
        )
        Muted(norms.footwearAndAids)
    }
}

@Composable
private fun TugActions(run: TugRun, onEvent: (TugUiEvent) -> Unit) {
    val wide = Modifier.fillMaxWidth()
    when (run) {
        is TugRun.Running -> ActionButton("Sat back down: stop", { onEvent(TugUiEvent.SatDown) }, wide, tone = Tone.Danger, big = true)
        is TugRun.Done -> ActionButton("Run again: Go", { onEvent(TugUiEvent.Go) }, wide, big = true, icon = Icons.Filled.Refresh)
        TugRun.Idle -> ActionButton("Go", { onEvent(TugUiEvent.Go) }, wide, big = true, icon = Icons.Filled.PlayArrow)
    }
}
