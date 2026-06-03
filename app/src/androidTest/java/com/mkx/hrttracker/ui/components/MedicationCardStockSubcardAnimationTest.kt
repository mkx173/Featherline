package com.mkx.hrttracker.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.RunwayProjection
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineDisplayDoseUnit
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class MedicationCardStockSubcardAnimationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun stockSubcardStaysComposedDuringExitTransition() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val chipText = context.getString(R.string.stock_subcard_chip_in_stock)
        val trackedProjection = trackedStockProjection()
        var projection by mutableStateOf<MedicineStockProjection?>(trackedProjection)

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                MedicationCardWithStockSubcard(
                    medicine = trackedProjection.medicine,
                    doseInstruction = DoseInstruction.Noop,
                    applicationType = MedicationApplicationType.ORAL,
                    medicationCount = 1,
                    groupColorKey = null,
                    stockProjection = projection,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    stockSubcardContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier,
                )
            }
        }

        composeRule.onNodeWithText(chipText).assertExists()

        composeRule.runOnIdle {
            projection = null
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithText(chipText).assertExists()

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.waitForIdle()

        composeRule.onNodeWithText(chipText).assertDoesNotExist()
    }
}

private fun trackedStockProjection(): MedicineStockProjection {
    val preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0)
    val medicine = Medicine(
        uuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001"),
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
        stock = MedicineStock(
            trackingEnabled = true,
            unitsRemaining = 10.0,
            unitsLastTotal = 10.0,
        ),
    )
    return MedicineStockProjection(
        medicine = medicine,
        dosesPerDayMagnitude = 1.0,
        totalStockUnits = 10.0,
        runway = RunwayProjection.Days(
            days = 10,
            lastFulfillable = LocalDate.of(2026, 1, 10),
        ),
        intervalDays = null,
        maxPerAdministration = 1.0,
        state = MedicineStockState.HEALTHY,
    )
}
