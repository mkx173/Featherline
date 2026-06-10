package com.mkx.hrttracker.ui.catalog.nudge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.data.repository.MedicineStockRepository
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.RunwayProjection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

    // True from the moment the nudge action is tapped until its projection
    // either resolves into [optInTarget] or is abandoned. The projection fetch
    // runs in a coroutine for up to STOCK_NUDGE_PROJECTION_WAIT_MILLIS, so
    // without this flag a navigation tap in that window would move the page out
    // from under the opt-in sheet and it would later open over the destination.
    // The NavHost folds this into its chrome lock so navigation is held back for
    // the resolve, exactly as optInTarget itself holds it once the sheet is up.
    private val _optInResolving = MutableStateFlow(false)
    val optInResolving: StateFlow<Boolean> = _optInResolving.asStateFlow()

    val enabled: Flow<Boolean> = gate.enabled

    private val _autoDisabledEvents = Channel<Unit>(Channel.BUFFERED)
    val autoDisabledEvents: Flow<Unit> = _autoDisabledEvents.receiveAsFlow()

    private val _optInFailureEvents = Channel<Unit>(Channel.BUFFERED)
    val optInFailureEvents: Flow<Unit> = _optInFailureEvents.receiveAsFlow()

    private val _optInAddedEvents = Channel<StockNudgeAddedConfirmation>(Channel.BUFFERED)
    val optInAddedEvents: Flow<StockNudgeAddedConfirmation> = _optInAddedEvents.receiveAsFlow()

    private var optInMutationJob: Job? = null

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
        // Set synchronously, before the launch, so the chrome lock is held the
        // instant the tap returns — a navigation tap in the same frame already
        // sees the lock and cannot move the page out from under the sheet.
        _optInResolving.value = true
        viewModelScope.launch {
            try {
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
            } finally {
                // optInTarget, once set above, carries the lock from here on; if
                // the projection never resolved this releases the lock with no
                // sheet to show.
                _optInResolving.value = false
            }
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
        if (optInMutationJob?.isActive == true) return
        val target = _optInTarget.value
            ?.takeIf { it.medicine.uuid == medicineId }
            ?: return
        optInMutationJob = viewModelScope.launch {
            try {
                val initialUnitsRemaining =
                    (target.medicine.stock.unitsRemaining ?: 0.0) + unitsReceived
                medicineRepository.enableTracking(
                    uuid = medicineId,
                    initialUnitsRemaining = initialUnitsRemaining,
                    initialOpenContainerAmount = null,
                    initialUnitsLastTotal = initialUnitsRemaining,
                )
                _optInTarget.value = null
                _optInAddedEvents.send(
                    StockNudgeAddedConfirmation(
                        amount = unitsReceived,
                        preparation = target.medicine.preparation,
                    ),
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Throwable) {
                _optInFailureEvents.send(Unit)
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            gate.setEnabled(enabled)
        }
    }
}

/**
 * Emitted after a successful in-place opt-in so the host can confirm how much
 * was added (e.g. "Added 2 tablets to stock"). [preparation] lets the host
 * resolve the unit label.
 */
data class StockNudgeAddedConfirmation(
    val amount: Double,
    val preparation: MedicinePreparation,
)
