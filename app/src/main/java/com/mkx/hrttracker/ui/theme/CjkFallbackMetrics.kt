package com.mkx.hrttracker.ui.theme

import android.graphics.Typeface
import android.os.Build
import android.text.StaticLayout
import android.text.TextPaint

/**
 * True when a line holding a CJK glyph gets a taller ascent/descent box than the system Latin
 * font alone, i.e. the platform draws CJK from a fallback font with bigger vertical metrics
 * (Roboto + Noto Sans CJK on Pixel, Roboto + SEC CJK on Samsung). Compose always lays text out
 * with fallback line spacing, so on such devices the baseline drops about 1.5 dp whenever CJK
 * text appears and text fields jump on the first Chinese character.
 *
 * False when the system font covers CJK itself (MiSans on HyperOS, HarmonyOS Sans, OPPO Sans)
 * and on API < 28, where Android has no fallback line spacing at all.
 *
 * [typeface] is the font to test; production passes the system default, tests and the debug
 * font lab pass a loaded file to simulate another OEM.
 */
fun hasTallerCjkFallback(typeface: Typeface = Typeface.DEFAULT): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
    // A layout failure here must not take the theme down with it: fall back to stock typography.
    return runCatching {
        val paint = TextPaint().apply {
            this.typeface = typeface
            textSize = 100f
        }
        val own = paint.fontMetricsInt
        val sample = "中"
        val layout = StaticLayout.Builder.obtain(sample, 0, sample.length, paint, 1000)
            .setUseLineSpacingFromFallbacks(true)
            .setIncludePad(false)
            .build()
        // ponytail: 1 px tolerance at 100 px absorbs rounding; a real mismatch is 20+ px.
        layout.getLineAscent(0) < own.ascent - 1 || layout.getLineDescent(0) > own.descent + 1
    }.getOrDefault(false)
}
