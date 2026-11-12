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
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import io.mockk.clearMocks
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
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

    @After
    fun tearDown() = clearAllMocks()

    @Before
    fun setUp() {
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState()
        coEvery { medicationGroupRepository.getGroup(group.uuid) } returns group
        coEvery { medicationLogRepository.getScheduledGroupEntriesSince(any()) } returns emptyList()
        coEvery { medicationReminderScheduler.awaitsReminderDelivery(any(), any()) } returns true
        coEvery { medicationReminderScheduler.skippedThrough(any()) } returns null
        every { medicationReminderScheduler.claimAlertSound(any()) } returns true
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
        // alert since would fire at once.
        val now = LocalDateTime.of(2026, 4, 23, 10, 0)

        deliver(now)

        assertEquals(listOf(group.uuid to LocalDateTime.of(2026, 4, 23, 9, 0)), postedSlots())
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleGroup(group.uuid, after = now.toInstant(ZoneOffset.UTC)) }
    }

    @Test
    fun aReminderDeliveredOnTime_namesItsDose_andArmsTheNextAlertAfterNow() = runTest {
        val now = scheduledAt.plusNanos(250_000_000)

        deliver(now = now)

        assertEquals(listOf(group.uuid to scheduledAt), postedSlots())
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleGroup(group.uuid, after = now.toInstant(ZoneOffset.UTC)) }
    }

    @Test
    fun aRepeat_postsTheSameDoseAgain_withSound() = runTest {
        val repeatAt = scheduledAt.plusMinutes(15)

        deliver(now = repeatAt.plusSeconds(1), alertAt = repeatAt)

        assertEquals(listOf(group.uuid to scheduledAt), postedSlots())
        coVerify { medicationReminderScheduler.claimAlertSound(repeatAt.toInstant(ZoneOffset.UTC)) }
        coVerify { reminderNotificationManager.showDoseReminderNotification(any(), any(), silent = false) }
    }

    @Test
    fun aPlanSharingAnAlertTimeThatAlreadySounded_postsSilently() = runTest {
        every { medicationReminderScheduler.claimAlertSound(any()) } returns false

        deliver(now = scheduledAt)

        coVerify(exactly = 1) {
            reminderNotificationManager.showDoseReminderNotification(any(), any(), silent = true)
        }
    }

    @Test
    fun aLateDelivery_toPlansSharingATime_remindsEachOfItsOwnLatestDueDose() = runTest {
        // A twice-daily plan shares 09:00 with the once-daily one. The clock
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
        coEvery { medicationGroupRepository.getGroup(twiceDaily.uuid) } returns twiceDaily
        val now = scheduledAt.withHour(22)

        deliver(now, groupUuid = twiceDaily.uuid)
        assertEquals(listOf(twiceDaily.uuid to scheduledAt.withHour(21)), postedSlots())

        // Its own reminder names its own plan only.
        clearMocks(reminderNotificationManager)
        deliver(now)
        assertEquals(listOf(group.uuid to scheduledAt), postedSlots())
    }

    @Test
    fun aDeliveryPastTheLastRepeat_andBeforeTheNextDose_postsNothing() = runTest {
        // Armed for the 09:45 repeat, but it lands at 10:30 after a 45-minute delay: the dose's
        // repeats are spent, and the next dose is tomorrow.
        val lastRepeat = scheduledAt.plusMinutes(45)

        deliver(now = scheduledAt.plusMinutes(90), alertAt = lastRepeat.plusMinutes(1))

        coVerify(exactly = 0) { reminderNotificationManager.showDoseReminderNotification(any(), any(), any()) }
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleGroup(group.uuid, any()) }
    }

    @Test
    fun aSkippedDose_postsNothing() = runTest {
        coEvery { medicationReminderScheduler.skippedThrough(group.uuid) } returns scheduledAt

        deliver(now = scheduledAt.plusMinutes(15), alertAt = scheduledAt.plusMinutes(15))

        coVerify(exactly = 0) { reminderNotificationManager.showDoseReminderNotification(any(), any(), any()) }
    }

    @Test
    fun anAlarmTheStoreNoLongerAwaits_postsNothing_butStillMarksAndReschedules() = runTest {
        // As an old alarm after a zone change: its instant isn't the one stored, though it may
        // share the new alarm's local time.
        coEvery { medicationReminderScheduler.awaitsReminderDelivery(group.uuid, any()) } returns false

        deliver(now = scheduledAt)

        coVerify(exactly = 0) { reminderNotificationManager.showDoseReminderNotification(any(), any(), any()) }
        coVerifyOrder {
            medicationReminderScheduler.markReminderDelivered(group.uuid, scheduledAt.toInstant(ZoneOffset.UTC))
            medicationReminderScheduler.rescheduleGroup(group.uuid, scheduledAt.toInstant(ZoneOffset.UTC))
        }
    }

    @Test
    fun aRepeatInFlightWhenItsDoseIsLogged_postsNothing() = runTest {
        // The 09:00 dose, logged at 09:05; its 09:15 repeat's alarm was armed before. The log is
        // dated 09:00, before the repeat: the receiver reads back to the dose.
        val record = testMedicationLogEntry(
            medicine = group.medications.single().medicine,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            sourceGroupUuid = group.uuid,
            appliedAt = scheduledAt.plusMinutes(5).toInstant(ZoneOffset.UTC),
            scheduledFor = scheduledAt,
            scheduleTimeUuid = group.schedule.timeSlots.single().uuid,
        )
        // As Room answers: dated from the given time on.
        coEvery { medicationLogRepository.getScheduledGroupEntriesSince(any()) } answers {
            listOf(record).filterNot { entry -> entry.scheduledFor!!.isBefore(firstArg()) }
        }

        deliver(now = scheduledAt.plusMinutes(15), alertAt = scheduledAt.plusMinutes(15))

        coVerify(exactly = 0) { reminderNotificationManager.showDoseReminderNotification(any(), any(), any()) }
    }

    @Test
    fun aDoseInTheSpringForwardGap_ringsOnceTheClocksHaveSkipped() = runTest {
        // New York skips 02:00 to 03:00 on Mar 8 2026; a 02:30 dose's alarm comes at 03:30 EDT.
        val newYork = ZoneId.of("America/New_York")
        val gapDose = group.copy(
            schedule = group.schedule.copy(times = listOf(LocalTime.of(2, 30))).let { schedule ->
                MedicationGroupSchedule(
                    type = schedule.type,
                    interval = 1,
                    since = LocalDate.of(2026, 3, 1),
                    weeklyDaysOfWeek = emptySet(),
                    times = listOf(LocalTime.of(2, 30)),
                )
            },
        )
        coEvery { medicationGroupRepository.getGroup(group.uuid) } returns gapDose
        val alertAt = Instant.parse("2026-03-08T07:30:00Z")

        handleDoseReminder(
            groupUuid = group.uuid,
            alertAt = alertAt,
            now = alertAt.plusSeconds(1),
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
            zone = newYork,
        )
        runCurrent()

        assertEquals(listOf(group.uuid to LocalDateTime.of(2026, 3, 8, 2, 30)), postedSlots())
    }

    /** [groupUuid]'s alarm for the alert at [alertAt] (UTC), received at [now]. */
    private fun TestScope.deliver(
        now: LocalDateTime,
        groupUuid: UUID = group.uuid,
        alertAt: LocalDateTime = scheduledAt,
    ) {
        handleDoseReminder(
            groupUuid = groupUuid,
            alertAt = alertAt.toInstant(ZoneOffset.UTC),
            now = now.toInstant(ZoneOffset.UTC),
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
            zone = ZoneOffset.UTC,
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
