package com.mkx.hrttracker.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mkx.hrttracker.data.repository.E2EstimateRepository
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.model.medication.previousScheduledForBefore
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
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
            now = LocalDateTime.now(),
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
    /** When the broadcast was received. */
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

            val groups = medicationGroupRepository.getGroups()
            // Delivered late, or after the clock moved ahead: the dose due now,
            // the group's latest past time, and not each time in between.
            val slotAt = groups.firstOrNull { group -> group.uuid == groupUuid }
                ?.previousScheduledForBefore(now)
                ?.takeIf { latest -> latest.isAfter(scheduledAt) }
                ?: scheduledAt
            val entries = medicationLogRepository.getScheduledGroupEntriesSince(slotAt)
            val bundle = buildMedicationReminderBundle(
                scheduledAt = slotAt,
                // A group due again since slotAt gets its own, later reminder.
                groups = groups.filterNot { group ->
                    group.previousScheduledForBefore(now)?.isAfter(slotAt) == true
                },
                entries = entries,
            )
            if (bundle != null) {
                reminderNotificationManager.showDoseReminderNotification(
                    bundle,
                    hideMedicationDetails = settings.hideMedicationDetails,
                )
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

            // Not before now: a slot in the past would fire at once.
            val after = maxOf(slotAt.plusSeconds(1), now)
            medicationReminderScheduler.rescheduleGroup(groupUuid = groupUuid, after = after)
            diagnosticsLogger.info(
                TAG,
                "reminder_receiver_reschedule_requested groupUuid=$groupUuid after=$after"
            )
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

private const val TAG = "MedicationReminderReceiver"

// Inside the ~10s background-broadcast goAsync allowance, as in
// WidgetDateReceiver.
private const val HANDLE_BUDGET_MS = 8_000L
