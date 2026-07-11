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

// Catalog entries carry medication-key identity plus assist-chip presets for
// raw preparation/dose fields. The actual preparation and dose values still
// live in the picker drafts until save.
data class MedicationCatalogEntry(
    val medicationKey: MedicationKey?,
    val doseAssistPresets: Map<MedicinePreparationType, List<MedicationDoseAssistPreset>> = emptyMap(),
)

sealed interface MedicationDoseAssistPreset {
    data class MgAsMedicine(
        val valueMg: String,
    ) : MedicationDoseAssistPreset

    data class GelPercent(
        val percent: String,
    ) : MedicationDoseAssistPreset

    data class GelWeightGrams(
        val weightGrams: String,
    ) : MedicationDoseAssistPreset

    data class GelContainerSizeGrams(
        val weightGrams: String,
    ) : MedicationDoseAssistPreset

    data class MultiUseVialConcentrationMgPerMl(
        val mgPerMl: String,
    ) : MedicationDoseAssistPreset

    data class MultiUseVialVolumeMl(
        val volumeMl: String,
    ) : MedicationDoseAssistPreset

    data class PatchTotalMg(
        val valueMg: String,
    ) : MedicationDoseAssistPreset

    data class PatchReleaseRateMcgPerDay(
        val valueMcgPerDay: String,
    ) : MedicationDoseAssistPreset
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
                    doseAssistPresets = pillMgDoseAssistPresets("1", "2"),
                ),
                MedicationCatalogEntry(
                    medicationKey = MedicationKey.ESTRADIOL,
                    doseAssistPresets = pillMgDoseAssistPresets("1", "2"),
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
                    doseAssistPresets = pillMgDoseAssistPresets("1", "2"),
                ),
                MedicationCatalogEntry(
                    medicationKey = MedicationKey.ESTRADIOL,
                    doseAssistPresets = pillMgDoseAssistPresets("1", "2"),
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
                    doseAssistPresets = singleUseVialMgDoseAssistPresets("5", "10") +
                            multiUseVialDoseAssistPresets(
                                concentrationsMgPerMl = listOf("20", "40"),
                                volumesMl = listOf("5", "10"),
                            ),
                ),
                MedicationCatalogEntry(
                    medicationKey = MedicationKey.ESTRADIOL_CYPIONATE,
                    doseAssistPresets = singleUseVialMgDoseAssistPresets("5", "10") +
                            multiUseVialDoseAssistPresets(
                                concentrationsMgPerMl = listOf("20", "40"),
                                volumesMl = listOf("5", "10"),
                            ),
                ),
                MedicationCatalogEntry(
                    medicationKey = MedicationKey.ESTRADIOL_ENANTHATE,
                    doseAssistPresets = singleUseVialMgDoseAssistPresets("5", "10") +
                            multiUseVialDoseAssistPresets(
                                concentrationsMgPerMl = listOf("20", "40"),
                                volumesMl = listOf("5", "10"),
                            ),
                ),
                MedicationCatalogEntry(
                    medicationKey = MedicationKey.ESTRADIOL_BENZOATE,
                    doseAssistPresets = singleUseVialMgDoseAssistPresets("5", "10") +
                            multiUseVialDoseAssistPresets(
                                concentrationsMgPerMl = listOf("20", "40"),
                                volumesMl = listOf("5", "10"),
                            ),
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
                    doseAssistPresets = gelDoseAssistPresets(),
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
                    doseAssistPresets = patchDoseAssistPresets(),
                ),
            ),
            allowCustomMedicationName = false,
        ),
        // PATCH_OFF retains its catalog entry so legacy slot/log code paths
        // that look up "the catalog for ESTRADIOL + PATCH_OFF" don't NPE.
        // Creating new medicines via this route is blocked by the creation
        // form picker; the PATCH_OFF singleton is the only patch-off Medicine
        // the app produces, and it's auto-created from MedicineRepository.
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
                MedicationCatalogEntry(
                    medicationKey = MedicationKey.SPIRONOLACTONE,
                    doseAssistPresets = pillMgDoseAssistPresets("20", "25", "50", "100"),
                ),
                MedicationCatalogEntry(
                    medicationKey = MedicationKey.CYPROTERONE_ACETATE,
                    doseAssistPresets = pillMgDoseAssistPresets("50", "100"),
                ),
                MedicationCatalogEntry(
                    medicationKey = MedicationKey.BICALUTAMIDE,
                    doseAssistPresets = pillMgDoseAssistPresets("50", "80"),
                ),
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

    private val catalogs =
        estradiolCatalog + antiandrogenCatalog + testosteroneCatalog + customCatalog

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

    // Routes the user can choose for a TABLET-form medicine in this category.
    // Antiandrogen and custom catalogs only register ORAL pills, so picking
    // sublingual for them would be unsupported; callers use this to suppress
    // the route picker when there is no real choice.
    fun tabletRoutesFor(category: MedicationCategory): List<MedicationApplicationType> {
        return applicationTypesFor(category).filter { applicationType ->
            applicationType == MedicationApplicationType.ORAL ||
                    applicationType == MedicationApplicationType.SUBLINGUAL
        }
    }

    fun preparationFormsFor(category: MedicationCategory): List<MedicinePreparationForm> {
        val forms = applicationTypesFor(category)
            .flatMap { applicationType -> applicationType.preparationForms() }
        val capsuleForms = if (category == MedicationCategory.CUSTOM) {
            listOf(MedicinePreparationForm.CAPSULE)
        } else {
            emptyList()
        }

        return (forms + capsuleForms).distinct()
    }

    fun entriesForForm(
        category: MedicationCategory,
        form: MedicinePreparationForm,
    ): List<MedicationCatalogEntry> {
        if (form == MedicinePreparationForm.CAPSULE) {
            return if (category == MedicationCategory.CUSTOM) {
                listOf(MedicationCatalogEntry(medicationKey = null))
            } else {
                emptyList()
            }
        }

        val entries = applicationTypesFor(category)
            .filter { applicationType -> form in applicationType.preparationForms() }
            .flatMap { applicationType ->
                catalogFor(
                    category = category,
                    applicationType = applicationType,
                ).entries
            }

        return mergeCatalogEntries(entries)
    }

    private fun mergeCatalogEntries(entries: List<MedicationCatalogEntry>): List<MedicationCatalogEntry> {
        return entries
            .groupBy(MedicationCatalogEntry::medicationKey)
            .map { (medicationKey, entriesForMedication) ->
                MedicationCatalogEntry(
                    medicationKey = medicationKey,
                    doseAssistPresets = mergeDoseAssistPresets(
                        entriesForMedication.map(MedicationCatalogEntry::doseAssistPresets),
                    ),
                )
            }
    }

    private fun mergeDoseAssistPresets(
        presetMaps: List<Map<MedicinePreparationType, List<MedicationDoseAssistPreset>>>,
    ): Map<MedicinePreparationType, List<MedicationDoseAssistPreset>> {
        return presetMaps
            .flatMap { presetsByPreparation -> presetsByPreparation.entries }
            .groupBy(
                keySelector = { entry -> entry.key },
                valueTransform = { entry -> entry.value },
            )
            .mapValues { (_, presetLists) -> presetLists.flatten().distinct() }
    }

    private fun pillMgDoseAssistPresets(
        vararg valuesMg: String,
    ): Map<MedicinePreparationType, List<MedicationDoseAssistPreset>> {
        return mapOf(
            MedicinePreparationType.PILL to valuesMg.map {
                MedicationDoseAssistPreset.MgAsMedicine(it)
            },
        )
    }

    private fun singleUseVialMgDoseAssistPresets(
        vararg valuesMg: String,
    ): Map<MedicinePreparationType, List<MedicationDoseAssistPreset>> {
        return mapOf(
            MedicinePreparationType.INJECTION_SINGLE_USE_VIAL to valuesMg.map {
                MedicationDoseAssistPreset.MgAsMedicine(it)
            },
        )
    }

    private fun multiUseVialDoseAssistPresets(
        concentrationsMgPerMl: List<String>,
        volumesMl: List<String>,
    ): Map<MedicinePreparationType, List<MedicationDoseAssistPreset>> {
        return mapOf(
            MedicinePreparationType.INJECTION_MULTI_USE_VIAL to (
                    concentrationsMgPerMl.map {
                        MedicationDoseAssistPreset.MultiUseVialConcentrationMgPerMl(it)
                    } + volumesMl.map {
                        MedicationDoseAssistPreset.MultiUseVialVolumeMl(it)
                    }
                    ),
        )
    }

    private fun gelDoseAssistPresets(): Map<MedicinePreparationType, List<MedicationDoseAssistPreset>> {
        val percentPresets = listOf(
            MedicationDoseAssistPreset.GelPercent("0.06"),
            MedicationDoseAssistPreset.GelPercent("0.1"),
            MedicationDoseAssistPreset.GelPercent("0.3"),
            MedicationDoseAssistPreset.GelPercent("0.6"),
        )
        val sachetWeightPresets = listOf(
            MedicationDoseAssistPreset.GelWeightGrams("0.5"),
            MedicationDoseAssistPreset.GelWeightGrams("1.0"),
            MedicationDoseAssistPreset.GelWeightGrams("1.25"),
            MedicationDoseAssistPreset.GelWeightGrams("2.5"),
        )
        val containerSizePresets = listOf(
            MedicationDoseAssistPreset.GelContainerSizeGrams("80"),
        )
        return mapOf(
            MedicinePreparationType.GEL_SACHET to percentPresets + sachetWeightPresets,
            MedicinePreparationType.GEL_CONTAINER to percentPresets + containerSizePresets,
        )
    }

    private fun patchDoseAssistPresets(): Map<MedicinePreparationType, List<MedicationDoseAssistPreset>> {
        return mapOf(
            MedicinePreparationType.PATCH to listOf(
                MedicationDoseAssistPreset.PatchReleaseRateMcgPerDay("50"),
                MedicationDoseAssistPreset.PatchReleaseRateMcgPerDay("75"),
                MedicationDoseAssistPreset.PatchReleaseRateMcgPerDay("100"),
                MedicationDoseAssistPreset.PatchTotalMg("0.36"),
                MedicationDoseAssistPreset.PatchTotalMg("0.72"),
            ),
        )
    }
}

fun MedicationApplicationType.preparationForms(): List<MedicinePreparationForm> {
    return when (this) {
        MedicationApplicationType.ORAL,
        MedicationApplicationType.SUBLINGUAL,
            -> listOf(MedicinePreparationForm.TABLET)

        MedicationApplicationType.INJECTION -> listOf(MedicinePreparationForm.INJECTION)
        MedicationApplicationType.GEL -> listOf(MedicinePreparationForm.GEL)
        MedicationApplicationType.PATCH_ON -> listOf(MedicinePreparationForm.PATCH)
        MedicationApplicationType.PATCH_OFF -> emptyList()
    }
}
