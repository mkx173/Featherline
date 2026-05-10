package com.mkx.hrttracker.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MedicationReminderRescheduleReceiver : BroadcastReceiver() {
    @Inject
    @AppScope
    lateinit var appScope: CoroutineScope

    @Inject
    lateinit var reminderCapabilityReconciler: ReminderCapabilityReconciler

    @Inject
    lateinit var homeSnapshotRepository: HomeSnapshotRepository

    @Inject
    lateinit var diagnosticsLogger: AppDiagnosticsLogger

    override fun onReceive(context: Context, intent: Intent) {
        handleReminderRescheduleBroadcast(
            action = intent.action,
            appScope = appScope,
            reminderCapabilityReconciler = reminderCapabilityReconciler,
            homeSnapshotRepository = homeSnapshotRepository,
            diagnosticsLogger = diagnosticsLogger,
            goAsync = ::goAsync,
        )
    }
}

internal fun isReminderRescheduleReceiverAction(action: String?): Boolean {
    return action == Intent.ACTION_BOOT_COMPLETED ||
        action == Intent.ACTION_MY_PACKAGE_REPLACED ||
        action == Intent.ACTION_TIME_CHANGED ||
        action == Intent.ACTION_TIMEZONE_CHANGED ||
        action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
}

internal fun handleReminderRescheduleBroadcast(
    action: String?,
    appScope: CoroutineScope,
    reminderCapabilityReconciler: ReminderCapabilityReconciler,
    homeSnapshotRepository: HomeSnapshotRepository,
    diagnosticsLogger: AppDiagnosticsLogger,
    goAsync: () -> BroadcastReceiver.PendingResult?,
) {
    if (!isReminderRescheduleReceiverAction(action)) {
        diagnosticsLogger.info(
            REMINDER_RESCHEDULE_RECEIVER_TAG,
            "reminder_reschedule_receiver_ignored action=$action",
        )
        return
    }

    val pendingResult = goAsync()
    diagnosticsLogger.info(
        REMINDER_RESCHEDULE_RECEIVER_TAG,
        "reminder_reschedule_receiver_received action=$action",
    )
    appScope.launch {
        runCatching {
            reminderCapabilityReconciler.reconcile(reason = "receiver_$action")
            // The snapshot's stored zoneId is captured at write time; a TZ or wall-clock
            // change leaves it mismatched. isSnapshotUsable correctly rejects it for
            // display, but no refresh would otherwise be enqueued — so the snapshot
            // stays stale on disk until the next mutation. Force a refresh here.
            if (
                action == Intent.ACTION_TIMEZONE_CHANGED ||
                action == Intent.ACTION_TIME_CHANGED
            ) {
                homeSnapshotRepository.refreshHomeSnapshotAsync(force = true)
            }
            diagnosticsLogger.info(
                REMINDER_RESCHEDULE_RECEIVER_TAG,
                "reminder_reschedule_receiver_complete action=$action",
            )
        }.onFailure { throwable ->
            diagnosticsLogger.warning(
                REMINDER_RESCHEDULE_RECEIVER_TAG,
                "reminder_reschedule_receiver_failed action=$action",
                throwable,
            )
        }.also {
            pendingResult?.finish()
        }
    }
}

private const val REMINDER_RESCHEDULE_RECEIVER_TAG = "MedicationReminderRescheduleReceiver"
