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
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val stored = mutableMapOf<UUID, Instant?>()
    private val skipped = mutableMapOf<UUID, LocalDateTime>()
    private var clock: Instant = testInstant(justAfterNine)

    /** Each reminder posted with sound, and each posted silently, in order. */
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
        coEvery { reminderScheduleStore.getSkippedThrough() } answers { skipped.toMap() }
        coEvery { reminderScheduleStore.skipThrough(any(), any()) } answers { skipped[firstArg()] = secondArg() }
        coEvery { settingsRepository.getCurrentSettings() } answers { SettingsState(remindersEnabled = remindersEnabled) }
        coEvery { groupRepository.getGroups() } answers { groups }
        coEvery { groupRepository.getGroup(any()) } answers { groups.firstOrNull { group -> group.uuid == firstArg() } }
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

    /** DoseAlarmDelivery, OneSoundPerAlertTime and EachAlertRingsOnce. */
    @Test
    fun twoPlansAtOneTime_eachPostsItsOwn_andOnlyTheFirstSounds() = runTest {
        stored[planA.uuid] = testInstant(nine)
        stored[planB.uuid] = testInstant(nine)

        // The phone delivers A's alarm, then B's; each is handled.
        deliver(planA, armedFor = nine, now = justAfterNine)
        deliver(planB, armedFor = nine, now = justAfterNine)

        assertEquals(listOf(setOf(planA.uuid)), audiblePosts.map(::groupsOf))
        assertEquals(listOf(setOf(planB.uuid)), silentPosts.map(::groupsOf))
        // Each delivery armed its own plan's first repeat.
        assertEquals(
            mapOf<UUID, Instant?>(planA.uuid to testInstant(nine.plusMinutes(15)), planB.uuid to testInstant(nine.plusMinutes(15))),
            stored,
        )
    }

    /** DoseAlarmDelivery, AlertsStopAtTheirCap: each repeat sounds, then the next dose is armed. */
    @Test
    fun aDoseRingsFourTimes_eachWithSound_andThenTheNextDoseIsArmed() = runTest {
        groups = listOf(planA)
        stored[planA.uuid] = testInstant(nine)

        listOf(0L, 15L, 30L, 45L).forEach { minutes ->
            val alertAt = nine.plusMinutes(minutes)
            clock = testInstant(alertAt)
            deliver(planA, armedFor = alertAt, now = alertAt)
        }

        assertEquals(4, audiblePosts.size)
        assertEquals(mapOf<UUID, Instant?>(planA.uuid to testInstant(nine.plusDays(1))), stored)
    }

    /** DoseAlarmDelivery, SwipeKeepsTheNextAlert: a swipe changes nothing stored. */
    @Test
    fun aSwipe_keepsTheNextAlert() = runTest {
        groups = listOf(planA)
        stored[planA.uuid] = testInstant(nine)
        deliver(planA, armedFor = nine, now = justAfterNine)

        // You swipe the reminder away: its delete intent.
        actionHandler().dismissed(slots = listOf(planA.slotAt(nine)), now = testInstant(justAfterNine))
        clock = testInstant(nine.plusMinutes(15))
        deliver(planA, armedFor = nine.plusMinutes(15), now = nine.plusMinutes(15))

        assertEquals(2, audiblePosts.size)
        verify { notificationManager.showNextReminderToast(testInstant(nine.plusMinutes(15))) }
    }

    /** DoseAlarmDelivery, NoAlertAfterLoggedOrSkipped: a repeat in flight after a Skip posts nothing. */
    @Test
    fun aSkip_stopsTheDosesAlerts_evenOneAlreadyInFlight() = runTest {
        groups = listOf(planA)
        stored[planA.uuid] = testInstant(nine)
        deliver(planA, armedFor = nine, now = justAfterNine)
        // The 09:15 alarm leaves AlarmManager; before its receiver runs, you tap Skip.
        clock = testInstant(nine.plusMinutes(15))
        actionHandler().skip(slots = listOf(planA.slotAt(nine)), notificationTag = "tag")
        // Skip's own reschedule moved the alarm to tomorrow's dose.
        assertEquals(mapOf<UUID, Instant?>(planA.uuid to testInstant(nine.plusDays(1))), stored)
        deliver(planA, armedFor = nine.plusMinutes(15), now = nine.plusMinutes(15))

        assertEquals(1, audiblePosts.size)
        assertEquals(mapOf<UUID, Instant?>(planA.uuid to testInstant(nine.plusDays(1))), stored)
    }

    /** A delivery is identified by its instant: an old alarm after a zone change posts nothing. */
    @Test
    fun anOldAlarmSharingTheNewOnesLocalTime_postsNothing() = runTest {
        groups = listOf(planA)
        // The zone pass armed A for 09:00 here; the alarm in flight is for 09:00 in a zone nine
        // hours ahead, the same local time.
        stored[planA.uuid] = testInstant(nine)
        clock = testInstant(nine)

        deliver(planA, armedFor = testInstant(nine).minusSeconds(9 * 3600), now = nine)

        assertEquals(0, audiblePosts.size)
        // It marked nothing: the 09:00 alarm still posts.
        assertEquals(mapOf<UUID, Instant?>(planA.uuid to testInstant(nine)), stored)
    }

    /** DoseAlarmDelivery, NoRingAfterRemindersOff. */
    @Test
    fun aDeliveryPastItsMasterCheck_postsNothingOnceSettingsTurnedRemindersOff() = runTest {
        groups = listOf(planA)
        stored[planA.uuid] = testInstant(nine)
        val resume = pauseAfterFirstSettingsRead()

        // The phone delivers A's alarm; the receiver reads reminders on.
        deliver(planA, armedFor = nine, now = justAfterNine)
        // You turn reminders off; Settings' full pass runs to the end and empties the store.
        remindersEnabled = false
        scheduler.rescheduleAll(now = testInstant(justAfterNine))
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
        stored[planA.uuid] = testInstant(nine)
        val resume = pauseAfterFirstSettingsRead()

        deliver(planA, armedFor = nine, now = justAfterNine)
        // You turn reminders off. The receiver takes the lock before Settings' pass does.
        remindersEnabled = false
        resume.complete(Unit)
        runCurrent()
        scheduler.rescheduleAll(now = testInstant(justAfterNine))

        assertEquals(0, audiblePosts.size)
        assertTrue(stored.isEmpty())
    }

    /** DoseAlarmDelivery, EachAlertRingsOnce: an alarm armed again while in flight posts once. */
    @Test
    fun anAlarmArmedAgainWhileInFlight_postsOnce_whenItsPlanIsArchivedBetween() = runTest {
        stored[planA.uuid] = testInstant(nine)
        stored[planB.uuid] = testInstant(nine)

        // A's 09:00 alarm has left AlarmManager. Before its receiver runs, A is rescheduled
        // (editor save, widget log): the passed 09:00, still missing, is armed again.
        scheduler.rescheduleGroup(planA.uuid, after = testInstant(justAfterNine))
        assertEquals(testInstant(nine), stored[planA.uuid])
        // The first delivery posts A 09:00 and moves A to its repeat.
        deliver(planA, armedFor = nine, now = justAfterNine)
        // You archive A: the write, then cancelReminder drops its key.
        groups = listOf(planA.copy(archivedAt = testInstant(justAfterNine)), planB)
        scheduler.cancelReminder(planA.uuid)
        // The alarm armed again arrives: A has no key, so it posts nothing.
        deliver(planA, armedFor = nine, now = justAfterNine.plusSeconds(1))

        assertEquals(listOf(setOf(planA.uuid)), audiblePosts.map(::groupsOf))
    }

    /** H1's null branch: v1.4.2 stored A with no time, and its alarm carries none. */
    @Test
    fun aKeyWithNoTime_takesAV142Alarm() = runTest {
        groups = listOf(planA)
        stored[planA.uuid] = null

        deliver(planA, armedFor = null, now = justAfterNine, legacyScheduledAt = nine)

        assertEquals(listOf(setOf(planA.uuid)), audiblePosts.map(::groupsOf))
        assertEquals(mapOf<UUID, Instant?>(planA.uuid to testInstant(nine.plusMinutes(15))), stored)
    }

    /**
     * DoseAlarmDelivery -failure, EachActivePlanArmed (open, R5): the delivered plan's own
     * reschedule fails after its post, and it has no alarm until the next full pass.
     */
    @Test
    fun aRescheduleThatFails_cancelsNothing_andLeavesThePlanUnarmed() = runTest {
        groups = listOf(planA)
        stored[planA.uuid] = testInstant(nine)
        var reads = 0
        // The receiver's read works; its reschedule's read fails.
        coEvery { groupRepository.getGroup(planA.uuid) } answers {
            if (reads++ == 0) planA else throw IOException("database busy")
        }

        deliver(planA, armedFor = nine, now = justAfterNine)

        assertEquals(listOf(setOf(planA.uuid)), audiblePosts.map(::groupsOf))
        assertTrue(stored.isEmpty())
        // The reads come before the cancel, so nothing was cancelled.
        verify(exactly = 0) { alarmManager.cancel(any<PendingIntent>()) }
    }

    /** A pass that fails while arming leaves keys, which the next pass arms, not alarms with none. */
    @Test
    fun aPassThatFailsWhileArming_hasStoredWhatItPlanned() = runTest {
        every { alarmManager.setExactAndAllowWhileIdle(any(), any(), any<PendingIntent>()) } throws
                IllegalStateException("alarm service gone")

        runCatching { scheduler.rescheduleAll(now = testInstant(justAfterNine)) }

        assertEquals(
            mapOf<UUID, Instant?>(
                planA.uuid to testInstant(nine.plusMinutes(15)),
                planB.uuid to testInstant(nine.plusMinutes(15)),
            ),
            stored,
        )
    }

    /** Suspends the receiver's master read until the returned signal. */
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
        reminderNotificationManager = notificationManager,
        diagnosticsLogger = diagnosticsLogger,
    )

    private fun groupsOf(bundle: MedicationReminderBundle): Set<UUID> =
        bundle.slots.mapTo(mutableSetOf(), MedicationReminderSlot::groupUuid)

    private fun MedicationGroup.slotAt(at: LocalDateTime) =
        MedicationReminderSlot(uuid, at, schedule.timeSlots.first().uuid)

    private fun TestScope.deliver(plan: MedicationGroup, armedFor: LocalDateTime, now: LocalDateTime) =
        deliver(plan, testInstant(armedFor), now)

    private fun TestScope.deliver(
        plan: MedicationGroup,
        armedFor: Instant?,
        now: LocalDateTime,
        legacyScheduledAt: LocalDateTime? = null,
    ) {
        handleDoseReminder(
            groupUuid = plan.uuid,
            alertAt = armedFor,
            legacyScheduledAt = legacyScheduledAt,
            now = testInstant(now),
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
