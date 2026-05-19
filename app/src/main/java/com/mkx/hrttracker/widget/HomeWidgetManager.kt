package com.mkx.hrttracker.widget

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.di.AppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeWidgetManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val widgetSnapshotStore: WidgetSnapshotStore,
    @AppScope private val appScope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return

        // 1. Enqueue the periodic re-render/rebuild worker (idempotent).
        val request = PeriodicWorkRequestBuilder<WidgetDailyRefreshWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )

        // 2. Re-render whenever the E2 display unit changes (no DB, no simulation).
        appScope.launch {
            settingsRepository.homeE2DisplayUnitFlow
                .drop(1)
                .collect { displayUnit ->
                    runCatching {
                        val snapshot = widgetSnapshotStore.readSnapshot()
                        if (snapshot != null) {
                            widgetSnapshotStore.writeSnapshot(
                                snapshot.copy(e2DisplayUnit = displayUnit.unit.storageValue)
                            )
                        }
                        updateAllHrtWidgets(context)
                    }
                }
        }
    }

    companion object {
        private const val WORK_NAME = "widget_daily_refresh"
    }
}
