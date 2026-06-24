package com.mkx.hrttracker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectionFabScrollStateTest {
    @Test
    fun requiresHideThresholdBeforeHiding() {
        val afterSmallScroll = updateSelectionFabScrollState(
            state = SelectionFabScrollState(visible = true),
            previousIndex = 0,
            previousOffset = 0,
            index = 0,
            offset = 24,
            estimatedItemSizePx = 100,
            hideThresholdPx = 48,
            showThresholdPx = 24,
        )
        val afterThresholdScroll = updateSelectionFabScrollState(
            state = afterSmallScroll,
            previousIndex = 0,
            previousOffset = 24,
            index = 0,
            offset = 48,
            estimatedItemSizePx = 100,
            hideThresholdPx = 48,
            showThresholdPx = 24,
        )

        assertEquals(true, afterSmallScroll.visible)
        assertEquals(false, afterThresholdScroll.visible)
    }

    @Test
    fun requiresShowThresholdBeforeShowing() {
        val afterSmallScroll = updateSelectionFabScrollState(
            state = SelectionFabScrollState(visible = false),
            previousIndex = 0,
            previousOffset = 100,
            index = 0,
            offset = 88,
            estimatedItemSizePx = 100,
            hideThresholdPx = 48,
            showThresholdPx = 24,
        )
        val afterThresholdScroll = updateSelectionFabScrollState(
            state = afterSmallScroll,
            previousIndex = 0,
            previousOffset = 88,
            index = 0,
            offset = 76,
            estimatedItemSizePx = 100,
            hideThresholdPx = 48,
            showThresholdPx = 24,
        )

        assertEquals(false, afterSmallScroll.visible)
        assertEquals(true, afterThresholdScroll.visible)
    }

    @Test
    fun resetsAccumulatedScrollWhenDirectionChanges() {
        val afterDownScroll = updateSelectionFabScrollState(
            state = SelectionFabScrollState(visible = true),
            previousIndex = 0,
            previousOffset = 0,
            index = 0,
            offset = 32,
            estimatedItemSizePx = 100,
            hideThresholdPx = 48,
            showThresholdPx = 24,
        )
        val afterDirectionChange = updateSelectionFabScrollState(
            state = afterDownScroll,
            previousIndex = 0,
            previousOffset = 32,
            index = 0,
            offset = 24,
            estimatedItemSizePx = 100,
            hideThresholdPx = 48,
            showThresholdPx = 24,
        )
        val afterSecondDownScroll = updateSelectionFabScrollState(
            state = afterDirectionChange,
            previousIndex = 0,
            previousOffset = 24,
            index = 0,
            offset = 44,
            estimatedItemSizePx = 100,
            hideThresholdPx = 48,
            showThresholdPx = 24,
        )

        assertEquals(true, afterDownScroll.visible)
        assertEquals(true, afterDirectionChange.visible)
        assertEquals(true, afterSecondDownScroll.visible)
    }

    @Test
    fun itemBoundaryCrossKeepsHysteresis() {
        val afterTinyDownCross = updateSelectionFabScrollState(
            state = SelectionFabScrollState(visible = true),
            previousIndex = 0,
            previousOffset = 95,
            index = 1,
            offset = 0,
            estimatedItemSizePx = 100,
            hideThresholdPx = 48,
            showThresholdPx = 24,
        )
        val afterTinyUpCross = updateSelectionFabScrollState(
            state = SelectionFabScrollState(visible = false),
            previousIndex = 1,
            previousOffset = 0,
            index = 0,
            offset = 95,
            estimatedItemSizePx = 100,
            hideThresholdPx = 48,
            showThresholdPx = 24,
        )

        assertEquals(true, afterTinyDownCross.visible)
        assertEquals(false, afterTinyUpCross.visible)
    }

    @Test
    fun flingAcrossItemsStillHides() {
        val afterFling = updateSelectionFabScrollState(
            state = SelectionFabScrollState(visible = true),
            previousIndex = 0,
            previousOffset = 0,
            index = 2,
            offset = 10,
            estimatedItemSizePx = 100,
            hideThresholdPx = 48,
            showThresholdPx = 24,
        )

        assertEquals(false, afterFling.visible)
    }
}
