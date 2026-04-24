package com.mkx.hrttracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResultInput
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class CalibrationViewModel @Inject constructor(
    private val bloodTestRepository: BloodTestRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CalibrationUiState(isLoading = true))
    val uiState: StateFlow<CalibrationUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            refreshPanels()
        }
    }

    suspend fun addE2Result(
        e2ValueText: String,
        collectedAt: Instant,
        collectedAtTimeZoneId: String,
    ): Boolean {
        val parsedValue = parseCalibrationNumericInput(e2ValueText) ?: return false
        _uiState.update { state ->
            state.copy(isSaving = true)
        }

        return runCatching {
            bloodTestRepository.savePanel(
                uuid = null,
                collectedAt = collectedAt,
                collectedAtTimeZoneId = collectedAtTimeZoneId,
                notes = null,
                results = listOf(
                    BloodTestResultInput.Builtin(
                        analyteKey = BloodAnalyteKey.E2,
                        unit = BloodUnitKey.PG_ML,
                        value = parsedValue,
                    )
                ),
                now = Instant.now()
            )
            refreshPanels()
            true
        }.getOrElse {
            _uiState.update { state ->
                state.copy(isSaving = false)
            }
            false
        }
    }

    private suspend fun refreshPanels() {
        _uiState.update { state ->
            state.copy(isLoading = true)
        }
        val panels = bloodTestRepository.getPanels()
        _uiState.update { state ->
            state.copy(
                panels = panels,
                isLoading = false,
                isSaving = false
            )
        }
    }
}

data class CalibrationUiState(
    val panels: List<BloodTestPanel> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
)

internal fun parseCalibrationNumericInput(input: String): Double? {
    return input.trim()
        .replace(',', '.')
        .toDoubleOrNull()
        ?.takeIf(Double::isFinite)
}
