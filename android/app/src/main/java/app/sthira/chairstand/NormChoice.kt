package app.sthira.chairstand

import app.sthira.R
import app.sthira.engine.norms.ChairStandNormEntry
import app.sthira.engine.norms.ChairStandNorms
import app.sthira.engine.norms.NormLookup
import app.sthira.engine.norms.NormSex
import app.sthira.engine.norms.NormsParseResult
import app.sthira.engine.norms.lookupChairStandNorm
import app.sthira.engine.norms.parseAgeBand
import app.sthira.ui.common.UiText
import app.sthira.ui.common.uiText

/** Which CDC row (from norms.json) a session is compared with, or why there is none. */
sealed interface NormChoice {
    /** [note] is set for the open band (80+): it names the CDC row used and what CDC lists for older ages. */
    data class Row(val entry: ChairStandNormEntry, val note: UiText?) : NormChoice
    data class None(val reason: UiText) : NormChoice
}

object NormChooser {
    fun choose(norms: NormsParseResult, band: AgeBand, sex: NormSex): NormChoice {
        val table = (norms as? NormsParseResult.Ok)?.norms ?: return NormChoice.None(uiText(R.string.cs_no_cdc_file))
        val normBand = band.normBand ?: return NormChoice.None(uiText(R.string.cs_no_cdc_under_60))
        if (sex == NormSex.NOT_RECORDED) return NormChoice.None(uiText(R.string.cs_no_cdc_sex))
        val noValue = NormChoice.None(uiText(R.string.cs_no_cdc_value, uiText(band.label)))
        val range = parseAgeBand(normBand) ?: return noValue
        if (range.max == null) return openBand(table, range.min, sex) ?: noValue
        return when (val lookup = lookupChairStandNorm(table, normBand, sex)) {
            is NormLookup.Available -> NormChoice.Row(lookup.entry, null)
            is NormLookup.NotAvailable -> noValue
        }
    }

    /**
     * An open band such as 80+ spans several CDC rows with their own values. It is compared with the
     * youngest of them, and the note says which row that is and how the older rows differ (read from
     * the table, never assumed).
     */
    private fun openBand(table: ChairStandNorms, minAge: Double, sex: NormSex): NormChoice? {
        val rows = table.entries.filter { it.sex == sex && it.ageMin >= minAge }.sortedBy { it.ageMin }
        val first = rows.firstOrNull() ?: return null
        val older = rows.drop(1)
        val next = older.firstOrNull()
            ?: return NormChoice.Row(first, uiText(R.string.cs_cdc_open_band_only, first.ageMin, first.ageMax))
        val lower = older.all { it.belowAverageIfLessThan < first.belowAverageIfLessThan }
        val id = if (lower) R.string.cs_cdc_open_band_lower else R.string.cs_cdc_open_band_different
        return NormChoice.Row(first, uiText(id, first.ageMin, first.ageMax, next.ageMin))
    }
}
