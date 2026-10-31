package app.sthira.chairstand

import androidx.annotation.StringRes
import app.sthira.R
import app.sthira.engine.norms.NormSex
import app.sthira.ui.common.UiText
import app.sthira.ui.common.uiText

/**
 * Age bands on the chair-stand form. [normBand] is the band looked up in norms.json: the CDC 5-year
 * bands from 60 to 79, and "80+", which is compared with the first CDC row at 80 and says so
 * (see [NormChooser]). Under 60 has no CDC chair-stand value.
 */
enum class AgeBand(@StringRes val label: Int, val normBand: String?) {
    Under60(R.string.cs_age_under_60, null),
    A60_64(R.string.cs_age_60_64, "60-64"),
    A65_69(R.string.cs_age_65_69, "65-69"),
    A70_74(R.string.cs_age_70_74, "70-74"),
    A75_79(R.string.cs_age_75_79, "75-79"),
    A80Plus(R.string.cs_age_80_plus, "80+"),
}

/** The form label for each sex choice (the engine's own labels are English only). */
@get:StringRes
val NormSex.labelRes: Int
    get() = when (this) {
        NormSex.FEMALE -> R.string.cs_sex_female
        NormSex.MALE -> R.string.cs_sex_male
        NormSex.NOT_RECORDED -> R.string.cs_sex_not_recorded
    }

/** What the form holds before validation. Nothing here is ever saved. */
data class SetupForm(
    val participantCode: String = "",
    val ageBand: AgeBand? = null,
    val sex: NormSex? = null,
)

/** In form order; [blocker] names the field in the "To continue, add ..." line. */
enum class SetupField(@StringRes val blocker: Int) {
    ParticipantCode(R.string.cs_blocker_code),
    AgeBand(R.string.cs_blocker_age),
    Sex(R.string.cs_blocker_sex),
}

data class Session(val participantCode: String, val ageBand: AgeBand, val sex: NormSex)

sealed interface SetupResult {
    data class Ok(val session: Session) : SetupResult
    data class Invalid(val errors: Map<SetupField, UiText>) : SetupResult
}

object SessionSetup {
    /** The same code rule as the web app and the labelling tool, so a code means the same everywhere. */
    private val CODE_CHARS = Regex("^[A-Za-z0-9_-]+$")

    /** Same limit as the ids in the labelling tool. */
    const val MAX_CODE = 40

    private fun codeError(code: String): UiText? = when {
        code.isEmpty() -> uiText(R.string.cs_error_code_empty)
        code.length > MAX_CODE -> uiText(R.string.cs_error_code_too_long, MAX_CODE)
        !CODE_CHARS.matches(code) -> uiText(R.string.cs_error_code_chars)
        else -> null
    }

    fun validate(form: SetupForm): SetupResult {
        val code = form.participantCode.trim()
        val errors = buildMap {
            codeError(code)?.let { put(SetupField.ParticipantCode, it) }
            if (form.ageBand == null) put(SetupField.AgeBand, uiText(R.string.cs_error_age_band))
            if (form.sex == null) put(SetupField.Sex, uiText(R.string.cs_error_sex))
        }
        val band = form.ageBand
        val sex = form.sex
        if (errors.isNotEmpty() || band == null || sex == null) return SetupResult.Invalid(errors)
        return SetupResult.Ok(Session(code, band, sex))
    }

    /** What is still missing, in form order; empty when Continue may be pressed. */
    fun blockers(form: SetupForm): List<SetupField> {
        val errors = (validate(form) as? SetupResult.Invalid)?.errors ?: return emptyList()
        return SetupField.entries.filter { it in errors }
    }

    /**
     * Errors shown inline while typing: only for a code that already holds something invalid, so an
     * untouched form is not covered in red. Missing choices are listed by [blockers] instead.
     */
    fun inlineErrors(form: SetupForm): Map<SetupField, UiText> {
        if (form.participantCode.isBlank()) return emptyMap()
        val all = (validate(form) as? SetupResult.Invalid)?.errors ?: return emptyMap()
        return all.filterKeys { it == SetupField.ParticipantCode }
    }
}
