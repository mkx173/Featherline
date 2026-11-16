package com.mkx.hrttracker.reminder

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
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
import com.mkx.hrttracker.ui.alarm.DoseAlarmActivity
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import com.mkx.hrttracker.util.appLanguageLocale
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.localizedShortTimeFormatter
import com.mkx.hrttracker.util.medicineDisplayName
import com.mkx.hrttracker.util.uses24HourTimeFormat
import com.mkx.hrttracker.util.withAppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
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
     * The channel for persistent reminders posted with the picked [soundUri].
     * A channel's sound can't change once created, so each sound gets its own
     * channel, keyed by the sound, and those for earlier picks are removed.
     */
    private fun ensureCustomSoundChannel(soundUri: Uri): String {
        val channelId = customSoundChannelId(soundUri.toString())
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        deleteCustomSoundChannelsExcept(channelId)
        val localizedContext = context.withAppLanguage()
        val channel = NotificationChannel(
            channelId,
            localizedContext.getString(R.string.settings_persistent_reminders),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = localizedContext.getString(R.string.persistent_reminders_channel_description)
            setSound(
                soundUri,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
        }
        // Creating an existing channel again only updates its name and
        // description, which keeps them in the current app language.
        notificationManager.createNotificationChannel(channel)
        return channelId
    }

    /**
     * Removes the persistent-reminder channels of sounds no longer picked;
     * [keepSoundUri] null removes them all, as when the default is picked again.
     */
    fun deleteUnusedCustomSoundChannels(keepSoundUri: String?) {
        runCatching { deleteCustomSoundChannelsExcept(keepSoundUri?.let(::customSoundChannelId)) }
    }

    private fun deleteCustomSoundChannelsExcept(keepChannelId: String?) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notificationChannels
            .filter { channel ->
                channel.id.startsWith(CUSTOM_SOUND_CHANNEL_PREFIX) && channel.id != keepChannelId
            }
            .forEach { channel -> notificationManager.deleteNotificationChannel(channel.id) }
    }

    private fun localizedContextFor(languageTag: String): Context {
        val locale = LocaleListCompat.forLanguageTags(languageTag).get(0) ?: return context
        val overrideConfig = Configuration(context.resources.configuration).apply {
            setLocale(locale)
        }
        return context.createConfigurationContext(overrideConfig)
    }

    /**
     * Posts (or replaces) the dose reminder for [bundle].
     *
     * [alarm] non-null asks for the full-screen alarm: the notification carries a
     * full-screen intent to [DoseAlarmActivity], which the system launches over
     * the lock screen when the device is locked or its screen is off, and shows
     * as a heads-up otherwise. [canStopRepeats] adds a "Stop reminding" action,
     * offered while the reminder repeats until logged. [sound] non-null posts
     * on the persistent-reminder channel that plays that sound.
     */
    fun showDoseReminderNotification(
        bundle: MedicationReminderBundle,
        canSnooze: Boolean = true,
        hideMedicationDetails: Boolean = false,
        silent: Boolean = false,
        alarm: DoseReminderAlarm? = null,
        canStopRepeats: Boolean = false,
        sound: String? = null,
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

        // Resolve every user-facing string and number through a context pinned to
        // the chosen app language. The injected ApplicationContext stays on the
        // system locale below API 33 (see Localization.withAppLanguage), so without
        // this the title, body, and dose details would ignore the user's language.
        val localizedContext = context.withAppLanguage()

        val notificationTag = bundle.notificationTag
        val contentIntent = PendingIntent.getActivity(
            context,
            REMINDER_NOTIFICATION_REQUEST_CODE,
            buildReminderNotificationContentIntent(bundle, notificationTag),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val isMerged = bundle.items.size > 1
        val fullScreen = alarm != null && canUseFullScreenIntent()
        // The alarm screen rings its own tone when it opens; let the
        // notification stay quiet then rather than play over it. It only opens
        // on a locked or dark screen, so the heads-up case keeps its sound.
        val quietForAlarmScreen = fullScreen && alarm?.playsSound == true && willShowFullScreen()
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

        val channelId = sound
            ?.let { uri -> runCatching { ensureCustomSoundChannel(uri.toUri()) }.getOrNull() }
            ?: REMINDER_CHANNEL_ID
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentTitle(title)
            .setContentText(body)
            // Two reminders for one group, say a snoozed dose and a later one,
            // would otherwise read the same: name the dose each one is for.
            .setSubText(
                localizedContext.getString(
                    R.string.reminder_notification_scheduled_at,
                    dateLabelFormatter(appLanguageLocale(), LocalDate.now())(bundle.scheduledAt.toLocalDate()),
                    bundle.scheduledAt.format(
                        localizedShortTimeFormatter(appLanguageLocale(), context.uses24HourTimeFormat())
                    ),
                )
            )
            // The header would show the post time next to the subtext's dose time.
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            // A trimmed repost has a new tag, so setOnlyAlertOnce can't stop the sound.
            .setSilent(silent || quietForAlarmScreen)
            .apply {
                if (fullScreen) {
                    setCategory(NotificationCompat.CATEGORY_ALARM)
                    setFullScreenIntent(
                        PendingIntent.getActivity(
                            context,
                            REMINDER_NOTIFICATION_REQUEST_CODE,
                            DoseAlarmActivity.intent(
                                context = context,
                                bundle = bundle,
                                notificationTag = notificationTag,
                                canSnooze = canSnooze,
                            ),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ),
                        true,
                    )
                } else {
                    setCategory(NotificationCompat.CATEGORY_REMINDER)
                }
            }
            // Read back by activeDoseReminders to tell which doses this one is
            // for, and whether what it shows has changed since.
            .addExtras(
                Bundle().apply {
                    putStringArrayList(
                        EXTRA_REMINDER_SLOTS,
                        ArrayList(bundle.slots.map(MedicationReminderSlot::toStorageValue)),
                    )
                    putString(EXTRA_REMINDER_CONTENT_HASH, bundle.contentHash)
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
                if (canStopRepeats) {
                    addAction(
                        R.drawable.ic_notifications_off,
                        localizedContext.getString(R.string.reminder_notification_action_stop),
                        buildReminderActionPendingIntent(
                            action = ACTION_MEDICATION_REMINDER_DISMISS,
                            bundle = bundle,
                            notificationTag = notificationTag,
                        )
                    )
                }
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
                        "items=${bundle.items.size} canSnooze=$canSnooze isMerged=$isMerged silent=$silent " +
                        "fullScreen=$fullScreen quietForAlarmScreen=$quietForAlarmScreen " +
                        "canStopRepeats=$canStopRepeats channel=$channelId"
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

    fun showDoseReminderRepeatsStoppedToast() {
        diagnosticsLogger.info(TAG, "reminder_notification_repeats_stopped_toast")
        showToast { localized -> localized.getString(R.string.reminder_notification_repeats_stopped) }
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

    /**
     * Whether the system may launch a full-screen intent for this app. From
     * Android 14 the user can withhold it (Settings > Apps > Special app access >
     * Full screen notifications); a notification asking for one then shows as
     * a plain heads-up.
     */
    fun canUseFullScreenIntent(): Boolean = isFullScreenIntentAllowed(context)

    /** Whether a full-screen intent posted now would open its activity rather than a heads-up. */
    private fun willShowFullScreen(): Boolean {
        return try {
            val powerManager = context.getSystemService(PowerManager::class.java)
            val keyguardManager = context.getSystemService(KeyguardManager::class.java)
            !powerManager.isInteractive || keyguardManager.isKeyguardLocked
        } catch (_: Throwable) {
            false
        }
    }

    private fun buildReminderActionPendingIntent(
        action: String,
        bundle: MedicationReminderBundle,
        notificationTag: String,
    ): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            REMINDER_NOTIFICATION_REQUEST_CODE,
            buildReminderActionIntent(context, action, bundle, notificationTag),
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
        // Resolve the toast string through a context pinned to the chosen app language.
        // The injected ApplicationContext stays on the system locale below API 33 (see
        // Localization.withAppLanguage), so resolving off it would show toasts in the
        // system language even when the user picked a different app language.
        val localizedContext = context.withAppLanguage()
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

/**
 * Asks [ReminderNotificationManager.showDoseReminderNotification] for the
 * full-screen alarm. [playsSound]: the alarm screen rings its own tone.
 */
data class DoseReminderAlarm(val playsSound: Boolean)

/**
 * The broadcast a reminder action sends to [MedicationReminderActionReceiver],
 * shared by the notification's buttons and the full-screen alarm's, so both
 * run the same handler path.
 */
internal fun buildReminderActionIntent(
    context: Context,
    action: String,
    bundle: MedicationReminderBundle,
    notificationTag: String,
): Intent {
    return Intent(context, MedicationReminderActionReceiver::class.java).apply {
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
}

fun isFullScreenIntentAllowed(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        return true
    }
    return try {
        context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    } catch (_: Throwable) {
        // As canPostNotifications: no notification service in preview/test.
        false
    }
}

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
private const val CUSTOM_SOUND_CHANNEL_PREFIX = "persistent_reminders_sound_"

private fun customSoundChannelId(soundUri: String): String =
    CUSTOM_SOUND_CHANNEL_PREFIX +
            java.util.UUID.nameUUIDFromBytes(soundUri.toByteArray(java.nio.charset.StandardCharsets.UTF_8))

private const val EXTRA_REMINDER_CONTENT_HASH = "reminderContentHash"
private const val TAG = "ReminderNotificationManager"
