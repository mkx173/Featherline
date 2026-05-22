package com.mkx.hrttracker.model.medication

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class MedicationGroupModelsTest {
    @Test
    fun totalMedicationCount_sums_each_group_medication_count() {
        val estradiol = testMedicationGroupMedication(
            uuid = UUID.fromString("647b5d44-bca8-486a-99ab-0fdc68943f8d"),
            medicine = testMedicine(key = MedicationKey.ESTRADIOL),
            count = 2,
        )
        val spiro = testMedicationGroupMedication(
            uuid = UUID.fromString("bf0f6178-906a-4702-bc04-4aa8c6ff2347"),
            medicine = testMedicine(key = MedicationKey.SPIRONOLACTONE),
        )

        assertEquals(3, listOf(estradiol, spiro).totalMedicationCount())
    }
}
