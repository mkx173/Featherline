package com.mkx.hrttracker.ui.medication

import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCatalog
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.testMedicine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun no_catalog_supports_both_catalog_selection_and_custom_names() {
        MedicationCategory.entries.forEach { category ->
            MedicationCatalog.applicationTypesFor(category).forEach { applicationType ->
                val draft = defaultMedicineDraft(
                    category = category,
                    applicationType = applicationType,
                )

                assertFalse(
                    "${category.name}/${applicationType.name} supports both catalog and custom",
                    draft.supportsCatalogSelection() && draft.supportsCustomName(),
                )
            }
        }
    }

    // --- Task 6 Step 1: required draft-model tests --------------------------

    @Test
    fun oralCatalogDraftInfersPillPreparationAndTabletFractionDose() {
        val draft = defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
        ).copy(
            medicationKey = MedicationKey.ESTRADIOL,
            pillStrengthMg = "2",
        )
        val doseDraft = draft.toDoseInstructionDraft().copy(
            tabletFractionNumerator = 1,
            tabletFractionDenominator = 2,
        )

        assertEquals(MedicinePreparation.Pill(2.0), draft.toNewMedicineRequest().preparation)
        assertEquals(DoseInstruction.TabletFraction(1, 2), doseDraft.toDoseInstruction())
    }

    @Test
    fun injectionDraftRequiresPreparationTypeWhenRouteIsAmbiguous() {
        val draft = defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.INJECTION,
        )

        assertTrue(draft.requiresPreparationTypeSelection())
        assertEquals(R.string.validation_preparation_type_required, draft.validationErrorRes())
    }

    @Test
    fun patchOffDraftProducesNoopDoseInstruction() {
        val draft = defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.PATCH_OFF,
        )

        assertEquals(DoseInstruction.Noop, draft.toDoseInstructionDraft().toDoseInstruction())
    }

    // --- Preparation-type inference ----------------------------------------

    @Test
    fun oral_route_infers_pill_without_user_selection() {
        val draft = defaultMedicineDraft(applicationType = MedicationApplicationType.ORAL)
        assertEquals(MedicinePreparationType.PILL, draft.inferredOrSelectedPreparationType())
        assertFalse(draft.requiresPreparationTypeSelection())
    }

    @Test
    fun patch_route_infers_patch_preparation() {
        val draft = defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.PATCH_ON,
        )
        assertEquals(MedicinePreparationType.PATCH, draft.inferredOrSelectedPreparationType())
    }

    @Test
    fun patch_total_mg_selection_ignores_release_rate_when_both_fields_are_filled() {
        val preparation = defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.PATCH_ON,
        ).copy(
            patchSpecKind = PatchSpecKind.TOTAL_MG,
            patchTotalMg = "3",
            patchReleaseRateMcgPerDay = "100",
        ).toMedicinePreparation()

        assertEquals(
            MedicinePreparation.Patch(
                MedicinePreparation.PatchSpecification.TotalMg(3.0),
            ),
            preparation,
        )
    }

    @Test
    fun patch_release_rate_selection_ignores_total_mg_when_both_fields_are_filled() {
        val preparation = defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.PATCH_ON,
        ).copy(
            patchSpecKind = PatchSpecKind.RELEASE_RATE,
            patchTotalMg = "3",
            patchReleaseRateMcgPerDay = "100",
        ).toMedicinePreparation()

        assertEquals(
            MedicinePreparation.Patch(
                MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay(100.0),
            ),
            preparation,
        )
    }

    @Test
    fun hydrating_release_rate_patch_selects_release_rate_spec_kind() {
        val medicine = testMedicine(
            preparation = MedicinePreparation.Patch(
                MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay(50.0),
            ),
        )

        val draft = medicineDraftFromMedicine(
            medicine = medicine,
            applicationType = MedicationApplicationType.PATCH_ON,
        )

        assertEquals(PatchSpecKind.RELEASE_RATE, draft.patchSpecKind)
        assertEquals("50", draft.patchReleaseRateMcgPerDay)
    }

    @Test
    fun injection_picks_between_single_and_multi_use_vial() {
        val draft = defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.INJECTION,
        )
        assertEquals(
            listOf(
                MedicinePreparationType.INJECTION_SINGLE_USE_VIAL,
                MedicinePreparationType.INJECTION_MULTI_USE_VIAL,
            ),
            ambiguousPreparationTypes(MedicationApplicationType.INJECTION),
        )
        val withType = draft.changePreparationType(
            MedicinePreparationType.INJECTION_MULTI_USE_VIAL,
        )
        assertEquals(
            MedicinePreparationType.INJECTION_MULTI_USE_VIAL,
            withType.inferredOrSelectedPreparationType(),
        )
    }

    // --- Count handling ----------------------------------------------------

    @Test
    fun medication_count_editor_is_only_available_for_supported_routes() {
        assertTrue(
            defaultMedicineDraft(applicationType = MedicationApplicationType.ORAL)
                .showsMedicationCountEditor(),
        )
        assertTrue(
            defaultMedicineDraft(applicationType = MedicationApplicationType.SUBLINGUAL)
                .showsMedicationCountEditor(),
        )
        assertTrue(
            defaultMedicineDraft(applicationType = MedicationApplicationType.PATCH_ON)
                .showsMedicationCountEditor(),
        )
        assertFalse(
            defaultMedicineDraft(applicationType = MedicationApplicationType.GEL)
                .showsMedicationCountEditor(),
        )
    }

    @Test
    fun resolving_medication_count_text_resets_when_application_type_changes() {
        val previousDraft = defaultMedicineDraft(applicationType = MedicationApplicationType.ORAL)
        val updatedDraft = previousDraft.changeApplicationType(
            MedicationApplicationType.SUBLINGUAL,
        )

        assertEquals(
            "1",
            resolveMedicationCountTextAfterDraftChange(
                previousDraft = previousDraft,
                updatedDraft = updatedDraft,
                currentCountText = "3",
            ),
        )
    }

    @Test
    fun stepping_medication_count_treats_typed_zero_as_zero_before_increment() {
        assertEquals(
            1,
            stepMedicationCount(
                applicationType = MedicationApplicationType.ORAL,
                countText = "0",
                delta = 1,
            ),
        )
    }

    @Test
    fun medication_count_validation_requires_positive_count_for_supported_routes() {
        assertEquals(
            R.string.validation_count_required,
            medicationCountValidationErrorRes(
                applicationType = MedicationApplicationType.ORAL,
                countText = "0",
            ),
        )
        assertNull(
            medicationCountValidationErrorRes(
                applicationType = MedicationApplicationType.ORAL,
                countText = "2",
            ),
        )
        assertNull(
            medicationCountValidationErrorRes(
                applicationType = MedicationApplicationType.GEL,
                countText = "0",
            ),
        )
    }

    // --- Validation --------------------------------------------------------

    @Test
    fun catalog_pill_draft_requires_a_positive_tablet_strength() {
        val draft = defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
        )
        assertEquals(R.string.validation_pill_strength_required, draft.validationErrorRes())
        assertNull(draft.copy(pillStrengthMg = "2").validationErrorRes())
    }

    @Test
    fun custom_draft_requires_a_name() {
        val draft = defaultMedicineDraft(category = MedicationCategory.CUSTOM)
            .copy(pillStrengthMg = "5")
        assertEquals(R.string.validation_name_required, draft.validationErrorRes())
        assertNull(draft.copy(customMedicationName = "My medication").validationErrorRes())
    }

    @Test
    fun selecting_an_existing_medicine_skips_form_validation() {
        val draft = defaultMedicineDraft(applicationType = MedicationApplicationType.ORAL)
            .copy(selectedMedicineUuid = java.util.UUID.randomUUID())
        assertNull(draft.validationErrorRes())
    }

    // --- Conversion --------------------------------------------------------

    @Test
    fun catalog_draft_builds_a_catalog_new_medicine_request() {
        val request = defaultMedicineDraft(
            category = MedicationCategory.ANTIANDROGEN,
            applicationType = MedicationApplicationType.ORAL,
        ).copy(
            medicationKey = MedicationKey.SPIRONOLACTONE,
            pillStrengthMg = "100",
        ).toNewMedicineRequest()

        assertEquals(MedicationSelectionKind.CATALOG, request.selectionKind)
        assertEquals(MedicationKey.SPIRONOLACTONE, request.medicationKey)
        assertEquals(MedicinePreparation.Pill(100.0), request.preparation)
    }

    @Test
    fun custom_draft_builds_a_custom_new_medicine_request() {
        val request = defaultMedicineDraft(category = MedicationCategory.CUSTOM)
            .copy(
                customMedicationName = "My medication",
                pillStrengthMg = "5",
            ).toNewMedicineRequest()

        assertEquals(MedicationSelectionKind.CUSTOM, request.selectionKind)
        assertEquals("My medication", request.customMedicationName)
        assertEquals(MedicinePreparation.Pill(5.0), request.preparation)
    }

    @Test
    fun gel_container_dose_instruction_carries_the_per_dose_weight() {
        val draft = defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.GEL,
        ).changePreparationType(MedicinePreparationType.GEL_CONTAINER)

        val doseDraft = draft.toDoseInstructionDraft().copy(weightGrams = "1.25")
        assertEquals(DoseInstruction.WeightGrams(1.25), doseDraft.toDoseInstruction())
    }

    @Test
    fun tablet_fraction_quarter_selection_writes_one_fourth() {
        val doseDraft = defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
        ).toDoseInstructionDraft()

        assertEquals(
            DoseInstruction.TabletFraction(1, 4),
            doseDraft.selectTabletFraction(TabletFractionOption.QUARTER).toDoseInstruction(),
        )
    }

    @Test
    fun tablet_fraction_whole_selection_writes_one_over_one() {
        val doseDraft = defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
        ).toDoseInstructionDraft()

        assertEquals(
            DoseInstruction.TabletFraction(1, 1),
            doseDraft.selectTabletFraction(TabletFractionOption.WHOLE).toDoseInstruction(),
        )
    }

    @Test
    fun non_canonical_tablet_fraction_remains_round_trippable_until_changed() {
        val doseDraft = DoseInstructionDraftUiState(
            applicationType = MedicationApplicationType.ORAL,
            preparationType = MedicinePreparationType.PILL,
            tabletFractionNumerator = 3,
            tabletFractionDenominator = 4,
        )

        assertEquals(TabletFractionOption.WHOLE, doseDraft.selectedTabletFractionOption())
        assertEquals(DoseInstruction.TabletFraction(3, 4), doseDraft.toDoseInstruction())
    }

    // --- Antiandrogen dose-warning (Fix 4) ---------------------------------
    //
    // The old draft compared a typed per-dose mg directly against the threshold.
    // The new draft has no mg field — `exceedsDoseWarningThreshold` derives
    // mg from strengthMgPerTablet × tabletFractionNumerator/denominator × count.
    // The threshold is exclusive: strictly greater than fires the warning.

    @Test
    fun antiandrogen_dose_warning_fires_only_above_the_strict_threshold() {
        // Spironolactone at exactly 200 mg / dose: no warning. Two pills = 400 mg
        // exceeds threshold and warns. CPA and bicalutamide thresholds verified
        // in the same way.
        val spiroAtThreshold = defaultMedicineDraft(
            category = MedicationCategory.ANTIANDROGEN,
            applicationType = MedicationApplicationType.ORAL,
        ).changeMedicationKey(MedicationKey.SPIRONOLACTONE)
            .copy(pillStrengthMg = "200")
        val cpaAtThreshold = defaultMedicineDraft(
            category = MedicationCategory.ANTIANDROGEN,
            applicationType = MedicationApplicationType.ORAL,
        ).changeMedicationKey(MedicationKey.CYPROTERONE_ACETATE)
            .copy(pillStrengthMg = "12.5")
        val bicaAboveThreshold = defaultMedicineDraft(
            category = MedicationCategory.ANTIANDROGEN,
            applicationType = MedicationApplicationType.ORAL,
        ).changeMedicationKey(MedicationKey.BICALUTAMIDE)
            .copy(pillStrengthMg = "50.1")

        assertFalse(spiroAtThreshold.exceedsDoseWarningThreshold(spiroAtThreshold.toDoseInstructionDraft()))
        assertTrue(
            spiroAtThreshold.exceedsDoseWarningThreshold(
                spiroAtThreshold.toDoseInstructionDraft(),
                count = 2,
            ),
        )
        assertFalse(cpaAtThreshold.exceedsDoseWarningThreshold(cpaAtThreshold.toDoseInstructionDraft()))
        assertTrue(
            cpaAtThreshold.exceedsDoseWarningThreshold(
                cpaAtThreshold.toDoseInstructionDraft(),
                count = 2,
            ),
        )
        assertTrue(
            bicaAboveThreshold.exceedsDoseWarningThreshold(
                bicaAboveThreshold.toDoseInstructionDraft(),
            ),
        )
    }

    @Test
    fun non_antiandrogen_medications_never_show_dose_warning() {
        // No threshold defined for estradiol or for custom medicines, so a
        // 999 mg-per-pill draft must not warn — the warning is intentionally
        // scoped to the three antiandrogen catalog entries.
        val estradiol = defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
        ).copy(pillStrengthMg = "999")
        val custom = defaultMedicineDraft(category = MedicationCategory.CUSTOM)
            .copy(pillStrengthMg = "999", customMedicationName = "Whatever")

        assertFalse(estradiol.exceedsDoseWarningThreshold(estradiol.toDoseInstructionDraft()))
        assertFalse(custom.exceedsDoseWarningThreshold(custom.toDoseInstructionDraft()))
    }

    @Test
    fun antiandrogen_dose_warning_scales_with_tablet_fraction_and_count() {
        // CPA at 12.5 mg per tablet, half a tablet per instruction = 6.25 mg.
        // Count of 1 stays under threshold; count of 3 = 18.75 mg, exceeds it.
        // Encodes the intent: the warning tracks per-instruction × count, not
        // a raw mg field.
        val cpaHalfTablet = defaultMedicineDraft(
            category = MedicationCategory.ANTIANDROGEN,
            applicationType = MedicationApplicationType.ORAL,
        ).changeMedicationKey(MedicationKey.CYPROTERONE_ACETATE)
            .copy(pillStrengthMg = "12.5")
        val halfTabletDose = cpaHalfTablet.toDoseInstructionDraft().copy(
            tabletFractionNumerator = 1,
            tabletFractionDenominator = 2,
        )

        assertFalse(cpaHalfTablet.exceedsDoseWarningThreshold(halfTabletDose, count = 1))
        assertFalse(cpaHalfTablet.exceedsDoseWarningThreshold(halfTabletDose, count = 2))
        assertTrue(cpaHalfTablet.exceedsDoseWarningThreshold(halfTabletDose, count = 3))
    }
}
