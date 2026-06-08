package com.mkx.hrttracker.model.medication

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.util.Locale

data class FoldedPortion(val numerator: Long, val denominator: Long) {
    init {
        require(denominator > 0L) { "denominator must be positive" }
        require(longGcd(numerator, denominator) == 1L) { "portion must be reduced" }
    }
}

fun reduceTabletPortion(numerator: Int, denominator: Int, count: Int): FoldedPortion {
    val foldedNumerator = numerator.toLong() * count.toLong()
    val foldedDenominator = denominator.toLong()
    val gcd = longGcd(foldedNumerator, foldedDenominator)
    return FoldedPortion(
        numerator = foldedNumerator / gcd,
        denominator = foldedDenominator / gcd,
    )
}

fun FoldedPortion.isWholeOne(): Boolean = numerator == denominator

fun FoldedPortion.formatPortion(locale: Locale): String = when {
    numerator == denominator -> "1"
    numerator < denominator -> "$numerator/$denominator"
    denominator == 1L -> numerator.toString()
    100L % denominator == 0L -> BigDecimal.valueOf(numerator)
        .divide(BigDecimal.valueOf(denominator))
        .formatNormalizedDecimal(locale)
    else -> "$numerator/$denominator"
}

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
    return normalized.formatWithLocaleDecimalSeparator(locale)
}

private fun BigDecimal.formatNormalizedDecimal(locale: Locale): String {
    val normalized = stripTrailingZeros().toPlainString()
    return normalized.formatWithLocaleDecimalSeparator(locale)
}

private fun String.formatWithLocaleDecimalSeparator(locale: Locale): String {
    val decimalSeparator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    return if (decimalSeparator == '.') {
        this
    } else {
        replace('.', decimalSeparator)
    }
}

private tailrec fun longGcd(a: Long, b: Long): Long {
    val positiveA = kotlin.math.abs(a)
    val positiveB = kotlin.math.abs(b)
    return if (positiveB == 0L) positiveA else longGcd(positiveB, positiveA % positiveB)
}
