package com.mkx.hrttracker.model.medication

// Identity used to match planned and logged doses across the Plan UI and PK
// projection. Two medications with the same signature are considered "the
// same dose" — same category/route/selection/dose. Counts are tracked
// separately (e.g., logged count toward required count).
data class MedicationSignature(
    val category: String,
    val applicationType: String,
    val selectionKind: String,
    val medicationKey: String?,
    val normalizedCustomMedicationName: String?,
    val customDoseUnit: String?,
    val doseKind: String,
    val doseValueMg: Double?,
    val doseValuePercent: Double?,
    val doseWeightGrams: Double?,
    val doseReleaseRateMcgPerDay: Double?,
) {
    companion object {
        fun fromGroupMedication(medication: MedicationGroupMedication): MedicationSignature {
            return fromMedicationDetails(medication.details)
        }

        fun fromLogEntry(entry: MedicationLogEntry): MedicationSignature {
            return fromMedicationDetails(entry.details)
        }

        fun fromMedicationDetails(details: MedicationDetails): MedicationSignature {
            val selection = details.selection
            val dose = details.dose
            return MedicationSignature(
                category = details.category.name,
                applicationType = details.applicationType.name,
                selectionKind = selection.kind.name,
                medicationKey = when (selection) {
                    is MedicationSelection.Catalog -> selection.medicationKey.name
                    is MedicationSelection.Custom -> null
                },
                normalizedCustomMedicationName = when (selection) {
                    is MedicationSelection.Catalog -> null
                    is MedicationSelection.Custom -> selection.medicationName.trim().lowercase()
                },
                customDoseUnit = when {
                    selection is MedicationSelection.Custom &&
                        dose is MedicationDose.MgAsMedicine -> details.customDoseUnit.storageValue

                    else -> null
                },
                doseKind = dose.kind.name,
                doseValueMg = when (dose) {
                    is MedicationDose.MgAsMedicine -> dose.valueMg
                    is MedicationDose.GelEquivalentEstradiolMg -> dose.valueMg
                    is MedicationDose.PatchTotalMg -> dose.valueMg
                    else -> null
                },
                doseValuePercent = when (dose) {
                    is MedicationDose.GelPercentAndWeight -> dose.percent
                    else -> null
                },
                doseWeightGrams = when (dose) {
                    is MedicationDose.GelPercentAndWeight -> dose.weightGrams
                    else -> null
                },
                doseReleaseRateMcgPerDay = when (dose) {
                    is MedicationDose.PatchReleaseRateMcgPerDay -> dose.valueMcgPerDay
                    else -> null
                },
            )
        }
    }
}
