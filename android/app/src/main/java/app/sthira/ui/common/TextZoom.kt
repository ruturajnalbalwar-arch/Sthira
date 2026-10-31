package app.sthira.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import app.sthira.ui.theme.Space
import app.sthira.ui.theme.Sthira
import app.sthira.ui.theme.SthiraType

/**
 * In-app text zoom: A- and A+ buttons with the current level (100% to 200%) between them. The control
 * itself is drawn at a fixed size (dp, not sp) so it never grows out of a bar at 200%. Settings uses the
 * bigger [TextSizeControl]; this compact one is for a screen that opts in through ScreenScaffold.
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
        Modifier.size(MainTarget).semantics { contentDescription = description },
        shape = Neu.ButtonShape, onClick = onClick, enabled = enabled,
        rim = Sthira.colors.text, rimWidth = Neu.StrongEdgeWidth,
    ) {
        // The glyph is not read out: the button's description says what it does.
        Box(Modifier.align(Alignment.Center).clearAndSetSemantics { }) {
            Text(glyph, style = SthiraType.label.copy(fontSize = fixedSp(20.dp), fontWeight = FontWeight.Bold), maxLines = 1)
        }
    }
}

/**
 * The Settings text size control: big "A− Smaller" and "A+ Larger" buttons side by side with the
 * level between them, and a sample line below that grows and shrinks as you tap. The buttons turn
 * grey (still readable) at the smallest and largest steps.
 */
@Composable
fun TextSizeControl(modifier: Modifier = Modifier) {
    val settings = LocalAppSettings.current
    val c = Sthira.colors
    val percent = settings.textScalePercent
    val state = stringResource(R.string.common_text_size_state, percent)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            SizeStepButton(
                glyph = stringResource(R.string.common_text_zoom_out_glyph), label = stringResource(R.string.common_text_smaller_short),
                description = stringResource(R.string.common_text_smaller) + ". " + state,
                enabled = settings.canZoomOut, onClick = settings::zoomOut, modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.common_text_size_percent, percent),
                modifier = Modifier.widthIn(min = 64.dp).clearAndSetSemantics {
                    contentDescription = state
                    liveRegion = LiveRegionMode.Polite
                },
                style = SthiraType.title.copy(fontSize = fixedSp(22.dp)), color = c.text, textAlign = TextAlign.Center, maxLines = 1,
            )
            SizeStepButton(
                glyph = stringResource(R.string.common_text_zoom_in_glyph), label = stringResource(R.string.common_text_larger_short),
                description = stringResource(R.string.common_text_larger) + ". " + state,
                enabled = settings.canZoomIn, onClick = settings::zoomIn, modifier = Modifier.weight(1f),
            )
        }
        NeuSurface(Modifier.fillMaxWidth(), shape = Neu.FieldShape, depth = NeuDepth.Inset) {
            Text(
                stringResource(R.string.common_text_size_sample), style = SthiraType.body, color = c.text,
                modifier = Modifier.padding(Space.m),
            )
        }
    }
}

/** One of the two text size buttons. Its glyph and word are drawn at a fixed size so both fit at 200%. */
@Composable
private fun SizeStepButton(glyph: String, label: String, description: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    NeuSurface(
        modifier.heightIn(min = BigTarget).semantics { contentDescription = description },
        shape = Neu.ButtonShape, onClick = onClick, enabled = enabled,
        rim = Sthira.colors.text, rimWidth = Neu.StrongEdgeWidth,
    ) {
        Column(
            Modifier.align(Alignment.Center).padding(vertical = 6.dp).clearAndSetSemantics { },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(glyph, style = SthiraType.title.copy(fontSize = fixedSp(24.dp), fontWeight = FontWeight.Bold), maxLines = 1)
            Text(label, style = SthiraType.label.copy(fontSize = fixedSp(16.dp)), maxLines = 1)
        }
    }
}

/** A text size that ignores the font scale, for controls that must keep their size. */
@Composable
internal fun fixedSp(size: Dp) = with(LocalDensity.current) { size.toSp() }
