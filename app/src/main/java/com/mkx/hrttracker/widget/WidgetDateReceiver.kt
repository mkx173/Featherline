package com.mkx.hrttracker.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WidgetDateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(context, action)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handle(context: Context, action: String) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext, WidgetEntryPoint::class.java
        )
        val diagnosticsLogger = entryPoint.diagnosticsLogger()
        diagnosticsLogger.info(TAG, "widget_date_receiver_received action=$action")
        scheduleNextWidgetDateRefresh(
            context = context.applicationContext,
            diagnosticsLogger = diagnosticsLogger,
        )
        // Force home refresh; HomeWidgetManager's snapshot observer rebuilds the widget.
        entryPoint.homeSnapshotRepository()
            .refreshHomeSnapshotIfNeeded(force = true)
        // Anchor surfaces read the journal directly (not the dose snapshot), so the home
        // refresh above does not update them. Refresh them on the same immediate path so a
        // midnight / clock / timezone / reboot event updates them without a polling worker.
        // Guard each independently: both can throw (SQLCipher open failure via databaseHolder,
        // ShortcutManager rate limits / IllegalArgumentException), and an uncaught throw from
        // this root coroutine would crash the process on every midnight/boot/timezone
        // broadcast while the condition holds. Mirrors AnchorWidgetManager's runCatching pair;
        // one failing must not skip the other (nor, via the finally, pendingResult.finish()).
        runCatching { updateAllAnchorWidgets(context.applicationContext) }
            .onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                diagnosticsLogger.warning(TAG, "anchor_widget_refresh_failed action=$action", it)
            }
        runCatching { AnchorShortcutManager.refreshAll(context.applicationContext) }
            .onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                diagnosticsLogger.warning(TAG, "anchor_shortcut_refresh_failed action=$action", it)
            }
        diagnosticsLogger.info(TAG, "widget_date_receiver_complete action=$action")
    }

    companion object {
        private const val TAG = "WidgetDateReceiver"
        private val HANDLED_ACTIONS = setOf(
            ACTION_WIDGET_DATE_REFRESH,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
