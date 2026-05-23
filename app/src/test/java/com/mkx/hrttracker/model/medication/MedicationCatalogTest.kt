package com.mkx.hrttracker.model.medication

import com.mkx.hrttracker.model.medication.MedicationCatalog.catalogFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MedicationCatalogTest {
    @Test
    fun estradiol_oral_catalog_includes_required_medications() {
        val catalog = MedicationCatalog.catalogFor(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
        )

        assertEquals(
            listOf(
                MedicationKey.ESTRADIOL_VALERATE,
                MedicationKey.ESTRADIOL,
            ),
            catalog.entries.mapNotNull(MedicationCatalogEntry::medicationKey),
        )
        assertFalse(catalog.allowCustomMedicationName)
    }

    @Test
    fun estradiol_injection_catalog_uses_requested_order() {
        val catalog = catalogFor(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.INJECTION,
        )

        assertEquals(
            listOf(
                MedicationKey.ESTRADIOL_VALERATE,
                MedicationKey.ESTRADIOL_CYPIONATE,
                MedicationKey.ESTRADIOL_ENANTHATE,
                MedicationKey.ESTRADIOL_BENZOATE,
            ),
            catalog.entries.mapNotNull(MedicationCatalogEntry::medicationKey),
        )
    }

    @Test
    fun antiandrogen_only_exposes_oral_route_with_required_medications() {
        val applicationTypes = MedicationCatalog.applicationTypesFor(MedicationCategory.ANTIANDROGEN)
        val catalog = MedicationCatalog.catalogFor(
            category = MedicationCategory.ANTIANDROGEN,
            applicationType = MedicationApplicationType.ORAL,
        )

        assertEquals(listOf(MedicationApplicationType.ORAL), applicationTypes)
        assertEquals(
            listOf(
                MedicationKey.SPIRONOLACTONE,
                MedicationKey.CYPROTERONE_ACETATE,
                MedicationKey.BICALUTAMIDE,
            ),
            catalog.entries.mapNotNull(MedicationCatalogEntry::medicationKey),
        )
        assertFalse(catalog.allowCustomMedicationName)
    }

    @Test
    fun catalog_preserves_old_mg_assist_values_on_raw_mass_preparations() {
        assertEquals(
            listOf(
                MedicationDoseAssistPreset.MgAsMedicine("1"),
                MedicationDoseAssistPreset.MgAsMedicine("2"),
                MedicationDoseAssistPreset.MgAsMedicine("3"),
            ),
            catalogEntry(
                category = MedicationCategory.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                medicationKey = MedicationKey.ESTRADIOL,
            ).doseAssistPresets.getValue(MedicinePreparationType.PILL),
        )
        assertEquals(
            listOf(
                MedicationDoseAssistPreset.MgAsMedicine("5"),
                MedicationDoseAssistPreset.MgAsMedicine("10"),
            ),
            catalogEntry(
                category = MedicationCategory.ESTRADIOL,
                applicationType = MedicationApplicationType.INJECTION,
                medicationKey = MedicationKey.ESTRADIOL_VALERATE,
            ).doseAssistPresets.getValue(MedicinePreparationType.INJECTION_SINGLE_USE_VIAL),
        )
    }

    @Test
    fun antiandrogen_catalog_preserves_old_assist_values_per_catalog_medicine() {
        assertEquals(
            listOf(
                MedicationDoseAssistPreset.MgAsMedicine("100"),
                MedicationDoseAssistPreset.MgAsMedicine("200"),
            ),
            catalogEntry(
                category = MedicationCategory.ANTIANDROGEN,
                applicationType = MedicationApplicationType.ORAL,
                medicationKey = MedicationKey.SPIRONOLACTONE,
            ).doseAssistPresets.getValue(MedicinePreparationType.PILL),
        )
        assertEquals(
            listOf(
                MedicationDoseAssistPreset.MgAsMedicine("6.25"),
                MedicationDoseAssistPreset.MgAsMedicine("12.5"),
                MedicationDoseAssistPreset.MgAsMedicine("25"),
            ),
            catalogEntry(
                category = MedicationCategory.ANTIANDROGEN,
                applicationType = MedicationApplicationType.ORAL,
                medicationKey = MedicationKey.CYPROTERONE_ACETATE,
            ).doseAssistPresets.getValue(MedicinePreparationType.PILL),
        )
        assertEquals(
            listOf(
                MedicationDoseAssistPreset.MgAsMedicine("25"),
                MedicationDoseAssistPreset.MgAsMedicine("50"),
            ),
            catalogEntry(
                category = MedicationCategory.ANTIANDROGEN,
                applicationType = MedicationApplicationType.ORAL,
                medicationKey = MedicationKey.BICALUTAMIDE,
            ).doseAssistPresets.getValue(MedicinePreparationType.PILL),
        )
    }

    @Test
    fun gel_and_patch_catalog_preserve_old_assist_values_without_patch_off_presets() {
        assertEquals(
            listOf(
                MedicationDoseAssistPreset.GelPercent("0.06"),
                MedicationDoseAssistPreset.GelPercent("0.3"),
                MedicationDoseAssistPreset.GelPercent("0.6"),
                MedicationDoseAssistPreset.GelWeightGrams("1.25"),
                MedicationDoseAssistPreset.GelWeightGrams("2.5"),
            ),
            catalogEntry(
                category = MedicationCategory.ESTRADIOL,
                applicationType = MedicationApplicationType.GEL,
                medicationKey = MedicationKey.ESTRADIOL_GEL,
            ).doseAssistPresets.getValue(MedicinePreparationType.GEL_CONTAINER),
        )
        assertEquals(
            listOf(
                MedicationDoseAssistPreset.PatchReleaseRateMcgPerDay("50"),
                MedicationDoseAssistPreset.PatchReleaseRateMcgPerDay("75"),
                MedicationDoseAssistPreset.PatchReleaseRateMcgPerDay("100"),
                MedicationDoseAssistPreset.PatchTotalMg("0.36"),
                MedicationDoseAssistPreset.PatchTotalMg("0.72"),
            ),
            catalogEntry(
                category = MedicationCategory.ESTRADIOL,
                applicationType = MedicationApplicationType.PATCH_ON,
                medicationKey = MedicationKey.ESTRADIOL_PATCH,
            ).doseAssistPresets.getValue(MedicinePreparationType.PATCH),
        )
        assertEquals(
            emptyMap<MedicinePreparationType, List<MedicationDoseAssistPreset>>(),
            catalogEntry(
                category = MedicationCategory.ESTRADIOL,
                applicationType = MedicationApplicationType.PATCH_OFF,
                medicationKey = MedicationKey.ESTRADIOL_PATCH,
            ).doseAssistPresets,
        )
    }

    private fun catalogEntry(
        category: MedicationCategory,
        applicationType: MedicationApplicationType,
        medicationKey: MedicationKey,
    ): MedicationCatalogEntry {
        return catalogFor(category, applicationType).entries.first {
            it.medicationKey == medicationKey
        }
    }
}
