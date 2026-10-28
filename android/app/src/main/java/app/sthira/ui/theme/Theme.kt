package app.sthira.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

private fun schemeFor(t: SthiraColors, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = t.primary, onPrimary = t.onPrimary, background = t.bg, onBackground = t.text,
        surface = t.surface, onSurface = t.text, onSurfaceVariant = t.muted, outline = t.borderStrong,
        error = t.danger, onError = t.onDanger, surfaceVariant = t.surface,
    )
}

/** Light and dark themes from the web tokens. Text sizes are in sp, so the system font scale applies. */
@Composable
fun SthiraTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val tokens = if (dark) DarkTokens else LightTokens
    CompositionLocalProvider(LocalSthiraColors provides tokens) {
        MaterialTheme(colorScheme = schemeFor(tokens, dark), content = content)
    }
}

object Sthira {
    val colors: SthiraColors
        @Composable @ReadOnlyComposable get() = LocalSthiraColors.current
}
