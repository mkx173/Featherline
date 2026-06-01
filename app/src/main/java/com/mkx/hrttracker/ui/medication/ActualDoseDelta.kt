package com.mkx.hrttracker.ui.medication

import com.mkx.hrttracker.data.repository.DoseInstructionCalculator
import kotlin.math.abs

internal const val ACTUAL_DOSE_DELTA_STEP = 0.1

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
