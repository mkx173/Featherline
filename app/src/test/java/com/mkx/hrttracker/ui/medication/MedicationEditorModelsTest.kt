package com.mkx.hrttracker.ui.medication

import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDoseAssistPreset
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
        assertEquals(MedicationDoseKind.MG_AS_MEDICINE, draft.doseKind)
        assertEquals("", draft.customMedicationName)
    }

    @Test
    fun changing_application_type_resets_to_first_available_dose_kind() {
        val draft = defaultMedicationDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.GEL
        ).changeDoseKind(
            MedicationDoseKind.GEL_PERCENT_AND_WEIGHT
        ).changeApplicationType(
            MedicationApplicationType.PATCH_ON
        )

        assertEquals(MedicationApplicationType.PATCH_ON, draft.applicationType)
        assertEquals(MedicationDoseKind.PATCH_TOTAL_MG, draft.doseKind)
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

    @Test
    fun from_details_trims_trailing_zero_for_single_decimal_input() {
        val draft = medicationDraftFromDetails(
            details = defaultMedicationDraft()
                .copy(doseMg = "12.5")
                .toMedicationDetails()
        )

        assertEquals("12.5", draft.doseMg)
    }

    @Test
    fun from_details_preserves_meaningful_decimal_precision() {
        val draft = medicationDraftFromDetails(
            details = defaultMedicationDraft(
                category = MedicationCategory.ESTRADIOL,
                applicationType = MedicationApplicationType.GEL
            ).changeDoseKind(
                MedicationDoseKind.GEL_PERCENT_AND_WEIGHT
            ).copy(
                gelPercent = "0.06",
                gelWeightGrams = "6.25"
            ).toMedicationDetails()
        )

        assertEquals("0.06", draft.gelPercent)
        assertEquals("6.25", draft.gelWeightGrams)
    }

    @Test
    fun estradiol_oral_catalog_exposes_quick_dose_presets() {
        val presets = defaultMedicationDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL
        ).changeMedicationKey(
            MedicationKey.ESTRADIOL
        ).activeDoseAssistPresets()

        assertEquals(
            listOf(
                MedicationDoseAssistPreset.MgAsMedicine("1"),
                MedicationDoseAssistPreset.MgAsMedicine("2"),
                MedicationDoseAssistPreset.MgAsMedicine("3"),
            ),
            presets
        )
    }

    @Test
    fun antiandrogen_catalog_exposes_requested_quick_dose_presets() {
        val spiroPresets = defaultMedicationDraft(
            category = MedicationCategory.ANTIANDROGEN,
            applicationType = MedicationApplicationType.ORAL
        ).changeMedicationKey(
            MedicationKey.SPIRONOLACTONE
        ).activeDoseAssistPresets()
        val cpaPresets = defaultMedicationDraft(
            category = MedicationCategory.ANTIANDROGEN,
            applicationType = MedicationApplicationType.ORAL
        ).changeMedicationKey(
            MedicationKey.CYPROTERONE_ACETATE
        ).activeDoseAssistPresets()
        val bicaPresets = defaultMedicationDraft(
            category = MedicationCategory.ANTIANDROGEN,
            applicationType = MedicationApplicationType.ORAL
        ).changeMedicationKey(
            MedicationKey.BICALUTAMIDE
        ).activeDoseAssistPresets()

        assertEquals(
            listOf(
                MedicationDoseAssistPreset.MgAsMedicine("100"),
                MedicationDoseAssistPreset.MgAsMedicine("200"),
            ),
            spiroPresets
        )
        assertEquals(
            listOf(
                MedicationDoseAssistPreset.MgAsMedicine("6.25"),
                MedicationDoseAssistPreset.MgAsMedicine("12.5"),
                MedicationDoseAssistPreset.MgAsMedicine("25"),
            ),
            cpaPresets
        )
        assertEquals(
            listOf(
                MedicationDoseAssistPreset.MgAsMedicine("25"),
                MedicationDoseAssistPreset.MgAsMedicine("50"),
            ),
            bicaPresets
        )
    }

    @Test
    fun patch_catalog_exposes_presets_for_each_patch_dose_kind() {
        val releaseRatePresets = defaultMedicationDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.PATCH_ON
        ).activeDoseAssistPresets()
        val totalMgPresets = defaultMedicationDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.PATCH_ON
        ).changeDoseKind(
            MedicationDoseKind.PATCH_TOTAL_MG
        ).activeDoseAssistPresets()

        assertEquals(
            listOf(
                MedicationDoseAssistPreset.PatchReleaseRateMcgPerDay("50"),
                MedicationDoseAssistPreset.PatchReleaseRateMcgPerDay("75"),
                MedicationDoseAssistPreset.PatchReleaseRateMcgPerDay("100"),
            ),
            releaseRatePresets
        )
        assertEquals(
            listOf(
                MedicationDoseAssistPreset.PatchTotalMg("0.36"),
                MedicationDoseAssistPreset.PatchTotalMg("0.72"),
            ),
            totalMgPresets
        )
    }

    @Test
    fun applying_gel_quick_dose_presets_updates_individual_fields() {
        val base = defaultMedicationDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.GEL
        ).changeDoseKind(MedicationDoseKind.GEL_PERCENT_AND_WEIGHT)

        val withPercent = base.applyDoseAssistPreset(
            MedicationDoseAssistPreset.GelPercent("0.3")
        )
        val withWeight = withPercent.applyDoseAssistPreset(
            MedicationDoseAssistPreset.GelWeightGrams("2.5")
        )

        assertEquals("0.3", withPercent.gelPercent)
        assertEquals("", withPercent.gelWeightGrams)
        assertEquals("2.5", withWeight.gelWeightGrams)
    }
}
