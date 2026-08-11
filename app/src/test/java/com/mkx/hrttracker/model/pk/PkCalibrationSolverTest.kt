package com.mkx.hrttracker.model.pk

import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResult
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

class PkCalibrationSolverTest {
    // ------------------------------------------------------------------
    // §A10.2 joint objective: derivatives pinned to finite differences
    // ------------------------------------------------------------------

    @Test
    fun jointGradientAndHessian_matchFiniteDifferencesIncludingCrossPartials() {
        val objective = jointObjective(
            lab(1, observed = 13.0, injection = 8.0, oral = 2.0),
            lab(2, observed = 8.0, injection = 3.0, oral = 7.0),
            lab(3, observed = 12.5, injection = 5.0, oral = 5.0),
        )
        val activeIndices = objective.activeRouteIndices
        assertEquals(
            listOf(
                PkCalibrationRoute.INJECTION.ordinal,
                PkCalibrationRoute.ORAL.ordinal,
            ),
            activeIndices,
        )
        val beta = DoubleArray(objective.routeCount)
        beta[PkCalibrationRoute.INJECTION.ordinal] = 0.15
        beta[PkCalibrationRoute.ORAL.ordinal] = -0.2

        val gradient = requireNotNull(objective.gradient(beta))
        for (position in activeIndices.indices) {
            val step = 1e-6
            val plus = beta.copyOf().also { it[activeIndices[position]] += step }
            val minus = beta.copyOf().also { it[activeIndices[position]] -= step }
            val finiteDifference = (
                    requireNotNull(objective.objective(plus)) -
                            requireNotNull(objective.objective(minus))
                    ) / (2.0 * step)
            assertEquals(finiteDifference, gradient[position], 1e-5)
        }

        val hessian = requireNotNull(objective.hessian(beta))
        for (row in activeIndices.indices) {
            for (column in activeIndices.indices) {
                val step = 1e-5
                val plus = beta.copyOf().also { it[activeIndices[column]] += step }
                val minus = beta.copyOf().also { it[activeIndices[column]] -= step }
                val finiteDifference = (
                        requireNotNull(objective.gradient(plus))[row] -
                                requireNotNull(objective.gradient(minus))[row]
                        ) / (2.0 * step)
                assertEquals(finiteDifference, hessian[row][column], 1e-4)
                assertEquals(
                    hessian[row][column].toBits(),
                    hessian[column][row].toBits(),
                )
            }
        }
        // The cross-partial is genuinely non-zero for overlapping evidence.
        assertTrue(abs(hessian[0][1]) > 1e-6)

        assertNull(objective.objective(DoubleArray(objective.routeCount) { Double.NaN }))
        assertNull(objective.gradient(DoubleArray(objective.routeCount) {
            Double.POSITIVE_INFINITY
        }))
        assertNull(objective.hessian(DoubleArray(objective.routeCount) {
            Double.NEGATIVE_INFINITY
        }))
    }

    @Test
    fun jointObjective_isBitDeterministicAcrossInputOrder() {
        val labs = listOf(
            lab(40, observed = 12.0, injection = 6.5, oral = 3.5),
            lab(3, observed = 9.0, injection = 2.0, oral = 8.0),
            lab(22, observed = 15.0, injection = 9.0, oral = 1.0),
            lab(7, observed = 10.5, injection = 5.0, oral = 5.0),
        )
        val forward = jointObjective(*labs.toTypedArray())
        val reversed = jointObjective(*labs.reversed().toTypedArray())
        val beta = DoubleArray(forward.routeCount)
        beta[PkCalibrationRoute.INJECTION.ordinal] = 0.123
        beta[PkCalibrationRoute.ORAL.ordinal] = -0.045

        assertEquals(
            requireNotNull(forward.objective(beta)).toBits(),
            requireNotNull(reversed.objective(beta)).toBits(),
        )
        assertEquals(
            requireNotNull(forward.gradient(beta)).map(Double::toBits),
            requireNotNull(reversed.gradient(beta)).map(Double::toBits),
        )

        val forwardFit = PkJointMapSolver.fit(forward) as PkJointFitOutcome.Fitted
        val reversedFit = PkJointMapSolver.fit(reversed) as PkJointFitOutcome.Fitted
        assertEquals(
            forwardFit.fit.beta.map(Double::toBits),
            reversedFit.fit.beta.map(Double::toBits),
        )
    }

    // ------------------------------------------------------------------
    // Recovery and identity properties
    // ------------------------------------------------------------------

    @Test
    fun totalLikelihoodResidual_isExactAtTheSection9RecoveryVector() {
        // w = 0.8, true injection scale 0.3, oral at population:
        // y = 0.3 * 8 + 2 = 4.4 with no subtraction anywhere.
        val objective = jointObjective(
            lab(1, observed = 0.3 * 8.0 + 2.0, injection = 8.0, oral = 2.0),
        )
        val beta = DoubleArray(objective.routeCount)
        beta[PkCalibrationRoute.INJECTION.ordinal] = ln(0.3)

        val residual = requireNotNull(objective.residual(objective.points.single(), beta))
        assertTrue(abs(residual) < 1e-12)
    }

