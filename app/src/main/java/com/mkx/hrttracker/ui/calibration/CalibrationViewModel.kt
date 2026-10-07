package com.mkx.hrttracker.ui.calibration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.data.repository.PkCalibrationLiveRepository
import com.mkx.hrttracker.data.repository.PkCalibrationLiveResult
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.ui.pkcalibrationdebug.PkCalibrationUiFixture
import com.mkx.hrttracker.ui.pkcalibrationdebug.PkCalibrationUiFixtureBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class CalibrationViewModel @Inject constructor(
    private val bloodTestRepository: BloodTestRepository,
    private val settingsRepository: SettingsRepository,
    private val pkCalibrationLiveRepository: PkCalibrationLiveRepository,
    private val pkUiFixtureBridge: PkCalibrationUiFixtureBridge,
) : ViewModel() {
    private val isDeletingAllEntries = MutableStateFlow(false)
    private val deleteAllEntriesResult =
        MutableStateFlow<CalibrationDeleteAllEntriesResult?>(null)
    private val cachedPanels = bloodTestRepository.getCachedPanels()

    // Loading also waits for the first live evaluation so the lab adjustment
    // section is in the first rendered frame rather than popping in above
    // the lab list.
    val uiState: StateFlow<CalibrationUiState> = combine(
        bloodTestRepository.observePanels(),
        settingsRepository.settingsState,
        isDeletingAllEntries,
        deleteAllEntriesResult,
        pkCalibrationLiveRepository.liveState,
    ) { panels, settingsState, isDeletingAllEntries, deleteAllEntriesResult, live ->
        CalibrationUiState(
            panels = panels,
            settingsState = settingsState,
            isLoading = live == null,
            isDeletingAllEntries = isDeletingAllEntries,
            deleteAllEntriesResult = deleteAllEntriesResult,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = CalibrationUiState(
            panels = cachedPanels.orEmpty(),
            settingsState = settingsRepository.settingsState.value,
            isLoading = cachedPanels == null || pkCalibrationLiveRepository.liveState.value == null,
        ),
    )

    /**
     * The status-surface projection. Null only while the first live
     * evaluation is loading. A finished evaluation that produced nothing
     * shows the numeric-failure row with Try again instead of hiding the
     * section.
     */
    val pkCalibrationState: StateFlow<PkCalibrationScreenState?> = combine(
        pkCalibrationLiveRepository.liveState,
        pkUiFixtureBridge.fixture,
        ::pkScreenState,
    )
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            // Seeded from the current values so the section is in the first
            // frame; a section inserted above the viewport one frame later
            // shifts the keyed LazyColumn down by its own height.
            initialValue = pkScreenState(
                pkCalibrationLiveRepository.liveState.value,
                pkUiFixtureBridge.fixture.value,
            ),
        )

    private fun pkScreenState(
        liveState: PkCalibrationLiveResult?,
        fixture: PkCalibrationUiFixture?,
    ): PkCalibrationScreenState? = when {
        // Debug harness fixture drives the real surface.
        fixture != null -> PkCalibrationScreenState(
            ui = pkCalibrationUiState(fixture.result, fixture.render),
            excludedResultIds = fixture.excludedResultIds,
        )

        liveState == null -> null

        else -> liveState.live?.let(::pkCalibrationScreenState)
            ?: pkCalibrationUnavailableScreenState()
    }

    /** Null until the stored flag is read; false exactly once per install/restore. */
    val pkIntroSeen: StateFlow<Boolean?> = settingsRepository.pkCalibrationIntroSeen
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun markPkIntroSeen() {
        viewModelScope.launch { settingsRepository.setPkCalibrationIntroSeen(true) }
    }

    fun retryPkCalibration() {
        pkCalibrationLiveRepository.retry()
    }

    fun deleteAllCalibrationEntries() {
        if (isDeletingAllEntries.value) {
            return
        }

        viewModelScope.launch {
            isDeletingAllEntries.value = true
            // Must complete even if the coroutine is cancelled mid-write — a
            // partially applied delete-all leaves calibration panels stranded.
            val result = withContext(NonCancellable) {
                runCatching {
                    bloodTestRepository.deleteAllPanels()
                }.fold(
                    onSuccess = { CalibrationDeleteAllEntriesResult.SUCCESS },
                    onFailure = { CalibrationDeleteAllEntriesResult.FAILURE },
                )
            }
            isDeletingAllEntries.value = false
            deleteAllEntriesResult.value = result
        }
    }

    fun consumeDeleteAllEntriesResult() {
        deleteAllEntriesResult.value = null
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
