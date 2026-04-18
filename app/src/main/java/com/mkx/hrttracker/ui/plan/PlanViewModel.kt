package com.mkx.hrttracker.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
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
            )
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
)
