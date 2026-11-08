package com.mkx.hrttracker.startup

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.DatabaseKeyUnavailableException
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.repository.HomeCacheState
import com.mkx.hrttracker.data.repository.HomeSnapshotRecord
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.UserProfileRepository
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.reminder.MedicationReminderSnoozeScheduler
import com.mkx.hrttracker.reminder.ReminderScheduleStore
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import com.mkx.hrttracker.util.ToastManager
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean

class StartupPreloaderTest {
    private val databaseHolder: DatabaseHolder = mockk()
    private val homeSnapshotRepository: HomeSnapshotRepository = mockk()
    private val medicationGroupRepository: MedicationGroupRepository = mockk()
    private val medicationLogRepository: MedicationLogRepository = mockk()
    private val medicineRepository: com.mkx.hrttracker.data.repository.MedicineRepository = mockk {
        coEvery { getAll() } returns emptyList()
    }
    private val userProfileRepository: UserProfileRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val medicationReminderScheduler: MedicationReminderScheduler = mockk()
    private val medicationReminderSnoozeScheduler: MedicationReminderSnoozeScheduler = mockk()
    private val diagnosticsLogger: AppDiagnosticsLogger = mockk(relaxed = true)

    @Test
    fun startAfterFirstHomeFrame_reschedulesRemindersAfterWarmup() = runTest {
        val database: HrtTrackerDatabase = mockk()
        val openHelper: SupportSQLiteOpenHelper = mockk()
        val writableDatabase: SupportSQLiteDatabase = mockk()
        every { databaseHolder.get() } returns database
        every { database.openHelper } returns openHelper
        every { openHelper.writableDatabase } returns writableDatabase
        coEvery { medicationGroupRepository.getGroups() } returns emptyList()
        coEvery { medicationLogRepository.getEntries() } returns emptyList()
        coEvery { userProfileRepository.getCurrentProfile() } returns UserProfile()
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState()
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } returns Unit
        coEvery { medicationReminderSnoozeScheduler.rescheduleAll(any()) } returns Unit

        StartupPreloader(
            appScope = CoroutineScope(Dispatchers.IO),
            databaseHolder = databaseHolder,
            homeSnapshotRepository = homeSnapshotRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            userProfileRepository = userProfileRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            diagnosticsLogger = diagnosticsLogger,
        ).startAfterFirstHomeFrame()

