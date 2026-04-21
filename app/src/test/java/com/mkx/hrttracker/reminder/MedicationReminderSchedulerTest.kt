package com.mkx.hrttracker.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.RouteOfAdministration
import com.mkx.hrttracker.model.settings.SettingsState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

class MedicationReminderSchedulerTest {
    private val context: Context = mockk()
    private val alarmManager: AlarmManager = mockk(relaxed = true)
    private val groupRepository: MedicationGroupRepository = mockk()
    private val logRepository: MedicationLogRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()

    private lateinit var scheduler: MedicationReminderScheduler

    @Before
    fun setUp() {
        mockkStatic(PendingIntent::class)
        every {
            PendingIntent.getBroadcast(any(), any(), any(), any<Int>())
        } returns mockk(relaxed = true)
        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        every { context.packageName } returns "com.mkx.hrttracker.test"
        every { alarmManager.canScheduleExactAlarms() } returns true

        scheduler = MedicationReminderScheduler(
            context = context,
            medicationGroupRepository = groupRepository,
            medicationLogRepository = logRepository,
            settingsRepository = settingsRepository
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun rescheduleAll_cancels_groups_and_skips_scheduling_when_master_switch_off() = runTest {
        val group = medicationGroup(
            uuid = UUID.fromString("4a9f4c8d-2b0f-4f6e-9d2a-7a5d1f3c8e11"),
            notificationsEnabled = true
        )
        coEvery { groupRepository.getGroups() } returns listOf(group)
        coEvery { settingsRepository.getCurrentSettings() } returns
            SettingsState(remindersEnabled = false)

        scheduler.rescheduleAll(now = LocalDateTime.of(2026, 4, 20, 9, 0))

        verify { alarmManager.cancel(any<PendingIntent>()) }
        verify(exactly = 0) {
            alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>())
        }
        verify(exactly = 0) {
            alarmManager.setAndAllowWhileIdle(any(), any(), any<PendingIntent>())
        }
        coVerify(exactly = 0) { logRepository.getScheduledGroupEntriesSince(any()) }
    }

    @Test
    fun rescheduleGroup_cancels_without_scheduling_when_master_switch_off() = runTest {
        val groupId = UUID.fromString("7e0b8a3d-1c2f-4d5e-8b9a-6f1e2c3d4a5b")
        coEvery { settingsRepository.getCurrentSettings() } returns
            SettingsState(remindersEnabled = false)

        scheduler.rescheduleGroup(
            groupUuid = groupId,
            after = LocalDateTime.of(2026, 4, 20, 9, 0)
        )

        verify { alarmManager.cancel(any<PendingIntent>()) }
        verify(exactly = 0) {
            alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>())
        }
        coVerify(exactly = 0) { groupRepository.getGroup(any()) }
        coVerify(exactly = 0) { logRepository.getScheduledGroupEntriesSince(any()) }
    }

    @Test
    fun rescheduleAll_schedules_enabled_groups_when_master_switch_on() = runTest {
        val group = medicationGroup(
            uuid = UUID.fromString("3c7d1e2f-4a5b-6c7d-8e9f-0a1b2c3d4e5f"),
            notificationsEnabled = true
        )
        coEvery { groupRepository.getGroups() } returns listOf(group)
        coEvery { settingsRepository.getCurrentSettings() } returns
            SettingsState(remindersEnabled = true)
        coEvery { logRepository.getScheduledGroupEntriesSince(any()) } returns emptyList()

        scheduler.rescheduleAll(now = LocalDateTime.of(2026, 4, 20, 8, 0))

        verify {
            alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>())
        }
    }

    private fun medicationGroup(
        uuid: UUID,
        notificationsEnabled: Boolean
    ): MedicationGroup {
        return MedicationGroup(
            uuid = uuid,
            name = "Group",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDayOfWeek = null,
                times = listOf(LocalTime.of(9, 0))
            ),
            medications = listOf(
                MedicationGroupMedication(
                    uuid = UUID.fromString("aa111111-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                    routeOfAdministration = RouteOfAdministration.ORAL,
                    medicineName = "Estradiol",
                    dosageMgAsMedicine = 2.0
                )
            ),
            notificationsEnabled = notificationsEnabled,
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z")
        )
    }
}
