package com.mkx.hrttracker.model.pk

/**
 * User-selectable window for the home estradiol chart.
 *
 * Each option owns the full PK sampling recipe used to build the projection
 * cache: visible past/future spans, the dense-sample policy, and whether to
 * inject post-dose offsets for absorption-phase fidelity. The projection span
 * adds a fixed [MainChartProjectionFutureBufferDays] beyond the visible
 * future window so a cached snapshot can serve subsequent days without
 * forcing a re-simulation.
 */
enum class HomeE2ChartWindowOption(
    val pastDays: Long,
    val futureDays: Long,
    val densePolicy: DenseSamplePolicy,
    val includesPostDoseOffsets: Boolean,
) {
    SEVEN_DAYS(
        pastDays = 3L,
        futureDays = 4L,
        densePolicy = DenseSamplePolicy.Interval(hours = 0.1),
        includesPostDoseOffsets = false,
    ),
    THIRTY_DAYS(
        pastDays = 16L,
        futureDays = 14L,
        densePolicy = DenseSamplePolicy.Budget(segmentCount = 2240),
        includesPostDoseOffsets = true,
    );

    val chartWindowDays: Long get() = pastDays + futureDays
    val chartWindowHours: Long get() = chartWindowDays * 24

    companion object {
        fun fromStorageValue(value: String?): HomeE2ChartWindowOption {
            if (value == null) return SEVEN_DAYS
            return entries.firstOrNull { it.name == value } ?: SEVEN_DAYS
        }
    }
}

sealed interface DenseSamplePolicy {
    data class Interval(val hours: Double) : DenseSamplePolicy
    data class Budget(val segmentCount: Int) : DenseSamplePolicy
}

const val MainChartProjectionFutureBufferDays: Long = 10L

fun HomeE2ChartWindowOption.projectionFutureDays(): Long =
    futureDays + MainChartProjectionFutureBufferDays

fun HomeE2ChartWindowOption.projectionWindowHours(): Long =
    (pastDays + projectionFutureDays()) * 24
