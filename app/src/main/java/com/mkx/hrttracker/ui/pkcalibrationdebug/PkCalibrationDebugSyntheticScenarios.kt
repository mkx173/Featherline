package com.mkx.hrttracker.ui.pkcalibrationdebug

import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResult
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import com.mkx.hrttracker.model.pk.E2CalibrationDisposition
import com.mkx.hrttracker.model.pk.E2CalibrationMetadata
import com.mkx.hrttracker.model.pk.PkCalibrationBandState
import com.mkx.hrttracker.model.pk.PkCalibrationConfig
import com.mkx.hrttracker.model.pk.PkCalibrationE2LabSource
import com.mkx.hrttracker.model.pk.PkCalibrationEffectiveDisposition
import com.mkx.hrttracker.model.pk.PkCalibrationEngine
import com.mkx.hrttracker.model.pk.PkCalibrationEngineEvaluation
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationIdentityPolicy
import com.mkx.hrttracker.model.pk.PkCalibrationInputSnapshot
import com.mkx.hrttracker.model.pk.PkCalibrationMedicationEventSource
import com.mkx.hrttracker.model.pk.PkCalibrationRenderResult
import com.mkx.hrttracker.model.pk.PkCalibrationRenderState
import com.mkx.hrttracker.model.pk.PkCalibrationResult
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.PkCalibrationScopeInputSnapshot
import com.mkx.hrttracker.model.pk.PkChartDomain
import com.mkx.hrttracker.model.pk.PkCompound
import com.mkx.hrttracker.model.pk.PkDoseEvent
import com.mkx.hrttracker.model.pk.PkE2ForwardModel
import com.mkx.hrttracker.model.pk.PkHormone
import com.mkx.hrttracker.model.pk.PkRoute
import com.mkx.hrttracker.model.pk.PkRouteCalibrationDisplayState
import java.time.Instant
import java.util.UUID
import kotlin.math.exp

/**
 * Small, debug-only real-engine path for the stable named scenarios whose inputs are
 * deterministic. Manual state composition and deliberate renderer failures stay on the
 * validated contract-fixture path.
 */
internal object PkCalibrationDebugSyntheticScenarios {
    fun buildRenderFaultIfSupported(
        scenario: PkCalibrationDebugScenario,
    ): PkCalibrationDebugSourceResult? {
        if (!scenario.centralUnavailable && !scenario.bandUnavailable &&
            scenario.routeRenderFallback == null
        ) {
            return null
        }
        val baseScenario = PkCalibrationDebugScenario.create(
            globalState = scenario.globalState,
            routeStateByRoute = scenario.routeStateByRoute,
            routeRenderFallback = null,
            bandUnavailable = false,
            centralUnavailable = false,
            outlierRoute = scenario.outlierRoute,
            nonPositiveInput = scenario.nonPositiveInput,
            fixtureDisposition = scenario.fixtureDisposition,
        ) ?: return PkCalibrationDebugSourceResult.Unavailable(
            PkCalibrationDebugSourceUnavailableReason.SOURCE_CONTRACT_MISMATCH
        )
        return when (val base = buildIfSupported(baseScenario) ?: return null) {
            is PkCalibrationDebugSourceResult.Available ->
                PkCalibrationDebugSourceResult.Available(
                    PkCalibrationDebugFixtures.buildRenderFault(
                        result = base.snapshot.result,
                        scenario = scenario,
                        reviewDispositionByResultId =
                            base.snapshot.reviewDispositionByResultId,
                    )
                )
            else -> base
        }
    }

    fun buildIfSupported(
        scenario: PkCalibrationDebugScenario,
    ): PkCalibrationDebugSourceResult? {
        val preset = supportedPreset(scenario) ?: return null
        val snapshot = when (preset) {
            SupportedPreset.POPULATION_ONLY -> buildPopulationOnly(scenario)
            SupportedPreset.INJECTION_CALIBRATED -> buildInjectionCalibrated(scenario)
            SupportedPreset.MIXED_INJECTION_ORAL -> buildMixedInjectionOral(scenario)
            SupportedPreset.INJECTION_REVIEW_FIT -> buildInjectionReview(scenario)
            SupportedPreset.ORAL_OUT_OF_RANGE -> buildOralOutOfRange(scenario)
        }
        return snapshot?.let { value -> PkCalibrationDebugSourceResult.Available(value) }
            ?: PkCalibrationDebugSourceResult.Unavailable(
                PkCalibrationDebugSourceUnavailableReason.SOURCE_CONTRACT_MISMATCH
            )
    }

