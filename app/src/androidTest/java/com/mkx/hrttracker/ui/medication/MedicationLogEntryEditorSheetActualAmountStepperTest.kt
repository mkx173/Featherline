package com.mkx.hrttracker.ui.medication

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineDisplayDoseUnit
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class MedicationLogEntryEditorSheetActualAmountStepperTest {
    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun newMultiUseVialLogShowsActualAmountStepperWhenAllowed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val actualAmount = context.getString(R.string.medication_log_actual_amount)
        val decreaseActualAmount = context.getString(R.string.medication_log_actual_amount_decrease)
        val increaseActualAmount = context.getString(R.string.medication_log_actual_amount_increase)

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                MedicationLogEntryEditorSheet(
                    title = "Add Entry",
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    confirmButtonText = "Save",
                    onDismissRequest = { },
                    onCloseClick = { },
                    medicineDraft = defaultMedicineDraft(),
                    doseInstructionDraft = doseInstructionDraftFromInstruction(
                        applicationType = MedicationApplicationType.INJECTION,
                        preparationType = MedicinePreparationType.INJECTION_MULTI_USE_VIAL,
                        doseInstruction = DoseInstruction.VolumeMl(0.5),
                    ),
                    lockedMedicine = multiUseVialMedicine(),
                    allowsActualDoseDelta = true,
                    doseAmountDelta = 0.1,
                    effectiveActualAmount = 0.6,
                    countText = "1",
                    appliedDate = LocalDate.of(2026, 4, 22),
                    appliedTime = LocalTime.of(21, 15),
                    onAppliedDateChange = { },
                    onAppliedTimeChange = { },
                    onAdjustDoseAmountDelta = { },
                    onConfirm = { },
                )
            }
        }

        composeRule
            .onNodeWithText(actualAmount)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("0.6 mL", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("+0.1 mL", useUnmergedTree = true).assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(decreaseActualAmount, useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(increaseActualAmount, useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun actualAmountStepperIsAbsentWhenDeltaIsNotAllowed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val actualAmount = context.getString(R.string.medication_log_actual_amount)
        val decreaseActualAmount = context.getString(R.string.medication_log_actual_amount_decrease)
        val increaseActualAmount = context.getString(R.string.medication_log_actual_amount_increase)

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                MedicationLogEntryEditorSheet(
                    title = "Edit Entry",
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    confirmButtonText = "Save",
                    onDismissRequest = { },
                    onCloseClick = { },
                    medicineDraft = defaultMedicineDraft(),
                    doseInstructionDraft = doseInstructionDraftFromInstruction(
                        applicationType = MedicationApplicationType.INJECTION,
                        preparationType = MedicinePreparationType.INJECTION_MULTI_USE_VIAL,
                        doseInstruction = DoseInstruction.VolumeMl(0.5),
                    ),
                    lockedMedicine = multiUseVialMedicine(),
                    allowsActualDoseDelta = false,
                    doseAmountDelta = null,
                    effectiveActualAmount = 0.5,
                    countText = "1",
                    appliedDate = LocalDate.of(2026, 4, 22),
                    appliedTime = LocalTime.of(21, 15),
                    onAppliedDateChange = { },
                    onAppliedTimeChange = { },
                    onAdjustDoseAmountDelta = { },
                    onConfirm = { },
                )
            }
        }

        composeRule.onNodeWithText(actualAmount, useUnmergedTree = true).assertDoesNotExist()
        composeRule
            .onNodeWithContentDescription(decreaseActualAmount, useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule
            .onNodeWithContentDescription(increaseActualAmount, useUnmergedTree = true)
            .assertDoesNotExist()
    }
}

private fun multiUseVialMedicine(): Medicine {
    val preparation = MedicinePreparation.InjectionMultiUseVial(
        concentrationMgPerMl = 40.0,
        vialVolumeMl = 5.0,
    )
    return Medicine(
        uuid = UUID.fromString("00000000-0000-0000-0000-000000000091"),
        selection = MedicineSelection.Catalog(MedicationKey.ESTRADIOL),
        category = MedicationCategory.ESTRADIOL,
        preparation = preparation,
        displayName = null,
        identityKey = MedicineIdentityKey.catalog(
            medicationKey = MedicationKey.ESTRADIOL,
            preparation = preparation,
        ),
        displayDoseUnit = MedicineDisplayDoseUnit.MG,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        archivedAt = null,
        stock = MedicineStock(),
    )
}
