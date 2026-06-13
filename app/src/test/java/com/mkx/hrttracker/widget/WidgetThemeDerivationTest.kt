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
        // Round 6: balance is pure tone depth, BIDIRECTIONAL. At balance 0.5 the shell holds at
        // its anchor (light 94 / dark 15); 0.5→1 DEEPENS toward the ceiling (light 88 / dark 25,
        // Round 5); 0.5→0 LIGHTENS the other way (light 98 / dark 8). Chroma is held constant by
        // saturation, so this isolates the depth axis.
        val (light, dark) = seededWidgetColorSchemes(seeds[0])
        val s = WidgetAppearance.DEFAULT_SATURATION
        fun lightShell(b: Float) = toneOf(deriveWidgetSurfaces(light.secondaryContainer, light.onSurface, light.onSurfaceVariant, light.outlineVariant, s, b, dark = false).shell)
        fun darkShell(b: Float) = toneOf(deriveWidgetSurfaces(dark.secondaryContainer, dark.onSurface, dark.onSurfaceVariant, dark.outlineVariant, s, b, dark = true).shell)

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
                    val l = deriveWidgetSurfaces(lightSc, light.onSurface, light.onSurfaceVariant, light.outlineVariant, s, balance, dark = false)
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
                    // Round-2: tinted outlineVariant must keep contrast vs shell (tone-based,
                    // so saturation-independent). Measured light min ≈20.19; floor 18.
                    val lightOutlineSep = abs(toneOf(l.outlineVariant) - toneOf(l.shell))
                    assertTrue("light outline-vs-shell $lightOutlineSep < 18 at balance=$balance s=$s hue=$hue", lightOutlineSep >= 18 - eps)

                    val d = deriveWidgetSurfaces(darkSc, dark.onSurface, dark.onSurfaceVariant, dark.outlineVariant, s, balance, dark = true)
                    val dCard = composite(d.card, d.shell, 0.85f)
                    assertTrue(abs(toneOf(d.onSurface) - toneOf(d.shell)) >= 50 - eps)
                    assertTrue(abs(toneOf(d.onSurface) - toneOf(dCard)) >= 50 - eps)
                    // Round 5: dark shell ceiling compressed 35 -> 25 (measured sweep max ≈25.19).
                    assertTrue(toneOf(d.shell) <= 25 + eps && toneOf(dCard) <= 45 + eps)
                    // Round-2: tinted outlineVariant vs shell (tone-based). Measured dark min ≈14.39; floor 13.
                    val darkOutlineSep = abs(toneOf(d.outlineVariant) - toneOf(d.shell))
                    assertTrue("dark outline-vs-shell $darkOutlineSep < 13 at balance=$balance s=$s hue=$hue", darkOutlineSep >= 13 - eps)
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
                val lAnchor = deriveWidgetSurfaces(lightSc, light.onSurface, light.onSurfaceVariant, light.outlineVariant, s, 0.5f, dark = false)
                val lAnchorCard = composite(lAnchor.card, lAnchor.shell, 0.85f)
                val lOnSurface = abs(toneOf(lAnchor.onSurface) - toneOf(lAnchorCard))
                val lOsv = abs(toneOf(lAnchor.onSurfaceVariant) - toneOf(lAnchorCard))
                val lOutline = abs(toneOf(lAnchor.outlineVariant) - toneOf(lAnchor.shell))

                val dAnchor = deriveWidgetSurfaces(darkSc, dark.onSurface, dark.onSurfaceVariant, dark.outlineVariant, s, 0.5f, dark = true)
                val dAnchorCard = composite(dAnchor.card, dAnchor.shell, 0.85f)
                val dOnSurface = abs(toneOf(dAnchor.onSurface) - toneOf(dAnchorCard))
                val dOsv = abs(toneOf(dAnchor.onSurfaceVariant) - toneOf(dAnchorCard))
                val dControl = abs(toneOf(dAnchor.onSurfaceVariant) - toneOf(dAnchor.control))

                for (balance in lightenSteps) {
                    val l = deriveWidgetSurfaces(lightSc, light.onSurface, light.onSurfaceVariant, light.outlineVariant, s, balance, dark = false)
                    val lCard = composite(l.card, l.shell, 0.85f)
                    val ctx = "balance=$balance s=$s hue=$hue"
                    assertTrue("light shell must brighten vs anchor at $ctx", toneOf(l.shell) >= toneOf(lAnchor.shell) - eps)
                    assertTrue("light onSurface-vs-card regressed at $ctx", abs(toneOf(l.onSurface) - toneOf(lCard)) >= lOnSurface - eps)
                    assertTrue("light osv-vs-card regressed at $ctx", abs(toneOf(l.onSurfaceVariant) - toneOf(lCard)) >= lOsv - eps)
                    assertTrue("light outline-vs-shell regressed at $ctx", abs(toneOf(l.outlineVariant) - toneOf(l.shell)) >= lOutline - eps)

                    val d = deriveWidgetSurfaces(darkSc, dark.onSurface, dark.onSurfaceVariant, dark.outlineVariant, s, balance, dark = true)
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
