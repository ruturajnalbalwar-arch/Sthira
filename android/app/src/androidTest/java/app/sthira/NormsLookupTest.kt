package app.sthira

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sthira.chairstand.AgeBand
import app.sthira.chairstand.ChairStandConfigLoader
import app.sthira.chairstand.ConfigLoad
import app.sthira.chairstand.Session
import app.sthira.engine.norms.NormLookup
import app.sthira.engine.norms.NormSex
import app.sthira.engine.norms.NormsParseResult
import app.sthira.engine.norms.lookupChairStandNorm
import app.sthira.engine.norms.parseChairStandNorms
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every age band x sex the form offers, checked against the bundled norms.json read independently
 * (org.json), and against the screen's own config path.
 */
@RunWith(AndroidJUnit4::class)
class NormsLookupTest {
    private data class Row(val sex: String, val band: String, val cutoff: Int)

    private val rows: List<Row> by lazy {
        val entries = JSONObject(asset("norms.json")).getJSONObject("chairStand30s").getJSONArray("entries")
        List(entries.length()) { i ->
            val e = entries.getJSONObject(i)
            Row(e.getString("sex"), "${e.getInt("ageMin")}-${e.getInt("ageMax")}", e.getInt("belowAverageIfLessThan"))
        }
    }

    @Test
    fun everyBandAndSexMatchesTheCdcTable() {
        val norms = (parseChairStandNorms(asset("norms.json")) as NormsParseResult.Ok).norms
        val config = (ChairStandConfigLoader.load(asset("params.json"), asset("norms.json")) as ConfigLoad.Ok).config
        var available = 0
        AgeBand.entries.forEach { band ->
            NormSex.entries.forEach { sex ->
                val lookup = lookupChairStandNorm(norms, band.label, sex)
                assertEquals(lookup, config.normLookup(Session("P07", band, sex, null)))
                val row = rows.firstOrNull { it.sex == sex.wire && it.band == band.label }
                val label = "${band.label}/${sex.label}"
                when {
                    sex == NormSex.NOT_RECORDED -> assertEquals(
                        label,
                        NormLookup.NotAvailable("The CDC table gives separate values for women and men, and sex was not recorded."),
                        lookup,
                    )
                    row == null -> assertEquals(label, NormLookup.NotAvailable("The CDC table has no value for age ${band.label}."), lookup)
                    else -> {
                        available++
                        val entry = (lookup as NormLookup.Available).entry
                        assertEquals(label, row.cutoff, entry.belowAverageIfLessThan)
                        assertEquals(label, row.band, "${entry.ageMin}-${entry.ageMax}")
                    }
                }
            }
        }
        // 60-64 .. 90-94 (7 bands) x Female/Male.
        assertEquals(14, available)
        listOf("18-29", "30-44", "45-59", "95+").forEach { b -> assertTrue(rows.none { it.band == b }) }
    }
}
