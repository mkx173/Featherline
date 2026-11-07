package com.mkx.hrttracker.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import android.os.Build
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleTime
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.util.MedicationDayBoundary
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import java.util.UUID

class MedicationReminderSnoozeSchedulerTest {
    private val context: Context = mockk()
    private val alarmManager: AlarmManager = mockk(relaxed = true)
    private val snoozeStore: MedicationReminderSnoozeStore = mockk()
    private val groupRepository: MedicationGroupRepository = mockk()
    private val logRepository: MedicationLogRepository = mockk()
    private val originalTimeZone: TimeZone = TimeZone.getDefault()

    /** Each armed alarm's trigger time, by the intent data its PendingIntent is keyed by. */
    private val armedAlarms = mutableMapOf<String, Long>()
    private val pendingIntents = mutableMapOf<String, PendingIntent>()

    /** The store's records as it persists them. */
    private var storedValues: Set<String> = emptySet()

    private lateinit var scheduler: MedicationReminderSnoozeScheduler

    @Before
    fun setUp() {
        mockkStatic(PendingIntent::class)
        mockkStatic(Uri::class)
        var lastIntentData = ""
        every { Uri.parse(any()) } answers {
            lastIntentData = firstArg()
            mockk(relaxed = true)
        }
        every {
            PendingIntent.getBroadcast(any(), any(), any(), any<Int>())
        } answers { pendingIntents.getOrPut(lastIntentData) { mockk(relaxed = true) } }
        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        every { context.packageName } returns "com.mkx.hrttracker.test"
        every { alarmManager.canScheduleExactAlarms() } returns true
        every {
            alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>())
        } answers { armedAlarms[intentDataOf(thirdArg())] = secondArg() }
        every {
            alarmManager.setAndAllowWhileIdle(any(), any(), any<PendingIntent>())
        } answers { armedAlarms[intentDataOf(thirdArg())] = secondArg() }
        every { alarmManager.cancel(any<PendingIntent>()) } answers {
            armedAlarms.remove(intentDataOf(firstArg()))
        }
        coEvery { snoozeStore.getSnoozeRecords() } answers {
            storedValues.mapNotNull(::snoozeRecordFromStorageValue)
        }
        coEvery { snoozeStore.replaceSnoozeRecords(any()) } answers {
            storedValues = firstArg<List<MedicationReminderSnoozeRecord>>()
                .mapTo(mutableSetOf()) { record -> record.toStorageValue() }
        }
        coEvery { groupRepository.getGroup(any()) } returns null
        coEvery { logRepository.getScheduledGroupEntriesSince(any()) } returns emptyList()

        scheduler = MedicationReminderSnoozeScheduler(
            context = context,
            snoozeStore = snoozeStore,
            medicationGroupRepository = groupRepository,
            medicationLogRepository = logRepository,
            sdkInt = Build.VERSION_CODES.S,
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
        TimeZone.setDefault(originalTimeZone)
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
            now = testInstant(LocalDateTime.of(2026, 4, 20, 9, 0)),
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
            now = testInstant(LocalDateTime.of(2026, 4, 20, 9, 0)),
        )

