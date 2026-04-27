package com.mkx.hrttracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.bloodtest.CustomBloodAnalyte
import com.mkx.hrttracker.model.settings.SettingsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CalibrationUnitsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val bloodTestRepository: BloodTestRepository,
) : ViewModel() {
    private val customAnalytes = MutableStateFlow<List<CustomBloodAnalyte>>(emptyList())
    private val isLoadingCustomAnalytes = MutableStateFlow(true)
    private val isArchivingCustomAnalyte = MutableStateFlow(false)
    private val archiveCustomAnalyteResult = MutableStateFlow<CalibrationArchiveCustomAnalyteResult?>(null)

    val uiState: StateFlow<CalibrationUnitsUiState> = combine(
        settingsRepository.settingsState,
        customAnalytes,
        isLoadingCustomAnalytes,
        isArchivingCustomAnalyte,
        archiveCustomAnalyteResult,
    ) { settingsState, customAnalytes, isLoadingCustomAnalytes, isArchivingCustomAnalyte, archiveCustomAnalyteResult ->
        CalibrationUnitsUiState(
            settingsState = settingsState,
            customAnalytes = customAnalytes,
            isLoadingCustomAnalytes = isLoadingCustomAnalytes,
            isArchivingCustomAnalyte = isArchivingCustomAnalyte,
            archiveCustomAnalyteResult = archiveCustomAnalyteResult,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = CalibrationUnitsUiState(),
        )

    init {
        refreshCustomAnalytes()
    }

    fun setCalibrationDefaultUnit(
        analyteKey: BloodAnalyteKey,
        unit: BloodUnitKey,
    ) {
        viewModelScope.launch {
            settingsRepository.setCalibrationDefaultUnit(analyteKey, unit)
        }
    }

    suspend fun saveCustomAnalyte(
        uuid: UUID?,
        abbreviation: String,
        name: String,
        unitLabel: String,
    ): Throwable? {
        return runCatching {
            bloodTestRepository.saveCustomAnalyte(
                uuid = uuid,
                abbreviation = abbreviation,
                name = name,
                unitLabel = unitLabel,
                now = Instant.now(),
            )
            customAnalytes.value = bloodTestRepository.getActiveCustomAnalytes()
        }.exceptionOrNull()
    }

    fun archiveCustomAnalyte(uuid: UUID) {
        if (isArchivingCustomAnalyte.value) {
            return
        }

        viewModelScope.launch {
            isArchivingCustomAnalyte.value = true
            archiveCustomAnalyteResult.value = null
            val result = runCatching {
                bloodTestRepository.archiveCustomAnalyte(
                    uuid = uuid,
                    now = Instant.now(),
                )
                customAnalytes.value = bloodTestRepository.getActiveCustomAnalytes()
            }.fold(
                onSuccess = { CalibrationArchiveCustomAnalyteResult.SUCCESS },
                onFailure = { CalibrationArchiveCustomAnalyteResult.FAILURE },
            )
            isArchivingCustomAnalyte.value = false
            archiveCustomAnalyteResult.value = result
        }
    }

    fun consumeArchiveCustomAnalyteResult() {
        archiveCustomAnalyteResult.value = null
    }

    suspend fun hasResultsForCustomAnalyte(uuid: UUID): Boolean {
        return runCatching {
            bloodTestRepository.hasResultsForCustomAnalyte(uuid)
        }.getOrDefault(false)
    }

    private fun refreshCustomAnalytes() {
        viewModelScope.launch {
            isLoadingCustomAnalytes.value = true
            customAnalytes.value = runCatching {
                bloodTestRepository.getActiveCustomAnalytes()
            }.getOrDefault(emptyList())
            isLoadingCustomAnalytes.value = false
        }
    }
}

data class CalibrationUnitsUiState(
    val settingsState: SettingsState = SettingsState(),
    val customAnalytes: List<CustomBloodAnalyte> = emptyList(),
    val isLoadingCustomAnalytes: Boolean = false,
    val isArchivingCustomAnalyte: Boolean = false,
    val archiveCustomAnalyteResult: CalibrationArchiveCustomAnalyteResult? = null,
)

enum class CalibrationArchiveCustomAnalyteResult {
    SUCCESS,
    FAILURE,
}
