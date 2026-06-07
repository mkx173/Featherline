package com.mkx.hrttracker.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.collection.intSetOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetManager.Companion.SET_WIDGET_PREVIEWS_RESULT_RATE_LIMITED
import androidx.glance.appwidget.GlanceAppWidgetManager.Companion.SET_WIDGET_PREVIEWS_RESULT_SUCCESS
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
import kotlinx.coroutines.CancellationException
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
        scheduleNextWidgetDateRefresh(context, diagnosticsLogger = diagnosticsLogger)
        publishGeneratedWidgetPreviewsIfNeeded()

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
                        // Don't let runCatching swallow cancellation of this app-scoped
                        // collector; rethrow so the coroutine stops cleanly.
                        if (throwable is CancellationException) throw throwable
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
                        settings.showArchivedGroupRecords,
                    )
                }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    runCatching {
                        widgetSnapshotRepository.refreshWidgetSnapshot()
                    }.onFailure { throwable ->
                        if (throwable is CancellationException) throw throwable
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
                        if (throwable is CancellationException) throw throwable
                        diagnosticsLogger.warning(TAG, "widget_snapshot_time_format_refresh_failed", throwable)
                    }
                }
        }
    }

    private fun publishGeneratedWidgetPreviewsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            publishGeneratedWidgetPreviews()
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun publishGeneratedWidgetPreviews() {
        appScope.launch {
            runCatching {
                val appWidgetManager = context.getSystemService(AppWidgetManager::class.java)
                val glanceAppWidgetManager = GlanceAppWidgetManager(context)
                val prefs = context.getSharedPreferences(GENERATED_PREVIEW_PREFS, Context.MODE_PRIVATE)

                for (receiver in GENERATED_PREVIEW_RECEIVERS) {
                    if (
                        receiver.hasGeneratedHomePreview(appWidgetManager) &&
                        prefs.getInt(receiver.generatedHomePreviewVersionKey, 0) == GENERATED_PREVIEW_VERSION
                    ) {
                        diagnosticsLogger.info(
                            TAG,
                            "widget_generated_preview_skipped receiver=${receiver.simpleName} reason=already_published",
                        )
                        continue
                    }

                    when (
                        glanceAppWidgetManager.setWidgetPreviews(
                            receiver = receiver.kotlin,
                            widgetCategories = intSetOf(WIDGET_CATEGORY_HOME_SCREEN),
                        )
                    ) {
                        SET_WIDGET_PREVIEWS_RESULT_SUCCESS -> {
                            prefs.edit()
                                .putInt(receiver.generatedHomePreviewVersionKey, GENERATED_PREVIEW_VERSION)
                                .apply()
                            diagnosticsLogger.info(
                                TAG,
                                "widget_generated_preview_published receiver=${receiver.simpleName}",
                            )
                        }

                        SET_WIDGET_PREVIEWS_RESULT_RATE_LIMITED -> diagnosticsLogger.warning(
                            TAG,
                            "widget_generated_preview_rate_limited receiver=${receiver.simpleName}",
                        )

                        else -> diagnosticsLogger.warning(
                            TAG,
                            "widget_generated_preview_failed receiver=${receiver.simpleName}",
                        )
                    }
                }
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                diagnosticsLogger.warning(TAG, "widget_generated_preview_publish_failed", throwable)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun Class<out GlanceAppWidgetReceiver>.hasGeneratedHomePreview(
        appWidgetManager: AppWidgetManager,
    ): Boolean {
        val componentName = ComponentName(context, this)
        val providerInfo = appWidgetManager.installedProviders
            .firstOrNull { providerInfo -> providerInfo.provider == componentName }
            ?: return false
        return providerInfo.generatedPreviewCategories and WIDGET_CATEGORY_HOME_SCREEN != 0
    }

    private val Class<out GlanceAppWidgetReceiver>.generatedHomePreviewVersionKey: String
        get() = "home_screen_preview_version_$name"

    companion object {
        private const val TAG = "HomeWidgetManager"
        private const val WORK_NAME = "widget_daily_refresh"
        private const val GENERATED_PREVIEW_PREFS = "hrt_widget_generated_previews"
        private const val GENERATED_PREVIEW_VERSION = 3
        private val GENERATED_PREVIEW_RECEIVERS = listOf<Class<out GlanceAppWidgetReceiver>>(
            HrtWidgetMediumReceiver::class.java,
            HrtWidgetLargeReceiver::class.java,
        )
    }
}
