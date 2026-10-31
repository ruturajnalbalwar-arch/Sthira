package app.sthira.nav

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.sthira.chairstand.ChairStandScreen
import app.sthira.home.HomeAction
import app.sthira.home.HomeScreen
import app.sthira.legal.LegalScreen
import app.sthira.legal.LicenceText
import app.sthira.legal.LicenceTextScreen
import app.sthira.legal.LicencesScreen
import app.sthira.legal.PRIVACY
import app.sthira.legal.TERMS
import app.sthira.settings.AppSettings
import app.sthira.settings.LanguageScreen
import app.sthira.settings.LocalAppSettings
import app.sthira.settings.SettingsScreen
import app.sthira.settings.rememberAppSettings
import app.sthira.timers.BalanceScreen
import app.sthira.timers.TugScreen
import app.sthira.ui.theme.Sthira
import app.sthira.ui.theme.SthiraTheme

/** A handful of flat screens, so plain saved state is enough; no navigation library. */
enum class Screen(val parent: Screen?) {
    Home(null),
    ChairStand(Home),
    Balance(Home),
    Tug(Home),
    Privacy(Home),
    Terms(Home),
    Licences(Home),
    Language(Home),
    Settings(Home),
    /** The language list opened from Settings: back returns to Settings. */
    SettingsLanguage(Settings),
    LicenceApache(Licences),
    LicenceBsd(Licences),
    LicenceMit(Licences),
}

private fun LicenceText.screen(): Screen = when (this) {
    LicenceText.Apache -> Screen.LicenceApache
    LicenceText.Bsd3 -> Screen.LicenceBsd
    LicenceText.Mit -> Screen.LicenceMit
}

@Composable
fun SthiraApp() {
    val settings = rememberAppSettings()
    CompositionLocalProvider(LocalAppSettings provides settings) {
        SthiraTheme(mode = settings.themeMode, textScale = settings.textScale) {
            SystemBarIcons()
            Screens(settings)
        }
    }
}

@Composable
private fun Screens(settings: AppSettings) {
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    val up = { screen = screen.parent ?: Screen.Home }
    BackHandler(enabled = screen != Screen.Home, onBack = up)
    when (screen) {
        Screen.Home -> HomeScreen({ action ->
            screen = when (action) {
                HomeAction.ChairStand -> Screen.ChairStand
                HomeAction.Balance -> Screen.Balance
                HomeAction.Tug -> Screen.Tug
                HomeAction.Privacy -> Screen.Privacy
                HomeAction.Terms -> Screen.Terms
                HomeAction.Licences -> Screen.Licences
                HomeAction.Language -> Screen.Language
                HomeAction.Settings -> Screen.Settings
            }
        })
        Screen.ChairStand -> ChairStandScreen(onBack = up)
        Screen.Balance -> BalanceScreen(onBack = up)
        Screen.Tug -> TugScreen(onBack = up)
        Screen.Privacy -> LegalScreen(PRIVACY, onBack = up, onLicences = { screen = Screen.Licences })
        Screen.Terms -> LegalScreen(TERMS, onBack = up)
        Screen.Licences -> LicencesScreen(onBack = up) { screen = it.screen() }
        Screen.LicenceApache -> LicenceTextScreen(LicenceText.Apache, onBack = up)
        Screen.LicenceBsd -> LicenceTextScreen(LicenceText.Bsd3, onBack = up)
        Screen.LicenceMit -> LicenceTextScreen(LicenceText.Mit, onBack = up)
        // Back to Home first: the activity is recreated in the new language and reopens on the saved screen.
        Screen.Language -> LanguageScreen(settings.languageTag, onPick = { tag ->
            screen = Screen.Home
            settings.setLanguage(context, tag)
        }, onBack = up)
        Screen.Settings -> SettingsScreen(onLanguage = { screen = Screen.SettingsLanguage }, onBack = up)
        Screen.SettingsLanguage -> LanguageScreen(settings.languageTag, onPick = { tag ->
            screen = Screen.Settings
            settings.setLanguage(context, tag)
        }, onBack = up)
    }
}

/** Status and navigation bar icons follow the app theme, not the phone's, when the user forces one. */
@Composable
private fun SystemBarIcons() {
    val activity = LocalContext.current as? ComponentActivity ?: return
    val dark = Sthira.colors.isDark
    DisposableEffect(activity, dark) {
        val style = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { }
    }
}
