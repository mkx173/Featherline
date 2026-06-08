package com.mkx.hrttracker.model.medication

import java.math.BigDecimal
import java.math.BigInteger
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
    // The reduced-form invariant (gcd == 1) makes numerator == denominator only
    // satisfiable by 1/1, which the denominator == 1L branch already renders as
    // "1"; callers also gate on isWholeOne() before formatting a whole portion.
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

// Formats a stock quantity or rate for display: at most two decimals, HALF_UP,
// trailing zeros trimmed, no digit grouping, with the locale's decimal separator.
// Mirrors formatDose's rounding direction so the same value reads identically on
// dose and stock surfaces, but omits formatDose's significant-figure fallback —
// a negligible residual (e.g. float dust from stock math) reads as a clean "0"
// rather than exposing "0.0001".
fun formatStockCount(value: Double, locale: Locale): String {
    val normalized = BigDecimal.valueOf(value)
        .setScale(2, RoundingMode.HALF_UP)
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

// BigInteger.gcd is sign-safe and overflow-free, unlike a hand-rolled Euclid
// using abs() (which overflows on Long.MIN_VALUE). The denominator is always
// positive, so the gcd never exceeds it and toLong() cannot overflow.
private fun longGcd(a: Long, b: Long): Long =
    BigInteger.valueOf(a).gcd(BigInteger.valueOf(b)).toLong()
