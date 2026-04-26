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
    fun estradiol_patch_off_only_allows_no_dose() {
        val entry = MedicationCatalog.defaultEntryFor(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.PATCH_OFF,
        )

        assertEquals(setOf(MedicationDoseKind.NONE), entry.doseKinds)
        assertEquals(MedicationDoseKind.NONE, entry.defaultDoseKind)
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
}
