package app.sthira.timers

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.sthira.engine.timers.BALANCE_START
import app.sthira.engine.timers.BalanceEvent
import app.sthira.engine.timers.BalanceNorms
import app.sthira.engine.timers.BalanceState
import app.sthira.engine.timers.TimerNorms
import app.sthira.engine.timers.TimerNormsResult
import app.sthira.engine.timers.TugNorms
import app.sthira.engine.timers.balanceReducer
import app.sthira.engine.timers.readTimerNorms
import app.sthira.R
import app.sthira.ui.common.UiText
import app.sthira.ui.common.uiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

private const val TICK_MS = 50L
private fun nowSeconds(): Double = SystemClock.elapsedRealtimeNanos() / 1_000_000_000.0

/** Reads the CDC balance and TUG values from the bundled norms.json; nothing is defaulted. Null if the file is missing. */
private fun loadTimerNorms(app: Application): TimerNormsResult? = try {
    readTimerNorms(app.assets.open("norms.json").bufferedReader().use { it.readText() })
} catch (e: IOException) {
    null
}

private fun TimerNormsResult?.normsOrNull(): TimerNorms? = (this as? TimerNormsResult.Ok)?.norms

/** Validation messages name JSON paths, so they stay as written (shown in a monospace list). */
private fun TimerNormsResult?.errorsOrNull(): List<UiText>? = when (this) {
    null -> listOf(uiText(R.string.timer_norms_missing))
    is TimerNormsResult.Failed -> errors.map { UiText.Raw(it) }
    is TimerNormsResult.Ok -> null
}

data class BalanceUiState(
    val norms: BalanceNorms? = null,
    val errors: List<UiText>? = null,
    val test: BalanceState = BALANCE_START,
    val nowT: Double = 0.0,
    /** True from the moment a stage is held until Next stage is pressed, so the result is seen first. */
    val reviewing: Boolean = false,
) {
    val running: Boolean get() = test.current?.startT != null
    val phase: BalancePhase get() = BalanceSteps.phase(test, reviewing)
    val heldS: Double
        get() {
            val start = test.current?.startT ?: return 0.0
            val limit = norms?.stageDurationS ?: return 0.0
            return (nowT - start).coerceIn(0.0, limit)
        }
}

sealed interface BalanceUiEvent {
    data object StartStage : BalanceUiEvent
    data object Lost : BalanceUiEvent
    data object CancelStage : BalanceUiEvent
    data object NextStage : BalanceUiEvent
    data object Reset : BalanceUiEvent
}

/** Applies an engine event; a newly recorded stage result opens the review step, Reset closes it. */
internal fun BalanceUiState.advance(event: BalanceEvent, t: Double, norms: BalanceNorms): BalanceUiState {
    val next = balanceReducer(test, event, norms)
    val reviewNow = when {
        event is BalanceEvent.Reset -> false
        next.results.size > test.results.size -> true
        else -> reviewing
    }
    return copy(test = next, nowT = t, reviewing = reviewNow)
}

/** CDC 4-stage balance, timed by the physio. The engine reducer decides every transition. */
class BalanceViewModel(app: Application) : AndroidViewModel(app) {
    private val _state: MutableStateFlow<BalanceUiState>
    val state: StateFlow<BalanceUiState>
    private var ticker: Job? = null

    init {
        val loaded = loadTimerNorms(app)
        _state = MutableStateFlow(BalanceUiState(norms = loaded.normsOrNull()?.balance, errors = loaded.errorsOrNull()))
        state = _state.asStateFlow()
    }

    fun onEvent(event: BalanceUiEvent) {
        val norms = _state.value.norms ?: return
        val t = nowSeconds()
        val engineEvent = when (event) {
            BalanceUiEvent.StartStage -> BalanceEvent.StartStage(t)
            BalanceUiEvent.Lost -> BalanceEvent.Lost(t)
            BalanceUiEvent.CancelStage -> BalanceEvent.CancelStage
            BalanceUiEvent.Reset -> BalanceEvent.Reset
            BalanceUiEvent.NextStage -> {
                _state.update { it.copy(reviewing = false) }
                return
            }
        }
        _state.update { it.advance(engineEvent, t, norms) }
        syncTicker(norms)
    }

    private fun syncTicker(norms: BalanceNorms) {
        if (!_state.value.running) {
            ticker?.cancel()
            ticker = null
            return
        }
        if (ticker != null) return
        ticker = viewModelScope.launch {
            while (isActive && _state.value.running) {
                delay(TICK_MS)
                val t = nowSeconds()
                _state.update { it.advance(BalanceEvent.Tick(t), t, norms) }
            }
            ticker = null
        }
    }
}

sealed interface TugRun {
    data object Idle : TugRun
    data class Running(val startT: Double) : TugRun
    data class Done(val seconds: Double) : TugRun
}

data class TugUiState(
    val norms: TugNorms? = null,
    val errors: List<UiText>? = null,
    val run: TugRun = TugRun.Idle,
    val nowT: Double = 0.0,
    val usedAid: Boolean = false,
) {
    val shownSeconds: Double
        get() = when (val r = run) {
            TugRun.Idle -> 0.0
            is TugRun.Running -> maxOf(nowT - r.startT, 0.0)
            is TugRun.Done -> r.seconds
        }
}

sealed interface TugUiEvent {
    data object Go : TugUiEvent
    data object SatDown : TugUiEvent
    data class UsedAid(val used: Boolean) : TugUiEvent
}

/** Timed Up and Go, timed by the physio from "Go" until the person sits back down. */
class TugViewModel(app: Application) : AndroidViewModel(app) {
    private val _state: MutableStateFlow<TugUiState>
    val state: StateFlow<TugUiState>
    private var ticker: Job? = null

    init {
        val loaded = loadTimerNorms(app)
        _state = MutableStateFlow(TugUiState(norms = loaded.normsOrNull()?.tug, errors = loaded.errorsOrNull()))
        state = _state.asStateFlow()
    }

    fun onEvent(event: TugUiEvent) {
        val t = nowSeconds()
        when (event) {
            TugUiEvent.Go -> if (_state.value.run !is TugRun.Running) {
                _state.update { it.copy(run = TugRun.Running(t), nowT = t) }
                startTicker()
            }
            TugUiEvent.SatDown -> {
                ticker?.cancel()
                _state.update { s -> (s.run as? TugRun.Running)?.let { s.copy(run = TugRun.Done(t - it.startT), nowT = t) } ?: s }
            }
            is TugUiEvent.UsedAid -> _state.update { it.copy(usedAid = event.used) }
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(TICK_MS)
                _state.update { it.copy(nowT = nowSeconds()) }
            }
        }
    }
}
