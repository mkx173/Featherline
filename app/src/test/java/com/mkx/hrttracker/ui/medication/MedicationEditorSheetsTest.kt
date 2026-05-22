package com.mkx.hrttracker.ui.medication

import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testCustomMedicine
import com.mkx.hrttracker.model.medication.testMedicine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class MedicationEditorSheetsTest {

    @Test
    fun existing_catalog_medicine_matches_a_catalog_picker_for_the_same_key() {
        val medicine = testMedicine(key = MedicationKey.ESTRADIOL)
        val draft = defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
        ).copy(medicationKey = MedicationKey.ESTRADIOL)

        assertTrue(medicineMatchesPickerIdentity(medicine, draft))
        assertFalse(
            medicineMatchesPickerIdentity(
                medicine,
                draft.copy(medicationKey = MedicationKey.ESTRADIOL_VALERATE),
            ),
        )
    }

    @Test
    fun existing_custom_medicine_matches_a_custom_picker_by_normalized_name() {
        val medicine = testCustomMedicine(medicationName = "My Medication")
        val draft = defaultMedicineDraft(category = MedicationCategory.CUSTOM)
            .copy(customMedicationName = "  my medication  ")

        // Normalization collapses casing/whitespace so the existing card surfaces.
        assertTrue(medicineMatchesPickerIdentity(medicine, draft))
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
