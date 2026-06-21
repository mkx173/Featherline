package com.mkx.hrttracker.ui.journal

import com.materialkolor.hct.Hct
import kotlin.math.abs

/**
 * Shared colour logic for the hero "aurora band" wash and the dialog swatch. [paletteSeeds] is the
 * single source of truth for *which* of a flag's seed colours appear, so a flag's chip and its
 * rendered background never diverge: the swatch shows that palette in its original hex, while the
 * bloom [normalize]s the same palette to the theme's common chroma/tone.
 *
 * Pure (no Compose): operates on packed ARGB ints. [paletteSeeds] reduces a flag to its chromatic
 * seeds (pure white/black/grey dropped) so the wash reads as colour, not grey; a flag left with
 * fewer than two chromatic seeds restores its greyest neutrals so it still blooms as a pair. A
 * neutral kept that way holds its own lightness (clamped to a visible band) instead of collapsing.
 *
 * Chroma/tone/alpha values mirror the hero-background-placements design's per-theme params.
 */
object HeroBackgroundColors {
    const val NeutralChromaThreshold = 8.0

    // Clamp neutral tone into a visible band: pure black would vanish on a dark card and
    // pure white would blow out a light one, yet restored neutrals must stay ordered.
    const val NeutralToneMin = 20.0
    const val NeutralToneMax = 90.0

    // When a flag is too sparse to bloom, restore the neutrals nearest mid-grey first.
    const val NeutralRestoreTone = 55.0

    // A date palette is mono-hue (primary + primaryContainer share the seed's hue), so it can't get
    // depth from hue spread the way a multi-hue flag does. Pull its darker seed this many tone units
    // below the bloom tone so the wash reads as a gradient, not a flat, over-intense single hue.
    const val DateToneSpread = 14.0

    // A mono-hue date wash reads more intensely than a multi-hue flag at equal chroma (one solid hue
    // vs a blend), so the date palette uses this fraction of the flag bloom chroma to feel as soft.
    const val DateChromaScale = 0.50

    data class ThemeParams(val chroma: Double, val tone: Double, val alpha: Float)

    // Per-theme bloom params, from the hero-background-placements design (Aurora band).
    val LightBloom = ThemeParams(chroma = 44.0, tone = 82.0, alpha = 0.55f)
    val DarkBloom = ThemeParams(chroma = 58.0, tone = 66.0, alpha = 0.42f)

    // Without the haze blur to diffuse it (API < 31), the wash is drawn sharp over the card and
    // reads stronger, so its opacity is scaled down by this fraction to feel closer to the
    // blurred look. Colour (chroma/tone) is left alone; only the bloom's opacity drops.
    const val UnblurredAlphaScale = 0.6f

    fun bloomParams(isDark: Boolean, blurred: Boolean = true): ThemeParams {
        val base = if (isDark) DarkBloom else LightBloom
        return if (blurred) base else base.copy(alpha = base.alpha * UnblurredAlphaScale)
    }

    /**
     * Normalise one ARGB [seed]: chromatic -> (hue, [chroma], [tone]); neutral -> (hue, 0,
     * own tone clamped to [[NeutralToneMin], [NeutralToneMax]]). Returns opaque ARGB.
     */
    fun normalize(seed: Int, chroma: Double, tone: Double): Int {
        val hct = Hct.fromInt(seed)
        return if (hct.chroma < NeutralChromaThreshold) {
            Hct.from(hct.hue, 0.0, hct.tone.coerceIn(NeutralToneMin, NeutralToneMax)).toInt()
        } else {
            Hct.from(hct.hue, chroma, tone).toInt()
        }
    }

    /** Hue-sort [seeds], cutting the largest wheel gap so the run spans the shortest arc. */
    fun hueSorted(seeds: List<Int>): List<Int> {
        if (seeds.size < 2) return seeds
        val sorted = seeds.sortedBy { Hct.fromInt(it).hue }
        val hues = sorted.map { Hct.fromInt(it).hue }
        var maxGap = -1.0
        var cut = 0
        for (i in hues.indices) {
            val next = if (i == hues.lastIndex) hues[0] + 360.0 else hues[i + 1]
            val gap = next - hues[i]
            if (gap > maxGap) {
                maxGap = gap
                cut = (i + 1) % hues.size
            }
        }
        return sorted.subList(cut, sorted.size) + sorted.subList(0, cut)
    }

    /**
     * The flag's colour palette for placement: its chromatic seeds only (source chroma >=
     * [NeutralChromaThreshold]). Pure neutrals (white/black/grey) are dropped so the wash and
     * swatch read as colour, not grey — unless that leaves fewer than two seeds, in which case the
     * neutrals nearest mid-grey ([NeutralRestoreTone]) are restored until two remain so the flag
     * still blooms as a pair.
     */
    fun paletteSeeds(seeds: List<Int>): List<Int> {
        val chromatic = seeds.filter { Hct.fromInt(it).chroma >= NeutralChromaThreshold }
        if (chromatic.size >= 2) return chromatic
        val restored = chromatic.toMutableList()
        seeds.filter { Hct.fromInt(it).chroma < NeutralChromaThreshold }
            .sortedBy { abs(Hct.fromInt(it).tone - NeutralRestoreTone) }
            .forEach { neutral -> if (restored.size < 2) restored.add(neutral) }
        return restored
    }

    /** Bloom seed colours: chromatic palette, hue-sorted, normalised at the bloom chroma/tone. Opaque. */
    fun bloomColors(seeds: List<Int>, isDark: Boolean): List<Int> {
        val params = bloomParams(isDark)
        return hueSorted(paletteSeeds(seeds)).map { normalize(it, params.chroma, params.tone) }
    }

    /**
     * Date palette bloom colours. Chroma is capped *below* the flag bloom chroma ([DateChromaScale])
     * because a mono-hue wash reads more intensely than a multi-hue flag blend, and the two seeds are
     * held at distinct tones ([params.tone] and [params.tone] - [DateToneSpread]) so the pair reads
     * as a gradient with depth instead of collapsing to one flat, over-intense colour. Opaque.
     */
    fun dateColorBloomColors(primary: Int, primaryContainer: Int, isDark: Boolean): List<Int> {
        val params = bloomParams(isDark)
        val chroma = params.chroma * DateChromaScale
        return listOf(
            normalize(primary, chroma, params.tone),
            normalize(primaryContainer, chroma, params.tone - DateToneSpread),
        )
    }
}
