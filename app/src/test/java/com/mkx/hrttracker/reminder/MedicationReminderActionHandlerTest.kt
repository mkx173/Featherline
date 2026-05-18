package com.mkx.hrttracker.reminder

import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogEntryInput
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.testCatalogMedicationDetails
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

class MedicationReminderActionHandlerTest {
    private val groupRepository: MedicationGroupRepository = mockk()
    private val logRepository: MedicationLogRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val reminderScheduler: MedicationReminderScheduler = mockk()
    private val snoozeScheduler: MedicationReminderSnoozeScheduler = mockk()
    private val notificationManager: ReminderNotificationManager = mockk(relaxed = true)
    private val diagnosticsLogger: AppDiagnosticsLogger = mockk(relaxed = true)

    private lateinit var actionHandler: MedicationReminderActionHandler

    @Before
    fun setUp() {
        coEvery { reminderScheduler.rescheduleGroup(any(), any()) } just Runs
        coEvery { snoozeScheduler.clearSnoozesForSlots(any()) } just Runs
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState(remindersEnabled = true)

        actionHandler = MedicationReminderActionHandler(
            medicationGroupRepository = groupRepository,
            medicationLogRepository = logRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = reminderScheduler,
            medicationReminderSnoozeScheduler = snoozeScheduler,
            reminderNotificationManager = notificationManager,
            diagnosticsLogger = diagnosticsLogger,
        )
    }

    @Test
    fun logNow_saves_only_missing_records_for_all_represented_slots() = runTest {
        val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
        val firstGroup = medicationGroup(
            uuid = UUID.fromString("1927d898-8a25-4120-8d28-067b17860e58"),
            name = "Estradiol",
            time = LocalTime.of(9, 0),
            medicationKey = MedicationKey.ESTRADIOL,
            medicationCount = 2,
        )
        val secondGroup = medicationGroup(
            uuid = UUID.fromString("4e405587-85d1-4b78-a058-ecdf95bd49dc"),
            name = "Spiro",
            time = LocalTime.of(9, 0),
            medicationKey = MedicationKey.SPIRONOLACTONE,
            medicationCount = 1,
        )
        val firstSlot = firstGroup.toReminderSlot(scheduledAt)
        val secondSlot = secondGroup.toReminderSlot(scheduledAt)
        val savedEntries = slot<Collection<MedicationLogEntryInput>>()
        coEvery { groupRepository.getGroup(firstGroup.uuid) } returns firstGroup
        coEvery { groupRepository.getGroup(secondGroup.uuid) } returns secondGroup
        coEvery { logRepository.getScheduledGroupEntriesSince(scheduledAt) } returns listOf(
            scheduledEntry(
                group = firstGroup,
                appliedAt = scheduledAt.plusMinutes(1),
                scheduledFor = scheduledAt,
                count = 1,
            )
        )
        coEvery { logRepository.saveNewEntries(capture(savedEntries)) } just Runs

        actionHandler.logNow(
            slots = listOf(firstSlot, secondSlot),
            notificationTag = "bundle-tag",
            now = LocalDateTime.of(2026, 4, 20, 9, 10),
        )

        assertEquals(
            listOf(firstGroup.uuid, secondGroup.uuid),
            savedEntries.captured.map { entry -> entry.sourceGroupUuid }
        )
        assertEquals(
            listOf(firstGroup.schedule.timeSlots.first().uuid, secondGroup.schedule.timeSlots.first().uuid),
            savedEntries.captured.map { entry -> entry.scheduleTimeUuid }
        )
        assertEquals(listOf(1, 1), savedEntries.captured.map { entry -> entry.count })
        assertEquals(listOf(scheduledAt, scheduledAt), savedEntries.captured.map { entry -> entry.scheduledFor })
        coVerify { snoozeScheduler.clearSnoozesForSlots(listOf(firstSlot, secondSlot)) }
        coVerify { reminderScheduler.rescheduleGroup(firstGroup.uuid, any()) }
        coVerify { reminderScheduler.rescheduleGroup(secondGroup.uuid, any()) }
        verify { notificationManager.cancelDoseReminderNotification("bundle-tag") }
        verify { notificationManager.showDoseReminderLoggedToast(2) }
        verify {
            diagnosticsLogger.info(
                "MedicationReminderActionHandler",
                match { message ->
                    "reminder_action_log_now_complete" in message &&
                        "slots=2" in message &&
                        "entriesSaved=2" in message &&
                        "groupsRescheduled=2" in message
                }
            )
        }
    }

