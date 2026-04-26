package com.mkx.hrttracker.ui.medication

import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationDoseAssistPreset
import com.mkx.hrttracker.model.medication.MedicationDoseKind
import com.mkx.hrttracker.model.medication.MedicationDoseUnit
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
    fun medication_count_editor_is_only_available_for_supported_routes() {
        assertEquals(
            true,
            defaultMedicationDraft(
                category = MedicationCategory.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL
            ).showsMedicationCountEditor()
        )
        assertEquals(
            true,
            defaultMedicationDraft(
                category = MedicationCategory.ESTRADIOL,
                applicationType = MedicationApplicationType.SUBLINGUAL
            ).showsMedicationCountEditor()
        )
        assertEquals(
            true,
            defaultMedicationDraft(
                category = MedicationCategory.ESTRADIOL,
                applicationType = MedicationApplicationType.PATCH_ON
            ).showsMedicationCountEditor()
        )
        assertEquals(
            false,
            defaultMedicationDraft(
                category = MedicationCategory.ESTRADIOL,
                applicationType = MedicationApplicationType.GEL
            ).showsMedicationCountEditor()
        )
    }

    @Test
    fun resolving_medication_count_resets_when_category_changes() {
        val previousDraft = defaultMedicationDraft(
            category = MedicationCategory.ANTIANDROGEN,
            applicationType = MedicationApplicationType.ORAL
        )
        val updatedDraft = previousDraft.changeCategory(MedicationCategory.ESTRADIOL)

        assertEquals(
            1,
            resolveMedicationCountAfterDraftChange(
                previousDraft = previousDraft,
                updatedDraft = updatedDraft,
                currentCount = 2
            )
        )
    }

    @Test
    fun resolving_medication_count_resets_when_application_type_changes() {
        val previousDraft = defaultMedicationDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL
        )
        val updatedDraft = previousDraft.changeApplicationType(MedicationApplicationType.SUBLINGUAL)

        assertEquals(
            1,
            resolveMedicationCountAfterDraftChange(
                previousDraft = previousDraft,
                updatedDraft = updatedDraft,
                currentCount = 3
            )
        )
    }

    @Test
    fun resolving_medication_count_preserves_when_medication_changes_with_same_context() {
        val previousDraft = defaultMedicationDraft(
            category = MedicationCategory.ANTIANDROGEN,
            applicationType = MedicationApplicationType.ORAL
        )
        val updatedDraft = previousDraft.changeMedicationKey(MedicationKey.CYPROTERONE_ACETATE)

        assertEquals(
            2,
            resolveMedicationCountAfterDraftChange(
                previousDraft = previousDraft,
                updatedDraft = updatedDraft,
                currentCount = 2
            )
        )
    }

    @Test
    fun resolving_medication_count_text_preserves_user_input_when_context_is_unchanged() {
        val previousDraft = defaultMedicationDraft(
            category = MedicationCategory.ANTIANDROGEN,
            applicationType = MedicationApplicationType.ORAL
        )
        val updatedDraft = previousDraft.changeMedicationKey(MedicationKey.CYPROTERONE_ACETATE)

        assertEquals(
            "0",
            resolveMedicationCountTextAfterDraftChange(
                previousDraft = previousDraft,
                updatedDraft = updatedDraft,
                currentCountText = "0"
            )
        )
    }

    @Test
    fun stepping_medication_count_treats_typed_zero_as_zero_before_increment() {
        assertEquals(
            1,
            stepMedicationCount(
                applicationType = MedicationApplicationType.ORAL,
                countText = "0",
                delta = 1
            )
        )
    }

    @Test
    fun medication_count_validation_requires_positive_count_for_supported_routes() {
        assertEquals(
            R.string.validation_count_required,
            medicationCountValidationErrorRes(
                applicationType = MedicationApplicationType.ORAL,
                countText = "0"
            )
        )
        assertEquals(
            R.string.validation_count_required,
            medicationCountValidationErrorRes(
                applicationType = MedicationApplicationType.PATCH_ON,
                countText = ""
            )
        )
        assertNull(
            medicationCountValidationErrorRes(
                applicationType = MedicationApplicationType.ORAL,
                countText = "2"
            )
        )
        assertNull(
            medicationCountValidationErrorRes(
                applicationType = MedicationApplicationType.GEL,
                countText = "0"
            )
        )
    }

    @Test
    fun stepping_medication_count_still_normalizes_unsupported_routes_to_one() {
        assertEquals(
            1,
            stepMedicationCount(
                applicationType = MedicationApplicationType.GEL,
                countText = "3",
                delta = 1
            )
        )
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
        assertEquals(MedicationDoseUnit.MG, details.customDoseUnit)
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
    fun custom_mcg_input_is_saved_as_canonical_mg() {
        val details = defaultMedicationDraft(category = MedicationCategory.CUSTOM)
            .copy(
                customMedicationName = "My custom medication",
                customDoseUnit = MedicationDoseUnit.MCG,
                doseMg = "200",
            )
            .toMedicationDetails()

        assertEquals(MedicationDose.MgAsMedicine(0.2), details.dose)
        assertEquals(MedicationDoseUnit.MCG, details.customDoseUnit)
    }

    @Test
    fun from_details_restores_custom_selected_unit_display_value() {
        val draft = medicationDraftFromDetails(
            details = defaultMedicationDraft(category = MedicationCategory.CUSTOM)
                .copy(
                    customMedicationName = "My custom medication",
                    customDoseUnit = MedicationDoseUnit.MCG,
                    doseMg = "200",
                )
                .toMedicationDetails()
        )

        assertEquals(MedicationDoseUnit.MCG, draft.customDoseUnit)
        assertEquals("200", draft.doseMg)
    }

    @Test
    fun changing_custom_dose_unit_keeps_display_value_unchanged() {
        val draft = defaultMedicationDraft(category = MedicationCategory.CUSTOM)
            .copy(
                customMedicationName = "My custom medication",
                customDoseUnit = MedicationDoseUnit.MCG,
                doseMg = "200",
            )
            .changeCustomDoseUnit(MedicationDoseUnit.MG)

        assertEquals(MedicationDoseUnit.MG, draft.customDoseUnit)
        assertEquals("200", draft.doseMg)
    }

    @Test
    fun saving_after_switching_custom_dose_unit_uses_selected_unit_for_canonical_mg() {
        val details = defaultMedicationDraft(category = MedicationCategory.CUSTOM)
            .copy(
                customMedicationName = "My custom medication",
                customDoseUnit = MedicationDoseUnit.MCG,
                doseMg = "200",
            )
            .changeCustomDoseUnit(MedicationDoseUnit.MG)
            .toMedicationDetails()

        assertEquals(MedicationDose.MgAsMedicine(200.0), details.dose)
        assertEquals(MedicationDoseUnit.MG, details.customDoseUnit)
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
    fun gel_catalog_exposes_presets_for_each_gel_dose_kind() {
        val equivalentPresets = defaultMedicationDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.GEL
        ).activeDoseAssistPresets()
        val percentAndWeightPresets = defaultMedicationDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.GEL
        ).changeDoseKind(
            MedicationDoseKind.GEL_PERCENT_AND_WEIGHT
        ).activeDoseAssistPresets()

        assertEquals(
            listOf(
                MedicationDoseAssistPreset.GelEquivalentEstradiolMg("0.75"),
                MedicationDoseAssistPreset.GelEquivalentEstradiolMg("1.5"),
            ),
            equivalentPresets
        )
        assertEquals(
            listOf(
                MedicationDoseAssistPreset.GelPercent("0.06"),
                MedicationDoseAssistPreset.GelPercent("0.3"),
                MedicationDoseAssistPreset.GelPercent("0.6"),
                MedicationDoseAssistPreset.GelWeightGrams("1.25"),
                MedicationDoseAssistPreset.GelWeightGrams("2.5"),
            ),
            percentAndWeightPresets
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

    @Test
    fun applying_gel_equivalent_quick_dose_preset_updates_dose_mg() {
        val updated = defaultMedicationDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.GEL
        ).applyDoseAssistPreset(
            MedicationDoseAssistPreset.GelEquivalentEstradiolMg("1.5")
        )

        assertEquals(MedicationDoseKind.GEL_EQUIVALENT_ESTRADIOL_MG, updated.doseKind)
        assertEquals("1.5", updated.doseMg)
    }

    @Test
    fun antiandrogen_dose_warning_thresholds_are_strictly_greater_than_limit() {
        val spiroAtThreshold = defaultMedicationDraft(
            category = MedicationCategory.ANTIANDROGEN,
            applicationType = MedicationApplicationType.ORAL
        ).changeMedicationKey(
            MedicationKey.SPIRONOLACTONE
        ).copy(doseMg = "200")
        val cpaAboveThreshold = defaultMedicationDraft(
            category = MedicationCategory.ANTIANDROGEN,
            applicationType = MedicationApplicationType.ORAL
        ).changeMedicationKey(
            MedicationKey.CYPROTERONE_ACETATE
        ).copy(doseMg = "12.5")
        val bicaAboveThreshold = defaultMedicationDraft(
            category = MedicationCategory.ANTIANDROGEN,
            applicationType = MedicationApplicationType.ORAL
        ).changeMedicationKey(
            MedicationKey.BICALUTAMIDE
        ).copy(doseMg = "50.1")

        assertEquals(false, spiroAtThreshold.exceedsDoseWarningThreshold())
        assertEquals(true, spiroAtThreshold.exceedsDoseWarningThreshold(count = 2))
        assertEquals(false, cpaAboveThreshold.exceedsDoseWarningThreshold())
        assertEquals(true, cpaAboveThreshold.exceedsDoseWarningThreshold(count = 2))
        assertEquals(true, bicaAboveThreshold.exceedsDoseWarningThreshold())
    }

    @Test
    fun non_matching_medications_do_not_show_dose_warning() {
        val estradiolDraft = defaultMedicationDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL
        ).copy(doseMg = "999")
        val customDraft = defaultMedicationDraft(
            category = MedicationCategory.CUSTOM,
        ).copy(doseMg = "999")

        assertEquals(false, estradiolDraft.exceedsDoseWarningThreshold())
        assertEquals(false, customDraft.exceedsDoseWarningThreshold())
    }
}
