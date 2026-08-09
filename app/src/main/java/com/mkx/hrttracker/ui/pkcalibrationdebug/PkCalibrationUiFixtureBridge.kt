package com.mkx.hrttracker.ui.pkcalibrationdebug

import com.mkx.hrttracker.BuildConfig
import com.mkx.hrttracker.model.pk.PkCalibrationRenderResult
import com.mkx.hrttracker.model.pk.PkCalibrationResult
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Debug harness fixture, published for the real Home and Calibration screens
 * (Phase-2 plan D3/2.5): QA exercises every §14 state against the shipping
 * surfaces instead of a lookalike. Values are validated production contract
 * objects built by [PkCalibrationDebugFixtures]. [scenario] carries the picker
 * state so a fresh harness ViewModel can replay the active forced state.
 */
data class PkCalibrationUiFixture(
    val result: PkCalibrationResult,
    val render: PkCalibrationRenderResult?,
    val excludedResultIds: Set<UUID>,
    val scenario: PkCalibrationDebugScenario? = null,
)

/**
 * Singleton bridge between the debug harness (writer) and the production
 * ViewModels (readers). Release builds never publish: writes are refused
 * outside debug, and the D2 surface gate keeps readers dark regardless. The
 * forced state survives navigation on purpose; the harness's reset control
 * (publishing null) is the only deliberate clear besides process death.
 */
@Singleton
class PkCalibrationUiFixtureBridge @Inject constructor() {
    private val state = MutableStateFlow<PkCalibrationUiFixture?>(null)

    val fixture: StateFlow<PkCalibrationUiFixture?> = state.asStateFlow()

    fun publish(fixture: PkCalibrationUiFixture?) {
        if (!BuildConfig.DEBUG) {
            return
        }
        state.value = fixture
    }
}
