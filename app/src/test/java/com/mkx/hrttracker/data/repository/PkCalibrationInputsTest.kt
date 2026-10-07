package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
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
        // A legacy row with no medicine on a non-patch-removal route has no
        // compound, so it cannot become a PK event.
        val legacy = testMedicationLogEntry(
            medicine = null,
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
            sourceGroupUuid = null,
            appliedAt = Instant.parse("2026-01-02T08:00:00Z"),
        )

        val input = buildPkCalibrationInput(
            labs = emptyList(),
            entries = listOf(convertible, legacy),
            weightKg = 60.0,
            metadata = emptyList(),
            fallbackOriginEpochMillis = 0L,
        )

        assertEquals(listOf(convertible.uuid), input.doseEvents.map { it.id })
    }
}
