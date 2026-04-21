package com.mkx.hrttracker.ui.main

import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.occurrencesBetween
import com.mkx.hrttracker.model.medication.nextOccurrencesFrom
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
    val groupColorKey: MedicationGroupColorKey,
    val scheduledAt: LocalDateTime,
    val medications: List<MedicationGroupMedication>,
)

internal fun buildMainE2Hero(
    entries: List<MedicationLogEntry>,
    zoneId: ZoneId = ZoneId.systemDefault()
): MainE2HeroUiState {
    val lastEstradiolEntry = entries
        .asSequence()
        .filter { entry -> entry.category == MedicationCategory.ESTRADIOL }
        .maxByOrNull { entry -> entry.appliedAt }

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
    return groups.flatMap { group ->
        val nextDoseAt = group.schedule.nextOccurrencesFrom(
            start = now,
            limit = 1
        ).firstOrNull()

        group.medications
            .filter { medication -> medication.category == MedicationCategory.ANTIANDROGEN }
            .map { medication ->
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
            groupColorKey = scheduledEntry.groupColorKey,
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
                        groupColorKey = group.colorKey,
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
                groupColorKey = scheduledEntry.groupColorKey,
                scheduledAt = LocalDateTime.of(date, scheduledEntry.scheduledTime),
                medications = scheduledEntry.medications
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

private const val MOCK_E2_CURRENT_VALUE = 100
private const val MOCK_E2_CHANGE_SINCE_YESTERDAY = 0
private const val MOCK_E2_TARGET_MIN = 100
private const val MOCK_E2_TARGET_MAX = 200
private const val E2_UNIT_PG_ML = "pg/mL"
private val MOCK_E2_CHART_POINTS = listOf(142f, 158f, 149f, 167f, 138f, 120f, 100f)
