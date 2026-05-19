package com.mkx.hrttracker.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

class WidgetDateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handle(context: Context) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext, WidgetEntryPoint::class.java
        )
        val widgetSnapshotStore = entryPoint.widgetSnapshotStore()
        val homeSnapshotRepository = entryPoint.homeSnapshotRepository()

        val now = LocalDateTime.now()
        val zoneId = ZoneId.systemDefault()
        val snapshot = widgetSnapshotStore.readSnapshot()

        val projectionExpired = snapshot == null ||
            snapshot.pkProjection?.toPkProjectionResult(now, zoneId) == null

        if (projectionExpired) {
            homeSnapshotRepository.refreshHomeSnapshotIfNeeded(force = true)
        } else {
            updateAllHrtWidgets(context.applicationContext)
        }
    }

    companion object {
        private val HANDLED_ACTIONS = setOf(
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
