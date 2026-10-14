package com.mkx.hrttracker.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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

// Written by v1.3.x widgetScale(); folded once by foldLegacyBaselineScale.
private const val LEGACY_BASELINE_PREFS = "hrt_widget_baseline"
private val LEGACY_BASELINE_FOLDED_KEY = booleanPreferencesKey("legacy_baseline_folded")

@Singleton
class WidgetAppearanceStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    // Bounded reader for one-shot reads (readEntry → currentEffective): retry transient
    // IOExceptions (low memory, EBUSY during fsync) briefly, then fall back to empty
    // (→ Default) so a read never hangs under persistent I/O failure, then complete.
    // Completing is fine for `.first()`, but NOT for the live observers below.
    private fun boundedData(): Flow<Preferences> =
        context.widgetAppearanceDataStore.data
            .retryWhen { cause, attempt ->
                val retry = cause is IOException && attempt < IO_RETRY_ATTEMPTS
                if (retry) delay(IO_RETRY_DELAY_MS)
                retry
            }
            .catch { cause ->
                if (cause is IOException) emit(emptyPreferences()) else throw cause
            }

    // App-lifetime reader for the live observers (observeEntry, observeChanges). Retry
    // transient IOExceptions indefinitely with capped backoff and NEVER complete, so an
    // observer pauses across an outage and resumes (re-emitting the current value) on
    // recovery instead of terminating. A completed flow would permanently freeze
    // appearance-driven updates — both the in-app StateFlow and the widget composition
    // collect this. Non-IO failures still propagate, matching boundedData.
    private fun liveData(): Flow<Preferences> =
        context.widgetAppearanceDataStore.data
            .retryWhen { cause, _ ->
                if (cause !is IOException) return@retryWhen false
                delay(IO_RETRY_DELAY_MS)
                true
            }

    // Live per-entry observation for the UI/widget collectors — never completes so it
    // recovers after an I/O outage (see liveData).
    internal fun observeEntry(appWidgetId: Int?): Flow<WidgetAppearance?> =
        liveData()
            .map { it.decodeEntry(appWidgetId) }
            .distinctUntilChanged()

    // One-shot bounded read backing currentEffective — completes (see boundedData) so
    // push/export callers never hang under persistent I/O failure.
    internal suspend fun readEntry(appWidgetId: Int?): WidgetAppearance? =
        boundedData().map { it.decodeEntry(appWidgetId) }.first()

    // One tick per store mutation; HomeWidgetManager re-renders widgets on it.
    internal fun observeChanges(): Flow<Preferences> = liveData()

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

    // The v1.3.x launcher factor from hrt_widget_baseline, weighed by which dose widget types
    // are placed now, or null when none was captured (fresh install, or already cleared).
    // Blocking I/O.
    internal fun readLegacyBaselineScaleRatio(): Float? {
        val prefs = legacyBaselinePrefs()
        return legacyBaselineScaleRatio(
            mediumDp = prefs.getFloat("medium_height_dp_v3", 0f),
            largeDp = prefs.getFloat("large_height_dp_v3", 0f),
            mediumPlaced = isPlaced(HrtWidgetMediumReceiver::class.java),
            largePlaced = isPlaced(HrtWidgetLargeReceiver::class.java),
        )
    }

    // False when the lookup fails (no widget support, JVM tests); the ratio then falls back
    // to any captured height.
    private fun isPlaced(receiverClass: Class<*>): Boolean = runCatching {
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, receiverClass))
            .isNotEmpty()
    }.getOrDefault(false)

    // Multiplies [ratio] into the default's contentScale and sets the folded marker in one
    // edit, so the fold lands at most once even if clearing the legacy file fails or the
    // process dies before it. Returns false when an earlier run already folded.
    internal suspend fun foldLegacyBaselineScale(ratio: Float): Boolean {
        var folded = false
        context.widgetAppearanceDataStore.edit { prefs ->
            if (prefs[LEGACY_BASELINE_FOLDED_KEY] == true) return@edit
            val current = prefs[DEFAULT_KEY]?.let(WidgetAppearanceCodec::decode)
                ?: WidgetAppearance.Default
            prefs[DEFAULT_KEY] = WidgetAppearanceCodec.encode(
                current.copy(contentScale = current.contentScale * ratio).sanitized()
            )
            prefs[LEGACY_BASELINE_FOLDED_KEY] = true
            folded = true
        }
        return folded
    }

    // Blocking I/O. False when the clear failed; the marker keeps a retry from refolding.
    internal fun clearLegacyBaseline(): Boolean = legacyBaselinePrefs().edit().clear().commit()

    private fun legacyBaselinePrefs() =
        context.getSharedPreferences(LEGACY_BASELINE_PREFS, Context.MODE_PRIVATE)

    private fun keyFor(appWidgetId: Int?): Preferences.Key<String> =
        if (appWidgetId == null) DEFAULT_KEY else overrideKey(appWidgetId)

    private fun Preferences.decodeEntry(appWidgetId: Int?): WidgetAppearance? =
        this[keyFor(appWidgetId)]?.let(WidgetAppearanceCodec::decode)
}
