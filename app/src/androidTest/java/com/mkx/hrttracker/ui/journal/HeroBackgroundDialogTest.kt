package com.mkx.hrttracker.ui.journal

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.PrideFlag
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HeroBackgroundDialogTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun pickingFlagAndPressingDone_confirmsThatFlag() {
        var confirmed: PrideFlag? = PrideFlag.AGENDER
        var confirmedCalled = false
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                HeroBackgroundDialog(
                    current = null,
                    onConfirm = { confirmed = it; confirmedCalled = true },
                    onDismissRequest = {},
                )
            }
        }

        composeRule
            .onNodeWithContentDescription(context.getString(R.string.journal_pride_flag_transgender))
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.journal_done)).performClick()

        composeRule.runOnIdle {
            assertTrue(confirmedCalled)
            assertEquals(PrideFlag.TRANSGENDER, confirmed)
        }
    }

    @Test
    fun pressingCancel_dismissesWithoutConfirming() {
        var confirmedCalled = false
        var dismissed = false
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                HeroBackgroundDialog(
                    current = PrideFlag.RAINBOW,
                    onConfirm = { confirmedCalled = true },
                    onDismissRequest = { dismissed = true },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.cancel)).performClick()

        composeRule.runOnIdle {
            assertTrue(dismissed)
            assertTrue(!confirmedCalled)
        }
    }

    @Test
    fun currentFlag_exposesSelectedSemantics() {
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                HeroBackgroundDialog(
                    current = PrideFlag.RAINBOW,
                    onConfirm = {},
                    onDismissRequest = {},
                )
            }
        }

        composeRule
            .onNodeWithContentDescription(context.getString(R.string.journal_pride_flag_rainbow))
            .assertIsSelected()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }
}
