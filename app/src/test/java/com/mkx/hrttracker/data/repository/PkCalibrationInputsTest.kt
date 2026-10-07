package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.testCustomMedicine
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.pk.PkCalibrationLab
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class PkCalibrationInputsTest {

    @Test
    fun buildPkCalibrationInput_skipsDoseThatCannotBecomePkEvent() {
        val convertible = testMedicationLogEntry(
            medicine = testMedicine(),
            sourceGroupUuid = null,
            appliedAt = Instant.parse("2026-01-01T08:00:00Z"),
        )
        // A custom estradiol medicine has no catalog compound, so it cannot
        // become a PK event.
        val custom = testMedicationLogEntry(
            medicine = testCustomMedicine(category = MedicationCategory.ESTRADIOL),
            applicationType = MedicationApplicationType.ORAL,
            sourceGroupUuid = null,
            appliedAt = Instant.parse("2026-01-02T08:00:00Z"),
        )

        val input = buildPkCalibrationInput(
            labs = emptyList(),
            entries = listOf(convertible, custom),
            weightKg = 60.0,
            metadata = emptyList(),
            fallbackOriginEpochMillis = 0L,
        )

        assertEquals(listOf(convertible.uuid), input.doseEvents.map { it.id })
    }

    @Test
    fun buildPkCalibrationInput_dropsDosesTooOldToShapeAnyLab() {
        val labAt = Instant.parse("2026-06-01T08:00:00Z")
        val lab = PkCalibrationLab(UUID(0L, 1L), labAt.toEpochMilli(), 100.0)
        val tooOld = testMedicationLogEntry(
            medicine = testMedicine(),
            sourceGroupUuid = null,
            appliedAt = labAt.minus(PK_CALIBRATION_DOSE_LOOKBACK).minusSeconds(1),
        )
        val inWindow = testMedicationLogEntry(
            medicine = testMedicine(),
            sourceGroupUuid = null,
            appliedAt = labAt.minus(PK_CALIBRATION_DOSE_LOOKBACK),
        )

        val input = buildPkCalibrationInput(
            labs = listOf(lab),
            entries = listOf(tooOld, inWindow),
            weightKg = 60.0,
            metadata = emptyList(),
            fallbackOriginEpochMillis = 0L,
        )

        assertEquals(listOf(inWindow.uuid), input.doseEvents.map { it.id })
        assertEquals(inWindow.appliedAt.toEpochMilli(), input.originEpochMillis)
        // No labs: nothing bounds the doses, the caller's fallback applies.
        assertEquals(7L, calibrationDoseWindowStartEpochMillis(emptyList(), 7L))
    }
}
