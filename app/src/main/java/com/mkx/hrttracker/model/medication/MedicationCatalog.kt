package com.mkx.hrttracker.model.medication

enum class MedicationCategory {
    ESTRADIOL,
    TESTOSTERONE,
    ANTIANDROGEN,
    CUSTOM;

    companion object {
        fun fromStorageValue(value: String?): MedicationCategory {
            return entries.firstOrNull { it.name == value } ?: CUSTOM
        }
    }
}

enum class MedicationApplicationType {
    ORAL,
    SUBLINGUAL,
    INJECTION,
    GEL,
    PATCH_ON,
    PATCH_OFF;

    companion object {
        fun fromStorageValue(value: String?): MedicationApplicationType {
            return entries.firstOrNull { it.name == value } ?: ORAL
        }
    }
}

enum class MedicationGelApplicationArea {
    DEFAULT;

    companion object {
        fun fromStorageValue(value: String?): MedicationGelApplicationArea {
            return entries.firstOrNull { it.name == value } ?: DEFAULT
        }
    }
}

enum class MedicationSelectionKind {
    CATALOG,
    CUSTOM;

    companion object {
        fun fromStorageValue(value: String?): MedicationSelectionKind {
            return entries.firstOrNull { it.name == value } ?: CUSTOM
        }
    }
}

enum class MedicationKey(val category: MedicationCategory) {
    SPIRONOLACTONE(category = MedicationCategory.ANTIANDROGEN),
    CYPROTERONE_ACETATE(category = MedicationCategory.ANTIANDROGEN),
    BICALUTAMIDE(category = MedicationCategory.ANTIANDROGEN),
    ESTRADIOL(category = MedicationCategory.ESTRADIOL),
    ESTRADIOL_VALERATE(category = MedicationCategory.ESTRADIOL),
    ESTRADIOL_BENZOATE(category = MedicationCategory.ESTRADIOL),
    ESTRADIOL_CYPIONATE(category = MedicationCategory.ESTRADIOL),
    ESTRADIOL_ENANTHATE(category = MedicationCategory.ESTRADIOL),
    ESTRADIOL_GEL(category = MedicationCategory.ESTRADIOL),
    ESTRADIOL_PATCH(category = MedicationCategory.ESTRADIOL);

    companion object {
        fun fromStorageValue(value: String?): MedicationKey? {
            return entries.firstOrNull { it.name == value }
        }
    }
}

// Catalog entries now carry only medication-key identity. Preparation fields
// and dose-instruction presets moved into picker drafts when the picker
// migrated to the new Medicine + DoseInstruction model.
data class MedicationCatalogEntry(
    val medicationKey: MedicationKey?,
)

data class MedicationApplicationCatalog(
    val category: MedicationCategory,
    val applicationType: MedicationApplicationType,
    val entries: List<MedicationCatalogEntry>,
    val allowCustomMedicationName: Boolean,
)

object MedicationCatalog {
    private val estradiolCatalog = listOf(
        MedicationApplicationCatalog(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
            entries = listOf(
                MedicationCatalogEntry(medicationKey = MedicationKey.ESTRADIOL_VALERATE),
                MedicationCatalogEntry(medicationKey = MedicationKey.ESTRADIOL),
            ),
            allowCustomMedicationName = false,
        ),
        MedicationApplicationCatalog(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.SUBLINGUAL,
            entries = listOf(
                MedicationCatalogEntry(medicationKey = MedicationKey.ESTRADIOL_VALERATE),
                MedicationCatalogEntry(medicationKey = MedicationKey.ESTRADIOL),
            ),
            allowCustomMedicationName = false,
        ),
        MedicationApplicationCatalog(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.INJECTION,
            entries = listOf(
                MedicationCatalogEntry(medicationKey = MedicationKey.ESTRADIOL_VALERATE),
                MedicationCatalogEntry(medicationKey = MedicationKey.ESTRADIOL_CYPIONATE),
                MedicationCatalogEntry(medicationKey = MedicationKey.ESTRADIOL_ENANTHATE),
                MedicationCatalogEntry(medicationKey = MedicationKey.ESTRADIOL_BENZOATE),
            ),
            allowCustomMedicationName = false,
        ),
        MedicationApplicationCatalog(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.GEL,
            entries = listOf(
                MedicationCatalogEntry(medicationKey = MedicationKey.ESTRADIOL_GEL),
            ),
            allowCustomMedicationName = false,
        ),
        MedicationApplicationCatalog(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.PATCH_ON,
            entries = listOf(
                MedicationCatalogEntry(medicationKey = MedicationKey.ESTRADIOL_PATCH),
            ),
            allowCustomMedicationName = false,
        ),
        // PATCH_OFF retains its catalog entry so legacy slot/log code paths
        // that look up "the catalog for ESTRADIOL + PATCH_OFF" don't NPE.
        // Creating new medicines via this route is gated by
        // CreateMedicineSheet.createMedicineApplicationTypesFor, which
        // filters PATCH_OFF out of the application-type picker — the
        // PATCH_OFF singleton is the only patch-off Medicine the app
        // produces, and it's auto-created from MedicineRepository.
        MedicationApplicationCatalog(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.PATCH_OFF,
            entries = listOf(
                MedicationCatalogEntry(medicationKey = MedicationKey.ESTRADIOL_PATCH),
            ),
            allowCustomMedicationName = false,
        ),
    )

    private val antiandrogenCatalog = listOf(
        MedicationApplicationCatalog(
            category = MedicationCategory.ANTIANDROGEN,
            applicationType = MedicationApplicationType.ORAL,
            entries = listOf(
                MedicationCatalogEntry(medicationKey = MedicationKey.SPIRONOLACTONE),
                MedicationCatalogEntry(medicationKey = MedicationKey.CYPROTERONE_ACETATE),
                MedicationCatalogEntry(medicationKey = MedicationKey.BICALUTAMIDE),
            ),
            allowCustomMedicationName = false,
        ),
    )

    private val testosteroneCatalog = MedicationApplicationType.entries.map { applicationType ->
        MedicationApplicationCatalog(
            category = MedicationCategory.TESTOSTERONE,
            applicationType = applicationType,
            entries = listOf(MedicationCatalogEntry(medicationKey = null)),
            allowCustomMedicationName = true,
        )
    }

    private val customCatalog = listOf(
        MedicationApplicationCatalog(
            category = MedicationCategory.CUSTOM,
            applicationType = MedicationApplicationType.ORAL,
            entries = listOf(MedicationCatalogEntry(medicationKey = null)),
            allowCustomMedicationName = true,
        ),
    )

    private val catalogs = estradiolCatalog + antiandrogenCatalog + testosteroneCatalog + customCatalog

    fun applicationTypesFor(category: MedicationCategory): List<MedicationApplicationType> {
        return catalogs
            .filter { it.category == category }
            .map(MedicationApplicationCatalog::applicationType)
    }

    fun catalogFor(
        category: MedicationCategory,
        applicationType: MedicationApplicationType,
    ): MedicationApplicationCatalog {
        return catalogs.first { catalog ->
            catalog.category == category && catalog.applicationType == applicationType
        }
    }

    fun defaultEntryFor(
        category: MedicationCategory,
        applicationType: MedicationApplicationType,
    ): MedicationCatalogEntry {
        return catalogFor(category, applicationType).entries.first()
    }
}
