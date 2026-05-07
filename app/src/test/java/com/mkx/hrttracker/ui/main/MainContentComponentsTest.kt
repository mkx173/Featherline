package com.mkx.hrttracker.ui.main

import com.mkx.hrttracker.R
import org.junit.Assert.assertEquals
import org.junit.Test

class MainContentComponentsTest {
    @Test
    fun mainE2EstimateInfoToastRes_uses_plasma_concentration_estimate_disclaimer() {
        assertEquals(
            R.string.medical_disclaimer_plasma_concentration_estimates,
            mainE2EstimateInfoToastRes()
        )
    }

    @Test
    fun mainTodayCountLabel_omits_manual_count_when_absent() {
        assertEquals(
            "1/4",
            mainTodayCountLabel(
                doneCount = 1,
                totalCount = 4,
                manualCount = 0
            )
        )
    }

    @Test
    fun mainTodayCountLabel_appends_manual_count_when_present() {
        assertEquals(
            "1/4 (2)",
            mainTodayCountLabel(
                doneCount = 1,
                totalCount = 4,
                manualCount = 2
            )
        )
    }

    @Test
    fun mainTodayCountLabel_keeps_fraction_when_only_manual_records_are_present() {
        assertEquals(
            "0/0 (2)",
            mainTodayCountLabel(
                doneCount = 0,
                totalCount = 0,
                manualCount = 2
            )
        )
    }

    @Test
    fun canResetMainE2ChartMinimap_disables_when_full_range_visible() {
        assertEquals(
            false,
            canResetMainE2ChartMinimap(
                visibleRange = MainE2ChartVisibleXRange(0.0, 168.0),
                chartWindowHours = 168,
                hasPendingReset = false,
            )
        )
    }

    @Test
    fun canResetMainE2ChartMinimap_enables_when_visible_range_is_zoomed() {
        assertEquals(
            true,
            canResetMainE2ChartMinimap(
                visibleRange = MainE2ChartVisibleXRange(48.0, 96.0),
                chartWindowHours = 168,
                hasPendingReset = false,
            )
        )
    }

    @Test
    fun canResetMainE2ChartMinimap_disables_while_reset_is_pending() {
        assertEquals(
            false,
            canResetMainE2ChartMinimap(
                visibleRange = MainE2ChartVisibleXRange(48.0, 96.0),
                chartWindowHours = 168,
                hasPendingReset = true,
            )
        )
    }

    @Test
    fun resolveMainE2ChartMinimapDateLabelRange_uses_pending_reset_target() {
        assertEquals(
            MainE2ChartVisibleXRange(0.0, 168.0),
            resolveMainE2ChartMinimapDateLabelRange(
                visibleRange = MainE2ChartVisibleXRange(48.0, 96.0),
                pendingResetRange = MainE2ChartVisibleXRange(0.0, 168.0),
            )
        )
    }
}
