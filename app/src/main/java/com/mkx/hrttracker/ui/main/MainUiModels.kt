package com.mkx.hrttracker.ui.main

import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestCatalog
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.findLastEstradiolEntry
import com.mkx.hrttracker.model.medication.nextScheduledForAfter
import com.mkx.hrttracker.model.medication.occurrencesBetweenInPlanWindow
import com.mkx.hrttracker.model.medication.previousScheduledForBefore
import com.mkx.hrttracker.model.medication.scheduleFulfillmentAllowedOffset
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.pk.PkConcentrationUnit
import com.mkx.hrttracker.model.pk.PkTrendResult
import com.mkx.hrttracker.ui.calibration.calibrationUnitLabel
import com.mkx.hrttracker.ui.calibration.formatCalibrationConvertedValue
import com.mkx.hrttracker.model.medication.MedicationSignature
import com.mkx.hrttracker.model.medication.MedicationGroupSlotKey
import com.mkx.hrttracker.ui.plan.buildPlanDaySchedule
import com.mkx.hrttracker.model.medication.isSlotFulfilledForMedication
import com.mkx.hrttracker.util.appliedAtAsLocalDateTime
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToLong

data class MainTodaySectionUiState(
    val date: LocalDate,
    val doneCount: Int = 0,
    val totalCount: Int = 0,
    val manualCount: Int = 0,
    val rows: List<MainTodayDoseRowUiState> = emptyList(),
)

data class MainLastNightSectionUiState(
    val date: LocalDate? = null,
    val doneCount: Int = 0,
    val totalCount: Int = 0,
    val manualCount: Int = 0,
    val rows: List<MainTodayDoseRowUiState> = emptyList(),
)

data class MainE2HeroUiState(
    val currentValue: Double = 0.0,
    val changeSinceYesterday: Double = 0.0,
    val targetMin: Double = E2_TARGET_MIN_CANONICAL,
    val targetMax: Double = E2_TARGET_MAX_CANONICAL,
    val unit: String = E2_UNIT_PG_ML,
    val lastDoseDetails: MedicationDetails? = null,
    val lastDoseAt: LocalDateTime? = null,
)

data class MainE2ChartUiState(
    val points: List<Float> = EmptyE2ChartPoints,
    val pointXHours: List<Double> = EmptyE2ChartPointXHours,
    val sampleIntervalHours: Int = EmptyE2ChartSampleIntervalHours,
    val doseMarkers: List<MainE2DoseMarkerUiState> = emptyList(),
    val windowHours: Int = EmptyE2ChartWindowHours,
    val predictionStartXHours: Double = EmptyE2ChartPredictionStartXHours,
    val chartWindowOption: HomeE2ChartWindowOption = HomeE2ChartWindowOption.SEVEN_DAYS,
    val pastDays: Long = chartWindowOption.pastDays,
)

data class MainE2DoseMarkerUiState(
    val xHours: Double,
    val concentration: Float,
    val isPlanned: Boolean = false,
)

data class MainE2ChartYAxisSpec(
    val maxY: Double,
    val tickStep: Double,
)

data class MainE2SplitChartSeries(
    val observedXHours: List<Double>,
    val observedPoints: List<Float>,
    val predictedXHours: List<Double>,
    val predictedPoints: List<Float>,
)

data class MainAntiandrogenCardUiState(
    val id: String,
    val groupUuid: UUID,
    val groupName: String,
    val groupColorKey: MedicationGroupColorKey,
    val medication: MedicationGroupMedication,
    val lastDoseDetails: MedicationDetails? = null,
    val lastDoseAt: LocalDateTime? = null,
    val nextDoseAt: LocalDateTime? = null,
    val isNextDosePastDue: Boolean = false,
)

data class MainTodayDoseRowUiState(
    val groupUuid: UUID?,
    val groupName: String,
    val groupColorKey: MedicationGroupColorKey?,
    val scheduleTimeUuid: UUID?,
    val scheduledAt: LocalDateTime,
    val medication: MedicationGroupMedication,
    val status: MainTodayDoseStatus,
    val loggedAt: LocalDateTime? = null,
    val outsideScheduleWindowLoggedAt: LocalDateTime? = null,
    val fulfillingEntryUuids: List<UUID> = emptyList(),
    val outsideScheduleWindowEntryUuids: List<UUID> = emptyList(),
    val loggedCount: Int = 0,
    val isManualRecord: Boolean = false,
    val groupCreatedAt: Instant = Instant.EPOCH,
    val medicationSortOrder: Int = 0,
    val sourceGroupPreviousScheduledFor: LocalDateTime? = null,
    val sourceGroupNextScheduledFor: LocalDateTime? = null,
    val editSnapshotEntries: List<MedicationLogEntry> = emptyList(),
)

enum class MainTodayDoseStatus {
    DONE,
    DUE_SOON,
    UPCOMING,
    OVERDUE,
}

