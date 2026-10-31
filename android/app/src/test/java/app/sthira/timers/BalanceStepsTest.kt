package app.sthira.timers

import app.sthira.R
import app.sthira.engine.timers.BALANCE_START
import app.sthira.engine.timers.BalanceEvent
import app.sthira.engine.timers.BalanceNorms
import app.sthira.engine.timers.BalanceStage
import app.sthira.engine.timers.BalanceState
import app.sthira.engine.timers.BalanceVerdict
import app.sthira.engine.timers.CdcSource
import app.sthira.engine.timers.CurrentStage
import app.sthira.engine.timers.StageResult
import app.sthira.engine.timers.TugNorms
import app.sthira.ui.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceStepsTest {
    @Test
    fun `a fresh test has the first stage current`() {
        assertEquals(
            listOf(StepStatus.Current, StepStatus.Upcoming, StepStatus.Upcoming, StepStatus.Upcoming),
            BalanceSteps.statuses(BALANCE_START, 4),
        )
    }

    @Test
    fun `held stages are marked and the next one is current`() {
        val state = BalanceState(listOf(StageResult(1, 10.0, true), StageResult(2, 10.0, true)), CurrentStage(2, 4.0), false)
        assertEquals(
            listOf(StepStatus.Held, StepStatus.Held, StepStatus.Current, StepStatus.Upcoming),
            BalanceSteps.statuses(state, 4),
        )
    }

    @Test
    fun `losing position stops the test and later stages are not run`() {
        val state = BalanceState(listOf(StageResult(1, 10.0, true), StageResult(2, 3.0, false)), null, true)
        assertEquals(
            listOf(StepStatus.Held, StepStatus.Lost, StepStatus.Skipped, StepStatus.Skipped),
            BalanceSteps.statuses(state, 4),
        )
    }

    @Test
    fun `every stepper state is spoken from its own string`() {
        assertEquals(
            listOf(
                R.string.timer_step_held, R.string.timer_step_lost, R.string.timer_step_current,
                R.string.timer_step_upcoming, R.string.timer_step_skipped,
            ),
            StepStatus.entries.map { it.spoken },
        )
    }
}

class BalanceFlowTest {
    private val source = CdcSource("https://example.org/steadi.pdf", "STEADI sheet")
    private val norms = BalanceNorms(
        stages = listOf(BalanceStage(1, "a", "A."), BalanceStage(2, "b", "B."), BalanceStage(3, "c", "C.")),
        stageDurationS = 10.0, progressionRule = "", allowedMovement = "", riskStage = 3, riskHoldLessThanS = 10.0, source = source,
    )
    private val start = BalanceUiState(norms = norms)

    @Test
    fun `a fresh test is ready for its first stage`() {
        assertEquals(BalancePhase.Ready, start.phase)
    }

    @Test
    fun `starting a stage runs the timer`() {
        val running = start.advance(BalanceEvent.StartStage(1.0), 1.0, norms)
        assertEquals(BalancePhase.Running, running.phase)
        assertFalse(running.reviewing)
    }

    @Test
    fun `holding the full time shows the stage result before the next stage`() {
        val held = start.advance(BalanceEvent.StartStage(1.0), 1.0, norms).advance(BalanceEvent.Tick(11.0), 11.0, norms)
        assertTrue(held.reviewing)
        assertEquals(BalancePhase.StageDone, held.phase)
        assertEquals(StageResult(1, 10.0, true), held.test.results.single())
        assertEquals(1, held.test.current?.index)
    }

    @Test
    fun `a tick before the full time keeps the stage running`() {
        val ticking = start.advance(BalanceEvent.StartStage(1.0), 1.0, norms).advance(BalanceEvent.Tick(5.0), 5.0, norms)
        assertEquals(BalancePhase.Running, ticking.phase)
        assertFalse(ticking.reviewing)
    }

    @Test
    fun `losing balance finishes the test`() {
        val lost = start.advance(BalanceEvent.StartStage(1.0), 1.0, norms).advance(BalanceEvent.Lost(4.0), 4.0, norms)
        assertEquals(BalancePhase.Finished, lost.phase)
        assertEquals(StageResult(1, 3.0, false), lost.test.results.single())
    }

    @Test
    fun `not ready returns to the same stage without a result`() {
        val cancelled = start.advance(BalanceEvent.StartStage(1.0), 1.0, norms).advance(BalanceEvent.CancelStage, 2.0, norms)
        assertEquals(BalancePhase.Ready, cancelled.phase)
        assertTrue(cancelled.test.results.isEmpty())
    }

