package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.testCustomMedicine
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.pk.PkCalibrationEngine
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationLab
import com.mkx.hrttracker.model.pk.PkMedicationSimulation
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        )

        assertEquals(listOf(inWindow.uuid), input.doseEvents.map { it.id })
        assertEquals(inWindow.appliedAt.toEpochMilli(), input.originEpochMillis)

        // Every dose too old: the latest stays so the engine reports the lab
        // as below the modeled signal rather than no dose history.
        val onlyOld = buildPkCalibrationInput(
            labs = listOf(lab),
            entries = listOf(tooOld),
            weightKg = 60.0,
            metadata = emptyList(),
        )
        assertEquals(listOf(tooOld.uuid), onlyOld.doseEvents.map { it.id })
        assertEquals(tooOld.appliedAt.toEpochMilli(), onlyOld.originEpochMillis)
        // No labs: no window, so the build reads none, only the latest dose.
        assertNull(calibrationDoseWindowStartEpochMillis(emptyList()))
    }

    @Test
    fun unsetWeight_fallsBackToTheAppDefault_insteadOfFailingInvalid() {
        // Current Weight never set: calibration resolves the same 70 kg
        // default as the Home projection instead of reporting the whole
        // evaluation as SHARED_INPUT_INVALID ("Check an E2 result").
        val input = buildPkCalibrationInput(
            labs = listOf(PkCalibrationLab(UUID(0L, 1L), Instant.parse("2026-06-01T08:00:00Z").toEpochMilli(), 100.0)),
            entries = emptyList(),
            weightKg = null,
            metadata = emptyList(),
        )

        assertEquals(PkMedicationSimulation.DefaultBodyWeightKg, input.weightKg, 0.0)
        assertEquals(PkCalibrationGlobalState.NO_DOSE_HISTORY, PkCalibrationEngine.evaluate(input).result.globalState)
    }
}
