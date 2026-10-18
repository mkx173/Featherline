package com.mkx.hrttracker.healthconnect

import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.settings.SettingsState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HealthConnectCapabilityReconcilerTest {
    private val hc = FakeHealthConnectManager()
    private val coordinator: WeightSyncCoordinator = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk()
    private val clock: Clock = mockk()
    private var syncEnabled = true
    private var now = Instant.parse("2026-10-16T09:00:00Z")

    init {
        coEvery { settingsRepository.getCurrentSettings() } answers {
            SettingsState(healthConnectWeightSyncEnabled = syncEnabled)
        }
        coEvery { settingsRepository.setHealthConnectWeightSyncEnabled(any()) } answers {
            syncEnabled = firstArg()
        }
        coEvery { coordinator.disable() } answers { syncEnabled = false }
        every { clock.instant() } answers { now }
    }

    private fun TestScope.reconciler() =
        HealthConnectCapabilityReconciler(this, hc, coordinator, settingsRepository, clock)

    @Test
    fun resumesSyncAtMostOnceAMinute_andWhenAsked() = runTest {
        val reconciler = reconciler()

        // App start, then more resumes within the minute.
        reconciler.requestReconcile("resume")
        reconciler.requestReconcile("resume")
        advanceUntilIdle()
        coVerify(exactly = 1) { coordinator.sync(any(), now) }

        reconciler.requestReconcile("weight_sync_now", sync = true)
        advanceUntilIdle()
        coVerify(exactly = 2) { coordinator.sync(any(), now) }

        now += Duration.ofMinutes(1)
        reconciler.requestReconcile("resume")
        reconciler.requestReconcile("resume")
        advanceUntilIdle()
        coVerify(exactly = 1) { coordinator.sync(any(), now) }
        coVerify(exactly = 3) { coordinator.sync(any(), any()) }
    }

    @Test
    fun backwardClockCorrection_expiresTheGuardAndStartsANewInterval() = runTest {
        val reconciler = reconciler()
        reconciler.requestReconcile("resume")
        advanceUntilIdle()

        now -= Duration.ofHours(1)
        reconciler.requestReconcile("resume")
        advanceUntilIdle()
        coVerify(exactly = 2) { coordinator.sync(any(), any()) }

        now += Duration.ofSeconds(59)
        reconciler.requestReconcile("resume")
        advanceUntilIdle()
        coVerify(exactly = 2) { coordinator.sync(any(), any()) }

        now += Duration.ofSeconds(1)
        reconciler.requestReconcile("resume")
        advanceUntilIdle()
        coVerify(exactly = 3) { coordinator.sync(any(), any()) }
    }

    @Test
    fun syncOff_doesNotSyncOrConsumeTodaysSync_andTurningOnSyncsOnce() = runTest {
        val reconciler = reconciler()
        syncEnabled = false

        // Access is already granted, but sync is off: resume mustn't sync or
        // arm the burst guard.
        reconciler.requestReconcile("resume")
        advanceUntilIdle()
        coVerify(exactly = 0) { coordinator.sync(any(), any()) }

        // A permission grant returns to MainActivity (resume) and calls
        // onEnable (weight_sync_enabled): only one sync runs.
        reconciler.enableSync()
        reconciler.requestReconcile("resume")
        advanceUntilIdle()
        coVerify(exactly = 1) { coordinator.sync(any(), now) }

        // Turned off and back on within the minute: disables through the coordinator
        // and syncs again.
        reconciler.disableSync()
        coVerify(exactly = 1) { coordinator.disable() }
        reconciler.enableSync()
        advanceUntilIdle()
        coVerify(exactly = 2) { coordinator.sync(any(), now) }
    }

    @Test
    fun offThenOnWhileReconciling_finishesEnabledAndSyncsAgain() = runTest {
        val reconciler = reconciler()
        val readGate = CompletableDeferred<Unit>()
        coEvery { coordinator.sync(any(), any()) } coAnswers { readGate.await() }
        reconciler.requestReconcile("resume")
        runCurrent()
        coVerify(exactly = 1) { coordinator.sync(any(), now) }

        // Both toggles must queue behind the in-flight reconcile, in order.
        launch { reconciler.disableSync() }
        runCurrent()
        launch { reconciler.enableSync() }
        runCurrent()
        coVerify(exactly = 0) { settingsRepository.setHealthConnectWeightSyncEnabled(any()) }

        readGate.complete(Unit)
        advanceUntilIdle()

        assertTrue(syncEnabled)
        coVerify(exactly = 1) { coordinator.disable() }
        coVerify(exactly = 2) { coordinator.sync(any(), now) }
    }

    @Test
    fun onThenOffWhileReconciling_finishesDisabledWithoutAnotherSync() = runTest {
        val reconciler = reconciler()
        val readGate = CompletableDeferred<Unit>()
        coEvery { coordinator.sync(any(), any()) } coAnswers { readGate.await() }
        reconciler.requestReconcile("resume")
        runCurrent()

        launch { reconciler.enableSync() }
        runCurrent()
        launch { reconciler.disableSync() }
        runCurrent()
        readGate.complete(Unit)
        advanceUntilIdle()

        assertFalse(syncEnabled)
        coVerify(exactly = 1) { coordinator.disable() }
        coVerify(exactly = 1) { coordinator.sync(any(), now) }
    }

    @Test
    fun aSyncThatThrew_isRetriedOnTheNextReconcile() = runTest {
        val reconciler = reconciler()
        coEvery { coordinator.sync(any(), any()) } throws IllegalStateException("hc ipc")

        reconciler.requestReconcile("resume")
        advanceUntilIdle()
        assertTrue(reconciler.state.value.syncFailed)

        coEvery { coordinator.sync(any(), any()) } returns Unit
        reconciler.requestReconcile("resume")
        advanceUntilIdle()
        coVerify(exactly = 2) { coordinator.sync(any(), any()) }
        assertFalse(reconciler.state.value.syncFailed)
    }

    @Test
    fun providerUpdateRequired_leavesSyncOn_untilHealthConnectIsUpdated() = runTest {
        val reconciler = reconciler()
        // Synced just before Health Connect needed an update.
        reconciler.requestReconcile("resume")
        advanceUntilIdle()
        coVerify(exactly = 1) { coordinator.sync(any(), now) }

        hc.available = false
        hc.providerUpdateRequired = true

        reconciler.requestReconcile("resume")
        advanceUntilIdle()
        coVerify(exactly = 0) { coordinator.disable() }
        assertTrue(reconciler.state.value.providerUpdateRequired)

        hc.available = true
        hc.providerUpdateRequired = false
        reconciler.requestReconcile("resume")
        advanceUntilIdle()
        coVerify(exactly = 2) { coordinator.sync(any(), now) }
    }

    @Test
    fun syncStaysOn_untilAReconcileKnowsTheAccess_soATypedWeightCantBeReplaced() = runTest {
        val reconciler = reconciler()
        hc.accessError = IllegalStateException("hc ipc")
        assertTrue(reconciler.state.value.isSyncOn(syncEnabled = true))

        reconciler.requestReconcile("resume")
        advanceUntilIdle()
        assertTrue(reconciler.state.value.syncFailed)
        assertTrue(reconciler.state.value.isSyncOn(syncEnabled = true))

        hc.accessError = null
        reconciler.requestReconcile("resume")
        advanceUntilIdle()
        assertFalse(reconciler.state.value.accessPending)
        coVerify(exactly = 1) { coordinator.sync(any(), any()) }
    }

    @Test
    fun lostAccessTurnsSyncOff_soALaterGrantCantReplaceATypedWeight() = runTest {
        val reconciler = reconciler()
        hc.access = false

        reconciler.requestReconcile("resume")
        advanceUntilIdle()

        coVerify(exactly = 1) { coordinator.disable() }
        coVerify(exactly = 0) { coordinator.sync(any(), any()) }
        assertFalse(reconciler.state.value.hasAccess)
    }

    @Test
    fun unavailableHealthConnectTurnsSyncOff_butSyncAlreadyOffIsLeftAlone() = runTest {
        val reconciler = reconciler()
        hc.available = false

        reconciler.requestReconcile("resume")
        advanceUntilIdle()
        coVerify(exactly = 1) { coordinator.disable() }

        syncEnabled = false
        reconciler.requestReconcile("resume")
        advanceUntilIdle()
        coVerify(exactly = 1) { coordinator.disable() }
    }
}
