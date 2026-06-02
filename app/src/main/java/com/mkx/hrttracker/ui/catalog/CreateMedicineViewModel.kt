package com.mkx.hrttracker.ui.catalog

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.ui.medication.MedicinePickerUiState
import com.mkx.hrttracker.ui.medication.defaultMedicineDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
    private var createGeneration = 0

    // Sheet hosts share a single VM instance and can reopen the sheet multiple
    // times in one composition; reset clears the previous attempt so the next
    // open starts from a clean draft.
    fun reset() {
        createGeneration += 1
        _uiState.value = CreateMedicineUiState()
    }

    fun updateDraft(transform: (MedicinePickerUiState) -> MedicinePickerUiState) {
        _uiState.update { state ->
            if (state.isLockedForUpdates()) {
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
        if (_uiState.value.isLockedForUpdates()) {
            return null
        }
        val draft = _uiState.value.draft
        validateMedicineDraftForCreate(draft)?.let { errorMessageRes ->
            _uiState.update {
                it.copy(
                    errorMessageRes = errorMessageRes,
                    saveResult = null,
                )
            }
            return null
        }
        val operationGeneration = nextCreateGeneration()

        _uiState.update {
            it.copy(isSaving = true, isSaved = false, errorMessageRes = null, saveResult = null)
        }
        return viewModelScope.launch {
            try {
                when (
                    val result = createMedicineFromDraft(
                        medicineRepository = medicineRepository,
                        draft = draft,
                    )
                ) {
                    is MedicineCreateResult.Success -> {
                        if (isCurrentCreate(operationGeneration)) {
                            _uiState.update {
                                it.copy(
                                    isSaving = false,
                                    isSaved = true,
                                    errorMessageRes = null,
                                    saveResult = CreateMedicineSaveResult.SUCCESS,
                                )
                            }
                            onCreated(result.medicine.uuid)
                        }
                    }

                    is MedicineCreateResult.ValidationError -> {
                        updateIfCurrent(operationGeneration) {
                            it.copy(
                                isSaving = false,
                                errorMessageRes = result.messageRes,
                                saveResult = null,
                            )
                        }
                    }

                    is MedicineCreateResult.SaveFailure -> {
                        updateIfCurrent(operationGeneration) {
                            it.copy(
                                isSaving = false,
                                saveResult = result.saveResult,
                            )
                        }
                    }
                }
            } catch (exception: CancellationException) {
                clearSavingIfCurrent(operationGeneration)
                throw exception
            }
        }
    }

    // Clears the save result after a failure has been surfaced as a toast so it
    // doesn't re-trigger on recomposition or configuration change.
    fun consumeSaveResult() {
        _uiState.update { it.copy(saveResult = null) }
    }

    private fun nextCreateGeneration(): Int {
        createGeneration += 1
        return createGeneration
    }

    private fun isCurrentCreate(operationGeneration: Int): Boolean {
        return operationGeneration == createGeneration
    }

    private fun updateIfCurrent(
        operationGeneration: Int,
        transform: (CreateMedicineUiState) -> CreateMedicineUiState,
    ) {
        if (isCurrentCreate(operationGeneration)) {
            _uiState.update(transform)
        }
    }

    private fun clearSavingIfCurrent(operationGeneration: Int) {
        updateIfCurrent(operationGeneration) {
            it.copy(isSaving = false)
        }
    }
}

data class CreateMedicineUiState(
    val draft: MedicinePickerUiState = defaultMedicineDraft(),
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    @param:StringRes val errorMessageRes: Int? = null,
    val saveResult: CreateMedicineSaveResult? = null,
) {
    fun isLockedForUpdates(): Boolean = isSaving || isSaved
}

enum class CreateMedicineSaveResult {
    SUCCESS,
    FAILURE_IDENTITY_COLLISION,
    FAILURE_OTHER,
}
