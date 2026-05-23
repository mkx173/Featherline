package com.mkx.hrttracker.model.medication

import org.junit.Assert.assertEquals
import org.junit.Test

class MedicineDisplayDoseUnitTest {

    // Round-trip across the three units: a value typed in one unit, converted
    // to mg for storage, then converted back, must equal the original. Catches
    // off-by-1000 errors in either direction.
    @Test
    fun roundTripPreservesValue() {
        val cases = listOf(
            MedicineDisplayDoseUnit.MG to 2.5,
            MedicineDisplayDoseUnit.MCG to 50.0,
            MedicineDisplayDoseUnit.G to 0.25,
        )
        for ((unit, value) in cases) {
            val mg = unit.toMg(value)
            assertEquals(value, unit.fromMg(mg), 1e-9)
        }
    }

    // Anchors the scaling so a future refactor of toMg/fromMg can't silently
    // reverse the direction of conversion.
    @Test
    fun mcgScalesByOneThousand() {
        assertEquals(0.05, MedicineDisplayDoseUnit.MCG.toMg(50.0), 1e-9)
        assertEquals(50.0, MedicineDisplayDoseUnit.MCG.fromMg(0.05), 1e-9)
    }

    @Test
    fun gramsScalesByOneThousand() {
        assertEquals(250.0, MedicineDisplayDoseUnit.G.toMg(0.25), 1e-9)
        assertEquals(0.25, MedicineDisplayDoseUnit.G.fromMg(250.0), 1e-9)
    }

    @Test
    fun fromStorageValueFallsBackToMg() {
        assertEquals(MedicineDisplayDoseUnit.MG, MedicineDisplayDoseUnit.fromStorageValue(null))
        assertEquals(MedicineDisplayDoseUnit.MG, MedicineDisplayDoseUnit.fromStorageValue("bogus"))
        assertEquals(MedicineDisplayDoseUnit.MCG, MedicineDisplayDoseUnit.fromStorageValue("MCG"))
    }
}
