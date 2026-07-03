package com.mkx.hrttracker.widget

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.DrawableCompat

// Watermark geometry, mirrored from the in-app hero card (JournalComponents:938-945 -
// 160dp glyph on a taller card ~= 1.1x this card's height, offset past the top-right edge).
private const val GLYPH_HEIGHT_FRACTION = 1.1f
private const val GLYPH_OVERHANG_FRACTION = 0.2f
private const val GLYPH_ALPHA = 26 // 10% - matches the in-app hero watermark

// Bakes the hero watermark for the anchor widget: the anchor glyph, accent-tinted at 10%
// alpha, bleeding off the card's top-right corner, on a TRANSPARENT backdrop clipped to the
// card's rounded corners. Glance can't offset/alpha a composable, so this bitmap is the
// card's backdrop layer (spec section 2); same bake pattern as renderAnchorFrostBitmap.
internal fun renderAnchorWatermarkBitmap(
    context: Context,
    iconRes: Int,
    widthPx: Int,
    heightPx: Int,
    cornerRadiusPx: Float,
    tintArgb: Int,
): Bitmap {
    val bitmap = createBitmap(widthPx, heightPx)
    val canvas = Canvas(bitmap)
    val drawable = try {
        ResourcesCompat.getDrawable(context.resources, iconRes, context.theme)
    } catch (_: Resources.NotFoundException) {
        null
    }
    drawable
        ?.mutate()
        ?.let { drawable ->
            DrawableCompat.setTint(drawable, tintArgb)
            drawable.alpha = GLYPH_ALPHA
            val glyphSize = (heightPx * GLYPH_HEIGHT_FRACTION).toInt()
            val overhang = (glyphSize * GLYPH_OVERHANG_FRACTION).toInt()
            drawable.setBounds(
                widthPx + overhang - glyphSize,
                -overhang,
                widthPx + overhang,
                glyphSize - overhang,
            )
            drawable.draw(canvas)
        }

    // Clip to the card's rounded corners so the overhang can't poke past the shell on
    // launchers where cornerRadius doesn't clip children (same SRC_IN clip as the frost).
    val rounded = createBitmap(widthPx, heightPx)
    val roundedCanvas = Canvas(rounded)
    val clipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    roundedCanvas.drawRoundRect(
        RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat()),
        cornerRadiusPx, cornerRadiusPx, clipPaint,
    )
    clipPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    roundedCanvas.drawBitmap(bitmap, 0f, 0f, clipPaint)
    bitmap.recycle()
    return rounded
}