    @Test
    fun remindLater_skips_snooze_and_clears_notification_when_master_switch_off() = runTest {
        val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
        val slot = MedicationReminderSlot(
            groupUuid = UUID.fromString("ec504d8c-c5cd-4d4e-af0b-8f9bb4f5e9bf"),
            scheduledAt = scheduledAt,
            scheduleTimeUuid = UUID.fromString("a012cd77-5a28-486b-bc91-5c1fa9052e57"),
        )
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState(remindersEnabled = false)

        actionHandler.remindLater(
            slots = listOf(slot),
            notificationTag = "bundle-tag",
            now = scheduledAt.plusMinutes(1),
        )

        coVerify(exactly = 0) { snoozeScheduler.snoozeSlots(any(), any()) }
        coVerify { snoozeScheduler.clearSnoozesForSlots(listOf(slot)) }
        verify { notificationManager.cancelDoseReminderNotification("bundle-tag") }
        coVerify(exactly = 0) { logRepository.getScheduledGroupEntriesSince(any()) }
    }

    @Test
    fun remindLater_shows_snoozed_toast_when_slots_are_snoozed() = runTest {
        val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
        val group = medicationGroup(
            uuid = UUID.fromString("b3fdc11f-33d2-4ce7-a759-79a9d56ba577"),
            name = "Estradiol",
            time = LocalTime.of(9, 0),
            medicationKey = MedicationKey.ESTRADIOL,
            medicationCount = 1,
        )
        val slot = group.toReminderSlot(scheduledAt)
        coEvery { groupRepository.getGroup(group.uuid) } returns group
        coEvery { logRepository.getScheduledGroupEntriesSince(scheduledAt) } returns emptyList()
        coEvery {
            snoozeScheduler.snoozeSlots(
                slots = listOf(slot),
                now = scheduledAt.plusMinutes(1),
            )
        } returns listOf(
            MedicationReminderSnoozeRecord(
                slot = slot,
                snoozeAt = scheduledAt.plusMinutes(16),
                snoozeCount = 1,
            )
        )

        actionHandler.remindLater(
            slots = listOf(slot),
            notificationTag = "bundle-tag",
            now = scheduledAt.plusMinutes(1),
        )

        verify {
            notificationManager.showDoseReminderSnoozedToast(REMINDER_SNOOZE_MINUTES)
        }
        verify { notificationManager.cancelDoseReminderNotification("bundle-tag") }
    }

    @Test
    fun remindLater_shows_already_logged_toast_when_all_slots_are_fulfilled() = runTest {
        val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
        val group = medicationGroup(
            uuid = UUID.fromString("de07063d-0914-4e68-beba-b0e6d3ca0b0f"),
            name = "Estradiol",
            time = LocalTime.of(9, 0),
            medicationKey = MedicationKey.ESTRADIOL,
            medicationCount = 2,
        )
        val slot = group.toReminderSlot(scheduledAt)
        coEvery { groupRepository.getGroup(group.uuid) } returns group
        coEvery { logRepository.getScheduledGroupEntriesSince(scheduledAt) } returns listOf(
            scheduledEntry(
                group = group,
                appliedAt = scheduledAt.plusMinutes(1),
                scheduledFor = scheduledAt,
                count = 2,
            )
        )

        actionHandler.remindLater(
            slots = listOf(slot),
            notificationTag = "bundle-tag",
            now = scheduledAt.plusMinutes(1),
        )

        verify { notificationManager.showDoseReminderNothingToAddToast() }
        verify(exactly = 0) { notificationManager.showDoseReminderSnoozedToast(any()) }
        verify { notificationManager.cancelDoseReminderNotification("bundle-tag") }
        coVerify(exactly = 0) { snoozeScheduler.snoozeSlots(any(), any()) }
        coVerify { snoozeScheduler.clearSnoozesForSlots(listOf(slot)) }
    }

