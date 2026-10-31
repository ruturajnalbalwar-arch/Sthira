package app.sthira.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.sthira.ui.theme.Sthira
import app.sthira.ui.theme.SthiraColors

/**
 * The single place that defines how a Sthira surface looks. The name is historical: the app used to
 * have a soft ("neumorphic") shadow look. Older users found its edges too weak, so every surface is
 * now flat with a clear outline:
 *
 * - [NeuDepth.Raised] (cards, outlined buttons): the card colour with a 1.5dp [SthiraColors.borderStrong] edge.
 * - [NeuDepth.Inset] (wells inside a card, fields): the screen colour with a thin [SthiraColors.border] edge.
 * - [NeuDepth.Flat]: the screen colour, no edge.
 *
 * A [fill] (primary or danger button, selected choice) replaces the colour and drops the edge. Pressed
 * surfaces get a dark (or light) wash and a thicker edge; focus gets a 3dp primary ring. Disabled ones
 * turn grey ([SthiraColors.disabled]) with grey text that still keeps AA contrast. Panels over the live
 * camera ([overlay]) are nearly opaque so text stays readable over any video frame.
 */
object Neu {
    /** The screen colours (equal to the bg tokens); also the fill of the camera overlay panels. */
    const val LIGHT_BASE = 0xEDEDED
    const val DARK_BASE = 0x181818

    /** Panels over the camera: nearly opaque so text keeps AA contrast over a black or a white frame. */
    const val OVERLAY_ALPHA = 0.96

    val CardShape = RoundedCornerShape(20.dp)
    val ButtonShape = RoundedCornerShape(16.dp)
    val FieldShape = RoundedCornerShape(14.dp)
    val ChipShape = RoundedCornerShape(50)

    /** The outline of a card or an outlined button. */
    val EdgeWidth = 1.5.dp

    /** The outline of a secondary (outlined) button or a selected item: thicker so it reads as a button. */
    val StrongEdgeWidth = 2.dp

    private fun rgb(v: Int) = Color(0xFF000000 or v.toLong())

    fun base(c: SthiraColors) = rgb(if (c.isDark) DARK_BASE else LIGHT_BASE)
}

enum class NeuDepth { Raised, Inset, Flat }

/**
 * A flat surface with a clear edge. Pass [onClick] only for tappable things: they darken while
 * pressed and get a focus ring. [fill] colours a primary or danger button; [rim] replaces the edge
 * colour (and [rimWidth] its width); [overlay] is for panels over the camera. [elevation] is kept for
 * source compatibility and no longer draws anything.
 */
@Composable
fun NeuSurface(
    modifier: Modifier = Modifier,
    shape: Shape = Neu.CardShape,
    depth: NeuDepth = NeuDepth.Raised,
    @Suppress("UNUSED_PARAMETER") elevation: Dp = 8.dp,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    enabled: Boolean = true,
    fill: Color? = null,
    contentColor: Color? = null,
    rim: Color? = null,
    rimWidth: Dp = Neu.EdgeWidth,
    overlay: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = Sthira.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val look = surfaceLook(c, depth, enabled, fill, rim, rimWidth, overlay)
    val active = onClick != null && enabled
    val styled = modifier
        .clip(shape)
        .background(look.fill, shape)
        .then(if (active && pressed) Modifier.background(c.text.copy(alpha = PRESSED_WASH), shape) else Modifier)
        .then(if (look.edge != null) Modifier.border(BorderStroke(look.edgeWidth, look.edge), shape) else Modifier)
        .then(if (active && pressed) Modifier.border(BorderStroke(PRESSED_EDGE, c.text), shape) else Modifier)
        .then(if (focused) Modifier.border(BorderStroke(FOCUS_RING, c.primary), shape) else Modifier)
    val tappable = if (onClick == null) styled else styled.clickable(
        interactionSource = source, indication = null, enabled = enabled, role = Role.Button,
        onClickLabel = onClickLabel, onClick = onClick,
    )
    val text = if (enabled) contentColor ?: c.text else c.onDisabled
    CompositionLocalProvider(LocalContentColor provides text) {
        Box(tappable, content = content)
    }
}

private data class SurfaceLook(val fill: Color, val edge: Color?, val edgeWidth: Dp)

private fun surfaceLook(
    c: SthiraColors, depth: NeuDepth, enabled: Boolean, fill: Color?, rim: Color?, rimWidth: Dp, overlay: Boolean,
): SurfaceLook = when {
    overlay -> SurfaceLook(
        (fill ?: Neu.base(c)).copy(alpha = Neu.OVERLAY_ALPHA.toFloat()), rim ?: Color.White.copy(alpha = OVERLAY_RIM),
        if (rim != null) rimWidth else 1.dp,
    )
    // Disabled: grey fill and a thin grey edge, whatever the tone was. The text colour is set by the caller.
    !enabled -> SurfaceLook(c.disabled, c.border, Neu.EdgeWidth)
    fill != null -> SurfaceLook(fill, rim, rimWidth)
    depth == NeuDepth.Raised -> SurfaceLook(c.surface, rim ?: c.borderStrong, rimWidth)
    depth == NeuDepth.Inset -> SurfaceLook(c.bg, rim ?: c.border, rimWidth)
    else -> SurfaceLook(c.bg, rim, rimWidth)
}

/** A full-width bar (top app bar): the card colour with a strong line along its bottom edge. */
@Composable
fun NeuBar(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = Sthira.colors
    Box(
        modifier
            .background(c.surface, RectangleShape)
            .drawBehind {
                val y = size.height - BAR_LINE.toPx() / 2
                drawLine(c.borderStrong, Offset(0f, y), Offset(size.width, y), BAR_LINE.toPx())
            },
    ) {
        CompositionLocalProvider(LocalContentColor provides c.text) { content() }
    }
}

/** The flat screen colour behind everything. */
@Composable
fun NeuBackdrop(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Sthira.colors.bg))
}

private val FOCUS_RING = 3.dp
private val PRESSED_EDGE = 2.5.dp
private val BAR_LINE = 1.5.dp
private const val PRESSED_WASH = 0.14f
private const val OVERLAY_RIM = 0.16f
