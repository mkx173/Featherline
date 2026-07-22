package com.mkx.hrttracker.model.pk

import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResult
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

class PkCalibrationSolverTest {
    @Test
    fun studentTObjective_exactDerivativesMatchFiniteDifferencesIncludingTailCurvature() {
        val objective = objective(-4.0, -0.2, 1.7)
        for (beta in listOf(-2.0, 0.1, 3.0)) {
            val objectiveStep = 1e-6
            val finiteDifferenceScore = (
                    requireNotNull(objective.objective(beta + objectiveStep)) -
                            requireNotNull(objective.objective(beta - objectiveStep))
                    ) / (2.0 * objectiveStep)
            assertEquals(
                finiteDifferenceScore,
                requireNotNull(objective.score(beta)),
                2e-7,
            )

            val scoreStep = 1e-5
            val finiteDifferenceHessian = (
                    requireNotNull(objective.score(beta + scoreStep)) -
                            requireNotNull(objective.score(beta - scoreStep))
                    ) / (2.0 * scoreStep)
            assertEquals(
                finiteDifferenceHessian,
                requireNotNull(objective.hessian(beta)),
                2e-7,
            )
        }

        assertTrue(requireNotNull(objective.hessian(-4.0)).isFinite())
        assertNull(objective.objective(Double.NaN))
        assertNull(objective.score(Double.POSITIVE_INFINITY))
        assertNull(objective.hessian(Double.NEGATIVE_INFINITY))
    }

    @Test
    fun diagnostics_useExactPosteriorHessianNotRobustFitCurvature() {
        val objective = objective(-1.6, 0.0, 0.1, 1.6)
        val diagnostics = requireNotNull(objective.diagnostics(0.0))

        assertEquals(
            1.0 / diagnostics.posteriorHessian,
            diagnostics.laplaceVarianceBeta,
            0.0,
        )
        assertEquals(
            sqrt(diagnostics.laplaceVarianceBeta),
            diagnostics.betaPosteriorSd,
            0.0,
        )
        assertNotEquals(diagnostics.posteriorHessian, diagnostics.robustFitHessian, 0.0)
        assertTrue(diagnostics.robustFitHessian > 0.0)
        assertTrue(diagnostics.betaUncertaintyReduction in 0.0..1.0)
        assertTrue(diagnostics.robustRmseLog >= 0.0)
        assertEquals(5.0 / 68.0, diagnostics.studentTWeightByResultId.getValue(uuid(1)), 1e-15)
    }

    @Test
    fun outwardIntervalPrimitives_encloseDecimalOraclesAndFailClosed() {
        val left = requireNotNull(OutwardInterval.create(0.1, 0.2))
        val right = requireNotNull(OutwardInterval.create(0.3, 0.4))
        val sum = requireNotNull(left.add(right))
        val exactLower = BigDecimal("0.1").add(BigDecimal("0.3")).toDouble()
        val exactUpper = BigDecimal("0.2").add(BigDecimal("0.4")).toDouble()
        assertTrue(sum.lower <= exactLower)
        assertTrue(sum.upper >= exactUpper)

        val product = requireNotNull(
            requireNotNull(OutwardInterval.create(-3.0, 4.0)).multiply(
                requireNotNull(OutwardInterval.create(2.0, 5.0))
            )
        )
        assertTrue(product.lower <= -15.0)
        assertTrue(product.upper >= 20.0)

        val quotient = requireNotNull(
            requireNotNull(OutwardInterval.create(-4.0, 6.0)).divideByStrictlyPositive(
                requireNotNull(OutwardInterval.create(2.0, 3.0))
            )
        )
        assertTrue(quotient.lower <= -2.0)
        assertTrue(quotient.upper >= 3.0)

        assertNull(
            left.divideByStrictlyPositive(requireNotNull(OutwardInterval.create(-1.0, 1.0)))
        )
        assertNull(
            OutwardInterval.singleton(Double.MAX_VALUE).multiply(OutwardInterval.singleton(2.0))
        )
        assertNull(OutwardInterval.create(2.0, 1.0))
    }

    @Test
    fun outwardWidthAndSeparationBounds_makeOneBitThresholdDecisionsConservatively() {
        val widthTolerance = PkCalibrationDefaults.STATIONARY_ROOT_ENCLOSURE_BETA_TOL
        val acceptedWidth = requireNotNull(
            OutwardInterval.create(0.0, Math.nextDown(widthTolerance))
        )
        val rejectedWidth = requireNotNull(OutwardInterval.create(0.0, widthTolerance))
        assertTrue(requireNotNull(acceptedWidth.widthUpperBound()) <= widthTolerance)
        assertTrue(requireNotNull(rejectedWidth.widthUpperBound()) > widthTolerance)

        val separation = PkCalibrationDefaults.STATIONARY_ROOT_MIN_SEPARATION
        val previous = OutwardInterval.singleton(0.0)
        val rejectedSeparation = OutwardInterval.singleton(Math.nextUp(separation))
        val acceptedSeparation = OutwardInterval.singleton(
            Math.nextUp(Math.nextUp(separation))
        )
        assertTrue(
            requireNotNull(rejectedSeparation.separationLowerBoundAfter(previous)) <= separation
        )
        assertTrue(
            requireNotNull(acceptedSeparation.separationLowerBoundAfter(previous)) > separation
        )
    }

