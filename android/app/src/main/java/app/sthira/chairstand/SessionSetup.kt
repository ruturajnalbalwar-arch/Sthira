package app.sthira.chairstand

import app.sthira.engine.norms.NormSex

/**
 * Age bands offered on the chair-stand form. Adults only (18+, as DPDP requires consent from the
 * person themselves). 60-94 are the CDC 5-year bands; the others have no CDC chair-stand value.
 * [recordingBand] is the band written to a sthira.recording.v1 file (whose enum ends at "80+").
 */
enum class AgeBand(val label: String, val recordingBand: String) {
    A18_29("18-29", "18-29"),
    A30_44("30-44", "30-44"),
    A45_59("45-59", "45-59"),
    A60_64("60-64", "60-64"),
    A65_69("65-69", "65-69"),
    A70_74("70-74", "70-74"),
    A75_79("75-79", "75-79"),
    A80_84("80-84", "80+"),
    A85_89("85-89", "80+"),
    A90_94("90-94", "80+"),
    A95_PLUS("95+", "80+"),
}

enum class Clothing(val wire: String, val label: String) {
    Saree("saree", "Saree"), Dhoti("dhoti", "Dhoti"), Lungi("lungi", "Lungi"),
    SalwarKameez("salwar-kameez", "Salwar kameez"), KurtaPyjama("kurta-pyjama", "Kurta pyjama"),
    TrackPants("track-pants", "Track pants"), Other("other", "Other"),
}

enum class ViewAngle(val wire: String, val label: String) {
    Degree45("45-degree", "45 degrees to the front (preferred)"),
    Side("side", "Side-on"),
    Front("front", "Front-on"),
}

/** What the form holds before validation. Numbers stay text until validated. */
data class SetupForm(
    val participantCode: String = "",
    val ageBand: AgeBand? = null,
    val sex: NormSex? = null,
    val saveRecording: Boolean = false,
    val clothing: Clothing? = null,
    val viewAngle: ViewAngle? = null,
    val cameraHeightCm: String = "",
    val cameraDistanceCm: String = "",
    val room: String = "",
    val notes: String = "",
    val consentConfirmed: Boolean = false,
)

enum class SetupField { ParticipantCode, AgeBand, Sex, Clothing, ViewAngle, CameraHeight, CameraDistance, Room, Notes, Consent }

/** Metadata for the optional recording file; matches sessionMetaSchema in web/src/lib/record/schema.ts. */
data class RecordingMeta(
    val participantCode: String,
    val ageBand: String,
    val clothing: String,
    val viewAngle: String,
    val cameraHeightCm: Double,
    val cameraDistanceCm: Double,
    val room: String,
    val notes: String,
)

data class Session(val participantCode: String, val ageBand: AgeBand, val sex: NormSex, val recording: RecordingMeta?)

sealed interface SetupResult {
    data class Ok(val session: Session) : SetupResult
    data class Invalid(val errors: Map<SetupField, String>) : SetupResult
}

object SessionSetup {
    /** The code becomes part of the saved file name, so only characters that are safe there are allowed. */
    private val CODE_CHARS = Regex("^[A-Za-z0-9_-]+$")

    /** Same limit as the ids in the labelling tool. */
    const val MAX_CODE = 40
    const val MAX_ROOM = 80
    const val MAX_NOTES = 500

    private fun codeError(code: String): String? = when {
        code.isEmpty() -> "Enter a participant code"
        code.length > MAX_CODE -> "Use at most $MAX_CODE characters"
        !CODE_CHARS.matches(code) -> "Use letters, digits, - or _ only (it is part of the file name)"
        else -> null
    }

