package com.mkx.hrttracker.ui.plan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.MedicationGroupMedicationInput
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.model.medication.RouteOfAdministration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MedicationGroupEditorViewModel @Inject constructor(
    private val medicationGroupRepository: MedicationGroupRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(MedicationGroupEditorUiState())
    val uiState: StateFlow<MedicationGroupEditorUiState> = _uiState.asStateFlow()
    private val editingGroupId = savedStateHandle.get<String>(GROUP_ID_ARG)

    init {
        if (editingGroupId != null) {
            loadGroupForEditing(editingGroupId)
        }
    }

    fun updateGroupName(name: String) {
        _uiState.update {
            it.copy(
                groupName = name,
                errorMessageRes = null
            )
        }
    }

    fun showAddMedicationEditor() {
        _uiState.update {
            it.copy(
                editingMedication = MedicationGroupMedicationItemUiState(),
                medicationEditorErrorMessageRes = null,
                errorMessageRes = null
            )
        }
    }

    fun removeMedication(localId: String) {
        _uiState.update {
            it.copy(
                medications = it.medications.filterNot { medication -> medication.localId == localId },
                errorMessageRes = null
            )
        }
    }

    fun showMedicationEditor(localId: String) {
        _uiState.update { currentState ->
            currentState.copy(
                editingMedication = currentState.medications.firstOrNull { it.localId == localId },
                medicationEditorErrorMessageRes = null,
                errorMessageRes = null
            )
        }
    }

    fun dismissMedicationEditor() {
        _uiState.update { it.copy(editingMedication = null, medicationEditorErrorMessageRes = null) }
    }

    fun updateEditingMedicationRoute(routeOfAdministration: RouteOfAdministration) {
        updateEditingMedication { medication ->
            medication.copy(routeOfAdministration = routeOfAdministration)
        }
    }

    fun updateEditingMedicationName(medicineName: String) {
        updateEditingMedication { medication ->
            medication.copy(medicineName = medicineName)
        }
    }

    fun updateEditingMedicationDosage(dosageMg: String) {
        updateEditingMedication { medication ->
            medication.copy(dosageMg = dosageMg)
        }
    }

    fun saveEditingMedication() {
        val currentState = _uiState.value
        val editingMedication = currentState.editingMedication ?: return
        val trimmedName = editingMedication.medicineName.trim()
        val parsedDose = editingMedication.dosageMg.toDoubleOrNull()

        val errorRes = when {
            trimmedName.isEmpty() -> R.string.validation_group_medication_name_required
            parsedDose == null || parsedDose <= 0.0 -> R.string.validation_group_medication_dose_required
            else -> null
        }

        if (errorRes != null) {
            _uiState.update { it.copy(medicationEditorErrorMessageRes = errorRes) }
            return
        }

        val savedMedication = editingMedication.copy(
            medicineName = trimmedName,
            dosageMg = parsedDose!!.toInputString()
        )

        _uiState.update {
            val existingIndex = it.medications.indexOfFirst { medication ->
                medication.localId == savedMedication.localId
            }
            val updatedMedications = if (existingIndex >= 0) {
                it.medications.toMutableList().apply {
                    this[existingIndex] = savedMedication
                }
            } else {
                it.medications + savedMedication
            }

            it.copy(
                medications = updatedMedications,
                editingMedication = null,
                medicationEditorErrorMessageRes = null,
                errorMessageRes = null
            )
        }
    }

    fun saveGroup() {
        val currentState = _uiState.value
        val trimmedGroupName = currentState.groupName.trim()

        val errorRes = when {
            trimmedGroupName.isEmpty() -> R.string.validation_group_name_required
            currentState.medications.isEmpty() -> R.string.validation_group_medications_required
            else -> null
        }

        if (errorRes != null) {
            _uiState.update { it.copy(errorMessageRes = errorRes) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessageRes = null) }

            medicationGroupRepository.saveGroup(
                uuid = currentState.editingGroupId?.let(UUID::fromString),
                name = trimmedGroupName,
                medications = currentState.medications.map { medication ->
                    MedicationGroupMedicationInput(
                        uuid = medication.persistedMedicationId?.let(UUID::fromString),
                        routeOfAdministration = medication.routeOfAdministration,
                        medicineName = medication.medicineName.trim(),
                        dosageMgAsMedicine = medication.dosageMg.toDouble()
                    )
                }
            )

            _uiState.update {
                it.copy(
                    groupName = trimmedGroupName,
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

    private fun loadGroupForEditing(groupId: String) {
        val uuid = runCatching { UUID.fromString(groupId) }.getOrNull() ?: return

        viewModelScope.launch {
            val group = medicationGroupRepository.getGroup(uuid) ?: return@launch

            _uiState.value = MedicationGroupEditorUiState(
                editingGroupId = group.uuid.toString(),
                groupName = group.name,
                medications = group.medications.map { medication ->
                    MedicationGroupMedicationItemUiState(
                        localId = medication.uuid.toString(),
                        persistedMedicationId = medication.uuid.toString(),
                        routeOfAdministration = medication.routeOfAdministration,
                        medicineName = medication.medicineName,
                        dosageMg = medication.dosageMgAsMedicine.toInputString()
                    )
                }
            )
        }
    }

    private fun updateEditingMedication(
        transform: (MedicationGroupMedicationItemUiState) -> MedicationGroupMedicationItemUiState
    ) {
        _uiState.update { currentState ->
            currentState.copy(
                editingMedication = currentState.editingMedication?.let(transform),
                medicationEditorErrorMessageRes = null
            )
        }
    }

    private fun Double.toInputString(): String {
        return if (this % 1.0 == 0.0) {
            String.format(Locale.US, "%.0f", this)
        } else {
            String.format(Locale.US, "%.2f", this)
        }
    }

    companion object {
        const val GROUP_ID_ARG = "groupId"
    }
}

data class MedicationGroupEditorUiState(
    val editingGroupId: String? = null,
    val groupName: String = "",
    val medications: List<MedicationGroupMedicationItemUiState> = emptyList(),
    val editingMedication: MedicationGroupMedicationItemUiState? = null,
    val medicationEditorErrorMessageRes: Int? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessageRes: Int? = null,
) {
    val isEditing: Boolean
        get() = editingGroupId != null
}

data class MedicationGroupMedicationItemUiState(
    val localId: String = UUID.randomUUID().toString(),
    val persistedMedicationId: String? = null,
    val routeOfAdministration: RouteOfAdministration = RouteOfAdministration.OTHER,
    val medicineName: String = "",
    val dosageMg: String = "",
)