    @Test
    fun `reset clears the review step and the results`() {
        val held = start.advance(BalanceEvent.StartStage(1.0), 1.0, norms).advance(BalanceEvent.Tick(11.0), 11.0, norms)
        val reset = held.advance(BalanceEvent.Reset, 12.0, norms)
        assertFalse(reset.reviewing)
        assertEquals(BALANCE_START, reset.test)
        assertEquals(BalancePhase.Ready, reset.phase)
    }

    @Test
    fun `phase follows the engine state`() {
        assertEquals(BalancePhase.Finished, BalanceSteps.phase(BalanceState(emptyList(), null, true), reviewing = true))
        assertEquals(BalancePhase.Running, BalanceSteps.phase(BalanceState(emptyList(), CurrentStage(0, 1.0), false), reviewing = true))
        assertEquals(BalancePhase.StageDone, BalanceSteps.phase(BalanceState(emptyList(), CurrentStage(1, null), false), reviewing = true))
        assertEquals(BalancePhase.Ready, BalanceSteps.phase(BalanceState(emptyList(), CurrentStage(1, null), false), reviewing = false))
    }

    @Test
    fun `each CDC stage has its own foot picture and unknown stages have none`() {
        assertEquals(
            listOf(FootPosition.SideBySide, FootPosition.SemiTandem, FootPosition.Tandem, FootPosition.OneFoot, null),
            listOf(1, 2, 3, 4, 9).map { BalanceSteps.footPosition(BalanceStage(it, "", "")) },
        )
    }
}

class TimerTextsTest {
    private val source = CdcSource("https://example.org/steadi.pdf", "STEADI sheet")
    private val balance = BalanceNorms(
        stages = listOf(BalanceStage(1, "feet side-by-side", "Stand."), BalanceStage(9, "new stage", "New text.")),
        stageDurationS = 10.0, progressionRule = "", allowedMovement = "", riskStage = 3, riskHoldLessThanS = 10.0, source = source,
    )
    private val tug = TugNorms(atRiskIfAtLeastS = 12.0, walkDistanceMeters = 3.0, timingRule = "", footwearAndAids = "", source = source)

    private fun s(value: Double) = UiText.Res(R.string.timer_seconds_value, listOf(value))

    @Test
    fun `known stages use translated strings and unknown ones fall back to norms json`() {
        assertEquals(UiText.Res(R.string.timer_stage_name_1), TimerTexts.stageName(balance.stages[0]))
        assertEquals(UiText.Res(R.string.timer_stage_do_1), TimerTexts.stageInstruction(balance.stages[0]))
        assertEquals(UiText.Raw("new stage"), TimerTexts.stageName(balance.stages[1]))
        assertEquals(UiText.Raw("New text."), TimerTexts.stageInstruction(balance.stages[1]))
    }

    @Test
    fun `stage of line carries the stage number and the number of stages`() {
        assertEquals(UiText.Res(R.string.timer_stage_of, listOf(9, 2)), TimerTexts.stageOf(balance.stages[1], balance.stages.size))
    }

    @Test
    fun `stage outcome says passed or stopped with the time held`() {
        assertEquals(UiText.Res(R.string.timer_stage_passed, listOf(2, s(10.0))), TimerTexts.stageOutcome(StageResult(2, 10.0, true)))
        assertEquals(UiText.Res(R.string.timer_stage_stopped, listOf(3, s(4.5))), TimerTexts.stageOutcome(StageResult(3, 4.5, false)))
    }

    @Test
    fun `balance verdict titles carry the held time and the norms cut-off`() {
        assertNull(TimerTexts.balanceVerdictTitle(BalanceVerdict.NotReached, balance))
        assertEquals(
            UiText.Res(R.string.timer_balance_stopped_before, listOf(2, s(10.0))),
            TimerTexts.balanceVerdictTitle(BalanceVerdict.StoppedBefore(2), balance),
        )
        assertEquals(
            UiText.Res(R.string.timer_balance_below, listOf(s(4.2), s(10.0))),
            TimerTexts.balanceVerdictTitle(BalanceVerdict.Below(4.2), balance),
        )
        assertEquals(
            UiText.Res(R.string.timer_balance_meets, listOf(s(10.0), s(10.0))),
            TimerTexts.balanceVerdictTitle(BalanceVerdict.Meets(10.0), balance),
        )
    }

    @Test
    fun `tug title says under or at or above the norms cut-off`() {
        assertEquals(UiText.Res(R.string.timer_tug_under, listOf(s(11.9), s(12.0))), TimerTexts.tugResultTitle(11.9, tug))
        assertEquals(UiText.Res(R.string.timer_tug_at_or_above, listOf(s(12.0), s(12.0))), TimerTexts.tugResultTitle(12.0, tug))
    }
}
