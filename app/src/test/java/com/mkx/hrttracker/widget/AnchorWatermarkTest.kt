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
    private fun render(visibleSizePx: Int = 100, cornerRadiusPx: Float = 24f) =
        renderAnchorWatermarkBitmap(
            context = ApplicationProvider.getApplicationContext(),
            iconRes = anchorIconRes(AnchorIcon.entries.first()),
            visibleSizePx = visibleSizePx,
            cornerRadiusPx = cornerRadiusPx,
        )

    // Intent: the bitmap IS the glyph's visible crop — a square the layout anchors
    // top-end (spec section 2). A rectangular or empty bake would mean the layout
    // contract (size(0.88 x card height), TopEnd) no longer draws what it promises.
    @Test
    fun `bitmap is the square visible glyph region with ink`() {
        val bitmap = render()
        assertEquals(100, bitmap.width)
        assertEquals(100, bitmap.height)
        var ink = 0
        for (x in 0 until bitmap.width step 2) {
            for (y in 0 until bitmap.height step 2) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 0) ink++
            }
        }
        assertTrue("expected watermark ink in the visible region", ink > 0)
    }

    // Intent: the bleed is baked as a crop of the glyph's top/end overhang, so ink must
    // reach the top and end edges (mid-glyph cut). If the crop pointed the wrong way the
    // glyph would float inset and the widget would lose the bleed-off-corner look.
    @Test
    fun `glyph bleeds off the top and end edges`() {
        val bitmap = render(cornerRadiusPx = 0f)
        var topEdgeInk = 0
        var endEdgeInk = 0
        for (i in 0 until 100 step 2) {
            if (Color.alpha(bitmap.getPixel(i, 0)) > 0) topEdgeInk++
            if (Color.alpha(bitmap.getPixel(99, i)) > 0) endEdgeInk++
        }
        assertTrue("expected ink cut off at the top edge", topEdgeInk > 0)
        assertTrue("expected ink cut off at the end edge", endEdgeInk > 0)
    }

    // Intent: the square sits flush at the card's rounded top-end corner, so the corner
    // arc must be clipped in the bake — without it the glyph pokes past the shell on
    // launchers where cornerRadius doesn't clip children. (95,4) lies outside a 24px
    // corner arc; the unclipped render proves the glyph inks that point at radius 0.
    @Test
    fun `top-end corner is clipped to the card radius`() {
        val unclipped = render(cornerRadiusPx = 0f)
        assertTrue(
            "sample point must be inked before clipping",
            Color.alpha(unclipped.getPixel(95, 4)) > 0,
        )
        val clipped = render(cornerRadiusPx = 24f)
        assertEquals(0, Color.alpha(clipped.getPixel(95, 4)))
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
            visibleSizePx = 37,
            cornerRadiusPx = 8f,
        )

        assertEquals(37, bitmap.width)
        assertEquals(37, bitmap.height)
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                assertEquals("ink at ($x,$y)", 0, Color.alpha(bitmap.getPixel(x, y)))
            }
        }
    }
}
