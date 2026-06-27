package com.mkx.hrttracker.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.mkx.hrttracker.model.journal.TrackedDate
import com.mkx.hrttracker.ui.journal.anchorIconRes
import com.mkx.hrttracker.ui.theme.MedicationGroupPalettes
import java.time.LocalDate

// Draws the pinned-shortcut adaptive bitmap. The launcher masks the 432×432 canvas to its
// own shape (circle/squircle/teardrop), so the background is full-bleed and all meaningful
// content stays inside the inner 66% safe zone. Pure given (anchor, today): no I/O, no
// shared state — safe to call from the refresh worker. Validated on-device by the probe.
object AnchorIconRenderer {
    // Adaptive-icon canvas: 108dp @ 4x. The launcher's mask keeps ~66% of the diameter
    // visible regardless of shape, so labels/glyphs must live within that inner box.
    private const val SIZE = 432
    private const val SAFE_FRACTION = 0.66f
    private val SAFE_INSET = (SIZE * (1f - SAFE_FRACTION) / 2f) // ≈ 73px each side
    private val SAFE_BOX = SIZE - 2 * SAFE_INSET                // ≈ 286px

    fun render(context: Context, anchor: TrackedDate, today: LocalDate): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Full-bleed background in the anchor's palette accent so every launcher mask
        // stays filled. lightAccent is the vivid tone; the label/glyph are white on top.
        val accent = MedicationGroupPalettes.getValue(anchor.palette).lightAccent.toArgb()
        canvas.drawColor(accent)

        // Watermark glyph: the anchor's vector drawable, white, low alpha, centred and
        // sized to the safe box so the launcher mask never clips it.
        ResourcesCompat.getDrawable(context.resources, anchorIconRes(anchor.icon), context.theme)
            ?.mutate()
            ?.let { drawable ->
                DrawableCompat.setTint(drawable, android.graphics.Color.WHITE)
                drawable.alpha = 56 // ~22% — a backdrop behind the number, not competing with it
                val half = (SAFE_BOX * 0.5f).toInt()
                val cx = SIZE / 2
                val cy = SIZE / 2
                drawable.setBounds(cx - half, cy - half, cx + half, cy + half)
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
