package app.sthira.chairstand

import app.sthira.R
import app.sthira.engine.norms.NormSex
import app.sthira.ui.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSetupTest {
    private val minimal = SetupForm(participantCode = " P07 ", ageBand = AgeBand.A70_74, sex = NormSex.FEMALE)

    @Test
    fun `a code, age band and sex are enough`() {
        val result = SessionSetup.validate(minimal) as SetupResult.Ok
        assertEquals(Session("P07", AgeBand.A70_74, NormSex.FEMALE), result.session)
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
                assertTrue(SessionSetup.validate(minimal.copy(ageBand = band, sex = sex)) is SetupResult.Ok)
            }
        }
    }

    @Test
    fun `any code of letters, digits, dash and underscore is accepted`() {
        listOf("A11", "P07", "p7", "W3-B12", "7", "ab_9", "KOL-2026-014", "x".repeat(SessionSetup.MAX_CODE)).forEach { code ->
            val result = SessionSetup.validate(minimal.copy(participantCode = code)) as SetupResult.Ok
            assertEquals(code, result.session.participantCode)
        }
    }

    @Test
    fun `a code with other characters is refused with the reason`() {
        listOf("A 11", "A/11", "..", "a:b", "A.11", "A\\11", "ab\ncd").forEach { code ->
            val result = SessionSetup.validate(minimal.copy(participantCode = code)) as SetupResult.Invalid
            assertEquals("'$code'", UiText.Res(R.string.cs_error_code_chars), result.errors[SetupField.ParticipantCode])
        }
    }

    @Test
    fun `an empty code and a code over the length limit are refused`() {
        val empty = SessionSetup.validate(minimal.copy(participantCode = "  ")) as SetupResult.Invalid
        assertEquals(UiText.Res(R.string.cs_error_code_empty), empty.errors[SetupField.ParticipantCode])
        val tooLong = SessionSetup.validate(minimal.copy(participantCode = "x".repeat(SessionSetup.MAX_CODE + 1))) as SetupResult.Invalid
        assertEquals(UiText.Res(R.string.cs_error_code_too_long, listOf(SessionSetup.MAX_CODE)), tooLong.errors[SetupField.ParticipantCode])
    }

    @Test
    fun `missing sex and age band are reported`() {
        val result = SessionSetup.validate(SetupForm(participantCode = "P123")) as SetupResult.Invalid
        assertEquals(UiText.Res(R.string.cs_error_age_band), result.errors[SetupField.AgeBand])
        assertEquals(UiText.Res(R.string.cs_error_sex), result.errors[SetupField.Sex])
        assertEquals(setOf(SetupField.AgeBand, SetupField.Sex), result.errors.keys)
    }

    @Test
    fun `blockers list what is missing in form order and clear when complete`() {
        assertEquals(listOf(SetupField.ParticipantCode, SetupField.AgeBand, SetupField.Sex), SessionSetup.blockers(SetupForm()))
        assertEquals(listOf(SetupField.AgeBand), SessionSetup.blockers(minimal.copy(ageBand = null)))
        assertTrue(SessionSetup.blockers(minimal).isEmpty())
        assertEquals(
            listOf(R.string.cs_blocker_code, R.string.cs_blocker_age, R.string.cs_blocker_sex),
            SetupField.entries.map { it.blocker },
        )
    }

    @Test
    fun `inline errors appear only for a code that holds something invalid`() {
        assertTrue(SessionSetup.inlineErrors(SetupForm()).isEmpty())
        assertTrue(SessionSetup.inlineErrors(SetupForm(participantCode = "A11")).isEmpty())
        assertEquals(setOf(SetupField.ParticipantCode), SessionSetup.inlineErrors(SetupForm(participantCode = "A 11")).keys)
    }
}
