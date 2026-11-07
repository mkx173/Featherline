package com.mkx.hrttracker.ui.journal

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Save hands the date over and starts the sheet's hide; Save again during the hide adds nothing. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AddDateSheetConfirmTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun saveActivatedAgainWhileTheSheetHides_addsTheDateOnce() {
        var confirms = 0
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                AddDateSheet(
                    today = LocalDate.of(2026, 6, 16),
                    anchor = null,
                    initiallyPinned = false,
                    onDismissRequest = {},
                    onConfirm = { _, _, _, _, _ -> confirms++ },
                )
            }
        }
        composeRule.onNodeWithTag(AddDateNameFieldTestTag).performTextInput("First injection")
        composeRule.onNodeWithText("Save").assertIsEnabled()

        // Activated the way TalkBack or a keyboard does: a touch on the hiding sheet catches its
        // animation instead of reaching Save, but these still reach the button.
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Save").performSemanticsAction(SemanticsActions.OnClick)
        // 100 ms into the hide animation, the sheet and its Save button are still there.
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.onNodeWithText("Save").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        assertEquals("Save again during the hide must not add the date again", 1, confirms)
    }
}
