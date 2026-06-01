package com.mkx.hrttracker.ui.medication

import com.mkx.hrttracker.model.medication.MedicinePreparationType
import org.junit.Assert.assertEquals
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
    fun range_gel_snapsAwayFromUglyThirds() {
        // 1.65 * 0.20 = 0.33 -> snapped to the 0.1 step -> 0.3, never 0.33.
        val range = actualDoseDeltaRange(1.65, fraction = 0.20, step = 0.1, underDrawOnly = false)
        assertEquals(-0.3, range.min, 1e-9)
        assertEquals(0.3, range.max, 1e-9)
    }

    @Test
    fun range_ampule_isUnderDrawOnlyAtFiftyPercent() {
        val range = actualDoseDeltaRange(5.0, fraction = 0.50, step = 0.1, underDrawOnly = true)
        assertEquals(-2.5, range.min, 1e-9)
        assertEquals(0.0, range.max, 1e-9)
    }

    @Test
    fun range_tinyDose_flooredToOneStep() {
        // 0.02 * 0.2 = 0.004 -> rounds to 0 -> floored to one step.
        val range = actualDoseDeltaRange(0.02, fraction = 0.20, step = 0.01, underDrawOnly = false)
        assertEquals(-0.01, range.min, 1e-9)
        assertEquals(0.01, range.max, 1e-9)
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
}
