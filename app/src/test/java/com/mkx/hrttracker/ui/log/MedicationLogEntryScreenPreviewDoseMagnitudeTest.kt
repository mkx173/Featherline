package com.mkx.hrttracker.ui.log

import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.ui.medication.doseInstructionDraftFromInstruction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MedicationLogEntryScreenPreviewDoseMagnitudeTest {
    @Test
    fun previewDoseMagnitudeForMultiUseVialUsesExactEffectiveActualAmount() {
        val medicine = testMedicine(
            preparation = MedicinePreparation.InjectionMultiUseVial(
                concentrationMgPerMl = 40.0,
                vialVolumeMl = 5.0,
            ),
        )
        val draft = doseInstructionDraftFromInstruction(
            applicationType = MedicationApplicationType.INJECTION,
            preparationType = MedicinePreparationType.INJECTION_MULTI_USE_VIAL,
            doseInstruction = DoseInstruction.VolumeMl(0.125),
        )

        val previewDoseMagnitude = medicationLogEntryPreviewDoseMagnitude(
            isEditing = false,
            medicine = medicine,
            doseInstructionDraft = draft,
            countText = "1",
            allowsActualDoseDelta = true,
            effectiveActualAmount = 0.125,
        )

        assertEquals(0.125, previewDoseMagnitude ?: error("Missing preview dose"), 0.0)
    }

    @Test
    fun previewDoseMagnitudeForSingleUseVialIgnoresTypedCount() {
        val medicine = testMedicine(
            preparation = MedicinePreparation.InjectionSingleUseVial(
                strengthMgPerVial = 10.0,
            ),
        )
        val draft = doseInstructionDraftFromInstruction(
            applicationType = MedicationApplicationType.INJECTION,
            preparationType = MedicinePreparationType.INJECTION_SINGLE_USE_VIAL,
            doseInstruction = DoseInstruction.WholeUnit,
        )

        // Single-use vials no longer expose a count editor: one ampule per
        // administration, regardless of any stale typed count, so the stock
        // preview always deducts a single vial.
        val previewDoseMagnitude = medicationLogEntryPreviewDoseMagnitude(
            isEditing = false,
            medicine = medicine,
            doseInstructionDraft = draft,
            countText = "2",
            allowsActualDoseDelta = true,
            effectiveActualAmount = 10.25,
        )

        assertEquals(1.0, previewDoseMagnitude ?: error("Missing preview dose"), 0.0)
    }

    @Test
    fun previewDoseMagnitudeIsNullWhenEditing() {
        val medicine = testMedicine(
            preparation = MedicinePreparation.GelContainer(
                concentrationPercent = 0.06,
                containerWeightGrams = 80.0,
            ),
        )
        val draft = doseInstructionDraftFromInstruction(
            applicationType = MedicationApplicationType.GEL,
            preparationType = MedicinePreparationType.GEL_CONTAINER,
            doseInstruction = DoseInstruction.WeightGrams(1.25),
        )

        val previewDoseMagnitude = medicationLogEntryPreviewDoseMagnitude(
            isEditing = true,
            medicine = medicine,
            doseInstructionDraft = draft,
            countText = "1",
            allowsActualDoseDelta = true,
            effectiveActualAmount = 1.25,
        )

        assertNull(previewDoseMagnitude)
    }
}
