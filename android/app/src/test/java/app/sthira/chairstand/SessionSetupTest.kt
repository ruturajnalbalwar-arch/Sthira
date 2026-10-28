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
    fun `names are rejected as participant codes`() {
        val result = SessionSetup.validate(minimal.copy(participantCode = "Asha")) as SetupResult.Invalid
        assertTrue(SetupField.ParticipantCode in result.errors)
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
            listOf("a participant code like P07", "an age band", "sex (or Not recorded)"),
            SessionSetup.blockers(SetupForm()),
        )
        assertEquals("To continue, add: an age band.", SessionSetup.blockerSentence(minimal.copy(ageBand = null)))
        assertTrue(SessionSetup.blockers(minimal).isEmpty())
        assertNull(SessionSetup.blockerSentence(minimal))
    }

    @Test
    fun `inline errors appear only for fields that hold something invalid`() {
        assertTrue(SessionSetup.inlineErrors(SetupForm()).isEmpty())
        val typed = SessionSetup.inlineErrors(SetupForm(participantCode = "Asha", saveRecording = true, cameraHeightCm = "abc"))
        assertEquals(setOf(SetupField.ParticipantCode, SetupField.CameraHeight), typed.keys)
    }
}
