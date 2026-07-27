package com.mkx.hrttracker.widget

import android.content.Context
import android.content.res.Configuration
import com.mkx.hrttracker.data.repository.HomeSnapshotRecord
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.SkippedDoseStore
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import com.mkx.hrttracker.util.localizedShortTimeFormatter
import com.mkx.hrttracker.util.uses24HourTimeFormat
import com.mkx.hrttracker.wear.WearSnapshotSink
import com.mkx.hrttracker.wear.toWearDoseSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetSnapshotRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val homeSnapshotRepository: HomeSnapshotRepository,
    private val settingsRepository: SettingsRepository,
    private val widgetSnapshotStore: WidgetSnapshotStore,
    private val wearSnapshotSinks: Set<@JvmSuppressWildcards WearSnapshotSink>,
    private val skippedDoseStore: SkippedDoseStore,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    suspend fun refreshWidgetSnapshot(now: LocalDateTime = LocalDateTime.now()) {
        // Re-derives the widget snapshot from the current home snapshot — used when
        // widget-only inputs (e.g. theme, scale, alpha, E2 unit) change and the home
        // snapshot itself doesn't need to be regenerated. Callers that actually need
        // fresh home data should go through HomeSnapshotRepository directly so the
        // home-snapshot observer can fan out to the widget.
        val homeSnapshot = homeSnapshotRepository.readUsableHomeSnapshot(now = now)
        if (homeSnapshot == null) {
            diagnosticsLogger.info(
                TAG,
                "widget_snapshot_refresh_skipped reason=no_home_snapshot now=$now"
            )
            clearWidgetSnapshot()
            return
        }
        writeWidgetSnapshot(homeSnapshot = homeSnapshot, now = now)
    }

    suspend fun writeWidgetSnapshot(
        homeSnapshot: HomeSnapshotRecord,
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        val zoneId = ZoneId.systemDefault()
        val settings = settingsRepository.getCurrentSettings()
        // Wrap with an explicitly-localized context so context.getString() inside the
        // builder resolves against the user's chosen app language even on API levels
        // where the application context lags AppCompatDelegate.setApplicationLocales.
        val localizedContext = context.withAppLocale(settings)
        val timeFormatter = localizedShortTimeFormatter(
            locale = Locale.forLanguageTag(settings.appLanguageOption.languageTag),
            uses24HourFormat = context.uses24HourTimeFormat(),
        )
        val widgetSnapshot = buildWidgetSnapshotRecord(
            context = localizedContext,
            homeSnapshot = homeSnapshot,
            settings = settings,
            now = now,
            zoneId = zoneId,
            timeFormatter = timeFormatter,
            skippedSlots = skippedDoseStore.getSkippedSlots(now),
        )
        widgetSnapshotStore.writeSnapshot(widgetSnapshot)
        pushHrtWidgets(context, widgetSnapshot)
        val wearSnapshot = widgetSnapshot.toWearDoseSnapshot()
        wearSnapshotSinks.forEach { sink ->
            try {
                sink.publish(wearSnapshot)
            } catch (throwable: Throwable) {
                if (throwable is kotlinx.coroutines.CancellationException) throw throwable
                diagnosticsLogger.warning(TAG, "wear_snapshot_publish_failed", throwable)
            }
        }
        diagnosticsLogger.info(
            TAG,
            "widget_snapshot_refreshed rows=${widgetSnapshot.doseRows.size} " +
                    "done=${widgetSnapshot.doneCount} total=${widgetSnapshot.totalCount}",
        )
    }

    private fun Context.withAppLocale(settings: SettingsState): Context {
        val locale = Locale.forLanguageTag(settings.appLanguageOption.languageTag)
        val config = Configuration(resources.configuration).apply { setLocale(locale) }
        return createConfigurationContext(config)
    }

    suspend fun clearWidgetSnapshot() {
        widgetSnapshotStore.clearSnapshot()
        // Push the empty state (record = null) through the API-selected widget update path.
        pushHrtWidgets(context, null)
    }

    companion object {
        private const val TAG = "WidgetSnapshotRepository"
    }
}
