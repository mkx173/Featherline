package com.mkx.hrttracker.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Intent
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationReminderRescheduleReceiverTest {
    private val reminderCapabilityReconciler: ReminderCapabilityReconciler = mockk(relaxed = true)
    private val homeSnapshotRepository: HomeSnapshotRepository = mockk(relaxed = true)
    private val diagnosticsLogger: AppDiagnosticsLogger = mockk(relaxed = true)
    private val pendingResult: BroadcastReceiver.PendingResult = mockk(relaxed = true)
    private val appScope = CoroutineScope(UnconfinedTestDispatcher())

    @Test
    fun acceptedActions_areAllRecognized() {
        val accepted = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )

        accepted.forEach { action ->
            assertTrue(
                "Expected $action to be accepted",
                isReminderRescheduleReceiverAction(action),
            )
        }
    }

    @Test
    fun unrelatedActions_areNotRecognized() {
        assertFalse(isReminderRescheduleReceiverAction(null))
        assertFalse(isReminderRescheduleReceiverAction(""))
        assertFalse(isReminderRescheduleReceiverAction("com.example.UNRELATED_ACTION"))
        assertFalse(isReminderRescheduleReceiverAction(Intent.ACTION_VIEW))
    }

    @Test
    fun bootCompleted_delegatesReconcileAndFinishesPendingResult() = runTest {
        coEvery { reminderCapabilityReconciler.reconcile(any()) } returns Unit

        handleReminderRescheduleBroadcast(
            action = Intent.ACTION_BOOT_COMPLETED,
            appScope = appScope,
            reminderCapabilityReconciler = reminderCapabilityReconciler,
            homeSnapshotRepository = homeSnapshotRepository,
            diagnosticsLogger = diagnosticsLogger,
            goAsync = { pendingResult },
        )

        coVerify(exactly = 1) { reminderCapabilityReconciler.reconcile(any()) }
        verify(exactly = 1) { pendingResult.finish() }
    }

    @Test
    fun packageReplaced_delegatesReconcileAndFinishesPendingResult() = runTest {
        coEvery { reminderCapabilityReconciler.reconcile(any()) } returns Unit

        handleReminderRescheduleBroadcast(
            action = Intent.ACTION_MY_PACKAGE_REPLACED,
            appScope = appScope,
            reminderCapabilityReconciler = reminderCapabilityReconciler,
            homeSnapshotRepository = homeSnapshotRepository,
            diagnosticsLogger = diagnosticsLogger,
            goAsync = { pendingResult },
        )

        coVerify(exactly = 1) { reminderCapabilityReconciler.reconcile(any()) }
        verify(exactly = 1) { pendingResult.finish() }
    }

    @Test
    fun exactAlarmPermissionStateChanged_delegatesReconcileAndFinishesPendingResult() = runTest {
        coEvery { reminderCapabilityReconciler.reconcile(any()) } returns Unit

        handleReminderRescheduleBroadcast(
            action = AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            appScope = appScope,
            reminderCapabilityReconciler = reminderCapabilityReconciler,
            homeSnapshotRepository = homeSnapshotRepository,
            diagnosticsLogger = diagnosticsLogger,
            goAsync = { pendingResult },
        )

        coVerify(exactly = 1) { reminderCapabilityReconciler.reconcile(any()) }
        verify(exactly = 1) { pendingResult.finish() }
    }

    @Test
    fun timezoneChange_forcesHomeSnapshotRefresh() = runTest {
        coEvery { reminderCapabilityReconciler.reconcile(any()) } returns Unit

        handleReminderRescheduleBroadcast(
            action = Intent.ACTION_TIMEZONE_CHANGED,
            appScope = appScope,
            reminderCapabilityReconciler = reminderCapabilityReconciler,
            homeSnapshotRepository = homeSnapshotRepository,
            diagnosticsLogger = diagnosticsLogger,
            goAsync = { pendingResult },
        )

        verify(exactly = 1) {
            homeSnapshotRepository.refreshHomeSnapshotAsync(now = any(), force = true)
        }
    }

    @Test
    fun timeChange_forcesHomeSnapshotRefresh() = runTest {
        coEvery { reminderCapabilityReconciler.reconcile(any()) } returns Unit

        handleReminderRescheduleBroadcast(
            action = Intent.ACTION_TIME_CHANGED,
            appScope = appScope,
            reminderCapabilityReconciler = reminderCapabilityReconciler,
            homeSnapshotRepository = homeSnapshotRepository,
            diagnosticsLogger = diagnosticsLogger,
            goAsync = { pendingResult },
        )

        verify(exactly = 1) {
            homeSnapshotRepository.refreshHomeSnapshotAsync(now = any(), force = true)
        }
    }

    @Test
    fun bootCompleted_doesNotRefreshSnapshot() = runTest {
        coEvery { reminderCapabilityReconciler.reconcile(any()) } returns Unit

        handleReminderRescheduleBroadcast(
            action = Intent.ACTION_BOOT_COMPLETED,
            appScope = appScope,
            reminderCapabilityReconciler = reminderCapabilityReconciler,
            homeSnapshotRepository = homeSnapshotRepository,
            diagnosticsLogger = diagnosticsLogger,
            goAsync = { pendingResult },
        )

        // The startup preloader handles snapshot refresh; the receiver shouldn't
        // double-fire here.
        verify(exactly = 0) {
            homeSnapshotRepository.refreshHomeSnapshotAsync(now = any(), force = any())
        }
    }

    @Test
    fun delegatesAcrossAllAcceptedActions() = runTest {
        coEvery { reminderCapabilityReconciler.reconcile(any()) } returns Unit
        val accepted = listOf(
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )

        accepted.forEach { action ->
            handleReminderRescheduleBroadcast(
                action = action,
                appScope = appScope,
                reminderCapabilityReconciler = reminderCapabilityReconciler,
                homeSnapshotRepository = homeSnapshotRepository,
                diagnosticsLogger = diagnosticsLogger,
                goAsync = { pendingResult },
            )
        }

        coVerify(exactly = accepted.size) { reminderCapabilityReconciler.reconcile(any()) }
        verify(exactly = accepted.size) { pendingResult.finish() }
    }

    @Test
    fun unrelatedAction_doesNotCallReconciler_andDoesNotCallGoAsync() = runTest {
        var goAsyncCallCount = 0

        handleReminderRescheduleBroadcast(
            action = "com.example.UNRELATED_ACTION",
            appScope = appScope,
            reminderCapabilityReconciler = reminderCapabilityReconciler,
            homeSnapshotRepository = homeSnapshotRepository,
            diagnosticsLogger = diagnosticsLogger,
            goAsync = {
                goAsyncCallCount += 1
                pendingResult
            },
        )

        coVerify(exactly = 0) { reminderCapabilityReconciler.reconcile(any()) }
        verify(exactly = 0) { pendingResult.finish() }
        assertTrue(
            "Expected goAsync to be skipped for unrelated actions",
            goAsyncCallCount == 0,
        )
    }

    @Test
    fun nullAction_isIgnored() = runTest {
        handleReminderRescheduleBroadcast(
            action = null,
            appScope = appScope,
            reminderCapabilityReconciler = reminderCapabilityReconciler,
            homeSnapshotRepository = homeSnapshotRepository,
            diagnosticsLogger = diagnosticsLogger,
            goAsync = { pendingResult },
        )

        coVerify(exactly = 0) { reminderCapabilityReconciler.reconcile(any()) }
    }

    @Test
    fun reconcileFailure_stillFinishesPendingResult() = runTest {
        coEvery { reminderCapabilityReconciler.reconcile(any()) } throws
            RuntimeException("simulated failure")

        handleReminderRescheduleBroadcast(
            action = Intent.ACTION_BOOT_COMPLETED,
            appScope = appScope,
            reminderCapabilityReconciler = reminderCapabilityReconciler,
            homeSnapshotRepository = homeSnapshotRepository,
            diagnosticsLogger = diagnosticsLogger,
            goAsync = { pendingResult },
        )

        verify(exactly = 1) { pendingResult.finish() }
    }
}
