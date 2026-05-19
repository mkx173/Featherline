package com.mkx.hrttracker.widget

import android.content.Context
import com.mkx.hrttracker.data.repository.HomeSnapshotRecord
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetSnapshotRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val homeSnapshotRepository: HomeSnapshotRepository,
    private val settingsRepository: SettingsRepository,
    private val widgetSnapshotStore: WidgetSnapshotStore,
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
            diagnosticsLogger.info(TAG, "widget_snapshot_refresh_skipped reason=no_home_snapshot now=$now")
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
        val widgetSnapshot = buildWidgetSnapshotRecord(
            context = context,
            homeSnapshot = homeSnapshot,
            settings = settings,
            now = now,
            zoneId = zoneId,
        )
        widgetSnapshotStore.writeSnapshot(widgetSnapshot)
        updateAllHrtWidgets(context)
        diagnosticsLogger.info(
            TAG,
            "widget_snapshot_refreshed rows=${widgetSnapshot.doseRows.size} " +
                "done=${widgetSnapshot.doneCount} total=${widgetSnapshot.totalCount}",
        )
    }

    suspend fun clearWidgetSnapshot() {
        widgetSnapshotStore.clearSnapshot()
        updateAllHrtWidgets(context)
    }

    companion object {
        private const val TAG = "WidgetSnapshotRepository"
    }
}