    private fun supportedPreset(
        scenario: PkCalibrationDebugScenario,
    ): SupportedPreset? {
        if (scenario == PkCalibrationDebugScenario.preset(
                PkCalibrationDebugPreset.POPULATION_ONLY
            )
        ) {
            return SupportedPreset.POPULATION_ONLY
        }
        if (scenario == PkCalibrationDebugScenario.preset(
                PkCalibrationDebugPreset.INJECTION_CALIBRATED
            )
        ) {
            return SupportedPreset.INJECTION_CALIBRATED
        }
        if (scenario == PkCalibrationDebugScenario.preset(
                PkCalibrationDebugPreset.MIXED_INJECTION_ORAL
            )
        ) {
            return SupportedPreset.MIXED_INJECTION_ORAL
        }
        if (scenario == PkCalibrationDebugScenario.preset(
                PkCalibrationDebugPreset.ORAL_OUT_OF_RANGE
            )
        ) {
            return SupportedPreset.ORAL_OUT_OF_RANGE
        }
        val reviewScenario = PkCalibrationDebugScenario
            .preset(PkCalibrationDebugPreset.INJECTION_REVIEW_FIT)
            .withFixtureDisposition(scenario.fixtureDisposition)
        return if (scenario == reviewScenario) {
            SupportedPreset.INJECTION_REVIEW_FIT
        } else {
            null
        }
    }

    private fun buildPopulationOnly(
        scenario: PkCalibrationDebugScenario,
    ): PkCalibrationDebugSnapshot? {
        val lab = createLab(
            resultId = debugUuid(20),
            collectedAtEpochMillis = OriginMillis + 8 * HourMillis,
            valuePgml = 100.0,
        ) ?: return null
        val domain = PkChartDomain.create(
            rangeStartEpochMillis = OriginMillis,
            rangeEndEpochMillis = OriginMillis + 24 * HourMillis,
            samplingIntervalMillis = 6 * HourMillis,
            chartGridVersion = DebugChartGridVersion,
            protectedKnotEpochMillis = listOf(lab.collectedAtEpochMillis),
        ) ?: return null
        val fixture = bindFixture(listOf(lab), emptyList(), domain) ?: return null
        return evaluateFixture(
            fixture = fixture,
            scenario = scenario,
            reviewTargetId = null,
        )?.takeIf { snapshot -> snapshot.result.matchesScenarioStates(scenario) }
    }

    private fun buildInjectionCalibrated(
        scenario: PkCalibrationDebugScenario,
    ): PkCalibrationDebugSnapshot? {
        val fixture = injectionFixture(
            timesH = listOf(4.0, 24.0, 72.0),
            qLogRatios = listOf(0.30, 0.30, 0.30),
            reviewTargetId = null,
        ) ?: return null
        return evaluateFixture(
            fixture = fixture,
            scenario = scenario,
            reviewTargetId = null,
        )?.takeIf { snapshot -> snapshot.result.matchesScenarioStates(scenario) }
    }

    private fun buildMixedInjectionOral(
        scenario: PkCalibrationDebugScenario,
    ): PkCalibrationDebugSnapshot? {
        val injection = createEvent(
            id = debugUuid(10),
            route = PkRoute.INJECTION,
            timeH = 0.0,
            doseMg = 2.0,
            compound = PkCompound.EB,
        ) ?: return null
        val oral = createEvent(
            id = debugUuid(11),
            route = PkRoute.ORAL,
            timeH = MixedOralDoseTimeH,
            doseMg = 10.0,
            compound = PkCompound.E2,
        ) ?: return null
        val fixture = syntheticFixture(
            events = listOf(injection, oral),
            labs = listOf(
                SyntheticLabSpec(debugUuid(30), PkCalibrationRoute.INJECTION, 4.0, 0.30),
                SyntheticLabSpec(debugUuid(31), PkCalibrationRoute.INJECTION, 24.0, 0.30),
                SyntheticLabSpec(debugUuid(32), PkCalibrationRoute.INJECTION, 72.0, 0.30),
                // Equal collection times deliberately exercise the provisional contrast gate.
                SyntheticLabSpec(
                    debugUuid(40),
                    PkCalibrationRoute.ORAL,
                    MixedOralLabTimeH,
                    0.30,
                ),
                SyntheticLabSpec(
                    debugUuid(41),
                    PkCalibrationRoute.ORAL,
                    MixedOralLabTimeH,
                    0.30,
                ),
                SyntheticLabSpec(
                    debugUuid(42),
                    PkCalibrationRoute.ORAL,
                    MixedOralLabTimeH,
                    0.30,
                ),
            ),
        ) ?: return null
        return evaluateFixture(
            fixture = fixture,
            scenario = scenario,
            reviewTargetId = null,
        )?.takeIf { snapshot -> snapshot.result.matchesScenarioStates(scenario) }
    }

