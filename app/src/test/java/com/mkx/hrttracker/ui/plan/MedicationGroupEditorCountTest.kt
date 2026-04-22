package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testCatalogMedicationDetails
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationGroupEditorCountTest {
    @Test
    fun incrementMedicationCount_increases_only_target_row() {
        val unchanged = medication(localId = "keep", count = 3)
        val updated = medication(localId = "change", count = 1)

        val result = incrementMedicationCount(
            medications = listOf(unchanged, updated),
            localId = "change"
        )

        assertEquals(listOf(3, 2), result.map { medication -> medication.count })
    }

    @Test
    fun decrementMedicationCountOrRemove_decrements_when_count_above_one() {
        val result = decrementMedicationCountOrRemove(
            medications = listOf(medication(localId = "change", count = 2)),
            localId = "change"
        )

        assertEquals(listOf(1), result.map { medication -> medication.count })
    }

    @Test
    fun decrementMedicationCountOrRemove_removes_when_count_is_one() {
        val result = decrementMedicationCountOrRemove(
            medications = listOf(medication(localId = "change", count = 1)),
            localId = "change"
        )

        assertEquals(emptyList<MedicationGroupMedicationItemUiState>(), result)
    }

    private fun medication(
        localId: String,
        count: Int
    ): MedicationGroupMedicationItemUiState {
        return MedicationGroupMedicationItemUiState(
            localId = localId,
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0)
            ),
            count = count
        )
    }
}
