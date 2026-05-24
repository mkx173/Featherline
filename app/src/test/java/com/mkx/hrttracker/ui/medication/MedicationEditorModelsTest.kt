package com.mkx.hrttracker.ui.medication

import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCatalog
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDoseAssistPreset
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationForm
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
    fun defaultCustomDraft_usesTabletFormAndPillPreparation() {
        val draft = defaultMedicineDraft(category = MedicationCategory.CUSTOM)

        assertEquals(MedicinePreparationForm.TABLET, draft.form)
        assertEquals(MedicinePreparationType.PILL, draft.inferredOrSelectedPreparationType())
        assertEquals(MedicationApplicationType.ORAL, draft.catalogFilterApplicationType)
    }

    @Test
    fun changingCustomDraftToCapsuleProducesOralCapsule() {
        val draft = defaultMedicineDraft(category = MedicationCategory.CUSTOM)
            .changeForm(MedicinePreparationForm.CAPSULE)
            .copy(pillStrengthMg = "100")

        assertEquals(MedicinePreparationForm.CAPSULE, draft.form)
        assertEquals(MedicinePreparationType.CAPSULE, draft.inferredOrSelectedPreparationType())
        assertEquals(MedicationApplicationType.ORAL, draft.catalogFilterApplicationType)
        assertEquals(
            MedicinePreparation.Capsule(strengthMgPerCapsule = 100.0),
            draft.toMedicinePreparation(),
        )
    }

    @Test
    fun changingToInjectionFormSelectsFirstInjectionPreparation() {
        val draft = defaultMedicineDraft(category = MedicationCategory.ESTRADIOL)
            .changeForm(MedicinePreparationForm.INJECTION)

        assertEquals(MedicinePreparationForm.INJECTION, draft.form)
        assertEquals(MedicationApplicationType.INJECTION, draft.catalogFilterApplicationType)
        assertEquals(MedicinePreparationType.INJECTION_SINGLE_USE_VIAL, draft.inferredOrSelectedPreparationType())
    }

    @Test
    fun doseInstructionDraftForCapsuleUsesWholeUnitShape() {
        val draft = defaultMedicineDraft(category = MedicationCategory.CUSTOM)
            .changeForm(MedicinePreparationForm.CAPSULE)

        val doseDraft = draft.toDoseInstructionDraft()

        assertEquals(MedicationApplicationType.ORAL, doseDraft.applicationType)
        assertEquals(MedicinePreparationType.CAPSULE, doseDraft.preparationType)
        assertEquals(DoseInstruction.WholeUnit, doseDraft.toDoseInstruction())
    }

    @Test
    fun doseInstructionDraftPreservesCompatibilityRouteCursor() {
        val sublingualDoseDraft = defaultMedicineDraft(
            applicationType = MedicationApplicationType.SUBLINGUAL,
        ).toDoseInstructionDraft()
        val patchOffDoseDraft = defaultMedicineDraft(
            applicationType = MedicationApplicationType.PATCH_OFF,
        ).toDoseInstructionDraft()

        assertEquals(MedicationApplicationType.SUBLINGUAL, sublingualDoseDraft.applicationType)
        assertEquals(DoseInstruction.TabletFraction(1, 1), sublingualDoseDraft.toDoseInstruction())
        assertEquals(MedicationApplicationType.PATCH_OFF, patchOffDoseDraft.applicationType)
        assertEquals(DoseInstruction.Noop, patchOffDoseDraft.toDoseInstruction())
    }

    @Test
    fun resolvedApplicationTypeForDose_preservesTabletDraftRoute() {
        val medicine = testMedicine(
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
        )
        val doseDraft = DoseInstructionDraftUiState(
            applicationType = MedicationApplicationType.SUBLINGUAL,
            preparationType = MedicinePreparationType.PILL,
        )

        assertEquals(
            MedicationApplicationType.SUBLINGUAL,
            resolvedApplicationTypeForDose(medicine.preparation.type, doseDraft),
        )
    }

    @Test
    fun resolvedApplicationTypeForDose_derivesNonTabletRouteFromPreparation() {
        val doseDraft = DoseInstructionDraftUiState(
            applicationType = MedicationApplicationType.SUBLINGUAL,
            preparationType = MedicinePreparationType.CAPSULE,
        )

        assertEquals(
            MedicationApplicationType.ORAL,
            resolvedApplicationTypeForDose(MedicinePreparationType.CAPSULE, doseDraft),
        )
    }

    // The picker still shows up for ambiguous routes — but draft starts with a
    // sensible default preparation, so the editor's dose form renders
    // immediately and validation fails on the strength field, not on "pick a
    // preparation type". Without a default, switching to gel/injection looks
    // broken (no fields appear) until the user taps a sub-type chip.
    @Test
    fun injectionDraftDefaultsToFirstAmbiguousPreparation() {
        val draft = defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.INJECTION,
        )

        assertTrue(draft.requiresPreparationTypeSelection())
        assertEquals(
            MedicinePreparationType.INJECTION_SINGLE_USE_VIAL,
            draft.preparationType,
        )
        // Strength field validation fires (no "preparation type required" stop).
        assertEquals(R.string.validation_vial_strength_required, draft.validationErrorRes())
    }

    @Test
    fun gelDraftDefaultsToContainer() {
        val draft = defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.GEL,
        )

        assertEquals(MedicinePreparationType.GEL_CONTAINER, draft.preparationType)
    }

    @Test
    fun patchFormProducesWholeUnitDoseInstruction() {
        val draft = defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
        ).changeForm(MedicinePreparationForm.PATCH)

        assertEquals(DoseInstruction.WholeUnit, draft.toDoseInstructionDraft().toDoseInstruction())
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
    fun pill_strength_assist_chip_fills_catalog_medicine_raw_strength_and_round_trips() {
        val draft = defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
        ).copy(medicationKey = MedicationKey.ESTRADIOL)

        assertEquals(
            listOf(
                MedicationDoseAssistPreset.MgAsMedicine("1"),
                MedicationDoseAssistPreset.MgAsMedicine("2"),
            ),
            draft.activeDoseAssistPresets(),
        )

        val updated = draft.applyDoseAssistPreset(
            MedicationDoseAssistPreset.MgAsMedicine("2"),
        )

        assertEquals("2", updated.pillStrengthMg)
        assertEquals(MedicinePreparation.Pill(2.0), updated.toMedicinePreparation())
    }

    @Test
    fun active_assist_chips_follow_selected_catalog_entry_not_first_entry() {
        val draft = defaultMedicineDraft(
            category = MedicationCategory.ANTIANDROGEN,
            applicationType = MedicationApplicationType.ORAL,
        ).changeMedicationKey(MedicationKey.CYPROTERONE_ACETATE)

        assertEquals(
            listOf(
                MedicationDoseAssistPreset.MgAsMedicine("50"),
                MedicationDoseAssistPreset.MgAsMedicine("100"),
            ),
            draft.activeDoseAssistPresets(),
        )
    }

    @Test
    fun gel_weight_preset_still_round_trips_to_container_dose_instruction() {
        // GelWeightGrams moved to sachet-only at the catalog level so the new
        // GelContainerSizeGrams chip can sit on the container-size field at
        // create time without leaking into the per-dose log chip row. The
        // catalog-driven per-dose row is therefore empty for GEL_CONTAINER;
        // the applier itself still writes weightGrams for any GelWeightGrams
        // preset reached through a non-catalog path.
        val medicineDraft = defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.GEL,
        ).changePreparationType(MedicinePreparationType.GEL_CONTAINER)
        val doseDraft = medicineDraft.toDoseInstructionDraft()

        assertEquals(
            emptyList<MedicationDoseAssistPreset>(),
            activeDoseAssistPresets(
                medicineDraft = medicineDraft,
                doseInstructionDraft = doseDraft,
            ),
        )

        val updated = doseDraft.applyDoseAssistPreset(
            MedicationDoseAssistPreset.GelWeightGrams("1.25"),
        )

        assertEquals("1.25", updated.weightGrams)
        assertEquals(DoseInstruction.WeightGrams(1.25), updated.toDoseInstruction())
    }

    @Test
    fun patch_release_rate_assist_chip_fills_patch_raw_field_and_round_trips() {
        val draft = defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.PATCH_ON,
        )

        val updated = draft.applyDoseAssistPreset(
            MedicationDoseAssistPreset.PatchReleaseRateMcgPerDay("75"),
        )

        assertEquals(PatchSpecKind.RELEASE_RATE, updated.patchSpecKind)
        assertEquals("75", updated.patchReleaseRateMcgPerDay)
        assertEquals(
            MedicinePreparation.Patch(
                MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay(75.0),
            ),
            updated.toMedicinePreparation(),
        )
    }

    @Test
    fun custom_and_capsule_drafts_do_not_show_catalog_assist_chips() {
        val custom = defaultMedicineDraft(
            category = MedicationCategory.CUSTOM,
            applicationType = MedicationApplicationType.ORAL,
        )
        val capsule = defaultMedicineDraft(category = MedicationCategory.CUSTOM)
            .changeForm(MedicinePreparationForm.CAPSULE)

        assertEquals(emptyList<MedicationDoseAssistPreset>(), custom.activeDoseAssistPresets())
        assertEquals(emptyList<MedicationDoseAssistPreset>(), capsule.activeDoseAssistPresets())
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
        // INJECTION supports count: "N vials" or "N injections of given volume".
        assertTrue(
            defaultMedicineDraft(applicationType = MedicationApplicationType.INJECTION)
                .showsMedicationCountEditor(),
        )
        assertFalse(
            defaultMedicineDraft(applicationType = MedicationApplicationType.GEL)
                .showsMedicationCountEditor(),
        )
    }

    @Test
    fun applicationTypesCompatibleWithPreparation_matchesPreparationFamily() {
        assertEquals(
            listOf(MedicationApplicationType.ORAL, MedicationApplicationType.SUBLINGUAL),
            applicationTypesCompatibleWithPreparation(MedicinePreparationType.PILL),
        )
        assertEquals(
            listOf(MedicationApplicationType.INJECTION),
            applicationTypesCompatibleWithPreparation(MedicinePreparationType.INJECTION_SINGLE_USE_VIAL),
        )
        assertEquals(
            listOf(MedicationApplicationType.GEL),
            applicationTypesCompatibleWithPreparation(MedicinePreparationType.GEL_CONTAINER),
        )
        assertEquals(
            listOf(MedicationApplicationType.PATCH_ON),
            applicationTypesCompatibleWithPreparation(MedicinePreparationType.PATCH),
        )
    }

    @Test
    fun resolving_medication_count_text_resets_when_preparation_type_changes() {
        val previousDraft = defaultMedicineDraft(category = MedicationCategory.CUSTOM)
        val updatedDraft = previousDraft.changeForm(MedicinePreparationForm.CAPSULE)

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

    @Test
    fun antiandrogen_dose_warning_uses_medication_summary_trailing_indicator() {
        // The editor shows the dose warning on the selected medication card, not
        // as separate inline content below the tablet-fraction controls.
        val warningDrafts = listOf(
            defaultMedicineDraft(
                category = MedicationCategory.ANTIANDROGEN,
                applicationType = MedicationApplicationType.ORAL,
            ).changeMedicationKey(MedicationKey.SPIRONOLACTONE)
                .copy(pillStrengthMg = "200") to "2",
            defaultMedicineDraft(
                category = MedicationCategory.ANTIANDROGEN,
                applicationType = MedicationApplicationType.ORAL,
            ).changeMedicationKey(MedicationKey.CYPROTERONE_ACETATE)
                .copy(pillStrengthMg = "12.5") to "2",
            defaultMedicineDraft(
                category = MedicationCategory.ANTIANDROGEN,
                applicationType = MedicationApplicationType.ORAL,
            ).changeMedicationKey(MedicationKey.BICALUTAMIDE)
                .copy(pillStrengthMg = "50.1") to "1",
        )

        warningDrafts.forEach { (medicineDraft, countText) ->
            assertEquals(
                MedicationSummaryTrailingIndicator.DOSE_WARNING,
                medicationSummaryTrailingIndicator(
                    medicineDraft = medicineDraft,
                    doseInstructionDraft = medicineDraft.toDoseInstructionDraft(),
                    countText = countText,
                ),
            )
        }

        val normalDose = defaultMedicineDraft(
            category = MedicationCategory.ANTIANDROGEN,
            applicationType = MedicationApplicationType.ORAL,
        ).changeMedicationKey(MedicationKey.CYPROTERONE_ACETATE)
            .copy(pillStrengthMg = "12.5")

        assertNull(
            medicationSummaryTrailingIndicator(
                medicineDraft = normalDose,
                doseInstructionDraft = normalDose.toDoseInstructionDraft(),
                countText = "1",
            ),
        )
    }
}