data class MainUpcomingSectionUiState(
    val title: MainUpcomingSectionTitle = MainUpcomingSectionTitle.TOMORROW,
    val anchorDate: LocalDate? = null,
    val rows: List<MainUpcomingDoseRowUiState> = emptyList(),
)

enum class MainUpcomingSectionTitle {
    TOMORROW,
    UPCOMING,
}

data class MainUpcomingDoseRowUiState(
    val groupUuid: UUID,
    val groupName: String,
    val groupColorKey: MedicationGroupColorKey,
    val scheduleTimeUuid: UUID?,
    val scheduledAt: LocalDateTime,
    val medication: MedicationGroupMedication,
)

data class MainQuickLogDoseRequest(
    val groupUuid: UUID,
    val scheduleTimeUuid: UUID?,
    val scheduledAt: LocalDateTime,
    val medicationDetails: MedicationDetails,
    val medicationCount: Int,
    val sourceGroupName: String,
    val sourceGroupColorKey: MedicationGroupColorKey?,
    val sourceGroupPreviousScheduledFor: LocalDateTime?,
    val sourceGroupNextScheduledFor: LocalDateTime?,
)

data class MainEditEntryRequest(
    val entryUuids: Set<UUID>,
    val snapshotEntries: List<MedicationLogEntry> = emptyList(),
    val sourceGroupName: String? = null,
    val sourceGroupColorKey: MedicationGroupColorKey? = null,
    val sourceGroupPreviousScheduledFor: LocalDateTime? = null,
    val sourceGroupNextScheduledFor: LocalDateTime? = null,
)

internal fun buildMainE2Hero(
    entries: List<MedicationLogEntry>,
    trendResult: PkTrendResult? = null,
    displayUnit: BloodUnitKey = BloodTestCatalog.canonicalUnitFor(BloodAnalyteKey.E2),
    zoneId: ZoneId = ZoneId.systemDefault()
): MainE2HeroUiState {
    val lastEstradiolEntry = findLastEstradiolEntry(entries)
    val concentrationUnit = trendResult?.concentrationUnit ?: PkConcentrationUnit.PG_PER_ML

    return MainE2HeroUiState(
        currentValue = trendResult
            ?.currentConcentration
            ?.let { concentration ->
                mainE2ConcentrationInDisplayUnit(
                    value = concentration,
                    sourceUnit = concentrationUnit,
                    displayUnit = displayUnit,
                )
            }
            ?: 0.0,
        changeSinceYesterday = trendResult
            ?.changeSincePreviousDay
            ?.let { concentration ->
                mainE2ConcentrationInDisplayUnit(
                    value = concentration,
                    sourceUnit = concentrationUnit,
                    displayUnit = displayUnit,
                )
            }
            ?: 0.0,
        targetMin = BloodTestCatalog.fromCanonical(
            analyteKey = BloodAnalyteKey.E2,
            canonicalValue = E2_TARGET_MIN_CANONICAL,
            unit = displayUnit,
        ),
        targetMax = BloodTestCatalog.fromCanonical(
            analyteKey = BloodAnalyteKey.E2,
            canonicalValue = E2_TARGET_MAX_CANONICAL,
            unit = displayUnit,
        ),
        unit = mainE2DisplayUnitLabel(displayUnit),
        lastDoseDetails = lastEstradiolEntry?.details,
        lastDoseAt = lastEstradiolEntry
            ?.appliedAt
            ?.atZone(zoneId)
            ?.toLocalDateTime()
    )
}

internal fun buildMainE2Chart(
    trendResult: PkTrendResult? = null,
    displayUnit: BloodUnitKey = BloodTestCatalog.canonicalUnitFor(BloodAnalyteKey.E2),
    chartWindowOption: HomeE2ChartWindowOption = HomeE2ChartWindowOption.SEVEN_DAYS,
): MainE2ChartUiState {
    val chartConcentrations = trendResult
        ?.chartConcentrations
        ?.takeIf { concentrations -> concentrations.isNotEmpty() }
    val chartTimeH = trendResult
        ?.chartTimeH
        ?.takeIf { timeH -> chartConcentrations != null && timeH.size == chartConcentrations.size }

    return MainE2ChartUiState(
        points = chartConcentrations
            ?.map { concentration ->
                mainE2ConcentrationInDisplayUnit(
                    value = concentration,
                    sourceUnit = trendResult.concentrationUnit,
                    displayUnit = displayUnit,
                ).toFloat()
            }
            ?: EmptyE2ChartPoints,
        pointXHours = chartTimeH
            ?.map { timeH -> timeH.toVicoXHour() }
            ?: EmptyE2ChartPointXHours,
        sampleIntervalHours = if (chartConcentrations != null) {
            trendResult.chartSampleIntervalHours.coerceAtLeast(1)
        } else {
            EmptyE2ChartSampleIntervalHours
        },
        doseMarkers = trendResult
            ?.doseMarkers
            ?.map { marker ->
                MainE2DoseMarkerUiState(
                    xHours = marker.timeH.toVicoXHour(),
                    concentration = mainE2ConcentrationInDisplayUnit(
                        value = marker.concentration,
                        sourceUnit = trendResult.concentrationUnit,
                        displayUnit = displayUnit,
                    ).toFloat(),
                    isPlanned = marker.isPlanned,
                )
            }
            ?: emptyList(),
        windowHours = trendResult?.chartWindowHours ?: EmptyE2ChartWindowHours,
        predictionStartXHours = trendResult
            ?.predictionStartTimeH
            ?.toVicoXHour()
            ?: EmptyE2ChartPredictionStartXHours,
        chartWindowOption = chartWindowOption,
        pastDays = chartWindowOption.pastDays,
    )
}

