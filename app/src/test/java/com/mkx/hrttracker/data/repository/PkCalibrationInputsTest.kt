package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.testCustomMedicine
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.pk.PkCalibrationEngine
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationInput
import com.mkx.hrttracker.model.pk.PkCalibrationLab
import com.mkx.hrttracker.model.pk.PkCalibrationLabIgnoreReason
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.PkMedicationSimulation
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
            nowEpochMillis = Long.MAX_VALUE,
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
            nowEpochMillis = Long.MAX_VALUE,
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
            nowEpochMillis = Long.MAX_VALUE,
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
            nowEpochMillis = Long.MAX_VALUE,
        )

        assertEquals(PkMedicationSimulation.DefaultBodyWeightKg, input.weightKg, 0.0)
        assertEquals(PkCalibrationGlobalState.NO_DOSE_HISTORY, PkCalibrationEngine.evaluate(input).result.globalState)
    }

    @Test
    fun aLabAfterNow_isLeftOutOfTheFit_untilNowReachesItsTime() {
        val zoneId = ZoneId.systemDefault()
        val now = LocalDateTime.of(2026, 5, 20, 10, 0)
        val estradiol = testMedicine(preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0))
        // A 2 mg tablet every morning, today's included.
        val logged = (0L..60L).map { daysAgo ->
            testMedicationLogEntry(
                medicine = estradiol,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
                sourceGroupUuid = null,
                appliedAt = now.toLocalDate().minusDays(daysAgo).atTime(8, 0).atZone(zoneId).toInstant(),
            )
        }
        fun lab(id: Long, at: LocalDateTime) =
            PkCalibrationLab(UUID(0L, id), at.atZone(zoneId).toInstant().toEpochMilli(), 140.0)
        val past = listOf(lab(1L, now.minusDays(6).withHour(9)), lab(2L, now.minusDays(2).withHour(9)))
        // Entered ahead: a draw booked for two hours from now.
        val future = lab(3L, now.plusHours(2))
        fun inputAt(nowEpochMillis: Long) = buildPkCalibrationInput(
            labs = past + future,
            entries = logged,
            weightKg = 62.0,
            metadata = emptyList(),
            nowEpochMillis = nowEpochMillis,
        )
        fun oralSupport(input: PkCalibrationInput) = PkCalibrationEngine.evaluate(input)
            .result.routeResults.single { row -> row.route == PkCalibrationRoute.ORAL }.supportingLabCount

        val before = inputAt(future.collectedAtEpochMillis - 1L)
        assertEquals(past, before.labs)
        assertEquals(setOf(future.resultId), before.futureLabIds)
        assertEquals(
            PkCalibrationLabIgnoreReason.FUTURE,
            PkCalibrationEngine.evaluate(before).result.ignoredLabs[future.resultId],
        )
        assertEquals(2, oralSupport(before))
        // Until then the clock doesn't move the fit's identity.
        assertEquals(
            E2Derivation.fitId(inputAt(now.atZone(zoneId).toInstant().toEpochMilli())),
            E2Derivation.fitId(before),
        )

        // At its time it joins the fit, which is a new one.
        val at = inputAt(future.collectedAtEpochMillis)
        assertEquals(past + future, at.labs)
        assertEquals(emptySet<UUID>(), at.futureLabIds)
        assertNull(PkCalibrationEngine.evaluate(at).result.ignoredLabs[future.resultId])
        assertEquals(3, oralSupport(at))
        assertNotEquals(E2Derivation.fitId(before), E2Derivation.fitId(at))
    }
}
