package com.mkx.hrttracker.model.pk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

class PkCalibrationPredictiveBandValidationTest {
    @Test
    fun studentT4ReferenceQuantiles_areRecoveredWhenRouteVarianceIsZero() {
        // R 4.5.1 qt(c(.025, .158655254, .5, .841344746, .975), df = 4).
        val reference = listOf(
            -2.7764451051977943,
            -1.1416266287209986,
            0.0,
            1.1416266287209986,
            2.7764451051977934,
        )
        val actual = requireNotNull(PkPredictiveBandMath.logQuantiles(0.0, 1.0))

        actual.gh16.zip(reference).forEach { (value, expected) ->
            assertEquals(expected, value, PkCalibrationDefaults.BAND_ROOT_X_ABS_TOL)
        }
        actual.gh32.zip(reference).forEach { (value, expected) ->
            assertEquals(expected, value, PkCalibrationDefaults.BAND_ROOT_X_ABS_TOL)
        }
    }

    @Test
    fun physicistsHermite16And32_matchFixedIndependentReferenceVectors() {
        // Fixed numpy.polynomial.hermite.hermgauss reference vectors, normalized by sqrt(pi).
        // NumPy documents hermgauss as Gauss-Hermite quadrature for weight exp(-x^2).
        assertRuleMatches(Gh16Nodes, Gh16Weights, PkCalibrationDefaults.BAND_GH_NODES)
        assertRuleMatches(Gh32Nodes, Gh32Weights, PkCalibrationDefaults.BAND_GH_REFINEMENT_NODES)
    }

    @Test
    fun alphaSquaredVariance_matchesOneRouteWithFixedRemainderAndMultiRouteOracles() {
        val oneRoute = requireNotNull(
            PkPredictiveBandMath.effectiveVariance(
                totalPgml = 100.0,
                contributionByRoutePgml = mapOf(PkCalibrationRoute.ORAL to 40.0),
                varianceByRoute = mapOf(PkCalibrationRoute.ORAL to 0.09),
                promotedRoutes = listOf(PkCalibrationRoute.ORAL),
            )
        )
        val oneRouteOracle = (40.0 / (40.0 + 60.0)).let { alpha ->
            alpha * alpha * 0.09
        }
        assertEquals(oneRouteOracle, oneRoute, 0.0)

        val multiRoute = requireNotNull(
            PkPredictiveBandMath.effectiveVariance(
                totalPgml = 200.0,
                contributionByRoutePgml = mapOf(
                    PkCalibrationRoute.INJECTION to 50.0,
                    PkCalibrationRoute.GEL to 80.0,
                ),
                varianceByRoute = mapOf(
                    PkCalibrationRoute.INJECTION to 0.04,
                    PkCalibrationRoute.GEL to 0.09,
                ),
                promotedRoutes = listOf(
                    PkCalibrationRoute.INJECTION,
                    PkCalibrationRoute.GEL,
                ),
            )
        )
        val multiRouteOracle =
            (50.0 / 200.0).let { alpha -> alpha * alpha * 0.04 } +
                    (80.0 / 200.0).let { alpha -> alpha * alpha * 0.09 }
        assertEquals(multiRouteOracle, multiRoute, 0.0)
    }

    @Test
    fun gh16Gh32Convergence_acceptsBoundaryAndRejectsOneBitAboveIt() {
        val exactAbsoluteBoundary = PkCalibrationDefaults.BAND_VALIDATE_ABS_PGML
        assertTrue(bandQuadratureConverges(exactAbsoluteBoundary, 0.0))
        assertFalse(
            bandQuadratureConverges(
                Math.nextUp(exactAbsoluteBoundary),
                0.0,
            )
        )
    }

