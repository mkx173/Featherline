package com.mkx.hrttracker.healthconnect

import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.di.AppScope
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

@Singleton
class HealthConnectSyncCoordinator @Inject constructor(
    private val healthConnect: FeatherlineHealthConnect,
    private val medicationLogRepository: MedicationLogRepository,
    @param:AppScope private val appScope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun start() {
        if (!started.compareAndSet(false, true)) return
        appScope.launch {
            combine(
                healthConnect.state,
                medicationLogRepository.observeEntries(),
            ) { state, entries ->
                state to entries
            }
                .debounce(750)
                .collectLatest { (state, entries) ->
                    if (
                        state.medicationSyncEnabled &&
                        state.canWriteMedication &&
                        entries != null
                    ) {
                        healthConnect.syncMedicationEntries(entries)
                    }
                }
        }
    }

    fun onForeground() {
        appScope.launch {
            healthConnect.syncEnabled(medicationLogRepository.getEntries())
        }
    }

    suspend fun syncNow(): Boolean =
        healthConnect.syncEnabled(medicationLogRepository.getEntries())

    suspend fun setMedicationSyncEnabled(enabled: Boolean) {
        val entries = if (enabled) medicationLogRepository.getEntries() else emptyList()
        healthConnect.setMedicationSyncEnabled(enabled, entries)
    }
}
