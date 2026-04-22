package com.mkx.hrttracker.model.medication

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class MedicationGroupModelsTest {
    @Test
    fun expandedInstances_repeats_each_group_medication_by_count() {
        val estradiol = testMedicationGroupMedication(
            uuid = UUID.fromString("647b5d44-bca8-486a-99ab-0fdc68943f8d"),
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0)
            ),
            count = 2
        )
        val spiro = testMedicationGroupMedication(
            uuid = UUID.fromString("bf0f6178-906a-4702-bc04-4aa8c6ff2347"),
            details = testCatalogMedicationDetails(
                key = MedicationKey.SPIRONOLACTONE,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(50.0)
            )
        )

        val instances = listOf(estradiol, spiro).expandedInstances()

        assertEquals(3, instances.size)
        assertEquals(
            listOf(estradiol.uuid, estradiol.uuid, spiro.uuid),
            instances.map { instance -> instance.groupMedicationUuid }
        )
        assertEquals(3, listOf(estradiol, spiro).totalMedicationCount())
    }
}
