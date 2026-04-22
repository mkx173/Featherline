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