    private fun buildOralOutOfRange(
        scenario: PkCalibrationDebugScenario,
    ): PkCalibrationDebugSnapshot? {
        val oral = createEvent(
            id = debugUuid(12),
            route = PkRoute.ORAL,
            timeH = 0.0,
            doseMg = 2.0,
            compound = PkCompound.E2,
        ) ?: return null
        val fixture = syntheticFixture(
            events = listOf(oral),
            labs = listOf(
                SyntheticLabSpec(debugUuid(50), PkCalibrationRoute.ORAL, 0.5, 0.90),
                SyntheticLabSpec(debugUuid(51), PkCalibrationRoute.ORAL, 2.0, 0.90),
                SyntheticLabSpec(debugUuid(52), PkCalibrationRoute.ORAL, 8.0, 0.90),
            ),
        ) ?: return null
        return evaluateFixture(
            fixture = fixture,
            scenario = scenario,
            reviewTargetId = null,
        )?.takeIf { snapshot -> snapshot.result.matchesScenarioStates(scenario) }
    }

    private fun buildInjectionReview(
        scenario: PkCalibrationDebugScenario,
    ): PkCalibrationDebugSnapshot? {
        val targetId = PkCalibrationDebugFixtures.outlierId(PkCalibrationRoute.INJECTION)
        val fixture = injectionFixture(
            timesH = listOf(4.0, 8.0, 12.0),
            qLogRatios = listOf(0.0, 0.0, 2.0),
            reviewTargetId = targetId,
        ) ?: return null
        val snapshot = evaluateFixture(
            fixture = fixture,
            scenario = scenario,
            reviewTargetId = targetId,
        ) ?: return null
        val injection = snapshot.result.routeResults.singleOrNull { result ->
            result.route == PkCalibrationRoute.INJECTION
        } ?: return null
        if (scenario.fixtureDisposition == PkCalibrationDebugFixtureDisposition.AUTO &&
            (injection.displayState !=
                    PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL ||
                    targetId !in injection.unreviewedOutlierLabIds)
        ) {
            return null
        }
        if (scenario.fixtureDisposition != PkCalibrationDebugFixtureDisposition.AUTO &&
            targetId in injection.unreviewedOutlierLabIds
        ) {
            return null
        }
        val expectedState = when (scenario.fixtureDisposition) {
            PkCalibrationDebugFixtureDisposition.AUTO ->
                PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL
            PkCalibrationDebugFixtureDisposition.EXCLUDED ->
                PkRouteCalibrationDisplayState.LAB_CALIBRATED
        }
        if (injection.displayState != expectedState || snapshot.result.routeResults
                .filterNot { result -> result.route == PkCalibrationRoute.INJECTION }
                .any { result ->
                    result.displayState !=
                            PkRouteCalibrationDisplayState.POPULATION_NO_SUPPORTING_LABS
                }
        ) {
            return null
        }
        return snapshot
    }

    private fun injectionFixture(
        timesH: List<Double>,
        qLogRatios: List<Double>,
        reviewTargetId: UUID?,
    ): SyntheticFixture? {
        if (timesH.size != qLogRatios.size || timesH.isEmpty()) return null
        val event = createEvent(
            id = debugUuid(10),
            route = PkRoute.INJECTION,
            timeH = 0.0,
            doseMg = 2.0,
            compound = PkCompound.EB,
        ) ?: return null
        return syntheticFixture(
            events = listOf(event),
            labs = timesH.indices.map { index ->
                SyntheticLabSpec(
                    resultId = if (reviewTargetId != null && index == timesH.lastIndex) {
                        reviewTargetId
                    } else {
                        debugUuid(30L + index)
                    },
                    route = PkCalibrationRoute.INJECTION,
                    timeH = timesH[index],
                    qLogRatio = qLogRatios[index],
                )
            },
        )
    }

