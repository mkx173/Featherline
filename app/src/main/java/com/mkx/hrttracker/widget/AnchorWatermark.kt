package com.mkx.hrttracker.widget

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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

// The visible portion of the glyph once the overhang bleeds off the card: the glyph is
// GLYPH_HEIGHT_FRACTION x card height, and GLYPH_OVERHANG_FRACTION of it is cropped off
// the top and end edges, leaving a square of this fraction of the card height.
internal const val GLYPH_VISIBLE_HEIGHT_FRACTION =
    GLYPH_HEIGHT_FRACTION * (1f - GLYPH_OVERHANG_FRACTION)

// Bakes the hero watermark's VISIBLE region for the anchor widget: the anchor glyph at 10%
// alpha with its bleed-off-the-corner overhang pre-cropped into the bitmap, so LAYOUT owns
// the placement (a top-end box sized GLYPH_VISIBLE_HEIGHT_FRACTION x card height in
// AnchorWidgetContent). The old full-card FillBounds bake was only correct when the
// RemoteViews displayed at exactly the composed size — any host re-apply at another size
// (picker preview box, resize window, fallback-sized compose) stretched the glyph
// non-uniformly. A fixed-dp top-end image can drift in scale but can never deform. Baked
// WHITE and colour-tinted by the composable's ColorFilter so the launcher resolves the
// day/night tint at RemoteViews apply time — baking a colour here would leave it stuck in
// whichever uiMode the process composed in (same rule as the dose widgets' progress ring).
internal fun renderAnchorWatermarkBitmap(
    context: Context,
    iconRes: Int,
    visibleSizePx: Int,
    cornerRadiusPx: Float,
): Bitmap {
    val bitmap = createBitmap(visibleSizePx, visibleSizePx)
    val canvas = Canvas(bitmap)
    val drawable = try {
        ResourcesCompat.getDrawable(context.resources, iconRes, context.theme)
    } catch (_: Resources.NotFoundException) {
        null
    }
    drawable
        ?.mutate()
        ?.let { drawable ->
            DrawableCompat.setTint(drawable, android.graphics.Color.WHITE)
            drawable.alpha = GLYPH_ALPHA
            // Full glyph placed so its overhang falls outside the canvas: top and end
            // strips (GLYPH_OVERHANG_FRACTION of the glyph) are cropped by the bitmap
            // bounds, exactly the crop the card edge used to apply.
            val glyphSize =
                (visibleSizePx / (1f - GLYPH_OVERHANG_FRACTION)).toInt()
            val overhang = glyphSize - visibleSizePx
            drawable.setBounds(0, -overhang, glyphSize, visibleSizePx)
            drawable.draw(canvas)
        }

    // Clip the bitmap's top-end corner to the card's rounded corner: the visible square
    // sits flush at the card's top-right, so only that corner needs the arc (same SRC_IN
    // clip as the frost, applied to one corner).
    val rounded = createBitmap(visibleSizePx, visibleSizePx)
    val roundedCanvas = Canvas(rounded)
    val clipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val clipPath = Path().apply {
        addRoundRect(
            RectF(0f, 0f, visibleSizePx.toFloat(), visibleSizePx.toFloat()),
            floatArrayOf(0f, 0f, cornerRadiusPx, cornerRadiusPx, 0f, 0f, 0f, 0f),
            Path.Direction.CW,
        )
    }
    roundedCanvas.drawPath(clipPath, clipPaint)
    clipPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    roundedCanvas.drawBitmap(bitmap, 0f, 0f, clipPaint)
    bitmap.recycle()
    return rounded
}
