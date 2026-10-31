package app.sthira.chairstand

import app.sthira.R
import app.sthira.engine.norms.NormSex
import app.sthira.ui.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSetupTest {
    private val complete = SetupForm(ageBand = AgeBand.A70_74, sex = NormSex.FEMALE)

    @Test
    fun `an age band and sex are enough, with no participant code`() {
        val result = SessionSetup.validate(complete) as SetupResult.Ok
        assertEquals(Session(AgeBand.A70_74, NormSex.FEMALE), result.session)
    }

    @Test
    fun `the age bands are under 60, the CDC 5-year bands to 79, and 80 plus`() {
        assertEquals(listOf(null, "60-64", "65-69", "70-74", "75-79", "80+"), AgeBand.entries.map { it.normBand })
        assertEquals(R.string.cs_age_under_60, AgeBand.Under60.label)
        assertEquals(R.string.cs_age_80_plus, AgeBand.A80Plus.label)
    }

    @Test
    fun `every age band and sex can be chosen`() {
        AgeBand.entries.forEach { band ->
            NormSex.entries.forEach { sex ->
                assertTrue(SessionSetup.validate(complete.copy(ageBand = band, sex = sex)) is SetupResult.Ok)
            }
        }
    }

    @Test
    fun `missing sex and age band are reported`() {
        val result = SessionSetup.validate(SetupForm()) as SetupResult.Invalid
        assertEquals(UiText.Res(R.string.cs_error_age_band), result.errors[SetupField.AgeBand])
        assertEquals(UiText.Res(R.string.cs_error_sex), result.errors[SetupField.Sex])
        assertEquals(setOf(SetupField.AgeBand, SetupField.Sex), result.errors.keys)
    }

    @Test
    fun `blockers list what is missing in form order and clear when complete`() {
        assertEquals(listOf(SetupField.AgeBand, SetupField.Sex), SessionSetup.blockers(SetupForm()))
        assertEquals(listOf(SetupField.AgeBand), SessionSetup.blockers(complete.copy(ageBand = null)))
        assertEquals(listOf(SetupField.Sex), SessionSetup.blockers(complete.copy(sex = null)))
        assertTrue(SessionSetup.blockers(complete).isEmpty())
        assertEquals(listOf(R.string.cs_blocker_age, R.string.cs_blocker_sex), SetupField.entries.map { it.blocker })
    }

    @Test
    fun `the old preview constructor ignores the code`() {
        assertEquals(complete, SetupForm("A11", AgeBand.A70_74, NormSex.FEMALE))
    }

    @Test
    fun `the sex labels are woman, man and prefer not to say`() {
        assertEquals(R.string.cs_sex_female, NormSex.FEMALE.labelRes)
        assertEquals(R.string.cs_sex_male, NormSex.MALE.labelRes)
        assertEquals(R.string.cs_sex_not_recorded, NormSex.NOT_RECORDED.labelRes)
    }
}
