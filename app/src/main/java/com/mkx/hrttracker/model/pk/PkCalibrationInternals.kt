package com.mkx.hrttracker.model.pk

internal fun Double.normalizePositiveZero(): Double = if (this == 0.0) 0.0 else this

internal const val MILLIS_PER_HOUR = 3_600_000.0

internal fun epochDifferenceHours(epochMillis: Long, originEpochMillis: Long): Double =
    (epochMillis - originEpochMillis) / MILLIS_PER_HOUR