    @Test
    fun singleRouteHistory_recoversShrunkScaleAndFullyCalibrates() {
        val result = solve(
            lab(1, observed = exp(0.30) * 10.0, injection = 10.0),
            lab(2, observed = exp(0.30) * 20.0, injection = 20.0),
            lab(3, observed = exp(0.30) * 40.0, injection = 40.0),
        )

        val injection = result.routeResults[PkCalibrationRoute.INJECTION.ordinal]
        assertEquals(PkRouteCalibrationDisplayState.LAB_CALIBRATED, injection.displayState)
        val beta = requireNotNull(injection.fittedBeta)
        assertTrue(beta > 0.0)
        assertTrue(beta < 0.30)
        assertEquals(beta.toBits(), injection.displayBeta.toBits())
        assertTrue(requireNotNull(injection.betaPosteriorSd) <= 0.20)
        assertEquals(3, injection.supportingLabCount)

        assertEquals(listOf(PkCalibrationRoute.INJECTION), result.promotedRoutes)
        val covariance = requireNotNull(result.promotedBetaCovariance)
        assertEquals(listOf(PkCalibrationRoute.INJECTION), covariance.routes)
        assertEquals(
            requireNotNull(injection.laplaceVarianceBeta).toBits(),
            requireNotNull(
                covariance.covariance(PkCalibrationRoute.INJECTION, PkCalibrationRoute.INJECTION)
            ).toBits(),
        )
        for (route in PkCalibrationRoute.entries.filterNot { route ->
            route == PkCalibrationRoute.INJECTION
        }) {
            val row = result.routeResults[route.ordinal]
            assertEquals(
                PkRouteCalibrationDisplayState.POPULATION_NO_SUPPORTING_LABS,
                row.displayState,
            )
            assertEquals(0, row.supportingLabCount)
            assertNull(row.fittedBeta)
            assertEquals(0L, row.displayBeta.toBits())
        }
    }

    @Test
    fun fiftyFiftyOverlap_capsAtSymmetricProvisional_sdBoundedByPriorOverSqrtTwo() {
        // Pure two-route overlap at truth 1: the data constrain only the sum
        // direction, so each marginal SD is bounded below by sigma_s/sqrt(2)
        // (v10.0 §A10.4) and full calibration is impossible by arithmetic.
        val labs = (0 until 10).map { index ->
            val total = if (index % 2 == 0) 10.0 else 25.0
            lab(
                100L + index,
                observed = total,
                injection = total / 2.0,
                oral = total / 2.0,
            )
        }
        val result = solve(*labs.toTypedArray())

        val bound = PkCalibrationDefaults.ROUTE_LOG_SCALE_PRIOR_SD / sqrt(2.0)
        for (route in listOf(PkCalibrationRoute.INJECTION, PkCalibrationRoute.ORAL)) {
            val row = result.routeResults[route.ordinal]
            assertEquals(
                PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL,
                row.displayState,
            )
            assertTrue(PkCalibrationReason.POSTERIOR_SD_TOO_WIDE in row.reasons)
            val posteriorSd = requireNotNull(row.betaPosteriorSd)
            assertTrue(posteriorSd >= bound - 1e-9)
            assertTrue(
                posteriorSd >
                        PkCalibrationDefaults
                            .ROUTE_LOG_SCALE_POSTERIOR_SD_MAX_FOR_FULL_CALIBRATION
            )
            assertEquals(10, row.supportingLabCount)
        }
        assertEquals(
            listOf(PkCalibrationRoute.INJECTION, PkCalibrationRoute.ORAL),
            result.promotedRoutes,
        )
        assertNotNull(result.promotedBetaCovariance)
    }

    @Test
    fun routeAbsentFromEveryLab_staysExactlyAtPopulation() {
        val result = solve(
            lab(1, observed = 11.0, injection = 10.0),
            lab(2, observed = 22.0, injection = 20.0),
        )

        val oral = result.routeResults[PkCalibrationRoute.ORAL.ordinal]
        assertEquals(
            PkRouteCalibrationDisplayState.POPULATION_NO_SUPPORTING_LABS,
            oral.displayState,
        )
        assertNull(oral.fittedBeta)
        assertEquals(0L, oral.displayBeta.toBits())
        assertEquals(listOf(PkCalibrationRoute.INJECTION), result.promotedRoutes)
    }

    // ------------------------------------------------------------------
    // §A10.4 promotion support floor
    // ------------------------------------------------------------------

