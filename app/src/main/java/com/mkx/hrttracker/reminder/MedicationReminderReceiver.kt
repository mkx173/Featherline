package com.mkx.hrttracker.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mkx.hrttracker.data.repository.E2EstimateRepository
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@AndroidEntryPoint
class MedicationReminderReceiver : BroadcastReceiver() {
    @Inject
    @AppScope
    lateinit var appScope: CoroutineScope

    @Inject
    lateinit var medicationGroupRepository: MedicationGroupRepository

    @Inject
    lateinit var medicationLogRepository: MedicationLogRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var medicationReminderScheduler: MedicationReminderScheduler

    @Inject
    lateinit var reminderNotificationManager: ReminderNotificationManager

    @Inject
    lateinit var diagnosticsLogger: AppDiagnosticsLogger

    @Inject
    lateinit var e2EstimateRepository: E2EstimateRepository

    override fun onReceive(context: Context, intent: Intent) {
        val deadline = TimeSource.Monotonic.markNow() + HANDLE_BUDGET_MS.milliseconds
        val pendingResult = goAsync()
        val groupUuid = intent.getStringExtra(EXTRA_GROUP_UUID)
            ?.let { value -> runCatching { UUID.fromString(value) }.getOrNull() }
            ?: return pendingResult.finish().also {
                diagnosticsLogger.info(
                    TAG,
                    "reminder_receiver_ignored reason=missing_or_invalid_group_uuid action=${intent.action}"
                )
            }
        // Null from a v1.4.2 alarm, which only a v1.4.2 key, with no time, takes.
        val alertAt = intent.takeIf { it.hasExtra(EXTRA_ALERT_AT) }
            ?.let { Instant.ofEpochMilli(it.getLongExtra(EXTRA_ALERT_AT, 0L)) }

        diagnosticsLogger.info(
            TAG,
            "reminder_receiver_received groupUuid=$groupUuid alertAt=$alertAt action=${intent.action}"
        )
        handleDoseReminder(
            groupUuid = groupUuid,
            alertAt = alertAt,
            legacyScheduledAt = intent.getStringExtra(EXTRA_SCHEDULED_AT)
                ?.let { value -> runCatching { LocalDateTime.parse(value) }.getOrNull() },
            deadline = deadline,
            appScope = appScope,
            settingsRepository = settingsRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            reminderNotificationManager = reminderNotificationManager,
            e2EstimateRepository = e2EstimateRepository,
            diagnosticsLogger = diagnosticsLogger,
            pendingResult = pendingResult,
        )
    }
}

/**
 * Posts the plan's reminder for its latest alert from [alertAt] up to the clock, read once it
 * holds the lock, then arms its next alert, and finishes the broadcast once it is done. Only the
 * plan's own doses: plans that share an alert time each post their own, and only the first to
 * arrive sounds.
 */
