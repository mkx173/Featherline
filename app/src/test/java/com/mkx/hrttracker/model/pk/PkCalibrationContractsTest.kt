package com.mkx.hrttracker.model.pk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import kotlin.math.ln

class PkCalibrationContractsTest {
    @Test
    fun routeStableIds_areCanonicalAndUnknownFailsClosed() {
        assertEquals(
            listOf("injection", "patch", "gel", "oral", "sublingual"),
            PkCalibrationRoute.entries.map(PkCalibrationRoute::stableId),
        )
        assertEquals(
            PkCalibrationRoute.INJECTION,
            PkCalibrationRoute.fromStableId("injection"),
        )
        assertNull(PkCalibrationRoute.fromStableId("Injection"))
        assertNull(PkCalibrationRoute.fromStableId("future-route"))
        assertNull(PkPersonalParams.fromStableIds(mapOf("future-route" to 0.1)))
    }

    @Test
    fun canonicalDigest_factoryRejectsNonCanonicalSha256Values() {
        assertNotNull(digest())
        assertNull(CanonicalDigest.create("", "SHA-256", "a".repeat(64)))
        assertNull(CanonicalDigest.create("schema-v1", "sha-256", "a".repeat(64)))
        assertNull(CanonicalDigest.create("schema-v1", "SHA-256", "A".repeat(64)))
        assertNull(CanonicalDigest.create("schema-v1", "SHA-256", "a".repeat(63)))
    }

    @Test
    fun personalParams_factoryCanonicalizesAndRejectsInvalidValues() {
        val params = requireNotNull(
            PkPersonalParams.create(
                linkedMapOf(
                    PkCalibrationRoute.SUBLINGUAL to -0.0,
                    PkCalibrationRoute.ORAL to ln(1.5),
                    PkCalibrationRoute.INJECTION to ln(2.0),
                )
            )
        )

        assertEquals(
            listOf(PkCalibrationRoute.INJECTION, PkCalibrationRoute.ORAL),
            params.routeLogScale.keys.toList(),
        )
        assertEquals(0.0, params.logScaleFor(PkCalibrationRoute.GEL), 0.0)
        assertNull(PkPersonalParams.create(mapOf(PkCalibrationRoute.GEL to Double.NaN)))
        assertNull(PkPersonalParams.create(mapOf(PkCalibrationRoute.GEL to -1_000.0)))
        assertNull(PkPersonalParams.create(thetaKGlobal = -0.0))
        assertNull(PkPersonalParams.create(thetaKGlobal = 0.01))
    }

    @Test
    fun forwardBreakdown_factoryRequiresAllRoutesInCanonicalOrderAndValidValues() {
        val canonical = linkedMapOf<PkCalibrationRoute, Double>().apply {
            PkCalibrationRoute.entries.forEachIndexed { index, route ->
                put(route, index.toDouble())
            }
        }
        val breakdown = requireNotNull(PkForwardBreakdown.create(canonical))

        assertEquals(10.0, breakdown.totalDrugPgml, 0.0)
        assertEquals(PkCalibrationRoute.entries, breakdown.byRouteDrugPgml.keys.toList())
        assertEquals(0.0.toBits(), breakdown.byRouteDrugPgml.getValue(PkCalibrationRoute.INJECTION).toBits())

        assertNull(PkForwardBreakdown.create(canonical - PkCalibrationRoute.GEL))
        assertNull(PkForwardBreakdown.create(LinkedHashMap(canonical.toList().reversed().toMap())))
        assertNull(
            PkForwardBreakdown.create(
                LinkedHashMap(canonical).apply { put(PkCalibrationRoute.GEL, -1.0) }
            )
        )
        assertNull(
            PkForwardBreakdown.create(
                LinkedHashMap(canonical).apply {
                    put(PkCalibrationRoute.GEL, Double.POSITIVE_INFINITY)
                }
            )
        )
    }

    @Test
    fun config_keepsProductionOffAndRequiresExplicitResearchEvidenceValues() {
        val production = PkCalibrationConfig.productionDefault()

        assertFalse(production.personalizedOutputEnabled)
        assertFalse(production.isResearchOrTest)
        assertNull(production.drugMinInformativePgml)
        assertNull(production.rLog)
        assertNull(PkCalibrationConfig.researchOrTest(0.0, 0.04))
        assertNull(PkCalibrationConfig.researchOrTest(1.0, Double.NaN))

        val research = requireNotNull(PkCalibrationConfig.researchOrTest(5.0, 0.04))
        assertTrue(research.personalizedOutputEnabled)
        assertTrue(research.isResearchOrTest)
        assertEquals(5.0, research.drugMinInformativePgml ?: 0.0, 0.0)
        assertEquals(0.04, research.rLog ?: 0.0, 0.0)
    }

