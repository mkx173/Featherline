package com.mkx.hrttracker.ui.components

import com.mkx.hrttracker.model.medication.MedicationApplicationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectedButtonGroupTest {
    @Test
    fun resolve_connected_button_selection_falls_back_to_first_option() {
        val resolved = resolveConnectedButtonSelection(
            options = listOf(MedicationApplicationType.ORAL),
            selectedOption = MedicationApplicationType.INJECTION,
        )

        assertEquals(MedicationApplicationType.ORAL, resolved)
    }

    @Test
    fun button_has_start_icon_when_icons_or_leading_content_are_present() {
        assertFalse(
            connectedButtonGroupButtonHasStartIcon(
                optionIconCount = 0,
                hasLeadingContent = false,
            ),
        )
        assertTrue(
            connectedButtonGroupButtonHasStartIcon(
                optionIconCount = 1,
                hasLeadingContent = false,
            ),
        )
        assertTrue(
            connectedButtonGroupButtonHasStartIcon(
                optionIconCount = 0,
                hasLeadingContent = true,
            ),
        )
    }
}
