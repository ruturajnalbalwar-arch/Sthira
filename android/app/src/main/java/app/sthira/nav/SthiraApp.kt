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
import app.sthira.legal.PRIVACY
import app.sthira.legal.TERMS
import app.sthira.timers.BalanceScreen
import app.sthira.timers.TugScreen
import app.sthira.ui.theme.SthiraTheme

/** Five flat screens, so plain state is enough; no navigation library. */
enum class Screen { Home, ChairStand, Balance, Tug, Privacy, Terms }

@Composable
fun SthiraApp() {
    SthiraTheme {
        var screen by rememberSaveable { mutableStateOf(Screen.Home) }
        val home = { screen = Screen.Home }
        BackHandler(enabled = screen != Screen.Home, onBack = home)
        when (screen) {
            Screen.Home -> HomeScreen { action ->
                screen = when (action) {
                    HomeAction.ChairStand -> Screen.ChairStand
                    HomeAction.Balance -> Screen.Balance
                    HomeAction.Tug -> Screen.Tug
                    HomeAction.Privacy -> Screen.Privacy
                    HomeAction.Terms -> Screen.Terms
                }
            }
            Screen.ChairStand -> ChairStandScreen(onBack = home)
            Screen.Balance -> BalanceScreen(onBack = home)
            Screen.Tug -> TugScreen(onBack = home)
            Screen.Privacy -> LegalScreen(PRIVACY, onBack = home)
            Screen.Terms -> LegalScreen(TERMS, onBack = home)
        }
    }
}