    @Test
    fun deltaAggregateQuantiles_trackIndependentTwoDimensionalQuadratureOracle() {
        val fixedRemainder = 70.0
        val injectionContribution = 20.0
        val gelContribution = 10.0
        val total = fixedRemainder + injectionContribution + gelContribution
        val injectionVariance = 1e-6
        val gelVariance = 2e-6
        val rLog = 0.04

        val effectiveVariance = requireNotNull(
            PkPredictiveBandMath.effectiveVariance(
                totalPgml = total,
                contributionByRoutePgml = mapOf(
                    PkCalibrationRoute.INJECTION to injectionContribution,
                    PkCalibrationRoute.GEL to gelContribution,
                ),
                varianceByRoute = mapOf(
                    PkCalibrationRoute.INJECTION to injectionVariance,
                    PkCalibrationRoute.GEL to gelVariance,
                ),
                promotedRoutes = listOf(
                    PkCalibrationRoute.INJECTION,
                    PkCalibrationRoute.GEL,
                ),
            )
        )
        val production = requireNotNull(
            PkPredictiveBandMath.logQuantiles(effectiveVariance, rLog)
        ).validatedRawQuantiles(total)
        requireNotNull(production)

        val oracle = independentTwoRouteRawQuantiles(
            fixedRemainder = fixedRemainder,
            firstContribution = injectionContribution,
            secondContribution = gelContribution,
            firstVariance = injectionVariance,
            secondVariance = gelVariance,
            rLog = rLog,
        )
        production.zip(oracle).forEach { (actual, expected) ->
            val normativeValidationBound = PkCalibrationDefaults.BAND_VALIDATE_ABS_PGML +
                    PkCalibrationDefaults.BAND_VALIDATE_REL * abs(expected)
            assertEquals(expected, actual, normativeValidationBound)
        }
    }

    private fun assertRuleMatches(
        expectedNodes: List<Double>,
        expectedWeights: List<Double>,
        nodeCount: Int,
    ) {
        val actual = requireNotNull(PkPredictiveBandMath.hermiteRuleForValidation(nodeCount))
        assertEquals(expectedNodes.size, actual.nodes.size)
        assertEquals(expectedWeights.size, actual.weights.size)
        expectedNodes.zip(actual.nodes).forEach { (expected, value) ->
            val normativeRootBound = PkCalibrationDefaults.BAND_ROOT_X_ABS_TOL *
                    max(1.0, abs(expected))
            assertEquals(expected, value, normativeRootBound)
        }
        expectedWeights.zip(actual.weights).forEach { (expected, value) ->
            val normativeCdfBound = PkCalibrationDefaults.BAND_ROOT_CDF_TOL * expected
            assertEquals(expected, value, normativeCdfBound)
        }
        assertEquals(1.0, actual.weights.sum(), PkCalibrationDefaults.BAND_ROOT_CDF_TOL)
    }

    private fun independentTwoRouteRawQuantiles(
        fixedRemainder: Double,
        firstContribution: Double,
        secondContribution: Double,
        firstVariance: Double,
        secondVariance: Double,
        rLog: Double,
    ): List<Double> {
        val total = fixedRemainder + firstContribution + secondContribution
        val components = ArrayList<Pair<Double, Double>>(Gh32Nodes.size * Gh32Nodes.size)
        for (firstIndex in Gh32Nodes.indices) {
            val firstEta = sqrt(2.0 * firstVariance) * Gh32Nodes[firstIndex]
            for (secondIndex in Gh32Nodes.indices) {
                val secondEta = sqrt(2.0 * secondVariance) * Gh32Nodes[secondIndex]
                val conditionalMedian = fixedRemainder +
                        firstContribution * exp(firstEta) +
                        secondContribution * exp(secondEta)
                components += ln(conditionalMedian / total) to
                        Gh32Weights[firstIndex] * Gh32Weights[secondIndex]
            }
        }
        val observationScale = sqrt(rLog)
        return Probabilities.map { probability ->
            var lower = -4.0
            var upper = 4.0
            repeat(100) {
                val midpoint = lower + (upper - lower) / 2.0
                var cdf = 0.0
                for ((conditionalLogRatio, weight) in components) {
                    val standardized = (midpoint - conditionalLogRatio) / observationScale
                    cdf += weight * independentStudentT4Cdf(standardized)
                }
                if (cdf >= probability) upper = midpoint else lower = midpoint
            }
            total * exp(lower + (upper - lower) / 2.0)
        }
    }

