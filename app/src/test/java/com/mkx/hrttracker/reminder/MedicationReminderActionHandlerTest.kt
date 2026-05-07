package com.mkx.hrttracker.reminder

import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogEntryInput
import com.mkx.hrttracker.data.repository.MedicationLogRepository
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
    private val reminderScheduler: MedicationReminderScheduler = mockk()
    private val snoozeScheduler: MedicationReminderSnoozeScheduler = mockk()
    private val notificationManager: ReminderNotificationManager = mockk(relaxed = true)

    private lateinit var actionHandler: MedicationReminderActionHandler

    @Before
    fun setUp() {
        coEvery { reminderScheduler.rescheduleGroup(any(), any()) } just Runs
        coEvery { snoozeScheduler.clearSnoozesForSlots(any()) } just Runs

        actionHandler = MedicationReminderActionHandler(
            medicationGroupRepository = groupRepository,
            medicationLogRepository = logRepository,
            medicationReminderScheduler = reminderScheduler,
            medicationReminderSnoozeScheduler = snoozeScheduler,
            reminderNotificationManager = notificationManager,
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
