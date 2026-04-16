package com.mkx.hrttracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val medicationLogRepository: MedicationLogRepository
) : ViewModel() {
    private val selectedEntryIds = MutableStateFlow<Set<UUID>>(emptySet())
    private val isDeleteConfirmationVisible = MutableStateFlow(false)

    val uiState: StateFlow<HistoryUiState> = combine(
        medicationLogRepository.observeEntries(),
        selectedEntryIds,
        isDeleteConfirmationVisible
    ) { entries, currentSelection, deleteConfirmationVisible ->
        val visibleSelection = currentSelection.intersect(entries.mapTo(mutableSetOf()) { it.uuid })
        HistoryUiState(
            entries = entries,
            selectedEntryIds = visibleSelection,
            isDeleteConfirmationVisible = deleteConfirmationVisible && visibleSelection.isNotEmpty()
        )
    }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryUiState()
        )

    fun toggleEntrySelection(entryId: UUID) {
        selectedEntryIds.update { currentSelection ->
            if (entryId in currentSelection) {
                currentSelection - entryId
            } else {
                currentSelection + entryId
            }
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
            selectedEntryIds.value = emptySet()
            isDeleteConfirmationVisible.value = false
        }
    }
}

data class HistoryUiState(
    val entries: List<MedicationLogEntry> = emptyList(),
    val selectedEntryIds: Set<UUID> = emptySet(),
    val isDeleteConfirmationVisible: Boolean = false,
) {
    val isSelectionMode: Boolean
        get() = selectedEntryIds.isNotEmpty()
}
