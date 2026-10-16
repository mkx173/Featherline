package com.mkx.hrttracker.healthconnect

import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class HealthConnectCapabilityState(
    val sdkAvailable: Boolean = false,
    val hasAccess: Boolean = false,
)

/**
 * Works like ReminderCapabilityReconciler: refreshes whether Health Connect is
 * usable (SDK available and READ weight granted), then syncs when access is held
 * and the intent flag is set. A lost permission leaves the flag on; the weight
 * sheet shows it as a recovery state and the weight becomes editable again.
 */
@Singleton
class HealthConnectCapabilityReconciler @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val healthConnectManager: HealthConnectManager,
    private val weightSyncCoordinator: WeightSyncCoordinator,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    // Seeded with SDK availability (a cheap package lookup) so the weight sheet
    // doesn't flash "not installed" before the first foreground reconcile lands.
    private val _state = MutableStateFlow(
        HealthConnectCapabilityState(sdkAvailable = healthConnectManager.isAvailable())
    )
    val state: StateFlow<HealthConnectCapabilityState> = _state.asStateFlow()

    fun requestReconcile(reason: String) {
        appScope.launch {
            runCatching { reconcile(reason) }
                .onFailure { throwable ->
                    diagnosticsLogger.warning(
                        TAG,
                        "health_connect_reconcile_failed reason=$reason",
                        throwable,
                    )
                }
        }
    }

    suspend fun reconcile(reason: String) {
        val sdkAvailable = healthConnectManager.isAvailable()
        val hasAccess = sdkAvailable && healthConnectManager.hasWeightAccess()
        _state.value = HealthConnectCapabilityState(sdkAvailable = sdkAvailable, hasAccess = hasAccess)
        diagnosticsLogger.info(
            TAG,
            "health_connect_reconcile reason=$reason sdk=$sdkAvailable access=$hasAccess",
        )
        // sync() checks the intent flag itself.
        if (hasAccess) weightSyncCoordinator.sync(reason)
    }

    private companion object {
        const val TAG = "HealthConnectCapabilityReconciler"
    }
}
