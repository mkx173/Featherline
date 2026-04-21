package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelection

object EstradiolEquivalentCalculator {
    private const val ESTRADIOL_MOLECULAR_WEIGHT = 272.4
    private const val ESTRADIOL_VALERATE_MOLECULAR_WEIGHT = 356.5
    private const val ESTRADIOL_CYPIONATE_MOLECULAR_WEIGHT = 396.6
    private const val ESTRADIOL_BENZOATE_MOLECULAR_WEIGHT = 376.4
    private const val ESTRADIOL_ENANTHATE_MOLECULAR_WEIGHT = 384.5

    private val equivalenceRatios = mapOf(
        MedicationKey.ESTRADIOL to 1.0,
        MedicationKey.ESTRADIOL_VALERATE to ESTRADIOL_MOLECULAR_WEIGHT / ESTRADIOL_VALERATE_MOLECULAR_WEIGHT,
        MedicationKey.ESTRADIOL_BENZOATE to ESTRADIOL_MOLECULAR_WEIGHT / ESTRADIOL_BENZOATE_MOLECULAR_WEIGHT,
        MedicationKey.ESTRADIOL_CYPIONATE to ESTRADIOL_MOLECULAR_WEIGHT / ESTRADIOL_CYPIONATE_MOLECULAR_WEIGHT,
        MedicationKey.ESTRADIOL_ENANTHATE to ESTRADIOL_MOLECULAR_WEIGHT / ESTRADIOL_ENANTHATE_MOLECULAR_WEIGHT,
        MedicationKey.ESTRADIOL_GEL to 1.0,
        MedicationKey.ESTRADIOL_PATCH to null,
    )

    fun calculate(medication: MedicationDetails): Double? {
        if (medication.category != MedicationCategory.ESTRADIOL) {
            return null
        }

        return when (val dose = medication.dose) {
            is MedicationDose.GelEquivalentEstradiolMg -> dose.valueMg
            is MedicationDose.GelPercentAndWeight -> dose.percent * dose.weightGrams * 10.0
            is MedicationDose.PatchReleaseRateMcgPerDay -> null
            is MedicationDose.None -> null
            is MedicationDose.MgAsMedicine,
            is MedicationDose.PatchTotalMg -> {
                val medicationKey = (medication.selection as? MedicationSelection.Catalog)?.medicationKey
                    ?: return null
                val ratio = equivalenceRatios[medicationKey] ?: return null
                val valueMg = when (dose) {
                    is MedicationDose.MgAsMedicine -> dose.valueMg
                    is MedicationDose.PatchTotalMg -> dose.valueMg
                    else -> error("Unsupported dose type")
                }
                valueMg * ratio
            }
        }
    }
}
