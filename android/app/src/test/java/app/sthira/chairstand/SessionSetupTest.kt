package app.sthira.chairstand

import app.sthira.engine.norms.NormSex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSetupTest {
    private val minimal = SetupForm(participantCode = " P07 ", ageBand = AgeBand.A70_74, sex = NormSex.FEMALE)

    @Test
    fun `a code, age band and sex are enough without a recording`() {
        val result = SessionSetup.validate(minimal) as SetupResult.Ok
        assertEquals("P07", result.session.participantCode)
        assertNull(result.session.recording)
    }

    @Test
    fun `any code of letters, digits, dash and underscore is accepted`() {
        listOf("A11", "P07", "p7", "W3-B12", "7", "ab_9", "KOL-2026-014", "x".repeat(SessionSetup.MAX_CODE)).forEach { code ->
            val result = SessionSetup.validate(minimal.copy(participantCode = code)) as SetupResult.Ok
            assertEquals(code, result.session.participantCode)
        }
    }

    @Test
    fun `a code that cannot be part of a file name is refused with the reason`() {
        listOf("A 11", "A/11", "..", "a:b", "A.11", "A\\11", "ab\ncd").forEach { code ->
            val result = SessionSetup.validate(minimal.copy(participantCode = code)) as SetupResult.Invalid
            assertTrue("'$code' should be refused", result.errors[SetupField.ParticipantCode]?.contains("letters, digits") == true)
        }
    }

    @Test
    fun `an empty code and a code over the length limit are refused`() {
        val empty = SessionSetup.validate(minimal.copy(participantCode = "  ")) as SetupResult.Invalid
        assertEquals("Enter a participant code", empty.errors[SetupField.ParticipantCode])
        val tooLong = SessionSetup.validate(minimal.copy(participantCode = "x".repeat(SessionSetup.MAX_CODE + 1))) as SetupResult.Invalid
        assertTrue(tooLong.errors[SetupField.ParticipantCode]?.contains("${SessionSetup.MAX_CODE}") == true)
    }

    @Test
    fun `missing sex and age band are reported`() {
        val result = SessionSetup.validate(SetupForm(participantCode = "P123")) as SetupResult.Invalid
        assertEquals(setOf(SetupField.AgeBand, SetupField.Sex), result.errors.keys)
    }

    @Test
    fun `saving a recording needs the pilot fields and consent`() {
        val result = SessionSetup.validate(minimal.copy(saveRecording = true, cameraHeightCm = "-3")) as SetupResult.Invalid
        assertTrue(SetupField.Consent in result.errors)
        assertTrue(SetupField.CameraHeight in result.errors)
        assertTrue(SetupField.Room in result.errors)
    }

    @Test
    fun `a full recording form maps bands above 80 to the schema band`() {
        val form = minimal.copy(
            ageBand = AgeBand.A85_89, saveRecording = true, clothing = Clothing.Saree, viewAngle = ViewAngle.Degree45,
            cameraHeightCm = "90", cameraDistanceCm = "250", room = " living room ", consentConfirmed = true,
        )
        val meta = (SessionSetup.validate(form) as SetupResult.Ok).session.recording
        assertEquals("80+", meta?.ageBand)
        assertEquals("living room", meta?.room)
        assertEquals(250.0, meta?.cameraDistanceCm ?: 0.0, 0.0)
    }

    @Test
    fun `blockers list what is missing in form order and clear when complete`() {
        assertEquals(
            listOf("a participant code", "an age band", "sex (or Not recorded)"),
            SessionSetup.blockers(SetupForm()),
        )
        assertEquals("To continue, add: an age band.", SessionSetup.blockerSentence(minimal.copy(ageBand = null)))
        assertTrue(SessionSetup.blockers(minimal).isEmpty())
        assertNull(SessionSetup.blockerSentence(minimal))
    }

    @Test
    fun `inline errors appear only for fields that hold something invalid`() {
        assertTrue(SessionSetup.inlineErrors(SetupForm()).isEmpty())
        val typed = SessionSetup.inlineErrors(SetupForm(participantCode = "A 11", saveRecording = true, cameraHeightCm = "abc"))
        assertEquals(setOf(SetupField.ParticipantCode, SetupField.CameraHeight), typed.keys)
    }
}
