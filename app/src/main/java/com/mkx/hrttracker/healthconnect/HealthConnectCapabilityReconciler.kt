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
import java.time.Duration
import java.time.Instant
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
    /**
     * [hasAccess] isn't settled yet: no reconcile has finished (cold start, or
     * the first one threw), or access is gone but turning sync off hasn't
     * gone through.
     */
    val accessPending: Boolean = false,
) {
    /**
     * Sync is on from the user's side: the flag is set and Health Connect is
     * the source, including while it only needs an update (the reconciler
     * keeps sync on then) or while access is pending, so a weight typed before
     * the first reconcile can't be replaced by its sync. Drives the sheet's
     * switch and the "No records" text; the "· Health Connect" suffix outside
     * needs [hasAccess].
     */
    fun isSyncOn(syncEnabled: Boolean): Boolean =
        syncEnabled && (hasAccess || providerUpdateRequired || accessPending)

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
 * Syncs on reconcile, at most once a minute (a burst guard for resumes seconds
 * apart, like the round trip through Health Connect's settings), and whenever
 * a caller asks for it.
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
                accessPending = true,
            )
        }
    )
    val state: StateFlow<HealthConnectCapabilityState> = _state.asStateFlow()

    // Like ReminderCapabilityReconciler's: runs one at a time, failure included,
    // so an older run can't publish its state (or syncFailed) over a newer one.
    private val reconcileMutex = Mutex()

    // Guarded by reconcileMutex. Null until the first sync of this process.
    private var lastSyncAt: Instant? = null

    /** [sync] forces a sync even if one just ran (Sync now, backup restore). */
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

    /** Queues enable with disable and reconcile so the last toggle wins. */
    suspend fun enableSync() {
        reconcileMutex.withLock {
            settingsRepository.setHealthConnectWeightSyncEnabled(true)
        }
        requestReconcile("weight_sync_enabled")
    }

    /**
     * Turns sync off and resets the burst guard under one lock, so a quick
     * off-then-on can't reconcile before the old time is cleared.
     */
    suspend fun disableSync() = reconcileMutex.withLock {
        weightSyncCoordinator.disable()
        lastSyncAt = null
    }

    private suspend fun reconcile(reason: String, sync: Boolean) {
        val now = clock.instant()
        // Read before access: sync turned on after a stale access check must stay on.
        val syncEnabled = settingsRepository.getCurrentSettings().healthConnectWeightSyncEnabled
        val sdkAvailable = healthConnectManager.isAvailable()
        val hasAccess = sdkAvailable && healthConnectManager.hasWeightAccess()
        val providerUpdateRequired = !sdkAvailable && healthConnectManager.isProviderUpdateRequired()
        // Health Connect needing an update is passing: sync resumes once it is
        // updated, and the sheet says so meanwhile.
        val disableSync = !hasAccess && syncEnabled && !providerUpdateRequired
        _state.value = HealthConnectCapabilityState(
            sdkAvailable = sdkAvailable,
            hasAccess = hasAccess,
            providerUpdateRequired = providerUpdateRequired,
            readHistorySupported = sdkAvailable && healthConnectManager.supportsReadHistory(),
            // Read-only until the flag is off, so a failed disable can't let a
            // weight be typed that a later grant's sync would replace.
            accessPending = disableSync,
        )
        diagnosticsLogger.info(
            TAG,
            "health_connect_reconcile reason=$reason sdk=$sdkAvailable access=$hasAccess",
        )
        if (!hasAccess || !syncEnabled) {
            lastSyncAt = null
        }
        when {
            disableSync -> {
                weightSyncCoordinator.disable()
                _state.update { it.copy(accessPending = false) }
                diagnosticsLogger.info(TAG, "health_connect_reconcile_disabled_sync reason=$reason")
            }
            // The time is set after it, so a sync that threw is retried on the
            // next reconcile.
            hasAccess && syncEnabled && (sync || lastSyncAt.isNotWithinInterval(now)) -> {
                weightSyncCoordinator.sync(reason, now)
                lastSyncAt = now
            }
        }
    }

    private fun Instant?.isNotWithinInterval(now: Instant): Boolean =
        // A backward clock correction must not suppress sync until the old time catches up.
        this == null || now < this || Duration.between(this, now) >= RESUME_SYNC_INTERVAL

    private companion object {
        const val TAG = "HealthConnectCapabilityReconciler"
        val RESUME_SYNC_INTERVAL: Duration = Duration.ofMinutes(1)
    }
}
