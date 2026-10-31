package app.sthira.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.sthira.settings.ThemeMode

private fun schemeFor(t: SthiraColors, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = t.primary, onPrimary = t.onPrimary, primaryContainer = t.primary, onPrimaryContainer = t.onPrimary,
        background = t.bg, onBackground = t.text, surface = t.bg, onSurface = t.text,
        surfaceVariant = t.surface, onSurfaceVariant = t.muted, surfaceContainer = t.surface,
        outline = t.borderStrong, outlineVariant = t.border, error = t.danger, onError = t.onDanger,
    )
}

/** True when the user turned animations off (Developer options or Accessibility "Remove animations"). */
val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * Light and dark themes from the tokens. Text sizes are in sp, so the system font scale applies, and
 * [textScale] (the in-app A-/A+ zoom) multiplies it: 1.5 at a phone setting of 1.3 gives 1.95.
 */
@Composable
fun SthiraTheme(dark: Boolean = isSystemInDarkTheme(), textScale: Float = 1f, content: @Composable () -> Unit) {
    val tokens = if (dark) DarkTokens else LightTokens
    val resolver = LocalContext.current.contentResolver
    val reduced = remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val system = LocalDensity.current
    val density = remember(system, textScale) { Density(system.density, system.fontScale * textScale) }
    CompositionLocalProvider(
        LocalSthiraColors provides tokens, LocalReducedMotion provides reduced, LocalDensity provides density,
    ) {
        MaterialTheme(colorScheme = schemeFor(tokens, dark), typography = SthiraTypography, content = content)
    }
}

/** The theme for the user's chosen [mode] (light, dark or follow the phone). */
@Composable
fun SthiraTheme(mode: ThemeMode, textScale: Float, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    SthiraTheme(dark = dark, textScale = textScale, content = content)
}

object Sthira {
    val colors: SthiraColors
        @Composable @ReadOnlyComposable get() = LocalSthiraColors.current

    /** A short, subtle transition, or none at all when animations are off. */
    @Composable
    @ReadOnlyComposable
    fun <T> motion(durationMs: Int = SHORT_MS): FiniteAnimationSpec<T> =
        if (LocalReducedMotion.current) snap() else tween(durationMs)

    const val SHORT_MS = 180
}
