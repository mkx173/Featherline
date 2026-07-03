package com.mkx.hrttracker.widget

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.journal.TrackedDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AnchorIconRendererTest {
    private val anchor = TrackedDate(
        id = "a1",
        name = "HRT",
        icon = AnchorIcon.entries.first(),
        date = LocalDate.of(2026, 6, 1),
        palette = null,
        pinnedOrder = null,
    )

    // Intent: the background must be a top-left-to-bottom-right gradient (spec section 1).
    // Flat, vertical, or horizontal fill regressions would leave at least one comparison equal.
    @Test
    fun `background is brightest at top-left of diagonal gradient`() {
        val bitmap = AnchorIconRenderer.render(
            ApplicationProvider.getApplicationContext(),
            anchor,
            today = LocalDate.of(2026, 7, 3),
        )
        // Sample just inside the corners, away from the number and the corner glyph.
        val topLeft = ColorUtils.calculateLuminance(bitmap.getPixel(20, 20))
        val topRight = ColorUtils.calculateLuminance(bitmap.getPixel(411, 20))
        val bottomLeft = ColorUtils.calculateLuminance(bitmap.getPixel(20, 411))
        assertTrue(
            "expected top-left ($topLeft) brighter than top-right ($topRight)",
            topLeft > topRight,
        )
        assertTrue(
            "expected top-left ($topLeft) brighter than bottom-left ($bottomLeft)",
            topLeft > bottomLeft,
        )
        // Every sampled pixel is opaque: the gradient must stay full-bleed for the launcher mask.
        assertEquals("top-left alpha", 255, Color.alpha(bitmap.getPixel(2, 2)))
        assertEquals("bottom-right alpha", 255, Color.alpha(bitmap.getPixel(429, 429)))
    }
}
