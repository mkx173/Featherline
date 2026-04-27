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
    private val isDeletingSelectedEntries = MutableStateFlow(false)
    private val isDeletingAllEntries = MutableStateFlow(false)
    private val deleteSelectedEntriesResult = MutableStateFlow<HistoryDeleteSelectedEntriesResult?>(null)
    private val deleteAllEntriesResult = MutableStateFlow<HistoryDeleteAllEntriesResult?>(null)
    private val displayedMonth = MutableStateFlow(YearMonth.now())
    private val selectedDate = MutableStateFlow<LocalDate?>(null)

    val uiState: StateFlow<HistoryUiState> = combine(
        combine(
            medicationLogRepository.observeEntries(),
            medicationGroupRepository.observeGroups(),
            ::Pair
        ),
        combine(
            combine(
                selectedEntryIds,
                isDeleteConfirmationVisible,
                isDeletingSelectedEntries,
                isDeletingAllEntries,
                ::HistoryDeleteInteractionUiState,
            ),
            combine(
                deleteSelectedEntriesResult,
                deleteAllEntriesResult,
                ::Pair,
            ),
        ) { interactionState, deleteResults ->
            val (selectedResult, allResult) = deleteResults
            HistoryDeletionUiState(
                selectedEntryIds = interactionState.selectedEntryIds,
                isDeleteConfirmationVisible = interactionState.isDeleteConfirmationVisible,
                isDeletingSelectedEntries = interactionState.isDeletingSelectedEntries,
                isDeletingAllEntries = interactionState.isDeletingAllEntries,
                deleteSelectedEntriesResult = selectedResult,
                deleteAllEntriesResult = allResult,
            )
        },
        combine(
            displayedMonth,
            selectedDate,
            ::Pair
        ),
    ) { entriesAndGroups,
        deletionUiState,
        displayState ->
        val (entriesOrNull, groupsOrNull) = entriesAndGroups
        val (month, selectedDay) = displayState
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
        val visibleSelection = deletionUiState.selectedEntryIds
            .intersect(entries.mapTo(mutableSetOf()) { it.uuid })
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
            isDeleteConfirmationVisible = deletionUiState.isDeleteConfirmationVisible &&
                visibleSelection.isNotEmpty(),
            isDeletingSelectedEntries = deletionUiState.isDeletingSelectedEntries,
            isDeletingAllEntries = deletionUiState.isDeletingAllEntries,
            deleteSelectedEntriesResult = deletionUiState.deleteSelectedEntriesResult,
            deleteAllEntriesResult = deletionUiState.deleteAllEntriesResult,
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

    fun toggleEntrySelection(entryId: UUID) {
        selectedEntryIds.update { currentSelection ->
            toggleHistoryEntrySelection(
                currentSelection = currentSelection,
                entryId = entryId
            )
        }

        if (selectedEntryIds.value.isEmpty()) {
            isDeleteConfirmationVisible.value = false
        }
    }

    fun clearEntrySelection() {
        selectedEntryIds.value = emptySet()
        isDeleteConfirmationVisible.value = false
    }

    fun selectEntries(entryIds: Set<UUID>) {
        selectedEntryIds.update { currentSelection ->
            selectAllHistoryEntries(
                currentSelection = currentSelection,
                entryIds = entryIds,
            )
        }
        isDeleteConfirmationVisible.value = false
    }

    fun reverseEntrySelection(entryIds: Set<UUID>) {
        selectedEntryIds.update { currentSelection ->
            reverseHistoryEntrySelection(
                currentSelection = currentSelection,
                entryIds = entryIds,
            )
        }
        isDeleteConfirmationVisible.value = false
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
        if (entryIdsToDelete.isEmpty() || isDeletingSelectedEntries.value) {
            return
        }

        viewModelScope.launch {
            isDeletingSelectedEntries.value = true
            isDeleteConfirmationVisible.value = false
            deleteSelectedEntriesResult.value = null

            val result = runCatching {
                medicationLogRepository.deleteEntries(entryIdsToDelete)
                medicationReminderScheduler.rescheduleAll()
            }.fold(
                onSuccess = { HistoryDeleteSelectedEntriesResult.SUCCESS },
                onFailure = { HistoryDeleteSelectedEntriesResult.FAILURE },
            )

            if (result == HistoryDeleteSelectedEntriesResult.SUCCESS) {
                selectedEntryIds.value = emptySet()
            }
            isDeletingSelectedEntries.value = false
            deleteSelectedEntriesResult.value = result
        }
    }

    fun deleteAllEntries() {
        if (isDeletingAllEntries.value || uiState.value.entries.isEmpty()) {
            return
        }

        selectedEntryIds.value = emptySet()
        isDeleteConfirmationVisible.value = false

        viewModelScope.launch {
            isDeletingAllEntries.value = true
            val result = runCatching {
                medicationLogRepository.deleteAllEntries()
                medicationReminderScheduler.rescheduleAll()
            }.fold(
                onSuccess = { HistoryDeleteAllEntriesResult.SUCCESS },
                onFailure = { HistoryDeleteAllEntriesResult.FAILURE },
            )
            isDeletingAllEntries.value = false
            deleteAllEntriesResult.value = result
        }
    }

    fun consumeDeleteAllEntriesResult() {
        deleteAllEntriesResult.value = null
    }

    fun consumeDeleteSelectedEntriesResult() {
        deleteSelectedEntriesResult.value = null
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
    val isDeletingSelectedEntries: Boolean = false,
    val isDeletingAllEntries: Boolean = false,
    val deleteSelectedEntriesResult: HistoryDeleteSelectedEntriesResult? = null,
    val deleteAllEntriesResult: HistoryDeleteAllEntriesResult? = null,
) {
    val isSelectionMode: Boolean
        get() = selectedEntryIds.isNotEmpty()
}

enum class HistoryDeleteAllEntriesResult {
    SUCCESS,
    FAILURE,
}

enum class HistoryDeleteSelectedEntriesResult {
    SUCCESS,
    FAILURE,
}

private data class HistoryDeletionUiState(
    val selectedEntryIds: Set<UUID>,
    val isDeleteConfirmationVisible: Boolean,
    val isDeletingSelectedEntries: Boolean,
    val isDeletingAllEntries: Boolean,
    val deleteSelectedEntriesResult: HistoryDeleteSelectedEntriesResult?,
    val deleteAllEntriesResult: HistoryDeleteAllEntriesResult?,
)

private data class HistoryDeleteInteractionUiState(
    val selectedEntryIds: Set<UUID>,
    val isDeleteConfirmationVisible: Boolean,
    val isDeletingSelectedEntries: Boolean,
    val isDeletingAllEntries: Boolean,
)
