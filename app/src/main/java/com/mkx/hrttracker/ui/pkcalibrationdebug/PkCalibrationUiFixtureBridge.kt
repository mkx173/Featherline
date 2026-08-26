package com.mkx.hrttracker.ui.pkcalibrationdebug

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
 * (Phase-2 plan D3/2.5): QA exercises every state against the shipping
 * surfaces instead of a lookalike. Values are validated production contract
 * objects.
 */
data class PkCalibrationUiFixture(
    val result: PkCalibrationResult,
    val render: PkCalibrationRenderResult?,
    val excludedResultIds: Set<UUID>,
)

/**
 * Singleton bridge between the debug harness (writer, `src/debug` only) and
 * the production ViewModels (readers). Nothing outside the debug source set
 * can publish, so release readers only ever see null. The forced state
 * survives navigation on purpose; the harness's reset control (publishing
 * null) is the only deliberate clear besides process death.
 */
@Singleton
class PkCalibrationUiFixtureBridge @Inject constructor() {
    private val state = MutableStateFlow<PkCalibrationUiFixture?>(null)

    val fixture: StateFlow<PkCalibrationUiFixture?> = state.asStateFlow()

    fun publish(fixture: PkCalibrationUiFixture?) {
        state.value = fixture
    }
}
