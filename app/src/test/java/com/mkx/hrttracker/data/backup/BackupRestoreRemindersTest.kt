package com.mkx.hrttracker.data.backup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.healthconnect.HealthConnectCapabilityReconciler
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.reminder.ReminderCapabilityReconciler
import com.mkx.hrttracker.reminder.ReminderNotificationManager
import com.mkx.hrttracker.reminder.ReminderScheduleStore
import com.mkx.hrttracker.reminder.ShownDoseReminder
import com.mkx.hrttracker.reminder.buildMedicationReminderBundle
import com.mkx.hrttracker.reminder.canPostNotifications
import com.mkx.hrttracker.reminder.canScheduleExactAlarms
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import com.mkx.hrttracker.widget.WidgetAppearanceRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

/**
 * What a restore's post-commit steps leave the reminders in, against the real
 * BackupRestoreService, ReminderCapabilityReconciler and MedicationReminderScheduler. These
 * were the replays of the findings in .claude/skills/verify-backup-restore/model/, flipped
 * once fixed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupRestoreRemindersTest {
    private val context: Context = mockk(relaxed = true)
    private val alarmManager: AlarmManager = mockk(relaxed = true)
    private val databaseHolder: DatabaseHolder = mockk()
    private val database: HrtTrackerDatabase = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val homeSnapshotRepository: HomeSnapshotRepository = mockk {
        every { generation } returns MutableStateFlow(1L)
    }
    private val groupRepository: MedicationGroupRepository = mockk()
    private val logRepository: MedicationLogRepository = mockk()
    private val reminderScheduleStore: ReminderScheduleStore = mockk()
    private val notificationManager: ReminderNotificationManager = mockk(relaxed = true)
    private val widgetAppearanceRepository: WidgetAppearanceRepository = mockk()
    private val diagnosticsLogger: AppDiagnosticsLogger = mockk(relaxed = true)
    private val backupCrypto = BackupCrypto(TestBackupArgon2KeyDeriver())
    private lateinit var service: BackupRestoreService

    private val nine = LocalDateTime.of(2026, 4, 20, 9, 0)
    private val planU = dailyAtNine(count = 1)

    /** The schedule store, the skips and Settings, as the steps change them. */
    private val stored = mutableMapOf<UUID, Instant?>()
    private val skipped = mutableMapOf<UUID, LocalDateTime>()
    private var reconcilePending = false
    private var remindersEnabled = true
    private var notificationAccess = true

    @Before
    fun setUp() {
        mockkStatic(PendingIntent::class)
        mockkStatic(Uri::class)
        mockkStatic("com.mkx.hrttracker.reminder.NotificationPermissionsKt")
        every { canPostNotifications(any()) } answers { notificationAccess }
        every { canScheduleExactAlarms(any<Context>()) } returns true
        every { PendingIntent.getBroadcast(any(), any(), any(), any<Int>()) } returns mockk(relaxed = true)
        every { Uri.parse(any()) } returns mockk(relaxed = true)
        every { context.packageName } returns "com.mkx.hrttracker"
        every { context.getSystemService(AlarmManager::class.java) } returns alarmManager
        every { alarmManager.canScheduleExactAlarms() } returns true
        every { databaseHolder.get() } returns database
        coEvery { databaseHolder.runTransaction(any()) } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { homeSnapshotRepository.runHomeDataMutation<Unit>(any()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
        }
        coEvery { settingsRepository.getCurrentSettings() } answers { SettingsState(remindersEnabled = remindersEnabled) }
        coEvery { settingsRepository.setRemindersEnabled(any()) } answers { remindersEnabled = firstArg() }
        coEvery { widgetAppearanceRepository.setDefault(any()) } just Runs
        coEvery { reminderScheduleStore.getScheduledReminders() } answers { stored.toMap() }
        coEvery { reminderScheduleStore.addScheduledReminder(any(), any()) } answers { stored[firstArg()] = secondArg() }
        coEvery { reminderScheduleStore.removeScheduledGroupUuid(any()) } answers { stored.remove(firstArg<UUID>()) }
        coEvery { reminderScheduleStore.replaceScheduledReminders(any()) } answers {
            stored.clear()
            stored.putAll(firstArg<Map<UUID, Instant>>())
        }
        coEvery { reminderScheduleStore.setReconcilePending(any()) } answers { reconcilePending = firstArg() }
        coEvery { reminderScheduleStore.isReconcilePending() } answers { reconcilePending }
        coEvery { reminderScheduleStore.getSkippedThrough() } answers { skipped.toMap() }
        coEvery { reminderScheduleStore.clearSkippedThrough() } answers { skipped.clear() }
        coEvery { logRepository.getScheduledGroupEntriesSince(any()) } returns emptyList()

        val scheduler = MedicationReminderScheduler(
            context = context,
            medicationGroupRepository = groupRepository,
            medicationLogRepository = logRepository,
            settingsRepository = settingsRepository,
            reminderScheduleStore = reminderScheduleStore,
            reminderNotificationManager = notificationManager,
            homeSnapshotRepository = homeSnapshotRepository,
            diagnosticsLogger = diagnosticsLogger,
        )
        service = BackupRestoreService(
            context = context,
            databaseHolder = databaseHolder,
            settingsRepository = settingsRepository,
            homeSnapshotRepository = homeSnapshotRepository,
            medicationReminderScheduler = scheduler,
            reminderCapabilityReconciler = ReminderCapabilityReconciler(
                context = context,
                appScope = CoroutineScope(UnconfinedTestDispatcher()),
                settingsRepository = settingsRepository,
                medicationReminderScheduler = scheduler,
                diagnosticsLogger = diagnosticsLogger,
            ),
            widgetAppearanceRepository = widgetAppearanceRepository,
            backupCrypto = backupCrypto,
            healthConnectCapabilityReconciler = mockk<HealthConnectCapabilityReconciler>(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /** BackupRestore model, RepostsOnScreenFollowTheRestore (decision B3 b). */
    @Test
    fun aRestoreRepostsEveryReminderOnScreen_evenOneWhoseDoseIsUnchanged() = runTest {
        // u's 09:00 reminder is on screen; the backup holds u as it is.
        val shownBundle = buildMedicationReminderBundle(nine, planU, emptyList())!!
        every { notificationManager.activeDoseReminders() } returns
            mapOf(shownBundle.notificationTag to ShownDoseReminder(shownBundle.slots, shownBundle.contentHash))
        coEvery { groupRepository.getGroups() } returns listOf(planU)

        service.restoreBackupBytes(emptySnapshotBytes(), password = "password")

        // In place, silently, so it shows in the restored language and Hide medication details.
        verify(exactly = 1) {
            notificationManager.showDoseReminderNotification(
                match { bundle -> bundle.notificationTag == shownBundle.notificationTag },
                any(),
                silent = true,
            )
        }
        verify(exactly = 0) { notificationManager.cancelDoseReminderNotification(any()) }
    }

    /** BackupRestore model, NoRemindersWithoutAccess (decision B11). */
    @Test
    fun aRestoreThatTurnsRemindersOn_onAPhoneWithoutAccess_turnsThemOffAgain() = runTest {
        notificationAccess = false
        // The restored settings say on (the backup's remindersEnabled; restoreSettings is a
        // no-op mock here, so the switch starts on as if it had written it).
        remindersEnabled = true
        coEvery { groupRepository.getGroups() } returns listOf(planU)

        service.restoreBackupBytes(emptySnapshotBytes(), password = "password")

        assertFalse(remindersEnabled)
        assertTrue("no alarm is armed into nothing", stored.isEmpty())
        verify { notificationManager.cancelAllDoseReminderNotifications() }
    }

    /** BackupRestore model, RemindersFollowRestoredData and NoOldSkipAfterRestore. */
    @Test
    fun aCancelPastTheCommitStillClearsTheSkipsAndReschedules() = runTest {
        coEvery { groupRepository.getGroups() } returns listOf(planU)
        skipped[planU.uuid] = nine
        // As the real one's lock would: throws if it runs cancelled.
        coEvery { reminderScheduleStore.clearSkippedThrough() } coAnswers {
            currentCoroutineContext().ensureActive()
            skipped.clear()
        }
        val inFollowUps = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        // The widget appearance write, the first step past the commit, waits for the cancel.
        coEvery { widgetAppearanceRepository.setDefault(any()) } coAnswers {
            inFollowUps.complete(Unit)
            release.await()
        }

        val restore = backgroundScope.launch {
            service.restoreBackupBytes(emptySnapshotBytes(), password = "password")
        }
        inFollowUps.await()
        coVerify(exactly = 1) { databaseHolder.runTransaction(any()) }
        // The restore is cancelled (viewModelScope) past the commit.
        restore.cancel()
        release.complete(Unit)
        restore.join()

        // u is armed from the restored data, the old skip is gone, and the reconcile settled.
        coVerify(exactly = 1) { reminderScheduleStore.replaceScheduledReminders(match { planU.uuid in it }) }
        assertTrue(skipped.isEmpty())
        assertFalse(reconcilePending)
    }

    @Test
    fun aCancellationExceptionFromTheMarksStoreWriteLeavesNoMarkOpen() = runTest {
        coEvery { groupRepository.getGroups() } returns listOf(planU)
        // The store's own failure: nothing cancelled the restore.
        coEvery { reminderScheduleStore.setReconcilePending(true) } throws CancellationException("store scope")

        val outcome = service.restoreBackupBytes(emptySnapshotBytes(), password = "password")

        // The restore went on without the mark, and its pass, finding no mark open, settled the flag.
        assertEquals(BackupRestoreOutcome.RESTORED, outcome)
        coVerify(exactly = 1) { reminderScheduleStore.setReconcilePending(false) }
    }

    /** A process's scheduler and capability reconciler, over the same stores. */
    private fun process(): Pair<MedicationReminderScheduler, ReminderCapabilityReconciler> {
        val scheduler = MedicationReminderScheduler(
            context = context,
            medicationGroupRepository = groupRepository,
            medicationLogRepository = logRepository,
            settingsRepository = settingsRepository,
            reminderScheduleStore = reminderScheduleStore,
            reminderNotificationManager = notificationManager,
            homeSnapshotRepository = homeSnapshotRepository,
            diagnosticsLogger = diagnosticsLogger,
        )
        return scheduler to ReminderCapabilityReconciler(
            context = context,
            appScope = CoroutineScope(UnconfinedTestDispatcher()),
            settingsRepository = settingsRepository,
            medicationReminderScheduler = scheduler,
            diagnosticsLogger = diagnosticsLogger,
        )
    }

    private fun showUsReminder() {
        val shown = buildMedicationReminderBundle(nine, planU, emptyList())!!
        every { notificationManager.activeDoseReminders() } returns
            mapOf(shown.notificationTag to ShownDoseReminder(shown.slots, shown.contentHash))
        coEvery { groupRepository.getGroups() } returns listOf(planU)
    }

    /**
     * BackupRestore model, RepostsFollowTheRestore with AppStarts' two launches in the unfavorable
     * order: the first resume's capability reconcile settles the flag before StartupPreloader's
     * pass reads it. Mark Commit Die ResumeReconcile StartupPass.
     */
    @Test
    fun afterADeathMidRestore_theNewProcessesFirstPassReposts_whicheverLaunchRunsFirst() = runTest {
        showUsReminder()
        val (restoring, _) = process()
        restoring.markReconcilePending()
        // The commit, then the process dies.
        val (started, reconciler) = process()
        reconciler.reconcile(reason = "main_activity_on_resume")
        started.rescheduleAll()

        verify(exactly = 1) { notificationManager.showDoseReminderNotification(any(), any(), silent = true) }
        assertFalse(reconcilePending)
    }

    /**
     * BackupRestore model, RepostsFollowTheRestore (finding 2 at the first sync, now closed): a forced
     * pass past the gate reposts, as it starts with the reconcile owed, and a death after it leaves
     * nothing unreposted. Mark Commit LeaveGate OtherPass Die AppStarts.
     */
    @Test
    fun aForcedPassPastTheGate_thenADeath_hasRepostedTheReminders() = runTest {
        showUsReminder()
        val (restoring, otherReconciler) = process()
        restoring.markReconcilePending().committed()
        otherReconciler.reconcile(reason = "receiver_time_set", forceReschedule = true)
        // The process dies; the next start owes nothing.
        val (started, _) = process()
        started.rescheduleAll()

        verify(exactly = 1) { notificationManager.showDoseReminderNotification(any(), any(), silent = true) }
    }

    private fun emptySnapshotBytes(): ByteArray = backupCrypto.encryptSnapshotJson(
        json = BackupSnapshotJsonCodec.encode(
            BackupSnapshot(
                exportedAtEpochMillis = 1_777_777_777_000L,
                app = BackupAppSnapshot(packageName = "com.mkx.hrttracker"),
                settings = BackupSettingsSnapshot(
                    darkModeOption = "FOLLOW_SYSTEM",
                    adaptiveColorEnabled = true,
                    remindersEnabled = true,
                    appLockGracePeriodOption = "ONE_MINUTE",
                    hideScreenContentEnabled = false,
                    onboardingCompleted = true,
                    appLanguageOption = "ENGLISH",
                    homeE2DisplayUnit = BloodUnitKey.PG_ML.storageValue,
                    calibrationDefaultUnits = emptyMap(),
                ),
                userProfile = BackupUserProfileSnapshot(
                    weightKg = null,
                    weightOriginalValue = null,
                    weightOriginalUnit = "KILOGRAMS",
                ),
                medicines = emptyList(),
                medicationGroups = emptyList(),
                medicationLogs = emptyList(),
                customBloodAnalytes = emptyList(),
                bloodTestPanels = emptyList(),
            )
        ),
        password = "password".toCharArray(),
    )

    private fun dailyAtNine(count: Int) = MedicationGroup(
        uuid = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001"),
        name = "U",
        schedule = MedicationGroupSchedule(
            type = MedicationGroupScheduleType.DAILY,
            interval = 1,
            since = LocalDate.of(2026, 4, 1),
            weeklyDaysOfWeek = emptySet(),
            times = listOf(LocalTime.of(9, 0)),
        ),
        medications = listOf(
            testMedicationGroupMedication(
                uuid = UUID.fromString("aa111111-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                medicine = testMedicine(key = MedicationKey.ESTRADIOL),
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
                count = count,
            )
        ),
        notificationsEnabled = true,
        createdAt = Instant.parse("2026-04-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-04-01T00:00:00Z"),
    )
}
