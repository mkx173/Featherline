package com.mkx.hrttracker.widget

import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.ui.journal.anchorIconRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AnchorWatermarkTest {
    private fun render() = renderAnchorWatermarkBitmap(
        context = ApplicationProvider.getApplicationContext(),
        iconRes = anchorIconRes(AnchorIcon.entries.first()),
        widthPx = 306,
        heightPx = 100,
        cornerRadiusPx = 24f,
        tintArgb = 0xFF2E5F6E.toInt(),
    )

    // Intent: the watermark is a top-right decoration on an otherwise transparent
    // backdrop - ink anywhere else would sit behind the widget's text (spec section 2).
    @Test
    fun `ink is confined to the top-right region`() {
        val bitmap = render()
        var topRightInk = 0
        for (x in 0 until bitmap.width step 4) {
            for (y in 0 until bitmap.height step 4) {
                val alpha = Color.alpha(bitmap.getPixel(x, y))
                if (x >= bitmap.width / 2 && y < bitmap.height * 3 / 4) {
                    if (alpha > 0) topRightInk++
                } else if (x < bitmap.width / 3) {
                    // Left third must stay fully transparent (name/since text sits there).
                    assertEquals("ink at ($x,$y)", 0, alpha)
                }
            }
        }
        assertTrue("expected some watermark ink in the top-right region", topRightInk > 0)
    }

    // Intent: 10% alpha reads as a watermark; anything near-opaque would compete
    // with the day count.
    @Test
    fun `ink is faint`() {
        val bitmap = render()
        var maxAlpha = 0
        for (x in 0 until bitmap.width step 2) {
            for (y in 0 until bitmap.height step 2) {
                maxAlpha = maxOf(maxAlpha, Color.alpha(bitmap.getPixel(x, y)))
            }
        }
        assertTrue("max alpha $maxAlpha should be <= ~10%", maxAlpha in 1..40)
    }

    @Test
    fun `missing drawable returns transparent bitmap`() {
        val bitmap = renderAnchorWatermarkBitmap(
            context = ApplicationProvider.getApplicationContext(),
            iconRes = 0,
            widthPx = 37,
            heightPx = 23,
            cornerRadiusPx = 8f,
            tintArgb = 0xFF2E5F6E.toInt(),
        )

        assertEquals(37, bitmap.width)
        assertEquals(23, bitmap.height)
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                assertEquals("ink at ($x,$y)", 0, Color.alpha(bitmap.getPixel(x, y)))
            }
        }
    }
}
