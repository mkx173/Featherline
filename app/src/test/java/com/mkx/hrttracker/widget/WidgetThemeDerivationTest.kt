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
    private fun chromaOf(color: Color): Double = Hct.fromInt(color.toArgb()).chroma

    // The pre-customization derivation (relative ±tone on secondaryContainer), kept
    // here as the executable definition of "today's output". WHY: the default
    // appearance is contractually pixel-equivalent to the shipped tinted theme.
    private fun legacyAdjust(input: Color, lightAdj: Double, darkAdj: Double): Color {
        val hct = Hct.fromInt(input.toArgb())
        val adj = if (hct.tone > 50) lightAdj else darkAdj
        return Color(Hct.from(hct.hue, hct.chroma, (hct.tone + adj).coerceIn(0.0, 100.0)).toInt())
    }

    // The tone design at FULL opacity: card composited at 0.85 over the shell
    // (CONTAINER_ALPHA_FACTOR_HIGH). Opacity-aware thinning below 1.0 is a separate axis.
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
                saturation = WidgetAppearance.DEFAULT_SATURATION,
                balance = WidgetAppearance.DEFAULT_BALANCE, dark = false,
            )
            assertColorClose(legacyAdjust(light.secondaryContainer, 4.0, -10.0), l.shell)
            assertColorClose(legacyAdjust(light.secondaryContainer, 0.0, 0.0), l.card)
            assertColorClose(legacyAdjust(light.secondaryContainer, -6.0, 0.0), l.control)
            assertColorClose(light.onSurface, l.onSurface)               // no lift at anchor
            assertColorClose(light.onSurfaceVariant, l.onSurfaceVariant)
            // Round 7: the outline now tints with the surface at EVERY balance (the old
            // u==0 scheme short-circuit is gone). At the anchor its tone sits the ANCHOR
            // offset (−18) below the shell, and it carries the surface hue + chroma (no
            // longer the near-neutral scheme grey).
            assertEquals(toneOf(l.shell) - 18.0, toneOf(l.outlineVariant), 0.6)
            assertTrue(chromaOf(l.outlineVariant) > 4.0)

            val d = deriveWidgetSurfaces(
                secondaryContainer = dark.secondaryContainer, onSurface = dark.onSurface,
                onSurfaceVariant = dark.onSurfaceVariant,
                saturation = WidgetAppearance.DEFAULT_SATURATION,
                balance = WidgetAppearance.DEFAULT_BALANCE, dark = true,
            )
            assertColorClose(legacyAdjust(dark.secondaryContainer, 4.0, -10.0), d.shell)
            assertColorClose(legacyAdjust(dark.secondaryContainer, 0.0, 0.0), d.card)
            // Dark control: approved change — tinted cardTone+6, NOT today's neutral
            // surfaceVariant. Assert the new contract instead of equivalence.
            assertEquals(toneOf(d.card) + 6.0, toneOf(d.control), 0.6)
            // Round 7: dark outline tone sits the anchor offset (+18) above the shell,
            // tinted (was the short-circuited neutral scheme color at the anchor).
            assertEquals(toneOf(d.shell) + 18.0, toneOf(d.outlineVariant), 0.6)
            assertTrue(chromaOf(d.outlineVariant) > 4.0)
        }
    }

    @Test
    fun `balance owns tone depth at fixed saturation`() {
        // Round 6: balance is pure tone depth, BIDIRECTIONAL. At balance 0.5 the shell holds at
        // its anchor (light 94 / dark 15); 0.5→1 DEEPENS toward the ceiling (light 88 / dark 25,
        // Round 5); 0.5→0 LIGHTENS the other way (light 98 / dark 8). Chroma is held constant by
        // saturation, so this isolates the depth axis.
        val (light, dark) = seededWidgetColorSchemes(seeds[0])
        val s = WidgetAppearance.DEFAULT_SATURATION
        fun lightShell(b: Float) = toneOf(deriveWidgetSurfaces(light.secondaryContainer, light.onSurface, light.onSurfaceVariant, s, b, dark = false).shell)
        fun darkShell(b: Float) = toneOf(deriveWidgetSurfaces(dark.secondaryContainer, dark.onSurface, dark.onSurfaceVariant, s, b, dark = true).shell)

        assertEquals(94.0, lightShell(0.5f), 0.6)  // anchor
        assertEquals(88.0, lightShell(1f), 0.6)    // deepen ceiling (Round 5)
        assertEquals(98.0, lightShell(0f), 0.6)    // lighten end (Round 6)

        assertEquals(15.0, darkShell(0.5f), 0.6)   // anchor
        assertEquals(25.0, darkShell(1f), 0.6)     // deepen ceiling (Round 5: 35 -> 25)
        assertEquals(8.0, darkShell(0f), 0.6)      // lighten end (Round 6)
    }

    @Test
    fun `saturation owns chroma s0 neutral s1 boosts past s05`() {
        // Round 4: saturation owns chroma outright (independent of balance). s=0 drives
        // chroma to ~0; s=1 boosts past s=0.5. Measured at balance 0 (today's tones).
        val (_, dark) = seededWidgetColorSchemes(seeds[0])
        fun surfacesAt(scHue: Double, s: Float): WidgetSurfaces {
            // Dark-mode synthesized sc at t25: the dark shell/outline have gamut headroom so the
            // s=1 boost isn't clamped flat the way the near-white t94 light shell is.
            val sc = Color(Hct.from(scHue, 16.0, 25.0).toInt())
            return deriveWidgetSurfaces(
                sc, dark.onSurface, dark.onSurfaceVariant,
                s, WidgetAppearance.DEFAULT_BALANCE, dark = true,
            )
        }
        // s=0 → ~0 chroma (tolerance 3 for the HCT solver's near-zero residual).
        assertEquals(0.0, chromaOf(surfacesAt(270.0, 0f).shell), 3.0)
        // s=1 chroma must exceed s=0.5 chroma at a dark-mode sc where headroom exists.
        val half = chromaOf(surfacesAt(270.0, 0.5f).shell)
        val full = chromaOf(surfacesAt(270.0, 1f).shell)
        assertTrue("s=1 chroma $full should exceed s=0.5 chroma $half at hue 270", full > half + 5)
        // Round 7: the outline tints with the surface too (was a neutral scheme grey at the
        // anchor) — its chroma collapses to ~0 at s=0 and lifts well above that at s=1.
        val outlineNeutral = chromaOf(surfacesAt(270.0, 0f).outlineVariant)
        val outlineTinted = chromaOf(surfacesAt(270.0, 1f).outlineVariant)
        assertEquals(0.0, outlineNeutral, 3.0)
        assertTrue("s=1 outline chroma $outlineTinted should tint past s=0 $outlineNeutral", outlineTinted > outlineNeutral + 5)
    }

    @Test
    fun `outline offset grows as background alpha drops (solid-outline alpha comp)`() {
        // Round 8: the outline is painted SOLID over the translucent shell, so as backgroundAlpha
        // drops the rendered background drifts toward the wallpaper and a fixed tone offset loses
        // contrast. WHY this matters: the offset grows on a LINEAR opacity ramp (×1 at full opacity →
        // ×1.333 at the 0.5 floor), so the solid outline's tone separation from the shell grows as
        // opacity falls — ~18 at full opacity, ~21 at 0.75, ~24 at the 0.5 floor — instead of
        // collapsing. A test that ignored alpha could never catch a regression here.
        val (light, _) = seededWidgetColorSchemes(seeds[0])
        fun lightOutlineSep(alpha: Float): Double {
            val s = deriveWidgetSurfaces(
                light.secondaryContainer, light.onSurface, light.onSurfaceVariant,
                WidgetAppearance.DEFAULT_SATURATION, WidgetAppearance.DEFAULT_BALANCE,
                dark = false, backgroundAlpha = alpha,
            )
            return abs(toneOf(s.shell) - toneOf(s.outlineVariant))
        }
        assertEquals(18.0, lightOutlineSep(1f), 0.6)    // full opacity: the base offset, no boost
        assertEquals(24.0, lightOutlineSep(0.5f), 0.8)  // 0.5 floor: 18 × 1.333 (linear ramp)
        // Monotonic: lower alpha → larger separation (defends the compensation direction).
        assertTrue(lightOutlineSep(0.75f) > lightOutlineSep(1f))
        assertTrue(lightOutlineSep(0.5f) > lightOutlineSep(0.75f))
    }

    @Test
    fun `DONE check-pill is derived from the card so it never collides`() {
        // Round 9: scheme primaryContainer is a fixed tint that collides with the card at high balance
        // (the card darkens past it). The DONE check-pill is derived from the card instead — tone =
        // cardTone + delta (light −10 / dark +10) — so it holds a CONSTANT separation at every balance,
        // and the check icon stays legible regardless. A fixed-color button could pass at one balance
        // yet still vanish at another; pinning the offset is the contract.
        val (light, dark) = seededWidgetColorSchemes(seeds[0])
        val s = WidgetAppearance.DEFAULT_SATURATION
        for (balance in floatArrayOf(0f, 0.5f, 0.75f, 1f)) {
            val l = deriveWidgetSurfaces(light.secondaryContainer, light.onSurface, light.onSurfaceVariant, s, balance, dark = false)
            val lDone = deriveWidgetPrimaryContainer(light.primaryContainer, l.card, s, dark = false)
            assertEquals("light DONE vs card at balance=$balance", toneOf(l.card) - 10.0, toneOf(lDone.container), 0.8)
            assertTrue("light check icon legible at balance=$balance", abs(toneOf(lDone.onContainer) - toneOf(lDone.container)) >= 45)

            val d = deriveWidgetSurfaces(dark.secondaryContainer, dark.onSurface, dark.onSurfaceVariant, s, balance, dark = true)
            val dDone = deriveWidgetPrimaryContainer(dark.primaryContainer, d.card, s, dark = true)
            assertEquals("dark DONE vs card at balance=$balance", toneOf(d.card) + 10.0, toneOf(dDone.container), 0.8)
            assertTrue("dark check icon legible at balance=$balance", abs(toneOf(dDone.onContainer) - toneOf(dDone.container)) >= 45)
        }
    }

    @Test
    fun `DONE pill chroma is floored, matches primaryContainer at default, and stays vivid`() {
        // Round 9: the control pill IS a surface element, so it collapses to neutral with the surfaces at
        // saturation 0 and is capped at CONTROL_CHROMA_MAX (24). The accent DONE pill keeps its OWN chroma
        // curve anchored on the scheme primaryContainer's chroma — a FLOOR so it still reads as the accent
        // at saturation 0, EXACTLY pcChroma at the 0.5 default (so the default pill matches the Material
        // primaryContainer), and a boost so it stays vivid above the control's cap at max saturation.
        // Dark, hue-headroom seed.
        val (_, dark) = seededWidgetColorSchemes(seeds[0])
        val pcChroma = chromaOf(dark.primaryContainer)
        fun darkSurfacesAt(saturation: Float) = deriveWidgetSurfaces(
            Color(Hct.from(270.0, 16.0, 25.0).toInt()), dark.onSurface, dark.onSurfaceVariant,
            saturation, WidgetAppearance.DEFAULT_BALANCE, dark = true,
        )
        fun doneChromaAt(saturation: Float) = chromaOf(
            deriveWidgetPrimaryContainer(dark.primaryContainer, darkSurfacesAt(saturation).card, saturation, dark = true).container
        )
        // sat 0 → the control collapses to neutral, but the DONE pill keeps a floored, still-colorful chroma.
        assertEquals(0.0, chromaOf(darkSurfacesAt(0f).control), 3.0)
        assertTrue("DONE keeps color at sat 0 (floor)", doneChromaAt(0f) > 5.0)
        assertTrue("DONE rises from its floor to the default", doneChromaAt(0.5f) > doneChromaAt(0f) + 3)
        // default → the DONE pill matches the scheme primaryContainer's chroma.
        assertEquals("DONE == primaryContainer chroma at default", pcChroma, doneChromaAt(0.5f), 5.0)
        // sat 1 → control held at the 24 cap; the DONE pill is boosted and stays vivid above it.
        val s1 = darkSurfacesAt(1f)
        assertTrue("control capped at ~24", chromaOf(s1.control) <= 24.5)
        assertTrue("DONE boosted past its default", doneChromaAt(1f) > doneChromaAt(0.5f) + 3)
        assertTrue("DONE stays vivid above the control cap", doneChromaAt(1f) > chromaOf(s1.control) + 3)
    }

    @Test
    fun `contrast invariants hold across hue x balance x saturation sweep`() {
        // Encodes the spec's contrast guarantee (not specific hexes):
        //  - onSurface ΔTone >= 50 against shell and the COMPOSITED card, at all balance
        //  - onSurfaceVariant ΔTone >= 49 (light) / >= 45 (dark) on cards at ALL balance
        //  - dark onSurfaceVariant-vs-control-pill >= 40 for balance > 0
        //  - shell/card tone safe bands: light >= 78, dark <= 45
        //
        // Round 6: balance is bidirectional (anchor 0.5). This sweep walks the DEEPEN half
        // (balance 0.5→1, u = (balance−0.5)·2) — the SAME u-points as the old 0→1 sweep, so it
        // pins that the deepen half reproduces today's output bit-for-bit. The lighten half
        // (balance 0.5→0) has its own no-regression test below.
        //
        // Round 5: the shell endpoints were compressed (light 82->88, dark 35->25), which
        // IMPROVES every deep-end contrast number — secondary text no longer degrades with
        // balance, so the old split anchor/degraded floors collapse to unconditional floors
        // whose minimum now sits at (or near) the anchor. Floors below are set just under
        // the measured sweep minima; the measured value is quoted at each assert.
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
            for (balance in floatArrayOf(0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1f)) {
                for (s in floatArrayOf(0f, 0.5f, 1f)) {
                    val l = deriveWidgetSurfaces(lightSc, light.onSurface, light.onSurfaceVariant, s, balance, dark = false)
                    val lCard = composite(l.card, l.shell, 0.85f)
                    assertTrue(abs(toneOf(l.onSurface) - toneOf(l.shell)) >= 50 - eps)
                    assertTrue(abs(toneOf(l.onSurface) - toneOf(lCard)) >= 50 - eps)
                    // Round 5: light secondary text no longer degrades above the anchor;
                    // unconditional floor (measured sweep min ≈49.61, at the anchor).
                    assertTrue(abs(toneOf(l.onSurfaceVariant) - toneOf(lCard)) >= 49 - eps)
                    assertTrue(toneOf(l.shell) >= 78 - eps && toneOf(lCard) >= 78 - 4 - eps)
                    // Round-2: light card-vs-shell separation (COMPOSITED) must be visible.
                    val lightSep = abs(toneOf(lCard) - toneOf(l.shell))
                    assertTrue("light card-vs-shell sep $lightSep < 3 at balance=$balance s=$s hue=$hue", lightSep >= 3 - eps)
                    if (balance == 1f) {
                        // Round 5: balance-1 shell rises 82->88, so the composited card sits
                        // closer to it — separation shrinks but stays ~double the anchor's 3.4
                        // (measured sweep min at balance 1 ≈7.31).
                        assertTrue("light card-vs-shell sep $lightSep < 7 at balance=1 s=$s hue=$hue", lightSep >= 7 - eps)
                    }
                    // Round 7/8: the tinted outline tracks the shell at a flat |offset| of 18 across
                    // the whole balance range (anchor == deepen). Tone-based, so saturation-independent.
                    // This sweep is at full opacity (the Round-8 alpha boost is 1 at alpha 1). Floor 17.
                    val lightOutlineSep = abs(toneOf(l.outlineVariant) - toneOf(l.shell))
                    assertTrue("light outline-vs-shell $lightOutlineSep < 17 at balance=$balance s=$s hue=$hue", lightOutlineSep >= 17 - eps)

                    val d = deriveWidgetSurfaces(darkSc, dark.onSurface, dark.onSurfaceVariant, s, balance, dark = true)
                    val dCard = composite(d.card, d.shell, 0.85f)
                    assertTrue(abs(toneOf(d.onSurface) - toneOf(d.shell)) >= 50 - eps)
                    assertTrue(abs(toneOf(d.onSurface) - toneOf(dCard)) >= 50 - eps)
                    // Round 5: dark shell ceiling compressed 35 -> 25 (measured sweep max ≈25.19).
                    assertTrue(toneOf(d.shell) <= 25 + eps && toneOf(dCard) <= 45 + eps)
                    // Round 7/8: dark outline tracks the shell at a flat |offset| of 18 across the
                    // whole balance range (full-opacity sweep; alpha boost is 1 at alpha 1). Floor 17.
                    val darkOutlineSep = abs(toneOf(d.outlineVariant) - toneOf(d.shell))
                    assertTrue("dark outline-vs-shell $darkOutlineSep < 17 at balance=$balance s=$s hue=$hue", darkOutlineSep >= 17 - eps)
                    // 45, not 50: SPEC_2025's dark onSurfaceVariant sits at tone ~70 (the 2021
                    // spec the design doc's "ΔTone >= 50" assumed had ~80), so the anchor gap
                    // is ~46. The contract is no-regression-vs-today, not the aspirational 2021
                    // number. Round 5: the dark gap-on-cards no longer degrades above the anchor
                    // (the anchor IS now the minimum, ~Δ45.97 measured, improving with balance),
                    // so the old split anchor/degraded floor collapses to one unconditional ≥45.
                    assertTrue(abs(toneOf(d.onSurfaceVariant) - toneOf(dCard)) >= 45 - eps)
                    if (balance != 0.5f) {
                        // Round 5: control-pill floor rises 36 -> ~40 (measured deep min ≈40.34).
                        // Skip the anchor (balance 0.5, u=0): its tinted-pill contrast is ~37,
                        // below 40 — the same exclusion the pre-Round-6 sweep made at balance 0.
                        assertTrue(abs(toneOf(d.onSurfaceVariant) - toneOf(d.control)) >= 40 - eps)
                    }
                }
            }
        }
    }

    @Test
    fun `container alpha ramps down with opacity but holds a separation floor`() {
        // Round 6 #1: cards stack on the shell, so a flat factor makes them occlude the
        // wallpaper through two layers and read "more solid" than the shell — worst at low
        // opacity. The factor now ramps from 0.85 @ opacity 1 (unchanged look) to 0.40 @ the
        // 0.5 floor, so the EXCESS solidity (cardAlpha·(1−a) above the shell's a) shrinks as
        // opacity drops, while the floor keeps the card painted enough to stay a visible row.
        assertEquals(0.85f, containerAlpha(1.0f), 1e-4f)        // full opacity: unchanged
        assertEquals(0.40f * 0.5f, containerAlpha(0.5f), 1e-4f) // floor: factor 0.40 · alpha 0.5
        // Strictly increasing in opacity (more opacity → more card paint).
        var prev = -1f
        var a = 0.5f
        while (a <= 1.0001f) {
            val v = containerAlpha(a)
            assertTrue("containerAlpha must increase with opacity (a=$a)", v > prev)
            prev = v
            a += 0.05f
        }
        // The point of the ramp: at every opacity below 1.0 the card's excess solidity over the
        // shell is SMALLER than today's flat-0.85 would give (and equal only at full opacity).
        for (alpha in floatArrayOf(0.5f, 0.6f, 0.7f, 0.8f, 0.9f)) {
            val newExcess = containerAlpha(alpha) * (1 - alpha)
            val flatExcess = 0.85f * alpha * (1 - alpha)
            assertTrue("ramp must reduce excess solidity at a=$alpha", newExcess < flatExcess)
        }
        assertEquals(0.85f * 1.0f * 0f, containerAlpha(1.0f) * (1 - 1.0f), 1e-4f) // equal (zero) at full
    }

    @Test
    fun `lighten half moves the shell and never regresses contrast below the anchor`() {
        // Round 6: the lighten half (balance 0.5→0) is TONES-ONLY — only shell/card/control
        // tones move (lighter in light mode, darker in dark mode); the text lifts and the
        // outline tint hold at the anchor (deepenRamp is 0 across this half). Lightening moves
        // the surfaces AWAY from the text tones, so every contrast metric can only IMPROVE.
        // This encodes that intent directly: the shell moves the expected direction and NO
        // metric drops below its anchor (balance 0.5) value — a self-validating guarantee, so
        // there are no hand-tuned lighten-half floors to drift.
        val (light, dark) = seededWidgetColorSchemes(seeds[0])
        val eps = 0.75
        val lightenSteps = floatArrayOf(0f, 0.1f, 0.2f, 0.3f, 0.4f)
        for (hue in 0 until 360 step 15) {
            val lightSc = Color(Hct.from(hue.toDouble(), 16.0, 90.0).toInt())
            val darkSc = Color(Hct.from(hue.toDouble(), 16.0, 25.0).toInt())
            for (s in floatArrayOf(0f, 0.5f, 1f)) {
                val lAnchor = deriveWidgetSurfaces(lightSc, light.onSurface, light.onSurfaceVariant, s, 0.5f, dark = false)
                val lAnchorCard = composite(lAnchor.card, lAnchor.shell, 0.85f)
                val lOnSurface = abs(toneOf(lAnchor.onSurface) - toneOf(lAnchorCard))
                val lOsv = abs(toneOf(lAnchor.onSurfaceVariant) - toneOf(lAnchorCard))
                val lOutline = abs(toneOf(lAnchor.outlineVariant) - toneOf(lAnchor.shell))

                val dAnchor = deriveWidgetSurfaces(darkSc, dark.onSurface, dark.onSurfaceVariant, s, 0.5f, dark = true)
                val dAnchorCard = composite(dAnchor.card, dAnchor.shell, 0.85f)
                val dOnSurface = abs(toneOf(dAnchor.onSurface) - toneOf(dAnchorCard))
                val dOsv = abs(toneOf(dAnchor.onSurfaceVariant) - toneOf(dAnchorCard))
                val dControl = abs(toneOf(dAnchor.onSurfaceVariant) - toneOf(dAnchor.control))

                for (balance in lightenSteps) {
                    val l = deriveWidgetSurfaces(lightSc, light.onSurface, light.onSurfaceVariant, s, balance, dark = false)
                    val lCard = composite(l.card, l.shell, 0.85f)
                    val ctx = "balance=$balance s=$s hue=$hue"
                    assertTrue("light shell must brighten vs anchor at $ctx", toneOf(l.shell) >= toneOf(lAnchor.shell) - eps)
                    assertTrue("light onSurface-vs-card regressed at $ctx", abs(toneOf(l.onSurface) - toneOf(lCard)) >= lOnSurface - eps)
                    assertTrue("light osv-vs-card regressed at $ctx", abs(toneOf(l.onSurfaceVariant) - toneOf(lCard)) >= lOsv - eps)
                    assertTrue("light outline-vs-shell regressed at $ctx", abs(toneOf(l.outlineVariant) - toneOf(l.shell)) >= lOutline - eps)

                    val d = deriveWidgetSurfaces(darkSc, dark.onSurface, dark.onSurfaceVariant, s, balance, dark = true)
                    val dCard = composite(d.card, d.shell, 0.85f)
                    assertTrue("dark shell must darken vs anchor at $ctx", toneOf(d.shell) <= toneOf(dAnchor.shell) + eps)
                    assertTrue("dark onSurface-vs-card regressed at $ctx", abs(toneOf(d.onSurface) - toneOf(dCard)) >= dOnSurface - eps)
                    assertTrue("dark osv-vs-card regressed at $ctx", abs(toneOf(d.onSurfaceVariant) - toneOf(dCard)) >= dOsv - eps)
                    assertTrue("dark osv-vs-control regressed at $ctx", abs(toneOf(d.onSurfaceVariant) - toneOf(d.control)) >= dControl - eps)
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
