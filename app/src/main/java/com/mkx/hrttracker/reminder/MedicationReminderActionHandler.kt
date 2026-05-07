package com.mkx.hrttracker.reminder

import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogEntryInput
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.isActive
import com.mkx.hrttracker.ui.plan.MedicationSignature
import com.mkx.hrttracker.ui.plan.PlanScheduleTimeSlot
import com.mkx.hrttracker.ui.plan.isEntryFulfillingPlanSlot
import com.mkx.hrttracker.ui.plan.isSlotFulfilled
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationReminderActionHandler @Inject constructor(
    private val medicationGroupRepository: MedicationGroupRepository,
    private val medicationLogRepository: MedicationLogRepository,
    private val settingsRepository: SettingsRepository,
    private val medicationReminderScheduler: MedicationReminderScheduler,
    private val medicationReminderSnoozeScheduler: MedicationReminderSnoozeScheduler,
    private val reminderNotificationManager: ReminderNotificationManager,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    suspend fun logNow(
        slots: List<MedicationReminderSlot>,
        notificationTag: String?,
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        val normalizedSlots = slots.distinct()
        diagnosticsLogger.info(
            TAG,
            "reminder_action_log_now_start slots=${normalizedSlots.size} " +
                "rawSlots=${slots.size} notificationTag=${notificationTag.orEmpty()} now=$now"
        )
        if (normalizedSlots.isEmpty()) {
            diagnosticsLogger.info(TAG, "reminder_action_log_now_skipped reason=empty_slots")
            notificationTag?.let(reminderNotificationManager::cancelDoseReminderNotification)
            return
        }

        val entries = medicationLogRepository.getScheduledGroupEntriesSince(
            normalizedSlots.minOf(MedicationReminderSlot::scheduledAt)
        )
        val groupsByUuid = loadRepresentedGroups(normalizedSlots)
        val entriesToSave = normalizedSlots.flatMap { slot ->
            val group = groupsByUuid[slot.groupUuid] ?: return@flatMap emptyList()
            buildMissingReminderLogEntries(
                group = group,
                slot = slot,
                entries = entries,
                appliedAt = now,
            )
        }

        if (entriesToSave.isNotEmpty()) {
            medicationLogRepository.saveNewEntries(entriesToSave)
            diagnosticsLogger.info(
                TAG,
                "reminder_action_log_now_saved entriesSaved=${entriesToSave.size} slots=${normalizedSlots.size}"
            )
        } else {
            diagnosticsLogger.info(
                TAG,
                "reminder_action_log_now_no_missing_entries slots=${normalizedSlots.size}"
            )
        }

        reminderNotificationManager.showDoseReminderLoggedToast(entriesToSave.size)
        medicationReminderSnoozeScheduler.clearSnoozesForSlots(normalizedSlots)
        notificationTag?.let(reminderNotificationManager::cancelDoseReminderNotification)
        val groupUuidsToReschedule = normalizedSlots
            .map(MedicationReminderSlot::groupUuid)
            .distinct()
        groupUuidsToReschedule.forEach { groupUuid ->
            medicationReminderScheduler.rescheduleGroup(groupUuid, after = now)
        }
        diagnosticsLogger.info(
            TAG,
            "reminder_action_log_now_complete slots=${normalizedSlots.size} " +
                "entriesSaved=${entriesToSave.size} groupsRescheduled=${groupUuidsToReschedule.size}"
        )
    }

    suspend fun remindLater(
        slots: List<MedicationReminderSlot>,
        notificationTag: String?,
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        val normalizedSlots = slots.distinct()
        diagnosticsLogger.info(
            TAG,
            "reminder_action_remind_later_start slots=${normalizedSlots.size} " +
                "rawSlots=${slots.size} notificationTag=${notificationTag.orEmpty()} now=$now"
        )
        if (!settingsRepository.getCurrentSettings().remindersEnabled) {
            diagnosticsLogger.info(
                TAG,
                "reminder_action_remind_later_skipped reason=master_disabled slots=${normalizedSlots.size}"
            )
            medicationReminderSnoozeScheduler.clearSnoozesForSlots(normalizedSlots)
            notificationTag?.let(reminderNotificationManager::cancelDoseReminderNotification)
            return
        }

        val unfulfilledSlots = currentlyUnfulfilledSlots(normalizedSlots)
        val scheduledSnoozes = medicationReminderSnoozeScheduler.snoozeSlots(
            slots = unfulfilledSlots,
            now = now,
        )
        if (scheduledSnoozes.isEmpty()) {
            diagnosticsLogger.info(
                TAG,
                "reminder_action_remind_later_no_snoozes unfulfilledSlots=${unfulfilledSlots.size}"
            )
            medicationReminderSnoozeScheduler.clearSnoozesForSlots(unfulfilledSlots)
        } else {
            reminderNotificationManager.showDoseReminderSnoozedToast(REMINDER_SNOOZE_MINUTES)
        }
        notificationTag?.let(reminderNotificationManager::cancelDoseReminderNotification)
        diagnosticsLogger.info(
            TAG,
            "reminder_action_remind_later_complete slots=${normalizedSlots.size} " +
                "unfulfilledSlots=${unfulfilledSlots.size} snoozes=${scheduledSnoozes.size}"
        )
    }

    suspend fun showSnoozedReminder(
        slots: List<MedicationReminderSlot>,
        notificationTag: String?,
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        val normalizedSlots = slots.distinct()
        diagnosticsLogger.info(
            TAG,
            "reminder_action_show_snoozed_start slots=${normalizedSlots.size} " +
                "rawSlots=${slots.size} notificationTag=${notificationTag.orEmpty()} now=$now"
        )
        if (!settingsRepository.getCurrentSettings().remindersEnabled) {
            diagnosticsLogger.info(
                TAG,
                "reminder_action_show_snoozed_skipped reason=master_disabled slots=${normalizedSlots.size}"
            )
            medicationReminderSnoozeScheduler.clearSnoozesForSlots(normalizedSlots)
            notificationTag?.let(reminderNotificationManager::cancelDoseReminderNotification)
            return
        }

        val unfulfilledSlots = currentlyUnfulfilledSlots(normalizedSlots)
        if (unfulfilledSlots.isEmpty()) {
            diagnosticsLogger.info(TAG, "reminder_action_show_snoozed_skipped reason=no_unfulfilled_slots")
            notificationTag?.let(reminderNotificationManager::cancelDoseReminderNotification)
            return
        }

        val groupsByUuid = loadRepresentedGroups(unfulfilledSlots)
        val bundleItems = unfulfilledSlots.mapNotNull { slot ->
            val group = groupsByUuid[slot.groupUuid] ?: return@mapNotNull null
            MedicationReminderBundleItem(
                slot = slot,
                groupName = group.name,
                medications = group.medications,
            )
        }
        if (bundleItems.isEmpty()) {
            diagnosticsLogger.info(TAG, "reminder_action_show_snoozed_skipped reason=no_bundle_items")
            return
        }

        val recordsBySlot = medicationReminderSnoozeScheduler.getSnoozeRecords()
            .associateBy(MedicationReminderSnoozeRecord::slot)
        val canSnooze = unfulfilledSlots.any { slot ->
            recordsBySlot[slot]?.snoozeCount.orZero() < MAX_REMINDER_SNOOZE_COUNT
        }

        reminderNotificationManager.showDoseReminderNotification(
            bundle = MedicationReminderBundle(
                scheduledAt = unfulfilledSlots.minOf(MedicationReminderSlot::scheduledAt),
                items = bundleItems,
            ),
            canSnooze = canSnooze,
        )
        diagnosticsLogger.info(
            TAG,
            "reminder_action_show_snoozed_complete slots=${normalizedSlots.size} " +
                "unfulfilledSlots=${unfulfilledSlots.size} bundleItems=${bundleItems.size} canSnooze=$canSnooze"
        )
    }

    private suspend fun currentlyUnfulfilledSlots(
        slots: List<MedicationReminderSlot>,
    ): List<MedicationReminderSlot> {
        if (slots.isEmpty()) {
            return emptyList()
        }

        val entries = medicationLogRepository.getScheduledGroupEntriesSince(
            slots.minOf(MedicationReminderSlot::scheduledAt)
        )
        val groupsByUuid = loadRepresentedGroups(slots)
        val unfulfilledSlots = slots.filter { slot ->
            val group = groupsByUuid[slot.groupUuid] ?: return@filter false
            !isSlotFulfilled(
                group = group,
                slot = slot.toPlanScheduleTimeSlot(),
                entries = entries,
            )
        }
        diagnosticsLogger.info(
            TAG,
            "reminder_action_unfulfilled_slots_checked slots=${slots.size} " +
                "groups=${groupsByUuid.size} entries=${entries.size} unfulfilled=${unfulfilledSlots.size}"
        )
        return unfulfilledSlots
    }

    private suspend fun loadRepresentedGroups(
        slots: List<MedicationReminderSlot>,
    ): Map<UUID, MedicationGroup> {
        val requestedGroupUuids = slots.map(MedicationReminderSlot::groupUuid).distinct()
        val groups = requestedGroupUuids
            .mapNotNull { groupUuid ->
                medicationGroupRepository.getGroup(groupUuid)
                    ?.takeIf { group -> group.isActive() && group.notificationsEnabled }
            }
            .associateBy(MedicationGroup::uuid)
        diagnosticsLogger.info(
            TAG,
            "reminder_action_groups_loaded requested=${requestedGroupUuids.size} loaded=${groups.size}"
        )
        return groups
    }
}