    @Test
    fun scaleCap_factoryRejectsNonPositiveNonFiniteAndReversedBounds() {
        assertNotNull(ScaleCap.create(0.5, 2.0))
        assertNull(ScaleCap.create(0.0, 2.0))
        assertNull(ScaleCap.create(2.0, 1.0))
        assertNull(ScaleCap.create(Double.NaN, 2.0))
    }

    @Test
    fun routeResult_factoryEnforcesPopulationBetaCountsAndActionableOutlierIds() {
        val outlierId = UUID.randomUUID()
        val result = requireNotNull(
            routeResult(
                route = PkCalibrationRoute.ORAL,
                displayState = PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE,
                reasons = setOf(PkCalibrationReason.UNREVIEWED_OUTLIER),
                fittedBeta = 0.0,
                betaPosteriorSd = 0.1,
                laplaceVarianceBeta = 0.01,
                robustRmseLog = 0.1,
                candidateCount = 3,
                includedCount = 2,
                outlierIds = setOf(outlierId),
            )
        )

        assertEquals(setOf(outlierId), result.unreviewedOutlierLabIds)
        assertNull(
            routeResult(
                route = PkCalibrationRoute.ORAL,
                displayBeta = 0.1,
            )
        )
        assertNull(
            routeResult(
                route = PkCalibrationRoute.ORAL,
                candidateCount = 1,
                includedCount = 2,
            )
        )
    }

    @Test
    fun routeResult_factoryAcceptsCanonicalPopulationCasesAndAppliesPrecedence() {
        fun populationResult(
            state: PkRouteCalibrationDisplayState,
            reasons: Set<PkCalibrationReason>,
            route: PkCalibrationRoute = PkCalibrationRoute.INJECTION,
            fittedBeta: Double? = null,
            betaPosteriorSd: Double? = fittedBeta?.let { 0.1 },
            laplaceVarianceBeta: Double? = fittedBeta?.let { 0.01 },
            drugSignalLogRange: Double? = fittedBeta?.let {
                PkCalibrationDefaults.DRUG_SIGNAL_LOG_RANGE_MIN
            },
            robustRmseLog: Double? = fittedBeta?.let { 0.1 },
            candidateCount: Int = 3,
            includedCount: Int = 3,
            atDisplayCapBoundary: Boolean = false,
            outlierIds: Set<UUID> = emptySet(),
        ): PkRouteCalibrationResult? {
            return PkRouteCalibrationResult.create(
                route = route,
                fittedBeta = fittedBeta,
                betaPosteriorSd = betaPosteriorSd,
                laplaceVarianceBeta = laplaceVarianceBeta,
                displayState = state,
                reasons = reasons,
                dominantCandidateLabCount = candidateCount,
                dominantLabCount = includedCount,
                drugSignalLogRange = drugSignalLogRange,
                robustRmseLog = robustRmseLog,
                atDisplayCapBoundary = atDisplayCapBoundary,
                unreviewedOutlierLabIds = outlierIds,
            )
        }

        assertNotNull(
            populationResult(
                PkRouteCalibrationDisplayState.POPULATION_NO_DOMINANT_LABS,
                setOf(PkCalibrationReason.NO_DOMINANT_LABS),
                candidateCount = 0,
                includedCount = 0,
            )
        )
        assertNotNull(
            populationResult(
                PkRouteCalibrationDisplayState.POPULATION_INSUFFICIENT_DOMINANT_LABS,
                setOf(PkCalibrationReason.INSUFFICIENT_DOMINANT_LABS),
                candidateCount = 1,
                includedCount = 1,
            )
        )
        assertNotNull(
            populationResult(
                PkRouteCalibrationDisplayState.POPULATION_INSUFFICIENT_DOMINANT_LABS,
                setOf(PkCalibrationReason.EXTREME_SCALE_REQUIRES_THREE_DOMINANT_LABS),
                route = PkCalibrationRoute.GEL,
                fittedBeta = ln(2.5),
                candidateCount = 2,
                includedCount = 2,
            )
        )
        assertNotNull(
            populationResult(
                PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE,
                setOf(PkCalibrationReason.POSTERIOR_MODE_AMBIGUOUS),
                candidateCount = 2,
                includedCount = 2,
            )
        )
        assertNotNull(
            populationResult(
                PkRouteCalibrationDisplayState.POPULATION_DISPLAY_CAP_EXCEEDED,
                setOf(PkCalibrationReason.DISPLAY_SCALE_EXCEEDED),
                fittedBeta = ln(2.1),
            )
        )
        assertNotNull(
            populationResult(
                PkRouteCalibrationDisplayState.POPULATION_NUMERIC_FAILURE,
                setOf(PkCalibrationReason.NUMERIC_FAILURE),
            )
        )
        assertNotNull(
            populationResult(
                PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE,
                setOf(PkCalibrationReason.RESIDUAL_FIT_POOR),
                fittedBeta = 0.0,
                robustRmseLog = Math.nextUp(
                    PkCalibrationDefaults.ROBUST_RMSE_LOG_MAX_FOR_PROMOTION
                ),
            )
        )
        val outlierId = UUID.randomUUID()
        assertNotNull(
            populationResult(
                PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE,
                setOf(PkCalibrationReason.UNREVIEWED_OUTLIER),
                fittedBeta = 0.0,
                outlierIds = setOf(outlierId),
            )
        )
        assertNotNull(
            populationResult(
                PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE,
                setOf(
                    PkCalibrationReason.RESIDUAL_FIT_POOR,
                    PkCalibrationReason.DISPLAY_SCALE_AT_BOUNDARY,
                ),
                fittedBeta = ln(2.0),
                robustRmseLog = Math.nextUp(
                    PkCalibrationDefaults.ROBUST_RMSE_LOG_MAX_FOR_PROMOTION
                ),
                atDisplayCapBoundary = true,
            )
        )

        assertNotNull(
            populationResult(
                PkRouteCalibrationDisplayState.POPULATION_DISPLAY_CAP_EXCEEDED,
                setOf(
                    PkCalibrationReason.DISPLAY_SCALE_EXCEEDED,
                    PkCalibrationReason.EXTREME_SCALE_REQUIRES_THREE_DOMINANT_LABS,
                    PkCalibrationReason.RESIDUAL_FIT_POOR,
                    PkCalibrationReason.UNREVIEWED_OUTLIER,
                    PkCalibrationReason.INSUFFICIENT_DRUG_SIGNAL_CONTRAST,
                    PkCalibrationReason.POSTERIOR_SD_TOO_WIDE,
                ),
                fittedBeta = ln(3.1),
                betaPosteriorSd =
                    PkCalibrationDefaults
                        .ROUTE_LOG_SCALE_POSTERIOR_SD_MAX_FOR_FULL_CALIBRATION + 0.01,
                drugSignalLogRange =
                    PkCalibrationDefaults.DRUG_SIGNAL_LOG_RANGE_MIN - 0.01,
                robustRmseLog =
                    PkCalibrationDefaults.ROBUST_RMSE_LOG_MAX_FOR_PROMOTION + 0.01,
                candidateCount = 2,
                includedCount = 2,
                outlierIds = setOf(outlierId),
            )
        )
    }

