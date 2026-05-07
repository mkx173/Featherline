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
) {
    suspend fun logNow(
        slots: List<MedicationReminderSlot>,
        notificationTag: String?,
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        val normalizedSlots = slots.distinct()
        if (normalizedSlots.isEmpty()) {
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
        }

        reminderNotificationManager.showDoseReminderLoggedToast(entriesToSave.size)
        medicationReminderSnoozeScheduler.clearSnoozesForSlots(normalizedSlots)
        notificationTag?.let(reminderNotificationManager::cancelDoseReminderNotification)
        normalizedSlots
            .map(MedicationReminderSlot::groupUuid)
            .distinct()
            .forEach { groupUuid ->
                medicationReminderScheduler.rescheduleGroup(groupUuid, after = now)
            }
    }

    suspend fun remindLater(
        slots: List<MedicationReminderSlot>,
        notificationTag: String?,
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        val normalizedSlots = slots.distinct()
        if (!settingsRepository.getCurrentSettings().remindersEnabled) {
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
            medicationReminderSnoozeScheduler.clearSnoozesForSlots(unfulfilledSlots)
        } else {
            reminderNotificationManager.showDoseReminderSnoozedToast(REMINDER_SNOOZE_MINUTES)
        }
        notificationTag?.let(reminderNotificationManager::cancelDoseReminderNotification)
    }

    suspend fun showSnoozedReminder(
        slots: List<MedicationReminderSlot>,
        notificationTag: String?,
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        val normalizedSlots = slots.distinct()
        if (!settingsRepository.getCurrentSettings().remindersEnabled) {
            medicationReminderSnoozeScheduler.clearSnoozesForSlots(normalizedSlots)
            notificationTag?.let(reminderNotificationManager::cancelDoseReminderNotification)
            return
        }

        val unfulfilledSlots = currentlyUnfulfilledSlots(normalizedSlots)
        if (unfulfilledSlots.isEmpty()) {
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
        return slots.filter { slot ->
            val group = groupsByUuid[slot.groupUuid] ?: return@filter false
            !isSlotFulfilled(
                group = group,
                slot = slot.toPlanScheduleTimeSlot(),
                entries = entries,
            )
        }
    }

    private suspend fun loadRepresentedGroups(
        slots: List<MedicationReminderSlot>,
    ): Map<UUID, MedicationGroup> {
        return slots
            .map(MedicationReminderSlot::groupUuid)
            .distinct()
            .mapNotNull { groupUuid ->
                medicationGroupRepository.getGroup(groupUuid)
                    ?.takeIf { group -> group.isActive() && group.notificationsEnabled }
            }
            .associateBy(MedicationGroup::uuid)
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
