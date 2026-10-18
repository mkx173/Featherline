package com.mkx.hrttracker.widget

import android.content.Context
import android.content.res.Configuration
import com.mkx.hrttracker.data.repository.E2EstimateRepository
import com.mkx.hrttracker.data.repository.HomeCacheState
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import com.mkx.hrttracker.util.localizedShortTimeFormatter
import com.mkx.hrttracker.util.uses24HourTimeFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetSnapshotRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val homeSnapshotRepository: HomeSnapshotRepository,
    private val e2EstimateRepository: E2EstimateRepository,
    private val settingsRepository: SettingsRepository,
    private val widgetSnapshotStore: WidgetSnapshotStore,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    // The cache observer, the settings refresh and the 15-minute worker each
    // write from a state they read earlier: one write at a time.
    private val writeMutex = Mutex()

    suspend fun refreshWidgetSnapshot(now: LocalDateTime = LocalDateTime.now()) {
        // Re-derives the widget snapshot from the current state — used when widget-only
        // inputs (e.g. theme, scale, alpha, E2 unit) change and Home data itself doesn't
        // need to be rebuilt. Callers that actually need fresh Home data go through
        // HomeSnapshotRepository, whose publish the cache observer fans out to the widget.
        val written = homeSnapshotRepository.currentState()
            ?.let { state -> writeWidgetSnapshot(state, now) } == true
        if (!written) {
            // Never cleared for it: the widget keeps its last snapshot until a run
            // publishes both parts, which the cache observer writes.
            diagnosticsLogger.info(
                TAG,
                "widget_snapshot_refresh_skipped reason=no_current_state now=$now"
            )
            homeSnapshotRepository.requestRebuild()
        }
    }

    /**
     * Writes the widget from [state] while it is still the cache's latest
     * current state and has both parts: its rows from the Home data, its
     * estimate from the E2 inputs published with it, or the last one written
     * when that derivation fails. Returns false, and leaves the widget as it
     * is, otherwise.
     */
    suspend fun writeWidgetSnapshot(
        state: HomeCacheState,
        now: LocalDateTime = LocalDateTime.now(),
    ): Boolean = writeMutex.withLock {
        // Checked under the lock, so a slow write of an older state never lands
        // after a newer one's. Identity, not only the generation: the midnight
        // and zone-change runs publish at the same generation, and a state read
        // before them must not overwrite their rows.
        val latest = homeSnapshotRepository.cache.value === state &&
                state.generation == homeSnapshotRepository.generation.value
        val homeData = state.homeData
        val e2Inputs = state.e2Inputs
        if (!latest || homeData == null || e2Inputs == null) return@withLock false
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
        val widgetEstimate = e2EstimateRepository.widgetEstimate(e2Inputs, now)
        val built = buildWidgetSnapshotRecord(
            context = localizedContext,
            homeSnapshot = homeData,
            widgetEstimate = widgetEstimate,
            settings = settings,
            now = now,
            zoneId = zoneId,
            timeFormatter = timeFormatter,
        )
        // Null only when the derivation threw, and memoized until its key
        // moves, at midnight at the latest: the rows update under the last
        // estimate written rather than blank it. The widget draws that one
        // until its chart leaves the estimate's window, at most about ten
        // days after it was derived.
        val widgetSnapshot = if (widgetEstimate != null) {
            built
        } else {
            built.copy(pkProjection = widgetSnapshotStore.readSnapshot()?.pkProjection)
        }
        widgetSnapshotStore.writeSnapshot(widgetSnapshot)
        pushHrtWidgets(context, widgetSnapshot)
        diagnosticsLogger.info(
            TAG,
            "widget_snapshot_refreshed rows=${widgetSnapshot.doseRows.size} " +
                    "done=${widgetSnapshot.doneCount} total=${widgetSnapshot.totalCount}",
        )
        true
    }

    private fun Context.withAppLocale(settings: SettingsState): Context {
        val locale = Locale.forLanguageTag(settings.appLanguageOption.languageTag)
        val config = Configuration(resources.configuration).apply { setLocale(locale) }
        return createConfigurationContext(config)
    }

    companion object {
        private const val TAG = "WidgetSnapshotRepository"
    }
}
