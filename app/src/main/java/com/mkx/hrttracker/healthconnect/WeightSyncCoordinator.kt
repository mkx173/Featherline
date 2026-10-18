package com.mkx.hrttracker.healthconnect

import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.UserProfileRepository
import com.mkx.hrttracker.util.AppDiagnosticsLogger
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
 * makes the next-latest reading the weight. With no reading or an invalid one,
 * the current weight stays and [readingProblem] tells the UI.
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

    // UI state from the last sync, not a column: why it kept the current weight.
    private val _readingProblem = MutableStateFlow<ReadingProblem?>(null)
    val readingProblem: StateFlow<ReadingProblem?> = _readingProblem.asStateFlow()

    /**
     * No-op unless sync is enabled. Access is the caller's check (the
     * reconciler's, which has just made it); a read without it throws.
     */
    suspend fun sync(
        reason: String = "explicit",
        now: Instant = Instant.now(),
    ) = syncMutex.withLock {
        if (!settingsRepository.getCurrentSettings().healthConnectWeightSyncEnabled) return@withLock
        _isSyncing.value = true
        try {
            val notAfter = now + FUTURE_TOLERANCE
            val latest = healthConnectManager.readLatestWeight(notAfter)
            // Not from a scale whose clock is ahead, and not one that rounds to 0
            // in either unit: backup restore rejects a 0 weight.
            val valid = latest?.takeIf { reading ->
                reading.time <= notAfter && reading.weightKg >= MIN_WEIGHT_KG
            }
            _readingProblem.value = when {
                latest == null -> ReadingProblem.NONE_FOUND
                valid == null -> ReadingProblem.INVALID
                else -> null
            }
            val changed = when {
                // Rechecked under the lock manual edits share: turned off mid-sync,
                // the weight is the user's now.
                valid != null -> userProfileRepository.adoptHcWeight(valid.weightKg, valid.time, now) {
                    settingsRepository.getCurrentSettings().healthConnectWeightSyncEnabled
                }
                latest == null -> userProfileRepository.clearHcMeasuredAt()
                // ponytail: only the latest is read, not a search for a valid one;
                // the last saved weight and its date stay until a valid reading.
                else -> false
            }
            diagnosticsLogger.info(
                TAG,
                "weight_sync_complete reason=$reason reading=${latest != null} changed=$changed",
            )
        } finally {
            // Nothing suspends before this, so a cancelled or failed sync can't
            // leave the singleton's state stuck on.
            _isSyncing.value = false
        }
    }

    /**
     * Turns sync off. The flag goes first so an in-flight sync drops its reading
     * (adoptHcWeight checks it under the lock manual edits share); the measured
     * time is cleared after that sync, so it can't write one back.
     */
    suspend fun disable() {
        settingsRepository.setHealthConnectWeightSyncEnabled(false)
        syncMutex.withLock {
            userProfileRepository.clearHcMeasuredAt()
            // Turning it back on reads "Syncing…" until the new sync, not the old result.
            _readingProblem.value = null
        }
    }

    private companion object {
        const val TAG = "WeightSyncCoordinator"
        val FUTURE_TOLERANCE: Duration = Duration.ofMinutes(5)
        // 0.1 kg rounds to at least 0.1 in kg and 0.2 in lb.
        const val MIN_WEIGHT_KG = 0.1
    }
}

/** Why the last sync kept the current weight. */
enum class ReadingProblem {
    NONE_FOUND,
    /** The latest reading is one the app can't use, e.g. 0 kg. */
    INVALID,
}
