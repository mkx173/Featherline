package com.mkx.hrttracker.widget

import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.TrackedDate
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate

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
    // to a live anchor, re-enable wrongly-disabled pins, and disable orphaned ones.
    // awaitTrackedDates (never the cache / raw observe) so the cold-start not-loaded
    // window can't read as "all anchors deleted" and mass-disable the pins.
    suspend fun refreshAll(context: Context) {
        val journalRepository = EntryPointAccessors
            .fromApplication(context, WidgetEntryPoint::class.java)
            .journalRepository()
        val liveById = journalRepository.awaitTrackedDates().associateBy { it.id }

        val pinnedShortcuts = ShortcutManagerCompat
            .getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)

        val plan = anchorShortcutRefreshPlan(
            pinnedIds = pinnedShortcuts.map { it.id }.toSet(),
            liveAnchorIds = liveById.keys,
            disabledPinnedIds = pinnedShortcuts.filterNot { it.isEnabled }.map { it.id }.toSet(),
        )

        val today = LocalDate.now()
        if (plan.toEnable.isNotEmpty()) {
            ShortcutManagerCompat.enableShortcuts(
                context,
                plan.toEnable.mapNotNull { id -> liveById[id] }
                    .map { anchor -> buildShortcut(context, anchor, today) },
            )
        }
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
