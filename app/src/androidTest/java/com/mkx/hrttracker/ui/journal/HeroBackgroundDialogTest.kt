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
import com.mkx.hrttracker.model.journal.HeroBackground
import com.mkx.hrttracker.model.journal.PrideFlag
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
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
        var confirmed: HeroBackground = HeroBackground.None
        var confirmedCalled = false
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                HeroBackgroundDialog(
                    current = HeroBackground.DateColor,
                    dateColorKey = MedicationGroupColorKey.ROSE,
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
            assertEquals(HeroBackground.Flag(PrideFlag.TRANSGENDER), confirmed)
        }
    }

    @Test
    fun pickingDateColorAndPressingDone_confirmsDateColor() {
        var confirmed: HeroBackground = HeroBackground.None
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                HeroBackgroundDialog(
                    current = HeroBackground.None,
                    dateColorKey = MedicationGroupColorKey.ROSE,
                    onConfirm = { confirmed = it },
                    onDismissRequest = {},
                )
            }
        }

        composeRule
            .onNodeWithContentDescription(context.getString(R.string.journal_hero_background_date_color))
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.journal_done)).performClick()

        composeRule.runOnIdle {
            assertEquals(HeroBackground.DateColor, confirmed)
        }
    }

    @Test
    fun pressingCancel_dismissesWithoutConfirming() {
        var confirmedCalled = false
        var dismissed = false
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                HeroBackgroundDialog(
                    current = HeroBackground.Flag(PrideFlag.RAINBOW),
                    dateColorKey = MedicationGroupColorKey.ROSE,
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
    fun dateColorIsSecondOptionAndSelectedByDefault() {
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                HeroBackgroundDialog(
                    current = HeroBackground.DateColor,
                    dateColorKey = MedicationGroupColorKey.ROSE,
                    onConfirm = {},
                    onDismissRequest = {},
                )
            }
        }

        val none = context.getString(R.string.journal_hero_background_none)
        val dateColor = context.getString(R.string.journal_hero_background_date_color)
        val transgender = context.getString(R.string.journal_pride_flag_transgender)
        val noneBounds = composeRule.onNodeWithContentDescription(none).fetchSemanticsNode().boundsInRoot
        val dateColorBounds = composeRule
            .onNodeWithContentDescription(dateColor)
            .assertIsSelected()
            .fetchSemanticsNode()
            .boundsInRoot
        val transgenderBounds = composeRule
            .onNodeWithContentDescription(transgender)
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(noneBounds.left < dateColorBounds.left)
        assertTrue(dateColorBounds.left < transgenderBounds.left)
        assertEquals(noneBounds.top, dateColorBounds.top, 1f)
        assertEquals(dateColorBounds.top, transgenderBounds.top, 1f)
    }

    @Test
    fun currentFlag_exposesSelectedSemantics() {
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                HeroBackgroundDialog(
                    current = HeroBackground.Flag(PrideFlag.RAINBOW),
                    dateColorKey = MedicationGroupColorKey.ROSE,
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
