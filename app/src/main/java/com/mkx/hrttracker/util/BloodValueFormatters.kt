package com.mkx.hrttracker.util

import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import java.util.Locale
import kotlin.math.round
import kotlin.math.roundToLong

fun calibrationUnitLabel(unit: BloodUnitKey): String {
    return formatCalibrationUnitLabel(unit.storageValue)
}

fun formatCalibrationUnitLabel(unitSnapshot: String): String {
    return when (BloodUnitKey.fromStorageValue(unitSnapshot)) {
        BloodUnitKey.PG_ML -> "pg/mL"
        BloodUnitKey.PMOL_L -> "pmol/L"
        BloodUnitKey.NG_DL -> "ng/dL"
        BloodUnitKey.NMOL_L -> "nmol/L"
        BloodUnitKey.NG_ML -> "ng/mL"
        BloodUnitKey.MIU_L -> "mIU/L"
        BloodUnitKey.MIU_ML -> "mIU/mL"
        BloodUnitKey.IU_L -> "IU/L"
        null -> unitSnapshot
    }
}

fun formatCalibrationNumericValue(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        value.toString()
    }
}

fun formatCalibrationConvertedValue(value: Double): String {
    val roundedValue = when {
        value >= 100.0 -> round(value * 10.0) / 10.0
        value >= 1.0 -> round(value * 100.0) / 100.0
        else -> round(value * 1000.0) / 1000.0
    }
    return formatCalibrationNumericValue(roundedValue)
}

fun formatMainE2ConcentrationValue(
    value: Double,
    displayUnit: BloodUnitKey,
): String {
    return when (displayUnit) {
        BloodUnitKey.PG_ML,
        BloodUnitKey.PMOL_L,
        -> value.roundToLong().toString()

        BloodUnitKey.NG_DL -> {
            val roundedValue = (value * 10.0).roundToLong() / 10.0
            String.format(Locale.US, "%.1f", roundedValue)
        }

        else -> formatCalibrationConvertedValue(value)
    }
}
