package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
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
 * fit (and the dose reads feeding it) start here. Same horizon as the Home
 * projection's input lookback: 180 d covers steady state for every route.
 */
val PK_CALIBRATION_DOSE_LOOKBACK: Duration = Duration.ofDays(180L)

/** Start of the dose window the fit needs; [fallbackStartEpochMillis] when there are no labs. */
fun calibrationDoseWindowStartEpochMillis(
    labs: List<PkCalibrationLab>,
    fallbackStartEpochMillis: Long,
): Long = labs.minOfOrNull { it.collectedAtEpochMillis }
    ?.minus(PK_CALIBRATION_DOSE_LOOKBACK.toMillis())
    ?: fallbackStartEpochMillis

/** E2 built-in results as calibration labs. */
fun List<BloodTestPanel>.toPkCalibrationLabs(): List<PkCalibrationLab> = flatMap { panel ->
    panel.results
        .filter { (it.analyte as? BloodTestResultAnalyte.Builtin)?.key == BloodAnalyteKey.E2 }
        .map { result ->
            PkCalibrationLab(
                resultId = result.uuid,
                collectedAtEpochMillis = panel.collectedAt.toEpochMilli(),
                valuePgml = result.canonicalValue,
            )
        }
}

/**
 * The one calibration input recipe, shared by the Home snapshot refresh and
 * the live calibration surface so both always agree. A dose entry that
 * cannot become a PK event is skipped, the same way the Home projection
 * skips it, instead of turning calibration off.
 */
fun buildPkCalibrationInput(
    labs: List<PkCalibrationLab>,
    entries: List<MedicationLogEntry>,
    weightKg: Double?,
    metadata: List<E2CalibrationMetadata>,
    /** Used only when there are no labs and no doses to anchor the origin. */
    fallbackOriginEpochMillis: Long,
): PkCalibrationInput {
    val windowStart = calibrationDoseWindowStartEpochMillis(labs, Long.MIN_VALUE)
    val estradiolEntries = entries.filter {
        it.category == MedicationCategory.ESTRADIOL && it.appliedAt.toEpochMilli() >= windowStart
    }
    val origin = (labs.map { it.collectedAtEpochMillis } +
            estradiolEntries.map { it.appliedAt.toEpochMilli() })
        .minOrNull() ?: fallbackOriginEpochMillis
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
        weightKg = weightKg?.takeIf { it.isFinite() && it > 0.0 }
            ?: PkMedicationSimulation.DefaultBodyWeightKg,
        metadata = metadata,
    )
}