    @Test
    fun promotionSupportFloor_isInclusiveAtExactShareAndExclusiveOneUlpBelow() {
        val atFloor = requireNotNull(
            PkForwardBreakdown.create(
                breakdownMap(injection = 8.0, oral = 2.0)
            )
        )
        assertEquals(10.0, atFloor.totalDrugPgml, 0.0)
        val oneUlpBelow = requireNotNull(
            PkForwardBreakdown.create(
                breakdownMap(injection = 8.0, oral = Math.nextDown(2.0))
            )
        )
        // 8.0 + nextDown(2.0) rounds back to exactly 10.0, so the oral share
        // is exactly one ulp below the 0.2 floor.
        assertEquals(10.0, oneUlpBelow.totalDrugPgml, 0.0)

        val included = solve(
            labFrom(1, observed = 10.0, breakdown = atFloor),
            labFrom(2, observed = 20.0, breakdown = scale(atFloor, 2.0)),
        )
        assertTrue(
            included.routeResults[PkCalibrationRoute.ORAL.ordinal].supportingLabCount == 2
        )

        val excluded = solve(
            labFrom(1, observed = 10.0, breakdown = oneUlpBelow),
            labFrom(2, observed = 20.0, breakdown = scale(oneUlpBelow, 2.0)),
        )
        val oralRow = excluded.routeResults[PkCalibrationRoute.ORAL.ordinal]
        assertEquals(0, oralRow.supportingLabCount)
        assertEquals(
            PkRouteCalibrationDisplayState.POPULATION_NO_SUPPORTING_LABS,
            oralRow.displayState,
        )
        // The floor is promotion-only: the labs still fit the injection route.
        assertEquals(
            2,
            excluded.routeResults[PkCalibrationRoute.INJECTION.ordinal].supportingLabCount,
        )
    }

    @Test
    fun belowFloorObservation_participatesWithoutNumericFailure() {
        // y below the oral population contribution: the residual is negative
        // at every beta, influence is bounded by the Student-t weight, and no
        // guard removes the lab (v10.0 §A7.1 carried into §A10.1).
        val result = solve(
            lab(1, observed = 1.0, injection = 8.0, oral = 2.0),
            lab(2, observed = 2.0, injection = 16.0, oral = 4.0),
        )

        assertEquals(PkCalibrationGlobalState.READY, result.globalState)
        val injection = result.routeResults[PkCalibrationRoute.INJECTION.ordinal]
        assertFalse(
            injection.displayState ==
                    PkRouteCalibrationDisplayState.POPULATION_NUMERIC_FAILURE
        )
        val beta = requireNotNull(injection.fittedBeta)
        assertTrue(beta < 0.0)
        assertTrue(beta.isFinite())
    }

    // ------------------------------------------------------------------
    // §A10.3 multi-start search: ambiguity and failure are global
    // ------------------------------------------------------------------

    @Test
    fun symmetricBimodalEvidence_flagsGlobalPosteriorModeAmbiguity() {
        val labs = symmetricClusterLabs(baseId = 10, injection = 10.0)
        assertGlobalAmbiguity(solve(*labs.toTypedArray()))
    }

    @Test
    fun coupledTwoRouteConflict_flagsGlobalPosteriorModeAmbiguity() {
        // Phase-3 finding #7 (Option A): the same symmetric-cluster conflict on
        // two separate routes puts every joint mode at a point where both
        // coordinates are displaced; the pairwise (b_i*, b_j*) starts must
        // surface at least two of the four separable modes.
        val labs = symmetricClusterLabs(baseId = 10, injection = 10.0) +
            symmetricClusterLabs(baseId = 30, oral = 10.0)
        assertGlobalAmbiguity(solve(*labs.toTypedArray()))
    }

    @Test
    fun conditionalStartEnumerationFailure_failsClosedAsGlobalNumericFailure() {
        // Phase-3 finding #6: a drug contribution large enough to overflow the
        // 1-D grid scan at its positive edge (halfWidth = 0.5625 for one lab)
        // must be a global numeric failure, never an unseeded search that
        // reports a confident fit.
        assertGlobalNumericFailure(solve(lab(1, observed = 10.0, injection = 1.5e308)))
    }

    @Test
    fun malformedEvidence_failsClosedGloballyAsNumericFailure() {
        val malformed = unsafePool(
            included = listOf(
                PkCalibrationLabEvidence(
                    resultId = uuid(1),
                    state = PkCalibrationLabEvidenceState.INCLUDED,
                    observedPgml = 10.0,
                    totalDrugPgml = 10.0,
                    breakdown = null,
                    effectiveDisposition = PkCalibrationEffectiveDisposition.AUTO,
                )
            ),
        )
        assertGlobalNumericFailure(requireNotNull(PkCalibrationSolver.solve(malformed)))
    }

    // ------------------------------------------------------------------
    // Decision 7: acceptance-aware RMSE gate + review-fit affordance
    // ------------------------------------------------------------------

