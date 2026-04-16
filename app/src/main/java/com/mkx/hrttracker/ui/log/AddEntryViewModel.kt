package com.mkx.hrttracker.ui.log

import androidx.lifecycle.ViewModel
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
import javax.inject.Inject

@HiltViewModel
class AddEntryViewModel @Inject constructor(
    private val medicationLogRepository: MedicationLogRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AddEntryUiState())
    val uiState: StateFlow<AddEntryUiState> = _uiState.asStateFlow()

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

            medicationLogRepository.addEntry(
                routeOfAdministration = currentState.routeOfAdministration,
                medicineName = trimmedName,
                dosageMgAsMedicine = parsedDose!!,
                appliedAt = parsedTime!!
            )

            _uiState.value = AddEntryUiState(
                isSaved = true
            )
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
}

data class AddEntryUiState(
    val routeOfAdministration: RouteOfAdministration = RouteOfAdministration.OTHER,
    val medicineName: String = "",
    val dosageMg: String = "",
    val appliedAtInput: String = LocalDateTime.now().format(INPUT_TIME_FORMATTER),
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessageRes: Int? = null,
)

val INPUT_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
