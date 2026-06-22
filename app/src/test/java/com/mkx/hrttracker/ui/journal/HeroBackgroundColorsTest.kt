package com.mkx.hrttracker.ui.journal

import com.materialkolor.hct.Hct
import com.mkx.hrttracker.model.journal.PrideFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroBackgroundColorsTest {
    @Test
    fun bloomParams_dimsAlphaAndChromaWhenUnblurred_keepingTone() {
        listOf(false, true).forEach { isDark ->
            val blurred = HeroBackgroundColors.bloomParams(isDark = isDark, blurred = true)
            val unblurred = HeroBackgroundColors.bloomParams(isDark = isDark, blurred = false)
            // Without the haze blur to diffuse it, the wash reads stronger, so it is both fainter
            // and a little less saturated; only the tone (lightness) is preserved.
            assertTrue(unblurred.alpha < blurred.alpha)
            assertEquals(
                blurred.alpha * HeroBackgroundColors.UnblurredAlphaScale,
                unblurred.alpha,
                1e-6f,
            )
            assertTrue(unblurred.chroma < blurred.chroma)
            assertEquals(
                blurred.chroma * HeroBackgroundColors.UnblurredChromaScale,
                unblurred.chroma,
                1e-9,
            )
            assertEquals(blurred.tone, unblurred.tone, 0.0)
        }
        // The default is the blurred (API 31+) look.
        assertEquals(
            HeroBackgroundColors.bloomParams(isDark = false, blurred = true),
            HeroBackgroundColors.bloomParams(isDark = false),
        )
    }

    @Test
    fun bloomColors_useUnblurredBloomParamsWhenNotBlurred() {
        val seeds = listOf(0xFF5BCEFA.toInt(), 0xFFF5A9B8.toInt())
        val params = HeroBackgroundColors.bloomParams(isDark = false, blurred = false)
        val expected = HeroBackgroundColors.hueSorted(HeroBackgroundColors.paletteSeeds(seeds))
            .map { HeroBackgroundColors.normalize(it, params.chroma, params.tone) }
        assertEquals(
            expected,
            HeroBackgroundColors.bloomColors(seeds, isDark = false, blurred = false),
        )
    }

    @Test
    fun dateColorBloomColors_useUnblurredBloomParamsWhenNotBlurred() {
        val primary = 0xFF5BCEFA.toInt()
        val container = 0xFFF5A9B8.toInt()
        val params = HeroBackgroundColors.bloomParams(isDark = false, blurred = false)
        val chroma = params.chroma * HeroBackgroundColors.DateChromaScale
        val expected = listOf(
            HeroBackgroundColors.normalize(primary, chroma, params.tone),
            HeroBackgroundColors.normalize(container, chroma, params.tone - HeroBackgroundColors.DateToneSpread),
        )
        assertEquals(
            expected,
            HeroBackgroundColors.dateColorBloomColors(primary, container, isDark = false, blurred = false),
        )
    }

    @Test
    fun normalize_forcesThemeToneAndKeepsHue_forChromaticSeed() {
        val blue = 0xFF5BCEFA.toInt()
        val original = Hct.fromInt(blue)
        val out = Hct.fromInt(HeroBackgroundColors.normalize(blue, chroma = 36.0, tone = 88.0))
        // Chromatic seed: tone forced to the theme tone, hue preserved.
        assertEquals(88.0, out.tone, 1.0)
        assertEquals(original.hue, out.hue, 4.0)
    }

    @Test
    fun normalize_keepsDistinctLightness_forNeutralSeeds() {
        // black != grey != white must survive: chroma collapses to ~0 but tone stays ordered.
        val black = Hct.fromInt(HeroBackgroundColors.normalize(0xFF000000.toInt(), 36.0, 88.0))
        val grey = Hct.fromInt(HeroBackgroundColors.normalize(0xFFA3A3A3.toInt(), 36.0, 88.0))
        val white = Hct.fromInt(HeroBackgroundColors.normalize(0xFFFFFFFF.toInt(), 36.0, 88.0))
        assertTrue("black<grey", black.tone < grey.tone)
        assertTrue("grey<white", grey.tone < white.tone)
        assertTrue("black neutral", black.chroma < HeroBackgroundColors.NeutralChromaThreshold)
        assertTrue("white neutral", white.chroma < HeroBackgroundColors.NeutralChromaThreshold)
    }

    @Test
    fun hueSorted_cutsTheLargestWheelGap() {
        // After sorting, the wrap gap (last -> first + 360) must be >= every internal gap,
        // i.e. the run was rotated to span the shortest arc.
        val sorted = HeroBackgroundColors.hueSorted(PrideFlag.RAINBOW.seeds)
        val hues = sorted.map { Hct.fromInt(it).hue }.unwrapHueRun()
        val internalGaps = hues.zipWithNext { a, b -> b - a }
        val wrapGap = (hues.first() + 360.0) - hues.last()
        assertTrue("internal hues ascending", internalGaps.all { it >= 0 })
        assertTrue("wrap gap is largest", internalGaps.all { wrapGap >= it })
    }

    @Test
    fun paletteSeeds_dropsPureNeutrals_whenEnoughChromaticRemain() {
        // Trans = blue, pink, white. The white is neutral and is dropped; blue + pink remain so
        // the wash reads as colour, not a washed-out near-white band.
        val palette = HeroBackgroundColors.paletteSeeds(PrideFlag.TRANSGENDER.seeds)
        assertEquals(2, palette.size)
        assertTrue(
            "all remaining seeds are chromatic",
            palette.all { Hct.fromInt(it).chroma >= HeroBackgroundColors.NeutralChromaThreshold },
        )
    }

    @Test
    fun paletteSeeds_keepsEveryChromaticFlagIntact() {
        // Rainbow has no neutral seeds — every colour survives.
        assertEquals(
            PrideFlag.RAINBOW.seeds.size,
            HeroBackgroundColors.paletteSeeds(PrideFlag.RAINBOW.seeds).size,
        )
    }

    @Test
    fun paletteSeeds_restoresGreyestNeutral_whenTooFewChromatic() {
        // Asexual = black, grey, white, purple. Only purple is chromatic, so the neutral nearest
        // mid-grey (the grey — not black or white) is restored so the flag still blooms as a pair.
        val palette = HeroBackgroundColors.paletteSeeds(PrideFlag.ASEXUAL.seeds)
        assertEquals(2, palette.size)
        val neutral = palette.single {
            Hct.fromInt(it).chroma < HeroBackgroundColors.NeutralChromaThreshold
        }
        assertTrue(
            "restored neutral is mid-grey, not black or white",
            Hct.fromInt(neutral).tone in 40.0..85.0,
        )
    }

    @Test
    fun dateColorBloomColors_normalizesChromaButSpreadsToneForDepth() {
        // A date palette is mono-hue (primary + primaryContainer share the seed's hue), so unlike a
        // multi-hue flag it cannot get its depth from hue spread. It must come from tone instead —
        // otherwise the two seeds collapse to one colour and the wash reads as a flat, over-intense
        // single hue rather than a gradient.
        val primary = 0xFFCE2C31.toInt()
        val primaryContainer = 0xFFFFDBDC.toInt()
        val params = HeroBackgroundColors.bloomParams(isDark = false)

        val out = HeroBackgroundColors.dateColorBloomColors(primary, primaryContainer, isDark = false)
        assertEquals(2, out.size)
        val light = Hct.fromInt(out[0])
        val dark = Hct.fromInt(out[1])

        // A mono-hue date wash reads more intensely than a multi-hue flag at equal chroma (one solid
        // hue vs a blend), so its chroma is capped *below* the flag bloom chroma to feel as soft —
        // yet both seeds stay clearly chromatic rather than collapsing to grey. (HCT gamut-clamps the
        // realized chroma, so this asserts the cap and the floor, not an exact value.)
        val dateChromaCap = params.chroma * HeroBackgroundColors.DateChromaScale
        assertTrue("date chroma is below flag chroma", dateChromaCap < params.chroma)
        assertTrue("light chroma capped", light.chroma <= dateChromaCap + 1.0)
        assertTrue("dark chroma capped", dark.chroma <= dateChromaCap + 1.0)
        assertTrue("light stays chromatic", light.chroma >= HeroBackgroundColors.NeutralChromaThreshold)
        assertTrue("dark stays chromatic", dark.chroma >= HeroBackgroundColors.NeutralChromaThreshold)

        // The lighter seed sits at the bloom tone (flag brightness ceiling); the partner is pulled
        // darker by DateToneSpread so the band reads as a gradient with depth.
        assertEquals(params.tone, light.tone, 1.0)
        assertEquals(params.tone - HeroBackgroundColors.DateToneSpread, dark.tone, 1.0)
        assertTrue("date palette spreads tone for depth", light.tone - dark.tone > 1.0)
    }

    private fun List<Double>.unwrapHueRun(): List<Double> {
        var offset = 0.0
        var previous = firstOrNull() ?: return emptyList()
        return map { hue ->
            if (hue + offset < previous) offset += 360.0
            previous = hue + offset
            previous
        }
    }
}
