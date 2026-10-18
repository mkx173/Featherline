package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.pk.E2CalibrationMetadata
import com.mkx.hrttracker.model.pk.PkCalibrationInput
import com.mkx.hrttracker.model.pk.PkCalibrationLab
import com.mkx.hrttracker.model.pk.PkMedicationSimulation
import com.mkx.hrttracker.model.pk.buildEstradiolPkDoseEvent
import java.time.Duration
import java.time.Instant

/**
 * Doses older than this before the earliest lab cannot shape any lab, so the
 * fit (and the dose reads feeding it) start here. Every estimate reads the
 * same horizon before its window: 180 d covers steady state for every route.
 */
val PK_CALIBRATION_DOSE_LOOKBACK: Duration = Duration.ofDays(180L)

/**
 * Start of the dose window the fit needs; null without labs, where no window
 * matters: the solve then only tells no dose history from no labs.
 */
fun calibrationDoseWindowStartEpochMillis(labs: List<PkCalibrationLab>): Long? =
    labs.minOfOrNull { it.collectedAtEpochMillis }?.minus(PK_CALIBRATION_DOSE_LOOKBACK.toMillis())

/**
 * The one calibration input recipe: the rebuild worker's run solves from it,
 * and its fitId tells when the published fit can stand in for the solve. A dose
 * entry that cannot become a PK event is skipped, the same way the Home
 * projection skips it, instead of turning calibration off.
 */
fun buildPkCalibrationInput(
    labs: List<PkCalibrationLab>,
    entries: List<MedicationLogEntry>,
    weightKg: Double?,
    metadata: List<E2CalibrationMetadata>,
): PkCalibrationInput {
    val windowStart = calibrationDoseWindowStartEpochMillis(labs) ?: Long.MIN_VALUE
    val allEstradiolEntries = entries.filter { it.category == MedicationCategory.ESTRADIOL }
    // Every dose older than the window: keep the latest so the state reads as
    // labs below the modeled signal, not as no dose logged. The worker's run
    // reads the window plus the latest dose, so it always has this entry.
    val estradiolEntries = allEstradiolEntries
        .filter { it.appliedAt.toEpochMilli() >= windowStart }
        .ifEmpty { listOfNotNull(allEstradiolEntries.maxByOrNull { it.appliedAt }) }
    // With no lab and no dose there is nothing to place, so any origin is the
    // same fit; a fixed one keeps the input, and the stored fit, off the clock.
    val origin = (labs.map { it.collectedAtEpochMillis } +
            estradiolEntries.map { it.appliedAt.toEpochMilli() })
        .minOrNull() ?: 0L
    val anchor = Instant.ofEpochMilli(origin)
    val doseEvents = estradiolEntries.mapNotNull { entry ->
        entry.buildEstradiolPkDoseEvent(anchor)
    }
    return PkCalibrationInput(
        labs = labs,
        doseEvents = doseEvents,
        originEpochMillis = origin,
        // Same resolution as the Home projection: an unset Current Weight
        // falls back to the app-wide default.
        weightKg = PkMedicationSimulation.resolvedBodyWeightKg(weightKg),
        metadata = metadata,
    )
}
