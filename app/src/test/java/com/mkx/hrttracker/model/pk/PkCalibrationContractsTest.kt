package com.mkx.hrttracker.model.pk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln

class PkCalibrationContractsTest {
    @Test
    fun personalParams_canonicalizeZeroAndRejectNonFinite() {
        assertSame(PkPersonalParams.population(), PkPersonalParams.create(emptyMap()))
        assertSame(
            PkPersonalParams.population(),
            PkPersonalParams.create(mapOf(PkCalibrationRoute.ORAL to -0.0)),
        )
        assertNull(PkPersonalParams.create(mapOf(PkCalibrationRoute.ORAL to Double.NaN)))
        assertNull(PkPersonalParams.create(mapOf(PkCalibrationRoute.ORAL to 1e6)))
        assertNull(PkPersonalParams.create(thetaKGlobal = 0.1))
        val params = requireNotNull(PkPersonalParams.create(mapOf(PkCalibrationRoute.ORAL to ln(2.0))))
        assertEquals(2.0, params.scaleFor(PkCalibrationRoute.ORAL), 1e-15)
        assertEquals(1.0, params.scaleFor(PkCalibrationRoute.GEL), 0.0)
    }

    @Test
    fun forwardBreakdown_requiresAllFiveRoutesAndSumsInCanonicalOrder() {
        assertNull(PkForwardBreakdown.create(mapOf(PkCalibrationRoute.ORAL to 1.0)))
        val breakdown = requireNotNull(
            PkForwardBreakdown.create(
                PkCalibrationRoute.entries.associateWith { route -> route.ordinal.toDouble() }
            )
        )
        assertEquals(10.0, breakdown.totalDrugPgml, 0.0)
        assertNull(
            PkForwardBreakdown.create(
                PkCalibrationRoute.entries.associateWith { route ->
                    if (route == PkCalibrationRoute.GEL) -1.0 else 0.0
                }
            )
        )
    }

    @Test
    fun routeResult_fittedBetaAndReasonsMatchTheDisplayState() {
        // An adjusted row must carry its fit; LAB_CALIBRATED means no warning fired.
        assertThrows(IllegalArgumentException::class.java) {
            PkRouteCalibrationResult(
                route = PkCalibrationRoute.ORAL,
                displayState = PkRouteCalibrationDisplayState.LAB_CALIBRATED,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PkRouteCalibrationResult(
                route = PkCalibrationRoute.ORAL,
                displayState = PkRouteCalibrationDisplayState.LAB_CALIBRATED,
                fittedBeta = 0.1,
                reasons = setOf(PkCalibrationReason.POSTERIOR_SD_TOO_WIDE),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PkRouteCalibrationResult(
                route = PkCalibrationRoute.ORAL,
                displayState = PkRouteCalibrationDisplayState.POPULATION_NO_LAB_SIGNAL,
                fittedBeta = 0.1,
            )
        }
    }

    @Test
    fun result_derivesPromotedRoutesAndDisplayParamsFromAdjustedRows() {
        val result = PkCalibrationResult(
            globalState = PkCalibrationGlobalState.READY,
            routeResults = PkCalibrationRoute.entries.map { route ->
                when (route) {
                    PkCalibrationRoute.INJECTION -> PkRouteCalibrationResult(
                        route = route,
                        displayState = PkRouteCalibrationDisplayState.LAB_CALIBRATED,
                        fittedBeta = ln(1.5),
                        betaPosteriorSd = 0.1,
                        supportingLabCount = 3,
                    )
                    PkCalibrationRoute.ORAL -> PkRouteCalibrationResult(
                        route = route,
                        displayState = PkRouteCalibrationDisplayState.LAB_ADJUSTED_PROVISIONAL,
                        reasons = setOf(PkCalibrationReason.NO_SUPPORTING_LABS),
                        fittedBeta = 0.0,
                        betaPosteriorSd = 0.3,
                    )
                    else -> PkRouteCalibrationResult(
                        route = route,
                        displayState = PkRouteCalibrationDisplayState.POPULATION_NO_LAB_SIGNAL,
                    )
                }
            },
        )
        assertEquals(listOf(PkCalibrationRoute.INJECTION, PkCalibrationRoute.ORAL), result.promotedRoutes)
        // A fitted beta of exactly zero is canonically absent from the params.
        assertEquals(setOf(PkCalibrationRoute.INJECTION), result.displayParams.routeLogScale.keys)
        assertEquals(1.5, result.displayParams.scaleFor(PkCalibrationRoute.INJECTION), 1e-15)
        assertTrue(PkCalibrationResult(PkCalibrationGlobalState.NO_USABLE_LABS).promotedRoutes.isEmpty())
    }
}
