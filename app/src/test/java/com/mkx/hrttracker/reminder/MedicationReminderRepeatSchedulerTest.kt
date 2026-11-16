package com.mkx.hrttracker.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import android.os.Build
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.settings.PersistentReminderSettings
import com.mkx.hrttracker.model.settings.ReminderRepeatIntervalOption
import com.mkx.hrttracker.model.settings.ReminderRepeatLimitOption
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.AppDiagnosticsLogger
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID

class MedicationReminderRepeatSchedulerTest {
    private val context: Context = mockk()
    private val alarmManager: AlarmManager = mockk(relaxed = true)
    private val repeatStore: MedicationReminderRepeatStore = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val diagnosticsLogger: AppDiagnosticsLogger = mockk(relaxed = true)

    private val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
    private val slot = MedicationReminderSlot(
        groupUuid = UUID.fromString("6c1f8a2e-3b4d-4e5f-9a6b-7c8d9e0f1a2b"),
        scheduledAt = scheduledAt,
        scheduleTimeUuid = UUID.fromString("7d2a9b3f-4c5e-4f6a-8b7c-8d9e0f1a2b3c"),
    )

    private lateinit var scheduler: MedicationReminderRepeatScheduler

    @Before
    fun setUp() {
        mockkStatic(PendingIntent::class)
        mockkStatic(Uri::class)
        every { PendingIntent.getBroadcast(any(), any(), any(), any<Int>()) } returns mockk(relaxed = true)
        every { PendingIntent.getActivity(any(), any(), any(), any<Int>()) } returns mockk(relaxed = true)
        every { Uri.parse(any()) } returns mockk(relaxed = true)
        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        every { context.packageName } returns "com.mkx.hrttracker.test"
        every { alarmManager.canScheduleExactAlarms() } returns true
        coEvery { repeatStore.getRepeatRecords() } returns emptyList()
        coEvery { repeatStore.replaceRepeatRecords(any()) } just Runs
        givenSettings(PersistentReminderSettings(repeatEnabled = true))

        scheduler = MedicationReminderRepeatScheduler(
            context = context,
            repeatStore = repeatStore,
            settingsRepository = settingsRepository,
            diagnosticsLogger = diagnosticsLogger,
            sdkInt = Build.VERSION_CODES.S,
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun scheduleNextRepeats_stores_the_next_repeat_one_interval_ahead_and_arms_an_exact_alarm() = runTest {
        givenSettings(
            PersistentReminderSettings(
                repeatEnabled = true,
                repeatInterval = ReminderRepeatIntervalOption.FIVE_MINUTES,
            )
        )
        val stored = slot<List<MedicationReminderRepeatRecord>>()
        coEvery { repeatStore.replaceRepeatRecords(capture(stored)) } just Runs
        val now = scheduledAt.plusMinutes(1)

        val armed = scheduler.scheduleNextRepeats(listOf(slot), now)

        val expected = listOf(MedicationReminderRepeatRecord(slot, now.plusMinutes(5), repeatCount = 1))
        assertEquals(expected, armed)
        assertEquals(expected, stored.captured)
        verify(exactly = 1) { alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>()) }
        verify(exactly = 0) { alarmManager.setAlarmClock(any(), any()) }
    }

    @Test
    fun scheduleNextRepeats_uses_an_alarm_clock_for_the_full_screen_alarm() = runTest {
        givenSettings(PersistentReminderSettings(repeatEnabled = true, fullScreenAlarmEnabled = true))

        scheduler.scheduleNextRepeats(listOf(slot), scheduledAt)

        verify(exactly = 1) { alarmManager.setAlarmClock(any(), any()) }
        verify(exactly = 0) { alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>()) }
    }

