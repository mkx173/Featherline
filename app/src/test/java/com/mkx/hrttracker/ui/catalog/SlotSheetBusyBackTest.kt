package com.mkx.hrttracker.ui.catalog

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.mkx.hrttracker.model.medication.testMedicine
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * A back swipe on the add-record sheets (Medicines → a medicine, or a new one, in manual-log mode;
 * the onboarding new-medicine sheet is the same CreateMedicineThenDoseSheet) once a save has
 * started. The sheet refuses to hide then, and Material3 doesn't animate its predictive-back shrink
 * back after a refused hide, so the swipe must not reach the sheet's own handler at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SlotSheetBusyBackTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val hideAsks = mutableListOf<SheetValue>()

    @Composable
    private fun sheetState(saving: () -> Boolean) = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value ->
            if (value == SheetValue.Hidden) hideAsks += value
            value != SheetValue.Hidden || !saving()
        },
    )

    private fun swipeBack() {
        composeRule.runOnIdle {
            val window = (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher
            window.dispatchOnBackStarted(BackEventCompat(0f, 800f, 0f, BackEventCompat.EDGE_LEFT))
            window.dispatchOnBackProgressed(BackEventCompat(200f, 800f, 0.5f, BackEventCompat.EDGE_LEFT))
            window.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    // The sheet is up, then the save starts; once it ends, the sheet takes the swipe again.
    private fun assertSwipeIgnoredOnlyWhileSaving(title: String, setSaving: (Boolean) -> Unit) {
        composeRule.onNodeWithText(title).assertIsDisplayed()

        setSaving(true)
        composeRule.waitForIdle()
        swipeBack()
        assertEquals("A swipe while saving reached the sheet", emptyList<SheetValue>(), hideAsks)

        setSaving(false)
        composeRule.waitForIdle()
        swipeBack()
        assertEquals(listOf(SheetValue.Hidden), hideAsks.distinct())
    }

    @Test
    fun existingMedicineManualLog_backSwipeWhileSaving_neverReachesTheSheet() {
        val state = MutableStateFlow(MedicineSlotDraftUiState(
                appliedDate = LocalDate.of(2026, 6, 16),
                appliedTime = LocalTime.of(9, 0),
                appliedZoneId = ZoneId.of("Asia/Tokyo"),
                appliedOffset = null,
            ))
        val viewModel = mockk<MedicineSlotDraftViewModel>(relaxed = true) {
            every { uiState } returns state
        }
        composeRule.setContent {
            ExistingMedicineDoseSheet(
                medicine = testMedicine(),
                sheetState = sheetState { state.value.isSaving },
                onDismissRequest = { },
                onCloseClick = { },
                onConfirm = { },
                mode = MedicineSlotDraftMode.MANUAL_LOG,
                viewModel = viewModel,
            )
        }
        assertSwipeIgnoredOnlyWhileSaving("Add record") { saving ->
            state.value = state.value.copy(isSaving = saving)
        }
    }

    @Test
    fun createMedicineThenDose_backSwipeWhileSaving_neverReachesTheSheet() {
        val state = MutableStateFlow(NewMedicineSlotUiState(
                appliedDate = LocalDate.of(2026, 6, 16),
                appliedTime = LocalTime.of(9, 0),
                appliedZoneId = ZoneId.of("Asia/Tokyo"),
                appliedOffset = null,
            ))
        val viewModel = mockk<NewMedicineSlotViewModel>(relaxed = true) {
            every { uiState } returns state
        }
        composeRule.setContent {
            CreateMedicineThenDoseSheet(
                sheetState = sheetState { state.value.isSaving },
                onDismissRequest = { },
                onCloseClick = { },
                onGroupSlotResolved = { _, _, _ -> },
                viewModel = viewModel,
            )
        }
        assertSwipeIgnoredOnlyWhileSaving("Add medicine") { saving ->
            state.value = state.value.copy(isSaving = saving)
        }
    }
}
