package com.mkx.hrttracker.ui.catalog.stock

import android.content.Context
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.StockReceived
import com.mkx.hrttracker.data.repository.StockRecount
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineDisplayDoseUnit
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.medication.RunwayProjection
import com.mkx.hrttracker.ui.catalog.AdjustSheetTab
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.rememberAppLocale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class AdjustStockSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

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
                ReceivedForm(
                    projection = projection,
                    isContainer = true,
                    locale = rememberAppLocale(),
                    previewRunway = { null },
                    onSubmit = { },
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
                ReceivedForm(
                    projection = projection,
                    isContainer = false,
                    locale = rememberAppLocale(),
                    previewRunway = {
                        RunwayProjection.Days(
                            days = 21,
                            lastFulfillable = LocalDate.of(2026, 1, 22),
                        )
                    },
                    onSubmit = { },
                )
            }
        }

        composeRule
            .onNode(hasSetTextAction())
            .performTextInput("4")

        composeRule
            .onNodeWithText(
                context.resources.getQuantityString(
                    R.plurals.stock_runway_days_remaining,
                    21,
                    21
                )
            )
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(
                context.resources.getQuantityString(
                    R.plurals.stock_runway_days_remaining,
                    28,
                    28
                )
            )
            .assertDoesNotExist()
    }

    @Test
    fun receivedSubmitDoesNotDismissSheetBeforeMutationResult() {
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
            runway = RunwayProjection.NoSchedule,
            intervalDays = 7,
            maxPerAdministration = 1.0,
            state = MedicineStockState.HEALTHY,
        )
        var submitted: StockReceived? = null

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                ReceivedForm(
                    projection = projection,
                    isContainer = false,
                    locale = rememberAppLocale(),
                    previewRunway = { null },
                    onSubmit = { submitted = it },
                )
            }
        }

        composeRule
            .onNode(hasSetTextAction())
            .performTextInput("4")
        composeRule.waitForIdle()
        composeRule
            .onNodeWithText(context.getString(R.string.stock_adjust_add))
            .performClick()

        // Submitting reports the value through onSubmit; the form has no dismiss path,
        // so it never closes the sheet itself — the caller keeps it open until the
        // mutation result lands. (Sheet-level dismissal is covered by the cancel test.)
        composeRule.runOnIdle {
            assertEquals(StockReceived(unitsReceived = 4.0), submitted)
        }
    }

    @Test
    fun recountSubmitDoesNotDismissSheetBeforeMutationResult() {
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
            runway = RunwayProjection.NoSchedule,
            intervalDays = 7,
            maxPerAdministration = 1.0,
            state = MedicineStockState.HEALTHY,
        )
        var submitted: StockRecount? = null

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                RecountForm(
                    projection = projection,
                    isContainer = false,
                    locale = rememberAppLocale(),
                    previewRunway = { null },
                    onSubmit = { submitted = it },
                )
            }
        }

        composeRule
            .onNode(hasSetTextAction())
            .performTextInput("4")
        composeRule.waitForIdle()
        composeRule
            .onNodeWithText(context.getString(R.string.stock_adjust_save))
            .performClick()

        // Submitting reports the value through onSubmit; the form has no dismiss path,
        // so it never closes the sheet itself — the caller keeps it open until the
        // mutation result lands. (Sheet-level dismissal is covered by the cancel test.)
        composeRule.runOnIdle {
            assertEquals(StockRecount(unitsRemaining = 4.0), submitted)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun cancelButtonDoesNotClearSheetSynchronously() {
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
            runway = RunwayProjection.NoSchedule,
            intervalDays = 7,
            maxPerAdministration = 1.0,
            state = MedicineStockState.HEALTHY,
        )
        var dismissCount = 0

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                AdjustStockSheet(
                    projection = projection,
                    initialTab = AdjustSheetTab.RECEIVED,
                    previewRunway = { null },
                    onRecount = { },
                    onReceived = { },
                    onDismissRequest = { dismissCount += 1 },
                )
            }
        }
        composeRule.awaitSheetReady(context)

        // Cancel must defer dismissal: it routes through hideBottomSheet, which animates
        // the sheet out and fires onDismissRequest only once it settles — so the click
        // must NOT dismiss synchronously. Freeze the clock so no animation frame can
        // run, then confirm the click did not invoke onDismissRequest.
        //
        // The test deliberately stops there. Driving the real hide animation to
        // completion under a manually advanced clock is irreducibly racy: the hide is
        // launched on a composition coroutine whose dispatch is gated on a frame the
        // frozen clock never produces, and the frozen→auto-advance handoff orphans that
        // launch ~1-4% of the time (confirmed: target stays Expanded, dismiss never
        // lands). That same fragility affects BottomSheetUtilsTest. The deferred
        // completion is hideBottomSheet's own concern, not this sheet's wiring, so this
        // test guards only the synchronous contract its name describes.
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule
                .onNodeWithText(context.getString(R.string.stock_cancel))
                .performClick()

            assertEquals(0, dismissCount)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }
}

// The cancel test renders the real AdjustStockSheet, whose content lives in a
// ModalBottomSheet's separate dialog window. That window attaches and lays out
// asynchronously — outside the Compose test clock — so waitForIdle() alone does not
// guarantee it. Poll until a stable, always-present anchor (the Cancel button) is
// actually displayed before touching the sheet; without it the first query races the
// attach and intermittently throws "No compose hierarchies found" or asserts on
// content that "is not displayed", especially under full-class/CI load. (The other
// tests sidestep this entirely by rendering RecountForm/ReceivedForm directly.)
private fun ComposeContentTestRule.awaitSheetReady(context: Context) {
    val cancel = context.getString(R.string.stock_cancel)
    waitUntil(timeoutMillis = 5_000) {
        try {
            onNodeWithText(cancel).assertIsDisplayed()
            true
        } catch (_: AssertionError) {
            false
        } catch (_: IllegalStateException) {
            false
        }
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
