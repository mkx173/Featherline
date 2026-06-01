package com.mkx.hrttracker.ui.medication

import com.mkx.hrttracker.data.repository.DoseInstructionCalculator
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import kotlin.math.abs
import kotlin.math.roundToLong

internal const val ACTUAL_DOSE_DELTA_STEP = 0.1

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

// Band proportional to the planned amount, snapped (half-up) to the step so
// endpoints are clean, floored at one step. Ampules are under-draw only.
internal fun actualDoseDeltaRange(
    plannedAmount: Double,
    fraction: Double,
    step: Double,
    underDrawOnly: Boolean,
): ActualDoseDeltaRange {
    val rawBand = abs(plannedAmount) * fraction
    val snappedSteps = (rawBand / step).roundToLong().coerceAtLeast(1L)
    val band = snappedSteps * step
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

internal fun effectiveActualDoseAmount(
    scheduledAmount: Double,
    doseAmountDelta: Double?,
): Double {
    return (scheduledAmount + (doseAmountDelta ?: 0.0))
        .coerceAtLeast(minimumDisplayableActualDoseAmount(scheduledAmount))
}

internal fun adjustedActualDoseDelta(
    scheduledAmount: Double,
    currentDoseAmountDelta: Double?,
    step: Double,
): Double? {
    if (!scheduledAmount.isFinite() || scheduledAmount <= 0.0 || !step.isFinite()) {
        return currentDoseAmountDelta
    }
    val effectiveAmount = effectiveActualDoseAmount(
        scheduledAmount = scheduledAmount,
        doseAmountDelta = (currentDoseAmountDelta ?: 0.0) + step,
    )
    val newDelta = effectiveAmount - scheduledAmount
    return if (abs(newDelta) < DoseInstructionCalculator.MIN_EFFECTIVE_DOSE_EPSILON) {
        null
    } else {
        newDelta
    }
}

private fun minimumDisplayableActualDoseAmount(scheduledAmount: Double): Double {
    return scheduledAmount
        .coerceAtMost(ACTUAL_DOSE_DELTA_STEP)
        .coerceAtLeast(DoseInstructionCalculator.MIN_EFFECTIVE_DOSE_EPSILON)
}
