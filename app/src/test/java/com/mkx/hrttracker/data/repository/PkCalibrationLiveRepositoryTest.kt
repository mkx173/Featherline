package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResult
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.pk.PkCalibrationConfig
import com.mkx.hrttracker.model.pk.PkCalibrationE2LabSource
import com.mkx.hrttracker.model.pk.PkCalibrationIdentityPolicy
import com.mkx.hrttracker.model.pk.PkCompound
import com.mkx.hrttracker.model.pk.PkRoute
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PkCalibrationLiveRepositoryTest {
    private val bloodTests: BloodTestRepository = mockk()
    private val medicationLogs: MedicationLogRepository = mockk()
    private val userProfiles: UserProfileRepository = mockk()
    private val storage: PkCalibrationStorageRepository = mockk()

    @Test
    fun explicitResearchPolicy_publishesActualEngineEvaluation_onInjectedDispatcher() = runTest {
        val fixture = validResearchFixture()
        val generations = MutableStateFlow(7L)
        every { storage.observeHomeDataGeneration() } returns generations
        coEvery { storage.captureHomeDataGeneration() } returns 7L
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
        val repository = repository(
            generations,
            fixture.policy,
            backgroundScope,
            recordingDispatcher,
        )
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.liveState.collect()
        }

        runCurrent()

        val available = repository.liveState.value as PkCalibrationLiveState.Available
        assertEquals(7L, available.inputGeneration)
        assertEquals(fixture.panel.results.single().uuid, available.context.input.labs.single().resultId)
        assertEquals(
            com.mkx.hrttracker.model.pk.PkCalibrationGlobalState.READY,
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
        assertSame(available.context, repository.currentEvaluationContext())
        // The context is generation-bound: once Home data moves on, review
        // actions must not act against the stale snapshot.
        coEvery { storage.captureHomeDataGeneration() } returns 8L
        assertNull(repository.currentEvaluationContext())
        collector.cancel()
    }

    @Test
    fun unsetWeight_fallsBackToTheAppDefault_insteadOfFailingInvalid() = runTest {
        val fixture = validResearchFixture()
        val generations = MutableStateFlow(1L)
        every { storage.observeHomeDataGeneration() } returns generations
        coEvery { storage.captureHomeDataGeneration() } returns 1L
        coEvery { bloodTests.getPanels() } returns listOf(fixture.panel)
        coEvery { medicationLogs.getEntries() } returns emptyList()
        // Current Weight never set: calibration resolves the same 70 kg
        // default as the Home projection instead of reporting the whole
        // evaluation as SHARED_INPUT_INVALID ("Check an E2 result").
        coEvery { userProfiles.getCurrentProfile() } returns UserProfile()
        coEvery { storage.getAllMetadata() } returns emptyList()
        val repository = repository(
            generations,
            fixture.policy,
            backgroundScope,
            StandardTestDispatcher(testScheduler),
        )
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.liveState.collect()
        }

        runCurrent()

        val available = repository.liveState.value as PkCalibrationLiveState.Available
        assertEquals(
            com.mkx.hrttracker.model.pk.PkCalibrationGlobalState.READY,
            available.evaluation.result.globalState,
        )
        assertEquals(
            com.mkx.hrttracker.model.pk.PkMedicationSimulation.DefaultBodyWeightKg,
            checkNotNull(available.context.input.resolvedCurrentWeightKg),
            0.0,
        )
        collector.cancel()
    }

    @Test
    fun generationChange_andRetry_reReadWithoutCreatingAnotherVersion() = runTest {
        val generations = MutableStateFlow(4L)
        every { storage.observeHomeDataGeneration() } returns generations
        coEvery { storage.captureHomeDataGeneration() } returns 4L
        stubEmptySourceReads()
        val repository = repository(
            generations,
            researchPolicy(),
            backgroundScope,
            StandardTestDispatcher(testScheduler),
        )
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.liveState.collect()
        }
        runCurrent()
        // Empty sources are no longer fail-closed: with no doses and no labs
        // the origin falls back to the clock and the evaluation lands on
        // NO_USABLE_LABS, keeping the calibration surface alive.
        assertEquals(
            com.mkx.hrttracker.model.pk.PkCalibrationGlobalState.NO_USABLE_LABS,
            (repository.liveState.value as PkCalibrationLiveState.Available)
                .evaluation.result.globalState,
        )

        generations.value = 5L
        coEvery { storage.captureHomeDataGeneration() } returns 5L
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
    fun unstableGeneration_isRetriedBoundedly_andNeverPublished() = runTest {
        val generations = MutableStateFlow(1L)
        every { storage.observeHomeDataGeneration() } returns generations
        coEvery { storage.captureHomeDataGeneration() } returnsMany
            listOf(1L, 2L, 2L, 3L, 3L, 4L)
        stubEmptySourceReads()
        val repository = repository(
            generations,
            researchPolicy(),
            backgroundScope,
            StandardTestDispatcher(testScheduler),
        )
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.liveState.collect()
        }

        runCurrent()

        assertEquals(
            PkCalibrationLiveUnavailableReason.INPUT_GENERATION_UNSTABLE,
            (repository.liveState.value as PkCalibrationLiveState.Unavailable).reason,
        )
        coVerify(exactly = 3) { bloodTests.getPanels() }
        collector.cancel()
    }

    @Test
    fun newerGeneration_cancelsAnOlderRead_andOnlyLatestStateSurvives() = runTest {
        val generations = MutableStateFlow(1L)
        every { storage.observeHomeDataGeneration() } returns generations
        coEvery { storage.captureHomeDataGeneration() } coAnswers { generations.value }
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
        val repository = repository(
            generations,
            researchPolicy(),
            backgroundScope,
            StandardTestDispatcher(testScheduler),
        )
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.liveState.collect()
        }

        firstReadStarted.await()
        generations.value = 2L
        runCurrent()

        assertEquals(2, reads)
        assertEquals(
            com.mkx.hrttracker.model.pk.PkCalibrationGlobalState.NO_USABLE_LABS,
            (repository.liveState.value as PkCalibrationLiveState.Available)
                .evaluation.result.globalState,
        )
        collector.cancel()
    }

    private fun repository(
        generations: MutableStateFlow<Long>,
        policy: PkCalibrationRuntimePolicy,
        appScope: kotlinx.coroutines.CoroutineScope,
        defaultDispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ): PkCalibrationLiveRepository {
        every { storage.observeHomeDataGeneration() } returns generations
        return PkCalibrationLiveRepository(
            bloodTestRepository = bloodTests,
            medicationLogRepository = medicationLogs,
            userProfileRepository = userProfiles,
            storageRepository = storage,
            runtimePolicy = policy,
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

    private fun researchPolicy(): PkCalibrationRuntimePolicy {
        return requireNotNull(PkCalibrationRuntimePolicy.create(
            identityPolicy = mockk<PkCalibrationIdentityPolicy>(relaxed = true),
            config = requireNotNull(PkCalibrationConfig.create(1.0, 0.1)),
            forwardModelVersion = "test:forward/v1",
            calibrationModelVersion = "test:calibration/v1",
        ))
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
        val lab = requireNotNull(PkCalibrationE2LabSource.create(
            panel = panel,
            result = result,
            analyteId = ANALYTE_ID,
            unitId = UNIT_ID,
        ))
        val eventTypeIds = linkedMapOf(
            PkRoute.INJECTION to "event:injection-dose/v1",
            PkRoute.PATCH_APPLY to "event:patch-apply/v1",
            PkRoute.PATCH_REMOVE to "event:patch-remove/v1",
            PkRoute.GEL to "event:gel-dose/v1",
            PkRoute.ORAL to "event:oral-dose/v1",
            PkRoute.SUBLINGUAL to "event:sublingual-dose/v1",
        )
        val routeIds = linkedMapOf(
            PkRoute.INJECTION to "injection",
            PkRoute.PATCH_APPLY to "patch",
            PkRoute.PATCH_REMOVE to "patch",
            PkRoute.GEL to "gel",
            PkRoute.ORAL to "oral",
            PkRoute.SUBLINGUAL to "sublingual",
        )
        val compoundIds = linkedMapOf(
            PkCompound.E2 to "compound:e2/v1",
            PkCompound.EB to "compound:eb/v1",
            PkCompound.EV to "compound:ev/v1",
            PkCompound.EC to "compound:ec/v1",
            PkCompound.EN to "compound:en/v1",
            PkCompound.EU to "compound:eu/v1",
        )
        val identityPolicy = requireNotNull(PkCalibrationIdentityPolicy.researchOrTest(
            builtinE2AnalyteId = ANALYTE_ID,
            targetHormoneId = "hrttracker:hormone/estradiol/v1",
            unitIdBySourceSnapshot = mapOf("pg_ml" to UNIT_ID),
            eventTypeIdByRoute = eventTypeIds,
            routeIdByRoute = routeIds,
            compoundIdByCompound = compoundIds,
        ))
        val policy = requireNotNull(PkCalibrationRuntimePolicy.create(
            identityPolicy = identityPolicy,
            config = requireNotNull(PkCalibrationConfig.create(1.0, 0.1)),
            forwardModelVersion = FORWARD_VERSION,
            calibrationModelVersion = CALIBRATION_VERSION,
        ))
        return ValidResearchFixture(panel, policy)
    }

    private data class ValidResearchFixture(
        val panel: BloodTestPanel,
        val policy: PkCalibrationRuntimePolicy,
    )

    private companion object {
        const val ANALYTE_ID = "hrttracker:analyte/e2/v1"
        const val UNIT_ID = "hrttracker:unit/pg-ml/v1"
        const val POLICY_VERSION = "scope-policy:test/v1"
        const val ISSUER_ID = "issuer:test/v1"
        const val PROVENANCE_REF = "urn:test:scope-provenance:v1"
        const val FORWARD_VERSION = "pk-forward:test/v1"
        const val CALIBRATION_VERSION = "pk-calibration:test/v9"
        const val FixedNowMillis = 1_700_000_000_000L
    }
}
