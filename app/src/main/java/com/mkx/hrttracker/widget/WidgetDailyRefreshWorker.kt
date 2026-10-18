package com.mkx.hrttracker.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mkx.hrttracker.data.repository.E2EstimateRepository
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.util.medicationDay
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDateTime

private const val TAG = "WidgetDailyRefreshWorker"

class WidgetDailyRefreshWorker(
    private val appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val entryPoint =
            EntryPointAccessors.fromApplication(appContext, WidgetEntryPoint::class.java)
        val dayStartMinutes = runCatching {
            entryPoint.settingsRepository().getCurrentSettings().medicationDayStartMinutes
        }.onFailure { throwable ->
            if (throwable is kotlinx.coroutines.CancellationException) throw throwable
            entryPoint.diagnosticsLogger().warning(TAG, "widget_day_start_read_failed", throwable)
        }.getOrDefault(0)
        handleWidgetDailyRefresh(
            now = LocalDateTime.now(),
            snapshot = entryPoint.widgetSnapshotStore().readSnapshot(),
            homeSnapshotRepository = entryPoint.homeSnapshotRepository(),
            widgetSnapshotRepository = entryPoint.widgetSnapshotRepository(),
            e2EstimateRepository = entryPoint.e2EstimateRepository(),
            dayStartMinutes = dayStartMinutes,
            rerender = { updateAllHrtWidgets(appContext) },
        )
        return Result.success()
    }
}

/**
 * One run of the 15-minute worker. When [snapshot]'s rows are stale at
 * [now], or there is no widget record yet, it rebuilds the record from the
 * current cache state, with no Room read and no solve. Without a current
 * state that has both parts it waits for a run instead, whose publish
 * rewrites the widget; so the worker also retries a run that failed.
 * Otherwise it only re-renders the widget. Either way it then warms Home's
 * estimate.
 *
 * The rows' anchor is the medication day at [dayStartMinutes], not the
 * calendar day, so a night-owl boundary keeps the row for the dose that is
 * still the current medication day's.
 */
internal suspend fun handleWidgetDailyRefresh(
    now: LocalDateTime,
    snapshot: WidgetSnapshotRecord?,
    homeSnapshotRepository: HomeSnapshotRepository,
    widgetSnapshotRepository: WidgetSnapshotRepository,
    e2EstimateRepository: E2EstimateRepository,
    dayStartMinutes: Int = 0,
    rerender: suspend () -> Unit,
) {
    val anchorDateStale = snapshot?.isAnchoredBefore(now, dayStartMinutes) == true
    val doseStatusStale = snapshot?.doseRows?.any { row ->
        when (row.status) {
            WidgetDoseStatus.UPCOMING ->
                !now.isBefore(row.scheduledAt.minusHours(1))

            WidgetDoseStatus.DUE_SOON ->
                !now.isBefore(row.scheduledAt.plusHours(1))

            else -> false
        }
    } == true

    try {
        if (snapshot == null || anchorDateStale || doseStatusStale) {
            val written = homeSnapshotRepository.currentState()
                ?.let { state -> widgetSnapshotRepository.writeWidgetSnapshot(state, now) } == true
            // HomeWidgetManager's cache observer rewrites the widget from the run's publish.
            // Waited for, so the job, and the process with it, outlive the run. A state a
            // newer publish superseded isn't written either: the observer writes that one,
            // and the run is only redundant.
            if (!written) homeSnapshotRepository.rebuildNow()
        } else {
            rerender()
        }
    } finally {
        // Even when the widget write throws. A slot can pass without a reminder
        // (reminders off, or Doze delayed the alarm): store Home's estimate
        // without it. Only a key when the stored estimate already matches.
        e2EstimateRepository.warmHome()
    }
}

internal fun WidgetSnapshotRecord.isAnchoredBefore(
    now: LocalDateTime,
    dayStartMinutes: Int = 0,
): Boolean = anchorDateEpochDay < medicationDay(now, dayStartMinutes).toEpochDay()
