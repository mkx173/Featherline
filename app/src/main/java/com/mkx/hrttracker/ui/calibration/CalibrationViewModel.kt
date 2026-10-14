package com.mkx.hrttracker.ui.calibration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.data.repository.PkCalibrationLiveRepository
import com.mkx.hrttracker.data.repository.PkCalibrationLiveResult
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.Held
import com.mkx.hrttracker.util.holdWhileUpdating
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
) : ViewModel() {
    private val isDeletingAllEntries = MutableStateFlow(false)
    private val deleteAllEntriesResult =
        MutableStateFlow<CalibrationDeleteAllEntriesResult?>(null)
    private val cachedPanels = bloodTestRepository.getCachedPanels()

    // Seeded from the cached values so the section is in the first frame. A
    // rebuild already in flight on entry holds the section on the last solve;
    // before the first solve there is nothing to hold, and the page stays
    // loading until the first settled value.
    private val seedData: CalibrationData? = pkCalibrationLiveRepository.liveState.value?.let { live ->
        CalibrationData(panels = cachedPanels, live = live, updating = false)
    }

    private val screenVisible = MutableStateFlow(false)

    // Only the lab adjustment section is held while a lab write's rebuild
    // lands; the lab list shows the write at once.
    private val heldData: StateFlow<Held<CalibrationData>> = combine(
        bloodTestRepository.observePanels(),
        pkCalibrationLiveRepository.liveState,
        pkCalibrationLiveRepository.updating,
    ) { panels, live, updating -> CalibrationData(panels, live, updating) }
        .holdWhileUpdating(screenVisible, settled = seedData) { data -> data.updating }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = Held(
                seedData ?: CalibrationData(panels = null, live = null, updating = true),
                isUpdating = false,
            ),
        )

    // Loading also waits for the first live evaluation so the lab adjustment
    // section is in the first rendered frame rather than popping in above
    // the lab list.
    val uiState: StateFlow<CalibrationUiState> = combine(
        heldData,
        settingsRepository.settingsState,
        isDeletingAllEntries,
        deleteAllEntriesResult,
    ) { held, settingsState, isDeletingAllEntries, deleteAllEntriesResult ->
        calibrationUiState(held, settingsState).copy(
            isDeletingAllEntries = isDeletingAllEntries,
            deleteAllEntriesResult = deleteAllEntriesResult,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = calibrationUiState(heldData.value, settingsRepository.settingsState.value),
    )

    private fun calibrationUiState(
        held: Held<CalibrationData>,
        settingsState: SettingsState,
    ) = CalibrationUiState(
        panels = held.latest.panels.orEmpty(),
        settingsState = settingsState,
        isLoading = held.latest.panels == null || held.value.live == null,
        isUpdating = held.isUpdating,
    )

    /**
     * The status-surface projection. Null only while the first live
     * evaluation is loading. A finished evaluation that produced nothing
     * shows the numeric-failure row with Try again instead of hiding the
     * section.
     */
    val pkCalibrationState: StateFlow<PkCalibrationScreenState?> = heldData
        .map { held -> pkScreenState(held.value.live) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            // Seeded from the current values so the section is in the first
            // frame; a section inserted above the viewport one frame later
            // shifts the keyed LazyColumn down by its own height.
            initialValue = pkScreenState(heldData.value.value.live),
        )

    private fun pkScreenState(
        liveState: PkCalibrationLiveResult?,
    ): PkCalibrationScreenState? = when {
        liveState == null -> null

        else -> liveState.live?.let(::pkCalibrationScreenState)
            ?: pkCalibrationUnavailableScreenState()
    }

    /** Null until the stored flag is read; false exactly once per install/restore. */
    val pkIntroSeen: StateFlow<Boolean?> = settingsRepository.pkCalibrationIntroSeen
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setScreenVisible(visible: Boolean) {
        screenVisible.value = visible
    }

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

private data class CalibrationData(
    /** Null until the first read. */
    val panels: List<BloodTestPanel>?,
    val live: PkCalibrationLiveResult?,
    val updating: Boolean,
)

data class CalibrationUiState(
    val panels: List<BloodTestPanel> = emptyList(),
    val settingsState: SettingsState = SettingsState(),
    val isLoading: Boolean = false,
    /** Shows the top updating indicator; the lab adjustment section is held until the rebuild lands. */
    val isUpdating: Boolean = false,
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
