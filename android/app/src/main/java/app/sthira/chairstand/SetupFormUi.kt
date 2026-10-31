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
import app.sthira.ui.common.Muted
import app.sthira.ui.theme.Space

/**
 * One change to the form, applied by the ViewModel to its latest form. Sending an edit instead of a
 * whole new form means two changes made before the next recomposition cannot overwrite each other.
 */
typealias SetupEdit = (SetupForm) -> SetupForm

/** Step 1: two questions only (age and sex), each a set of big tiles with one short help line. */
@Composable
fun SetupFormUi(form: SetupForm, onChange: (SetupEdit) -> Unit) {
    val sexMain = NormSex.entries.filter { it != NormSex.NOT_RECORDED }
    val sexExtra = NormSex.entries.firstOrNull { it == NormSex.NOT_RECORDED }
    Column(verticalArrangement = Arrangement.spacedBy(Space.l)) {
        ChoiceTiles(
            stringResource(R.string.cs_age_label), AgeBand.entries, form.ageBand, { stringResource(it.label) },
            { band -> onChange { f -> f.copy(ageBand = band) } }, help = stringResource(R.string.cs_age_help),
        )
        ChoiceTiles(
            stringResource(R.string.cs_sex_label), sexMain, form.sex, { stringResource(it.labelRes) },
            { sex -> onChange { f -> f.copy(sex = sex) } }, help = stringResource(R.string.cs_sex_help), extra = sexExtra,
        )
    }
}

/** Sticky bottom bar of step 1: the one Next button stays visible and says why it is disabled. */
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