    @Test
    fun routeResult_factoryRejectsPopulationFieldReasonContradictions() {
        fun populationResult(
            state: PkRouteCalibrationDisplayState,
            reasons: Set<PkCalibrationReason>,
            fittedBeta: Double? = null,
            betaPosteriorSd: Double? = fittedBeta?.let { 0.1 },
            laplaceVarianceBeta: Double? = fittedBeta?.let { 0.01 },
            drugSignalLogRange: Double? = fittedBeta?.let {
                PkCalibrationDefaults.DRUG_SIGNAL_LOG_RANGE_MIN
            },
            robustRmseLog: Double? = fittedBeta?.let { 0.1 },
            candidateCount: Int = 3,
            includedCount: Int = 3,
            atDisplayCapBoundary: Boolean = false,
            outlierIds: Set<UUID> = emptySet(),
        ): PkRouteCalibrationResult? {
            return PkRouteCalibrationResult.create(
                route = PkCalibrationRoute.INJECTION,
                fittedBeta = fittedBeta,
                betaPosteriorSd = betaPosteriorSd,
                laplaceVarianceBeta = laplaceVarianceBeta,
                displayState = state,
                reasons = reasons,
                dominantCandidateLabCount = candidateCount,
                dominantLabCount = includedCount,
                drugSignalLogRange = drugSignalLogRange,
                robustRmseLog = robustRmseLog,
                atDisplayCapBoundary = atDisplayCapBoundary,
                unreviewedOutlierLabIds = outlierIds,
            )
        }

        assertNull(
            populationResult(
                PkRouteCalibrationDisplayState.POPULATION_NO_DOMINANT_LABS,
                setOf(PkCalibrationReason.NO_DOMINANT_LABS),
            )
        )
        assertNull(
            populationResult(
                PkRouteCalibrationDisplayState.POPULATION_INSUFFICIENT_DOMINANT_LABS,
                setOf(PkCalibrationReason.INSUFFICIENT_DOMINANT_LABS),
            )
        )
        assertNull(
            populationResult(
                PkRouteCalibrationDisplayState.POPULATION_NO_DOMINANT_LABS,
                setOf(PkCalibrationReason.NO_DOMINANT_LABS),
                candidateCount = 1,
                includedCount = 0,
            )
        )
        assertNull(
            populationResult(
                PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE,
                setOf(PkCalibrationReason.RESIDUAL_FIT_POOR),
                fittedBeta = 1_000.0,
                robustRmseLog =
                    PkCalibrationDefaults.ROBUST_RMSE_LOG_MAX_FOR_PROMOTION + 0.01,
            )
        )
        assertNull(
            populationResult(
                PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE,
                setOf(PkCalibrationReason.RESIDUAL_FIT_POOR),
                fittedBeta = -1_000.0,
                robustRmseLog =
                    PkCalibrationDefaults.ROBUST_RMSE_LOG_MAX_FOR_PROMOTION + 0.01,
            )
        )

        val outlierId = UUID.randomUUID()
        assertNull(
            populationResult(
                PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE,
                emptySet(),
                fittedBeta = 0.0,
                outlierIds = setOf(outlierId),
            )
        )
        assertNull(
            populationResult(
                PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE,
                emptySet(),
                fittedBeta = 0.0,
                robustRmseLog =
                    PkCalibrationDefaults.ROBUST_RMSE_LOG_MAX_FOR_PROMOTION + 0.01,
            )
        )
        assertNull(
            populationResult(
                PkRouteCalibrationDisplayState.POPULATION_DISPLAY_CAP_EXCEEDED,
                setOf(PkCalibrationReason.DISPLAY_SCALE_EXCEEDED),
                fittedBeta = ln(2.1),
                drugSignalLogRange =
                    PkCalibrationDefaults.DRUG_SIGNAL_LOG_RANGE_MIN - 0.01,
            )
        )
        assertNull(
            populationResult(
                PkRouteCalibrationDisplayState.POPULATION_DISPLAY_CAP_EXCEEDED,
                setOf(PkCalibrationReason.DISPLAY_SCALE_EXCEEDED),
                fittedBeta = ln(2.1),
                betaPosteriorSd =
                    PkCalibrationDefaults
                        .ROUTE_LOG_SCALE_POSTERIOR_SD_MAX_FOR_FULL_CALIBRATION + 0.01,
            )
        )
        assertNull(
            populationResult(
                PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE,
                setOf(PkCalibrationReason.UNREVIEWED_OUTLIER),
                fittedBeta = 0.0,
            )
        )
        assertNull(
            populationResult(
                PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE,
                setOf(PkCalibrationReason.RESIDUAL_FIT_POOR),
                fittedBeta = 0.0,
            )
        )
        assertNull(
            populationResult(
                PkRouteCalibrationDisplayState.POPULATION_DISPLAY_CAP_EXCEEDED,
                setOf(
                    PkCalibrationReason.DISPLAY_SCALE_EXCEEDED,
                    PkCalibrationReason.DISPLAY_SCALE_AT_BOUNDARY,
                ),
                fittedBeta = ln(2.1),
                atDisplayCapBoundary = true,
            )
        )
        assertNull(
            populationResult(
                PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE,
                setOf(PkCalibrationReason.RESIDUAL_FIT_POOR),
                fittedBeta = ln(2.0),
                robustRmseLog =
                    PkCalibrationDefaults.ROBUST_RMSE_LOG_MAX_FOR_PROMOTION + 0.01,
            )
        )
    }

