package com.mkx.hrttracker.ui.plan

import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.ui.medication.DoseInstructionDraftUiState
import com.mkx.hrttracker.ui.medication.defaultMedicineDraft
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationGroupEditorViewModelTest {
    @Test
    fun resolvedApplicationType_usesDoseDraftPreparationForNullMedicinePatchOff() {
        val medication = MedicationGroupMedicationEditorUiState(
            resolvedMedicine = null,
            medicineDraft = defaultMedicineDraft(
                applicationType = MedicationApplicationType.PATCH_OFF,
            ),
            doseInstructionDraft = DoseInstructionDraftUiState(
                applicationType = MedicationApplicationType.PATCH_OFF,
                preparationType = MedicinePreparationType.PATCH_OFF,
            ),
        )

        assertEquals(
            MedicationApplicationType.PATCH_OFF,
            medication.resolvedApplicationType(),
        )
    }
}
