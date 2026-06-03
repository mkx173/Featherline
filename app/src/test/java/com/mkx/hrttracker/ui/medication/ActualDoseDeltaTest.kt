package com.mkx.hrttracker.ui.medication

import com.mkx.hrttracker.model.medication.MedicinePreparationType
import kotlin.math.roundToLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActualDoseDeltaTest {

    @Test
    fun range_vial_isSymmetricTwentyPercentSnappedToStep() {
        val range = actualDoseDeltaRange(
            plannedAmount = 0.25, fraction = 0.20, step = 0.01, underDrawOnly = false,
        )
        assertEquals(-0.05, range.min, 1e-9)
        assertEquals(0.05, range.max, 1e-9)
        assertEquals(0.01, range.step, 1e-9)
    }

    @Test
    fun range_vial_reportedCase_pointTwoRoundsUpToFullMajorInterval() {
        // The reported bug: 0.2 * 0.20 = 0.04 = 4 steps of 0.01. Rounding up to
        // one full major interval (5 steps) ends the band on the major tick at
        // 0.05 instead of mid-segment at 0.04, so the last segment keeps its 4
        // minor ticks.
        val range = actualDoseDeltaRange(0.2, fraction = 0.20, step = 0.01, underDrawOnly = false)
        assertEquals(-0.05, range.min, 1e-9)
        assertEquals(0.05, range.max, 1e-9)
    }

    @Test
    fun range_alwaysRoundsUp_neverDownToNearerMajor() {
        // 0.26 * 0.20 = 0.052 = 5.2 steps. "Always round up" takes the next whole
        // major interval (10 steps), not the nearer 5.
        val range = actualDoseDeltaRange(0.26, fraction = 0.20, step = 0.01, underDrawOnly = false)
        assertEquals(-0.10, range.min, 1e-9)
        assertEquals(0.10, range.max, 1e-9)
    }

    @Test
    fun range_wholeStepsButNotMultipleOfFive_roundsUp() {
        // 0.30 * 0.20 = 0.06 = exactly 6 steps -> up to the next interval (10).
        val range = actualDoseDeltaRange(0.30, fraction = 0.20, step = 0.01, underDrawOnly = false)
        assertEquals(-0.10, range.min, 1e-9)
        assertEquals(0.10, range.max, 1e-9)
    }

    @Test
    fun range_exactMajorMultiple_doesNotOverRound() {
        // 0.50 * 0.20 = 0.10 = exactly 10 steps (2 intervals): the float-noise
        // epsilon keeps it at 10, not bumped up to 15.
        val range = actualDoseDeltaRange(0.50, fraction = 0.20, step = 0.01, underDrawOnly = false)
        assertEquals(-0.10, range.min, 1e-9)
        assertEquals(0.10, range.max, 1e-9)
    }

    @Test
    fun range_gel_roundsUpToWholeMajorInterval() {
        // 1.65 * 0.20 = 0.33 = 3.3 steps of 0.1 -> rounded up to one full major
        // interval (5 steps) so the band ends on a labeled major tick.
        val range = actualDoseDeltaRange(1.65, fraction = 0.20, step = 0.1, underDrawOnly = false)
        assertEquals(-0.5, range.min, 1e-9)
        assertEquals(0.5, range.max, 1e-9)
    }

    @Test
    fun range_ampule_isUnderDrawOnlyAtFiftyPercent() {
        // 5.0 * 0.50 = 2.5 = exactly 25 steps (5 intervals): clean, under-draw only.
        val range = actualDoseDeltaRange(5.0, fraction = 0.50, step = 0.1, underDrawOnly = true)
        assertEquals(-2.5, range.min, 1e-9)
        assertEquals(0.0, range.max, 1e-9)
    }

    @Test
    fun range_tinyDose_flooredToOneMajorInterval() {
        // 0.02 * 0.2 = 0.004 = 0.4 steps -> floored to one full major interval.
        val range = actualDoseDeltaRange(0.02, fraction = 0.20, step = 0.01, underDrawOnly = false)
        assertEquals(-0.05, range.min, 1e-9)
        assertEquals(0.05, range.max, 1e-9)
    }

    @Test
    fun range_bandIsAlwaysWholeMajorIntervals() {
        // The invariant the round-up exists to guarantee: the band spans a whole
        // number of 5-step major intervals (>= 1), which is exactly what makes
        // every pair of adjacent major ticks enclose 4 minor ticks. This sweep
        // fails the instant the band snaps to a non-multiple of the major step.
        val plannedAmounts = listOf(0.02, 0.1, 0.2, 0.25, 0.26, 0.3, 0.5, 1.0, 1.65, 5.0)
        val forms = listOf(
            ActualDoseDeltaFormParams(fraction = 0.50, step = 0.1, underDrawOnly = true),
            ActualDoseDeltaFormParams(fraction = 0.20, step = 0.01, underDrawOnly = false),
            ActualDoseDeltaFormParams(fraction = 0.20, step = 0.1, underDrawOnly = false),
        )
        for (params in forms) {
            for (planned in plannedAmounts) {
                val range = actualDoseDeltaRange(
                    planned, params.fraction, params.step, params.underDrawOnly,
                )
                // min is always -band; recover the band's step count.
                val bandSteps = (-range.min / range.step).roundToLong()
                val label = "planned=$planned params=$params"
                assertTrue(
                    "$label: band must span at least one major interval",
                    bandSteps >= ACTUAL_DOSE_DELTA_MAJOR_TICK_EVERY,
                )
                assertEquals(
                    "$label: band must be a whole number of major intervals",
                    0L,
                    bandSteps % ACTUAL_DOSE_DELTA_MAJOR_TICK_EVERY,
                )
            }
        }
    }

    @Test
    fun formParams_perPreparationType() {
        assertEquals(
            ActualDoseDeltaFormParams(fraction = 0.50, step = 0.1, underDrawOnly = true),
            actualDoseDeltaFormParams(MedicinePreparationType.INJECTION_SINGLE_USE_VIAL),
        )
        assertEquals(
            ActualDoseDeltaFormParams(fraction = 0.20, step = 0.01, underDrawOnly = false),
            actualDoseDeltaFormParams(MedicinePreparationType.INJECTION_MULTI_USE_VIAL),
        )
        assertEquals(
            ActualDoseDeltaFormParams(fraction = 0.20, step = 0.1, underDrawOnly = false),
            actualDoseDeltaFormParams(MedicinePreparationType.GEL_CONTAINER),
        )
        assertNull(actualDoseDeltaFormParams(MedicinePreparationType.PILL))
    }

    @Test
    fun deltaForActual_normalizesNearPlannedToNull() {
        assertNull(doseAmountDeltaForActual(scheduledAmount = 0.25, actualAmount = 0.25))
    }

    @Test
    fun deltaForActual_returnsSignedDifference() {
        assertEquals(0.05, doseAmountDeltaForActual(0.25, 0.30)!!, 1e-9)
        assertEquals(-0.05, doseAmountDeltaForActual(0.25, 0.20)!!, 1e-9)
    }

    @Test
    fun deltaForActual_clampsActualAboveZero() {
        val delta = doseAmountDeltaForActual(scheduledAmount = 0.25, actualAmount = -1.0)!!
        assertTrue(0.25 + delta > 0.0)
    }

    @Test
    fun rulerOverscrollScale_isCenteredStretchCappedByViewport() {
        assertEquals(1.0f, actualAmountRulerOverscrollScale(0f, viewportWidthPx = 1000f), 0f)
        assertEquals(1.04f, actualAmountRulerOverscrollScale(40f, viewportWidthPx = 1000f), 1e-6f)
        assertEquals(1.04f, actualAmountRulerOverscrollScale(-40f, viewportWidthPx = 1000f), 1e-6f)
        assertEquals(1.08f, actualAmountRulerOverscrollScale(500f, viewportWidthPx = 1000f), 1e-6f)
        assertEquals(1.0f, actualAmountRulerOverscrollScale(40f, viewportWidthPx = 0f), 0f)
    }

    @Test
    fun majorTick_endpointIsAlwaysMajor() {
        // A non-multiple delta still counts as major when it's a band endpoint,
        // so tight bands (gel) always label their extremes.
        assertTrue(isActualDoseDeltaMajorTick(delta = 0.02, step = 0.01, isEndpoint = true))
    }

    @Test
    fun majorTick_zeroAndMultiplesOfFiveStepsAreMajor() {
        assertTrue(isActualDoseDeltaMajorTick(0.0, step = 0.01, isEndpoint = false))
        assertTrue(isActualDoseDeltaMajorTick(0.05, step = 0.01, isEndpoint = false))
        assertTrue(isActualDoseDeltaMajorTick(-0.05, step = 0.01, isEndpoint = false))
        // ampule grid: 5 * 0.1 = 0.5
        assertTrue(isActualDoseDeltaMajorTick(-0.5, step = 0.1, isEndpoint = false))
        assertTrue(isActualDoseDeltaMajorTick(-2.0, step = 0.1, isEndpoint = false))
    }

    @Test
    fun majorTick_nonMultiplesAreMinor() {
        assertFalse(isActualDoseDeltaMajorTick(0.02, step = 0.01, isEndpoint = false))
        assertFalse(isActualDoseDeltaMajorTick(0.03, step = 0.01, isEndpoint = false))
        assertFalse(isActualDoseDeltaMajorTick(-0.3, step = 0.1, isEndpoint = false))
    }

    @Test
    fun majorTick_degenerateStepIsMinor() {
        assertFalse(isActualDoseDeltaMajorTick(0.0, step = 0.0, isEndpoint = false))
    }
}
