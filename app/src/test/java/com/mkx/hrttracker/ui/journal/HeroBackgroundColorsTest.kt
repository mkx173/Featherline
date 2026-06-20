package com.mkx.hrttracker.ui.journal

import com.materialkolor.hct.Hct
import com.mkx.hrttracker.model.journal.PrideFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroBackgroundColorsTest {
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
    fun dateColorBloomColors_usesTheSameNormalizationPipelineAsFlags() {
        val primary = 0xFFCE2C31.toInt()
        val primaryContainer = 0xFFFFDBDC.toInt()

        assertEquals(
            HeroBackgroundColors.bloomColors(listOf(primary, primaryContainer), isDark = false),
            HeroBackgroundColors.dateColorBloomColors(primary, primaryContainer, isDark = false),
        )
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
