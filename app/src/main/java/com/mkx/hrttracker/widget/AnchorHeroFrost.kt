package com.mkx.hrttracker.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import com.mkx.hrttracker.model.journal.PrideFlag
import com.mkx.hrttracker.ui.journal.HeroBackgroundColors

// Baked "frosted aurora" background for the anchor widget — the home-screen mirror of the
// in-app hero wash. Glance can't draw a Compose brush, blur, or animate, so we bake the same
// HeroBackgroundColors the in-app hero uses into a small radial-bloom bitmap and upscale it
// with bilinear filtering: a low-res upscale of a smooth gradient is visually indistinguishable
// from a Gaussian blur, for free (frost-workaround design, 2026-06-27).
//
// This background is MUTUALLY EXCLUSIVE with the appearance colour system: when a flag is chosen
// in the widget config the frost is the whole card (neutral base + aurora + scrim) and the
// seed/saturation/balance controls do not apply. Scale and opacity still apply — the base honours
// backgroundAlpha; dark mode selects the base/bloom tones.

// Aurora band geometry, mirrored from JournalComponents.HeroAuroraBackground so the widget wash
// matches the in-app placement: blooms spread left→right across the top, tilting gently down,
// faded out before the lower text.
private const val BLOOM_SPAN_START = 0.12f
private const val BLOOM_SPAN_WIDTH = 0.76f
private const val BLOOM_BAND_TOP = 0.16f
private const val BLOOM_TILT = 0.14f
private const val BLOOM_RADIUS = 0.42f
private const val MASK_OPAQUE_STOP = 0.32f // blooms fully visible from the top down to here…
private const val MASK_FADE_STOP = 0.78f   // …then gone by here, clearing the lower text

// ~1/6 of the target is enough diffusion for a smooth wash and keeps the RemoteViews bitmap cheap.
private const val SMALL_DIVISOR = 6

// Neutral frost surface + foreground, picked by dark mode (NOT the seeded appearance colour —
// the two systems are mutually exclusive). ponytail: flat constants, tune visually.
private const val BASE_DARK = 0xFF1C1B22.toInt()
private const val BASE_LIGHT = 0xFFF3F0F6.toInt()
private const val ON_DARK = 0xFFEDEDF2.toInt()
private const val ON_LIGHT = 0xFF1B1B1F.toInt()
private const val ON_VARIANT_DARK = 0xFFBFBDC7.toInt()
private const val ON_VARIANT_LIGHT = 0xFF49454E.toInt()
private const val SCRIM_ALPHA = 0.16f // translucent base tint over the blooms → frosted read

internal fun frostBaseColor(isDark: Boolean): Int = if (isDark) BASE_DARK else BASE_LIGHT
internal fun frostOnSurface(isDark: Boolean): Int = if (isDark) ON_DARK else ON_LIGHT
internal fun frostOnSurfaceVariant(isDark: Boolean): Int =
    if (isDark) ON_VARIANT_DARK else ON_VARIANT_LIGHT

// The selected flag's bloom colours (ARGB with the bloom alpha baked in). Uses the blurred bloom
// params on every API: the upscale supplies the diffusion the in-app Haze provides. Flags only —
// the widget config offers the flag palette, not the in-app None/DateColor options.
internal fun flagBloomColors(flag: PrideFlag, isDark: Boolean): List<Int> {
    val alpha = HeroBackgroundColors.bloomParams(isDark, blurred = true).alpha
    return HeroBackgroundColors.bloomColors(flag.seeds, isDark, blurred = true)
        .map { ColorUtils.setAlphaComponent(it, (alpha * 255f).toInt().coerceIn(0, 255)) }
}

// Bake the bloom colours into an opaque, rounded-corner card bitmap sized to the widget:
// neutral base → radial blooms (faded down) → frost scrim → bilinear upscale (the "blur") →
// rounded clip. Self-contained: this IS the card.
internal fun renderAnchorFrostBitmap(
    widthPx: Int,
    heightPx: Int,
    cornerRadiusPx: Float,
    isDark: Boolean,
    backgroundAlpha: Float,
    bloomColors: List<Int>,
): Bitmap {
    val smallW = (widthPx / SMALL_DIVISOR).coerceAtLeast(2)
    val smallH = (heightPx / SMALL_DIVISOR).coerceAtLeast(2)
    val small = createBitmap(smallW, smallH)
    val canvas = Canvas(small)

    // Neutral base — the frosted card surface the wash sits on. Honours the opacity slider
    // (backgroundAlpha) so the gradient card lets the wallpaper through, like the colour card.
    val baseAlpha = (backgroundAlpha.coerceIn(0f, 1f) * 255f).toInt()
    canvas.drawColor(ColorUtils.setAlphaComponent(frostBaseColor(isDark), baseAlpha))

    // Blooms drawn on their own layer so the vertical fade clips only the wash, not the base.
    val bloomPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val layer = canvas.saveLayer(0f, 0f, smallW.toFloat(), smallH.toFloat(), null)
    val lastIndex = bloomColors.size - 1
    bloomColors.forEachIndexed { i, color ->
        val t = if (lastIndex <= 0) 0.5f else i.toFloat() / lastIndex
        val cx = (BLOOM_SPAN_START + BLOOM_SPAN_WIDTH * t) * smallW
        val cy = (BLOOM_BAND_TOP + BLOOM_TILT * t) * smallH
        val radius = (BLOOM_RADIUS * smallW).coerceAtLeast(1f)
        bloomPaint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(color, color and 0x00FFFFFF), // hue → same hue at alpha 0
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius, bloomPaint)
    }
    // Vertical fade (DST_IN) keeps the blooms across the top, gone before the lower text.
    val maskPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        shader = LinearGradient(
            0f, 0f, 0f, smallH.toFloat(),
            intArrayOf(0xFF000000.toInt(), 0xFF000000.toInt(), 0),
            floatArrayOf(0f, MASK_OPAQUE_STOP, MASK_FADE_STOP),
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRect(0f, 0f, smallW.toFloat(), smallH.toFloat(), maskPaint)
    canvas.restoreToCount(layer)

    // Frost scrim — a faint neutral wash over the blooms so they read as frosted, not painted.
    canvas.drawColor(
        ColorUtils.setAlphaComponent(
            frostBaseColor(isDark), (SCRIM_ALPHA * backgroundAlpha * 255f).toInt(),
        ),
    )

    // Bilinear upscale — a smooth wash makes this indistinguishable from a real blur.
    val upscaled = Bitmap.createScaledBitmap(small, widthPx, heightPx, true)
    if (upscaled != small) small.recycle()

    // Clip to the card's rounded corners.
    val rounded = createBitmap(widthPx, heightPx)
    val roundedCanvas = Canvas(rounded)
    val clipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    roundedCanvas.drawRoundRect(
        RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat()),
        cornerRadiusPx, cornerRadiusPx, clipPaint,
    )
    clipPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    roundedCanvas.drawBitmap(upscaled, 0f, 0f, clipPaint)
    upscaled.recycle()
    return rounded
}
