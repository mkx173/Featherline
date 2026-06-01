package com.mkx.hrttracker.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import android.os.Build
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleTime
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

class MedicationReminderSnoozeSchedulerTest {
    private val context: Context = mockk()
    private val alarmManager: AlarmManager = mockk(relaxed = true)
    private val snoozeStore: MedicationReminderSnoozeStore = mockk()

    private lateinit var scheduler: MedicationReminderSnoozeScheduler

    @Before
    fun setUp() {
        mockkStatic(PendingIntent::class)
        mockkStatic(Uri::class)
        every {
            PendingIntent.getBroadcast(any(), any(), any(), any<Int>())
        } returns mockk(relaxed = true)
        every { Uri.parse(any()) } returns mockk(relaxed = true)
        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        every { context.packageName } returns "com.mkx.hrttracker.test"
        every { alarmManager.canScheduleExactAlarms() } returns true
        coEvery { snoozeStore.replaceSnoozeRecords(any()) } just Runs

        scheduler = MedicationReminderSnoozeScheduler(
            context = context,
            snoozeStore = snoozeStore,
            sdkInt = Build.VERSION_CODES.S,
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun snoozeSlots_schedulesExactAlarmWhenExactAlarmAccessIsAvailable() = runTest {
        val slot = snoozeRecord(
            groupUuid = UUID.fromString("1377bcf2-bd79-47d6-8d2d-979cbfd406ea"),
            scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0),
            snoozeAt = LocalDateTime.of(2026, 4, 20, 9, 15),
        ).slot
        coEvery { snoozeStore.getSnoozeRecords() } returns emptyList()

        scheduler.snoozeSlots(
            slots = listOf(slot),
            now = LocalDateTime.of(2026, 4, 20, 9, 0),
        )

        verify(exactly = 1) {
            alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>())
        }
        verify(exactly = 0) {
            alarmManager.setAndAllowWhileIdle(any(), any(), any<PendingIntent>())
        }
    }

    @Test
    fun snoozeSlots_fallsBackToInexactAlarmWhenExactAlarmAccessIsMissing() = runTest {
        every { alarmManager.canScheduleExactAlarms() } returns false
        val slot = snoozeRecord(
            groupUuid = UUID.fromString("debbf44c-08ed-409e-9d4a-df3dd834c5ba"),
            scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0),
            snoozeAt = LocalDateTime.of(2026, 4, 20, 9, 15),
        ).slot
        coEvery { snoozeStore.getSnoozeRecords() } returns emptyList()

        scheduler.snoozeSlots(
            slots = listOf(slot),
            now = LocalDateTime.of(2026, 4, 20, 9, 0),
        )

