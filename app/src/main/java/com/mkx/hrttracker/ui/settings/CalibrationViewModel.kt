package com.mkx.hrttracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.settings.SettingsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class CalibrationViewModel @Inject constructor(
    private val bloodTestRepository: BloodTestRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<CalibrationUiState> = combine(
        bloodTestRepository.observePanels(),
        settingsRepository.settingsState,
    ) { panels, settingsState ->
        CalibrationUiState(
            panels = panels,
            settingsState = settingsState,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = CalibrationUiState(isLoading = true),
    )
}

data class CalibrationUiState(
    val panels: List<BloodTestPanel> = emptyList(),
    val settingsState: SettingsState = SettingsState(),
    val isLoading: Boolean = false,
)

internal fun parseCalibrationNumericInput(input: String): Double? {
    return input.trim()
        .replace(',', '.')
        .toDoubleOrNull()
        ?.takeIf { value -> value.isFinite() && value >= 0.0 }
}
