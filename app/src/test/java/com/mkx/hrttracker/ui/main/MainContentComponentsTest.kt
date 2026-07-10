package com.mkx.hrttracker.ui.main

import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.testCustomMedicine
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MainContentComponentsTest {
    @Test
    fun mainTodayRowIndicatorSizes_useFixedSlotWithSmallerDownloadGlyph() {
        assertEquals(18.dp, MainTodayRowIndicatorSlotSize)
        assertEquals(16.dp, mainTodayRowIndicatorGlyphSize(R.drawable.ic_download))
        assertEquals(17.dp, mainTodayRowIndicatorGlyphSize(R.drawable.ic_edit_square))
        assertEquals(18.dp, mainTodayRowIndicatorGlyphSize(R.drawable.ic_archive))
    }

    @Test
    fun mainTodayDoseRowIndicatorIconRes_usesDownloadForImportedRows() {
        val row = scheduledTodayRow(
            groupUuid = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            scheduleTimeUuid = null,
            scheduledAt = LocalDateTime.of(2026, 5, 20, 9, 0),
            medicationUuid = UUID.fromString("22222222-2222-2222-2222-222222222222"),
        ).copy(
            isManualRecord = true,
            isImportedRecord = true,
        )

        assertEquals(R.drawable.ic_download, mainTodayDoseRowIndicatorIconRes(row))
    }

    @Test
    fun mainTodayDoseRowIndicatorIconRes_keepsEditForLocalManualRows() {
        val row = scheduledTodayRow(
            groupUuid = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            scheduleTimeUuid = null,
            scheduledAt = LocalDateTime.of(2026, 5, 20, 9, 0),
            medicationUuid = UUID.fromString("22222222-2222-2222-2222-222222222222"),
        ).copy(isManualRecord = true)

        assertEquals(R.drawable.ic_edit_square, mainTodayDoseRowIndicatorIconRes(row))
    }

    @Test
    fun mainTodayDoseRowIndicatorIconRes_omitsIconForScheduledRows() {
        val row = scheduledTodayRow(
            groupUuid = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            scheduleTimeUuid = null,
            scheduledAt = LocalDateTime.of(2026, 5, 20, 9, 0),
            medicationUuid = UUID.fromString("22222222-2222-2222-2222-222222222222"),
        )

        assertNull(mainTodayDoseRowIndicatorIconRes(row))
    }

    @Test
    fun runDoseRowHighlightPulse_risesToPeakThenFallsToZeroWithoutPlateau() = runTest {
        // The pulse must be a single rise-then-fall sweep ending at fully
        // transparent: a peak leg up to DoseRowHighlightPeakAlpha followed by a
        // fall leg back to 0, with no hold in between. A plateau (or a final
        // target above 0) would reintroduce the fade-in/hold/fade-out blink the
        // pulse replaces.
        val targets = mutableListOf<Float>()
        runDoseRowHighlightPulse { target, _ -> targets += target }

        assertEquals(
            listOf(DoseRowHighlightPeakFraction, 0f),
            targets,
        )
    }

    @Test
    fun mainDoseRowHighlightScrollTargetKey_usesFirstMatchingTodayRowInRenderedOrder() {
        val morningGroupUuid = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val eveningGroupUuid = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val morningSlotUuid = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val eveningSlotUuid = UUID.fromString("44444444-4444-4444-4444-444444444444")
        val morningMedicationUuid = UUID.fromString("55555555-5555-5555-5555-555555555555")
        val eveningMedicationUuid = UUID.fromString("66666666-6666-6666-6666-666666666666")
        val morningAt = LocalDateTime.of(2026, 5, 20, 8, 0)
        val eveningAt = LocalDateTime.of(2026, 5, 20, 21, 0)
        val morningRow = scheduledTodayRow(
            groupUuid = morningGroupUuid,
            scheduleTimeUuid = morningSlotUuid,
            scheduledAt = morningAt,
            medicationUuid = morningMedicationUuid,
        )
        val eveningRow = scheduledTodayRow(
            groupUuid = eveningGroupUuid,
            scheduleTimeUuid = eveningSlotUuid,
            scheduledAt = eveningAt,
            medicationUuid = eveningMedicationUuid,
        )
        val request = DoseRowHighlightRequest(
            listOf(
                DoseRowHighlightKey.Scheduled(
                    groupUuid = morningGroupUuid,
                    scheduleTimeUuid = morningSlotUuid,
                    scheduledAt = morningAt,
                    medicationUuid = morningMedicationUuid,
                ),
                DoseRowHighlightKey.Scheduled(
                    groupUuid = eveningGroupUuid,
                    scheduleTimeUuid = eveningSlotUuid,
                    scheduledAt = eveningAt,
                    medicationUuid = eveningMedicationUuid,
                ),
            )
        )
        // Rows are stored evening-first, but they render grouped by time range
        // (morning before evening), so the morning row is rendered first.
        val uiState = MainUiState(
            now = morningAt,
            todaySection = MainTodaySectionUiState(
                date = morningAt.toLocalDate(),
                rows = listOf(eveningRow, morningRow),
            ),
        )

        // The first match in rendered order is the scroll target so it lands
        // closest to the viewport center (the later evening match sits below it).
        assertEquals(
            mainTodayDoseRowCompositionKey(morningRow),
            mainDoseRowHighlightScrollTargetKey(uiState, request),
        )
    }

    @Test
    fun mainDoseRowHighlightScrollTargetKey_prefersComingUpRowBeforeUpcomingPreview() {
        val groupUuid = UUID.fromString("77777777-7777-7777-7777-777777777777")
        val slotUuid = UUID.fromString("88888888-8888-8888-8888-888888888888")
        val medicationUuid = UUID.fromString("99999999-9999-9999-9999-999999999999")
        val scheduledAt = LocalDateTime.of(2026, 5, 21, 0, 30)
        val comingUpRow = scheduledTodayRow(
            groupUuid = groupUuid,
            scheduleTimeUuid = slotUuid,
            scheduledAt = scheduledAt,
            medicationUuid = medicationUuid,
        )
        val upcomingRow = MainUpcomingDoseRowUiState(
            groupUuid = groupUuid,
            groupName = "Test",
            groupColorKey = MedicationGroupColorKey.PLUM,
            scheduleTimeUuid = slotUuid,
            scheduledAt = scheduledAt,
            medication = comingUpRow.medication,
        )
        val request = DoseRowHighlightRequest(
            listOf(
                DoseRowHighlightKey.Scheduled(
                    groupUuid = groupUuid,
                    scheduleTimeUuid = slotUuid,
                    scheduledAt = scheduledAt,
                    medicationUuid = medicationUuid,
                )
            )
        )
        val uiState = MainUiState(
            now = LocalDateTime.of(2026, 5, 20, 23, 55),
            comingUpSection = MainComingUpSectionUiState(
                date = scheduledAt.toLocalDate(),
                rows = listOf(comingUpRow),
            ),
            upcomingSection = MainUpcomingSectionUiState(
                rows = listOf(upcomingRow),
            ),
        )

        assertEquals(
            mainTodayDoseRowCompositionKey(comingUpRow),
            mainDoseRowHighlightScrollTargetKey(uiState, request),
        )
    }

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
    fun mainAntiandrogenInfoPills_addsManualRecordChipAfterLastDoseForManualRows() {
        assertEquals(
            listOf(
                MainAntiandrogenInfoPillSpec(
                    iconDrawableRes = R.drawable.ic_check_circle_heavy,
                    text = "3h ago",
                ),
                MainAntiandrogenInfoPillSpec(
                    iconDrawableRes = R.drawable.ic_edit_square_heavy,
                    text = "Manual record",
                ),
            ),
            mainAntiandrogenInfoPillSpecs(
                hasPreviousRecord = true,
                takenText = "3h ago",
                dueText = "Tomorrow",
                isManualRow = true,
                manualRecordText = "Manual record",
            )
        )
    }

    @Test
    fun mainAntiandrogenInfoPills_keepsNextDoseChipForScheduledRows() {
        assertEquals(
            listOf(
                MainAntiandrogenInfoPillSpec(
                    iconDrawableRes = R.drawable.ic_info_heavy,
                    text = "No records",
                ),
                MainAntiandrogenInfoPillSpec(
                    iconDrawableRes = R.drawable.ic_schedule_heavy,
                    text = "Tomorrow",
                ),
            ),
            mainAntiandrogenInfoPillSpecs(
                hasPreviousRecord = false,
                takenText = "No records",
                dueText = "Tomorrow",
                isManualRow = false,
                manualRecordText = "Manual record",
            )
        )
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

    @Test
    fun buildMainE2ChartModel_attachesViewSpecToModelExtraStore() {
        // The bottom-axis formatter, x-range provider, item placers, and the
        // current-time decoration all read the view spec from the model's
        // ExtraStore so their output always matches the series being drawn.
        // If the synchronous build path dropped the spec, axis labels would
        // silently render blank and the x-range would fall back to the raw
        // data max instead of the chart window.
        val spec = testMainE2ChartViewSpec()
        val model = buildMainE2ChartModel(
            splitChartSeries = testSplitChartSeries(),
            currentTimeXHours = 1.0,
            currentTimeConcentration = 20f,
            doseMarkerLoggedXHours = emptyList(),
            doseMarkerLoggedConcentrations = emptyList(),
            doseMarkerPlannedXHours = emptyList(),
            doseMarkerPlannedConcentrations = emptyList(),
            viewSpec = spec,
        )

        assertEquals(spec, model.extraStore.getOrNull(MainE2ChartViewSpecKey))
    }

    @Test
    fun buildMainE2ChartModel_emitsOnlyLineSlotsWhenNoMarkersExist() {
        // Vico rejects empty series, so the marker slots must be omitted
        // entirely — not emitted empty — when there are no markers at all.
        // Slots 0-2 (observed line, predicted line, "you are here" dot) are
        // unconditional so the LineProvider's index-based styling stays
        // aligned.
        val split = testSplitChartSeries()
        val model = buildMainE2ChartModel(
            splitChartSeries = split,
            currentTimeXHours = 1.0,
            currentTimeConcentration = 20f,
            doseMarkerLoggedXHours = emptyList(),
            doseMarkerLoggedConcentrations = emptyList(),
            doseMarkerPlannedXHours = emptyList(),
            doseMarkerPlannedConcentrations = emptyList(),
            viewSpec = testMainE2ChartViewSpec(),
        )

        val layer = model.models.single() as LineCartesianLayerModel
        assertEquals(3, layer.series.size)
        assertEquals(split.observedXHours, layer.series[0].map { it.x })
        assertEquals(split.predictedXHours, layer.series[1].map { it.x })
        assertEquals(listOf(1.0), layer.series[2].map { it.x })
    }

    @Test
    fun buildMainE2ChartModel_padsEmptyMarkerBucketWithOffAxisSentinel() {
        // Logged (slot 3) and planned (slot 4) markers get different point
        // styles via the LineProvider's index-based mapping. When only one
        // bucket has data, the other must still occupy its slot — padded with
        // an off-axis sentinel point — or the present bucket would silently
        // shift into the wrong slot and render with the wrong style.
        val model = buildMainE2ChartModel(
            splitChartSeries = testSplitChartSeries(),
            currentTimeXHours = 1.0,
            currentTimeConcentration = 20f,
            doseMarkerLoggedXHours = listOf(0.5),
            doseMarkerLoggedConcentrations = listOf(15f),
            doseMarkerPlannedXHours = emptyList(),
            doseMarkerPlannedConcentrations = emptyList(),
            viewSpec = testMainE2ChartViewSpec(),
        )

        val layer = model.models.single() as LineCartesianLayerModel
        assertEquals(5, layer.series.size)
        assertEquals(listOf(0.5), layer.series[3].map { it.x })
        val plannedSentinel = layer.series[4].single()
        assertEquals(-1.0, plannedSentinel.x, 0.0)
        assertEquals(-1_000_000.0, plannedSentinel.y, 0.0)
    }

    private fun testSplitChartSeries(): MainE2SplitChartSeries = MainE2SplitChartSeries(
        observedXHours = listOf(0.0, 1.0),
        observedPoints = listOf(10f, 20f),
        predictedXHours = listOf(1.0, 2.0),
        predictedPoints = listOf(20f, 30f),
    )

    private fun testMainE2ChartViewSpec(): MainE2ChartViewSpec = MainE2ChartViewSpec(
        chartWindowOption = HomeE2ChartWindowOption.SEVEN_DAYS,
        chartWindowStart = LocalDateTime.of(2026, 6, 4, 0, 0),
        chartWindowHours = 168,
        currentTimeXHours = 120.0,
        pastDays = 6L,
        now = LocalDateTime.of(2026, 6, 10, 12, 0),
    )

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
            medicine = testCustomMedicine(medicationName = "Estradiol"),
        ),
        status = MainTodayDoseStatus.DUE_SOON,
    )
}
