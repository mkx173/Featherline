package com.mkx.hrttracker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentPositionTest {
    @Test
    fun emptySection_hasNoPositions() {
        assertEquals(emptyList<SegmentPosition?>(), segmentPositionsFor(emptyList()))
    }

    @Test
    fun singleVisibleRow_isAStandaloneCard() {
        assertEquals(listOf(SegmentPosition(0, 1)), segmentPositionsFor(listOf(true)))
    }

    @Test
    fun threeVisibleRows_areNumberedZeroToTwoOfThree() {
        assertEquals(
            listOf(SegmentPosition(0, 3), SegmentPosition(1, 3), SegmentPosition(2, 3)),
            segmentPositionsFor(listOf(true, true, true)),
        )
    }

    @Test
    fun hiddenRowIsExcludedFromCountAndYieldsNull() {
        assertEquals(
            listOf(SegmentPosition(0, 2), null, SegmentPosition(1, 2)),
            segmentPositionsFor(listOf(true, false, true)),
        )
    }

    @Test
    fun allHidden_yieldsAllNull() {
        assertEquals(listOf<SegmentPosition?>(null), segmentPositionsFor(listOf(false)))
    }
}
