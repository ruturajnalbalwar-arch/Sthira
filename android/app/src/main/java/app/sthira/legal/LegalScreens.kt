package app.sthira.legal

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.sthira.R
import app.sthira.ui.common.Body
import app.sthira.ui.common.Heading
import app.sthira.ui.common.LinkButton
import app.sthira.ui.common.Muted
import app.sthira.ui.common.ScreenScaffold
import app.sthira.ui.common.UiText
import app.sthira.ui.common.asString
import app.sthira.ui.common.uiText
import app.sthira.ui.theme.Space

data class LegalSection(@StringRes val heading: Int, val body: List<UiText>)

data class LegalDoc(
    @StringRes val title: Int,
    @StringRes val updated: Int,
    @StringRes val intro: Int,
    val sections: List<LegalSection>,
)

private val CONTACT: UiText = uiText(R.string.legal_contact_line, uiText(R.string.legal_team_name), uiText(R.string.legal_contact_email))

private fun section(@StringRes heading: Int, vararg body: Int) = LegalSection(heading, body.map { uiText(it) })

/** What the Android app does: camera frames are processed on the phone and only display settings are stored. */
val PRIVACY = LegalDoc(
    title = R.string.legal_privacy_title,
    updated = R.string.legal_updated_date,
    intro = R.string.legal_privacy_intro,
    sections = listOf(
        section(
            R.string.legal_privacy_camera_heading,
            R.string.legal_privacy_camera_permission,
            R.string.legal_privacy_camera_not_saved,
            R.string.legal_privacy_offline,
            R.string.legal_privacy_mediapipe,
            R.string.legal_privacy_screen_capture,
        ),
        section(R.string.legal_privacy_stored_heading, R.string.legal_privacy_stored_settings),
        section(R.string.legal_privacy_not_collected_heading, R.string.legal_privacy_not_collected),
        section(R.string.legal_privacy_backup_heading, R.string.legal_privacy_backup),
        LegalSection(R.string.legal_contact_heading, listOf(uiText(R.string.legal_privacy_contact, CONTACT))),
    ),
)

val TERMS = LegalDoc(
    title = R.string.legal_terms_title,
    updated = R.string.legal_updated_date,
    intro = R.string.legal_terms_intro,
    sections = listOf(
        section(R.string.legal_terms_prototype_heading, R.string.legal_terms_prototype, R.string.legal_terms_reference),
        section(R.string.legal_terms_safety_heading, R.string.legal_terms_safety),
        section(R.string.legal_terms_responsibilities_heading, R.string.legal_terms_responsibilities),
        section(R.string.legal_terms_warranty_heading, R.string.legal_terms_warranty),
        LegalSection(R.string.legal_contact_heading, listOf(CONTACT)),
    ),
)

@Composable
fun LegalScreen(doc: LegalDoc, onBack: () -> Unit, onLicences: (() -> Unit)? = null) {
    ScreenScaffold(stringResource(doc.title), onBack) {
        Muted(stringResource(R.string.legal_last_updated, stringResource(doc.updated)))
        Body(stringResource(doc.intro), size = 18)
        doc.sections.forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Heading(stringResource(section.heading), size = 20)
                section.body.forEach { Body(it.asString()) }
            }
        }
        if (onLicences != null) LinkButton(stringResource(R.string.legal_licences_title), onLicences)
    }
}
