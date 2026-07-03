package com.mkx.hrttracker.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import com.mkx.hrttracker.model.journal.TrackedDate
import com.mkx.hrttracker.ui.journal.anchorIconRes
import com.mkx.hrttracker.ui.theme.MedicationGroupPalettes
import java.time.LocalDate

// Draws the pinned-shortcut adaptive bitmap. The launcher masks the 432×432 canvas to its
// own shape (circle/squircle/teardrop), so the gradient background is full-bleed, the glyph
// is a decorative corner peek, and the number stays inside the inner 66% safe zone. Pure
// given (anchor, today): no I/O, no shared state — safe to call from the refresh worker.
// Validated on-device by the probe.
object AnchorIconRenderer {
    // Adaptive-icon canvas: 108dp @ 4x. The launcher's mask keeps ~66% of the diameter
    // visible regardless of shape, so the number must live within that inner box.
    private const val SIZE = 432
    private const val SAFE_FRACTION = 0.66f
    private val SAFE_INSET = (SIZE * (1f - SAFE_FRACTION) / 2f) // ≈ 73px each side
    private val SAFE_BOX = SIZE - 2 * SAFE_INSET                // ≈ 286px

    fun render(context: Context, anchor: TrackedDate, today: LocalDate): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Diagonal accent gradient (top-left +15% white -> bottom-right +15% black) keeps
        // every launcher mask filled while giving the flat fill some depth (spec section 1).
        val accent = MedicationGroupPalettes.getValue(anchor.palette).lightAccent.toArgb()
        val gradientPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, SIZE.toFloat(), SIZE.toFloat(),
                ColorUtils.blendARGB(accent, android.graphics.Color.WHITE, 0.15f),
                ColorUtils.blendARGB(accent, android.graphics.Color.BLACK, 0.15f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), gradientPaint)

        // Glyph peek: white, ~14% alpha, tucked into the top-right corner and deliberately
        // overhanging the canvas so the launcher mask half-clips it. Top-right keeps it out
        // from under the launcher's pinned-shortcut app badge (bottom-right) and matches the
        // in-app hero watermark. Decoration only - the safe zone applies to the number, not
        // to this (spec section 1).
        ResourcesCompat.getDrawable(context.resources, anchorIconRes(anchor.icon), context.theme)
            ?.mutate()
            ?.let { drawable ->
                DrawableCompat.setTint(drawable, android.graphics.Color.WHITE)
                drawable.alpha = 36 // ~14%
                val glyphSize = (SIZE * 0.79f).toInt()
                val overhang = (SIZE * 0.19f).toInt()
                drawable.setBounds(
                    SIZE + overhang - glyphSize,
                    -overhang,
                    SIZE + overhang,
                    glyphSize - overhang,
                )
                drawable.draw(canvas)
            }

        // Label in front, white bold, auto-shrunk to fit the safe-zone width.
        val label = anchorIconLabel(anchor.date, today)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        paint.textSize = fitTextSize(paint, label, maxWidth = SAFE_BOX * 0.92f, startSize = 220f)
        // Vertically centre on the text's visual midline.
        val metrics = paint.fontMetrics
        val baselineY = SIZE / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(label, SIZE / 2f, baselineY, paint)

        return bitmap
    }

    // Largest text size (down to a floor) whose measured width fits maxWidth.
    private fun fitTextSize(paint: Paint, text: String, maxWidth: Float, startSize: Float): Float {
        var size = startSize
        paint.textSize = size
        while (paint.measureText(text) > maxWidth && size > 40f) {
            size -= 4f
            paint.textSize = size
        }
        return size
    }
}
