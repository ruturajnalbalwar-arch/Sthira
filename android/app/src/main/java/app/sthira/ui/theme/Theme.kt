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

/** Light and dark themes from the web tokens. Text sizes are in sp, so the system font scale applies. */
@Composable
fun SthiraTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val tokens = if (dark) DarkTokens else LightTokens
    val resolver = LocalContext.current.contentResolver
    val reduced = remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    CompositionLocalProvider(LocalSthiraColors provides tokens, LocalReducedMotion provides reduced) {
        MaterialTheme(colorScheme = schemeFor(tokens, dark), typography = SthiraTypography, content = content)
    }
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
