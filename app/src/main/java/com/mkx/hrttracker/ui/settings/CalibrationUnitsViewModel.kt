package com.mkx.hrttracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.settings.SettingsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CalibrationUnitsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<CalibrationUnitsUiState> = settingsRepository.settingsState
        .map { settingsState ->
            CalibrationUnitsUiState(settingsState = settingsState)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = CalibrationUnitsUiState(),
        )

    fun setCalibrationDefaultUnit(
        analyteKey: BloodAnalyteKey,
        unit: BloodUnitKey,
    ) {
        viewModelScope.launch {
            settingsRepository.setCalibrationDefaultUnit(analyteKey, unit)
        }
    }
}

data class CalibrationUnitsUiState(
    val settingsState: SettingsState = SettingsState(),
)