    @Test
    fun acceptedConflictingLab_noLongerHoldsTheRouteAtPopulation() {
        // Issue-1 repro: one good lab + one kept conflicting lab previously
        // failed the RMSE gate forever ("review fit" with the Keep action
        // apparently ignored). Acceptance excludes the vouched lab from the
        // gate, so the route promotes; the consistency signal survives via
        // minStudentTWeight for the confidence tier.
        val result = solve(
            lab(1, observed = 42.0, oral = 40.0),
            lab(
                2,
                observed = 300.0,
                oral = 44.0,
                disposition = PkCalibrationEffectiveDisposition.ACCEPTED,
            ),
        )

        assertEquals(PkCalibrationGlobalState.READY, result.globalState)
        val oral = result.routeResults[PkCalibrationRoute.ORAL.ordinal]
        assertTrue(
            oral.displayState == PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL ||
                oral.displayState == PkRouteCalibrationDisplayState.LAB_CALIBRATED
        )
        assertTrue(oral.unreviewedOutlierLabIds.isEmpty())
        // The kept outlier stays visible to the consistency tier.
        assertTrue(
            requireNotNull(oral.minStudentTWeight) < PkCalibrationDefaults.OUTLIER_WEIGHT_MIN
        )
    }

    @Test
    fun poorFitWithNothingFlagged_flagsTheWorstUnacceptedLab() {
        // Labs ~1.6x apart fail the RMSE gate while neither crosses the |z|>4
        // outlier threshold — previously a "review fit" dead end with no
        // affordance. The worst-fitting unaccepted lab is now actionable.
        val result = solve(
            lab(1, observed = 40.0, oral = 50.0),
            lab(2, observed = 110.0, oral = 50.0),
        )

        assertEquals(PkCalibrationGlobalState.READY, result.globalState)
        val oral = result.routeResults[PkCalibrationRoute.ORAL.ordinal]
        assertEquals(
            PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE,
            oral.displayState,
        )
        assertEquals(setOf(uuid(2)), oral.unreviewedOutlierLabIds)
        assertTrue(PkCalibrationReason.RESIDUAL_FIT_POOR in oral.reasons)
        assertTrue(PkCalibrationReason.UNREVIEWED_OUTLIER in oral.reasons)
    }

    @Test
    fun everySupportingLabAccepted_isNotANumericFailure() {
        // The unvouched-evidence pool can be empty; that passes the gate
        // trivially instead of tripping the weightSum guard.
        val result = solve(
            lab(
                1,
                observed = 40.0,
                oral = 50.0,
                disposition = PkCalibrationEffectiveDisposition.ACCEPTED,
            ),
            lab(
                2,
                observed = 110.0,
                oral = 50.0,
                disposition = PkCalibrationEffectiveDisposition.ACCEPTED,
            ),
        )

        assertEquals(PkCalibrationGlobalState.READY, result.globalState)
        val oral = result.routeResults[PkCalibrationRoute.ORAL.ordinal]
        assertFalse(
            oral.displayState == PkRouteCalibrationDisplayState.POPULATION_NUMERIC_FAILURE
        )
        assertTrue(oral.unreviewedOutlierLabIds.isEmpty())
    }

    // ------------------------------------------------------------------
    // §A10.4 outlier review: one weight per lab, blocks supported routes
    // ------------------------------------------------------------------

    @Test
    fun unreviewedOutlier_blocksEveryRouteItSupports_acceptedDoesNot() {
        val cleanLabs = listOf(
            lab(1, observed = 10.0, injection = 5.0, oral = 5.0),
            lab(2, observed = 25.0, injection = 12.5, oral = 12.5),
            lab(3, observed = 10.0, injection = 5.0, oral = 5.0),
        )
        val outlier = { disposition: PkCalibrationEffectiveDisposition ->
            lab(
                9,
                observed = 10.0 * exp(2.0),
                injection = 5.0,
                oral = 5.0,
                disposition = disposition,
            )
        }

        val blocked = solve(
            *(cleanLabs + outlier(PkCalibrationEffectiveDisposition.AUTO)).toTypedArray()
        )
        for (route in listOf(PkCalibrationRoute.INJECTION, PkCalibrationRoute.ORAL)) {
            val row = blocked.routeResults[route.ordinal]
            assertEquals(
                PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE,
                row.displayState,
            )
            assertTrue(PkCalibrationReason.UNREVIEWED_OUTLIER in row.reasons)
            assertTrue(uuid(9) in row.unreviewedOutlierLabIds)
        }
        assertTrue(blocked.promotedRoutes.isEmpty())

        val accepted = solve(
            *(cleanLabs + outlier(PkCalibrationEffectiveDisposition.ACCEPTED)).toTypedArray()
        )
        for (route in listOf(PkCalibrationRoute.INJECTION, PkCalibrationRoute.ORAL)) {
            val row = accepted.routeResults[route.ordinal]
            assertTrue(row.unreviewedOutlierLabIds.isEmpty())
            assertFalse(PkCalibrationReason.UNREVIEWED_OUTLIER in row.reasons)
            assertEquals(
                PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL,
                row.displayState,
            )
        }
    }

    // ------------------------------------------------------------------
    // Gate classification at a fixed diagnostics point (§A10.4 precedence)
    // ------------------------------------------------------------------