internal fun mainE2ChartWindowStart(
    now: LocalDateTime,
    pastDays: Long = MainE2ChartPastDays,
): LocalDateTime {
    return now.toLocalDate()
        .atStartOfDay()
        .minusDays(pastDays)
}

// One noon tick per day across the chart window. Used by the 7-day chart's
// fixed-density bottom axis (the 30-day chart uses the adaptive placer
// below).
internal fun mainE2ChartNoonTickHours(
    now: LocalDateTime,
    windowHours: Int,
    pastDays: Long = MainE2ChartPastDays,
): List<Double> {
    val resolvedWindowHours = windowHours.coerceAtLeast(1)
    val windowStart = mainE2ChartWindowStart(now, pastDays)
    val windowEnd = windowStart.plusHours(resolvedWindowHours.toLong())
    val endDate = windowEnd.toLocalDate()
    val tickHours = mutableListOf<Double>()
    var date = windowStart.toLocalDate()

    while (!date.isAfter(endDate)) {
        val noon = LocalDateTime.of(date, LocalTime.NOON)
        if (noon.isAfter(windowStart) && noon.isBefore(windowEnd)) {
            val hoursFromWindowStart = Duration.between(windowStart, noon).toMillis() / 3_600_000.0
            if (hoursFromWindowStart in 0.0..resolvedWindowHours.toDouble()) {
                tickHours += hoursFromWindowStart.toVicoXHour()
            }
        }
        date = date.plusDays(1L)
    }

    return tickHours.distinct()
}

// Adaptive bottom-axis tick interval, picked from the currently visible
// chart span. Max granularity is one day; the chart never shows sub-daily
// ticks even when pinch-zoomed past a one-day visible window.
internal fun mainE2ChartAxisTickIntervalDays(visibleHours: Double): Long {
    return when {
        visibleHours <= 168.0 -> 1L      // ≤ 7 d → daily
        visibleHours <= 336.0 -> 2L      // ≤ 14 d → every 2 days
        visibleHours <= 720.0 -> 6L      // ≤ 30 d → every 6 days
        else -> 7L                       // > 30 d → weekly
    }
}

// Noon-tick xHours (relative to chartWindowStart) at the given interval,
// clipped to the chart's [0, chartWindowHours] range and the visible x range.
// Anchored at chartWindowStart + interval/2 days so the tick series is centred
// in the window and the first tick falls near day 4 (not day 7). Coarser
// intervals remain supersets of finer ones (no jitter across zoom boundaries).
// Ticks on the first and last calendar day of the window are suppressed to
// avoid label clipping at the canvas edges.
internal fun mainE2ChartAxisLabelTickHours(
    chartWindowStart: LocalDateTime,
    chartWindowHours: Int,
    visibleXRange: ClosedFloatingPointRange<Double>,
    intervalDays: Long,
): List<Double> {
    val safeInterval = intervalDays.coerceAtLeast(1L)
    val resolvedWindowHours = chartWindowHours.coerceAtLeast(1)
    val windowEnd = chartWindowStart.plusHours(resolvedWindowHours.toLong())
    val endDate = windowEnd.toLocalDate()
    val firstDay = chartWindowStart.toLocalDate()
    val lastDay = windowEnd.toLocalDate().let { d ->
        if (windowEnd == d.atStartOfDay()) d.minusDays(1) else d
    }
    val tickHours = mutableListOf<Double>()
    var date = chartWindowStart.toLocalDate().plusDays(safeInterval / 2L)

    while (!date.isAfter(endDate)) {
        val noon = LocalDateTime.of(date, LocalTime.NOON)
        if (
            noon.isAfter(chartWindowStart) && noon.isBefore(windowEnd) &&
            date != firstDay && date != lastDay
        ) {
            val hoursFromWindowStart =
                Duration.between(chartWindowStart, noon).toMillis() / 3_600_000.0
            if (
                hoursFromWindowStart in 0.0..resolvedWindowHours.toDouble() &&
                hoursFromWindowStart in visibleXRange
            ) {
                tickHours += hoursFromWindowStart.toVicoXHour()
            }
        }
        date = date.plusDays(safeInterval)
    }

    return tickHours.distinct()
}

