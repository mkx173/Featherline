package com.mkx.hrttracker.ui.main

import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.RunwayProjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class MainLowStockSectionTest {
    @Test
    fun mainLowStockExpandChevronTargetRotation_pointsDownWhenCollapsedAndUpWhenExpanded() {
        assertEquals(0f, mainLowStockExpandChevronTargetRotation(expanded = false), 0f)
        assertEquals(180f, mainLowStockExpandChevronTargetRotation(expanded = true), 0f)
    }

    @Test
    fun mainLowStockRunwaySupportingText_uses_days_remaining_copy() {
        val text = mainLowStockRunwaySupportingText(
            RunwayProjection.Days(
                days = 7,
                lastFulfillable = LocalDate.of(2026, 1, 8),
            ),
        )

        assertEquals(R.string.stock_runway_days_remaining, text.resId)
        assertEquals(7, text.intArg)
    }

    @Test
    fun mainLowStockRunwaySupportingText_uses_more_than_one_year_copy() {
        val text = mainLowStockRunwaySupportingText(RunwayProjection.BeyondHorizon)

        assertEquals(R.string.stock_runway_more_than_one_year, text.resId)
        assertNull(text.intArg)
    }

    @Test
    fun mainLowStockRunwaySupportingText_uses_unknown_copy_without_schedule() {
        val text = mainLowStockRunwaySupportingText(RunwayProjection.NoSchedule)

        assertEquals(R.string.stock_runway_unknown_title, text.resId)
        assertNull(text.intArg)
    }
}