    @Test
    fun exactCoreEndpointsAreOrdinary_butOneBitOutsideNeedsThirdSupportingLab() {
        for (scale in listOf(0.5, 2.0)) {
            val exact = requireNotNull(
                PkCalibrationSolver.classifyRoute(
                    route = PkCalibrationRoute.GEL,
                    diagnostics = diagnostics(fittedBeta = betaForExactScale(scale)),
                    rLog = RLog,
                )
            )
            assertEquals(PkRouteCalibrationDisplayState.LAB_CALIBRATED, exact.displayState)
            assertFalse(
                PkCalibrationReason.EXTREME_SCALE_REQUIRES_THREE_SUPPORTING_LABS in
                        exact.reasons
            )
        }

        for (beta in listOf(betaProducingScaleBelow(0.5), betaProducingScaleAbove(2.0))) {
            val outsideCore = requireNotNull(
                PkCalibrationSolver.classifyRoute(
                    route = PkCalibrationRoute.GEL,
                    diagnostics = diagnostics(fittedBeta = beta),
                    rLog = RLog,
                )
            )
            assertEquals(
                PkRouteCalibrationDisplayState.POPULATION_INSUFFICIENT_SUPPORTING_LABS,
                outsideCore.displayState,
            )
            assertTrue(
                PkCalibrationReason.EXTREME_SCALE_REQUIRES_THREE_SUPPORTING_LABS in
                        outsideCore.reasons
            )
            assertEquals(0L, outsideCore.displayBeta.toBits())
            assertEquals(beta.toBits(), requireNotNull(outsideCore.fittedBeta).toBits())
        }
    }

    @Test
    fun everyRouteCap_isInclusiveAndOutsideValuesFallBackWithoutClamping() {
        for (route in PkCalibrationRoute.entries) {
            val cap = PkCalibrationDefaults.DISPLAY_SCALE_CAP_BY_ROUTE.getValue(route)
            for (scale in listOf(cap.minInclusive, cap.maxInclusive)) {
                val atBoundary = requireNotNull(
                    PkCalibrationSolver.classifyRoute(
                        route = route,
                        diagnostics = diagnostics(
                            supportingLabCount = 3,
                            fittedBeta = betaForExactScale(scale),
                        ),
                        rLog = RLog,
                    )
                )
                assertEquals(
                    PkRouteCalibrationDisplayState.LAB_CALIBRATED,
                    atBoundary.displayState,
                )
                assertTrue(atBoundary.atDisplayCapBoundary)
                assertTrue(PkCalibrationReason.DISPLAY_SCALE_AT_BOUNDARY in atBoundary.reasons)
            }

            for (beta in listOf(
                betaProducingScaleBelow(cap.minInclusive),
                betaProducingScaleAbove(cap.maxInclusive),
            )) {
                val exceeded = requireNotNull(
                    PkCalibrationSolver.classifyRoute(
                        route = route,
                        diagnostics = diagnostics(
                            supportingLabCount = 3,
                            fittedBeta = beta,
                        ),
                        rLog = RLog,
                    )
                )
                assertEquals(
                    PkRouteCalibrationDisplayState.POPULATION_DISPLAY_CAP_EXCEEDED,
                    exceeded.displayState,
                )
                assertTrue(PkCalibrationReason.DISPLAY_SCALE_EXCEEDED in exceeded.reasons)
                assertFalse(exceeded.atDisplayCapBoundary)
                assertEquals(0L, exceeded.displayBeta.toBits())
                assertEquals(beta.toBits(), requireNotNull(exceeded.fittedBeta).toBits())
            }
        }
    }

    @Test
    fun fullCalibrationGates_areInclusiveAtExactContrastAndPosteriorSd() {
        val exact = requireNotNull(
            PkCalibrationSolver.classifyRoute(
                route = PkCalibrationRoute.INJECTION,
                diagnostics = diagnostics(
                    drugSignalLogRange = PkCalibrationDefaults.DRUG_SIGNAL_LOG_RANGE_MIN,
                    posteriorSd = PkCalibrationDefaults
                        .ROUTE_LOG_SCALE_POSTERIOR_SD_MAX_FOR_FULL_CALIBRATION,
                ),
                rLog = RLog,
            )
        )
        assertEquals(PkRouteCalibrationDisplayState.LAB_CALIBRATED, exact.displayState)

        val lowContrast = requireNotNull(
            PkCalibrationSolver.classifyRoute(
                route = PkCalibrationRoute.INJECTION,
                diagnostics = diagnostics(
                    drugSignalLogRange = Math.nextDown(
                        PkCalibrationDefaults.DRUG_SIGNAL_LOG_RANGE_MIN
                    ),
                ),
                rLog = RLog,
            )
        )
        assertEquals(
            PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL,
            lowContrast.displayState,
        )
        assertTrue(
            PkCalibrationReason.INSUFFICIENT_DRUG_SIGNAL_CONTRAST in lowContrast.reasons
        )

        val widePosterior = requireNotNull(
            PkCalibrationSolver.classifyRoute(
                route = PkCalibrationRoute.INJECTION,
                diagnostics = diagnostics(
                    posteriorSd = Math.nextUp(
                        PkCalibrationDefaults
                            .ROUTE_LOG_SCALE_POSTERIOR_SD_MAX_FOR_FULL_CALIBRATION
                    ),
                ),
                rLog = RLog,
            )
        )
        assertEquals(
            PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL,
            widePosterior.displayState,
        )
        assertTrue(PkCalibrationReason.POSTERIOR_SD_TOO_WIDE in widePosterior.reasons)
    }

