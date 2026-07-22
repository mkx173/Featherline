package com.mkx.hrttracker.ui.pkcalibrationdebug

import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.PkCurvePoint
import com.mkx.hrttracker.model.pk.PkPredictiveBandKnot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PkCalibrationDebugScreenGeometryTest {
    @Test
    fun chartGeometry_mapsCentralAndOuterBandPolylinesIntoBounds() {
        val central = listOf(
            curvePoint(1_000L, 10.0),
            curvePoint(2_000L, 20.0),
            curvePoint(3_000L, 15.0),
        )
        val bands = listOf(
            bandKnot(1_000L, lower = 5.0, median = 10.0, upper = 15.0),
            bandKnot(2_000L, lower = 10.0, median = 20.0, upper = 25.0),
            bandKnot(3_000L, lower = 7.5, median = 15.0, upper = 20.0),
        )

        val geometry = pkCalibrationDebugChartGeometry(
            centralCurve = central,
            bandKnots = bands,
            width = 100f,
            height = 50f,
        )

        assertEquals(3, geometry.central.size)
        assertEquals(3, geometry.lowerBand.size)
        assertEquals(3, geometry.upperBand.size)
        assertEquals(0f, geometry.central.first().x, 0.0001f)
        assertEquals(100f, geometry.central.last().x, 0.0001f)
        assertEquals(50f, geometry.lowerBand.first().y, 0.0001f)
        assertEquals(0f, geometry.upperBand[1].y, 0.0001f)
        (geometry.central + geometry.lowerBand + geometry.upperBand).forEach { point ->
            assertTrue(point.x in 0f..100f)
            assertTrue(point.y in 0f..50f)
        }
    }

    @Test
    fun chartGeometry_returnsEmptyForNoDataOrInvalidSize() {
        assertEquals(
            PkCalibrationDebugChartGeometry(emptyList(), emptyList(), emptyList()),
            pkCalibrationDebugChartGeometry(emptyList(), emptyList(), 100f, 50f),
        )
        assertEquals(
            PkCalibrationDebugChartGeometry(emptyList(), emptyList(), emptyList()),
            pkCalibrationDebugChartGeometry(
                centralCurve = listOf(curvePoint(1_000L, 10.0)),
                bandKnots = emptyList(),
                width = 0f,
                height = 50f,
            ),
        )
    }

    @Test
    fun visibleRouteRows_failClosedUnlessReadyAndExactlyCanonical() {
        val snapshot = DefaultPkCalibrationDebugScenarioSource(
            debugGate = PkCalibrationDebugGate { true }
        ).loadFixture(
            PkCalibrationDebugScenario.preset(
                PkCalibrationDebugPreset.MIXED_INJECTION_ORAL
            )
        ) as PkCalibrationDebugSourceResult.Available
        val readyState = PkCalibrationDebugUiState(
            debugEnabled = true,
            globalState = PkCalibrationGlobalState.READY,
            routeRows = snapshot.snapshot.result.routeResults,
        )

        assertEquals(
            PkCalibrationRoute.entries.toList(),
            pkCalibrationDebugVisibleRouteRows(readyState).map { it.route },
        )
        assertTrue(
            pkCalibrationDebugVisibleRouteRows(
                readyState.copy(globalState = PkCalibrationGlobalState.SHARED_INPUT_INVALID)
            ).isEmpty()
        )
        assertTrue(
            pkCalibrationDebugVisibleRouteRows(
                readyState.copy(routeRows = readyState.routeRows.dropLast(1))
            ).isEmpty()
        )
    }

    private fun curvePoint(epochMillis: Long, concentration: Double): PkCurvePoint =
        requireNotNull(PkCurvePoint.create(epochMillis, concentration))

    private fun bandKnot(
        epochMillis: Long,
        lower: Double,
        median: Double,
        upper: Double,
    ): PkPredictiveBandKnot = requireNotNull(PkPredictiveBandKnot.create(
        epochMillis = epochMillis,
        p025Pgml = lower,
        p158655254Pgml = (lower + median) / 2.0,
        p50Pgml = median,
        p841344746Pgml = (median + upper) / 2.0,
        p975Pgml = upper,
    ))
}
