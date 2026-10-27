package com.mkx.hrttracker.reminder

import android.content.BroadcastReceiver
import com.mkx.hrttracker.data.repository.E2EstimateRepository
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
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationReminderReceiverTest {
    private val settingsRepository: SettingsRepository = mockk()
    private val medicationGroupRepository: MedicationGroupRepository = mockk()
    private val medicationLogRepository: MedicationLogRepository = mockk()
    private val medicationReminderScheduler: MedicationReminderScheduler = mockk(relaxed = true)
    private val reminderNotificationManager: ReminderNotificationManager = mockk(relaxed = true)
    private val e2EstimateRepository: E2EstimateRepository = mockk(relaxed = true)
    private val diagnosticsLogger: AppDiagnosticsLogger = mockk(relaxed = true)
    private val pendingResult: BroadcastReceiver.PendingResult = mockk(relaxed = true)

    private val scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0)
    private val group = MedicationGroup(
        uuid = UUID.fromString("ea12a7fa-9077-442f-b322-988a48195515"),
        name = "Estradiol",
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
            )
        ),
        notificationsEnabled = true,
        createdAt = Instant.parse("2026-04-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-04-01T00:00:00Z"),
    )

    @Before
    fun setUp() {
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState()
        coEvery { medicationGroupRepository.getGroups() } returns listOf(group)
        coEvery { medicationLogRepository.getScheduledGroupEntriesSince(any()) } returns emptyList()
    }

    @Test
    fun aDoseReminder_warmsHomesEstimate_afterPostingTheNotification() = runTest {
        // At the slot time the slot is no longer planned: Home's estimate
        // moved, and the next open should find the new one stored.
        deliver(now = scheduledAt)

        coVerifyOrder {
            reminderNotificationManager.showDoseReminderNotification(any(), any(), any())
            e2EstimateRepository.warmHome()
            pendingResult.finish()
        }
    }

    @Test
    fun aReminderDeliveredDaysLate_namesTheLatestDueDose_andReArmsFromNow() = runTest {
        // A late delivery, or a clock moved ahead. On the alarm's own slot,
        // Log now would save today's dose days back; re-armed from it, each
        // daily slot since would fire at once.
        val now = LocalDateTime.of(2026, 4, 23, 10, 0)

        deliver(now)

        assertEquals(listOf(group.uuid to LocalDateTime.of(2026, 4, 23, 9, 0)), postedSlots())
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleGroup(group.uuid, after = now) }
    }

    @Test
    fun aReminderDeliveredOnTime_namesItsSlot_andReArmsFromASecondAfterIt() = runTest {
        deliver(now = scheduledAt.plusNanos(250_000_000))

        assertEquals(listOf(group.uuid to scheduledAt), postedSlots())
        coVerify(exactly = 1) {
            medicationReminderScheduler.rescheduleGroup(group.uuid, after = scheduledAt.plusSeconds(1))
        }
    }

    @Test
    fun aLateDelivery_toGroupsSharingATime_remindsEachOfItsOwnLatestDueDose() = runTest {
        // A twice-daily group shares 09:00 with the once-daily one. The clock
        // jumps from 08:00 to 22:00, and both 09:00 alarms fire at once.
        val twiceDaily = group.copy(
            uuid = UUID.fromString("5e1b7c2a-3d4f-4a6b-9c8d-0e1f2a3b4c5d"),
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(9, 0), LocalTime.of(21, 0)),
            ),
        )
        coEvery { medicationGroupRepository.getGroups() } returns listOf(twiceDaily, group)
        val now = scheduledAt.withHour(22)

        deliver(now, groupUuid = twiceDaily.uuid)
        assertEquals(listOf(twiceDaily.uuid to scheduledAt.withHour(21)), postedSlots())

        // Not the twice-daily group's 09:00 too: Log now would save it at 22:00.
        clearMocks(reminderNotificationManager)
        deliver(now)
        assertEquals(listOf(group.uuid to scheduledAt), postedSlots())
    }

    /** The alarm of [groupUuid]'s group for [scheduledAt], received at [now]. */
    private fun TestScope.deliver(now: LocalDateTime, groupUuid: UUID = group.uuid) {
        handleDoseReminder(
            groupUuid = groupUuid,
            scheduledAt = scheduledAt,
            now = now,
            deadline = TimeSource.Monotonic.markNow() + 8.seconds,
            appScope = backgroundScope,
            settingsRepository = settingsRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            reminderNotificationManager = reminderNotificationManager,
            e2EstimateRepository = e2EstimateRepository,
            diagnosticsLogger = diagnosticsLogger,
            pendingResult = pendingResult,
        )
        runCurrent()
    }

    /** The slots of the one reminder posted, by group and time. */
    private fun postedSlots(): List<Pair<UUID, LocalDateTime>> {
        val bundle = slot<MedicationReminderBundle>()
        coVerify(exactly = 1) { reminderNotificationManager.showDoseReminderNotification(capture(bundle), any(), any()) }
        return bundle.captured.slots.map { slot -> slot.groupUuid to slot.scheduledAt }
    }
}
