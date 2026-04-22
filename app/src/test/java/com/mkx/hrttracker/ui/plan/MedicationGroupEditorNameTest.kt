package com.mkx.hrttracker.ui.plan

import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationGroupEditorNameTest {
    @Test
    fun defaultMedicationGroupName_uses_existing_group_count_plus_one() {
        val defaultName = defaultMedicationGroupName(
            existingGroupCount = 4
        ) { index ->
            "Group $index"
        }

        assertEquals("Group 5", defaultName)
    }

    @Test
    fun resolveMedicationGroupName_uses_default_for_blank_new_group_name() {
        assertEquals(
            "Group 1",
            resolveMedicationGroupName(
                groupName = "   ",
                defaultGroupName = "Group 1",
                isEditing = false
            )
        )
    }

    @Test
    fun resolveMedicationGroupName_keeps_blank_edit_name_invalid() {
        assertEquals(
            "",
            resolveMedicationGroupName(
                groupName = "   ",
                defaultGroupName = "Group 1",
                isEditing = true
            )
        )
    }
}