    private fun independentStudentT4Cdf(value: Double): Double {
        // Closed-form integral of f(t) = 3/8 * (1 + t^2/4)^(-5/2).
        val sine = value / sqrt(value * value + 4.0)
        return 0.5 + 0.75 * sine - 0.25 * sine * sine * sine
    }

    private companion object {
        val Probabilities = listOf(0.025, 0.158655254, 0.5, 0.841344746, 0.975)

        val Gh16Nodes = listOf(
            -4.6887389393058188,
            -3.8694479048601229,
            -3.176999161979956,
            -2.5462021578474814,
            -1.9517879909162539,
            -1.3802585391988809,
            -0.8229514491446559,
            -0.27348104613815249,
            0.27348104613815249,
            0.8229514491446559,
            1.3802585391988809,
            1.9517879909162539,
            2.5462021578474814,
            3.176999161979956,
            3.8694479048601229,
            4.6887389393058188,
        )
        val Gh16Weights = listOf(
            1.4978147231618314e-10,
            1.3094732162868187e-7,
            1.5300032162487242e-5,
            5.2598492657390935e-4,
            0.0072669376011847428,
            0.047284752354014033,
            0.15833837275094964,
            0.28656852123801202,
            0.28656852123801202,
            0.15833837275094964,
            0.047284752354014033,
            0.0072669376011847428,
            5.2598492657390935e-4,
            1.5300032162487242e-5,
            1.3094732162868187e-7,
            1.4978147231618314e-10,
        )
        val Gh32Nodes = listOf(
            -7.1258139098307272,
            -6.4094981492696608,
            -5.8122259495159136,
            -5.2755509865158805,
            -4.777164503502596,
            -4.3055479533511987,
            -3.8537554854714449,
            -3.4171674928185705,
            -2.9924908250023741,
            -2.5772495377323175,
            -2.1694991836061122,
            -1.7676541094632017,
            -1.3703764109528718,
            -0.9765004635896829,
            -0.58497876543593241,
            -0.19484074156939934,
            0.19484074156939934,
            0.58497876543593241,
            0.9765004635896829,
            1.3703764109528718,
            1.7676541094632017,
            2.1694991836061122,
            2.5772495377323175,
            2.9924908250023741,
            3.4171674928185705,
            3.8537554854714449,
            4.3055479533511987,
            4.777164503502596,
            5.2755509865158805,
            5.8122259495159136,
            6.4094981492696608,
            7.1258139098307272,
        )
        val Gh32Weights = listOf(
            4.1246074890182794e-23,
            5.2084495919608411e-19,
            6.7552902236701258e-16,
            2.3780648557777899e-13,
            3.3475012398012279e-11,
            2.3125184120742302e-9,
            8.8812907131058956e-8,
            2.0596221039534347e-6,
            3.0559803060896336e-5,
            3.0255702581706242e-4,
            0.0020620510513078829,
            0.0099034617023205894,
            0.034109847726092081,
            0.085344808272080644,
            0.15653899375759844,
            0.21170556988047937,
            0.21170556988047937,
            0.15653899375759844,
            0.085344808272080644,
            0.034109847726092081,
            0.0099034617023205894,
            0.0020620510513078829,
            3.0255702581706242e-4,
            3.0559803060896336e-5,
            2.0596221039534347e-6,
            8.8812907131058956e-8,
            2.3125184120742302e-9,
            3.3475012398012279e-11,
            2.3780648557777899e-13,
            6.7552902236701258e-16,
            5.2084495919608411e-19,
            4.1246074890182794e-23,
        )
    }
}
