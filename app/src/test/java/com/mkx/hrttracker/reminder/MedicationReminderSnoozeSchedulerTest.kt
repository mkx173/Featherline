package com.mkx.hrttracker.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
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
        coEvery { snoozeStore.replaceSnoozeRecords(any()) } just Runs

        scheduler = MedicationReminderSnoozeScheduler(
            context = context,
            snoozeStore = snoozeStore,
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
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

    private fun snoozeRecord(
        groupUuid: UUID,
        scheduledAt: LocalDateTime,
        snoozeAt: LocalDateTime,
    ): MedicationReminderSnoozeRecord {
        return MedicationReminderSnoozeRecord(
            slot = MedicationReminderSlot(
                groupUuid = groupUuid,
                scheduledAt = scheduledAt,
                scheduleTimeUuid = UUID.nameUUIDFromBytes(groupUuid.toString().toByteArray()),
            ),
            snoozeAt = snoozeAt,
            snoozeCount = 1,
        )
    }
}
