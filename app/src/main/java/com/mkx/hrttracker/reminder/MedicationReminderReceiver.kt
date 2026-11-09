package com.mkx.hrttracker.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mkx.hrttracker.data.repository.E2EstimateRepository
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.previousScheduledForBefore
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
        val scheduledAt = intent.getStringExtra(EXTRA_SCHEDULED_AT)
            ?.let(LocalDateTime::parse)
            ?: LocalDateTime.now()

        diagnosticsLogger.info(
            TAG,
            "reminder_receiver_received groupUuid=$groupUuid scheduledAt=$scheduledAt action=${intent.action}"
        )
        handleDoseReminder(
            groupUuid = groupUuid,
            scheduledAt = scheduledAt,
            now = receiptTime(Instant.now()),
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
 * Posts the dose reminder for [scheduledAt], or for the group's latest due
 * time when that is later, and finishes the broadcast once it is done.
 */
internal fun handleDoseReminder(
    groupUuid: UUID,
    scheduledAt: LocalDateTime,
    /** When the broadcast was received, as far as it has passed ([passedAsOf]). */
    now: LocalDateTime,
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
) {
    appScope.launch {
        runCatching {
            val settings = settingsRepository.getCurrentSettings()
            if (!settings.remindersEnabled) {
                diagnosticsLogger.info(
                    TAG,
                    "reminder_receiver_master_disabled groupUuid=$groupUuid scheduledAt=$scheduledAt"
                )
                medicationReminderScheduler.cancelReminder(groupUuid)
                return@runCatching
            }

            // One hold from the check to the reschedule: a pass in between would arm this
            // dose again, and that alarm would pass the check too.
            withReminderScheduleLock {
                val groups = medicationGroupRepository.getGroups()
                val slotAt = groups.firstOrNull { group -> group.uuid == groupUuid }
                    ?.reminderSlotAt(armedAt = scheduledAt, now = now)
                    ?: scheduledAt
                // The groups this post names, besides this one: each is marked and rescheduled with it.
                var named = emptySet<UUID>()
                if (!medicationReminderScheduler.awaitsReminderDelivery(groupUuid, scheduledAt)) {
                    diagnosticsLogger.info(
                        TAG,
                        "reminder_receiver_notification_skipped reason=delivered groupUuid=$groupUuid " +
                                "scheduledAt=$scheduledAt"
                    )
                } else {
                    val entries = medicationLogRepository.getScheduledGroupEntriesSince(slotAt)
                    // Another group's dose only while its own alarm for it is undelivered, as that
                    // alarm rang it already otherwise. Marked below, that alarm then posts nothing.
                    val undelivered = medicationReminderScheduler.undeliveredRemindersBy(slotAt)
                    val bundle = buildMedicationReminderBundle(
                        scheduledAt = slotAt,
                        // A group due again since slotAt gets its own, later reminder.
                        groups = groups.filter { group ->
                            group.previousScheduledForBefore(now)?.isAfter(slotAt) != true &&
                                    (group.uuid == groupUuid || group.uuid in undelivered)
                        },
                        entries = entries,
                    )
                    // Read again last before the post: Settings may have turned reminders off
                    // since the read above, and its own pass runs only after this hold.
                    val current = settingsRepository.getCurrentSettings()
                    if (bundle != null && !current.remindersEnabled) {
                        diagnosticsLogger.info(
                            TAG,
                            "reminder_receiver_notification_skipped reason=master_disabled groupUuid=$groupUuid"
                        )
                    } else if (bundle != null) {
                        reminderNotificationManager.showDoseReminderNotification(
                            bundle,
                            hideMedicationDetails = current.hideMedicationDetails,
                        )
                        named = bundle.slots.mapTo(mutableSetOf(), MedicationReminderSlot::groupUuid) - groupUuid
                        named.forEach { other ->
                            medicationReminderScheduler.markReminderDelivered(other, undelivered.getValue(other))
                        }
                        diagnosticsLogger.info(
                            TAG,
                            "reminder_receiver_notification_shown groupUuid=$groupUuid " +
                                    "slotAt=$slotAt groups=${groups.size} entries=${entries.size} " +
                                    "items=${bundle.items.size}"
                        )
                    } else {
                        diagnosticsLogger.info(
                            TAG,
                            "reminder_receiver_notification_skipped reason=no_bundle groupUuid=$groupUuid " +
                                    "slotAt=$slotAt groups=${groups.size} entries=${entries.size}"
                        )
                    }
                }
                medicationReminderScheduler.markReminderDelivered(groupUuid, scheduledAt)

                // Not before now: a slot in the past would fire at once.
                val after = maxOf(slotAt.plusSeconds(1), now)
                (setOf(groupUuid) + named).forEach { uuid ->
                    // Each in its own try: one that fails leaves the others to run.
                    try {
                        medicationReminderScheduler.rescheduleGroup(groupUuid = uuid, after = after)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        diagnosticsLogger.warning(
                            TAG,
                            "reminder_receiver_reschedule_failed groupUuid=$uuid after=$after",
                            error,
                        )
                    }
                }
                diagnosticsLogger.info(
                    TAG,
                    "reminder_receiver_reschedule_requested groupUuid=$groupUuid named=${named.size} after=$after"
                )
            }
            // The slot is no longer planned, so Home's estimate moved: store
            // the new one before the user opens the app, within what is left
            // of the budget.
            withTimeoutOrNull(-deadline.elapsedNow()) { e2EstimateRepository.warmHome() }
        }.onFailure { throwable ->
            diagnosticsLogger.warning(
                TAG,
                "reminder_receiver_failed groupUuid=$groupUuid scheduledAt=$scheduledAt",
                throwable,
            )
        }.also {
            pendingResult.finish()
        }
    }
}

/**
 * The dose an alarm armed for [armedAt] names when received at [now]: delivered late, or after
 * the clock moved ahead, the group's latest due time, and not each time in between.
 */
internal fun MedicationGroup.reminderSlotAt(armedAt: LocalDateTime, now: LocalDateTime): LocalDateTime =
    previousScheduledForBefore(now)?.takeIf { latest -> latest.isAfter(armedAt) } ?: armedAt

/** The wall time at [receivedAt], as far as it has passed ([passedAsOf]), both from one clock read. */
internal fun receiptTime(receivedAt: Instant, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
    LocalDateTime.ofInstant(receivedAt, zone).passedAsOf(receivedAt, zone)

private const val TAG = "MedicationReminderReceiver"

// Inside the ~10s background-broadcast goAsync allowance, as in
// WidgetDateReceiver.
private const val HANDLE_BUDGET_MS = 8_000L
