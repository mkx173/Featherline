package com.mkx.hrttracker.ui.medication

import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDoseKind
import androidx.compose.ui.text.input.ImeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StructuredMedicationEditorSheetTest {
    @Test
    fun resolve_medication_editor_field_errors_marks_multiple_invalid_fields() {
        val draft = defaultMedicationDraft(category = MedicationCategory.CUSTOM)
        val fieldErrors = resolveMedicationEditorFieldErrors(
            draft = draft,
            errorMessageRes = draft.validationErrorRes()
        )

        assertEquals(R.string.validation_name_required, fieldErrors.customName)
        assertEquals(R.string.validation_dose_required, fieldErrors.doseMg)
    }

    @Test
    fun resolve_medication_editor_field_errors_marks_both_gel_fields() {
        val draft = defaultMedicationDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.GEL
        ).changeDoseKind(
            com.mkx.hrttracker.model.medication.MedicationDoseKind.GEL_PERCENT_AND_WEIGHT
        )
        val fieldErrors = resolveMedicationEditorFieldErrors(
            draft = draft,
            errorMessageRes = draft.validationErrorRes()
        )

        assertEquals(R.string.validation_gel_percent_required, fieldErrors.gelPercent)
        assertEquals(R.string.validation_gel_weight_required, fieldErrors.gelWeight)
    }

    @Test
    fun resolve_medication_editor_field_errors_ignores_unmapped_error() {
        val draft = defaultMedicationDraft().copy(doseMg = "2")
        val fieldErrors = resolveMedicationEditorFieldErrors(
            draft = draft,
            R.string.validation_medication_selection_required
        )

        assertNull(fieldErrors.customName)
        assertNull(fieldErrors.doseMg)
        assertNull(fieldErrors.gelPercent)
        assertNull(fieldErrors.gelWeight)
        assertNull(fieldErrors.patchReleaseRate)
    }

    @Test
    fun resolve_medication_editor_field_errors_stays_clear_before_validation() {
        val draft = defaultMedicationDraft(category = MedicationCategory.CUSTOM)
        val fieldErrors = resolveMedicationEditorFieldErrors(
            draft = draft,
            errorMessageRes = null
        )

        assertNull(fieldErrors.customName)
        assertNull(fieldErrors.doseMg)
        assertNull(fieldErrors.gelPercent)
        assertNull(fieldErrors.gelWeight)
        assertNull(fieldErrors.patchReleaseRate)
    }

    @Test
    fun doseFieldPainterRes_uses_injection_icon_for_mg_dose_fields() {
        assertEquals(
            R.drawable.ic_vaccines,
            doseFieldPainterRes(
                applicationType = MedicationApplicationType.INJECTION,
                doseKind = MedicationDoseKind.MG_AS_MEDICINE
            )
        )
        assertEquals(
            R.drawable.ic_medication,
            doseFieldPainterRes(
                applicationType = MedicationApplicationType.ORAL,
                doseKind = MedicationDoseKind.MG_AS_MEDICINE
            )
        )
        assertEquals(
            R.drawable.ic_medication,
            doseFieldPainterRes(
                applicationType = MedicationApplicationType.SUBLINGUAL,
                doseKind = MedicationDoseKind.MG_AS_MEDICINE
            )
        )
        assertEquals(
            R.drawable.ic_remove_selection,
            doseFieldPainterRes(
                applicationType = MedicationApplicationType.PATCH_OFF,
                doseKind = MedicationDoseKind.NONE
            )
        )
    }

    @Test
    fun medicationApplicationButtonIconRes_uses_requested_icons() {
        assertEquals(
            R.drawable.ic_pill,
            medicationApplicationButtonIconRes(MedicationApplicationType.ORAL)
        )
        assertEquals(
            R.drawable.ic_pill_alt,
            medicationApplicationButtonIconRes(MedicationApplicationType.SUBLINGUAL)
        )
        assertEquals(
            R.drawable.ic_syringe,
            medicationApplicationButtonIconRes(MedicationApplicationType.INJECTION)
        )
        assertEquals(
            R.drawable.ic_water_drops,
            medicationApplicationButtonIconRes(MedicationApplicationType.GEL)
        )
        assertEquals(
            R.drawable.ic_sticker_add,
            medicationApplicationButtonIconRes(MedicationApplicationType.PATCH_ON)
        )
        assertEquals(
            R.drawable.ic_tab_close_inactive,
            medicationApplicationButtonIconRes(MedicationApplicationType.PATCH_OFF)
        )
    }

    @Test
    fun resolveDoseTextFieldValue_uses_placeholder_as_display_text_when_disabled() {
        assertEquals(
            "Patch removal has no dose fields.",
            resolveDoseTextFieldValue(
                value = "",
                placeholder = "Patch removal has no dose fields.",
                enabled = false
            )
        )
        assertEquals(
            null,
            resolveDoseTextFieldPlaceholder(
                value = "",
                placeholder = "Patch removal has no dose fields.",
                enabled = false
            )
        )
    }

    @Test
    fun structuredMedicationEditorEditableFields_singleDoseField_usesDoneImeAction() {
        val draft = defaultMedicationDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL
        )

        val fields = structuredMedicationEditorEditableFields(draft)

        assertEquals(
            listOf(StructuredMedicationEditorTextField.DOSE_MG),
            fields
        )
        assertEquals(
            ImeAction.Done,
            structuredMedicationEditorImeAction(
                editableFields = fields,
                field = StructuredMedicationEditorTextField.DOSE_MG
            )
        )
    }

    @Test
    fun structuredMedicationEditorEditableFields_multiDoseField_usesNextThenDoneImeAction() {
        val draft = defaultMedicationDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.GEL
        ).changeDoseKind(MedicationDoseKind.GEL_PERCENT_AND_WEIGHT)

        val fields = structuredMedicationEditorEditableFields(draft)

        assertEquals(
            listOf(
                StructuredMedicationEditorTextField.GEL_PERCENT,
                StructuredMedicationEditorTextField.GEL_WEIGHT,
            ),
            fields
        )
        assertEquals(
            ImeAction.Next,
            structuredMedicationEditorImeAction(
                editableFields = fields,
                field = StructuredMedicationEditorTextField.GEL_PERCENT
            )
        )
        assertEquals(
            ImeAction.Done,
            structuredMedicationEditorImeAction(
                editableFields = fields,
                field = StructuredMedicationEditorTextField.GEL_WEIGHT
            )
        )
    }

    @Test
    fun structuredMedicationEditorEditableFields_customNameFlowsIntoDoseField() {
        val draft = defaultMedicationDraft(category = MedicationCategory.CUSTOM)

        val fields = structuredMedicationEditorEditableFields(draft)

        assertEquals(
            listOf(
                StructuredMedicationEditorTextField.CUSTOM_NAME,
                StructuredMedicationEditorTextField.DOSE_MG,
            ),
            fields
        )
        assertEquals(
            ImeAction.Next,
            structuredMedicationEditorImeAction(
                editableFields = fields,
                field = StructuredMedicationEditorTextField.CUSTOM_NAME
            )
        )
        assertEquals(
            ImeAction.Done,
            structuredMedicationEditorImeAction(
                editableFields = fields,
                field = StructuredMedicationEditorTextField.DOSE_MG
            )
        )
    }
}
