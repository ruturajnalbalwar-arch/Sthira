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
    val isDark: Boolean = false,
)

val LightTokens = SthiraColors(
    bg = Color(0xFFFAF7F2),
    surface = Color(0xFFFFFFFF),
    text = Color(0xFF1F1A14),
    muted = Color(0xFF54493C),
    border = Color(0xFFE2DACD),
    borderStrong = Color(0xFF8F806C),
    primary = Color(0xFF8A3D00),
    onPrimary = Color(0xFFFFFFFF),
    danger = Color(0xFFA8102F),
    onDanger = Color(0xFFFFFFFF),
    warn = Color(0xFF5E4A00),
    warnBg = Color(0xFFFFF6CC),
    success = Color(0xFF0F5C38),
    successBg = Color(0xFFE6F4EC),
)

val DarkTokens = SthiraColors(
    bg = Color(0xFF15110C),
    surface = Color(0xFF1F1912),
    text = Color(0xFFF2ECE4),
    muted = Color(0xFFBDB2A3),
    border = Color(0xFF3A3128),
    borderStrong = Color(0xFF7A6D5E),
    primary = Color(0xFFFFB15C),
    onPrimary = Color(0xFF2A1500),
    danger = Color(0xFFFF8A94),
    onDanger = Color(0xFF2A0008),
    warn = Color(0xFFF2CC4D),
    warnBg = Color(0xFF2A2410),
    success = Color(0xFF5CCB8F),
    successBg = Color(0xFF12261B),
    isDark = true,
)

val LocalSthiraColors = staticCompositionLocalOf { LightTokens }
