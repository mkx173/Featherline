package com.mkx.hrttracker.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.model.medication.RouteOfAdministration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AddEntryViewModel @Inject constructor(
    private val medicationLogRepository: MedicationLogRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(AddEntryUiState())
    val uiState: StateFlow<AddEntryUiState> = _uiState.asStateFlow()
    private val editingEntryId = savedStateHandle.get<String>(ENTRY_ID_ARG)

    init {
        if (editingEntryId != null) {
            loadEntryForEditing(editingEntryId)
        }
    }

    fun updateRoute(routeOfAdministration: RouteOfAdministration) {
        _uiState.update {
            it.copy(
                routeOfAdministration = routeOfAdministration,
                errorMessageRes = null
            )
        }
    }

    fun updateMedicineName(medicineName: String) {
        _uiState.update {
            it.copy(
                medicineName = medicineName,
                errorMessageRes = null
            )
        }
    }

    fun updateDosageMg(dosageMg: String) {
        _uiState.update {
            it.copy(
                dosageMg = dosageMg,
                errorMessageRes = null
            )
        }
    }

    fun updateAppliedAt(appliedAtInput: String) {
        _uiState.update {
            it.copy(
                appliedAtInput = appliedAtInput,
                errorMessageRes = null
            )
        }
    }

    fun saveEntry() {
        val currentState = _uiState.value
        val trimmedName = currentState.medicineName.trim()
        val parsedDose = currentState.dosageMg.toDoubleOrNull()
        val parsedTime = parseAppliedAt(currentState.appliedAtInput)

        val errorRes = when {
            trimmedName.isEmpty() -> R.string.validation_name_required
            parsedDose == null || parsedDose <= 0.0 -> R.string.validation_dose_required
            parsedTime == null -> R.string.validation_time_required
            else -> null
        }

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
                routeOfAdministration = currentState.routeOfAdministration,
                medicineName = trimmedName,
                dosageMgAsMedicine = parsedDose!!,
                appliedAt = parsedTime!!
            )

            _uiState.update {
                it.copy(
                    medicineName = trimmedName,
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

    private fun parseAppliedAt(input: String): Instant? {
        return try {
            LocalDateTime.parse(input, INPUT_TIME_FORMATTER)
                .atZone(ZoneId.systemDefault())
                .toInstant()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun loadEntryForEditing(entryId: String) {
        val uuid = runCatching { UUID.fromString(entryId) }.getOrNull() ?: return

        viewModelScope.launch {
            val entry = medicationLogRepository.getEntry(uuid) ?: return@launch

            _uiState.value = AddEntryUiState(
                editingEntryId = entry.uuid.toString(),
                routeOfAdministration = entry.routeOfAdministration,
                medicineName = entry.medicineName,
                dosageMg = entry.dosageMgAsMedicine.toInputString(),
                appliedAtInput = entry.appliedAt
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
                    .format(INPUT_TIME_FORMATTER)
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
        const val ENTRY_ID_ARG = "entryId"
    }
}

data class AddEntryUiState(
    val editingEntryId: String? = null,
    val routeOfAdministration: RouteOfAdministration = RouteOfAdministration.OTHER,
    val medicineName: String = "",
    val dosageMg: String = "",
    val appliedAtInput: String = LocalDateTime.now().format(INPUT_TIME_FORMATTER),
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessageRes: Int? = null,
) {
    val isEditing: Boolean
        get() = editingEntryId != null
}

val INPUT_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
