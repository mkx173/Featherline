package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResult
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import com.mkx.hrttracker.model.personalization.UserProfile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PkCalibrationLiveRepositoryTest {
    private val bloodTests: BloodTestRepository = mockk()
    private val medicationLogs: MedicationLogRepository = mockk()
    private val userProfiles: UserProfileRepository = mockk()
    private val storage: PkCalibrationStorageRepository = mockk()

    @Test
    fun liveState_publishesActualEngineEvaluation_onInjectedDispatcher() = runTest {
        val fixture = validResearchFixture()
        val generations = MutableStateFlow(7L)
        every { storage.observeHomeSnapshotWrites() } returns generations
        coEvery { bloodTests.getPanels() } returns listOf(fixture.panel)
        coEvery { medicationLogs.getEntries() } returns emptyList()
        coEvery { userProfiles.getCurrentProfile() } returns UserProfile(weightKg = 70.0)
        coEvery { storage.getAllMetadata() } returns emptyList()
        val delegate = StandardTestDispatcher(testScheduler)
        var dispatchCount = 0
        val recordingDispatcher = object : kotlinx.coroutines.CoroutineDispatcher() {
            override fun isDispatchNeeded(context: CoroutineContext): Boolean =
                delegate.isDispatchNeeded(context)

            override fun dispatch(context: CoroutineContext, block: Runnable) {
                dispatchCount += 1
                delegate.dispatch(context, block)
            }
        }
        val repository = repository(generations, backgroundScope, recordingDispatcher)
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.liveState.collect()
        }

        runCurrent()

        val available = requireNotNull(repository.liveState.value)
        assertEquals(fixture.panel.results.single().uuid, available.input.labs.single().resultId)
        assertEquals(
            com.mkx.hrttracker.model.pk.PkCalibrationGlobalState.NO_DOSE_HISTORY,
            available.evaluation.result.globalState,
        )
        assertTrue(dispatchCount > 0)
        // Phase-3 #9: the render domain tracks the injected clock (widest past
        // span + 1 day flooring slack .. widest future span), not the earliest
        // event.
        assertEquals(
            FixedNowMillis - 17L * 24L * 3_600_000L,
            available.domain.rangeStartEpochMillis,
        )
        assertEquals(
            FixedNowMillis + 14L * 24L * 3_600_000L,
            available.domain.rangeEndEpochMillis,
        )
        collector.cancel()
    }

    @Test
    fun unsetWeight_fallsBackToTheAppDefault_insteadOfFailingInvalid() = runTest {
        val fixture = validResearchFixture()
        val generations = MutableStateFlow(1L)
        every { storage.observeHomeSnapshotWrites() } returns generations
        coEvery { bloodTests.getPanels() } returns listOf(fixture.panel)
        coEvery { medicationLogs.getEntries() } returns emptyList()
        // Current Weight never set: calibration resolves the same 70 kg
        // default as the Home projection instead of reporting the whole
        // evaluation as SHARED_INPUT_INVALID ("Check an E2 result").
        coEvery { userProfiles.getCurrentProfile() } returns UserProfile()
        coEvery { storage.getAllMetadata() } returns emptyList()
        val repository = repository(generations, backgroundScope, StandardTestDispatcher(testScheduler))
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.liveState.collect()
        }

        runCurrent()

        val available = requireNotNull(repository.liveState.value)
        assertEquals(
            com.mkx.hrttracker.model.pk.PkCalibrationGlobalState.NO_DOSE_HISTORY,
            available.evaluation.result.globalState,
        )
        assertEquals(
            com.mkx.hrttracker.model.pk.PkMedicationSimulation.DefaultBodyWeightKg,
            available.input.weightKg,
            0.0,
        )
        collector.cancel()
    }

    @Test
    fun generationChange_andRetry_reReadWithoutCreatingAnotherVersion() = runTest {
        val generations = MutableStateFlow(4L)
        every { storage.observeHomeSnapshotWrites() } returns generations
        stubEmptySourceReads()
        val repository = repository(generations, backgroundScope, StandardTestDispatcher(testScheduler))
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.liveState.collect()
        }
        runCurrent()
        // Empty sources are no longer fail-closed: with no doses and no labs
        // the origin falls back to the clock and the evaluation lands on
        // NO_DOSE_HISTORY, keeping the calibration surface alive.
        assertEquals(
            com.mkx.hrttracker.model.pk.PkCalibrationGlobalState.NO_DOSE_HISTORY,
            requireNotNull(repository.liveState.value)
                .evaluation.result.globalState,
        )

        generations.value = 5L
        runCurrent()
        repository.retry()
        runCurrent()

        coVerify(exactly = 3) { bloodTests.getPanels() }
        coVerify(exactly = 3) { medicationLogs.getEntries() }
        coVerify(exactly = 3) { userProfiles.getCurrentProfile() }
        coVerify(exactly = 3) { storage.getAllMetadata() }
        collector.cancel()
    }

    @Test
    fun newerGeneration_cancelsAnOlderRead_andOnlyLatestStateSurvives() = runTest {
        val generations = MutableStateFlow(1L)
        every { storage.observeHomeSnapshotWrites() } returns generations
        val firstReadStarted = CompletableDeferred<Unit>()
        var reads = 0
        coEvery { bloodTests.getPanels() } coAnswers {
            reads += 1
            if (reads == 1) {
                firstReadStarted.complete(Unit)
                awaitCancellation()
            }
            emptyList()
        }
        coEvery { medicationLogs.getEntries() } returns emptyList()
        coEvery { userProfiles.getCurrentProfile() } returns UserProfile()
        coEvery { storage.getAllMetadata() } returns emptyList()
        val repository = repository(generations, backgroundScope, StandardTestDispatcher(testScheduler))
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.liveState.collect()
        }

        firstReadStarted.await()
        generations.value = 2L
        runCurrent()

        assertEquals(2, reads)
        assertEquals(
            com.mkx.hrttracker.model.pk.PkCalibrationGlobalState.NO_DOSE_HISTORY,
            requireNotNull(repository.liveState.value)
                .evaluation.result.globalState,
        )
        collector.cancel()
    }

    private fun repository(
        generations: MutableStateFlow<Long>,
        appScope: kotlinx.coroutines.CoroutineScope,
        defaultDispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ): PkCalibrationLiveRepository {
        every { storage.observeHomeSnapshotWrites() } returns generations
        return PkCalibrationLiveRepository(
            bloodTestRepository = bloodTests,
            medicationLogRepository = medicationLogs,
            userProfileRepository = userProfiles,
            storageRepository = storage,
            clock = Clock.fixed(Instant.ofEpochMilli(FixedNowMillis), ZoneOffset.UTC),
            defaultDispatcher = defaultDispatcher,
            appScope = appScope,
        )
    }

    private fun stubEmptySourceReads() {
        coEvery { bloodTests.getPanels() } returns emptyList()
        coEvery { medicationLogs.getEntries() } returns emptyList()
        coEvery { userProfiles.getCurrentProfile() } returns UserProfile()
        coEvery { storage.getAllMetadata() } returns emptyList()
    }

    private fun validResearchFixture(): ValidResearchFixture {
        val resultId = UUID(0L, 42L)
        val collectedAt = Instant.ofEpochMilli(1_700_000_000_000L)
        val result = BloodTestResult(
            uuid = resultId,
            createdAt = collectedAt,
            displayOrder = 0,
            analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.E2),
            value = 100.0,
            unitSnapshot = "pg_ml",
            canonicalValue = 100.0,
        )
        val panel = BloodTestPanel(
            uuid = UUID(0L, 43L),
            collectedAt = collectedAt,
            collectedAtTimeZoneId = "UTC",
            notes = null,
            timeSinceLastEstradiolDoseMillis = null,
            timeSinceLastTestosteroneDoseMillis = null,
            results = listOf(result),
            createdAt = collectedAt,
            updatedAt = collectedAt,
        )
        return ValidResearchFixture(panel)
    }

    private data class ValidResearchFixture(val panel: BloodTestPanel)

    private companion object {
        const val FixedNowMillis = 1_700_000_000_000L
    }
}
