package app.sthira.chairstand

import app.sthira.engine.CounterResult
import app.sthira.engine.norms.chairStandVerdict

/** Pure mapping from the live count to what the screen shows. Wording follows the web app. */
object ChairStandResults {
    const val NO_COUNT_NOT_TUNED = "No count: the counter is not tuned yet. This run was a rehearsal of the setup."
    const val NO_COUNT_INVALID = "No count: the counter settings in params.json are invalid."
    const val NO_COUNT_CALIBRATION = "No count: calibration did not pass, so the stands were not counted. Run again."
    const val NO_COUNT_CONTEXT = "No count: the counter could not start."
    const val NO_COUNT_BEFORE_GO = "No count: the test was stopped before Go."

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
        is CountSetup.Finished ->
            ResultView(setup.summary, chairStandVerdict(setup.summary, config.normLookup(session)), null)
        CountSetup.NotTuned ->
            ResultView(null, null, if (config.counter is CounterResult.Invalid) NO_COUNT_INVALID else NO_COUNT_NOT_TUNED)
        is CountSetup.CalibrationFailed -> ResultView(null, null, NO_COUNT_CALIBRATION)
        is CountSetup.ContextFailed -> ResultView(null, null, NO_COUNT_CONTEXT)
        CountSetup.Waiting, is CountSetup.Starting, is CountSetup.Counting -> ResultView(null, null, NO_COUNT_BEFORE_GO)
    }
}
