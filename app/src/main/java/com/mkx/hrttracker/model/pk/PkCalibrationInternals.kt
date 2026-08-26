package com.mkx.hrttracker.model.pk

import java.util.Collections

/** Shared helpers for the calibration package (ponytail: one copy instead of one per file). */

internal fun Double.normalizePositiveZero(): Double = if (this == 0.0) 0.0 else this

internal fun <T> immutableList(source: List<T>): List<T> {
    return Collections.unmodifiableList(ArrayList(source))
}

internal fun <T> immutableSet(source: Set<T>): Set<T> {
    return Collections.unmodifiableSet(LinkedHashSet(source))
}

internal fun <K, V> immutableMap(source: Map<K, V>): Map<K, V> {
    return Collections.unmodifiableMap(LinkedHashMap(source))
}

internal const val MILLIS_PER_HOUR = 3_600_000.0

internal fun epochDifferenceHours(epochMillis: Long, originEpochMillis: Long): Double? {
    val difference = runCatching { Math.subtractExact(epochMillis, originEpochMillis) }
        .getOrNull() ?: return null
    return (difference / MILLIS_PER_HOUR).takeIf(Double::isFinite)
}
