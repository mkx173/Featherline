package com.mkx.hrttracker.ui.medication

import com.mkx.hrttracker.model.medication.DoseInstructionCalculator
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToLong

internal const val ACTUAL_DOSE_DELTA_STEP = 0.1

// Every 5th step from the planned point (delta 0) is a labeled major tick, so
// majors land on round delta values (0.05 mL, 0.5 g, 0.5 mg for the three forms).
internal const val ACTUAL_DOSE_DELTA_MAJOR_TICK_EVERY = 5L

internal data class ActualDoseDeltaRange(
    val min: Double,
    val max: Double,
    val step: Double,
)

internal data class ActualDoseDeltaFormParams(
    val fraction: Double,
    val step: Double,
    val underDrawOnly: Boolean,
)

// Ruler params for the measured forms the delta applies to; null otherwise.
internal fun actualDoseDeltaFormParams(
    preparationType: MedicinePreparationType?,
): ActualDoseDeltaFormParams? = when (preparationType) {
    MedicinePreparationType.INJECTION_SINGLE_USE_VIAL ->
        ActualDoseDeltaFormParams(fraction = 0.50, step = 0.1, underDrawOnly = true)
    MedicinePreparationType.INJECTION_MULTI_USE_VIAL ->
        ActualDoseDeltaFormParams(fraction = 0.20, step = 0.01, underDrawOnly = false)
    MedicinePreparationType.GEL_CONTAINER ->
        ActualDoseDeltaFormParams(fraction = 0.20, step = 0.1, underDrawOnly = false)
    else -> null
}

// Band proportional to the planned amount, rounded UP to a whole number of
// major-tick intervals so its endpoints always land on a labeled major tick.
// Adjacent majors then always enclose exactly ACTUAL_DOSE_DELTA_MAJOR_TICK_EVERY
// - 1 minor ticks, never a short final segment (e.g. a 0.2 mL plan's 20% band
// of 4 steps rounds up to 5 -> ±0.05 instead of ending mid-segment at ±0.04).
// Floored at one full interval. Ampules are under-draw only.
internal fun actualDoseDeltaRange(
    plannedAmount: Double,
    fraction: Double,
    step: Double,
    underDrawOnly: Boolean,
): ActualDoseDeltaRange {
    val rawSteps = abs(plannedAmount) * fraction / step
    // The epsilon absorbs binary-float noise (e.g. 0.05 / 0.01 landing at
    // 5.0000000001) so a band already spanning a whole number of intervals is
    // not nudged up to the next one.
    val majorIntervals = ceil(rawSteps / ACTUAL_DOSE_DELTA_MAJOR_TICK_EVERY - 1e-9)
        .toLong()
        .coerceAtLeast(1L)
    val band = majorIntervals * ACTUAL_DOSE_DELTA_MAJOR_TICK_EVERY * step
    return ActualDoseDeltaRange(
        min = -band,
        max = if (underDrawOnly) 0.0 else band,
        step = step,
    )
}

// Canonical delta for a tick's actual amount: clamps the resolved actual above
// zero (reusing effectiveActualDoseAmount's floor) and normalizes a near-planned
// value to null (no adjustment).
internal fun doseAmountDeltaForActual(
    scheduledAmount: Double,
    actualAmount: Double,
): Double? {
    if (!scheduledAmount.isFinite() || scheduledAmount <= 0.0 || !actualAmount.isFinite()) {
        return null
    }
    val clampedActual = effectiveActualDoseAmount(
        scheduledAmount = scheduledAmount,
        doseAmountDelta = actualAmount - scheduledAmount,
    )
    val delta = clampedActual - scheduledAmount
    return if (abs(delta) < DoseInstructionCalculator.MIN_EFFECTIVE_DOSE_EPSILON) null else delta
}

// A ruler tick is major (taller + labeled) when it is a band endpoint or its
// delta is a whole multiple of ACTUAL_DOSE_DELTA_MAJOR_TICK_EVERY * step. Ticks
// sit exactly at integer multiples of step, so the multiple test is exact via
// the rounded step count.
internal fun isActualDoseDeltaMajorTick(
    delta: Double,
    step: Double,
    isEndpoint: Boolean,
): Boolean {
    if (isEndpoint) return true
    if (!delta.isFinite() || !step.isFinite() || step <= 0.0) return false
    val steps = (delta / step).roundToLong()
    return steps % ACTUAL_DOSE_DELTA_MAJOR_TICK_EVERY == 0L
}

internal fun effectiveActualDoseAmount(
    scheduledAmount: Double,
    doseAmountDelta: Double?,
): Double {
    return (scheduledAmount + (doseAmountDelta ?: 0.0))
        .coerceAtLeast(minimumDisplayableActualDoseAmount(scheduledAmount))
}

private fun minimumDisplayableActualDoseAmount(scheduledAmount: Double): Double {
    return scheduledAmount
        .coerceAtMost(ACTUAL_DOSE_DELTA_STEP)
        .coerceAtLeast(DoseInstructionCalculator.MIN_EFFECTIVE_DOSE_EPSILON)
}