    @Test
    fun calibrationResult_factoryEnforcesGlobalAndCanonicalPromotionInvariants() {
        val scopeDigest = scopeDigest()
        val populationRoutes = PkCalibrationRoute.entries.map { route ->
            requireNotNull(routeResult(route))
        }
        assertNull(
            PkCalibrationResult.create(
                globalState = PkCalibrationGlobalState.READY,
                routeResults = populationRoutes,
                forwardModelVersion = "forward-v1",
                calibrationModelVersion = "calibration-v9",
            )
        )
        assertNull(
            PkCalibrationResult.create(
                globalState = PkCalibrationGlobalState.READY,
                routeResults = populationRoutes,
                scopeDecisionDigest = digest(),
                forwardModelVersion = "forward-v1",
                calibrationModelVersion = "calibration-v9",
            )
        )
        assertNull(
            PkCalibrationResult.create(
                globalState = PkCalibrationGlobalState.READY,
                routeResults = populationRoutes,
                scopeDecisionDigest = digest("unknown-scope-schema/v1"),
                forwardModelVersion = "forward-v1",
                calibrationModelVersion = "calibration-v9",
            )
        )
        val readyPopulation = PkCalibrationResult.create(
            globalState = PkCalibrationGlobalState.READY,
            routeResults = populationRoutes,
            scopeDecisionDigest = scopeDigest,
            forwardModelVersion = "forward-v1",
            calibrationModelVersion = "calibration-v9",
        )
        assertNotNull(readyPopulation)

        assertNull(
            PkCalibrationResult.create(
                globalState = PkCalibrationGlobalState.SCOPE_NOT_CONFIRMED,
                routeResults = populationRoutes,
                forwardModelVersion = "forward-v1",
                calibrationModelVersion = "calibration-v9",
            )
        )
        assertNull(
            PkCalibrationResult.create(
                globalState = PkCalibrationGlobalState.READY,
                routeResults = populationRoutes.reversed(),
                scopeDecisionDigest = scopeDigest,
                forwardModelVersion = "forward-v1",
                calibrationModelVersion = "calibration-v9",
            )
        )

        val beta = ln(1.5)
        val mixedRoutes = populationRoutes.toMutableList().apply {
            this[0] = requireNotNull(
                promotedRouteResult(
                    route = PkCalibrationRoute.INJECTION,
                    displayState = PkRouteCalibrationDisplayState.LAB_CALIBRATED,
                    displayBeta = beta,
                )
            )
        }
        assertNotNull(
            PkCalibrationResult.create(
                globalState = PkCalibrationGlobalState.READY,
                routeResults = mixedRoutes,
                promotedRoutes = listOf(PkCalibrationRoute.INJECTION),
                displayParams = requireNotNull(
                    PkPersonalParams.create(mapOf(PkCalibrationRoute.INJECTION to beta))
                ),
                scopeDecisionDigest = scopeDigest,
                forwardModelVersion = "forward-v1",
                calibrationModelVersion = "calibration-v9",
            )
        )
        assertNull(
            PkCalibrationResult.create(
                globalState = PkCalibrationGlobalState.READY,
                routeResults = mixedRoutes,
                promotedRoutes = listOf(PkCalibrationRoute.INJECTION),
                displayParams = PkPersonalParams.population(),
                scopeDecisionDigest = scopeDigest,
                forwardModelVersion = "forward-v1",
                calibrationModelVersion = "calibration-v9",
            )
        )

        val zeroBetaPromotedRoutes = populationRoutes.toMutableList().apply {
            this[0] = requireNotNull(
                promotedRouteResult(
                    route = PkCalibrationRoute.INJECTION,
                    displayState = PkRouteCalibrationDisplayState.LAB_CALIBRATED,
                    displayBeta = 0.0,
                )
            )
        }
        assertNotNull(
            PkCalibrationResult.create(
                globalState = PkCalibrationGlobalState.READY,
                routeResults = zeroBetaPromotedRoutes,
                promotedRoutes = listOf(PkCalibrationRoute.INJECTION),
                displayParams = PkPersonalParams.population(),
                scopeDecisionDigest = scopeDigest,
                forwardModelVersion = "forward-v1",
                calibrationModelVersion = "calibration-v9",
            )
        )
    }

