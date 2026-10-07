package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResult
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.pk.PkCalibrationEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PkCalibrationLiveRepositoryTest {
    private val bloodTests: BloodTestRepository = mockk()
    private val medicationLogs: MedicationLogRepository = mockk()
    private val userProfiles: UserProfileRepository = mockk()
    private val storage: PkCalibrationStorageRepository = mockk()
    private val homeSnapshots: HomeSnapshotRepository = mockk(relaxed = true)
    private val builds = MutableStateFlow<HomeCalibrationBuild?>(null)

    @Test
    fun liveState_publishesActualEngineEvaluation() = runTest {
        val fixture = validResearchFixture()
        coEvery { bloodTests.getPanels() } returns listOf(fixture.panel)
        coEvery { medicationLogs.getEntries() } returns emptyList()
        coEvery { userProfiles.getCurrentProfile() } returns UserProfile(weightKg = 70.0)
        coEvery { storage.getAllMetadata() } returns emptyList()
        val repository = repository(backgroundScope, StandardTestDispatcher(testScheduler))
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.liveState.collect()
        }

        runCurrent()

        val available = requireNotNull(repository.liveState.value?.live)
        assertEquals(fixture.panel.results.single().uuid, available.input.labs.single().resultId)
        assertEquals(
            com.mkx.hrttracker.model.pk.PkCalibrationGlobalState.NO_DOSE_HISTORY,
            available.evaluation.result.globalState,
        )
        // The render domain tracks the injected clock (widest past span + 1 day
        // flooring slack .. widest future span), not the earliest event.
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
        coEvery { bloodTests.getPanels() } returns listOf(fixture.panel)
        coEvery { medicationLogs.getEntries() } returns emptyList()
        // Current Weight never set: calibration resolves the same 70 kg
        // default as the Home projection instead of reporting the whole
        // evaluation as SHARED_INPUT_INVALID ("Check an E2 result").
        coEvery { userProfiles.getCurrentProfile() } returns UserProfile()
        coEvery { storage.getAllMetadata() } returns emptyList()
        val repository = repository(backgroundScope, StandardTestDispatcher(testScheduler))
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.liveState.collect()
        }

        runCurrent()

        val available = requireNotNull(repository.liveState.value?.live)
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
    fun snapshotBuild_isRenderedInsteadOfSolvedAgain_andRetryForcesARebuild() = runTest {
        stubEmptySourceReads()
        val repository = repository(backgroundScope, StandardTestDispatcher(testScheduler))
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.liveState.collect()
        }
        runCurrent()
        // No build yet: the page solves once itself, so it never waits on a
        // snapshot write.
        coVerify(exactly = 1) { bloodTests.getPanels() }
        val selfSolved = requireNotNull(repository.liveState.value?.live)
        assertEquals(
            com.mkx.hrttracker.model.pk.PkCalibrationGlobalState.NO_DOSE_HISTORY,
            selfSolved.evaluation.result.globalState,
        )

        val evaluation = PkCalibrationEngine.evaluate(selfSolved.input)
        builds.value = HomeCalibrationBuild(
            generation = 1L,
            generatedAtEpochMillis = FixedNowMillis,
            input = selfSolved.input,
            evaluation = evaluation,
        )
        runCurrent()
        // The published solve is reused as-is; no second read or solve.
        assertSame(evaluation, requireNotNull(repository.liveState.value?.live).evaluation)
        coVerify(exactly = 1) { bloodTests.getPanels() }

        // A build whose solve threw surfaces as the unavailable state.
        builds.value = HomeCalibrationBuild(
            generation = 2L,
            generatedAtEpochMillis = FixedNowMillis + 1L,
            input = selfSolved.input,
            evaluation = null,
        )
        runCurrent()
        assertNull(requireNotNull(repository.liveState.value).live)

        repository.retry()
        verify(exactly = 1) { homeSnapshots.refreshHomeSnapshotAsync(any(), force = true, any()) }
        collector.cancel()
    }

    @Test
    fun newerBuild_cancelsAnOlderRead_andOnlyLatestStateSurvives() = runTest {
        val firstReadStarted = CompletableDeferred<Unit>()
        coEvery { bloodTests.getPanels() } coAnswers {
            firstReadStarted.complete(Unit)
            awaitCancellation()
        }
        coEvery { medicationLogs.getEntries() } returns emptyList()
        coEvery { userProfiles.getCurrentProfile() } returns UserProfile()
        coEvery { storage.getAllMetadata() } returns emptyList()
        val repository = repository(backgroundScope, StandardTestDispatcher(testScheduler))
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.liveState.collect()
        }

        firstReadStarted.await()
        val input = buildPkCalibrationInput(
            labs = emptyList(),
            entries = emptyList(),
            weightKg = null,
            metadata = emptyList(),
            fallbackOriginEpochMillis = FixedNowMillis,
        )
        builds.value = HomeCalibrationBuild(
            generation = 2L,
            generatedAtEpochMillis = FixedNowMillis,
            input = input,
            evaluation = PkCalibrationEngine.evaluate(input),
        )
        runCurrent()

        assertEquals(
            com.mkx.hrttracker.model.pk.PkCalibrationGlobalState.NO_DOSE_HISTORY,
            requireNotNull(repository.liveState.value?.live)
                .evaluation.result.globalState,
        )
        collector.cancel()
    }

    private fun repository(
        appScope: kotlinx.coroutines.CoroutineScope,
        defaultDispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ): PkCalibrationLiveRepository {
        every { homeSnapshots.calibrationBuilds } returns builds
        return PkCalibrationLiveRepository(
            bloodTestRepository = bloodTests,
            medicationLogRepository = medicationLogs,
            userProfileRepository = userProfiles,
            storageRepository = storage,
            homeSnapshotRepository = homeSnapshots,
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
