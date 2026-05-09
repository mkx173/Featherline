package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationGroupEditorColorTest {
    @Test
    fun resolveMedicationGroupColorKey_assigns_next_available_color_for_new_group() {
        val colorKey = resolveMedicationGroupColorKey(
            currentColorKey = MedicationGroupColorKey.ROSE,
            usedColors = listOf(
                MedicationGroupColorKey.ROSE,
                MedicationGroupColorKey.PLUM
            ),
            seed = 42,
            isEditing = false,
            hasAssignedColor = false
        )

        assertEquals(MedicationGroupColorKey.CORAL, colorKey)
    }

    @Test
    fun resolveMedicationGroupColorKey_keeps_assigned_new_group_color_stable() {
        val colorKey = resolveMedicationGroupColorKey(
            currentColorKey = MedicationGroupColorKey.TEAL,
            usedColors = MedicationGroupColorKey.entries,
            seed = 42,
            isEditing = false,
            hasAssignedColor = true
        )

        assertEquals(MedicationGroupColorKey.TEAL, colorKey)
    }

    @Test
    fun resolveMedicationGroupColorKey_keeps_existing_group_color_when_editing() {
        val colorKey = resolveMedicationGroupColorKey(
            currentColorKey = MedicationGroupColorKey.CORAL,
            usedColors = listOf(MedicationGroupColorKey.ROSE),
            seed = 42,
            isEditing = true,
            hasAssignedColor = false
        )

        assertEquals(MedicationGroupColorKey.CORAL, colorKey)
    }
}