    @Test
    fun promotedRouteFactory_enforcesCertifiedFitCountsCapsAndFullCalibrationGates() {
        val beta = ln(1.5)
        assertNotNull(promotedRouteResult(PkCalibrationRoute.INJECTION, displayBeta = beta))
        assertNull(
            promotedRouteResult(
                route = PkCalibrationRoute.INJECTION,
                displayBeta = beta,
                fittedBeta = null,
            )
        )
        assertNull(
            promotedRouteResult(
                route = PkCalibrationRoute.INJECTION,
                displayBeta = beta,
                fittedBeta = ln(1.4),
            )
        )
        assertNull(
            promotedRouteResult(
                route = PkCalibrationRoute.INJECTION,
                displayBeta = beta,
                candidateCount = 1,
                includedCount = 1,
            )
        )
        assertNull(
            promotedRouteResult(
                route = PkCalibrationRoute.INJECTION,
                displayBeta = beta,
                betaPosteriorSd = null,
            )
        )
        assertNull(
            promotedRouteResult(
                route = PkCalibrationRoute.INJECTION,
                displayBeta = beta,
                laplaceVarianceBeta = null,
            )
        )
        assertNull(
            promotedRouteResult(
                route = PkCalibrationRoute.INJECTION,
                displayBeta = beta,
                laplaceVarianceBeta = 0.0,
            )
        )
        assertNull(
            promotedRouteResult(
                route = PkCalibrationRoute.INJECTION,
                displayBeta = beta,
                robustRmseLog = PkCalibrationDefaults.ROBUST_RMSE_LOG_MAX_FOR_PROMOTION + 0.01,
            )
        )
        assertNull(
            promotedRouteResult(
                route = PkCalibrationRoute.INJECTION,
                displayBeta = beta,
                robustRmseLog = null,
            )
        )
        assertNull(
            promotedRouteResult(
                route = PkCalibrationRoute.INJECTION,
                displayBeta = beta,
                outlierIds = setOf(UUID.randomUUID()),
            )
        )

        val extremeBeta = ln(2.5)
        assertNull(
            promotedRouteResult(
                route = PkCalibrationRoute.GEL,
                displayBeta = extremeBeta,
                candidateCount = 2,
                includedCount = 2,
            )
        )
        assertNotNull(
            promotedRouteResult(
                route = PkCalibrationRoute.GEL,
                displayBeta = extremeBeta,
                candidateCount = 3,
                includedCount = 3,
            )
        )
        assertNull(
            promotedRouteResult(
                route = PkCalibrationRoute.INJECTION,
                displayBeta = ln(2.1),
                candidateCount = 3,
                includedCount = 3,
            )
        )

        val boundaryBeta = ln(2.0)
        assertNull(
            promotedRouteResult(
                route = PkCalibrationRoute.INJECTION,
                displayBeta = boundaryBeta,
            )
        )
        assertNotNull(
            promotedRouteResult(
                route = PkCalibrationRoute.INJECTION,
                displayBeta = boundaryBeta,
                reasons = setOf(PkCalibrationReason.DISPLAY_SCALE_AT_BOUNDARY),
                atDisplayCapBoundary = true,
            )
        )

        assertNull(
            promotedRouteResult(
                route = PkCalibrationRoute.INJECTION,
                displayBeta = beta,
                drugSignalLogRange = null,
            )
        )
        assertNull(
            promotedRouteResult(
                route = PkCalibrationRoute.INJECTION,
                displayBeta = beta,
                drugSignalLogRange = PkCalibrationDefaults.DRUG_SIGNAL_LOG_RANGE_MIN - 0.01,
            )
        )
        assertNull(
            promotedRouteResult(
                route = PkCalibrationRoute.INJECTION,
                displayBeta = beta,
                betaPosteriorSd =
                    PkCalibrationDefaults
                        .ROUTE_LOG_SCALE_POSTERIOR_SD_MAX_FOR_FULL_CALIBRATION + 0.01,
            )
        )
        assertNotNull(
            promotedRouteResult(
                route = PkCalibrationRoute.INJECTION,
                displayState = PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL,
                displayBeta = beta,
                drugSignalLogRange = PkCalibrationDefaults.DRUG_SIGNAL_LOG_RANGE_MIN - 0.01,
                betaPosteriorSd =
                    PkCalibrationDefaults
                        .ROUTE_LOG_SCALE_POSTERIOR_SD_MAX_FOR_FULL_CALIBRATION + 0.01,
                reasons = setOf(
                    PkCalibrationReason.INSUFFICIENT_DRUG_SIGNAL_CONTRAST,
                    PkCalibrationReason.POSTERIOR_SD_TOO_WIDE,
                ),
            )
        )
        assertNotNull(
            promotedRouteResult(
                route = PkCalibrationRoute.INJECTION,
                displayState = PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL,
                displayBeta = beta,
                drugSignalLogRange = PkCalibrationDefaults.DRUG_SIGNAL_LOG_RANGE_MIN - 0.01,
                reasons = setOf(PkCalibrationReason.INSUFFICIENT_DRUG_SIGNAL_CONTRAST),
            )
        )
        assertNotNull(
            promotedRouteResult(
                route = PkCalibrationRoute.INJECTION,
                displayState = PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL,
                displayBeta = beta,
                drugSignalLogRange = null,
                betaPosteriorSd =
                    PkCalibrationDefaults
                        .ROUTE_LOG_SCALE_POSTERIOR_SD_MAX_FOR_FULL_CALIBRATION + 0.01,
                reasons = setOf(PkCalibrationReason.POSTERIOR_SD_TOO_WIDE),
            )
        )
        assertNull(
            promotedRouteResult(
                route = PkCalibrationRoute.INJECTION,
                displayState = PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL,
                displayBeta = beta,
            )
        )
        assertNull(
            promotedRouteResult(
                route = PkCalibrationRoute.INJECTION,
                displayBeta = beta,
                reasons = setOf(PkCalibrationReason.NUMERIC_FAILURE),
            )
        )
        assertNull(
            promotedRouteResult(
                route = PkCalibrationRoute.INJECTION,
                displayBeta = beta,
                reasons = setOf(PkCalibrationReason.NO_DOMINANT_LABS),
            )
        )
        assertNull(
            promotedRouteResult(
                route = PkCalibrationRoute.INJECTION,
                displayState = PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL,
                displayBeta = beta,
                betaPosteriorSd =
                    PkCalibrationDefaults
                        .ROUTE_LOG_SCALE_POSTERIOR_SD_MAX_FOR_FULL_CALIBRATION + 0.01,
                reasons = emptySet(),
            )
        )
    }

