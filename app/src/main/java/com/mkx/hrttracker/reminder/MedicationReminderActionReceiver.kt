package com.mkx.hrttracker.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mkx.hrttracker.di.AppScope
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

    override fun onReceive(context: Context, intent: Intent) {
        val slots = intent.getStringArrayListExtra(EXTRA_REMINDER_SLOTS)
            .orEmpty()
            .mapNotNull(::medicationReminderSlotFromStorageValue)
        if (slots.isEmpty()) {
            return
        }

        val notificationTag = intent.getStringExtra(EXTRA_NOTIFICATION_TAG)
        val pendingResult = goAsync()
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
                }
            }.also {
                pendingResult.finish()
            }
        }
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
