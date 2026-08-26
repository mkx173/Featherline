package com.mkx.hrttracker.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The user's content scale is the ONLY scale knob: launcher cell allocation never
 * multiplies into it (the old baseline ratio was removed), so the stored value must be
 * bounded here — nothing downstream shrinks an oversized value to fit a cell.
 */
class WidgetContentScaleTest {

    @Test
    fun `default content scale is 1x`() {
        assertEquals(1.0f, WidgetAppearance.Default.contentScale)
    }

    @Test
    fun `sanitized clamps content scale to the slider range`() {
        val base = WidgetAppearance.Default
        assertEquals(0.5f, base.copy(contentScale = 0.1f).sanitized().contentScale)
        assertEquals(1.5f, base.copy(contentScale = 9f).sanitized().contentScale)
        assertEquals(1.0f, base.copy(contentScale = Float.NaN).sanitized().contentScale)
        assertEquals(1.2f, base.copy(contentScale = 1.2f).sanitized().contentScale)
    }
}
