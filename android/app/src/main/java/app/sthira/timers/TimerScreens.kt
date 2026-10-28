package app.sthira.timers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import app.sthira.ui.theme.Sthira

@Composable
fun BalanceScreen(onBack: () -> Unit, vm: BalanceViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    ScreenScaffold("4-Stage Balance Test", onBack) {
        val norms = state.norms
        if (norms == null) {
            TimerError(state.errors.orEmpty())
            return@ScreenScaffold
        }
        Muted("You time each stage. Stand beside the person, ready to steady them. No cane or walker, eyes open. ${norms.progressionRule}")
        BalanceStage(state, norms, vm::onEvent)
        if (state.test.finished) {
            Text("Test complete.", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Sthira.colors.text,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        StageResults(state.test.results)
        BalanceVerdictCard(balanceVerdict(state.test, norms), norms)
        if (state.test.results.isNotEmpty()) {
            ActionButton("Start over", { vm.onEvent(BalanceUiEvent.Reset) }, tone = Tone.Secondary)
        }
    }
}

@Composable
private fun BalanceStage(state: BalanceUiState, norms: BalanceNorms, onEvent: (BalanceUiEvent) -> Unit) {
    val index = state.test.current?.index ?: return
    val stage = norms.stages[index]
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Heading("Stage ${stage.stage} of ${norms.stages.size}: ${stage.name}", size = 22)
        Body("“${stage.cdcText}”", size = 18)
        BigTime(state.heldS, "Hold for ${formatSeconds(norms.stageDurationS)}")
        if (state.running) {
            ActionButton("Lost position", { onEvent(BalanceUiEvent.Lost) }, Modifier.fillMaxWidth(), tone = Tone.Danger, big = true)
            ActionButton("Not ready, restart stage", { onEvent(BalanceUiEvent.CancelStage) }, Modifier.fillMaxWidth(), tone = Tone.Secondary)
        } else {
            ActionButton("Start stage ${stage.stage}", { onEvent(BalanceUiEvent.StartStage) }, Modifier.fillMaxWidth(), big = true)
        }
        Muted("Press Lost position if the feet move or the person needs support. ${norms.allowedMovement}")
    }
}

@Composable
private fun StageResults(results: List<StageResult>) {
    if (results.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Heading("Stage results", size = 18)
        results.forEach { r ->
            val outcome = if (r.completed) "Held" else "Lost position"
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                contentDescription = "Stage ${r.stage}: ${formatSeconds(r.heldS)}, $outcome"
            }) {
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
    ScreenScaffold("Timed Up and Go", onBack) {
        val norms = state.norms
        if (norms == null) {
            TimerError(state.errors.orEmpty())
            return@ScreenScaffold
        }
        Muted(
            "Mark a line ${norms.walkDistanceMeters.toInt()} m from the chair. The person sits back in the chair. Press Go as you say " +
                "“Go”; they stand, walk to the line at a normal pace, turn, walk back and sit down. " +
                "${norms.timingRule} ${norms.footwearAndAids}",
        )
        BigTime(state.shownSeconds, "Timed Up and Go")
        when (state.run) {
            is TugRun.Running -> ActionButton("Sat back down: stop", { vm.onEvent(TugUiEvent.SatDown) }, Modifier.fillMaxWidth(), big = true)
            is TugRun.Done -> ActionButton("Run again: Go", { vm.onEvent(TugUiEvent.Go) }, Modifier.fillMaxWidth(), big = true)
            TugRun.Idle -> ActionButton("Go", { vm.onEvent(TugUiEvent.Go) }, Modifier.fillMaxWidth(), big = true)
        }
        CheckRow("Used a walking aid", state.usedAid, { vm.onEvent(TugUiEvent.UsedAid(it)) })
        val done = state.run as? TugRun.Done ?: return@ScreenScaffold
        val below = tugVerdict(done.seconds, norms) == TugVerdict.BELOW
        val limit = formatSeconds(norms.atRiskIfAtLeastS)
        ResultBanner(below, "${formatSeconds(done.seconds)}: ${if (below) "under" else "at or above"} the CDC reference of $limit") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (state.usedAid) Body("A walking aid was used.", bold = true)
                CdcNote(norms.source)
            }
        }
    }
}