    private fun syntheticFixture(
        events: List<PkCalibrationMedicationEventSource>,
        labs: List<SyntheticLabSpec>,
    ): SyntheticFixture? {
        if (events.isEmpty() || labs.isEmpty()) return null
        val forward = PkE2ForwardModel.create(events.map { source -> source.event }, BodyWeightKg)
            ?: return null
        val sources = labs.map { spec ->
            val breakdown = forward.breakdownAt(spec.timeH) ?: return null
            val dominant = breakdown.byRouteDrugPgml[spec.route]
                ?.takeIf { value -> value.isFinite() && value > 0.0 }
                ?: return null
            if (dominant < 0.8 * breakdown.totalDrugPgml) return null
            val otherRoutes = breakdown.totalDrugPgml - dominant
            val observed = otherRoutes + dominant * exp(spec.qLogRatio)
            if (!observed.isFinite() || observed <= 0.0) return null
            createLab(
                resultId = spec.resultId,
                collectedAtEpochMillis = epochMillisAt(spec.timeH) ?: return null,
                valuePgml = observed,
            ) ?: return null
        }
        val maximumTimeH = maxOf(
            labs.maxOf(SyntheticLabSpec::timeH),
            events.maxOf { source -> source.event.timeH },
        )
        val domain = PkChartDomain.create(
            rangeStartEpochMillis = sources.minOf(
                PkCalibrationE2LabSource::collectedAtEpochMillis
            ),
            rangeEndEpochMillis = epochMillisAt(maximumTimeH + 24.0) ?: return null,
            samplingIntervalMillis = 24 * HourMillis,
            chartGridVersion = DebugChartGridVersion,
            protectedKnotEpochMillis = sources.map(
                PkCalibrationE2LabSource::collectedAtEpochMillis
            ),
        ) ?: return null
        return bindFixture(sources, events, domain)
    }

    private fun evaluateFixture(
        fixture: SyntheticFixture,
        scenario: PkCalibrationDebugScenario,
        reviewTargetId: UUID?,
    ): PkCalibrationDebugSnapshot? {
        val autoEvaluation = fixture.evaluate(emptyList())
        if (!autoEvaluation.isReady ||
            autoEvaluation.result.globalState != PkCalibrationGlobalState.READY
        ) {
            return null
        }
        val disposition = if (reviewTargetId == null) {
            null
        } else {
            scenario.fixtureDisposition.toModelDisposition()
        }
        val metadata = when (disposition) {
            null,
            E2CalibrationDisposition.AUTO,
            E2CalibrationDisposition.ACCEPTED,
            -> emptyList()

            E2CalibrationDisposition.EXCLUDED -> {
                val targetId = reviewTargetId ?: return null
                listOf(
                    E2CalibrationMetadata.create(
                        resultId = targetId,
                        disposition = disposition,
                        acceptedRecord = null,
                        updatedAt = Instant.ofEpochMilli(ReviewUpdatedAtMillis),
                    ) ?: return null
                )
            }
        }
        val evaluation = if (metadata.isEmpty()) {
            autoEvaluation
        } else {
            fixture.evaluate(metadata)
        }
        if (!evaluation.isReady || !evaluation.matchesReview(reviewTargetId, disposition)) {
            return null
        }
        val render = evaluation.renderFor(fixture.domain) ?: return null
        if (!render.isHealthyFor(evaluation.result)) return null
        return PkCalibrationDebugSnapshot(
            result = evaluation.result,
            render = render,
            kind = PkCalibrationDebugSnapshotKind.SYNTHETIC_ENGINE_EVALUATION,
            scenario = scenario,
            reviewDispositionByResultId = if (reviewTargetId != null && disposition != null) {
                mapOf(reviewTargetId to disposition)
            } else {
                emptyMap()
            },
        )
    }

    private fun PkCalibrationEngineEvaluation.matchesReview(
        resultId: UUID?,
        disposition: E2CalibrationDisposition?,
    ): Boolean {
        if (resultId == null || disposition == null) return true
        val evidence = readyEvidence ?: return false
        return when (disposition) {
            E2CalibrationDisposition.AUTO,
            E2CalibrationDisposition.ACCEPTED,
            -> evidence.included
                .singleOrNull { item -> item.resultId == resultId }
                ?.effectiveDisposition == PkCalibrationEffectiveDisposition.AUTO

            E2CalibrationDisposition.EXCLUDED -> evidence.excluded
                .singleOrNull { item -> item.resultId == resultId }
                ?.effectiveDisposition == PkCalibrationEffectiveDisposition.EXCLUDED
        }
    }