    @Test
    fun allQZero_isCertifiedDirectlyAtPositiveCurvatureMinimum() {
        val objective = objective(0.0, 0.0, 0.0)
        val certificate = PkStationaryRootCertificate.certify(objective)
            as PkStationaryCertificateResult.Covered
        val root = certificate.roots.single()

        assertEquals(PkStationaryRootKind.MINIMUM, root.kind)
        assertEquals(0L, root.refinedBeta.toBits())
        assertEquals(0.0, requireNotNull(root.enclosure.widthUpperBound()), 0.0)
        assertTrue(requireNotNull(objective.hessianInterval(root.enclosure)).isStrictlyPositive)
    }

    @Test
    fun certifiedBracket_isExhaustivelyCoveredThenRefinedByBisection() {
        val objective = objective(-0.4, 0.15, 0.7, 0.9)
        val certificate = PkStationaryRootCertificate.certify(objective)
            as PkStationaryCertificateResult.Covered
        val minima = certificate.roots.filter { root ->
            root.kind == PkStationaryRootKind.MINIMUM
        }

        assertEquals(1, minima.size)
        val root = minima.single()
        assertTrue(
            requireNotNull(root.enclosure.widthUpperBound()) <=
                    PkCalibrationDefaults.STATIONARY_ROOT_ENCLOSURE_BETA_TOL
        )
        assertTrue(root.enclosure.contains(root.refinedBeta))
        assertTrue(requireNotNull(objective.scoreInterval(root.enclosure)).containsZero)
        assertTrue(requireNotNull(objective.hessianInterval(root.enclosure)).isStrictlyPositive)
        assertTrue(abs(requireNotNull(objective.score(root.refinedBeta))) < 1e-8)
    }

    @Test
    fun boundaryRootProcedure_ownsExactSplitRootWithoutDuplicates() {
        val certificate = PkStationaryRootCertificate.certify(BoundaryStationaryFunction)
            as PkStationaryCertificateResult.Covered

        assertEquals(1, certificate.roots.size)
        assertEquals(PkStationaryRootKind.MINIMUM, certificate.roots.single().kind)
        assertTrue(certificate.roots.single().enclosure.contains(0.0))
    }

    @Test
    fun flatNonfiniteAndBudgetExhaustion_failClosed() {
        assertTrue(
            PkStationaryRootCertificate.certify(
                FlatStationaryFunction,
                PkStationaryCertificateBudget(maxCells = 8, maxRefinementEvaluations = 8),
            ) is PkStationaryCertificateResult.NumericFailure
        )
        assertTrue(
            PkStationaryRootCertificate.certify(NonFiniteStationaryFunction) is
                    PkStationaryCertificateResult.NumericFailure
        )

        val noRoot = PkStationaryRootCertificate.certify(NoRootStationaryFunction)
            as PkStationaryCertificateResult.Covered
        assertTrue(noRoot.roots.isEmpty())
        assertEquals(PkMinimumSelection.NumericFailure, PkRouteMapSolver.selectMinimum(noRoot))
    }

    @Test
    fun uniqueMinimumSelection_rejectsCompetingModesDeterministically() {
        val first = certifiedRoot(PkStationaryRootKind.MINIMUM, -1.0)
        val maximum = certifiedRoot(PkStationaryRootKind.MAXIMUM, 0.0)
        val second = certifiedRoot(PkStationaryRootKind.MINIMUM, 1.0)
        val covered = PkStationaryCertificateResult.Covered(listOf(first, maximum, second))

        assertEquals(PkMinimumSelection.Ambiguous, PkRouteMapSolver.selectMinimum(covered))
    }

    @Test
    fun studentTWeightBound_acceptsFormulaMaximumAndRejectsNextDouble() {
        val maximum = (PkCalibrationDefaults.STUDENT_T_NU + 1.0) /
                PkCalibrationDefaults.STUDENT_T_NU

        assertNotNull(
            PkRouteCalibrationResult.create(
                route = PkCalibrationRoute.INJECTION,
                displayState = PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE,
                minStudentTWeight = maximum,
            )
        )
        assertNull(
            PkRouteCalibrationResult.create(
                route = PkCalibrationRoute.INJECTION,
                displayState = PkRouteCalibrationDisplayState.POPULATION_LOW_CONFIDENCE,
                minStudentTWeight = Math.nextUp(maximum),
            )
        )
    }

