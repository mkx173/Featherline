package com.mkx.hrttracker.ui.journal

import com.materialkolor.hct.Hct

/**
 * Turns a flag's raw seed colours into the normalised colours the hero bloom and the
 * dialog swatch share, so a flag's chip and its rendered background never diverge.
 *
 * Pure (no Compose): operates on packed ARGB ints. Chromatic seeds keep their hue but
 * adopt the theme's common chroma/tone; neutral seeds (chroma below
 * [NeutralChromaThreshold]) drop to chroma 0 yet keep their own lightness (clamped to a
 * visible band) so black, grey and white stay distinct instead of collapsing to one grey.
 *
 * All chroma/tone/alpha values are current prototype values, expected to be re-tuned.
 */
object HeroBackgroundColors {
    const val NeutralChromaThreshold = 8.0

    // Clamp neutral tone into a visible band: pure black would vanish on a dark card and
    // pure white would blow out a light one, yet black/grey/white must stay ordered.
    const val NeutralToneMin = 20.0
    const val NeutralToneMax = 90.0

    data class ThemeParams(val chroma: Double, val tone: Double, val alpha: Float)

    // Per-theme bloom params (prototype values from the spec's table).
    val LightBloom = ThemeParams(chroma = 36.0, tone = 88.0, alpha = 0.50f)
    val DarkBloom = ThemeParams(chroma = 36.0, tone = 36.0, alpha = 0.35f)

    // Swatch strips reuse the bloom chroma but a lower tone so hues stay distinct at chip size.
    const val SwatchToneLight = 72.0
    const val SwatchToneDark = 44.0

    fun bloomParams(isDark: Boolean): ThemeParams = if (isDark) DarkBloom else LightBloom

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

    /** Bloom seed colours: hue-sorted then normalised at the theme's bloom chroma/tone. Opaque. */
    fun bloomColors(seeds: List<Int>, isDark: Boolean): List<Int> {
        val params = bloomParams(isDark)
        return hueSorted(seeds).map { normalize(it, params.chroma, params.tone) }
    }

    /** Swatch strip colours: hue-sorted then normalised at the swatch tone. Opaque. */
    fun swatchColors(seeds: List<Int>, isDark: Boolean): List<Int> {
        val chroma = bloomParams(isDark).chroma
        val tone = if (isDark) SwatchToneDark else SwatchToneLight
        return hueSorted(seeds).map { normalize(it, chroma, tone) }
    }
}
