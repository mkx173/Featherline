package com.mkx.hrttracker.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mkx.hrttracker.data.repository.E2EstimateRepository
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.util.MedicationDayBoundary
import com.mkx.hrttracker.util.medicationDay
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDateTime

class WidgetDailyRefreshWorker(
    private val appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val entryPoint =
            EntryPointAccessors.fromApplication(appContext, WidgetEntryPoint::class.java)
        handleWidgetDailyRefresh(
            now = LocalDateTime.now(),
            snapshot = entryPoint.widgetSnapshotStore().readSnapshot(),
            homeSnapshotRepository = entryPoint.homeSnapshotRepository(),
            widgetSnapshotRepository = entryPoint.widgetSnapshotRepository(),
            e2EstimateRepository = entryPoint.e2EstimateRepository(),
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
 * The rows are stale once the calendar day or the medication day at 04:00
 * moves on: a night-owl plan's rows leave Today at 04:00, whatever the plans are.
 */
internal suspend fun handleWidgetDailyRefresh(
    now: LocalDateTime,
    snapshot: WidgetSnapshotRecord?,
    homeSnapshotRepository: HomeSnapshotRepository,
    widgetSnapshotRepository: WidgetSnapshotRepository,
    e2EstimateRepository: E2EstimateRepository,
    rerender: suspend () -> Unit,
) {
    val anchorDateStale = snapshot?.isAnchoredBefore(now) == true
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

internal fun WidgetSnapshotRecord.isAnchoredBefore(now: LocalDateTime): Boolean =
    anchorDateEpochDay < now.toLocalDate().toEpochDay() ||
        fourAmAnchorDateEpochDay < medicationDay(now, MedicationDayBoundary.FourAm).toEpochDay()
