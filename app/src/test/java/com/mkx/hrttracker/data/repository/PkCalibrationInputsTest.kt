package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.testCustomMedicine
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import java.time.Instant
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
}
