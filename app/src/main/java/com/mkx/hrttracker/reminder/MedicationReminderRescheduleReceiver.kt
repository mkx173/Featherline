package com.mkx.hrttracker.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
    lateinit var medicationReminderScheduler: MedicationReminderScheduler

    @Inject
    lateinit var medicationReminderSnoozeScheduler: MedicationReminderSnoozeScheduler

    @Inject
    lateinit var diagnosticsLogger: AppDiagnosticsLogger

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> Unit
            else -> return diagnosticsLogger.info(
                TAG,
                "reminder_reschedule_receiver_ignored action=${intent.action}"
            )
        }

        val pendingResult = goAsync()
        diagnosticsLogger.info(TAG, "reminder_reschedule_receiver_received action=${intent.action}")
        appScope.launch {
            runCatching {
                medicationReminderScheduler.rescheduleAll()
                medicationReminderSnoozeScheduler.rescheduleAll()
                diagnosticsLogger.info(TAG, "reminder_reschedule_receiver_complete action=${intent.action}")
            }.onFailure { throwable ->
                diagnosticsLogger.warning(
                    TAG,
                    "reminder_reschedule_receiver_failed action=${intent.action}",
                    throwable,
                )
            }.also {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "MedicationReminderRescheduleReceiver"
    }
}
