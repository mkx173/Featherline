package com.mkx.hrttracker.widget

import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// override ?: default ?: built-in. Pure so the resolution contract is testable
// without DataStore plumbing.
internal fun resolveEffectiveAppearance(
    override: WidgetAppearance?,
    default: WidgetAppearance?,
): WidgetAppearance = override ?: default ?: WidgetAppearance.Default

// The one-time mapping from the three legacy SettingsRepository keys. New theme
// params start at Default — that combination is the regression anchor.
internal fun legacyWidgetAppearance(
    contentScale: Float,
    backgroundAlpha: Float,
    darkMode: DarkModeOption,
): WidgetAppearance = WidgetAppearance.Default.copy(
    contentScale = contentScale,
    backgroundAlpha = backgroundAlpha,
    darkMode = darkMode,
).sanitized()

@Singleton
class WidgetAppearanceRepository @Inject constructor(
    private val store: WidgetAppearanceStore,
    private val settingsRepository: SettingsRepository,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    // null appWidgetId = "the default entry" (used by the v1 global UI and previews).
    // The default path has no override, so observe the default entry once instead of
    // combining two collectors on the same key; only a real appWidgetId needs the
    // override ?: default merge.
    fun effectiveFor(appWidgetId: Int?): Flow<WidgetAppearance> =
        if (appWidgetId == null) {
            store.observeEntry(null)
                .map { resolveEffectiveAppearance(null, it) }
                .distinctUntilChanged()
        } else {
            combine(
                store.observeEntry(appWidgetId),
                store.observeEntry(null),
            ) { override, default ->
                resolveEffectiveAppearance(override, default)
            }.distinctUntilChanged()
        }

    // One-shot bounded reads (not effectiveFor().first(), whose live flow never
    // completes under persistent I/O failure and would hang here).
    suspend fun currentEffective(appWidgetId: Int?): WidgetAppearance {
        val default = store.readEntry(null)
        val override = if (appWidgetId == null) null else store.readEntry(appWidgetId)
        return resolveEffectiveAppearance(override, default)
    }

    // Resolves several widget instances against a SINGLE read of the shared default entry,
    // so a multi-widget push doesn't re-read the default once per instance (currentEffective
    // would). Each id's override is layered over that one default.
    suspend fun currentEffectiveFor(appWidgetIds: IntArray): Map<Int, WidgetAppearance> {
        val default = store.readEntry(null)
        return appWidgetIds.associateWith { appWidgetId ->
            resolveEffectiveAppearance(store.readEntry(appWidgetId), default)
        }
    }

    suspend fun setDefault(appearance: WidgetAppearance) = store.setEntry(null, appearance)

    suspend fun updateDefault(transform: (WidgetAppearance) -> WidgetAppearance) =
        store.updateDefault(transform)

    // Dormant in v1 — activated by a future per-widget editor.
    suspend fun setForWidget(appWidgetId: Int, appearance: WidgetAppearance) =
        store.setEntry(appWidgetId, appearance)

    suspend fun deleteOverrides(appWidgetIds: IntArray) = store.deleteOverrides(appWidgetIds)

    fun observeChanges() = store.observeChanges()

    // Seeds appearance_default from the legacy SettingsRepository keys exactly once,
    // then deletes them. Idempotent and safe to call on every app start. Returns
    // true when legacy data existed (the store was seeded / appearance may have
    // changed): the caller must repaint widgets then, because the migration write
    // lands as the change-observer's dropped initial emission and the async startup
    // worker may have already pushed with Default appearance. A failed legacy-key
    // clear is non-fatal for the same reason a crash between seed and clear is: the
    // surviving keys make the next start re-run harmlessly (seed no-ops) and retry
    // the clear — but the seed DID land, so we still report true for the repaint.
    //
    // Several call sites invoke this fire-and-forget at startup/first-use (HomeWidgetManager,
    // SettingsViewModel, WidgetConfigActivity, BackupExportService); once the keys are gone
    // there is nothing left to do, so short-circuit on a process-lifetime flag to skip the
    // redundant readLegacyWidgetAppearance read every subsequent call would otherwise pay.
    // The flag is only set once the legacy keys are confirmed gone (absent, or cleared), so
    // a clear failure still re-runs and retries on the next call.
    @Volatile
    private var legacyMigrationComplete = false

    suspend fun migrateFromLegacySettingsIfNeeded(): Boolean {
        if (legacyMigrationComplete) return false
        val legacy = settingsRepository.readLegacyWidgetAppearance() ?: run {
            legacyMigrationComplete = true
            return false
        }
        val seeded = store.seedDefaultIfAbsent(
            legacyWidgetAppearance(legacy.contentScale, legacy.backgroundAlpha, legacy.darkMode)
        )
        val cleared = runCatching { settingsRepository.clearLegacyWidgetAppearanceKeys() }
            .onFailure { error -> if (error is CancellationException) throw error }
            .isSuccess
        if (cleared) legacyMigrationComplete = true
        diagnosticsLogger.info(TAG, "widget_appearance_migrated seeded=$seeded cleared=$cleared")
        return true
    }

    private companion object {
        const val TAG = "WidgetAppearanceRepo"
    }
}