    fun validate(form: SetupForm): SetupResult {
        val code = form.participantCode.trim()
        val errors = buildMap {
            codeError(code)?.let { put(SetupField.ParticipantCode, it) }
            if (form.ageBand == null) put(SetupField.AgeBand, "Choose an age band")
            if (form.sex == null) put(SetupField.Sex, "Choose one, or Not recorded")
            if (form.saveRecording) putAll(recordingErrors(form))
        }
        val band = form.ageBand
        val sex = form.sex
        if (errors.isNotEmpty() || band == null || sex == null) return SetupResult.Invalid(errors)
        return SetupResult.Ok(Session(code, band, sex, if (form.saveRecording) recordingMeta(code, band, form) else null))
    }

    /** Short names of what is still missing, in form order; empty when Continue may be pressed. */
    fun blockers(form: SetupForm): List<String> {
        val errors = (validate(form) as? SetupResult.Invalid)?.errors ?: return emptyList()
        return SetupField.entries.filter { it in errors }.map { BLOCKER_NAMES.getValue(it) }
    }

    /** "To continue, add ..." line under the disabled button, or null when the form is complete. */
    fun blockerSentence(form: SetupForm): String? {
        val missing = blockers(form)
        return if (missing.isEmpty()) null else "To continue, add: ${missing.joinToString(", ")}."
    }

    /**
     * Errors shown inline while typing: only for fields that already hold something invalid, so an
     * untouched form is not covered in red. Missing choices are listed by [blockers] instead.
     */
    fun inlineErrors(form: SetupForm): Map<SetupField, String> {
        val all = (validate(form) as? SetupResult.Invalid)?.errors ?: return emptyMap()
        val typed = buildSet {
            if (form.participantCode.isNotBlank()) add(SetupField.ParticipantCode)
            if (form.cameraHeightCm.isNotBlank()) add(SetupField.CameraHeight)
            if (form.cameraDistanceCm.isNotBlank()) add(SetupField.CameraDistance)
            if (form.room.length > MAX_ROOM) add(SetupField.Room)
            if (form.notes.isNotBlank()) add(SetupField.Notes)
        }
        return all.filterKeys { it in typed }
    }

    private val BLOCKER_NAMES = mapOf(
        SetupField.ParticipantCode to "a participant code",
        SetupField.AgeBand to "an age band",
        SetupField.Sex to "sex (or Not recorded)",
        SetupField.Clothing to "clothing",
        SetupField.ViewAngle to "camera angle",
        SetupField.CameraHeight to "camera height",
        SetupField.CameraDistance to "camera distance",
        SetupField.Room to "the room",
        SetupField.Notes to "shorter notes",
        SetupField.Consent to "consent confirmation",
    )

    private fun positive(raw: String): Double? = raw.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }

    private fun recordingErrors(form: SetupForm): Map<SetupField, String> = buildMap {
        if (form.clothing == null) put(SetupField.Clothing, "Choose what the participant is wearing")
        if (form.viewAngle == null) put(SetupField.ViewAngle, "Choose the camera angle")
        if (positive(form.cameraHeightCm) == null) put(SetupField.CameraHeight, "Enter the camera height in cm (floor to lens)")
        if (positive(form.cameraDistanceCm) == null) put(SetupField.CameraDistance, "Enter the camera distance in cm (lens to chair front)")
        val room = form.room.trim()
        if (room.isEmpty() || room.length > MAX_ROOM) put(SetupField.Room, "Describe the room, e.g. 'hostel common room'")
        if (form.notes.trim().length > MAX_NOTES) put(SetupField.Notes, "Keep notes under $MAX_NOTES characters")
        if (!form.consentConfirmed) put(SetupField.Consent, "Written consent must be signed before recording")
    }

    private fun recordingMeta(code: String, band: AgeBand, form: SetupForm): RecordingMeta? {
        val clothing = form.clothing ?: return null
        val angle = form.viewAngle ?: return null
        val height = positive(form.cameraHeightCm) ?: return null
        val distance = positive(form.cameraDistanceCm) ?: return null
        return RecordingMeta(code, band.recordingBand, clothing.wire, angle.wire, height, distance, form.room.trim(), form.notes.trim())
    }
}
