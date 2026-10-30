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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.sthira.ui.theme.Sthira
import app.sthira.ui.theme.SthiraColors

/**
 * The single place that defines Sthira's soft ("neumorphic") look: every surface is the same colour
 * as the screen and only looks raised or pressed in because of a light shadow on one side and a
 * dark shadow on the other.
 *
 * Shadows alone make poor affordances, so the rules are: text and icons keep the AA contrast of the
 * plain tokens (NeuContrastTest), selected and invalid states also change an outline, a check mark
 * or text (never the shadow alone), fields and checkboxes keep a 3:1 outline, and panels over the
 * live camera are nearly opaque because shadows cannot separate a panel from video.
 */
object Neu {
    const val LIGHT_BASE = 0xE8ECEA
    const val DARK_BASE = 0x1B2321
    const val LIGHT_HIGHLIGHT = 0xFFFFFF
    const val DARK_HIGHLIGHT = 0x2B3835
    const val LIGHT_SHADE = 0xB4BFBB
    const val DARK_SHADE = 0x090D0C

    /** Panels over the camera: nearly opaque so text keeps AA contrast over a black or a white frame. */
    const val OVERLAY_ALPHA = 0.96

    val CardShape = RoundedCornerShape(22.dp)
    val ButtonShape = RoundedCornerShape(18.dp)
    val FieldShape = RoundedCornerShape(16.dp)
    val ChipShape = RoundedCornerShape(50)

    private fun rgb(v: Int) = Color(0xFF000000 or v.toLong())

    fun base(c: SthiraColors) = rgb(if (c.isDark) DARK_BASE else LIGHT_BASE)
    fun highlight(c: SthiraColors) = rgb(if (c.isDark) DARK_HIGHLIGHT else LIGHT_HIGHLIGHT)
    fun shade(c: SthiraColors) = rgb(if (c.isDark) DARK_SHADE else LIGHT_SHADE)
}

enum class NeuDepth { Raised, Inset, Flat }

/**
 * A soft surface. Pass [onClick] only for tappable things: they sink in while pressed and get a
 * focus ring. [fill] colours a primary or danger button; [overlay] is for panels over the camera.
 */
@Composable
fun NeuSurface(
    modifier: Modifier = Modifier,
    shape: Shape = Neu.CardShape,
    depth: NeuDepth = NeuDepth.Raised,
    elevation: Dp = 8.dp,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    enabled: Boolean = true,
    fill: Color? = null,
    contentColor: Color? = null,
    rim: Color? = null,
    rimWidth: Dp = 1.5.dp,
    overlay: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = Sthira.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val effective = when {
        !enabled || depth == NeuDepth.Flat -> NeuDepth.Flat
        onClick != null && pressed -> NeuDepth.Inset
        else -> depth
    }
    val color = (fill ?: Neu.base(c)).let { if (overlay) it.copy(alpha = Neu.OVERLAY_ALPHA.toFloat()) else it }
    val styled = modifier
        .drawBehind { if (effective == NeuDepth.Raised) raisedShadows(shape, c, elevation.toPx(), overlay) }
        .clip(shape)
        .background(color, shape)
        .drawBehind { if (effective == NeuDepth.Inset) insetShadows(shape, c, elevation.toPx()) }
        .then(if (overlay) Modifier.border(BorderStroke(1.dp, Color.White.copy(alpha = OVERLAY_RIM)), shape) else Modifier)
        .then(if (rim != null) Modifier.border(BorderStroke(rimWidth, rim), shape) else Modifier)
        .then(if (focused) Modifier.border(BorderStroke(3.dp, c.primary), shape) else Modifier)
    val tappable = if (onClick == null) styled else styled.clickable(
        interactionSource = source, indication = null, enabled = enabled, role = Role.Button,
        onClickLabel = onClickLabel, onClick = onClick,
    )
    val text = (contentColor ?: c.text).let { if (enabled) it else it.copy(alpha = DISABLED_ALPHA) }
    CompositionLocalProvider(LocalContentColor provides text) {
        Box(tappable, content = content)
    }
}

/** A full-width bar (top app bar): no corners at the screen edge, a small soft shadow below. */
@Composable
fun NeuBar(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    NeuSurface(modifier, shape = androidx.compose.ui.graphics.RectangleShape, elevation = 4.dp, content = content)
}

/** The flat screen colour every soft surface is cut from. */
@Composable
fun NeuBackdrop(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Neu.base(Sthira.colors)))
}

private fun DrawScope.shapePath(shape: Shape): Path = when (val outline = shape.createOutline(size, layoutDirection, this)) {
    is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
    is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
    is Outline.Generic -> outline.path
}

/** Draws [path] in [fill] with a blurred shadow of colour [shadow] shifted by (dx, dy). */
private fun DrawScope.castShadow(path: Path, fill: Color, shadow: Color, blurPx: Float, dx: Float, dy: Float) {
    drawIntoCanvas { canvas ->
        val paint = Paint()
        paint.asFrameworkPaint().apply {
            isAntiAlias = true
            color = fill.toArgb()
            setShadowLayer(blurPx, dx, dy, shadow.toArgb())
        }
        canvas.drawPath(path, paint)
    }
}

/** Light from the top-left: a light shadow up-left and a dark one down-right make the surface look raised. */
private fun DrawScope.raisedShadows(shape: Shape, c: SthiraColors, offsetPx: Float, overlay: Boolean) {
    val path = shapePath(shape)
    val base = Neu.base(c)
    val blur = offsetPx * BLUR_PER_OFFSET
    if (!overlay) castShadow(path, base, Neu.highlight(c), blur, -offsetPx, -offsetPx)
    castShadow(path, base, Neu.shade(c), blur, offsetPx, offsetPx)
}

/** The same two shadows cast from a frame around the shape, so they fall inside it and look pressed in. */
private fun DrawScope.insetShadows(shape: Shape, c: SthiraColors, offsetPx: Float) {
    val inner = shapePath(shape)
    val pad = offsetPx * BLUR_PER_OFFSET * FRAME_PAD
    val frame = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(Rect(-pad, -pad, size.width + pad, size.height + pad))
        addPath(inner)
    }
    val base = Neu.base(c)
    val blur = offsetPx * BLUR_PER_OFFSET
    clipPath(inner) {
        castShadow(frame, base, Neu.shade(c), blur, offsetPx, offsetPx)
        castShadow(frame, base, Neu.highlight(c), blur, -offsetPx, -offsetPx)
    }
}

private const val BLUR_PER_OFFSET = 1.6f
private const val FRAME_PAD = 3f
private const val OVERLAY_RIM = 0.16f
private const val DISABLED_ALPHA = 0.55f
