package app.sthira.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * The user's choices (display, language, voice) as Compose state, with setters that save them. Read it anywhere below
 * SthiraApp through [LocalAppSettings]. Outside the app (previews, isolated UI tests) the default is an
 * in-memory copy with the defaults, so nothing is written to disk.
 */
@Stable
class AppSettings internal constructor(private val store: SettingsStore?, initial: SettingsValues) {
    var values: SettingsValues by mutableStateOf(initial)
        private set

    val themeMode: ThemeMode get() = values.themeMode
    val textScalePercent: Int get() = values.textScalePercent
    /** Multiplier on the phone's own font scale. */
    val textScale: Float get() = values.textScalePercent / PERCENT
    /** The picked language tag, or null when the app follows the phone. */
    val languageTag: String? get() = values.languageTag
    /** True when the test screens should speak their instructions. Read it as `LocalAppSettings.current.voiceOn`. */
    val voiceOn: Boolean get() = values.voiceOn

    val canZoomIn: Boolean get() = textScalePercent < TEXT_SCALE_STEPS.last()
    val canZoomOut: Boolean get() = textScalePercent > TEXT_SCALE_STEPS.first()

    fun setThemeMode(mode: ThemeMode) = update(values.copy(themeMode = mode))

    /** Turns the spoken instructions on or off (saved). */
    fun setVoiceOn(on: Boolean) = update(values.copy(voiceOn = on))

    fun setTextScalePercent(percent: Int) {
        if (percent in TEXT_SCALE_STEPS) update(values.copy(textScalePercent = percent))
    }

    fun zoomIn() = step(+1)
    fun zoomOut() = step(-1)

    private fun step(by: Int) {
        val index = TEXT_SCALE_STEPS.indexOf(textScalePercent).coerceAtLeast(0)
        setTextScalePercent(TEXT_SCALE_STEPS[(index + by).coerceIn(0, TEXT_SCALE_STEPS.lastIndex)])
    }

    /** Switches the app language (null: follow the phone). The activity is recreated in the new language. */
    fun setLanguage(context: Context, tag: String?) {
        if (tag == languageTag) return
        update(values.copy(languageTag = tag))
        context.findActivity()?.let { AppLocales.apply(it, tag) }
    }

    private fun update(next: SettingsValues) {
        if (next == values) return
        values = next
        store?.write(next)
    }

    companion object {
        private const val PERCENT = 100f

        fun load(context: Context): AppSettings {
            val store = SettingsStore(context)
            val saved = store.read()
            return AppSettings(store, saved.copy(languageTag = AppLocales.current(context, saved.languageTag)))
        }

        fun inMemory(values: SettingsValues = SettingsValues()): AppSettings = AppSettings(null, values)
    }
}

val LocalAppSettings = staticCompositionLocalOf { AppSettings.inMemory() }

/** The saved settings, loaded once per activity. */
@Composable
fun rememberAppSettings(): AppSettings {
    val context = LocalContext.current
    return remember(context) { AppSettings.load(context) }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
