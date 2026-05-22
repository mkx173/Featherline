package com.mkx.hrttracker.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.mkx.hrttracker.data.repository.HomeSnapshotRecord
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.AppDiagnosticsLogger
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
import org.junit.Assert.assertEquals
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
    private val reminderScheduleStore: ReminderScheduleStore = mockk()
    private val snoozeScheduler: MedicationReminderSnoozeScheduler = mockk()
    private val diagnosticsLogger: AppDiagnosticsLogger = mockk(relaxed = true)

    private lateinit var scheduler: MedicationReminderScheduler

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
        coEvery { reminderScheduleStore.getScheduledGroupUuids() } returns emptySet()
        coEvery { reminderScheduleStore.addScheduledGroupUuid(any()) } just Runs
        coEvery { reminderScheduleStore.removeScheduledGroupUuid(any()) } just Runs
        coEvery { reminderScheduleStore.replaceScheduledGroupUuids(any()) } just Runs
        coEvery { snoozeScheduler.clearStaleSnoozesForGroup(any()) } just Runs

        scheduler = MedicationReminderScheduler(
            context = context,
            medicationGroupRepository = groupRepository,
            medicationLogRepository = logRepository,
            settingsRepository = settingsRepository,
            reminderScheduleStore = reminderScheduleStore,
            medicationReminderSnoozeScheduler = snoozeScheduler,
            diagnosticsLogger = diagnosticsLogger,
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
        coVerify { reminderScheduleStore.replaceScheduledGroupUuids(emptySet()) }
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
        coVerify { reminderScheduleStore.removeScheduledGroupUuid(groupId) }
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
        coVerify { reminderScheduleStore.replaceScheduledGroupUuids(setOf(group.uuid)) }
        verify {
            diagnosticsLogger.info(
                "MedicationReminderScheduler",
                match { message ->
                    "reminder_reschedule_all_complete" in message &&
                        "groups=1" in message &&
                        "plans=1" in message &&
                        "stored=1" in message
                }
            )
        }
    }

    @Test
    fun rescheduleFromHomeSnapshot_schedules_enabled_groups_without_room_reads() = runTest {
        val now = LocalDateTime.of(2026, 4, 20, 8, 0)
        val group = medicationGroup(
            uuid = UUID.fromString("c532a083-f7ef-464a-9cdb-b70a5eaf4c9c"),
            notificationsEnabled = true
        )
        coEvery { settingsRepository.getCurrentSettings() } returns
            SettingsState(remindersEnabled = true)

        scheduler.rescheduleFromHomeSnapshot(
            snapshot = HomeSnapshotRecord(
                schemaVersion = 3,
                generation = 1L,
                generatedAtEpochMillis = Instant.parse("2026-04-20T00:00:00Z").toEpochMilli(),
                anchorDateEpochDay = now.toLocalDate().toEpochDay(),
                zoneId = "Asia/Tokyo",
                pkProjection = null,
                activeGroups = listOf(group),
                scheduleEntries = emptyList(),
                antiandrogenHistoryEntries = emptyList(),
            ),
            now = now,
        )

        verify {
            alarmManager.cancel(any<PendingIntent>())
            alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>())
        }
        coVerify { reminderScheduleStore.replaceScheduledGroupUuids(setOf(group.uuid)) }
        coVerify(exactly = 0) { groupRepository.getGroups() }
        coVerify(exactly = 0) { logRepository.getScheduledGroupEntriesSince(any()) }
    }

    @Test
    fun rescheduleAll_cancels_stored_stale_group_reminders() = runTest {
        val activeGroup = medicationGroup(
            uuid = UUID.fromString("8fedbc75-f26a-4a88-ae0f-ee24a26d7a21"),
            notificationsEnabled = true
        )
        val staleGroupUuid = UUID.fromString("c087d68b-1da2-4b2d-bfb2-f247e845a599")
        coEvery { groupRepository.getGroups() } returns listOf(activeGroup)
        coEvery { settingsRepository.getCurrentSettings() } returns
            SettingsState(remindersEnabled = true)
        coEvery { logRepository.getScheduledGroupEntriesSince(any()) } returns emptyList()
        coEvery { reminderScheduleStore.getScheduledGroupUuids() } returns setOf(staleGroupUuid)

        scheduler.rescheduleAll(now = LocalDateTime.of(2026, 4, 20, 8, 0))

        verify(exactly = 2) { alarmManager.cancel(any<PendingIntent>()) }
        coVerify { reminderScheduleStore.replaceScheduledGroupUuids(setOf(activeGroup.uuid)) }
    }

    @Test
    fun rescheduleAll_clears_stored_group_reminders_when_master_switch_off() = runTest {
        val activeGroup = medicationGroup(
            uuid = UUID.fromString("f402e00e-7566-4878-b3e5-fc306be38ebc"),
            notificationsEnabled = true
        )
        val staleGroupUuid = UUID.fromString("9c7b52d9-0ca7-4a29-9225-98e920ef0ad7")
        coEvery { groupRepository.getGroups() } returns listOf(activeGroup)
        coEvery { settingsRepository.getCurrentSettings() } returns
            SettingsState(remindersEnabled = false)
        coEvery { reminderScheduleStore.getScheduledGroupUuids() } returns setOf(staleGroupUuid)

        scheduler.rescheduleAll(now = LocalDateTime.of(2026, 4, 20, 8, 0))

        verify(exactly = 2) { alarmManager.cancel(any<PendingIntent>()) }
        coVerify { reminderScheduleStore.replaceScheduledGroupUuids(emptySet()) }
    }

    @Test
    fun rescheduleFromHomeSnapshot_cancels_stored_stale_group_reminders() = runTest {
        val now = LocalDateTime.of(2026, 4, 20, 8, 0)
        val activeGroup = medicationGroup(
            uuid = UUID.fromString("40307933-b8a5-49e7-8446-3843a5eb53dd"),
            notificationsEnabled = true
        )
        val staleGroupUuid = UUID.fromString("a5b3593c-dfb6-4a9b-8de6-b4b4950c9113")
        coEvery { settingsRepository.getCurrentSettings() } returns
            SettingsState(remindersEnabled = true)
        coEvery { reminderScheduleStore.getScheduledGroupUuids() } returns setOf(staleGroupUuid)

        scheduler.rescheduleFromHomeSnapshot(
            snapshot = HomeSnapshotRecord(
                schemaVersion = 3,
                generation = 1L,
                generatedAtEpochMillis = Instant.parse("2026-04-20T00:00:00Z").toEpochMilli(),
                anchorDateEpochDay = now.toLocalDate().toEpochDay(),
                zoneId = "Asia/Tokyo",
                pkProjection = null,
                activeGroups = listOf(activeGroup),
                scheduleEntries = emptyList(),
                antiandrogenHistoryEntries = emptyList(),
            ),
            now = now,
        )

        verify(exactly = 2) { alarmManager.cancel(any<PendingIntent>()) }
        coVerify { reminderScheduleStore.replaceScheduledGroupUuids(setOf(activeGroup.uuid)) }
    }

    @Test
    fun rescheduleAll_uses_group_uuid_data_to_distinguish_same_time_reminder_intents() = runTest {
        val firstGroup = medicationGroup(
            uuid = UUID.fromString("c1f20d2e-3b0a-47a8-b8fa-eedb52b6d4ef"),
            notificationsEnabled = true
        )
        val secondGroup = medicationGroup(
            uuid = UUID.fromString("4506b057-5756-4dc9-9152-971579dc8e28"),
            notificationsEnabled = true
        )
        val capturedIntents = mutableListOf<Intent>()
        val parsedUriStrings = mutableListOf<String>()
        every {
            PendingIntent.getBroadcast(any(), any(), capture(capturedIntents), any<Int>())
        } returns mockk(relaxed = true)
        every { Uri.parse(capture(parsedUriStrings)) } answers { mockk(relaxed = true) }
        coEvery { groupRepository.getGroups() } returns listOf(firstGroup, secondGroup)
        coEvery { settingsRepository.getCurrentSettings() } returns
            SettingsState(remindersEnabled = true)
        coEvery { logRepository.getScheduledGroupEntriesSince(any()) } returns emptyList()

        scheduler.rescheduleAll(now = LocalDateTime.of(2026, 4, 20, 8, 0))

        assertEquals(
            setOf(
                "hrttracker://medication-reminder/${firstGroup.uuid}",
                "hrttracker://medication-reminder/${secondGroup.uuid}"
            ),
            parsedUriStrings.toSet()
        )
    }

    @Test
    fun rescheduleGroup_removes_stored_uuid_when_no_new_plan_is_scheduled() = runTest {
        val group = medicationGroup(
            uuid = UUID.fromString("89d27e7a-756b-453b-bdfc-448f7ab3ade9"),
            notificationsEnabled = false
        )
        coEvery { settingsRepository.getCurrentSettings() } returns
            SettingsState(remindersEnabled = true)
        coEvery { groupRepository.getGroup(group.uuid) } returns group

        scheduler.rescheduleGroup(
            groupUuid = group.uuid,
            after = LocalDateTime.of(2026, 4, 20, 8, 0)
        )

        coVerify { reminderScheduleStore.removeScheduledGroupUuid(group.uuid) }
        coVerify(exactly = 0) { reminderScheduleStore.addScheduledGroupUuid(group.uuid) }
        verify(exactly = 0) {
            alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>())
        }
    }

    @Test
    fun rescheduleGroup_adds_stored_uuid_when_new_plan_is_scheduled() = runTest {
        val group = medicationGroup(
            uuid = UUID.fromString("1702b8fc-b933-44ea-bbea-14ab339bc358"),
            notificationsEnabled = true
        )
        coEvery { settingsRepository.getCurrentSettings() } returns
            SettingsState(remindersEnabled = true)
        coEvery { groupRepository.getGroup(group.uuid) } returns group
        coEvery { logRepository.getScheduledGroupEntriesSince(any()) } returns emptyList()

        scheduler.rescheduleGroup(
            groupUuid = group.uuid,
            after = LocalDateTime.of(2026, 4, 20, 8, 0)
        )

        coVerify { reminderScheduleStore.removeScheduledGroupUuid(group.uuid) }
        coVerify { reminderScheduleStore.addScheduledGroupUuid(group.uuid) }
        verify {
            alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>())
        }
    }

    @Test
    fun rescheduleGroup_clears_stale_snoozes_for_current_group_schedule() = runTest {
        val group = medicationGroup(
            uuid = UUID.fromString("ff687dfd-1ec9-49d3-9901-d99a6636e66d"),
            notificationsEnabled = true,
        )
        coEvery { settingsRepository.getCurrentSettings() } returns
            SettingsState(remindersEnabled = true)
        coEvery { groupRepository.getGroup(group.uuid) } returns group
        coEvery { logRepository.getScheduledGroupEntriesSince(any()) } returns emptyList()
        scheduler.rescheduleGroup(
            groupUuid = group.uuid,
            after = LocalDateTime.of(2026, 4, 20, 8, 0),
        )

        coVerify { snoozeScheduler.clearStaleSnoozesForGroup(group) }
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
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0))
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("aa111111-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                    medicine = testMedicine(key = MedicationKey.ESTRADIOL),
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.WholeUnit,
                )
            ),
            notificationsEnabled = notificationsEnabled,
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z")
        )
    }

}