    @Test
    fun routeReasonEvaluation_isCompleteWhileStateUsesNormativePrecedence() {
        val result = requireNotNull(
            PkCalibrationSolver.classifyRoute(
                route = PkCalibrationRoute.INJECTION,
                diagnostics = diagnostics(
                    fittedBeta = betaProducingScaleAbove(2.0),
                    drugSignalLogRange = 0.0,
                    posteriorSd = 0.21,
                    robustRmseLog = Math.nextUp(
                        PkCalibrationDefaults.robustRmseLogMaxForPromotion(RLog)
                    ),
                    unreviewedOutlierLabIds = setOf(uuid(77)),
                ),
                rLog = RLog,
            )
        )

        assertEquals(
            PkRouteCalibrationDisplayState.POPULATION_DISPLAY_CAP_EXCEEDED,
            result.displayState,
        )
        assertTrue(PkCalibrationReason.DISPLAY_SCALE_EXCEEDED in result.reasons)
        assertTrue(
            PkCalibrationReason.EXTREME_SCALE_REQUIRES_THREE_SUPPORTING_LABS in result.reasons
        )
        assertTrue(PkCalibrationReason.RESIDUAL_FIT_POOR in result.reasons)
        assertTrue(PkCalibrationReason.UNREVIEWED_OUTLIER in result.reasons)
        assertTrue(PkCalibrationReason.INSUFFICIENT_DRUG_SIGNAL_CONTRAST in result.reasons)
        assertTrue(PkCalibrationReason.POSTERIOR_SD_TOO_WIDE in result.reasons)
    }

    // ------------------------------------------------------------------
    // Top-level assembly
    // ------------------------------------------------------------------

    @Test
    fun emptyEvidencePool_returnsFivePopulationRowsWithoutFitting() {
        val result = requireNotNull(PkCalibrationSolver.solve(pool()))

        assertEquals(PkCalibrationGlobalState.READY, result.globalState)
        assertEquals(
            PkCalibrationRoute.entries,
            result.routeResults.map(PkRouteCalibrationResult::route),
        )
        assertTrue(result.promotedRoutes.isEmpty())
        assertNull(result.promotedBetaCovariance)
        for (row in result.routeResults) {
            assertEquals(
                PkRouteCalibrationDisplayState.POPULATION_NO_SUPPORTING_LABS,
                row.displayState,
            )
        }
    }

    @Test
    fun topLevelSolver_assemblesFiveRoutesWithCountsAndPromotedCovariance() {
        val result = solve(
            lab(1, observed = exp(0.30) * 10.0, injection = 10.0),
            lab(2, observed = exp(0.30) * 20.0, injection = 20.0),
            lab(3, observed = exp(0.30) * 40.0, injection = 40.0),
            lab(4, observed = 10.0, patch = 10.0),
        )

        assertEquals(PkCalibrationGlobalState.READY, result.globalState)
        assertEquals(
            PkCalibrationRoute.entries,
            result.routeResults.map(PkRouteCalibrationResult::route),
        )
        // Floor = 1 (decision 6): the single-lab patch route promotes as a
        // provisional adjustment (zero signal contrast) instead of hiding
        // behind an insufficient-labs count.
        assertEquals(
            listOf(PkCalibrationRoute.INJECTION, PkCalibrationRoute.PATCH),
            result.promotedRoutes,
        )
        assertEquals(
            PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL,
            result.routeResults[PkCalibrationRoute.PATCH.ordinal].displayState,
        )
        assertEquals(1, result.routeResults[PkCalibrationRoute.PATCH.ordinal].supportingLabCount)
        assertEquals(
            PkRouteCalibrationDisplayState.POPULATION_NO_SUPPORTING_LABS,
            result.routeResults[PkCalibrationRoute.GEL.ordinal].displayState,
        )
        assertTrue(PkCalibrationRoute.INJECTION in result.displayParams.routeLogScale.keys)
        val covariance = requireNotNull(result.promotedBetaCovariance)
        assertEquals(result.promotedRoutes, covariance.routes)
    }

