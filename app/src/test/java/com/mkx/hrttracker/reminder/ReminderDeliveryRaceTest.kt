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
import com.mkx.hrttracker.data.repository.MedicineStockRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testInstant
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The paths TLC found in .claude/skills/verify-reminders/model/, one step per model action,
 * against the real scheduler, receiver and action handler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReminderDeliveryRaceTest {
    private val context: Context = mockk()
    private val alarmManager: AlarmManager = mockk(relaxed = true)
    private val groupRepository: MedicationGroupRepository = mockk()
    private val logRepository: MedicationLogRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val reminderScheduleStore: ReminderScheduleStore = mockk()
    private val snoozeScheduler: MedicationReminderSnoozeScheduler = mockk()
    private val notificationManager: ReminderNotificationManager = mockk(relaxed = true)
    private val homeSnapshotRepository: HomeSnapshotRepository = mockk {
        every { generation } returns MutableStateFlow(1L)
    }
    private val diagnosticsLogger: AppDiagnosticsLogger = mockk(relaxed = true)
    private lateinit var scheduler: MedicationReminderScheduler

    private val nine = LocalDateTime.of(2026, 4, 20, 9, 0)
    private val justAfterNine = nine.plusSeconds(30)
    private val planA = dailyAtNine("aaaaaaaa-0000-4000-8000-000000000001", "A", MedicationKey.ESTRADIOL)
    private val planB = dailyAtNine("bbbbbbbb-0000-4000-8000-000000000002", "B", MedicationKey.SPIRONOLACTONE)

    /** What Room, Settings and the schedule store hold, as the steps change them. */
    private var groups = listOf(planA, planB)
    private var remindersEnabled = true
    private val stored = mutableMapOf<UUID, LocalDateTime?>()

    /** Each reminder posted with sound, in order. */
    private val audiblePosts = mutableListOf<MedicationReminderBundle>()

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
            stored.putAll(firstArg<Map<UUID, LocalDateTime>>())
        }
        coEvery { reminderScheduleStore.setReconcilePending(any()) } just Runs
        coEvery { settingsRepository.getCurrentSettings() } answers { SettingsState(remindersEnabled = remindersEnabled) }
        coEvery { groupRepository.getGroups() } answers { groups }
        coEvery { groupRepository.getGroup(any()) } answers { groups.firstOrNull { group -> group.uuid == firstArg() } }
        coEvery { logRepository.getScheduledGroupEntriesSince(any()) } returns emptyList()
        coEvery { snoozeScheduler.clearStaleSnoozesForGroup(any()) } just Runs
        coEvery { snoozeScheduler.getSnoozeRecords() } returns emptyList()
        every { notificationManager.activeDoseReminders() } returns emptyMap()
        every {
            notificationManager.showDoseReminderNotification(any(), any(), any(), silent = false)
        } answers { audiblePosts += firstArg<MedicationReminderBundle>() }

        scheduler = MedicationReminderScheduler(
            context = context,
            medicationGroupRepository = groupRepository,
            medicationLogRepository = logRepository,
            settingsRepository = settingsRepository,
            reminderScheduleStore = reminderScheduleStore,
            medicationReminderSnoozeScheduler = snoozeScheduler,
            reminderNotificationManager = notificationManager,
            homeSnapshotRepository = homeSnapshotRepository,
            diagnosticsLogger = diagnosticsLogger,
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /** DoseAlarmDelivery, EachDoseRingsOnce. */
    @Test
    fun twoPlansAtOneTime_ringOnce_andTheOtherPlansAlarmPostsNothing() = runTest {
        stored[planA.uuid] = nine
        stored[planB.uuid] = nine

        // The phone delivers A's alarm, then B's; each is handled.
        deliver(planA, armedFor = nine, now = justAfterNine)
        deliver(planB, armedFor = nine, now = justAfterNine)

        assertEquals(listOf(setOf(planA.uuid, planB.uuid)), audiblePosts.map(::groupsOf))
        // A's delivery moved both to tomorrow.
        assertEquals(mapOf<UUID, LocalDateTime?>(planA.uuid to nine.plusDays(1), planB.uuid to nine.plusDays(1)), stored)
    }

    /** DoseAlarmDelivery, EachDoseRingsOnce: the reverse path. */
    @Test
    fun aPlanWhoseDoseWasLoggedFirst_doesNotPostTheOtherPlansDoseAgain() = runTest {
        stored[planA.uuid] = nine
        stored[planB.uuid] = nine
        // You log A's 09:00 dose in the app.
        val medication = planA.medications.single()
        coEvery { logRepository.getScheduledGroupEntriesSince(any()) } returns listOf(
            testMedicationLogEntry(
                medicine = medication.medicine,
                applicationType = medication.applicationType,
                doseInstruction = medication.doseInstruction,
                sourceGroupUuid = planA.uuid,
                appliedAt = testInstant(justAfterNine),
                scheduledFor = nine,
            )
        )

        // B's alarm posts B alone; then A's alarm comes.
        deliver(planB, armedFor = nine, now = justAfterNine)
        deliver(planA, armedFor = nine, now = justAfterNine)

        assertEquals(listOf(setOf(planB.uuid)), audiblePosts.map(::groupsOf))
    }

    /** DoseAlarmDelivery, NoRingAfterRemindersOff. */
    @Test
    fun aDeliveryPastItsMasterCheck_postsNothingOnceSettingsTurnedRemindersOff() = runTest {
        groups = listOf(planA)
        stored[planA.uuid] = nine
        val resume = pauseAfterFirstSettingsRead()

        // The phone delivers A's alarm; the receiver reads reminders on.
        deliver(planA, armedFor = nine, now = justAfterNine)
        // You turn reminders off; Settings' full pass runs to the end and empties the store.
        remindersEnabled = false
        scheduler.rescheduleAll(now = justAfterNine)
        assertTrue(stored.isEmpty())
        // The receiver takes the lock: A has no key, so it was delivered or cancelled.
        resume.complete(Unit)
        runCurrent()

        assertEquals(0, audiblePosts.size)
    }

    /** The race NoRingAfterRemindersOff leaves open without the receiver's second read. */
    @Test
    fun aDeliveryBetweenSettingsWriteAndItsPass_postsNothing() = runTest {
        groups = listOf(planA)
        stored[planA.uuid] = nine
        val resume = pauseAfterFirstSettingsRead()

        deliver(planA, armedFor = nine, now = justAfterNine)
        // You turn reminders off. The receiver takes the lock before Settings' pass does.
        remindersEnabled = false
        resume.complete(Unit)
        runCurrent()
        scheduler.rescheduleAll(now = justAfterNine)

        assertEquals(0, audiblePosts.size)
        assertTrue(stored.isEmpty())
    }

    /** DoseAlarmDelivery, OneAlarmPostsOnce. */
    @Test
    fun anAlarmArmedAgainWhileInFlight_postsOnce_whenItsPlanIsArchivedBetween() = runTest {
        stored[planA.uuid] = nine
        stored[planB.uuid] = nine

        // A's 09:00 alarm has left AlarmManager. Before its receiver runs, A is rescheduled
        // (editor save, widget log): the passed 09:00, still missing, is armed again.
        scheduler.rescheduleGroup(planA.uuid, after = justAfterNine)
        assertEquals(nine, stored[planA.uuid])
        // The first delivery posts A + B 09:00 and moves A to tomorrow.
        deliver(planA, armedFor = nine, now = justAfterNine)
        // You archive A: the write, then cancelReminder drops its key.
        groups = listOf(planA.copy(archivedAt = testInstant(justAfterNine)), planB)
        scheduler.cancelReminder(planA.uuid)
        // The alarm armed again arrives: A has no key, so it posts nothing.
        deliver(planA, armedFor = nine, now = justAfterNine.plusSeconds(1))

        assertEquals(listOf(setOf(planA.uuid, planB.uuid)), audiblePosts.map(::groupsOf))
    }

    /** H1's null branch: an older build stored A with no time. */
    @Test
    fun aKeyWithNoTime_stillPosts_andIsNeverNamedInAnotherPlansReminder() = runTest {
        stored[planA.uuid] = null
        stored[planB.uuid] = nine

        deliver(planB, armedFor = nine, now = justAfterNine)
        deliver(planA, armedFor = nine, now = justAfterNine.plusMinutes(20))

        assertEquals(listOf(setOf(planB.uuid), setOf(planA.uuid)), audiblePosts.map(::groupsOf))
    }

    /** Product note R1: B's alarm comes first, you swipe its reminder away, then A's comes late. */
    @Test
    fun r1_theSecondPlansLateAlarm_bringsBackNoSwipedReminder() = runTest {
        stored[planA.uuid] = nine
        stored[planB.uuid] = nine

        deliver(planB, armedFor = nine, now = justAfterNine)
        // You swipe it away: nothing in the store changes.
        deliver(planA, armedFor = nine, now = justAfterNine.plusMinutes(20))

        // B's post named A, so A's alarm finds it delivered.
        assertEquals(listOf(setOf(planA.uuid, planB.uuid)), audiblePosts.map(::groupsOf))
    }

    /** DoseAlarmDelivery, EachActivePlanArmed's guard: one plan's failed reschedule spares the rest. */
    @Test
    fun aRescheduleThatFails_cancelsNothing_andLeavesTheOtherPlansToRun() = runTest {
        stored[planA.uuid] = nine
        stored[planB.uuid] = nine
        // A's plan read fails while its delivery reschedules it.
        coEvery { groupRepository.getGroup(planA.uuid) } throws IOException("database busy")

        deliver(planA, armedFor = nine, now = justAfterNine)

        assertEquals(listOf(setOf(planA.uuid, planB.uuid)), audiblePosts.map(::groupsOf))
        // B, named in the post, is rescheduled all the same.
        assertEquals(mapOf<UUID, LocalDateTime?>(planB.uuid to nine.plusDays(1)), stored)
        // Only B's alarm was cancelled to arm it again: A's failed before it cancelled anything.
        verify(exactly = 1) { alarmManager.cancel(any<PendingIntent>()) }
    }

    /** A pass that fails while arming leaves keys, which the next pass arms, not alarms with none. */
    @Test
    fun aPassThatFailsWhileArming_hasStoredWhatItPlanned() = runTest {
        every { alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>()) } throws
                IllegalStateException("alarm service gone")

        runCatching { scheduler.rescheduleAll(now = justAfterNine) }

        assertEquals(
            mapOf<UUID, LocalDateTime?>(planA.uuid to nine.plusDays(1), planB.uuid to nine.plusDays(1)),
            stored,
        )
    }

    /** SnoozeDelivery, NoSnoozeRingAfterRemindersOff. */
    @Test
    fun aSnoozeDelivery_postsNothingOnceRemindersAreTurnedOffDuringIt() = runTest {
        val handler = actionHandler()
        val slot = MedicationReminderSlot(planA.uuid, nine, planA.schedule.timeSlots.first().uuid)
        coEvery { snoozeScheduler.checkSnoozeDelivery(any(), any()) } answers { firstArg() }
        coEvery { snoozeScheduler.markSnoozeDelivered(any(), any()) } just Runs
        // You turn reminders off while the delivery reads the plan and the log.
        coEvery { snoozeScheduler.getSnoozeRecords() } answers {
            remindersEnabled = false
            emptyList()
        }

        handler.showSnoozedReminder(
            slots = listOf(slot),
            notificationTag = null,
            snoozeAt = testInstant(nine.plusMinutes(15)),
            now = nine.plusMinutes(15),
        )

        assertEquals(0, audiblePosts.size)
        coVerify { snoozeScheduler.markSnoozeDelivered(listOf(slot), testInstant(nine.plusMinutes(15))) }
    }

    /** SnoozeDelivery, NoReminderForLoggedDose (and its WhileOff, ArchivedPlan siblings). */
    @Test
    fun aSnoozeDelivery_holdsTheScheduleLockFromItsDueReadToItsPost() = runTest {
        val handler = actionHandler()
        val slot = MedicationReminderSlot(planA.uuid, nine, planA.schedule.timeSlots.first().uuid)
        val steps = mutableListOf<String>()
        coEvery { snoozeScheduler.checkSnoozeDelivery(any(), any()) } answers { firstArg() }
        coEvery { snoozeScheduler.markSnoozeDelivered(any(), any()) } answers { steps += "mark" }
        every {
            notificationManager.showDoseReminderNotification(any(), any(), any(), silent = false)
        } answers { steps += "post" }
        val inGap = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        // Read after the plan and the log, before the post.
        coEvery { snoozeScheduler.getSnoozeRecords() } coAnswers {
            inGap.complete(Unit)
            resume.await()
            emptyList()
        }

        // The snooze alarm goes off; the delivery checks its record and reads the dose as due.
        backgroundScope.launch {
            handler.showSnoozedReminder(
                slots = listOf(slot),
                notificationTag = null,
                snoozeAt = testInstant(nine.plusMinutes(15)),
                now = nine.plusMinutes(15),
            )
        }
        runCurrent()
        assertTrue(inGap.isCompleted)
        // You log the dose from the widget: its clearSnoozesForSlots and its reconcile wait for
        // the lock, so they tidy up after the post.
        backgroundScope.launch { withReminderScheduleLock { steps += "widget tidy-up" } }
        runCurrent()
        resume.complete(Unit)
        runCurrent()

        assertEquals(listOf("post", "mark", "widget tidy-up"), steps)
    }

    /** Suspends the receiver's master read (Rcv:112) until the returned signal. */
    private fun pauseAfterFirstSettingsRead(): CompletableDeferred<Unit> {
        val resume = CompletableDeferred<Unit>()
        var first = true
        coEvery { settingsRepository.getCurrentSettings() } coAnswers {
            val settings = SettingsState(remindersEnabled = remindersEnabled)
            if (first) {
                first = false
                resume.await()
            }
            settings
        }
        return resume
    }

    private fun actionHandler() = MedicationReminderActionHandler(
        medicationGroupRepository = groupRepository,
        medicationLogRepository = logRepository,
        medicineStockRepository = mockk<MedicineStockRepository>(relaxed = true),
        settingsRepository = settingsRepository,
        medicationReminderScheduler = scheduler,
        medicationReminderSnoozeScheduler = snoozeScheduler,
        reminderNotificationManager = notificationManager,
        diagnosticsLogger = diagnosticsLogger,
    )

    private fun groupsOf(bundle: MedicationReminderBundle): Set<UUID> =
        bundle.slots.mapTo(mutableSetOf(), MedicationReminderSlot::groupUuid)

    private fun TestScope.deliver(plan: MedicationGroup, armedFor: LocalDateTime, now: LocalDateTime) {
        handleDoseReminder(
            groupUuid = plan.uuid,
            scheduledAt = armedFor,
            now = now,
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

    private fun dailyAtNine(uuid: String, name: String, key: MedicationKey) = MedicationGroup(
        uuid = UUID.fromString(uuid),
        name = name,
        schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.DAILY,
            interval = 1,
            since = LocalDate.of(2026, 4, 1),
            weeklyDaysOfWeek = emptySet(),
            times = listOf(LocalTime.of(9, 0)),
        ),
        medications = listOf(
            testMedicationGroupMedication(
                medicine = testMedicine(key = key),
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
            )
        ),
        notificationsEnabled = true,
        createdAt = Instant.parse("2026-04-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-04-01T00:00:00Z"),
    )
}
