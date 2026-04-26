package com.mkx.hrttracker.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.ui.medication.MedicationDraftUiState
import com.mkx.hrttracker.ui.medication.defaultMedicationDraft
import com.mkx.hrttracker.ui.medication.medicationDraftFromDetails
import com.mkx.hrttracker.ui.medication.medicationCountValidationErrorRes
import com.mkx.hrttracker.ui.medication.normalizeMedicationCount
import com.mkx.hrttracker.ui.medication.parseMedicationCountText
import com.mkx.hrttracker.ui.medication.resolveMedicationCountTextAfterDraftChange
import com.mkx.hrttracker.ui.medication.resolvedMedicationCountForSave
import com.mkx.hrttracker.ui.medication.sanitizeMedicationCountText
import com.mkx.hrttracker.ui.medication.stepMedicationCount
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

    fun initialize(entryIds: List<String>) {
        loadEntryJob?.cancel()
        val normalizedEntryIds = normalizeEditingEntryIds(entryIds)
        _uiState.value = AddEntryUiState(editingEntryIds = normalizedEntryIds)

        if (normalizedEntryIds.isNotEmpty()) {
            loadEntriesForEditing(normalizedEntryIds)
        }
    }

    fun updateMedicationDraft(
        transform: (MedicationDraftUiState) -> MedicationDraftUiState
    ) {
        _uiState.update { currentState ->
            val updatedDraft = transform(currentState.medicationDraft)
            currentState.copy(
                medicationDraft = updatedDraft,
                countText = resolveMedicationCountTextAfterDraftChange(
                    previousDraft = currentState.medicationDraft,
                    updatedDraft = updatedDraft,
                    currentCountText = currentState.countText
                ),
                errorMessageRes = null
            )
        }
    }

    fun updateCountText(countText: String) {
        _uiState.update { currentState ->
            currentState.copy(
                countText = sanitizeMedicationCountText(countText),
                errorMessageRes = null
            )
        }
    }

    fun decreaseCount() {
        _uiState.update { currentState ->
            currentState.copy(
                countText = stepMedicationCount(
                    applicationType = currentState.medicationDraft.applicationType,
                    countText = currentState.countText,
                    delta = -1
                ).toString(),
                errorMessageRes = null
            )
        }
    }

    fun increaseCount() {
        _uiState.update { currentState ->
            currentState.copy(
                countText = stepMedicationCount(
                    applicationType = currentState.medicationDraft.applicationType,
                    countText = currentState.countText,
                    delta = 1
                ).toString(),
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
            ?: medicationCountValidationErrorRes(
                applicationType = currentState.medicationDraft.applicationType,
                countText = currentState.countText
            )

        if (errorRes != null) {
            _uiState.update {
                it.copy(errorMessageRes = errorRes)
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessageRes = null) }

            val editingEntryUuids = currentState.editingEntryIds.map(UUID::fromString)
            val resolvedCount = resolvedMedicationCountForSave(
                applicationType = currentState.medicationDraft.applicationType,
                countText = currentState.countText,
            )
            if (editingEntryUuids.size > 1) {
                medicationLogRepository.saveEntries(
                    uuids = editingEntryUuids,
                    medication = currentState.medicationDraft.toMedicationDetails(),
                    sourceGroupUuid = currentState.sourceGroupUuid,
                    appliedAt = appliedAt,
                    scheduledFor = currentState.scheduledFor,
                    count = resolvedCount
                )
            } else {
                medicationLogRepository.saveEntry(
                    uuid = editingEntryUuids.firstOrNull(),
                    medication = currentState.medicationDraft.toMedicationDetails(),
                    sourceGroupUuid = currentState.sourceGroupUuid,
                    appliedAt = appliedAt,
                    scheduledFor = currentState.scheduledFor,
                    count = resolvedCount
                )
            }
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

    fun deleteEntry() {
        val editingEntryUuids = _uiState.value.editingEntryIds.map(UUID::fromString)
        if (editingEntryUuids.isEmpty()) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessageRes = null) }

            medicationLogRepository.deleteEntries(editingEntryUuids)
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

    private fun loadEntriesForEditing(entryIds: List<String>) {
        loadEntryJob = viewModelScope.launch {
            val entries = medicationLogRepository.getEntries(entryIds.map(UUID::fromString))
            _uiState.value = buildEditingUiState(entries) ?: AddEntryUiState()
        }
    }

}

data class AddEntryUiState(
    val editingEntryIds: List<String> = emptyList(),
    val medicationDraft: MedicationDraftUiState = defaultMedicationDraft(),
    val sourceGroupUuid: UUID? = null,
    val scheduledFor: LocalDateTime? = null,
    val countText: String = "1",
    val appliedDate: LocalDate = LocalDate.now(),
    val appliedTime: LocalTime = LocalTime.now().withSecond(0).withNano(0),
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessageRes: Int? = null,
) {
    val count: Int
        get() = parseMedicationCountText(countText)

    val isEditing: Boolean
        get() = editingEntryIds.isNotEmpty()

    val isBulkEditing: Boolean
        get() = editingEntryIds.size > 1

    val canEditMedicationIdentity: Boolean
        get() = sourceGroupUuid == null

    val canDelete: Boolean
        get() = isEditing
}

internal fun normalizeEditingEntryIds(entryIds: Collection<String>): List<String> {
    return entryIds.mapNotNull { entryId ->
        runCatching { UUID.fromString(entryId).toString() }.getOrNull()
    }.distinct()
}

internal fun buildEditingUiState(entries: List<MedicationLogEntry>): AddEntryUiState? {
    val representativeEntry = entries.firstOrNull() ?: return null
    val editableEntries = if (canBulkEditTogether(entries)) entries else listOf(representativeEntry)
    val appliedAt = representativeEntry.appliedAt.atZone(ZoneId.systemDefault())

    return AddEntryUiState(
        editingEntryIds = editableEntries.map { entry -> entry.uuid.toString() },
        medicationDraft = medicationDraftFromDetails(representativeEntry.details),
        sourceGroupUuid = representativeEntry.sourceGroupUuid,
        scheduledFor = representativeEntry.scheduledFor,
        countText = normalizeMedicationCount(
            representativeEntry.details.applicationType,
            representativeEntry.count
        ).toString(),
        appliedDate = appliedAt.toLocalDate(),
        appliedTime = appliedAt.toLocalTime().withSecond(0).withNano(0)
    )
}

internal fun canBulkEditTogether(entries: List<MedicationLogEntry>): Boolean {
    if (entries.size <= 1) {
        return true
    }

    val firstEntry = entries.first()
    return entries.all { entry ->
        entry.details == firstEntry.details &&
            entry.sourceGroupUuid == firstEntry.sourceGroupUuid &&
            entry.appliedAt == firstEntry.appliedAt &&
            entry.scheduledFor == firstEntry.scheduledFor &&
            entry.count == firstEntry.count
    }
}
