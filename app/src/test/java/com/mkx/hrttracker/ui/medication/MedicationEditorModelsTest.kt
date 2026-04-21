package com.mkx.hrttracker.ui.medication

import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationDoseKind
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MedicationEditorModelsTest {
    @Test
    fun editor_categories_hide_testosterone() {
        assertEquals(
            listOf(
                MedicationCategory.ESTRADIOL,
                MedicationCategory.ANTIANDROGEN,
                MedicationCategory.CUSTOM,
            ),
            editorMedicationCategories(),
        )
    }

    @Test
    fun changing_category_resets_to_catalog_defaults() {
        val draft = defaultMedicationDraft()
            .changeCategory(MedicationCategory.CUSTOM)

        assertEquals(MedicationCategory.CUSTOM, draft.category)
        assertEquals(MedicationApplicationType.ORAL, draft.applicationType)
        assertEquals("", draft.customMedicationName)
    }

    @Test
    fun gel_percent_and_weight_validates_both_fields() {
        val draft = defaultMedicationDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.GEL
        ).changeDoseKind(MedicationDoseKind.GEL_PERCENT_AND_WEIGHT)

        assertEquals(R.string.validation_gel_percent_required, draft.validationErrorRes())

        val withPercent = draft.copy(gelPercent = "0.06")
        assertEquals(R.string.validation_gel_weight_required, withPercent.validationErrorRes())

        val valid = withPercent.copy(gelWeightGrams = "2.5")
        assertNull(valid.validationErrorRes())
    }

    @Test
    fun patch_off_maps_to_none_dose() {
        val details = defaultMedicationDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.PATCH_OFF
        ).toMedicationDetails()

        assertEquals(MedicationDose.None, details.dose)
    }

    @Test
    fun custom_category_uses_custom_name_selection() {
        val details = defaultMedicationDraft(category = MedicationCategory.CUSTOM)
            .copy(customMedicationName = "My custom medication", doseMg = "5")
            .toMedicationDetails()

        assertEquals(MedicationCategory.CUSTOM, details.category)
        assertEquals(
            MedicationSelection.Custom("My custom medication"),
            details.selection
        )
        assertEquals(MedicationDose.MgAsMedicine(5.0), details.dose)
    }

    @Test
    fun unsupported_custom_application_type_coerces_to_oral() {
        val draft = defaultMedicationDraft(
            category = MedicationCategory.CUSTOM,
            applicationType = MedicationApplicationType.PATCH_ON,
        )

        assertEquals(MedicationApplicationType.ORAL, draft.applicationType)
        assertEquals(MedicationDoseKind.MG_AS_MEDICINE, draft.doseKind)
    }

    @Test
    fun antiandrogen_defaults_to_catalog_selection() {
        val draft = defaultMedicationDraft(category = MedicationCategory.ANTIANDROGEN)

        assertEquals(MedicationApplicationType.ORAL, draft.applicationType)
        assertEquals(MedicationKey.SPIRONOLACTONE, draft.medicationKey)
    }

    @Test
    fun from_details_restores_catalog_medication_and_dose() {
        val draft = medicationDraftFromDetails(
            details = defaultMedicationDraft()
                .changeMedicationKey(MedicationKey.ESTRADIOL)
                .copy(doseMg = "2")
                .toMedicationDetails()
        )

        assertEquals(MedicationKey.ESTRADIOL, draft.medicationKey)
        assertEquals("2", draft.doseMg)
    }
}
