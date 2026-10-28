package app.sthira.nav

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.sthira.chairstand.ChairStandScreen
import app.sthira.home.HomeAction
import app.sthira.home.HomeScreen
import app.sthira.legal.LegalScreen
import app.sthira.legal.LicenceText
import app.sthira.legal.LicenceTextScreen
import app.sthira.legal.LicencesScreen
import app.sthira.legal.PRIVACY
import app.sthira.legal.TERMS
import app.sthira.timers.BalanceScreen
import app.sthira.timers.TugScreen
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
    SthiraTheme {
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
        }
    }
}