internal fun handleDoseReminder(
    groupUuid: UUID,
    /** The alert the alarm was armed for; null from a v1.4.2 alarm. */
    alertAt: Instant?,
    /** The dose time a v1.4.2 alarm carried. */
    legacyScheduledAt: LocalDateTime? = null,
    /** Read under the lock, not at receipt: a delivery can wait there past the plan's next alert. */
    clock: () -> Instant = Instant::now,
    /** The end of the broadcast's budget, counted from its receipt. */
    deadline: TimeMark,
    appScope: CoroutineScope,
    settingsRepository: SettingsRepository,
    medicationGroupRepository: MedicationGroupRepository,
    medicationLogRepository: MedicationLogRepository,
    medicationReminderScheduler: MedicationReminderScheduler,
    reminderNotificationManager: ReminderNotificationManager,
    e2EstimateRepository: E2EstimateRepository,
    diagnosticsLogger: AppDiagnosticsLogger,
    pendingResult: BroadcastReceiver.PendingResult,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    appScope.launch {
        runCatching {
            if (!settingsRepository.getCurrentSettings().remindersEnabled) {
                diagnosticsLogger.info(
                    TAG,
                    "reminder_receiver_master_disabled groupUuid=$groupUuid alertAt=$alertAt"
                )
                medicationReminderScheduler.cancelReminder(groupUuid)
                return@runCatching
            }

            // One hold from the check to the reschedule: a pass in between would arm this
            // alert again, and that alarm would pass the check too.
            withReminderScheduleLock {
                // The clock and the repeats as of the hold: a delivery that waited for the lock
                // past the plan's next alert rings that one once, and a repeat received before
                // repeats were turned off rings nothing.
                val now = clock()
                val repeats = settingsRepository.getCurrentSettings().reminderRepeats
                if (!medicationReminderScheduler.awaitsReminderDelivery(groupUuid, alertAt)) {
                    diagnosticsLogger.info(
                        TAG,
                        "reminder_receiver_notification_skipped reason=delivered groupUuid=$groupUuid " +
                                "alertAt=$alertAt"
                    )
                } else {
                    // Compared as instants: an alert's own, never its wall time read back.
                    val since = alertAt
                        ?: legacyScheduledAt?.atZone(zone)?.toInstant()
                        ?: now
                    val group = medicationGroupRepository.getGroup(groupUuid)
                    val entries = medicationLogRepository.getScheduledGroupEntriesSince(
                        reminderDosesFrom(minOf(since, now), repeats, zone)
                    )
                    val alert = group?.latestDueReminderAlert(
                        since = since,
                        now = now,
                        entries = entries,
                        repeats = repeats,
                        skippedThrough = medicationReminderScheduler.skippedThrough(groupUuid),
                        zone = zone,
                    )
                    val bundle = if (group != null && alert != null) {
                        buildMedicationReminderBundle(scheduledAt = alert.doseAt, group = group, entries = entries)
                    } else {
                        null
                    }
                    // Read again last before the post: Settings may have turned reminders off
                    // since the read above, and its own pass runs only after this hold.
                    val current = settingsRepository.getCurrentSettings()
                    if (bundle != null && !current.remindersEnabled) {
                        diagnosticsLogger.info(
                            TAG,
                            "reminder_receiver_notification_skipped reason=master_disabled groupUuid=$groupUuid"
                        )
                    } else if (alert != null && bundle != null) {
                        val sounds = medicationReminderScheduler.claimAlertSound(alert.alertAt)
                        reminderNotificationManager.showDoseReminderNotification(
                            bundle,
                            hideMedicationDetails = current.hideMedicationDetails,
                            silent = !sounds,
                        )
                        diagnosticsLogger.info(
                            TAG,
                            "reminder_receiver_notification_shown groupUuid=$groupUuid " +
                                    "doseAt=${alert.doseAt} alertAt=${alert.alertAt} sounds=$sounds " +
                                    "entries=${entries.size} items=${bundle.items.size}"
                        )
                    } else {
                        diagnosticsLogger.info(
                            TAG,
                            "reminder_receiver_notification_skipped reason=no_bundle groupUuid=$groupUuid " +
                                    "since=$since entries=${entries.size}"
                        )
                    }
                }
                medicationReminderScheduler.markReminderDelivered(groupUuid, alertAt)

                // The next alert strictly after now: the one rung is at or before it.
                try {
                    medicationReminderScheduler.rescheduleGroup(
                        groupUuid = groupUuid,
                        after = now,
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    diagnosticsLogger.warning(
                        TAG,
                        "reminder_receiver_reschedule_failed groupUuid=$groupUuid after=$now",
                        error,
                    )
                }
            }
            // The alert is no longer planned, so Home's estimate moved: store
            // the new one before the user opens the app, within what is left
            // of the budget.
            withTimeoutOrNull(-deadline.elapsedNow()) { e2EstimateRepository.warmHome() }
        }.onFailure { throwable ->
            diagnosticsLogger.warning(
                TAG,
                "reminder_receiver_failed groupUuid=$groupUuid alertAt=$alertAt",
                throwable,
            )
        }.also {
            pendingResult.finish()
        }
    }
}

private const val TAG = "MedicationReminderReceiver"

// Inside the ~10s background-broadcast goAsync allowance, as in
// WidgetDateReceiver.
private const val HANDLE_BUDGET_MS = 8_000L