internal fun splitMainE2ChartSeries(
    xHours: List<Double>,
    points: List<Float>,
    observedEndXHours: Double,
    predictedStartXHours: Double,
): MainE2SplitChartSeries {
    val paired = xHours
        .zip(points)
        .filter { (x, y) -> x.isFinite() && y.isFinite() }
        .distinctBy { (x, _) -> x }
        .sortedBy { (x, _) -> x }
    if (paired.isEmpty()) {
        return MainE2SplitChartSeries(
            observedXHours = emptyList(),
            observedPoints = emptyList(),
            predictedXHours = emptyList(),
            predictedPoints = emptyList(),
        )
    }

    val observedEnd = observedEndXHours.toVicoXHour()
    val predictedStart = predictedStartXHours.toVicoXHour()

    val observedEndPoint = paired.exactOrInterpolatedPointAt(observedEnd)
    val observed = buildList {
        addAll(paired.filter { it.first <= observedEnd })
        if (observedEndPoint != null && none { it.first == observedEnd }) {
            add(observedEndPoint)
        }
    }.sortedBy { it.first }

    val predictedStartPoint = paired.exactOrInterpolatedPointAt(predictedStart)
    val predicted = buildList {
        if (predictedStartPoint != null) {
            add(predictedStartPoint)
        }
        addAll(paired.filter { it.first > predictedStart })
    }.distinctBy { it.first }.sortedBy { it.first }

    return MainE2SplitChartSeries(
        observedXHours = observed.map { it.first },
        observedPoints = observed.map { it.second },
        predictedXHours = predicted.map { it.first },
        predictedPoints = predicted.map { it.second },
    )
}

internal fun mainE2ChartYAxisSpec(
    points: List<Float>,
    doseMarkers: List<MainE2DoseMarkerUiState> = emptyList(),
): MainE2ChartYAxisSpec {
    val rawMax = (
        points.asSequence().map(Float::toDouble) +
            doseMarkers.asSequence().map { marker -> marker.concentration.toDouble() }
        )
        .filter { value -> value.isFinite() && value > 0.0 }
        .maxOrNull()
        ?: 0.0
    // Headroom for Catmull-Rom overshoot between sample points; otherwise a
    // sample flush with the top tick clips the rendered curve and minimap.
    val maxValue = (rawMax * MainE2YAxisHeadroomMultiplier).coerceAtLeast(1.0)
    val candidates = MainE2YAxisTickSteps.filter { candidate ->
        ceil(maxValue / candidate) <= MainE2YAxisMaxTickIntervals
    }
    val step = if (candidates.isEmpty()) {
        fallbackMainE2YAxisStep(maxValue)
    } else {
        // Among ties on maxY, prefer the largest step so tick labels stay clean
        // (e.g., 0/25/50/75/100 over 0/20/40/60/80/100), but keep enough
        // intervals that the axis doesn't collapse to 0/100.
        val smallestMaxY = candidates.minOf { ceil(maxValue / it) * it }
        val tied = candidates.filter { ceil(maxValue / it) * it == smallestMaxY }
        tied.filter { ceil(maxValue / it) >= MainE2YAxisMinPreferredTickIntervals }
            .maxOrNull()
            ?: tied.min()
    }
    val maxY = ceil(maxValue / step)
        .coerceAtLeast(1.0) * step

    return MainE2ChartYAxisSpec(
        maxY = maxY,
        tickStep = step,
    )
}

internal fun buildMainAntiandrogenCards(
    groups: List<MedicationGroup>,
    entries: List<MedicationLogEntry>,
    now: LocalDateTime,
    zoneId: ZoneId = ZoneId.systemDefault()
): List<MainAntiandrogenCardUiState> {
    return groups.sortedBy { it.createdAt }.flatMap { group ->
        group.medications
            .filter { medication -> medication.category == MedicationCategory.ANTIANDROGEN }
            .groupBy(MedicationSignature::fromGroupMedication)
            .map { (_, medicationsForSignature) ->
                val medication = medicationsForSignature.first().copy(
                    count = medicationsForSignature.sumOf { it.count }
                )
                val nextDose = group.nextMainAntiandrogenDueSlot(
                    medication = medication,
                    entries = entries,
                    now = now,
                    zoneId = zoneId,
                )
                val lastMatchingEntry = entries
                    .asSequence()
                    .filter { entry ->
                        entry.category == MedicationCategory.ANTIANDROGEN &&
                            (entry.sourceGroupUuid == null || entry.sourceGroupUuid == group.uuid) &&
                            entry.details.isSameMedicationTrackingIdentity(medication.details)
                    }
                    .maxByOrNull { entry -> entry.appliedAt }

                MainAntiandrogenCardUiState(
                    id = "${group.uuid}:${medication.uuid}",
                    groupUuid = group.uuid,
                    groupName = group.name,
                    groupColorKey = group.colorKey,
                    medication = medication,
                    lastDoseDetails = lastMatchingEntry?.details,
                    lastDoseAt = lastMatchingEntry
                        ?.appliedAt
                        ?.atZone(zoneId)
                        ?.toLocalDateTime(),
                    nextDoseAt = nextDose?.scheduledAt,
                    isNextDosePastDue = nextDose?.isPastDue == true,
                )
            }
    }
}

