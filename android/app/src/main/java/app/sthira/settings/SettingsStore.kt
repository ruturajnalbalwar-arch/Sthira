package app.sthira.settings

import android.content.Context
import android.content.SharedPreferences

/** Light, dark, or whatever the phone is set to. */
enum class ThemeMode { System, Light, Dark }

/** The in-app text zoom steps, in percent of the phone's own font size. 100 is the default. */
val TEXT_SCALE_STEPS: List<Int> = listOf(100, 125, 150, 175, 200)
const val DEFAULT_TEXT_SCALE = 100

/** One snapshot of the user's display choices. [languageTag] null means "follow the phone language". */
data class SettingsValues(
    val themeMode: ThemeMode = ThemeMode.System,
    val textScalePercent: Int = DEFAULT_TEXT_SCALE,
    val languageTag: String? = null,
)

/**
 * The display choices on disk: a private SharedPreferences file (backup is off for the whole app).
 * Nothing here is health data. Unknown or corrupt values fall back to the defaults.
 */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun read(): SettingsValues = SettingsValues(
        themeMode = ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.System,
        textScalePercent = prefs.getInt(KEY_SCALE, DEFAULT_TEXT_SCALE).takeIf { it in TEXT_SCALE_STEPS } ?: DEFAULT_TEXT_SCALE,
        languageTag = prefs.getString(KEY_LANGUAGE, null)?.takeIf { tag -> AppLocales.find(tag) != null },
    )

    fun write(values: SettingsValues) {
        prefs.edit()
            .putString(KEY_THEME, values.themeMode.name)
            .putInt(KEY_SCALE, values.textScalePercent)
            .apply { if (values.languageTag == null) remove(KEY_LANGUAGE) else putString(KEY_LANGUAGE, values.languageTag) }
            .apply()
    }

    private companion object {
        const val FILE = "sthira_settings"
        const val KEY_THEME = "theme_mode"
        const val KEY_SCALE = "text_scale_percent"
        const val KEY_LANGUAGE = "language_tag"
    }
}
