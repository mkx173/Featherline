package com.mkx.hrttracker.model.pk

import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResult
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Shared host/minified-Android numerical vectors.
 *
 * This source directory is wired only into `test` and `benchmark`. It must not be added to
 * `main`: the benchmark-only manifest entrypoint is what intentionally retains and optimizes
 * the calibration call graph under R8, while an ordinary release remains free to remove it.
 */
object PkCalibrationParityVectors {
    const val ACTION = "com.mkx.hrttracker.action.RUN_PK_CALIBRATION_R8_PARITY"
    const val RESULT_TAG = "pk_calibration_r8_parity_result"
    const val SUCCESS_TEXT = "PK_CALIBRATION_R8_PARITY_OK:v1"

    /**
     * Decimal references were evaluated with 100-digit arithmetic using the exact binary64
     * input constants. Hex endpoints are an outward binary64 operation envelope (including
     * the one-ulp Math.exp contract), so the check needs no newly invented relative tolerance.
     */
    val forwardOracles: List<PkForwardOracleVector> = listOf(
        PkForwardOracleVector(
            id = "injection_ev_2mg_8h",
            route = PkCalibrationRoute.INJECTION,
            sampleTimeH = 8.0,
            highPrecisionDecimalPgMl =
                "24.0004598511476351830856715137373322012994130453065174807940549073321144002852902818814758",
            expectedPgMl = PkBinary64OutwardInterval("4038001e2305a2ad", "4038001e2305a4d3"),
        ),
        PkForwardOracleVector(
            id = "patch_100ug_8h",
            route = PkCalibrationRoute.PATCH,
            sampleTimeH = 8.0,
            highPrecisionDecimalPgMl =
                "69.8585760157392450447502152352875163421819277890364429980752955886482634072594461286822283",
            expectedPgMl = PkBinary64OutwardInterval("405176f2e8d12eb6", "405176f2e8d12ebc"),
        ),
        PkForwardOracleVector(
            id = "patch_100ug_removed18_42h",
            route = PkCalibrationRoute.PATCH,
            sampleTimeH = 42.0,
            highPrecisionDecimalPgMl =
                "0.00386498891841778824706245659795876725853332175400519627192679216277914441107423650935881432",
            expectedPgMl = PkBinary64OutwardInterval("3f6fa9782020d2db", "3f6fa9782020d2f7"),
        ),
        PkForwardOracleVector(
            id = "gel_1mg_8h",
            route = PkCalibrationRoute.GEL,
            sampleTimeH = 8.0,
            highPrecisionDecimalPgMl =
                "19.464404252935659980056334196224051583358200656758236252040880347694024292685402107850995",
            expectedPgMl = PkBinary64OutwardInterval("403376e332767b5e", "403376e332767b66"),
        ),
        PkForwardOracleVector(
            id = "oral_2mg_8h",
            route = PkCalibrationRoute.ORAL,
            sampleTimeH = 8.0,
            highPrecisionDecimalPgMl =
                "60.4594036359977711816790765510122865789114632810680511203968196403408160182504466070989017",
            expectedPgMl = PkBinary64OutwardInterval("404e3acdbd042308", "404e3acdbd04231b"),
        ),
        PkForwardOracleVector(
            id = "sublingual_1mg_8h",
            route = PkCalibrationRoute.SUBLINGUAL,
            sampleTimeH = 8.0,
            highPrecisionDecimalPgMl =
                "65.1895552990456045870171408957790380191446425794069663359963915277374567610939588206899313",
            expectedPgMl = PkBinary64OutwardInterval("40504c21ac8c8bc8", "40504c21ac8c8bd2"),
        ),
    )

