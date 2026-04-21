package com.mkx.hrttracker.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.model.medication.MedicationLogEntrySourceType
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.ui.medication.MedicationDraftUiState
import com.mkx.hrttracker.ui.medication.defaultMedicationDraft
import com.mkx.hrttracker.ui.medication.medicationDraftFromDetails
import com.mkx.hrttracker.ui.medication.toMedicationDetails
import com.mkx.hrttracker.ui.medication.validationErrorRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AddEntryViewModel @Inject constructor(
    private val medicationLogRepository: MedicationLogRepository,
    private val medicationReminderScheduler: MedicationReminderScheduler
) : ViewModel() {
    private val _uiState = MutableStateFlow(AddEntryUiState())
    val uiState: StateFlow<AddEntryUiState> = _uiState.asStateFlow()
    private var loadEntryJob: Job? = null

    fun initialize(entryId: String?) {
        loadEntryJob?.cancel()
        _uiState.value = AddEntryUiState(editingEntryId = entryId)

        if (entryId != null) {
            loadEntryForEditing(entryId)
        }
    }

    fun updateMedicationDraft(
        transform: (MedicationDraftUiState) -> MedicationDraftUiState
    ) {
        _uiState.update {
            it.copy(
                medicationDraft = transform(it.medicationDraft),
                errorMessageRes = null
            )
        }
    }

    fun updateAppliedDate(appliedDate: LocalDate) {
        _uiState.update {
            it.copy(
                appliedDate = appliedDate,
                errorMessageRes = null
            )
        }
    }

    fun updateAppliedTime(appliedTime: LocalTime) {
        _uiState.update {
            it.copy(
                appliedTime = appliedTime.withSecond(0).withNano(0),
                errorMessageRes = null
            )
        }
    }

    fun saveEntry() {
        val currentState = _uiState.value
        val appliedAt = LocalDateTime.of(
            currentState.appliedDate,
            currentState.appliedTime
        ).atZone(ZoneId.systemDefault()).toInstant()
        val errorRes = currentState.medicationDraft.validationErrorRes()

        if (errorRes != null) {
            _uiState.update {
                it.copy(errorMessageRes = errorRes)
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessageRes = null) }

            medicationLogRepository.saveEntry(
                uuid = currentState.editingEntryId?.let(UUID::fromString),
                medication = currentState.medicationDraft.toMedicationDetails(),
                sourceType = currentState.sourceType,
                sourceGroupUuid = currentState.sourceGroupUuid,
                appliedAt = appliedAt
            )
            medicationReminderScheduler.rescheduleAll()

            _uiState.update {
                it.copy(
                    isSaving = false,
                    isSaved = true,
                    errorMessageRes = null
                )
            }
        }
    }

    fun consumeSavedState() {
        _uiState.update { it.copy(isSaved = false) }
    }

    private fun loadEntryForEditing(entryId: String) {
        val uuid = runCatching { UUID.fromString(entryId) }.getOrNull() ?: return

        loadEntryJob = viewModelScope.launch {
            val entry = medicationLogRepository.getEntry(uuid) ?: return@launch
            val appliedAt = entry.appliedAt.atZone(ZoneId.systemDefault())

            _uiState.value = AddEntryUiState(
                editingEntryId = entry.uuid.toString(),
                medicationDraft = medicationDraftFromDetails(entry.details),
                sourceType = entry.sourceType,
                sourceGroupUuid = entry.sourceGroupUuid,
                appliedDate = appliedAt.toLocalDate(),
                appliedTime = appliedAt.toLocalTime().withSecond(0).withNano(0)
            )
        }
    }

}

data class AddEntryUiState(
    val editingEntryId: String? = null,
    val medicationDraft: MedicationDraftUiState = defaultMedicationDraft(),
    val sourceType: MedicationLogEntrySourceType = MedicationLogEntrySourceType.MANUAL,
    val sourceGroupUuid: UUID? = null,
    val appliedDate: LocalDate = LocalDate.now(),
    val appliedTime: LocalTime = LocalTime.now().withSecond(0).withNano(0),
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessageRes: Int? = null,
) {
    val isEditing: Boolean
        get() = editingEntryId != null
}
