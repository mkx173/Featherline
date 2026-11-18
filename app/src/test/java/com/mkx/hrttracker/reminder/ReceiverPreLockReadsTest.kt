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
import com.mkx.hrttracker.model.medication.testInstant
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The receiver reads the clock and the repeats once it holds the lock, not when it receives the
 * alarm (first review 2026-11-18, Medium 5; second review, finding 11), and audit N1's Log now
 * case. Against the real scheduler and receiver with ReminderDeliveryRaceTest's harness.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReceiverPreLockReadsTest {
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

    private val nine = LocalDateTime.of(2026, 4, 20, 9, 0)
    private val plan = MedicationGroup(
        uuid = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001"),
        name = "A",
        schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.DAILY,
            interval = 1,
            since = LocalDate.of(2026, 4, 1),
            weeklyDaysOfWeek = emptySet(),
            times = listOf(LocalTime.of(9, 0)),
        ),
        medications = listOf(
            testMedicationGroupMedication(
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
            )
        ),
        notificationsEnabled = true,
        createdAt = Instant.parse("2026-04-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-04-01T00:00:00Z"),
    )

    /** What Settings and the schedule store hold, and the scheduler's clock. */
    private var repeats = ReminderRepeats()
    private val stored = mutableMapOf<UUID, Instant?>()
    private var clock: Instant = testInstant(nine)
    private val audiblePosts = mutableListOf<MedicationReminderBundle>()
    private val silentPosts = mutableListOf<MedicationReminderBundle>()

    @Before
    fun setUp() {
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
            notificationManager.showDoseReminderNotification(any(), any(), silent = false)
        } answers { audiblePosts += firstArg<MedicationReminderBundle>() }
        every {
            notificationManager.showDoseReminderNotification(any(), any(), silent = true)
        } answers { silentPosts += firstArg<MedicationReminderBundle>() }
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
    }

    /**
     * Audit N1: Log now starts at 08:59:59, the 09:00 alarm is delivered while it writes, and its
     * last step reschedules (`rescheduleGroup(groupUuid)`, which reads the clock under the lock),
     * so 09:00 is not armed again.
     */
    @Test
    fun aLogNowThatStartedBeforeTheDoseTime_doesntRingTheDeliveredDoseAgain() = runTest {
        stored[plan.uuid] = testInstant(nine)
        clock = testInstant(nine.plusSeconds(1))
        deliver(armedFor = nine)
        assertEquals("the delivery moved the plan to its repeat", testInstant(nine.plusMinutes(15)), stored[plan.uuid])

        clock = testInstant(nine.plusSeconds(2))
        scheduler.rescheduleGroup(plan.uuid)

        assertEquals("09:00 is not armed again", testInstant(nine.plusMinutes(15)), stored[plan.uuid])
        assertEquals(1, audiblePosts.size)
    }

    /**
     * The 09:00 alarm comes late (an inexact alarm, Doze) and is received at 09:14:59, and the
     * lock is busy until 09:15:02, past the plan's 09:15 alert. The delivery posts 09:15's alert
     * as the latest due and arms 09:30. With the clock read at receipt, it armed 09:15, already
     * past, which fired at once and sounded a second time.
     */
    @Test
    fun aLateDeliveryThatWaitsForTheLockPastThePlansNextAlert_soundsOnce() = runTest {
        stored[plan.uuid] = testInstant(nine)
        val release = CompletableDeferred<Unit>()
        backgroundScope.launch { withReminderScheduleLock { release.await() } }
        runCurrent()

        clock = testInstant(nine.plusMinutes(14).plusSeconds(59))
        deliver(armedFor = nine)
        clock = testInstant(nine.plusMinutes(15).plusSeconds(2))
        release.complete(Unit)
        runCurrent()
        deliver(armedFor = nine.plusMinutes(15))

        assertEquals("one sound", 1, audiblePosts.size)
        assertEquals(0, silentPosts.size)
        assertEquals("the next alert is ahead", testInstant(nine.plusMinutes(30)), stored[plan.uuid])
    }

    /**
     * The 09:15 repeat's alarm is received and the receiver reads the settings (repeats on).
     * Before it takes the lock, Settings stores repeats Off. The delivery reads the repeats in
     * its hold, so the repeat rings nothing.
     */
    @Test
    fun aRepeatInFlightWhenRepeatsAreSwitchedOff_ringsNothing() = runTest {
        stored[plan.uuid] = testInstant(nine.plusMinutes(15))
        clock = testInstant(nine.plusMinutes(15).plusSeconds(1))
        val resume = pauseAfterFirstSettingsRead()

        deliver(armedFor = nine.plusMinutes(15))
        repeats = ReminderRepeats(count = 0)
        resume.complete(Unit)
        runCurrent()

        assertEquals(0, audiblePosts.size + silentPosts.size)
        assertEquals("tomorrow's dose", testInstant(nine.plusDays(1)), stored[plan.uuid])
    }

    /**
     * The same, with Settings' full pass taking the lock first (second review, finding 11). The
     * pass arms the passed 09:15 alert again as it is, so the delivery still finds its instant
     * stored, and reads repeats Off in its hold.
     */
    @Test
    fun aRepeatInFlight_whenSettingsPassWinsTheLock_ringsNothing() = runTest {
        stored[plan.uuid] = testInstant(nine.plusMinutes(15))
        clock = testInstant(nine.plusMinutes(15).plusSeconds(1))
        val resume = pauseAfterFirstSettingsRead()

        deliver(armedFor = nine.plusMinutes(15))
        repeats = ReminderRepeats(count = 0)
        scheduler.rescheduleAll()
        assertEquals("the pass kept the passed alert armed", testInstant(nine.plusMinutes(15)), stored[plan.uuid])
        resume.complete(Unit)
        runCurrent()

        assertEquals(0, audiblePosts.size + silentPosts.size)
        assertEquals(testInstant(nine.plusDays(1)), stored[plan.uuid])
    }

    /**
     * Settings turns reminders off while the delivery reads the log under the lock (its write
     * takes no lock; its pass waits for this hold). The switch is read again last before the
     * post, so nothing posts.
     */
    @Test
    fun remindersTurnedOffDuringTheHoldsRoomRead_postNothing() = runTest {
        var remindersEnabled = true
        coEvery { settingsRepository.getCurrentSettings() } answers {
            SettingsState(remindersEnabled = remindersEnabled, reminderRepeats = repeats)
        }
        coEvery { logRepository.getScheduledGroupEntriesSince(any()) } answers {
            remindersEnabled = false
            emptyList()
        }
        stored[plan.uuid] = testInstant(nine)
        clock = testInstant(nine.plusSeconds(1))

        deliver(armedFor = nine)

        assertEquals(0, audiblePosts.size + silentPosts.size)
    }

    /** Suspends the receiver's first settings read, after it took its value, until the signal. */
    private fun pauseAfterFirstSettingsRead(): CompletableDeferred<Unit> {
        val resume = CompletableDeferred<Unit>()
        var first = true
        coEvery { settingsRepository.getCurrentSettings() } coAnswers {
            val settings = SettingsState(reminderRepeats = repeats)
            if (first) {
                first = false
                resume.await()
            }
            settings
        }
        return resume
    }

    /** [plan]'s alarm armed for [armedFor], handled with the test's [clock]. */
    private fun TestScope.deliver(armedFor: LocalDateTime) {
        handleDoseReminder(
            groupUuid = plan.uuid,
            alertAt = testInstant(armedFor),
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
        )
        runCurrent()
    }
}

