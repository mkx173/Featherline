package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DoseInstructionCalculatorTest {
    @Test
    fun pillFractionMultipliesStrengthFractionAndCount() {
        val medicine = medicine(
            selection = MedicineSelection.Catalog(MedicationKey.ESTRADIOL),
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )
        val perUnitAmountMg = DoseInstructionCalculator.perUnitAmountMg(
            medicine = medicine,
            doseInstruction = DoseInstruction.TabletFraction(numerator = 1, denominator = 4),
        )

        assertEquals(0.5, perUnitAmountMg ?: 0.0, 0.0001)
        assertEquals(
            1.0,
            DoseInstructionCalculator.totalAmountMg(perUnitAmountMg = perUnitAmountMg, count = 2)
                ?: 0.0,
            0.0001,
        )
    }

    @Test
    fun multiUseInjectionUsesConcentrationAndVolume() {
        val medicine = medicine(
            selection = MedicineSelection.Catalog(MedicationKey.ESTRADIOL_VALERATE),
            preparation = MedicinePreparation.InjectionMultiUseVial(
                concentrationMgPerMl = 20.0,
                vialVolumeMl = 10.0,
            ),
        )

        val result = DoseInstructionCalculator.perUnitAmountMg(
            medicine = medicine,
            doseInstruction = DoseInstruction.VolumeMl(valueMl = 0.2),
        )

        assertEquals(4.0, result ?: 0.0, 0.0001)
    }

    @Test
    fun gelPercentConvertsToMgPerGram() {
        val medicine = medicine(
            selection = MedicineSelection.Catalog(MedicationKey.ESTRADIOL_GEL),
            preparation = MedicinePreparation.GelContainer(
                concentrationPercent = 0.06,
                containerWeightGrams = 80.0,
            ),
        )

        val result = DoseInstructionCalculator.perUnitEquivalentE2Mg(
            medicine = medicine,
            doseInstruction = DoseInstruction.WeightGrams(valueGrams = 2.5),
        )

        assertEquals(1.5, result ?: 0.0, 0.0001)
    }

    @Test
    fun patchOffHasNoConsumedAmount() {
        val medicine = medicine(
            selection = MedicineSelection.Catalog(MedicationKey.ESTRADIOL_PATCH),
            preparation = MedicinePreparation.Patch(
                specification = MedicinePreparation.PatchSpecification.TotalMg(valueMg = 1.56),
            ),
        )

        val result = DoseInstructionCalculator.perUnitAmountMg(
            medicine = medicine,
            doseInstruction = DoseInstruction.Noop,
        )

        assertNull(result)
    }

    @Test
    fun customMedicineIsExcludedFromEquivalentE2() {
        val medicine = medicine(
            selection = MedicineSelection.Custom(medicationName = "Custom estradiol"),
            category = MedicationCategory.ESTRADIOL,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )

        val result = DoseInstructionCalculator.perUnitEquivalentE2Mg(
            medicine = medicine,
            doseInstruction = DoseInstruction.TabletFraction(numerator = 1, denominator = 1),
        )

        assertNull(result)
    }

    private fun medicine(
        selection: MedicineSelection,
        preparation: MedicinePreparation,
        category: MedicationCategory = when (selection) {
            is MedicineSelection.Catalog -> selection.medicationKey.category
            is MedicineSelection.Custom -> MedicationCategory.CUSTOM
        },
    ): Medicine {
        val timestamp = Instant.parse("2026-05-22T00:00:00Z")
        return Medicine(
            uuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000"),
            selection = selection,
            category = category,
            preparation = preparation,
            displayName = null,
            identityKey = "test",
            createdAt = timestamp,
            updatedAt = timestamp,
            archivedAt = null,
        )
    }
}
