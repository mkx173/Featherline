package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.DoseInstructionCalculator
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.medication.MedicineStock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class DoseInstructionCalculatorTest {
    private fun multiUseVial(concentrationMgPerMl: Double): Medicine = medicine(
        selection = MedicineSelection.Catalog(MedicationKey.ESTRADIOL_VALERATE),
        preparation = MedicinePreparation.InjectionMultiUseVial(
            concentrationMgPerMl = concentrationMgPerMl,
            vialVolumeMl = 10.0,
        ),
    )

    private fun singleUseVial(strengthMgPerVial: Double): Medicine = medicine(
        selection = MedicineSelection.Catalog(MedicationKey.ESTRADIOL_VALERATE),
        preparation = MedicinePreparation.InjectionSingleUseVial(
            strengthMgPerVial = strengthMgPerVial,
        ),
    )

    @Test
    fun perUnitAmountMg_appliesDeltaForMultiUseVial() {
        val mg = DoseInstructionCalculator.perUnitAmountMg(
            medicine = multiUseVial(concentrationMgPerMl = 20.0),
            doseInstruction = DoseInstruction.VolumeMl(0.5),
            doseAmountDelta = 0.1,
        )
        assertEquals(12.0, mg!!, 1e-9)
    }

    @Test
    fun perUnitAmountMg_appliesDeltaForSingleUseVialInMg() {
        val mg = DoseInstructionCalculator.perUnitAmountMg(
            medicine = singleUseVial(strengthMgPerVial = 5.0),
            doseInstruction = DoseInstruction.WholeUnit,
            doseAmountDelta = 0.2,
        )
        assertEquals(5.2, mg!!, 1e-9)
    }

    @Test
    fun perUnitAmountMg_clampsEffectiveAmountAboveZero() {
        val mg = DoseInstructionCalculator.perUnitAmountMg(
            medicine = multiUseVial(concentrationMgPerMl = 20.0),
            doseInstruction = DoseInstruction.VolumeMl(0.5),
            doseAmountDelta = -0.5,
        )
        assertTrue(mg!! > 0.0)
    }

    @Test
    fun effectiveDoseInstructionForDisplay_substitutesActualVolumeForMultiUseVial() {
        val effective = DoseInstructionCalculator.effectiveDoseInstructionForDisplay(
            preparation = multiUseVial(concentrationMgPerMl = 20.0).preparation,
            doseInstruction = DoseInstruction.VolumeMl(0.5),
            doseAmountDelta = 0.1,
        )
        assertEquals(0.6, (effective as DoseInstruction.VolumeMl).valueMl, 1e-9)
    }

    @Test
    fun effectiveDoseInstructionForDisplay_substitutesActualWeightForGelContainer() {
        val gel = medicine(
            selection = MedicineSelection.Catalog(MedicationKey.ESTRADIOL_GEL),
            preparation = MedicinePreparation.GelContainer(
                concentrationPercent = 0.06,
                containerWeightGrams = 80.0,
            ),
        )
        val effective = DoseInstructionCalculator.effectiveDoseInstructionForDisplay(
            preparation = gel.preparation,
            doseInstruction = DoseInstruction.WeightGrams(1.25),
            doseAmountDelta = -0.25,
        )
        assertEquals(1.0, (effective as DoseInstruction.WeightGrams).valueGrams, 1e-9)
    }

    @Test
    fun effectiveDoseInstructionForDisplay_leavesAmpuleAndNullDeltaUnchanged() {
        // Single-use vial carries no portion amount; the delta lands on the mg
        // line, so the displayed instruction stays WholeUnit.
        assertEquals(
            DoseInstruction.WholeUnit,
            DoseInstructionCalculator.effectiveDoseInstructionForDisplay(
                preparation = singleUseVial(strengthMgPerVial = 5.0).preparation,
                doseInstruction = DoseInstruction.WholeUnit,
                doseAmountDelta = 0.2,
            ),
        )
        // A null delta is a no-op regardless of preparation.
        assertEquals(
            DoseInstruction.VolumeMl(0.5),
            DoseInstructionCalculator.effectiveDoseInstructionForDisplay(
                preparation = multiUseVial(concentrationMgPerMl = 20.0).preparation,
                doseInstruction = DoseInstruction.VolumeMl(0.5),
                doseAmountDelta = null,
            ),
        )
    }

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

        assertEquals(0.5, requireNotNull(perUnitAmountMg), 0.0001)
        assertEquals(
            1.0,
            requireNotNull(
                DoseInstructionCalculator.totalAmountMg(
                    perUnitAmountMg = perUnitAmountMg,
                    count = 2
                ),
            ),
            0.0001,
        )
    }

    @Test
    fun capsuleWholeUnitUsesStrengthMgPerCapsule() {
        val medicine = medicine(
            selection = MedicineSelection.Custom(medicationName = "Progesterone"),
            preparation = MedicinePreparation.Capsule(strengthMgPerCapsule = 100.0),
        )

        assertEquals(
            100.0,
            requireNotNull(
                DoseInstructionCalculator.perUnitAmountMg(
                    medicine = medicine,
                    doseInstruction = DoseInstruction.WholeUnit,
                )
            ),
            1e-9,
        )
    }

    @Test
    fun capsuleTabletFractionReturnsNull() {
        val medicine = medicine(
            selection = MedicineSelection.Custom(medicationName = "Progesterone"),
            preparation = MedicinePreparation.Capsule(strengthMgPerCapsule = 100.0),
        )

        assertNull(
            DoseInstructionCalculator.perUnitAmountMg(
                medicine = medicine,
                doseInstruction = DoseInstruction.TabletFraction(1, 2),
            )
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

        assertEquals(4.0, requireNotNull(result), 0.0001)
    }

    @Test
    fun valerateEquivalentE2UsesMolecularWeightRatio() {
        val medicine = medicine(
            selection = MedicineSelection.Catalog(MedicationKey.ESTRADIOL_VALERATE),
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 4.0),
        )

        val result = DoseInstructionCalculator.perUnitEquivalentE2Mg(
            medicine = medicine,
            doseInstruction = DoseInstruction.TabletFraction(numerator = 1, denominator = 1),
        )

        assertEquals(4.0 * 272.4 / 356.5, requireNotNull(result), 0.0001)
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

        assertEquals(1.5, requireNotNull(result), 0.0001)
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
    fun pillBranchPreservesPrecisionUnderFractionReorder() {
        // strengthMgPerTablet * numerator must promote before integer division,
        // otherwise 3 * 1 / 4 truncates to 0.
        val medicine = medicine(
            selection = MedicineSelection.Catalog(MedicationKey.ESTRADIOL),
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 3.0),
        )

        val result = DoseInstructionCalculator.perUnitAmountMg(
            medicine = medicine,
            doseInstruction = DoseInstruction.TabletFraction(numerator = 1, denominator = 4),
        )

        assertEquals(0.75, requireNotNull(result), 1e-9)
    }

    @Test
    fun patchReleaseRateReturnsNullForPerUnitMg() {
        val medicine = medicine(
            selection = MedicineSelection.Catalog(MedicationKey.ESTRADIOL_PATCH),
            preparation = MedicinePreparation.Patch(
                specification = MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay(
                    valueMcgPerDay = 100.0,
                ),
            ),
        )

        assertNull(
            DoseInstructionCalculator.perUnitAmountMg(
                medicine = medicine,
                doseInstruction = DoseInstruction.WholeUnit,
            )
        )
    }

    @Test
    fun perUnitReleaseRateReturnsValueForReleaseRatePatchWholeUnit() {
        val medicine = medicine(
            selection = MedicineSelection.Catalog(MedicationKey.ESTRADIOL_PATCH),
            preparation = MedicinePreparation.Patch(
                specification = MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay(
                    valueMcgPerDay = 100.0,
                ),
            ),
        )

        assertEquals(
            100.0,
            requireNotNull(
                DoseInstructionCalculator.perUnitReleaseRateMcgPerDay(
                    medicine = medicine,
                    doseInstruction = DoseInstruction.WholeUnit,
                )
            ),
            1e-9,
        )
    }

    @Test
    fun perUnitReleaseRateReturnsNullForTotalMgPatch() {
        val medicine = medicine(
            selection = MedicineSelection.Catalog(MedicationKey.ESTRADIOL_PATCH),
            preparation = MedicinePreparation.Patch(
                specification = MedicinePreparation.PatchSpecification.TotalMg(valueMg = 1.56),
            ),
        )

        assertNull(
            DoseInstructionCalculator.perUnitReleaseRateMcgPerDay(
                medicine = medicine,
                doseInstruction = DoseInstruction.WholeUnit,
            )
        )
    }

    @Test
    fun perUnitReleaseRateReturnsNullForNonPatchPreparation() {
        val medicine = medicine(
            selection = MedicineSelection.Custom(medicationName = "Custom"),
            preparation = MedicinePreparation.Capsule(strengthMgPerCapsule = 50.0),
        )

        assertNull(
            DoseInstructionCalculator.perUnitReleaseRateMcgPerDay(
                medicine = medicine,
                doseInstruction = DoseInstruction.WholeUnit,
            )
        )
    }

    @Test
    fun catalogMedicineRejectsCategoryMismatch() {
        assertThrows(IllegalArgumentException::class.java) {
            medicine(
                selection = MedicineSelection.Catalog(MedicationKey.ESTRADIOL),
                category = MedicationCategory.ANTIANDROGEN,
                preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
            )
        }
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
            is MedicineSelection.PatchOff -> MedicationCategory.ESTRADIOL
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
            stock = MedicineStock(),
        )
    }
}
