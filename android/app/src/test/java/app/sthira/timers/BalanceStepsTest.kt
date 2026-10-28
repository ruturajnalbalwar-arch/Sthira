package app.sthira.timers

import app.sthira.engine.timers.BALANCE_START
import app.sthira.engine.timers.BalanceState
import app.sthira.engine.timers.CurrentStage
import app.sthira.engine.timers.StageResult
import org.junit.Assert.assertEquals
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
}
