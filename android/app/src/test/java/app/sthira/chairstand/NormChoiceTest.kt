package app.sthira.chairstand

import app.sthira.R
import app.sthira.engine.norms.NormSex
import app.sthira.engine.norms.NormsParseResult
import app.sthira.engine.norms.parseChairStandNorms
import app.sthira.ui.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Checked against the real norms.json (the build passes its path); no CDC value is written here. */
class NormChoiceTest {
    private val norms = parseChairStandNorms(File(requireNotNull(System.getProperty("sthira.norms"))).readText())
    private val table = (norms as NormsParseResult.Ok).norms

    private fun row(band: AgeBand, sex: NormSex) = NormChooser.choose(norms, band, sex) as NormChoice.Row

    @Test
    fun `each closed band uses its own CDC row`() {
        listOf(AgeBand.A60_64, AgeBand.A65_69, AgeBand.A70_74, AgeBand.A75_79).forEach { band ->
            listOf(NormSex.FEMALE, NormSex.MALE).forEach { sex ->
                val r = row(band, sex)
                assertEquals("$band/$sex", band.normBand, "${r.entry.ageMin}-${r.entry.ageMax}")
                assertEquals(sex, r.entry.sex)
                assertEquals(null, r.note)
            }
        }
    }

    @Test
    fun `80 plus uses the youngest CDC row from 80 and says so`() {
        listOf(NormSex.FEMALE, NormSex.MALE).forEach { sex ->
            val rows = table.entries.filter { it.sex == sex && it.ageMin >= 80 }.sortedBy { it.ageMin }
            val first = rows.first()
            val r = row(AgeBand.A80Plus, sex)
            assertEquals(first, r.entry)
            val lower = rows.drop(1).all { it.belowAverageIfLessThan < first.belowAverageIfLessThan }
            val id = if (lower) R.string.cs_cdc_open_band_lower else R.string.cs_cdc_open_band_different
            assertEquals(UiText.Res(id, listOf(first.ageMin, first.ageMax, rows[1].ageMin)), r.note)
        }
    }

    @Test
    fun `under 60 and not recorded sex have no comparison, with the reason`() {
        NormSex.entries.forEach { sex ->
            assertEquals(NormChoice.None(UiText.Res(R.string.cs_no_cdc_under_60)), NormChooser.choose(norms, AgeBand.Under60, sex))
        }
        AgeBand.entries.filter { it != AgeBand.Under60 }.forEach { band ->
            assertEquals(NormChoice.None(UiText.Res(R.string.cs_no_cdc_sex)), NormChooser.choose(norms, band, NormSex.NOT_RECORDED))
        }
    }

    @Test
    fun `an unreadable norms file gives no comparison`() {
        val failed = NormsParseResult.Failed(listOf("bad"))
        assertTrue(NormChooser.choose(failed, AgeBand.A70_74, NormSex.FEMALE) is NormChoice.None)
    }
}