    @Test
    fun outlierGate_isStrictAtWeightQuarterAndAcceptedRowsDoNotBlock() {
        val justPastFourSigma = Math.nextUp(0.8)
        val objective = requireNotNull(
            PkRouteStudentTObjective.create(
                points = listOf(
                    point(id = 1, q = 0.8),
                    point(id = 2, q = justPastFourSigma),
                    point(
                        id = 3,
                        q = 1.6,
                        disposition = PkCalibrationEffectiveDisposition.ACCEPTED,
                    ),
                ),
                rLog = RLog,
            )
        )
        val diagnostics = requireNotNull(objective.diagnostics(0.0))

        assertEquals(
            PkCalibrationDefaults.OUTLIER_WEIGHT_MIN,
            diagnostics.studentTWeightByResultId.getValue(uuid(1)),
            0.0,
        )
        assertTrue(
            diagnostics.studentTWeightByResultId.getValue(uuid(2)) <
                    PkCalibrationDefaults.OUTLIER_WEIGHT_MIN
        )
        assertTrue(
            diagnostics.studentTWeightByResultId.getValue(uuid(3)) <
                    PkCalibrationDefaults.OUTLIER_WEIGHT_MIN
        )
        assertEquals(setOf(uuid(2)), diagnostics.unreviewedOutlierLabIds)
    }

    @Test
    fun canonicalUuidOrdering_isBitDeterministicAcrossInputOrder() {
        val points = listOf(
            point(id = 40, q = -0.65),
            point(id = 3, q = 0.15),
            point(id = 22, q = 0.72),
            point(id = 7, q = -0.08),
        )
        val forward = requireNotNull(PkRouteStudentTObjective.create(points, RLog))
        val reversed = requireNotNull(PkRouteStudentTObjective.create(points.reversed(), RLog))

        assertEquals(
            requireNotNull(forward.objective(0.123)).toBits(),
            requireNotNull(reversed.objective(0.123)).toBits(),
        )
        assertEquals(
            requireNotNull(forward.score(0.123)).toBits(),
            requireNotNull(reversed.score(0.123)).toBits(),
        )
        assertEquals(
            requireNotNull(forward.hessian(0.123)).toBits(),
            requireNotNull(reversed.hessian(0.123)).toBits(),
        )

        val forwardRoot = (
                PkStationaryRootCertificate.certify(forward)
                        as PkStationaryCertificateResult.Covered
                ).roots.single { root -> root.kind == PkStationaryRootKind.MINIMUM }
        val reversedRoot = (
                PkStationaryRootCertificate.certify(reversed)
                        as PkStationaryCertificateResult.Covered
                ).roots.single { root -> root.kind == PkStationaryRootKind.MINIMUM }
        assertEquals(forwardRoot.refinedBeta.toBits(), reversedRoot.refinedBeta.toBits())
        assertEquals(forwardRoot.enclosure.lower.toBits(), reversedRoot.enclosure.lower.toBits())
        assertEquals(forwardRoot.enclosure.upper.toBits(), reversedRoot.enclosure.upper.toBits())
    }

    @Test
    fun syntheticRouteFit_recoversSignalWithPriorShrinkage() {
        val evidence = routeEvidence(
            route = PkCalibrationRoute.INJECTION,
            qValues = listOf(0.30, 0.30, 0.30),
            totalDrugPgml = listOf(10.0, 20.0, 40.0),
        )

        val result = PkRouteCalibrationSolver.solve(evidence, RLog)

        assertEquals(PkRouteCalibrationDisplayState.LAB_CALIBRATED, result.displayState)
        val beta = requireNotNull(result.fittedBeta)
        assertTrue(beta > 0.0)
        assertTrue(beta < 0.30)
        assertEquals(beta.toBits(), result.displayBeta.toBits())
        assertTrue(requireNotNull(result.betaPosteriorSd) <= 0.20)
    }

    @Test
    fun exactCoreEndpointsAreOrdinary_butOneBitOutsideNeedsThirdLab() {
        val twoLabs = routeEvidence(
            route = PkCalibrationRoute.GEL,
            qValues = listOf(0.0, 0.0),
            totalDrugPgml = listOf(10.0, 20.0),
        )

        for (scale in listOf(0.5, 2.0)) {
            val exact = PkRouteCalibrationSolver.classify(
                twoLabs,
                diagnostics(fittedBeta = betaForExactScale(scale)),
            )
            assertEquals(PkRouteCalibrationDisplayState.LAB_CALIBRATED, exact.displayState)
            assertFalse(
                PkCalibrationReason.EXTREME_SCALE_REQUIRES_THREE_DOMINANT_LABS in
                        exact.reasons
            )
        }

        for (beta in listOf(betaProducingScaleBelow(0.5), betaProducingScaleAbove(2.0))) {
            val outsideCore = PkRouteCalibrationSolver.classify(
                twoLabs,
                diagnostics(fittedBeta = beta),
            )
            assertEquals(
                PkRouteCalibrationDisplayState.POPULATION_INSUFFICIENT_DOMINANT_LABS,
                outsideCore.displayState,
            )
            assertTrue(
                PkCalibrationReason.EXTREME_SCALE_REQUIRES_THREE_DOMINANT_LABS in
                        outsideCore.reasons
            )
            assertEquals(0L, outsideCore.displayBeta.toBits())
            assertEquals(beta.toBits(), requireNotNull(outsideCore.fittedBeta).toBits())
        }
    }

