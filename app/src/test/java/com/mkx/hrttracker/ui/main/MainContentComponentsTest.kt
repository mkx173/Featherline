package com.mkx.hrttracker.ui.main

import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

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
            "1/4 DONE",
            mainTodayCountLabel(
                doneCount = 1,
                totalCount = 4,
                manualCount = 0,
                doneLabel = "DONE",
                manualLabel = "MANUAL"
            )
        )
    }

    @Test
    fun mainTodayCountLabel_appends_manual_count_when_present() {
        assertEquals(
            "1/4 DONE \u00b7 2 MANUAL",
            mainTodayCountLabel(
                doneCount = 1,
                totalCount = 4,
                manualCount = 2,
                doneLabel = "DONE",
                manualLabel = "MANUAL"
            )
        )
    }

    @Test
    fun mainTodayCountLabel_keeps_fraction_when_only_manual_records_are_present() {
        assertEquals(
            "2 MANUAL",
            mainTodayCountLabel(
                doneCount = 0,
                totalCount = 0,
                manualCount = 2,
                doneLabel = "DONE",
                manualLabel = "MANUAL"
            )
        )
    }

    @Test
    fun mainTodayCountLabel_uses_supplied_localized_labels() {
        assertEquals(
            "1/4 LOCAL_DONE \u00b7 2 LOCAL_MANUAL",
            mainTodayCountLabel(
                doneCount = 1,
                totalCount = 4,
                manualCount = 2,
                doneLabel = "LOCAL_DONE",
                manualLabel = "LOCAL_MANUAL"
            )
        )
    }

    @Test
    fun mainTodayCountLabel_has_resource_labels() {
        assertTrue(R.string.main_today_summary_done_label != 0)
        assertTrue(R.string.main_today_summary_manual_label != 0)
    }

    @Test
    fun mainTodayCountLabel_omits_empty_count() {
        assertNull(
            mainTodayCountLabel(
                doneCount = 0,
                totalCount = 0,
                manualCount = 0,
                doneLabel = "DONE",
                manualLabel = "MANUAL"
            )
        )
    }

    @Test
    fun mainTodayCompactCountLabel_uses_short_form_for_time_range_headers() {
        assertEquals(
            "1/4",
            mainTodayCompactCountLabel(
                doneCount = 1,
                totalCount = 4,
                manualCount = 0
            )
        )
        assertEquals(
            "(2)",
            mainTodayCompactCountLabel(
                doneCount = 0,
                totalCount = 0,
                manualCount = 2
            )
        )
        assertEquals(
            "1/4 (2)",
            mainTodayCompactCountLabel(
                doneCount = 1,
                totalCount = 4,
                manualCount = 2
            )
        )
        assertNull(
            mainTodayCompactCountLabel(
                doneCount = 0,
                totalCount = 0,
                manualCount = 0
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

    @Test
    fun mainE2ChartDisplayDateTimeForXHours_mapsWindowEndToLastVisibleMinute() {
        val chartWindowStart = LocalDateTime.of(2026, 5, 11, 0, 0)

        assertEquals(
            LocalDateTime.of(2026, 5, 11, 0, 0),
            mainE2ChartDisplayDateTimeForXHours(
                chartWindowStart = chartWindowStart,
                xHours = 0.0,
                chartWindowHours = 168,
            )
        )
        assertEquals(
            LocalDateTime.of(2026, 5, 14, 0, 0),
            mainE2ChartDisplayDateTimeForXHours(
                chartWindowStart = chartWindowStart,
                xHours = 72.0,
                chartWindowHours = 168,
            )
        )
        assertEquals(
            LocalDateTime.of(2026, 5, 17, 23, 59),
            mainE2ChartDisplayDateTimeForXHours(
                chartWindowStart = chartWindowStart,
                xHours = 168.0,
                chartWindowHours = 168,
            )
        )
    }

    @Test
    fun mainE2ChartZoomFloorHours_landsAtThreeDaysOnPhoneWidth() {
        // THIRTY_DAYS spans 40 d = 960 h with 2240 budget segments. At the
        // 400 px phone-portrait reference width and the 0.42 density factor,
        // the floor is 0.42 * (960 / 2240) * 400 = 72 h = 3 d.
        val floor = mainE2ChartZoomFloorHours(
            projectionSpanHours = 960.0,
            segmentCount = 2240,
            chartPixelWidthPx = 400,
        )
        assertEquals(72.0, floor, 1e-9)
    }

    @Test
    fun mainE2ChartZoomFloorHours_scalesWithMeasuredWidth() {
        // Tablets get a wider floor (~6 d) at the same samples-per-pixel
        // target. Locking to the 400 px fallback would let tablets zoom past
        // the linearisation slack the design accepts.
        val floor = mainE2ChartZoomFloorHours(
            projectionSpanHours = 960.0,
            segmentCount = 2240,
            chartPixelWidthPx = 800,
        )
        assertEquals(144.0, floor, 1e-9)
    }

    @Test
    fun mainE2ChartZoomFloorHours_fallsBackToReferenceWidthBeforeLayout() {
        // Compose has not reported a size yet; the formula uses the 400 px
        // fallback so the chart starts at a sensible max-zoom before the
        // first .onSizeChanged.
        val floor = mainE2ChartZoomFloorHours(
            projectionSpanHours = 960.0,
            segmentCount = 2240,
            chartPixelWidthPx = 0,
        )
        assertEquals(72.0, floor, 1e-9)
    }

    @Test
    fun mainE2ChartMaxZoomXRangeHours_keepsSevenDayFloorAt48Hours() {
        // 7-day mode is the pre-feature behaviour. Width is irrelevant.
        for (width in intArrayOf(0, 400, 800)) {
            assertEquals(
                48.0,
                mainE2ChartMaxZoomXRangeHours(
                    option = HomeE2ChartWindowOption.SEVEN_DAYS,
                    chartPixelWidthPx = width,
                ),
                1e-9,
            )
        }
    }

    @Test
    fun mainE2ChartMaxZoomXRangeHours_thirtyDayUsesMeasuredWidthFormula() {
        assertEquals(
            72.0,
            mainE2ChartMaxZoomXRangeHours(
                option = HomeE2ChartWindowOption.THIRTY_DAYS,
                chartPixelWidthPx = 400,
            ),
            1e-9,
        )
        assertEquals(
            144.0,
            mainE2ChartMaxZoomXRangeHours(
                option = HomeE2ChartWindowOption.THIRTY_DAYS,
                chartPixelWidthPx = 800,
            ),
            1e-9,
        )
    }
}