    /**
     * Full adapter -> solver -> renderer oracle. Beta roots use the existing normative
     * stationary-root absolute bound; the curve envelopes propagate those roots through the
     * same high-precision outward binary64 model used by the per-route vectors. Noncentral
     * band endpoints use an independent 128-node Gauss-Hermite reference expanded only by the
     * existing BAND_VALIDATE_ABS_PGML + BAND_VALIDATE_REL contract.
     */
    val engineOracle = PkEngineRenderOracleVector(
        scopeDecisionDigestHex =
            "acc62d8d9e9297151b79f3d0c6b472533018429a00429a523bb85096e7d06517",
        renderDomainDigestHex =
            "9b6f5023e71993abe603a815fe39ab70049be46ad14b7f6b0bc43e3412d03bde",
        fittedBetaByRoute = linkedMapOf(
            PkCalibrationRoute.INJECTION to
                    PkBinary64OutwardInterval("3fbffbb5c29ff96f", "3fbffbb5c2a22c65"),
            PkCalibrationRoute.PATCH to
                    PkBinary64OutwardInterval("0000000000000000", "0000000000000000"),
            PkCalibrationRoute.GEL to
                    PkBinary64OutwardInterval("3fb5d0042988d21f", "3fb5d004298b0515"),
            PkCalibrationRoute.ORAL to
                    PkBinary64OutwardInterval("3fc4dbf29822728a", "3fc4dbf298238c06"),
            PkCalibrationRoute.SUBLINGUAL to
                    PkBinary64OutwardInterval("bfc986c1555fad2a", "bfc986c1555e93ae"),
        ),
        selectedEpochMillis = epochMillis(1_008.0),
        selectedCentralPgMl = PkBinary64OutwardInterval(
            "403b31be305651b1",
            "403b31be30568ff6",
        ),
        selectedBandPgMl = listOf(
            PkBinary64OutwardInterval("402e3552965a4dbb", "402e780e550d59f1"),
            PkBinary64OutwardInterval("40350eeb79b376d4", "403533568ca6c122"),
            PkBinary64OutwardInterval("403b31be305651aa", "403b31be30568ffe"),
            PkBinary64OutwardInterval("404175205aa094e0", "40418ae2ead32118"),
            PkBinary64OutwardInterval("40485394e82d49b7", "40486cdcb35725ff"),
        ),
    )

    fun run(): PkCalibrationParityReport {
        val failures = mutableListOf<String>()
        runCatching { checkForwardOracles(failures) }
            .onFailure { error -> failures += "forward-exception:${error.describe()}" }
        runCatching { checkEngineAndRenderer(failures) }
            .onFailure { error -> failures += "engine-exception:${error.describe()}" }
        return PkCalibrationParityReport(failures)
    }

    private fun checkForwardOracles(failures: MutableList<String>) {
        for (oracle in forwardOracles) {
            val decimalReference = BigDecimal(oracle.highPrecisionDecimalPgMl).toDouble()
            if (!oracle.expectedPgMl.contains(decimalReference)) {
                failures += "${oracle.id}:decimal=${decimalReference.toHexBits()}"
            }

            val forward = PkE2ForwardModel.create(
                events = isolatedEvents(oracle.id),
                bodyWeightKg = BodyWeightKg,
            )
            if (forward == null) {
                failures += "${oracle.id}:forward-null"
                continue
            }
            val breakdown = forward.breakdownAt(oracle.sampleTimeH)
            val direct = forward.directDrugPgmlAt(oracle.sampleTimeH)
            if (breakdown == null || direct == null) {
                failures += "${oracle.id}:evaluation-null"
                continue
            }
            val contribution = breakdown.byRouteDrugPgml.getValue(oracle.route)
            if (!oracle.expectedPgMl.contains(contribution)) {
                failures += "${oracle.id}:route=${contribution.toHexBits()}"
            }
            if (!oracle.expectedPgMl.contains(direct)) {
                failures += "${oracle.id}:direct=${direct.toHexBits()}"
            }
            val simulation = PkSimulationEngine(
                events = isolatedEvents(oracle.id),
                hormone = PkHormone.ESTRADIOL,
                bodyWeightKg = BodyWeightKg,
                startTimeH = 0.0,
                endTimeH = oracle.sampleTimeH,
                numberOfSteps = 2,
            ).run(sampleTimeH = listOf(0.0, oracle.sampleTimeH))
                .concentrationAt(oracle.sampleTimeH)
            if (simulation == null || !oracle.expectedPgMl.contains(simulation)) {
                failures += "${oracle.id}:simulation=${simulation?.toHexBits()}"
            }
            if (breakdown.totalDrugPgml.toBits() != contribution.toBits()) {
                failures += "${oracle.id}:total=${breakdown.totalDrugPgml.toHexBits()}"
            }
            for (otherRoute in PkCalibrationRoute.entries - oracle.route) {
                val other = breakdown.byRouteDrugPgml.getValue(otherRoute)
                if (other.toBits() != 0L) {
                    failures += "${oracle.id}:${otherRoute.stableId}=${other.toHexBits()}"
                }
            }
        }
    }

