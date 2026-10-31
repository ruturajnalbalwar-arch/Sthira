package app.sthira.legal

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.sthira.R
import app.sthira.ui.common.Body
import app.sthira.ui.common.Heading
import app.sthira.ui.common.LinkButton
import app.sthira.ui.common.Muted
import app.sthira.ui.common.ScreenScaffold
import app.sthira.ui.theme.Space
import app.sthira.ui.theme.Sthira
import app.sthira.ui.theme.SthiraType
import java.io.IOException

private val CARD_SHAPE = RoundedCornerShape(16.dp)
private val CARD_BORDER = 2.dp

/** Licence texts shipped in res/raw. Their titles are licence names, so they stay in English. */
enum class LicenceText(val title: String, @RawRes val res: Int) {
    Apache("Apache License 2.0", R.raw.apache_2_0),
    Bsd3("BSD 3-Clause License", R.raw.bsd_3_clause),
    Mit("MIT License", R.raw.mit),
}

/** [name] and [by] are library names and authors, kept in English; only [note] is translated. */
data class Component(val name: String, val by: String, val licence: LicenceText, @StringRes val note: Int? = null)

val COMPONENTS = listOf(
    Component(
        "MediaPipe Tasks Vision and the BlazePose GHUM 3D pose model (pose_landmarker_full)", "Google", LicenceText.Apache,
        R.string.legal_mediapipe_model_note,
    ),
    Component("AndroidX Activity, Core, Lifecycle, Compose, Material 3 and CameraX", "Google / The Android Open Source Project", LicenceText.Apache),
    Component("Kotlin standard library, kotlinx.coroutines and kotlinx.serialization", "JetBrains", LicenceText.Apache),
    Component("protobuf-javalite", "Google", LicenceText.Bsd3),
    Component("Guava", "Google", LicenceText.Apache),
    Component("Google datatransport", "Google", LicenceText.Apache),
    Component("checker-compat-qual", "Checker Framework developers", LicenceText.Mit),
    Component("animal-sniffer annotations", "codehaus.org", LicenceText.Mit),
)

@Composable
fun LicencesScreen(onBack: () -> Unit, onOpen: (LicenceText) -> Unit) {
    val c = Sthira.colors
    ScreenScaffold(stringResource(R.string.legal_licences_title), onBack) {
        Body(stringResource(R.string.legal_licences_intro))
        COMPONENTS.forEach { component ->
            Column(
                Modifier.fillMaxWidth().background(c.surface, CARD_SHAPE).border(CARD_BORDER, c.borderStrong, CARD_SHAPE).padding(Space.m),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Text(component.name, style = SthiraType.titleSmall, color = c.text)
                Muted(stringResource(R.string.legal_component_by_licence, component.by, component.licence.title))
                component.note?.let { Body(stringResource(it)) }
            }
        }
        Heading(stringResource(R.string.legal_licences_full_texts), Modifier.padding(top = Space.s), size = 20)
        LicenceText.entries.forEach { text -> LinkButton(text.title, { onOpen(text) }) }
    }
}

@Composable
fun LicenceTextScreen(text: LicenceText, onBack: () -> Unit) {
    val context = LocalContext.current
    val readError = stringResource(R.string.legal_licence_read_error)
    val body = remember(text, readError) {
        try {
            context.resources.openRawResource(text.res).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            readError
        }
    }
    ScreenScaffold(text.title, onBack) {
        Text(body, style = SthiraType.label.copy(fontFamily = FontFamily.Monospace, fontWeight = null), color = Sthira.colors.text)
    }
}