        verify(exactly = 0) {
            alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>())
        }
        verify(exactly = 1) {
            alarmManager.setAndAllowWhileIdle(any(), any(), any<PendingIntent>())
        }
    }

    @Test
    fun snoozeSlots_fallsBackToInexactAlarmWhenExactAlarmAccessIsRevokedDuringScheduling() =
        runTest {
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
                now = testInstant(LocalDateTime.of(2026, 4, 20, 9, 0)),
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
        // Fired yesterday, and today's 09:00 dose has come since: the record outlived its dose.
        val expiredGroup = dailyGroup(UUID.fromString("a1b2c3d4-0000-0000-0000-000000000001"))
        val expiredRecord = expiredGroup.snoozeRecord(
            scheduledAt = LocalDateTime.of(2026, 4, 19, 9, 0),
            snoozeAt = LocalDateTime.of(2026, 4, 19, 9, 15),
            fired = true,
        )
        val futureGroup = dailyGroup(UUID.fromString("a1b2c3d4-0000-0000-0000-000000000002"))
        val futureRecord = futureGroup.snoozeRecord(
            scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0),
            snoozeAt = LocalDateTime.of(2026, 4, 20, 9, 45),
        )
        coEvery { groupRepository.getGroup(expiredGroup.uuid) } returns expiredGroup
        coEvery { groupRepository.getGroup(futureGroup.uuid) } returns futureGroup
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

    @Test
    fun clearStaleSnoozesForGroup_removes_records_for_doses_the_schedule_no_longer_has() = runTest {
        // A Monday 01:00 dose, once the plan's day ends at 04:00, is on calendar
        // Tuesday: Monday's snooze has the same slot and time but no dose behind it.
        val groupUuid = UUID.fromString("6d1f0a9e-3c2b-4e8d-9a7f-1b5c4d3e2f10")
        val scheduleTimeUuid = UUID.fromString("a4c3b2d1-7e6f-4a5b-8c9d-0e1f2a3b4c5d")
        val staleRecord = snoozeRecord(
            groupUuid = groupUuid,
            scheduleTimeUuid = scheduleTimeUuid,
            scheduledAt = LocalDateTime.of(2026, 4, 20, 1, 0),
            snoozeAt = LocalDateTime.of(2026, 4, 20, 1, 15),
        )
        val currentRecord = snoozeRecord(
            groupUuid = groupUuid,
            scheduleTimeUuid = scheduleTimeUuid,
            scheduledAt = LocalDateTime.of(2026, 4, 21, 1, 0),
            snoozeAt = LocalDateTime.of(2026, 4, 21, 1, 15),
        )
        val storedRecords = slot<List<MedicationReminderSnoozeRecord>>()
        coEvery { snoozeStore.getSnoozeRecords() } returns listOf(staleRecord, currentRecord)
        coEvery { snoozeStore.replaceSnoozeRecords(capture(storedRecords)) } just Runs

        scheduler.clearStaleSnoozesForGroup(
            MedicationGroup(
                uuid = groupUuid,
                name = "Group",
                schedule = MedicationGroupSchedule(
                    type = MedicationGroupScheduleType.WEEKLY,
                    interval = 1,
                    since = LocalDate.of(2026, 4, 1),
                    weeklyDaysOfWeek = setOf(DayOfWeek.MONDAY),
                    times = listOf(LocalTime.of(1, 0)),
                    timeSlots = listOf(
                        MedicationGroupScheduleTime(
                            uuid = scheduleTimeUuid,
                            time = LocalTime.of(1, 0),
                            effectiveFrom = LocalDateTime.of(2026, 4, 1, 0, 0),
                        ),
                    ),
                    dayBoundary = MedicationDayBoundary.FourAm,
                ),
                medications = emptyList(),
                createdAt = Instant.parse("2026-04-01T00:00:00Z"),
                updatedAt = Instant.parse("2026-04-20T00:00:00Z"),
            )
        )

        assertEquals(listOf(currentRecord), storedRecords.captured)
    }

    @Test
    fun clearStaleSnoozesForGroup_removes_records_before_the_slots_start() = runTest {
        // Today's 08:00 dose, once the plan's start moved to 10:00 today.
        val groupUuid = UUID.fromString("7e2a1b0f-4d3c-4f9e-8b1a-2c6d5e4f3a21")
        val scheduleTimeUuid = UUID.fromString("b5d4c3e2-8f7a-4b6c-9d0e-1f2a3b4c5d6e")
        val staleRecord = snoozeRecord(
            groupUuid = groupUuid,
            scheduleTimeUuid = scheduleTimeUuid,
            scheduledAt = LocalDateTime.of(2026, 4, 20, 8, 0),
            snoozeAt = LocalDateTime.of(2026, 4, 20, 8, 15),
        )
        val currentRecord = snoozeRecord(
            groupUuid = groupUuid,
            scheduleTimeUuid = scheduleTimeUuid,
            scheduledAt = LocalDateTime.of(2026, 4, 21, 8, 0),
            snoozeAt = LocalDateTime.of(2026, 4, 21, 8, 15),
        )
        val storedRecords = slot<List<MedicationReminderSnoozeRecord>>()
        coEvery { snoozeStore.getSnoozeRecords() } returns listOf(staleRecord, currentRecord)
        coEvery { snoozeStore.replaceSnoozeRecords(capture(storedRecords)) } just Runs

        scheduler.clearStaleSnoozesForGroup(
            MedicationGroup(
                uuid = groupUuid,
                name = "Group",
                schedule = MedicationGroupSchedule(
                    type = MedicationGroupScheduleType.DAILY,
                    interval = 1,
                    since = LocalDate.of(2026, 4, 20),
                    weeklyDaysOfWeek = emptySet(),
                    times = listOf(LocalTime.of(8, 0)),
                    timeSlots = listOf(
                        MedicationGroupScheduleTime(
                            uuid = scheduleTimeUuid,
                            time = LocalTime.of(8, 0),
                            effectiveFrom = LocalDateTime.of(2026, 4, 20, 10, 0),
                        ),
                    ),
                ),
                medications = emptyList(),
                createdAt = Instant.parse("2026-04-01T00:00:00Z"),
                updatedAt = Instant.parse("2026-04-20T00:00:00Z"),
            )
        )

        assertEquals(listOf(currentRecord), storedRecords.captured)
    }

    @Test
    fun rescheduleAll_rearms_an_unfired_snooze_whose_time_passed_while_its_dose_is_due() = runTest {
        // Remind later at 08:02 armed 08:17, and the phone was off from 08:10 to 10:00.
        val now = LocalDateTime.of(2026, 4, 20, 10, 0)
        val dueGroup = dailyGroup(UUID.fromString("0b3e6f1a-2c4d-4e5f-8a9b-0c1d2e3f4a5b"), LocalTime.of(8, 0))
        val due = dueGroup.snoozeRecord(
            scheduledAt = LocalDateTime.of(2026, 4, 20, 8, 0),
            snoozeAt = LocalDateTime.of(2026, 4, 20, 8, 17),
        )
        // Logged in the app since: nothing left to remind.
        val loggedGroup = dailyGroup(UUID.fromString("1c4f7a2b-3d5e-4f6a-9b0c-1d2e3f4a5b6c"), LocalTime.of(8, 0))
        val logged = loggedGroup.snoozeRecord(
            scheduledAt = LocalDateTime.of(2026, 4, 20, 8, 0),
            snoozeAt = LocalDateTime.of(2026, 4, 20, 8, 17),
        )
        // Yesterday's, off for a day: today's 08:00 dose has come since.
        val dayOldGroup = dailyGroup(UUID.fromString("2d5a8b3c-4e6f-4a7b-8c1d-2e3f4a5b6c7d"), LocalTime.of(8, 0))
        val dayOld = dayOldGroup.snoozeRecord(
            scheduledAt = LocalDateTime.of(2026, 4, 19, 8, 0),
            snoozeAt = LocalDateTime.of(2026, 4, 19, 8, 17),
        )
        storeRecords(due, logged, dayOld)
        listOf(dueGroup, loggedGroup, dayOldGroup).forEach { group ->
            coEvery { groupRepository.getGroup(group.uuid) } returns group
        }
        coEvery { logRepository.getScheduledGroupEntriesSince(any()) } returns listOf(
            loggedGroup.scheduledEntry(LocalDateTime.of(2026, 4, 20, 8, 0))
        )

        scheduler.rescheduleAll(now = now)

        assertEquals(listOf(due.slot), storedRecords().map(MedicationReminderSnoozeRecord::slot))
        // Armed at its own time, past, so it fires at once.
        assertEquals(
            listOf(testInstant(LocalDateTime.of(2026, 4, 20, 8, 17)).toEpochMilli()),
            armedAlarms.values.toList(),
        )
    }

    @Test
    fun rescheduleAll_keeps_a_fired_snooze_and_its_count_while_its_dose_is_due() = runTest {
        // The fourth snooze fired at 09:15, the other plan's second too. A pass at 09:30
        // must not give the first four more.
        val cappedGroup = dailyGroup(UUID.fromString("3e6b9c4d-5f7a-4b8c-9d2e-3f4a5b6c7d8e"))
        val capped = cappedGroup.snoozeRecord(
            scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0),
            snoozeAt = LocalDateTime.of(2026, 4, 20, 9, 15),
            snoozeCount = MAX_REMINDER_SNOOZE_COUNT,
            fired = true,
        )
        val otherGroup = dailyGroup(UUID.fromString("4f7c0d5e-6a8b-4c9d-8e3f-4a5b6c7d8e9f"))
        val other = otherGroup.snoozeRecord(
            scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0),
            snoozeAt = LocalDateTime.of(2026, 4, 20, 9, 15),
            snoozeCount = 2,
            fired = true,
        )
        storeRecords(capped, other)
        coEvery { groupRepository.getGroup(cappedGroup.uuid) } returns cappedGroup
        coEvery { groupRepository.getGroup(otherGroup.uuid) } returns otherGroup

        scheduler.rescheduleAll(now = LocalDateTime.of(2026, 4, 20, 9, 30))

        assertEquals(emptyMap<String, Long>(), armedAlarms)
        val snoozed = scheduler.snoozeSlots(
            slots = listOf(capped.slot, other.slot),
            now = testInstant(LocalDateTime.of(2026, 4, 20, 9, 31)),
        )
        assertEquals(
            listOf(other.slot to 3),
            snoozed.map { record -> record.slot to record.snoozeCount },
        )
        assertEquals(
            emptyList<MedicationReminderSnoozeRecord>(),
            scheduler.snoozeSlots(
                slots = listOf(capped.slot),
                now = testInstant(LocalDateTime.of(2026, 4, 20, 9, 32)),
            ),
        )
    }

    @Test
    fun a_snooze_fires_at_the_same_instant_after_a_zone_change() = runTest {
        val tokyo = ZoneId.of("Asia/Tokyo")
        TimeZone.setDefault(TimeZone.getTimeZone(tokyo))
        val group = dailyGroup(UUID.fromString("5a8d1e6f-7b9c-4dae-9f4a-5b6c7d8e9fa0"))
        coEvery { groupRepository.getGroup(group.uuid) } returns group
        val slot = group.reminderSlot(LocalDateTime.of(2026, 4, 20, 9, 0))

        scheduler.snoozeSlots(
            slots = listOf(slot),
            now = ZonedDateTime.of(2026, 4, 20, 9, 2, 0, 0, tokyo).toInstant(),
        )
        val armedInTokyo = armedAlarms.values.single()
        // Landed in Los Angeles: the zone change reschedules every snooze.
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        scheduler.rescheduleAll(now = LocalDateTime.of(2026, 4, 19, 17, 10))

        assertEquals(
            ZonedDateTime.of(2026, 4, 20, 9, 17, 0, 0, tokyo).toInstant().toEpochMilli(),
            armedInTokyo,
        )
        assertEquals(listOf(armedInTokyo), armedAlarms.values.toList())
    }

    @Test
    fun a_snooze_set_before_the_clocks_fall_back_fires_fifteen_real_minutes_later() = runTest {
        val newYork = ZoneId.of("America/New_York")
        TimeZone.setDefault(TimeZone.getTimeZone(newYork))
        val slot = dailyGroup(UUID.fromString("6b9e2f7a-8cad-4ebf-8a5b-6c7d8e9fa0b1"), LocalTime.of(1, 0))
            .reminderSlot(LocalDateTime.of(2026, 11, 1, 1, 0))
        // 01:50 EDT. The clocks fall back at 02:00, so 15 minutes on is 01:05 EST.
        val tappedAt = ZonedDateTime.of(2026, 11, 1, 1, 50, 0, 0, newYork)

        scheduler.snoozeSlots(slots = listOf(slot), now = tappedAt.toInstant())

        assertEquals(
            listOf(tappedAt.toInstant().plusSeconds(REMINDER_SNOOZE_MINUTES * 60).toEpochMilli()),
            armedAlarms.values.toList(),
        )
    }

    @Test
    fun a_repeat_remind_later_leaves_one_armed_alarm_and_counts_each_tap_once() = runTest {
        val slot = dailyGroup(UUID.fromString("7c0f3a8b-9dbe-4fc0-9b6c-7d8e9fa0b1c2"))
            .reminderSlot(LocalDateTime.of(2026, 4, 20, 9, 0))

        scheduler.snoozeSlots(slots = listOf(slot), now = testInstant(LocalDateTime.of(2026, 4, 20, 9, 2)))
        // The same reminder's Remind later, delivered again.
        scheduler.snoozeSlots(slots = listOf(slot), now = testInstant(LocalDateTime.of(2026, 4, 20, 9, 3)))

        assertEquals(
            listOf(testInstant(LocalDateTime.of(2026, 4, 20, 9, 18)).toEpochMilli()),
            armedAlarms.values.toList(),
        )
        assertEquals(listOf(2), storedRecords().map(MedicationReminderSnoozeRecord::snoozeCount))
    }

    @Test
    fun claimSnoozeDelivery_takes_only_the_alarm_the_record_is_armed_for_and_only_once() = runTest {
        val group = dailyGroup(UUID.fromString("8d1a4b9c-aecf-4ad1-8c7d-8e9fa0b1c2d3"))
        coEvery { groupRepository.getGroup(group.uuid) } returns group
        val slot = group.reminderSlot(LocalDateTime.of(2026, 4, 20, 9, 0))
        val first = scheduler.snoozeSlots(
            slots = listOf(slot),
            now = testInstant(LocalDateTime.of(2026, 4, 20, 9, 2)),
        ).single()
        val second = scheduler.snoozeSlots(
            slots = listOf(slot),
            now = testInstant(LocalDateTime.of(2026, 4, 20, 9, 3)),
        ).single()

        // The first alarm, had it stayed armed, is stale; the second comes once.
        assertEquals(
            emptyList<MedicationReminderSlot>(),
            scheduler.claimSnoozeDelivery(listOf(slot), first.snoozeAt),
        )
        assertEquals(listOf(slot), scheduler.claimSnoozeDelivery(listOf(slot), second.snoozeAt))
        assertEquals(
            emptyList<MedicationReminderSlot>(),
            scheduler.claimSnoozeDelivery(listOf(slot), second.snoozeAt),
        )
        // Delivered, so a later pass doesn't arm it again.
        scheduler.rescheduleAll(now = LocalDateTime.of(2026, 4, 20, 9, 30))
        assertEquals(emptyMap<String, Long>(), armedAlarms)
    }

    @Test
    fun rescheduleAll_drops_a_skipped_doses_snooze_once_its_next_dose_over_90_days_on_has_come() = runTest {
        // Every 91 days: the April 1 dose was snoozed, never logged, and July 1's has come.
        val group = dailyGroup(UUID.fromString("9e2b5c0d-bfd0-4be2-9d8e-9fa0b1c2d3e4")).let { group ->
            group.copy(schedule = group.schedule.copy(interval = 91))
        }
        coEvery { groupRepository.getGroup(group.uuid) } returns group
        storeRecords(
            group.snoozeRecord(
                scheduledAt = LocalDateTime.of(2026, 4, 1, 9, 0),
                snoozeAt = LocalDateTime.of(2026, 4, 1, 9, 15),
                fired = true,
            )
        )

        scheduler.rescheduleAll(now = LocalDateTime.of(2026, 7, 1, 10, 0))

        assertEquals(emptyList<MedicationReminderSnoozeRecord>(), storedRecords())
    }

    @Test
    fun a_remind_later_during_a_snooze_pass_keeps_its_record_and_alarm() = runTest {
        // The pass reads its plans while Remind later on another reminder writes a record.
        val now = LocalDateTime.of(2026, 4, 20, 9, 30)
        val expiredGroup = dailyGroup(UUID.fromString("af3c6d1e-c0e1-4cf3-8e9f-a0b1c2d3e4f5"))
        storeRecords(
            expiredGroup.snoozeRecord(
                scheduledAt = LocalDateTime.of(2026, 4, 19, 9, 0),
                snoozeAt = LocalDateTime.of(2026, 4, 19, 9, 15),
                fired = true,
            )
        )
        val readingPlans = CompletableDeferred<Unit>()
        val plansRead = CompletableDeferred<Unit>()
        coEvery { groupRepository.getGroup(expiredGroup.uuid) } coAnswers {
            readingPlans.complete(Unit)
            plansRead.await()
            expiredGroup
        }
        val snoozedSlot = dailyGroup(UUID.fromString("b04d7e2f-d1f2-4d04-9fa0-b1c2d3e4f5a6"))
            .reminderSlot(LocalDateTime.of(2026, 4, 20, 9, 0))

        val pass = launch { scheduler.rescheduleAll(now = now) }
        readingPlans.await()
        val remindLater = launch { scheduler.snoozeSlots(listOf(snoozedSlot), now = testInstant(now)) }
        runCurrent()
        plansRead.complete(Unit)
        pass.join()
        remindLater.join()

        assertEquals(listOf(snoozedSlot), storedRecords().map(MedicationReminderSnoozeRecord::slot))
        assertEquals(
            listOf(testInstant(now.plusMinutes(REMINDER_SNOOZE_MINUTES)).toEpochMilli()),
            armedAlarms.values.toList(),
        )
    }

    @Test
    fun rescheduleAll_leaves_every_alarm_armed_when_the_store_write_fails() = runTest {
        val now = LocalDateTime.of(2026, 4, 20, 9, 30)
        val dueGroup = dailyGroup(UUID.fromString("c15e8f3a-e2a3-4e15-8ab1-c2d3e4f5a6b7"))
        val due = dueGroup.snoozeRecord(
            scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0),
            snoozeAt = LocalDateTime.of(2026, 4, 20, 9, 45),
        )
        // Yesterday's, outlived by today's dose: the pass drops it, so it writes.
        val expiredGroup = dailyGroup(UUID.fromString("d26f9a4b-f3b4-4f26-9bc2-d3e4f5a6b7c8"))
        val expired = expiredGroup.snoozeRecord(
            scheduledAt = LocalDateTime.of(2026, 4, 19, 9, 0),
            snoozeAt = LocalDateTime.of(2026, 4, 19, 9, 15),
            fired = true,
        )
        listOf(dueGroup, expiredGroup).forEach { group ->
            coEvery { groupRepository.getGroup(group.uuid) } returns group
        }
        storeRecords(due)
        scheduler.rescheduleAll(now = now)
        val armedBefore = armedAlarms.toMap()
        storeRecords(due, expired)
        coEvery { snoozeStore.replaceSnoozeRecords(any()) } throws java.io.IOException("disk full")

        runCatching { scheduler.rescheduleAll(now = now) }

        assertEquals(listOf(testInstant(LocalDateTime.of(2026, 4, 20, 9, 45)).toEpochMilli()), armedBefore.values.toList())
        assertEquals(armedBefore, armedAlarms)
    }

    private fun intentDataOf(pendingIntent: PendingIntent): String =
        pendingIntents.entries.first { (_, value) -> value === pendingIntent }.key

    private fun storeRecords(vararg records: MedicationReminderSnoozeRecord) {
        storedValues = records.mapTo(mutableSetOf()) { record -> record.toStorageValue() }
    }

    private fun storedRecords(): List<MedicationReminderSnoozeRecord> =
        storedValues.mapNotNull(::snoozeRecordFromStorageValue)

    private fun dailyGroup(uuid: UUID, time: LocalTime = LocalTime.of(9, 0)): MedicationGroup {
        return MedicationGroup(
            uuid = uuid,
            name = "Group",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(time),
            ),
            medications = listOf(testMedicationGroupMedication()),
            notificationsEnabled = true,
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z"),
        )
    }

    private fun MedicationGroup.reminderSlot(scheduledAt: LocalDateTime) = MedicationReminderSlot(
        groupUuid = uuid,
        scheduledAt = scheduledAt,
        scheduleTimeUuid = schedule.timeSlots.single().uuid,
    )

    private fun MedicationGroup.snoozeRecord(
        scheduledAt: LocalDateTime,
        snoozeAt: LocalDateTime,
        snoozeCount: Int = 1,
        fired: Boolean = false,
    ) = MedicationReminderSnoozeRecord(
        slot = reminderSlot(scheduledAt),
        snoozeAt = testInstant(snoozeAt),
        snoozeCount = snoozeCount,
        fired = fired,
    )

    private fun MedicationGroup.scheduledEntry(scheduledFor: LocalDateTime) =
        medications.single().let { medication ->
            testMedicationLogEntry(
                medicine = medication.medicine,
                applicationType = medication.applicationType,
                doseInstruction = medication.doseInstruction,
                sourceGroupUuid = uuid,
                appliedAt = testInstant(scheduledFor.plusMinutes(30)),
                scheduledFor = scheduledFor,
            )
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
            snoozeAt = testInstant(snoozeAt),
            snoozeCount = 1,
        )
    }
}
