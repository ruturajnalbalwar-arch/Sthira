package app.sthira.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colour tokens: a black-and-white palette. Light is black on a light grey screen with white cards;
 * dark is white on near-black with dark grey cards. Cards and buttons are flat: their edge is a
 * [borderStrong] outline (3:1 or more), never a shadow. [disabled] and [onDisabled] are the grey fill
 * and text of a control that cannot be used yet: clearly grey, but the text is still AA readable.
 * Danger, warn and success keep a muted hue but always come with an icon and a word.
 * NeuContrastTest checks every text pair against WCAG AA.
 */
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
    val disabled: Color,
    val onDisabled: Color,
    val isDark: Boolean = false,
)

val LightTokens = SthiraColors(
    bg = Color(0xFFEDEDED),
    surface = Color(0xFFFFFFFF),
    text = Color(0xFF000000),
    muted = Color(0xFF444444),
    border = Color(0xFFD0D0D0),
    borderStrong = Color(0xFF5E5E5E),
    primary = Color(0xFF000000),
    onPrimary = Color(0xFFFFFFFF),
    danger = Color(0xFF9B2C2C),
    onDanger = Color(0xFFFFFFFF),
    warn = Color(0xFF5C4A12),
    warnBg = Color(0xFFF5EFD9),
    success = Color(0xFF2F5D45),
    successBg = Color(0xFFE3EFE8),
    disabled = Color(0xFFD6D6D6),
    onDisabled = Color(0xFF444444),
)

val DarkTokens = SthiraColors(
    bg = Color(0xFF181818),
    surface = Color(0xFF242424),
    text = Color(0xFFFFFFFF),
    muted = Color(0xFFBDBDBD),
    border = Color(0xFF3A3A3A),
    borderStrong = Color(0xFF9A9A9A),
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    danger = Color(0xFFF2A0A0),
    onDanger = Color(0xFF000000),
    warn = Color(0xFFE6D08A),
    warnBg = Color(0xFF2E2A1C),
    success = Color(0xFF9ED2B4),
    successBg = Color(0xFF1C2A22),
    disabled = Color(0xFF333333),
    onDisabled = Color(0xFFBDBDBD),
    isDark = true,
)

val LocalSthiraColors = staticCompositionLocalOf { LightTokens }
