package com.mkx.hrttracker.ui.main

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The more-options menu is transient chrome: it must not survive leaving the
 * page. Saveable menu state is restored on tab return, re-presenting a menu
 * the user abandoned mid-navigation "out of nowhere" (PR #60 finding 10).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HomeMoreOptionsMenuRestorationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun openMenu_doesNotReappear_afterStateRestoration() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            HomeMoreOptionsMenu(
                selectedUnit = BloodUnitKey.PG_ML,
                onUnitSelected = {},
            )
        }

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Display unit").assertExists()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Display unit").assertDoesNotExist()
    }
}