    @Test
    fun renderFactoryEnforcesCentralCurveRouteAndBandInvariants() {
        val digest = digest()
        val curve = listOf(
            requireNotNull(PkCurvePoint.create(1L, 10.0)),
            requireNotNull(PkCurvePoint.create(2L, 12.0)),
        )
        val knot = requireNotNull(PkPredictiveBandKnot.create(1L, 5.0, 7.0, 10.0, 13.0, 16.0))

        assertNotNull(
            PkCalibrationRenderResult.create(
                domainDigest = digest,
                renderState = PkCalibrationRenderState.POPULATION,
                centralCurve = curve,
                bandState = PkCalibrationBandState.NOT_APPLICABLE_POPULATION,
            )
        )
        assertNull(
            PkCalibrationRenderResult.create(
                domainDigest = digest,
                renderState = PkCalibrationRenderState.POPULATION,
                centralCurve = emptyList(),
                bandState = PkCalibrationBandState.NOT_APPLICABLE_POPULATION,
            )
        )
        assertNull(
            PkCalibrationRenderResult.create(
                domainDigest = digest,
                renderState = PkCalibrationRenderState.NUMERIC_UNAVAILABLE,
                centralCurve = curve,
                bandState = PkCalibrationBandState.NUMERIC_UNAVAILABLE,
            )
        )
        assertNotNull(
            PkCalibrationRenderResult.create(
                domainDigest = digest,
                renderState = PkCalibrationRenderState.NUMERIC_UNAVAILABLE,
                renderReasons = setOf(PkCalibrationReason.NUMERIC_FAILURE),
                centralCurve = emptyList(),
                bandState = PkCalibrationBandState.NOT_APPLICABLE_POPULATION,
            )
        )
        assertNull(
            PkCalibrationRenderResult.create(
                domainDigest = digest,
                renderState = PkCalibrationRenderState.NUMERIC_UNAVAILABLE,
                renderReasons = setOf(PkCalibrationReason.NUMERIC_FAILURE),
                centralCurve = emptyList(),
                bandState = PkCalibrationBandState.NUMERIC_UNAVAILABLE,
                bandReasons = setOf(PkCalibrationReason.BAND_NUMERIC_FAILURE),
            )
        )
        assertNull(
            PkCalibrationRenderResult.create(
                domainDigest = digest,
                renderState = PkCalibrationRenderState.NUMERIC_UNAVAILABLE,
                renderReasons = setOf(PkCalibrationReason.NUMERIC_FAILURE),
                centralCurve = emptyList(),
                bandState = PkCalibrationBandState.NOT_APPLICABLE_POPULATION,
                bandReasons = setOf(PkCalibrationReason.BAND_NUMERIC_FAILURE),
            )
        )

        val params = requireNotNull(
            PkPersonalParams.create(mapOf(PkCalibrationRoute.ORAL to ln(1.2)))
        )
        assertNull(
            PkCalibrationRenderResult.create(
                domainDigest = digest,
                renderState = PkCalibrationRenderState.NUMERIC_UNAVAILABLE,
                renderReasons = setOf(PkCalibrationReason.NUMERIC_FAILURE),
                effectivePromotedRoutes = listOf(PkCalibrationRoute.ORAL),
                effectiveDisplayParams = params,
                centralCurve = emptyList(),
                bandState = PkCalibrationBandState.NOT_APPLICABLE_POPULATION,
            )
        )
        assertNull(
            PkCalibrationRenderResult.create(
                domainDigest = digest,
                renderState = PkCalibrationRenderState.NUMERIC_UNAVAILABLE,
                renderReasons = setOf(PkCalibrationReason.NUMERIC_FAILURE),
                centralCurve = emptyList(),
                bandState = PkCalibrationBandState.NOT_APPLICABLE_POPULATION,
                bandKnots = listOf(knot),
            )
        )
        assertNotNull(
            PkCalibrationRenderResult.create(
                domainDigest = digest,
                renderState = PkCalibrationRenderState.PERSONALIZED,
                effectivePromotedRoutes = listOf(PkCalibrationRoute.ORAL),
                effectiveDisplayParams = params,
                centralCurve = curve,
                bandState = PkCalibrationBandState.READY,
                bandKnots = listOf(knot),
            )
        )
        assertNull(
            PkCalibrationRenderResult.create(
                domainDigest = digest,
                renderState = PkCalibrationRenderState.PERSONALIZED,
                effectivePromotedRoutes = listOf(PkCalibrationRoute.ORAL),
                effectiveDisplayParams = params,
                centralCurve = curve,
                bandState = PkCalibrationBandState.READY,
                bandKnots = emptyList(),
            )
        )
        assertNull(
            PkCalibrationRenderResult.create(
                domainDigest = digest,
                renderState = PkCalibrationRenderState.PERSONALIZED,
                effectivePromotedRoutes = listOf(PkCalibrationRoute.ORAL),
                effectiveDisplayParams = params,
                routeRenderFallbacks = listOf(PkCalibrationRoute.ORAL),
                centralCurve = curve,
                bandState = PkCalibrationBandState.NUMERIC_UNAVAILABLE,
            )
        )
        assertNotNull(
            PkCalibrationRenderResult.create(
                domainDigest = digest,
                renderState = PkCalibrationRenderState.PERSONALIZED,
                effectivePromotedRoutes = listOf(PkCalibrationRoute.ORAL),
                effectiveDisplayParams = params,
                centralCurve = curve,
                bandState = PkCalibrationBandState.NUMERIC_UNAVAILABLE,
                bandReasons = setOf(PkCalibrationReason.BAND_NUMERIC_FAILURE),
            )
        )
    }

