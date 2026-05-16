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

enum class MedicationDoseKind {
    MG_AS_MEDICINE,
    GEL_EQUIVALENT_ESTRADIOL_MG,
    GEL_PERCENT_AND_WEIGHT,
    PATCH_TOTAL_MG,
    PATCH_RELEASE_RATE_MCG_DAY,
    NONE;

    companion object {
        fun fromStorageValue(value: String?): MedicationDoseKind {
            return entries.firstOrNull { it.name == value } ?: NONE
        }
    }
}

// Append-only: persisted storage values are enum names.
enum class MedicationDoseUnit {
    MG,
    MCG,
    G;

    val storageValue: String
        get() = name

    companion object {
        fun fromStorageValue(value: String?): MedicationDoseUnit {
            return entries.firstOrNull { it.storageValue == value } ?: MG
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

sealed interface MedicationSelection {
    val kind: MedicationSelectionKind

    data class Catalog(val medicationKey: MedicationKey) : MedicationSelection {
        override val kind: MedicationSelectionKind = MedicationSelectionKind.CATALOG
    }

    data class Custom(val medicationName: String) : MedicationSelection {
        override val kind: MedicationSelectionKind = MedicationSelectionKind.CUSTOM
    }
}

sealed interface MedicationDose {
    val kind: MedicationDoseKind

    data class MgAsMedicine(val valueMg: Double) : MedicationDose {
        override val kind: MedicationDoseKind = MedicationDoseKind.MG_AS_MEDICINE
    }

    data class GelEquivalentEstradiolMg(val valueMg: Double) : MedicationDose {
        override val kind: MedicationDoseKind = MedicationDoseKind.GEL_EQUIVALENT_ESTRADIOL_MG
    }

    data class GelPercentAndWeight(
        val percent: Double,
        val weightGrams: Double,
    ) : MedicationDose {
        override val kind: MedicationDoseKind = MedicationDoseKind.GEL_PERCENT_AND_WEIGHT
    }

    data class PatchTotalMg(val valueMg: Double) : MedicationDose {
        override val kind: MedicationDoseKind = MedicationDoseKind.PATCH_TOTAL_MG
    }

    data class PatchReleaseRateMcgPerDay(val valueMcgPerDay: Double) : MedicationDose {
        override val kind: MedicationDoseKind = MedicationDoseKind.PATCH_RELEASE_RATE_MCG_DAY
    }

    data object None : MedicationDose {
        override val kind: MedicationDoseKind = MedicationDoseKind.NONE
    }
}

data class MedicationDetails(
    val category: MedicationCategory,
    val applicationType: MedicationApplicationType,
    val selection: MedicationSelection,
    val dose: MedicationDose,
    val gelApplicationArea: MedicationGelApplicationArea = MedicationGelApplicationArea.DEFAULT,
    val customDoseUnit: MedicationDoseUnit = MedicationDoseUnit.MG,
)

data class MedicationCatalogEntry(
    val medicationKey: MedicationKey?,
    val doseKinds: Set<MedicationDoseKind>,
    val defaultDoseKind: MedicationDoseKind,
    val doseAssistPresets: Map<MedicationDoseKind, List<MedicationDoseAssistPreset>> = emptyMap(),
)

sealed interface MedicationDoseAssistPreset {
    val doseKind: MedicationDoseKind

    data class MgAsMedicine(
        val valueMg: String,
    ) : MedicationDoseAssistPreset {
        override val doseKind: MedicationDoseKind = MedicationDoseKind.MG_AS_MEDICINE
    }

    data class GelEquivalentEstradiolMg(
        val valueMg: String,
    ) : MedicationDoseAssistPreset {
        override val doseKind: MedicationDoseKind = MedicationDoseKind.GEL_EQUIVALENT_ESTRADIOL_MG
    }

    data class GelPercent(
        val percent: String,
    ) : MedicationDoseAssistPreset {
        override val doseKind: MedicationDoseKind = MedicationDoseKind.GEL_PERCENT_AND_WEIGHT
    }

    data class GelWeightGrams(
        val weightGrams: String,
    ) : MedicationDoseAssistPreset {
        override val doseKind: MedicationDoseKind = MedicationDoseKind.GEL_PERCENT_AND_WEIGHT
    }

    data class PatchTotalMg(
        val valueMg: String,
    ) : MedicationDoseAssistPreset {
        override val doseKind: MedicationDoseKind = MedicationDoseKind.PATCH_TOTAL_MG
    }

    data class PatchReleaseRateMcgPerDay(
        val valueMcgPerDay: String,
    ) : MedicationDoseAssistPreset {
        override val doseKind: MedicationDoseKind = MedicationDoseKind.PATCH_RELEASE_RATE_MCG_DAY
    }
}

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
                MedicationCatalogEntry(
                    medicationKey = MedicationKey.ESTRADIOL_VALERATE,
                    doseKinds = setOf(MedicationDoseKind.MG_AS_MEDICINE),
                    defaultDoseKind = MedicationDoseKind.MG_AS_MEDICINE,
                    doseAssistPresets = mgDoseAssistPresets("1", "2"),
                ),
                MedicationCatalogEntry(
                    medicationKey = MedicationKey.ESTRADIOL,
                    doseKinds = setOf(MedicationDoseKind.MG_AS_MEDICINE),
                    defaultDoseKind = MedicationDoseKind.MG_AS_MEDICINE,
                    doseAssistPresets = mgDoseAssistPresets("1", "2"),
                ),
            ),
            allowCustomMedicationName = false,
        ),
        MedicationApplicationCatalog(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.SUBLINGUAL,
            entries = listOf(
                MedicationCatalogEntry(
                    medicationKey = MedicationKey.ESTRADIOL_VALERATE,
                    doseKinds = setOf(MedicationDoseKind.MG_AS_MEDICINE),
                    defaultDoseKind = MedicationDoseKind.MG_AS_MEDICINE,
                    doseAssistPresets = mgDoseAssistPresets("1", "2"),
                ),
                MedicationCatalogEntry(
                    medicationKey = MedicationKey.ESTRADIOL,
                    doseKinds = setOf(MedicationDoseKind.MG_AS_MEDICINE),
                    defaultDoseKind = MedicationDoseKind.MG_AS_MEDICINE,
                    doseAssistPresets = mgDoseAssistPresets("1", "2"),
                ),
            ),
            allowCustomMedicationName = false,
        ),
        MedicationApplicationCatalog(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.INJECTION,
            entries = listOf(
                MedicationCatalogEntry(
                    medicationKey = MedicationKey.ESTRADIOL_VALERATE,
                    doseKinds = setOf(MedicationDoseKind.MG_AS_MEDICINE),
                    defaultDoseKind = MedicationDoseKind.MG_AS_MEDICINE,
                    doseAssistPresets = mgDoseAssistPresets("5", "10"),
                ),
                MedicationCatalogEntry(
                    medicationKey = MedicationKey.ESTRADIOL_CYPIONATE,
                    doseKinds = setOf(MedicationDoseKind.MG_AS_MEDICINE),
                    defaultDoseKind = MedicationDoseKind.MG_AS_MEDICINE,
                    doseAssistPresets = mgDoseAssistPresets("5", "10"),
                ),
                MedicationCatalogEntry(
                    medicationKey = MedicationKey.ESTRADIOL_ENANTHATE,
                    doseKinds = setOf(MedicationDoseKind.MG_AS_MEDICINE),
                    defaultDoseKind = MedicationDoseKind.MG_AS_MEDICINE,
                    doseAssistPresets = mgDoseAssistPresets("5", "10"),
                ),
                MedicationCatalogEntry(
                    medicationKey = MedicationKey.ESTRADIOL_BENZOATE,
                    doseKinds = setOf(MedicationDoseKind.MG_AS_MEDICINE),
                    defaultDoseKind = MedicationDoseKind.MG_AS_MEDICINE,
                    doseAssistPresets = mgDoseAssistPresets("5", "10"),
                ),
            ),
            allowCustomMedicationName = false,
        ),
        MedicationApplicationCatalog(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.GEL,
            entries = listOf(
                MedicationCatalogEntry(
                    medicationKey = MedicationKey.ESTRADIOL_GEL,
                    doseKinds = setOf(
                        MedicationDoseKind.GEL_EQUIVALENT_ESTRADIOL_MG,
                        MedicationDoseKind.GEL_PERCENT_AND_WEIGHT,
                    ),
                    defaultDoseKind = MedicationDoseKind.GEL_EQUIVALENT_ESTRADIOL_MG,
                    doseAssistPresets = mapOf(
                        MedicationDoseKind.GEL_EQUIVALENT_ESTRADIOL_MG to listOf(
                            MedicationDoseAssistPreset.GelEquivalentEstradiolMg("0.75"),
                            MedicationDoseAssistPreset.GelEquivalentEstradiolMg("1.5"),
                        ),
                        MedicationDoseKind.GEL_PERCENT_AND_WEIGHT to listOf(
                            MedicationDoseAssistPreset.GelPercent(
                                percent = "0.06",
                            ),
                            MedicationDoseAssistPreset.GelPercent(
                                percent = "0.3",
                            ),
                            MedicationDoseAssistPreset.GelPercent(
                                percent = "0.6",
                            ),
                            MedicationDoseAssistPreset.GelWeightGrams("1.25"),
                            MedicationDoseAssistPreset.GelWeightGrams("2.5"),
                        )
                    ),
                ),
            ),
            allowCustomMedicationName = false,
        ),
        MedicationApplicationCatalog(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.PATCH_ON,
            entries = listOf(
                MedicationCatalogEntry(
                    medicationKey = MedicationKey.ESTRADIOL_PATCH,
                    doseKinds = setOf(
                        MedicationDoseKind.PATCH_TOTAL_MG,
                        MedicationDoseKind.PATCH_RELEASE_RATE_MCG_DAY,
                    ),
                    defaultDoseKind = MedicationDoseKind.PATCH_RELEASE_RATE_MCG_DAY,
                    doseAssistPresets = mapOf(
                        MedicationDoseKind.PATCH_RELEASE_RATE_MCG_DAY to listOf(
                            MedicationDoseAssistPreset.PatchReleaseRateMcgPerDay("50"),
                            MedicationDoseAssistPreset.PatchReleaseRateMcgPerDay("75"),
                            MedicationDoseAssistPreset.PatchReleaseRateMcgPerDay("100"),
                        ),
                        MedicationDoseKind.PATCH_TOTAL_MG to listOf(
                            MedicationDoseAssistPreset.PatchTotalMg("0.36"),
                            MedicationDoseAssistPreset.PatchTotalMg("0.72"),
                        ),
                    ),
                ),
            ),
            allowCustomMedicationName = false,
        ),
        MedicationApplicationCatalog(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.PATCH_OFF,
            entries = listOf(
                MedicationCatalogEntry(
                    medicationKey = MedicationKey.ESTRADIOL_PATCH,
                    doseKinds = setOf(MedicationDoseKind.NONE),
                    defaultDoseKind = MedicationDoseKind.NONE,
                ),
            ),
            allowCustomMedicationName = false,
        ),
    )

    private val antiandrogenCatalog = listOf(
        MedicationApplicationCatalog(
            category = MedicationCategory.ANTIANDROGEN,
            applicationType = MedicationApplicationType.ORAL,
            entries = listOf(
                MedicationCatalogEntry(
                    medicationKey = MedicationKey.SPIRONOLACTONE,
                    doseKinds = setOf(MedicationDoseKind.MG_AS_MEDICINE),
                    defaultDoseKind = MedicationDoseKind.MG_AS_MEDICINE,
                    doseAssistPresets = mgDoseAssistPresets("100", "200"),
                ),
                MedicationCatalogEntry(
                    medicationKey = MedicationKey.CYPROTERONE_ACETATE,
                    doseKinds = setOf(MedicationDoseKind.MG_AS_MEDICINE),
                    defaultDoseKind = MedicationDoseKind.MG_AS_MEDICINE,
                    doseAssistPresets = mgDoseAssistPresets("6.25", "12.5"),
                ),
                MedicationCatalogEntry(
                    medicationKey = MedicationKey.BICALUTAMIDE,
                    doseKinds = setOf(MedicationDoseKind.MG_AS_MEDICINE),
                    defaultDoseKind = MedicationDoseKind.MG_AS_MEDICINE,
                    doseAssistPresets = mgDoseAssistPresets("25", "50"),
                ),
            ),
            allowCustomMedicationName = false,
        ),
    )

    private val testosteroneCatalog = MedicationApplicationType.entries.map { applicationType ->
        MedicationApplicationCatalog(
            category = MedicationCategory.TESTOSTERONE,
            applicationType = applicationType,
            entries = defaultEntriesFor(applicationType),
            allowCustomMedicationName = true,
        )
    }

    private val customCatalog = listOf(
        MedicationApplicationCatalog(
            category = MedicationCategory.CUSTOM,
            applicationType = MedicationApplicationType.ORAL,
            entries = defaultEntriesFor(MedicationApplicationType.ORAL),
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

    private fun defaultEntriesFor(
        applicationType: MedicationApplicationType,
    ): List<MedicationCatalogEntry> {
        return when (applicationType) {
            MedicationApplicationType.ORAL,
            MedicationApplicationType.SUBLINGUAL,
            MedicationApplicationType.INJECTION -> listOf(
                MedicationCatalogEntry(
                    medicationKey = null,
                    doseKinds = setOf(MedicationDoseKind.MG_AS_MEDICINE),
                    defaultDoseKind = MedicationDoseKind.MG_AS_MEDICINE,
                ),
            )

            MedicationApplicationType.GEL -> listOf(
                MedicationCatalogEntry(
                    medicationKey = null,
                    doseKinds = setOf(
                        MedicationDoseKind.GEL_EQUIVALENT_ESTRADIOL_MG,
                        MedicationDoseKind.GEL_PERCENT_AND_WEIGHT,
                    ),
                    defaultDoseKind = MedicationDoseKind.GEL_EQUIVALENT_ESTRADIOL_MG,
                ),
            )

            MedicationApplicationType.PATCH_ON -> listOf(
                MedicationCatalogEntry(
                    medicationKey = null,
                    doseKinds = setOf(
                        MedicationDoseKind.PATCH_TOTAL_MG,
                        MedicationDoseKind.PATCH_RELEASE_RATE_MCG_DAY,
                    ),
                    defaultDoseKind = MedicationDoseKind.PATCH_RELEASE_RATE_MCG_DAY,
                ),
            )

            MedicationApplicationType.PATCH_OFF -> listOf(
                MedicationCatalogEntry(
                    medicationKey = null,
                    doseKinds = setOf(MedicationDoseKind.NONE),
                    defaultDoseKind = MedicationDoseKind.NONE,
                ),
            )
        }
    }
}

private fun mgDoseAssistPresets(vararg valuesMg: String): Map<MedicationDoseKind, List<MedicationDoseAssistPreset>> {
    return mapOf(
        MedicationDoseKind.MG_AS_MEDICINE to valuesMg.map { valueMg ->
            MedicationDoseAssistPreset.MgAsMedicine(valueMg)
        }
    )
}
