package com.mkx.hrttracker.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.net.Uri
import com.mkx.hrttracker.data.repository.E2EstimateRepository
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.settings.ReminderRepeats
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * New York, 2026-03-08, where 02:00 to 03:00 is skipped: one daily plan with doses at 02:10,
 * 02:50 and 03:05 (second review 2026-11-18, finding 3). 02:10 and 02:50 resolve to 03:10 and
 * 03:50, after 03:05, so only the 03:05 dose rings, as decision V7 has the valid dose after the
 * gap cover those in it. Through the real planner, scheduler and receiver, with the default zone
 * set and a fixed clock. The last test is V7's own case.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpringForwardDoseOrderTest {
    private val context: Context = mockk()
    private val alarmManager: AlarmManager = mockk(relaxed = true)
    private val groupRepository: MedicationGroupRepository = mockk()
    private val logRepository: MedicationLogRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val reminderScheduleStore: ReminderScheduleStore = mockk()
    private val notificationManager: ReminderNotificationManager = mockk(relaxed = true)
    private val homeSnapshotRepository: HomeSnapshotRepository = mockk {
        every { generation } returns MutableStateFlow(1L)
    }
    private val diagnosticsLogger: AppDiagnosticsLogger = mockk(relaxed = true)
    private lateinit var scheduler: MedicationReminderScheduler
    private lateinit var zoneBefore: TimeZone

    private val newYork: ZoneId = ZoneId.of("America/New_York")
    private val repeatsOff = ReminderRepeats(count = 0)
    private val march8 = LocalDate.of(2026, 3, 8)
    private val fivePastThree = LocalDateTime.of(march8, LocalTime.of(3, 5))

    // Instants on Mar 8, UTC. EST is UTC-5 until 07:00Z, EDT is UTC-4 after it.
    private val at0159Est = Instant.parse("2026-03-08T06:59:00Z")
    private val at0305Edt = Instant.parse("2026-03-08T07:05:00Z")
    private val at0310Edt = Instant.parse("2026-03-08T07:10:00Z")
    private val nextDay0210Edt = Instant.parse("2026-03-09T06:10:00Z")

    private val plan = nightPlan(LocalTime.of(2, 10), LocalTime.of(2, 50), LocalTime.of(3, 5))

    private val stored = mutableMapOf<UUID, Instant?>()
    private var clock: Instant = at0159Est
    private var repeats = repeatsOff
    private val posts = mutableListOf<MedicationReminderBundle>()

    @Before
    fun setUp() {
        // The scheduler reads the system zone.
        zoneBefore = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(newYork))
        mockkStatic(PendingIntent::class)
        mockkStatic(Uri::class)
        every { PendingIntent.getBroadcast(any(), any(), any(), any<Int>()) } returns mockk(relaxed = true)
        every { Uri.parse(any()) } returns mockk(relaxed = true)
        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        every { context.packageName } returns "com.mkx.hrttracker.test"
        every { alarmManager.canScheduleExactAlarms() } returns true
        coEvery { reminderScheduleStore.getScheduledReminders() } answers { stored.toMap() }
        coEvery { reminderScheduleStore.addScheduledReminder(any(), any()) } answers { stored[firstArg()] = secondArg() }
        coEvery { reminderScheduleStore.removeScheduledGroupUuid(any()) } answers { stored.remove(firstArg<UUID>()) }
        coEvery { reminderScheduleStore.replaceScheduledReminders(any()) } answers {
            stored.clear()
            stored.putAll(firstArg<Map<UUID, Instant>>())
        }
        coEvery { reminderScheduleStore.setReconcilePending(any()) } just Runs
        coEvery { reminderScheduleStore.isReconcilePending() } returns false
        coEvery { reminderScheduleStore.getSkippedThrough() } returns emptyMap()
        coEvery { settingsRepository.getCurrentSettings() } answers { SettingsState(reminderRepeats = repeats) }
        coEvery { groupRepository.getGroups() } returns listOf(plan)
        coEvery { groupRepository.getGroup(any()) } returns plan
        coEvery { logRepository.getScheduledGroupEntriesSince(any()) } returns emptyList()
        every { notificationManager.activeDoseReminders() } returns emptyMap()
        every {
            notificationManager.showDoseReminderNotification(any(), any(), any())
        } answers { posts += firstArg<MedicationReminderBundle>() }
        scheduler = MedicationReminderScheduler(
            context = context,
            medicationGroupRepository = groupRepository,
            medicationLogRepository = logRepository,
            settingsRepository = settingsRepository,
            reminderScheduleStore = reminderScheduleStore,
            reminderNotificationManager = notificationManager,
            homeSnapshotRepository = homeSnapshotRepository,
            diagnosticsLogger = diagnosticsLogger,
            sdkInt = 31,
            instantNow = { clock },
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
        TimeZone.setDefault(zoneBefore)
    }

    /** Only the 03:05 dose has an alert: 02:10 (03:10) and 02:50 (03:50) come after it. */
    @Test
    fun onlyTheDoseAfterTheGapRings() {
        val alerts = plan.reminderAlerts(march8.atStartOfDay(), emptyList(), repeatsOff, null, newYork)
            .takeWhile { alert -> alert.doseAt.toLocalDate() == march8 }
            .toList()

        assertEquals(listOf(fivePastThree to at0305Edt), alerts.map { alert -> alert.doseAt to alert.alertAt })
        assertEquals(at0305Edt, plan.nextReminderAlert(at0159Est, emptyList(), repeatsOff, null, newYork)?.alertAt)
    }

    /** With the default repeats (3 × 15 min) the 03:05 dose rings four times, then tomorrow's 02:10. */
    @Test
    fun withTheDefaultRepeats_the0305DoseRingsWithItsRepeats() {
        assertEquals(
            listOf(
                LocalTime.of(3, 5) to at0305Edt,
                LocalTime.of(3, 5) to Instant.parse("2026-03-08T07:20:00Z"),
                LocalTime.of(3, 5) to Instant.parse("2026-03-08T07:35:00Z"),
                LocalTime.of(3, 5) to Instant.parse("2026-03-08T07:50:00Z"),
                LocalTime.of(2, 10) to nextDay0210Edt,
            ),
            ringsFrom(at0159Est, ReminderRepeats()),
        )
    }

    /** The scheduler and receiver, repeats off: 03:05 rings, then tomorrow. */
    @Test
    fun from0159_only0305Rings() = runTest {
        scheduler.rescheduleAll()
        assertEquals("armed for 03:05 EDT", at0305Edt, stored[plan.uuid])

        clock = at0305Edt.plusSeconds(1)
        deliver(armedFor = at0305Edt)

        assertEquals(listOf(fivePastThree), posts.map { it.scheduledAt })
        assertEquals("the plan's next alarm is tomorrow's 02:10", nextDay0210Edt, stored[plan.uuid])
    }

    /** The scheduler and receiver, default repeats: four posts, each for the 03:05 dose. */
    @Test
    fun from0159_withTheDefaultRepeats_the0305DosePostsFourTimes() = runTest {
        repeats = ReminderRepeats()
        scheduler.rescheduleAll()
        while (stored.getValue(plan.uuid)!!.isBefore(nextDay0210Edt)) {
            val armed = stored.getValue(plan.uuid)!!
            clock = armed.plusSeconds(1)
            deliver(armedFor = armed)
        }

        assertEquals(List(4) { fivePastThree }, posts.map { it.scheduledAt })
    }

    /** A delivery armed for 03:05 and handled at 03:12 still posts the 03:05 dose. */
    @Test
    fun aLateDelivery_postsThe0305Dose() = runTest {
        stored[plan.uuid] = at0305Edt
        clock = Instant.parse("2026-03-08T07:12:00Z")
        deliver(armedFor = at0305Edt)

        assertEquals(listOf(fivePastThree), posts.map { it.scheduledAt })
        assertEquals(
            fivePastThree,
            plan.latestDueReminderAlert(at0159Est, at0310Edt.plusSeconds(1), emptyList(), repeatsOff, null, newYork)?.doseAt,
        )
    }

    /**
     * Decision V7, accepted (notes/2026-11-10-reminders-v2.md): doses at 02:30 and 03:00. 02:30's
     * moment, 03:30, comes after the plan's next dose, 03:00, so it doesn't ring; the 03:00
     * reminder covers it.
     */
    @Test
    fun v7_aGapDoseBeforeTheNextDose_doesntRing_andTheNextDoseDoes() {
        val v7 = nightPlan(LocalTime.of(2, 30), LocalTime.of(3, 0))
        val at0300Edt = Instant.parse("2026-03-08T07:00:00Z")

        val first = v7.nextReminderAlert(at0159Est, emptyList(), repeatsOff, null, newYork)
        assertEquals(LocalDateTime.of(march8, LocalTime.of(3, 0)) to at0300Edt, first?.let { it.doseAt to it.alertAt })
        assertEquals(
            "then tomorrow's 02:30",
            LocalDateTime.of(march8.plusDays(1), LocalTime.of(2, 30)),
            v7.nextReminderAlert(at0300Edt, emptyList(), repeatsOff, null, newYork)?.doseAt,
        )
        assertEquals(
            "a late delivery names 03:00 too",
            LocalDateTime.of(march8, LocalTime.of(3, 0)),
            v7.latestDueReminderAlert(at0159Est, Instant.parse("2026-03-08T07:40:00Z"), emptyList(), repeatsOff, null, newYork)
                ?.doseAt,
        )
    }

    /** Each alert the plan rings from [after] on, delivered on time, the first five. */
    private fun ringsFrom(after: Instant, repeats: ReminderRepeats) =
        generateSequence(plan.nextReminderAlert(after, emptyList(), repeats, null, newYork)) { rang ->
            plan.nextReminderAlert(rang.alertAt, emptyList(), repeats, null, newYork)
        }.take(5).map { alert -> alert.doseAt.toLocalTime() to alert.alertAt }.toList()

    private fun nightPlan(vararg times: LocalTime) = MedicationGroup(
        uuid = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001"),
        name = "Night",
        schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.DAILY,
            interval = 1,
            since = LocalDate.of(2026, 3, 1),
            weeklyDaysOfWeek = emptySet(),
            times = times.toList(),
        ),
        medications = listOf(
            testMedicationGroupMedication(
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
            )
        ),
        notificationsEnabled = true,
        createdAt = Instant.parse("2026-03-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-03-01T00:00:00Z"),
    )

    private fun TestScope.deliver(armedFor: Instant) {
        handleDoseReminder(
            groupUuid = plan.uuid,
            alertAt = armedFor,
            clock = { clock },
            deadline = TimeSource.Monotonic.markNow() + 8.seconds,
            appScope = backgroundScope,
            settingsRepository = settingsRepository,
            medicationGroupRepository = groupRepository,
            medicationLogRepository = logRepository,
            medicationReminderScheduler = scheduler,
            reminderNotificationManager = notificationManager,
            e2EstimateRepository = mockk<E2EstimateRepository>(relaxed = true),
            diagnosticsLogger = diagnosticsLogger,
            pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true),
            zone = newYork,
        )
        runCurrent()
    }
}
