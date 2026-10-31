package app.sthira.timers

import app.sthira.R
import app.sthira.engine.timers.BALANCE_START
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
import org.junit.Assert.assertNull
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
        assertEquals(
            UiText.Res(R.string.timer_stage_quote, listOf(UiText.Res(R.string.timer_stage_cdc_1))),
            TimerTexts.stageInstruction(balance.stages[0]),
        )
        assertEquals(UiText.Raw("new stage"), TimerTexts.stageName(balance.stages[1]))
        assertEquals(
            UiText.Res(R.string.timer_stage_quote, listOf(UiText.Raw("New text."))),
            TimerTexts.stageInstruction(balance.stages[1]),
        )
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
