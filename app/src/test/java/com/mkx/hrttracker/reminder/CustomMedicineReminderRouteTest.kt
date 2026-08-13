package com.mkx.hrttracker.reminder

import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.testCustomMedicine
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomMedicineReminderRouteTest {
    @Test
    fun customMedicine_showsCustomLabelInsteadOfInferredRoute() {
        val medication = testMedicationGroupMedication(
            medicine = testCustomMedicine(
                medicationName = "Progesterone",
                preparation = MedicinePreparation.Capsule(strengthMgPerCapsule = 100.0),
            ),
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.WholeUnit,
            count = 1,
        )
        val context = RuntimeEnvironment.getApplication().applicationContext

        assertEquals(
            "Progesterone · Progesterone · Custom · 100 mg",
            medicationDetailLine(context, "Progesterone", medication),
        )
    }
}
