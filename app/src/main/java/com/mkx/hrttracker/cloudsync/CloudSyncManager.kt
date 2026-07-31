package com.mkx.hrttracker.cloudsync

import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudSyncManager @Inject constructor(
    private val preferences: CloudSyncPreferences,
    private val scheduler: CloudSyncScheduler,
    private val gateway: CloudDriveGateway,
    @param:AppScope private val appScope: CoroutineScope,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        appScope.launch {
            preferences.state
                .map { state -> state.enabled to state.interval }
                .distinctUntilChanged()
                .catch { error ->
                    diagnosticsLogger.warning(TAG, "cloud_sync_schedule_observer_failed", error)
                }
                .collect { (enabled, interval) ->
                    if (enabled && gateway.isAvailable) {
                        scheduler.schedule(interval)
                    } else {
                        scheduler.cancel()
                    }
                }
        }
    }

    private companion object {
        const val TAG = "CloudSyncManager"
    }
}
