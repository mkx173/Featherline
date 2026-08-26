package com.mkx.hrttracker.ui.calibration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.data.repository.PkCalibrationLiveRepository
import com.mkx.hrttracker.data.repository.PkCalibrationLiveState
import com.mkx.hrttracker.data.repository.PkCalibrationStorageRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.pk.E2CalibrationDisposition
import com.mkx.hrttracker.model.pk.E2CalibrationMetadata
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.ui.pkcalibrationdebug.PkCalibrationUiFixtureBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CalibrationViewModel @Inject constructor(
    private val bloodTestRepository: BloodTestRepository,
    private val settingsRepository: SettingsRepository,
    private val pkCalibrationLiveRepository: PkCalibrationLiveRepository,
    private val pkStorageRepository: PkCalibrationStorageRepository,
    private val clock: Clock,
    private val pkUiFixtureBridge: PkCalibrationUiFixtureBridge,
) : ViewModel() {
    private val isDeletingAllEntries = MutableStateFlow(false)
    private val deleteAllEntriesResult =
        MutableStateFlow<CalibrationDeleteAllEntriesResult?>(null)
    private val cachedPanels = bloodTestRepository.getCachedPanels()

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
        initialValue = CalibrationUiState(
            panels = cachedPanels.orEmpty(),
            settingsState = settingsRepository.settingsState.value,
            isLoading = cachedPanels == null,
        ),
    )

    /**
     * The status-surface projection. Null while the live evaluation is
     * loading/unavailable — the section is then structurally absent, never a
     * synthesized failure state.
     */
    val pkCalibrationState: StateFlow<PkCalibrationScreenState?> = combine(
        pkCalibrationLiveRepository.liveState,
        pkUiFixtureBridge.fixture,
    ) { liveState, fixture ->
        when {
            // Debug harness fixture drives the real surface (plan D3).
            fixture != null -> PkCalibrationScreenState(
                ui = pkCalibrationUiState(fixture.result, fixture.render),
                excludedResultIds = fixture.excludedResultIds,
            )

            else -> (liveState as? PkCalibrationLiveState.Available)?.let { available ->
                PkCalibrationScreenState(
                    ui = pkCalibrationUiState(
                        available.evaluation.result,
                        available.render,
                    ),
                    excludedResultIds = available.context.metadata
                        .filter { item ->
                            item.disposition == E2CalibrationDisposition.EXCLUDED
                        }
                        .map { item -> item.resultId }
                        .toSet(),
                )
            }
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    private val pkReviewRejectionEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** One event per failed review write; the screen surfaces a toast. */
    val pkReviewRejections: SharedFlow<Unit> = pkReviewRejectionEvents.asSharedFlow()

    fun retryPkCalibration() {
        pkCalibrationLiveRepository.retry()
    }

    fun excludePkLab(resultId: UUID) = savePkDisposition(resultId, E2CalibrationDisposition.EXCLUDED)

    fun reincludePkLab(resultId: UUID) = savePkDisposition(resultId, E2CalibrationDisposition.AUTO)

    /**
     * The user's review decision is persisted unconditionally; the storage
     * layer only enforces that the target is a built-in E2 result. A failed
     * write surfaces as a toast.
     */
    private fun savePkDisposition(resultId: UUID, disposition: E2CalibrationDisposition) {
        viewModelScope.launch {
            try {
                pkStorageRepository.saveMetadata(
                    E2CalibrationMetadata(resultId, disposition, Instant.now(clock))
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                pkReviewRejectionEvents.tryEmit(Unit)
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
