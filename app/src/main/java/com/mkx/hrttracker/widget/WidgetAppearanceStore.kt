package com.mkx.hrttracker.widget

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// Dedicated Preferences DataStore: `appearance_default` plus dormant
// `appearance_<appWidgetId>` overrides (storage is per-instance from day one so a
// later per-widget editor needs no migration; the v1 UI writes only the default).
// Same recovery posture as the settings store (SettingsRepository): corruption
// replaces with empty (widgets fall back to Default appearance), and transient
// IOExceptions must not tear down the appearance collectors.
private val Context.widgetAppearanceDataStore by preferencesDataStore(
    name = "widget_appearance",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

private const val IO_RETRY_ATTEMPTS = 3L
private const val IO_RETRY_DELAY_MS = 250L

private val DEFAULT_KEY = stringPreferencesKey("appearance_default")
private fun overrideKey(appWidgetId: Int) = stringPreferencesKey("appearance_$appWidgetId")

@Singleton
class WidgetAppearanceStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    // Bounded recovery for the one-shot readers (currentEffective, via observeEntry):
    // retry transient IOExceptions (low memory, EBUSY during fsync) briefly, then fall
    // back to empty (→ Default) so a read never hangs under persistent I/O failure. The
    // flow completes after that fallback — acceptable for `.first()`, but NOT for the
    // app-lifetime change observer, which must never complete (see observeChanges).
    private fun safeData(): Flow<Preferences> =
        context.widgetAppearanceDataStore.data
            .retryWhen { cause, attempt ->
                val retry = cause is IOException && attempt < IO_RETRY_ATTEMPTS
                if (retry) delay(IO_RETRY_DELAY_MS)
                retry
            }
            .catch { cause ->
                if (cause is IOException) emit(emptyPreferences()) else throw cause
            }

    internal fun observeEntry(appWidgetId: Int?): Flow<WidgetAppearance?> =
        safeData()
            .map { it.decodeEntry(appWidgetId) }
            .distinctUntilChanged()

    // One tick per store mutation; HomeWidgetManager re-renders widgets on it. This is
    // an app-lifetime collector, so unlike safeData it must NEVER complete on I/O
    // failure — a completed flow would permanently stop appearance-driven repaints.
    // Retry transient IOExceptions indefinitely with capped backoff so the observer
    // pauses across an outage and resumes (re-emitting the current value) on recovery,
    // instead of terminating; non-IO failures still propagate, matching safeData.
    internal fun observeChanges(): Flow<Preferences> =
        context.widgetAppearanceDataStore.data
            .retryWhen { cause, _ ->
                if (cause !is IOException) return@retryWhen false
                delay(IO_RETRY_DELAY_MS)
                true
            }

    internal suspend fun setEntry(appWidgetId: Int?, appearance: WidgetAppearance) {
        context.widgetAppearanceDataStore.edit { prefs ->
            prefs[keyFor(appWidgetId)] = WidgetAppearanceCodec.encode(appearance.sanitized())
        }
    }

    internal suspend fun updateDefault(transform: (WidgetAppearance) -> WidgetAppearance) {
        context.widgetAppearanceDataStore.edit { prefs ->
            val current = prefs[DEFAULT_KEY]?.let(WidgetAppearanceCodec::decode)
                ?: WidgetAppearance.Default
            prefs[DEFAULT_KEY] = WidgetAppearanceCodec.encode(transform(current).sanitized())
        }
    }

    internal suspend fun deleteOverrides(appWidgetIds: IntArray) {
        context.widgetAppearanceDataStore.edit { prefs ->
            appWidgetIds.forEach { prefs.remove(overrideKey(it)) }
        }
    }

    internal suspend fun seedDefaultIfAbsent(appearance: WidgetAppearance): Boolean {
        var seeded = false
        context.widgetAppearanceDataStore.edit { prefs ->
            if (prefs[DEFAULT_KEY] == null) {
                prefs[DEFAULT_KEY] = WidgetAppearanceCodec.encode(appearance.sanitized())
                seeded = true
            }
        }
        return seeded
    }

    private fun keyFor(appWidgetId: Int?): Preferences.Key<String> =
        if (appWidgetId == null) DEFAULT_KEY else overrideKey(appWidgetId)

    private fun Preferences.decodeEntry(appWidgetId: Int?): WidgetAppearance? =
        this[keyFor(appWidgetId)]?.let(WidgetAppearanceCodec::decode)
}
