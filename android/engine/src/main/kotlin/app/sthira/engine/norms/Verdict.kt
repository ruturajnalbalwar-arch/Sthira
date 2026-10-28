package app.sthira.engine.norms

import app.sthira.engine.ChairStandSummary

/** Mirror of web/src/lib/count/verdict.ts. */
const val CDC_CITATION = "CDC STEADI Assessment: 30-Second Chair Stand (2017)"

/** Why a finished run is not compared with the CDC table. Each one is shown to the physiotherapist. */
object NoComparisonText {
    const val STOPPED_EARLY = "Test stopped early: not a CDC score. The CDC score needs the full timed window."
    const val UNRELIABLE = "Count not reliable: too many stands were unclear on camera. Repeat the test."
    const val LOW_FPS = "The camera ran too slowly for a dependable count. Close other apps and repeat the test."
}

sealed interface Verdict {
    /** cutoff is the CDC value: fewer full stands than this is "below average". */
    data class Compared(val full: Int, val belowAverage: Boolean, val cutoff: Int, val sentence: String) : Verdict
    data class NotCompared(val reasons: List<String>) : Verdict
}

private fun sexWord(sex: NormSex): String = when (sex) {
    NormSex.FEMALE -> "women"
    NormSex.MALE -> "men"
    NormSex.NOT_RECORDED -> "people"
}

private fun comparisonSentence(full: Int, entry: ChairStandNormEntry, below: Boolean): String {
    val group = "${sexWord(entry.sex)} aged ${entry.ageMin}-${entry.ageMax}"
    val position = if (below) "Below the CDC average" else "At or above the CDC average"
    return "$position for $group: the CDC sheet marks fewer than ${entry.belowAverageIfLessThan} stands " +
        "as below average; this run scored $full."
}

/**
 * Puts a finished count next to the CDC reference value. It never interprets the score further:
 * a run that is incomplete, unreliable or too slow is not compared at all.
 */
fun chairStandVerdict(summary: ChairStandSummary, lookup: NormLookup): Verdict {
    val reasons = listOfNotNull(
        if (summary.windowComplete) null else NoComparisonText.STOPPED_EARLY,
        if (summary.unreliable) NoComparisonText.UNRELIABLE else null,
        if (summary.lowFps) NoComparisonText.LOW_FPS else null,
        (lookup as? NormLookup.NotAvailable)?.let { "No CDC comparison: ${it.reason}" },
    )
    if (reasons.isNotEmpty() || lookup !is NormLookup.Available) return Verdict.NotCompared(reasons)
    val cutoff = lookup.entry.belowAverageIfLessThan
    val belowAverage = summary.full < cutoff
    return Verdict.Compared(summary.full, belowAverage, cutoff, comparisonSentence(summary.full, lookup.entry, belowAverage))
}
