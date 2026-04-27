package com.mkx.hrttracker.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.occurrencesBetween
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
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
    settingsRepository: SettingsRepository
) : ViewModel() {
    private val selectedDate = MutableStateFlow<LocalDate?>(null)
    private val currentDateTime = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(CURRENT_TIME_REFRESH_INTERVAL_MILLIS)
        }
    }

    val uiState: StateFlow<PlanUiState> = combine(
        medicationGroupRepository.observeGroups(),
        medicationLogRepository.observeEntries(),
        settingsRepository.settingsState,
        selectedDate,
        currentDateTime
    ) { groupsOrNull, entriesOrNull, settingsState, selection, now ->
        val isLoading = groupsOrNull == null || entriesOrNull == null
        val groups = groupsOrNull.orEmpty()
        val entries = entriesOrNull.orEmpty()
        val today = now.toLocalDate()
        val calendarRange = buildPlanCalendarRange(
            today = today,
            firstDayOfWeek = DayOfWeek.MONDAY
        )
        val clampedSelection = selection?.coerceIn(calendarRange.startDate, calendarRange.endDate)
        val displayedDate = clampedSelection ?: today
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
            selectedDate = clampedSelection,
            entries = entries,
            medicationGroups = groups,
            remindersEnabled = settingsState.remindersEnabled,
            calendarDays = buildPlanCalendarDayUiState(
                groups = groups,
                entries = entries,
                startDate = calendarRange.startDate,
                endDate = calendarRange.endDate
            ),
            daySchedule = daySchedule,
            nextOccurrencesByGroup = nextOccurrencesByGroup
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = PlanUiState()
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
        const val CURRENT_TIME_REFRESH_INTERVAL_MILLIS = 60_000L
    }
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
    val remindersEnabled: Boolean = true,
    val calendarDays: Map<LocalDate, PlanCalendarDayUiState> = emptyMap(),
    val daySchedule: PlanDaySchedule = PlanDaySchedule(
        date = selectedDate ?: today,
        scheduledEntries = emptyList(),
        unplannedEntries = emptyList()
    ),
    val nextOccurrencesByGroup: Map<UUID, List<LocalDateTime>> = emptyMap(),
)
