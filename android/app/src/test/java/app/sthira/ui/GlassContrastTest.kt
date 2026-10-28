package app.sthira.ui

import app.sthira.ui.common.Glass
import app.sthira.ui.theme.Contrast
import app.sthira.ui.theme.DarkTokens
import app.sthira.ui.theme.LightTokens
import app.sthira.ui.theme.SthiraColors
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassContrastTest {
    private fun rgb(c: Color): Int = (c.value shr 32).toInt() and 0xFFFFFF

    /** Every text token that is drawn on glass (titles, body, labels, tone titles). */
    private fun textOnGlass(t: SthiraColors) = mapOf(
        "text" to t.text, "muted" to t.muted, "primary" to t.primary,
        "danger" to t.danger, "warn" to t.warn, "success" to t.success,
    )

    private fun check(name: String, t: SthiraColors, fill: Int, alpha: Double) {
        val failures = textOnGlass(t).mapNotNull { (token, color) ->
            val worst = Contrast.worstOnGlass(rgb(color), fill, alpha)
            if (worst < Contrast.AA_TEXT) "$name/$token = ${"%.2f".format(worst)}" else null
        }
        assertTrue("Below 4.5:1 on glass over black/white: $failures", failures.isEmpty())
    }

    @Test
    fun `contrast maths matches the WCAG reference values`() {
        assertEquals(21.0, Contrast.ratio(0x000000, 0xFFFFFF), 1e-9)
        assertEquals(1.0, Contrast.ratio(0x777777, 0x777777), 1e-9)
        assertEquals(4.48, Contrast.ratio(0x777777, 0xFFFFFF), 0.01)
        assertEquals(0x808080, Contrast.over(0xFFFFFF, 0.5, 0x000000))
    }

    @Test
    fun `dark glass keeps every text token at AA over white and black camera frames`() {
        check("dark", DarkTokens, Glass.DARK_FILL, Glass.DARK_ALPHA)
    }

    @Test
    fun `light glass keeps every text token at AA over white and black camera frames`() {
        check("light", LightTokens, Glass.LIGHT_FILL, Glass.LIGHT_ALPHA)
    }

    @Test
    fun `dense bar glass keeps AA in both themes`() {
        check("dark bar", DarkTokens, Glass.DARK_FILL, Glass.BAR_ALPHA)
        check("light bar", LightTokens, Glass.LIGHT_FILL, Glass.BAR_ALPHA)
    }

    @Test
    fun `text on the glass fill matches the token colours`() {
        assertEquals(Glass.DARK_FILL, rgb(Glass.fill(DarkTokens)))
        assertEquals(Glass.LIGHT_FILL, rgb(Glass.fill(LightTokens)))
    }

    @Test
    fun `plain background text and primary buttons meet AA`() {
        listOf(LightTokens, DarkTokens).forEach { t ->
            val bg = rgb(t.bg)
            listOf(t.text, t.muted, t.primary, t.danger, t.success, t.warn).forEach {
                assertTrue("${t.isDark} ${Integer.toHexString(rgb(it))}", Contrast.ratio(rgb(it), bg) >= Contrast.AA_TEXT)
            }
            assertTrue(Contrast.ratio(rgb(t.onPrimary), rgb(t.primary)) >= Contrast.AA_TEXT)
            assertTrue(Contrast.ratio(rgb(t.onDanger), rgb(t.danger)) >= Contrast.AA_TEXT)
            assertTrue(Contrast.ratio(rgb(t.warn), rgb(t.warnBg)) >= Contrast.AA_TEXT)
            assertTrue(Contrast.ratio(rgb(t.success), rgb(t.successBg)) >= Contrast.AA_TEXT)
            // Outlines of chips, checkboxes and fields use the muted token: 3:1 for UI components.
            // (borderStrong is only 2.85:1 in the light theme, so it is used for decoration only.)
            assertTrue(Contrast.ratio(rgb(t.muted), bg) >= Contrast.AA_UI)
        }
    }

    @Test
    fun `a text colour inside the backdrop span is reported as failing`() {
        assertEquals(1.0, Contrast.worstOnGlass(0x777777, 0x777777, 0.1), 0.0)
    }
}
