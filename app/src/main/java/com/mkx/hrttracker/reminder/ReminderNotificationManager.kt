package com.mkx.hrttracker.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import com.mkx.hrttracker.EXTRA_HIGHLIGHT_KIND
import com.mkx.hrttracker.EXTRA_HIGHLIGHT_SCHEDULED_TARGETS
import com.mkx.hrttracker.HIGHLIGHT_KIND_SCHEDULED
import com.mkx.hrttracker.MainActivity
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ScheduledDoseRowHighlightTarget
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.toStorageValue
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import com.mkx.hrttracker.util.medicineDisplayName
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    /**
     * Creates (or updates the mutable name/description fields of) the
     * dose-reminder channel.
     *
     * When called in response to a locale change, pass [languageTag] (e.g.
     * `"en"`, `"zh-Hans"`). The strings are then resolved through a
     * configuration-overridden context, sidestepping the timing gap where
     * `AppCompatDelegate.setApplicationLocales` has updated the system's
     * per-app locale but the singleton ApplicationContext's Resources cache
     * hasn't refreshed yet. Without this, re-registering the channel right
     * after the locale switch would just re-write the channel with the
     * old-locale strings.
     */
    fun createNotificationChannel(languageTag: String? = null) {
        val resolvedContext = languageTag?.let(::localizedContextFor) ?: context
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            resolvedContext.getString(R.string.reminder_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = resolvedContext.getString(R.string.reminder_notification_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
        diagnosticsLogger.info(
            TAG,
            "reminder_notification_channel_created channelId=$REMINDER_CHANNEL_ID " +
                "languageTag=${languageTag ?: "default"}"
        )
    }

    private fun localizedContextFor(languageTag: String): Context {
        val locale = LocaleListCompat.forLanguageTags(languageTag).get(0) ?: return context
        val overrideConfig = Configuration(context.resources.configuration).apply {
            setLocale(locale)
        }
        return context.createConfigurationContext(overrideConfig)
    }

    fun showDoseReminderNotification(
        bundle: MedicationReminderBundle,
        canSnooze: Boolean = true,
        hideMedicationDetails: Boolean = false,
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
            buildReminderNotificationContentIntent(bundle, notificationTag),
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
                NotificationCompat.BigTextStyle().bigText(
                    if (hideMedicationDetails) {
                        bundle.items.joinToString(separator = " · ") { it.groupName }
                    } else {
                        buildExpandedNotificationBody(context, bundle.items)
                    }
                )
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

    fun showStockOutToast(medicine: Medicine) {
        diagnosticsLogger.info(TAG, "reminder_notification_stock_out_toast medicine=${medicine.uuid}")
        showToast(
            context.getString(
                R.string.stock_toast_out_single,
                medicineDisplayName(medicine, context),
            )
        )
    }

    fun showStockOutCountToast(count: Int) {
        diagnosticsLogger.info(TAG, "reminder_notification_stock_out_count_toast count=$count")
        showToast(
            context.resources.getQuantityString(
                R.plurals.stock_toast_out_multiple,
                count,
                count,
            )
        )
    }

    fun showStockImminentToast(medicine: Medicine) {
        diagnosticsLogger.info(TAG, "reminder_notification_stock_imminent_toast medicine=${medicine.uuid}")
        showToast(
            context.getString(
                R.string.stock_toast_imminent_single,
                medicineDisplayName(medicine, context),
            )
        )
    }

    fun showStockImminentCountToast(count: Int) {
        diagnosticsLogger.info(TAG, "reminder_notification_stock_imminent_count_toast count=$count")
        showToast(
            context.resources.getQuantityString(
                R.plurals.stock_toast_imminent_multiple,
                count,
                count,
            )
        )
    }

    fun showStockUserLowToast(medicine: Medicine) {
        diagnosticsLogger.info(TAG, "reminder_notification_stock_user_low_toast medicine=${medicine.uuid}")
        showToast(
            context.getString(
                R.string.stock_toast_user_low_single,
                medicineDisplayName(medicine, context),
            )
        )
    }

    fun showStockUserLowCountToast(count: Int) {
        diagnosticsLogger.info(TAG, "reminder_notification_stock_user_low_count_toast count=$count")
        showToast(
            context.resources.getQuantityString(
                R.plurals.stock_toast_user_low_multiple,
                count,
                count,
            )
        )
    }

    fun showStockManyAttentionToast(count: Int) {
        diagnosticsLogger.info(TAG, "reminder_notification_stock_many_attention_toast count=$count")
        showToast(
            context.resources.getQuantityString(
                R.plurals.stock_toast_many_attention,
                count,
                count,
            )
        )
    }

    fun showDoseReminderNothingToAddToast() {
        diagnosticsLogger.info(TAG, "reminder_notification_nothing_to_add_toast")
        showToast(context.getString(R.string.reminder_notification_nothing_to_add))
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
            // Log-now uses targets to restrict writes to medications shown in
            // the notification body. Snooze / remind-later operate at slot
            // level, so targets would just be dead weight.
            if (action == ACTION_MEDICATION_REMINDER_LOG_NOW) {
                putStringArrayListExtra(
                    EXTRA_REMINDER_LOG_TARGETS,
                    ArrayList(bundle.logTargets.map(MedicationReminderLogTarget::toStorageValue)),
                )
            }
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
            is ReminderNotificationBody.TwoGroups -> context.getString(
                R.string.reminder_notification_two_groups,
                firstGroupName,
                secondGroupName,
            )
            is ReminderNotificationBody.MoreGroups -> context.resources.getQuantityString(
                R.plurals.reminder_notification_more_groups,
                additionalGroupCount,
                firstGroupName,
                secondGroupName,
                additionalGroupCount,
            )
        }
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildReminderNotificationContentIntent(
        bundle: MedicationReminderBundle,
        notificationTag: String,
    ): Intent {
        return Intent(context, MainActivity::class.java).apply {
            data = reminderNotificationContentData(notificationTag)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            val targets = reminderNotificationScheduledHighlightTargetStorageValues(bundle)
            if (targets.isNotEmpty()) {
                putExtra(EXTRA_HIGHLIGHT_KIND, HIGHLIGHT_KIND_SCHEDULED)
                putStringArrayListExtra(
                    EXTRA_HIGHLIGHT_SCHEDULED_TARGETS,
                    ArrayList(targets),
                )
            }
        }
    }
}

private fun buildExpandedNotificationBody(
    context: Context,
    items: List<MedicationReminderBundleItem>,
): String {
    val expanded = buildExpandedDetailLines(items) { groupName, medication ->
        medicationDetailLine(context, groupName, medication)
    }
    return buildString {
        append(expanded.visibleLines.joinToString(separator = "\n"))
        if (expanded.hiddenCount > 0) {
            if (expanded.visibleLines.isNotEmpty()) append('\n')
            append(
                context.resources.getQuantityString(
                    R.plurals.reminder_notification_more_medications,
                    expanded.hiddenCount,
                    expanded.hiddenCount,
                )
            )
        }
    }
}

internal fun reminderNotificationScheduledHighlightTargetStorageValues(
    bundle: MedicationReminderBundle,
): List<String> =
    bundle.items
        .map { item ->
            ScheduledDoseRowHighlightTarget(
                groupUuid = item.slot.groupUuid,
                scheduleTimeUuid = item.slot.scheduleTimeUuid,
                scheduledAt = item.slot.scheduledAt,
            )
        }
        .distinct()
        .map { target -> target.toStorageValue() }

private fun reminderNotificationContentData(notificationTag: String): Uri {
    return "$REMINDER_NOTIFICATION_CONTENT_URI_PREFIX/$notificationTag".toUri()
}

private fun reminderNotificationActionData(
    action: String,
    bundle: MedicationReminderBundle,
): Uri {
    val actionUuid = java.util.UUID.nameUUIDFromBytes(
        "${action}:${bundle.notificationTag}".toByteArray(java.nio.charset.StandardCharsets.UTF_8)
    )
    return "$REMINDER_NOTIFICATION_ACTION_URI_PREFIX/$action/$actionUuid".toUri()
}

private const val DOSE_REMINDER_NOTIFICATION_ID = 0
private const val REMINDER_NOTIFICATION_REQUEST_CODE = 0
private const val REMINDER_NOTIFICATION_CONTENT_URI_PREFIX =
    "hrttracker://medication-reminder-notification"
private const val REMINDER_NOTIFICATION_ACTION_URI_PREFIX =
    "hrttracker://medication-reminder-action"
const val REMINDER_CHANNEL_ID = "dose_reminders"
private const val TAG = "ReminderNotificationManager"
