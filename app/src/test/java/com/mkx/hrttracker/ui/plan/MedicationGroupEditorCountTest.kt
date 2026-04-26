package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testCatalogMedicationDetails
import com.mkx.hrttracker.ui.medication.normalizeMedicationCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationGroupEditorCountTest {
    @Test
    fun removeMedicationItem_removes_only_target_row() {
        val unchanged = medication(localId = "keep", count = 3)
        val updated = medication(localId = "change", count = 1)

        val result = removeMedicationItem(
            medications = listOf(unchanged, updated),
            localId = "change"
        )

        assertEquals(listOf(3), result.map { medication -> medication.count })
    }

    @Test
    fun medicationItem_toEditorUiState_coerces_unsupported_routes_to_count_one() {
        val editorState = MedicationGroupMedicationItemUiState(
            localId = "injection",
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL_VALERATE,
                applicationType = MedicationApplicationType.INJECTION,
                dose = MedicationDose.MgAsMedicine(5.0)
            ),
            count = 3
        ).toEditorUiState()

        assertEquals(1, editorState.count)
    }

    @Test
    fun normalizeMedicationCount_preserves_patch_on_counts() {
        assertEquals(
            2,
            normalizeMedicationCount(MedicationApplicationType.PATCH_ON, 2)
        )
        assertEquals(
            1,
            normalizeMedicationCount(MedicationApplicationType.GEL, 2)
        )
    }

    @Test
    fun upsertMedication_merges_duplicate_add_into_existing_count() {
        val existingMedication = medication(
            localId = "existing",
            count = 2,
            doseMg = 1.0
        )
        val duplicateAdd = medication(
            localId = "new",
            count = 1,
            doseMg = 1.0
        )

        val result = upsertMedication(
            medications = listOf(existingMedication),
            savedMedication = duplicateAdd
        )

        assertTrue(result.mergedIntoExisting)
        assertEquals(1, result.medications.size)
        assertEquals("existing", result.resolvedMedication.localId)
        assertEquals(3, result.resolvedMedication.count)
        assertEquals(listOf(3), result.medications.map { medication -> medication.count })
    }

    @Test
    fun upsertMedication_keeps_distinct_dosage_as_separate_entry() {
        val existingMedication = medication(
            localId = "existing",
            count = 2,
            doseMg = 1.0
        )
        val distinctDoseMedication = medication(
            localId = "new",
            count = 1,
            doseMg = 2.0
        )

        val result = upsertMedication(
            medications = listOf(existingMedication),
            savedMedication = distinctDoseMedication
        )

        assertFalse(result.mergedIntoExisting)
        assertEquals(2, result.medications.size)
        assertEquals(listOf(1.0, 2.0), result.medications.map { medication ->
            (medication.details.dose as MedicationDose.MgAsMedicine).valueMg
        })
    }

    @Test
    fun upsertMedication_merges_duplicate_edit_into_existing_count() {
        val existingMedication = medication(
            localId = "existing",
            count = 3,
            doseMg = 1.0
        )
        val editedMedication = medication(
            localId = "edited",
            count = 2,
            doseMg = 2.0
        )

        val result = upsertMedication(
            medications = listOf(existingMedication, editedMedication),
            savedMedication = editedMedication.copy(
                details = testCatalogMedicationDetails(
                    key = MedicationKey.ESTRADIOL,
                    applicationType = MedicationApplicationType.ORAL,
                    dose = MedicationDose.MgAsMedicine(1.0)
                )
            )
        )

        assertTrue(result.mergedIntoExisting)
        assertEquals(1, result.medications.size)
        assertEquals("existing", result.medications.single().localId)
        assertEquals(5, result.medications.single().count)
    }

    private fun medication(
        localId: String,
        count: Int,
        doseMg: Double = 2.0,
    ): MedicationGroupMedicationItemUiState {
        return MedicationGroupMedicationItemUiState(
            localId = localId,
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(doseMg)
            ),
            count = count
        )
    }
}
