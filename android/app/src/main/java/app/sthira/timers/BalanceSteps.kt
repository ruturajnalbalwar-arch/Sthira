package app.sthira.timers

import app.sthira.engine.timers.BalanceState

/** One dot of the 1-4 stage stepper. */
enum class StepStatus(val spoken: String) {
    Held("held"),
    Lost("lost position"),
    Current("current"),
    Upcoming("not started"),
    /** The test stopped at an earlier stage, so this one is never run (CDC: stop at the first failure). */
    Skipped("not run"),
}

object BalanceSteps {
    fun statuses(state: BalanceState, stageCount: Int): List<StepStatus> = List(stageCount) { i ->
        val result = state.results.getOrNull(i)
        when {
            result != null -> if (result.completed) StepStatus.Held else StepStatus.Lost
            state.current?.index == i -> StepStatus.Current
            state.finished -> StepStatus.Skipped
            else -> StepStatus.Upcoming
        }
    }
}
