package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationDoseUnit
import com.mkx.hrttracker.model.medication.testCustomMedicationDetails
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MedicationSignatureTest {
    @Test
    fun custom_mg_medications_with_different_display_units_have_distinct_signatures() {
        val mgMedication = testMedicationGroupMedication(
            details = testCustomMedicationDetails(
                medicationName = "Custom medication",
                dose = MedicationDose.MgAsMedicine(0.2),
                customDoseUnit = MedicationDoseUnit.MG,
            )
        )
        val mcgMedication = testMedicationGroupMedication(
            details = testCustomMedicationDetails(
                medicationName = "Custom medication",
                dose = MedicationDose.MgAsMedicine(0.2),
                customDoseUnit = MedicationDoseUnit.MCG,
            )
        )

        assertNotEquals(
            MedicationSignature.fromGroupMedication(mgMedication),
            MedicationSignature.fromGroupMedication(mcgMedication),
        )
    }
}
