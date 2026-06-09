package com.mkx.hrttracker.model.medication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MedicineIdentityKeyTest {
    @Test
    fun customNameNormalization_trimsLowercasesAndCollapsesWhitespace() {
        assertEquals(
            "estro gel forte",
            normalizeCustomMedicationName("  Estro\tGel   Forte  "),
        )
    }

    @Test
    fun customSelectionCopyRecomputesNormalizedMedicationName() {
        val selection =
            MedicineSelection.Custom("Estradiol").copy(medicationName = "  Estro   Gel  ")

        assertEquals("estro gel", selection.normalizedMedicationName)
    }

    @Test
    fun catalogPillIdentityIncludesMedicationKeyPreparationTypeAndStrength() {
        val identityKey = MedicineIdentityKey.catalog(
            medicationKey = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )

        assertEquals("C|ESTRADIOL|PILL|strengthMgPerTablet=2", identityKey)
    }

    @Test
    fun customCapsuleIdentityDiffersFromCustomPillAtSameStrength() {
        val pillKey = MedicineIdentityKey.custom(
            customMedicationName = "Progesterone",
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 100.0),
        )
        val capsuleKey = MedicineIdentityKey.custom(
            customMedicationName = "Progesterone",
            preparation = MedicinePreparation.Capsule(strengthMgPerCapsule = 100.0),
        )

        assertEquals("X|progesterone|PILL|strengthMgPerTablet=100", pillKey)
        assertEquals("X|progesterone|CAPSULE|strengthMgPerTablet=100", capsuleKey)
        assertNotEquals(pillKey, capsuleKey)
    }

    @Test
    fun customGelContainerIdentityUsesNormalizedNameAndContainerFields() {
        val identityKey = MedicineIdentityKey.custom(
            customMedicationName = "  Estro   Gel  ",
            preparation = MedicinePreparation.GelContainer(
                concentrationPercent = 0.06,
                containerWeightGrams = 80.0,
            ),
        )

        assertEquals(
            "X|estro gel|GEL_CONTAINER|concentrationPercent=0.06|containerWeightGrams=80",
            identityKey,
        )
    }

    @Test
    fun canonicalDoubleCollapsesLogicallyEqualValues() {
        assertEquals(
            MedicineIdentityKey.canonicalDouble(0.3),
            MedicineIdentityKey.canonicalDouble(0.1 + 0.2),
        )
        assertEquals("2.5", MedicineIdentityKey.canonicalDouble(2.5000001))
    }

    @Test
    fun medicinePreparationNumericFieldsRejectInvalidValues() {
        val invalidValues = listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)

        invalidValues.forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                MedicinePreparation.Pill(strengthMgPerTablet = value)
            }
            assertThrows(IllegalArgumentException::class.java) {
                MedicinePreparation.InjectionSingleUseVial(strengthMgPerVial = value)
            }
            assertThrows(IllegalArgumentException::class.java) {
                MedicinePreparation.InjectionMultiUseVial(
                    concentrationMgPerMl = value,
                    vialVolumeMl = 10.0,
                )
            }
            assertThrows(IllegalArgumentException::class.java) {
                MedicinePreparation.InjectionMultiUseVial(
                    concentrationMgPerMl = 20.0,
                    vialVolumeMl = value,
                )
            }
            assertThrows(IllegalArgumentException::class.java) {
                MedicinePreparation.GelSachet(
                    concentrationPercent = value,
                    sachetWeightGrams = 2.5,
                )
            }
            assertThrows(IllegalArgumentException::class.java) {
                MedicinePreparation.GelSachet(
                    concentrationPercent = 0.06,
                    sachetWeightGrams = value,
                )
            }
            assertThrows(IllegalArgumentException::class.java) {
                MedicinePreparation.GelContainer(
                    concentrationPercent = value,
                    containerWeightGrams = 80.0,
                )
            }
            assertThrows(IllegalArgumentException::class.java) {
                MedicinePreparation.GelContainer(
                    concentrationPercent = 0.06,
                    containerWeightGrams = value,
                )
            }
            assertThrows(IllegalArgumentException::class.java) {
                MedicinePreparation.Patch(
                    specification = MedicinePreparation.PatchSpecification.TotalMg(valueMg = value),
                )
            }
            assertThrows(IllegalArgumentException::class.java) {
                MedicinePreparation.Patch(
                    specification = MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay(
                        valueMcgPerDay = value,
                    ),
                )
            }
        }
    }
}
