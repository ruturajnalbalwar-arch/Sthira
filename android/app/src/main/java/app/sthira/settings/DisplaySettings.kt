package app.sthira.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.sthira.R

/** The current language in its own script, or "Phone language". */
@Composable
fun languageName(tag: String?): String =
    tag?.let(AppLocales::find)?.let { stringResource(it.nativeName) } ?: stringResource(R.string.common_language_phone)

/**
 * The language the screen is in right now, in its own script (for example "हिन्दी"), also when the
 * app follows the phone. English when the phone's language is not one the app offers.
 */
@Composable
fun shownLanguageName(tag: String?): String {
    val shown = tag ?: LocalConfiguration.current.locales[0]?.toLanguageTag()
    val language = shown?.let(AppLocales::find) ?: AppLocales.LANGUAGES.first()
    return stringResource(language.nativeName)
}

@Composable
fun themeName(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.System -> R.string.common_theme_system
        ThemeMode.Light -> R.string.common_theme_light
        ThemeMode.Dark -> R.string.common_theme_dark
    },
)
