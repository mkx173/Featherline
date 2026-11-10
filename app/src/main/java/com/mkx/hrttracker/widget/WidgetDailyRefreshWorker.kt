package com.mkx.hrttracker.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mkx.hrttracker.data.repository.E2EstimateRepository
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.model.medication.isDueSoon
import com.mkx.hrttracker.model.medication.isPastDue
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import com.mkx.hrttracker.util.MedicationDayBoundary
import com.mkx.hrttracker.util.medicationDay
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class WidgetDailyRefreshWorker(
    private val appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val entryPoint =
            EntryPointAccessors.fromApplication(appContext, WidgetEntryPoint::class.java)
        val clockNow = Instant.now()
        handleWidgetDailyRefresh(
            now = LocalDateTime.ofInstant(clockNow, ZoneId.systemDefault()),
            clockNow = clockNow,
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
 * They are also stale once a section start passes after they were built:
 * noon and 18:00 move no date and may leave no row to turn stale, so this
 * run rebuilds what a late, missed or unarmed date alarm left. A dose
 * status is stale once the builder would give it another, between instants
 * as it does. And the record is stale when it wasn't built from the current
 * state: the write of that state threw, and nothing else writes it again. A
 * rewrite that throws is logged, and the widget re-rendered from its record.
 */
internal suspend fun handleWidgetDailyRefresh(
    now: LocalDateTime,
    snapshot: WidgetSnapshotRecord?,
    homeSnapshotRepository: HomeSnapshotRepository,
    widgetSnapshotRepository: WidgetSnapshotRepository,
    e2EstimateRepository: E2EstimateRepository,
    rerender: suspend () -> Unit,
    /** The clock's instant: which pass of a fall-back hour [now] is on. */
    clockNow: Instant = now.atZone(ZoneId.systemDefault()).toInstant(),
    diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    val zoneId = ZoneId.systemDefault()
    val anchorDateStale = snapshot?.isAnchoredBefore(now) == true
    val doseStatusStale = snapshot?.doseRows?.any { row ->
        val pastDue = isPastDue(row.scheduledAt, now, zoneId, clockNow)
        when (row.status) {
            WidgetDoseStatus.UPCOMING -> pastDue || isDueSoon(row.scheduledAt, now, zoneId, clockNow)
            WidgetDoseStatus.DUE_SOON -> pastDue
            else -> false
        }
    } == true

    try {
        val state = homeSnapshotRepository.currentState()
        val notFromState = state?.homeData?.generatedAtEpochMillis
            ?.let { stamp -> snapshot?.sourceGeneratedAtEpochMillis != stamp } == true
        if (snapshot == null || anchorDateStale || doseStatusStale || notFromState) {
            val written = try {
                state?.let { widgetSnapshotRepository.writeWidgetSnapshot(it) } == true
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                // Every run would throw the same way: keep drawing the record, and try again next run.
                diagnosticsLogger.warning(TAG, "widget_daily_refresh_write_failed", throwable)
                rerender()
                return
            }
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

private const val TAG = "WidgetDailyRefreshWorker"

internal fun WidgetSnapshotRecord.isAnchoredBefore(now: LocalDateTime): Boolean =
    anchorDateEpochDay < now.toLocalDate().toEpochDay() ||
        fourAmAnchorDateEpochDay < medicationDay(now, MedicationDayBoundary.FourAm).toEpochDay() ||
        !now.isBefore(nextWidgetDateRefreshAt(builtAt))
