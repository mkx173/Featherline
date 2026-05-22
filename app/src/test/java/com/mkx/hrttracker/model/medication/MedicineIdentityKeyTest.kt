package com.mkx.hrttracker.model.medication

import org.junit.Assert.assertEquals
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
    fun catalogPillIdentityIncludesMedicationKeyPreparationTypeAndStrength() {
        val identityKey = MedicineIdentityKey.catalog(
            medicationKey = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )

        assertEquals("C|ESTRADIOL|PILL|strengthMgPerTablet=2", identityKey)
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
}
