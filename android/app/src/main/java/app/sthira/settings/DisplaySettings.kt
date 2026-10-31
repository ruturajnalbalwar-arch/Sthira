package app.sthira.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.sthira.R
import app.sthira.ui.common.AppIcons
import app.sthira.ui.common.ChoiceGroup
import app.sthira.ui.common.LinkButton
import app.sthira.ui.common.Neu
import app.sthira.ui.common.NeuSurface
import app.sthira.ui.common.TextZoomControl
import app.sthira.ui.theme.Space
import app.sthira.ui.theme.Sthira
import app.sthira.ui.theme.SthiraType

/**
 * The Home "Display" row: language, theme and text size side by side, wrapping onto more lines when
 * the text is large. Each button shows its current value, so nothing is hidden behind an icon.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplaySettingsRow(onLanguage: () -> Unit, modifier: Modifier = Modifier) {
    val settings = LocalAppSettings.current
    var choosingTheme by rememberSaveable { mutableStateOf(false) }
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        SettingButton(
            icon = AppIcons.Globe,
            label = stringResource(R.string.common_language_button),
            value = languageName(settings.languageTag),
            onClick = onLanguage,
        )
        SettingButton(
            icon = AppIcons.Contrast,
            label = stringResource(R.string.common_theme_label),
            value = themeName(settings.themeMode),
            onClick = { choosingTheme = true },
        )
        TextZoomControl(Modifier.align(Alignment.CenterVertically))
    }
    if (choosingTheme) ThemeDialog(settings.themeMode, settings::setThemeMode) { choosingTheme = false }
}

/** The current language in its own script, or "Phone language". */
@Composable
fun languageName(tag: String?): String =
    tag?.let(AppLocales::find)?.let { stringResource(it.nativeName) } ?: stringResource(R.string.common_language_phone)

@Composable
fun themeName(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.System -> R.string.common_theme_system
        ThemeMode.Light -> R.string.common_theme_light
        ThemeMode.Dark -> R.string.common_theme_dark
    },
)

/** A raised button with an icon, a label and its current value underneath (both read by TalkBack). */
@Composable
private fun SettingButton(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    NeuSurface(Modifier.heightIn(min = SETTING_HEIGHT), shape = Neu.ButtonShape, elevation = 5.dp, onClick = onClick) {
        Row(
            Modifier.align(Alignment.CenterStart).padding(horizontal = 14.dp, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Column {
                Text(label, style = SthiraType.label)
                Text(value, style = SthiraType.label.copy(fontWeight = FontWeight.Normal), color = Sthira.colors.muted)
            }
        }
    }
}

/** Same as phone, light or dark: a single-choice list (radio buttons for TalkBack). Picking closes it. */
@Composable
private fun ThemeDialog(current: ThemeMode, onPick: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
    val c = Sthira.colors
    val names = ThemeMode.entries.associateWith { themeName(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { LinkButton(stringResource(R.string.common_close), onDismiss) },
        containerColor = Neu.base(c),
        textContentColor = c.text,
        text = {
            ChoiceGroup(
                label = stringResource(R.string.common_theme_label),
                options = ThemeMode.entries,
                selected = current,
                text = { names.getValue(it) },
                onSelect = { mode ->
                    onPick(mode)
                    onDismiss()
                },
            )
        },
    )
}

private val SETTING_HEIGHT = 56.dp
