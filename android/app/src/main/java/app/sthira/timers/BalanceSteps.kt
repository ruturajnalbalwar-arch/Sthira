package app.sthira.timers

import androidx.annotation.StringRes
import app.sthira.R
import app.sthira.engine.timers.BalanceNorms
import app.sthira.engine.timers.BalanceStage
import app.sthira.engine.timers.BalanceState
import app.sthira.engine.timers.BalanceVerdict
import app.sthira.engine.timers.StageResult
import app.sthira.engine.timers.TugNorms
import app.sthira.engine.timers.TugVerdict
import app.sthira.engine.timers.tugVerdict
import app.sthira.ui.common.UiText
import app.sthira.ui.common.uiText

/** One dot of the stage dots; [spoken] is what TalkBack says for its state. */
enum class StepStatus(@StringRes val spoken: Int) {
    Held(R.string.timer_step_held),
    Lost(R.string.timer_step_lost),
    Current(R.string.timer_step_current),
    Upcoming(R.string.timer_step_upcoming),
    /** The test stopped at an earlier stage, so this one is never run (CDC: stop at the first failure). */
    Skipped(R.string.timer_step_skipped),
}

/**
 * What the balance screen shows, one step at a time: [Ready] (stage set up, Start), [Running] (timer,
 * Lost balance), [StageDone] (held, Next stage) and [Finished] (results, Start over).
 */
enum class BalancePhase { Ready, Running, StageDone, Finished }

/** The foot picture drawn for a stage. */
enum class FootPosition { SideBySide, SemiTandem, Tandem, OneFoot }

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

    /** [reviewing] is true from the moment a stage is held until the physio presses Next stage. */
    fun phase(state: BalanceState, reviewing: Boolean): BalancePhase {
        val current = state.current ?: return BalancePhase.Finished
        return when {
            current.startT != null -> BalancePhase.Running
            reviewing -> BalancePhase.StageDone
            else -> BalancePhase.Ready
        }
    }

    private val FEET = mapOf(
        1 to FootPosition.SideBySide, 2 to FootPosition.SemiTandem, 3 to FootPosition.Tandem, 4 to FootPosition.OneFoot,
    )

    /** The picture for a CDC stage number, or null for a stage the app has no picture for. */
    fun footPosition(stage: BalanceStage): FootPosition? = FEET[stage.stage]
}

/**
 * Translated text for the timer screens, built outside composables so it can be unit tested.
 * Stage names and instructions are keyed by the stage number in norms.json; a stage the app has
 * no translation for falls back to the English text from norms.json.
 */
object TimerTexts {
    private val STAGE_NAMES = mapOf(
        1 to R.string.timer_stage_name_1, 2 to R.string.timer_stage_name_2,
        3 to R.string.timer_stage_name_3, 4 to R.string.timer_stage_name_4,
    )
    private val STAGE_DO = mapOf(
        1 to R.string.timer_stage_do_1, 2 to R.string.timer_stage_do_2,
        3 to R.string.timer_stage_do_3, 4 to R.string.timer_stage_do_4,
    )

    fun seconds(value: Double): UiText = uiText(R.string.timer_seconds_value, value)

    fun stageName(stage: BalanceStage): UiText = STAGE_NAMES[stage.stage]?.let { uiText(it) } ?: UiText.Raw(stage.name)

    /** The one short line shown and spoken for a stage. */
    fun stageInstruction(stage: BalanceStage): UiText = STAGE_DO[stage.stage]?.let { uiText(it) } ?: UiText.Raw(stage.cdcText)

    fun stageOf(stage: BalanceStage, stageCount: Int): UiText = uiText(R.string.timer_stage_of, stage.stage, stageCount)

    /** The passed or stopped line shown after a stage. */
    fun stageOutcome(result: StageResult): UiText {
        val id = if (result.completed) R.string.timer_stage_passed else R.string.timer_stage_stopped
        return uiText(id, result.stage, seconds(result.heldS))
    }

    /** The result banner title, or null before the reference stage is reached. */
    fun balanceVerdictTitle(verdict: BalanceVerdict, norms: BalanceNorms): UiText? {
        val limit = seconds(norms.riskHoldLessThanS)
        return when (verdict) {
            BalanceVerdict.NotReached -> null
            is BalanceVerdict.StoppedBefore -> uiText(R.string.timer_balance_stopped_before, verdict.stoppedAtStage, limit)
            is BalanceVerdict.Below -> uiText(R.string.timer_balance_below, seconds(verdict.heldS), limit)
            is BalanceVerdict.Meets -> uiText(R.string.timer_balance_meets, seconds(verdict.heldS), limit)
        }
    }

    fun tugResultTitle(seconds: Double, norms: TugNorms): UiText {
        val id = if (tugVerdict(seconds, norms) == TugVerdict.BELOW) R.string.timer_tug_under else R.string.timer_tug_at_or_above
        return uiText(id, seconds(seconds), seconds(norms.atRiskIfAtLeastS))
    }
}
