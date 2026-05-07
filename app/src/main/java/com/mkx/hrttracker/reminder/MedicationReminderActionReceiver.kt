package com.mkx.hrttracker.reminder

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
class MedicationReminderActionReceiver : BroadcastReceiver() {
    @Inject
    @AppScope
    lateinit var appScope: CoroutineScope

    @Inject
    lateinit var actionHandler: MedicationReminderActionHandler

    @Inject
    lateinit var diagnosticsLogger: AppDiagnosticsLogger

    override fun onReceive(context: Context, intent: Intent) {
        val slots = intent.getStringArrayListExtra(EXTRA_REMINDER_SLOTS)
            .orEmpty()
            .mapNotNull(::medicationReminderSlotFromStorageValue)
        if (slots.isEmpty()) {
            diagnosticsLogger.info(
                TAG,
                "reminder_action_receiver_ignored reason=empty_or_invalid_slots action=${intent.action}"
            )
            return
        }

        val notificationTag = intent.getStringExtra(EXTRA_NOTIFICATION_TAG)
        val pendingResult = goAsync()
        diagnosticsLogger.info(
            TAG,
            "reminder_action_receiver_received action=${intent.action} " +
                "slots=${slots.size} notificationTag=${notificationTag.orEmpty()}"
        )
        appScope.launch {
            runCatching {
                when (intent.action) {
                    ACTION_MEDICATION_REMINDER_LOG_NOW -> actionHandler.logNow(
                        slots = slots,
                        notificationTag = notificationTag,
                    )

                    ACTION_MEDICATION_REMINDER_REMIND_LATER -> actionHandler.remindLater(
                        slots = slots,
                        notificationTag = notificationTag,
                    )

                    ACTION_MEDICATION_REMINDER_SNOOZE_ALARM -> actionHandler.showSnoozedReminder(
                        slots = slots,
                        notificationTag = notificationTag,
                    )

                    else -> diagnosticsLogger.info(
                        TAG,
                        "reminder_action_receiver_ignored reason=unknown_action action=${intent.action}"
                    )
                }
            }.onFailure { throwable ->
                diagnosticsLogger.warning(
                    TAG,
                    "reminder_action_receiver_failed action=${intent.action} slots=${slots.size}",
                    throwable,
                )
            }.also {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "MedicationReminderActionReceiver"
    }
}

const val ACTION_MEDICATION_REMINDER_LOG_NOW =
    "com.mkx.hrttracker.action.MEDICATION_REMINDER_LOG_NOW"
const val ACTION_MEDICATION_REMINDER_REMIND_LATER =
    "com.mkx.hrttracker.action.MEDICATION_REMINDER_REMIND_LATER"
const val ACTION_MEDICATION_REMINDER_SNOOZE_ALARM =
    "com.mkx.hrttracker.action.MEDICATION_REMINDER_SNOOZE_ALARM"
const val EXTRA_REMINDER_SLOTS = "reminderSlots"
const val EXTRA_NOTIFICATION_TAG = "notificationTag"
