package com.mkx.hrttracker.widget

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import com.mkx.hrttracker.util.observeUses24HourTimeFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeWidgetManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val homeSnapshotRepository: HomeSnapshotRepository,
    private val settingsRepository: SettingsRepository,
    private val widgetSnapshotRepository: WidgetSnapshotRepository,
    @param:AppScope private val appScope: CoroutineScope,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return

        val workManager = WorkManager.getInstance(context)

        // 1. Enqueue the periodic re-render/rebuild worker (idempotent).
        val periodicRequest = PeriodicWorkRequestBuilder<WidgetDailyRefreshWorker>(15, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest,
        )

        // Run the worker logic once immediately on startup so the widget is never stale
        // after a long absence or a fresh install.
        workManager.enqueue(OneTimeWorkRequestBuilder<WidgetDailyRefreshWorker>().build())

        appScope.launch {
            // Skip transient nulls that observeHomeSnapshot emits during runHomeDataMutation
            // (clearSnapshotBestEffort -> async rebuild). Clearing the widget snapshot on each
            // gap causes a visible "no medications" flash after every quick-log tap. The
            // rebuild emits a fresh non-null snapshot shortly after, which we propagate.
            homeSnapshotRepository.observeHomeSnapshot()
                .filterNotNull()
                .collect { snapshot ->
                    runCatching {
                        widgetSnapshotRepository.writeWidgetSnapshot(snapshot)
                    }.onFailure { throwable ->
                        diagnosticsLogger.warning(TAG, "widget_snapshot_home_observer_failed", throwable)
                    }
                }
        }

        // Rebuild only the widget snapshot when widget-facing settings change.
        appScope.launch {
            settingsRepository.settingsState
                .map { settings ->
                    listOf(
                        settings.hideMedicationDetails,
                        settings.adaptiveColorEnabled,
                        settings.widgetContentScale,
                        settings.widgetBackgroundAlpha,
                        settings.widgetDarkModeOption,
                        settings.homeE2DisplayUnit,
                        settings.appLanguageOption,
                    )
                }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    runCatching {
                        widgetSnapshotRepository.refreshWidgetSnapshot()
                    }.onFailure { throwable ->
                        diagnosticsLogger.warning(TAG, "widget_snapshot_settings_refresh_failed", throwable)
                    }
                }
        }

        // Android does not broadcast when the user toggles the system 24-hour time
        // format, so the widget's pre-formatted trailing-time strings would otherwise
        // stay stale until another refresh trigger fires. Observe TIME_12_24 directly
        // and rebuild the snapshot when it flips.
        appScope.launch {
            context.observeUses24HourTimeFormat()
                .drop(1)
                .collect {
                    runCatching {
                        widgetSnapshotRepository.refreshWidgetSnapshot()
                    }.onFailure { throwable ->
                        diagnosticsLogger.warning(TAG, "widget_snapshot_time_format_refresh_failed", throwable)
                    }
                }
        }
    }

    companion object {
        private const val TAG = "HomeWidgetManager"
        private const val WORK_NAME = "widget_daily_refresh"
    }
}