    private fun checkEngineAndRenderer(failures: MutableList<String>) {
        val fixture = engineFixture()
        val evaluation = PkCalibrationEngine.evaluate(
            input = fixture.input,
            metadata = emptyList(),
            identityPolicy = fixture.identityPolicy,
            config = fixture.config,
            scopeDecisionProvider = fixture.scopeDecisionProvider,
        )
        val result = evaluation.result
        if (!evaluation.isReady || result.globalState != PkCalibrationGlobalState.READY) {
            failures += "engine-state:${result.globalState}"
            return
        }
        if (result.scopeDecisionDigest?.hexLower != engineOracle.scopeDecisionDigestHex) {
            failures += "scope-digest:${result.scopeDecisionDigest?.hexLower}"
        }
        if (result.promotedRoutes != PkCalibrationRoute.entries) {
            failures += "promoted:${result.promotedRoutes.joinToString { it.stableId }}"
        }
        for (routeResult in result.routeResults) {
            if (routeResult.displayState != PkRouteCalibrationDisplayState.LAB_CALIBRATED) {
                failures += "${routeResult.route.stableId}-state:${routeResult.displayState}"
            }
            val fittedBeta = routeResult.fittedBeta
            val interval = engineOracle.fittedBetaByRoute.getValue(routeResult.route)
            if (fittedBeta == null || !interval.contains(fittedBeta)) {
                failures += "${routeResult.route.stableId}-beta:${fittedBeta?.toHexBits()}"
            }
        }
        if (PkCalibrationRoute.PATCH !in result.promotedRoutes ||
            PkCalibrationRoute.PATCH in result.displayParams.routeLogScale ||
            result.routeResults.single { it.route == PkCalibrationRoute.PATCH }
                .displayBeta.toBits() != 0L
        ) {
            failures += "zero-beta-promotion-identity"
        }

        val domain = requireNotNull(
            PkChartDomain.create(
                rangeStartEpochMillis = epochMillis(1_001.0),
                rangeEndEpochMillis = epochMillis(1_096.0),
                samplingIntervalMillis = 24L * HourMillis,
                chartGridVersion = ChartGridVersion,
                protectedKnotEpochMillis = listOf(engineOracle.selectedEpochMillis),
            )
        )
        val render = PkCalibrationRenderer.render(evaluation, domain)
        if (render == null) {
            failures += "render-null"
            return
        }
        if (render.domainDigest.hexLower != engineOracle.renderDomainDigestHex) {
            failures += "render-digest:${render.domainDigest.hexLower}"
        }
        if (render.renderState != PkCalibrationRenderState.PERSONALIZED ||
            render.bandState != PkCalibrationBandState.READY ||
            render.effectivePromotedRoutes != PkCalibrationRoute.entries ||
            render.routeRenderFallbacks.isNotEmpty()
        ) {
            failures += "render-state:${render.renderState}/${render.bandState}"
        }
        val selected = render.centralCurve.singleOrNull {
            it.epochMillis == engineOracle.selectedEpochMillis
        }
        if (selected == null || !engineOracle.selectedCentralPgMl.contains(
                selected.concentrationPgMl
            )
        ) {
            failures += "central:${selected?.concentrationPgMl?.toHexBits()}"
        }
        val bandKnot = render.bandKnots.singleOrNull {
            it.epochMillis == engineOracle.selectedEpochMillis
        }
        val bandValues = bandKnot?.let { knot ->
            listOf(
                knot.p025Pgml,
                knot.p158655254Pgml,
                knot.p50Pgml,
                knot.p841344746Pgml,
                knot.p975Pgml,
            )
        }
        if (bandValues == null || bandValues.size != engineOracle.selectedBandPgMl.size) {
            failures += "band-knot-missing"
        } else {
            for (index in bandValues.indices) {
                if (!engineOracle.selectedBandPgMl[index].contains(bandValues[index])) {
                    failures += "band-$index:${bandValues[index].toHexBits()}"
                }
            }
        }

        val forward = requireNotNull(
            PkE2ForwardModel.create(
                fixture.input.medicationEvents.map { it.event },
                BodyWeightKg,
            )
        )
        val population = requireNotNull(forward.breakdownAt(1_008.0)).totalDrugPgml
        if (selected != null && selected.concentrationPgMl.toBits() == population.toBits()) {
            failures += "nonzero-forward-scaling-not-observed"
        }
    }

