package com.mkx.hrttracker.ui.medication

import com.mkx.hrttracker.model.medication.MedicationApplicationType
import org.junit.Assert.assertEquals
import org.junit.Test

class StructuredMedicationEditorSheetTest {
    @Test
    fun resolve_connected_button_selection_falls_back_to_first_option() {
        val resolved = resolveConnectedButtonSelection(
            options = listOf(MedicationApplicationType.ORAL),
            selectedOption = MedicationApplicationType.INJECTION,
        )

        assertEquals(MedicationApplicationType.ORAL, resolved)
    }
}
