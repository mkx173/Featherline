package com.mkx.hrttracker.healthconnect

import androidx.health.connect.client.permission.HealthPermission
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class HealthConnectCapabilityState(
    val sdkAvailable: Boolean = false,
    val hasAccess: Boolean = false,
    /** Not available, but installing or updating Health Connect from Play fixes that. */
    val providerUpdateRequired: Boolean = false,
    /** The last reconcile or sync threw; cleared when the next one starts. */
    val syncFailed: Boolean = false,
    /** READ history can be asked for alongside READ weight. */
    val readHistorySupported: Boolean = false,
) {
    /**
     * What turning sync on asks for: READ weight, plus history where supported,
     * since without it HC hides records from over 30 days before the first
     * grant. History is optional; sync only needs READ weight.
     */
    val weightPermissionsToRequest: Set<String>
        get() = if (readHistorySupported) {
            setOf(HEALTH_CONNECT_READ_WEIGHT_PERMISSION, HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)
        } else {
            setOf(HEALTH_CONNECT_READ_WEIGHT_PERMISSION)
        }
}

/**
 * Works like ReminderCapabilityReconciler: refreshes whether Health Connect is
 * usable (SDK available and READ weight granted) on every resume, and turns sync
 * off when it isn't, so a later grant can't replace a weight typed meanwhile.
 * Syncs on the first reconcile of each day (app start included) and when a
 * caller asks for it.
 */
@Singleton
class HealthConnectCapabilityReconciler @Inject constructor(
    @param:AppScope private val appScope: CoroutineScope,
    private val healthConnectManager: HealthConnectManager,
    private val weightSyncCoordinator: WeightSyncCoordinator,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    // Seeded with SDK availability (a package lookup, on the UI thread at
    // injection) so the weight sheet doesn't flash "not available" before the
    // first reconcile lands.
    private val _state = MutableStateFlow(
        healthConnectManager.isAvailable().let { sdkAvailable ->
            HealthConnectCapabilityState(
                sdkAvailable = sdkAvailable,
                providerUpdateRequired = !sdkAvailable && healthConnectManager.isProviderUpdateRequired(),
            )
        }
    )
    val state: StateFlow<HealthConnectCapabilityState> = _state.asStateFlow()

    // Like ReminderCapabilityReconciler's: runs one at a time, failure included,
    // so an older run can't publish its state (or syncFailed) over a newer one.
    private val reconcileMutex = Mutex()

    // Guarded by reconcileMutex. Null until the first sync of this process.
    private var lastSyncDate: LocalDate? = null

    /** [sync] forces a sync even if one already ran today (Sync now, backup restore). */
    fun requestReconcile(reason: String, sync: Boolean = false) {
        appScope.launch {
            reconcileMutex.withLock {
                runCatching { reconcile(reason, sync) }
                    .onFailure { throwable ->
                        _state.update { it.copy(syncFailed = true) }
                        diagnosticsLogger.warning(
                            TAG,
                            "health_connect_reconcile_failed reason=$reason",
                            throwable,
                        )
                    }
            }
        }
    }

    /**
     * Turns sync off and resets the daily gate under one lock, so a quick
     * off-then-on can't reconcile before the old date is cleared.
     */
    suspend fun disableSync() = reconcileMutex.withLock {
        weightSyncCoordinator.disable()
        lastSyncDate = null
    }

    private suspend fun reconcile(reason: String, sync: Boolean) {
        val now = clock.instant()
        val today = now.atZone(ZoneId.systemDefault()).toLocalDate()
        // Read before access: sync turned on after a stale access check must stay on.
        val syncEnabled = settingsRepository.getCurrentSettings().healthConnectWeightSyncEnabled
        val sdkAvailable = healthConnectManager.isAvailable()
        val hasAccess = sdkAvailable && healthConnectManager.hasWeightAccess()
        val providerUpdateRequired = !sdkAvailable && healthConnectManager.isProviderUpdateRequired()
        _state.value = HealthConnectCapabilityState(
            sdkAvailable = sdkAvailable,
            hasAccess = hasAccess,
            providerUpdateRequired = providerUpdateRequired,
            readHistorySupported = sdkAvailable && healthConnectManager.supportsReadHistory(),
        )
        diagnosticsLogger.info(
            TAG,
            "health_connect_reconcile reason=$reason sdk=$sdkAvailable access=$hasAccess",
        )
        if (!hasAccess || !syncEnabled) {
            lastSyncDate = null
        }
        when {
            // Health Connect needing an update is passing: sync resumes once it
            // is updated, and the sheet says so meanwhile.
            !hasAccess && syncEnabled && !providerUpdateRequired -> {
                weightSyncCoordinator.disable()
                diagnosticsLogger.info(TAG, "health_connect_reconcile_disabled_sync reason=$reason")
            }
            // The date is set after it, so a sync that threw is retried on the
            // next reconcile.
            hasAccess && syncEnabled && (sync || lastSyncDate != today) -> {
                weightSyncCoordinator.sync(reason, now)
                lastSyncDate = today
            }
        }
    }

    private companion object {
        const val TAG = "HealthConnectCapabilityReconciler"
    }
}
