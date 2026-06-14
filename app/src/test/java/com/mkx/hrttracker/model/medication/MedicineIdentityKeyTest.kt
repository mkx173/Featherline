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
    fun externalIdentityCanonicalizesDoseKeyComponents() {
        val keys = listOf(5.0, 5.00, 5.000).map { value ->
            MedicineIdentityKey.external(
                sourceApp = "NoMTF",
                applicationType = MedicationApplicationType.INJECTION,
                compound = "ESTRADIOL_VALERATE",
                doseKey = MedicineIdentityKey.canonicalDouble(value),
            )
        }

        assertEquals("E|NoMTF|INJECTION|ESTRADIOL_VALERATE|5", keys[0])
        assertEquals(keys[0], keys[1])
        assertEquals(keys[0], keys[2])
    }

    @Test
    fun externalIdentityDifferentiatesSourceRouteCompoundAndDose() {
        val base = MedicineIdentityKey.external(
            sourceApp = "NoMTF",
            applicationType = MedicationApplicationType.INJECTION,
            compound = "ESTRADIOL_VALERATE",
            doseKey = "5",
        )

        assertNotEquals(
            base,
            MedicineIdentityKey.external(
                sourceApp = "DoseLogger",
                applicationType = MedicationApplicationType.INJECTION,
                compound = "ESTRADIOL_VALERATE",
                doseKey = "5",
            ),
        )
        assertNotEquals(
            base,
            MedicineIdentityKey.external(
                sourceApp = "NoMTF",
                applicationType = MedicationApplicationType.GEL,
                compound = "ESTRADIOL_VALERATE",
                doseKey = "5",
            ),
        )
        assertNotEquals(
            base,
            MedicineIdentityKey.external(
                sourceApp = "NoMTF",
                applicationType = MedicationApplicationType.INJECTION,
                compound = "ESTRADIOL_CYPIONATE",
                doseKey = "5",
            ),
        )
        assertNotEquals(
            base,
            MedicineIdentityKey.external(
                sourceApp = "NoMTF",
                applicationType = MedicationApplicationType.INJECTION,
                compound = "ESTRADIOL_VALERATE",
                doseKey = "10",
            ),
        )
    }

    @Test
    fun externalIdentityRejectsBlankComponents() {
        assertThrows(IllegalArgumentException::class.java) {
            MedicineIdentityKey.external(
                sourceApp = " ",
                applicationType = MedicationApplicationType.INJECTION,
                compound = "ESTRADIOL_VALERATE",
                doseKey = "5",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MedicineIdentityKey.external(
                sourceApp = "NoMTF",
                applicationType = MedicationApplicationType.INJECTION,
                compound = " ",
                doseKey = "5",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MedicineIdentityKey.external(
                sourceApp = "NoMTF",
                applicationType = MedicationApplicationType.INJECTION,
                compound = "ESTRADIOL_VALERATE",
                doseKey = " ",
            )
        }
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
    fun canonicalDoubleUsesSixSignificantFiguresAboveOne() {
        assertEquals("123457", MedicineIdentityKey.canonicalDouble(123456.789))
    }

    @Test
    fun canonicalDoubleUsesSixSignificantFiguresBelowOne() {
        assertEquals("0.000123457", MedicineIdentityKey.canonicalDouble(0.000123456789))
    }

    @Test
    fun canonicalDoubleStripsTrailingZerosWithoutExponentNotation() {
        assertEquals("12.34", MedicineIdentityKey.canonicalDouble(12.3400001))
        assertEquals("100000", MedicineIdentityKey.canonicalDouble(100000.0))
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
