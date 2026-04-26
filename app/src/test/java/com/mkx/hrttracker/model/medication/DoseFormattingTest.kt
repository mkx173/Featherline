package com.mkx.hrttracker.model.medication

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class DoseFormattingTest {
    @Test
    fun format_dose_trims_trailing_zeroes() {
        assertEquals("12.5", 12.5.formatDose(Locale.US))
        assertEquals("6.25", 6.25.formatDose(Locale.US))
        assertEquals("2", 2.0.formatDose(Locale.US))
        assertEquals("0.06", 0.06.formatDose(Locale.US))
    }

    @Test
    fun format_dose_uses_locale_decimal_separator() {
        assertEquals("12,5", 12.5.formatDose(Locale.GERMANY))
    }

    @Test
    fun medication_dose_units_convert_to_and_from_canonical_mg() {
        assertEquals(0.2, MedicationDoseUnit.MCG.toCanonicalMg(200.0), 0.0)
        assertEquals(200.0, MedicationDoseUnit.MCG.fromCanonicalMg(0.2), 0.0)
        assertEquals(2.0, MedicationDoseUnit.G.fromCanonicalMg(2000.0), 0.0)
        assertEquals(2000.0, MedicationDoseUnit.G.toCanonicalMg(2.0), 0.0)
    }

    @Test
    fun medication_dose_units_format_canonical_mg_in_selected_unit() {
        assertEquals("200", MedicationDoseUnit.MCG.formatDoseFromCanonicalMg(0.2, Locale.US))
        assertEquals("2", MedicationDoseUnit.G.formatDoseFromCanonicalMg(2000.0, Locale.US))
    }
}