        coVerify(timeout = 1_000, exactly = 1) { medicationReminderScheduler.rescheduleAll(any()) }
        coVerify(
            timeout = 1_000,
            exactly = 1
        ) { medicationReminderSnoozeScheduler.rescheduleAll(any()) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun startReminderRescheduleFromSnapshot_usesSnapshotWithoutWarmingDatabase() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val snapshot: HomeSnapshotRecord = mockk()
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        coEvery { homeSnapshotRepository.currentState() } returns HomeCacheState(0L, snapshot, e2Inputs = null)
        coEvery { medicationReminderScheduler.isReconcilePending() } returns false
        coEvery {
            medicationReminderScheduler.rescheduleFromHomeSnapshot(
                snapshot = snapshot,
                now = now,
            )
        } returns true
        coEvery { medicationReminderSnoozeScheduler.rescheduleAll(now = now) } returns Unit

        StartupPreloader(
            appScope = CoroutineScope(dispatcher),
            databaseHolder = databaseHolder,
            homeSnapshotRepository = homeSnapshotRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            userProfileRepository = userProfileRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            diagnosticsLogger = diagnosticsLogger,
        ).startReminderRescheduleFromSnapshot(now = now)
        advanceUntilIdle()

        coVerify(
            timeout = 1_000,
            exactly = 1
        ) { homeSnapshotRepository.currentState() }
        coVerify(timeout = 1_000, exactly = 1) {
            medicationReminderScheduler.rescheduleFromHomeSnapshot(
                snapshot = snapshot,
                now = now,
            )
        }
        coVerify(
            timeout = 1_000,
            exactly = 1
        ) { medicationReminderSnoozeScheduler.rescheduleAll(now = now) }
        verify(exactly = 0) { databaseHolder.get() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun startReminderRescheduleFromSnapshot_reschedulesSnoozesWhenNoUsableSnapshotExists() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val now = LocalDateTime.of(2026, 5, 6, 10, 15)
            coEvery { homeSnapshotRepository.currentState() } returns null
            coEvery { medicationReminderScheduler.isReconcilePending() } returns false
            coEvery { medicationReminderSnoozeScheduler.rescheduleAll(now = now) } returns Unit

            StartupPreloader(
                appScope = CoroutineScope(dispatcher),
                databaseHolder = databaseHolder,
                homeSnapshotRepository = homeSnapshotRepository,
                medicationGroupRepository = medicationGroupRepository,
                medicationLogRepository = medicationLogRepository,
                medicineRepository = medicineRepository,
                userProfileRepository = userProfileRepository,
                settingsRepository = settingsRepository,
                medicationReminderScheduler = medicationReminderScheduler,
                medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
                diagnosticsLogger = diagnosticsLogger,
            ).startReminderRescheduleFromSnapshot(now = now)
            advanceUntilIdle()

            coVerify(
                timeout = 1_000,
                exactly = 1
            ) { medicationReminderSnoozeScheduler.rescheduleAll(now = now) }
            coVerify(timeout = 1_000, exactly = 0) {
                medicationReminderScheduler.rescheduleFromHomeSnapshot(any(), any())
            }
            verify(exactly = 0) { databaseHolder.get() }
            verify {
                diagnosticsLogger.info(
                    "StartupPreloader",
                    match { message ->
                        "snapshot_reminder_reschedule_no_usable_snapshot" in message &&
                                "now=2026-05-06T10:15" in message
                    }
                )
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun startReminderRescheduleFromSnapshot_reconcilesFromRoomWhenAReconcileIsPending() = runTest {
        // A restore committed, and the process died before its reschedule: the
        // snapshot can predate the restore, and only rescheduleAll reconciles what's shown.
        val dispatcher = StandardTestDispatcher(testScheduler)
        val snapshot: HomeSnapshotRecord = mockk()
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        coEvery { homeSnapshotRepository.currentState() } returns HomeCacheState(0L, snapshot, e2Inputs = null)
        coEvery { medicationReminderScheduler.isReconcilePending() } returns true
        coEvery { medicationReminderScheduler.rescheduleFromHomeSnapshot(any(), any()) } returns true
        coEvery { medicationReminderScheduler.rescheduleAll(now = now) } returns Unit
        coEvery { medicationReminderSnoozeScheduler.rescheduleAll(now = now) } returns Unit

        StartupPreloader(
            appScope = CoroutineScope(dispatcher),
            databaseHolder = databaseHolder,
            homeSnapshotRepository = homeSnapshotRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            userProfileRepository = userProfileRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            diagnosticsLogger = diagnosticsLogger,
        ).startReminderRescheduleFromSnapshot(now = now)
        advanceUntilIdle()

        coVerify(timeout = 1_000, exactly = 1) { medicationReminderScheduler.rescheduleAll(now = now) }
        coVerify(exactly = 0) { medicationReminderScheduler.rescheduleFromHomeSnapshot(any(), any()) }
    }

    @Test
    fun startReminderRescheduleFromSnapshot_reconcilesFromRoomForAMarkThatAPassBeforeItsCommitLeft() = runTest {
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        // The reminder schedule store, as DataStore keeps it from one process to the next.
        val reconcilePending = AtomicBoolean(false)
        val reminderScheduleStore: ReminderScheduleStore = mockk {
            coEvery { getScheduledReminders() } returns emptyMap()
            coEvery { replaceScheduledReminders(any()) } returns Unit
            coEvery { isReconcilePending() } answers { reconcilePending.get() }
            coEvery { setReconcilePending(any()) } answers { reconcilePending.set(firstArg()) }
        }
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState(remindersEnabled = true)
        coEvery { medicationGroupRepository.getGroups() } returns emptyList()
        coEvery { medicationLogRepository.getScheduledGroupEntriesSince(any()) } returns emptyList()
        coEvery { medicationReminderSnoozeScheduler.rescheduleAll(now = now) } returns Unit
        fun schedulerOfAProcess() = MedicationReminderScheduler(
            context = mockk(),
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            settingsRepository = settingsRepository,
            reminderScheduleStore = reminderScheduleStore,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            reminderNotificationManager = mockk { every { activeDoseReminders() } returns emptyMap() },
            homeSnapshotRepository = homeSnapshotRepository,
            diagnosticsLogger = diagnosticsLogger,
        )
        // The earlier process: a restore marks, an unrelated pass reads the data it replaces,
        // the restore commits, and its own pass fails.
        val earlier = schedulerOfAProcess()
        val restoreMark = earlier.markReconcilePending()
        earlier.rescheduleAll(now)
        restoreMark.committed()
        coEvery { medicationGroupRepository.getGroups() } throws IllegalStateException("database closed")
        runCatching { earlier.rescheduleAll(now) }
        assertTrue("the restore's reconcile is owed at the next start", reconcilePending.get())
        coEvery { medicationGroupRepository.getGroups() } returns emptyList()

        StartupPreloader(
            appScope = CoroutineScope(Dispatchers.IO),
            databaseHolder = databaseHolder,
            homeSnapshotRepository = homeSnapshotRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            userProfileRepository = userProfileRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = schedulerOfAProcess(),
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            diagnosticsLogger = diagnosticsLogger,
        ).startReminderRescheduleFromSnapshot(now = now)

        // Last in the startup job, on either path.
        coVerify(timeout = 1_000) { medicationReminderSnoozeScheduler.rescheduleAll(now = now) }
        coVerify(exactly = 0) { homeSnapshotRepository.currentState() }
        assertFalse("the startup reconcile settles the mark", reconcilePending.get())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun startReminderRescheduleFromSnapshot_reschedulesSnoozesAfterTheSnapshotPass() = runTest {
        // The snooze pass reads Room once records exist; the snapshot pass shouldn't wait on it.
        val dispatcher = StandardTestDispatcher(testScheduler)
        val snapshot: HomeSnapshotRecord = mockk()
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        coEvery { homeSnapshotRepository.currentState() } returns HomeCacheState(0L, snapshot, e2Inputs = null)
        coEvery { medicationReminderScheduler.isReconcilePending() } returns false
        coEvery { medicationReminderScheduler.rescheduleFromHomeSnapshot(snapshot, now) } returns true
        coEvery { medicationReminderSnoozeScheduler.rescheduleAll(now = now) } returns Unit

        preloader(dispatcher).startReminderRescheduleFromSnapshot(now = now)
        advanceUntilIdle()

        // On Dispatchers.IO, past the test scheduler: wait for both before checking their order.
        coVerify(timeout = 1_000) { medicationReminderScheduler.rescheduleFromHomeSnapshot(snapshot, now) }
        coVerify(timeout = 1_000) { medicationReminderSnoozeScheduler.rescheduleAll(now = now) }
        coVerifyOrder {
            medicationReminderScheduler.rescheduleFromHomeSnapshot(snapshot, now)
            medicationReminderSnoozeScheduler.rescheduleAll(now = now)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun startReminderRescheduleFromSnapshot_reconcilesFromRoomWhenTheSnapshotIsStale() = runTest {
        // A save since the snapshot was read: the scheduler turned the snapshot down.
        val dispatcher = StandardTestDispatcher(testScheduler)
        val snapshot: HomeSnapshotRecord = mockk()
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        coEvery { homeSnapshotRepository.currentState() } returns HomeCacheState(0L, snapshot, e2Inputs = null)
        coEvery { medicationReminderScheduler.isReconcilePending() } returns false
        coEvery { medicationReminderScheduler.rescheduleFromHomeSnapshot(snapshot, now) } returns false
        coEvery { medicationReminderScheduler.rescheduleAll(now = now) } returns Unit
        coEvery { medicationReminderSnoozeScheduler.rescheduleAll(now = now) } returns Unit

        preloader(dispatcher).startReminderRescheduleFromSnapshot(now = now)
        advanceUntilIdle()

        coVerify(timeout = 1_000, exactly = 1) { medicationReminderScheduler.rescheduleAll(now = now) }
    }

    private fun preloader(dispatcher: CoroutineDispatcher) = StartupPreloader(
        appScope = CoroutineScope(dispatcher),
        databaseHolder = databaseHolder,
        homeSnapshotRepository = homeSnapshotRepository,
        medicationGroupRepository = medicationGroupRepository,
        medicationLogRepository = medicationLogRepository,
        medicineRepository = medicineRepository,
        userProfileRepository = userProfileRepository,
        settingsRepository = settingsRepository,
        medicationReminderScheduler = medicationReminderScheduler,
        medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
        diagnosticsLogger = diagnosticsLogger,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun startReminderRescheduleFromWarmDatabase_reschedulesWithoutWarmupPass() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        coEvery { medicationReminderScheduler.rescheduleAll(now = now) } returns Unit
        coEvery { medicationReminderSnoozeScheduler.rescheduleAll(now = now) } returns Unit

        StartupPreloader(
            appScope = CoroutineScope(dispatcher),
            databaseHolder = databaseHolder,
            homeSnapshotRepository = homeSnapshotRepository,
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            userProfileRepository = userProfileRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            diagnosticsLogger = diagnosticsLogger,
        ).startReminderRescheduleFromWarmDatabase(now = now)
        advanceUntilIdle()

        coVerify(
            timeout = 1_000,
            exactly = 1
        ) { medicationReminderScheduler.rescheduleAll(now = now) }
        coVerify(
            timeout = 1_000,
            exactly = 1
        ) { medicationReminderSnoozeScheduler.rescheduleAll(now = now) }
        verify(exactly = 0) { databaseHolder.get() }
        coVerify(exactly = 0) { medicationGroupRepository.getGroups() }
        coVerify(exactly = 0) { medicationLogRepository.getEntries() }
        coVerify(exactly = 0) { userProfileRepository.getCurrentProfile() }
        coVerify(exactly = 0) { settingsRepository.getCurrentSettings() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun startReminderRescheduleFromWarmDatabase_reschedulesSnoozesWhenRoomReminderRescheduleFails() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val now = LocalDateTime.of(2026, 5, 6, 10, 15)
            coEvery { medicationReminderScheduler.rescheduleAll(now = now) } throws
                    RuntimeException("Room unavailable")
            coEvery { medicationReminderSnoozeScheduler.rescheduleAll(now = now) } returns Unit

            StartupPreloader(
                appScope = CoroutineScope(dispatcher),
                databaseHolder = databaseHolder,
                homeSnapshotRepository = homeSnapshotRepository,
                medicationGroupRepository = medicationGroupRepository,
                medicationLogRepository = medicationLogRepository,
                medicineRepository = medicineRepository,
                userProfileRepository = userProfileRepository,
                settingsRepository = settingsRepository,
                medicationReminderScheduler = medicationReminderScheduler,
                medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
                diagnosticsLogger = diagnosticsLogger,
            ).startReminderRescheduleFromWarmDatabase(now = now)
            advanceUntilIdle()

            coVerify(
                timeout = 1_000,
                exactly = 1
            ) { medicationReminderScheduler.rescheduleAll(now = now) }
            coVerify(
                timeout = 1_000,
                exactly = 1
            ) { medicationReminderSnoozeScheduler.rescheduleAll(now = now) }
            verify(exactly = 0) { databaseHolder.get() }
        }

    // Intent: a database key that can't be recovered (DatabaseKeyUnavailableException)
    // closes the app from the in-app startup open: no database opens again until the data is
    // cleared. The preloader only signals it; MainActivity toasts on each exit.
    @Test
    fun startAfterFirstHomeFrame_signalsTheExitWithoutToastingWhenTheDatabaseKeyIsLost() {
        every { databaseHolder.openAndSignal() } throws
                DatabaseKeyUnavailableException("The database exists, but its stored passphrase is missing.")

        val preloader = startAfterFirstHomeFrameTwice()

        assertTrue(
            "A lost key must signal MainActivity to toast and close the app",
            preloader.databaseKeyLost.value,
        )
        verify(exactly = 0) { ToastManager.showMessageRes(any()) }
        verify(exactly = 0) { ToastManager.showMessage(any<String>()) }
    }

    // Guard: an open that succeeds tells nothing and keeps the app open.
    @Test
    fun startAfterFirstHomeFrame_showsNoToastAndStaysOpenWhenTheDatabaseOpens() {
        every { databaseHolder.openAndSignal() } returns mockk()

        val preloader = startAfterFirstHomeFrameTwice()

        verify(exactly = 0) { ToastManager.showMessageRes(any()) }
        verify(exactly = 0) { ToastManager.showMessage(any<String>()) }
        assertFalse(preloader.databaseKeyLost.value)
    }

    // Guard: any other open failure still has its key, and clearing the data would lose it.
    @Test
    fun startAfterFirstHomeFrame_showsNoToastAndStaysOpenWhenTheOpenFailsWithItsKey() {
        every { databaseHolder.openAndSignal() } throws IOException("disk I/O error")

        val preloader = startAfterFirstHomeFrameTwice()

        verify(exactly = 0) { ToastManager.showMessageRes(any()) }
        verify(exactly = 0) { ToastManager.showMessage(any<String>()) }
        assertFalse(preloader.databaseKeyLost.value)
    }

    // MainActivity is a Hilt entry point with no Hilt test setup, so this reads its source:
    // the first-home-frame effect that starts the open toasts and finishes the task once the
    // key is lost. The signal is sticky, so an activity recreated before the exit closes too.
    @Test
    fun mainActivity_toastsThenFinishesItsTaskOnceTheDatabaseKeyIsLost() {
        val effect = mainActivityFirstHomeFrameEffect()
        val start = effect.indexOf(".startAfterFirstHomeFrame()")
        val await = effect.indexOf(".databaseKeyLost.first { it }")
        val exit = effect.indexOf("finishAndRemoveTask()")

        assertTrue(
            "MainActivity must await StartupPreloader.databaseKeyLost after starting the " +
                "open, then finishAndRemoveTask()",
            start >= 0 && await > start && exit > await,
        )
        assertTrue(
            "The lost-key toast must come right before finishAndRemoveTask()",
            effect.substring(0, exit).trimEnd().endsWith(DATABASE_KEY_TOAST),
        )
    }

    // A warm relaunch: the process lives on, so the open latch skips the new activity's run
    // (the helper's second call) and the preloader toasts nothing. The flag stays set for a
    // second observation, and each observation toasts, with no guard of its own.
    @Test
    fun warmRelaunch_toastsAgainBeforeItCloses() = runBlocking {
        every { databaseHolder.openAndSignal() } throws
                DatabaseKeyUnavailableException("The database exists, but its stored passphrase is missing.")
        val preloader = startAfterFirstHomeFrameTwice()

        repeat(2) {
            assertTrue(
                "The lost key must stay set for each activity's observation",
                withTimeoutOrNull(1_000) { preloader.databaseKeyLost.first { it } } == true,
            )
        }
        val afterAwait = mainActivityFirstHomeFrameEffect()
            .substringAfter(".databaseKeyLost.first { it }", missingDelimiterValue = "")
        assertTrue(
            "Each observation of the lost key must toast, with nothing before it to skip " +
                "it, so a warm relaunch explains its exit too",
            afterAwait.trimStart().startsWith(DATABASE_KEY_TOAST),
        )
    }

    // Whole-line comments dropped, so a commented-out call doesn't count.
    private fun mainActivityFirstHomeFrameEffect(): String =
        File("src/main/java/com/mkx/hrttracker/MainActivity.kt").readLines()
            .filterNot { it.trimStart().startsWith("//") }
            .joinToString("\n")
            .substringAfter("main_activity_first_home_frame_ready")
            .substringBefore("AppAuthenticationPromptEffect(")

    // The second call stands for a recreated or warm-relaunched activity's first home frame.
    private fun startAfterFirstHomeFrameTwice(): StartupPreloader {
        mockkObject(ToastManager)
        every { ToastManager.showMessageRes(any()) } just Runs
        every { ToastManager.showMessage(any<String>()) } just Runs
        val preloader = preloader(Dispatchers.IO)

        preloader.startAfterFirstHomeFrame()
        preloader.startAfterFirstHomeFrame()

        verify(timeout = 1_000) {
            diagnosticsLogger.info("StartupPreloader", "startup_after_first_home_frame_complete")
        }
        return preloader
    }

    @After
    fun tearDown() {
        unmockkObject(ToastManager)
    }

    private companion object {
        const val DATABASE_KEY_TOAST = "ToastManager.showMessageRes(R.string.database_key_unavailable)"
    }
}
