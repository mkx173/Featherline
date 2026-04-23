package com.mkx.hrttracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val medicationLogRepository: MedicationLogRepository,
    medicationGroupRepository: MedicationGroupRepository,
    private val medicationReminderScheduler: MedicationReminderScheduler
) : ViewModel() {
    private val selectedEntryIds = MutableStateFlow<Set<UUID>>(emptySet())
    private val isDeleteConfirmationVisible = MutableStateFlow(false)
    private val displayedMonth = MutableStateFlow(YearMonth.now())
    private val selectedDate = MutableStateFlow<LocalDate?>(null)

    val uiState: StateFlow<HistoryUiState> = combine(
        combine(
            medicationLogRepository.observeEntries(),
            medicationGroupRepository.observeGroups(),
            ::Pair
        ),
        selectedEntryIds,
        isDeleteConfirmationVisible,
        displayedMonth,
        selectedDate
    ) { (entriesOrNull, groupsOrNull), currentSelection, deleteConfirmationVisible, month, selectedDay ->
        val isLoading = entriesOrNull == null || groupsOrNull == null
        val entries = entriesOrNull.orEmpty()
        val groups = groupsOrNull.orEmpty()
        val currentMonth = YearMonth.now()
        val earliestEntryMonth = entries.minOfOrNull { entry ->
            YearMonth.from(entry.appliedAt.atZone(ZoneId.systemDefault()).toLocalDate())
        }
        val earliestGroupMonth = groups.minOfOrNull { group ->
            YearMonth.from(group.schedule.since)
        }
        val calendarStartMonth = listOfNotNull(earliestEntryMonth, earliestGroupMonth)
            .minOrNull()
            ?.coerceAtMost(currentMonth)
            ?: currentMonth
        val calendarEndMonth = currentMonth
        val visibleMonth = month.coerceIn(calendarStartMonth, calendarEndMonth)
        val visibleSelection = currentSelection.intersect(entries.mapTo(mutableSetOf()) { it.uuid })
        HistoryUiState(
            isLoading = isLoading,
            entries = entries,
            medicationGroups = groups,
            calendarFirstDayOfWeek = DayOfWeek.MONDAY,
            calendarStartMonth = calendarStartMonth,
            calendarEndMonth = calendarEndMonth,
            displayedMonth = visibleMonth,
            selectedDate = selectedDay,
            selectedEntryIds = visibleSelection,
            isDeleteConfirmationVisible = deleteConfirmationVisible && visibleSelection.isNotEmpty()
        )
    }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = HistoryUiState()
        )

    fun setDisplayedMonth(month: YearMonth, clearSelection: Boolean = true) {
        if (displayedMonth.value == month) {
            return
        }
        displayedMonth.value = month
        selectedEntryIds.value = emptySet()
        isDeleteConfirmationVisible.value = false
        if (clearSelection) {
            selectedDate.value = null
        }
    }

    fun toggleSelectedDate(date: LocalDate) {
        if (!canSelectHistoryCalendarDate(date, LocalDate.now())) {
            return
        }
        selectedDate.value = if (selectedDate.value == date) {
            null
        } else {
            date
        }
        selectedEntryIds.value = emptySet()
        isDeleteConfirmationVisible.value = false
    }

    fun toggleEntrySelection(entryIds: Set<UUID>) {
        if (entryIds.isEmpty()) {
            return
        }

        selectedEntryIds.update { currentSelection ->
            toggleHistoryEntrySelection(
                currentSelection = currentSelection,
                entryIds = entryIds
            )
        }

        if (selectedEntryIds.value.isEmpty()) {
            isDeleteConfirmationVisible.value = false
        }
    }

    fun showDeleteConfirmation() {
        if (selectedEntryIds.value.isNotEmpty()) {
            isDeleteConfirmationVisible.value = true
        }
    }

    fun dismissDeleteConfirmation() {
        isDeleteConfirmationVisible.value = false
    }

    fun deleteSelectedEntries() {
        val entryIdsToDelete = selectedEntryIds.value
        if (entryIdsToDelete.isEmpty()) {
            return
        }

        viewModelScope.launch {
            medicationLogRepository.deleteEntries(entryIdsToDelete)
            medicationReminderScheduler.rescheduleAll()
            selectedEntryIds.value = emptySet()
            isDeleteConfirmationVisible.value = false
        }
    }
}

data class HistoryUiState(
    val isLoading: Boolean = true,
    val entries: List<MedicationLogEntry> = emptyList(),
    val medicationGroups: List<MedicationGroup> = emptyList(),
    val calendarFirstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val calendarStartMonth: YearMonth = YearMonth.now(),
    val calendarEndMonth: YearMonth = YearMonth.now(),
    val displayedMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate? = null,
    val selectedEntryIds: Set<UUID> = emptySet(),
    val isDeleteConfirmationVisible: Boolean = false,
) {
    val isSelectionMode: Boolean
        get() = selectedEntryIds.isNotEmpty()
}
