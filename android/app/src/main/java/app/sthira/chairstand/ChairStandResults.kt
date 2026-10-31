package app.sthira.chairstand

import app.sthira.R
import app.sthira.engine.ChairStandSummary
import app.sthira.engine.CounterResult
import app.sthira.engine.norms.NormLookup
import app.sthira.engine.norms.chairStandVerdict
import app.sthira.ui.common.UiText
import app.sthira.ui.common.uiText

/** Pure mapping from the live count to what the screen shows. Wording follows the web app. */
object ChairStandResults {
    fun countView(live: LiveCountState): CountView = when (val s = live.setup) {
        CountSetup.Waiting -> CountView.Waiting
        is CountSetup.Starting -> CountView.Checking
        CountSetup.NotTuned -> CountView.NotTuned
        is CountSetup.CalibrationFailed -> CountView.CalibrationFailed(s.reasons)
        is CountSetup.ContextFailed -> CountView.ContextFailed(s.errors)
        is CountSetup.Counting -> LiveCount.liveSummary(live)?.let { CountView.Counting(it) } ?: CountView.Checking
        is CountSetup.Finished -> CountView.Finished(s.summary)
    }

    fun resultFor(setup: CountSetup, config: ChairStandConfig, session: Session): ResultView = when (setup) {
        is CountSetup.Finished -> finished(setup.summary, config.normChoice(session))
        CountSetup.NotTuned -> ResultView(
            null, null,
            uiText(if (config.counter is CounterResult.Invalid) R.string.cs_no_count_invalid else R.string.cs_no_count_not_tuned),
        )
        is CountSetup.CalibrationFailed -> ResultView(null, null, uiText(R.string.cs_no_count_calibration))
        is CountSetup.ContextFailed -> ResultView(null, null, uiText(R.string.cs_no_count_context))
        CountSetup.Waiting, is CountSetup.Starting, is CountSetup.Counting -> ResultView(null, null, uiText(R.string.cs_no_count_before_go))
    }

    /** The engine decides below / at-or-above from the CDC row; the words come from the app's strings. */
    private fun finished(summary: ChairStandSummary, choice: NormChoice): ResultView {
        val lookup = when (choice) {
            is NormChoice.Row -> NormLookup.Available(choice.entry)
            // The engine's English reason is not shown; notComparedReasons carries the translated one.
            is NormChoice.None -> NormLookup.NotAvailable("")
        }
        return ResultView(summary, chairStandVerdict(summary, lookup), null, choice, notComparedReasons(summary, choice))
    }

    /** Same rules as the engine's verdict, in order: incomplete, unreliable, too slow, no CDC row. */
    fun notComparedReasons(summary: ChairStandSummary, choice: NormChoice): List<UiText> = listOfNotNull(
        if (summary.windowComplete) null else uiText(R.string.cs_reason_stopped_early),
        if (summary.unreliable) uiText(R.string.cs_reason_unreliable) else null,
        if (summary.lowFps) uiText(R.string.cs_reason_low_fps) else null,
        (choice as? NormChoice.None)?.reason,
    )
}
