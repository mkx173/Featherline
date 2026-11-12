package com.mkx.hrttracker.reminder

import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogEntryInput
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineStockRepository
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
import com.mkx.hrttracker.model.medication.RunwayProjection
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
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
    private val notificationManager: ReminderNotificationManager = mockk(relaxed = true)
    private val diagnosticsLogger: AppDiagnosticsLogger = mockk(relaxed = true)

    private lateinit var actionHandler: MedicationReminderActionHandler

    @After
    fun tearDown() = clearAllMocks()

    @Before
    fun setUp() {
        coEvery { reminderScheduler.rescheduleGroup(any(), any()) } just Runs
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState(remindersEnabled = true)
        coEvery { medicineStockRepository.projectAllOnce(any()) } returns emptyList()

        actionHandler = MedicationReminderActionHandler(
            medicationGroupRepository = groupRepository,
            medicationLogRepository = logRepository,
            medicineStockRepository = medicineStockRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = reminderScheduler,
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
        coEvery { logRepository.saveNewEntries(capture(savedEntries)) } coAnswers { savedEntries.captured.toList() }

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
            listOf(
                firstGroup.schedule.timeSlots.first().uuid,
                secondGroup.schedule.timeSlots.first().uuid
            ),
            savedEntries.captured.map { entry -> entry.scheduleTimeUuid }
        )
        assertEquals(listOf(1, 1), savedEntries.captured.map { entry -> entry.count })
        assertEquals(
            listOf(scheduledAt, scheduledAt),
            savedEntries.captured.map { entry -> entry.scheduledFor })
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
        coEvery { logRepository.saveNewEntries(capture(savedEntries)) } coAnswers { savedEntries.captured.toList() }

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
        coEvery { logRepository.saveNewEntries(capture(savedEntries)) } coAnswers { savedEntries.captured.toList() }

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
        coEvery { logRepository.saveNewEntries(any()) } coAnswers { firstArg<Collection<MedicationLogEntryInput>>().toList() }
        // First projection is the pre-log "before" baseline (empty = was healthy),
        // second is the post-log result that the warning is derived from.
        coEvery { medicineStockRepository.projectAllOnce(any()) } returnsMany listOf(
            emptyList(),
            listOf(stockProjection(medicine, MedicineStockState.USER_LOW)),
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
    fun logNow_singleUserLowMedicineAfterSave_whenMedicationDetailsHidden_showsUserLowCountToast() =
        runTest {
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
            coEvery { logRepository.saveNewEntries(any()) } coAnswers { firstArg<Collection<MedicationLogEntryInput>>().toList() }
            // First projection is the pre-log "before" baseline (empty = was healthy),
            // second is the post-log result that the warning is derived from.
            coEvery { medicineStockRepository.projectAllOnce(any()) } returnsMany listOf(
                emptyList(),
                listOf(stockProjection(medicine, MedicineStockState.USER_LOW)),
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
    fun logNow_twoUserLowMedicinesAfterSave_showsManyAttentionToast() = runTest {
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
        coEvery { logRepository.saveNewEntries(any()) } coAnswers { firstArg<Collection<MedicationLogEntryInput>>().toList() }
        coEvery { medicineStockRepository.projectAllOnce(any()) } returnsMany listOf(
            emptyList(),
            listOf(
                stockProjection(firstMedicine, MedicineStockState.USER_LOW),
                stockProjection(secondMedicine, MedicineStockState.USER_LOW),
            ),
        )

        actionHandler.logNow(
            slots = listOf(firstSlot, secondSlot),
            logTargets = null,
            notificationTag = "bundle-tag",
            now = scheduledAt.plusMinutes(10),
        )

        verify { notificationManager.showStockManyAttentionToast(2) }
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
        coEvery { logRepository.saveNewEntries(any()) } coAnswers { firstArg<Collection<MedicationLogEntryInput>>().toList() }
        coEvery { medicineStockRepository.projectAllOnce(any()) } returnsMany listOf(
            emptyList(),
            listOf(
                stockProjection(outMedicine, MedicineStockState.OUT),
                stockProjection(healthyMedicine, MedicineStockState.HEALTHY),
            ),
        )

        actionHandler.logNow(
            slots = listOf(
                outGroup.toReminderSlot(scheduledAt),
                healthyGroup.toReminderSlot(scheduledAt)
            ),
            logTargets = null,
            notificationTag = "bundle-tag",
            now = scheduledAt.plusMinutes(10),
        )

        verify { notificationManager.showStockOutToast(outMedicine) }
        verify(exactly = 0) { notificationManager.showDoseReminderLoggedToast(any()) }
    }

    @Test
    fun logNow_outAndUserLowAfterSave_showsManyAttentionToastForAllWarnedMedicines() = runTest {
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
        coEvery { logRepository.saveNewEntries(any()) } coAnswers { firstArg<Collection<MedicationLogEntryInput>>().toList() }
        coEvery { medicineStockRepository.projectAllOnce(any()) } returnsMany listOf(
            emptyList(),
            listOf(
                stockProjection(outMedicine, MedicineStockState.OUT),
                stockProjection(lowMedicine, MedicineStockState.USER_LOW),
            ),
        )

        actionHandler.logNow(
            slots = listOf(
                outGroup.toReminderSlot(scheduledAt),
                lowGroup.toReminderSlot(scheduledAt)
            ),
            logTargets = null,
            notificationTag = "bundle-tag",
            now = scheduledAt.plusMinutes(10),
        )

        verify { notificationManager.showStockManyAttentionToast(2) }
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
        coEvery { logRepository.saveNewEntries(any()) } coAnswers { firstArg<Collection<MedicationLogEntryInput>>().toList() }
        coEvery { medicineStockRepository.projectAllOnce(any()) } throws RuntimeException("projection failed")

        actionHandler.logNow(
            slots = listOf(slot),
            logTargets = null,
            notificationTag = "bundle-tag",
            now = scheduledAt.plusMinutes(10),
        )

        verify { notificationManager.showDoseReminderLoggedToast(1) }
        verify { notificationManager.cancelDoseReminderNotification("bundle-tag") }
        coVerify { reminderScheduler.rescheduleGroup(group.uuid, any()) }
    }

    @Test
    fun logNow_whenAnotherWriterLoggedTheDoseFirst_showsAlreadyLogged() = runTest {
        val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
        val group = medicationGroup(
            uuid = UUID.fromString("3f0c5e52-8d1a-4b7e-9c64-2a9d81f0b3c7"),
            name = "Estradiol",
            time = LocalTime.of(9, 0),
            medicationKey = MedicationKey.ESTRADIOL,
            medicationCount = 1,
        )
        val slot = group.toReminderSlot(scheduledAt)
        coEvery { groupRepository.getGroup(group.uuid) } returns group
        coEvery { logRepository.getScheduledGroupEntriesSince(scheduledAt) } returns emptyList()
        // The widget logged the slot between this read and the write.
        coEvery { logRepository.saveNewEntries(any()) } returns emptyList()

        actionHandler.logNow(
            slots = listOf(slot),
            logTargets = null,
            notificationTag = "bundle-tag",
            now = scheduledAt.plusMinutes(10),
        )

        verify { notificationManager.showDoseReminderNothingToAddToast() }
        verify(exactly = 0) { notificationManager.showDoseReminderLoggedToast(any()) }
        verify { notificationManager.cancelDoseReminderNotification("bundle-tag") }
    }

    @Test
    fun logNow_logsGroupArchivedAfterNotificationFired() = runTest {
        // The notification fired while the group was active; the user then archived the
        // group in-app (which cancels the future alarm but leaves the posted notification
        // up) and tapped "Log all". An explicit Log-all must still write the dose rather
        // than silently no-op.
        val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
        val group = medicationGroup(
            uuid = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000001"),
            name = "Estradiol",
            time = LocalTime.of(9, 0),
            medicationKey = MedicationKey.ESTRADIOL,
            medicationCount = 1,
            archivedAt = Instant.parse("2026-04-20T00:05:00Z"),
        )
        val slot = group.toReminderSlot(scheduledAt)
        val savedEntries = slot<Collection<MedicationLogEntryInput>>()
        coEvery { groupRepository.getGroup(group.uuid) } returns group
        coEvery { logRepository.getScheduledGroupEntriesSince(scheduledAt) } returns emptyList()
        coEvery { logRepository.saveNewEntries(capture(savedEntries)) } coAnswers { savedEntries.captured.toList() }

        actionHandler.logNow(
            slots = listOf(slot),
            logTargets = null,
            notificationTag = "bundle-tag",
            now = scheduledAt.plusMinutes(10),
        )

        assertEquals(listOf(group.uuid), savedEntries.captured.map { it.sourceGroupUuid })
    }

    @Test
    fun logNow_logsGroupWithNotificationsDisabledAfterNotificationFired() = runTest {
        // Same shape as the archive case, but the post-fire change is disabling the
        // group's notifications. An explicit Log-all tap on the still-visible
        // notification must still write the dose.
        val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
        val group = medicationGroup(
            uuid = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000002"),
            name = "Estradiol",
            time = LocalTime.of(9, 0),
            medicationKey = MedicationKey.ESTRADIOL,
            medicationCount = 1,
            notificationsEnabled = false,
        )
        val slot = group.toReminderSlot(scheduledAt)
        val savedEntries = slot<Collection<MedicationLogEntryInput>>()
        coEvery { groupRepository.getGroup(group.uuid) } returns group
        coEvery { logRepository.getScheduledGroupEntriesSince(scheduledAt) } returns emptyList()
        coEvery { logRepository.saveNewEntries(capture(savedEntries)) } coAnswers { savedEntries.captured.toList() }

        actionHandler.logNow(
            slots = listOf(slot),
            logTargets = null,
            notificationTag = "bundle-tag",
            now = scheduledAt.plusMinutes(10),
        )

        assertEquals(listOf(group.uuid), savedEntries.captured.map { it.sourceGroupUuid })
    }

    @Test
    fun logNow_inMixedBundle_logsBothActiveAndArchivedGroups() = runTest {
        // A bundle spanning two groups fired; one was archived afterwards. Log-all writes
        // both — the archived group (explicit re-log) and, critically, the still-active
        // group, which must not be collateral-dropped because a sibling went archived.
        val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
        val archivedGroup = medicationGroup(
            uuid = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000003"),
            name = "Estradiol",
            time = LocalTime.of(9, 0),
            medicationKey = MedicationKey.ESTRADIOL,
            medicationCount = 1,
            archivedAt = Instant.parse("2026-04-20T00:05:00Z"),
        )
        val activeGroup = medicationGroup(
            uuid = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000004"),
            name = "Spiro",
            time = LocalTime.of(9, 0),
            medicationKey = MedicationKey.SPIRONOLACTONE,
            medicationCount = 1,
        )
        val archivedSlot = archivedGroup.toReminderSlot(scheduledAt)
        val activeSlot = activeGroup.toReminderSlot(scheduledAt)
        val savedEntries = slot<Collection<MedicationLogEntryInput>>()
        coEvery { groupRepository.getGroup(archivedGroup.uuid) } returns archivedGroup
        coEvery { groupRepository.getGroup(activeGroup.uuid) } returns activeGroup
        coEvery { logRepository.getScheduledGroupEntriesSince(scheduledAt) } returns emptyList()
        coEvery { logRepository.saveNewEntries(capture(savedEntries)) } coAnswers { savedEntries.captured.toList() }

        actionHandler.logNow(
            slots = listOf(archivedSlot, activeSlot),
            logTargets = null,
            notificationTag = "bundle-tag",
            now = scheduledAt.plusMinutes(10),
        )

        assertEquals(
            listOf(archivedGroup.uuid, activeGroup.uuid),
            savedEntries.captured.map { it.sourceGroupUuid },
        )
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
                beforeStatesByUuid = emptyMap(),
            )
            fail("Expected CancellationException")
        } catch (exception: CancellationException) {
            assertEquals("projection cancelled", exception.message)
        }
        verify(exactly = 0) { notificationManager.showDoseReminderLoggedToast(any()) }
    }

    @Test
    fun resolvePostLogStockWarning_singleWorstState() {
        val outGroup = medicationGroup(
            uuid = UUID.fromString("ea0dd67a-e176-4fe7-9e99-876ff2b9a8b6"),
            name = "Estradiol",
            time = LocalTime.of(9, 0),
            medicationKey = MedicationKey.ESTRADIOL,
            medicationCount = 1,
        )
        val lowGroup = medicationGroup(
            uuid = UUID.fromString("fdb218ac-0eb2-4c40-b208-e38b185c549e"),
            name = "Spiro",
            time = LocalTime.of(9, 0),
            medicationKey = MedicationKey.SPIRONOLACTONE,
            medicationCount = 1,
        )
        val outMedicine = outGroup.medications.single().medicine!!
        val lowMedicine = lowGroup.medications.single().medicine!!

        val warning = resolvePostLogStockWarning(
            projections = listOf(
                stockProjection(outMedicine, MedicineStockState.OUT),
                stockProjection(lowMedicine, MedicineStockState.USER_LOW),
            ),
            affectedMedicineUuids = setOf(outMedicine.uuid),
            beforeStatesByUuid = emptyMap(),
        )

        assertEquals(
            PostLogStockWarning.Single(outMedicine, MedicineStockState.OUT),
            warning,
        )
    }

    @Test
    fun resolvePostLogStockWarning_noneWhenHealthy() {
        val group = medicationGroup(
            uuid = UUID.fromString("74f9094e-7ae3-4f9f-b4ef-9e068497c6e7"),
            name = "Estradiol",
            time = LocalTime.of(9, 0),
            medicationKey = MedicationKey.ESTRADIOL,
            medicationCount = 1,
        )
        val medicine = group.medications.single().medicine!!

        val warning = resolvePostLogStockWarning(
            projections = listOf(stockProjection(medicine, MedicineStockState.HEALTHY)),
            affectedMedicineUuids = setOf(medicine.uuid),
            beforeStatesByUuid = emptyMap(),
        )

        assertNull(warning)
    }

    @Test
    fun resolvePostLogStockWarning_manyUsesWorstStateAndWarnedCount() {
        val outGroup = medicationGroup(
            uuid = UUID.fromString("8f651746-b066-4c01-96c3-6d7b05d62665"),
            name = "Estradiol",
            time = LocalTime.of(9, 0),
            medicationKey = MedicationKey.ESTRADIOL,
            medicationCount = 1,
        )
        val lowGroup = medicationGroup(
            uuid = UUID.fromString("760993d8-3840-4c6d-ae0d-6217d632a1eb"),
            name = "Spiro",
            time = LocalTime.of(9, 0),
            medicationKey = MedicationKey.SPIRONOLACTONE,
            medicationCount = 1,
        )
        val healthyGroup = medicationGroup(
            uuid = UUID.fromString("75439923-ee44-4c38-85d2-50e73317733e"),
            name = "Progesterone",
            time = LocalTime.of(9, 0),
            medicationKey = MedicationKey.CYPROTERONE_ACETATE,
            medicationCount = 1,
        )
        val outMedicine = outGroup.medications.single().medicine!!
        val lowMedicine = lowGroup.medications.single().medicine!!
        val healthyMedicine = healthyGroup.medications.single().medicine!!

        val warning = resolvePostLogStockWarning(
            projections = listOf(
                stockProjection(outMedicine, MedicineStockState.OUT),
                stockProjection(lowMedicine, MedicineStockState.USER_LOW),
                stockProjection(healthyMedicine, MedicineStockState.HEALTHY),
            ),
            affectedMedicineUuids = setOf(outMedicine.uuid, lowMedicine.uuid, healthyMedicine.uuid),
            beforeStatesByUuid = emptyMap(),
        )

        assertEquals(
            PostLogStockWarning.Many(count = 2),
            warning,
        )
    }

    @Test
    fun resolvePostLogStockWarning_onlyWarnsForMedicinesThatWorsened() {
        val droppedGroup = medicationGroup(
            uuid = UUID.fromString("0a4c8d2e-1111-4c2a-9a5b-000000000001"),
            name = "Estradiol",
            time = LocalTime.of(9, 0),
            medicationKey = MedicationKey.ESTRADIOL,
            medicationCount = 1,
        )
        val stayedGroup = medicationGroup(
            uuid = UUID.fromString("0a4c8d2e-2222-4c2a-9a5b-000000000002"),
            name = "Spiro",
            time = LocalTime.of(9, 0),
            medicationKey = MedicationKey.SPIRONOLACTONE,
            medicationCount = 1,
        )
        val droppedMedicine = droppedGroup.medications.single().medicine!!
        val stayedMedicine = stayedGroup.medications.single().medicine!!

        val warning = resolvePostLogStockWarning(
            projections = listOf(
                stockProjection(droppedMedicine, MedicineStockState.USER_LOW),
                stockProjection(stayedMedicine, MedicineStockState.USER_LOW),
            ),
            affectedMedicineUuids = setOf(droppedMedicine.uuid, stayedMedicine.uuid),
            beforeStatesByUuid = mapOf(
                // Already low before the log → unchanged tier → must be skipped,
                // so an existing low-stock medicine doesn't re-warn on every dose.
                stayedMedicine.uuid to MedicineStockState.USER_LOW,
                // Was healthy before → dropping to low is a genuine worsening.
                droppedMedicine.uuid to MedicineStockState.HEALTHY,
            ),
        )

        assertEquals(
            PostLogStockWarning.Single(droppedMedicine, MedicineStockState.USER_LOW),
            warning,
        )
    }

    @Test
    fun actions_ignore_a_dose_the_plan_no_longer_has() = runTest {
        // A Monday 09:00 reminder still on screen after the plan moved to Tuesdays.
        val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
        val dailyGroup = medicationGroup(
            uuid = UUID.fromString("5b6c7d8e-9f0a-4b1c-8d2e-3f4a5b6c7d8e"),
            name = "Estradiol",
            time = LocalTime.of(9, 0),
            medicationKey = MedicationKey.ESTRADIOL,
            medicationCount = 1,
        )
        val slot = dailyGroup.toReminderSlot(scheduledAt)
        val group = dailyGroup.copy(
            schedule = dailyGroup.schedule.copy(
                type = MedicationGroupScheduleType.WEEKLY,
                weeklyDaysOfWeek = setOf(DayOfWeek.TUESDAY),
            ),
        )
        coEvery { groupRepository.getGroup(group.uuid) } returns group
        coEvery { logRepository.getScheduledGroupEntriesSince(scheduledAt) } returns emptyList()
        coEvery { logRepository.saveNewEntries(any()) } coAnswers { firstArg<Collection<MedicationLogEntryInput>>().toList() }

        actionHandler.logNow(slots = listOf(slot), logTargets = null, notificationTag = "tag", now = scheduledAt.plusMinutes(20))

        verify(exactly = 0) { notificationManager.showDoseReminderNotification(any(), any(), any()) }
        coVerify(exactly = 0) { logRepository.saveNewEntries(any()) }
        verify { notificationManager.showDoseReminderNoLongerScheduledToast() }
    }

    @Test
    fun skip_cancelsTheReminderFirst_thenMovesThePlansWatermark_andLogsNothing() = runTest {
        val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
        val group = medicationGroup(
            uuid = UUID.fromString("6c7d8e9f-0a1b-4c2d-9e3f-4a5b6c7d8e9f"),
            name = "Estradiol",
            time = LocalTime.of(9, 0),
            medicationKey = MedicationKey.ESTRADIOL,
            medicationCount = 1,
        )
        coEvery { reminderScheduler.skipThrough(any(), any(), any()) } just Runs

        actionHandler.skip(slots = listOf(group.toReminderSlot(scheduledAt)), notificationTag = "tag")

        coVerifyOrder {
            notificationManager.cancelDoseReminderNotification("tag")
            reminderScheduler.skipThrough(group.uuid, scheduledAt, any())
            notificationManager.showDoseReminderSkippedToast()
        }
        coVerify(exactly = 0) { logRepository.saveNewEntries(any()) }
    }

    @Test
    fun dismissed_namesThePlansArmedAlert_inAToast() = runTest {
        val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
        val groupUuid = UUID.fromString("7d8e9f0a-1b2c-4d3e-8f4a-5b6c7d8e9f0a")
        val now = testInstant(scheduledAt.plusMinutes(5))
        val next = testInstant(scheduledAt.plusMinutes(15))
        coEvery { reminderScheduler.armedAlertAt(groupUuid) } returns next

        actionHandler.dismissed(
            slots = listOf(MedicationReminderSlot(groupUuid, scheduledAt, null)),
            now = now,
        )

        verify { notificationManager.showNextReminderToast(next) }
    }

    @Test
    fun dismissed_showsNoToast_withNoAlertAhead_orRemindersOff() = runTest {
        val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
        val groupUuid = UUID.fromString("8e9f0a1b-2c3d-4e4f-9a5b-6c7d8e9f0a1b")
        val slots = listOf(MedicationReminderSlot(groupUuid, scheduledAt, null))
        coEvery { reminderScheduler.armedAlertAt(groupUuid) } returns null

        actionHandler.dismissed(slots = slots, now = testInstant(scheduledAt))
        // Armed for an alert due now, its alarm in flight: no toast naming a past time.
        coEvery { reminderScheduler.armedAlertAt(groupUuid) } returns testInstant(scheduledAt.plusMinutes(15))
        actionHandler.dismissed(slots = slots, now = testInstant(scheduledAt.plusMinutes(15)))
        actionHandler.dismissed(slots = slots, now = testInstant(scheduledAt.plusMinutes(16)))
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState(remindersEnabled = false)
        actionHandler.dismissed(slots = slots, now = testInstant(scheduledAt))

        verify(exactly = 0) { notificationManager.showNextReminderToast(any()) }
    }

    private fun medicationGroup(
        uuid: UUID,
        name: String,
        time: LocalTime,
        medicationKey: MedicationKey,
        medicationCount: Int,
        notificationsEnabled: Boolean = true,
        archivedAt: Instant? = null,
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
            notificationsEnabled = notificationsEnabled,
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z"),
            archivedAt = archivedAt,
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
