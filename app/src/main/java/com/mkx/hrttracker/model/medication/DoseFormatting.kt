package com.mkx.hrttracker.model.medication

import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.util.Locale

fun Double.formatDose(locale: Locale): String {
    val normalized = BigDecimal.valueOf(this)
        .stripTrailingZeros()
        .toPlainString()
    val decimalSeparator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    return if (decimalSeparator == '.') {
        normalized
    } else {
        normalized.replace('.', decimalSeparator)
    }
}

fun MedicationDoseUnit.toCanonicalMg(displayValue: Double): Double {
    return when (this) {
        MedicationDoseUnit.MG -> displayValue
        MedicationDoseUnit.MCG -> displayValue / 1000.0
        MedicationDoseUnit.G -> displayValue * 1000.0
    }
}

fun MedicationDoseUnit.fromCanonicalMg(valueMg: Double): Double {
    return when (this) {
        MedicationDoseUnit.MG -> valueMg
        MedicationDoseUnit.MCG -> valueMg * 1000.0
        MedicationDoseUnit.G -> valueMg / 1000.0
    }
}

fun MedicationDoseUnit.formatDoseFromCanonicalMg(
    valueMg: Double,
    locale: Locale,
): String {
    return fromCanonicalMg(valueMg).formatDose(locale)
}

fun MedicationDetails.customDoseDisplayUnit(): MedicationDoseUnit {
    return if (selection is MedicationSelection.Custom && dose is MedicationDose.MgAsMedicine) {
        customDoseUnit
    } else {
        MedicationDoseUnit.MG
    }
}
