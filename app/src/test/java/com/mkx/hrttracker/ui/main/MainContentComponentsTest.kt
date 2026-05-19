package com.mkx.hrttracker.ui.main

import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.testCustomMedicationDetails
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID

class MainContentComponentsTest {
    @Test
    fun mainTodayDoseRowCompositionKey_distinguishesScheduledSlotsForSameGroup() {
        val groupUuid = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val medicationUuid = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val firstSlotUuid = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val secondSlotUuid = UUID.fromString("44444444-4444-4444-4444-444444444444")

        val firstSlotKey = mainTodayDoseRowCompositionKey(
            scheduledTodayRow(
                groupUuid = groupUuid,
                scheduleTimeUuid = firstSlotUuid,
                scheduledAt = LocalDateTime.of(2026, 5, 20, 8, 0),
                medicationUuid = medicationUuid,
            )
        )
        val secondSlotKey = mainTodayDoseRowCompositionKey(
            scheduledTodayRow(
                groupUuid = groupUuid,
                scheduleTimeUuid = secondSlotUuid,
                scheduledAt = LocalDateTime.of(2026, 5, 20, 21, 0),
                medicationUuid = medicationUuid,
            )
        )

        assertNotEquals(firstSlotKey, secondSlotKey)
    }

    @Test
    fun mainTodayDoseRowCompositionKey_distinguishesMedicationRowsInsideSameSlot() {
        val groupUuid = UUID.fromString("55555555-5555-5555-5555-555555555555")
        val slotUuid = UUID.fromString("66666666-6666-6666-6666-666666666666")
        val scheduledAt = LocalDateTime.of(2026, 5, 20, 8, 0)

        val firstMedicationKey = mainTodayDoseRowCompositionKey(
            scheduledTodayRow(
                groupUuid = groupUuid,
                scheduleTimeUuid = slotUuid,
                scheduledAt = scheduledAt,
                medicationUuid = UUID.fromString("77777777-7777-7777-7777-777777777777"),
            )
        )
        val secondMedicationKey = mainTodayDoseRowCompositionKey(
            scheduledTodayRow(
                groupUuid = groupUuid,
                scheduleTimeUuid = slotUuid,
                scheduledAt = scheduledAt,
                medicationUuid = UUID.fromString("88888888-8888-8888-8888-888888888888"),
            )
        )

        assertNotEquals(firstMedicationKey, secondMedicationKey)
    }

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
    fun mainE2ChartZoomFloorHours_floorsToWholeDay() {
        // 1160 px raw = 0.42 * (960/2240) * 1160 = 208.8 h; flooring to a
        // whole-day boundary keeps the max-zoom edge aligned with the
        // chart's day ticks. floor(208.8 / 24) * 24 = 192 h = 8 d.
        val floor = mainE2ChartZoomFloorHours(
            projectionSpanHours = 960.0,
            segmentCount = 2240,
            chartPixelWidthPx = 1160,
        )
        assertEquals(192.0, floor, 1e-9)
    }

    @Test
    fun mainE2ChartZoomFloorHours_minimumIsOneDay() {
        // A very narrow chart would compute a raw floor below 24 h; the
        // clamp guarantees the max-zoom span never goes below one full day.
        val floor = mainE2ChartZoomFloorHours(
            projectionSpanHours = 960.0,
            segmentCount = 2240,
            chartPixelWidthPx = 100,
        )
        assertEquals(24.0, floor, 1e-9)
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

    private fun scheduledTodayRow(
        groupUuid: UUID,
        scheduleTimeUuid: UUID?,
        scheduledAt: LocalDateTime,
        medicationUuid: UUID,
    ): MainTodayDoseRowUiState = MainTodayDoseRowUiState(
        groupUuid = groupUuid,
        groupName = "Test",
        groupColorKey = null,
        scheduleTimeUuid = scheduleTimeUuid,
        scheduledAt = scheduledAt,
        medication = testMedicationGroupMedication(
            uuid = medicationUuid,
            details = testCustomMedicationDetails(
                medicationName = "Estradiol",
                dose = MedicationDose.MgAsMedicine(2.0),
            ),
        ),
        status = MainTodayDoseStatus.DUE_SOON,
    )
}
