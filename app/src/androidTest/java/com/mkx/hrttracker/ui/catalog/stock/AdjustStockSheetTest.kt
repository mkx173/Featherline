package com.mkx.hrttracker.ui.catalog.stock

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineDisplayDoseUnit
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.ui.catalog.AdjustSheetTab
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.UUID

class AdjustStockSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun receivedPreviewPreservesOpenContainerInTotal() {
        // Vial size 5 mL, 2 sealed, 2.5 mL open → 12.5 baseline.
        // Receiving 3 more sealed should preview 12.5 + 15 = 27.5.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val projection = MedicineStockProjection(
            medicine = containerMedicine(
                stock = MedicineStock(
                    trackingEnabled = true,
                    unitsRemaining = 2.0,
                    openContainerAmount = 2.5,
                )
            ),
            dosesPerDayMagnitude = 0.0,
            totalStockUnits = 12.5,
            runwayDays = null,
            state = MedicineStockState.HEALTHY,
        )

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                AdjustStockSheet(
                    projection = projection,
                    initialTab = AdjustSheetTab.RECEIVED,
                    onRecount = { },
                    onReceived = { },
                    onDismissRequest = { },
                )
            }
        }

        // Type "3" into the only field (sealed received).
        composeRule
            .onNodeWithText(context.getString(R.string.stock_adjust_field_sealed_received))
            .performTextInput("3")

        composeRule
            .onNodeWithText(context.getString(R.string.stock_adjust_after, "27.50"))
            .assertIsDisplayed()
    }
}

private fun containerMedicine(stock: MedicineStock): Medicine {
    val preparation = MedicinePreparation.InjectionMultiUseVial(
        concentrationMgPerMl = 10.0,
        vialVolumeMl = 5.0,
    )
    return Medicine(
        uuid = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001"),
        selection = MedicineSelection.Catalog(com.mkx.hrttracker.model.medication.MedicationKey.ESTRADIOL),
        category = MedicationCategory.ESTRADIOL,
        preparation = preparation,
        displayName = null,
        identityKey = MedicineIdentityKey.catalog(
            com.mkx.hrttracker.model.medication.MedicationKey.ESTRADIOL,
            preparation,
        ),
        displayDoseUnit = MedicineDisplayDoseUnit.MG,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        archivedAt = null,
        stock = stock,
    )
}
