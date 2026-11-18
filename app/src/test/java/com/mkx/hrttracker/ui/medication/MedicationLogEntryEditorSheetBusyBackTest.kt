package com.mkx.hrttracker.ui.medication

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.testMedicine
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

/**
 * A back swipe on the log editor while it saves. The sheet refuses to hide then, and Material3's
 * sheet doesn't animate its predictive-back shrink back after a refused hide, so the swipe must not
 * reach the sheet's own handler at all: one on the activity's window gets no Back while the sheet's
 * window has focus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MedicationLogEntryEditorSheetBusyBackTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val hideAsks = mutableListOf<SheetValue>()
    private var saving by mutableStateOf(false)

    private fun showSheet(isSaving: Boolean) {
        saving = isSaving
        val draft = defaultMedicineDraft(
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
        )
        composeRule.setContent {
            MedicationLogEntryEditorSheet(
                title = "Edit record",
                sheetState = rememberModalBottomSheetState(
                    skipPartiallyExpanded = true,
                    confirmValueChange = { value ->
                        if (value == SheetValue.Hidden) hideAsks += value
                        value != SheetValue.Hidden || !saving
                    },
                ),
                confirmButtonText = "Save",
                onDismissRequest = { },
                onCloseClick = { },
                medicineDraft = draft,
                doseInstructionDraft = draft.toDoseInstructionDraft(),
                lockedMedicine = testMedicine(),
                countText = "1",
                appliedDate = LocalDate.of(2026, 6, 16),
                appliedTime = LocalTime.of(9, 0),
                appliedOffset = null,
                onAppliedDateChange = { },
                onAppliedTimeChange = { },
                isSaving = saving,
                onConfirm = { },
            )
        }
        composeRule.onNodeWithText("Edit record").assertIsDisplayed()
    }

    private fun swipeBack() {
        composeRule.runOnIdle {
            val window = (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher
            window.dispatchOnBackStarted(BackEventCompat(0f, 800f, 0f, BackEventCompat.EDGE_LEFT))
            window.dispatchOnBackProgressed(BackEventCompat(200f, 800f, 0.5f, BackEventCompat.EDGE_LEFT))
            window.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun backSwipeWhileSaving_neverReachesTheSheet() {
        showSheet(isSaving = true)
        swipeBack()
        assertEquals("The sheet took the swipe and tried to hide", emptyList<SheetValue>(), hideAsks)
    }

    // As in the app: the sheet is up, then the save starts; once it ends, the sheet takes the swipe.
    @Test
    fun saveStartsWithTheSheetUp_backSwipeReachesTheSheetOnlyOnceItEnds() {
        showSheet(isSaving = false)

        saving = true
        composeRule.waitForIdle()
        swipeBack()
        assertEquals("The sheet took the swipe and tried to hide", emptyList<SheetValue>(), hideAsks)

        saving = false
        composeRule.waitForIdle()
        swipeBack()
        assertEquals(listOf(SheetValue.Hidden), hideAsks.distinct())
    }

    // Control: not saving, the sheet takes the swipe and hides.
    @Test
    fun backSwipe_hidesTheSheet() {
        showSheet(isSaving = false)
        swipeBack()
        assertEquals(listOf(SheetValue.Hidden), hideAsks.distinct())
    }
}
