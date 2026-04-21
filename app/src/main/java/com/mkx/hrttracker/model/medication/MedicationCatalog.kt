package com.mkx.hrttracker.model.medication

import androidx.annotation.StringRes
import com.mkx.hrttracker.R

enum class MedicationCategory(@get:StringRes val labelRes: Int) {
    ESTRADIOL(R.string.medication_category_estradiol),
    TESTOSTERONE(R.string.medication_category_testosterone),
    ANTIANDROGEN(R.string.medication_category_antiandrogen),
    CUSTOM(R.string.medication_category_custom);

    companion object {
        fun fromStorageValue(value: String?): MedicationCategory {
            return entries.firstOrNull { it.name == value } ?: CUSTOM
        }
    }
}

enum class MedicationApplicationType(@get:StringRes val labelRes: Int) {
    ORAL(R.string.medication_application_oral),
    SUBLINGUAL(R.string.medication_application_sublingual),
    INJECTION(R.string.medication_application_injection),
    GEL(R.string.medication_application_gel),
    PATCH_ON(R.string.medication_application_patch_on),
    PATCH_OFF(R.string.medication_application_patch_off);

    companion object {
        fun fromStorageValue(value: String?): MedicationApplicationType {
            return entries.firstOrNull { it.name == value } ?: ORAL
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

enum class MedicationKey(
    val category: MedicationCategory,
    @get:StringRes val labelRes: Int,
) {
    SPIRONOLACTONE(
        category = MedicationCategory.ANTIANDROGEN,
        labelRes = R.string.medication_name_spironolactone,
    ),
    CYPROTERONE_ACETATE(
        category = MedicationCategory.ANTIANDROGEN,
        labelRes = R.string.medication_name_cyproterone_acetate,
    ),
    ESTRADIOL(
        category = MedicationCategory.ESTRADIOL,
        labelRes = R.string.medication_name_estradiol,
    ),
    ESTRADIOL_VALERATE(
        category = MedicationCategory.ESTRADIOL,
        labelRes = R.string.medication_name_estradiol_valerate,
    ),
    ESTRADIOL_BENZOATE(
        category = MedicationCategory.ESTRADIOL,
        labelRes = R.string.medication_name_estradiol_benzoate,
    ),
    ESTRADIOL_CYPIONATE(
        category = MedicationCategory.ESTRADIOL,
        labelRes = R.string.medication_name_estradiol_cypionate,
    ),
    ESTRADIOL_ENANTHATE(
        category = MedicationCategory.ESTRADIOL,
        labelRes = R.string.medication_name_estradiol_enanthate,
    ),
    ESTRADIOL_GEL(
        category = MedicationCategory.ESTRADIOL,
        labelRes = R.string.medication_name_estradiol_gel,
    ),
    ESTRADIOL_PATCH(
        category = MedicationCategory.ESTRADIOL,
        labelRes = R.string.medication_name_estradiol_patch,
    );

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
)

data class MedicationCatalogEntry(
    val medicationKey: MedicationKey?,
    val doseKinds: Set<MedicationDoseKind>,
    val defaultDoseKind: MedicationDoseKind,
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
                MedicationCatalogEntry(
                    medicationKey = MedicationKey.ESTRADIOL_VALERATE,
                    doseKinds = setOf(MedicationDoseKind.MG_AS_MEDICINE),
                    defaultDoseKind = MedicationDoseKind.MG_AS_MEDICINE,
                ),
                MedicationCatalogEntry(
                    medicationKey = MedicationKey.ESTRADIOL,
                    doseKinds = setOf(MedicationDoseKind.MG_AS_MEDICINE),
                    defaultDoseKind = MedicationDoseKind.MG_AS_MEDICINE,
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
                ),
                MedicationCatalogEntry(
                    medicationKey = MedicationKey.ESTRADIOL,
                    doseKinds = setOf(MedicationDoseKind.MG_AS_MEDICINE),
                    defaultDoseKind = MedicationDoseKind.MG_AS_MEDICINE,
                ),
            ),
            allowCustomMedicationName = false,
        ),
        MedicationApplicationCatalog(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.INJECTION,
            entries = listOf(
                MedicationCatalogEntry(
                    medicationKey = MedicationKey.ESTRADIOL_BENZOATE,
                    doseKinds = setOf(MedicationDoseKind.MG_AS_MEDICINE),
                    defaultDoseKind = MedicationDoseKind.MG_AS_MEDICINE,
                ),
                MedicationCatalogEntry(
                    medicationKey = MedicationKey.ESTRADIOL_VALERATE,
                    doseKinds = setOf(MedicationDoseKind.MG_AS_MEDICINE),
                    defaultDoseKind = MedicationDoseKind.MG_AS_MEDICINE,
                ),
                MedicationCatalogEntry(
                    medicationKey = MedicationKey.ESTRADIOL_CYPIONATE,
                    doseKinds = setOf(MedicationDoseKind.MG_AS_MEDICINE),
                    defaultDoseKind = MedicationDoseKind.MG_AS_MEDICINE,
                ),
                MedicationCatalogEntry(
                    medicationKey = MedicationKey.ESTRADIOL_ENANTHATE,
                    doseKinds = setOf(MedicationDoseKind.MG_AS_MEDICINE),
                    defaultDoseKind = MedicationDoseKind.MG_AS_MEDICINE,
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
                ),
                MedicationCatalogEntry(
                    medicationKey = MedicationKey.CYPROTERONE_ACETATE,
                    doseKinds = setOf(MedicationDoseKind.MG_AS_MEDICINE),
                    defaultDoseKind = MedicationDoseKind.MG_AS_MEDICINE,
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