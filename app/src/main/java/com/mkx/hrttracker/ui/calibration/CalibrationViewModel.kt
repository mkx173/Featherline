package com.mkx.hrttracker.ui.calibration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.settings.SettingsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CalibrationViewModel @Inject constructor(
    private val bloodTestRepository: BloodTestRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val isDeletingAllEntries = MutableStateFlow(false)
    private val deleteAllEntriesResult =
        MutableStateFlow<CalibrationDeleteAllEntriesResult?>(null)

    val uiState: StateFlow<CalibrationUiState> = combine(
        bloodTestRepository.observePanels(),
        settingsRepository.settingsState,
        isDeletingAllEntries,
        deleteAllEntriesResult,
    ) { panels, settingsState, isDeletingAllEntries, deleteAllEntriesResult ->
        CalibrationUiState(
            panels = panels,
            settingsState = settingsState,
            isLoading = false,
            isDeletingAllEntries = isDeletingAllEntries,
            deleteAllEntriesResult = deleteAllEntriesResult,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = CalibrationUiState(isLoading = true),
    )

    init {
        preloadCustomAnalytes()
    }

    fun deleteAllCalibrationEntries() {
        if (isDeletingAllEntries.value) {
            return
        }

        viewModelScope.launch {
            isDeletingAllEntries.value = true
            val result = runCatching {
                bloodTestRepository.deleteAllPanels()
            }.fold(
                onSuccess = { CalibrationDeleteAllEntriesResult.SUCCESS },
                onFailure = { CalibrationDeleteAllEntriesResult.FAILURE },
            )
            isDeletingAllEntries.value = false
            deleteAllEntriesResult.value = result
        }
    }

    fun consumeDeleteAllEntriesResult() {
        deleteAllEntriesResult.value = null
    }

    private fun preloadCustomAnalytes() {
        viewModelScope.launch {
            runCatching {
                bloodTestRepository.preloadActiveCustomAnalytes()
            }
        }
    }
}

data class CalibrationUiState(
    val panels: List<BloodTestPanel> = emptyList(),
    val settingsState: SettingsState = SettingsState(),
    val isLoading: Boolean = false,
    val isDeletingAllEntries: Boolean = false,
    val deleteAllEntriesResult: CalibrationDeleteAllEntriesResult? = null,
)

enum class CalibrationDeleteAllEntriesResult {
    SUCCESS,
    FAILURE,
}

internal fun parseCalibrationNumericInput(input: String): Double? {
    return input.trim()
        .replace(',', '.')
        .toDoubleOrNull()
        ?.takeIf { value -> value.isFinite() && value >= 0.0 }
}
