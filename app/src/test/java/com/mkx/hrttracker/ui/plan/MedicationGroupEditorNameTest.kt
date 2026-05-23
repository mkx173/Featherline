package com.mkx.hrttracker.ui.plan

import androidx.compose.material3.TextFieldLabelPosition
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.util.medicationGroupScheduleDateFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class MedicationGroupEditorNameTest {
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
    fun applyDefaultGroupNameToEditorState_stores_default_name_as_placeholder_for_new_group() {
        val updated = applyDefaultGroupNameToEditorState(
            currentState = MedicationGroupEditorUiState(),
            defaultGroupName = "Group 3"
        )

        assertEquals("", updated.groupName)
        assertEquals("Group 3", updated.defaultGroupName)
        assertTrue(updated.hasResolvedInitialGroupName)
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
    fun hasSaveableMedicationGroupContent_accepts_blank_new_group_name_with_default_name() {
        assertTrue(
            hasSaveableMedicationGroupContent(
                MedicationGroupEditorUiState(
                    groupName = "   ",
                    defaultGroupName = "Group 1",
                    medications = listOf(
                        MedicationGroupMedicationItemUiState(
                            resolvedMedicine = testMedicine(key = MedicationKey.ESTRADIOL),
                            applicationType = MedicationApplicationType.ORAL,
                            doseInstruction = DoseInstruction.TabletFraction(1, 1),
                        )
                    )
                )
            )
        )
    }

    @Test
    fun hasSaveableMedicationGroupContent_rejects_blank_edit_group_name_even_with_default_name() {
        assertFalse(
            hasSaveableMedicationGroupContent(
                MedicationGroupEditorUiState(
                    editingGroupId = "group-id",
                    groupName = "   ",
                    defaultGroupName = "Group 1",
                    medications = listOf(
                        MedicationGroupMedicationItemUiState(
                            resolvedMedicine = testMedicine(key = MedicationKey.ESTRADIOL),
                            applicationType = MedicationApplicationType.ORAL,
                            doseInstruction = DoseInstruction.TabletFraction(1, 1),
                        )
                    )
                )
            )
        )
    }

    @Test
    fun medicationGroupNamePlaceholder_uses_default_name_for_new_group() {
        assertEquals(
            "Group 3",
            medicationGroupNamePlaceholder(
                defaultGroupName = "Group 3",
                isEditing = false,
            )
        )
    }

    @Test
    fun medicationGroupNamePlaceholder_suppressed_while_editing_existing_group() {
        assertNull(
            medicationGroupNamePlaceholder(
                defaultGroupName = "Morning meds",
                isEditing = true,
            )
        )
    }

    @Test
    fun medicationGroupNameFieldLabelPosition_keepsPlaceholderVisibleWhenEmpty() {
        val position = medicationGroupNameFieldLabelPosition()

        assertTrue(position is TextFieldLabelPosition.Attached)
        assertTrue((position as TextFieldLabelPosition.Attached).alwaysMinimize)
    }

    @Test
    fun medicationGroupScheduleDateFormatter_appends_weekday_after_date() {
        assertEquals(
            "Apr 25 Sat",
            medicationGroupScheduleDateFormatter(
                locale = Locale.US,
                today = LocalDate.of(2026, 4, 28),
            )(LocalDate.of(2026, 4, 25))
        )
    }

    @Test
    fun medicationGroupScheduleDateFormatter_shows_year_for_different_year() {
        assertEquals(
            "Jan 2, 2027 Sat",
            medicationGroupScheduleDateFormatter(
                locale = Locale.US,
                today = LocalDate.of(2026, 4, 28),
            )(LocalDate.of(2027, 1, 2))
        )
    }
}
