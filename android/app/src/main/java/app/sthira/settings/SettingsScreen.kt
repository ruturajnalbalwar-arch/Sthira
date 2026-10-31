package app.sthira.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.sthira.R
import app.sthira.ui.common.AppIcons
import app.sthira.ui.common.BigTarget
import app.sthira.ui.common.Body
import app.sthira.ui.common.ChoiceTileGrid
import app.sthira.ui.common.Heading
import app.sthira.ui.common.Neu
import app.sthira.ui.common.NeuSurface
import app.sthira.ui.common.ScreenScaffold
import app.sthira.ui.common.SwitchRow
import app.sthira.ui.common.TextSizeControl
import app.sthira.ui.theme.Space
import app.sthira.ui.theme.Sthira
import app.sthira.ui.theme.SthiraType

/**
 * Every app setting on one screen, one section each: language, theme, text size, spoken
 * instructions, and a short "About" with the offline promise. Each choice takes effect at once.
 */
@Composable
fun SettingsScreen(onLanguage: () -> Unit, onBack: () -> Unit) {
    val settings = LocalAppSettings.current
    ScreenScaffold(stringResource(R.string.common_settings_title), onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xl)) {
            Section(stringResource(R.string.common_language_title)) {
                LanguageButton(shownLanguageName(settings.languageTag), onLanguage)
            }
            Section(stringResource(R.string.common_theme_label)) {
                // Three across at normal text sizes; one under the other once the text is large.
                val wide = LocalDensity.current.fontScale < WIDE_THEME_MAX_SCALE
                val names = ThemeMode.entries.associateWith { themeName(it) }
                ChoiceTileGrid(
                    options = ThemeMode.entries, selected = settings.themeMode, text = { names.getValue(it) },
                    onSelect = settings::setThemeMode, columns = if (wide) ThemeMode.entries.size else 1,
                )
            }
            Section(stringResource(R.string.common_text_size_title)) { TextSizeControl() }
            Section(stringResource(R.string.common_voice_title)) {
                SwitchRow(
                    text = stringResource(R.string.common_voice_help), checked = settings.voiceOn,
                    onChange = settings::setVoiceOn,
                )
            }
            Section(stringResource(R.string.common_about_title)) {
                Body(stringResource(R.string.home_tagline))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Sthira.colors.text, modifier = Modifier.size(24.dp))
                    Body(stringResource(R.string.common_about_offline))
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Heading(title)
        content()
    }
}

/** A big outlined row: globe, "Language / भाषा", the current language, and an arrow to the list. */
@Composable
private fun LanguageButton(current: String, onClick: () -> Unit) {
    val c = Sthira.colors
    val spoken = stringResource(R.string.common_language_change, current)
    NeuSurface(
        Modifier.fillMaxWidth().heightIn(min = BigTarget).semantics { contentDescription = spoken },
        shape = Neu.ButtonShape, onClick = onClick, rim = c.text, rimWidth = Neu.StrongEdgeWidth,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.m, vertical = 12.dp).align(Alignment.CenterStart)
                .clearAndSetSemantics { },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(AppIcons.Globe, contentDescription = null, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.common_language_button), style = SthiraType.label, color = c.muted)
                Text(current, style = SthiraType.title, color = c.text)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(28.dp))
        }
    }
}

/** Above this font scale the three theme tiles are stacked instead of side by side. */
private const val WIDE_THEME_MAX_SCALE = 1.3f
