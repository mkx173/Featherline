package com.mkx.hrttracker.ui.journal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PinnedTrayAnimationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun removedPinRemainsComposedDuringExitAnimation() {
        var anchors by mutableStateOf(listOf(estradiolAnchor(), surgeryAnchor()))

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                PinnedTray(
                    anchors = anchors,
                    isEditMode = true,
                    onReorder = {},
                    onSetPinned = { _, _ -> },
                    today = LocalDate.of(2026, 6, 17),
                )
            }
        }

        composeRule.onAllNodesWithText("Surgery", useUnmergedTree = true)
            .assertCountEquals(1)

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { anchors = listOf(estradiolAnchor()) }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onAllNodesWithText("Surgery", useUnmergedTree = true)
            .assertCountEquals(1)

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Surgery", useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun removingLastPinMorphsToSupportMessage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val supportMessage = context.getString(R.string.journal_home_slot_empty)
        var anchors by mutableStateOf(listOf(estradiolAnchor()))

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                PinnedTray(
                    anchors = anchors,
                    isEditMode = true,
                    onReorder = {},
                    onSetPinned = { _, _ -> },
                    today = LocalDate.of(2026, 6, 17),
                )
            }
        }

        composeRule.onAllNodesWithText(supportMessage, useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("On estradiol", useUnmergedTree = true)
            .assertCountEquals(1)

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { anchors = emptyList() }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(120)

        composeRule.onAllNodesWithText(supportMessage, useUnmergedTree = true)
            .assertCountEquals(1)
        composeRule.onAllNodesWithText("On estradiol", useUnmergedTree = true)
            .assertCountEquals(1)

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(supportMessage, useUnmergedTree = true)
            .assertCountEquals(1)
        composeRule.onAllNodesWithText("On estradiol", useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun addingFirstPinMorphsFromSupportMessage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val supportMessage = context.getString(R.string.journal_home_slot_empty)
        var anchors by mutableStateOf(emptyList<AnchorRowUiState>())

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                PinnedTray(
                    anchors = anchors,
                    isEditMode = true,
                    onReorder = {},
                    onSetPinned = { _, _ -> },
                    today = LocalDate.of(2026, 6, 17),
                )
            }
        }

        composeRule.onAllNodesWithText(supportMessage, useUnmergedTree = true)
            .assertCountEquals(1)
        composeRule.onAllNodesWithText("On estradiol", useUnmergedTree = true)
            .assertCountEquals(0)

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { anchors = listOf(estradiolAnchor()) }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(120)

        composeRule.onAllNodesWithText(supportMessage, useUnmergedTree = true)
            .assertCountEquals(1)
        composeRule.onAllNodesWithText("On estradiol", useUnmergedTree = true)
            .assertCountEquals(1)

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(supportMessage, useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("On estradiol", useUnmergedTree = true)
            .assertCountEquals(1)
    }

    private fun estradiolAnchor() = AnchorRowUiState(
        id = "estradiol",
        name = "On estradiol",
        icon = AnchorIcon.MEDICATION,
        palette = MedicationGroupColorKey.ROSE,
        date = LocalDate.of(2024, 4, 1),
        dayMagnitude = 807,
        isFuture = false,
    )

    private fun surgeryAnchor() = AnchorRowUiState(
        id = "surgery",
        name = "Surgery",
        icon = AnchorIcon.EVENT,
        palette = MedicationGroupColorKey.TEAL,
        date = LocalDate.of(2026, 9, 15),
        dayMagnitude = 90,
        isFuture = true,
    )
}
