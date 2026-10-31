package app.sthira.settings

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import app.sthira.R
import java.util.Locale

/** A language the app offers. [nativeName] is the language's name in its own script, never translated. */
data class AppLanguage(val tag: String, @StringRes val nativeName: Int)

/**
 * The app's languages and how a choice is applied. Keep this list in step with res/xml/locales_config.xml.
 *
 * Android 13 and later keep the per-app language themselves (LocaleManager), which also localises the
 * Application context the ViewModels use. Android 10 to 12 have no per-app language: the tag is saved
 * in [SettingsStore] and MainActivity wraps its base context with it ([wrap]) before recreating.
 */
object AppLocales {
    val LANGUAGES: List<AppLanguage> = listOf(
        AppLanguage("en", R.string.common_lang_en),
        AppLanguage("hi", R.string.common_lang_hi),
        AppLanguage("bn", R.string.common_lang_bn),
        AppLanguage("mr", R.string.common_lang_mr),
        AppLanguage("te", R.string.common_lang_te),
        AppLanguage("ta", R.string.common_lang_ta),
        AppLanguage("gu", R.string.common_lang_gu),
        AppLanguage("kn", R.string.common_lang_kn),
        AppLanguage("or", R.string.common_lang_or),
        AppLanguage("ml", R.string.common_lang_ml),
        AppLanguage("pa", R.string.common_lang_pa),
    )

    /** Matches on the language part only, so "hi-IN" finds Hindi. */
    fun find(tag: String): AppLanguage? {
        val language = Locale.forLanguageTag(tag).language
        return LANGUAGES.firstOrNull { it.tag == language }
    }

    private val perAppLocales: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** The language the user picked in the app, or null when the app follows the phone. */
    fun current(context: Context, saved: String?): String? {
        if (!perAppLocales) return saved
        val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales ?: return saved
        return if (locales.isEmpty) null else find(locales[0].toLanguageTag())?.tag
    }

    /**
     * Applies [tag] (null: follow the phone). On Android 13+ the system recreates the activity itself;
     * on older versions the caller has already saved the tag, so recreating picks it up in [wrap].
     */
    fun apply(activity: Activity, tag: String?) {
        if (perAppLocales) {
            val list = if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
            activity.getSystemService(LocaleManager::class.java)?.setApplicationLocales(list)
        } else {
            activity.recreate()
        }
    }

    /** For MainActivity.attachBaseContext on Android 10 to 12: a context in the saved language. */
    fun wrap(base: Context): Context {
        if (perAppLocales) return base
        val tag = SettingsStore(base).read().languageTag
        val locale = if (tag == null) Resources.getSystem().configuration.locales[0] else Locale.forLanguageTag(tag)
        // Dates and numbers formatted with the default locale follow the chosen language too.
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList(locale)) }
        updateApplication(base, config)
        return base.createConfigurationContext(config)
    }

    /** Text resolved from the Application context (ViewModels) follows the choice on old Android too. */
    @Suppress("DEPRECATION")
    private fun updateApplication(base: Context, config: Configuration) {
        val app = base.applicationContext ?: return
        app.resources.updateConfiguration(Configuration(app.resources.configuration).apply { setLocales(config.locales) }, app.resources.displayMetrics)
    }
}