    @Test
    fun curveAndBandFactoriesRejectInvalidNumericOrOrderingInput() {
        assertNull(PkCurvePoint.create(1L, -1.0))
        assertNull(PkCurvePoint.create(1L, Double.NaN))
        assertNull(PkPredictiveBandKnot.create(1L, 1.0, 2.0, 4.0, 3.0, 5.0))

        val repeatedTimeCurve = listOf(
            requireNotNull(PkCurvePoint.create(1L, 10.0)),
            requireNotNull(PkCurvePoint.create(1L, 11.0)),
        )
        assertNull(
            PkCalibrationRenderResult.create(
                domainDigest = digest(),
                renderState = PkCalibrationRenderState.POPULATION,
                centralCurve = repeatedTimeCurve,
                bandState = PkCalibrationBandState.NOT_APPLICABLE_POPULATION,
            )
        )
    }

    private fun routeResult(
        route: PkCalibrationRoute,
        displayState: PkRouteCalibrationDisplayState =
            PkRouteCalibrationDisplayState.POPULATION_NO_DOMINANT_LABS,
        displayBeta: Double = 0.0,
        reasons: Set<PkCalibrationReason> =
            setOf(PkCalibrationReason.NO_DOMINANT_LABS),
        fittedBeta: Double? = null,
        betaPosteriorSd: Double? = null,
        laplaceVarianceBeta: Double? = null,
        robustRmseLog: Double? = null,
        candidateCount: Int = 0,
        includedCount: Int = 0,
        outlierIds: Set<UUID> = emptySet(),
    ): PkRouteCalibrationResult? {
        return PkRouteCalibrationResult.create(
            route = route,
            fittedBeta = fittedBeta,
            displayState = displayState,
            displayBeta = displayBeta,
            betaPosteriorSd = betaPosteriorSd,
            laplaceVarianceBeta = laplaceVarianceBeta,
            reasons = reasons,
            dominantCandidateLabCount = candidateCount,
            dominantLabCount = includedCount,
            robustRmseLog = robustRmseLog,
            unreviewedOutlierLabIds = outlierIds,
        )
    }

