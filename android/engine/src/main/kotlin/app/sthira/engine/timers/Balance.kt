package app.sthira.engine.timers

/** Mirror of web/src/lib/timers/balance.ts: the CDC 4-stage balance flow as a pure reducer. */
data class StageResult(val stage: Int, val heldS: Double, val completed: Boolean)

/** Stage being run (index into the CDC stages); startT is null while waiting for the physio to start it. */
data class CurrentStage(val index: Int, val startT: Double?)

data class BalanceState(val results: List<StageResult>, val current: CurrentStage?, val finished: Boolean)

sealed interface BalanceEvent {
    data class StartStage(val t: Double) : BalanceEvent
    data class Tick(val t: Double) : BalanceEvent
    data class Lost(val t: Double) : BalanceEvent
    data object CancelStage : BalanceEvent
    data object Reset : BalanceEvent
}

val BALANCE_START: BalanceState = BalanceState(results = emptyList(), current = CurrentStage(0, null), finished = false)

private fun record(state: BalanceState, heldS: Double, completed: Boolean, norms: BalanceNorms): BalanceState {
    val index = state.current?.index ?: return state
    val results = state.results + StageResult(norms.stages[index].stage, heldS, completed)
    val next = index + 1
    if (!completed || next >= norms.stages.size) return BalanceState(results, null, true)
    return BalanceState(results, CurrentStage(next, null), false)
}

private fun timedEvent(state: BalanceState, event: BalanceEvent, t: Double, startT: Double, norms: BalanceNorms): BalanceState {
    val elapsed = minOf(maxOf(t - startT, 0.0), norms.stageDurationS)
    if (event is BalanceEvent.Lost) return record(state, elapsed, false, norms)
    return if (elapsed >= norms.stageDurationS) record(state, norms.stageDurationS, true, norms) else state
}

/** Hold each stage for the protocol time; stop at the first stage not held. */
fun balanceReducer(state: BalanceState, event: BalanceEvent, norms: BalanceNorms): BalanceState {
    if (event is BalanceEvent.Reset) return BALANCE_START
    val current = state.current ?: return state
    if (event is BalanceEvent.StartStage) {
        return if (current.startT == null) state.copy(current = current.copy(startT = event.t)) else state
    }
    val startT = current.startT ?: return state
    return when (event) {
        is BalanceEvent.CancelStage -> state.copy(current = current.copy(startT = null))
        is BalanceEvent.Lost -> timedEvent(state, event, event.t, startT, norms)
        is BalanceEvent.Tick -> timedEvent(state, event, event.t, startT, norms)
        is BalanceEvent.StartStage, is BalanceEvent.Reset -> state
    }
}

sealed interface BalanceVerdict {
    val kind: String

    data object NotReached : BalanceVerdict {
        override val kind = "not-reached"
    }

    data class StoppedBefore(val stoppedAtStage: Int) : BalanceVerdict {
        override val kind = "stopped-before"
    }

    data class Below(val heldS: Double) : BalanceVerdict {
        override val kind = "below"
    }

    data class Meets(val heldS: Double) : BalanceVerdict {
        override val kind = "meets"
    }
}

/** Compares the CDC reference stage (tandem) with the CDC statement. A test that stopped earlier never reached it. */
fun balanceVerdict(state: BalanceState, norms: BalanceNorms): BalanceVerdict {
    val result = state.results.firstOrNull { it.stage == norms.riskStage }
    val last = state.results.lastOrNull()
    if (result == null) {
        return if (state.finished && last != null) BalanceVerdict.StoppedBefore(last.stage) else BalanceVerdict.NotReached
    }
    return if (result.heldS < norms.riskHoldLessThanS) BalanceVerdict.Below(result.heldS) else BalanceVerdict.Meets(result.heldS)
}
