package com.mkx.hrttracker.ui.journal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class MilestonesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun shell_rendersHeroPinnedTimelineAndTogglesEdit() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val locale = context.resources.configuration.locales[0]
        var navigatedBack = false
        var addRequests = 0

        composeRule.setContent {
            var isEditMode by remember { mutableStateOf(false) }

            HrtTrackerTheme(dynamicColor = false) {
                MilestonesScreenContent(
                    uiState = sampleMilestonesUiState(isEditMode = isEditMode),
                    onNavigateBack = { navigatedBack = true },
                    onToggleEdit = { isEditMode = !isEditMode },
                    onSetPinned = { _, _ -> },
                    onReorder = { },
                    onAddDate = { addRequests += 1 },
                    onUpdateDate = { },
                    onDeleteDate = { },
                )
            }
        }

        val title = context.getString(R.string.journal_since_you_started)
        val pinned = context.getString(R.string.journal_pinned_section).uppercase(locale)
        val timeline = context.getString(R.string.journal_timeline_section).uppercase(locale)
        val addDate = context.getString(R.string.journal_add_date)

        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onAllNodesWithText("On estradiol").assertCountEquals(3)
        composeRule.onNodeWithText("Surgery").assertIsDisplayed()
        composeRule.onNodeWithText(pinned).assertIsDisplayed()
        composeRule.onNodeWithText(timeline).assertIsDisplayed()
        composeRule.onNodeWithText(addDate).assertIsDisplayed()

        val titleTop = composeRule.onNodeWithText(title, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        val pinnedTop = composeRule.onNodeWithText(pinned, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        val timelineTop = composeRule.onNodeWithText(timeline, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        val addDateTop = composeRule.onNodeWithText(addDate, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        assertTrue(titleTop < pinnedTop)
        assertTrue(pinnedTop < timelineTop)
        assertTrue(timelineTop < addDateTop)

        composeRule.onNodeWithContentDescription(context.getString(R.string.navigate_back))
            .performClick()
        composeRule.runOnIdle {
            assertTrue(navigatedBack)
        }

        composeRule.onNodeWithText(context.getString(R.string.journal_edit))
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.journal_done))
            .assertIsDisplayed()

        composeRule.onNodeWithText(addDate).performClick()
        composeRule.runOnIdle {
            assertEquals(1, addRequests)
        }
    }

    private fun sampleMilestonesUiState(isEditMode: Boolean): MilestonesUiState {
        val today = LocalDate.of(2026, 6, 16)
        val estradiol = AnchorRowUiState(
            id = "estradiol",
            name = "On estradiol",
            icon = AnchorIcon.MEDICATION,
            palette = MedicationGroupColorKey.ROSE,
            date = LocalDate.of(2024, 4, 1),
            dayMagnitude = 807,
            isFuture = false,
        )
        val surgery = AnchorRowUiState(
            id = "surgery",
            name = "Surgery",
            icon = AnchorIcon.EVENT,
            palette = MedicationGroupColorKey.TEAL,
            date = LocalDate.of(2026, 9, 15),
            dayMagnitude = 91,
            isFuture = true,
        )

        return MilestonesUiState(
            isLoading = false,
            today = today,
            hero = estradiol,
            heroNextMilestoneLabel = "1000 days",
            pinnedTray = listOf(estradiol),
            timeline = listOf(
                TimelineNodeUiState(anchor = estradiol, isPinned = true),
                TimelineNodeUiState(anchor = surgery, isPinned = false),
            ),
            todayDividerIndex = 1,
            isEditMode = isEditMode,
        )
    }
}
