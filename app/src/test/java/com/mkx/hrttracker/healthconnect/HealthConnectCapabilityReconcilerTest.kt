package com.mkx.hrttracker.healthconnect

import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.settings.SettingsState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant

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
        every { clock.instant() } answers { now }
    }

    private fun TestScope.reconciler() =
        HealthConnectCapabilityReconciler(this, hc, coordinator, settingsRepository, clock)

    @Test
    fun resumesSyncOnceADay_andWhenAsked() = runTest {
        val reconciler = reconciler()

        // App start, then more resumes the same day.
        reconciler.requestReconcile("resume")
        reconciler.requestReconcile("resume")
        advanceUntilIdle()
        coVerify(exactly = 1) { coordinator.sync(any(), any()) }

        reconciler.requestReconcile("weight_sync_now", sync = true)
        advanceUntilIdle()
        coVerify(exactly = 2) { coordinator.sync(any(), any()) }

        now += Duration.ofDays(1)
        reconciler.requestReconcile("resume")
        reconciler.requestReconcile("resume")
        advanceUntilIdle()
        coVerify(exactly = 3) { coordinator.sync(any(), any()) }
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
