package com.mkx.hrttracker.ui.medication

import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationUiTextTest {
    @Test
    fun formatTabletFraction_renders_integer_when_denominator_is_one() {
        assertEquals("1", formatTabletFraction(DoseInstruction.TabletFraction(1, 1)))
        assertEquals("3", formatTabletFraction(DoseInstruction.TabletFraction(3, 1)))
    }

    @Test
    fun formatTabletFraction_renders_ascii_fraction_otherwise() {
        assertEquals("1/2", formatTabletFraction(DoseInstruction.TabletFraction(1, 2)))
        assertEquals("1/4", formatTabletFraction(DoseInstruction.TabletFraction(1, 4)))
        assertEquals("3/4", formatTabletFraction(DoseInstruction.TabletFraction(3, 4)))
    }

    @Test
    fun patch_off_without_medicine_still_uses_application_type_as_title() {
        assertTrue(
            shouldUseApplicationTypeAsMedicationEntryTitle(
                hasMedicine = false,
                applicationType = MedicationApplicationType.PATCH_OFF,
            ),
        )
    }

    @Test
    fun unresolved_non_patch_off_entry_omits_application_type_from_supporting_text() {
        assertFalse(
            shouldIncludeApplicationTypeInSupportingText(
                hasMedicine = false,
                applicationType = MedicationApplicationType.ORAL,
            ),
        )
    }

    @Test
    fun resolved_oral_entry_includes_application_type_in_supporting_text() {
        assertTrue(
            shouldIncludeApplicationTypeInSupportingText(
                hasMedicine = true,
                applicationType = MedicationApplicationType.ORAL,
            ),
        )
    }
}
