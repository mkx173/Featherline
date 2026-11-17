package com.mkx.hrttracker.startup

import android.app.Activity
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.mkx.hrttracker.R
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
import io.mockk.verifyOrder
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
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
        coEvery { medicationReminderScheduler.rescheduleAll(any(), any()) } returns Unit

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
            diagnosticsLogger = diagnosticsLogger,
        ).startAfterFirstHomeFrame()

        coVerify(timeout = 1_000, exactly = 1) { medicationReminderScheduler.rescheduleAll(any(), any()) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun startReminderRescheduleFromSnapshot_usesSnapshotWithoutWarmingDatabase() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val snapshot: HomeSnapshotRecord = mockk()
        coEvery { homeSnapshotRepository.currentState() } returns HomeCacheState(0L, snapshot, e2Inputs = null)
        coEvery { medicationReminderScheduler.isReconcilePending() } returns false
        coEvery {
            medicationReminderScheduler.rescheduleFromHomeSnapshot(
                snapshot = snapshot,
                now = any(),
            )
        } returns true

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
            diagnosticsLogger = diagnosticsLogger,
        ).startReminderRescheduleFromSnapshot()
        advanceUntilIdle()

        coVerify(
            timeout = 1_000,
            exactly = 1
        ) { homeSnapshotRepository.currentState() }
        coVerify(timeout = 1_000, exactly = 1) {
            medicationReminderScheduler.rescheduleFromHomeSnapshot(
                snapshot = snapshot,
                now = any(),
            )
        }
        verify(exactly = 0) { databaseHolder.get() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun startReminderRescheduleFromSnapshot_logsWhenNoUsableSnapshotExists() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            coEvery { homeSnapshotRepository.currentState() } returns null
            coEvery { medicationReminderScheduler.isReconcilePending() } returns false

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
                diagnosticsLogger = diagnosticsLogger,
            ).startReminderRescheduleFromSnapshot()
            advanceUntilIdle()
            // The job runs on Dispatchers.IO: wait for its last step before the negative checks.
            verify(timeout = 1_000) {
                diagnosticsLogger.info(
                    "StartupPreloader",
                    match { message ->
                        "snapshot_reminder_reschedule_no_usable_snapshot" in message
                    }
                )
            }
            coVerify(exactly = 0) {
                medicationReminderScheduler.rescheduleFromHomeSnapshot(any(), any())
            }
            verify(exactly = 0) { databaseHolder.get() }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun startReminderRescheduleFromSnapshot_reconcilesFromRoomWhenAReconcileIsPending() = runTest {
        // A restore committed, and the process died before its reschedule: the
        // snapshot can predate the restore, and only rescheduleAll reconciles what's shown.
        val dispatcher = StandardTestDispatcher(testScheduler)
        val snapshot: HomeSnapshotRecord = mockk()
        coEvery { homeSnapshotRepository.currentState() } returns HomeCacheState(0L, snapshot, e2Inputs = null)
        coEvery { medicationReminderScheduler.isReconcilePending() } returns true
        coEvery { medicationReminderScheduler.rescheduleFromHomeSnapshot(any(), any()) } returns true
        coEvery { medicationReminderScheduler.rescheduleAll(any(), any()) } returns Unit

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
            diagnosticsLogger = diagnosticsLogger,
        ).startReminderRescheduleFromSnapshot()
        advanceUntilIdle()

        coVerify(timeout = 1_000, exactly = 1) { medicationReminderScheduler.rescheduleAll(any(), any()) }
        coVerify(exactly = 0) { medicationReminderScheduler.rescheduleFromHomeSnapshot(any(), any()) }
    }

    @Test
    fun startReminderRescheduleFromSnapshot_reconcilesFromRoomForAMarkThatAPassBeforeItsCommitLeft() = runTest {
        // The reminder schedule store, as DataStore keeps it from one process to the next.
        val reconcilePending = AtomicBoolean(false)
        val reminderScheduleStore: ReminderScheduleStore = mockk {
            coEvery { getScheduledReminders() } returns emptyMap()
            coEvery { replaceScheduledReminders(any()) } returns Unit
            coEvery { getSkippedThrough() } returns emptyMap()
            coEvery { isReconcilePending() } answers { reconcilePending.get() }
            coEvery { setReconcilePending(any()) } answers { reconcilePending.set(firstArg()) }
        }
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState(remindersEnabled = true)
        coEvery { medicationGroupRepository.getGroups() } returns emptyList()
        coEvery { medicationLogRepository.getScheduledGroupEntriesSince(any()) } returns emptyList()
        fun schedulerOfAProcess() = MedicationReminderScheduler(
            context = mockk(),
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            settingsRepository = settingsRepository,
            reminderScheduleStore = reminderScheduleStore,
            reminderNotificationManager = mockk { every { activeDoseReminders() } returns emptyMap() },
            homeSnapshotRepository = homeSnapshotRepository,
            diagnosticsLogger = diagnosticsLogger,
        )
        // The earlier process: a restore marks, an unrelated pass reads the data it replaces,
        // the restore commits, and its own pass fails.
        val earlier = schedulerOfAProcess()
        val restoreMark = earlier.markReconcilePending()
        earlier.rescheduleAll()
        restoreMark.committed()
        coEvery { medicationGroupRepository.getGroups() } throws IllegalStateException("database closed")
        runCatching { earlier.rescheduleAll() }
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
            diagnosticsLogger = diagnosticsLogger,
        ).startReminderRescheduleFromSnapshot()

        // Last in the startup job, on either path.
        coVerify(timeout = 1_000) { reminderScheduleStore.setReconcilePending(false) }
        coVerify(exactly = 0) { homeSnapshotRepository.currentState() }
        assertFalse("the startup reconcile settles the mark", reconcilePending.get())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun startReminderRescheduleFromSnapshot_reconcilesFromRoomWhenTheSnapshotIsStale() = runTest {
        // A save since the snapshot was read: the scheduler turned the snapshot down.
        val dispatcher = StandardTestDispatcher(testScheduler)
        val snapshot: HomeSnapshotRecord = mockk()
        coEvery { homeSnapshotRepository.currentState() } returns HomeCacheState(0L, snapshot, e2Inputs = null)
        coEvery { medicationReminderScheduler.isReconcilePending() } returns false
        coEvery { medicationReminderScheduler.rescheduleFromHomeSnapshot(snapshot, any()) } returns false
        coEvery { medicationReminderScheduler.rescheduleAll(any(), any()) } returns Unit

        preloader(dispatcher).startReminderRescheduleFromSnapshot()
        advanceUntilIdle()

        coVerify(timeout = 1_000, exactly = 1) { medicationReminderScheduler.rescheduleAll(any(), any()) }
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
        diagnosticsLogger = diagnosticsLogger,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun startReminderRescheduleFromWarmDatabase_reschedulesWithoutWarmupPass() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        coEvery { medicationReminderScheduler.rescheduleAll(any(), any()) } returns Unit

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
            diagnosticsLogger = diagnosticsLogger,
        ).startReminderRescheduleFromWarmDatabase()
        advanceUntilIdle()

        coVerify(
            timeout = 1_000,
            exactly = 1
        ) { medicationReminderScheduler.rescheduleAll(any(), any()) }
        verify(exactly = 0) { databaseHolder.get() }
        coVerify(exactly = 0) { medicationGroupRepository.getGroups() }
        coVerify(exactly = 0) { medicationLogRepository.getEntries() }
        coVerify(exactly = 0) { userProfileRepository.getCurrentProfile() }
        coVerify(exactly = 0) { settingsRepository.getCurrentSettings() }
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

    // The exit both activities run: once the key is lost, the toast, then the task closes. A
    // warm relaunch: the process lives on, so the open latch skips the new activity's run and
    // the preloader toasts nothing itself, but the flag stays set, and each activity's exit
    // toasts again before it closes.
    @Test
    fun closeOnLostDatabaseKey_toastsThenFinishesTheTask_onEveryExit() = runBlocking {
        every { databaseHolder.openAndSignal() } throws
                DatabaseKeyUnavailableException("The database exists, but its stored passphrase is missing.")
        val preloader = startAfterFirstHomeFrameTwice()

        repeat(2) {
            val activity = mockk<Activity>(relaxed = true)
            assertTrue(
                "The lost key must stay set for each activity's exit",
                withTimeoutOrNull(1_000) { preloader.closeOnLostDatabaseKey(activity) } != null,
            )
            verifyOrder {
                ToastManager.showMessageRes(R.string.database_key_unavailable)
                activity.finishAndRemoveTask()
            }
        }
        verify(exactly = 2) { ToastManager.showMessageRes(R.string.database_key_unavailable) }
    }

    // Guard: with the key there, the exit waits and closes nothing.
    @Test
    fun closeOnLostDatabaseKey_waitsWhenTheDatabaseOpens() = runBlocking {
        every { databaseHolder.openAndSignal() } returns mockk()
        val preloader = startAfterFirstHomeFrameTwice()
        val activity = mockk<Activity>(relaxed = true)

        assertNull(withTimeoutOrNull(200) { preloader.closeOnLostDatabaseKey(activity) })
        verify(exactly = 0) { activity.finishAndRemoveTask() }
        verify(exactly = 0) { ToastManager.showMessageRes(any()) }
    }

    // The widget's path: the open alone finds the key lost, with no warm-up, backfill or
    // reschedule; and a later full startup doesn't open again.
    @Test
    fun startDatabaseOpen_findsALostKey_withoutRunningTheRestOfTheStartup() = runBlocking {
        every { databaseHolder.openAndSignal() } throws
                DatabaseKeyUnavailableException("The database exists, but its stored passphrase is missing.")
        coEvery { medicationGroupRepository.getGroups() } returns emptyList()
        coEvery { medicationLogRepository.getEntries() } returns emptyList()
        coEvery { userProfileRepository.getCurrentProfile() } returns UserProfile()
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState()
        coEvery { medicationReminderScheduler.rescheduleAll(any(), any()) } returns Unit
        val preloader = preloader(Dispatchers.IO)

        preloader.startDatabaseOpen()

        assertTrue(withTimeoutOrNull(1_000) { preloader.databaseKeyLost.first { it } } == true)
        verify(exactly = 1) { databaseHolder.openAndSignal() }
        coVerify(exactly = 0) { medicationGroupRepository.getGroups() }
        coVerify(exactly = 0) { medicationLogRepository.getEntries() }
        coVerify(exactly = 0) { userProfileRepository.getCurrentProfile() }
        coVerify(exactly = 0) { settingsRepository.getCurrentSettings() }
        coVerify(exactly = 0) { medicineRepository.getAll() }
        coVerify(exactly = 0) { medicationReminderScheduler.rescheduleAll(any(), any()) }

        preloader.startAfterFirstHomeFrame()
        coVerify(timeout = 1_000, exactly = 1) { medicationReminderScheduler.rescheduleAll(any(), any()) }
        verify(exactly = 1) { databaseHolder.openAndSignal() }
    }

    // MainActivity and WidgetConfigActivity are Hilt entry points with no Hilt test setup, so
    // this reads their source: each starts its open (the widget's the open alone), then runs
    // the shared exit.
    @Test
    fun bothActivities_closeOnALostDatabaseKey() {
        for ((path, start) in listOf(
            "src/main/java/com/mkx/hrttracker/MainActivity.kt" to ".startAfterFirstHomeFrame()",
            "src/main/java/com/mkx/hrttracker/widget/WidgetConfigActivity.kt" to ".startDatabaseOpen()",
        )) {
            // Whole-line comments dropped, so a commented-out call doesn't count.
            val source = File(path).readLines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
            val started = source.indexOf(start)
            assertTrue("$path must call $start", started >= 0)
            assertTrue(
                "$path must run closeOnLostDatabaseKey after $start",
                source.indexOf(".closeOnLostDatabaseKey(this@") > started,
            )
        }
        val widget = File("src/main/java/com/mkx/hrttracker/widget/WidgetConfigActivity.kt").readText()
        assertFalse("The widget config must not run the full startup", widget.contains(".startAfterFirstHomeFrame()"))
    }

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
}
