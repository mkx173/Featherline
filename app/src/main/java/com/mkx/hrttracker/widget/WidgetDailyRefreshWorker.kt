package com.mkx.hrttracker.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDateTime
import java.time.ZoneId

class WidgetDailyRefreshWorker(
    private val appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(appContext, WidgetEntryPoint::class.java)
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
            updateAllHrtWidgets(appContext)
        }
        return Result.success()
    }
}
