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

/**
 * What step 1 holds before Next: only the two answers the CDC comparison needs. There is no
 * participant code: nothing is saved, so a code would have no use. Nothing here is ever saved.
 */
data class SetupForm(
    val ageBand: AgeBand? = null,
    val sex: NormSex? = null,
) {
    /** Kept so the debug font-scale previews still build; the code is ignored. */
    @Suppress("UNUSED_PARAMETER")
    constructor(participantCode: String, ageBand: AgeBand? = null, sex: NormSex? = null) : this(ageBand, sex)
}

/** In form order; [blocker] names the field in the "To continue, add ..." line. */
enum class SetupField(@StringRes val blocker: Int) {
    AgeBand(R.string.cs_blocker_age),
    Sex(R.string.cs_blocker_sex),
}

data class Session(val ageBand: AgeBand, val sex: NormSex)

sealed interface SetupResult {
    data class Ok(val session: Session) : SetupResult
    data class Invalid(val errors: Map<SetupField, UiText>) : SetupResult
}

object SessionSetup {
    fun validate(form: SetupForm): SetupResult {
        val band = form.ageBand
        val sex = form.sex
        if (band != null && sex != null) return SetupResult.Ok(Session(band, sex))
        val errors = buildMap {
            if (band == null) put(SetupField.AgeBand, uiText(R.string.cs_error_age_band))
            if (sex == null) put(SetupField.Sex, uiText(R.string.cs_error_sex))
        }
        return SetupResult.Invalid(errors)
    }

    /** What is still missing, in form order; empty when Next may be pressed. */
    fun blockers(form: SetupForm): List<SetupField> {
        val errors = (validate(form) as? SetupResult.Invalid)?.errors ?: return emptyList()
        return SetupField.entries.filter { it in errors }
    }
}