    private fun bindFixture(
        labs: List<PkCalibrationE2LabSource>,
        events: List<PkCalibrationMedicationEventSource>,
        domain: PkChartDomain,
    ): SyntheticFixture? {
        val input = PkCalibrationInputSnapshot.create(
            labs = labs,
            medicationEvents = events,
            forwardTimeOriginEpochMillis = OriginMillis,
            resolvedCurrentWeightKg = BodyWeightKg,
            forwardModelVersion = DebugForwardModelVersion,
            calibrationModelVersion = DebugCalibrationModelVersion,
        ) ?: return null
        return SyntheticFixture(input, domain)
    }

    private fun createLab(
        resultId: UUID,
        collectedAtEpochMillis: Long,
        valuePgml: Double,
    ): PkCalibrationE2LabSource? {
        val result = BloodTestResult(
            uuid = resultId,
            createdAt = Instant.EPOCH,
            displayOrder = 0,
            analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.E2),
            value = valuePgml,
            unitSnapshot = SourceUnitSnapshot,
            canonicalValue = valuePgml,
        )
        val panel = BloodTestPanel(
            uuid = UUID(DebugUuidMost, resultId.leastSignificantBits + 1_000),
            collectedAt = Instant.ofEpochMilli(collectedAtEpochMillis),
            collectedAtTimeZoneId = "UTC",
            notes = null,
            timeSinceLastEstradiolDoseMillis = null,
            timeSinceLastTestosteroneDoseMillis = null,
            results = listOf(result),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        return PkCalibrationE2LabSource.create(
            panel = panel,
            result = result,
            analyteId = DebugAnalyteId,
            unitId = DebugUnitId,
        )
    }

    private fun createEvent(
        id: UUID,
        route: PkRoute,
        timeH: Double,
        doseMg: Double,
        compound: PkCompound,
    ): PkCalibrationMedicationEventSource? {
        val event = PkDoseEvent(
            id = id,
            sourceGroupUuid = null,
            hormone = PkHormone.ESTRADIOL,
            route = route,
            timeH = timeH,
            doseMg = doseMg,
            compound = compound,
        )
        return PkCalibrationMedicationEventSource.create(
            event = event,
            epochMillis = epochMillisAt(timeH) ?: return null,
            eventTypeId = EventTypeIds.getValue(route),
            hormoneId = DebugHormoneId,
            routeId = RouteIds.getValue(route),
            compoundId = CompoundIds.getValue(compound),
        )
    }

    private fun epochMillisAt(timeH: Double): Long? {
        if (!timeH.isFinite()) return null
        val offset = timeH * HourMillis.toDouble()
        if (!offset.isFinite() || offset < 0.0 || offset > Long.MAX_VALUE.toDouble()) return null
        val offsetMillis = offset.toLong()
        if (offsetMillis.toDouble() != offset) return null
        return runCatching { Math.addExact(OriginMillis, offsetMillis) }.getOrNull()
    }

    private fun PkCalibrationResult.matchesScenarioStates(
        scenario: PkCalibrationDebugScenario,
    ): Boolean {
        if (globalState != PkCalibrationGlobalState.READY ||
            routeResults.map { result -> result.route } != PkCalibrationRoute.entries.toList()
        ) {
            return false
        }
        return routeResults.all { result ->
            result.displayState == scenario.routeStateByRoute[result.route]
        }
    }

    private fun PkCalibrationRenderResult.isHealthyFor(
        result: PkCalibrationResult,
    ): Boolean {
        if (centralCurve.isEmpty() || renderReasons.isNotEmpty() ||
            routeRenderFallbacks.isNotEmpty() || bandReasons.isNotEmpty() ||
            effectivePromotedRoutes != result.promotedRoutes ||
            effectiveDisplayParams != result.displayParams
        ) {
            return false
        }
        return if (result.promotedRoutes.isEmpty()) {
            renderState == PkCalibrationRenderState.POPULATION &&
                    bandState == PkCalibrationBandState.NOT_APPLICABLE_POPULATION &&
                    bandKnots.isEmpty()
        } else {
            renderState == PkCalibrationRenderState.PERSONALIZED &&
                    bandState == PkCalibrationBandState.READY &&
                    bandKnots.isNotEmpty()
        }
    }

