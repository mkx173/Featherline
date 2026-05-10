package com.mkx.hrttracker.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mkx.hrttracker.MainActivity
import com.mkx.hrttracker.R
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            diagnosticsLogger.info(TAG, "reminder_notification_channel_skipped sdk=${Build.VERSION.SDK_INT}")
            return
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            context.getString(R.string.reminder_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.reminder_notification_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
        diagnosticsLogger.info(TAG, "reminder_notification_channel_created channelId=$REMINDER_CHANNEL_ID")
    }

    fun showDoseReminderNotification(
        bundle: MedicationReminderBundle,
        canSnooze: Boolean = true,
    ) {
        if (!canPostNotifications()) {
            diagnosticsLogger.info(
                TAG,
                "reminder_notification_show_skipped reason=no_permission " +
                    "tag=${bundle.notificationTag} items=${bundle.items.size}"
            )
            return
        }
        createNotificationChannel()

        val notificationTag = bundle.notificationTag
        val contentIntent = PendingIntent.getActivity(
            context,
            REMINDER_NOTIFICATION_REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                data = reminderNotificationContentData(notificationTag)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val isMerged = bundle.items.size > 1
        val notificationText = buildReminderNotificationText(bundle)
        val title = context.getString(notificationText.titleRes)
        val body = notificationText.body.resolve()
        val logActionTitle = context.getString(
            if (isMerged) {
                R.string.reminder_notification_action_log_all
            } else {
                R.string.reminder_notification_action_log_now
            }
        )

        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setStyle(
                NotificationCompat.InboxStyle().also { style ->
                    bundle.items.forEach { item ->
                        style.addLine(item.groupName)
                    }
                }
            )
            .apply {
                if (canSnooze) {
                    addAction(
                        R.drawable.ic_snooze,
                        context.getString(R.string.reminder_notification_action_remind_later),
                        buildReminderActionPendingIntent(
                            action = ACTION_MEDICATION_REMINDER_REMIND_LATER,
                            bundle = bundle,
                            notificationTag = notificationTag,
                        )
                    )
                }
            }
            .addAction(
                R.drawable.ic_edit_square,
                logActionTitle,
                buildReminderActionPendingIntent(
                    action = ACTION_MEDICATION_REMINDER_LOG_NOW,
                    bundle = bundle,
                    notificationTag = notificationTag,
                )
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                notificationTag,
                DOSE_REMINDER_NOTIFICATION_ID,
                notification
            )
            diagnosticsLogger.info(
                TAG,
                "reminder_notification_shown tag=$notificationTag " +
                    "items=${bundle.items.size} canSnooze=$canSnooze isMerged=$isMerged"
            )
        } catch (_: SecurityException) {
            // Notification permission can be revoked after the preflight check.
            diagnosticsLogger.warning(
                TAG,
                "reminder_notification_show_failed reason=security_exception tag=$notificationTag"
            )
        }
    }

    fun showDoseReminderLoggedToast(entryCount: Int) {
        diagnosticsLogger.info(TAG, "reminder_notification_logged_toast entryCount=$entryCount")
        showToast(
            context.resources.getQuantityString(
                R.plurals.reminder_notification_entries_added,
                entryCount,
                entryCount,
            )
        )
    }

    fun showDoseReminderSnoozedToast(snoozeMinutes: Long) {
        diagnosticsLogger.info(TAG, "reminder_notification_snoozed_toast minutes=$snoozeMinutes")
        showToast(
            context.getString(
                R.string.reminder_notification_snoozed,
                snoozeMinutes,
            )
        )
    }

    fun cancelDoseReminderNotification(notificationTag: String) {
        NotificationManagerCompat.from(context).cancel(
            notificationTag,
            DOSE_REMINDER_NOTIFICATION_ID,
        )
        diagnosticsLogger.info(TAG, "reminder_notification_cancelled tag=$notificationTag")
    }

    /**
     * Cancel every dose-reminder notification this app currently has visible.
     * Used by backup restore: any active reminder references slot UUIDs from the
     * pre-restore database, so the notification must be dismissed before the
     * user can tap an action that would dispatch with stale state.
     */
    fun cancelAllDoseReminderNotifications() {
        NotificationManagerCompat.from(context).cancelAll()
        diagnosticsLogger.info(TAG, "reminder_notifications_cancelled_all")
    }

    fun canPostNotifications(): Boolean {
        try {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                return false
            }
        } catch (_: Throwable) {
            // Notification service is not available in some environments like Android Studio preview.
            return true
        }

        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun buildReminderActionPendingIntent(
        action: String,
        bundle: MedicationReminderBundle,
        notificationTag: String,
    ): PendingIntent {
        val intent = Intent(context, MedicationReminderActionReceiver::class.java).apply {
            this.action = action
            data = reminderNotificationActionData(action, bundle)
            putStringArrayListExtra(
                EXTRA_REMINDER_SLOTS,
                ArrayList(bundle.slots.map(MedicationReminderSlot::toStorageValue)),
            )
            putExtra(EXTRA_NOTIFICATION_TAG, notificationTag)
        }

        return PendingIntent.getBroadcast(
            context,
            REMINDER_NOTIFICATION_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ReminderNotificationBody.resolve(): String {
        return when (this) {
            is ReminderNotificationBody.GroupName -> groupName
            is ReminderNotificationBody.MoreGroups -> context.resources.getQuantityString(
                R.plurals.reminder_notification_more_groups,
                additionalGroupCount,
                firstGroupName,
                additionalGroupCount,
            )
        }
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}

private fun reminderNotificationContentData(notificationTag: String): Uri {
    return Uri.parse("$REMINDER_NOTIFICATION_CONTENT_URI_PREFIX/$notificationTag")
}

private fun reminderNotificationActionData(
    action: String,
    bundle: MedicationReminderBundle,
): Uri {
    val actionUuid = java.util.UUID.nameUUIDFromBytes(
        "${action}:${bundle.notificationTag}".toByteArray(java.nio.charset.StandardCharsets.UTF_8)
    )
    return Uri.parse(
        "$REMINDER_NOTIFICATION_ACTION_URI_PREFIX/$action/$actionUuid"
    )
}

private const val DOSE_REMINDER_NOTIFICATION_ID = 0
private const val REMINDER_NOTIFICATION_REQUEST_CODE = 0
private const val REMINDER_NOTIFICATION_CONTENT_URI_PREFIX =
    "hrttracker://medication-reminder-notification"
private const val REMINDER_NOTIFICATION_ACTION_URI_PREFIX =
    "hrttracker://medication-reminder-action"
const val REMINDER_CHANNEL_ID = "dose_reminders"
private const val TAG = "ReminderNotificationManager"
