package app.sthira.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colour tokens, copied from web/src/app/globals.css so both apps look the same. */
@Immutable
data class SthiraColors(
    val bg: Color,
    val surface: Color,
    val text: Color,
    val muted: Color,
    val border: Color,
    val borderStrong: Color,
    val primary: Color,
    val onPrimary: Color,
    val danger: Color,
    val onDanger: Color,
    val warn: Color,
    val warnBg: Color,
    val success: Color,
    val successBg: Color,
)

val LightTokens = SthiraColors(
    bg = Color(0xFFF7F7F4),
    surface = Color(0xFFFFFFFF),
    text = Color(0xFF1A1F1E),
    muted = Color(0xFF4A5553),
    border = Color(0xFFD5D9D6),
    borderStrong = Color(0xFF8A9592),
    primary = Color(0xFF0F6E66),
    onPrimary = Color(0xFFFFFFFF),
    danger = Color(0xFFB42318),
    onDanger = Color(0xFFFFFFFF),
    warn = Color(0xFF7A4F00),
    warnBg = Color(0xFFFFF4D6),
    success = Color(0xFF146C43),
    successBg = Color(0xFFE6F4EC),
)

val DarkTokens = SthiraColors(
    bg = Color(0xFF0F1413),
    surface = Color(0xFF172020),
    text = Color(0xFFE8ECEA),
    muted = Color(0xFFA9B4B1),
    border = Color(0xFF2C3836),
    borderStrong = Color(0xFF5E6D6A),
    primary = Color(0xFF4FC1B5),
    onPrimary = Color(0xFF04201D),
    danger = Color(0xFFF97066),
    onDanger = Color(0xFF1A0503),
    warn = Color(0xFFF5B83D),
    warnBg = Color(0xFF2A2210),
    success = Color(0xFF5CCB8F),
    successBg = Color(0xFF12261B),
)

val LocalSthiraColors = staticCompositionLocalOf { LightTokens }
