package app.sthira.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sthira.ui.common.ActionButton
import app.sthira.ui.common.Body
import app.sthira.ui.common.Heading
import app.sthira.ui.common.LinkButton
import app.sthira.ui.common.Muted
import app.sthira.ui.common.ScreenScaffold
import app.sthira.ui.common.Tone
import app.sthira.ui.theme.Sthira

enum class HomeAction { ChairStand, Balance, Tug, Privacy, Terms }

@Composable
fun HomeScreen(onAction: (HomeAction) -> Unit) {
    ScreenScaffold("Sthira", onBack = null) {
        Text("Steady scores for home physiotherapy", color = Sthira.colors.primary, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp)
        Body(
            "Runs the CDC STEADI chair-stand, balance and Timed Up and Go tests. Pose tracking happens on this phone and " +
                "video never leaves it. Results are shown next to CDC reference values (US data, not validated for India) " +
                "for the physiotherapist to interpret; Sthira does not diagnose or predict falls.",
            size = 17,
        )
        Heading("Tests", size = 20)
        val wide = Modifier.fillMaxWidth()
        ActionButton("30-second chair stand", { onAction(HomeAction.ChairStand) }, wide, big = true)
        ActionButton("4-stage balance", { onAction(HomeAction.Balance) }, wide, big = true, tone = Tone.Secondary)
        ActionButton("Timed Up and Go", { onAction(HomeAction.Tug) }, wide, big = true, tone = Tone.Secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LinkButton("Privacy", { onAction(HomeAction.Privacy) })
            LinkButton("Terms", { onAction(HomeAction.Terms) })
        }
        Muted("Prototype. Not a medical device.")
    }
}
