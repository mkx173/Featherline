package com.mkx.hrttracker.ui.medication

import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class MedicationEditorSheetsTest {

    // Fix 1 (Task 6-7A review): existing-medicine selection must keep the dose
    // form visible for routes whose dose is per-instruction (multi-use vial,
    // gel container, pill). Without this, picking an existing INJECTION_MULTI_-
    // USE_VIAL hides the volume field and `DoseInstructionDraftUiState.validation
    // ErrorRes()` returns `validation_dose_volume_required` with no UI to fix it.
    @Test
    fun existing_multi_use_vial_selection_keeps_dose_volume_form_visible() {
        // A picker draft pointing at an existing multi-use-vial medicine: the
        // dose draft is per-instruction (volume in ml) and must remain editable.
        val draft = defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.INJECTION,
        ).changePreparationType(MedicinePreparationType.INJECTION_MULTI_USE_VIAL)
            .copy(selectedMedicineUuid = java.util.UUID.randomUUID())
        val doseDraft = draft.toDoseInstructionDraft()

        assertTrue(
            "INJECTION_MULTI_USE_VIAL is per-instruction (volumeMl)",
            requiresEditableDoseInstructionForm(doseDraft.preparationType),
        )

        // The dose draft starts empty; selecting an existing medicine doesn't
        // populate volumeMl. Validation requires it and there must be UI to
        // enter it — that's the bug Fix 1 patches.
        assertEquals(
            R.string.validation_dose_volume_required,
            doseDraft.validationErrorRes(),
        )
        // Entering volume satisfies validation. The picker also stops blocking
        // the save because an existing selection bypasses the picker's form
        // validation.
        assertNull(doseDraft.copy(volumeMl = "0.5").validationErrorRes())
        assertNull(draft.validationErrorRes())
    }

    @Test
    fun gel_container_route_requires_editable_dose_form_regardless_of_selection() {
        // GEL_CONTAINER carries a per-instruction WeightGrams dose. Editor
        // must surface the weight field for both new-medicine and existing-
        // medicine paths so the user can finish saving.
        val draft = defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.GEL,
        ).changePreparationType(MedicinePreparationType.GEL_CONTAINER)
            .copy(selectedMedicineUuid = java.util.UUID.randomUUID())
        val doseDraft = draft.toDoseInstructionDraft()

        assertTrue(requiresEditableDoseInstructionForm(doseDraft.preparationType))
        assertEquals(
            R.string.validation_dose_weight_required,
            doseDraft.validationErrorRes(),
        )
        assertNull(doseDraft.copy(weightGrams = "1.25").validationErrorRes())
    }

    @Test
    fun routes_with_whole_unit_dose_keep_dose_form_hidden() {
        // Whole-unit routes (PATCH, INJECTION_SINGLE_USE_VIAL, GEL_SACHET) need
        // no dose form — the dose is fully determined by the medicine identity.
        assertFalse(requiresEditableDoseInstructionForm(MedicinePreparationType.PATCH))
        assertFalse(
            requiresEditableDoseInstructionForm(
                MedicinePreparationType.INJECTION_SINGLE_USE_VIAL,
            ),
        )
        assertFalse(requiresEditableDoseInstructionForm(MedicinePreparationType.GEL_SACHET))
    }

    @Test
    fun preparation_type_labels_resolve_for_every_type() {
        com.mkx.hrttracker.model.medication.MedicinePreparationType.entries.forEach { type ->
            assertEquals(
                preparationTypeLabelRes(type),
                preparationTypeLabelRes(type),
            )
        }
    }

    @Test
    fun medicationLogScheduleOffset_selects_localized_label_and_single_largest_unit() {
        val scheduledFor = LocalDateTime.of(2026, 4, 22, 21, 0)

        assertNull(
            medicationLogScheduleOffset(scheduledFor = scheduledFor, appliedAt = scheduledFor),
        )
        assertEquals(
            MedicationLogScheduleOffset(
                labelRes = R.string.medication_editor_schedule_offset_minutes_later,
                value = 20,
            ),
            medicationLogScheduleOffset(
                scheduledFor = scheduledFor,
                appliedAt = scheduledFor.plusMinutes(20),
            ),
        )
        assertEquals(
            MedicationLogScheduleOffset(
                labelRes = R.string.medication_editor_schedule_offset_minutes_earlier,
                value = 59,
            ),
            medicationLogScheduleOffset(
                scheduledFor = scheduledFor,
                appliedAt = scheduledFor.minusMinutes(59),
            ),
        )
        assertEquals(
            MedicationLogScheduleOffset(
                labelRes = R.string.medication_editor_schedule_offset_hours_later,
                value = 1,
            ),
            medicationLogScheduleOffset(
                scheduledFor = scheduledFor,
                appliedAt = scheduledFor.plusMinutes(80),
            ),
        )
        assertEquals(
            MedicationLogScheduleOffset(
                labelRes = R.string.medication_editor_schedule_offset_days_later,
                value = 1,
            ),
            medicationLogScheduleOffset(
                scheduledFor = scheduledFor,
                appliedAt = scheduledFor.plusHours(47),
            ),
        )
        assertEquals(
            MedicationLogScheduleOffset(
                labelRes = R.string.medication_editor_schedule_offset_days_earlier,
                value = 1,
            ),
            medicationLogScheduleOffset(
                scheduledFor = scheduledFor,
                appliedAt = scheduledFor.minusHours(25),
            ),
        )
    }
}