        verify(exactly = 0) {
            alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>())
        }
        verify(exactly = 1) {
            alarmManager.setAndAllowWhileIdle(any(), any(), any<PendingIntent>())
        }
    }

    @Test
    fun snoozeSlots_fallsBackToInexactAlarmWhenExactAlarmAccessIsRevokedDuringScheduling() = runTest {
        every {
            alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>())
        } throws SecurityException("revoked")
        val slot = snoozeRecord(
            groupUuid = UUID.fromString("9de5ac6c-e704-4487-b22e-6ea726c84042"),
            scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0),
            snoozeAt = LocalDateTime.of(2026, 4, 20, 9, 15),
        ).slot
        coEvery { snoozeStore.getSnoozeRecords() } returns emptyList()

        scheduler.snoozeSlots(
            slots = listOf(slot),
            now = LocalDateTime.of(2026, 4, 20, 9, 0),
        )

        verify(exactly = 1) {
            alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>())
        }
        verify(exactly = 1) {
            alarmManager.setAndAllowWhileIdle(any(), any(), any<PendingIntent>())
        }
    }

    @Test
    fun rescheduleAll_dropsExpiredRecordsAndDoesNotScheduleAlarmForThem() = runTest {
        val now = LocalDateTime.of(2026, 4, 20, 9, 30)
        val expiredRecord = snoozeRecord(
            groupUuid = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000001"),
            scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0),
            snoozeAt = LocalDateTime.of(2026, 4, 20, 9, 15),
        )
        val futureRecord = snoozeRecord(
            groupUuid = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000002"),
            scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0),
            snoozeAt = LocalDateTime.of(2026, 4, 20, 9, 45),
        )
        val storedRecords = slot<List<MedicationReminderSnoozeRecord>>()
        coEvery { snoozeStore.getSnoozeRecords() } returns listOf(expiredRecord, futureRecord)
        coEvery { snoozeStore.replaceSnoozeRecords(capture(storedRecords)) } just Runs

        scheduler.rescheduleAll(now = now)

        assertEquals(listOf(futureRecord), storedRecords.captured)
        verify(exactly = 1) {
            alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>())
        }
        verify(exactly = 0) {
            alarmManager.setAndAllowWhileIdle(any(), any(), any<PendingIntent>())
        }
    }

    @Test
    fun clearAllSnoozes_cancelsStoredSnoozeAlarmsAndClearsRecords() = runTest {
        val snoozeAt = LocalDateTime.of(2026, 4, 20, 9, 15)
        val records = listOf(
            snoozeRecord(
                groupUuid = UUID.fromString("5caf4bbb-7093-4b19-969c-9e42fa97bd9d"),
                scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0),
                snoozeAt = snoozeAt,
            ),
            snoozeRecord(
                groupUuid = UUID.fromString("8f742831-ee3c-40dc-b0f3-82fab5805312"),
                scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0),
                snoozeAt = snoozeAt,
            ),
            snoozeRecord(
                groupUuid = UUID.fromString("0724f699-c0bb-4d19-8451-35bbbe32f8d0"),
                scheduledAt = LocalDateTime.of(2026, 4, 20, 10, 0),
                snoozeAt = LocalDateTime.of(2026, 4, 20, 10, 15),
            ),
        )
        coEvery { snoozeStore.getSnoozeRecords() } returns records

        scheduler.clearAllSnoozes()

        verify(exactly = 2) { alarmManager.cancel(any<PendingIntent>()) }
        coVerify(exactly = 1) { snoozeStore.replaceSnoozeRecords(emptyList()) }
    }

    @Test
    fun clearStaleSnoozesForGroup_removes_records_for_changed_schedule_time_slots() = runTest {
        val groupUuid = UUID.fromString("2b233a8f-1d50-4664-a810-8f117b05a5fd")
        val changedScheduleTimeUuid = UUID.fromString("17f70e3e-5698-487c-9206-63c4d2d3992b")
        val unchangedScheduleTimeUuid = UUID.fromString("17e9d170-9824-450a-aa9a-e608615e0121")
        val otherGroupUuid = UUID.fromString("94733f54-a441-457d-93f0-3fd8f733f223")
        val sharedSnoozeAt = LocalDateTime.of(2026, 4, 20, 9, 15)
        val changedRecord = snoozeRecord(
            groupUuid = groupUuid,
            scheduleTimeUuid = changedScheduleTimeUuid,
            scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0),
            snoozeAt = sharedSnoozeAt,
        )
        val unchangedRecord = snoozeRecord(
            groupUuid = groupUuid,
            scheduleTimeUuid = unchangedScheduleTimeUuid,
            scheduledAt = LocalDateTime.of(2026, 4, 20, 21, 0),
            snoozeAt = sharedSnoozeAt,
        )
        val otherGroupRecord = snoozeRecord(
            groupUuid = otherGroupUuid,
            scheduleTimeUuid = UUID.fromString("0296c27f-5f6c-40c4-b104-162d070b68e4"),
            scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0),
            snoozeAt = LocalDateTime.of(2026, 4, 20, 9, 30),
        )
        val storedRecords = slot<List<MedicationReminderSnoozeRecord>>()
        coEvery { snoozeStore.getSnoozeRecords() } returns listOf(
            changedRecord,
            unchangedRecord,
            otherGroupRecord,
        )
        coEvery { snoozeStore.replaceSnoozeRecords(capture(storedRecords)) } just Runs

        scheduler.clearStaleSnoozesForGroup(
            MedicationGroup(
                uuid = groupUuid,
                name = "Group",
                schedule = MedicationGroupSchedule(
                    type = MedicationGroupScheduleType.DAILY,
                    interval = 1,
                    since = LocalDate.of(2026, 4, 1),
                    weeklyDaysOfWeek = emptySet(),
                    times = listOf(LocalTime.of(10, 0), LocalTime.of(21, 0)),
                    timeSlots = listOf(
                        MedicationGroupScheduleTime(
                            uuid = changedScheduleTimeUuid,
                            time = LocalTime.of(10, 0),
                            effectiveFrom = LocalDateTime.of(2026, 4, 20, 9, 5),
                        ),
                        MedicationGroupScheduleTime(
                            uuid = unchangedScheduleTimeUuid,
                            time = LocalTime.of(21, 0),
                            effectiveFrom = LocalDateTime.of(2026, 4, 1, 0, 0),
                        ),
                    ),
                ),
                medications = emptyList(),
                createdAt = Instant.parse("2026-04-01T00:00:00Z"),
                updatedAt = Instant.parse("2026-04-20T00:00:00Z"),
            )
        )

        assertEquals(listOf(unchangedRecord, otherGroupRecord), storedRecords.captured)
        verify(exactly = 1) { alarmManager.cancel(any<PendingIntent>()) }
        verify(exactly = 1) {
            alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>())
        }
    }

    private fun snoozeRecord(
        groupUuid: UUID,
        scheduledAt: LocalDateTime,
        snoozeAt: LocalDateTime,
        scheduleTimeUuid: UUID = UUID.nameUUIDFromBytes(groupUuid.toString().toByteArray()),
    ): MedicationReminderSnoozeRecord {
        return MedicationReminderSnoozeRecord(
            slot = MedicationReminderSlot(
                groupUuid = groupUuid,
                scheduledAt = scheduledAt,
                scheduleTimeUuid = scheduleTimeUuid,
            ),
            snoozeAt = snoozeAt,
            snoozeCount = 1,
        )
    }
}
