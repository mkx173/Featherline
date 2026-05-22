package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection

object DoseInstructionCalculator {
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

    fun perUnitAmountMg(
        medicine: Medicine,
        doseInstruction: DoseInstruction,
    ): Double? {
        return when (val preparation = medicine.preparation) {
            is MedicinePreparation.Pill -> {
                val fraction = doseInstruction as? DoseInstruction.TabletFraction ?: return null
                preparation.strengthMgPerTablet * fraction.numerator / fraction.denominator
            }
            is MedicinePreparation.InjectionSingleUseVial -> {
                if (doseInstruction == DoseInstruction.WholeUnit) {
                    preparation.strengthMgPerVial
                } else {
                    null
                }
            }
            is MedicinePreparation.InjectionMultiUseVial -> {
                val volume = doseInstruction as? DoseInstruction.VolumeMl ?: return null
                preparation.concentrationMgPerMl * volume.valueMl
            }
            is MedicinePreparation.GelSachet -> {
                if (doseInstruction == DoseInstruction.WholeUnit) {
                    preparation.concentrationPercent * 10.0 * preparation.sachetWeightGrams
                } else {
                    null
                }
            }
            is MedicinePreparation.GelContainer -> {
                val weight = doseInstruction as? DoseInstruction.WeightGrams ?: return null
                preparation.concentrationPercent * 10.0 * weight.valueGrams
            }
            is MedicinePreparation.Patch -> {
                when (val specification = preparation.specification) {
                    is MedicinePreparation.PatchSpecification.TotalMg -> {
                        if (doseInstruction == DoseInstruction.WholeUnit) {
                            specification.valueMg
                        } else {
                            null
                        }
                    }
                    is MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay -> null
                }
            }
        }
    }

    fun totalAmountMg(
        perUnitAmountMg: Double?,
        count: Int,
    ): Double? {
        require(count > 0)
        return perUnitAmountMg?.let { it * count }
    }

    fun perUnitEquivalentE2Mg(
        medicine: Medicine,
        doseInstruction: DoseInstruction,
    ): Double? {
        if (medicine.category != MedicationCategory.ESTRADIOL) {
            return null
        }

        val perUnitAmountMg = perUnitAmountMg(medicine, doseInstruction) ?: return null
        val medicationKey = (medicine.selection as? MedicineSelection.Catalog)?.medicationKey
            ?: return null
        val ratio = equivalenceRatios[medicationKey] ?: return null
        return perUnitAmountMg * ratio
    }
}
