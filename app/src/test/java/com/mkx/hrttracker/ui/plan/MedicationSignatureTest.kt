package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.DoseInstructionKind
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationSignature
import com.mkx.hrttracker.model.medication.testMedicine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class MedicationSignatureTest {
    @Test
    fun signatureUsesMedicineUuidApplicationTypeAndDoseInstruction() {
        val medicine = testMedicine(uuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000"))
        val medication = MedicationGroupMedication(
            uuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000000"),
            medicine = medicine,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 2),
            count = 1,
        )

        assertEquals(
            MedicationSignature(
                medicineUuid = "aaaaaaaa-0000-0000-0000-000000000000",
                applicationType = MedicationApplicationType.ORAL.name,
                doseInstructionKind = DoseInstructionKind.TABLET_FRACTION.name,
                tabletFractionNumerator = 1,
                tabletFractionDenominator = 2,
                doseVolumeMl = null,
                doseWeightGrams = null,
            ),
            MedicationSignature.fromGroupMedication(medication),
        )
    }

    @Test
    fun patchOffSignatureCollapsesToApplicationTypeOnly() {
        val first = patchOffMedication("aaaaaaaa-0000-0000-0000-000000000000")
        val second = patchOffMedication("bbbbbbbb-0000-0000-0000-000000000000")

        assertEquals(
            MedicationSignature.patchOff(),
            MedicationSignature.fromGroupMedication(first),
        )
        assertEquals(
            MedicationSignature.fromGroupMedication(first),
            MedicationSignature.fromGroupMedication(second),
        )
    }

    // A PATCH_OFF slot carries no medicine — only its route distinguishes it.
    private fun patchOffMedication(uuid: String): MedicationGroupMedication {
        return MedicationGroupMedication(
            uuid = UUID.fromString(uuid),
            medicine = null,
            applicationType = MedicationApplicationType.PATCH_OFF,
            doseInstruction = DoseInstruction.Noop,
            count = 1,
        )
    }
}