    @Test
    fun everyRouteCap_isInclusiveAndOutsideValuesFallBackWithoutClamping() {
        for (route in PkCalibrationRoute.entries) {
            val evidence = routeEvidence(
                route = route,
                qValues = listOf(0.0, 0.0, 0.0),
                totalDrugPgml = listOf(10.0, 20.0, 40.0),
            )
            val cap = PkCalibrationDefaults.DISPLAY_SCALE_CAP_BY_ROUTE.getValue(route)
            for (scale in listOf(cap.minInclusive, cap.maxInclusive)) {
                val atBoundary = PkRouteCalibrationSolver.classify(
                    evidence,
                    diagnostics(fittedBeta = betaForExactScale(scale)),
                )
                assertEquals(PkRouteCalibrationDisplayState.LAB_CALIBRATED, atBoundary.displayState)
                assertTrue(atBoundary.atDisplayCapBoundary)
                assertTrue(PkCalibrationReason.DISPLAY_SCALE_AT_BOUNDARY in atBoundary.reasons)
            }

            for (beta in listOf(
                betaProducingScaleBelow(cap.minInclusive),
                betaProducingScaleAbove(cap.maxInclusive),
            )) {
                val exceeded = PkRouteCalibrationSolver.classify(
                    evidence,
                    diagnostics(fittedBeta = beta),
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
        val evidence = routeEvidence(
            route = PkCalibrationRoute.INJECTION,
            qValues = listOf(0.0, 0.0),
            totalDrugPgml = listOf(10.0, 20.0),
        )
        val exact = PkRouteCalibrationSolver.classify(
            evidence,
            diagnostics(
                drugSignalLogRange = PkCalibrationDefaults.DRUG_SIGNAL_LOG_RANGE_MIN,
                posteriorSd =
                    PkCalibrationDefaults.ROUTE_LOG_SCALE_POSTERIOR_SD_MAX_FOR_FULL_CALIBRATION,
            ),
        )
        assertEquals(PkRouteCalibrationDisplayState.LAB_CALIBRATED, exact.displayState)

        val lowContrast = PkRouteCalibrationSolver.classify(
            evidence,
            diagnostics(
                drugSignalLogRange = Math.nextDown(
                    PkCalibrationDefaults.DRUG_SIGNAL_LOG_RANGE_MIN
                )
            ),
        )
        assertEquals(
            PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL,
            lowContrast.displayState,
        )
        assertTrue(
            PkCalibrationReason.INSUFFICIENT_DRUG_SIGNAL_CONTRAST in lowContrast.reasons
        )

        val widePosterior = PkRouteCalibrationSolver.classify(
            evidence,
            diagnostics(
                posteriorSd = Math.nextUp(
                    PkCalibrationDefaults
                        .ROUTE_LOG_SCALE_POSTERIOR_SD_MAX_FOR_FULL_CALIBRATION
                )
            ),
        )
        assertEquals(
            PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL,
            widePosterior.displayState,
        )
        assertTrue(PkCalibrationReason.POSTERIOR_SD_TOO_WIDE in widePosterior.reasons)
    }

    @Test
    fun routeReasonEvaluation_isCompleteWhileStateUsesNormativePrecedence() {
        val evidence = routeEvidence(
            route = PkCalibrationRoute.INJECTION,
            qValues = listOf(0.0, 0.0),
            totalDrugPgml = listOf(10.0, 20.0),
        )
        val outlierId = evidence.included.first().resultId
        val result = PkRouteCalibrationSolver.classify(
            evidence,
            diagnostics(
                fittedBeta = betaProducingScaleAbove(2.0),
                drugSignalLogRange = 0.0,
                posteriorSd = 0.21,
                robustRmseLog = Math.nextUp(
                    PkCalibrationDefaults.ROBUST_RMSE_LOG_MAX_FOR_PROMOTION
                ),
                unreviewedOutlierLabIds = setOf(outlierId),
            ),
        )

        assertEquals(
            PkRouteCalibrationDisplayState.POPULATION_DISPLAY_CAP_EXCEEDED,
            result.displayState,
        )
        assertTrue(PkCalibrationReason.DISPLAY_SCALE_EXCEEDED in result.reasons)
        assertTrue(
            PkCalibrationReason.EXTREME_SCALE_REQUIRES_THREE_DOMINANT_LABS in result.reasons
        )
        assertTrue(PkCalibrationReason.RESIDUAL_FIT_POOR in result.reasons)
        assertTrue(PkCalibrationReason.UNREVIEWED_OUTLIER in result.reasons)
        assertTrue(PkCalibrationReason.INSUFFICIENT_DRUG_SIGNAL_CONTRAST in result.reasons)
        assertTrue(PkCalibrationReason.POSTERIOR_SD_TOO_WIDE in result.reasons)
    }

    @Test
    fun routeSolver_reachesEveryDisplayState() {
        val calibratedEvidence = routeEvidence(
            route = PkCalibrationRoute.INJECTION,
            qValues = listOf(0.0, 0.0),
            totalDrugPgml = listOf(10.0, 20.0),
        )
        val malformedIncluded = calibratedEvidence.included.first().copy(
            route = PkCalibrationRoute.PATCH
        )
        val malformed = unsafeRouteEvidence(
            route = PkCalibrationRoute.INJECTION,
            dominantCandidates = calibratedEvidence.dominantCandidates,
            included = listOf(malformedIncluded, calibratedEvidence.included.last()),
        )
        val observedStates = setOf(
            PkRouteCalibrationSolver.solve(
                routeEvidence(PkCalibrationRoute.INJECTION, emptyList(), emptyList()),
                RLog,
            ).displayState,
            PkRouteCalibrationSolver.solve(
                routeEvidence(PkCalibrationRoute.INJECTION, listOf(0.0), listOf(10.0)),
                RLog,
            ).displayState,
            PkRouteCalibrationSolver.solve(malformed, RLog).displayState,
            PkRouteCalibrationSolver.classify(
                routeEvidence(
                    PkCalibrationRoute.INJECTION,
                    listOf(0.0, 0.0, 0.0),
                    listOf(10.0, 20.0, 40.0),
                ),
                diagnostics(fittedBeta = betaProducingScaleAbove(2.0)),
            ).displayState,
            PkRouteCalibrationSolver.classify(
                calibratedEvidence,
                diagnostics(
                    robustRmseLog = Math.nextUp(
                        PkCalibrationDefaults.ROBUST_RMSE_LOG_MAX_FOR_PROMOTION
                    )
                ),
            ).displayState,
            PkRouteCalibrationSolver.classify(
                calibratedEvidence,
                diagnostics(drugSignalLogRange = 0.0),
            ).displayState,
            PkRouteCalibrationSolver.classify(
                calibratedEvidence,
                diagnostics(),
            ).displayState,
        )

        assertEquals(PkRouteCalibrationDisplayState.entries.toSet(), observedStates)
    }

    @Test
    fun routeEvidenceFactory_rejectsTamperingDuplicatesAndIncludedBeyondCandidates() {
        val valid = routeEvidence(
            PkCalibrationRoute.INJECTION,
            listOf(0.0, 0.1),
            listOf(10.0, 20.0),
        )
        val first = valid.included.first()
        val tamperedSameUuid = first.copy(
            routeAttributableObservedPgml = requireNotNull(
                first.routeAttributableObservedPgml
            ) * 2.0
        )
        assertNull(
            PkCalibrationRouteEvidence.create(
                route = valid.route,
                dominantCandidates = valid.dominantCandidates,
                included = listOf(tamperedSameUuid, valid.included.last()),
            )
        )
        assertNull(
            PkCalibrationRouteEvidence.create(
                route = valid.route,
                dominantCandidates = listOf(first, first),
                included = listOf(first),
            )
        )
        assertNull(
            PkCalibrationRouteEvidence.create(
                route = valid.route,
                dominantCandidates = emptyList(),
                included = listOf(first),
            )
        )

        val mutableCandidates = valid.dominantCandidates.toMutableList()
        val mutableIncluded = valid.included.toMutableList()
        val immutable = requireNotNull(
            PkCalibrationRouteEvidence.create(
                route = valid.route,
                dominantCandidates = mutableCandidates,
                included = mutableIncluded,
            )
        )
        mutableCandidates.clear()
        mutableIncluded.clear()
        assertEquals(2, immutable.dominantCandidateLabCount)
        assertEquals(2, immutable.dominantLabCount)

        val adversarialFixtures = listOf(
            unsafeRouteEvidence(
                route = valid.route,
                dominantCandidates = valid.dominantCandidates,
                included = listOf(tamperedSameUuid, valid.included.last()),
            ),
            unsafeRouteEvidence(
                route = valid.route,
                dominantCandidates = listOf(first, first),
                included = listOf(first),
            ),
            unsafeRouteEvidence(
                route = valid.route,
                dominantCandidates = emptyList(),
                included = listOf(first),
            ),
        )
        for (malformed in adversarialFixtures) {
            val result = PkRouteCalibrationSolver.solve(malformed, RLog)
            assertEquals(
                PkRouteCalibrationDisplayState.POPULATION_NUMERIC_FAILURE,
                result.displayState,
            )
            assertEquals(0L, result.displayBeta.toBits())
            assertTrue(PkCalibrationReason.NUMERIC_FAILURE in result.reasons)
        }

        val routesWithMalformedInjection = PkCalibrationRoute.entries.map { route ->
            if (route == PkCalibrationRoute.INJECTION) {
                adversarialFixtures.first()
            } else {
                routeEvidence(route, emptyList(), emptyList())
            }
        }
        assertNull(
            PkCalibrationEvidencePartition.create(
                canonicalInput = requireNotNull(canonicalInput(testConfig())),
                routeEvidence = routesWithMalformedInjection,
                unassigned = emptyList(),
                excluded = emptyList(),
            )
        )
    }

    @Test
    fun partitionFactory_rejectsCandidateUuidReuseAcrossRoutes() {
        val injection = routeEvidence(
            PkCalibrationRoute.INJECTION,
            listOf(0.0),
            listOf(10.0),
        )
        val patchOriginal = routeEvidence(
            PkCalibrationRoute.PATCH,
            listOf(0.0),
            listOf(10.0),
        )
        val reusedId = patchOriginal.included.single().copy(
            resultId = injection.included.single().resultId
        )
        val patch = requireNotNull(
            PkCalibrationRouteEvidence.create(
                route = PkCalibrationRoute.PATCH,
                dominantCandidates = listOf(reusedId),
                included = listOf(reusedId),
            )
        )
        val routeEvidence = PkCalibrationRoute.entries.map { route ->
            when (route) {
                PkCalibrationRoute.INJECTION -> injection
                PkCalibrationRoute.PATCH -> patch
                else -> routeEvidence(route, emptyList(), emptyList())
            }
        }

        assertNull(
            PkCalibrationEvidencePartition.create(
                canonicalInput = requireNotNull(canonicalInput(testConfig())),
                routeEvidence = routeEvidence,
                unassigned = emptyList(),
                excluded = emptyList(),
            )
        )
    }

    @Test
    fun solverConsumesOnlySnapshotBoundEligibleConfig() {
        val config = testConfig()
        val routeEvidence = PkCalibrationRoute.entries.map { route ->
            routeEvidence(route, emptyList(), emptyList())
        }
        val partition = evidencePartition(routeEvidence, config)

        assertTrue(partition.canonicalInput.config === config)
        assertTrue(partition.config === config)
        assertNull(canonicalInput(PkCalibrationConfig.productionDefault()))
        val solveMethods = PkCalibrationSolver::class.java.methods.filter { method ->
            method.name == "solve"
        }
        assertEquals(1, solveMethods.size)
        assertEquals(1, solveMethods.single().parameterCount)
        assertNotNull(PkCalibrationSolver.solve(partition))
    }

    @Test
    fun topLevelSolver_assemblesFiveRoutesAndIsolatesRouteFailures() {
        val injection = routeEvidence(
            PkCalibrationRoute.INJECTION,
            listOf(0.30, 0.30, 0.30),
            listOf(10.0, 20.0, 40.0),
        )
        val patch = routeEvidence(
            PkCalibrationRoute.PATCH,
            listOf(0.0),
            listOf(10.0),
        )
        val gel = routeEvidence(PkCalibrationRoute.GEL, emptyList(), emptyList())
        val oral = routeEvidence(
            PkCalibrationRoute.ORAL,
            listOf(21.0, 21.0),
            listOf(10.0, 20.0),
        )
        val sublingual = routeEvidence(
            PkCalibrationRoute.SUBLINGUAL,
            listOf(0.0, 0.0),
            listOf(10.0, 20.0),
        )
        val config = requireNotNull(
            PkCalibrationConfig.researchOrTest(
                drugMinInformativePgml = 1e-12,
                rLog = RLog,
            )
        )
        val partition = evidencePartition(
            routeEvidence = listOf(injection, patch, gel, oral, sublingual),
            config = config,
        )

        assertTrue(partition.config === config)
        val result = requireNotNull(PkCalibrationSolver.solve(partition))

        assertEquals(PkCalibrationGlobalState.READY, result.globalState)
        assertEquals(PkCalibrationRoute.entries, result.routeResults.map { item -> item.route })
        assertEquals(
            listOf(PkCalibrationRoute.INJECTION, PkCalibrationRoute.SUBLINGUAL),
            result.promotedRoutes,
        )
        assertEquals(
            PkRouteCalibrationDisplayState.POPULATION_INSUFFICIENT_DOMINANT_LABS,
            result.routeResults[PkCalibrationRoute.PATCH.ordinal].displayState,
        )
        assertEquals(
            PkRouteCalibrationDisplayState.POPULATION_NO_DOMINANT_LABS,
            result.routeResults[PkCalibrationRoute.GEL.ordinal].displayState,
        )
        assertEquals(
            PkRouteCalibrationDisplayState.POPULATION_NUMERIC_FAILURE,
            result.routeResults[PkCalibrationRoute.ORAL.ordinal].displayState,
        )
        assertEquals(
            setOf(PkCalibrationRoute.INJECTION),
            result.displayParams.routeLogScale.keys,
        )
        assertEquals(
            0L,
            result.routeResults[PkCalibrationRoute.SUBLINGUAL.ordinal].displayBeta.toBits(),
        )
    }

    private fun objective(vararg qValues: Double): PkRouteStudentTObjective {
        return requireNotNull(
            PkRouteStudentTObjective.create(
                points = qValues.mapIndexed { index, q ->
                    PkStudentTPoint(
                        resultId = uuid(index + 1L),
                        qLogRatio = q,
                        logTotalDrugPgml = ln(10.0 + index),
                        effectiveDisposition = PkCalibrationEffectiveDisposition.AUTO,
                    )
                },
                rLog = RLog,
            )
        )
    }

    private fun point(
        id: Long,
        q: Double,
        disposition: PkCalibrationEffectiveDisposition = PkCalibrationEffectiveDisposition.AUTO,
    ): PkStudentTPoint {
        return PkStudentTPoint(
            resultId = uuid(id),
            qLogRatio = q,
            logTotalDrugPgml = ln(10.0 + id),
            effectiveDisposition = disposition,
        )
    }

    private fun routeEvidence(
        route: PkCalibrationRoute,
        qValues: List<Double>,
        totalDrugPgml: List<Double>,
        dispositions: List<PkCalibrationEffectiveDisposition> =
            List(qValues.size) { PkCalibrationEffectiveDisposition.AUTO },
    ): PkCalibrationRouteEvidence {
        require(qValues.size == totalDrugPgml.size)
        require(qValues.size == dispositions.size)
        val included = qValues.mapIndexed { index, q ->
            val dominant = totalDrugPgml[index]
            val attributable = exp(q) * dominant
            PkCalibrationLabEvidence(
                resultId = uuid(1_000L + route.ordinal * 100L + index),
                state = PkCalibrationLabEvidenceState.INCLUDED,
                route = route,
                observedPgml = attributable,
                totalDrugPgml = totalDrugPgml[index],
                dominantRouteDrugPgml = dominant,
                otherRoutePopulationPgml = 0.0,
                routeAttributableObservedPgml = attributable,
                effectiveDisposition = dispositions[index],
            )
        }
        return requireNotNull(
            PkCalibrationRouteEvidence.create(
                route = route,
                dominantCandidates = included,
                included = included,
            )
        )
    }

    private fun diagnostics(
        fittedBeta: Double = 0.0,
        posteriorSd: Double = 0.10,
        drugSignalLogRange: Double = PkCalibrationDefaults.DRUG_SIGNAL_LOG_RANGE_MIN,
        robustRmseLog: Double = 0.10,
        unreviewedOutlierLabIds: Set<UUID> = emptySet(),
    ): PkRouteFitDiagnostics {
        val variance = posteriorSd * posteriorSd
        val hessian = 1.0 / variance
        return PkRouteFitDiagnostics(
            fittedBeta = fittedBeta,
            posteriorHessian = hessian,
            robustFitHessian = hessian,
            laplaceVarianceBeta = variance,
            betaPosteriorSd = posteriorSd,
            betaUncertaintyReduction = (
                    1.0 - posteriorSd /
                            PkCalibrationDefaults.ROUTE_LOG_SCALE_PRIOR_SD
                    ).coerceIn(0.0, 1.0),
            drugSignalLogRange = drugSignalLogRange,
            robustRmseLog = robustRmseLog,
            minStudentTWeight =
                (PkCalibrationDefaults.STUDENT_T_NU + 1.0) /
                        PkCalibrationDefaults.STUDENT_T_NU,
            studentTWeightByResultId = emptyMap(),
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

    private fun evidencePartition(
        routeEvidence: List<PkCalibrationRouteEvidence>,
        config: PkCalibrationConfig = testConfig(),
    ): PkCalibrationEvidencePartition {
        return requireNotNull(
            PkCalibrationEvidencePartition.create(
                canonicalInput = requireNotNull(canonicalInput(config)),
                routeEvidence = routeEvidence,
                unassigned = emptyList(),
                excluded = emptyList(),
            )
        )
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
                providerId = "provider:test/v1",
                assayMethodId = "assay:test/v1",
            )
        )
        val authorization = requireNotNull(
            PkAuthorizedE2Result.create(resultId, lab.resultScopeDigest())
        )
        val scopeInput = requireNotNull(
            PkCalibrationScopeInputSnapshot.create(
                authorizedE2Results = listOf(authorization),
                medicationEvents = emptyList(),
                resolvedCurrentWeightKg = 70.0,
                forwardModelVersion = "pk-forward:test/v1",
            )
        )
        val decision = requireNotNull(
            PkCalibrationScopeDecision.create(
                decisionId = uuid(9_002),
                revision = 1,
                policyVersion = "scope-policy:test/v1",
                issuerId = "issuer:test/v1",
                issuedAt = Instant.EPOCH,
                provenanceRef = "provenance:test/v1",
                inputSnapshotDigest = scopeInput.digest,
                authorizedE2Results = listOf(authorization),
            )
        )
        return PkCalibrationCanonicalInputSnapshot.create(
            authorizedLabs = listOf(lab),
            medicationEvents = emptyList(),
            forwardTimeOriginEpochMillis = 0L,
            resolvedCurrentWeightKg = 70.0,
            metadata = emptyList(),
            scopeDecision = decision,
            scopeDecisionDigest = decision.digest(),
            scopeInputSnapshot = scopeInput,
            forwardModelVersion = "pk-forward:test/v1",
            calibrationModelVersion = "pk-calibration:test/v9",
            config = config,
            reviewDigestByResultId = emptyMap(),
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
    private fun unsafeRouteEvidence(
        route: PkCalibrationRoute,
        dominantCandidates: List<PkCalibrationLabEvidence>,
        included: List<PkCalibrationLabEvidence>,
    ): PkCalibrationRouteEvidence {
        val constructor = PkCalibrationRouteEvidence::class.java.declaredConstructors
            .single { candidate -> candidate.parameterCount == 3 }
        constructor.isAccessible = true
        return constructor.newInstance(route, dominantCandidates, included) as
                PkCalibrationRouteEvidence
    }

    private fun certifiedRoot(
        kind: PkStationaryRootKind,
        beta: Double,
    ): PkCertifiedStationaryRoot {
        return PkCertifiedStationaryRoot(
            kind = kind,
            enclosure = OutwardInterval.singleton(beta),
            refinedBeta = beta,
        )
    }

    private fun uuid(value: Long): UUID = UUID(0, value)

    private object BoundaryStationaryFunction : PkCertifiedStationaryFunction {
        override val stationaryDomain = requireNotNull(OutwardInterval.create(-1.0, 1.0))
        override fun score(beta: Double): Double = beta
        override fun hessian(beta: Double): Double = 1.0
        override fun scoreInterval(beta: OutwardInterval): OutwardInterval = beta
        override fun hessianInterval(beta: OutwardInterval): OutwardInterval {
            return if (requireNotNull(beta.widthUpperBound()) > 0.25) {
                requireNotNull(OutwardInterval.create(-1.0, 1.0))
            } else {
                OutwardInterval.singleton(1.0)
            }
        }
    }

    private object FlatStationaryFunction : PkCertifiedStationaryFunction {
        override val stationaryDomain = requireNotNull(OutwardInterval.create(-1.0, 1.0))
        override fun score(beta: Double): Double = 0.0
        override fun hessian(beta: Double): Double = 0.0
        override fun scoreInterval(beta: OutwardInterval): OutwardInterval =
            OutwardInterval.singleton(0.0)

        override fun hessianInterval(beta: OutwardInterval): OutwardInterval =
            OutwardInterval.singleton(0.0)
    }

    private object NonFiniteStationaryFunction : PkCertifiedStationaryFunction {
        override val stationaryDomain = requireNotNull(OutwardInterval.create(-1.0, 1.0))
        override fun score(beta: Double): Double? = null
        override fun hessian(beta: Double): Double? = null
        override fun scoreInterval(beta: OutwardInterval): OutwardInterval? = null
        override fun hessianInterval(beta: OutwardInterval): OutwardInterval? = null
    }

    private object NoRootStationaryFunction : PkCertifiedStationaryFunction {
        override val stationaryDomain = requireNotNull(OutwardInterval.create(-1.0, 1.0))
        override fun score(beta: Double): Double = 1.0
        override fun hessian(beta: Double): Double = 0.0
        override fun scoreInterval(beta: OutwardInterval): OutwardInterval =
            OutwardInterval.singleton(1.0)

        override fun hessianInterval(beta: OutwardInterval): OutwardInterval =
            OutwardInterval.singleton(0.0)
    }

    private companion object {
        const val RLog = 0.04
    }
}
