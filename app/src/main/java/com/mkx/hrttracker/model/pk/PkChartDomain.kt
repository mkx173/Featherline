package com.mkx.hrttracker.model.pk

import java.util.TreeSet

/**
 * Presentation-free, exact chart range and sampling recipe for one calibration render.
 * Uniform samples are augmented by caller-provided knots here and by in-range dose
 * instants in [PkCalibrationRenderer].
 */
@ConsistentCopyVisibility
data class PkChartDomain private constructor(
    val rangeStartEpochMillis: Long,
    val rangeEndEpochMillis: Long,
    val samplingIntervalMillis: Long,
    val knotEpochMillis: List<Long>,
) {
    companion object {
        /** Inclusive grid; the final endpoint is always present. */
        fun create(
            rangeStartEpochMillis: Long,
            rangeEndEpochMillis: Long,
            samplingIntervalMillis: Long,
            protectedKnotEpochMillis: List<Long> = emptyList(),
        ): PkChartDomain? {
            val rangeMillis = rangeEndEpochMillis - rangeStartEpochMillis
            if (rangeMillis <= 0L || samplingIntervalMillis <= 0L) return null
            if (protectedKnotEpochMillis.any { it !in rangeStartEpochMillis..rangeEndEpochMillis }) {
                return null
            }
            if (rangeMillis / samplingIntervalMillis > MaxChartDomainKnots) return null
            val knots = TreeSet<Long>()
            var knot = rangeStartEpochMillis
            while (knot < rangeEndEpochMillis) {
                knots += knot
                knot += samplingIntervalMillis
            }
            knots += rangeEndEpochMillis
            knots += protectedKnotEpochMillis
            if (knots.size > MaxChartDomainKnots) return null
            return PkChartDomain(
                rangeStartEpochMillis = rangeStartEpochMillis,
                rangeEndEpochMillis = rangeEndEpochMillis,
                samplingIntervalMillis = samplingIntervalMillis,
                knotEpochMillis = knots.toList(),
            )
        }
    }
}

internal const val MaxChartDomainKnots = 10_000
