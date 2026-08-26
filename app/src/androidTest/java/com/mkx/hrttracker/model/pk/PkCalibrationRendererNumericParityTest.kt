package com.mkx.hrttracker.model.pk

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

@RunWith(AndroidJUnit4::class)
class PkCalibrationRendererNumericParityTest {
    @Test
    fun rendererPredictiveBand_matchesFixedDf4ReferenceOnAndroidRuntime() {
        val referenceLogQuantiles = listOf(
            -2.7764451051977943,
            -1.1416266287209986,
            0.0,
            1.1416266287209986,
            2.7764451051977934,
        )
        val referenceRawQuantilesAt100 = listOf(
            6.225944020859163,
            31.92992178885083,
            100.0,
            313.18585952477224,
            1606.182125392772,
        )
        val law = requireNotNull(PkPredictiveBandMath.logQuantiles(0.0, 1.0))

        law.zip(referenceLogQuantiles).forEach { (actual, expected) ->
            assertEquals(expected, actual, PkCalibrationDefaults.BAND_ROOT_X_ABS_TOL)
        }
        val raw = law.map { offset -> 100.0 * exp(offset) }
        raw.zip(referenceRawQuantilesAt100).forEach { (actual, expected) ->
            assertEquals(expected, actual, 0.05 + 1e-3 * abs(expected))
        }
    }

    @Test
    fun gh32_matchesFixedReferenceVectorsOnAndroidRuntime() {
        assertRuleMatches(Gh32Nodes, Gh32Weights)
    }

    private fun assertRuleMatches(
        expectedNodes: List<Double>,
        expectedWeights: List<Double>,
    ) {
        val actual = PkPredictiveBandMath.hermiteRule
        assertEquals(expectedNodes.size, actual.nodes.size)
        assertEquals(expectedWeights.size, actual.weights.size)
        expectedNodes.zip(actual.nodes).forEach { (expected, value) ->
            assertEquals(
                expected,
                value,
                PkCalibrationDefaults.BAND_ROOT_X_ABS_TOL * max(1.0, abs(expected)),
            )
        }
        expectedWeights.zip(actual.weights).forEach { (expected, value) ->
            assertEquals(
                expected,
                value,
                PkCalibrationDefaults.BAND_ROOT_CDF_TOL * expected,
            )
        }
        assertEquals(1.0, actual.weights.sum(), PkCalibrationDefaults.BAND_ROOT_CDF_TOL)
    }

    private companion object {
        val Gh32Nodes = listOf(
            -7.1258139098307272, -6.4094981492696608, -5.8122259495159136,
            -5.2755509865158805, -4.777164503502596, -4.3055479533511987,
            -3.8537554854714449, -3.4171674928185705, -2.9924908250023741,
            -2.5772495377323175, -2.1694991836061122, -1.7676541094632017,
            -1.3703764109528718, -0.9765004635896829, -0.58497876543593241,
            -0.19484074156939934, 0.19484074156939934, 0.58497876543593241,
            0.9765004635896829, 1.3703764109528718, 1.7676541094632017,
            2.1694991836061122, 2.5772495377323175, 2.9924908250023741,
            3.4171674928185705, 3.8537554854714449, 4.3055479533511987,
            4.777164503502596, 5.2755509865158805, 5.8122259495159136,
            6.4094981492696608, 7.1258139098307272,
        )
        val Gh32Weights = listOf(
            4.1246074890182794e-23, 5.2084495919608411e-19, 6.7552902236701258e-16,
            2.3780648557777899e-13, 3.3475012398012279e-11, 2.3125184120742302e-9,
            8.8812907131058956e-8, 2.0596221039534347e-6, 3.0559803060896336e-5,
            3.0255702581706242e-4, 0.0020620510513078829, 0.0099034617023205894,
            0.034109847726092081, 0.085344808272080644, 0.15653899375759844,
            0.21170556988047937, 0.21170556988047937, 0.15653899375759844,
            0.085344808272080644, 0.034109847726092081, 0.0099034617023205894,
            0.0020620510513078829, 3.0255702581706242e-4, 3.0559803060896336e-5,
            2.0596221039534347e-6, 8.8812907131058956e-8, 2.3125184120742302e-9,
            3.3475012398012279e-11, 2.3780648557777899e-13, 6.7552902236701258e-16,
            5.2084495919608411e-19, 4.1246074890182794e-23,
        )
    }
}
