package app.sthira.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.sthira.R
import app.sthira.settings.LocalAppSettings
import app.sthira.ui.theme.Sthira
import app.sthira.ui.theme.SthiraType

/**
 * In-app text zoom: A- and A+ buttons with the current level (100% to 200%) between them. The control
 * itself is drawn at a fixed size (dp, not sp) so it never grows out of the top bar at 200%.
 */
@Composable
fun TextZoomControl(modifier: Modifier = Modifier) {
    val settings = LocalAppSettings.current
    val percent = settings.textScalePercent
    val state = stringResource(R.string.common_text_size_state, percent)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        ZoomButton(
            glyph = stringResource(R.string.common_text_zoom_out_glyph),
            description = stringResource(R.string.common_text_smaller) + ". " + state,
            enabled = settings.canZoomOut, onClick = settings::zoomOut,
        )
        Text(
            stringResource(R.string.common_text_size_percent, percent),
            modifier = Modifier.widthIn(min = 44.dp).clearAndSetSemantics {
                contentDescription = state
                liveRegion = LiveRegionMode.Polite
            },
            style = SthiraType.label.copy(fontSize = fixedSp(15.dp)),
            color = Sthira.colors.text, textAlign = TextAlign.Center, maxLines = 1,
        )
        ZoomButton(
            glyph = stringResource(R.string.common_text_zoom_in_glyph),
            description = stringResource(R.string.common_text_larger) + ". " + state,
            enabled = settings.canZoomIn, onClick = settings::zoomIn,
        )
    }
}

@Composable
private fun ZoomButton(glyph: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    NeuSurface(
        Modifier.size(MinTarget).semantics { contentDescription = description },
        shape = Neu.ButtonShape, elevation = 3.dp, onClick = onClick, enabled = enabled,
    ) {
        // The glyph is not read out: the button's description says what it does.
        Box(Modifier.align(Alignment.Center).clearAndSetSemantics { }) {
            Text(glyph, style = SthiraType.label.copy(fontSize = fixedSp(20.dp), fontWeight = FontWeight.Bold), maxLines = 1)
        }
    }
}

/** A text size that ignores the font scale, for controls that must keep their size. */
@Composable
private fun fixedSp(size: Dp) = with(LocalDensity.current) { size.toSp() }
