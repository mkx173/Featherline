package com.mkx.hrttracker.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The large header tightens its gap on a fit check, not on the widget's width — a long
// label clips at widths where a short one is comfortable, which a width breakpoint cannot
// express. NATIVE graphics so measureText reports real font metrics rather than
// Robolectric's one-pixel-per-character stub.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LargeHeaderGapTest {

    @Test
    fun roomyGapSurvivesWhenTheHeaderIsShort() {
        assertTrue(fits(headerText = "Today", availableWidthPx = 300f))
    }

    @Test
    fun sameWidthRejectsTheRoomyGapOnceTheHeaderGrows() {
        // Identical width to the case above — only the header changed. A width breakpoint
        // would have kept the roomy gap here and let the label clip.
        assertFalse(
            fits(headerText = "Today · 12/14 done · 3 manual", availableWidthPx = 300f),
        )
    }

    @Test
    fun theRoomyGapItselfCanBeWhatPushesTheHeaderOut() {
        // Same header, same width: it only stops fitting because of the gap, which is
        // exactly the case tightening the gap is there to recover.
        val header = "Today · 12/14 done"
        assertFalse(fits(headerText = header, availableWidthPx = 320f, roomyGapPx = 96f))
        assertTrue(fits(headerText = header, availableWidthPx = 320f, roomyGapPx = 0f))
    }

    private fun fits(
        headerText: String,
        availableWidthPx: Float,
        roomyGapPx: Float = 48f,
    ): Boolean = largeHeaderFitsRoomyGap(
        headerText = headerText,
        e2PlaceholderText = "E2 ~8888 pg/mL",
        headerFontSizePx = 18f,
        e2FontSizePx = 16f,
        roomyGapPx = roomyGapPx,
        availableWidthPx = availableWidthPx,
    )
}
