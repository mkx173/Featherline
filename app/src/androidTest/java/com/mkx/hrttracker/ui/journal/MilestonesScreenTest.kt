package com.mkx.hrttracker.ui.journal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
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

        // The hero is now the first row of the single Pinned section (no separate
        // card), enriched in view mode with the big numeral, chips, and Home tag.
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                PinnedTray(
                    anchors = listOf(hero),
                    isEditMode = false,
                    onReorder = {},
                    onSetPinned = { _, _ -> },
                    heroNextMilestone = NextMilestoneUiState(
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
        composeRule.onNodeWithText(context.getString(R.string.journal_home_tag))
            .assertIsDisplayed()
    }

    @Test
    fun pinnedTray_emptyShowsHomeSlotTeaching() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                PinnedTray(
                    anchors = emptyList(),
                    isEditMode = false,
                    onReorder = {},
                    onSetPinned = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.journal_home_slot_empty))
            .assertIsDisplayed()
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
            context.resources.getQuantityString(R.plurals.journal_milestone_label_years, 2, 2),
        )

        composeRule.setContent {
            HrtTrackerTheme {
                MilestonesScreenContent(
                    uiState = milestonesUiStateFixture(),
                    onNavigateBack = {}, onToggleEdit = {}, onSetPinned = { _, _ -> },
                    onReorder = {}, onAddDate = {}, onUpdateDate = {},
                    onOpenHeroBackground = {},
                )
            }
        }
        // The hero is the first row of the single Pinned section, so "On estradiol"
        // appears in that enriched pinned row + its timeline row (2x). Scope the name
        // check by count rather than a single node.
        composeRule.onAllNodesWithText("On estradiol").assertCountEquals(2)
        composeRule.onNodeWithText("807").assertIsDisplayed()        // big numeral
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
                    onOpenHeroBackground = {},
                )
            }
        }
        // The hero (pinnedTray[0]) is the first row of the single Pinned section, and
        // the timeline always renders every anchor, so the hero name appears twice:
        // its pinned row + its timeline row (2x).
        composeRule.onAllNodesWithText("On estradiol").assertCountEquals(2)
        // The non-hero pin also renders a pinned row + a timeline row (2x), while a
        // non-pinned anchor ("Surgery") appears only once (timeline). That 2-vs-1
        // contrast confirms the Pinned section holds every pin, hero included.
        composeRule.onAllNodesWithText("First injection").assertCountEquals(2)
        composeRule.onAllNodesWithText("Surgery").assertCountEquals(1)
    }

    @Test
    fun editMode_showsUnpinButton() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            HrtTrackerTheme {
                MilestonesScreenContent(
                    uiState = milestonesUiStateFixture(isEditMode = true),
                    onNavigateBack = {}, onToggleEdit = {}, onSetPinned = { _, _ -> },
                    onReorder = {}, onAddDate = {}, onUpdateDate = {},
                    onOpenHeroBackground = {},
                )
            }
        }
        // Every pinned row exposes an unpin button in edit mode. Whole-row long-press drag
        // has no per-handle node, so the reorder a11y is covered by
        // editMode_rendersPinsInOrderAndRoutesUnpin.
        composeRule.onNode(
            hasContentDescription(context.getString(R.string.journal_unpin_anchor, "First injection")),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun editMode_hidesBigHeroCard() {
        composeRule.setContent {
            HrtTrackerTheme {
                MilestonesScreenContent(
                    uiState = milestonesUiStateFixture(isEditMode = true),
                    onNavigateBack = {}, onToggleEdit = {}, onSetPinned = { _, _ -> },
                    onReorder = {}, onAddDate = {}, onUpdateDate = {},
                    onOpenHeroBackground = {},
                )
            }
        }

        // In edit mode the hero row collapses to a compact draggable row, so its bare
        // big numeral ("807") must be gone. The timeline renders the day count as ONE
        // merged node ("807 days"), never the bare "807", so "807" alone was unique to
        // the hero's enriched view-mode block.
        composeRule.onNodeWithText("807").assertDoesNotExist()
    }

    @Test
    fun editMode_rendersPinsInOrderAndRoutesUnpin() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val anchors = listOf(estradiolAnchor(), surgeryAnchor(), labsAnchor())
        val unpinned = mutableListOf<Pair<String, Boolean>>()

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                PinnedTray(
                    anchors = anchors,
                    isEditMode = true,
                    onReorder = { },
                    onSetPinned = { id, pinned -> unpinned += id to pinned },
                )
            }
        }

        // Every pin keeps its supplied order, hero first.
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

        // The × button routes an unpin through onSetPinned (the reorder a11y actions
        // and the drag gesture are verified manually — Compose can't invoke them).
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.journal_unpin_anchor, "Surgery")
        ).performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("surgery" to false), unpinned)
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
    fun timeline_showsCountsAndTodayLabel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val today = LocalDate.of(2026, 6, 17)
        val todayAnchor = AnchorRowUiState(
            id = "t",
            name = "Blood test",
            icon = AnchorIcon.BLOODTYPE,
            palette = MedicationGroupColorKey.TEAL,
            date = today,
            dayMagnitude = 0,
            isFuture = false,
        )
        val base = milestonesUiStateFixture()
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                MilestonesScreenContent(
                    uiState = base.copy(
                        timeline = base.timeline + TimelineNodeUiState(todayAnchor, isPinned = false),
                    ),
                    onNavigateBack = {}, onToggleEdit = {}, onSetPinned = { _, _ -> },
                    onReorder = {}, onAddDate = {}, onUpdateDate = {},
                    onOpenHeroBackground = {},
                )
            }
        }

        // Both labels are localized: "807 days" from the past-days plural and
        // "Today" from journal_today. Derive them from resources so the
        // assertions hold on the zh emulator. The past count appears only in
        // the timeline row (the hero renders 807 + its unit word separately).
        val pastCountLabel = context.resources.getQuantityString(
            R.plurals.journal_milestone_days_past,
            807,
            807,
        )
        composeRule.onNodeWithText(pastCountLabel).assertExists()

        // The today-dated anchor's count must read "Today", not the zero-day
        // past plural. That zero label is the real RED here: before the change
        // the row renders journal_milestone_days_past(0) ("0 days"); after, it
        // renders journal_today. The TodayDivider also shows journal_today
        // (uppercased), so assert the "Today" count appears (>= 1) AND the
        // zero-past label is gone.
        val zeroPastLabel = context.resources.getQuantityString(
            R.plurals.journal_milestone_days_past,
            0,
            0,
        )
        val todayLabel = context.getString(R.string.journal_today)
        composeRule.onAllNodesWithText(zeroPastLabel).assertCountEquals(0)
        composeRule.onAllNodesWithText(todayLabel).onFirst().assertExists()
    }

    @Test
    fun timeline_todayNodeShowsTodayAndDate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // The fixture's today is 2026-06-17 and carries no today-dated anchor, so
        // journal_today renders only in the Today node. Both strings are localized
        // (zh on the emulator): "Today" from journal_today, the date from the same
        // dateLabelFormatter(appLocale, today) the node uses — derive both so the
        // assertions hold regardless of device locale.
        val today = LocalDate.of(2026, 6, 17)
        val todayLabel = context.getString(R.string.journal_today)
        val dateLabel = dateLabelFormatter(
            locale = context.resources.configuration.locales[0],
            today = today,
        )(today)

        composeRule.setContent {
            HrtTrackerTheme {
                MilestonesScreenContent(
                    uiState = milestonesUiStateFixture(),  // divider at index 2, before "Surgery"
                    onNavigateBack = {}, onToggleEdit = {}, onSetPinned = { _, _ -> },
                    onReorder = {}, onAddDate = {}, onUpdateDate = {},
                    onOpenHeroBackground = {},
                )
            }
        }
        composeRule.onNodeWithText(todayLabel).assertExists()
        composeRule.onNodeWithText(dateLabel).assertExists()

        // The redesigned Today marker is an inline pill (date beside "Today"), so it
        // no longer stacks. Intent that still holds: the marker renders both "Today"
        // and the date, and sits above the future milestone. "Surgery" is the only
        // timeline node not also pinned (the fixture pins estradiol + injection), so
        // it is a unique anchor in this full-screen layout. Ordering is locale-independent.
        val surgeryTop = composeRule.onNodeWithText("Surgery", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.top
        val todayTop = composeRule.onNodeWithText(todayLabel, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue(todayTop < surgeryTop)
    }

    @Test
    fun editMode_rowTapTogglesPinAgainstCurrentState() {
        val today = LocalDate.of(2026, 6, 16)
        var pinRequest: Pair<String, Boolean>? = null

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                MilestonesTimeline(
                    nodes = listOf(
                        TimelineNodeUiState(estradiolAnchor(), isPinned = true),
                        TimelineNodeUiState(surgeryAnchor(), isPinned = false),
                    ),
                    todayDividerIndex = 1,
                    isEditMode = true,
                    onSetPinned = { id, pinned -> pinRequest = id to pinned },
                    onUpdateDate = { },
                    today = today,
                )
            }
        }

        // The whole row is the pin control in edit mode, and it toggles against the
        // current state: a pinned row requests unpin, an unpinned row requests pin.
        composeRule.onNodeWithText("On estradiol").performClick()
        composeRule.runOnIdle { assertEquals("estradiol" to false, pinRequest) }
        composeRule.onNodeWithText("Surgery").performClick()
        composeRule.runOnIdle { assertEquals("surgery" to true, pinRequest) }
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
    fun timeline_viewModeRowTapsThroughToEditorAndShowsNoPinToggle() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val today = LocalDate.of(2026, 6, 16)
        var edited: AnchorRowUiState? = null

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                MilestonesTimeline(
                    nodes = listOf(TimelineNodeUiState(anchor = surgeryAnchor(), isPinned = false)),
                    todayDividerIndex = 0,
                    isEditMode = false,
                    onSetPinned = { _, _ -> },
                    onUpdateDate = { edited = it },
                    today = today,
                )
            }
        }

        // In view mode the trailing flip shows its chevron face, so the pin toggle is
        // absent — pinning is an edit-mode-only control.
        composeRule.onAllNodes(
            hasContentDescription(context.getString(R.string.journal_pin_to_home_content_description))
        ).assertCountEquals(0)
        // And tapping the row opens the date editor for that anchor (the chevron's promise).
        composeRule.onNodeWithText("Surgery").performClick()
        composeRule.runOnIdle {
            assertEquals("surgery", edited?.id)
        }
    }

    @Test
    fun timeline_hidesTodayMarkerWhenAllPast() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val today = LocalDate.of(2026, 6, 16)
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                MilestonesTimeline(
                    nodes = listOf(
                        TimelineNodeUiState(estradiolAnchor(), isPinned = false),
                        TimelineNodeUiState(labsAnchor(), isPinned = false),
                    ),
                    // Divider past the end: every date is in the past, so Today would
                    // dangle at the bottom. It must be suppressed.
                    todayDividerIndex = 2,
                    isEditMode = false,
                    onSetPinned = { _, _ -> },
                    onUpdateDate = { },
                    today = today,
                )
            }
        }
        composeRule.onAllNodesWithText(context.getString(R.string.journal_today))
            .assertCountEquals(0)
    }

    @Test
    fun timeline_hidesTodayMarkerWhenAllFuture() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val today = LocalDate.of(2026, 6, 16)
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                MilestonesTimeline(
                    nodes = listOf(TimelineNodeUiState(surgeryAnchor(), isPinned = false)),
                    // Divider at the start: every date is in the future, so Today would
                    // dangle at the top. It must be suppressed.
                    todayDividerIndex = 0,
                    isEditMode = false,
                    onSetPinned = { _, _ -> },
                    onUpdateDate = { },
                    today = today,
                )
            }
        }
        composeRule.onAllNodesWithText(context.getString(R.string.journal_today))
            .assertCountEquals(0)
    }

    @Test
    fun timeline_suppressesTodayDividerWhenNodeIsToday() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val today = LocalDate.of(2026, 6, 16)
        val todayAnchor = AnchorRowUiState(
            id = "today",
            name = "Blood test",
            icon = AnchorIcon.BLOODTYPE,
            palette = MedicationGroupColorKey.TEAL,
            date = today,
            dayMagnitude = 0,
            isFuture = false,
        )
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                MilestonesTimeline(
                    nodes = listOf(
                        TimelineNodeUiState(estradiolAnchor(), isPinned = false),
                        TimelineNodeUiState(todayAnchor, isPinned = false),
                    ),
                    // One past date precedes today, so the divider would otherwise render
                    // directly above the today-dated node.
                    todayDividerIndex = 1,
                    isEditMode = false,
                    onSetPinned = { _, _ -> },
                    onUpdateDate = { },
                    today = today,
                )
            }
        }
        // The today node already marks "now" (its badge reads Today + a haloed dot), so the
        // divider must not stack a second "Today" above it: journal_today appears exactly
        // once — from the node's count, not a divider. Before the suppression it appeared
        // twice (node badge + divider label).
        composeRule.onAllNodesWithText(context.getString(R.string.journal_today))
            .assertCountEquals(1)
    }

    @Test
    fun timeline_editModeKeepsCountInTrailingNotSupport() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val today = LocalDate.of(2026, 6, 16)
        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                MilestonesTimeline(
                    nodes = listOf(TimelineNodeUiState(estradiolAnchor(), isPinned = false)),
                    todayDividerIndex = 1,
                    isEditMode = true,
                    onSetPinned = { _, _ -> },
                    onUpdateDate = { },
                    today = today,
                )
            }
        }
        val dateLabel = dateLabelFormatter(
            locale = context.resources.configuration.locales[0],
            today = today,
        )(estradiolAnchor().date)
        val countLabel = context.resources.getQuantityString(
            R.plurals.journal_milestone_days_past,
            807,
            807,
        )
        // The day count stays visible in edit mode (now in the trailing slot)...
        composeRule.onNodeWithText(countLabel).assertExists()
        // ...and is no longer concatenated into the supporting line ("date · count").
        composeRule.onAllNodesWithText("$dateLabel · $countLabel").assertCountEquals(0)
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
                    onOpenHeroBackground = {},
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
            heroNextMilestone = NextMilestoneUiState(remainingDays = 106, value = 2, unit = MilestoneUnit.YEARS),
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