internal fun buildMainTodaySection(
    groups: List<MedicationGroup>,
    entries: List<MedicationLogEntry>,
    now: LocalDateTime,
    zoneId: ZoneId = ZoneId.systemDefault()
): MainTodaySectionUiState {
    val today = now.toLocalDate()
    val entriesByUuid = entries.associateBy { it.uuid }
    val todayRows = buildMainTodayRowsForDate(
        date = today,
        groups = groups,
        entries = entries,
        entriesByUuid = entriesByUuid,
        now = now,
        zoneId = zoneId,
    )
    val rows = (todayRows.scheduledRows + todayRows.manualRows)
        .sortedWith(mainTodayDoseRowComparator)

    return MainTodaySectionUiState(
        date = today,
        doneCount = todayRows.scheduledRows.count { it.status == MainTodayDoseStatus.DONE },
        totalCount = todayRows.scheduledRows.size,
        manualCount = todayRows.manualRows.size,
        rows = rows
    )
}

internal fun buildMainLastNightSection(
    groups: List<MedicationGroup>,
    entries: List<MedicationLogEntry>,
    now: LocalDateTime,
    zoneId: ZoneId = ZoneId.systemDefault()
): MainLastNightSectionUiState {
    if (!mainIsOvernightTime(now.toLocalTime())) {
        return MainLastNightSectionUiState()
    }
    val today = now.toLocalDate()
    val previousDate = today.minusDays(1)
    val entriesByUuid = entries.associateBy { it.uuid }
    val lastNightRows = buildMainTodayRowsForDate(
        date = previousDate,
        groups = groups,
        entries = entries,
        entriesByUuid = entriesByUuid,
        now = now,
        zoneId = zoneId,
    ).filter { row -> mainIsLastNightTime(row.scheduledAt.toLocalTime()) }
    val rows = (lastNightRows.scheduledRows + lastNightRows.manualRows)
        .sortedWith(mainTodayDoseRowComparator)

    return MainLastNightSectionUiState(
        date = previousDate,
        doneCount = lastNightRows.scheduledRows.count { it.status == MainTodayDoseStatus.DONE },
        totalCount = lastNightRows.scheduledRows.size,
        manualCount = lastNightRows.manualRows.size,
        rows = rows
    )
}

internal fun formatMainE2ConcentrationValue(
    value: Double,
    displayUnit: BloodUnitKey,
): String {
    return when (displayUnit) {
        BloodUnitKey.PG_ML,
        BloodUnitKey.PMOL_L,
        -> value.roundToLong().toString()

        BloodUnitKey.NG_DL -> {
            val roundedValue = (value * 10.0).roundToLong() / 10.0
            String.format(Locale.US, "%.1f", roundedValue)
        }

        else -> formatCalibrationConvertedValue(value)
    }
}

internal fun formatMainE2TrendDeltaValue(
    changeSinceYesterday: Double,
    displayUnit: BloodUnitKey,
): String {
    val absoluteValueLabel = formatMainE2ConcentrationValue(abs(changeSinceYesterday), displayUnit)
    val sign = if (isMainE2TrendDeltaDisplayZero(changeSinceYesterday, displayUnit) ||
        changeSinceYesterday >= 0.0
    ) {
        "+"
    } else {
        "-"
    }
    return "$sign$absoluteValueLabel"
}

internal fun isMainE2TrendDeltaDisplayZero(
    changeSinceYesterday: Double,
    displayUnit: BloodUnitKey,
): Boolean {
    return formatMainE2ConcentrationValue(abs(changeSinceYesterday), displayUnit) ==
        formatMainE2ConcentrationValue(0.0, displayUnit)
}

private fun mainE2DisplayUnitLabel(displayUnit: BloodUnitKey): String {
    return calibrationUnitLabel(displayUnit)
}

private fun mainE2ConcentrationInDisplayUnit(
    value: Double,
    sourceUnit: PkConcentrationUnit,
    displayUnit: BloodUnitKey,
): Double {
    val canonicalValue = mainE2ConcentrationToCanonical(
        value = value,
        sourceUnit = sourceUnit,
    )
    return BloodTestCatalog.fromCanonical(
        analyteKey = BloodAnalyteKey.E2,
        canonicalValue = canonicalValue,
        unit = displayUnit,
    )
}

