package com.mkx.hrttracker.ui.catalog.nudge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.data.repository.MedicineStockRepository
import com.mkx.hrttracker.data.repository.RunwayProjection
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject

private const val STOCK_NUDGE_PROJECTION_WAIT_MILLIS = 5_000L

@HiltViewModel
class StockTrackingNudgeViewModel @Inject constructor(
    private val gate: StockNudgeGate,
    private val medicineRepository: MedicineRepository,
    private val stockRepository: MedicineStockRepository,
) : ViewModel() {

    private val _pendingNudge = MutableStateFlow<Medicine?>(null)
    val pendingNudge: StateFlow<Medicine?> = _pendingNudge.asStateFlow()

    private val _optInTarget = MutableStateFlow<MedicineStockProjection?>(null)
    val optInTarget: StateFlow<MedicineStockProjection?> = _optInTarget.asStateFlow()

    val enabled: Flow<Boolean> = gate.enabled

    private val _autoDisabledEvents = Channel<Unit>(Channel.BUFFERED)
    val autoDisabledEvents: Flow<Unit> = _autoDisabledEvents.receiveAsFlow()

    fun onNewMedicineCreated(medicineId: UUID) {
        viewModelScope.launch {
            if (!gate.enabled.first()) return@launch
            val medicine = medicineRepository.observeByUuid(medicineId).firstOrNull()
                ?: return@launch
            _pendingNudge.value = medicine
        }
    }

    fun onNudgeDismissedViaX() {
        _pendingNudge.value = null
        viewModelScope.launch {
            if (gate.onDismissedViaX()) {
                _autoDisabledEvents.send(Unit)
            }
        }
    }

    fun onNudgeActionTapped() {
        val medicineId = _pendingNudge.value?.uuid ?: return
        _pendingNudge.value = null
        viewModelScope.launch {
            val projection = stockRepository.getCachedProjection(medicineId)
                ?: withTimeoutOrNull(STOCK_NUDGE_PROJECTION_WAIT_MILLIS) {
                    stockRepository.observeProjections()
                        .mapNotNull { projections ->
                            projections.firstOrNull { it.medicine.uuid == medicineId }
                        }
                        .first()
                }
                ?: return@launch
            _optInTarget.value = projection
        }
    }

    fun onNudgeTimedOut() {
        _pendingNudge.value = null
    }

    fun dismissOptInSheet() {
        _optInTarget.value = null
    }

    fun previewRunway(
        medicineId: UUID,
        hypotheticalStock: MedicineStock,
    ): RunwayProjection? {
        return stockRepository.previewRunway(medicineId, hypotheticalStock)
    }

    fun submitOptInReceived(
        medicineId: UUID,
        unitsReceived: Double,
    ) {
        val target = _optInTarget.value
            ?.takeIf { it.medicine.uuid == medicineId }
            ?: return
        viewModelScope.launch {
            val initialUnitsRemaining =
                (target.medicine.stock.unitsRemaining ?: 0.0) + unitsReceived
            medicineRepository.enableTracking(
                uuid = medicineId,
                initialUnitsRemaining = initialUnitsRemaining,
                initialOpenContainerAmount = null,
                initialUnitsLastTotal = initialUnitsRemaining,
            )
            _optInTarget.value = null
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            gate.setEnabled(enabled)
        }
    }
}
