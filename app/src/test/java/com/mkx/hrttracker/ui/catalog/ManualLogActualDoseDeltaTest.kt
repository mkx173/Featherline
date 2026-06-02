package com.mkx.hrttracker.ui.catalog

import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualLogActualDoseDeltaTest {

    @Test
    fun ampuleAllowsActualDoseDeltaInManualLog() {
        // Ampules have no amount field; the +/- mg delta is the only way to
        // record drawing slightly more or less than the nominal vial.
        assertTrue(
            manualLogAllowsActualDoseDelta(
                preparationType = MedicinePreparationType.INJECTION_SINGLE_USE_VIAL,
                applicationType = MedicationApplicationType.INJECTION,
            ),
        )
    }

    @Test
    fun measuredFormsDoNotAllowActualDoseDeltaInManualLog() {
        // Multi-use vial (mL) and gel container (g) ask for the dose directly,
        // so the delta stepper would be redundant.
        assertFalse(
            manualLogAllowsActualDoseDelta(
                preparationType = MedicinePreparationType.INJECTION_MULTI_USE_VIAL,
                applicationType = MedicationApplicationType.INJECTION,
            ),
        )
        assertFalse(
            manualLogAllowsActualDoseDelta(
                preparationType = MedicinePreparationType.GEL_CONTAINER,
                applicationType = MedicationApplicationType.GEL,
            ),
        )
    }
}
