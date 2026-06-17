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
import com.mkx.hrttracker.model.journal.MilestoneUnit
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.dateLabelFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class MilestonesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun hero_rendersDetails() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val today = LocalDate.of(2026, 6, 16)
        val hero = estradiolAnchor()
        val dateLabel = dateLabelFormatter(
            locale = context.resources.configuration.locales[0],
            today = today,
        )(hero.date)
        val sinceLabel = context.getString(R.string.journal_since_date, dateLabel)
        // The hero now splits the count into a big numeral + a separate unit word.
        val dayUnitLabel = context.resources.getQuantityString(
            R.plurals.journal_day_unit,
            807,
        )
        val nextMilestoneLabel = context.resources.getQuantityString(
            R.plurals.journal_next_milestone_days_to_label,
            193,
            193,
            context.resources.getQuantityString(
                R.plurals.journal_milestone_label_days,
                1000,
                1000,
            ),
        )

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                MilestonesHero(
                    hero = hero,
                    nextMilestone = NextMilestoneUiState(
                        remainingDays = 193,
                        value = 1000,
                        unit = MilestoneUnit.DAYS,
                    ),
                    today = today,
                )
            }
        }

        composeRule.onNodeWithText("On estradiol").assertIsDisplayed()
        composeRule.onNodeWithText("807").assertIsDisplayed()
        composeRule.onNodeWithText(dayUnitLabel).assertIsDisplayed()
        composeRule.onNodeWithText(sinceLabel).assertIsDisplayed()
        composeRule.onNodeWithText(nextMilestoneLabel).assertIsDisplayed()
    }

    @Test
    fun hero_emptyStateUsesPinnedCopy() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                MilestonesHero(
                    hero = null,
                    nextMilestone = null,
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.journal_nothing_pinned))
            .assertIsDisplayed()
        composeRule.onAllNodesWithText(context.getString(R.string.journal_no_dates))
            .assertCountEquals(0)
    }

    @Test
    fun hero_showsBigCountAndChips() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val locale = context.resources.configuration.locales[0]
        val heroDate = LocalDate.of(2024, 4, 1)
        // Derive the locale-dependent pill labels from resources (matching this
        // file's convention) so the assertions hold on any device locale.
        val sinceLabel = context.getString(
            R.string.journal_since_date,
            dateLabelFormatter(locale = locale, today = LocalDate.of(2026, 6, 17))(heroDate),
        )
        val nextMilestoneLabel = context.resources.getQuantityString(
            R.plurals.journal_next_milestone_days_to_label,
            106,
            106,
            context.resources.getQuantityString(R.plurals.journal_milestone_label_months, 30, 30),
        )

        composeRule.setContent {
            HrtTrackerTheme {
                MilestonesScreenContent(
                    uiState = milestonesUiStateFixture(),
                    onNavigateBack = {}, onToggleEdit = {}, onSetPinned = { _, _ -> },
                    onReorder = {}, onAddDate = {}, onUpdateDate = {},
                )
            }
        }
        // In view mode the hero is lifted out of the pinned tray, so "On estradiol"
        // appears in the hero card + its timeline row (2x), not the tray. Scope the
        // name check by count rather than a single node.
        composeRule.onAllNodesWithText("On estradiol").assertCountEquals(2)
        composeRule.onNodeWithText("807").assertIsDisplayed()        // 60px numeral
        composeRule.onNodeWithText(sinceLabel).assertExists()
        composeRule.onNodeWithText(nextMilestoneLabel).assertExists()
    }

    @Test
    fun viewMode_showsHeroOnceAndRestInTray() {
        composeRule.setContent {
            HrtTrackerTheme {
                MilestonesScreenContent(
                    uiState = milestonesUiStateFixture(),
                    onNavigateBack = {}, onToggleEdit = {}, onSetPinned = { _, _ -> },
                    onReorder = {}, onAddDate = {}, onUpdateDate = {},
                )
            }
        }
        // The hero (pinnedTray[0]) is lifted OUT of the Pinned tray in view mode, so
        // "On estradiol" no longer renders a pinned-tray row. The timeline always
        // renders every anchor, so the hero name still appears twice: the hero card
        // + its timeline row (2x, down from 3x before this task).
        composeRule.onAllNodesWithText("On estradiol").assertCountEquals(2)
        // The remaining non-hero pin IS shown in the Pinned tray. Like every anchor
        // it also renders a timeline row, so a pinned non-hero appears twice (tray +
        // timeline), while a non-pinned anchor ("Surgery") appears only once (timeline).
        // That tray-2-vs-1 contrast is what this task's split produces.
        composeRule.onAllNodesWithText("First injection").assertCountEquals(2)
        composeRule.onAllNodesWithText("Surgery").assertCountEquals(1)
    }

    @Test
    fun pinnedTray_rendersOrderHeroBadgeUnpinAndReorder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val anchors = listOf(estradiolAnchor(), surgeryAnchor(), labsAnchor())
        val unpinned = mutableListOf<Pair<String, Boolean>>()
        var reorderedIds: List<String>? = null

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                PinnedTray(
                    anchors = anchors,
                    isEditMode = true,
                    onReorder = { reorderedIds = it },
                    onSetPinned = { id, pinned -> unpinned += id to pinned },
                )
            }
        }

        // The HeroBadge is removed in this task (Task 4) and reinstated for edit mode
        // in Task 5; until then the tray renders no badge, so don't assert it here.
        val estradiolTop = composeRule.onNodeWithText("On estradiol", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        val surgeryTop = composeRule.onNodeWithText("Surgery", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        val labsTop = composeRule.onNodeWithText("Labs", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        assertTrue(estradiolTop < surgeryTop)
        assertTrue(surgeryTop < labsTop)

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.journal_unpin_anchor, "Surgery")
        ).performClick()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.journal_move_anchor_down, "On estradiol")
        ).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("surgery" to false), unpinned)
            assertEquals(listOf("surgery", "estradiol", "labs"), reorderedIds)
        }
    }

    @Test
    fun timeline_rendersTodayDivider() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val today = LocalDate.of(2026, 6, 16)

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                MilestonesTimeline(
                    nodes = listOf(
                        TimelineNodeUiState(anchor = estradiolAnchor(), isPinned = true),
                        TimelineNodeUiState(anchor = surgeryAnchor(), isPinned = false),
                    ),
                    todayDividerIndex = 1,
                    isEditMode = false,
                    onSetPinned = { _, _ -> },
                    onUpdateDate = { },
                    today = today,
                )
            }
        }

        val todayLabel = context.getString(R.string.journal_today)
            .uppercase(context.resources.configuration.locales[0])
        val pastDayCountLabel = context.resources.getQuantityString(
            R.plurals.journal_milestone_days_past,
            807,
            807,
        )
        val futureDayCountLabel = context.resources.getQuantityString(
            R.plurals.journal_milestone_days_future,
            91,
            91,
        )
        composeRule.onNodeWithText(pastDayCountLabel).assertIsDisplayed()
        composeRule.onNodeWithText(futureDayCountLabel).assertIsDisplayed()

        val estradiolTop = composeRule.onNodeWithText("On estradiol", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        val todayTop = composeRule.onNodeWithText(todayLabel, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        val surgeryTop = composeRule.onNodeWithText("Surgery", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        assertTrue(estradiolTop < todayTop)
        assertTrue(todayTop < surgeryTop)
    }

    @Test
    fun timeline_pinToggleInEditModeCallsSetPinned() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val today = LocalDate.of(2026, 6, 16)
        var pinRequest: Pair<String, Boolean>? = null

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                MilestonesTimeline(
                    nodes = listOf(TimelineNodeUiState(anchor = surgeryAnchor(), isPinned = false)),
                    todayDividerIndex = 0,
                    isEditMode = true,
                    onSetPinned = { id, pinned -> pinRequest = id to pinned },
                    onUpdateDate = { },
                    today = today,
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.journal_pin_to_home_content_description)
        ).performClick()
        composeRule.runOnIdle {
            assertEquals("surgery" to true, pinRequest)
        }
    }

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
                )
            }
        }

        val title = context.getString(R.string.journal_since_you_started)
        val pinned = context.getString(R.string.journal_pinned_section).uppercase(locale)
        val timeline = context.getString(R.string.journal_timeline_section).uppercase(locale)
        val addDate = context.getString(R.string.journal_add_date)

        composeRule.onNodeWithText(title).assertIsDisplayed()
        // View mode lifts the hero out of the tray: "On estradiol" is the hero card +
        // its timeline row (2x). "Surgery" is the remaining non-hero pin, so it shows
        // in the tray + timeline (2x).
        composeRule.onAllNodesWithText("On estradiol").assertCountEquals(2)
        composeRule.onAllNodesWithText("Surgery").assertCountEquals(2)
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
        val estradiol = estradiolAnchor()
        val surgery = surgeryAnchor()

        return MilestonesUiState(
            isLoading = false,
            today = today,
            hero = estradiol,
            heroNextMilestone = NextMilestoneUiState(
                remainingDays = 193,
                value = 1000,
                unit = MilestoneUnit.DAYS,
            ),
            // Two pins (hero + a non-hero) so the Pinned section stays visible in
            // view mode, where the hero is lifted out and the tray shows the rest.
            pinnedTray = listOf(estradiol, surgery),
            timeline = listOf(
                TimelineNodeUiState(anchor = estradiol, isPinned = true),
                TimelineNodeUiState(anchor = surgery, isPinned = true),
            ),
            todayDividerIndex = 1,
            isEditMode = isEditMode,
        )
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
        dayMagnitude = 91,
        isFuture = true,
    )

    private fun labsAnchor() = AnchorRowUiState(
        id = "labs",
        name = "Labs",
        icon = AnchorIcon.LABS,
        palette = null,
        date = LocalDate.of(2026, 6, 15),
        dayMagnitude = 1,
        isFuture = false,
    )

    private fun milestonesUiStateFixture(
        isEditMode: Boolean = false,
    ): MilestonesUiState {
        val today = LocalDate.of(2026, 6, 17)
        fun row(id: String, name: String, icon: AnchorIcon, palette: MedicationGroupColorKey?,
                date: LocalDate, magnitude: Long, future: Boolean) =
            AnchorRowUiState(id, name, icon, palette, date, magnitude, future)
        val estradiol = row("e", "On estradiol", AnchorIcon.MEDICATION,
            MedicationGroupColorKey.ROSE, LocalDate.of(2024, 4, 1), 807, false)
        val injection = row("i", "First injection", AnchorIcon.VACCINES,
            MedicationGroupColorKey.INDIGO, LocalDate.of(2026, 3, 1), 108, false)
        val surgery = row("s", "Surgery", AnchorIcon.FLAG,
            MedicationGroupColorKey.SAGE, LocalDate.of(2026, 9, 15), 90, true)
        return MilestonesUiState(
            isLoading = false, today = today, hero = estradiol,
            heroNextMilestone = NextMilestoneUiState(remainingDays = 106, value = 30, unit = MilestoneUnit.MONTHS),
            pinnedTray = listOf(estradiol, injection),
            timeline = listOf(
                TimelineNodeUiState(estradiol, isPinned = true),
                TimelineNodeUiState(injection, isPinned = false),
                TimelineNodeUiState(surgery, isPinned = false),
            ),
            todayDividerIndex = 2, isEditMode = isEditMode,
        )
    }
}
