package com.mkx.hrttracker.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition

// Per-instance anchor selection. anchorId == TrackedDate.id is the only per-widget state;
// name/icon/palette/date are read live from the journal at composition (nothing the journal
// owns is duplicated here). Backed by Glance's built-in Preferences store keyed per
// appWidgetId, so deletion of a widget instance drops its entry for free.
internal val ANCHOR_ID_KEY: Preferences.Key<String> = stringPreferencesKey("anchor_id")

internal fun Preferences.anchorId(): String? = this[ANCHOR_ID_KEY]

// Persists the chosen anchor for one widget instance. Called from the config Activity,
// which resolves the GlanceId from the appWidgetId via GlanceAppWidgetManager.getGlanceIdBy.
internal suspend fun writeAnchorId(context: Context, glanceId: GlanceId, anchorId: String) {
    updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
        prefs.toMutablePreferences().apply { this[ANCHOR_ID_KEY] = anchorId }
    }
}
