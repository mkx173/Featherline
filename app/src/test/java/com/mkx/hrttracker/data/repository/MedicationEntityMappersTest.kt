package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.MedicationGroupItemEntity
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.testCustomMedicine
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationEntityMappersTest {
    @Test
    fun capsulePreparationRoundTripsThroughMedicineEntityFields() {
        val medicine = testCustomMedicine(
            medicationName = "Progesterone",
            category = MedicationCategory.CUSTOM,
            preparation = MedicinePreparation.Capsule(strengthMgPerCapsule = 100.0),
        )

        val entity = medicine.toEntity()
        val restored = entity.toMedicineModel()

        assertEquals(MedicinePreparationType.CAPSULE.name, entity.preparationType)
        assertEquals(100.0, entity.strengthMgPerTablet!!, 1e-9)
        assertEquals(MedicinePreparation.Capsule(strengthMgPerCapsule = 100.0), restored.preparation)
    }

    @Test
    fun groupItemDoseInstructionStorage_roundTripsEveryShape() {
        val cases = listOf(
            DoseInstruction.TabletFraction(1, 4),
            DoseInstruction.WholeUnit,
            DoseInstruction.VolumeMl(0.2),
            DoseInstruction.WeightGrams(2.5),
            DoseInstruction.Noop,
        )

        cases.forEachIndexed { index, instruction ->
            val entity = groupItemEntity(index, instruction)
            assertEquals(instruction, entity.toDoseInstruction())
        }
    }

    private fun groupItemEntity(index: Int, doseInstruction: DoseInstruction): MedicationGroupItemEntity {
        return MedicationGroupItemEntity(
            uuid = "aaaaaaaa-0000-0000-0000-00000000000$index",
            groupUuid = "bbbbbbbb-0000-0000-0000-000000000000",
            sortOrder = index,
            count = 1,
            medicineUuid = "cccccccc-0000-0000-0000-000000000000",
            applicationType = "ORAL",
            doseInstructionKind = doseInstruction.kind.name,
            tabletFractionNumerator = (doseInstruction as? DoseInstruction.TabletFraction)?.numerator,
            tabletFractionDenominator = (doseInstruction as? DoseInstruction.TabletFraction)?.denominator,
            doseVolumeMl = (doseInstruction as? DoseInstruction.VolumeMl)?.valueMl,
            doseWeightGrams = (doseInstruction as? DoseInstruction.WeightGrams)?.valueGrams,
            gelApplicationArea = "DEFAULT",
        )
    }
}
