package com.mkx.hrttracker.ui.medication

import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicinePreparationForm
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.testMedicine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class MedicationDoseDraftReducerTest {

    private fun draftFor(
        category: MedicationCategory,
        applicationType: MedicationApplicationType,
    ): MedicationDoseDraft {
        val picker = defaultMedicineDraft(category = category, applicationType = applicationType)
        return MedicationDoseDraft(
            medicineDraft = picker,
            doseInstructionDraft = picker.toDoseInstructionDraft(),
            countText = "1",
        )
    }

    // WHY: changing the preparation type makes the prior dose instruction
    // meaningless (a vial volume is nonsense for a pill), so the draft must be
    // rebuilt — otherwise the user can save a dose instruction that doesn't
    // match the medicine. Mirrors NewMedicineSlotViewModel/MedicationLogEntryViewModel.
    @Test
    fun preparation_change_resets_dose_draft() {
        val pill = draftFor(MedicationCategory.ESTRADIOL, MedicationApplicationType.ORAL)
        val start = pill.copy(
            doseInstructionDraft = pill.doseInstructionDraft.copy(
                tabletFractionNumerator = 1,
                tabletFractionDenominator = 2,
            ),
            countText = "3",
        )

        val result = start.applyMedicinePicker(transform = {
            it.changeForm(MedicinePreparationForm.INJECTION)
        })

        assertEquals(
            result.medicineDraft.toDoseInstructionDraft(),
            result.doseInstructionDraft,
        )
        assertEquals("1", result.countText)
    }

    // WHY: a non-preparation edit (e.g. category tweak that keeps the same prep
    // type) must not wipe the user's in-progress dose instruction.
    @Test
    fun non_preparation_edit_keeps_dose_draft() {
        val start = draftFor(MedicationCategory.ESTRADIOL, MedicationApplicationType.ORAL)
        val customDose = start.doseInstructionDraft.copy(
            tabletFractionNumerator = 1,
            tabletFractionDenominator = 2,
        )
        val withDose = start.copy(doseInstructionDraft = customDose, countText = "2")

        val result = withDose.applyMedicinePicker(transform = {
            it.copy(customMedicationName = "Draft label")
        })
        val expectedDose = customDose.copy(
            preparationType = result.medicineDraft.inferredOrSelectedPreparationType()
                ?: customDose.preparationType,
        )

        assertEquals(expectedDose, result.doseInstructionDraft)
        assertEquals("2", result.countText)
    }

    // WHY: the existing-medicine flow (ExistingMedicineDoseSheet) locks identity;
    // prep type should stay stable and the resolved medicine must remain attached.
    @Test
    fun resolved_medicine_with_stable_prep_does_not_reset_dose_draft() {
        val medicine = testMedicine()
        val picker = medicineDraftFromMedicine(medicine, MedicationApplicationType.ORAL)
        val customDose = picker.toDoseInstructionDraft().copy(
            tabletFractionNumerator = 1,
            tabletFractionDenominator = 2,
        )
        val start = MedicationDoseDraft(
            medicineDraft = picker,
            doseInstructionDraft = customDose,
            countText = "1",
            resolvedMedicine = medicine,
        )

        // A same-preparation metadata edit keeps the resolved medicine attached.
        val result = start.applyMedicinePicker(transform = {
            it.copy(displayName = "Display label")
        })

        assertEquals(customDose.preparationType, result.doseInstructionDraft.preparationType)
        assertEquals(customDose, result.doseInstructionDraft)
        assertSame(medicine, result.resolvedMedicine)
    }

    // WHY: ExistingMedicineDoseSheet currently has no reset branch at all. The
    // explicit KEEP_EXISTING_DOSE policy must not rebuild the dose draft even if a
    // defensive test transform changes preparation type.
    @Test
    fun keep_existing_dose_policy_does_not_rebuild_on_preparation_change() {
        val medicine = testMedicine()
        val picker = medicineDraftFromMedicine(medicine, MedicationApplicationType.ORAL)
        val customDose = picker.toDoseInstructionDraft().copy(
            tabletFractionNumerator = 1,
            tabletFractionDenominator = 2,
        )
        val start = MedicationDoseDraft(
            medicineDraft = picker,
            doseInstructionDraft = customDose,
            countText = "1",
            resolvedMedicine = medicine,
        )

        val result = start.applyMedicinePicker(
            transform = { draft ->
                draft.changeForm(MedicinePreparationForm.INJECTION)
                    .copy(selectedMedicineUuid = draft.selectedMedicineUuid)
            },
            resetPolicy = MedicationDoseResetPolicy.KEEP_EXISTING_DOSE,
        )

        assertEquals(
            MedicinePreparationType.INJECTION_SINGLE_USE_VIAL,
            result.doseInstructionDraft.preparationType,
        )
        assertEquals(1, result.doseInstructionDraft.tabletFractionNumerator)
        assertEquals(2, result.doseInstructionDraft.tabletFractionDenominator)
        assertNotEquals(
            result.medicineDraft.toDoseInstructionDraft(),
            result.doseInstructionDraft,
        )
        assertSame(medicine, result.resolvedMedicine)
    }

    // WHY: clearing the catalog selection means the resolved medicine is no
    // longer valid; keeping it would let the user save against a stale identity.
    @Test
    fun clears_resolved_medicine_when_selection_removed() {
        val medicine = testMedicine()
        val picker = medicineDraftFromMedicine(medicine, MedicationApplicationType.ORAL)
        val start = MedicationDoseDraft(
            medicineDraft = picker,
            doseInstructionDraft = picker.toDoseInstructionDraft(),
            countText = "1",
            resolvedMedicine = medicine,
        )

        val result = start.applyMedicinePicker(transform = {
            it.copy(selectedMedicineUuid = null)
        })

        assertNull(result.resolvedMedicine)
    }

    // WHY: count edits clear any stale validation error so the user isn't shown
    // a "count required" message while actively fixing the count.
    @Test
    fun with_count_text_sanitizes_count_and_clears_error() {
        val start = draftFor(MedicationCategory.ESTRADIOL, MedicationApplicationType.ORAL)
            .copy(errorMessageRes = R.string.validation_count_required)

        val result = start.withCountText("2x")

        assertEquals("2", result.countText)
        assertNull(result.errorMessageRes)
    }

    // WHY: the create-then-dose flow has a create-specific first validation step
    // in ui/catalog (including PATCH_OFF handling). The reducer must call the
    // supplied validator first instead of hard-coding a weaker validation.
    @Test
    fun validated_with_uses_supplied_medicine_validator_first() {
        val base = draftFor(MedicationCategory.ESTRADIOL, MedicationApplicationType.ORAL)
        val start = base.copy(
            doseInstructionDraft = base.doseInstructionDraft.copy(tabletFractionNumerator = 0),
        )

        val result = start.validatedWith(
            preparationType = start.doseInstructionDraft.preparationType,
            validateMedicineDraft = { R.string.validation_preparation_type_required },
        )

        assertEquals(R.string.validation_preparation_type_required, result.errorMessageRes)
    }

    // WHY: the log/existing-medicine target validates "a medicine is selected"
    // first (not the create form), matching MedicationLogEntry/GroupEditor/ExistingMedicine.
    @Test
    fun validated_with_can_use_selected_medicine_validation_for_log() {
        val start = draftFor(MedicationCategory.ESTRADIOL, MedicationApplicationType.ORAL)

        val result = start.validatedWith(
            preparationType = start.doseInstructionDraft.preparationType,
            validateMedicineDraft = { it.selectedMedicineValidationErrorRes() },
        )

        assertEquals(start.medicineDraft.selectedMedicineValidationErrorRes(), result.errorMessageRes)
    }
}
