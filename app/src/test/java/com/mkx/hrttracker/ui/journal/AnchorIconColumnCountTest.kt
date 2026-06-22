package com.mkx.hrttracker.ui.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnchorIconColumnCountTest {
    private val gap = 8f
    private val maxTile = 56f

    @Test
    fun phoneWidths_keepEightColumns() {
        // The phone layout (two rows of eight) must be preserved: at typical phone content
        // widths the eight tiles are already smaller than maxTile, so no columns are added.
        assertEquals(8, anchorIconColumnCount(availableWidthDp = 360f, gapDp = gap, maxTileDp = maxTile))
        assertEquals(8, anchorIconColumnCount(availableWidthDp = 411f, gapDp = gap, maxTileDp = maxTile))
    }

    @Test
    fun neverFallsBelowEightColumns_evenWhenNarrow() {
        assertEquals(8, anchorIconColumnCount(availableWidthDp = 200f, gapDp = gap, maxTileDp = maxTile))
    }

    @Test
    fun wideSheet_addsColumnsSoTileStaysWithinMax() {
        // A tablet/unfolded sheet (capped near 640dp) must add columns so each tile stays
        // bounded by maxTile instead of inflating around the fixed-size glyph.
        val width = 600f
        val columns = anchorIconColumnCount(availableWidthDp = width, gapDp = gap, maxTileDp = maxTile)
        assertTrue("expected more than 8 columns for a wide sheet, got $columns", columns > 8)
        val tile = (width - (columns - 1) * gap) / columns
        assertTrue("tile width $tile should not exceed maxTile $maxTile", tile <= maxTile)
    }
}
