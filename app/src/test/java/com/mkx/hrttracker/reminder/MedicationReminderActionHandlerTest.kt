package com.mkx.hrttracker.reminder

import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogEntryInput
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineStockRepository
import com.mkx.hrttracker.data.repository.RunwayProjection
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationSignature
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
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
    private val medicineStockRepository: MedicineStockRepository = mockk()
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
        coEvery { medicineStockRepository.projectAllOnce(any()) } returns emptyList()

        actionHandler = MedicationReminderActionHandler(
            medicationGroupRepository = groupRepository,
            medicationLogRepository = logRepository,
            medicineStockRepository = medicineStockRepository,
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
            logTargets = null,
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
    fun logNow_with_logTargets_only_writes_signatures_present_in_targets() = runTest {
        // Scenario: group has [Estradiol, Spiro] at 9am. User logs Estradiol
        // at 8:50am — notification at 9am ships only Spiro as a log target.
        // User deletes the Estradiol log, then taps "Log all". The handler
        // must NOT re-log Estradiol because it was never displayed.
        val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
        val group = MedicationGroup(
            uuid = UUID.fromString("ddddd111-0000-0000-0000-000000000000"),
            name = "Morning",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0)),
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    medicine = testMedicine(key = MedicationKey.ESTRADIOL),
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
                ),
                testMedicationGroupMedication(
                    medicine = testMedicine(key = MedicationKey.SPIRONOLACTONE),
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
                ),
            ),
            notificationsEnabled = true,
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z"),
        )
        val slot = group.toReminderSlot(scheduledAt)
        val spiroMedication = group.medications[1]
        val savedEntries = slot<Collection<MedicationLogEntryInput>>()
        coEvery { groupRepository.getGroup(group.uuid) } returns group
        // Mirror "user deleted the Estradiol log" — DB has no entries for the slot.
        coEvery { logRepository.getScheduledGroupEntriesSince(scheduledAt) } returns emptyList()
        coEvery { logRepository.saveNewEntries(capture(savedEntries)) } just Runs

        actionHandler.logNow(
            slots = listOf(slot),
            logTargets = listOf(
                MedicationReminderLogTarget(
                    slot = slot,
                    signature = MedicationSignature.fromGroupMedication(spiroMedication),
                ),
            ),
            notificationTag = "bundle-tag",
            now = scheduledAt.plusMinutes(10),
        )

        assertEquals(1, savedEntries.captured.size)
        assertEquals(
            spiroMedication.medicineUuid,
            savedEntries.captured.single().medicineUuid,
        )
    }

    @Test
    fun logNow_with_logTargets_missing_slot_writes_nothing_for_that_slot() = runTest {
        // A current-format payload (non-null logTargets) that contains no
        // entry for a slot must restrict that slot to nothing, not silently
        // fall through to the legacy "log everything missing" path — that
        // would re-introduce the very race the targets exist to prevent.
        val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
        val firstGroup = medicationGroup(
            uuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
            name = "Estradiol",
            time = LocalTime.of(9, 0),
            medicationKey = MedicationKey.ESTRADIOL,
            medicationCount = 1,
        )
        val secondGroup = medicationGroup(
            uuid = UUID.fromString("ffffffff-1111-2222-3333-444444444444"),
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
        coEvery { logRepository.getScheduledGroupEntriesSince(scheduledAt) } returns emptyList()
        coEvery { logRepository.saveNewEntries(capture(savedEntries)) } just Runs

        // logTargets present but only references firstSlot. secondSlot has no
        // surviving entry — simulates every target for it failing to parse.
        actionHandler.logNow(
            slots = listOf(firstSlot, secondSlot),
            logTargets = listOf(
                MedicationReminderLogTarget(
                    slot = firstSlot,
                    signature = MedicationSignature.fromGroupMedication(firstGroup.medications.single()),
                ),
            ),
            notificationTag = "bundle-tag",
            now = scheduledAt.plusMinutes(10),
        )

        assertEquals(
            listOf(firstGroup.uuid),
            savedEntries.captured.map { entry -> entry.sourceGroupUuid }
        )
    }

    @Test
    fun logNow_singleUserLowMedicineAfterSave_showsUserLowToastInsteadOfLoggedToast() = runTest {
        val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
        val group = medicationGroup(
            uuid = UUID.fromString("8d74b369-cece-4681-88b1-68aaacfc6e96"),
            name = "Estradiol",
            time = LocalTime.of(9, 0),
            medicationKey = MedicationKey.ESTRADIOL,
            medicationCount = 1,
        )
        val medicine = group.medications.single().medicine!!
        val slot = group.toReminderSlot(scheduledAt)
        coEvery { groupRepository.getGroup(group.uuid) } returns group
        coEvery { logRepository.getScheduledGroupEntriesSince(scheduledAt) } returns emptyList()
        coEvery { logRepository.saveNewEntries(any()) } just Runs
        coEvery { medicineStockRepository.projectAllOnce(any()) } returns listOf(
            stockProjection(medicine, MedicineStockState.USER_LOW)
        )

        actionHandler.logNow(
            slots = listOf(slot),
            logTargets = null,
            notificationTag = "bundle-tag",
            now = scheduledAt.plusMinutes(10),
        )

        verify { notificationManager.showStockUserLowToast(medicine) }
        verify(exactly = 0) { notificationManager.showDoseReminderLoggedToast(any()) }
    }

    @Test
    fun logNow_singleUserLowMedicineAfterSave_whenMedicationDetailsHidden_showsUserLowCountToast() = runTest {
        val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
        val group = medicationGroup(
            uuid = UUID.fromString("d7193a2c-c4bf-4705-8ce0-20fad5b52471"),
            name = "Estradiol",
            time = LocalTime.of(9, 0),
            medicationKey = MedicationKey.ESTRADIOL,
            medicationCount = 1,
        )
        val medicine = group.medications.single().medicine!!
        val slot = group.toReminderSlot(scheduledAt)
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState(
            remindersEnabled = true,
            hideMedicationDetails = true,
        )
        coEvery { groupRepository.getGroup(group.uuid) } returns group
        coEvery { logRepository.getScheduledGroupEntriesSince(scheduledAt) } returns emptyList()
        coEvery { logRepository.saveNewEntries(any()) } just Runs
        coEvery { medicineStockRepository.projectAllOnce(any()) } returns listOf(
            stockProjection(medicine, MedicineStockState.USER_LOW)
        )

        actionHandler.logNow(
            slots = listOf(slot),
            logTargets = null,
            notificationTag = "bundle-tag",
            now = scheduledAt.plusMinutes(10),
        )

        verify { notificationManager.showStockUserLowCountToast(1) }
        verify(exactly = 0) { notificationManager.showStockUserLowToast(any()) }
        verify(exactly = 0) { notificationManager.showDoseReminderLoggedToast(any()) }
    }

    @Test
    fun logNow_twoUserLowMedicinesAfterSave_showsUserLowCountToast() = runTest {
        val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
        val firstGroup = medicationGroup(
            uuid = UUID.fromString("39c14b90-3225-4c96-8375-e509c43b7b4a"),
            name = "Estradiol",
            time = LocalTime.of(9, 0),
            medicationKey = MedicationKey.ESTRADIOL,
            medicationCount = 1,
        )
        val secondGroup = medicationGroup(
            uuid = UUID.fromString("743858c0-1478-432e-a53b-2d0ce381efcb"),
            name = "Spiro",
            time = LocalTime.of(9, 0),
            medicationKey = MedicationKey.SPIRONOLACTONE,
            medicationCount = 1,
        )
        val firstMedicine = firstGroup.medications.single().medicine!!
        val secondMedicine = secondGroup.medications.single().medicine!!
        val firstSlot = firstGroup.toReminderSlot(scheduledAt)
        val secondSlot = secondGroup.toReminderSlot(scheduledAt)
        coEvery { groupRepository.getGroup(firstGroup.uuid) } returns firstGroup
        coEvery { groupRepository.getGroup(secondGroup.uuid) } returns secondGroup
        coEvery { logRepository.getScheduledGroupEntriesSince(scheduledAt) } returns emptyList()
        coEvery { logRepository.saveNewEntries(any()) } just Runs
        coEvery { medicineStockRepository.projectAllOnce(any()) } returns listOf(
            stockProjection(firstMedicine, MedicineStockState.USER_LOW),
            stockProjection(secondMedicine, MedicineStockState.USER_LOW),
        )

        actionHandler.logNow(
            slots = listOf(firstSlot, secondSlot),
            logTargets = null,
            notificationTag = "bundle-tag",
            now = scheduledAt.plusMinutes(10),
        )

        verify { notificationManager.showStockUserLowCountToast(2) }
        verify(exactly = 0) { notificationManager.showDoseReminderLoggedToast(any()) }
    }

    @Test
    fun logNow_mixedOutAndHealthyAfterSave_showsSingleOutToast() = runTest {
        val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
        val outGroup = medicationGroup(
            uuid = UUID.fromString("b699d57d-6241-4b71-bb35-ae1c1277389f"),
            name = "Estradiol",
            time = LocalTime.of(9, 0),
            medicationKey = MedicationKey.ESTRADIOL,
            medicationCount = 1,
        )
        val healthyGroup = medicationGroup(
            uuid = UUID.fromString("9b2360b0-63d9-4234-89f4-9336f927618a"),
            name = "Spiro",
            time = LocalTime.of(9, 0),
            medicationKey = MedicationKey.SPIRONOLACTONE,
            medicationCount = 1,
        )
        val outMedicine = outGroup.medications.single().medicine!!
        val healthyMedicine = healthyGroup.medications.single().medicine!!
        coEvery { groupRepository.getGroup(outGroup.uuid) } returns outGroup
        coEvery { groupRepository.getGroup(healthyGroup.uuid) } returns healthyGroup
        coEvery { logRepository.getScheduledGroupEntriesSince(scheduledAt) } returns emptyList()
        coEvery { logRepository.saveNewEntries(any()) } just Runs
        coEvery { medicineStockRepository.projectAllOnce(any()) } returns listOf(
            stockProjection(outMedicine, MedicineStockState.OUT),
            stockProjection(healthyMedicine, MedicineStockState.HEALTHY),
        )

        actionHandler.logNow(
            slots = listOf(outGroup.toReminderSlot(scheduledAt), healthyGroup.toReminderSlot(scheduledAt)),
            logTargets = null,
            notificationTag = "bundle-tag",
            now = scheduledAt.plusMinutes(10),
        )

        verify { notificationManager.showStockOutToast(outMedicine) }
        verify(exactly = 0) { notificationManager.showDoseReminderLoggedToast(any()) }
    }

    @Test
    fun logNow_outAndUserLowAfterSave_showsOutCountToastForAllWarnedMedicines() = runTest {
        val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
        val outGroup = medicationGroup(
            uuid = UUID.fromString("6747b45a-c0bb-4588-bf27-fb487df7387e"),
            name = "Estradiol",
            time = LocalTime.of(9, 0),
            medicationKey = MedicationKey.ESTRADIOL,
            medicationCount = 1,
        )
        val lowGroup = medicationGroup(
            uuid = UUID.fromString("d0be1fb8-3f7c-4ed5-b05c-7b32259e3b27"),
            name = "Spiro",
            time = LocalTime.of(9, 0),
            medicationKey = MedicationKey.SPIRONOLACTONE,
            medicationCount = 1,
        )
        val outMedicine = outGroup.medications.single().medicine!!
        val lowMedicine = lowGroup.medications.single().medicine!!
        coEvery { groupRepository.getGroup(outGroup.uuid) } returns outGroup
        coEvery { groupRepository.getGroup(lowGroup.uuid) } returns lowGroup
        coEvery { logRepository.getScheduledGroupEntriesSince(scheduledAt) } returns emptyList()
        coEvery { logRepository.saveNewEntries(any()) } just Runs
        coEvery { medicineStockRepository.projectAllOnce(any()) } returns listOf(
            stockProjection(outMedicine, MedicineStockState.OUT),
            stockProjection(lowMedicine, MedicineStockState.USER_LOW),
        )

        actionHandler.logNow(
            slots = listOf(outGroup.toReminderSlot(scheduledAt), lowGroup.toReminderSlot(scheduledAt)),
            logTargets = null,
            notificationTag = "bundle-tag",
            now = scheduledAt.plusMinutes(10),
        )

        verify { notificationManager.showStockOutCountToast(2) }
        verify(exactly = 0) { notificationManager.showDoseReminderLoggedToast(any()) }
    }

    @Test
    fun logNow_projectionFailureAfterSave_stillRunsCleanupAndShowsLoggedToast() = runTest {
        val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
        val group = medicationGroup(
            uuid = UUID.fromString("204b54e8-1eb8-4206-8260-dbca90c798f0"),
            name = "Estradiol",
            time = LocalTime.of(9, 0),
            medicationKey = MedicationKey.ESTRADIOL,
            medicationCount = 1,
        )
        val slot = group.toReminderSlot(scheduledAt)
        coEvery { groupRepository.getGroup(group.uuid) } returns group
        coEvery { logRepository.getScheduledGroupEntriesSince(scheduledAt) } returns emptyList()
        coEvery { logRepository.saveNewEntries(any()) } just Runs
        coEvery { medicineStockRepository.projectAllOnce(any()) } throws RuntimeException("projection failed")

        actionHandler.logNow(
            slots = listOf(slot),
            logTargets = null,
            notificationTag = "bundle-tag",
            now = scheduledAt.plusMinutes(10),
        )

        verify { notificationManager.showDoseReminderLoggedToast(1) }
        coVerify { snoozeScheduler.clearSnoozesForSlots(listOf(slot)) }
        verify { notificationManager.cancelDoseReminderNotification("bundle-tag") }
        coVerify { reminderScheduler.rescheduleGroup(group.uuid, any()) }
    }

    @Test
    fun showPostLogToast_rethrowsCancellation() = runTest {
        val cancellation = CancellationException("projection cancelled")
        coEvery { medicineStockRepository.projectAllOnce(any()) } throws cancellation

        try {
            showPostLogToast(
                entriesToSave = listOf(
                    MedicationLogEntryInput(
                        medicineUuid = UUID.fromString("aaaa1111-0000-0000-0000-000000000001"),
                        applicationType = MedicationApplicationType.ORAL,
                        doseInstruction = DoseInstruction.TabletFraction(1, 1),
                        sourceGroupUuid = UUID.fromString("bbbb1111-0000-0000-0000-000000000001"),
                        appliedAt = Instant.parse("2026-04-20T00:10:00Z"),
                        count = 1,
                    )
                ),
                now = LocalDateTime.of(2026, 4, 20, 9, 10),
                medicineStockRepository = medicineStockRepository,
                reminderNotificationManager = notificationManager,
            )
            fail("Expected CancellationException")
        } catch (exception: CancellationException) {
            assertEquals("projection cancelled", exception.message)
        }
        verify(exactly = 0) { notificationManager.showDoseReminderLoggedToast(any()) }
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
                    medicine = testMedicine(key = medicationKey),
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
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
        val templateMedication = group.medications.first()
        return testMedicationLogEntry(
            medicine = templateMedication.medicine,
            applicationType = templateMedication.applicationType,
            doseInstruction = templateMedication.doseInstruction,
            sourceGroupUuid = group.uuid,
            appliedAt = testInstant(appliedAt),
            scheduledFor = scheduledFor,
            count = count,
        )
    }

    private fun stockProjection(
        medicine: Medicine,
        state: MedicineStockState,
    ): MedicineStockProjection {
        return MedicineStockProjection(
            medicine = medicine,
            dosesPerDayMagnitude = 1.0,
            totalStockUnits = if (state == MedicineStockState.OUT) 0.0 else 4.0,
            runway = RunwayProjection.NoSchedule,
            intervalDays = null,
            maxPerAdministration = 1.0,
            state = state,
        )
    }
}
