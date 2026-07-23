package com.mkx.hrttracker.data.repository

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.mkx.hrttracker.data.local.BloodTestPanelEntity
import com.mkx.hrttracker.data.local.BloodTestResultEntity
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResult
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import com.mkx.hrttracker.model.pk.E2CalibrationDisposition
import com.mkx.hrttracker.model.pk.PkAuthorizedE2Result
import com.mkx.hrttracker.model.pk.PkCalibrationConfig
import com.mkx.hrttracker.model.pk.PkCalibrationEffectiveDisposition
import com.mkx.hrttracker.model.pk.PkCalibrationE2LabSource
import com.mkx.hrttracker.model.pk.PkCalibrationEngine
import com.mkx.hrttracker.model.pk.PkCalibrationEvidenceAdapter
import com.mkx.hrttracker.model.pk.PkCalibrationEvidenceBuildResult
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationIdentityPolicy
import com.mkx.hrttracker.model.pk.PkCalibrationInputSnapshot
import com.mkx.hrttracker.model.pk.PkCalibrationLabEvidence
import com.mkx.hrttracker.model.pk.PkCalibrationMedicationEventSource
import com.mkx.hrttracker.model.pk.PkCalibrationReason
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.PkCalibrationScopeDecision
import com.mkx.hrttracker.model.pk.PkCalibrationScopeDecisionProvider
import com.mkx.hrttracker.model.pk.PkCalibrationScopeInputSnapshot
import com.mkx.hrttracker.model.pk.PkCompound
import com.mkx.hrttracker.model.pk.PkDoseEvent
import com.mkx.hrttracker.model.pk.PkE2ForwardModel
import com.mkx.hrttracker.model.pk.PkHormone
import com.mkx.hrttracker.model.pk.PkRoute
import com.mkx.hrttracker.model.pk.ResearchOrTestPkCalibrationScopeDecisionProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.exp

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PkCalibrationReviewActionServiceTest {
    private lateinit var database: HrtTrackerDatabase
    private lateinit var repository: PkCalibrationStorageRepository
    private val homeSnapshotRepository: HomeSnapshotRepository = mockk()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HrtTrackerDatabase::class.java,
        ).allowMainThreadQueries().build()
        val holder: DatabaseHolder = mockk()
        every { holder.get() } returns database
        coEvery { holder.withTransaction<Unit>(any()) } coAnswers {
            database.withTransaction {
                firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
            }
        }
        coEvery { homeSnapshotRepository.runHomeDataMutation<Unit>(any()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
        }
        coEvery {
            homeSnapshotRepository.runHomeDataMutationIfGenerationCurrent<Unit>(any(), any())
        } coAnswers {
            val expectedGeneration = firstArg<Long>()
            secondArg<suspend (Long) -> Unit>().invoke(expectedGeneration + 1L)
            HomeDataConditionalMutationResult.Published(
                generation = expectedGeneration + 1L,
                value = Unit,
            )
        }
        repository = PkCalibrationStorageRepository(holder, homeSnapshotRepository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun reviewActions_roundTripThroughRoom_andStaleAcceptanceNormalizesToAuto() = runTest {
        val fixture = outlierFixture()
        insertPersistedLabs(fixture)
        var currentInput = fixture.input
        val service = fixture.service(repository) { currentInput }
        val targetId = fixture.outlierResultId

        val initial = fixture.compute(repository.getAllMetadata(), currentInput)
        assertTrue(
            initial.routeResults.any { route ->
                targetId in route.unreviewedOutlierLabIds
            }
        )

        val kept = service.keepForAdjustment(targetId)
            as PkCalibrationReviewActionResult.Applied
        assertEquals(E2CalibrationDisposition.ACCEPTED, kept.metadata.disposition)
        assertNotNull(kept.metadata.acceptedReviewDigest)
        assertEquals(kept.metadata, repository.getAllMetadata().single())

        val accepted = fixture.readyEvidence(repository.getAllMetadata(), currentInput)
        assertEquals(
            PkCalibrationEffectiveDisposition.ACCEPTED,
            accepted.routeEvidence
                .flatMap { route -> route.dominantCandidates }
                .single { evidence -> evidence.resultId == targetId }
                .effectiveDisposition,
        )

        currentInput = fixture.withOneBitOutlierValueChange()
        val stale = fixture.readyEvidence(repository.getAllMetadata(), currentInput)
        val staleTarget = stale.routeEvidence
            .flatMap { route -> route.dominantCandidates }
            .single { evidence -> evidence.resultId == targetId }
        assertEquals(PkCalibrationEffectiveDisposition.AUTO, staleTarget.effectiveDisposition)
        assertEquals(kept.metadata, repository.getAllMetadata().single())

        val excluded = service.exclude(targetId) as PkCalibrationReviewActionResult.Applied
        assertEquals(E2CalibrationDisposition.EXCLUDED, excluded.metadata.disposition)
        assertEquals(null, excluded.metadata.acceptedReviewDigest)
        assertEquals(excluded.metadata, repository.getAllMetadata().single())

        val reIncluded = service.reinclude(targetId)
            as PkCalibrationReviewActionResult.Applied
        assertEquals(E2CalibrationDisposition.AUTO, reIncluded.metadata.disposition)
        coVerify(exactly = 3) {
            homeSnapshotRepository.runHomeDataMutationIfGenerationCurrent<Unit>(
                0L,
                any(),
            )
        }
        assertEquals(null, reIncluded.metadata.acceptedReviewDigest)
        assertEquals(reIncluded.metadata, repository.getAllMetadata().single())
    }

    @Test
    fun generationChangeAfterEvaluation_rejectsReviewWithoutWritingMetadata() = runTest {
        val fixture = outlierFixture(idOffset = 700)
        insertPersistedLabs(fixture)
        val expectedGeneration = 7L
        val service = fixture.service(
            repository = repository,
            inputGeneration = expectedGeneration,
        ) { fixture.input }
        coEvery {
            homeSnapshotRepository.runHomeDataMutationIfGenerationCurrent<Unit>(
                expectedGeneration,
                any(),
            )
        } returns HomeDataConditionalMutationResult.Stale(
            expectedGeneration = expectedGeneration,
            currentGeneration = expectedGeneration + 1L,
        )

        assertRejected(
            service.keepForAdjustment(fixture.outlierResultId),
            PkCalibrationReviewActionRejection.INPUT_GENERATION_CHANGED,
        )

        assertTrue(repository.getAllMetadata().isEmpty())
        coVerify(exactly = 1) {
            homeSnapshotRepository.runHomeDataMutationIfGenerationCurrent<Unit>(
                expectedGeneration,
                any(),
            )
        }
    }

    @Test
    fun invalidAndStaleActions_areExplicitAndNeverPartiallyWrite() = runTest {
        val fixture = outlierFixture()
        insertPersistedLabs(fixture)
        var currentInput = fixture.input
        val service = fixture.service(repository) { currentInput }
        val ordinaryId = fixture.labs.first().resultId

        assertRejected(
            service.keepForAdjustment(ordinaryId),
            PkCalibrationReviewActionRejection.NOT_CURRENT_UNREVIEWED_OUTLIER,
        )
        assertRejected(
            service.exclude(uuid(999)),
            PkCalibrationReviewActionRejection.LAB_NOT_AUTHORIZED_E2,
        )
        assertRejected(
            service.reinclude(ordinaryId),
            PkCalibrationReviewActionRejection.NOT_CURRENTLY_EXCLUDED,
        )
        assertTrue(repository.getAllMetadata().isEmpty())

        currentInput = fixture.withNonOutlierTargetValue()
        assertRejected(
            service.keepForAdjustment(fixture.outlierResultId),
            PkCalibrationReviewActionRejection.NOT_CURRENT_UNREVIEWED_OUTLIER,
        )
        assertTrue(repository.getAllMetadata().isEmpty())
    }

    @Test
    fun underlyingMissingOrNonE2Rows_areRejectedByTransactionalRepositoryWithoutWrites() =
        runTest {
            val missingFixture = outlierFixture(idOffset = 100)
            val missingService = missingFixture.service(repository) { missingFixture.input }
            assertRejected(
                missingService.exclude(missingFixture.outlierResultId),
                PkCalibrationReviewActionRejection.LAB_NOT_AUTHORIZED_E2,
            )
            assertTrue(repository.getAllMetadata().isEmpty())

            val nonE2Fixture = outlierFixture(idOffset = 200)
            insertPersistedLabs(nonE2Fixture, analyteOverride = "testosterone")
            val nonE2Service = nonE2Fixture.service(repository) { nonE2Fixture.input }
            assertRejected(
                nonE2Service.exclude(nonE2Fixture.outlierResultId),
                PkCalibrationReviewActionRejection.LAB_NOT_AUTHORIZED_E2,
            )
            assertTrue(repository.getAllMetadata().isEmpty())
        }

    @Test
    fun excludeAndReinclude_rejectEveryStaleScopeInputWithoutWriting() = runTest {
        val fixture = outlierFixture(idOffset = 300)
        insertPersistedLabs(fixture)
        val staleInputs = listOf(
            fixture.withAdditionalMedicationHistory(),
            fixture.withChangedWeight(),
            fixture.withChangedForwardModelVersion(),
        )

        for (staleInput in staleInputs) {
            val staleService = fixture.service(repository) { staleInput }
            assertRejected(
                staleService.exclude(fixture.outlierResultId),
                PkCalibrationReviewActionRejection.SCOPE_NOT_CURRENT,
            )
            assertTrue(repository.getAllMetadata().isEmpty())
        }

        val currentService = fixture.service(repository) { fixture.input }
        val excluded = currentService.exclude(fixture.outlierResultId)
            as PkCalibrationReviewActionResult.Applied
        for (staleInput in staleInputs) {
            val staleService = fixture.service(repository) { staleInput }
            assertRejected(
                staleService.reinclude(fixture.outlierResultId),
                PkCalibrationReviewActionRejection.SCOPE_NOT_CURRENT,
            )
            assertEquals(excluded.metadata, repository.getAllMetadata().single())
        }
    }

    @Test
    fun currentAuthorizedNonpositiveE2_canStillBeExcluded() = runTest {
        val fixture = outlierFixture(idOffset = 400).withNonpositiveTarget()
        insertPersistedLabs(fixture)
        val before = fixture.compute(emptyList(), fixture.input)
        assertEquals(PkCalibrationGlobalState.SHARED_INPUT_INVALID, before.globalState)
        assertEquals(
            setOf(PkCalibrationReason.INVALID_NONPOSITIVE_E2),
            before.globalReasons,
        )

        val service = fixture.service(repository) { fixture.input }
        val result = service.exclude(fixture.outlierResultId)
            as PkCalibrationReviewActionResult.Applied

        assertEquals(E2CalibrationDisposition.EXCLUDED, result.metadata.disposition)
        assertEquals(null, result.metadata.acceptedReviewDigest)
        assertEquals(result.metadata, repository.getAllMetadata().single())
    }

    @Test
    fun currentContextProvider_ordinaryFailureMapsToUnavailableWithoutWriting() = runTest {
        val fixture = outlierFixture(idOffset = 500)
        insertPersistedLabs(fixture)
        val service = fixture.service(repository) { throw IllegalStateException("unavailable") }

        assertRejected(
            service.exclude(fixture.outlierResultId),
            PkCalibrationReviewActionRejection.CURRENT_INPUT_UNAVAILABLE,
        )
        assertTrue(repository.getAllMetadata().isEmpty())
    }

    @Test
    fun currentContextProvider_cancellationIsRethrownWithoutWriting() = runTest {
        val fixture = outlierFixture(idOffset = 600)
        insertPersistedLabs(fixture)
        val expected = CancellationException("cancel current input")
        val service = fixture.service(repository) { throw expected }

        val caught = try {
            service.exclude(fixture.outlierResultId)
            null
        } catch (cancellation: CancellationException) {
            cancellation
        }

        assertTrue(caught === expected)
        assertTrue(repository.getAllMetadata().isEmpty())
    }

    private fun outlierFixture(idOffset: Long = 0): Fixture {
        val event = medicationEvent(uuid(10 + idOffset), PkRoute.ORAL, 0.0)
        val forward = requireNotNull(PkE2ForwardModel.create(listOf(event.event), WeightKg))
        val qValues = listOf(0.0, 0.0, 2.0)
        val timesH = listOf(4.0, 8.0, 12.0)
        val labs = qValues.mapIndexed { index, q ->
            val total = requireNotNull(forward.breakdownAt(timesH[index])).totalDrugPgml
            lab(
                resultId = uuid(idOffset + index + 1),
                collectedAtEpochMillis = OriginMillis + (timesH[index] * HourMillis).toLong(),
                value = total * exp(q),
            )
        }
        val authorizations = labs.map { source ->
            requireNotNull(PkAuthorizedE2Result.create(source.resultId, source.resultScopeDigest()))
        }
        val scopeInput = requireNotNull(
            PkCalibrationScopeInputSnapshot.create(
                authorizedE2Results = authorizations,
                medicationEvents = listOf(event),
                resolvedCurrentWeightKg = WeightKg,
                forwardModelVersion = ForwardModelVersion,
            )
        )
        val decision = requireNotNull(
            PkCalibrationScopeDecision.create(
                decisionId = uuid(90 + idOffset),
                revision = 1,
                policyVersion = PolicyVersion,
                issuerId = IssuerId,
                issuedAt = Instant.ofEpochMilli(1_234),
                provenanceRef = ProvenanceRef,
                inputSnapshotDigest = scopeInput.digest,
                authorizedE2Results = authorizations,
            )
        )
        val provider = requireNotNull(
            ResearchOrTestPkCalibrationScopeDecisionProvider.create(
                decision = decision,
                expectedPolicyVersion = PolicyVersion,
                expectedIssuerId = IssuerId,
                expectedProvenanceRef = ProvenanceRef,
            )
        )
        return Fixture(
            labs = labs,
            event = event,
            provider = provider,
            outlierResultId = labs.last().resultId,
        )
    }

    private inner class Fixture(
        val labs: List<PkCalibrationE2LabSource>,
        val event: PkCalibrationMedicationEventSource,
        val provider: PkCalibrationScopeDecisionProvider,
        val outlierResultId: UUID,
    ) {
        val input: PkCalibrationInputSnapshot
            get() = input(labs)

        fun service(
            repository: PkCalibrationStorageRepository,
            inputGeneration: Long = 0L,
            currentInput: suspend () -> PkCalibrationInputSnapshot?,
        ): PkCalibrationReviewActionService {
            return PkCalibrationReviewActionService(
                storageRepository = repository,
                currentContextProvider = PkCalibrationCurrentEvaluationContextProvider {
                    currentInput()?.let { input ->
                        PkCalibrationEvaluationContext.forTest(
                            inputGeneration = inputGeneration,
                            input = input,
                            metadata = repository.getAllMetadata(),
                            identityPolicy = IdentityPolicy,
                            config = CalibrationConfig,
                            scopeDecisionProvider = provider,
                        )
                    }
                },
                clock = FixedClock,
            )
        }

        fun compute(
            metadata: List<com.mkx.hrttracker.model.pk.E2CalibrationMetadata>,
            input: PkCalibrationInputSnapshot,
        ) = PkCalibrationEngine.compute(
            input = input,
            metadata = metadata,
            identityPolicy = IdentityPolicy,
            config = CalibrationConfig,
            scopeDecisionProvider = provider,
        )

        fun readyEvidence(
            metadata: List<com.mkx.hrttracker.model.pk.E2CalibrationMetadata>,
            input: PkCalibrationInputSnapshot,
        ) = (PkCalibrationEvidenceAdapter.build(
            labs = input.labs,
            medicationEvents = input.medicationEvents,
            forwardTimeOriginEpochMillis = input.forwardTimeOriginEpochMillis,
            resolvedCurrentWeightKg = input.resolvedCurrentWeightKg,
            metadata = metadata,
            identityPolicy = IdentityPolicy,
            config = CalibrationConfig,
            scopeDecisionProvider = provider,
            forwardModelVersion = input.forwardModelVersion,
            calibrationModelVersion = input.calibrationModelVersion,
        ) as PkCalibrationEvidenceBuildResult.Ready).partition

        fun withOneBitOutlierValueChange(): PkCalibrationInputSnapshot {
            return replaceOutlierValue(Math.nextUp(labs.last().sourceValue))
        }

        fun withNonOutlierTargetValue(): PkCalibrationInputSnapshot {
            val target = labs.last()
            val timeH = (target.collectedAtEpochMillis - OriginMillis).toDouble() /
                    HourMillis.toDouble()
            val forward = requireNotNull(
                PkE2ForwardModel.create(listOf(event.event), WeightKg)
            )
            val populationValue = requireNotNull(forward.breakdownAt(timeH)).totalDrugPgml
            return replaceOutlierValue(populationValue)
        }

        fun withAdditionalMedicationHistory(): PkCalibrationInputSnapshot {
            val additional = medicationEvent(uuid(8_001), PkRoute.ORAL, -24.0)
            return input(
                labs = labs,
                medicationEvents = listOf(additional, event),
            )
        }

        fun withChangedWeight(): PkCalibrationInputSnapshot {
            return input(labs = labs, weightKg = Math.nextUp(WeightKg))
        }

        fun withChangedForwardModelVersion(): PkCalibrationInputSnapshot {
            return input(labs = labs, forwardModelVersion = "pk-forward:test/v2")
        }

        fun withNonpositiveTarget(): Fixture {
            val target = labs.last()
            val changed = lab(
                resultId = target.resultId,
                collectedAtEpochMillis = target.collectedAtEpochMillis,
                value = 0.0,
            )
            return Fixture(
                labs = labs.dropLast(1) + changed,
                event = event,
                provider = provider,
                outlierResultId = outlierResultId,
            )
        }

        private fun replaceOutlierValue(value: Double): PkCalibrationInputSnapshot {
            val target = labs.last()
            val changed = lab(
                resultId = target.resultId,
                collectedAtEpochMillis = target.collectedAtEpochMillis,
                value = value,
            )
            return input(labs.dropLast(1) + changed)
        }

        private fun input(
            labs: List<PkCalibrationE2LabSource>,
            medicationEvents: List<PkCalibrationMedicationEventSource> = listOf(event),
            weightKg: Double? = WeightKg,
            forwardModelVersion: String = ForwardModelVersion,
        ): PkCalibrationInputSnapshot {
            return requireNotNull(
                PkCalibrationInputSnapshot.create(
                    labs = labs,
                    medicationEvents = medicationEvents,
                    forwardTimeOriginEpochMillis = OriginMillis,
                    resolvedCurrentWeightKg = weightKg,
                    forwardModelVersion = forwardModelVersion,
                    calibrationModelVersion = CalibrationModelVersion,
                )
            )
        }
    }

    private suspend fun insertPersistedLabs(
        fixture: Fixture,
        analyteOverride: String? = null,
    ) {
        fixture.labs.forEachIndexed { index, lab ->
            val panelId = "panel-${lab.resultId}"
            database.bloodTestDao().insertPanel(
                BloodTestPanelEntity(
                    uuid = panelId,
                    collectedAtInstantEpochMillis = lab.collectedAtEpochMillis,
                    collectedAtTimeZoneId = "UTC",
                    notes = null,
                    timeSinceLastEstradiolDoseMillis = null,
                    timeSinceLastTestosteroneDoseMillis = null,
                    createdAtEpochMillis = 1_000,
                    updatedAtEpochMillis = 1_000,
                )
            )
            database.bloodTestDao().insertResults(
                listOf(
                    BloodTestResultEntity(
                        uuid = lab.resultId.toString(),
                        panelUuid = panelId,
                        createdAtEpochMillis = 1_000,
                        displayOrder = index,
                        builtinAnalyteKey = analyteOverride ?: "e2",
                        customAnalyteUuid = null,
                        value = lab.sourceValue,
                        unitSnapshot = "pg_ml",
                        canonicalValue = lab.canonicalValuePgml,
                    )
                )
            )
        }
    }

    private fun lab(
        resultId: UUID,
        collectedAtEpochMillis: Long,
        value: Double,
    ): PkCalibrationE2LabSource {
        val result = BloodTestResult(
            uuid = resultId,
            createdAt = Instant.EPOCH,
            displayOrder = 0,
            analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.E2),
            value = value,
            unitSnapshot = "pg_ml",
            canonicalValue = value,
        )
        val panel = BloodTestPanel(
            uuid = UUID(1, resultId.leastSignificantBits),
            collectedAt = Instant.ofEpochMilli(collectedAtEpochMillis),
            collectedAtTimeZoneId = "UTC",
            notes = null,
            timeSinceLastEstradiolDoseMillis = null,
            timeSinceLastTestosteroneDoseMillis = null,
            results = listOf(result),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        return requireNotNull(
            PkCalibrationE2LabSource.create(
                panel = panel,
                result = result,
                analyteId = AnalyteId,
                unitId = UnitId,
                providerId = ProviderId,
                assayMethodId = AssayId,
            )
        )
    }

    private fun medicationEvent(
        eventId: UUID,
        route: PkRoute,
        timeH: Double,
    ): PkCalibrationMedicationEventSource {
        val event = PkDoseEvent(
            id = eventId,
            sourceGroupUuid = null,
            hormone = PkHormone.ESTRADIOL,
            route = route,
            timeH = timeH,
            doseMg = 2.0,
            compound = PkCompound.E2,
        )
        return requireNotNull(
            PkCalibrationMedicationEventSource.create(
                event = event,
                epochMillis = OriginMillis + (timeH * HourMillis).toLong(),
                eventTypeId = EventTypeIds.getValue(route),
                hormoneId = HormoneId,
                routeId = RouteIds.getValue(route),
                compoundId = CompoundIds.getValue(PkCompound.E2),
            )
        )
    }

    private fun assertRejected(
        result: PkCalibrationReviewActionResult,
        expected: PkCalibrationReviewActionRejection,
    ) {
        assertTrue(result is PkCalibrationReviewActionResult.Rejected)
        assertEquals(expected, (result as PkCalibrationReviewActionResult.Rejected).reason)
    }

    private fun uuid(value: Long): UUID = UUID(0, value)

    private companion object {
        const val OriginMillis = 1_700_000_000_000L
        const val HourMillis = 3_600_000L
        const val WeightKg = 70.0
        const val AnalyteId = "hrttracker:analyte/e2/v1"
        const val HormoneId = "hrttracker:hormone/estradiol/v1"
        const val UnitId = "hrttracker:unit/pg-ml/v1"
        const val ProviderId = "provider:test/v1"
        const val AssayId = "assay:test/v1"
        const val PolicyVersion = "scope-policy:test/v1"
        const val IssuerId = "issuer:test/v1"
        const val ProvenanceRef = "urn:test:scope-provenance:v1"
        const val ForwardModelVersion = "pk-forward:test/v1"
        const val CalibrationModelVersion = "pk-calibration:test/v9"

        val FixedClock: Clock = Clock.fixed(Instant.ofEpochMilli(9_000), ZoneOffset.UTC)
        val CalibrationConfig: PkCalibrationConfig = requireNotNull(
            PkCalibrationConfig.researchOrTest(
                drugMinInformativePgml = 1e-20,
                rLog = 0.04,
            )
        )
        val EventTypeIds = linkedMapOf(
            PkRoute.INJECTION to "event:injection-dose/v1",
            PkRoute.PATCH_APPLY to "event:patch-apply/v1",
            PkRoute.PATCH_REMOVE to "event:patch-remove/v1",
            PkRoute.GEL to "event:gel-dose/v1",
            PkRoute.ORAL to "event:oral-dose/v1",
            PkRoute.SUBLINGUAL to "event:sublingual-dose/v1",
        )
        val RouteIds = linkedMapOf(
            PkRoute.INJECTION to "injection",
            PkRoute.PATCH_APPLY to "patch",
            PkRoute.PATCH_REMOVE to "patch",
            PkRoute.GEL to "gel",
            PkRoute.ORAL to "oral",
            PkRoute.SUBLINGUAL to "sublingual",
        )
        val CompoundIds = linkedMapOf(
            PkCompound.E2 to "compound:e2/v1",
            PkCompound.EB to "compound:eb/v1",
            PkCompound.EV to "compound:ev/v1",
            PkCompound.EC to "compound:ec/v1",
            PkCompound.EN to "compound:en/v1",
            PkCompound.EU to "compound:eu/v1",
        )
        val IdentityPolicy: PkCalibrationIdentityPolicy = requireNotNull(
            PkCalibrationIdentityPolicy.researchOrTest(
                builtinE2AnalyteId = AnalyteId,
                targetHormoneId = HormoneId,
                unitIdBySourceSnapshot = mapOf("pg_ml" to UnitId),
                supportedProviderIds = setOf(ProviderId),
                supportedAssayMethodIds = setOf(AssayId),
                eventTypeIdByRoute = EventTypeIds,
                routeIdByRoute = RouteIds,
                compoundIdByCompound = CompoundIds,
                trustedPolicyVersions = setOf(PolicyVersion),
                trustedIssuerIds = setOf(IssuerId),
                trustedProvenanceRefs = setOf(ProvenanceRef),
            )
        )
    }
}