    private fun PkCalibrationDebugFixtureDisposition.toModelDisposition():
            E2CalibrationDisposition =
        when (this) {
            PkCalibrationDebugFixtureDisposition.AUTO -> E2CalibrationDisposition.AUTO
            PkCalibrationDebugFixtureDisposition.EXCLUDED -> E2CalibrationDisposition.EXCLUDED
        }

    private data class SyntheticFixture(
        val input: PkCalibrationInputSnapshot,
        val domain: PkChartDomain,
    ) {
        fun evaluate(metadata: List<E2CalibrationMetadata>): PkCalibrationEngineEvaluation {
            return PkCalibrationEngine.evaluate(
                input = input,
                metadata = metadata,
                identityPolicy = IdentityPolicy,
                config = ResearchConfig,
            )
        }
    }

    private data class SyntheticLabSpec(
        val resultId: UUID,
        val route: PkCalibrationRoute,
        val timeH: Double,
        val qLogRatio: Double,
    )

    private enum class SupportedPreset {
        POPULATION_ONLY,
        INJECTION_CALIBRATED,
        MIXED_INJECTION_ORAL,
        INJECTION_REVIEW_FIT,
        ORAL_OUT_OF_RANGE,
    }

    private fun debugUuid(value: Long): UUID = UUID(DebugUuidMost, value)

    private const val OriginMillis = 1_700_000_000_000L
    private const val HourMillis = 3_600_000L
    private const val BodyWeightKg = 70.0
    private const val ReviewUpdatedAtMillis = 9_000L
    private const val MixedOralDoseTimeH = 480.0
    private const val MixedOralLabTimeH = 480.75
    private const val DebugUuidMost = 0x44454255474c4142L
    private const val DebugAnalyteId = "debug:analyte/e2/v1"
    private const val DebugHormoneId = "debug:hormone/estradiol/v1"
    private const val DebugUnitId = "debug:unit/pg-ml/v1"
    private const val SourceUnitSnapshot = "pg_ml"
    private const val DebugPolicyVersion = "debug:scope-policy/v1"
    private const val DebugIssuerId = "debug:issuer/synthetic/v1"
    private const val DebugProvenanceRef = "urn:debug:scope-provenance:v1"
    private const val DebugForwardModelVersion = "debug:pk-forward/v1"
    private const val DebugCalibrationModelVersion = "debug:route-calibration/v10"
    private const val DebugChartGridVersion = "debug:pk-chart-grid/v1"

    private val EventTypeIds = linkedMapOf(
        PkRoute.INJECTION to "debug:event/injection-dose/v1",
        PkRoute.PATCH_APPLY to "debug:event/patch-apply/v1",
        PkRoute.PATCH_REMOVE to "debug:event/patch-remove/v1",
        PkRoute.GEL to "debug:event/gel-dose/v1",
        PkRoute.ORAL to "debug:event/oral-dose/v1",
        PkRoute.SUBLINGUAL to "debug:event/sublingual-dose/v1",
    )
    private val RouteIds = linkedMapOf(
        PkRoute.INJECTION to "injection",
        PkRoute.PATCH_APPLY to "patch",
        PkRoute.PATCH_REMOVE to "patch",
        PkRoute.GEL to "gel",
        PkRoute.ORAL to "oral",
        PkRoute.SUBLINGUAL to "sublingual",
    )
    private val CompoundIds = linkedMapOf(
        PkCompound.E2 to "debug:compound/e2/v1",
        PkCompound.EB to "debug:compound/eb/v1",
        PkCompound.EV to "debug:compound/ev/v1",
        PkCompound.EC to "debug:compound/ec/v1",
        PkCompound.EN to "debug:compound/en/v1",
        PkCompound.EU to "debug:compound/eu/v1",
    )
    private val ResearchConfig = requireNotNull(
        PkCalibrationConfig.create(
            drugMinInformativePgml = 1e-20,
            rLog = 0.04,
        )
    )
    private val IdentityPolicy = requireNotNull(
        PkCalibrationIdentityPolicy.researchOrTest(
            builtinE2AnalyteId = DebugAnalyteId,
            targetHormoneId = DebugHormoneId,
            unitIdBySourceSnapshot = mapOf(SourceUnitSnapshot to DebugUnitId),
            eventTypeIdByRoute = EventTypeIds,
            routeIdByRoute = RouteIds,
            compoundIdByCompound = CompoundIds,
        )
    )
}
