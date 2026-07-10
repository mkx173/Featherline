package com.mkx.hrttracker.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AnchorDayLabelTest {
    private val today = LocalDate.of(2026, 6, 27)

    @Test fun `day 7 shows magnitude`() =
        // Encodes intent: short counts stay numeric so the icon reads as a day count.
        assertEquals("7", anchorIconLabel(today.minusDays(7), today))

    @Test fun `day 364 still shows magnitude just before first anniversary`() =
        assertEquals("364", anchorIconLabel(today.minusDays(364), today))

    @Test fun `today is zero`() =
        assertEquals("0", anchorIconLabel(today, today))

    @Test fun `future date shows magnitude not years`() =
        // Future anchors have completed zero anniversaries, so they count down numerically.
        assertEquals("100", anchorIconLabel(today.plusDays(100), today))

    @Test fun `first anniversary stays numeric`() =
        // Intent: no "Ny" rollover — the icon always shows the day count and the
        // renderer shrinks the font to fit longer numbers.
        assertEquals("365", anchorIconLabel(LocalDate.of(2025, 6, 27), today))

    @Test fun `two full years stays numeric`() =
        assertEquals("730", anchorIconLabel(LocalDate.of(2024, 6, 27), today))
}
