package com.mkx.hrttracker.ui.history

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryScreenTopAppBarTest {
    @Test
    fun historyHasScrolled_returnsFalse_only_atAbsoluteTop() {
        assertEquals(false, historyHasScrolled(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0))
    }

    @Test
    fun historyHasScrolled_returnsTrue_whenOffsetOrIndexIsPastTop() {
        assertEquals(true, historyHasScrolled(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 1))
        assertEquals(true, historyHasScrolled(firstVisibleItemIndex = 1, firstVisibleItemScrollOffset = 0))
    }

    @Test
    fun scrolledTopAppBarContentOffset_mapsScrolledStateToStableValues() {
        assertEquals(0f, scrolledTopAppBarContentOffset(false))
        assertEquals(1f, scrolledTopAppBarContentOffset(true))
    }
}
