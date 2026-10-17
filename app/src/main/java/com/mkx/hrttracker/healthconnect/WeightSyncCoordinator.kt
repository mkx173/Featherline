package com.mkx.hrttracker.healthconnect

import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.UserProfileRepository
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-only, latest-only body-weight sync from Health Connect. With sync on, HC
 * is the source: its latest reading becomes the app's weight, so an HC deletion
 * makes the next-latest reading the weight. With no reading, the current weight
 * stays and [hasNoReading] tells the UI.
 */
@Singleton
class WeightSyncCoordinator @Inject constructor(
    private val healthConnectManager: HealthConnectManager,
    private val userProfileRepository: UserProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    private val syncMutex = Mutex()
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // UI state from the last sync, not a column: Health Connect had no reading.
    private val _hasNoReading = MutableStateFlow(false)
    val hasNoReading: StateFlow<Boolean> = _hasNoReading.asStateFlow()

    /** No-op unless sync is enabled and Health Connect access is held. */
    suspend fun sync(reason: String = "explicit", now: Instant = Instant.now()) = syncMutex.withLock {
        if (!settingsRepository.getCurrentSettings().healthConnectWeightSyncEnabled) return@withLock
        if (!healthConnectManager.isAvailable() || !healthConnectManager.hasWeightAccess()) {
            return@withLock
        }
        _isSyncing.value = true
        val startedAtNanos = System.nanoTime()
        try {
            val notAfter = now + FUTURE_TOLERANCE
            val latest = healthConnectManager.readLatestWeight(notAfter)
                // Ignore readings from a scale whose clock is ahead, and ones that
                // round to 0 in either unit: backup restore rejects a 0 weight.
                ?.takeIf { reading -> reading.time <= notAfter && reading.weightKg >= MIN_WEIGHT_KG }
            // Turned off while the read was in flight: the weight is the user's now.
            if (!settingsRepository.getCurrentSettings().healthConnectWeightSyncEnabled) return@withLock
            _hasNoReading.value = latest == null
            val changed = if (latest != null) {
                userProfileRepository.adoptHcWeight(latest.weightKg, latest.time, now)
            } else {
                userProfileRepository.clearHcMeasuredAt()
            }
            diagnosticsLogger.info(
                TAG,
                "weight_sync_complete reason=$reason reading=${latest != null} changed=$changed",
            )
            // A read often returns in a few ms; hold the state so the sheet shows
            // "Syncing…" and one whole turn of the icon instead of a flash.
            val elapsedMillis = (System.nanoTime() - startedAtNanos) / 1_000_000
            delay(MIN_SYNCING_MILLIS - elapsedMillis)
        } finally {
            // Nothing suspends before this, so a cancelled or failed sync can't
            // leave the singleton's state stuck on.
            _isSyncing.value = false
        }
    }

    /**
     * Turns sync off. The flag goes first so an in-flight sync drops its reading;
     * the measured time is cleared under the lock, so a sync already past that
     * check can't write one back afterwards.
     */
    suspend fun disable() {
        settingsRepository.setHealthConnectWeightSyncEnabled(false)
        syncMutex.withLock { userProfileRepository.clearHcMeasuredAt() }
    }

    companion object {
        /** Shortest time [isSyncing] stays true; also one turn of the sheet's sync icon. */
        const val MIN_SYNCING_MILLIS = 900L

        private const val TAG = "WeightSyncCoordinator"
        private val FUTURE_TOLERANCE: Duration = Duration.ofMinutes(5)
        // 0.1 kg rounds to at least 0.1 in kg and 0.2 in lb.
        private const val MIN_WEIGHT_KG = 0.1
    }
}