internal fun buildMissingReminderLogEntries(
    group: MedicationGroup,
    slot: MedicationReminderSlot,
    entries: List<MedicationLogEntry>,
    appliedAt: LocalDateTime,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<MedicationLogEntryInput> {
    if (!group.isActive() || !group.notificationsEnabled || group.medications.isEmpty()) {
        return emptyList()
    }

    val planSlot = slot.toPlanScheduleTimeSlot()
    val slotLogs = entries.filter { entry ->
        isEntryFulfillingPlanSlot(
            group = group,
            slot = planSlot,
            entry = entry,
            zoneId = zoneId,
        )
    }
    val requiredCounts = group.medications
        .groupBy(MedicationSignature::fromGroupMedication)
        .mapValues { (_, medications) -> medications.sumOf { medication -> medication.count } }
    val loggedCounts = slotLogs
        .groupBy(MedicationSignature::fromLogEntry)
        .mapValues { (_, logs) -> logs.sumOf(MedicationLogEntry::count) }

    return group.medications
        .groupBy(MedicationSignature::fromGroupMedication)
        .mapNotNull { (signature, medications) ->
            val missingCount = requiredCounts.getValue(signature) -
                loggedCounts.getOrDefault(signature, 0)
            if (missingCount <= 0) {
                return@mapNotNull null
            }
            MedicationLogEntryInput(
                medication = medications.first().details,
                sourceGroupUuid = group.uuid,
                scheduleTimeUuid = slot.scheduleTimeUuid,
                appliedAt = appliedAt.atZone(zoneId).toInstant(),
                scheduledFor = slot.scheduledAt,
                count = missingCount,
                appliedAtTimeZoneId = zoneId.id,
            )
        }
}

internal fun MedicationReminderSlot.toPlanScheduleTimeSlot(): PlanScheduleTimeSlot {
    return PlanScheduleTimeSlot(
        scheduleTimeUuid = scheduleTimeUuid,
        scheduledFor = scheduledAt,
    )
}

private fun Int?.orZero(): Int = this ?: 0
private const val TAG = "MedicationReminderActionHandler"