private fun mainE2ConcentrationToCanonical(
    value: Double,
    sourceUnit: PkConcentrationUnit,
): Double {
    return when (sourceUnit) {
        PkConcentrationUnit.PG_PER_ML -> value
        PkConcentrationUnit.PMOL_PER_L -> BloodTestCatalog.toCanonical(
            analyteKey = BloodAnalyteKey.E2,
            value = value,
            unit = BloodUnitKey.PMOL_L,
        )
        PkConcentrationUnit.NG_PER_DL -> BloodTestCatalog.toCanonical(
            analyteKey = BloodAnalyteKey.E2,
            value = value,
            unit = BloodUnitKey.NG_DL,
        )
        PkConcentrationUnit.NG_PER_ML -> value * 1_000.0
        PkConcentrationUnit.NMOL_PER_L -> BloodTestCatalog.toCanonical(
            analyteKey = BloodAnalyteKey.E2,
            value = value * 1_000.0,
            unit = BloodUnitKey.PMOL_L,
        )
    }
}

private data class MainTodayRowsForDate(
    val scheduledRows: List<MainTodayDoseRowUiState> = emptyList(),
    val manualRows: List<MainTodayDoseRowUiState> = emptyList(),
) {
    fun filter(predicate: (MainTodayDoseRowUiState) -> Boolean): MainTodayRowsForDate {
        return MainTodayRowsForDate(
            scheduledRows = scheduledRows.filter(predicate),
            manualRows = manualRows.filter(predicate),
        )
    }
}

private fun buildMainTodayRowsForDate(
    date: LocalDate,
    groups: List<MedicationGroup>,
    entries: List<MedicationLogEntry>,
    entriesByUuid: Map<UUID, MedicationLogEntry>,
    now: LocalDateTime,
    zoneId: ZoneId,
): MainTodayRowsForDate {
    val daySchedule = buildPlanDaySchedule(
        date = date,
        groups = groups,
        entries = entries,
        now = now,
        zoneId = zoneId
    )
    val groupsByUuid = groups.associateBy { group -> group.uuid }

    val scheduledRows = daySchedule.scheduledEntries.map { scheduledEntry ->
        val scheduledAt = scheduledEntry.scheduledFor
        val group = groupsByUuid[scheduledEntry.groupUuid]
        val fulfillingEntries = scheduledEntry.fulfillingEntryUuids
            .mapNotNull { entriesByUuid[it] }
            .sortedBy { it.appliedAt }
        val outsideScheduleWindowEntries = scheduledEntry.outsideScheduleWindowEntryUuids
            .mapNotNull { entriesByUuid[it] }
            .sortedBy { it.appliedAt }
        val editSnapshotEntries = (fulfillingEntries + outsideScheduleWindowEntries)
            .distinctBy(MedicationLogEntry::uuid)
        val lastFulfillingEntry = fulfillingEntries.lastOrNull()

        MainTodayDoseRowUiState(
            groupUuid = scheduledEntry.groupUuid,
            groupName = scheduledEntry.groupName,
            groupColorKey = scheduledEntry.groupColorKey,
            scheduleTimeUuid = scheduledEntry.scheduleTimeUuid,
            scheduledAt = scheduledAt,
            sourceGroupPreviousScheduledFor = group?.previousScheduledForBefore(
                scheduledFor = scheduledAt,
                zoneId = zoneId,
            ),
            sourceGroupNextScheduledFor = group?.nextScheduledForAfter(
                scheduledFor = scheduledAt,
                zoneId = zoneId,
            ),
            medication = scheduledEntry.medication,
            status = when {
                scheduledEntry.isFulfilled -> MainTodayDoseStatus.DONE
                scheduledEntry.isDueSoon -> MainTodayDoseStatus.DUE_SOON
                scheduledAt.isBefore(now) -> MainTodayDoseStatus.OVERDUE
                else -> MainTodayDoseStatus.UPCOMING
            },
            loggedAt = lastFulfillingEntry?.let { appliedAtAsLocalDateTime(it, zoneId) },
            outsideScheduleWindowLoggedAt = scheduledEntry.outsideScheduleWindowLoggedAt,
            fulfillingEntryUuids = fulfillingEntries.map { it.uuid },
            outsideScheduleWindowEntryUuids = scheduledEntry.outsideScheduleWindowEntryUuids,
            loggedCount = scheduledEntry.loggedCount,
            groupCreatedAt = scheduledEntry.groupCreatedAt,
            medicationSortOrder = scheduledEntry.medicationSortOrder,
            editSnapshotEntries = editSnapshotEntries,
        )
    }
    val manualRows = daySchedule.unplannedEntries.map { entry ->
        val appliedAt = entry.appliedAt
            .atZone(zoneId)
            .toLocalDateTime()

        MainTodayDoseRowUiState(
            groupUuid = null,
            groupName = "",
            groupColorKey = null,
            scheduleTimeUuid = null,
            scheduledAt = appliedAt,
            medication = MedicationGroupMedication(
                uuid = entry.uuid,
                details = entry.details,
                count = entry.count
            ),
            status = MainTodayDoseStatus.DONE,
            loggedAt = appliedAt,
            fulfillingEntryUuids = listOf(entry.uuid),
            loggedCount = entry.count,
            isManualRecord = true,
            editSnapshotEntries = listOf(entry),
        )
    }

    return MainTodayRowsForDate(
        scheduledRows = scheduledRows,
        manualRows = manualRows,
    )
}