    @Test
    fun solverConsumesOnlySnapshotBoundEligibleConfig() {
        val config = testConfig()
        val evidencePool = pool(config = config)

        assertTrue(evidencePool.canonicalInput.config === config)
        assertTrue(evidencePool.config === config)
        assertNull(canonicalInput(PkCalibrationConfig.productionDefault()))
        val solveMethods = PkCalibrationSolver::class.java.methods.filter { method ->
            method.name == "solve"
        }
        assertEquals(1, solveMethods.size)
        assertEquals(1, solveMethods.single().parameterCount)
        assertNotNull(PkCalibrationSolver.solve(evidencePool))
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * At q^2 = 3 * nu * R_LOG, three observations in each symmetric cluster
     * make beta=0 a local maximum of the route's restricted objective with two
     * finite side minima — the load-bearing bimodality construction shared by
     * every ambiguity test.
     */
    private fun symmetricClusterLabs(
        baseId: Long,
        injection: Double = 0.0,
        oral: Double = 0.0,
    ): List<PkCalibrationLabEvidence> {
        val q = sqrt(3.0 * PkCalibrationDefaults.STUDENT_T_NU * RLog)
        return (0 until 3).map { index ->
            lab(baseId + index, observed = 10.0 * exp(-q), injection = injection, oral = oral)
        } + (0 until 3).map { index ->
            lab(baseId + 10 + index, observed = 10.0 * exp(q), injection = injection, oral = oral)
        }
    }

    private fun assertGlobalAmbiguity(result: PkCalibrationResult) {
        assertGlobalPopulationFallback(
            result,
            PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE,
            PkCalibrationReason.POSTERIOR_MODE_AMBIGUOUS,
        )
    }

    private fun assertGlobalNumericFailure(result: PkCalibrationResult) {
        assertGlobalPopulationFallback(
            result,
            PkRouteCalibrationDisplayState.POPULATION_NUMERIC_FAILURE,
            PkCalibrationReason.NUMERIC_FAILURE,
        )
    }

    private fun assertGlobalPopulationFallback(
        result: PkCalibrationResult,
        expectedState: PkRouteCalibrationDisplayState,
        expectedReason: PkCalibrationReason,
    ) {
        assertEquals(PkCalibrationGlobalState.READY, result.globalState)
        assertTrue(result.promotedRoutes.isEmpty())
        assertNull(result.promotedBetaCovariance)
        for (row in result.routeResults) {
            assertEquals(expectedState, row.displayState)
            assertEquals(setOf(expectedReason), row.reasons)
            assertNull(row.fittedBeta)
        }
    }

    private fun breakdownMap(
        injection: Double = 0.0,
        patch: Double = 0.0,
        gel: Double = 0.0,
        oral: Double = 0.0,
        sublingual: Double = 0.0,
    ): Map<PkCalibrationRoute, Double> = linkedMapOf(
        PkCalibrationRoute.INJECTION to injection,
        PkCalibrationRoute.PATCH to patch,
        PkCalibrationRoute.GEL to gel,
        PkCalibrationRoute.ORAL to oral,
        PkCalibrationRoute.SUBLINGUAL to sublingual,
    )

    private fun scale(breakdown: PkForwardBreakdown, factor: Double): PkForwardBreakdown {
        return requireNotNull(
            PkForwardBreakdown.create(
                breakdown.byRouteDrugPgml.mapValues { (_, value) -> value * factor }
            )
        )
    }

    private fun lab(
        id: Long,
        observed: Double,
        injection: Double = 0.0,
        patch: Double = 0.0,
        gel: Double = 0.0,
        oral: Double = 0.0,
        sublingual: Double = 0.0,
        disposition: PkCalibrationEffectiveDisposition =
            PkCalibrationEffectiveDisposition.AUTO,
    ): PkCalibrationLabEvidence {
        val breakdown = requireNotNull(
            PkForwardBreakdown.create(
                breakdownMap(injection, patch, gel, oral, sublingual)
            )
        )
        return labFrom(id, observed, breakdown, disposition)
    }

    private fun labFrom(
        id: Long,
        observed: Double,
        breakdown: PkForwardBreakdown,
        disposition: PkCalibrationEffectiveDisposition =
            PkCalibrationEffectiveDisposition.AUTO,
    ): PkCalibrationLabEvidence = PkCalibrationLabEvidence(
        resultId = uuid(id),
        state = PkCalibrationLabEvidenceState.INCLUDED,
        observedPgml = observed,
        totalDrugPgml = breakdown.totalDrugPgml,
        breakdown = breakdown,
        effectiveDisposition = disposition,
    )

    private fun jointObjective(
        vararg labs: PkCalibrationLabEvidence,
    ): PkJointStudentTObjective {
        return requireNotNull(
            PkJointStudentTObjective.fromEvidence(labs.toList(), RLog)
        )
    }

    private fun solve(vararg labs: PkCalibrationLabEvidence): PkCalibrationResult {
        return requireNotNull(PkCalibrationSolver.solve(pool(included = labs.toList())))
    }

    private fun pool(
        included: List<PkCalibrationLabEvidence> = emptyList(),
        config: PkCalibrationConfig = testConfig(),
    ): PkCalibrationEvidencePool {
        return requireNotNull(
            PkCalibrationEvidencePool.create(
                canonicalInput = requireNotNull(canonicalInput(config)),
                included = included,
                unassigned = emptyList(),
                excluded = emptyList(),
            )
        )
    }

    private fun diagnostics(
        supportingLabCount: Int = 2,
        fittedBeta: Double = 0.0,
        posteriorSd: Double = 0.10,
        drugSignalLogRange: Double = PkCalibrationDefaults.DRUG_SIGNAL_LOG_RANGE_MIN,
        robustRmseLog: Double = 0.10,
        unreviewedOutlierLabIds: Set<UUID> = emptySet(),
    ): PkJointRouteDiagnostics {
        return PkJointRouteDiagnostics(
            supportingLabCount = supportingLabCount,
            fittedBeta = fittedBeta,
            laplaceVarianceBeta = posteriorSd * posteriorSd,
            betaPosteriorSd = posteriorSd,
            betaUncertaintyReduction = (
                    1.0 - posteriorSd / PkCalibrationDefaults.ROUTE_LOG_SCALE_PRIOR_SD
                    ).coerceIn(0.0, 1.0),
            drugSignalLogRange = drugSignalLogRange,
            robustRmseLog = robustRmseLog,
            minStudentTWeight = (PkCalibrationDefaults.STUDENT_T_NU + 1.0) /
                    PkCalibrationDefaults.STUDENT_T_NU,
            unreviewedOutlierLabIds = unreviewedOutlierLabIds,
        )
    }

    private fun betaForExactScale(scale: Double): Double {
        var beta = ln(scale)
        repeat(64) {
            val actual = exp(beta)
            if (actual == scale) return beta
            beta = if (actual > scale) Math.nextDown(beta) else Math.nextUp(beta)
        }
        error("No nearby binary64 beta exponentiates to exact scale $scale")
    }

    private fun betaProducingScaleBelow(scale: Double): Double {
        var beta = betaForExactScale(scale)
        repeat(64) {
            beta = Math.nextDown(beta)
            if (exp(beta) < scale) return beta
        }
        error("No nearby binary64 beta exponentiates below scale $scale")
    }

    private fun betaProducingScaleAbove(scale: Double): Double {
        var beta = betaForExactScale(scale)
        repeat(64) {
            beta = Math.nextUp(beta)
            if (exp(beta) > scale) return beta
        }
        error("No nearby binary64 beta exponentiates above scale $scale")
    }

    private fun canonicalInput(
        config: PkCalibrationConfig,
    ): PkCalibrationCanonicalInputSnapshot? {
        val resultId = uuid(9_000)
        val result = BloodTestResult(
            uuid = resultId,
            createdAt = Instant.EPOCH,
            displayOrder = 0,
            analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.E2),
            value = 100.0,
            unitSnapshot = "pg_ml",
            canonicalValue = 100.0,
        )
        val panel = BloodTestPanel(
            uuid = uuid(9_001),
            collectedAt = Instant.EPOCH,
            collectedAtTimeZoneId = "UTC",
            notes = null,
            timeSinceLastEstradiolDoseMillis = null,
            timeSinceLastTestosteroneDoseMillis = null,
            results = listOf(result),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val lab = requireNotNull(
            PkCalibrationE2LabSource.create(
                panel = panel,
                result = result,
                analyteId = "hrttracker:analyte/e2/v1",
                unitId = "hrttracker:unit/pg-ml/v1",
            )
        )
        val scopeInput = requireNotNull(
            PkCalibrationScopeInputSnapshot.create(
                labs = listOf(lab),
                medicationEvents = emptyList(),
                resolvedCurrentWeightKg = 70.0,
                forwardModelVersion = "pk-forward:test/v1",
            )
        )
        return PkCalibrationCanonicalInputSnapshot.create(
            authorizedLabs = listOf(lab),
            medicationEvents = emptyList(),
            forwardTimeOriginEpochMillis = 0L,
            resolvedCurrentWeightKg = 70.0,
            metadata = emptyList(),
            attestation = PkCalibrationAttestation(0L),
            scopeInputSnapshot = scopeInput,
            forwardModelVersion = "pk-forward:test/v1",
            calibrationModelVersion = "pk-calibration:test/v10",
            config = config,
            acceptanceRecordByResultId = emptyMap(),
        )
    }

    private fun testConfig(): PkCalibrationConfig {
        return requireNotNull(
            PkCalibrationConfig.researchOrTest(
                drugMinInformativePgml = 1e-12,
                rLog = RLog,
            )
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun unsafePool(
        included: List<PkCalibrationLabEvidence>,
    ): PkCalibrationEvidencePool {
        val constructor = PkCalibrationEvidencePool::class.java.declaredConstructors
            .single { candidate -> candidate.parameterCount == 4 }
        constructor.isAccessible = true
        return constructor.newInstance(
            requireNotNull(canonicalInput(testConfig())),
            included,
            emptyList<PkCalibrationLabEvidence>(),
            emptyList<PkCalibrationLabEvidence>(),
        ) as PkCalibrationEvidencePool
    }

    private fun uuid(value: Long): UUID = UUID(0, value)

    private companion object {
        const val RLog = 0.04
    }
}
