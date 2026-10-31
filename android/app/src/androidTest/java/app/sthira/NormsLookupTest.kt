package app.sthira

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sthira.chairstand.AgeBand
import app.sthira.chairstand.ChairStandConfigLoader
import app.sthira.chairstand.ConfigLoad
import app.sthira.chairstand.NormChoice
import app.sthira.chairstand.Session
import app.sthira.engine.norms.NormSex
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every age band x sex the form offers, checked against the bundled norms.json read independently
 * (org.json), through the screen's own config path.
 */
@RunWith(AndroidJUnit4::class)
class NormsLookupTest {
    private data class Row(val sex: String, val ageMin: Int, val ageMax: Int, val cutoff: Int)

    private val rows: List<Row> by lazy {
        val entries = JSONObject(asset("norms.json")).getJSONObject("chairStand30s").getJSONArray("entries")
        List(entries.length()) { i ->
            val e = entries.getJSONObject(i)
            Row(e.getString("sex"), e.getInt("ageMin"), e.getInt("ageMax"), e.getInt("belowAverageIfLessThan"))
        }
    }

    @Test
    fun everyBandAndSexMatchesTheCdcTable() {
        val config = (ChairStandConfigLoader.load(asset("params.json"), asset("norms.json")) as ConfigLoad.Ok).config
        var available = 0
        AgeBand.entries.forEach { band ->
            NormSex.entries.forEach { sex ->
                val choice = config.normChoice(Session(band, sex))
                val label = "$band/$sex"
                val normBand = band.normBand
                if (normBand == null || sex == NormSex.NOT_RECORDED) {
                    assertTrue(label, choice is NormChoice.None)
                    return@forEach
                }
                available++
                val r = choice as NormChoice.Row
                val sexRows = rows.filter { it.sex == sex.wire }
                val expected = if (normBand.endsWith("+")) {
                    // The open band uses the youngest CDC row at or above its start, and explains that.
                    val start = normBand.removeSuffix("+").toInt()
                    sexRows.filter { it.ageMin >= start }.minByOrNull { it.ageMin }
                } else {
                    sexRows.firstOrNull { "${it.ageMin}-${it.ageMax}" == normBand }
                }
                requireNotNull(expected) { label }
                assertEquals(label, expected.cutoff, r.entry.belowAverageIfLessThan)
                assertEquals(label, expected.ageMin, r.entry.ageMin)
                assertEquals(label, expected.ageMax, r.entry.ageMax)
                if (normBand.endsWith("+")) {
                    val note = r.note?.resolve(targetContext).orEmpty()
                    assertTrue("$label: $note", note.contains("${expected.ageMin}-${expected.ageMax}"))
                } else {
                    assertNull(label, r.note)
                }
            }
        }
        // 60-64 .. 75-79 and 80+ (5 bands) x Female/Male.
        assertEquals(10, available)
    }
}
