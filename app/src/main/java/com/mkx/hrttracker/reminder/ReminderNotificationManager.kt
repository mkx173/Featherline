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
import android.os.Bundle
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
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import com.mkx.hrttracker.util.currentAppLocale
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.localizedShortTimeFormatter
import com.mkx.hrttracker.util.medicineDisplayName
import com.mkx.hrttracker.util.uses24HourTimeFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    /**
     * Creates (or updates the mutable name/description fields of) the
     * dose-reminder channel, in the app language.
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
        val resolvedContext = languageTag?.let(::localizedContextFor) ?: localizedContext()
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            resolvedContext.getString(R.string.reminder_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description =
                resolvedContext.getString(R.string.reminder_notification_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
        diagnosticsLogger.info(
            TAG,
            "reminder_notification_channel_created channelId=$REMINDER_CHANNEL_ID " +
                    "languageTag=${languageTag ?: "default"}"
        )
    }

    /**
     * [context] in the app language. Its own Resources stay on the system locale below API 33,
     * and a process an alarm woke has no activity for AppCompat to know the app language from
     * ([SettingsRepository.appLanguageTag]).
     */
    private fun localizedContext(): Context =
        settingsRepository.appLanguageTag()?.let(::localizedContextFor) ?: context

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
        silent: Boolean = false,
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

        // Every user-facing string, number and date in the app language.
        val localizedContext = localizedContext()
        val locale = localizedContext.currentAppLocale()

        val notificationTag = bundle.notificationTag
        val contentIntent = PendingIntent.getActivity(
            context,
            REMINDER_NOTIFICATION_REQUEST_CODE,
            buildReminderNotificationContentIntent(bundle, notificationTag),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val isMerged = bundle.items.size > 1
        val notificationText = buildReminderNotificationText(bundle)
        val title = localizedContext.getString(notificationText.titleRes)
        val body = notificationText.body.resolve(localizedContext)
        val logActionTitle = localizedContext.getString(
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
            // Two reminders for one group, say a snoozed dose and a later one,
            // would otherwise read the same: name the dose each one is for.
            .setSubText(
                localizedContext.getString(
                    R.string.reminder_notification_scheduled_at,
                    dateLabelFormatter(locale, LocalDate.now())(bundle.scheduledAt.toLocalDate()),
                    bundle.scheduledAt.format(
                        localizedShortTimeFormatter(locale, context.uses24HourTimeFormat())
                    ),
                )
            )
            // The header would show the post time next to the subtext's dose time.
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            // A trimmed repost has a new tag, so setOnlyAlertOnce can't stop the sound.
            .setSilent(silent)
            // Read back by activeDoseReminders to tell which doses this one is
            // for, and whether what it shows has changed since.
            .addExtras(
                Bundle().apply {
                    putStringArrayList(
                        EXTRA_REMINDER_SLOTS,
                        ArrayList(bundle.slots.map(MedicationReminderSlot::toStorageValue)),
                    )
                    putString(EXTRA_REMINDER_CONTENT_HASH, bundle.postedContentHash(hideMedicationDetails))
                }
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    if (hideMedicationDetails) {
                        bundle.items.joinToString(separator = " · ") { it.groupName }
                    } else {
                        buildExpandedNotificationBody(localizedContext, bundle.items)
                    }
                )
            )
            .apply {
                if (canSnooze) {
                    addAction(
                        R.drawable.ic_snooze,
                        localizedContext.getString(R.string.reminder_notification_action_remind_later),
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
                        "items=${bundle.items.size} canSnooze=$canSnooze isMerged=$isMerged silent=$silent"
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
        showToast { localized ->
            localized.resources.getQuantityString(
                R.plurals.reminder_notification_entries_added,
                entryCount,
                entryCount,
            )
        }
    }

    fun showStockOutToast(medicine: Medicine) {
        diagnosticsLogger.info(
            TAG,
            "reminder_notification_stock_out_toast medicine=${medicine.uuid}"
        )
        showToast { localized ->
            localized.getString(
                R.string.stock_toast_out_single,
                medicineDisplayName(medicine, localized),
            )
        }
    }

    fun showStockOutCountToast(count: Int) {
        diagnosticsLogger.info(TAG, "reminder_notification_stock_out_count_toast count=$count")
        showToast { localized ->
            localized.resources.getQuantityString(
                R.plurals.stock_toast_out_multiple,
                count,
                count,
            )
        }
    }

    fun showStockImminentToast(medicine: Medicine) {
        diagnosticsLogger.info(
            TAG,
            "reminder_notification_stock_imminent_toast medicine=${medicine.uuid}"
        )
        showToast { localized ->
            localized.getString(
                R.string.stock_toast_imminent_single,
                medicineDisplayName(medicine, localized),
            )
        }
    }

    fun showStockImminentCountToast(count: Int) {
        diagnosticsLogger.info(TAG, "reminder_notification_stock_imminent_count_toast count=$count")
        showToast { localized ->
            localized.resources.getQuantityString(
                R.plurals.stock_toast_imminent_multiple,
                count,
                count,
            )
        }
    }

    fun showStockUserLowToast(medicine: Medicine) {
        diagnosticsLogger.info(
            TAG,
            "reminder_notification_stock_user_low_toast medicine=${medicine.uuid}"
        )
        showToast { localized ->
            localized.getString(
                R.string.stock_toast_user_low_single,
                medicineDisplayName(medicine, localized),
            )
        }
    }

    fun showStockUserLowCountToast(count: Int) {
        diagnosticsLogger.info(TAG, "reminder_notification_stock_user_low_count_toast count=$count")
        showToast { localized ->
            localized.resources.getQuantityString(
                R.plurals.stock_toast_user_low_multiple,
                count,
                count,
            )
        }
    }

    fun showStockManyAttentionToast(count: Int) {
        diagnosticsLogger.info(TAG, "reminder_notification_stock_many_attention_toast count=$count")
        showToast { localized ->
            localized.resources.getQuantityString(
                R.plurals.stock_toast_many_attention,
                count,
                count,
            )
        }
    }

    fun showDoseReminderNothingToAddToast() {
        diagnosticsLogger.info(TAG, "reminder_notification_nothing_to_add_toast")
        showToast { localized -> localized.getString(R.string.reminder_notification_nothing_to_add) }
    }

    fun showDoseReminderNoLongerScheduledToast() {
        diagnosticsLogger.info(TAG, "reminder_notification_no_longer_scheduled_toast")
        showToast { localized -> localized.getString(R.string.reminder_notification_no_longer_scheduled) }
    }

    fun showQuickLogFailedToast() {
        diagnosticsLogger.info(TAG, "reminder_notification_quick_log_failed_toast")
        showToast { localized -> localized.getString(R.string.widget_quick_log_failed) }
    }

    fun showDoseReminderSnoozedToast(snoozeMinutes: Long) {
        diagnosticsLogger.info(TAG, "reminder_notification_snoozed_toast minutes=$snoozeMinutes")
        showToast { localized ->
            localized.getString(
                R.string.reminder_notification_snoozed,
                snoozeMinutes,
            )
        }
    }

    fun cancelDoseReminderNotification(notificationTag: String) {
        NotificationManagerCompat.from(context).cancel(
            notificationTag,
            DOSE_REMINDER_NOTIFICATION_ID,
        )
        diagnosticsLogger.info(TAG, "reminder_notification_cancelled tag=$notificationTag")
    }

    /** Cancels every notification this app has on screen, once reminders are off. */
    fun cancelAllDoseReminderNotifications() {
        NotificationManagerCompat.from(context).cancelAll()
        diagnosticsLogger.info(TAG, "reminder_notifications_cancelled_all")
    }

    /**
     * The dose reminders still on screen, by tag. One posted before its doses went
     * into its extras is left out.
     */
    fun activeDoseReminders(): Map<String, ShownDoseReminder> {
        val active = try {
            context.getSystemService(NotificationManager::class.java).activeNotifications
        } catch (_: Throwable) {
            // As in canPostNotifications: no notification service in some environments.
            return emptyMap()
        }
        return active
            .filter { notification ->
                notification.id == DOSE_REMINDER_NOTIFICATION_ID &&
                        notification.tag?.startsWith(DOSE_REMINDER_TAG_PREFIX) == true
            }
            .mapNotNull { notification ->
                val extras = notification.notification.extras
                val slots = extras.getStringArrayList(EXTRA_REMINDER_SLOTS)
                    ?.mapNotNull(::medicationReminderSlotFromStorageValue)
                    ?.takeIf(List<MedicationReminderSlot>::isNotEmpty)
                    ?: return@mapNotNull null
                notification.tag to ShownDoseReminder(
                    slots = slots,
                    contentHash = extras.getString(EXTRA_REMINDER_CONTENT_HASH),
                )
            }
            .toMap()
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

    private fun ReminderNotificationBody.resolve(context: Context): String {
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

    private fun showToast(message: (Context) -> String) {
        val localizedContext = localizedContext()
        val text = message(localizedContext)
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(localizedContext, text, Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildReminderNotificationContentIntent(
        bundle: MedicationReminderBundle,
        notificationTag: String,
    ): Intent {
        return Intent(context, MainActivity::class.java).apply {
            data = reminderNotificationContentData(notificationTag)
            // Reuse the running MainActivity (onNewIntent) like the widget rows, so the Home
            // deep link can ask an editor with unsaved changes before leaving it; CLEAR_TASK
            // would destroy that editor first.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
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
private const val EXTRA_REMINDER_CONTENT_HASH = "reminderContentHash"
private const val TAG = "ReminderNotificationManager"
