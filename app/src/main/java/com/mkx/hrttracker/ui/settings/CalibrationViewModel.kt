package com.mkx.hrttracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CalibrationViewModel @Inject constructor(
    private val bloodTestRepository: BloodTestRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CalibrationUiState(isLoading = true))
    val uiState: StateFlow<CalibrationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            bloodTestRepository.observePanels().collect { panels ->
                _uiState.update { state ->
                    state.copy(panels = panels, isLoading = false)
                }
            }
        }
    }
}

data class CalibrationUiState(
    val panels: List<BloodTestPanel> = emptyList(),
    val isLoading: Boolean = false,
)

internal fun parseCalibrationNumericInput(input: String): Double? {
    return input.trim()
        .replace(',', '.')
        .toDoubleOrNull()
        ?.takeIf { value -> value.isFinite() && value >= 0.0 }
}
