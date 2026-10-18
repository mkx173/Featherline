package com.mkx.hrttracker.reminder

import android.content.BroadcastReceiver
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationReminderActionReceiverTest {
    private val actionHandler: MedicationReminderActionHandler = mockk(relaxed = true)
    private val homeSnapshotRepository: HomeSnapshotRepository = mockk()
    private val diagnosticsLogger: AppDiagnosticsLogger = mockk(relaxed = true)
    private val pendingResult: BroadcastReceiver.PendingResult = mockk(relaxed = true)
    private val slot = MedicationReminderSlot(
        groupUuid = UUID.fromString("1246f246-6e7d-4fac-a16c-0a557dd0c7fd"),
        scheduledAt = LocalDateTime.of(2026, 4, 20, 9, 0),
        scheduleTimeUuid = null,
    )

    @Test
    fun logNow_finishesOnlyOnceItsRebuildAndWarmUpLanded() = runTest {
        // A killed process after the finish used to leave Home without a
        // snapshot or an estimate for the next open.
        val rebuilt = CompletableDeferred<Unit>()
        coEvery { homeSnapshotRepository.rebuildNow() } coAnswers { rebuilt.await() }

        logNow()
        runCurrent()
        coVerify(exactly = 1) { actionHandler.logNow(listOf(slot), null, null, any()) }
        verify(exactly = 0) { pendingResult.finish() }

        rebuilt.complete(Unit)
        runCurrent()
        verify(exactly = 1) { pendingResult.finish() }
    }

    @Test
    fun logNow_finishesWhenTheBudgetRunsOut() = runTest {
        coEvery { homeSnapshotRepository.rebuildNow() } coAnswers { awaitCancellation() }

        logNow()
        runCurrent()
        advanceTimeBy(7_000L)
        runCurrent()
        verify(exactly = 0) { pendingResult.finish() }

        advanceTimeBy(1_100L)
        runCurrent()
        verify(exactly = 1) { pendingResult.finish() }
    }

    private fun TestScope.logNow() = handleReminderAction(
        action = ACTION_MEDICATION_REMINDER_LOG_NOW,
        slots = listOf(slot),
        logTargets = null,
        notificationTag = null,
        deadline = TimeSource.Monotonic.markNow() + 8.seconds,
        appScope = backgroundScope,
        actionHandler = actionHandler,
        homeSnapshotRepository = homeSnapshotRepository,
        diagnosticsLogger = diagnosticsLogger,
        pendingResult = pendingResult,
    )
}
