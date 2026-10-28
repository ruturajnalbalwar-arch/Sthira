package app.sthira.ui.common

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.sthira.ui.theme.LocalReducedMotion
import app.sthira.ui.theme.Sthira
import app.sthira.ui.theme.SthiraColors

/**
 * The single place that defines Sthira's "liquid glass" look.
 *
 * Fill alphas are chosen so that every text token on the glass keeps >= 4.5:1 contrast over ANY
 * backdrop, from a black to a white camera frame (see GlassContrastTest). That makes the glass
 * fairly dense; it still reads as glass through the highlight rim, soft shadow and tint.
 *
 * Blur: Compose cannot sample and blur what is drawn *behind* a composable (a PreviewView in
 * particular), so over the camera the glass relies on this dense translucent scrim. Behind the home
 * cards, the decorative background layer itself is blurred with RenderEffect on API 31+
 * ([GlassBackdrop]); below API 31 that blur is a no-op and the plain gradient shows.
 */
object Glass {
    const val DARK_FILL = 0x0F1413
    const val DARK_ALPHA = 0.88
    const val LIGHT_FILL = 0xFFFFFF
    const val LIGHT_ALPHA = 0.90
    /** Bars have text scrolling underneath them, so they are denser than cards (always above the AA minimum). */
    const val BAR_ALPHA = 0.96

    val CardShape = RoundedCornerShape(22.dp)
    val ChipShape = RoundedCornerShape(50)

    fun fill(c: SthiraColors, dense: Boolean = false): Color {
        val alpha = if (dense) BAR_ALPHA else if (c.isDark) DARK_ALPHA else LIGHT_ALPHA
        val rgb = if (c.isDark) DARK_FILL else LIGHT_FILL
        return Color(0xFF000000 or rgb.toLong()).copy(alpha = alpha.toFloat())
    }

    /** The 1dp rim: lighter at the top edge, like light catching the glass. */
    fun rim(c: SthiraColors, bright: Boolean): Brush {
        val top = if (c.isDark) 0.30f else 0.95f
        val bottom = if (c.isDark) 0.06f else 0.35f
        val boost = if (bright) 0.25f else 0f
        return Brush.verticalGradient(
            listOf(Color.White.copy(alpha = (top + boost).coerceAtMost(1f)), Color.White.copy(alpha = bottom)),
        )
    }
}

/**
 * Glass panel for bars, cards and overlays. Pass [onClick] only for tappable things: those get the
 * pressed state (scale 0.98 and a brighter rim) and a focus ring; static panels never react.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = Glass.CardShape,
    elevation: Dp = 6.dp,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    dense: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = Sthira.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val reduced = LocalReducedMotion.current
    val target = if (onClick != null && pressed) PRESSED_SCALE else 1f
    val scale by animateFloatAsState(target, Sthira.motion(), label = "glassPress")
    val base = modifier
        .scale(if (reduced) 1f else scale)
        .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape, ambientColor = SHADOW, spotColor = SHADOW) else Modifier)
        .clip(shape)
        .background(Glass.fill(c, dense), shape)
        .border(BorderStroke(1.dp, Glass.rim(c, bright = pressed)), shape)
        .then(if (focused) Modifier.border(BorderStroke(3.dp, c.primary), shape) else Modifier)
    val clickable = if (onClick == null) base else base.clickable(
        interactionSource = source, indication = LocalIndication.current, role = Role.Button,
        onClickLabel = onClickLabel, onClick = onClick,
    )
    CompositionLocalProvider(LocalContentColor provides c.text) {
        Box(clickable, content = content)
    }
}

/** A full-width glass bar (top app bar, bottom action bar): no corners at the screen edge. */
@Composable
fun GlassBar(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    GlassSurface(modifier, shape = RectangleShape, elevation = 0.dp, dense = true, content = content)
}

/**
 * Subtle teal background that the glass sits on. On API 31+ the soft colour fields are really
 * blurred (RenderEffect via Modifier.blur); below that they are drawn without blur.
 */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier) {
    val c = Sthira.colors
    val base = if (c.isDark) listOf(c.bg, Color(0xFF0E1C1A)) else listOf(c.bg, Color(0xFFE6F1EE))
    val glow = c.primary.copy(alpha = if (c.isDark) 0.16f else 0.12f)
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(base))) {
        val blurred = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(BACKDROP_BLUR) else Modifier
        Box(
            Modifier.fillMaxSize().then(blurred).background(
                Brush.radialGradient(listOf(glow, Color.Transparent), radius = GLOW_RADIUS),
            ),
        )
    }
}

private const val PRESSED_SCALE = 0.98f
private const val GLOW_RADIUS = 1400f
private val BACKDROP_BLUR = 48.dp
private val SHADOW = Color(0x66000000)