private fun mainIsOvernightTime(time: LocalTime): Boolean {
    return time.isBefore(MainOvernightEndTime)
}

private fun mainIsLastNightTime(time: LocalTime): Boolean {
    return !time.isBefore(MainLastNightStartTime)
}

private val mainTodayDoseRowComparator = compareBy<MainTodayDoseRowUiState> { row -> row.scheduledAt }
    .thenBy { row -> if (row.isManualRecord) 1 else 0 }
    .thenBy { row -> row.groupCreatedAt }
    .thenBy { row -> row.medicationSortOrder }

internal fun buildMainUpcomingSection(
    groups: List<MedicationGroup>,
    entries: List<MedicationLogEntry>,
    now: LocalDateTime,
    lookaheadDays: Long = MainUpcomingLookaheadDays,
    zoneId: ZoneId = ZoneId.systemDefault(),
): MainUpcomingSectionUiState {
    val tomorrow = now.toLocalDate().plusDays(1)
    val tomorrowRows = buildMainPreviewRowsForDate(
        date = tomorrow,
        groups = groups,
        entries = entries,
        zoneId = zoneId
    )

    if (tomorrowRows.isNotEmpty()) {
        return MainUpcomingSectionUiState(
            title = MainUpcomingSectionTitle.TOMORROW,
            anchorDate = tomorrow,
            rows = tomorrowRows
        )
    }

    val lastLookaheadDate = now.toLocalDate().plusDays(lookaheadDays)
    var date = tomorrow.plusDays(1)
    while (!date.isAfter(lastLookaheadDate)) {
        val upcomingRows = buildMainPreviewRowsForDate(
            date = date,
            groups = groups,
            entries = entries,
            zoneId = zoneId
        )
        if (upcomingRows.isNotEmpty()) {
            return MainUpcomingSectionUiState(
                title = MainUpcomingSectionTitle.UPCOMING,
                anchorDate = date,
                rows = upcomingRows
            )
        }
        date = date.plusDays(1)
    }

    return MainUpcomingSectionUiState(
        title = MainUpcomingSectionTitle.UPCOMING,
        anchorDate = null,
        rows = emptyList()
    )
}

