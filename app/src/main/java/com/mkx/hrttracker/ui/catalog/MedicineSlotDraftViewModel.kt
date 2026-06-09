package com.mkx.hrttracker.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineStockRepository
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.reminder.PostLogStockWarning
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MedicineSlotDraftViewModel @Inject constructor(
    private val medicationLogRepository: MedicationLogRepository,
    private val medicineStockRepository: MedicineStockRepository,
    private val medicationReminderScheduler: MedicationReminderScheduler,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        MedicineSlotDraftUiState(appliedZoneId = ZoneId.systemDefault())
    )
    val uiState: StateFlow<MedicineSlotDraftUiState> = _uiState.asStateFlow()

    internal constructor(
        medicationLogRepository: MedicationLogRepository,
        medicineStockRepository: MedicineStockRepository,
        medicationReminderScheduler: MedicationReminderScheduler,
        initialAppliedZoneId: ZoneId,
    ) : this(
        medicationLogRepository = medicationLogRepository,
        medicineStockRepository = medicineStockRepository,
        medicationReminderScheduler = medicationReminderScheduler,
    ) {
        _uiState.value = _uiState.value.copy(appliedZoneId = initialAppliedZoneId)
    }

    fun resetManualLogDraft() {
        _uiState.update {
            MedicineSlotDraftUiState(appliedZoneId = it.appliedZoneId)
        }
    }

    fun previewStateFor(
        medicineUuid: UUID,
        hypotheticalStock: MedicineStock,
    ): MedicineStockState? = medicineStockRepository.previewState(medicineUuid, hypotheticalStock)

    fun updateAppliedDate(appliedDate: LocalDate) {
        _uiState.update { state ->
            if (state.isLockedForUpdates()) {
                state
            } else {
                state.copy(
                    appliedDate = appliedDate,
                    saveResult = null,
                    postLogStockWarning = null,
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
                    saveResult = null,
                    postLogStockWarning = null,
                )
            }
        }
    }

    fun saveManualLog(
        medicineUuid: UUID,
        applicationType: MedicationApplicationType,
        doseInstruction: DoseInstruction,
        count: Int,
        doseAmountDelta: Double? = null,
    ) {
        val currentState = _uiState.value
        if (currentState.isSaving || currentState.isSaved) {
            return
        }

        _uiState.update {
            it.copy(
                isSaving = true,
                saveResult = null,
            )
        }
        viewModelScope.launch {
            try {
                val saveResult = saveManualMedicineLog(
                    medicationLogRepository = medicationLogRepository,
                    medicineStockRepository = medicineStockRepository,
                    medicationReminderScheduler = medicationReminderScheduler,
                    medicineUuid = medicineUuid,
                    resolvedApplicationType = applicationType,
                    doseInstruction = doseInstruction,
                    count = count,
                    doseAmountDelta = doseAmountDelta,
                    appliedDate = currentState.appliedDate,
                    appliedTime = currentState.appliedTime,
                    appliedZoneId = currentState.appliedZoneId,
                )
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isSaved = saveResult.isSuccess,
                        saveResult = saveResult.saveResult,
                        postLogStockWarning = saveResult.postLogStockWarning,
                    )
                }
            } catch (exception: CancellationException) {
                _uiState.update { it.copy(isSaving = false) }
                throw exception
            }
        }
    }

    fun consumeSavedState() {
        _uiState.update { it.copy(isSaved = false, postLogStockWarning = null) }
    }

    fun consumeSaveResult() {
        _uiState.update { it.copy(saveResult = null) }
    }

    private fun MedicineSlotDraftUiState.isLockedForUpdates(): Boolean {
        return isSaving || isSaved
    }
}

data class MedicineSlotDraftUiState(
    val appliedDate: LocalDate = LocalDate.now(),
    val appliedTime: LocalTime = LocalTime.now().withSecond(0).withNano(0),
    val appliedZoneId: ZoneId,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val saveResult: MedicineSlotDraftSaveResult? = null,
    val postLogStockWarning: PostLogStockWarning? = null,
)

enum class MedicineSlotDraftSaveResult {
    FAILURE,
}