    @Test
    fun scheduleNextRepeats_falls_back_to_an_inexact_alarm_without_exact_access() = runTest {
        every { alarmManager.canScheduleExactAlarms() } returns false

        scheduler.scheduleNextRepeats(listOf(slot), scheduledAt)

        verify(exactly = 1) { alarmManager.setAndAllowWhileIdle(any(), any(), any<PendingIntent>()) }
        verify(exactly = 0) { alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>()) }
    }

    @Test
    fun scheduleNextRepeats_ends_a_chain_that_used_up_its_limit() = runTest {
        givenSettings(
            PersistentReminderSettings(repeatEnabled = true, repeatLimit = ReminderRepeatLimitOption.THREE)
        )
        coEvery { repeatStore.getRepeatRecords() } returns listOf(
            MedicationReminderRepeatRecord(slot, scheduledAt.plusMinutes(30), repeatCount = 3)
        )
        val stored = slot<List<MedicationReminderRepeatRecord>>()
        coEvery { repeatStore.replaceRepeatRecords(capture(stored)) } just Runs

        val armed = scheduler.scheduleNextRepeats(listOf(slot), scheduledAt.plusMinutes(30))

        assertEquals(emptyList<MedicationReminderRepeatRecord>(), armed)
        assertEquals(emptyList<MedicationReminderRepeatRecord>(), stored.captured)
        verify(exactly = 0) { alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>()) }
    }

    @Test
    fun scheduleNextRepeats_does_nothing_but_clear_when_repeats_are_off() = runTest {
        givenSettings(PersistentReminderSettings(repeatEnabled = false))

        val armed = scheduler.scheduleNextRepeats(listOf(slot), scheduledAt)

        assertEquals(emptyList<MedicationReminderRepeatRecord>(), armed)
        verify(exactly = 0) { alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>()) }
        verify(exactly = 0) { alarmManager.setAlarmClock(any(), any()) }
    }

    @Test
    fun rescheduleAll_rearms_a_repeat_missed_while_off_to_fire_now() = runTest {
        val now = scheduledAt.plusHours(1)
        coEvery { repeatStore.getRepeatRecords() } returns listOf(
            MedicationReminderRepeatRecord(slot, scheduledAt.plusMinutes(10), repeatCount = 1)
        )
        val stored = slot<List<MedicationReminderRepeatRecord>>()
        coEvery { repeatStore.replaceRepeatRecords(capture(stored)) } just Runs

        scheduler.rescheduleAll(now)

        assertEquals(listOf(MedicationReminderRepeatRecord(slot, now, repeatCount = 1)), stored.captured)
        verify(exactly = 1) { alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>()) }
    }

    @Test
    fun rescheduleAll_clears_every_chain_when_reminders_are_off() = runTest {
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState(
            remindersEnabled = false,
            persistentReminders = PersistentReminderSettings(repeatEnabled = true),
        )
        coEvery { repeatStore.getRepeatRecords() } returns listOf(
            MedicationReminderRepeatRecord(slot, scheduledAt.plusMinutes(10), repeatCount = 1)
        )

        scheduler.rescheduleAll(scheduledAt)

        coVerify { repeatStore.replaceRepeatRecords(emptyList()) }
        verify(exactly = 1) { alarmManager.cancel(any<PendingIntent>()) }
        verify(exactly = 0) { alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>()) }
    }

    @Test
    fun buildNextRepeatRecords_counts_up_and_keeps_going_without_a_limit() {
        val existing = listOf(MedicationReminderRepeatRecord(slot, scheduledAt, repeatCount = 41))

        val next = buildNextRepeatRecords(
            slots = listOf(slot, slot),
            existingRecords = existing,
            repeatAt = scheduledAt.plusMinutes(1),
            maxRepeats = null,
        )

        assertEquals(listOf(MedicationReminderRepeatRecord(slot, scheduledAt.plusMinutes(1), 42)), next)
    }

    @Test
    fun repeat_record_storage_round_trips() {
        val record = MedicationReminderRepeatRecord(slot, scheduledAt.plusMinutes(20), repeatCount = 2)

        assertEquals(record, repeatRecordFromStorageValue(record.toStorageValue()))
        assertNull(repeatRecordFromStorageValue("not a record"))
    }

    @Test
    fun doseReminderAlarm_is_requested_only_with_the_full_screen_alarm_on_for_an_included_plan() {
        assertNull(PersistentReminderSettings().doseReminderAlarm(listOf(slot)))
        assertEquals(
            DoseReminderAlarm(playsSound = true),
            PersistentReminderSettings(fullScreenAlarmEnabled = true).doseReminderAlarm(listOf(slot)),
        )
        assertNull(
            PersistentReminderSettings(
                fullScreenAlarmEnabled = true,
                excludedGroupUuids = setOf(slot.groupUuid),
            ).doseReminderAlarm(listOf(slot))
        )
    }

    @Test
    fun scheduleNextRepeats_leaves_out_a_plan_turned_off_for_persistent_reminders() = runTest {
        givenSettings(
            PersistentReminderSettings(repeatEnabled = true, excludedGroupUuids = setOf(slot.groupUuid))
        )

        val armed = scheduler.scheduleNextRepeats(listOf(slot), scheduledAt)

        assertEquals(emptyList<MedicationReminderRepeatRecord>(), armed)
        verify(exactly = 0) { alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>()) }
    }

    @Test
    fun clearRepeatsForGroups_ends_only_that_plans_chains() = runTest {
        val otherSlot = slot.copy(groupUuid = UUID.fromString("8e3b0c4a-5d6f-4a7b-9c8d-0e1f2a3b4c5d"))
        coEvery { repeatStore.getRepeatRecords() } returns listOf(
            MedicationReminderRepeatRecord(slot, scheduledAt.plusMinutes(10), repeatCount = 1),
            MedicationReminderRepeatRecord(otherSlot, scheduledAt.plusMinutes(20), repeatCount = 1),
        )
        val stored = slot<List<MedicationReminderRepeatRecord>>()
        coEvery { repeatStore.replaceRepeatRecords(capture(stored)) } just Runs

        scheduler.clearRepeatsForGroups(setOf(slot.groupUuid))

        assertEquals(listOf(otherSlot), stored.captured.map(MedicationReminderRepeatRecord::slot))
    }

    private fun givenSettings(persistent: PersistentReminderSettings) {
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState(
            remindersEnabled = true,
            persistentReminders = persistent,
        )
    }
}