    private fun engineFixture(): EngineFixture {
        val events = engineMedicationEvents()
        val labs = engineLabObservations.mapIndexed { index, observation ->
            lab(
                resultId = uuid(100L + index),
                collectedAtEpochMillis = epochMillis(observation.timeH),
                observedPgMl = observation.observedPgMl,
            )
        }
        val authorizations = labs.map { lab ->
            requireNotNull(PkAuthorizedE2Result.create(lab.resultId, lab.resultScopeDigest()))
        }
        val scopeInput = requireNotNull(
            PkCalibrationScopeInputSnapshot.create(
                authorizedE2Results = authorizations,
                medicationEvents = events,
                resolvedCurrentWeightKg = BodyWeightKg,
                forwardModelVersion = ForwardModelVersion,
            )
        )
        val decision = requireNotNull(
            PkCalibrationScopeDecision.create(
                decisionId = uuid(900),
                revision = 1L,
                policyVersion = PolicyVersion,
                issuerId = IssuerId,
                issuedAt = Instant.ofEpochMilli(1_234L),
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
        val policy = requireNotNull(
            PkCalibrationIdentityPolicy.researchOrTest(
                builtinE2AnalyteId = AnalyteId,
                targetHormoneId = HormoneId,
                unitIdBySourceSnapshot = mapOf(SourceUnitSnapshot to UnitId),
                supportedProviderIds = setOf(ProviderId),
                supportedAssayMethodIds = setOf(AssayId),
                eventTypeIdByRoute = EventTypeIdByRoute,
                routeIdByRoute = RouteIdByRoute,
                compoundIdByCompound = CompoundIdByCompound,
                trustedPolicyVersions = setOf(PolicyVersion),
                trustedIssuerIds = setOf(IssuerId),
                trustedProvenanceRefs = setOf(ProvenanceRef),
            )
        )
        val config = requireNotNull(
            PkCalibrationConfig.researchOrTest(
                drugMinInformativePgml = 1e-12,
                rLog = 0.04,
            )
        )
        return EngineFixture(
            input = requireNotNull(
                PkCalibrationInputSnapshot.create(
                    labs = labs,
                    medicationEvents = events,
                    forwardTimeOriginEpochMillis = OriginMillis,
                    resolvedCurrentWeightKg = BodyWeightKg,
                    forwardModelVersion = ForwardModelVersion,
                    calibrationModelVersion = CalibrationModelVersion,
                )
            ),
            identityPolicy = policy,
            config = config,
            scopeDecisionProvider = provider,
        )
    }

    private fun isolatedEvents(id: String): List<PkDoseEvent> {
        return when (id) {
            "injection_ev_2mg_8h" -> listOf(
                doseEvent(1, PkRoute.INJECTION, 0.0, 2.0, PkCompound.EV)
            )
            "patch_100ug_8h", "patch_100ug_removed18_42h" -> listOf(
                doseEvent(
                    2,
                    PkRoute.PATCH_APPLY,
                    0.0,
                    0.0,
                    PkCompound.E2,
                    releaseRateMcgPerDay = 100.0,
                ),
                doseEvent(3, PkRoute.PATCH_REMOVE, 18.0, 0.0, PkCompound.E2),
            )
            "gel_1mg_8h" -> listOf(doseEvent(4, PkRoute.GEL, 0.0, 1.0, PkCompound.E2))
            "oral_2mg_8h" -> listOf(doseEvent(5, PkRoute.ORAL, 0.0, 2.0, PkCompound.E2))
            "sublingual_1mg_8h" -> listOf(
                doseEvent(6, PkRoute.SUBLINGUAL, 0.0, 1.0, PkCompound.E2)
            )
            else -> error("Unknown forward oracle $id")
        }
    }

    private fun engineMedicationEvents(): List<PkCalibrationMedicationEventSource> = listOf(
        medicationEvent(1, PkRoute.PATCH_APPLY, 0.0, 0.0, releaseRateMcgPerDay = 100.0),
        medicationEvent(2, PkRoute.PATCH_REMOVE, 18.0, 0.0),
        medicationEvent(3, PkRoute.ORAL, 100.0, 2.0),
        medicationEvent(4, PkRoute.SUBLINGUAL, 200.0, 1.0),
        medicationEvent(5, PkRoute.GEL, 300.0, 1.0),
        medicationEvent(6, PkRoute.INJECTION, 1_000.0, 2.0, PkCompound.EV),
    )

    private fun medicationEvent(
        id: Long,
        route: PkRoute,
        timeH: Double,
        doseMg: Double,
        compound: PkCompound = PkCompound.E2,
        releaseRateMcgPerDay: Double? = null,
    ): PkCalibrationMedicationEventSource {
        val event = doseEvent(
            id = id,
            route = route,
            timeH = timeH,
            doseMg = doseMg,
            compound = compound,
            releaseRateMcgPerDay = releaseRateMcgPerDay,
        )
        return requireNotNull(
            PkCalibrationMedicationEventSource.create(
                event = event,
                epochMillis = epochMillis(timeH),
                eventTypeId = EventTypeIdByRoute.getValue(route),
                hormoneId = HormoneId,
                routeId = RouteIdByRoute.getValue(route),
                compoundId = CompoundIdByCompound.getValue(compound),
            )
        )
    }

    private fun doseEvent(
        id: Long,
        route: PkRoute,
        timeH: Double,
        doseMg: Double,
        compound: PkCompound,
        releaseRateMcgPerDay: Double? = null,
    ): PkDoseEvent = PkDoseEvent(
        id = uuid(id),
        sourceGroupUuid = null,
        hormone = PkHormone.ESTRADIOL,
        route = route,
        timeH = timeH,
        doseMg = doseMg,
        compound = compound,
        releaseRateMcgPerDay = releaseRateMcgPerDay,
    )

    private fun lab(
        resultId: UUID,
        collectedAtEpochMillis: Long,
        observedPgMl: Double,
    ): PkCalibrationE2LabSource {
        val result = BloodTestResult(
            uuid = resultId,
            createdAt = Instant.EPOCH,
            displayOrder = 0,
            analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.E2),
            value = observedPgMl,
            unitSnapshot = SourceUnitSnapshot,
            canonicalValue = observedPgMl,
        )
        val panel = BloodTestPanel(
            uuid = uuid(resultId.leastSignificantBits + 10_000L),
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

    private fun epochMillis(timeH: Double): Long =
        OriginMillis + (timeH * HourMillis).toLong()

    private fun uuid(value: Long): UUID = UUID(0L, value)

    private fun Throwable.describe(): String =
        "${javaClass.simpleName}:${message.orEmpty().take(120)}"

    private data class EngineFixture(
        val input: PkCalibrationInputSnapshot,
        val identityPolicy: PkCalibrationIdentityPolicy,
        val config: PkCalibrationConfig,
        val scopeDecisionProvider: PkCalibrationScopeDecisionProvider,
    )

    private data class EngineLabObservation(
        val timeH: Double,
        val observedPgMl: Double,
    )

    private val engineLabObservations = listOf(
        EngineLabObservation(0.25, "401c4993e55fa6f4".binary64()),
        EngineLabObservation(2.0, "40444f3dc630de59".binary64()),
        EngineLabObservation(8.0, "405176f2e8d12eb9".binary64()),
        EngineLabObservation(100.25, "4042c71c935bfacd".binary64()),
        EngineLabObservation(101.5, "4061dc73ce49856a".binary64()),
        EngineLabObservation(108.0, "405223483e35aea2".binary64()),
        EngineLabObservation(200.25, "406c59c0d801f0f4".binary64()),
        EngineLabObservation(201.0, "407b78d0424d608e".binary64()),
        EngineLabObservation(208.0, "404a1369141413b2".binary64()),
        EngineLabObservation(304.0, "40334b2641a307f2".binary64()),
        EngineLabObservation(324.0, "402f870d143ee123".binary64()),
        EngineLabObservation(396.0, "4009dfcb6fc06b16".binary64()),
        EngineLabObservation(1_001.0, "3fe291cd2e2891c0".binary64()),
        EngineLabObservation(1_008.0, "403b99bc87fb8d92".binary64()),
        EngineLabObservation(1_096.0, "4062d861d148feff".binary64()),
    )

    private const val OriginMillis = 1_700_000_000_000L
    private const val HourMillis = 3_600_000L
    private const val BodyWeightKg = 70.0
    private const val AnalyteId = "hrttracker:analyte/e2/v1"
    private const val HormoneId = "hrttracker:hormone/estradiol/v1"
    private const val SourceUnitSnapshot = "pg_ml"
    private const val UnitId = "hrttracker:unit/pg-ml/v1"
    private const val ProviderId = "provider:parity/v1"
    private const val AssayId = "assay:parity/v1"
    private const val PolicyVersion = "scope-policy:parity/v1"
    private const val IssuerId = "issuer:parity/v1"
    private const val ProvenanceRef = "urn:hrttracker:parity:v1"
    private const val ForwardModelVersion = "pk-forward:parity/v1"
    private const val CalibrationModelVersion = "pk-calibration:parity/v9"
    private const val ChartGridVersion = "pk-chart-grid:parity/v1"

    private val EventTypeIdByRoute = linkedMapOf(
        PkRoute.INJECTION to "event:injection-dose/v1",
        PkRoute.PATCH_APPLY to "event:patch-apply/v1",
        PkRoute.PATCH_REMOVE to "event:patch-remove/v1",
        PkRoute.GEL to "event:gel-dose/v1",
        PkRoute.ORAL to "event:oral-dose/v1",
        PkRoute.SUBLINGUAL to "event:sublingual-dose/v1",
    )
    private val RouteIdByRoute = linkedMapOf(
        PkRoute.INJECTION to "injection",
        PkRoute.PATCH_APPLY to "patch",
        PkRoute.PATCH_REMOVE to "patch",
        PkRoute.GEL to "gel",
        PkRoute.ORAL to "oral",
        PkRoute.SUBLINGUAL to "sublingual",
    )
    private val CompoundIdByCompound = linkedMapOf(
        PkCompound.E2 to "compound:e2/v1",
        PkCompound.EB to "compound:eb/v1",
        PkCompound.EV to "compound:ev/v1",
        PkCompound.EC to "compound:ec/v1",
        PkCompound.EN to "compound:en/v1",
        PkCompound.EU to "compound:eu/v1",
    )
}

data class PkForwardOracleVector(
    val id: String,
    val route: PkCalibrationRoute,
    val sampleTimeH: Double,
    val highPrecisionDecimalPgMl: String,
    val expectedPgMl: PkBinary64OutwardInterval,
)

data class PkEngineRenderOracleVector(
    val scopeDecisionDigestHex: String,
    val renderDomainDigestHex: String,
    val fittedBetaByRoute: Map<PkCalibrationRoute, PkBinary64OutwardInterval>,
    val selectedEpochMillis: Long,
    val selectedCentralPgMl: PkBinary64OutwardInterval,
    /** p025, p158655254, p50, p841344746, p975 in renderer contract order. */
    val selectedBandPgMl: List<PkBinary64OutwardInterval>,
)

data class PkBinary64OutwardInterval(
    val lowerHex: String,
    val upperHex: String,
) {
    val lower: Double = lowerHex.binary64()
    val upper: Double = upperHex.binary64()

    init {
        require(lower.isFinite() && upper.isFinite() && lower <= upper)
    }

    fun contains(value: Double): Boolean = value.isFinite() && value in lower..upper
}

data class PkCalibrationParityReport(val failures: List<String>) {
    val isSuccess: Boolean get() = failures.isEmpty()
    val uiText: String
        get() = if (isSuccess) {
            PkCalibrationParityVectors.SUCCESS_TEXT
        } else {
            "PK_CALIBRATION_R8_PARITY_FAIL:v1:${failures.joinToString("|").take(1_500)}"
        }
}

private fun String.binary64(): Double =
    Double.fromBits(java.lang.Long.parseUnsignedLong(this, 16))

private fun Double.toHexBits(): String =
    java.lang.Long.toUnsignedString(toBits(), 16).padStart(16, '0')
