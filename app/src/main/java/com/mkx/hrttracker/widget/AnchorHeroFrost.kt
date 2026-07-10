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

// Baked "frosted aurora" wash for the anchor widget — the home-screen mirror of the in-app
// hero wash. Glance can't draw a Compose brush, blur, or animate, so we bake the same
// HeroBackgroundColors the in-app hero uses into a small radial-bloom bitmap and upscale it
// with bilinear filtering: a low-res upscale of a smooth gradient is visually indistinguishable
// from a Gaussian blur, for free (frost-workaround design, 2026-06-27).
//
// The blooms LAYER OVER the appearance colour system's seeded card (base under, widgetScrim
// over — both day/night colour providers that flip with the system at apply time), so the
// seed/saturation/balance sliders tint the background under the wash, matching the in-app
// hero which draws these blooms over the app's themed surface. Only the blooms are a baked
// bitmap, tuned to the composing mode and re-tuned on the next re-render.

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

// The widget wash reads stronger than the in-app hero's (smaller card, no real blur), so it
// runs at this fraction of the hero bloom alpha. Tune visually.
private const val WIDGET_BLOOM_ALPHA_SCALE = 0.75f

// The selected flag's bloom colours (ARGB with the bloom alpha baked in). Uses the blurred bloom
// params on every API: the upscale supplies the diffusion the in-app Haze provides. Flags only —
// the widget config offers the flag palette, not the in-app None/DateColor options.
// backgroundAlpha: the opacity slider, so the wash fades together with the base instead of
// staying at full strength on a see-through card.
internal fun flagBloomColors(flag: PrideFlag, isDark: Boolean, backgroundAlpha: Float): List<Int> {
    val alpha = HeroBackgroundColors.bloomParams(isDark, blurred = true).alpha *
        WIDGET_BLOOM_ALPHA_SCALE * backgroundAlpha.coerceIn(0f, 1f)
    return HeroBackgroundColors.bloomColors(flag.seeds, isDark, blurred = true)
        .map { ColorUtils.setAlphaComponent(it, (alpha * 255f).toInt().coerceIn(0, 255)) }
}

// Bake the bloom colours into a TRANSPARENT, rounded-corner bitmap sized to the widget:
// radial blooms (faded down) → bilinear upscale (the "blur") → rounded clip. The seeded
// card UNDER it and the shell-tinted scrim OVER it (widgetScrim) are separate day/night
// colour-provider layers, so they flip with the system at RemoteViews apply time; only
// this bloom wash is baked. Its light/dark tuning refreshes on the next re-render —
// accepted as visually close enough not to chase (resolved micro-decision).
internal fun renderAnchorBloomsBitmap(
    widthPx: Int,
    heightPx: Int,
    cornerRadiusPx: Float,
    bloomColors: List<Int>,
): Bitmap {
    val smallW = (widthPx / SMALL_DIVISOR).coerceAtLeast(2)
    val smallH = (heightPx / SMALL_DIVISOR).coerceAtLeast(2)
    val small = createBitmap(smallW, smallH)
    val canvas = Canvas(small)

    // Blooms drawn on their own layer so the vertical fade clips only the wash.
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
