package com.mkx.hrttracker.ui.calibration

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.data.repository.CalibrationEstimate
import com.mkx.hrttracker.data.repository.E2EstimateRepository
import com.mkx.hrttracker.data.repository.E2Pending
import com.mkx.hrttracker.data.repository.E2State
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.di.ProcessLifecycleState
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.AppTimeSource
import com.mkx.hrttracker.util.Held
import com.mkx.hrttracker.util.holdWhileUpdating
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class CalibrationViewModel @Inject constructor(
    private val bloodTestRepository: BloodTestRepository,
    private val settingsRepository: SettingsRepository,
    e2EstimateRepository: E2EstimateRepository,
    private val homeSnapshotRepository: HomeSnapshotRepository,
    @ProcessLifecycleState processLifecycleState: StateFlow<@JvmSuppressWildcards Lifecycle.State>,
    appTimeSource: AppTimeSource,
) : ViewModel() {
    private val isDeletingAllEntries = MutableStateFlow(false)
    private val deleteAllEntriesResult =
        MutableStateFlow<CalibrationDeleteAllEntriesResult?>(null)

    // The lab list is live, seeded from the cached values so it is in the
    // first frame. Null until the first read.
    private val panels: StateFlow<List<BloodTestPanel>?> = bloodTestRepository.observePanels()
        .stateIn(viewModelScope, SharingStarted.Eagerly, bloodTestRepository.getCachedPanels())

    private val screenVisible = MutableStateFlow(false)

    // Whether the last state showed the lab list without the section. Only
    // the states below read and write it, one at a time on Main.
    private var showingListAlone = false

    /**
     * The lab adjustment section, held while a change the user made is
     * derived; the lab list shows the change at once. Observed from the
     * moment the page exists, and again each time the app comes to the
     * foreground, so a return follows the cold-start rule: a key that still
     * matches is in the first state, one that moved shows the loading state
     * until it is derived, or its value at once when it already is. Nothing
     * observes it in the background. Every state is built on Main as it
     * arrives, so a return's first state is in before the first frame.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val heldEstimate: StateFlow<Held<E2State<CalibrationEstimate>>> = processLifecycleState
        .map { state -> state.isAtLeast(Lifecycle.State.STARTED) }
        .distinctUntilChanged()
        .flatMapLatest { inForeground ->
            if (inForeground) e2EstimateRepository.observeCalibration(appTimeSource.currentMinute) else emptyFlow()
        }
        .holdWhileUpdating(screenVisible) { state -> state.pending == E2Pending.USER }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            // Pending: a frame before the observation's first state still
            // waits for the section.
            initialValue = Held(E2State(null, E2Pending.SYSTEM), isUpdating = false),
        )

    val uiState: StateFlow<CalibrationUiState> = combine(
        panels,
        heldEstimate,
        settingsRepository.settingsState,
        isDeletingAllEntries,
        deleteAllEntriesResult,
    ) { panels, held, settingsState, isDeletingAllEntries, deleteAllEntriesResult ->
        calibrationUiState(panels, held, settingsState).copy(
            isDeletingAllEntries = isDeletingAllEntries,
            deleteAllEntriesResult = deleteAllEntriesResult,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = calibrationUiState(panels.value, heldEstimate.value, settingsRepository.settingsState.value),
    )

    private fun calibrationUiState(
        panels: List<BloodTestPanel>?,
        held: Held<E2State<CalibrationEstimate>>,
        settingsState: SettingsState,
    ): CalibrationUiState {
        // Loading also waits while the estimate section is derived, so it is
        // in the first rendered frame rather than popping in above the lab
        // list. With nothing to derive it from (a rebuild pending, or one
        // that keeps failing), the live lab list shows without it, and keeps
        // showing while a derivation that starts later runs: the section
        // appears when derived, rather than the page going back to loading.
        val isLoading = panels == null ||
            (held.value.value == null && held.value.pending != null && !showingListAlone)
        showingListAlone = !isLoading && held.value.value == null
        return CalibrationUiState(
            panels = panels.orEmpty(),
            settingsState = settingsState,
            isLoading = isLoading,
            isUpdating = held.isUpdating,
        )
    }

    /**
     * The status-surface projection. Null while the section loads, or while
     * there is nothing to derive it from. A fit that failed, or a render that
     * threw, shows the numeric-failure row with Try again instead of hiding
     * the section.
     */
    val pkCalibrationState: StateFlow<PkCalibrationScreenState?> = heldEstimate
        .map(::pkScreenState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            // Seeded from the current values so the section is in the first
            // frame; a section inserted above the viewport one frame later
            // shifts the keyed LazyColumn down by its own height.
            initialValue = pkScreenState(heldEstimate.value),
        )

    private fun pkScreenState(held: Held<E2State<CalibrationEstimate>>): PkCalibrationScreenState? =
        held.value.value?.let(::pkCalibrationScreenState)

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

    /** Re-solves with a new fit epoch: a user change, so the section holds until it lands. */
    fun retryPkCalibration() {
        viewModelScope.launch {
            // Completes even if the page closes: Home's estimate is derived
            // again from it too. A failed generation bump leaves the failure
            // row, and its Try again, in place.
            withContext(NonCancellable) {
                runCatching { homeSnapshotRepository.retryCalibration() }
            }
        }
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
    /** Shows the top updating indicator; the lab adjustment section is held until its estimate lands. */
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
