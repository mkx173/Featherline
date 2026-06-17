package com.mkx.hrttracker.ui.journal

import org.junit.Assert.assertEquals
import org.junit.Test

class PinnedReorderTest {
    private val ids = listOf("a", "b", "c")

    @Test fun moveToTop_putsItemFirst() {
        assertEquals(listOf("c", "a", "b"), reorderedIds(ids, fromIndex = 2, toIndex = 0))
    }

    @Test fun moveUp_swapsWithPrevious() {
        assertEquals(listOf("a", "c", "b"), reorderedIds(ids, fromIndex = 2, toIndex = 1))
    }

    @Test fun moveDown_swapsWithNext() {
        assertEquals(listOf("b", "a", "c"), reorderedIds(ids, fromIndex = 0, toIndex = 1))
    }

    @Test fun outOfRange_returnsUnchanged() {
        assertEquals(ids, reorderedIds(ids, fromIndex = 0, toIndex = 5))
    }
}
