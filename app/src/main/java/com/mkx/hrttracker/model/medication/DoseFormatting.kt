package com.mkx.hrttracker.model.medication

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.util.Locale

fun Double.formatDose(locale: Locale): String {
    val exact = BigDecimal.valueOf(this)
    val twoDecimals = exact.setScale(2, RoundingMode.HALF_UP)
    // 2-decimal rounding keeps repeating tails (e.g. 1/3 of a tablet) clean, but
    // would floor a genuinely tiny dose like 0.0002 mg to "0". When that happens,
    // fall back to significant-figure rounding so the value still shows.
    val rounded = if (twoDecimals.signum() == 0 && exact.signum() != 0) {
        exact.round(MathContext(2))
    } else {
        twoDecimals
    }
    val normalized = rounded
        .stripTrailingZeros()
        .toPlainString()
    val decimalSeparator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    return if (decimalSeparator == '.') {
        normalized
    } else {
        normalized.replace('.', decimalSeparator)
    }
}
