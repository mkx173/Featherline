package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testCatalogMedicationDetails
import com.mkx.hrttracker.model.medication.testCustomMedicationDetails
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
    fun upsertMedication_rejects_duplicate_add_without_changing_existing_count() {
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

        assertTrue(result.duplicateAlreadyExists)
        assertEquals(1, result.medications.size)
        assertEquals("existing", result.medications.single().localId)
        assertEquals(2, result.medications.single().count)
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

        assertFalse(result.duplicateAlreadyExists)
        assertEquals(2, result.medications.size)
        assertEquals(listOf(1.0, 2.0), result.medications.map { medication ->
            (medication.details.dose as MedicationDose.MgAsMedicine).valueMg
        })
    }

    @Test
    fun upsertMedication_rejects_duplicate_edit_without_changing_existing_items() {
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

        assertTrue(result.duplicateAlreadyExists)
        assertEquals(2, result.medications.size)
        assertEquals(listOf("existing", "edited"), result.medications.map { medication ->
            medication.localId
        })
        assertEquals(listOf(3, 2), result.medications.map { medication -> medication.count })
        assertEquals(listOf(1.0, 2.0), result.medications.map { medication ->
            (medication.details.dose as MedicationDose.MgAsMedicine).valueMg
        })
    }

    @Test
    fun upsertMedication_rejects_custom_medication_with_same_normalized_name() {
        // Schedule aggregation and PK matching collapse meds by MedicationSignature,
        // which normalizes custom names via trim().lowercase(). If the editor allowed
        // through near-duplicate customs, the widget snapshot would aggregate them and
        // the quick-log callback would under-log via the single uuid it carries.
        val existingMedication = customMedication(
            localId = "existing",
            name = "Estradiol Valerate",
            doseMg = 2.0,
        )
        val nearDuplicate = customMedication(
            localId = "new",
            name = "  estradiol valerate ",
            doseMg = 2.0,
        )

        val result = upsertMedication(
            medications = listOf(existingMedication),
            savedMedication = nearDuplicate,
        )

        assertTrue(result.duplicateAlreadyExists)
        assertEquals(1, result.medications.size)
        assertEquals("existing", result.medications.single().localId)
    }

    @Test
    fun upsertMedication_keeps_custom_medications_with_distinct_normalized_names() {
        val existingMedication = customMedication(
            localId = "existing",
            name = "Estradiol Valerate",
            doseMg = 2.0,
        )
        val distinctMedication = customMedication(
            localId = "new",
            name = "Estradiol Cypionate",
            doseMg = 2.0,
        )

        val result = upsertMedication(
            medications = listOf(existingMedication),
            savedMedication = distinctMedication,
        )

        assertFalse(result.duplicateAlreadyExists)
        assertEquals(2, result.medications.size)
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

    private fun customMedication(
        localId: String,
        name: String,
        doseMg: Double,
        count: Int = 1,
    ): MedicationGroupMedicationItemUiState {
        return MedicationGroupMedicationItemUiState(
            localId = localId,
            details = testCustomMedicationDetails(
                medicationName = name,
                dose = MedicationDose.MgAsMedicine(doseMg),
            ),
            count = count,
        )
    }
}
