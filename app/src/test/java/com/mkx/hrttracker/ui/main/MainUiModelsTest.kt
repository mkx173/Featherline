package com.mkx.hrttracker.ui.main

import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.pk.PkConcentrationUnit
import com.mkx.hrttracker.model.pk.PkDoseMarker
import com.mkx.hrttracker.model.pk.PkTrendResult
import com.mkx.hrttracker.util.formatMainE2ConcentrationValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class MainUiModelsTest {
    private val testZoneId: ZoneId = ZoneId.systemDefault()

    // Shared estradiol medicine with a fixed UUID. Slot fulfillment matches a
    // logged entry to a planned slot by MedicationSignature (which keys on
    // medicineUuid), so the default group medication and the entries used to
    // fulfil it must reference the same medicine instance.
    private val defaultEstradiolMedicine = testMedicine(
        uuid = UUID.fromString("aaaa0000-0000-0000-0000-000000000001"),
        key = MedicationKey.ESTRADIOL,
    )

    @Test
    fun buildMainE2Hero_uses_pk_trend_and_latest_actual_estradiol_dose() {
        val latestEstradiolDoseTime = LocalDateTime.of(2026, 4, 18, 20, 5)
        val latestEstradiolMedicine = testMedicine(
            key = MedicationKey.ESTRADIOL_VALERATE,
            preparation = MedicinePreparation.InjectionMultiUseVial(
                concentrationMgPerMl = 10.0,
                vialVolumeMl = 5.0,
            ),
        )
        val latestEstradiolDoseInstruction = DoseInstruction.VolumeMl(0.5)
        val trendResult = PkTrendResult(
            currentConcentration = 160.4,
            previousDayConcentration = 151.1,
            dailyConcentrations = listOf(120.0, 130.0, 140.0, 150.0, 151.1, 155.0, 160.4),
            concentrationUnit = PkConcentrationUnit.PG_PER_ML,
        )

        val hero = buildMainE2Hero(
            entries = listOf(
                testMedicationLogEntry(
                    uuid = UUID.randomUUID(),
                    medicine = testMedicine(key = MedicationKey.SPIRONOLACTONE),
                    applicationType = MedicationApplicationType.ORAL,
                    equivalentE2Mg = null,
                    sourceGroupUuid = null,
                    appliedAt = testInstant(LocalDateTime.of(2026, 4, 18, 8, 0))
                ),
                testMedicationLogEntry(
                    uuid = UUID.randomUUID(),
                    medicine = latestEstradiolMedicine,
                    applicationType = MedicationApplicationType.INJECTION,
                    doseInstruction = latestEstradiolDoseInstruction,
                    equivalentE2Mg = 3.82,
                    sourceGroupUuid = UUID.randomUUID(),
                    appliedAt = testInstant(latestEstradiolDoseTime)
                )
            ),
            trendResult = trendResult,
            zoneId = testZoneId
        )

        assertEquals(160.4, hero.currentValue, 1e-9)
        assertEquals(9.3, hero.changeSinceYesterday, 1e-9)
        assertEquals(100.0, hero.targetMin, 1e-9)
        assertEquals(200.0, hero.targetMax, 1e-9)
        assertEquals("pg/mL", hero.unit)
        assertEquals(latestEstradiolDoseTime, hero.lastDoseAt)
        assertEquals(latestEstradiolMedicine, hero.lastDose?.medicine)
        assertEquals(MedicationApplicationType.INJECTION, hero.lastDose?.applicationType)
        assertEquals(latestEstradiolDoseInstruction, hero.lastDose?.doseInstruction)
    }

    @Test
    fun buildMainE2Hero_ignores_future_dated_dose_when_now_is_provided() {
        // A pre-logged dose stamped after `now` has not actually been taken yet,
        // so it must not be reported as the last dose. Otherwise the elapsed
        // label clamps the negative duration to "0 min ago" (see the Room path
        // which surfaces future-but-today entries), diverging from the snapshot
        // path which renders "no log yet".
        val now = LocalDateTime.of(2026, 5, 31, 12, 0)
        val futureDoseTime = now.plusHours(3)

        val hero = buildMainE2Hero(
            entries = listOf(
                testMedicationLogEntry(
                    uuid = UUID.randomUUID(),
                    medicine = defaultEstradiolMedicine,
                    sourceGroupUuid = null,
                    appliedAt = testInstant(futureDoseTime),
                )
            ),
            zoneId = testZoneId,
            now = now,
        )

        assertNull(hero.lastDoseAt)
        assertNull(hero.lastDose)
    }

    @Test
    fun buildMainE2Hero_keeps_past_dose_when_now_is_provided() {
        val now = LocalDateTime.of(2026, 5, 31, 12, 0)
        val pastDoseTime = now.minusHours(2)

        val hero = buildMainE2Hero(
            entries = listOf(
                testMedicationLogEntry(
                    uuid = UUID.randomUUID(),
                    medicine = defaultEstradiolMedicine,
                    sourceGroupUuid = null,
                    appliedAt = testInstant(pastDoseTime),
                )
            ),
            zoneId = testZoneId,
            now = now,
        )

        assertEquals(pastDoseTime, hero.lastDoseAt)
    }

    @Test
    fun buildMainE2Hero_includes_dose_logged_within_current_displayed_minute() {
        // The home clock is minute-granular: a dose logged at 12:00:05 while the
        // UI still says 12:00 must count as the last dose, not be hidden as
        // "future" until the next tick.
        val now = LocalDateTime.of(2026, 5, 31, 12, 0)
        val doseTime = now.plusSeconds(5)

        val hero = buildMainE2Hero(
            entries = listOf(
                testMedicationLogEntry(
                    uuid = UUID.randomUUID(),
                    medicine = defaultEstradiolMedicine,
                    sourceGroupUuid = null,
                    appliedAt = testInstant(doseTime),
                )
            ),
            zoneId = testZoneId,
            now = now,
        )

        assertEquals(doseTime, hero.lastDoseAt)
    }

    @Test
    fun buildMainE2Chart_converts_pk_chart_concentrations_to_display_unit() {
        val chart = buildMainE2Chart(
            trendResult = PkTrendResult(
                currentConcentration = 70.0,
                previousDayConcentration = 60.0,
                dailyConcentrations = listOf(10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0),
                chartConcentrations = listOf(10.0, 20.0, 10.0, 40.0, 15.0),
                chartSampleIntervalHours = 1,
                chartTimeH = listOf(0.0, 0.5, 1.0, 2.0, 4.0),
                doseMarkers = listOf(PkDoseMarker(timeH = 0.5, concentration = 20.0)),
                chartWindowHours = 168,
                predictionStartTimeH = 2.0,
                concentrationUnit = PkConcentrationUnit.PG_PER_ML,
            ),
            displayUnit = BloodUnitKey.NG_DL,
        )

        assertEquals(5, chart.points.size)
        assertEquals(1, chart.sampleIntervalHours)
        assertEquals(listOf(1f, 2f, 1f, 4f, 1.5f), chart.points)
        assertEquals(listOf(0.0, 0.5, 1.0, 2.0, 4.0), chart.pointXHours)
        assertEquals(
            listOf(MainE2DoseMarkerUiState(xHours = 0.5, concentration = 2f)),
            chart.doseMarkers
        )
        assertEquals(168, chart.windowHours)
        assertEquals(2.0, chart.predictionStartXHours, 1e-9)
    }

    @Test
    fun formatMainE2ConcentrationValue_rounds_by_display_unit() {
        assertEquals("160", formatMainE2ConcentrationValue(160.4, BloodUnitKey.PG_ML))
        assertEquals("589", formatMainE2ConcentrationValue(588.8, BloodUnitKey.PMOL_L))
        assertEquals("16.0", formatMainE2ConcentrationValue(16.04, BloodUnitKey.NG_DL))
        assertEquals("16.1", formatMainE2ConcentrationValue(16.06, BloodUnitKey.NG_DL))
    }

    @Test
    fun isMainE2TrendDeltaDisplayZero_uses_rounded_display_value() {
        assertEquals(true, isMainE2TrendDeltaDisplayZero(0.0, BloodUnitKey.PG_ML))
        assertEquals(true, isMainE2TrendDeltaDisplayZero(-0.4, BloodUnitKey.PG_ML))
        assertEquals(true, isMainE2TrendDeltaDisplayZero(-0.04, BloodUnitKey.NG_DL))
        assertEquals(false, isMainE2TrendDeltaDisplayZero(-0.06, BloodUnitKey.NG_DL))
    }

    @Test
    fun formatMainE2TrendDeltaValue_formats_signed_non_zero_delta() {
        assertEquals("-0.1", formatMainE2TrendDeltaValue(-0.06, BloodUnitKey.NG_DL))
        assertEquals("+1", formatMainE2TrendDeltaValue(0.6, BloodUnitKey.PG_ML))
    }

    @Test
    fun mainE2ChartNoonTickHours_centers_current_day_as_fourth_tick() {
        val ticks = mainE2ChartNoonTickHours(
            now = LocalDateTime.of(2026, 5, 5, 23, 37),
            windowHours = 168,
        )

        assertEquals(7, ticks.size)
        assertEquals(listOf(12.0, 36.0, 60.0, 84.0, 108.0, 132.0, 156.0), ticks)
        assertEquals(84.0, ticks[3], 1e-9)
    }

    @Test
    fun mainE2ChartNoonTickHours_keeps_exact_noon_window_to_seven_labels() {
        val ticks = mainE2ChartNoonTickHours(
            now = LocalDateTime.of(2026, 5, 5, 12, 0),
            windowHours = 168,
        )

        assertEquals(listOf(12.0, 36.0, 60.0, 84.0, 108.0, 132.0, 156.0), ticks)
    }

    @Test
    fun mainE2ChartAxisTickIntervalDays_ladder() {
        // ≤ 7 d (168 h) visible → daily
        assertEquals(1L, mainE2ChartAxisTickIntervalDays(48.0))
        assertEquals(1L, mainE2ChartAxisTickIntervalDays(168.0))
        // ≤ 14 d (336 h) → every 2 days
        assertEquals(2L, mainE2ChartAxisTickIntervalDays(168.0 + 1e-3))
        assertEquals(2L, mainE2ChartAxisTickIntervalDays(336.0))
        // ≤ 30 d (720 h) → every 6 days
        assertEquals(6L, mainE2ChartAxisTickIntervalDays(336.0 + 1e-3))
        assertEquals(6L, mainE2ChartAxisTickIntervalDays(720.0))
        // > 30 d → weekly
        assertEquals(7L, mainE2ChartAxisTickIntervalDays(720.0 + 1e-3))
        assertEquals(7L, mainE2ChartAxisTickIntervalDays(2400.0))
    }

    @Test
    fun mainE2ChartAxisLabelTickHours_dailyOver7DayWindow() {
        // 3 past + 4 future days from a 23:37 anchor: window is May 2–9.
        // Edge-day suppression drops May 2 (firstDay) and May 8 (lastDay),
        // leaving 5 ticks at noons of May 3–7.
        val windowStart = mainE2ChartWindowStart(LocalDateTime.of(2026, 5, 5, 23, 37))
        val ticks = mainE2ChartAxisLabelTickHours(
            chartWindowStart = windowStart,
            chartWindowHours = 168,
            visibleXRange = 0.0..168.0,
            intervalDays = 1L,
        )

        assertEquals(5, ticks.size)
        assertEquals(listOf(36.0, 60.0, 84.0, 108.0, 132.0), ticks)
    }

    @Test
    fun mainE2ChartAxisLabelTickHours_filtersToVisibleRange() {
        // Zoomed in to a 3-day window centered around day 4 of a 7-day chart:
        // only ticks whose noon falls inside [72, 144] should survive.
        val windowStart = mainE2ChartWindowStart(LocalDateTime.of(2026, 5, 5, 23, 37))
        val ticks = mainE2ChartAxisLabelTickHours(
            chartWindowStart = windowStart,
            chartWindowHours = 168,
            visibleXRange = 72.0..144.0,
            intervalDays = 1L,
        )

        assertEquals(listOf(84.0, 108.0, 132.0), ticks)
    }

    @Test
    fun mainE2ChartAxisLabelTickHours_30DayFiveDaySpacingSkipsEdgeDays() {
        // THIRTY_DAYS window (Apr 19–May 19): intervalDays=5, anchor offset=2.
        // Anchor = Apr 21; steps land on Apr 21, 26, May 1, 6, 11, 16.
        // Edge days Apr 19/May 18 not reached; all 6 ticks survive.
        val windowStart = mainE2ChartWindowStart(
            LocalDateTime.of(2026, 5, 5, 23, 37),
            pastDays = 16L,
        )
        val ticks = mainE2ChartAxisLabelTickHours(
            chartWindowStart = windowStart,
            chartWindowHours = 720,
            visibleXRange = 0.0..720.0,
            intervalDays = 5L,
        )

        assertEquals(6, ticks.size)
        assertEquals(listOf(60.0, 180.0, 300.0, 420.0, 540.0, 660.0), ticks)
    }

    @Test
    fun mainE2ChartAxisLabelTickHours_30DayDefaultUsesSixDaySpacing() {
        // THIRTY_DAYS at full zoom: visibleHours = 720 → intervalDays = 6.
        // Window Apr 19–May 19; anchor offset = 3 → anchor = Apr 22.
        // Steps land on Apr 22, 28, May 4, 10, 16 — 5 ticks from day 4 to
        // day 28, covering the range from near-start to near-end.
        val windowStart = mainE2ChartWindowStart(
            LocalDateTime.of(2026, 5, 5, 23, 37),
            pastDays = 16L,
        )
        val ticks = mainE2ChartAxisLabelTickHours(
            chartWindowStart = windowStart,
            chartWindowHours = 720,
            visibleXRange = 0.0..720.0,
            intervalDays = 6L,
        )

        assertEquals(5, ticks.size)
        assertEquals(listOf(84.0, 228.0, 372.0, 516.0, 660.0), ticks)
    }

    @Test
    fun splitMainE2ChartSeries_overlaps_observed_to_now_with_predicted_from_today_start() {
        // Observed series extends to "now" (x = 60), predicted starts from
        // "today start" (x = 36). The two ranges overlap on [36, 60].
        val series = splitMainE2ChartSeries(
            xHours = listOf(0.0, 24.0, 48.0, 72.0),
            points = listOf(10f, 20f, 30f, 40f),
            observedEndXHours = 60.0,
            predictedStartXHours = 36.0,
        )

        // Observed: window-start (0) → now (60), interpolated boundary at 60
        assertEquals(listOf(0.0, 24.0, 48.0, 60.0), series.observedXHours)
        assertEquals(listOf(10f, 20f, 30f, 35f), series.observedPoints)
        // Predicted: today-start (36) → end, interpolated boundary at 36
        assertEquals(listOf(36.0, 48.0, 72.0), series.predictedXHours)
        assertEquals(listOf(25f, 30f, 40f), series.predictedPoints)
    }

    @Test
    fun mainE2ChartYAxisSpec_uses_consistent_nice_steps() {
        assertEquals(
            MainE2ChartYAxisSpec(maxY = 80.0, tickStep = 20.0),
            mainE2ChartYAxisSpec(points = listOf(0f, 63f, 72f))
        )
        assertEquals(
            MainE2ChartYAxisSpec(maxY = 200.0, tickStep = 50.0),
            mainE2ChartYAxisSpec(points = listOf(128f, 174f))
        )
        assertEquals(
            MainE2ChartYAxisSpec(maxY = 1000.0, tickStep = 250.0),
            mainE2ChartYAxisSpec(
                points = listOf(410f),
                doseMarkers = listOf(MainE2DoseMarkerUiState(xHours = 12.0, concentration = 860f))
            )
        )
    }

    @Test
    fun mainE2ChartYAxisSpec_uses_whole_number_ticks_for_tiny_ranges() {
        val expected = MainE2ChartYAxisSpec(maxY = 2.0, tickStep = 1.0)

        assertEquals(expected, mainE2ChartYAxisSpec(points = emptyList()))
        assertEquals(expected, mainE2ChartYAxisSpec(points = listOf(0.4f)))
    }

    @Test
    fun mainE2ChartYAxisSpec_bumps_when_sample_is_flush_with_tick_boundary() {
        // Catmull-Rom interpolation overshoots between samples; a peak sitting
        // exactly on the top tick would clip in the rendered curve and minimap.
        assertEquals(
            MainE2ChartYAxisSpec(maxY = 80.0, tickStep = 20.0),
            mainE2ChartYAxisSpec(points = listOf(60f, 75f))
        )
    }

    @Test
    fun mainE2ChartYAxisSpec_prefers_larger_step_when_multiple_steps_yield_same_maxY() {
        // Peak ≈80 produces maxY=100 from both step=20 and step=25; prefer
        // step=25 so ticks read 0/25/50/75/100 instead of 0/20/40/60/80/100.
        assertEquals(
            MainE2ChartYAxisSpec(maxY = 100.0, tickStep = 25.0),
            mainE2ChartYAxisSpec(points = listOf(60f, 80f))
        )
    }

    @Test
    fun buildMainAntiandrogenCards_returns_one_card_per_antiandrogen_with_real_last_and_next_values() {
        // Shared medicine instances: the doses below are group-linked logs,
        // so each antiandrogen plan row can report the plan's own last dose.
        val spiroMedicine = testMedicine(
            uuid = UUID.fromString("aaaa0000-0000-0000-0000-000000000010"),
            key = MedicationKey.SPIRONOLACTONE,
        )
        val cyproMedicine = testMedicine(
            uuid = UUID.fromString("aaaa0000-0000-0000-0000-000000000011"),
            key = MedicationKey.CYPROTERONE_ACETATE,
        )
        val spiroActualDoseInstruction = DoseInstruction.TabletFraction(1, 2)
        val cyproDoseInstruction = DoseInstruction.TabletFraction(1, 4)
        val antiandrogenGroup = MedicationGroup(
            uuid = UUID.fromString("efea64bc-0f6d-4813-a39d-428bdce6fd6a"),
            name = "Daily antiandrogen",
            colorKey = MedicationGroupColorKey.TEAL,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("96f31e44-2059-4f89-a3f4-5477cbbce4b3"),
                    medicine = spiroMedicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = spiroActualDoseInstruction,
                ),
                testMedicationGroupMedication(
                    uuid = UUID.fromString("e1a7c6d6-6ef2-4c8b-9a56-3a4f4f1298ce"),
                    medicine = cyproMedicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = cyproDoseInstruction,
                )
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z")
        )
        val now = LocalDateTime.of(2026, 4, 18, 11, 0)
        val spiroLastDoseTime = LocalDateTime.of(2026, 4, 18, 8, 4)
        val cyproLastDoseTime = LocalDateTime.of(2026, 4, 17, 20, 2)

        val cards = buildMainAntiandrogenCards(
            groups = listOf(antiandrogenGroup),
            entries = listOf(
                testMedicationLogEntry(
                    uuid = UUID.randomUUID(),
                    medicine = spiroMedicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = spiroActualDoseInstruction,
                    equivalentE2Mg = null,
                    sourceGroupUuid = antiandrogenGroup.uuid,
                    appliedAt = testInstant(spiroLastDoseTime),
                    count = 2,
                ),
                testMedicationLogEntry(
                    uuid = UUID.randomUUID(),
                    medicine = cyproMedicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = cyproDoseInstruction,
                    equivalentE2Mg = null,
                    sourceGroupUuid = antiandrogenGroup.uuid,
                    appliedAt = testInstant(cyproLastDoseTime),
                )
            ),
            now = now,
            zoneId = testZoneId
        )

        assertEquals(2, cards.size)
        assertEquals(
            listOf(
                MedicineSelection.Catalog(MedicationKey.SPIRONOLACTONE),
                MedicineSelection.Catalog(MedicationKey.CYPROTERONE_ACETATE),
            ),
            cards.map { it.medication.medicine?.selection }
        )
        assertEquals(listOf(spiroLastDoseTime, cyproLastDoseTime), cards.map { it.lastDoseAt })
        assertEquals(
            listOf(spiroActualDoseInstruction, cyproDoseInstruction),
            cards.map { it.lastDose?.doseInstruction }
        )
        assertEquals(
            listOf(2, 1),
            cards.map { it.lastDose?.count }
        )
        assertEquals(listOf(false, false), cards.map { it.isManualRow })
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 4, 18, 8, 0),
                LocalDateTime.of(2026, 4, 18, 8, 0)
            ),
            cards.map { it.nextDoseAt }
        )
        assertEquals(listOf(true, true), cards.map { it.isNextDosePastDue })
    }

    @Test
    fun buildMainAntiandrogenCards_emits_manual_row_by_medication_name_ignoring_preparation() {
        // The slot is a 50mg CPA tablet; the user manually logged a 100mg CPA
        // tablet (a different preparation, hence a different medicine UUID, and
        // not linked to the group). The plan row stays tied to the plan, while
        // the manual log gets a trailing row matched by medication name.
        val cardMedicine = testMedicine(
            uuid = UUID.fromString("aaaa0000-0000-0000-0000-000000000020"),
            key = MedicationKey.CYPROTERONE_ACETATE,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 50.0),
        )
        val loggedMedicine = testMedicine(
            uuid = UUID.fromString("aaaa0000-0000-0000-0000-000000000021"),
            key = MedicationKey.CYPROTERONE_ACETATE,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 100.0),
        )
        val doseInstruction = DoseInstruction.TabletFraction(1, 4)
        val group = MedicationGroup(
            uuid = UUID.fromString("c3b6f0a1-1111-4aaa-9999-000000000001"),
            name = "CPA",
            colorKey = MedicationGroupColorKey.TEAL,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(20, 0))
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("96f31e44-2059-4f89-a3f4-5477cbbce4b3"),
                    medicine = cardMedicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = doseInstruction,
                )
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z")
        )
        val lastDoseTime = LocalDateTime.of(2026, 4, 18, 20, 3)

        val cards = buildMainAntiandrogenCards(
            groups = listOf(group),
            entries = listOf(
                testMedicationLogEntry(
                    uuid = UUID.randomUUID(),
                    medicine = loggedMedicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = doseInstruction,
                    sourceGroupUuid = null,
                    appliedAt = testInstant(lastDoseTime),
                )
            ),
            now = LocalDateTime.of(2026, 4, 18, 21, 0),
            zoneId = testZoneId
        )

        assertEquals(2, cards.size)
        val planRow = cards[0]
        val manualRow = cards[1]
        assertEquals(cardMedicine, planRow.medication.medicine)
        assertNull(planRow.lastDoseAt)
        assertNull(planRow.lastDose)
        assertEquals(false, planRow.isManualRow)
        assertEquals(LocalDateTime.of(2026, 4, 18, 20, 0), planRow.nextDoseAt)
        assertEquals(true, planRow.isNextDosePastDue)

        assertEquals(
            "${group.uuid}:C|${MedicationKey.CYPROTERONE_ACETATE.name}:manual",
            manualRow.id
        )
        assertEquals(cardMedicine, manualRow.medication.medicine)
        assertEquals(lastDoseTime, manualRow.lastDoseAt)
        assertEquals(loggedMedicine, manualRow.lastDose?.medicine)
        assertEquals(true, manualRow.isManualRow)
        assertNull(manualRow.nextDoseAt)
        assertEquals(false, manualRow.isNextDosePastDue)
    }

    @Test
    fun buildMainAntiandrogenCards_routes_group_linked_last_dose_to_card_matching_its_dose_type() {
        // One group holds the same antiandrogen at two dose types (1/4 and 1/3
        // of a tablet), producing two cards. Group-linked logs are matched by
        // their full signature, so each card's last dose reflects the latest log
        // of *its* dose type, not whichever log is newest overall.
        val sharedMedicine = testMedicine(
            uuid = UUID.fromString("aaaa0000-0000-0000-0000-000000000030"),
            key = MedicationKey.CYPROTERONE_ACETATE,
        )
        val quarterDose = DoseInstruction.TabletFraction(1, 4)
        val thirdDose = DoseInstruction.TabletFraction(1, 3)
        val group = MedicationGroup(
            uuid = UUID.fromString("c3b6f0a1-2222-4aaa-9999-000000000002"),
            name = "CPA",
            colorKey = MedicationGroupColorKey.TEAL,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(20, 0))
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("96f31e44-2059-4f89-a3f4-5477cbbce4b3"),
                    medicine = sharedMedicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = quarterDose,
                ),
                testMedicationGroupMedication(
                    uuid = UUID.fromString("e1a7c6d6-6ef2-4c8b-9a56-3a4f4f1298ce"),
                    medicine = sharedMedicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = thirdDose,
                )
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z")
        )
        val quarterDoseTime = LocalDateTime.of(2026, 4, 18, 8, 0)
        val thirdDoseTime = LocalDateTime.of(2026, 4, 18, 9, 0)

        val cards = buildMainAntiandrogenCards(
            groups = listOf(group),
            entries = listOf(
                testMedicationLogEntry(
                    uuid = UUID.randomUUID(),
                    medicine = sharedMedicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = quarterDose,
                    sourceGroupUuid = group.uuid,
                    appliedAt = testInstant(quarterDoseTime),
                ),
                testMedicationLogEntry(
                    uuid = UUID.randomUUID(),
                    medicine = sharedMedicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = thirdDose,
                    sourceGroupUuid = group.uuid,
                    appliedAt = testInstant(thirdDoseTime),
                )
            ),
            now = LocalDateTime.of(2026, 4, 18, 11, 0),
            zoneId = testZoneId
        )

        assertEquals(2, cards.size)
        assertEquals(
            listOf(quarterDose, thirdDose),
            cards.map { it.medication.doseInstruction }
        )
        assertEquals(
            listOf(quarterDoseTime, thirdDoseTime),
            cards.map { it.lastDoseAt }
        )
        assertEquals(
            listOf(quarterDose, thirdDose),
            cards.map { it.lastDose?.doseInstruction }
        )
        assertEquals(listOf(false, false), cards.map { it.isManualRow })
    }

    @Test
    fun buildMainAntiandrogenCards_manual_dose_adds_one_row_after_all_plan_slots_for_that_medication() {
        // Same two-card setup (CPA at 1/4 and 1/3), but the latest dose is a
        // single manual log of a third preparation (100mg) at yet another dose
        // type. Manual logs match by medication name alone, but they render as
        // one trailing row instead of replacing either plan row.
        val cardMedicine = testMedicine(
            uuid = UUID.fromString("aaaa0000-0000-0000-0000-000000000040"),
            key = MedicationKey.CYPROTERONE_ACETATE,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 50.0),
        )
        val manualMedicine = testMedicine(
            uuid = UUID.fromString("aaaa0000-0000-0000-0000-000000000041"),
            key = MedicationKey.CYPROTERONE_ACETATE,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 100.0),
        )
        val quarterDose = DoseInstruction.TabletFraction(1, 4)
        val thirdDose = DoseInstruction.TabletFraction(1, 3)
        val group = MedicationGroup(
            uuid = UUID.fromString("c3b6f0a1-3333-4aaa-9999-000000000003"),
            name = "CPA",
            colorKey = MedicationGroupColorKey.TEAL,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(20, 0))
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("96f31e44-2059-4f89-a3f4-5477cbbce4b3"),
                    medicine = cardMedicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = quarterDose,
                ),
                testMedicationGroupMedication(
                    uuid = UUID.fromString("e1a7c6d6-6ef2-4c8b-9a56-3a4f4f1298ce"),
                    medicine = cardMedicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = thirdDose,
                )
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z")
        )
        val manualDoseTime = LocalDateTime.of(2026, 4, 18, 10, 30)

        val cards = buildMainAntiandrogenCards(
            groups = listOf(group),
            entries = listOf(
                testMedicationLogEntry(
                    uuid = UUID.randomUUID(),
                    medicine = manualMedicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = thirdDose,
                    sourceGroupUuid = null,
                    appliedAt = testInstant(manualDoseTime),
                )
            ),
            now = LocalDateTime.of(2026, 4, 18, 11, 0),
            zoneId = testZoneId
        )

        assertEquals(3, cards.size)
        assertEquals(
            listOf(quarterDose, thirdDose, quarterDose),
            cards.map { it.medication.doseInstruction }
        )
        assertEquals(
            listOf(null, null, manualDoseTime),
            cards.map { it.lastDoseAt }
        )
        assertEquals(
            listOf(null, null, manualMedicine),
            cards.map { it.lastDose?.medicine }
        )
        assertEquals(listOf(false, false, true), cards.map { it.isManualRow })
        assertEquals(listOf(null), cards.filter { it.isManualRow }.map { it.nextDoseAt })
    }

    @Test
    fun buildMainAntiandrogenCards_last_dose_excludes_future_logs() {
        // A dose pre-logged for later today has not been taken yet, so the card's
        // manual row must reflect the most recent manual dose at or before `now`,
        // not the future one. The plan row remains empty because manual logs do
        // not fill plan last-dose state.
        val medicine = testMedicine(
            uuid = UUID.fromString("aaaa0000-0000-0000-0000-000000000050"),
            key = MedicationKey.CYPROTERONE_ACETATE,
        )
        val doseInstruction = DoseInstruction.TabletFraction(1, 4)
        val group = MedicationGroup(
            uuid = UUID.fromString("c3b6f0a1-4444-4aaa-9999-000000000004"),
            name = "CPA",
            colorKey = MedicationGroupColorKey.TEAL,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(20, 0))
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("96f31e44-2059-4f89-a3f4-5477cbbce4b3"),
                    medicine = medicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = doseInstruction,
                )
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z")
        )
        val now = LocalDateTime.of(2026, 4, 18, 11, 0)
        val pastDoseTime = LocalDateTime.of(2026, 4, 18, 8, 0)
        val futureDoseTime = LocalDateTime.of(2026, 4, 18, 20, 0)

        val cards = buildMainAntiandrogenCards(
            groups = listOf(group),
            entries = listOf(
                testMedicationLogEntry(
                    uuid = UUID.randomUUID(),
                    medicine = medicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = doseInstruction,
                    sourceGroupUuid = null,
                    appliedAt = testInstant(pastDoseTime),
                ),
                testMedicationLogEntry(
                    uuid = UUID.randomUUID(),
                    medicine = medicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = doseInstruction,
                    sourceGroupUuid = null,
                    appliedAt = testInstant(futureDoseTime),
                )
            ),
            now = now,
            zoneId = testZoneId
        )

        assertEquals(2, cards.size)
        assertNull(cards[0].lastDoseAt)
        assertEquals(false, cards[0].isManualRow)
        assertEquals(pastDoseTime, cards[1].lastDoseAt)
        assertEquals(true, cards[1].isManualRow)
    }

    @Test
    fun buildMainAntiandrogenCards_suppresses_manual_row_when_group_linked_dose_is_newer() {
        val medicine = testMedicine(
            uuid = UUID.fromString("aaaa0000-0000-0000-0000-000000000060"),
            key = MedicationKey.CYPROTERONE_ACETATE,
        )
        val doseInstruction = DoseInstruction.TabletFraction(1, 4)
        val group = MedicationGroup(
            uuid = UUID.fromString("c3b6f0a1-5555-4aaa-9999-000000000005"),
            name = "CPA",
            colorKey = MedicationGroupColorKey.TEAL,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(20, 0))
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("96f31e44-2059-4f89-a3f4-5477cbbce4b3"),
                    medicine = medicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = doseInstruction,
                )
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z")
        )
        val manualDoseTime = LocalDateTime.of(2026, 4, 18, 8, 0)
        val groupLinkedDoseTime = LocalDateTime.of(2026, 4, 18, 9, 0)

        val cards = buildMainAntiandrogenCards(
            groups = listOf(group),
            entries = listOf(
                testMedicationLogEntry(
                    uuid = UUID.randomUUID(),
                    medicine = medicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = doseInstruction,
                    sourceGroupUuid = null,
                    appliedAt = testInstant(manualDoseTime),
                ),
                testMedicationLogEntry(
                    uuid = UUID.randomUUID(),
                    medicine = medicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = doseInstruction,
                    sourceGroupUuid = group.uuid,
                    appliedAt = testInstant(groupLinkedDoseTime),
                )
            ),
            now = LocalDateTime.of(2026, 4, 18, 11, 0),
            zoneId = testZoneId
        )

        assertEquals(1, cards.size)
        assertEquals(groupLinkedDoseTime, cards.single().lastDoseAt)
        assertEquals(doseInstruction, cards.single().lastDose?.doseInstruction)
        assertEquals(false, cards.single().isManualRow)
    }

    @Test
    fun buildMainAntiandrogenCards_includes_logs_within_current_displayed_minute() {
        val medicine = testMedicine(
            uuid = UUID.fromString("aaaa0000-0000-0000-0000-000000000064"),
            key = MedicationKey.CYPROTERONE_ACETATE,
        )
        val doseInstruction = DoseInstruction.TabletFraction(1, 4)
        val group = MedicationGroup(
            uuid = UUID.fromString("c3b6f0a1-8888-4aaa-9999-000000000008"),
            name = "CPA",
            colorKey = MedicationGroupColorKey.TEAL,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(20, 0))
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("96f31e44-2059-4f89-a3f4-5477cbbce4b3"),
                    medicine = medicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = doseInstruction,
                )
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z")
        )
        val displayedNow = LocalDateTime.of(2026, 4, 18, 9, 0)
        val groupLinkedDoseTime = displayedNow.plusSeconds(5)
        val manualDoseTime = displayedNow.plusSeconds(6)

        val cards = buildMainAntiandrogenCards(
            groups = listOf(group),
            entries = listOf(
                testMedicationLogEntry(
                    uuid = UUID.randomUUID(),
                    medicine = medicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = doseInstruction,
                    sourceGroupUuid = group.uuid,
                    appliedAt = testInstant(groupLinkedDoseTime),
                ),
                testMedicationLogEntry(
                    uuid = UUID.randomUUID(),
                    medicine = medicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = doseInstruction,
                    sourceGroupUuid = null,
                    appliedAt = testInstant(manualDoseTime),
                )
            ),
            now = displayedNow,
            zoneId = testZoneId
        )

        assertEquals(2, cards.size)
        assertEquals(groupLinkedDoseTime, cards[0].lastDoseAt)
        assertEquals(false, cards[0].isManualRow)
        assertEquals(manualDoseTime, cards[1].lastDoseAt)
        assertEquals(true, cards[1].isManualRow)
    }

    @Test
    fun buildMainAntiandrogenCards_keeps_manual_row_when_manual_log_ties_group_linked_minute() {
        val medicine = testMedicine(
            uuid = UUID.fromString("aaaa0000-0000-0000-0000-000000000065"),
            key = MedicationKey.CYPROTERONE_ACETATE,
        )
        val doseInstruction = DoseInstruction.TabletFraction(1, 4)
        val group = MedicationGroup(
            uuid = UUID.fromString("c3b6f0a1-9999-4aaa-9999-000000000009"),
            name = "CPA",
            colorKey = MedicationGroupColorKey.TEAL,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(20, 0))
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("96f31e44-2059-4f89-a3f4-5477cbbce4b3"),
                    medicine = medicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = doseInstruction,
                )
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z")
        )
        val loggedMinute = LocalDateTime.of(2026, 4, 18, 9, 0)

        val cards = buildMainAntiandrogenCards(
            groups = listOf(group),
            entries = listOf(
                testMedicationLogEntry(
                    uuid = UUID.randomUUID(),
                    medicine = medicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = doseInstruction,
                    sourceGroupUuid = group.uuid,
                    appliedAt = testInstant(loggedMinute),
                ),
                testMedicationLogEntry(
                    uuid = UUID.randomUUID(),
                    medicine = medicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = doseInstruction,
                    sourceGroupUuid = null,
                    appliedAt = testInstant(loggedMinute),
                )
            ),
            now = loggedMinute,
            zoneId = testZoneId
        )

        assertEquals(2, cards.size)
        assertEquals(loggedMinute, cards[0].lastDoseAt)
        assertEquals(false, cards[0].isManualRow)
        assertEquals(loggedMinute, cards[1].lastDoseAt)
        assertEquals(true, cards[1].isManualRow)
    }

    @Test
    fun buildMainAntiandrogenCards_emits_manual_row_when_manual_dose_is_newer_than_group_linked_doses() {
        val medicine = testMedicine(
            uuid = UUID.fromString("aaaa0000-0000-0000-0000-000000000061"),
            key = MedicationKey.CYPROTERONE_ACETATE,
        )
        val quarterDose = DoseInstruction.TabletFraction(1, 4)
        val thirdDose = DoseInstruction.TabletFraction(1, 3)
        val group = MedicationGroup(
            uuid = UUID.fromString("c3b6f0a1-6666-4aaa-9999-000000000006"),
            name = "CPA",
            colorKey = MedicationGroupColorKey.TEAL,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(20, 0))
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("96f31e44-2059-4f89-a3f4-5477cbbce4b3"),
                    medicine = medicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = quarterDose,
                ),
                testMedicationGroupMedication(
                    uuid = UUID.fromString("e1a7c6d6-6ef2-4c8b-9a56-3a4f4f1298ce"),
                    medicine = medicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = thirdDose,
                )
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z")
        )
        val quarterDoseTime = LocalDateTime.of(2026, 4, 18, 8, 0)
        val thirdDoseTime = LocalDateTime.of(2026, 4, 18, 9, 0)
        val manualDoseTime = LocalDateTime.of(2026, 4, 18, 10, 0)

        val cards = buildMainAntiandrogenCards(
            groups = listOf(group),
            entries = listOf(
                testMedicationLogEntry(
                    uuid = UUID.randomUUID(),
                    medicine = medicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = quarterDose,
                    sourceGroupUuid = group.uuid,
                    appliedAt = testInstant(quarterDoseTime),
                ),
                testMedicationLogEntry(
                    uuid = UUID.randomUUID(),
                    medicine = medicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = thirdDose,
                    sourceGroupUuid = group.uuid,
                    appliedAt = testInstant(thirdDoseTime),
                ),
                testMedicationLogEntry(
                    uuid = UUID.randomUUID(),
                    medicine = medicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = thirdDose,
                    sourceGroupUuid = null,
                    appliedAt = testInstant(manualDoseTime),
                )
            ),
            now = LocalDateTime.of(2026, 4, 18, 11, 0),
            zoneId = testZoneId
        )

        assertEquals(3, cards.size)
        assertEquals(
            listOf(quarterDoseTime, thirdDoseTime, manualDoseTime),
            cards.map { it.lastDoseAt }
        )
        assertEquals(
            listOf(false, false, true),
            cards.map { it.isManualRow }
        )
        assertNull(cards.last().nextDoseAt)
    }

    @Test
    fun buildMainAntiandrogenCards_orders_manual_row_after_its_medication_plan_slots_before_next_medication() {
        val cpaMedicine = testMedicine(
            uuid = UUID.fromString("aaaa0000-0000-0000-0000-000000000062"),
            key = MedicationKey.CYPROTERONE_ACETATE,
        )
        val spiroMedicine = testMedicine(
            uuid = UUID.fromString("aaaa0000-0000-0000-0000-000000000063"),
            key = MedicationKey.SPIRONOLACTONE,
        )
        val quarterDose = DoseInstruction.TabletFraction(1, 4)
        val thirdDose = DoseInstruction.TabletFraction(1, 3)
        val group = MedicationGroup(
            uuid = UUID.fromString("c3b6f0a1-7777-4aaa-9999-000000000007"),
            name = "Blockers",
            colorKey = MedicationGroupColorKey.TEAL,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(20, 0))
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("96f31e44-2059-4f89-a3f4-5477cbbce4b3"),
                    medicine = cpaMedicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = quarterDose,
                ),
                testMedicationGroupMedication(
                    uuid = UUID.fromString("e1a7c6d6-6ef2-4c8b-9a56-3a4f4f1298ce"),
                    medicine = cpaMedicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = thirdDose,
                ),
                testMedicationGroupMedication(
                    uuid = UUID.fromString("bc722321-7c11-4318-a7d5-cbd712ad1621"),
                    medicine = spiroMedicine,
                    applicationType = MedicationApplicationType.ORAL,
                )
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z")
        )
        val manualDoseTime = LocalDateTime.of(2026, 4, 18, 10, 0)

        val cards = buildMainAntiandrogenCards(
            groups = listOf(group),
            entries = listOf(
                testMedicationLogEntry(
                    uuid = UUID.randomUUID(),
                    medicine = cpaMedicine,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = thirdDose,
                    sourceGroupUuid = null,
                    appliedAt = testInstant(manualDoseTime),
                )
            ),
            now = LocalDateTime.of(2026, 4, 18, 11, 0),
            zoneId = testZoneId
        )

        assertEquals(
            listOf(
                MedicineSelection.Catalog(MedicationKey.CYPROTERONE_ACETATE),
                MedicineSelection.Catalog(MedicationKey.CYPROTERONE_ACETATE),
                MedicineSelection.Catalog(MedicationKey.CYPROTERONE_ACETATE),
                MedicineSelection.Catalog(MedicationKey.SPIRONOLACTONE),
            ),
            cards.map { card ->
                card.lastDose?.medicine?.selection ?: card.medication.medicine?.selection
            }
        )
        assertEquals(listOf(false, false, true, false), cards.map { it.isManualRow })
    }

    @Test
    fun buildMainAntiandrogenCards_skips_fulfilled_upcoming_due_slot() {
        val antiandrogenGroup = antiandrogenGroup(
            uuid = UUID.fromString("d4d0d2d5-3201-4bf1-84e0-64d95d37599d"),
            times = listOf(LocalTime.of(19, 30))
        )
        val now = LocalDateTime.of(2026, 4, 18, 19, 18)
        val scheduledFor = LocalDateTime.of(2026, 4, 18, 19, 30)

        val cards = buildMainAntiandrogenCards(
            groups = listOf(antiandrogenGroup),
            entries = listOf(
                scheduledAntiandrogenEntry(
                    group = antiandrogenGroup,
                    appliedAt = now,
                    scheduledFor = scheduledFor
                )
            ),
            now = now,
            zoneId = testZoneId
        )

        assertEquals(LocalDateTime.of(2026, 4, 19, 19, 30), cards.single().nextDoseAt)
        assertEquals(false, cards.single().isNextDosePastDue)
    }

    @Test
    fun buildMainAntiandrogenCards_marks_due_slot_past_due_after_display_grace() {
        val antiandrogenGroup = antiandrogenGroup(
            uuid = UUID.fromString("1e0e149c-28bb-48e7-a037-64a57f571e57"),
            times = listOf(LocalTime.of(19, 30))
        )

        val cards = buildMainAntiandrogenCards(
            groups = listOf(antiandrogenGroup),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 18, 20, 31),
            zoneId = testZoneId
        )

        assertEquals(LocalDateTime.of(2026, 4, 18, 19, 30), cards.single().nextDoseAt)
        assertEquals(true, cards.single().isNextDosePastDue)
    }

    @Test
    fun buildMainAntiandrogenCards_marks_due_slot_past_due_at_exactlyOneHour() {
        val antiandrogenGroup = antiandrogenGroup(
            uuid = UUID.fromString("3af0ed19-8986-4a66-8ced-3a2b06c219e8"),
            times = listOf(LocalTime.of(19, 30))
        )

        val cards = buildMainAntiandrogenCards(
            groups = listOf(antiandrogenGroup),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 18, 20, 30),
            zoneId = testZoneId
        )

        assertEquals(LocalDateTime.of(2026, 4, 18, 19, 30), cards.single().nextDoseAt)
        assertEquals(true, cards.single().isNextDosePastDue)
    }

    @Test
    fun buildMainAntiandrogenCards_returns_next_scheduled_slot_after_past_due_window_expires() {
        val antiandrogenGroup = antiandrogenGroup(
            uuid = UUID.fromString("c240fd51-2797-47dc-a6ff-ad2e22309641"),
            times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))
        )

        val cards = buildMainAntiandrogenCards(
            groups = listOf(antiandrogenGroup),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 18, 15, 0),
            zoneId = testZoneId
        )

        assertEquals(LocalDateTime.of(2026, 4, 18, 20, 0), cards.single().nextDoseAt)
        assertEquals(false, cards.single().isNextDosePastDue)
    }

    @Test
    fun mainCompactElapsedTotalMinutes_counts_elapsed_minute_boundaries() {
        val appliedAt = LocalDateTime.of(2026, 4, 18, 19, 18, 50)

        assertEquals(
            0L,
            mainCompactElapsedTotalMinutes(appliedAt, LocalDateTime.of(2026, 4, 18, 19, 18))
        )
        assertEquals(
            1L,
            mainCompactElapsedTotalMinutes(appliedAt, LocalDateTime.of(2026, 4, 18, 19, 19))
        )
        assertEquals(
            2L,
            mainCompactElapsedTotalMinutes(appliedAt, LocalDateTime.of(2026, 4, 18, 19, 20))
        )
        assertEquals(
            3L,
            mainCompactElapsedTotalMinutes(appliedAt, LocalDateTime.of(2026, 4, 18, 19, 21))
        )
    }

    @Test
    fun buildMainAntiandrogenCards_ignores_non_antiandrogen_groups() {
        val estradiolGroup = medicationGroup(
            uuid = UUID.fromString("b0fd2bf8-91f8-4cf8-a1ec-5d9f666c25db"),
            name = "Daily estradiol",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0))
            )
        )

        assertEquals(
            emptyList<MainAntiandrogenCardUiState>(),
            buildMainAntiandrogenCards(
                groups = listOf(estradiolGroup),
                entries = emptyList(),
                now = LocalDateTime.of(2026, 4, 18, 11, 0)
            )
        )
    }

    @Test
    fun buildMainAntiandrogenCards_collapses_duplicate_matching_rows_into_one_counted_card() {
        val sharedMedicine = testMedicine(key = MedicationKey.SPIRONOLACTONE)
        val antiandrogenGroup = MedicationGroup(
            uuid = UUID.fromString("0f32c3be-a63a-4a2f-aebd-f9c9bc2df942"),
            name = "Night blocker",
            colorKey = MedicationGroupColorKey.TEAL,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(22, 0))
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("725d4c60-1684-4c8f-b786-4d7df50a6400"),
                    medicine = sharedMedicine,
                    applicationType = MedicationApplicationType.ORAL,
                ),
                testMedicationGroupMedication(
                    uuid = UUID.fromString("74c29e3e-724d-4f3c-996f-6f4195ae7904"),
                    medicine = sharedMedicine,
                    applicationType = MedicationApplicationType.ORAL,
                )
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z")
        )

        val cards = buildMainAntiandrogenCards(
            groups = listOf(antiandrogenGroup),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 18, 11, 0),
            zoneId = testZoneId
        )

        assertEquals(1, cards.size)
        assertEquals(2, cards.single().medication.count)
    }

    @Test
    fun buildMainAntiandrogenCards_orders_groups_by_oldest_creation_time_first() {
        val olderGroup = medicationGroup(
            uuid = UUID.fromString("c806d3bb-61a6-4efa-883a-99b03cc503b8"),
            name = "Older blocker",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0))
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("bc722321-7c11-4318-a7d5-cbd712ad1621"),
                    medicine = testMedicine(key = MedicationKey.SPIRONOLACTONE),
                    applicationType = MedicationApplicationType.ORAL,
                )
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z")
        )
        val newerGroup = medicationGroup(
            uuid = UUID.fromString("d1e9086f-a911-46d6-a941-fb390dc09f9a"),
            name = "Newer blocker",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(20, 0))
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("c6d2a3e6-5f5f-4cb7-9e5b-3e6634745f1b"),
                    medicine = testMedicine(key = MedicationKey.CYPROTERONE_ACETATE),
                    applicationType = MedicationApplicationType.ORAL,
                )
            ),
            createdAt = Instant.parse("2026-04-10T00:00:00Z")
        )

        val cards = buildMainAntiandrogenCards(
            groups = listOf(newerGroup, olderGroup),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 18, 11, 0),
            zoneId = testZoneId
        )

        assertEquals(listOf("Older blocker", "Newer blocker"), cards.map { it.groupName })
    }

    @Test
    fun buildMainTodaySection_marks_done_overdue_dueSoon_and_upcoming_rows() {
        val group = medicationGroup(
            uuid = UUID.fromString("7b53f876-8809-4f64-91b0-c6c6a59b87c0"),
            name = "Daily estradiol",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(
                    LocalTime.of(8, 0),
                    LocalTime.of(9, 0),
                    LocalTime.of(11, 30),
                    LocalTime.of(20, 0)
                )
            )
        )

        val todaySection = buildMainTodaySection(
            groups = listOf(group),
            entries = listOf(
                scheduledEntry(
                    groupUuid = group.uuid,
                    appliedAt = LocalDateTime.of(2026, 4, 18, 8, 5),
                    scheduledFor = LocalDateTime.of(2026, 4, 18, 8, 0)
                )
            ),
            now = LocalDateTime.of(2026, 4, 18, 11, 0)
        )

        assertEquals(1, todaySection.doneCount)
        assertEquals(4, todaySection.totalCount)
        assertEquals(
            listOf(
                MainTodayDoseStatus.DONE,
                MainTodayDoseStatus.OVERDUE,
                MainTodayDoseStatus.DUE_SOON,
                MainTodayDoseStatus.UPCOMING
            ),
            todaySection.rows.map { it.status }
        )
        assertEquals(
            listOf(
                MedicationGroupColorKey.PLUM,
                MedicationGroupColorKey.PLUM,
                MedicationGroupColorKey.PLUM,
                MedicationGroupColorKey.PLUM,
            ),
            todaySection.rows.map { it.groupColorKey }
        )
        assertNotNull(todaySection.rows.first().loggedAt)
    }

    @Test
    fun buildMainTodaySection_countsLoggedArchivedDose() {
        val now = LocalDateTime.of(2026, 5, 6, 12, 0)
        val archived = medicationGroup(
            uuid = UUID.fromString("c1d2e3f4-5678-49ab-8cde-f0123456789a"),
            name = "Archived estradiol",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0)),
            ),
        ).copy(
            archivedAt = testInstant(now.minusDays(1)),
            archivedAtLocal = now.minusDays(1),
        )
        val logged = scheduledEntry(
            groupUuid = archived.uuid,
            appliedAt = now.toLocalDate().atTime(8, 5),
            scheduledFor = now.toLocalDate().atTime(8, 0),
        )

        val section = buildMainTodaySection(
            groups = listOf(archived),
            entries = listOf(logged),
            now = now,
            zoneId = testZoneId,
            includeUnloggedArchivedSlots = false,
            unloggedArchivedSlotCutoff = now,
        )

        assertEquals(1, section.totalCount)
        assertEquals(1, section.doneCount)
        assertEquals(archived.name, section.rows.first().groupName)
        assertEquals(MedicationGroupColorKey.PLUM, section.rows.first().groupColorKey)
    }

    @Test
    fun buildMainTodaySection_flagsArchivedGroupRows_butNotActiveGroupRows() {
        val now = LocalDateTime.of(2026, 5, 6, 12, 0)
        val schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.DAILY,
            interval = 1,
            since = LocalDate.of(2026, 4, 1),
            weeklyDaysOfWeek = emptySet(),
            times = listOf(LocalTime.of(8, 0)),
        )
        val active = medicationGroup(
            uuid = UUID.fromString("11111111-1111-41ab-8cde-f0123456789a"),
            name = "Active estradiol",
            schedule = schedule,
        )
        val archived = medicationGroup(
            uuid = UUID.fromString("22222222-2222-49ab-8cde-f0123456789a"),
            name = "Archived estradiol",
            schedule = schedule,
        ).copy(
            archivedAt = testInstant(now.minusDays(1)),
            archivedAtLocal = now.minusDays(1),
        )
        val activeLog = scheduledEntry(
            groupUuid = active.uuid,
            appliedAt = now.toLocalDate().atTime(8, 5),
            scheduledFor = now.toLocalDate().atTime(8, 0),
        )
        val archivedLog = scheduledEntry(
            groupUuid = archived.uuid,
            appliedAt = now.toLocalDate().atTime(8, 5),
            scheduledFor = now.toLocalDate().atTime(8, 0),
        )

        val section = buildMainTodaySection(
            groups = listOf(active, archived),
            entries = listOf(activeLog, archivedLog),
            now = now,
            zoneId = testZoneId,
            includeUnloggedArchivedSlots = false,
            unloggedArchivedSlotCutoff = now,
        )

        assertEquals(
            true,
            section.rows.single { it.groupUuid == archived.uuid }.isFromArchivedGroup
        )
        assertEquals(false, section.rows.single { it.groupUuid == active.uuid }.isFromArchivedGroup)
    }

    @Test
    fun buildMainTodaySection_includesPastArchivedSlot_excludesFutureArchivedSlot() {
        val now = LocalDateTime.of(2026, 5, 6, 12, 0)
        val archived = medicationGroup(
            uuid = UUID.fromString("a9b8c7d6-5432-41fe-9dcb-a09876543210"),
            name = "Archived estradiol",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
            ),
        ).copy(
            archivedAt = testInstant(now.toLocalDate().atTime(23, 0)),
            archivedAtLocal = now.toLocalDate().atTime(23, 0),
        )

        val section = buildMainTodaySection(
            groups = listOf(archived),
            entries = emptyList(),
            now = now,
            zoneId = testZoneId,
            includeUnloggedArchivedSlots = false,
            unloggedArchivedSlotCutoff = now,
        )

        // The group is archived later today (23:00), so both 08:00 and 20:00
        // are "owned" unlogged occurrences. With includeUnloggedArchivedSlots
        // = false and the cutoff at now (12:00): the 08:00 slot is before the
        // cutoff so it still appears; the 20:00 slot is after the cutoff so it
        // is suppressed.
        assertEquals(1, section.totalCount)
        assertEquals(LocalTime.of(8, 0), section.rows.single().scheduledAt.toLocalTime())
    }

    @Test
    fun buildMainTodaySection_marks_exactlyOneHourLateSlot_overdue() {
        val group = medicationGroup(
            uuid = UUID.fromString("53f2b974-b2de-49c4-9160-c83a2219f803"),
            name = "Daily estradiol",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(21, 0))
            )
        )

        val todaySection = buildMainTodaySection(
            groups = listOf(group),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 18, 22, 0),
            zoneId = testZoneId
        )

        assertEquals(MainTodayDoseStatus.OVERDUE, todaySection.rows.single().status)
    }

    @Test
    fun buildMainTodaySection_keeps_quickLogSourceGroupContext_onScheduledRows() {
        val groupUuid = UUID.fromString("fa31a982-b7f5-40bf-8d87-8f87d61c0237")
        val group = medicationGroup(
            uuid = groupUuid,
            name = "Snapshot estradiol",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(21, 0))
            )
        )
        val scheduledFor = LocalDateTime.of(2026, 4, 18, 21, 0)

        val todaySection = buildMainTodaySection(
            groups = listOf(group),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 18, 20, 0),
            zoneId = testZoneId
        )

        val row = todaySection.rows.single()
        assertEquals(groupUuid, row.groupUuid)
        assertEquals("Snapshot estradiol", row.groupName)
        assertEquals(MedicationGroupColorKey.PLUM, row.groupColorKey)
        assertEquals(scheduledFor.minusDays(1), row.sourceGroupPreviousScheduledFor)
        assertEquals(scheduledFor.plusDays(1), row.sourceGroupNextScheduledFor)
    }

    @Test
    fun buildMainTodaySection_keeps_editSnapshotForLoggedScheduledRows() {
        val groupUuid = UUID.fromString("1b947ceb-a655-48a7-b017-fb180dc17548")
        val entryUuid = UUID.fromString("e765fdad-fd9f-4291-84a1-d43d25003b83")
        val scheduledFor = LocalDateTime.of(2026, 4, 18, 21, 0)
        val group = medicationGroup(
            uuid = groupUuid,
            name = "Snapshot estradiol",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(scheduledFor.toLocalTime())
            )
        )
        val entry = testMedicationLogEntry(
            uuid = entryUuid,
            medicine = group.medications.single().medicine,
            applicationType = group.medications.single().applicationType,
            doseInstruction = group.medications.single().doseInstruction,
            equivalentE2Mg = 2.0,
            sourceGroupUuid = groupUuid,
            appliedAt = testInstant(scheduledFor.plusMinutes(5)),
            scheduledFor = scheduledFor,
        )

        val todaySection = buildMainTodaySection(
            groups = listOf(group),
            entries = listOf(entry),
            now = LocalDateTime.of(2026, 4, 18, 22, 0),
            zoneId = testZoneId
        )

        val row = todaySection.rows.single()
        assertEquals(MainTodayDoseStatus.DONE, row.status)
        assertEquals(listOf(entryUuid), row.fulfillingEntryUuids)
        assertEquals(listOf(entry), row.editSnapshotEntries)
        assertEquals("Snapshot estradiol", row.groupName)
        assertEquals(MedicationGroupColorKey.PLUM, row.groupColorKey)
    }

    @Test
    fun buildMainTodaySection_orders_planned_ties_by_groupCreation_andMedicationOrder() {
        val schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.DAILY,
            interval = 1,
            since = LocalDate.of(2026, 4, 1),
            weeklyDaysOfWeek = emptySet(),
            times = listOf(LocalTime.of(9, 0))
        )
        val olderGroupUuid = UUID.fromString("1e68415f-cf91-4aeb-aa2d-174b233a57c9")
        val newerGroupUuid = UUID.fromString("bd2715d0-a95d-4e92-aa0a-a86f23357d96")
        val olderFirstMedicationUuid = UUID.fromString("e07a7207-c38d-4e03-84b4-bc92efa39109")
        val olderSecondMedicationUuid = UUID.fromString("89ebfa97-ae2a-4819-bf33-30d3ed3fb8af")
        val newerMedicationUuid = UUID.fromString("ae02f5cb-e61d-4bfd-a17b-2634a1876d30")
        val olderGroup = medicationGroup(
            uuid = olderGroupUuid,
            name = "Older plan",
            schedule = schedule,
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = olderFirstMedicationUuid,
                    medicine = testMedicine(key = MedicationKey.SPIRONOLACTONE),
                    applicationType = MedicationApplicationType.ORAL,
                ),
                testMedicationGroupMedication(
                    uuid = olderSecondMedicationUuid,
                    medicine = testMedicine(key = MedicationKey.ESTRADIOL),
                    applicationType = MedicationApplicationType.ORAL,
                )
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z")
        )
        val newerGroup = medicationGroup(
            uuid = newerGroupUuid,
            name = "Newer plan",
            schedule = schedule,
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = newerMedicationUuid,
                    medicine = testMedicine(key = MedicationKey.CYPROTERONE_ACETATE),
                    applicationType = MedicationApplicationType.ORAL,
                )
            ),
            createdAt = Instant.parse("2026-04-02T00:00:00Z")
        )

        val todaySection = buildMainTodaySection(
            groups = listOf(newerGroup, olderGroup),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 18, 7, 0),
            zoneId = testZoneId
        )

        assertEquals(
            listOf(olderGroupUuid, olderGroupUuid, newerGroupUuid),
            todaySection.rows.map { row -> row.groupUuid }
        )
        assertEquals(
            listOf(olderFirstMedicationUuid, olderSecondMedicationUuid, newerMedicationUuid),
            todaySection.rows.map { row -> row.medication.uuid }
        )
    }

    @Test
    fun buildMainTodaySection_includes_manual_records() {
        val manualEntryUuid = UUID.fromString("0e9e45b6-e8a9-4a44-8073-fd34785df69f")
        val earlierManualEntryUuid = UUID.fromString("a4596c95-a24a-40c2-a794-31d68aec6a25")
        val manualAppliedAt = LocalDateTime.of(2026, 4, 18, 10, 15)
        val earlierManualAppliedAt = LocalDateTime.of(2026, 4, 18, 7, 45)

        val todaySection = buildMainTodaySection(
            groups = emptyList(),
            entries = listOf(
                manualEntry(
                    uuid = manualEntryUuid,
                    appliedAt = manualAppliedAt,
                    count = 2
                ),
                manualEntry(
                    uuid = earlierManualEntryUuid,
                    appliedAt = earlierManualAppliedAt
                )
            ),
            now = LocalDateTime.of(2026, 4, 18, 11, 0),
            zoneId = testZoneId
        )

        assertEquals(0, todaySection.doneCount)
        assertEquals(0, todaySection.totalCount)
        assertEquals(2, todaySection.manualCount)
        assertEquals(2, todaySection.rows.size)
        assertEquals(
            listOf(earlierManualEntryUuid, manualEntryUuid),
            todaySection.rows.map { row -> row.medication.uuid }
        )

        val row = todaySection.rows.last()
        assertEquals(MainTodayDoseStatus.DONE, row.status)
        assertEquals(manualAppliedAt, row.scheduledAt)
        assertEquals(manualAppliedAt, row.loggedAt)
        assertEquals(listOf(manualEntryUuid), row.fulfillingEntryUuids)
        assertEquals(2, row.loggedCount)
        assertEquals(2, row.medication.count)
        assertEquals(true, row.isManualRecord)
        assertNull(row.groupUuid)
        assertNull(row.groupColorKey)
    }

    @Test
    fun buildMainTodaySection_attaches_cross_zone_entry_to_scheduled_slot_not_manual() {
        // Reproduce the production-reported bug: an entry logged in another zone
        // (here Tokyo) for today's scheduled slot was rendering as a separate
        // manual row on the home screen instead of fulfilling the slot.
        val groupUuid = UUID.fromString("c1f7d35a-7322-4cdc-a8f5-30bd7f3a2e62")
        val tokyo = ZoneId.of("Asia/Tokyo")
        val deviceZone = ZoneId.of("America/Los_Angeles")
        val scheduledFor = LocalDateTime.of(2026, 4, 18, 9, 0)
        val group = medicationGroup(
            uuid = groupUuid,
            name = "Daily oral",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0)),
            ),
        )
        val crossZoneEntry = testMedicationLogEntry(
            uuid = UUID.fromString("b2a83c9e-6d1f-4f6b-b9b3-5db5ee3b35b1"),
            medicine = group.medications.single().medicine,
            applicationType = group.medications.single().applicationType,
            doseInstruction = group.medications.single().doseInstruction,
            equivalentE2Mg = 2.0,
            sourceGroupUuid = group.uuid,
            appliedAt = scheduledFor.atZone(tokyo).toInstant(),
            appliedAtTimeZoneId = "Asia/Tokyo",
            scheduledFor = scheduledFor,
        )

        val todaySection = buildMainTodaySection(
            groups = listOf(group),
            entries = listOf(crossZoneEntry),
            now = LocalDateTime.of(2026, 4, 18, 12, 0),
            zoneId = deviceZone,
        )

        // The scheduled slot should be present and marked DONE; no manual rows.
        assertEquals(0, todaySection.manualCount)
        assertEquals(1, todaySection.totalCount)
        assertEquals(1, todaySection.doneCount)
        val row = todaySection.rows.single()
        assertEquals(false, row.isManualRecord)
        assertEquals(MainTodayDoseStatus.DONE, row.status)
        assertEquals(listOf(crossZoneEntry.uuid), row.fulfillingEntryUuids)
    }

    @Test
    fun buildMainTodaySection_orders_manual_record_after_scheduled_row_at_same_time() {
        val scheduledGroupUuid = UUID.fromString("576db5e4-a76a-4fc3-8adb-89d83d914411")
        val manualEntryUuid = UUID.fromString("82546aa9-e2d4-4db2-b376-b5d54f92ca0c")
        val sharedTime = LocalDateTime.of(2026, 4, 18, 10, 15)
        val scheduledGroup = medicationGroup(
            uuid = scheduledGroupUuid,
            name = "Scheduled estradiol",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(sharedTime.toLocalTime())
            )
        )

        val todaySection = buildMainTodaySection(
            groups = listOf(scheduledGroup),
            entries = listOf(
                manualEntry(
                    uuid = manualEntryUuid,
                    appliedAt = sharedTime
                )
            ),
            now = LocalDateTime.of(2026, 4, 18, 11, 0),
            zoneId = testZoneId
        )

        assertEquals(
            listOf(false, true),
            todaySection.rows.map { row -> row.isManualRecord }
        )
        assertEquals(scheduledGroupUuid, todaySection.rows.first().groupUuid)
        assertEquals(manualEntryUuid, todaySection.rows.last().medication.uuid)
    }

    @Test
    fun buildMainTodaySection_excludesLastNightRows_duringOvernight() {
        val previousDate = LocalDate.of(2026, 4, 18)
        val today = previousDate.plusDays(1)
        val group = medicationGroup(
            uuid = UUID.fromString("6aac3b69-faa0-4895-a465-9e25e85ef791"),
            name = "Saturday evening estradiol",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.WEEKLY,
                interval = 1,
                since = previousDate,
                weeklyDaysOfWeek = setOf(previousDate.dayOfWeek),
                times = listOf(LocalTime.of(17, 0), LocalTime.of(20, 0))
            )
        )
        val beforeLastNightManualUuid = UUID.fromString("79b66034-0348-4bb4-b0e1-4c53873cbb4a")
        val lastNightManualUuid = UUID.fromString("54c3d987-a5bf-48d3-835d-ddd7578c9994")
        val todayManualUuid = UUID.fromString("52dd87f9-f07d-40e1-b87e-82dcd5da1280")

        val entries = listOf(
            manualEntry(
                uuid = beforeLastNightManualUuid,
                appliedAt = LocalDateTime.of(previousDate, LocalTime.of(17, 59))
            ),
            manualEntry(
                uuid = lastNightManualUuid,
                appliedAt = LocalDateTime.of(previousDate, LocalTime.of(21, 15))
            ),
            manualEntry(
                uuid = todayManualUuid,
                appliedAt = LocalDateTime.of(today, LocalTime.of(1, 15))
            )
        )
        val now = LocalDateTime.of(today, LocalTime.of(2, 30))

        val todaySection = buildMainTodaySection(
            groups = listOf(group),
            entries = entries,
            now = now,
            zoneId = testZoneId
        )

        assertEquals(0, todaySection.doneCount)
        assertEquals(0, todaySection.totalCount)
        assertEquals(1, todaySection.manualCount)
        assertEquals(
            listOf(LocalDateTime.of(today, LocalTime.of(1, 15))),
            todaySection.rows.map { row -> row.scheduledAt }
        )

        val lastNightSection = buildMainLastNightSection(
            groups = listOf(group),
            entries = entries,
            now = now,
            zoneId = testZoneId
        )

        assertEquals(previousDate, lastNightSection.date)
        assertEquals(0, lastNightSection.doneCount)
        assertEquals(1, lastNightSection.totalCount)
        assertEquals(1, lastNightSection.manualCount)
        assertEquals(
            listOf(
                LocalDateTime.of(previousDate, LocalTime.of(20, 0)),
                LocalDateTime.of(previousDate, LocalTime.of(21, 15))
            ),
            lastNightSection.rows.map { row -> row.scheduledAt }
        )
        assertEquals(
            listOf(false, true),
            lastNightSection.rows.map { row -> row.isManualRecord }
        )
    }

    @Test
    fun buildMainLastNightSection_isEmpty_atSix() {
        val previousDate = LocalDate.of(2026, 4, 18)
        val today = previousDate.plusDays(1)
        val group = medicationGroup(
            uuid = UUID.fromString("6a5adecf-79c8-467f-a9a2-0846f5577f59"),
            name = "Saturday evening estradiol",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.WEEKLY,
                interval = 1,
                since = previousDate,
                weeklyDaysOfWeek = setOf(previousDate.dayOfWeek),
                times = listOf(LocalTime.of(20, 0))
            )
        )
        val entries = listOf(
            manualEntry(
                appliedAt = LocalDateTime.of(previousDate, LocalTime.of(21, 15))
            )
        )
        val now = LocalDateTime.of(today, LocalTime.of(6, 0))

        val todaySection = buildMainTodaySection(
            groups = listOf(group),
            entries = entries,
            now = now,
            zoneId = testZoneId
        )
        assertEquals(emptyList<MainTodayDoseRowUiState>(), todaySection.rows)
        assertEquals(0, todaySection.totalCount)
        assertEquals(0, todaySection.manualCount)

        val lastNightSection = buildMainLastNightSection(
            groups = listOf(group),
            entries = entries,
            now = now,
            zoneId = testZoneId
        )
        assertEquals(emptyList<MainTodayDoseRowUiState>(), lastNightSection.rows)
        assertEquals(0, lastNightSection.totalCount)
        assertEquals(0, lastNightSection.manualCount)
        assertNull(lastNightSection.date)
    }

    @Test
    fun buildMainUpcomingSection_returns_tomorrow_rows_when_tomorrow_has_unfulfilled_slots() {
        val group = medicationGroup(
            uuid = UUID.fromString("1ec1b1bf-f3ff-4104-9ed5-68d5aa7e5a47"),
            name = "Daily spiro",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))
            )
        )

        val upcomingSection = buildMainUpcomingSection(
            groups = listOf(group),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 18, 11, 0)
        )

        assertEquals(MainUpcomingSectionTitle.TOMORROW, upcomingSection.title)
        assertEquals(LocalDate.of(2026, 4, 19), upcomingSection.anchorDate)
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 4, 19, 8, 0),
                LocalDateTime.of(2026, 4, 19, 20, 0)
            ),
            upcomingSection.rows.map { it.scheduledAt }
        )
        assertEquals(
            listOf(MedicationGroupColorKey.PLUM, MedicationGroupColorKey.PLUM),
            upcomingSection.rows.map { it.groupColorKey }
        )
    }

    @Test
    fun buildMainUpcomingSection_falls_back_to_future_upcoming_when_tomorrow_is_empty() {
        val group = medicationGroup(
            uuid = UUID.fromString("efea64bc-0f6d-4813-a39d-428bdce6fd6a"),
            name = "Weekly estradiol",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.WEEKLY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = setOf(LocalDate.of(2026, 4, 20).dayOfWeek),
                times = listOf(LocalTime.of(13, 30))
            )
        )

        val upcomingSection = buildMainUpcomingSection(
            groups = listOf(group),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 18, 11, 0)
        )

        assertEquals(MainUpcomingSectionTitle.UPCOMING, upcomingSection.title)
        assertEquals(LocalDate.of(2026, 4, 20), upcomingSection.anchorDate)
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 4, 20, 13, 30)
            ),
            upcomingSection.rows.map { it.scheduledAt }
        )
    }

    @Test
    fun buildMainUpcomingSection_finds_next_schedule_beyond_two_weeks() {
        val nextScheduleDate = LocalDate.of(2026, 5, 27)
        val group = medicationGroup(
            uuid = UUID.fromString("a69109cd-ea09-491c-a30f-43658fd3d5b4"),
            name = "Sparse weekly estradiol",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.WEEKLY,
                interval = 1,
                since = nextScheduleDate,
                weeklyDaysOfWeek = setOf(nextScheduleDate.dayOfWeek),
                times = listOf(LocalTime.of(8, 30))
            )
        )

        val upcomingSection = buildMainUpcomingSection(
            groups = listOf(group),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 5, 6, 11, 0)
        )

        assertEquals(MainUpcomingSectionTitle.UPCOMING, upcomingSection.title)
        assertEquals(nextScheduleDate, upcomingSection.anchorDate)
        assertEquals(
            listOf(LocalDateTime.of(2026, 5, 27, 8, 30)),
            upcomingSection.rows.map { it.scheduledAt }
        )
    }

    @Test
    fun buildMainUpcomingSection_includes_schedule_exactly_ninety_days_out() {
        val nextScheduleDate = LocalDate.of(2026, 8, 4)
        val group = medicationGroup(
            uuid = UUID.fromString("5262749b-4962-47c0-8c6f-71fe91e84e8d"),
            name = "Three month boundary",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.WEEKLY,
                interval = 1,
                since = nextScheduleDate,
                weeklyDaysOfWeek = setOf(nextScheduleDate.dayOfWeek),
                times = listOf(LocalTime.of(8, 30))
            )
        )

        val upcomingSection = buildMainUpcomingSection(
            groups = listOf(group),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 5, 6, 11, 0)
        )

        assertEquals(nextScheduleDate, upcomingSection.anchorDate)
        assertEquals(
            listOf(LocalDateTime.of(2026, 8, 4, 8, 30)),
            upcomingSection.rows.map { it.scheduledAt }
        )
    }

    @Test
    fun buildMainUpcomingSection_orders_future_planned_ties_by_groupCreation_andMedicationOrder() {
        val weeklyDate = LocalDate.of(2026, 4, 20)
        val schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.WEEKLY,
            interval = 1,
            since = LocalDate.of(2026, 4, 1),
            weeklyDaysOfWeek = setOf(weeklyDate.dayOfWeek),
            times = listOf(LocalTime.of(9, 0))
        )
        val olderGroupUuid = UUID.fromString("23ee70f4-9f60-4c86-a068-3ee679f75531")
        val newerGroupUuid = UUID.fromString("362e4541-18a0-419e-a012-16f21622d8a6")
        val olderFirstMedicationUuid = UUID.fromString("4da07ba4-ad0d-43af-8651-07f37f4dfed0")
        val olderSecondMedicationUuid = UUID.fromString("33f62c5a-f4c0-444e-86d8-d1cd4fd6cd6e")
        val newerMedicationUuid = UUID.fromString("ef2acf68-7a13-422c-882b-a0d7d56aac2f")
        val olderGroup = medicationGroup(
            uuid = olderGroupUuid,
            name = "Older weekly",
            schedule = schedule,
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = olderFirstMedicationUuid,
                    medicine = testMedicine(key = MedicationKey.SPIRONOLACTONE),
                    applicationType = MedicationApplicationType.ORAL,
                ),
                testMedicationGroupMedication(
                    uuid = olderSecondMedicationUuid,
                    medicine = testMedicine(key = MedicationKey.ESTRADIOL),
                    applicationType = MedicationApplicationType.ORAL,
                )
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z")
        )
        val newerGroup = medicationGroup(
            uuid = newerGroupUuid,
            name = "Newer weekly",
            schedule = schedule,
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = newerMedicationUuid,
                    medicine = testMedicine(key = MedicationKey.CYPROTERONE_ACETATE),
                    applicationType = MedicationApplicationType.ORAL,
                )
            ),
            createdAt = Instant.parse("2026-04-02T00:00:00Z")
        )

        val upcomingSection = buildMainUpcomingSection(
            groups = listOf(newerGroup, olderGroup),
            entries = emptyList(),
            now = LocalDateTime.of(2026, 4, 18, 11, 0)
        )

        assertEquals(MainUpcomingSectionTitle.UPCOMING, upcomingSection.title)
        assertEquals(
            listOf(olderGroupUuid, olderGroupUuid, newerGroupUuid),
            upcomingSection.rows.map { row -> row.groupUuid }
        )
        assertEquals(
            listOf(olderFirstMedicationUuid, olderSecondMedicationUuid, newerMedicationUuid),
            upcomingSection.rows.map { row -> row.medication.uuid }
        )
    }

    @Test
    fun buildMainUpcomingSection_excludes_future_slots_that_are_already_fulfilled() {
        val group = medicationGroup(
            uuid = UUID.fromString("ec938925-8b3c-46dd-9912-79244a13f813"),
            name = "Daily estradiol",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0))
            )
        )

        val upcomingSection = buildMainUpcomingSection(
            groups = listOf(group),
            entries = listOf(
                scheduledEntry(
                    groupUuid = group.uuid,
                    appliedAt = LocalDateTime.of(2026, 4, 19, 8, 5),
                    scheduledFor = LocalDateTime.of(2026, 4, 19, 8, 0)
                )
            ),
            now = LocalDateTime.of(2026, 4, 18, 11, 0)
        )

        assertEquals(MainUpcomingSectionTitle.UPCOMING, upcomingSection.title)
        assertEquals(LocalDate.of(2026, 4, 20), upcomingSection.anchorDate)
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 4, 20, 8, 0)
            ),
            upcomingSection.rows.map { it.scheduledAt }
        )
    }

    private fun medicationGroup(
        uuid: UUID,
        name: String,
        schedule: MedicationGroupSchedule,
        medications: List<com.mkx.hrttracker.model.medication.MedicationGroupMedication> = listOf(
            testMedicationGroupMedication(
                uuid = UUID.randomUUID(),
                medicine = defaultEstradiolMedicine,
                applicationType = MedicationApplicationType.ORAL,
            )
        ),
        createdAt: Instant = Instant.parse("2026-04-01T00:00:00Z")
    ): MedicationGroup {
        return MedicationGroup(
            uuid = uuid,
            name = name,
            colorKey = MedicationGroupColorKey.PLUM,
            schedule = schedule,
            medications = medications,
            createdAt = createdAt,
            updatedAt = createdAt
        )
    }

    private fun antiandrogenGroup(
        uuid: UUID,
        times: List<LocalTime>
    ): MedicationGroup {
        return medicationGroup(
            uuid = uuid,
            name = "Daily antiandrogen",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = times
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.randomUUID(),
                    medicine = testMedicine(key = MedicationKey.SPIRONOLACTONE),
                    applicationType = MedicationApplicationType.ORAL,
                )
            )
        )
    }

    private fun scheduledAntiandrogenEntry(
        group: MedicationGroup,
        appliedAt: LocalDateTime,
        scheduledFor: LocalDateTime
    ): MedicationLogEntry {
        return testMedicationLogEntry(
            uuid = UUID.randomUUID(),
            medicine = group.medications.single().medicine,
            applicationType = group.medications.single().applicationType,
            doseInstruction = group.medications.single().doseInstruction,
            equivalentE2Mg = null,
            sourceGroupUuid = group.uuid,
            appliedAt = testInstant(appliedAt),
            scheduledFor = scheduledFor
        )
    }

    private fun scheduledEntry(
        groupUuid: UUID,
        appliedAt: LocalDateTime,
        scheduledFor: LocalDateTime
    ): MedicationLogEntry {
        return testMedicationLogEntry(
            uuid = UUID.randomUUID(),
            medicine = defaultEstradiolMedicine,
            applicationType = MedicationApplicationType.ORAL,
            equivalentE2Mg = 2.0,
            sourceGroupUuid = groupUuid,
            appliedAt = testInstant(appliedAt),
            scheduledFor = scheduledFor
        )
    }

    private fun manualEntry(
        uuid: UUID = UUID.randomUUID(),
        appliedAt: LocalDateTime,
        count: Int = 1
    ): MedicationLogEntry {
        return testMedicationLogEntry(
            uuid = uuid,
            medicine = defaultEstradiolMedicine,
            applicationType = MedicationApplicationType.ORAL,
            equivalentE2Mg = 2.0,
            sourceGroupUuid = null,
            appliedAt = testInstant(appliedAt),
            count = count
        )
    }
}
