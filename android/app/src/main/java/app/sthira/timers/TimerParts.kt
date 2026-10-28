package app.sthira.timers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sthira.engine.timers.CdcSource
import app.sthira.ui.common.Banner
import app.sthira.ui.common.BannerKind
import app.sthira.ui.common.BannerText
import app.sthira.ui.common.Body
import app.sthira.ui.common.BulletList
import app.sthira.ui.common.CardShape
import app.sthira.ui.common.Muted
import app.sthira.ui.theme.Sthira
import java.util.Locale

fun formatSeconds(seconds: Double): String = String.format(Locale.US, "%.1f s", seconds)

/** The running time, readable across the room. Screen readers get the label only, not every tenth. */
@Composable
fun BigTime(seconds: Double, label: String) {
    val size = with(LocalDensity.current) { 64.dp.toSp() }
    Surface(
        color = Sthira.colors.surface, contentColor = Sthira.colors.text, shape = CardShape,
        border = BorderStroke(1.dp, Sthira.colors.border), modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(20.dp).clearAndSetSemantics { contentDescription = label },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label.uppercase(Locale.US), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Sthira.colors.muted)
            Text(formatSeconds(seconds), fontSize = size, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun ResultBanner(meets: Boolean, title: String, content: @Composable () -> Unit) {
    Banner(if (meets) BannerKind.Success else BannerKind.Warn) {
        BannerText(title)
        content()
    }
}

/** The CDC source line, as plain text: the app is offline, so it shows the reference instead of a link. */
@Composable
fun CdcNote(source: CdcSource) {
    Muted(
        "Reference values from ${source.title} (${source.url}). US data, not validated for older adults in India. " +
            "For the physiotherapist to interpret; Sthira does not diagnose.",
    )
}

@Composable
fun TimerError(errors: List<String>) {
    Banner(BannerKind.Danger) {
        BannerText("The CDC reference values in norms.json are missing or invalid.")
        BulletList(errors, mono = true)
    }
}

@Composable
fun Instructions(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Body(text) }
}
