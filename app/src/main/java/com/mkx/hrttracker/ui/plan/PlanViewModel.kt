package com.mkx.hrttracker.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.isActive
import com.mkx.hrttracker.model.medication.isArchived
import com.mkx.hrttracker.model.medication.occurrencesBetween
import com.mkx.hrttracker.model.medication.visibleMedicationEntries
import com.mkx.hrttracker.util.AppTimeSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PlanViewModel @Inject constructor(
    medicationGroupRepository: MedicationGroupRepository,
    medicationLogRepository: MedicationLogRepository,
    settingsRepository: SettingsRepository,
    appTimeSource: AppTimeSource
) : ViewModel() {
    private val selectedDate = MutableStateFlow<LocalDate?>(null)
    private val currentDateTime = appTimeSource.currentMinute

    val uiState: StateFlow<PlanUiState> = combine(
        medicationGroupRepository.observeGroups(),
        medicationLogRepository.observeEntries(),
        settingsRepository.settingsState,
        selectedDate,
        currentDateTime
    ) { groupsOrNull, entriesOrNull, settingsState, selection, now ->
        val isLoading = groupsOrNull == null || entriesOrNull == null
        val allGroups = sortPlanMedicationGroups(groupsOrNull.orEmpty())
        val groups = allGroups.filter(MedicationGroup::isActive)
        val archivedGroups = allGroups.filter(MedicationGroup::isArchived)
        val entries = visibleMedicationEntries(
            entries = entriesOrNull.orEmpty(),
            groups = allGroups,
            showArchivedGroupRecords = settingsState.showArchivedGroupRecords,
        )
        val today = now.toLocalDate()
        val calendarRange = buildPlanCalendarRange(
            today = today,
            firstDayOfWeek = DayOfWeek.MONDAY
        )
        val displayedDate = selection ?: today
        val daySchedule = buildPlanDaySchedule(
            date = displayedDate,
            groups = groups,
            entries = entries,
            now = now
        )
        val nextOccurrencesByGroup = buildNextOccurrencesByGroup(
            groups = groups,
            entries = entries,
            start = now,
            limit = UPCOMING_OCCURRENCES_LIMIT
        )

        PlanUiState(
            isLoading = isLoading,
            today = today,
            calendarFirstDayOfWeek = calendarRange.firstDayOfWeek,
            calendarStartDate = calendarRange.startDate,
            calendarEndDate = calendarRange.endDate,
            selectedDate = selection,
            entries = entries,
            medicationGroups = groups,
            archivedMedicationGroups = archivedGroups,
            showArchivedGroupRecords = settingsState.showArchivedGroupRecords,
            remindersEnabled = settingsState.remindersEnabled,
            calendarDays = buildPlanCalendarDayUiStateIncludingDisplayedDate(
                groups = groups,
                entries = entries,
                startDate = calendarRange.startDate,
                endDate = calendarRange.endDate,
                displayedDate = displayedDate,
            ),
            daySchedule = daySchedule,
            nextOccurrencesByGroup = nextOccurrencesByGroup
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(UI_STATE_STOP_TIMEOUT_MILLIS),
            initialValue = PlanUiState(today = currentDateTime.value.toLocalDate())
        )

    fun toggleSelectedDate(date: LocalDate) {
        selectedDate.value = if (date == uiState.value.today || selectedDate.value == date) {
            null
        } else {
            date
        }
    }

    fun clearSelectedDate() {
        if (selectedDate.value != null) {
            selectedDate.value = null
        }
    }

    private companion object {
        const val UPCOMING_OCCURRENCES_LIMIT = 3
        const val UI_STATE_STOP_TIMEOUT_MILLIS = 5_000L
    }
}

internal fun sortPlanMedicationGroups(groups: List<MedicationGroup>): List<MedicationGroup> {
    return groups.sortedWith(
        compareBy<MedicationGroup> { it.createdAt }
            .thenBy { it.uuid }
    )
}

private fun buildPlanCalendarDayUiStateIncludingDisplayedDate(
    groups: List<MedicationGroup>,
    entries: List<MedicationLogEntry>,
    startDate: LocalDate,
    endDate: LocalDate,
    displayedDate: LocalDate,
): Map<LocalDate, PlanCalendarDayUiState> {
    val calendarDays = buildPlanCalendarDayUiState(
        groups = groups,
        entries = entries,
        startDate = startDate,
        endDate = endDate,
    )

    if (displayedDate in calendarDays) {
        return calendarDays
    }

    return calendarDays + buildPlanCalendarDayUiState(
        groups = groups,
        entries = entries,
        startDate = displayedDate,
        endDate = displayedDate,
    )
}

internal fun buildNextOccurrencesByGroup(
    groups: List<MedicationGroup>,
    entries: List<MedicationLogEntry>,
    start: LocalDateTime,
    limit: Int,
    lookaheadDays: Long = 90L
): Map<UUID, List<LocalDateTime>> {
    return groups.associate { group ->
        group.uuid to group.schedule
            .occurrencesBetween(
                startDate = start.toLocalDate(),
                endDate = start.toLocalDate().plusDays(lookaheadDays)
            )
            .asSequence()
            .filter { occurrence ->
                !occurrence.isBefore(start)
            }
            .filterNot { occurrence ->
                isSlotFulfilled(
                    group = group,
                    date = occurrence.toLocalDate(),
                    time = occurrence.toLocalTime(),
                    entries = entries
                )
            }
            .take(limit)
            .toList()
    }
}

data class PlanUiState(
    val isLoading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val calendarFirstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val calendarStartDate: LocalDate = buildPlanCalendarRange(
        today = today,
        firstDayOfWeek = calendarFirstDayOfWeek
    ).startDate,
    val calendarEndDate: LocalDate = buildPlanCalendarRange(
        today = today,
        firstDayOfWeek = calendarFirstDayOfWeek
    ).endDate,
    val selectedDate: LocalDate? = null,
    val entries: List<MedicationLogEntry> = emptyList(),
    val medicationGroups: List<MedicationGroup> = emptyList(),
    val archivedMedicationGroups: List<MedicationGroup> = emptyList(),
    val showArchivedGroupRecords: Boolean = true,
    val remindersEnabled: Boolean = true,
    val calendarDays: Map<LocalDate, PlanCalendarDayUiState> = emptyMap(),
    val daySchedule: PlanDaySchedule = PlanDaySchedule(
        date = selectedDate ?: today,
        scheduledEntries = emptyList(),
        unplannedEntries = emptyList()
    ),
    val nextOccurrencesByGroup: Map<UUID, List<LocalDateTime>> = emptyMap(),
)
