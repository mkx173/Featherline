package com.mkx.hrttracker.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.nextOccurrencesFrom
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
    medicationLogRepository: MedicationLogRepository
) : ViewModel() {
    private val selectedDate = MutableStateFlow(LocalDate.now())

    val uiState: StateFlow<PlanUiState> = combine(
        medicationGroupRepository.observeGroups(),
        medicationLogRepository.observeEntries(),
        selectedDate
    ) { groupsOrNull, entriesOrNull, selection ->
        val isLoading = groupsOrNull == null || entriesOrNull == null
        val groups = groupsOrNull.orEmpty()
        val entries = entriesOrNull.orEmpty()
        val today = LocalDate.now()
        val calendarRange = buildPlanCalendarRange(
            today = today,
            firstDayOfWeek = DayOfWeek.MONDAY
        )
        val clampedSelection = selection.coerceIn(calendarRange.startDate, calendarRange.endDate)
        val daySchedule = buildPlanDaySchedule(
            date = clampedSelection,
            groups = groups,
            entries = entries
        )
        val nextOccurrencesFrom = LocalDateTime.of(today, java.time.LocalTime.MIN)
        val nextOccurrencesByGroup = groups.associate { group ->
            group.uuid to group.schedule.nextOccurrencesFrom(
                start = nextOccurrencesFrom,
                limit = UPCOMING_OCCURRENCES_LIMIT
            )
        }

        PlanUiState(
            isLoading = isLoading,
            today = today,
            calendarFirstDayOfWeek = calendarRange.firstDayOfWeek,
            calendarStartDate = calendarRange.startDate,
            calendarEndDate = calendarRange.endDate,
            selectedDate = clampedSelection,
            entries = entries,
            medicationGroups = groups,
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

    fun setSelectedDate(date: LocalDate) {
        if (selectedDate.value == date) {
            return
        }
        selectedDate.value = date
    }

    private companion object {
        const val UPCOMING_OCCURRENCES_LIMIT = 3
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
    val selectedDate: LocalDate = today,
    val entries: List<MedicationLogEntry> = emptyList(),
    val medicationGroups: List<MedicationGroup> = emptyList(),
    val calendarDays: Map<LocalDate, PlanCalendarDayUiState> = emptyMap(),
    val daySchedule: PlanDaySchedule = PlanDaySchedule(
        date = selectedDate,
        scheduledEntries = emptyList(),
        unplannedEntries = emptyList()
    ),
    val nextOccurrencesByGroup: Map<UUID, List<LocalDateTime>> = emptyMap(),
)
