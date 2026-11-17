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
import android.os.SystemClock
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
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

    /**
     * Posts [bundle], one plan's doses at one time, under its tag. [silent] posts it with no
     * sound, vibration or heads-up: a repost, or a plan that shares its alert time with one that
     * sounded already.
     */
    fun showDoseReminderNotification(
        bundle: MedicationReminderBundle,
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
        val title = localizedContext.getString(R.string.reminder_notification_single_title)
        val body = bundle.items.first().groupName

        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentTitle(title)
            .setContentText(body)
            // Two reminders for one plan, say a missed dose and a later one,
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
            // A swipe or Clear all: the alerts go on, and a toast names the next one.
            .setDeleteIntent(
                buildReminderActionPendingIntent(
                    action = ACTION_MEDICATION_REMINDER_DISMISSED,
                    bundle = bundle,
                    notificationTag = notificationTag,
                )
            )
            // No setAutoCancel: a tap on the body opens the app (on the lock screen, after an
            // unlock) and leaves the reminder until the dose is logged or skipped, or it is swiped.
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // No setOnlyAlertOnce: a repeat posts under the same tag, and must sound.
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
            .addAction(
                R.drawable.ic_notifications_off,
                localizedContext.getString(R.string.reminder_notification_action_skip),
                buildReminderActionPendingIntent(
                    action = ACTION_MEDICATION_REMINDER_SKIP,
                    bundle = bundle,
                    notificationTag = notificationTag,
                )
            )
            .addAction(
                R.drawable.ic_edit_square,
                localizedContext.getString(R.string.reminder_notification_action_log_now),
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
                        "items=${bundle.items.size} silent=$silent"
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

    fun showDoseReminderSkippedToast() {
        diagnosticsLogger.info(TAG, "reminder_notification_skipped_toast")
        showToast { localized -> localized.getString(R.string.reminder_notification_skipped) }
    }

    /** The clock the next-reminder toast's throttle reads; a test sets its own. */
    internal var elapsedRealtime: () -> Long = SystemClock::elapsedRealtime

    // In memory beside the toasts: a process death resets them, which at worst shows one more
    // toast, or none for a window still open.
    private var lastNextReminderToastAt: Long? = null
    private var pendingNextAlertAt: Instant? = null

    /**
     * After a swipe: names the plan's next alert at [alertAt]. Clear all sends one dismissal per
     * reminder, each its own broadcast, and the receiver takes them one at a time: the first opens
     * a window of [NEXT_REMINDER_TOAST_WINDOW_MS], the others join it, and its end shows one toast
     * for the earliest alert among them. Throttled as the Settings version copy is: a swipe
     * within 2 s of the last window's opening shows none.
     */
    fun showNextReminderToast(alertAt: Instant) {
        val shownAt = elapsedRealtime()
        synchronized(this) {
            pendingNextAlertAt?.let { pending ->
                pendingNextAlertAt = minOf(pending, alertAt)
                diagnosticsLogger.info(TAG, "reminder_notification_next_toast_joined alertAt=$alertAt")
                return
            }
            val last = lastNextReminderToastAt
            if (last != null && shownAt - last < NEXT_REMINDER_TOAST_THROTTLE_MS) {
                diagnosticsLogger.info(TAG, "reminder_notification_next_toast_throttled")
                return
            }
            lastNextReminderToastAt = shownAt
            pendingNextAlertAt = alertAt
        }
        // ponytail: a dismissal handled after the window closes (a slow cold start) is throttled,
        // so its alert is left out even if earlier; a longer window would delay a single swipe's toast.
        Handler(Looper.getMainLooper()).postDelayed({
            val earliest = synchronized(this) {
                pendingNextAlertAt.also { pendingNextAlertAt = null }
            } ?: return@postDelayed
            diagnosticsLogger.info(TAG, "reminder_notification_next_toast alertAt=$earliest")
            showToast { localized ->
                nextReminderToastText(
                    context = localized,
                    alertAt = LocalDateTime.ofInstant(earliest, ZoneId.systemDefault()),
                    today = LocalDate.now(),
                    is24Hour = context.uses24HourTimeFormat(),
                )
            }
        }, NEXT_REMINDER_TOAST_WINDOW_MS)
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
            // the notification body. Skip and a swipe operate at slot level,
            // so targets would just be dead weight.
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

/**
 * "Next reminder: 08:15" today, else "Next reminder: Nov 14 · 08:15", with the year when it is
 * another year's, in [context]'s language, with the time in the device's 12/24-hour setting.
 */
internal fun nextReminderToastText(
    context: Context,
    alertAt: LocalDateTime,
    today: LocalDate,
    is24Hour: Boolean,
): String {
    val locale = context.currentAppLocale()
    val time = alertAt.format(localizedShortTimeFormatter(locale, is24Hour))
    val date = alertAt.toLocalDate()
    return context.getString(
        R.string.reminder_notification_next,
        if (date == today) time else "${dateLabelFormatter(locale, today)(date)} · $time",
    )
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
// As VERSION_COPY_THROTTLE_MS in SettingsScreen.
internal const val NEXT_REMINDER_TOAST_THROTTLE_MS = 2_000L
// Long enough for Clear all's other dismissals to be handled, short enough to read as a response.
internal const val NEXT_REMINDER_TOAST_WINDOW_MS = 500L
private const val TAG = "ReminderNotificationManager"
