package com.mkx.hrttracker.ui.main

import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.occurrencesBetween
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

data class MainTodayDoseRowUiState(
    val groupUuid: UUID,
    val groupName: String,
    val scheduledAt: LocalDateTime,
    val medications: List<MedicationGroupMedication>,
    val status: MainTodayDoseStatus,
    val loggedAt: LocalDateTime? = null,
    val fulfillingEntryUuids: List<UUID> = emptyList(),
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
    val scheduledAt: LocalDateTime,
    val medications: List<MedicationGroupMedication>,
)

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

    val rows = daySchedule.scheduledEntries.map { scheduledEntry ->
        val scheduledAt = LocalDateTime.of(today, scheduledEntry.scheduledTime)
        val fulfillingEntries = entries
            .filter { entry ->
                entry.sourceGroupUuid == scheduledEntry.groupUuid && entry.scheduledFor == scheduledAt
            }
            .sortedBy { it.appliedAt }

        MainTodayDoseRowUiState(
            groupUuid = scheduledEntry.groupUuid,
            groupName = scheduledEntry.groupName,
            scheduledAt = scheduledAt,
            medications = scheduledEntry.medications,
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
            fulfillingEntryUuids = fulfillingEntries.map { it.uuid }
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
    upcomingLimit: Int = 3
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
            group.schedule
                .occurrencesBetween(
                    startDate = futureStartDate,
                    endDate = futureStartDate.plusDays(lookaheadDays)
                )
                .asSequence()
                .filterNot { occurrence ->
                    isSlotFulfilled(
                        group = group,
                        date = occurrence.toLocalDate(),
                        time = occurrence.toLocalTime(),
                        entries = entries
                    )
                }
                .map { occurrence ->
                    MainUpcomingDoseRowUiState(
                        groupUuid = group.uuid,
                        groupName = group.name,
                        scheduledAt = occurrence,
                        medications = group.medications
                    )
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
                scheduledAt = LocalDateTime.of(date, scheduledEntry.scheduledTime),
                medications = scheduledEntry.medications
            )
        }
        .sortedBy { it.scheduledAt }
        .toList()
}
