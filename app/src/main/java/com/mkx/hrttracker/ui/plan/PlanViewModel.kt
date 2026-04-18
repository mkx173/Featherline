package com.mkx.hrttracker.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val uiState: StateFlow<PlanUiState> = combine(
        medicationGroupRepository.observeGroups(),
        medicationLogRepository.observeEntries()
    ) { groups, entries ->
        val today = LocalDate.now()
        val calendarRange = buildPlanCalendarRange(
            today = today,
            firstDayOfWeek = DayOfWeek.MONDAY
        )

        PlanUiState(
            today = today,
            calendarFirstDayOfWeek = calendarRange.firstDayOfWeek,
            calendarStartDate = calendarRange.startDate,
            calendarEndDate = calendarRange.endDate,
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
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlanUiState()
        )
}

data class PlanUiState(
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
    val entries: List<MedicationLogEntry> = emptyList(),
    val medicationGroups: List<MedicationGroup> = emptyList(),
    val calendarDays: Map<LocalDate, PlanCalendarDayUiState> = emptyMap(),
)
