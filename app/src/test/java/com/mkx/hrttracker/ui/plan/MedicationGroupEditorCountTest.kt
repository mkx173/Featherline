package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.ui.medication.defaultMedicineDraft
import com.mkx.hrttracker.ui.medication.normalizeMedicationCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class MedicationGroupEditorCountTest {

    // A shared estradiol medicine: two slots resolving to the same medicine,
    // route, and dose instruction collapse to one MedicationSignature.
    private val estradiolMedicine = testMedicine(
        uuid = UUID.fromString("aaaa0000-0000-0000-0000-000000000001"),
        key = MedicationKey.ESTRADIOL,
    )

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
        // GEL is currently the only non-PATCH_OFF unsupported route; INJECTION
        // now supports a count editor ("N vials" / "N injections of dose").
        val editorState = MedicationGroupMedicationItemUiState(
            localId = "gel",
            resolvedMedicine = testMedicine(key = MedicationKey.ESTRADIOL),
            applicationType = MedicationApplicationType.GEL,
            doseInstruction = DoseInstruction.WeightGrams(2.5),
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
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
        )
        val duplicateAdd = medication(
            localId = "new",
            count = 1,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
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
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
        )
        val distinctDoseMedication = medication(
            localId = "new",
            count = 1,
            doseInstruction = DoseInstruction.TabletFraction(1, 2),
        )

        val result = upsertMedication(
            medications = listOf(existingMedication),
            savedMedication = distinctDoseMedication
        )

        assertFalse(result.duplicateAlreadyExists)
        assertEquals(2, result.medications.size)
        assertEquals(
            listOf(
                DoseInstruction.TabletFraction(1, 1),
                DoseInstruction.TabletFraction(1, 2),
            ),
            result.medications.map { medication -> medication.doseInstruction }
        )
    }

    @Test
    fun upsertMedication_rejects_duplicate_edit_without_changing_existing_items() {
        val existingMedication = medication(
            localId = "existing",
            count = 3,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
        )
        val editedMedication = medication(
            localId = "edited",
            count = 2,
            doseInstruction = DoseInstruction.TabletFraction(1, 2),
        )

        val result = upsertMedication(
            medications = listOf(existingMedication, editedMedication),
            savedMedication = editedMedication.copy(
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
            )
        )

        assertTrue(result.duplicateAlreadyExists)
        assertEquals(2, result.medications.size)
        assertEquals(listOf("existing", "edited"), result.medications.map { medication ->
            medication.localId
        })
        assertEquals(listOf(3, 2), result.medications.map { medication -> medication.count })
        assertEquals(
            listOf(
                DoseInstruction.TabletFraction(1, 1),
                DoseInstruction.TabletFraction(1, 2),
            ),
            result.medications.map { medication -> medication.doseInstruction }
        )
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
        )
        val nearDuplicate = customMedication(
            localId = "new",
            name = "  estradiol valerate ",
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
        )
        val distinctMedication = customMedication(
            localId = "new",
            name = "Estradiol Cypionate",
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
        doseInstruction: DoseInstruction = DoseInstruction.TabletFraction(1, 1),
    ): MedicationGroupMedicationItemUiState {
        return MedicationGroupMedicationItemUiState(
            localId = localId,
            resolvedMedicine = estradiolMedicine,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = doseInstruction,
            count = count
        )
    }

    // A brand-new "+ New" custom slot — no resolved medicine yet; the editor's
    // duplicate key falls back to the picker draft's normalized custom name.
    private fun customMedication(
        localId: String,
        name: String,
        count: Int = 1,
    ): MedicationGroupMedicationItemUiState {
        return MedicationGroupMedicationItemUiState(
            localId = localId,
            resolvedMedicine = null,
            medicineDraft = defaultMedicineDraft(category = MedicationCategory.CUSTOM)
                .copy(customMedicationName = name),
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            count = count,
        )
    }
}
