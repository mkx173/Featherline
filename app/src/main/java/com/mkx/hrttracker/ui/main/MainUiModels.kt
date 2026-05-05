package com.mkx.hrttracker.ui.main

import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.findLastEstradiolEntry
import com.mkx.hrttracker.model.medication.nextOccurrencesInPlanWindowFrom
import com.mkx.hrttracker.model.medication.occurrencesBetweenInPlanWindow
import com.mkx.hrttracker.ui.plan.MedicationSignature
import com.mkx.hrttracker.ui.plan.PlanScheduleTimeSlot
import com.mkx.hrttracker.ui.plan.buildPlanDaySchedule
import com.mkx.hrttracker.ui.plan.isSlotFulfilled
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

data class MainTodaySectionUiState(
    val date: LocalDate,
    val doneCount: Int = 0,
    val totalCount: Int = 0,
    val rows: List<MainTodayDoseRowUiState> = emptyList(),
)

data class MainE2HeroUiState(
    val currentValue: Int = MOCK_E2_CURRENT_VALUE,
    val changeSinceYesterday: Int = MOCK_E2_CHANGE_SINCE_YESTERDAY,
    val targetMin: Int = MOCK_E2_TARGET_MIN,
    val targetMax: Int = MOCK_E2_TARGET_MAX,
    val unit: String = E2_UNIT_PG_ML,
    val lastDoseDetails: MedicationDetails? = null,
    val lastDoseAt: LocalDateTime? = null,
)

data class MainE2ChartUiState(
    val points: List<Float> = MOCK_E2_CHART_POINTS,
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
)

data class MainTodayDoseRowUiState(
    val groupUuid: UUID,
    val groupName: String,
    val groupColorKey: MedicationGroupColorKey,
    val scheduleTimeUuid: UUID?,
    val scheduledAt: LocalDateTime,
    val medication: MedicationGroupMedication,
    val status: MainTodayDoseStatus,
    val loggedAt: LocalDateTime? = null,
    val outsideScheduleWindowLoggedAt: LocalDateTime? = null,
    val fulfillingEntryUuids: List<UUID> = emptyList(),
    val outsideScheduleWindowEntryUuids: List<UUID> = emptyList(),
    val loggedCount: Int = 0,
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
    zoneId: ZoneId = ZoneId.systemDefault()
): MainE2HeroUiState {
    val lastEstradiolEntry = findLastEstradiolEntry(entries)

    return MainE2HeroUiState(
        lastDoseDetails = lastEstradiolEntry?.details,
        lastDoseAt = lastEstradiolEntry
            ?.appliedAt
            ?.atZone(zoneId)
            ?.toLocalDateTime()
    )
}

internal fun buildMainE2Chart(): MainE2ChartUiState {
    return MainE2ChartUiState()
}

internal fun buildMainAntiandrogenCards(
    groups: List<MedicationGroup>,
    entries: List<MedicationLogEntry>,
    now: LocalDateTime,
    zoneId: ZoneId = ZoneId.systemDefault()
): List<MainAntiandrogenCardUiState> {
    return groups.sortedBy { it.createdAt }.flatMap { group ->
        val nextDoseAt = group.nextOccurrencesInPlanWindowFrom(
            start = now,
            limit = 1,
            zoneId = zoneId,
        ).firstOrNull()?.scheduledFor

        group.medications
            .filter { medication -> medication.category == MedicationCategory.ANTIANDROGEN }
            .groupBy(MedicationSignature::fromGroupMedication)
            .map { (_, medicationsForSignature) ->
                val medication = medicationsForSignature.first().copy(
                    count = medicationsForSignature.sumOf { it.count }
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
                    nextDoseAt = nextDoseAt
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
    val rows = daySchedule.scheduledEntries.map { scheduledEntry ->
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
            loggedCount = scheduledEntry.loggedCount
        )
    }

    return MainTodaySectionUiState(
        date = today,
        doneCount = rows.count { it.status == MainTodayDoseStatus.DONE },
        totalCount = rows.size,
        rows = rows
    )
}

internal fun buildMainUpcomingSection(
    groups: List<MedicationGroup>,
    entries: List<MedicationLogEntry>,
    now: LocalDateTime,
    lookaheadDays: Long = 14L,
    upcomingLimit: Int = 3,
    zoneId: ZoneId = ZoneId.systemDefault(),
): MainUpcomingSectionUiState {
    val tomorrow = now.toLocalDate().plusDays(1)
    val tomorrowRows = buildMainPreviewRowsForDate(
        date = tomorrow,
        groups = groups,
        entries = entries
    )

    if (tomorrowRows.isNotEmpty()) {
        return MainUpcomingSectionUiState(
            title = MainUpcomingSectionTitle.TOMORROW,
            anchorDate = tomorrow,
            rows = tomorrowRows
        )
    }

    val futureStartDate = tomorrow.plusDays(1)
    val upcomingRows = groups
        .flatMap { group ->
            val distinctMedications = group.medications
                .groupBy(MedicationSignature::fromGroupMedication)
                .map { (_, meds) ->
                    meds.first().copy(count = meds.sumOf { medication -> medication.count })
                }
            group
                .occurrencesBetweenInPlanWindow(
                    startDate = futureStartDate,
                    endDate = futureStartDate.plusDays(lookaheadDays),
                    zoneId = zoneId,
                )
                .asSequence()
                .filterNot { occurrence ->
                    isSlotFulfilled(
                        group = group,
                        slot = PlanScheduleTimeSlot(
                            scheduleTimeUuid = occurrence.scheduleTimeUuid,
                            scheduledFor = occurrence.scheduledFor,
                        ),
                        entries = entries
                    )
                }
                .flatMap { occurrence ->
                    distinctMedications.map { medication ->
                        MainUpcomingDoseRowUiState(
                            groupUuid = group.uuid,
                            groupName = group.name,
                            groupColorKey = group.colorKey,
                            scheduleTimeUuid = occurrence.scheduleTimeUuid,
                            scheduledAt = occurrence.scheduledFor,
                            medication = medication
                        )
                    }
                }
                .toList()
        }
        .sortedBy { it.scheduledAt }
        .take(upcomingLimit)

    return MainUpcomingSectionUiState(
        title = MainUpcomingSectionTitle.UPCOMING,
        anchorDate = upcomingRows.firstOrNull()?.scheduledAt?.toLocalDate(),
        rows = upcomingRows
    )
}

internal fun buildMainPreviewRowsForDate(
    date: LocalDate,
    groups: List<MedicationGroup>,
    entries: List<MedicationLogEntry>
): List<MainUpcomingDoseRowUiState> {
    return buildPlanDaySchedule(
        date = date,
        groups = groups,
        entries = entries,
        now = date.atStartOfDay()
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
        .sortedBy { it.scheduledAt }
        .toList()
}

private fun MedicationDetails.isSameMedicationTrackingIdentity(other: MedicationDetails): Boolean {
    return category == other.category &&
        applicationType == other.applicationType &&
        selection == other.selection
}

private const val MOCK_E2_CURRENT_VALUE = 1145
private const val MOCK_E2_CHANGE_SINCE_YESTERDAY = 14
private const val MOCK_E2_TARGET_MIN = 100
private const val MOCK_E2_TARGET_MAX = 200
private const val E2_UNIT_PG_ML = "pg/mL"
private val MOCK_E2_CHART_POINTS = listOf(142f, 158f, 149f, 167f, 138f, 120f, 100f)
