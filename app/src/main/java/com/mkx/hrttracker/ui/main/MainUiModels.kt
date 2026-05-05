package com.mkx.hrttracker.ui.main

import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.findLastEstradiolEntry
import com.mkx.hrttracker.model.medication.nextScheduledForAfter
import com.mkx.hrttracker.model.medication.occurrencesBetweenInPlanWindow
import com.mkx.hrttracker.model.medication.scheduleFulfillmentAllowedOffset
import com.mkx.hrttracker.model.pk.PkTrendResult
import com.mkx.hrttracker.ui.plan.MedicationSignature
import com.mkx.hrttracker.ui.plan.PlanScheduleTimeSlot
import com.mkx.hrttracker.ui.plan.buildPlanDaySchedule
import com.mkx.hrttracker.ui.plan.isEntryFulfillingPlanSlot
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.roundToLong
import kotlin.math.roundToInt

data class MainTodaySectionUiState(
    val date: LocalDate,
    val doneCount: Int = 0,
    val totalCount: Int = 0,
    val manualCount: Int = 0,
    val rows: List<MainTodayDoseRowUiState> = emptyList(),
)

data class MainE2HeroUiState(
    val currentValue: Int = 0,
    val changeSinceYesterday: Int = 0,
    val targetMin: Int = MOCK_E2_TARGET_MIN,
    val targetMax: Int = MOCK_E2_TARGET_MAX,
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
)

data class MainE2DoseMarkerUiState(
    val xHours: Double,
    val concentration: Float,
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

internal fun buildMainE2Hero(
    entries: List<MedicationLogEntry>,
    trendResult: PkTrendResult? = null,
    zoneId: ZoneId = ZoneId.systemDefault()
): MainE2HeroUiState {
    val lastEstradiolEntry = findLastEstradiolEntry(entries)

    return MainE2HeroUiState(
        currentValue = trendResult?.currentConcentration?.roundToInt() ?: 0,
        changeSinceYesterday = trendResult?.changeSincePreviousDay?.roundToInt() ?: 0,
        unit = trendResult?.concentrationUnit?.symbol ?: E2_UNIT_PG_ML,
        lastDoseDetails = lastEstradiolEntry?.details,
        lastDoseAt = lastEstradiolEntry
            ?.appliedAt
            ?.atZone(zoneId)
            ?.toLocalDateTime()
    )
}

internal fun buildMainE2Chart(
    trendResult: PkTrendResult? = null,
): MainE2ChartUiState {
    val chartConcentrations = trendResult
        ?.chartConcentrations
        ?.takeIf { concentrations -> concentrations.isNotEmpty() }
    val chartTimeH = trendResult
        ?.chartTimeH
        ?.takeIf { timeH -> chartConcentrations != null && timeH.size == chartConcentrations.size }

    return MainE2ChartUiState(
        points = chartConcentrations
            ?.map { concentration -> concentration.toFloat() }
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
                    concentration = marker.concentration.toFloat(),
                )
            }
            ?: emptyList(),
        windowHours = trendResult?.chartWindowHours ?: EmptyE2ChartWindowHours,
        predictionStartXHours = trendResult
            ?.predictionStartTimeH
            ?.toVicoXHour()
            ?: EmptyE2ChartPredictionStartXHours,
    )
}

internal fun mainE2ChartWindowStart(now: LocalDateTime): LocalDateTime {
    return now.toLocalDate()
        .atStartOfDay()
        .minusDays(MainE2ChartPastDays)
}

internal fun mainE2ChartNoonTickHours(
    now: LocalDateTime,
    windowHours: Int,
): List<Double> {
    val resolvedWindowHours = windowHours.coerceAtLeast(1)
    val windowStart = mainE2ChartWindowStart(now)
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
        date = date.plusDays(1)
    }

    return tickHours.distinct()
}

