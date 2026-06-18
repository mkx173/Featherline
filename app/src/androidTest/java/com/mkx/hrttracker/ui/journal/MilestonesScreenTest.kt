package com.mkx.hrttracker.ui.journal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
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
    fun editMode_showsHomeTagAndUnpin() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            HrtTrackerTheme {
                MilestonesScreenContent(
                    uiState = milestonesUiStateFixture(isEditMode = true),
                    onNavigateBack = {}, onToggleEdit = {}, onSetPinned = { _, _ -> },
                    onReorder = {}, onAddDate = {}, onUpdateDate = {},
                )
            }
        }
        // The first pinned row (the hero) marks itself Home inside its summary line
        // ("date · day count · Home"), and every row exposes an unpin button. Whole-row
        // long-press drag has no per-handle node, so the reorder a11y is covered by
        // editMode_rendersPinsInOrderAndRoutesUnpin. Home now lives in the hero's summary
        // rather than a standalone chip, so match the hero row by name + Home to avoid
        // also matching the home-slot hint ("…shown on your Home screen").
        composeRule.onNode(
            hasText(context.getString(R.string.journal_home_tag), substring = true) and
                hasText("On estradiol", substring = true),
        ).assertExists()
        composeRule.onNode(
            hasContentDescription(context.getString(R.string.journal_unpin_anchor, "First injection")),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun editMode_hidesBigHeroCard() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            HrtTrackerTheme {
                MilestonesScreenContent(
                    uiState = milestonesUiStateFixture(isEditMode = true),
                    onNavigateBack = {}, onToggleEdit = {}, onSetPinned = { _, _ -> },
                    onReorder = {}, onAddDate = {}, onUpdateDate = {},
                )
            }
        }

        // In edit mode the hero row collapses to a compact draggable row, so its bare
        // big numeral ("807") must be gone. The timeline renders the day count as ONE
        // merged node ("807 days"), never the bare "807", so "807" alone was unique to
        // the hero's enriched view-mode block.
        composeRule.onNodeWithText("807").assertDoesNotExist()
        // The Home marker still identifies the hero, now embedded in its summary line.
        // Match the hero row by name + Home so the home-slot hint isn't also caught.
        composeRule.onNode(
            hasText(context.getString(R.string.journal_home_tag), substring = true) and
                hasText("On estradiol", substring = true),
        ).assertExists()
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

        // The Home marker (in the hero's summary line) sits at index 0; every pin keeps
        // its supplied order. Match the hero row by name + Home so the home-slot hint
        // ("…shown on your Home screen") isn't also caught.
        composeRule.onNode(
            hasText(context.getString(R.string.journal_home_tag), substring = true) and
                hasText("On estradiol", substring = true),
        ).assertExists()
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
    fun editMode_reorderAnimatesHomeTagBetweenRows() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val homeTag = context.getString(R.string.journal_home_tag)
        // The hero's date-line tag is "· Home"; matching the leading separator excludes the
        // "Home slot" hint ("…your Home screen"), which would otherwise inflate the count.
        val homeTagSuffix = context
            .getString(R.string.journal_hero_edit_home_suffix, homeTag).trim()
        var anchors by mutableStateOf(listOf(estradiolAnchor(), surgeryAnchor()))

        composeRule.setContent {
            HrtTrackerTheme(dynamicColor = false) {
                PinnedTray(
                    anchors = anchors,
                    isEditMode = true,
                    onReorder = {},
                    onSetPinned = { _, _ -> },
                )
            }
        }

        fun tagCount() = composeRule
            .onAllNodesWithText(homeTagSuffix, substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes().size

        // At rest only the hero (index 0) carries the "· Home" tag.
        assertEquals(1, tagCount())

        // Pause the clock and promote a different row to hero, as a drag-reorder would.
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { anchors = listOf(surgeryAnchor(), estradiolAnchor()) }
        // A couple of frames in (well short of the transition's duration), the tag must be
        // mid-flight on BOTH rows: shrinking out of the old hero while expanding into the new
        // one. If the reorder reset the tag (the earlier bug, since ReorderableColumn rebuilds
        // the row) it would snap — old one gone, new one already full — leaving exactly one.
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(2, tagCount())

        // Once the transition settles the tag lives only on the new hero.
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertEquals(1, tagCount())
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
                )
            }
        }
        composeRule.onNodeWithText(todayLabel).assertExists()
        composeRule.onNodeWithText(dateLabel).assertExists()

        // The redesigned node stacks "Today" over the date on two lines, unlike the
        // old hairline divider that laid them out inline on a single baseline. The
        // top-ordering check is the real RED: it fails against the inline divider and
        // is locale-independent (no reliance on en uppercase("TODAY")).
        val todayTop = composeRule.onNodeWithText(todayLabel, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        val dateTop = composeRule.onNodeWithText(dateLabel, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        assertTrue(todayTop < dateTop)
    }

    @Test
    fun editMode_timelinePinToggleIsToggleable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // The pin content description is localized (zh on the emulator), so derive it
        // from resources rather than hardcoding "Pin to Home".
        val pinDescription = context.getString(R.string.journal_pin_to_home_content_description)

        composeRule.setContent {
            HrtTrackerTheme {
                MilestonesScreenContent(
                    uiState = milestonesUiStateFixture(isEditMode = true),
                    onNavigateBack = {}, onToggleEdit = {}, onSetPinned = { _, _ -> },
                    onReorder = {}, onAddDate = {}, onUpdateDate = {},
                )
            }
        }

        // The fixture's first timeline node ("On estradiol") is pinned, so its toggle
        // reports ON; a non-pinned node ("First injection") reports OFF. Asserting both
        // pins the on/off semantics to the isPinned state, not just toggle existence.
        val toggles = composeRule.onAllNodes(hasContentDescription(pinDescription))
        toggles.onFirst().assertIsOn()
        toggles[1].assertIsOff()
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
