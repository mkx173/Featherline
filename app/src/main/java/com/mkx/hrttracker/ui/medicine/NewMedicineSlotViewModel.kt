package com.mkx.hrttracker.ui.medicine

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.ui.medication.DoseInstructionDraftUiState
import com.mkx.hrttracker.ui.medication.MedicinePickerUiState
import com.mkx.hrttracker.ui.medication.defaultMedicineDraft
import com.mkx.hrttracker.ui.medication.inferredOrSelectedPreparationType
import com.mkx.hrttracker.ui.medication.medicationCountValidationErrorRes
import com.mkx.hrttracker.ui.medication.resolveMedicationCountTextAfterDraftChange
import com.mkx.hrttracker.ui.medication.resolvedApplicationTypeForDose
import com.mkx.hrttracker.ui.medication.resolvedMedicationCountForSave
import com.mkx.hrttracker.ui.medication.sanitizeMedicationCountText
import com.mkx.hrttracker.ui.medication.toDoseInstruction
import com.mkx.hrttracker.ui.medication.toDoseInstructionDraft
import com.mkx.hrttracker.ui.medication.validationErrorRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class NewMedicineSlotViewModel @Inject constructor(
    private val medicineRepository: MedicineRepository,
    private val medicationLogRepository: MedicationLogRepository,
    private val medicationReminderScheduler: MedicationReminderScheduler,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        NewMedicineSlotUiState(appliedZoneId = ZoneId.systemDefault())
    )
    val uiState: StateFlow<NewMedicineSlotUiState> = _uiState.asStateFlow()
    private var saveGeneration = 0

    internal constructor(
        medicineRepository: MedicineRepository,
        medicationLogRepository: MedicationLogRepository,
        medicationReminderScheduler: MedicationReminderScheduler,
        initialAppliedZoneId: ZoneId,
    ) : this(
        medicineRepository = medicineRepository,
        medicationLogRepository = medicationLogRepository,
        medicationReminderScheduler = medicationReminderScheduler,
    ) {
        _uiState.value = NewMedicineSlotUiState(appliedZoneId = initialAppliedZoneId)
    }

    fun reset() {
        saveGeneration += 1
        _uiState.update {
            NewMedicineSlotUiState(appliedZoneId = it.appliedZoneId)
        }
    }

    fun updateMedicineDraft(transform: (MedicinePickerUiState) -> MedicinePickerUiState) {
        _uiState.update { state ->
            if (state.isLockedForUpdates()) {
                state
            } else {
                val updatedDraft = transform(state.medicineDraft)
                val shouldResetDoseDraft =
                    state.medicineDraft.inferredOrSelectedPreparationType() !=
                        updatedDraft.inferredOrSelectedPreparationType()
                state.copy(
                    medicineDraft = updatedDraft,
                    doseInstructionDraft = if (shouldResetDoseDraft) {
                        updatedDraft.toDoseInstructionDraft()
                    } else {
                        state.doseInstructionDraft.copy(
                            preparationType = updatedDraft.inferredOrSelectedPreparationType()
                                ?: state.doseInstructionDraft.preparationType,
                        )
                    },
                    countText = resolveMedicationCountTextAfterDraftChange(
                        previousDraft = state.medicineDraft,
                        updatedDraft = updatedDraft,
                        currentCountText = state.countText,
                    ),
                    errorMessageRes = null,
                    createSaveResult = null,
                    manualLogSaveResult = null,
                    slotResult = null,
                )
            }
        }
    }

    fun updateDoseInstructionDraft(
        transform: (DoseInstructionDraftUiState) -> DoseInstructionDraftUiState,
    ) {
        _uiState.update { state ->
            if (state.isLockedForUpdates()) {
                state
            } else {
                state.copy(
                    doseInstructionDraft = transform(state.doseInstructionDraft),
                    errorMessageRes = null,
                    createSaveResult = null,
                    manualLogSaveResult = null,
                    slotResult = null,
                )
            }
        }
    }

    fun updateCountText(countText: String) {
        _uiState.update { state ->
            if (state.isLockedForUpdates()) {
                state
            } else {
                state.copy(
                    countText = sanitizeMedicationCountText(countText),
                    errorMessageRes = null,
                    createSaveResult = null,
                    manualLogSaveResult = null,
                    slotResult = null,
                )
            }
        }
    }

    fun updateAppliedDate(appliedDate: LocalDate) {
        _uiState.update { state ->
            if (state.isLockedForUpdates()) {
                state
            } else {
                state.copy(
                    appliedDate = appliedDate,
                    manualLogSaveResult = null,
                )
            }
        }
    }

    fun updateAppliedTime(appliedTime: LocalTime) {
        _uiState.update { state ->
            if (state.isLockedForUpdates()) {
                state
            } else {
                state.copy(
                    appliedTime = appliedTime.withSecond(0).withNano(0),
                    manualLogSaveResult = null,
                )
            }
        }
    }

    fun saveGroupSlot(): Job? {
        val currentState = _uiState.value
        if (currentState.isSaving || currentState.isSaved) {
            return null
        }
        validateSlot(currentState)?.let { errorMessageRes ->
            _uiState.update {
                it.copy(
                    errorMessageRes = errorMessageRes,
                    createSaveResult = null,
                    manualLogSaveResult = null,
                    slotResult = null,
                )
            }
            return null
        }
        val operationGeneration = nextSaveGeneration()

        _uiState.update {
            it.copy(
                isSaving = true,
                errorMessageRes = null,
                createSaveResult = null,
                manualLogSaveResult = null,
                slotResult = null,
            )
        }
        return viewModelScope.launch {
            try {
                saveMedicineThen(currentState, operationGeneration) { medicine ->
                    val applicationType = resolvedApplicationType(currentState)
                    updateIfCurrent(operationGeneration) {
                        it.copy(
                            isSaving = false,
                            isSaved = true,
                            slotResult = MedicineSlotResult(
                                medicineUuid = medicine.uuid,
                                applicationType = applicationType,
                                doseInstruction = resolvedDoseInstruction(currentState),
                                count = resolvedMedicationCountForSave(
                                    applicationType,
                                    currentState.countText,
                                ),
                            ),
                        )
                    }
                }
            } catch (exception: CancellationException) {
                clearSavingIfCurrent(operationGeneration)
                throw exception
            }
        }
    }

    fun saveManualLog(): Job? {
        val currentState = _uiState.value
        if (currentState.isSaving || currentState.isSaved) {
            return null
        }
        validateSlot(currentState)?.let { errorMessageRes ->
            _uiState.update {
                it.copy(
                    errorMessageRes = errorMessageRes,
                    createSaveResult = null,
                    manualLogSaveResult = null,
                    slotResult = null,
                )
            }
            return null
        }
        val operationGeneration = nextSaveGeneration()

        _uiState.update {
            it.copy(
                isSaving = true,
                errorMessageRes = null,
                createSaveResult = null,
                manualLogSaveResult = null,
                slotResult = null,
            )
        }
        return viewModelScope.launch {
            try {
                saveMedicineThen(currentState, operationGeneration) { medicine ->
                    val applicationType = resolvedApplicationType(currentState)
                    val saveResult = saveManualMedicineLog(
                        medicationLogRepository = medicationLogRepository,
                        medicationReminderScheduler = medicationReminderScheduler,
                        medicineUuid = medicine.uuid,
                        resolvedApplicationType = applicationType,
                        doseInstruction = resolvedDoseInstruction(currentState),
                        count = resolvedMedicationCountForSave(
                            applicationType,
                            currentState.countText,
                        ),
                        appliedDate = currentState.appliedDate,
                        appliedTime = currentState.appliedTime,
                        appliedZoneId = currentState.appliedZoneId,
                    )
                    updateIfCurrent(operationGeneration) {
                        it.copy(
                            isSaving = false,
                            isSaved = saveResult == null,
                            manualLogSaveResult = saveResult,
                        )
                    }
                }
            } catch (exception: CancellationException) {
                clearSavingIfCurrent(operationGeneration)
                throw exception
            }
        }
    }

    fun consumeSavedState() {
        _uiState.update {
            it.copy(
                isSaved = false,
                slotResult = null,
            )
        }
    }

    fun consumeManualLogSaveResult() {
        _uiState.update { it.copy(manualLogSaveResult = null) }
    }

    private suspend fun saveMedicineThen(
        state: NewMedicineSlotUiState,
        operationGeneration: Int,
        onCreated: suspend (Medicine) -> Unit,
    ) {
        // validateSlot already ran before this point; validate again inside the shared create helper as a defensive guard.
        when (
            val result = createMedicineFromDraft(
                medicineRepository = medicineRepository,
                draft = state.medicineDraft,
            )
        ) {
            is MedicineCreateResult.Success -> {
                if (isCurrentSave(operationGeneration)) {
                    onCreated(result.medicine)
                }
            }

            is MedicineCreateResult.ValidationError -> {
                updateIfCurrent(operationGeneration) {
                    it.copy(
                        isSaving = false,
                        errorMessageRes = result.messageRes,
                        createSaveResult = null,
                    )
                }
            }

            is MedicineCreateResult.SaveFailure -> {
                updateIfCurrent(operationGeneration) {
                    it.copy(
                        isSaving = false,
                        createSaveResult = result.saveResult,
                    )
                }
            }
        }
    }

    @StringRes
    private fun validateSlot(state: NewMedicineSlotUiState): Int? {
        validateMedicineDraftForCreate(state.medicineDraft)?.let { return it }
        state.doseInstructionDraft.validationErrorRes()?.let { return it }
        return medicationCountValidationErrorRes(
            applicationType = resolvedApplicationType(state),
            countText = state.countText,
        )
    }

    private fun resolvedApplicationType(state: NewMedicineSlotUiState): MedicationApplicationType {
        return resolvedApplicationTypeForDose(
            preparationType = state.medicineDraft.inferredOrSelectedPreparationType()
                ?: state.doseInstructionDraft.preparationType,
            doseInstructionDraft = state.doseInstructionDraft,
        )
    }

    private fun resolvedDoseInstruction(state: NewMedicineSlotUiState): DoseInstruction {
        val resolvedApplicationType = resolvedApplicationType(state)
        return if (resolvedApplicationType == MedicationApplicationType.PATCH_OFF) {
            DoseInstruction.Noop
        } else {
            state.doseInstructionDraft.toDoseInstruction()
        }
    }

    private fun nextSaveGeneration(): Int {
        saveGeneration += 1
        return saveGeneration
    }

    private fun isCurrentSave(operationGeneration: Int): Boolean {
        return operationGeneration == saveGeneration
    }

    private fun updateIfCurrent(
        operationGeneration: Int,
        transform: (NewMedicineSlotUiState) -> NewMedicineSlotUiState,
    ) {
        if (isCurrentSave(operationGeneration)) {
            _uiState.update(transform)
        }
    }

    private fun clearSavingIfCurrent(operationGeneration: Int) {
        updateIfCurrent(operationGeneration) {
            it.copy(isSaving = false)
        }
    }

    private fun NewMedicineSlotUiState.isLockedForUpdates(): Boolean {
        return isSaving || isSaved
    }
}

data class NewMedicineSlotUiState(
    val medicineDraft: MedicinePickerUiState = defaultMedicineDraft(),
    val doseInstructionDraft: DoseInstructionDraftUiState =
        defaultMedicineDraft().toDoseInstructionDraft(),
    val countText: String = "1",
    val appliedDate: LocalDate = LocalDate.now(),
    val appliedTime: LocalTime = LocalTime.now().withSecond(0).withNano(0),
    val appliedZoneId: ZoneId,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    @param:StringRes val errorMessageRes: Int? = null,
    val createSaveResult: CreateMedicineSaveResult? = null,
    val manualLogSaveResult: MedicineSlotDraftSaveResult? = null,
    val slotResult: MedicineSlotResult? = null,
)
