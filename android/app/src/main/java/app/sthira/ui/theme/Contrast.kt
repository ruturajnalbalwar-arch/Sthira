package app.sthira.ui.theme

import kotlin.math.pow

/**
 * WCAG 2.x contrast maths on 0xRRGGBB colours, plus the alpha compositing a translucent
 * panel fill gets on top of an unknown backdrop (the live camera). Pure Kotlin so it runs in plain JVM unit tests.
 */
object Contrast {
    const val AA_TEXT = 4.5
    const val AA_UI = 3.0

    private fun channel(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    fun luminance(rgb: Int): Double {
        val r = channel(rgb shr 16 and 0xFF)
        val g = channel(rgb shr 8 and 0xFF)
        val b = channel(rgb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    fun ratio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** [fill] at [alpha] drawn over [backdrop] (source-over in sRGB, as Android composites). */
    fun over(fill: Int, alpha: Double, backdrop: Int): Int {
        fun mix(shift: Int): Int {
            val f = fill shr shift and 0xFF
            val b = backdrop shr shift and 0xFF
            return Math.round(alpha * f + (1 - alpha) * b).toInt().coerceIn(0, 255)
        }
        return (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    /** The lowest contrast [text] gets on the panel over any backdrop between black and white. */
    fun worstOverBackdrop(text: Int, fill: Int, alpha: Double): Double {
        val onBlack = over(fill, alpha, BLACK)
        val onWhite = over(fill, alpha, WHITE)
        // Composite luminance moves monotonically between the two extremes. If the text's luminance
        // falls inside that span, some grey backdrop matches it exactly: contrast 1.
        val lt = luminance(text)
        if ((lt > luminance(onBlack)) != (lt > luminance(onWhite))) return 1.0
        return minOf(ratio(text, onBlack), ratio(text, onWhite))
    }

    const val BLACK = 0x000000
    const val WHITE = 0xFFFFFF
}
