package com.mkx.hrttracker.ui.medication

import androidx.compose.ui.text.input.ImeAction
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDoseKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class MedicationEditorSheetsTest {
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
        assertNull(fieldErrors.count)
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
        assertNull(fieldErrors.count)
    }

    @Test
    fun resolve_medication_editor_field_errors_maps_count_validation() {
        val fieldErrors = resolveMedicationEditorFieldErrors(
            draft = defaultMedicationDraft().copy(doseMg = "2"),
            errorMessageRes = R.string.validation_count_required
        )

        assertEquals(R.string.validation_count_required, fieldErrors.count)
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
    fun medicationApplicationIconRes_uses_requested_icons() {
        assertEquals(
            R.drawable.ic_pill,
            medicationApplicationIconRes(MedicationApplicationType.ORAL)
        )
        assertEquals(
            R.drawable.ic_sublingual,
            medicationApplicationIconRes(MedicationApplicationType.SUBLINGUAL)
        )
        assertEquals(
            R.drawable.ic_syringe,
            medicationApplicationIconRes(MedicationApplicationType.INJECTION)
        )
        assertEquals(
            R.drawable.ic_water_drops,
            medicationApplicationIconRes(MedicationApplicationType.GEL)
        )
        assertEquals(
            R.drawable.ic_sticker_add,
            medicationApplicationIconRes(MedicationApplicationType.PATCH_ON)
        )
        assertEquals(
            R.drawable.ic_tab_close_inactive,
            medicationApplicationIconRes(MedicationApplicationType.PATCH_OFF)
        )
    }

    @Test
    fun medicationApplicationOutlinedIconRes_uses_requested_alt_icons() {
        assertEquals(
            R.drawable.ic_pill_alt,
            medicationApplicationOutlinedIconRes(MedicationApplicationType.ORAL)
        )
        assertEquals(
            R.drawable.ic_sublingual_alt,
            medicationApplicationOutlinedIconRes(MedicationApplicationType.SUBLINGUAL)
        )
        assertEquals(
            R.drawable.ic_syringe_alt,
            medicationApplicationOutlinedIconRes(MedicationApplicationType.INJECTION)
        )
        assertEquals(
            R.drawable.ic_water_drops_alt,
            medicationApplicationOutlinedIconRes(MedicationApplicationType.GEL)
        )
        assertEquals(
            R.drawable.ic_sticker_add_alt,
            medicationApplicationOutlinedIconRes(MedicationApplicationType.PATCH_ON)
        )
        assertEquals(
            R.drawable.ic_tab_close_inactive_alt,
            medicationApplicationOutlinedIconRes(MedicationApplicationType.PATCH_OFF)
        )
    }

    @Test
    fun medicationLogScheduleOffset_selects_localized_label_and_single_largest_unit() {
        val scheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0)

        assertNull(medicationLogScheduleOffset(scheduledFor = scheduledFor, appliedAt = scheduledFor))
        assertEquals(
            MedicationLogScheduleOffset(
                labelRes = R.string.medication_editor_schedule_offset_minutes_later,
                value = 20
            ),
            medicationLogScheduleOffset(
                scheduledFor = scheduledFor,
                appliedAt = scheduledFor.plusMinutes(20)
            )
        )
        assertEquals(
            MedicationLogScheduleOffset(
                labelRes = R.string.medication_editor_schedule_offset_minutes_earlier,
                value = 5
            ),
            medicationLogScheduleOffset(
                scheduledFor = scheduledFor,
                appliedAt = scheduledFor.minusMinutes(5)
            )
        )
        assertEquals(
            MedicationLogScheduleOffset(
                labelRes = R.string.medication_editor_schedule_offset_hours_later,
                value = 1
            ),
            medicationLogScheduleOffset(
                scheduledFor = scheduledFor,
                appliedAt = scheduledFor.plusMinutes(80)
            )
        )
        assertEquals(
            MedicationLogScheduleOffset(
                labelRes = R.string.medication_editor_schedule_offset_hours_earlier,
                value = 2
            ),
            medicationLogScheduleOffset(
                scheduledFor = scheduledFor,
                appliedAt = scheduledFor.minusMinutes(130)
            )
        )
        assertEquals(
            MedicationLogScheduleOffset(
                labelRes = R.string.medication_editor_schedule_offset_days_later,
                value = 1
            ),
            medicationLogScheduleOffset(
                scheduledFor = scheduledFor,
                appliedAt = scheduledFor.plusHours(47)
            )
        )
        assertEquals(
            MedicationLogScheduleOffset(
                labelRes = R.string.medication_editor_schedule_offset_days_earlier,
                value = 1
            ),
            medicationLogScheduleOffset(
                scheduledFor = scheduledFor,
                appliedAt = scheduledFor.minusHours(25)
            )
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