    private fun promotedRouteResult(
        route: PkCalibrationRoute,
        displayState: PkRouteCalibrationDisplayState =
            PkRouteCalibrationDisplayState.LAB_CALIBRATED,
        displayBeta: Double = 0.0,
        fittedBeta: Double? = displayBeta,
        betaPosteriorSd: Double? = 0.10,
        laplaceVarianceBeta: Double? = 0.01,
        candidateCount: Int = 2,
        includedCount: Int = 2,
        drugSignalLogRange: Double? = PkCalibrationDefaults.DRUG_SIGNAL_LOG_RANGE_MIN,
        robustRmseLog: Double? = 0.10,
        reasons: Set<PkCalibrationReason> = emptySet(),
        atDisplayCapBoundary: Boolean = false,
        outlierIds: Set<UUID> = emptySet(),
    ): PkRouteCalibrationResult? {
        return PkRouteCalibrationResult.create(
            route = route,
            fittedBeta = fittedBeta,
            displayBeta = displayBeta,
            betaPosteriorSd = betaPosteriorSd,
            laplaceVarianceBeta = laplaceVarianceBeta,
            displayState = displayState,
            reasons = reasons,
            dominantCandidateLabCount = candidateCount,
            dominantLabCount = includedCount,
            drugSignalLogRange = drugSignalLogRange,
            robustRmseLog = robustRmseLog,
            atDisplayCapBoundary = atDisplayCapBoundary,
            unreviewedOutlierLabIds = outlierIds,
        )
    }

    private fun scopeDigest(): CanonicalDigest {
        return digest(PK_CALIBRATION_SCOPE_DECISION_DIGEST_SCHEMA)
    }

    private fun digest(schema: String = "pk-render-domain-v1"): CanonicalDigest {
        return requireNotNull(
            CanonicalDigest.create(
                schema = schema,
                algorithm = "SHA-256",
                hexLower = "a".repeat(64),
            )
        )
    }
}
