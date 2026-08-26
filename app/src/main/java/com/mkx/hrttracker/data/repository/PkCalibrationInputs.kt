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
import java.time.Instant

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
 * the live calibration surface so both always agree. Null when a dose entry
 * cannot become a PK event.
 */
fun buildPkCalibrationInput(
    labs: List<PkCalibrationLab>,
    entries: List<MedicationLogEntry>,
    weightKg: Double?,
    metadata: List<E2CalibrationMetadata>,
    /** Used only when there are no labs and no doses to anchor the origin. */
    fallbackOriginEpochMillis: Long,
): PkCalibrationInput? {
    val estradiolEntries = entries.filter { it.category == MedicationCategory.ESTRADIOL }
    val origin = (labs.map { it.collectedAtEpochMillis } +
            estradiolEntries.map { it.appliedAt.toEpochMilli() })
        .minOrNull() ?: fallbackOriginEpochMillis
    val anchor = Instant.ofEpochMilli(origin)
    val doseEvents = estradiolEntries.map { entry ->
        entry.buildEstradiolPkDoseEvent(anchor) ?: return null
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
