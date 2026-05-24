package com.mkx.hrttracker.model.medication

import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationCompatibilityTest {
    @Test
    fun preparationType_form_mapsEveryPreparationType() {
        assertForm(MedicinePreparationType.PILL, MedicinePreparationForm.TABLET)
        assertForm(MedicinePreparationType.CAPSULE, MedicinePreparationForm.CAPSULE)
        assertForm(MedicinePreparationType.INJECTION_SINGLE_USE_VIAL, MedicinePreparationForm.INJECTION)
        assertForm(MedicinePreparationType.INJECTION_MULTI_USE_VIAL, MedicinePreparationForm.INJECTION)
        assertForm(MedicinePreparationType.GEL_SACHET, MedicinePreparationForm.GEL)
        assertForm(MedicinePreparationType.GEL_CONTAINER, MedicinePreparationForm.GEL)
        assertForm(MedicinePreparationType.PATCH, MedicinePreparationForm.PATCH)
        assertForm(MedicinePreparationType.PATCH_OFF, MedicinePreparationForm.PATCH)
    }

    @Test
    fun applicationTypeCompatibility_matchesPreparationTable() {
        assertTrue(MedicationApplicationType.ORAL.isCompatibleWith(MedicinePreparationType.PILL))
        assertTrue(MedicationApplicationType.SUBLINGUAL.isCompatibleWith(MedicinePreparationType.PILL))
        assertFalse(MedicationApplicationType.INJECTION.isCompatibleWith(MedicinePreparationType.PILL))

        assertTrue(MedicationApplicationType.ORAL.isCompatibleWith(MedicinePreparationType.CAPSULE))
        assertFalse(MedicationApplicationType.SUBLINGUAL.isCompatibleWith(MedicinePreparationType.CAPSULE))

        assertTrue(MedicationApplicationType.INJECTION.isCompatibleWith(MedicinePreparationType.INJECTION_SINGLE_USE_VIAL))
        assertTrue(MedicationApplicationType.INJECTION.isCompatibleWith(MedicinePreparationType.INJECTION_MULTI_USE_VIAL))
        assertFalse(MedicationApplicationType.ORAL.isCompatibleWith(MedicinePreparationType.INJECTION_SINGLE_USE_VIAL))

        assertTrue(MedicationApplicationType.GEL.isCompatibleWith(MedicinePreparationType.GEL_SACHET))
        assertTrue(MedicationApplicationType.GEL.isCompatibleWith(MedicinePreparationType.GEL_CONTAINER))
        assertFalse(MedicationApplicationType.ORAL.isCompatibleWith(MedicinePreparationType.GEL_SACHET))

        assertTrue(MedicationApplicationType.PATCH_ON.isCompatibleWith(MedicinePreparationType.PATCH))
        assertFalse(MedicationApplicationType.ORAL.isCompatibleWith(MedicinePreparationType.PATCH))

        assertTrue(MedicationApplicationType.PATCH_OFF.isCompatibleWith(MedicinePreparationType.PATCH_OFF))
        assertTrue(MedicationApplicationType.PATCH_OFF.isCompatibleWith(null))
        assertFalse(MedicationApplicationType.ORAL.isCompatibleWith(null))
    }

    @Test
    fun doseInstructionCompatibility_matchesDoseShapeTable() {
        assertTrue(DoseInstruction.TabletFraction(1, 1).isCompatibleWith(MedicinePreparationType.PILL))
        assertFalse(DoseInstruction.WholeUnit.isCompatibleWith(MedicinePreparationType.PILL))

        assertTrue(DoseInstruction.WholeUnit.isCompatibleWith(MedicinePreparationType.CAPSULE))
        assertFalse(DoseInstruction.TabletFraction(1, 1).isCompatibleWith(MedicinePreparationType.CAPSULE))

        assertTrue(DoseInstruction.WholeUnit.isCompatibleWith(MedicinePreparationType.INJECTION_SINGLE_USE_VIAL))
        assertFalse(DoseInstruction.VolumeMl(0.5).isCompatibleWith(MedicinePreparationType.INJECTION_SINGLE_USE_VIAL))

        assertTrue(DoseInstruction.VolumeMl(0.5).isCompatibleWith(MedicinePreparationType.INJECTION_MULTI_USE_VIAL))
        assertFalse(DoseInstruction.WholeUnit.isCompatibleWith(MedicinePreparationType.INJECTION_MULTI_USE_VIAL))

        assertTrue(DoseInstruction.WholeUnit.isCompatibleWith(MedicinePreparationType.GEL_SACHET))
        assertFalse(DoseInstruction.WeightGrams(1.0).isCompatibleWith(MedicinePreparationType.GEL_SACHET))

        assertTrue(DoseInstruction.WeightGrams(1.0).isCompatibleWith(MedicinePreparationType.GEL_CONTAINER))
        assertFalse(DoseInstruction.WholeUnit.isCompatibleWith(MedicinePreparationType.GEL_CONTAINER))

        assertTrue(DoseInstruction.WholeUnit.isCompatibleWith(MedicinePreparationType.PATCH))
        assertFalse(DoseInstruction.TabletFraction(1, 1).isCompatibleWith(MedicinePreparationType.PATCH))

        assertTrue(DoseInstruction.Noop.isCompatibleWith(MedicinePreparationType.PATCH_OFF))
        assertTrue(DoseInstruction.Noop.isCompatibleWith(null))
        assertFalse(DoseInstruction.WholeUnit.isCompatibleWith(null))
    }

    @Test
    fun requiredApplicationType_returnsNullOnlyForPill() {
        assertTrue(MedicinePreparation.Pill(2.0).requiredApplicationType() == null)
        assertTrue(MedicinePreparation.Capsule(100.0).requiredApplicationType() == MedicationApplicationType.ORAL)
        assertTrue(
            MedicinePreparation.InjectionSingleUseVial(10.0).requiredApplicationType() ==
                MedicationApplicationType.INJECTION,
        )
        assertTrue(
            MedicinePreparation.InjectionMultiUseVial(10.0, 5.0).requiredApplicationType() ==
                MedicationApplicationType.INJECTION,
        )
        assertTrue(MedicinePreparation.GelSachet(0.06, 1.0).requiredApplicationType() == MedicationApplicationType.GEL)
        assertTrue(
            MedicinePreparation.GelContainer(0.06, 80.0).requiredApplicationType() == MedicationApplicationType.GEL,
        )
        assertTrue(
            MedicinePreparation.Patch(MedicinePreparation.PatchSpecification.TotalMg(1.56))
                .requiredApplicationType() == MedicationApplicationType.PATCH_ON,
        )
        assertTrue(MedicinePreparation.PatchOff.requiredApplicationType() == MedicationApplicationType.PATCH_OFF)
    }

    @Test
    fun medicationGroupMedication_rejectsRouteIncompatibleWithPreparation() {
        val medicine = testMedicine(
            preparation = MedicinePreparation.Capsule(strengthMgPerCapsule = 100.0),
        )

        assertThrows(IllegalArgumentException::class.java) {
            MedicationGroupMedication(
                uuid = UUID.randomUUID(),
                medicine = medicine,
                applicationType = MedicationApplicationType.SUBLINGUAL,
                doseInstruction = DoseInstruction.WholeUnit,
            )
        }
    }

    @Test
    fun medicationGroupMedication_rejectsDoseShapeIncompatibleWithPreparation() {
        val medicine = testMedicine(
            preparation = MedicinePreparation.Capsule(strengthMgPerCapsule = 100.0),
        )

        assertThrows(IllegalArgumentException::class.java) {
            MedicationGroupMedication(
                uuid = UUID.randomUUID(),
                medicine = medicine,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
            )
        }
    }

    @Test
    fun medicationLogEntry_rejectsRouteIncompatibleWithPreparation() {
        val medicine = testMedicine(
            preparation = MedicinePreparation.Capsule(strengthMgPerCapsule = 100.0),
        )

        assertThrows(IllegalArgumentException::class.java) {
            MedicationLogEntry(
                uuid = UUID.randomUUID(),
                medicine = medicine,
                category = medicine.category,
                applicationType = MedicationApplicationType.SUBLINGUAL,
                doseInstruction = DoseInstruction.WholeUnit,
                equivalentE2Mg = null,
                sourceGroupUuid = null,
                appliedAt = Instant.EPOCH,
            )
        }
    }

    @Test
    fun medicationLogEntry_rejectsDoseShapeIncompatibleWithPreparation() {
        val medicine = testMedicine(
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )

        assertThrows(IllegalArgumentException::class.java) {
            MedicationLogEntry(
                uuid = UUID.randomUUID(),
                medicine = medicine,
                category = medicine.category,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.WholeUnit,
                equivalentE2Mg = null,
                sourceGroupUuid = null,
                appliedAt = Instant.EPOCH,
            )
        }
    }

    @Test
    fun patchOffNullMedicineFallbackStillWorks() {
        MedicationGroupMedication(
            uuid = UUID.randomUUID(),
            medicine = null,
            applicationType = MedicationApplicationType.PATCH_OFF,
            doseInstruction = DoseInstruction.Noop,
        )

        MedicationLogEntry(
            uuid = UUID.randomUUID(),
            medicine = null,
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.PATCH_OFF,
            doseInstruction = DoseInstruction.Noop,
            equivalentE2Mg = null,
            sourceGroupUuid = null,
            appliedAt = Instant.EPOCH,
        )
    }

    private fun assertForm(
        preparationType: MedicinePreparationType,
        expectedForm: MedicinePreparationForm,
    ) {
        assertTrue(preparationType.form() == expectedForm)
    }
}
