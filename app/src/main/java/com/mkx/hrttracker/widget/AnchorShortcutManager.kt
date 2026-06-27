package com.mkx.hrttracker.widget

import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.TrackedDate
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate
import kotlinx.coroutines.flow.first

// The pinned-shortcut surface. No new persistence: the set of pinned anchor shortcuts is
// ShortcutManager.pinnedShortcuts whose id matches a live anchor id. shortcutId == anchor.id
// is the join key. An anchor is pinned once; all later updates go through updateShortcuts /
// disableShortcuts (re-pinning reuses the stale cached icon — probe gotcha).
object AnchorShortcutManager {

    fun isSupported(context: Context): Boolean =
        ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    // Entry A: request the launcher pin a shortcut for [anchor]. Gated on support.
    fun pin(context: Context, anchor: TrackedDate) {
        if (!isSupported(context)) return
        val shortcut = buildShortcut(context, anchor, LocalDate.now())
        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    // Daily / on-change refresh: regenerate the bitmap for every pinned id that still maps
    // to a live anchor, and disable orphaned pins. Reads anchors live by id.
    suspend fun refreshAll(context: Context) {
        val journalRepository = EntryPointAccessors
            .fromApplication(context, WidgetEntryPoint::class.java)
            .journalRepository()
        val liveAnchors = journalRepository.getCachedTrackedDates()
            ?: journalRepository.observeTrackedDates().first()
        val liveById = liveAnchors.associateBy { it.id }

        val pinnedIds = ShortcutManagerCompat
            .getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
            .map { it.id }
            .toSet()

        val plan = anchorShortcutRefreshPlan(pinnedIds, liveById.keys)

        val today = LocalDate.now()
        val updated = plan.toUpdate.mapNotNull { id -> liveById[id] }
            .map { anchor -> buildShortcut(context, anchor, today) }
        if (updated.isNotEmpty()) {
            ShortcutManagerCompat.updateShortcuts(context, updated)
        }
        if (plan.toDisable.isNotEmpty()) {
            ShortcutManagerCompat.disableShortcuts(
                context,
                plan.toDisable.toList(),
                context.getString(R.string.anchor_shortcut_disabled_message),
            )
        }
    }

    private fun buildShortcut(
        context: Context,
        anchor: TrackedDate,
        today: LocalDate,
    ): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, anchor.id)
            // Label is only shown in the system shortcut list / a11y, never on the masked
            // icon — privacy of the home-screen icon (number only) is preserved.
            .setShortLabel(anchor.name)
            .setIcon(
                IconCompat.createWithAdaptiveBitmap(
                    AnchorIconRenderer.render(context, anchor, today)
                )
            )
            .setIntent(anchorOpenMilestonesIntent(context).setAction(android.content.Intent.ACTION_VIEW))
            .build()
}
