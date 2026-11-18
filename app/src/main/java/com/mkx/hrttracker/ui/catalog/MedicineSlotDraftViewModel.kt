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
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MedicineSlotDraftViewModel @Inject constructor(
    private val medicationLogRepository: MedicationLogRepository,
    private val medicineStockRepository: MedicineStockRepository,
    private val medicationReminderScheduler: MedicationReminderScheduler,
    private val clock: Clock,
) : ViewModel() {
    private val _uiState = MutableStateFlow(freshState(ZoneId.systemDefault()))
    val uiState: StateFlow<MedicineSlotDraftUiState> = _uiState.asStateFlow()

    internal constructor(
        medicationLogRepository: MedicationLogRepository,
        medicineStockRepository: MedicineStockRepository,
        medicationReminderScheduler: MedicationReminderScheduler,
        initialAppliedZoneId: ZoneId,
        clock: Clock = Clock.systemUTC(),
    ) : this(
        medicationLogRepository = medicationLogRepository,
        medicineStockRepository = medicineStockRepository,
        medicationReminderScheduler = medicationReminderScheduler,
        clock = clock,
    ) {
        _uiState.value = freshState(initialAppliedZoneId)
    }

    fun resetManualLogDraft() {
        _uiState.update { freshState(it.appliedZoneId) }
    }

    // Now on the clock, on the pass of a repeated fall-back hour it is on.
    private fun freshState(zoneId: ZoneId): MedicineSlotDraftUiState {
        val now = clock.instant().atZone(zoneId)
        return MedicineSlotDraftUiState(
            appliedDate = now.toLocalDate(),
            appliedTime = now.toLocalTime().withSecond(0).withNano(0),
            appliedZoneId = zoneId,
            appliedOffset = now.offset,
        )
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
                    appliedOffset = currentState.appliedOffset,
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
    val appliedDate: LocalDate,
    val appliedTime: LocalTime,
    val appliedZoneId: ZoneId,
    val appliedOffset: ZoneOffset?,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val saveResult: MedicineSlotDraftSaveResult? = null,
    val postLogStockWarning: PostLogStockWarning? = null,
)

enum class MedicineSlotDraftSaveResult {
    FAILURE,
}
