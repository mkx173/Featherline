package com.mkx.hrttracker.ui.medicine

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.MedicineIdentityCollisionException
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
import com.mkx.hrttracker.ui.medication.MedicinePickerUiState
import com.mkx.hrttracker.ui.medication.defaultMedicineDraft
import com.mkx.hrttracker.ui.medication.toNewMedicineRequest
import com.mkx.hrttracker.ui.medication.validationErrorRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CreateMedicineViewModel @Inject constructor(
    private val medicineRepository: MedicineRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateMedicineUiState())
    val uiState: StateFlow<CreateMedicineUiState> = _uiState.asStateFlow()

    // Sheet hosts share a single VM instance and can reopen the sheet multiple
    // times in one composition; reset clears the previous attempt so the next
    // open starts from a clean draft.
    fun reset() {
        _uiState.value = CreateMedicineUiState()
    }

    fun updateDraft(transform: (MedicinePickerUiState) -> MedicinePickerUiState) {
        _uiState.update { state ->
            if (state.isSaving) {
                state
            } else {
                state.copy(
                    draft = transform(state.draft),
                    errorMessageRes = null,
                    saveResult = null,
                )
            }
        }
    }

    fun create(onCreated: (UUID) -> Unit): Job? {
        if (_uiState.value.isSaving) {
            return null
        }
        val draft = _uiState.value.draft
        if (draft.applicationType == MedicationApplicationType.PATCH_OFF) {
            _uiState.update {
                it.copy(
                    errorMessageRes = R.string.validation_preparation_type_required,
                    saveResult = null,
                )
            }
            return null
        }
        draft.validationErrorRes()?.let { errorMessageRes ->
            _uiState.update {
                it.copy(
                    errorMessageRes = errorMessageRes,
                    saveResult = null,
                )
            }
            return null
        }

        _uiState.update {
            it.copy(isSaving = true, errorMessageRes = null, saveResult = null)
        }
        return viewModelScope.launch {
            runCatching {
                val request = draft.toNewMedicineRequest()
                when (request.selectionKind) {
                    MedicationSelectionKind.CATALOG -> medicineRepository.findOrCreateForCatalog(
                        medicationKey = checkNotNull(request.medicationKey),
                        preparation = request.preparation,
                    )

                    MedicationSelectionKind.CUSTOM -> medicineRepository.findOrCreateForCustom(
                        customMedicationName = checkNotNull(request.customMedicationName),
                        displayName = request.displayName,
                        category = request.category,
                        preparation = request.preparation,
                        displayDoseUnit = request.displayDoseUnit,
                    )
                }
            }.onSuccess { medicine ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessageRes = null,
                        saveResult = CreateMedicineSaveResult.SUCCESS,
                    )
                }
                onCreated(medicine.uuid)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveResult = when (error) {
                            is MedicineIdentityCollisionException ->
                                CreateMedicineSaveResult.FAILURE_IDENTITY_COLLISION

                            else -> CreateMedicineSaveResult.FAILURE_OTHER
                        },
                    )
                }
            }
        }
    }
}

data class CreateMedicineUiState(
    val draft: MedicinePickerUiState = defaultMedicineDraft(),
    val isSaving: Boolean = false,
    @param:StringRes val errorMessageRes: Int? = null,
    val saveResult: CreateMedicineSaveResult? = null,
)

enum class CreateMedicineSaveResult {
    SUCCESS,
    FAILURE_IDENTITY_COLLISION,
    FAILURE_OTHER,
}
