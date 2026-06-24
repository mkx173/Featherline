package com.mkx.hrttracker.ui.main

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.home.HomeCardLayout
import com.mkx.hrttracker.model.home.HomeCardType
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeCardLayoutDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Resolve labels from resources so the assertions hold under any device locale
    // (the test devices run non-English locales), matching HeroBackgroundDialogTest.
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val estradiolName = context.getString(R.string.home_card_name_e2_hero)
    private val timelineName = context.getString(R.string.home_card_name_timeline)
    private val hideEstradiol = context.getString(R.string.home_card_hide, estradiolName)
    private val moveTimelineToTop = context.getString(R.string.home_card_move_to_top, timelineName)
    private val done = context.getString(R.string.journal_done)
    private val cancel = context.getString(R.string.cancel)

    @Test
    fun eyeToggleHidesCardAndDoneCommits() {
        var committedOrder: List<HomeCardType>? = null
        var committedHidden: Set<HomeCardType>? = null

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                HomeCardLayoutDialog(
                    layout = HomeCardLayout(),
                    onConfirm = { order, hidden ->
                        committedOrder = order
                        committedHidden = hidden
                    },
                    onDismiss = {},
                )
            }
        }

        // "Hide Estradiol level" is the eye-button content description while the card is shown.
        composeRule.onNodeWithContentDescription(hideEstradiol).performClick()
        composeRule.onNodeWithText(done).performClick()

        assertEquals(setOf(HomeCardType.E2_HERO), committedHidden)
        assertEquals(HomeCardLayout().order, committedOrder)
    }

    @Test
    fun cancelDiscardsDraftChanges() {
        var committed = false
        var dismissed = false

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                HomeCardLayoutDialog(
                    layout = HomeCardLayout(),
                    onConfirm = { _, _ -> committed = true },
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription(hideEstradiol).performClick()
        composeRule.onNodeWithText(cancel).performClick()

        assertEquals(false, committed)
        assertEquals(true, dismissed)
    }

    @Test
    fun accessibilityMoveToTopReordersDraftAndDoneCommits() {
        var committedOrder: List<HomeCardType>? = null

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                HomeCardLayoutDialog(
                    layout = HomeCardLayout(),
                    onConfirm = { order, _ -> committedOrder = order },
                    onDismiss = {},
                )
            }
        }

        // Move the Timeline row (last in default order) to the top via its a11y custom action.
        // CustomActions is keyed to a List<CustomAccessibilityAction>, not an AccessibilityAction,
        // so the action is read from the node config and invoked directly on the UI thread —
        // the two-arg performSemanticsAction block overload does not apply to CustomActions.
        // The row is a clickable (tap-to-toggle) Surface, so its semantics merge: the Timeline
        // text node IS the row node that carries the custom actions.
        val action = composeRule.onNodeWithText(timelineName)
            .fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
            .first { it.label == moveTimelineToTop }
        composeRule.runOnUiThread { action.action() }
        composeRule.onNodeWithText(done).performClick()

        assertEquals(HomeCardType.TIMELINE, committedOrder?.first())
        assertEquals(5, committedOrder?.size)
    }
}
