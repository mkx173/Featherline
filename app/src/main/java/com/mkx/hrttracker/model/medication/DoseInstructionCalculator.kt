package com.mkx.hrttracker.model.medication

object DoseInstructionCalculator {
    internal const val MIN_EFFECTIVE_DOSE_EPSILON = 1e-6

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

    fun effectivePerAdministrationStockAmount(
        preparation: MedicinePreparation,
        doseInstruction: DoseInstruction,
        doseAmountDelta: Double?,
    ): Double? {
        val delta = doseAmountDelta ?: 0.0
        return when {
            preparation is MedicinePreparation.InjectionMultiUseVial &&
                doseInstruction is DoseInstruction.VolumeMl ->
                (doseInstruction.valueMl + delta).coerceAtLeast(MIN_EFFECTIVE_DOSE_EPSILON)
            preparation is MedicinePreparation.GelContainer &&
                doseInstruction is DoseInstruction.WeightGrams ->
                (doseInstruction.valueGrams + delta).coerceAtLeast(MIN_EFFECTIVE_DOSE_EPSILON)
            else -> null
        }
    }

    fun perUnitAmountMg(
        medicine: Medicine,
        doseInstruction: DoseInstruction,
    ): Double? {
        return when (val preparation = medicine.preparation) {
            is MedicinePreparation.Pill -> {
                val fraction = doseInstruction as? DoseInstruction.TabletFraction ?: return null
                preparation.strengthMgPerTablet * fraction.numerator.toDouble() / fraction.denominator
            }
            is MedicinePreparation.Capsule -> {
                if (doseInstruction == DoseInstruction.WholeUnit) {
                    preparation.strengthMgPerCapsule
                } else {
                    null
                }
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
            // PATCH_OFF carries no per-unit mass; the PK simulator routes patch
            // removals on applicationType alone and ignores the dose amount.
            is MedicinePreparation.PatchOff -> null
        }
    }

    // The dose instruction to render for display purposes. The stored
    // instruction always reflects the scheduled amount; for measured forms
    // (multi-use vial volume, gel-container weight) we substitute the actual
    // administered amount (scheduled + delta, clamped positive) so summaries
    // show what was really taken. Ampules carry the delta on the mg line, not
    // a portion, so their WholeUnit instruction is returned unchanged.
    fun effectiveDoseInstructionForDisplay(
        preparation: MedicinePreparation,
        doseInstruction: DoseInstruction,
        doseAmountDelta: Double?,
    ): DoseInstruction {
        if (doseAmountDelta == null) {
            return doseInstruction
        }
        val effectiveAmount = effectivePerAdministrationStockAmount(
            preparation = preparation,
            doseInstruction = doseInstruction,
            doseAmountDelta = doseAmountDelta,
        ) ?: return doseInstruction
        return when (doseInstruction) {
            is DoseInstruction.VolumeMl -> DoseInstruction.VolumeMl(effectiveAmount)
            is DoseInstruction.WeightGrams -> DoseInstruction.WeightGrams(effectiveAmount)
            else -> doseInstruction
        }
    }

    fun perUnitAmountMg(
        medicine: Medicine,
        doseInstruction: DoseInstruction,
        doseAmountDelta: Double?,
    ): Double? {
        if (doseAmountDelta == null) {
            return perUnitAmountMg(medicine = medicine, doseInstruction = doseInstruction)
        }

        return when (val preparation = medicine.preparation) {
            is MedicinePreparation.InjectionSingleUseVial -> {
                if (doseInstruction == DoseInstruction.WholeUnit) {
                    (preparation.strengthMgPerVial + doseAmountDelta)
                        .coerceAtLeast(MIN_EFFECTIVE_DOSE_EPSILON)
                } else {
                    perUnitAmountMg(medicine = medicine, doseInstruction = doseInstruction)
                }
            }
            is MedicinePreparation.InjectionMultiUseVial -> {
                val volumeMl = effectivePerAdministrationStockAmount(
                    preparation = preparation,
                    doseInstruction = doseInstruction,
                    doseAmountDelta = doseAmountDelta,
                ) ?: return perUnitAmountMg(medicine = medicine, doseInstruction = doseInstruction)
                preparation.concentrationMgPerMl * volumeMl
            }
            is MedicinePreparation.GelContainer -> {
                val weightGrams = effectivePerAdministrationStockAmount(
                    preparation = preparation,
                    doseInstruction = doseInstruction,
                    doseAmountDelta = doseAmountDelta,
                ) ?: return perUnitAmountMg(medicine = medicine, doseInstruction = doseInstruction)
                preparation.concentrationPercent * 10.0 * weightGrams
            }
            else -> perUnitAmountMg(medicine = medicine, doseInstruction = doseInstruction)
        }
    }

    fun totalAmountMg(
        perUnitAmountMg: Double?,
        count: Int,
    ): Double? {
        require(count > 0)
        return perUnitAmountMg?.let { it * count }
    }

    fun perUnitReleaseRateMcgPerDay(
        medicine: Medicine,
        doseInstruction: DoseInstruction,
    ): Double? {
        if (doseInstruction != DoseInstruction.WholeUnit) return null
        val patch = medicine.preparation as? MedicinePreparation.Patch ?: return null
        val rate = patch.specification as? MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay
            ?: return null
        return rate.valueMcgPerDay
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

    fun perUnitEquivalentE2Mg(
        medicine: Medicine,
        doseInstruction: DoseInstruction,
        doseAmountDelta: Double?,
    ): Double? {
        if (doseAmountDelta == null) {
            return perUnitEquivalentE2Mg(medicine = medicine, doseInstruction = doseInstruction)
        }

        if (medicine.category != MedicationCategory.ESTRADIOL) {
            return null
        }

        val perUnitAmountMg = perUnitAmountMg(
            medicine = medicine,
            doseInstruction = doseInstruction,
            doseAmountDelta = doseAmountDelta,
        ) ?: return null
        val medicationKey = (medicine.selection as? MedicineSelection.Catalog)?.medicationKey
            ?: return null
        val ratio = equivalenceRatios[medicationKey] ?: return null
        return perUnitAmountMg * ratio
    }
}
