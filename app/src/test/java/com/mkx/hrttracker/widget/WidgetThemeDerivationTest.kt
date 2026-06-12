package com.mkx.hrttracker.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import com.mkx.hrttracker.ui.theme.DefaultSeedColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WidgetThemeDerivationTest {

    private val seeds = listOf(
        Color(0xFF6476A5), // DefaultSeedColor
        Color(0xFFB3261E), Color(0xFF386A20), Color(0xFF7D5260), Color(0xFF006A6A),
    )

    private fun toneOf(color: Color): Double = Hct.fromInt(color.toArgb()).tone

    // The pre-customization derivation (relative ±tone on secondaryContainer), kept
    // here as the executable definition of "today's output". WHY: the default
    // appearance is contractually pixel-equivalent to the shipped tinted theme.
    private fun legacyAdjust(input: Color, lightAdj: Double, darkAdj: Double): Color {
        val hct = Hct.fromInt(input.toArgb())
        val adj = if (hct.tone > 50) lightAdj else darkAdj
        return Color(Hct.from(hct.hue, hct.chroma, (hct.tone + adj).coerceIn(0.0, 100.0)).toInt())
    }

    // What the launcher shows: card drawn at 0.85 over the shell (CONTAINER_ALPHA_FACTOR).
    private fun composite(top: Color, bottom: Color, alpha: Float): Color = Color(
        red = top.red * alpha + bottom.red * (1 - alpha),
        green = top.green * alpha + bottom.green * (1 - alpha),
        blue = top.blue * alpha + bottom.blue * (1 - alpha),
    )

    private fun assertColorClose(expected: Color, actual: Color, channelTolerance: Int = 1) {
        val e = expected.toArgb(); val a = actual.toArgb()
        for (shift in intArrayOf(16, 8, 0)) {
            val diff = abs(((e shr shift) and 0xFF) - ((a shr shift) and 0xFF))
            assertTrue("channel@$shift diff=$diff expected=${"%08X".format(e)} actual=${"%08X".format(a)}", diff <= channelTolerance)
        }
    }

    @Test
    fun `anchor vibrancy reproduces legacy derivation for shell and card`() {
        for (seed in seeds) {
            val (light, dark) = seededWidgetColorSchemes(seed)
            val l = deriveWidgetSurfaces(
                secondaryContainer = light.secondaryContainer, onSurface = light.onSurface,
                onSurfaceVariant = light.onSurfaceVariant,
                schemeOutlineVariant = light.outlineVariant,
                backgroundHue = null, vibrancy = WidgetAppearance.DEFAULT_VIBRANCY, dark = false,
            )
            assertColorClose(legacyAdjust(light.secondaryContainer, 4.0, -10.0), l.shell)
            assertColorClose(legacyAdjust(light.secondaryContainer, 0.0, 0.0), l.card)
            assertColorClose(legacyAdjust(light.secondaryContainer, -6.0, 0.0), l.control)
            assertColorClose(light.onSurface, l.onSurface)               // no lift at anchor
            assertColorClose(light.onSurfaceVariant, l.onSurfaceVariant)
            // outlineVariant short-circuits to the scheme color at the anchor (u==0):
            // EXACT argb equality, not tolerance — the early return guarantees it.
            assertEquals(light.outlineVariant.toArgb(), l.outlineVariant.toArgb())

            val d = deriveWidgetSurfaces(
                secondaryContainer = dark.secondaryContainer, onSurface = dark.onSurface,
                onSurfaceVariant = dark.onSurfaceVariant,
                schemeOutlineVariant = dark.outlineVariant,
                backgroundHue = null, vibrancy = WidgetAppearance.DEFAULT_VIBRANCY, dark = true,
            )
            assertColorClose(legacyAdjust(dark.secondaryContainer, 4.0, -10.0), d.shell)
            assertColorClose(legacyAdjust(dark.secondaryContainer, 0.0, 0.0), d.card)
            // Dark control: approved change — tinted cardTone+6, NOT today's neutral
            // surfaceVariant. Assert the new contract instead of equivalence.
            assertEquals(toneOf(d.card) + 6.0, toneOf(d.control), 0.6)
            assertEquals(dark.outlineVariant.toArgb(), d.outlineVariant.toArgb())
        }
    }

    @Test
    fun `zero vibrancy is neutral and full vibrancy boosts chroma`() {
        val (light, dark) = seededWidgetColorSchemes(seeds[0])
        val neutral = deriveWidgetSurfaces(light.secondaryContainer, light.onSurface, light.onSurfaceVariant, light.outlineVariant, null, 0f, dark = false)
        // Tolerance 3.0: the HCT solver leaves a ~2.75 chroma residual at near-white
        // tones even for a requested chroma of 0 — solver noise, not derivation error.
        assertEquals(0.0, Hct.fromInt(neutral.shell.toArgb()).chroma, 3.0)
        assertEquals(94.0, toneOf(neutral.shell), 0.6) // tones hold below the anchor

        // The 3.0 solver tolerance above can't distinguish "driven to 0" from "stuck
        // small" — assert the below-anchor ramp actually climbs between v=0 and v=0.2.
        val lowV = deriveWidgetSurfaces(light.secondaryContainer, light.onSurface, light.onSurfaceVariant, light.outlineVariant, null, 0.2f, dark = false)
        assertTrue(
            "below-anchor chroma must climb with v",
            Hct.fromInt(lowV.shell.toArgb()).chroma > Hct.fromInt(neutral.shell.toArgb()).chroma + 2,
        )

        val anchorD = deriveWidgetSurfaces(dark.secondaryContainer, dark.onSurface, dark.onSurfaceVariant, dark.outlineVariant, null, WidgetAppearance.DEFAULT_VIBRANCY, dark = true)
        val fullD = deriveWidgetSurfaces(dark.secondaryContainer, dark.onSurface, dark.onSurfaceVariant, dark.outlineVariant, null, 1f, dark = true)
        assertTrue(
            "v=1 chroma must exceed anchor chroma",
            Hct.fromInt(fullD.shell.toArgb()).chroma > Hct.fromInt(anchorD.shell.toArgb()).chroma + 5,
        )
        assertEquals(35.0, toneOf(fullD.shell), 0.6) // dark ceiling
    }

    @Test
    fun `contrast invariants hold across hue x vibrancy sweep`() {
        // Encodes the spec's contrast guarantee (not specific hexes):
        //  - onSurface ΔTone >= 50 against shell and the COMPOSITED card, at all vibrancy
        //  - onSurfaceVariant ΔTone >= 50 at/below the anchor
        //  - accepted dark floors at v=1: >= 44 on cards, >= 36 on control pills
        //  - shell/card tone safe bands: light >= 78, dark <= 45
        val (light, dark) = seededWidgetColorSchemes(seeds[0])
        val eps = 0.75
        for (hue in 0 until 360 step 15) {
            for (v in floatArrayOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f)) {
                val l = deriveWidgetSurfaces(light.secondaryContainer, light.onSurface, light.onSurfaceVariant, light.outlineVariant, hue.toFloat(), v, dark = false)
                val lCard = composite(l.card, l.shell, 0.85f)
                assertTrue(abs(toneOf(l.onSurface) - toneOf(l.shell)) >= 50 - eps)
                assertTrue(abs(toneOf(l.onSurface) - toneOf(lCard)) >= 50 - eps)
                if (v <= WidgetAppearance.DEFAULT_VIBRANCY) {
                    assertTrue(abs(toneOf(l.onSurfaceVariant) - toneOf(lCard)) >= 50 - eps)
                } else {
                    assertTrue(abs(toneOf(l.onSurfaceVariant) - toneOf(lCard)) >= 44 - eps)
                }
                assertTrue(toneOf(l.shell) >= 78 - eps && toneOf(lCard) >= 78 - 4 - eps)
                // Round-2: light card-vs-shell separation (COMPOSITED) must be visible.
                // Anchor value ≈3.4 (measured min across the grid ≈3.16); at v=1 the
                // deeper −9 card delta lands ≈7.65 (measured ≈7.47). Floors are just
                // under the observed minima.
                val lightSep = abs(toneOf(lCard) - toneOf(l.shell))
                assertTrue("light card-vs-shell sep $lightSep < 3 at v=$v hue=$hue", lightSep >= 3 - eps)
                if (v == 1f) {
                    assertTrue("light card-vs-shell sep $lightSep < 7 at v=1 hue=$hue", lightSep >= 7 - eps)
                }
                // Round-2: tinted outlineVariant must keep contrast vs shell. Measured
                // light min ≈20.19 (near the anchor); floor 18 (just under, well above
                // the never-weaken-below-12 guard).
                val lightOutlineSep = abs(toneOf(l.outlineVariant) - toneOf(l.shell))
                assertTrue("light outline-vs-shell $lightOutlineSep < 18 at v=$v hue=$hue", lightOutlineSep >= 18 - eps)

                val d = deriveWidgetSurfaces(dark.secondaryContainer, dark.onSurface, dark.onSurfaceVariant, dark.outlineVariant, hue.toFloat(), v, dark = true)
                val dCard = composite(d.card, d.shell, 0.85f)
                assertTrue(abs(toneOf(d.onSurface) - toneOf(d.shell)) >= 50 - eps)
                assertTrue(abs(toneOf(d.onSurface) - toneOf(dCard)) >= 50 - eps)
                assertTrue(toneOf(d.shell) <= 35 + eps && toneOf(dCard) <= 45 + eps)
                // Round-2: tinted outlineVariant vs shell. Measured dark min ≈14.39
                // (mid-ramp, where the ramp toward shellTone+26 crosses near the rising
                // shell); floor 13 (just under, above the 12 guard).
                val darkOutlineSep = abs(toneOf(d.outlineVariant) - toneOf(d.shell))
                assertTrue("dark outline-vs-shell $darkOutlineSep < 13 at v=$v hue=$hue", darkOutlineSep >= 13 - eps)
                if (v <= WidgetAppearance.DEFAULT_VIBRANCY) {
                    // 45, not 50: SPEC_2025's dark onSurfaceVariant sits at tone ~70 (the 2021
                    // spec the design doc's "ΔTone >= 50" assumed had ~80), so TODAY'S shipped
                    // anchor gap is ~46. The contract is no-regression-vs-today, not the
                    // aspirational 2021 number.
                    assertTrue(abs(toneOf(d.onSurfaceVariant) - toneOf(dCard)) >= 45 - eps)
                } else {
                    assertTrue(abs(toneOf(d.onSurfaceVariant) - toneOf(dCard)) >= 44 - eps)
                    assertTrue(abs(toneOf(d.onSurfaceVariant) - toneOf(d.control)) >= 36 - eps)
                }
            }
        }
    }

    // Smallest absolute angular distance on the hue circle (handles 0/360 wraparound).
    private fun circularHueDiff(a: Float, b: Float): Float {
        val d = abs(a.mod(360f) - b.mod(360f))
        return minOf(d, 360f - d)
    }

    @Test
    fun `derivedBackgroundHue equals seed hue under TonalSpot no-rotation`() {
        // Guards the assumption that makes derivedBackgroundHue's no-scheme-gen fast
        // path equivalent to what the widget actually renders: under TonalSpot the
        // SPEC_2025 SCHEME's secondaryContainer carries the seed hue UNROTATED. We
        // therefore measure the SCHEME's hue, not derivedBackgroundHue's (which is now
        // literally h.mod(360) and would tautologically pass). The 2deg tolerance is
        // the no-regression budget; if a MaterialKolor/spec change ever rotates the
        // secondary palette this fails and the fast path must be revisited.
        for (h in 0 until 360 step 30) {
            val schemeHue = Hct.fromInt(
                seededWidgetColorSchemes(seedColorFromHue(h.toFloat()))
                    .first.secondaryContainer.toArgb()
            ).hue.toFloat()
            assertEquals(
                "hue $h should pass through TonalSpot unrotated",
                0f,
                circularHueDiff(schemeHue, h.toFloat()),
                2f,
            )
        }
        // The default resting hue must track the default seed's SCHEME secondaryContainer
        // (same fast-path equivalence for the null case; the handle position is cosmetic).
        val defaultSchemeHue = Hct.fromInt(
            seededWidgetColorSchemes(DefaultSeedColor).first.secondaryContainer.toArgb()
        ).hue.toFloat()
        assertEquals(
            "default resting hue tracks the default scheme's secondaryContainer",
            0f,
            circularHueDiff(defaultSchemeHue, defaultSeedHue()),
            2f,
        )
    }

    @Test
    fun `explicit seed hue is stable and saturation-free`() {
        // TonalSpot consumes only the seed's hue; Hct(hue, 36, 50) construction makes
        // the foreground slider exactly orthogonal to the background derivation.
        val seed = seedColorFromHue(212f)
        assertEquals(212.0, Hct.fromInt(seed.toArgb()).hue, 1.5)
        // SPEC_2025 seed-chroma check (design open item): same hue, different chroma
        // seeds must produce the same secondaryContainer. If this fails, pin the seed
        // through seedColorFromHue everywhere and document it — the hue-only UI holds.
        val a = seededWidgetColorSchemes(Color(Hct.from(212.0, 36.0, 50.0).toInt())).first
        val b = seededWidgetColorSchemes(Color(Hct.from(212.0, 70.0, 50.0).toInt())).first
        assertColorClose(a.secondaryContainer, b.secondaryContainer, channelTolerance = 2)
    }
}
