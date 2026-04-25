package com.mkx.hrttracker.ui.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

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

    @Test
    fun applyDefaultGroupNameToEditorState_sets_initial_name_for_new_group() {
        val updated = applyDefaultGroupNameToEditorState(
            currentState = MedicationGroupEditorUiState(),
            defaultGroupName = "Group 3"
        )

        assertEquals("Group 3", updated.groupName)
        assertEquals("Group 3", updated.defaultGroupName)
    }

    @Test
    fun applyDefaultGroupNameToEditorState_does_not_override_after_user_edit() {
        val updated = applyDefaultGroupNameToEditorState(
            currentState = MedicationGroupEditorUiState(
                groupName = "",
                defaultGroupName = "Group 2",
                hasResolvedInitialGroupName = true
            ),
            defaultGroupName = "Group 3"
        )

        assertEquals("", updated.groupName)
        assertEquals("Group 3", updated.defaultGroupName)
    }

    @Test
    fun shouldShowGroupNameClearAction_returns_true_for_non_blank_name() {
        assertTrue(shouldShowGroupNameClearAction("Morning meds"))
    }

    @Test
    fun shouldShowGroupNameClearAction_returns_false_for_blank_name() {
        assertFalse(shouldShowGroupNameClearAction("   "))
    }

    @Test
    fun medicationGroupScheduleDateFormatter_appends_weekday_after_date() {
        assertEquals(
            "Apr 25, 2026 Sat",
            LocalDate.of(2026, 4, 25).format(medicationGroupScheduleDateFormatter(Locale.US))
        )
    }
}
