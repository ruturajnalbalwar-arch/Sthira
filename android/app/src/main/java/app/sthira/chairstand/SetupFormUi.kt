package app.sthira.chairstand

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import app.sthira.R
import app.sthira.engine.norms.NormSex
import app.sthira.ui.common.ActionButton
import app.sthira.ui.common.ChoiceGroup
import app.sthira.ui.common.LabeledField
import app.sthira.ui.common.Muted
import app.sthira.ui.common.asString
import app.sthira.ui.theme.Space

/**
 * One change to the form, applied by the ViewModel to its latest form. Sending an edit instead of a
 * whole new form means two changes made before the next recomposition cannot overwrite each other.
 */
typealias SetupEdit = (SetupForm) -> SetupForm

/** Three questions only (code, age band, sex), with large single-tap choices and one short help line each. */
@Composable
fun SetupFormUi(form: SetupForm, onChange: (SetupEdit) -> Unit) {
    val errors = SessionSetup.inlineErrors(form)
    // ChoiceGroup's label lambda is not composable, so the labels are resolved here.
    val ageLabels = AgeBand.entries.associateWith { stringResource(it.label) }
    val sexLabels = NormSex.entries.associateWith { stringResource(it.labelRes) }
    Column(verticalArrangement = Arrangement.spacedBy(Space.l)) {
        LabeledField(
            stringResource(R.string.cs_code_label), form.participantCode, { onChange { f -> f.copy(participantCode = it.uppercase()) } },
            error = errors[SetupField.ParticipantCode]?.asString(), help = stringResource(R.string.cs_code_help),
        )
        ChoiceGroup(
            stringResource(R.string.cs_age_label), AgeBand.entries, form.ageBand, { ageLabels.getValue(it) },
            { onChange { f -> f.copy(ageBand = it) } }, help = stringResource(R.string.cs_age_help),
        )
        ChoiceGroup(
            stringResource(R.string.cs_sex_label), NormSex.entries, form.sex, { sexLabels.getValue(it) },
            { onChange { f -> f.copy(sex = it) } }, help = stringResource(R.string.cs_sex_help),
        )
    }
}

/** Sticky bottom bar content: the button stays visible and says why it is disabled. */
@Composable
fun ContinueBar(form: SetupForm, onSubmit: () -> Unit) {
    val missing = SessionSetup.blockers(form).map { stringResource(it.blocker) }
    val separator = stringResource(R.string.cs_list_separator)
    val reason = if (missing.isEmpty()) null else stringResource(R.string.cs_continue_blocked, missing.joinToString(separator))
    if (reason != null) Muted(reason, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    ActionButton(
        stringResource(R.string.cs_continue), onSubmit, Modifier.fillMaxWidth(), enabled = reason == null, big = true,
        icon = Icons.AutoMirrored.Filled.ArrowForward,
    )
}