    @Test
    fun showSnoozedReminder_postsNotificationWithoutSnoozeActionWhenAllSlotsAreAtMaxSnoozeCount() = runTest {
        val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
        val group = medicationGroup(
            uuid = UUID.fromString("d4e5f6a7-b8c9-4d0e-1f2a-3b4c5d6e7f80"),
            name = "Estradiol",
            time = LocalTime.of(9, 0),
            medicationKey = MedicationKey.ESTRADIOL,
            medicationCount = 1,
        )
        val slot = group.toReminderSlot(scheduledAt)
        coEvery { groupRepository.getGroup(group.uuid) } returns group
        coEvery { logRepository.getScheduledGroupEntriesSince(scheduledAt) } returns emptyList()
        coEvery { snoozeScheduler.getSnoozeRecords() } returns listOf(
            MedicationReminderSnoozeRecord(
                slot = slot,
                snoozeAt = scheduledAt.plusMinutes(15),
                snoozeCount = MAX_REMINDER_SNOOZE_COUNT,
            )
        )

        actionHandler.showSnoozedReminder(
            slots = listOf(slot),
            notificationTag = "bundle-tag",
            now = scheduledAt.plusMinutes(15),
        )

        verify(exactly = 1) {
            notificationManager.showDoseReminderNotification(
                any(),
                canSnooze = false,
                hideMedicationDetails = false,
            )
        }
    }

    @Test
    fun showSnoozedReminder_skips_notification_when_master_switch_off() = runTest {
        val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
        val slot = MedicationReminderSlot(
            groupUuid = UUID.fromString("2f4484ea-b2de-4827-8900-2c4365b80346"),
            scheduledAt = scheduledAt,
            scheduleTimeUuid = UUID.fromString("dff5a6ad-3619-49d5-b2dd-6146f61920b2"),
        )
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState(remindersEnabled = false)

        actionHandler.showSnoozedReminder(
            slots = listOf(slot),
            notificationTag = "bundle-tag",
            now = scheduledAt.plusMinutes(15),
        )

        verify(exactly = 0) {
            notificationManager.showDoseReminderNotification(any(), any(), any())
        }
        coVerify { snoozeScheduler.clearSnoozesForSlots(listOf(slot)) }
        verify { notificationManager.cancelDoseReminderNotification("bundle-tag") }
        coVerify(exactly = 0) { logRepository.getScheduledGroupEntriesSince(any()) }
    }

    private fun medicationGroup(
        uuid: UUID,
        name: String,
        time: LocalTime,
        medicationKey: MedicationKey,
        medicationCount: Int,
    ): MedicationGroup {
        return MedicationGroup(
            uuid = uuid,
            name = name,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(time),
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    details = testCatalogMedicationDetails(
                        key = medicationKey,
                        applicationType = MedicationApplicationType.ORAL,
                        dose = MedicationDose.MgAsMedicine(2.0),
                    ),
                    count = medicationCount,
                )
            ),
            notificationsEnabled = true,
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z"),
        )
    }

    private fun MedicationGroup.toReminderSlot(scheduledAt: LocalDateTime): MedicationReminderSlot {
        return MedicationReminderSlot(
            groupUuid = uuid,
            scheduledAt = scheduledAt,
            scheduleTimeUuid = schedule.timeSlots.first().uuid,
        )
    }

    private fun scheduledEntry(
        group: MedicationGroup,
        appliedAt: LocalDateTime,
        scheduledFor: LocalDateTime,
        count: Int,
    ): MedicationLogEntry {
        return testMedicationLogEntry(
            details = group.medications.first().details,
            sourceGroupUuid = group.uuid,
            appliedAt = testInstant(appliedAt),
            scheduledFor = scheduledFor,
            count = count,
        )
    }
}
