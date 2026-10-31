package app.sthira.ui

import androidx.compose.ui.graphics.Color
import app.sthira.ui.common.Neu
import app.sthira.ui.theme.Contrast
import app.sthira.ui.theme.DarkTokens
import app.sthira.ui.theme.LightTokens
import app.sthira.ui.theme.SthiraColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NeuContrastTest {
    private fun rgb(c: Color): Int = (c.value shr 32).toInt() and 0xFFFFFF

    private fun base(t: SthiraColors) = if (t.isDark) Neu.DARK_BASE else Neu.LIGHT_BASE

    /** Every text token that is drawn on a soft surface (titles, body, labels, tone titles). */
    private fun textTokens(t: SthiraColors) = mapOf(
        "text" to t.text, "muted" to t.muted, "primary" to t.primary,
        "danger" to t.danger, "warn" to t.warn, "success" to t.success,
    )

    @Test
    fun `contrast maths matches the WCAG reference values`() {
        assertEquals(21.0, Contrast.ratio(0x000000, 0xFFFFFF), 1e-9)
        assertEquals(1.0, Contrast.ratio(0x777777, 0x777777), 1e-9)
        assertEquals(4.48, Contrast.ratio(0x777777, 0xFFFFFF), 0.01)
        assertEquals(0x808080, Contrast.over(0xFFFFFF, 0.5, 0x000000))
    }

    @Test
    fun `every text token keeps AA contrast on the soft base colour in both themes`() {
        listOf(LightTokens, DarkTokens).forEach { t ->
            textTokens(t).forEach { (name, color) ->
                val ratio = Contrast.ratio(rgb(color), base(t))
                assertTrue("${if (t.isDark) "dark" else "light"} $name on base = ${"%.2f".format(ratio)}", ratio >= Contrast.AA_TEXT)
            }
        }
    }

    @Test
    fun `every text token keeps AA on the bg and surface tokens in both themes`() {
        listOf(LightTokens, DarkTokens).forEach { t ->
            listOf("bg" to t.bg, "surface" to t.surface).forEach { (ground, g) ->
                textTokens(t).forEach { (name, color) ->
                    val ratio = Contrast.ratio(rgb(color), rgb(g))
                    assertTrue("${if (t.isDark) "dark" else "light"} $name on $ground = ${"%.2f".format(ratio)}", ratio >= Contrast.AA_TEXT)
                }
            }
        }
    }

    @Test
    fun `the palette is black and white with the soft base equal to the screen colour`() {
        assertEquals(Contrast.BLACK, rgb(LightTokens.text))
        assertEquals(Contrast.BLACK, rgb(LightTokens.primary))
        assertEquals(Contrast.WHITE, rgb(LightTokens.surface))
        assertEquals(Contrast.WHITE, rgb(DarkTokens.text))
        assertEquals(Contrast.WHITE, rgb(DarkTokens.primary))
        assertEquals(Neu.LIGHT_BASE, rgb(LightTokens.bg))
        assertEquals(Neu.DARK_BASE, rgb(DarkTokens.bg))
    }

    @Test
    fun `strong borders are at least 3 to 1 on bg and surface`() {
        listOf(LightTokens, DarkTokens).forEach { t ->
            listOf(t.bg, t.surface).forEach { g ->
                val ratio = Contrast.ratio(rgb(t.borderStrong), rgb(g))
                assertTrue("${t.isDark} borderStrong = ${"%.2f".format(ratio)}", ratio >= Contrast.AA_UI)
            }
        }
    }

    @Test
    fun `status colours keep AA on their own tinted fills`() {
        listOf(LightTokens, DarkTokens).forEach { t ->
            listOf(t.text to t.warnBg, t.text to t.successBg, t.danger to t.surface).forEach { (fg, g) ->
                assertTrue(Contrast.ratio(rgb(fg), rgb(g)) >= Contrast.AA_TEXT)
            }
        }
    }

    @Test
    fun `camera overlay panels keep AA for every text token over a black or a white frame`() {
        listOf(LightTokens, DarkTokens).forEach { t ->
            val failures = textTokens(t).mapNotNull { (name, color) ->
                val worst = Contrast.worstOverBackdrop(rgb(color), base(t), Neu.OVERLAY_ALPHA)
                if (worst < Contrast.AA_TEXT) "${if (t.isDark) "dark" else "light"}/$name = ${"%.2f".format(worst)}" else null
            }
            assertTrue("Below 4.5:1 on an overlay panel: $failures", failures.isEmpty())
        }
    }

    @Test
    fun `filled buttons and tinted chips meet AA`() {
        listOf(LightTokens, DarkTokens).forEach { t ->
            assertTrue(Contrast.ratio(rgb(t.onPrimary), rgb(t.primary)) >= Contrast.AA_TEXT)
            assertTrue(Contrast.ratio(rgb(t.onDanger), rgb(t.danger)) >= Contrast.AA_TEXT)
            assertTrue(Contrast.ratio(rgb(t.warn), rgb(t.warnBg)) >= Contrast.AA_TEXT)
            assertTrue(Contrast.ratio(rgb(t.success), rgb(t.successBg)) >= Contrast.AA_TEXT)
        }
    }

    @Test
    fun `outlines of fields, checkboxes and selected chips are at least 3 to 1 against the base`() {
        listOf(LightTokens, DarkTokens).forEach { t ->
            // Muted outlines the fields and unchecked boxes, primary the selected chips and checked boxes,
            // danger an invalid chip. borderStrong also passes 3:1 (checked below).
            listOf(t.muted, t.primary, t.danger).forEach {
                assertTrue("${t.isDark} ${Integer.toHexString(rgb(it))}", Contrast.ratio(rgb(it), base(t)) >= Contrast.AA_UI)
            }
        }
    }

    @Test
    fun `the soft shadows are visibly different from the base so raised and pressed read at a glance`() {
        listOf(
            Triple("light", Neu.LIGHT_HIGHLIGHT to Neu.LIGHT_SHADE, Neu.LIGHT_BASE),
            Triple("dark", Neu.DARK_HIGHLIGHT to Neu.DARK_SHADE, Neu.DARK_BASE),
        ).forEach { (name, shadows, base) ->
            assertTrue("$name highlight", Contrast.ratio(shadows.first, base) >= MIN_SHADOW_STEP)
            assertTrue("$name shade", Contrast.ratio(shadows.second, base) >= MIN_SHADOW_STEP)
        }
    }

    @Test
    fun `a text colour inside the backdrop span is reported as failing`() {
        assertEquals(1.0, Contrast.worstOverBackdrop(0x777777, 0x777777, 0.1), 0.0)
    }

    private companion object {
        /** Not a WCAG rule: the least step between the base and a shadow for the depth to be visible at all. */
        const val MIN_SHADOW_STEP = 1.15
    }
}