internal fun buildMainPreviewRowsForDate(
    date: LocalDate,
    groups: List<MedicationGroup>,
    entries: List<MedicationLogEntry>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<MainUpcomingDoseRowUiState> {
    return buildPlanDaySchedule(
        date = date,
        groups = groups,
        entries = entries,
        now = date.atStartOfDay(),
        zoneId = zoneId,
    ).scheduledEntries
        .asSequence()
        .filterNot { it.isFulfilled }
        .map { scheduledEntry ->
            MainUpcomingDoseRowUiState(
                groupUuid = scheduledEntry.groupUuid,
                groupName = scheduledEntry.groupName,
                groupColorKey = scheduledEntry.groupColorKey,
                scheduleTimeUuid = scheduledEntry.scheduleTimeUuid,
                scheduledAt = scheduledEntry.scheduledFor,
                medication = scheduledEntry.medication
            )
        }
        .toList()
}

private fun MedicationDetails.isSameMedicationTrackingIdentity(other: MedicationDetails): Boolean {
    return category == other.category &&
        applicationType == other.applicationType &&
        selection == other.selection
}

private data class MainAntiandrogenDueSlot(
    val scheduledAt: LocalDateTime,
    val isPastDue: Boolean,
)

private fun MedicationGroup.nextMainAntiandrogenDueSlot(
    medication: MedicationGroupMedication,
    entries: List<MedicationLogEntry>,
    now: LocalDateTime,
    zoneId: ZoneId,
): MainAntiandrogenDueSlot? {
    return occurrencesBetweenInPlanWindow(
        startDate = now.toLocalDate().minusDays(MainAntiandrogenDueLookbackDays),
        endDate = now.toLocalDate().plusDays(MainAntiandrogenDueLookaheadDays),
        zoneId = zoneId,
    )
        .asSequence()
        .firstNotNullOfOrNull { occurrence ->
            val scheduledAt = occurrence.scheduledFor
            val slot = MedicationGroupSlotKey(
                scheduleTimeUuid = occurrence.scheduleTimeUuid,
                scheduledFor = scheduledAt,
            )
            val isFulfilled = isSlotFulfilledForMedication(
                group = this,
                slot = slot,
                medication = medication,
                entries = entries,
                zoneId = zoneId,
            )
            if (isFulfilled) {
                return@firstNotNullOfOrNull null
            }

            if (now.isBefore(scheduledAt.plus(MainAntiandrogenDisplayGracePeriod))) {
                MainAntiandrogenDueSlot(
                    scheduledAt = scheduledAt,
                    isPastDue = false,
                )
            } else {
                val pastDueWindow = scheduleFulfillmentAllowedOffset(
                    scheduledFor = scheduledAt,
                    adjacentScheduledFor = nextScheduledForAfter(
                        scheduledFor = scheduledAt,
                        zoneId = zoneId,
                    ),
                )
                if (!now.isAfter(scheduledAt.plus(pastDueWindow))) {
                    MainAntiandrogenDueSlot(
                        scheduledAt = scheduledAt,
                        isPastDue = true,
                    )
                } else {
                    null
                }
            }
        }
}

private fun Double.toVicoXHour(): Double {
    return (this * VicoXPrecisionScale).roundToLong() / VicoXPrecisionScale
}

private const val E2_TARGET_MIN_CANONICAL = 100.0
private const val E2_TARGET_MAX_CANONICAL = 200.0
private const val E2_UNIT_PG_ML = "pg/mL"
private const val MainUpcomingLookaheadDays = 90L
private const val MainAntiandrogenDueLookbackDays = 2L
private const val MainAntiandrogenDueLookaheadDays = 90L
private val MainAntiandrogenDisplayGracePeriod = Duration.ofHours(1)
private val MainOvernightEndTime = LocalTime.of(6, 0)
private val MainLastNightStartTime = LocalTime.of(18, 0)
private const val VicoXPrecisionScale = 10_000.0
internal const val MainE2ChartPastDays = 3L
private const val MainE2YAxisMaxTickIntervals = 5
private const val MainE2YAxisMinPreferredTickIntervals = 3
private const val MainE2YAxisHeadroomMultiplier = 1.05
private const val EmptyE2ChartSampleIntervalHours = 24
private const val EmptyE2ChartWindowHours = 7 * 24
private const val EmptyE2ChartPredictionStartXHours = 3 * 24.0
private val MainE2YAxisTickSteps = listOf(
    0.5,
    1.0,
    2.0,
    5.0,
    10.0,
    15.0,
    20.0,
    25.0,
    50.0,
    100.0,
    150.0,
    200.0,
    250.0,
    500.0,
    1000.0,
    1500.0,
    2000.0,
    2500.0,
    5000.0
)
private val EmptyE2ChartPoints = List(7) { 0f }
private val EmptyE2ChartPointXHours = List(7) { index ->
    index * EmptyE2ChartWindowHours.toDouble() / (EmptyE2ChartPoints.size - 1)
}

private fun fallbackMainE2YAxisStep(maxValue: Double): Double {
    var step = MainE2YAxisTickSteps.last()
    while (ceil(maxValue / step) > MainE2YAxisMaxTickIntervals) {
        step *= 2.0
    }
    return step
}

private fun List<Pair<Double, Float>>.exactOrInterpolatedPointAt(
    x: Double,
): Pair<Double, Float>? {
    firstOrNull { (candidateX, _) -> candidateX == x }?.let { return it }
    val previous = lastOrNull { (candidateX, _) -> candidateX < x }
    val next = firstOrNull { (candidateX, _) -> candidateX > x }

    return when {
        previous != null && next != null -> {
            val (x0, y0) = previous
            val (x1, y1) = next
            val ratio = (x - x0) / (x1 - x0)
            x to (y0 + (y1 - y0) * ratio).toFloat()
        }
        previous != null && x == previous.first -> previous
        next != null && x == next.first -> next
        else -> null
    }
}

// ── Widget dose row deep link ──────────────────────────────────────────────────

sealed interface DoseRowHighlightKey {
    data class Scheduled(
        val groupUuid: UUID,
        val scheduleTimeUuid: UUID?,
        val scheduledAt: LocalDateTime,
        val medicationUuid: UUID?,
    ) : DoseRowHighlightKey

    data class Manual(
        val entryUuid: UUID,
    ) : DoseRowHighlightKey
}

internal fun DoseRowHighlightKey.matches(row: MainTodayDoseRowUiState): Boolean =
    when (this) {
        is DoseRowHighlightKey.Scheduled ->
            row.groupUuid == groupUuid &&
                row.scheduleTimeUuid == scheduleTimeUuid &&
                row.scheduledAt == scheduledAt &&
                (medicationUuid == null || row.medication.uuid == medicationUuid)

        is DoseRowHighlightKey.Manual ->
            row.isManualRecord && entryUuid in row.fulfillingEntryUuids
    }

internal fun DoseRowHighlightKey.matches(row: MainUpcomingDoseRowUiState): Boolean =
    this is DoseRowHighlightKey.Scheduled &&
        row.groupUuid == groupUuid &&
        row.scheduleTimeUuid == scheduleTimeUuid &&
        row.scheduledAt == scheduledAt &&
        (medicationUuid == null || row.medication.uuid == medicationUuid)
