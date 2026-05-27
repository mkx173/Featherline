package com.mkx.hrttracker.ui.catalog.stock

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.RunwayProjection
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
import java.time.LocalDate
import java.util.UUID

class AdjustStockSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun receivedPreviewShowsSealedVialCount() {
        // Vial size 5 mL, 2 sealed + 3 received = 5 sealed.
        // Open container is excluded from the preview total.
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
            runway = RunwayProjection.NoSchedule,
            intervalDays = null,
            maxPerAdministration = 0.0,
            state = MedicineStockState.HEALTHY,
        )

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                AdjustStockSheet(
                    projection = projection,
                    initialTab = AdjustSheetTab.RECEIVED,
                    previewRunway = { null },
                    onRecount = { },
                    onReceived = { },
                    onDismissRequest = { },
                )
            }
        }

        // Type "3" into the only field (sealed received).
        composeRule
            .onNode(hasSetTextAction())
            .performTextInput("3")

        composeRule
            .onNodeWithText(
                context.getString(
                    R.string.stock_adjust_after,
                    "5",
                    context.getString(R.string.stock_unit_vials),
                )
            )
            .assertIsDisplayed()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun receivedPreviewUsesProvidedScheduleAwareRunway() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val projection = MedicineStockProjection(
            medicine = patchMedicine(
                stock = MedicineStock(
                    trackingEnabled = true,
                    unitsRemaining = 0.0,
                )
            ),
            dosesPerDayMagnitude = 1.0 / 7.0,
            totalStockUnits = 0.0,
            runway = RunwayProjection.Days(
                days = 0,
                lastFulfillable = LocalDate.of(2026, 1, 1),
            ),
            intervalDays = 7,
            maxPerAdministration = 1.0,
            state = MedicineStockState.HEALTHY,
        )

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                AdjustStockSheet(
                    projection = projection,
                    initialTab = AdjustSheetTab.RECEIVED,
                    previewRunway = {
                        RunwayProjection.Days(
                            days = 21,
                            lastFulfillable = LocalDate.of(2026, 1, 22),
                        )
                    },
                    onRecount = { },
                    onReceived = { },
                    onDismissRequest = { },
                )
            }
        }

        composeRule
            .onNode(hasSetTextAction())
            .performTextInput("4")

        composeRule
            .onNodeWithText(context.getString(R.string.stock_runway_days_remaining, 21))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.stock_runway_days_remaining, 28))
            .assertDoesNotExist()
    }
}

private fun containerMedicine(stock: MedicineStock): Medicine {
    val preparation = MedicinePreparation.InjectionMultiUseVial(
        concentrationMgPerMl = 10.0,
        vialVolumeMl = 5.0,
    )
    return medicine(preparation = preparation, stock = stock)
}

private fun patchMedicine(stock: MedicineStock): Medicine {
    val preparation = MedicinePreparation.Patch(
        MedicinePreparation.PatchSpecification.TotalMg(valueMg = 1.0),
    )
    return medicine(preparation = preparation, stock = stock)
}

private fun medicine(
    preparation: MedicinePreparation,
    stock: MedicineStock,
): Medicine {
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
