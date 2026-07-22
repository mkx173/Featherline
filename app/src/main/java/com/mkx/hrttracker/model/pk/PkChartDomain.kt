package com.mkx.hrttracker.model.pk

import java.util.Collections

const val PK_CALIBRATION_RENDER_DOMAIN_DIGEST_SCHEMA = "pk-render-domain-v1"

/**
 * Presentation-free, exact chart range and sampling recipe for one calibration render.
 *
 * Uniform samples are augmented by caller-provided peak-protection knots here and by
 * in-range medication/patch-transition instants in [PkCalibrationRenderer]. The resulting
 * exact ordered knot list is bound into the render-domain digest.
 */
@ConsistentCopyVisibility
data class PkChartDomain private constructor(
    val rangeStartEpochMillis: Long,
    val rangeEndEpochMillis: Long,
    val projectionEndEpochMillis: Long,
    val samplingIntervalMillis: Long,
    val chartGridVersion: String,
    val protectedKnotEpochMillis: List<Long>,
    val knotEpochMillis: List<Long>,
) {
    companion object {
        /**
         * Creates a deterministic inclusive grid. The final endpoint is always present even
         * when the interval does not divide the range exactly.
         */
        fun create(
            rangeStartEpochMillis: Long,
            rangeEndEpochMillis: Long,
            projectionEndEpochMillis: Long = rangeEndEpochMillis,
            samplingIntervalMillis: Long,
            chartGridVersion: String,
            protectedKnotEpochMillis: List<Long> = emptyList(),
        ): PkChartDomain? {
            if (rangeStartEpochMillis !in -JcsSafeIntegerMax..JcsSafeIntegerMax ||
                rangeEndEpochMillis !in -JcsSafeIntegerMax..JcsSafeIntegerMax ||
                projectionEndEpochMillis !in -JcsSafeIntegerMax..JcsSafeIntegerMax
            ) {
                return null
            }
            val rangeMillis = runCatching {
                Math.subtractExact(rangeEndEpochMillis, rangeStartEpochMillis)
            }.getOrNull() ?: return null
            if (rangeMillis <= 0L || samplingIntervalMillis <= 0L) return null
            if (projectionEndEpochMillis !in rangeStartEpochMillis..rangeEndEpochMillis) {
                return null
            }
            if (!chartGridVersion.isStableAsciiIdentity()) return null
            if (protectedKnotEpochMillis.any { epochMillis ->
                    epochMillis !in rangeStartEpochMillis..rangeEndEpochMillis ||
                            epochMillis !in -JcsSafeIntegerMax..JcsSafeIntegerMax
                }
            ) {
                return null
            }

            val uniformInteriorCount = rangeMillis / samplingIntervalMillis
            if (uniformInteriorCount > MaxChartDomainKnots) return null
            val knots = java.util.TreeSet<Long>()
            knots += rangeStartEpochMillis
            var offset = samplingIntervalMillis
            while (offset < rangeMillis) {
                val knot = runCatching {
                    Math.addExact(rangeStartEpochMillis, offset)
                }.getOrNull() ?: return null
                knots += knot
                offset = runCatching { Math.addExact(offset, samplingIntervalMillis) }
                    .getOrNull() ?: break
            }
            knots += rangeEndEpochMillis
            knots += projectionEndEpochMillis
            knots += protectedKnotEpochMillis
            if (knots.size > MaxChartDomainKnots) return null

            val canonicalProtected = protectedKnotEpochMillis.distinct().sorted()
            return PkChartDomain(
                rangeStartEpochMillis = rangeStartEpochMillis,
                rangeEndEpochMillis = rangeEndEpochMillis,
                projectionEndEpochMillis = projectionEndEpochMillis,
                samplingIntervalMillis = samplingIntervalMillis,
                chartGridVersion = chartGridVersion,
                protectedKnotEpochMillis = immutableLongList(canonicalProtected),
                knotEpochMillis = immutableLongList(knots.toList()),
            )
        }
    }
}

internal const val MaxChartDomainKnots = 10_000
private const val JcsSafeIntegerMax = 9_007_199_254_740_991L

private fun immutableLongList(source: List<Long>): List<Long> {
    return Collections.unmodifiableList(ArrayList(source))
}