internal fun splitMainE2ChartSeries(
    xHours: List<Double>,
    points: List<Float>,
    predictionStartXHours: Double,
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

    val splitX = predictionStartXHours.toVicoXHour()
    val splitPoint = paired.exactOrInterpolatedPointAt(splitX)
    val observed = buildList {
        addAll(paired.filter { it.first <= splitX })
        if (splitPoint != null && none { it.first == splitX }) {
            add(splitPoint)
        }
    }.sortedBy { it.first }
    val predicted = buildList {
        if (splitPoint != null) {
            add(splitPoint)
        }
        addAll(paired.filter { it.first > splitX })
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
    val maxValue = (
        points.asSequence().map(Float::toDouble) +
            doseMarkers.asSequence().map { marker -> marker.concentration.toDouble() }
        )
        .filter { value -> value.isFinite() && value > 0.0 }
        .maxOrNull()
        ?: 0.0
    val step = MainE2YAxisTickSteps.firstOrNull { candidate ->
        ceil(maxValue.coerceAtLeast(1.0) / candidate) <= MainE2YAxisMaxTickIntervals
    } ?: fallbackMainE2YAxisStep(maxValue)
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
    val daySchedule = buildPlanDaySchedule(
        date = today,
        groups = groups,
        entries = entries,
        now = now,
        zoneId = zoneId
    )

    val entriesByUuid = entries.associateBy { it.uuid }
    val scheduledRows = daySchedule.scheduledEntries.map { scheduledEntry ->
        val scheduledAt = scheduledEntry.scheduledFor
        val fulfillingEntries = scheduledEntry.fulfillingEntryUuids
            .mapNotNull { entriesByUuid[it] }
            .sortedBy { it.appliedAt }

        MainTodayDoseRowUiState(
            groupUuid = scheduledEntry.groupUuid,
            groupName = scheduledEntry.groupName,
            groupColorKey = scheduledEntry.groupColorKey,
            scheduleTimeUuid = scheduledEntry.scheduleTimeUuid,
            scheduledAt = scheduledAt,
            medication = scheduledEntry.medication,
            status = when {
                scheduledEntry.isFulfilled -> MainTodayDoseStatus.DONE
                scheduledEntry.isDueSoon -> MainTodayDoseStatus.DUE_SOON
                scheduledAt.isBefore(now) -> MainTodayDoseStatus.OVERDUE
                else -> MainTodayDoseStatus.UPCOMING
            },
            loggedAt = fulfillingEntries.lastOrNull()
                ?.appliedAt
                ?.atZone(zoneId)
                ?.toLocalDateTime(),
            outsideScheduleWindowLoggedAt = scheduledEntry.outsideScheduleWindowLoggedAt,
            fulfillingEntryUuids = fulfillingEntries.map { it.uuid },
            outsideScheduleWindowEntryUuids = scheduledEntry.outsideScheduleWindowEntryUuids,
            loggedCount = scheduledEntry.loggedCount,
            groupCreatedAt = scheduledEntry.groupCreatedAt,
            medicationSortOrder = scheduledEntry.medicationSortOrder
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
            isManualRecord = true
        )
    }
    val rows = (scheduledRows + manualRows).sortedWith(mainTodayDoseRowComparator)

    return MainTodaySectionUiState(
        date = today,
        doneCount = scheduledRows.count { it.status == MainTodayDoseStatus.DONE },
        totalCount = scheduledRows.size,
        manualCount = manualRows.size,
        rows = rows
    )
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
            val slot = PlanScheduleTimeSlot(
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

            if (!now.isAfter(scheduledAt.plus(MainAntiandrogenDisplayGracePeriod))) {
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

private fun isSlotFulfilledForMedication(
    group: MedicationGroup,
    slot: PlanScheduleTimeSlot,
    medication: MedicationGroupMedication,
    entries: List<MedicationLogEntry>,
    zoneId: ZoneId,
): Boolean {
    val signature = MedicationSignature.fromGroupMedication(medication)
    val loggedCount = entries
        .asSequence()
        .filter { entry -> MedicationSignature.fromLogEntry(entry) == signature }
        .filter { entry ->
            isEntryFulfillingPlanSlot(
                group = group,
                slot = slot,
                entry = entry,
                zoneId = zoneId,
            )
        }
        .sumOf { entry -> entry.count }

    return loggedCount >= medication.count
}

private fun Double.toVicoXHour(): Double {
    return (this * VicoXPrecisionScale).roundToLong() / VicoXPrecisionScale
}

private const val MOCK_E2_TARGET_MIN = 100
private const val MOCK_E2_TARGET_MAX = 200
private const val E2_UNIT_PG_ML = "pg/mL"
private const val MainUpcomingLookaheadDays = 90L
private const val MainAntiandrogenDueLookbackDays = 2L
private const val MainAntiandrogenDueLookaheadDays = 90L
private val MainAntiandrogenDisplayGracePeriod = Duration.ofHours(1)
private const val VicoXPrecisionScale = 10_000.0
private const val MainE2ChartPastDays = 3L
private const val MainE2YAxisMaxTickIntervals = 5
private const val EmptyE2ChartSampleIntervalHours = 24
private const val EmptyE2ChartWindowHours = 7 * 24
private const val EmptyE2ChartPredictionStartXHours = 3 * 24.0
private val MainE2YAxisTickSteps = listOf(25.0, 50.0, 100.0, 250.0, 500.0, 1000.0, 2500.0, 5000.0)
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
