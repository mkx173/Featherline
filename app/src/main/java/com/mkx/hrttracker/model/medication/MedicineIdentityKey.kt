package com.mkx.hrttracker.model.medication

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

fun normalizeCustomMedicationName(value: String): String {
    return value.trim()
        .replace(Regex("\\s+"), " ")
        .lowercase(Locale.ROOT)
}

object MedicineIdentityKey {
    // Stable identity for the global PATCH_OFF singleton. The medicine
    // repository looks this up to enforce one-row-per-database semantics.
    const val PATCH_OFF = "P|PATCH_OFF"

    fun catalog(
        medicationKey: MedicationKey,
        preparation: MedicinePreparation,
    ): String {
        return buildString {
            append("C|")
            append(medicationKey.name)
            append("|")
            append(preparation.type.name)
            appendPreparationFields(preparation)
        }
    }

    fun custom(
        customMedicationName: String,
        preparation: MedicinePreparation,
    ): String {
        return buildString {
            append("X|")
            append(normalizeCustomMedicationName(customMedicationName))
            append("|")
            append(preparation.type.name)
            appendPreparationFields(preparation)
        }
    }

    fun patchOff(): String = PATCH_OFF

    fun canonicalDouble(value: Double): String {
        require(value > 0.0 && !value.isNaN() && !value.isInfinite())
        return BigDecimal.valueOf(value)
            .setScale(6, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }

    private fun StringBuilder.appendPreparationFields(preparation: MedicinePreparation) {
        when (preparation) {
            is MedicinePreparation.Pill -> {
                appendField("strengthMgPerTablet", preparation.strengthMgPerTablet)
            }
            is MedicinePreparation.Capsule -> {
                appendField("strengthMgPerTablet", preparation.strengthMgPerCapsule)
            }
            is MedicinePreparation.InjectionSingleUseVial -> {
                appendField("strengthMgPerVial", preparation.strengthMgPerVial)
            }
            is MedicinePreparation.InjectionMultiUseVial -> {
                appendField("concentrationMgPerMl", preparation.concentrationMgPerMl)
                appendField("vialVolumeMl", preparation.vialVolumeMl)
            }
            is MedicinePreparation.GelSachet -> {
                appendField("concentrationPercent", preparation.concentrationPercent)
                appendField("sachetWeightGrams", preparation.sachetWeightGrams)
            }
            is MedicinePreparation.GelContainer -> {
                appendField("concentrationPercent", preparation.concentrationPercent)
                appendField("containerWeightGrams", preparation.containerWeightGrams)
            }
            is MedicinePreparation.Patch -> {
                when (val specification = preparation.specification) {
                    is MedicinePreparation.PatchSpecification.TotalMg -> {
                        appendField("patchTotalMg", specification.valueMg)
                    }
                    is MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay -> {
                        appendField("patchReleaseRateMcgPerDay", specification.valueMcgPerDay)
                    }
                }
            }
            // The PATCH_OFF singleton always carries the same identity key, so
            // nothing extra to append after the preparation type marker. (Lives
            // in catalog/custom helpers' appendPreparationFields purely for
            // exhaustiveness of the when over MedicinePreparation; the patchOff
            // helper does not call this path.)
            is MedicinePreparation.PatchOff -> Unit
        }
    }

    private fun StringBuilder.appendField(name: String, value: Double) {
        append("|")
        append(name)
        append("=")
        append(canonicalDouble(value))
    }
}
