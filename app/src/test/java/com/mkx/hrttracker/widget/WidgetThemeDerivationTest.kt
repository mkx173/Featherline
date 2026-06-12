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
    fun `anchor reproduces legacy derivation for shell and card`() {
        for (seed in seeds) {
            val (light, dark) = seededWidgetColorSchemes(seed)
            val l = deriveWidgetSurfaces(
                secondaryContainer = light.secondaryContainer, onSurface = light.onSurface,
                onSurfaceVariant = light.onSurfaceVariant,
                schemeOutlineVariant = light.outlineVariant,
                saturation = WidgetAppearance.DEFAULT_SATURATION,
                balance = WidgetAppearance.DEFAULT_BALANCE, dark = false,
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
                saturation = WidgetAppearance.DEFAULT_SATURATION,
                balance = WidgetAppearance.DEFAULT_BALANCE, dark = true,
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
    fun `balance owns tone depth at fixed saturation`() {
        // Round 4: balance is pure tone depth. At balance 0 the shell holds at its base
        // (light 94 / dark 15); at balance 1 it reaches the ceiling (light 82 / dark 35).
        // Chroma is held constant by saturation, so this isolates the depth axis.
        val (light, dark) = seededWidgetColorSchemes(seeds[0])
        val s = WidgetAppearance.DEFAULT_SATURATION
        val lAnchor = deriveWidgetSurfaces(light.secondaryContainer, light.onSurface, light.onSurfaceVariant, light.outlineVariant, s, 0f, dark = false)
        val lDeep = deriveWidgetSurfaces(light.secondaryContainer, light.onSurface, light.onSurfaceVariant, light.outlineVariant, s, 1f, dark = false)
        assertEquals(94.0, toneOf(lAnchor.shell), 0.6)
        assertEquals(82.0, toneOf(lDeep.shell), 0.6)

        val dAnchor = deriveWidgetSurfaces(dark.secondaryContainer, dark.onSurface, dark.onSurfaceVariant, dark.outlineVariant, s, 0f, dark = true)
        val dDeep = deriveWidgetSurfaces(dark.secondaryContainer, dark.onSurface, dark.onSurfaceVariant, dark.outlineVariant, s, 1f, dark = true)
        assertEquals(15.0, toneOf(dAnchor.shell), 0.6)
        assertEquals(35.0, toneOf(dDeep.shell), 0.6) // dark ceiling
    }

    @Test
    fun `saturation owns chroma s0 neutral s1 boosts past s05`() {
        // Round 4: saturation owns chroma outright (independent of balance). s=0 drives
        // chroma to ~0; s=1 boosts past s=0.5. Measured at balance 0 (today's tones).
        val (_, dark) = seededWidgetColorSchemes(seeds[0])
        fun shellChroma(scHue: Double, s: Float): Double {
            // Dark-mode synthesized sc at t25: the t25 shell has gamut headroom so the
            // s=1 boost isn't clamped flat the way the near-white t94 light shell is.
            val sc = Color(Hct.from(scHue, 16.0, 25.0).toInt())
            val surfaces = deriveWidgetSurfaces(
                sc, dark.onSurface, dark.onSurfaceVariant, dark.outlineVariant,
                s, WidgetAppearance.DEFAULT_BALANCE, dark = true,
            )
            return Hct.fromInt(surfaces.shell.toArgb()).chroma
        }
        // s=0 → ~0 chroma (tolerance 3 for the HCT solver's near-zero residual).
        assertEquals(0.0, shellChroma(270.0, 0f), 3.0)
        // s=1 chroma must exceed s=0.5 chroma at a dark-mode sc where headroom exists.
        val half = shellChroma(270.0, 0.5f)
        val full = shellChroma(270.0, 1f)
        assertTrue("s=1 chroma $full should exceed s=0.5 chroma $half at hue 270", full > half + 5)
    }

    @Test
    fun `contrast invariants hold across hue x balance x saturation sweep`() {
        // Encodes the spec's contrast guarantee (not specific hexes):
        //  - onSurface ΔTone >= 50 against shell and the COMPOSITED card, at all balance
        //  - onSurfaceVariant ΔTone >= 50 (light) / >= 45 (dark) at balance 0 (the anchor)
        //  - accepted degraded floors for balance > 0: >= 44 on cards, >= 36 on pills
        //  - shell/card tone safe bands: light >= 78, dark <= 45
        //
        // Round 4: the depth axis is `balance` (u = balance), re-anchored at 0 = today's
        // tones / 1 = deepest. The "anchor floors" apply at balance 0 (no lift, tones
        // held); the "degraded floors" apply for balance > 0 (lift active, tones moving).
        //
        // Round 3 removed backgroundHue, so the hue axis can no longer be driven by a
        // parameter. Instead we synthesize each hue's INPUT secondaryContainer directly
        // — Hct(h, 16, 90) light / Hct(h, 16, 25) dark (chroma 16 ~ a typical TonalSpot
        // secondaryContainer) — and keep onSurface/onSurfaceVariant/outlineVariant from
        // the default scheme (their tones don't vary with hue, and every floor below is
        // TONE-based; chroma never affects tone, so saturation can't move a floor).
        val (light, dark) = seededWidgetColorSchemes(seeds[0])
        val eps = 0.75
        for (hue in 0 until 360 step 15) {
            val lightSc = Color(Hct.from(hue.toDouble(), 16.0, 90.0).toInt())
            val darkSc = Color(Hct.from(hue.toDouble(), 16.0, 25.0).toInt())
            for (balance in floatArrayOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f)) {
                for (s in floatArrayOf(0f, 0.5f, 1f)) {
                    val l = deriveWidgetSurfaces(lightSc, light.onSurface, light.onSurfaceVariant, light.outlineVariant, s, balance, dark = false)
                    val lCard = composite(l.card, l.shell, 0.85f)
                    assertTrue(abs(toneOf(l.onSurface) - toneOf(l.shell)) >= 50 - eps)
                    assertTrue(abs(toneOf(l.onSurface) - toneOf(lCard)) >= 50 - eps)
                    if (balance == 0f) {
                        assertTrue(abs(toneOf(l.onSurfaceVariant) - toneOf(lCard)) >= 50 - eps)
                    } else {
                        assertTrue(abs(toneOf(l.onSurfaceVariant) - toneOf(lCard)) >= 44 - eps)
                    }
                    assertTrue(toneOf(l.shell) >= 78 - eps && toneOf(lCard) >= 78 - 4 - eps)
                    // Round-2: light card-vs-shell separation (COMPOSITED) must be visible.
                    val lightSep = abs(toneOf(lCard) - toneOf(l.shell))
                    assertTrue("light card-vs-shell sep $lightSep < 3 at balance=$balance s=$s hue=$hue", lightSep >= 3 - eps)
                    if (balance == 1f) {
                        assertTrue("light card-vs-shell sep $lightSep < 7 at balance=1 s=$s hue=$hue", lightSep >= 7 - eps)
                    }
                    // Round-2: tinted outlineVariant must keep contrast vs shell (tone-based,
                    // so saturation-independent). Measured light min ≈20.19; floor 18.
                    val lightOutlineSep = abs(toneOf(l.outlineVariant) - toneOf(l.shell))
                    assertTrue("light outline-vs-shell $lightOutlineSep < 18 at balance=$balance s=$s hue=$hue", lightOutlineSep >= 18 - eps)

                    val d = deriveWidgetSurfaces(darkSc, dark.onSurface, dark.onSurfaceVariant, dark.outlineVariant, s, balance, dark = true)
                    val dCard = composite(d.card, d.shell, 0.85f)
                    assertTrue(abs(toneOf(d.onSurface) - toneOf(d.shell)) >= 50 - eps)
                    assertTrue(abs(toneOf(d.onSurface) - toneOf(dCard)) >= 50 - eps)
                    assertTrue(toneOf(d.shell) <= 35 + eps && toneOf(dCard) <= 45 + eps)
                    // Round-2: tinted outlineVariant vs shell (tone-based). Measured dark min ≈14.39; floor 13.
                    val darkOutlineSep = abs(toneOf(d.outlineVariant) - toneOf(d.shell))
                    assertTrue("dark outline-vs-shell $darkOutlineSep < 13 at balance=$balance s=$s hue=$hue", darkOutlineSep >= 13 - eps)
                    if (balance == 0f) {
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
    }

    // Smallest absolute angular distance on the hue circle (handles 0/360 wraparound).
    private fun circularHueDiff(a: Float, b: Float): Float {
        val d = abs(a.mod(360f) - b.mod(360f))
        return minOf(d, 360f - d)
    }

    @Test
    fun `background follows the seed scheme secondaryContainer hue unrotated`() {
        // Round 3: the background always follows the seed scheme's secondaryContainer
        // hue (the separate background-hue pick is gone). This guards that under
        // TonalSpot the SPEC_2025 scheme's secondaryContainer carries the seed hue
        // UNROTATED, so an accent pick drives the background hue 1:1. The 2deg
        // tolerance is the no-regression budget; if a MaterialKolor/spec change ever
        // rotates the secondary palette this fails and the relationship must be
        // revisited.
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
        // The accent slider's resting hue must track the default seed's SCHEME
        // secondaryContainer (the handle position is cosmetic, but should match what
        // actually renders for the dynamic/null case).
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
