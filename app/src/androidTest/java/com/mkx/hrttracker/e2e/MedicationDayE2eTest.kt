package com.mkx.hrttracker.e2e

import com.mkx.hrttracker.util.MedicationDayBoundary
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.printToString
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mkx.hrttracker.MainActivity
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.HomeE2Estimate
import com.mkx.hrttracker.data.repository.MedicationGroupMedicationInput
import com.mkx.hrttracker.data.repository.MedicationGroupScheduleInput
import com.mkx.hrttracker.model.home.HomeCardType
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.RunwayProjection
import com.mkx.hrttracker.model.medication.nextOccurrencesFrom
import com.mkx.hrttracker.widget.WidgetDoseChip
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.settings.AppLanguageOption
import com.mkx.hrttracker.reminder.REMINDER_CHANNEL_ID
import com.mkx.hrttracker.testing.E2eEntryPoint
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.uses24HourTimeFormat
import com.mkx.hrttracker.util.localizedShortTimeFormatter
import dagger.hilt.android.EntryPointAccessors
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs MainActivity, its real navigation, SQLCipher/Room, DataStore and application lifecycle.
 * Each plan carries its own day ending: night-owl plans end their day at 04:00, the rest at midnight.
 */
@RunWith(AndroidJUnit4::class)
class MedicationDayE2eTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val device = EmulatorDeviceState()
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var entryPoint: E2eEntryPoint
    private lateinit var context: Context
    private val day = LocalDate.of(2026, 10, 31)
    private val dose = DoseInstruction.TabletFraction(1, 1)
    private val nightOwl = MedicationDayBoundary.FourAm
    // The migration marker's state before pinEnglish, put back afterwards; null until pinned.
    private var originalLocaleMarkerState: Int? = null
    private val localeMarker
        get() = ComponentName(context, "androidx.appcompat.app.AppLocalesMetadataHolderService")

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".debug")) { "Run the Debug variant." }
        assumeTrue("Per-app locales need API 33 or later.", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        pinEnglish()
        device.captureAndConfigure()
        device.setTime(day.plusDays(1).atTime(6, 0))
        entryPoint = EntryPointAccessors.fromApplication(context, E2eEntryPoint::class.java)
        io {
            entryPoint.settingsRepository().apply {
                setOnboardingCompleted(true)
                setScreenLockProtectionEnabled(false)
                setHideScreenContentEnabled(false)
                setHideMedicationDetails(false)
                setRemindersEnabled(false)
                setShowArchivedGroupRecords(true)
                setHazeBlurEnabled(false)
                setStockNudgeEnabled(false)
                setHomeCardLayout(
                    HomeCardType.entries,
                    HomeCardType.entries.filter { it != HomeCardType.TIMELINE }.toSet(),
                )
                setAppLanguageOption(AppLanguageOption.ENGLISH)
                acknowledgeTimeZone(ZoneId.systemDefault().id)
            }
            clearDatabase()
            entryPoint.reminderScheduler().rescheduleAll()
        }
        launch()
    }

    @After
    fun tearDown() {
        try {
            scenario?.close()
            if (::entryPoint.isInitialized) io {
                entryPoint.settingsRepository().setRemindersEnabled(false)
                entryPoint.reminderScheduler().rescheduleAll()
            }
        } finally {
            try {
                if (originalLocaleMarkerState != null) resetAppLocale()
            } finally {
                device.close()
            }
        }
    }

    @Test
    fun planEditorTurnsNightOwlOnSurvivesRelaunchAndLocksOnceThePlanHasRecords() {
        val medicine = medicine("Editor dose")
        group(medicine, listOf(LocalTime.of(1, 0)), name = "Editor plan")
        openPlanEditor("Editor plan")
        val nightOwlRow = hasText(text(R.string.settings_medication_day_ends_at_four_am)) and hasClickAction()
        awaitText(text(R.string.settings_medication_day_ends_at_four_am))
        await { compose.onAllNodes(nightOwlRow).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(nightOwlRow).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.onNode(hasText(text(R.string.save)) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        await { io { entryPoint.medicationGroupRepository().getGroups() }.single().schedule.dayBoundary == nightOwl }

        // After a relaunch Home reads the stored ending: at 06:00 tonight's 01:00 is the evening's, with the moon.
        scenario!!.recreate()
        scenario!!.close()
        launch()
        await { todayExists(hasText(moon(clock(1, 0)))) }

        val plan = io { entryPoint.medicationGroupRepository().getGroups() }.single()
        log(medicine, day.plusDays(1).atTime(1, 0), plan, day.plusDays(1).atTime(1, 0))
        openPlanEditor("Editor plan")
        awaitText(text(R.string.group_locked_banner))
        awaitText(text(R.string.settings_medication_day_ends_at_four_am))
        // Locked: the row no longer turns it off, and its switch stays on but disabled.
        val nightOwlSwitch = isToggleable() and hasParent(hasText(text(R.string.settings_medication_day_ends_at_four_am)))
        await {
            compose.onAllNodes(nightOwlRow and isEnabled()).fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodes(nightOwlSwitch and isOn() and isNotEnabled()).fetchSemanticsNodes().size == 1
        }
        assertEquals(nightOwl, io { entryPoint.medicationGroupRepository().getGroup(plan.uuid)!! }.schedule.dayBoundary)
    }

    @Test
    fun defaultMidnightKeepsDawnInCivilDay() {
        val dawn = medicine("Dawn dose")
        log(dawn, day.plusDays(1).atTime(1, 0))
        assertTodayDate(day.plusDays(1))
        assertTodayMedicine(dawn)
        history()
        selectHistoryDate(day.plusDays(1))
        awaitText("Dawn dose")
    }

    @Test
    fun manualRecordsStayOnTheCalendarDayAtFourAmWhileANightOwlDoseCrossesTheMonth() {
        val nightOwlDose = medicine("Night owl dose")
        val plan = group(nightOwlDose, listOf(LocalTime.of(1, 0)), dayBoundary = nightOwl)
        val before = medicine("Before boundary")
        val exact = medicine("At boundary")
        log(nightOwlDose, day.plusDays(1).atTime(3, 59, 59), plan, day.plusDays(1).atTime(1, 0))
        log(before, day.plusDays(1).atTime(3, 59, 59))
        log(exact, day.plusDays(1).atTime(4, 0))

        // The app reads the clock by the minute, so 03:59:00 shows the same Home as 03:59:59.
        device.setTime(day.plusDays(1).atTime(3, 59))
        // The night-owl plan's evening is still today, with its dose done; only 03:59 reads 1/1.
        assertTodayCount("1/1")
        assertTodayDate(day.plusDays(1))
        assertTodayMedicine(before)
        assertTodayMedicine(exact)
        assertTrue(compose.onAllNodes(hasText(text(R.string.main_last_night_title).uppercase())).fetchSemanticsNodes().isEmpty())
        device.setTime(day.plusDays(1).atTime(4, 0))
        assertTodayCount("0/1")
        assertTodayDate(day.plusDays(1))
        assertTodayMedicine(before)
        assertTodayMedicine(exact)
        awaitText(text(R.string.main_last_night_title).uppercase())

        history()
        selectHistoryDate(day.plusDays(1))
        awaitText("Before boundary")
        awaitText("At boundary")
        selectHistoryDate(day)
        awaitText("Night owl dose")
        compose.onNodeWithText("Before boundary").assertDoesNotExist()
        compose.onNodeWithText("At boundary").assertDoesNotExist()
    }

    @Test
    fun nightOwlPlanKeepsItsSmallHoursOnTheEveningButNotFourAm() {
        val dawnPlans = listOf(LocalTime.of(0, 0), LocalTime.of(1, 0), LocalTime.of(3, 59)).map { time ->
            val medicine = medicine("Dawn ${time.hour}")
            log(medicine, day.plusDays(1).atTime(time), group(medicine, listOf(time), dayBoundary = nightOwl, since = day), day.plusDays(1).atTime(time))
            medicine
        }
        val fourAm = medicine("Four o'clock")
        val fourAmPlan = group(fourAm, listOf(LocalTime.of(4, 0)), dayBoundary = nightOwl, since = day.plusDays(1))
        log(fourAm, day.plusDays(1).atTime(4, 0), fourAmPlan, day.plusDays(1).atTime(4, 0))
        val midnight = medicine("Midnight dawn")
        val midnightPlan = group(midnight, listOf(LocalTime.of(1, 0)), since = day.plusDays(1))
        log(midnight, day.plusDays(1).atTime(1, 0), midnightPlan, day.plusDays(1).atTime(1, 0))

        assertTodayMedicine(fourAm)
        assertTodayMedicine(midnight)
        history()
        selectHistoryDate(day.plusDays(1))
        awaitText("Four o'clock")
        awaitText("Midnight dawn")
        dawnPlans.forEach { compose.onNodeWithText((it.selection as MedicineSelection.Custom).medicationName).assertDoesNotExist() }
        selectHistoryDate(day)
        listOf(0, 1, 3).forEach { awaitText("Dawn $it") }
        compose.onNodeWithText("Four o'clock").assertDoesNotExist()
        compose.onNodeWithText("Midnight dawn").assertDoesNotExist()
    }

    @Test
    fun dailyPlanOrdersNextDayDawnLastAndDoesNotDuplicateContextRows() {
        device.setTime(day.atTime(20, 0))
        group(medicine("Daily dose"), listOf(LocalTime.of(8, 0), LocalTime.of(16, 0), LocalTime.of(1, 0)), dayBoundary = nightOwl)
        val midnight = medicine("Midnight dose")
        group(midnight, listOf(LocalTime.of(1, 0)))
        assertTodayDate(day)
        assertTodayCount("0/4")
        await { todayExists(hasText(moon(clock(1, 0)))) }
        // The midnight plan's 01:00 opens the day; the night-owl plan's comes after its evening.
        val midnightRow = todayTop(hasText("Midnight dose"))
        val morning = todayTop(hasText(text(R.string.main_today_range_morning), substring = true))
        val nightOwlDawn = todayTop(hasText(moon(clock(1, 0))))
        assertTrue("The midnight plan's 01:00 should open the day", midnightRow < morning)
        assertTrue("The night-owl plan's 01:00 should follow daytime doses", nightOwlDawn > morning)
        compose.onAllNodes(hasContentDescription(text(R.string.main_today_quick_log)) and hasAnyAncestor(hasTestTag("main_today_section"))).assertCountEquals(4)
        // Tonight previews only the midnight plan's next 01:00.
        val tonight = text(R.string.main_coming_up_title).uppercase()
        awaitText(tonight)
        val tomorrow = text(R.string.main_tomorrow_title).uppercase()
        awaitText(tomorrow)
        fun inTonight(name: String) = compose.onAllNodes(hasText(name) and !hasAnyAncestor(hasTestTag("main_today_section")))
            .fetchSemanticsNodes().filter { node -> node.boundsInRoot.top > top(hasText(tonight)) && node.boundsInRoot.top < top(hasText(tomorrow)) }
        assertEquals(1, inTonight("Midnight dose").size)
        assertEquals(0, inTonight("Daily dose").size)
    }

    @Test
    fun quickLogThreeSlotsUpdatesHomeCalendarAndStoredOccurrenceTimes() {
        device.setTime(day.atTime(8, 0))
        group(medicine("Daily dose"), listOf(LocalTime.of(8, 0), LocalTime.of(16, 0), LocalTime.of(1, 0)), dayBoundary = nightOwl)
        listOf(day.atTime(8, 0), day.atTime(16, 0), day.plusDays(1).atTime(1, 0)).forEachIndexed { index, time ->
            device.setTime(time)
            // Today's header is the calendar date; the 01:00 dose still counts toward the evening.
            assertTodayDate(time.toLocalDate())
            assertTodayCount("$index/3")
            compose.onAllNodes(hasContentDescription(text(R.string.main_today_quick_log)) and hasAnyAncestor(hasTestTag("main_today_section")))[0]
                .performSemanticsAction(SemanticsActions.OnClick)
            awaitText(text(R.string.save))
            compose.onNodeWithText(text(R.string.save)).performSemanticsAction(SemanticsActions.OnClick)
            assertTodayCount("${index + 1}/3")
            await { compose.onAllNodes(hasText(text(R.string.save))).fetchSemanticsNodes().isEmpty() }
        }
        val records = io { entryPoint.medicationLogRepository().getEntries() }
        assertEquals(3, records.size)
        assertEquals(
            setOf(day.atTime(8, 0), day.atTime(16, 0), day.plusDays(1).atTime(1, 0)),
            records.map { it.scheduledFor }.toSet(),
        )
        scenario!!.recreate()
        assertTodayCount("3/3")
        tab(R.string.tab_plan)
        selectPlanDate(day)
        await { compose.onAllNodesWithTag("plan_day_row").fetchSemanticsNodes().size == 3 }
        history()
        selectHistoryDate(day)
        awaitText("Daily dose")
    }

    @Test
    fun manualDoseDoesNotFulfillPlanAndLinkedSupplementKeepsScheduledDay() {
        val medicine = medicine("Evening dose")
        val group = group(medicine, listOf(LocalTime.of(23, 0)), dayBoundary = nightOwl)
        device.setTime(day.plusDays(1).atTime(1, 0))
        // At 01:00 Today holds the evening's overdue 23:00 (no plain time), not tonight's.
        await { todayExists(hasText("Evening dose")) && !todayExists(hasText(clock(23, 0))) }
        val manual = log(medicine, day.plusDays(1).atTime(1, 0))
        assertTodayCount("0/1")
        io { entryPoint.medicationLogRepository().deleteEntries(listOf(manual.uuid)) }
        log(medicine, day.plusDays(1).atTime(1, 0), group, day.atTime(23, 0))
        assertTodayCount("1/1")
        device.setTime(day.plusDays(1).atTime(6, 0))
        assertTodayDate(day.plusDays(1))
        history()
        selectHistoryDate(day)
        awaitText("Evening dose")
    }

    @Test
    fun mondayDawnSlotStaysOnMondayAndFiresInItsEarlyHours() {
        val monday = LocalDate.of(2026, 11, 2)
        device.setTime(monday.atTime(20, 0))
        val nightOwlPlan = group(medicine("Weekly night owl"), listOf(LocalTime.of(1, 0)), setOf(DayOfWeek.MONDAY), dayBoundary = nightOwl)
        val midnightPlan = group(medicine("Weekly midnight"), listOf(LocalTime.of(1, 0)), setOf(DayOfWeek.MONDAY))
        assertTodayDate(monday)
        assertTodayCount("0/2")
        tab(R.string.tab_plan)
        compose.onNodeWithTag("plan_calendar_day_$monday").performClick()
        await { compose.onAllNodesWithTag("plan_day_row").fetchSemanticsNodes().size == 2 }
        compose.onNodeWithTag("plan_calendar_day_${monday.plusDays(1)}").performClick()
        awaitText(text(R.string.plan_selected_day_records_empty))
        assertEquals(
            listOf(monday.plusDays(1).atTime(1, 0)),
            io { entryPoint.medicationGroupRepository().getGroup(nightOwlPlan.uuid)!! }
                .schedule.nextOccurrencesFrom(monday.atTime(20, 0), limit = 1),
        )
        assertEquals(
            listOf(monday.plusDays(7).atTime(1, 0)),
            io { entryPoint.medicationGroupRepository().getGroup(midnightPlan.uuid)!! }
                .schedule.nextOccurrencesFrom(monday.atTime(20, 0), limit = 1),
        )
    }

    @Test
    fun nightOwlDawnRecordShowsTheMoonOnItsEveningAndNoRecordIsRewritten() {
        val nightOwlDose = medicine("Night owl dose")
        val plan = group(nightOwlDose, listOf(LocalTime.of(1, 0)), dayBoundary = nightOwl)
        val linked = log(nightOwlDose, day.plusDays(1).atTime(1, 0), plan, day.plusDays(1).atTime(1, 0))
        val manual = log(medicine("Manual dawn dose"), day.plusDays(1).atTime(1, 0))

        tab(R.string.tab_plan)
        selectPlanDate(day)
        await {
            compose.onAllNodes(hasText(moon(clock(1, 0))) and hasAnyAncestor(hasTestTag("plan_day_row")))
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertNoDayOffset()

        history()
        selectHistoryDate(day)
        awaitText("Night owl dose")
        awaitText(moon(clock(1, 0)))
        assertNoDayOffset()
        compose.onNodeWithText("Manual dawn dose").assertDoesNotExist()
        selectHistoryDate(day.plusDays(1))
        awaitText("Manual dawn dose")
        awaitText(clock(1, 0))
        compose.onNodeWithText(moon(clock(1, 0))).assertDoesNotExist()

        assertEquals(linked, io { entryPoint.medicationLogRepository().getEntry(linked.uuid) })
        assertEquals(manual, io { entryPoint.medicationLogRepository().getEntry(manual.uuid) })
    }

    @Test
    fun homeAtHalfPastMidnightReadsEachRowsOwnPlan() {
        group(medicine("Night owl dose"), listOf(LocalTime.of(22, 0), LocalTime.of(2, 0)), dayBoundary = nightOwl)
        val morning = medicine("Morning dose")
        group(morning, listOf(LocalTime.of(8, 0)))
        device.setTime(day.plusDays(1).atTime(0, 30))
        assertTodayDate(day.plusDays(1))
        assertTodayMedicine(morning)
        // Tonight's 02:00 is the evening's: the moon, and no day offset. The evening's 22:00 is
        // overdue, so it shows no plain time; at 06:00 it would be tonight's, with one.
        await { todayExists(hasText(moon(clock(2, 0)))) && !todayExists(hasText(clock(22, 0))) }
        assertNoDayOffset()
        val tomorrow = text(R.string.main_tomorrow_title).uppercase()
        awaitText(tomorrow)
        assertEquals(emptyList<String>(), headerSummary(tomorrow))

        // The Plan tab's Oct 31: tonight's 02:00 is still to come (planned, not missed); the untaken 22:00 is missed.
        tab(R.string.tab_plan)
        selectPlanDate(day)
        val planned = text(R.string.plan_schedule_entry_planned).uppercase()
        val missed = text(R.string.plan_schedule_entry_missed).uppercase()
        fun planRow(vararg texts: String) = compose.onAllNodes(
            texts.map { hasText(it) }.reduce { a, b -> a and b } and hasAnyAncestor(hasTestTag("plan_day_row"))
        ).fetchSemanticsNodes()
        await { planRow(moon(clock(2, 0)), planned).isNotEmpty() && planRow(clock(22, 0), missed).isNotEmpty() }
        assertTrue(planRow(moon(clock(2, 0)), missed).isEmpty())
        tab(R.string.tab_main)

        device.setTime(day.plusDays(1).atTime(4, 0))
        await { headerSummary(tomorrow).any { it.contains(dateText(day.plusDays(2)), ignoreCase = true) } }
        assertTodayDate(day.plusDays(1))
        await { todayExists(hasText(moon(clock(2, 0)))) }
    }

    @Test
    fun antiandrogenDueLabelAndComingUpTitleFollowEachCardsOwnPlan() {
        io {
            entryPoint.settingsRepository().setHomeCardLayout(
                HomeCardType.entries,
                HomeCardType.entries.filter { it != HomeCardType.TIMELINE && it != HomeCardType.ANTIANDROGEN }.toSet(),
            )
        }
        val nightOwlBlocker = medicine("Night owl blocker", MedicationCategory.ANTIANDROGEN)
        val nightOwlPlan = group(nightOwlBlocker, listOf(LocalTime.of(8, 0), LocalTime.of(22, 0)), dayBoundary = nightOwl, since = day)
        log(nightOwlBlocker, day.atTime(8, 0), nightOwlPlan, day.atTime(8, 0))
        log(nightOwlBlocker, day.atTime(22, 0), nightOwlPlan, day.atTime(22, 0))
        val midnightBlocker = medicine("Midnight blocker", MedicationCategory.ANTIANDROGEN)
        val midnightPlan = group(midnightBlocker, listOf(LocalTime.of(22, 0)), since = day)
        log(midnightBlocker, day.atTime(22, 0), midnightPlan, day.atTime(22, 0))
        device.setTime(day.plusDays(1).atTime(0, 30))
        assertTodayDate(day.plusDays(1))

        // Both next doses are Nov 1; only the night-owl plan's day is still Oct 31.
        val dueTomorrow = text(R.string.main_antiandrogen_next_dose, text(R.string.main_relative_tomorrow))
        val dueTonight = text(R.string.main_antiandrogen_next_dose, clock(22, 0))
        awaitText(dueTomorrow)
        awaitText(dueTonight)
        assertTrue(
            "The night-owl card, created first, should say tomorrow",
            top(hasText(dueTomorrow)) < top(hasText(dueTonight)),
        )
        // Coming up is the night-owl plan's next morning, not the midnight plan's tonight.
        awaitText(text(R.string.main_coming_up_morning_title).uppercase())
        compose.onNodeWithText(text(R.string.main_coming_up_title).uppercase()).assertDoesNotExist()
    }

    @Test
    fun foregroundTickerMovesANightOwlPlanToItsNewDayAtFourAm() {
        group(medicine("Night owl dose"), listOf(LocalTime.of(1, 0)), dayBoundary = nightOwl)
        val midnight = medicine("Midnight dose")
        group(midnight, listOf(LocalTime.of(1, 0)))
        device.setTime(day.plusDays(1).atTime(3, 59, 50))
        assertTodayDate(day.plusDays(1))
        val lastNight = text(R.string.main_last_night_title).uppercase()
        // Before 04:00 both plans' 01:00 doses are Today's and overdue, and nothing is Last night.
        await {
            todayExists(hasText("0/2", substring = true)) &&
                compose.onAllNodes(hasText(lastNight)).fetchSemanticsNodes().isEmpty() &&
                !todayExists(hasText(moon(clock(1, 0))))
        }
        await(timeout = 25_000) {
            todayExists(hasText(moon(clock(1, 0)))) && compose.onAllNodes(hasText(lastNight)).fetchSemanticsNodes().isNotEmpty()
        }
        assertTodayDate(day.plusDays(1))
        assertTodayMedicine(midnight)
    }

    @Test
    fun backgroundAndActivityRelaunchReadNewDayAfterClockChange() {
        group(medicine("Night owl dose"), listOf(LocalTime.of(1, 0)), dayBoundary = nightOwl)
        group(medicine("Midnight dose"), listOf(LocalTime.of(1, 0)))
        device.setTime(day.atTime(23, 59))
        assertTodayDate(day)
        await { todayExists(hasText(moon(clock(1, 0)))) }
        assertFalse(todayExists(hasText(clock(1, 0))))
        scenario!!.moveToState(Lifecycle.State.CREATED)
        device.setTime(day.plusDays(1).atTime(0, 0))
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        // The midnight plan rolls over at 00:00; the night-owl plan's 01:00 stays the evening's.
        assertTodayDate(day.plusDays(1))
        await { todayExists(hasText(clock(1, 0))) }
        assertTrue(todayExists(hasText(moon(clock(1, 0)))))
        // The night-owl plan re-anchors at 04:00 while backgrounded: its 01:00 moves to Last night.
        val lastNight = text(R.string.main_last_night_title).uppercase()
        device.setTime(day.plusDays(1).atTime(3, 59))
        await { compose.onAllNodes(hasText(lastNight)).fetchSemanticsNodes().isEmpty() }
        scenario!!.moveToState(Lifecycle.State.CREATED)
        device.setTime(day.plusDays(1).atTime(4, 0))
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        awaitText(lastNight)
        assertTodayDate(day.plusDays(1))
        scenario!!.close()
        launch()
        assertTodayDate(day.plusDays(1))
        awaitText(text(R.string.main_last_night_title).uppercase())
    }

    @Test
    fun yearBoundaryPlacesJanuaryDawnInDecemberHistory() {
        val december = LocalDate.of(2026, 12, 31)
        device.setTime(december.plusDays(1).atTime(6, 0))
        val nightOwlDose = medicine("New year dawn dose")
        val plan = group(nightOwlDose, listOf(LocalTime.of(1, 0)), dayBoundary = nightOwl)
        log(nightOwlDose, december.plusDays(1).atTime(1, 0), plan, december.plusDays(1).atTime(1, 0))
        log(medicine("New year manual dose"), december.plusDays(1).atTime(1, 0))
        history()
        selectHistoryDate(december.plusDays(1))
        awaitText("New year manual dose")
        selectHistoryDate(december)
        awaitText("New year dawn dose")
        compose.onNodeWithText("New year manual dose").assertDoesNotExist()
    }

    @Test
    fun storedEntryZoneStillControlsManualDayAfterDeviceZoneChanges() {
        val medicine = medicine("Tokyo dose")
        log(medicine, day.plusDays(1).atTime(6, 0), zone = ZoneId.of("Asia/Tokyo"))
        assertTodayMedicine(medicine)
        device.setZone("UTC")
        device.setTime(day.plusDays(1).atTime(12, 0))
        io { entryPoint.settingsRepository().acknowledgeTimeZone("UTC") }
        assertTodayMedicine(medicine)
        history()
        selectHistoryDate(day.plusDays(1))
        awaitText("Tokyo dose")
    }

    @Test
    fun editingAndDeletingManualDawnRecordKeepsItOnItsCalendarDay() {
        val medicine = medicine("Editable dose")
        val record = log(medicine, day.plusDays(1).atTime(3, 59))
        assertTodayMedicine(medicine)
        history()
        selectHistoryDate(day.plusDays(1))
        awaitText("Editable dose")
        compose.onNodeWithText("Editable dose").performSemanticsAction(SemanticsActions.OnClick)
        awaitText(text(R.string.save))
        await { compose.onAllNodes(hasContentDescription(text(R.string.select_time)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(text(R.string.select_time), useUnmergedTree = true).performScrollTo().performClick()
        pickTime(4, 0)
        compose.onNodeWithText(text(R.string.confirm)).performClick()
        compose.onNodeWithText(text(R.string.save)).performClick()
        await { io { entryPoint.medicationLogRepository().getEntry(record.uuid)!!.appliedAt } == day.plusDays(1).atTime(4, 0).atZone(ZoneId.systemDefault()).toInstant() }
        await { compose.onAllNodes(hasText(text(R.string.save))).fetchSemanticsNodes().isEmpty() }
        tab(R.string.tab_main)
        assertTodayMedicine(medicine)
        compose.onNodeWithContentDescription(text(R.string.edit_entry)).performSemanticsAction(SemanticsActions.OnClick)
        awaitText(text(R.string.delete_entries_confirm))
        compose.onNodeWithText(text(R.string.delete_entries_confirm)).performSemanticsAction(SemanticsActions.OnClick)
        awaitText(text(R.string.delete_editing_entry_confirmation))
        compose.onNode(
            hasText(text(R.string.delete_entries_confirm)) and hasAnyAncestor(
                isDialog() and hasAnyDescendant(hasText(text(R.string.delete_editing_entry_confirmation)))
            )
        ).performSemanticsAction(SemanticsActions.OnClick)
        await { io { entryPoint.medicationLogRepository().getEntry(record.uuid) } == null }
        assertTodayDate(day.plusDays(1))
        await { !todayExists(hasText("Editable dose")) }
    }

    @Test
    fun archiveVisibilitySettingHidesAndRestoresHistoryWithoutDeletingRecords() {
        val medicine = medicine("Archived dawn dose")
        val group = group(medicine, listOf(LocalTime.of(1, 0)), dayBoundary = nightOwl)
        val record = log(medicine, day.plusDays(1).atTime(1, 0), group, day.plusDays(1).atTime(1, 0))
        io {
            entryPoint.medicationGroupRepository().archiveGroup(
                uuid = group.uuid,
            )
        }
        history()
        selectHistoryDate(day)
        awaitText("Archived dawn dose")
        tab(R.string.tab_settings)
        compose.onNode(hasText(text(R.string.settings_hide_archived_group_records)) and hasClickAction())
            .performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        await { !io { entryPoint.settingsRepository().getCurrentSettings().showArchivedGroupRecords } }
        // History opens fresh on today, and with the only record hidden there is none to show.
        history()
        awaitText(text(R.string.history_empty_state))
        compose.onNodeWithText("Archived dawn dose").assertDoesNotExist()
        tab(R.string.tab_settings)
        compose.onNode(hasText(text(R.string.settings_hide_archived_group_records)) and hasClickAction())
            .performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        history()
        selectHistoryDate(day)
        awaitText("Archived dawn dose")
        assertEquals(record, io { entryPoint.medicationLogRepository().getEntry(record.uuid) })
    }

    @Test
    fun encryptedBackupRestoresAPlansNightOwl() {
        val medicine = medicine("Backed up dawn dose")
        val plan = group(medicine, listOf(LocalTime.of(1, 0)), dayBoundary = nightOwl)
        log(medicine, day.plusDays(1).atTime(1, 0), plan, day.plusDays(1).atTime(1, 0))
        val encrypted = io { entryPoint.backupExportService().buildEncryptedBackupBytes("e2e-password") }
        io { clearDatabase() }
        io { entryPoint.backupRestoreService().restoreBackupBytes(encrypted, "e2e-password") }
        assertEquals(nightOwl, io { entryPoint.medicationGroupRepository().getGroups() }.single().schedule.dayBoundary)
        history()
        selectHistoryDate(day)
        awaitText("Backed up dawn dose")
    }

    @Test
    fun olderBackupRestoresPlansAsMidnight() {
        val medicine = medicine("Backed up dawn dose")
        val plan = group(medicine, listOf(LocalTime.of(1, 0)), dayBoundary = nightOwl)
        log(medicine, day.plusDays(1).atTime(1, 0), plan, day.plusDays(1).atTime(1, 0))
        val json = JSONObject(io { entryPoint.backupExportService().buildBackupSnapshotJson() })
        val groups = json.getJSONArray("medicationGroups")
        assertEquals(1, groups.length())
        assertTrue(groups.getJSONObject(0).getBoolean("dayEndsAtFourAm"))
        groups.getJSONObject(0).remove("dayEndsAtFourAm")
        json.put("snapshotVersion", 7)
        val older = io { entryPoint.backupCrypto().encryptSnapshotJson(json.toString(), "e2e-password".toCharArray()) }
        io { clearDatabase() }
        io { entryPoint.backupRestoreService().restoreBackupBytes(older, "e2e-password") }
        assertEquals(MedicationDayBoundary.Midnight, io { entryPoint.medicationGroupRepository().getGroups() }.single().schedule.dayBoundary)
        history()
        selectHistoryDate(day.plusDays(1))
        awaitText("Backed up dawn dose")
        selectHistoryDate(day)
        awaitText(text(R.string.history_selected_day_empty_state))
        compose.onNodeWithText("Backed up dawn dose").assertDoesNotExist()
    }

    @Test
    fun realWidgetUpdatesCountsAndRefreshesAtMedicationDayBoundary() {
        val medicine = medicine("Widget dose")
        val group = group(medicine, listOf(LocalTime.of(1, 0)), dayBoundary = nightOwl)
        log(medicine, day.plusDays(1).atTime(1, 0), group, day.plusDays(1).atTime(1, 0))
        val midnight = medicine("Midnight widget dose")
        val midnightGroup = group(midnight, listOf(LocalTime.of(1, 0)))
        log(midnight, day.plusDays(1).atTime(1, 0), midnightGroup, day.plusDays(1).atTime(1, 0))
        device.setTime(day.plusDays(1).atTime(3, 58))
        assertTodayCount("2/2")
        E2eWidgetHost(context).use { host ->
            host.attach(scenario!!)
            awaitWidgetCount(host, "2/2")
            val before = io { entryPoint.widgetSnapshotStore().readSnapshot()!! }
            assertEquals(day.plusDays(1).toEpochDay(), before.anchorDateEpochDay)
            assertEquals(day.toEpochDay(), before.fourAmAnchorDateEpochDay)
            device.setTime(day.plusDays(1).atTime(3, 59, 50))
            // At 04:00 the night-owl plan's next 01:00 is due; the midnight plan keeps its logged dose.
            EmulatorDeviceState.waitFor("widget date alarm", 25_000) {
                val snapshot = io { entryPoint.widgetSnapshotStore().readSnapshot() }
                snapshot?.fourAmAnchorDateEpochDay == day.plusDays(1).toEpochDay() &&
                    snapshot.anchorDateEpochDay == day.plusDays(1).toEpochDay() &&
                    snapshot.doneCount == 1 && snapshot.totalCount == 2
            }
            awaitWidgetCount(host, "1/2")
        }
    }

    @Test
    fun actualReminderFiresOnTuesdayAtOneForANightOwlMondayPlan() {
        EmulatorDeviceState.shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        EmulatorDeviceState.shell("appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow")
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        val tuesday = LocalDate.of(2026, 11, 3)
        device.setTime(tuesday.atTime(0, 58))
        group(medicine("Reminder dose"), listOf(LocalTime.of(1, 0)), setOf(DayOfWeek.MONDAY), notifications = true, dayBoundary = nightOwl, name = "Night owl plan")
        group(medicine("Midnight reminder dose"), listOf(LocalTime.of(1, 0)), setOf(DayOfWeek.MONDAY), notifications = true, name = "Midnight plan")
        io {
            entryPoint.settingsRepository().setRemindersEnabled(true)
            entryPoint.reminderScheduler().rescheduleAll()
        }
        assertTrue(manager.activeNotifications.none { it.notification.channelId == REMINDER_CHANNEL_ID })
        device.setTime(tuesday.atTime(0, 59, 50))
        EmulatorDeviceState.waitFor("real 01:00 reminder", 25_000) {
            manager.activeNotifications.any { it.notification.channelId == REMINDER_CHANNEL_ID }
        }
        assertEquals(LocalTime.of(1, 0), LocalTime.now().withSecond(0).withNano(0))
        val texts = manager.activeNotifications
            .filter { it.notification.channelId == REMINDER_CHANNEL_ID }
            .map { it.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() }
        assertTrue("Expected the night-owl plan's reminder; got $texts", "Night owl plan" in texts)
        assertTrue("The midnight plan reminded on Tuesday: $texts", texts.none { it?.contains("Midnight plan") == true })
        manager.cancelAll()
    }

    @Test
    fun journalStillUsesCivilDayWhenANightOwlPlansDayIsYesterday() {
        val medicine = medicine("Night owl dose")
        group(medicine, listOf(LocalTime.of(1, 0)), dayBoundary = nightOwl)
        device.setTime(day.plusDays(1).atTime(1, 32))
        assertTodayDate(day.plusDays(1))
        assertTodayMedicine(medicine)
        // Only 01:32 has the evening's 01:00 in Today with no Last night (06:00 has one).
        await {
            todayExists(hasText(moon(clock(1, 0)))) &&
                compose.onAllNodes(hasText(text(R.string.main_last_night_title).uppercase())).fetchSemanticsNodes().isEmpty()
        }
        io { entryPoint.journalRepository().saveNoteForDate(day.plusDays(1), "Civil-day note") }
        tab(R.string.tab_journal)
        awaitText("Civil-day note")
        await { compose.onAllNodes(hasText("Nov 1", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(day.plusDays(1).toString(), io { entryPoint.journalRepository().getNoteEntities().single().dateIso })
    }

    @Test
    fun lateDoseFulfillsFourAmIntervalPlan() {
        device.setTime(day.plusDays(1).atTime(20, 0))
        val medicine = medicine("Interval dose")
        val group = group(medicine, listOf(LocalTime.of(1, 0)), interval = 2, dayBoundary = nightOwl)
        val scheduled = day.plusDays(1).atTime(1, 0)
        log(medicine, day.plusDays(1).atTime(20, 0), group, scheduled)

        tab(R.string.tab_plan)
        selectPlanDate(day)
        awaitText("1/1")
        compose.onNodeWithText("0/1", substring = true).assertDoesNotExist()
    }

    @Test
    fun stockRunwayUsesFourAmIntervalPlan() {
        val now = day.plusDays(1).atTime(6, 0)
        device.setTime(now)
        val medicine = medicine("Runway dose")
        group(medicine, listOf(LocalTime.of(1, 0)), interval = 2, dayBoundary = nightOwl)
        io {
            entryPoint.medicineRepository().enableTracking(
                uuid = medicine.uuid,
                initialUnitsRemaining = 1.0,
                initialOpenContainerAmount = null,
                initialUnitsLastTotal = 1.0,
                now = now.atZone(ZoneId.systemDefault()).toInstant(),
            )
        }

        val runway = io {
            entryPoint.medicineStockRepository().projectAllOnce(
                now = now.atZone(ZoneId.systemDefault()).toInstant(),
                zoneId = ZoneId.systemDefault(),
            ).single { projection -> projection.medicine.uuid == medicine.uuid }.runway
        }

        assertEquals(
            RunwayProjection.Days(days = 0, lastFulfillable = day.plusDays(1)),
            runway,
        )
    }

    @Test
    fun pkPlannedDosesUseFourAmIntervalPlan() {
        val now = day.plusDays(1).atTime(6, 0)
        device.setTime(now)
        val estradiol = io {
            entryPoint.medicineRepository().findOrCreateForCatalog(
                medicationKey = MedicationKey.ESTRADIOL,
                preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
            )
        }
        group(estradiol, listOf(LocalTime.of(1, 0)), interval = 2, dayBoundary = nightOwl)
        io { entryPoint.homeSnapshotRepository().rebuildNow() }

        val estimate = io {
            withTimeout(20_000) {
                entryPoint.e2EstimateRepository()
                    .observeHome(flowOf(now))
                    .first { state -> state.value != null }
                    .value!!
            }
        }

        fun plannedDoseTimes(estimate: HomeE2Estimate): List<LocalDateTime> =
            estimate.projection.doseMarkers
                .filter { marker -> marker.isPlanned }
                .map { marker ->
                    estimate.projection.windowStart
                        .plusSeconds(Math.round(marker.timeH * 3_600.0))
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime()
                }

        fun hasDoseAt(times: List<LocalDateTime>, expected: LocalDateTime): Boolean =
            times.any { actual ->
                Duration.between(actual, expected).abs() <= Duration.ofMinutes(2)
            }

        val fourAmDoseTimes = plannedDoseTimes(estimate)
        assertTrue(
            "Expected a planned dose at ${day.plusDays(3).atTime(1, 0)}; got $fourAmDoseTimes",
            hasDoseAt(fourAmDoseTimes, day.plusDays(3).atTime(1, 0)),
        )
        assertFalse(
            "Calendar-day planned dose leaked into the night-owl plan: $fourAmDoseTimes",
            hasDoseAt(fourAmDoseTimes, day.plusDays(2).atTime(1, 0)),
        )
    }

    @Test
    fun widgetComingUpMirrorsManualAndArchivedRows() {
        device.setTime(day.plusDays(1).atTime(20, 0))

        val manualMedicine = medicine("Manual midnight")
        log(manualMedicine, day.plusDays(2).atTime(0, 30))

        val archivedMedicine = medicine("Archived midnight")
        val archivedGroup = group(archivedMedicine, listOf(LocalTime.of(0, 30)))
        io {
            entryPoint.medicationGroupRepository().archiveGroup(
                uuid = archivedGroup.uuid,
            )
        }
        log(
            medicine = archivedMedicine,
            time = day.plusDays(2).atTime(0, 35),
            group = archivedGroup,
            scheduled = day.plusDays(2).atTime(0, 30),
        )
        io { entryPoint.homeSnapshotRepository().rebuildNow() }

        E2eWidgetHost(context).use { host ->
            host.attach(scenario!!)
            EmulatorDeviceState.waitFor("widget coming up rows", 20_000) {
                val rows = io {
                    entryPoint.widgetSnapshotStore().readSnapshot()?.doseRows
                        ?.filter { row -> row.contextChip == WidgetDoseChip.COMING_UP }
                        .orEmpty()
                }
                rows.size == 2 &&
                    rows.any { row -> row.isManualRecord } &&
                    rows.any { row -> row.isFromArchivedGroup }
            }
        }
    }

    @Test
    fun widgetDropsLastNightAtNoon() {
        device.setTime(day.plusDays(1).atTime(11, 59, 50))
        val medicine = medicine("Last night dose")
        val group = group(medicine, listOf(LocalTime.of(20, 0), LocalTime.of(1, 0)), dayBoundary = nightOwl)
        log(medicine, day.atTime(20, 0), group, day.atTime(20, 0))
        log(medicine, day.plusDays(1).atTime(1, 0), group, day.plusDays(1).atTime(1, 0))
        io { entryPoint.homeSnapshotRepository().rebuildNow() }

        E2eWidgetHost(context).use { host ->
            host.attach(scenario!!)
            // Last night holds the night-owl plan's 01:00 next to its 20:00.
            EmulatorDeviceState.waitFor("last night rows before noon", 20_000) {
                io {
                    entryPoint.widgetSnapshotStore().readSnapshot()?.doseRows
                        ?.count { row -> row.contextChip == WidgetDoseChip.LAST_NIGHT } == 2
                }
            }
            EmulatorDeviceState.waitFor("last night removed at noon", 25_000) {
                val snapshot = io { entryPoint.widgetSnapshotStore().readSnapshot() }
                snapshot != null && snapshot.doseRows.none { row ->
                    row.contextChip == WidgetDoseChip.LAST_NIGHT
                }
            }
            EmulatorDeviceState.waitFor("widget last night header removed", 20_000) {
                !host.visibleTexts().any { label -> label == text(R.string.widget_last_night) }
            }
        }
    }

    private fun launch() {
        assertAppLocaleIsEnglish()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        await { compose.onAllNodesWithTag("main_today_section").fetchSemanticsNodes().isNotEmpty() }
    }

    /**
     * Pins the app's per-app locale to en at the platform, for the emulator's own language may not
     * be English. First marks AppCompat's one-time locale migration as done: with `autoStoreLocales`,
     * the first AppCompat activity start after install (AGP reinstalls on every run) sees no app
     * locales yet and writes AppCompat's own stored list, empty on a fresh install, to the platform,
     * clearing the pin to the system language. Enabling AppLocalesMetadataHolderService is the
     * migration's done marker; only the app's own uid may set it, so shell `pm enable` can't.
     */
    private fun pinEnglish() {
        originalLocaleMarkerState = context.packageManager.getComponentEnabledSetting(localeMarker)
        context.packageManager.setComponentEnabledSetting(
            localeMarker,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        val setLocale = EmulatorDeviceState.shell("cmd locale set-app-locales ${context.packageName} --locales en").trim()
        check(setLocale.isEmpty()) { "Could not pin the app locale to en: $setLocale" }
        assertAppLocaleIsEnglish()
    }

    /**
     * Back to the system language and AppCompat's migration marker as it was, so later
     * instrumented tests in the same run see the device's own locale and first launch.
     */
    private fun resetAppLocale() {
        context.packageManager.setComponentEnabledSetting(
            localeMarker,
            checkNotNull(originalLocaleMarkerState),
            PackageManager.DONT_KILL_APP,
        )
        val reset = EmulatorDeviceState.shell("cmd locale set-app-locales ${context.packageName}").trim()
        val appLocales = EmulatorDeviceState.shell("cmd locale get-app-locales ${context.packageName}").trim()
        check(reset.isEmpty() && appLocales.endsWith("[]")) { "Could not reset the app locale: $reset / $appLocales" }
    }

    private fun assertAppLocaleIsEnglish() {
        val appLocales = EmulatorDeviceState.shell("cmd locale get-app-locales ${context.packageName}").trim()
        check("[en]" in appLocales) { "The app locale isn't pinned to en: $appLocales" }
    }

    private suspend fun clearDatabase() {
        entryPoint.homeSnapshotRepository().runHomeDataMutation {
            entryPoint.databaseHolder().awaitOpen().clearAllTables()
        }
    }

    private fun tab(id: Int) {
        val matcher = hasText(text(id)) and hasClickAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
        // Sub pages have no bottom bar, so back out to the top-level pages first.
        repeat(3) {
            if (compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()) return@repeat
            scenario!!.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle()
        }
        compose.onNode(matcher).performSemanticsAction(SemanticsActions.OnClick)
        await { compose.onNode(matcher).fetchSemanticsNode().config[SemanticsProperties.Selected] }
        compose.waitForIdle()
    }

    private fun history() {
        tab(R.string.tab_plan)
        val historyReady = hasContentDescription(text(R.string.history_current_month))
        val openHistory = hasContentDescription(text(R.string.plan_open_history))
        await { compose.onAllNodes(historyReady or openHistory).fetchSemanticsNodes().isNotEmpty() }
        if (compose.onAllNodes(historyReady).fetchSemanticsNodes().isEmpty()) {
            compose.onNode(openHistory).performSemanticsAction(SemanticsActions.OnClick)
        }
        await { compose.onAllNodes(historyReady).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun selectHistoryDate(date: LocalDate) {
        val matcher = hasTestTag("history_calendar_day_$date")
        if (compose.onAllNodes(matcher).fetchSemanticsNodes().isEmpty()) {
            val previousMonth = hasContentDescription(text(R.string.history_previous_month)) and isEnabled()
            await { compose.onAllNodes(previousMonth).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(previousMonth).performSemanticsAction(SemanticsActions.OnClick)
        }
        await { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(matcher)[0].performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    /** Plan's week strip opens on the calendar today's week, which can leave out the day before. */
    private fun selectPlanDate(date: LocalDate) {
        val matcher = hasTestTag("plan_calendar_day_$date")
        await { compose.onAllNodes(hasTestTag("plan_calendar_day_${LocalDate.now()}")).fetchSemanticsNodes().isNotEmpty() }
        if (compose.onAllNodes(matcher).fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithContentDescription(text(R.string.plan_previous_week))
                .performSemanticsAction(SemanticsActions.OnClick)
        }
        await { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(matcher)[0].performClick()
    }

    private fun openPlanEditor(name: String) {
        tab(R.string.tab_plan)
        awaitText(name)
        compose.onAllNodes(hasText(name) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
        awaitText(text(R.string.edit_medication_group))
    }

    private fun pickTime(hour: Int, minute: Int) {
        listOf("$hour hours", "$minute minutes").forEach { description ->
            val nodes = compose.onAllNodes(hasContentDescription(description) and hasAnyAncestor(hasTestTag("time_picker")))
            await { nodes.fetchSemanticsNodes().isNotEmpty() }
            nodes[nodes.fetchSemanticsNodes().lastIndex].performSemanticsAction(SemanticsActions.OnClick)
        }
    }

    private fun medicine(name: String, category: MedicationCategory = MedicationCategory.CUSTOM): Medicine = io {
        entryPoint.medicineRepository().findOrCreateForCustom(name, null, category, MedicinePreparation.Pill(1.0))
    }

    private fun group(
        medicine: Medicine,
        times: List<LocalTime>,
        weekdays: Set<DayOfWeek> = emptySet(),
        notifications: Boolean = false,
        interval: Int = 1,
        dayBoundary: MedicationDayBoundary = MedicationDayBoundary.Midnight,
        name: String = "E2E plan",
        since: LocalDate = day.minusDays(2),
    ): MedicationGroup = io {
        val uuid = entryPoint.medicationGroupRepository().saveGroup(
            uuid = null, name = name, colorKey = MedicationGroupColorKey.ROSE,
            schedule = MedicationGroupScheduleInput(
                if (weekdays.isEmpty()) MedicationGroupScheduleType.DAILY else MedicationGroupScheduleType.WEEKLY,
                interval, since, weekdays, times,
                dayBoundary = dayBoundary,
            ),
            medications = listOf(MedicationGroupMedicationInput(medicineUuid = medicine.uuid, applicationType = MedicationApplicationType.ORAL, doseInstruction = dose)),
            notificationsEnabled = notifications,
        )
        entryPoint.medicationGroupRepository().getGroup(uuid)!!.also { check(it.schedule.dayBoundary == dayBoundary) }
    }

    private fun log(medicine: Medicine, time: LocalDateTime, group: MedicationGroup? = null, scheduled: LocalDateTime? = null, zone: ZoneId = ZoneId.systemDefault()): MedicationLogEntry = io {
        entryPoint.medicationLogRepository().saveEntry(
            uuid = null, medicineUuid = medicine.uuid, applicationType = MedicationApplicationType.ORAL,
            doseInstruction = dose, sourceGroupUuid = group?.uuid,
            scheduleTimeUuid = group?.schedule?.timeSlots?.firstOrNull { it.time == scheduled?.toLocalTime() }?.uuid,
            appliedAt = time.atZone(zone).toInstant(), scheduledFor = scheduled, appliedAtTimeZoneId = zone.id,
        )
        entryPoint.medicationLogRepository().getEntries().first { it.medicine?.uuid == medicine.uuid && it.appliedAt == time.atZone(zone).toInstant() }
    }

    private fun todayNode(matcher: SemanticsMatcher) = compose.onNode(matcher and hasAnyAncestor(hasTestTag("main_today_section")))
    private fun todayTop(matcher: SemanticsMatcher) = todayNode(matcher).fetchSemanticsNode().boundsInRoot.top
    private fun top(matcher: SemanticsMatcher) = compose.onAllNodes(matcher)[0].fetchSemanticsNode().boundsInRoot.top

    /** The other texts on a Home section header's line, such as its date. */
    private fun headerSummary(title: String): List<String> {
        val header = compose.onAllNodes(hasText(title)).fetchSemanticsNodes().firstOrNull()?.boundsInRoot ?: return emptyList()
        return compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).fetchSemanticsNodes()
            .filter { node -> node.boundsInRoot.top < header.bottom && node.boundsInRoot.bottom > header.top }
            .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
            .filter { it != title }
    }

    private fun assertNoDayOffset() {
        val offset = "(${text(R.string.plan_schedule_entry_logged_day_offset_later, 1)})"
        assertTrue(compose.onAllNodes(hasText(offset, substring = true)).fetchSemanticsNodes().isEmpty())
    }

    private fun awaitWidgetCount(host: E2eWidgetHost, expected: String) {
        try {
            EmulatorDeviceState.waitFor("rendered widget count $expected") {
                host.visibleTexts().joinToString("").filterNot(Char::isWhitespace).contains(expected)
            }
        } catch (error: Throwable) {
            throw AssertionError("Expected widget count $expected; rendered texts: ${host.visibleTexts()}", error)
        }
    }
    private fun todayExists(matcher: SemanticsMatcher) = compose.onAllNodes(matcher and hasAnyAncestor(hasTestTag("main_today_section"))).fetchSemanticsNodes().isNotEmpty()
    private fun assertTodayMedicine(medicine: Medicine) = await { todayExists(hasText((medicine.selection as MedicineSelection.Custom).medicationName)) }
    private fun assertTodayDate(date: LocalDate) = await { todayExists(hasText(dateText(date), substring = true, ignoreCase = true)) }
    private fun assertTodayCount(count: String) = await { todayExists(hasText(count, substring = true)) }
    private fun dateText(date: LocalDate) = dateLabelFormatter(Locale.ENGLISH, LocalDate.now())(date)
    private fun clock(hour: Int, minute: Int): String = LocalTime.of(hour, minute)
        .format(localizedShortTimeFormatter(Locale.ENGLISH, context.uses24HourTimeFormat()))
    /** A time label led by the night owl moon, which reads as "After midnight". */
    private fun moon(time: String): String = "${text(R.string.medication_day_past_midnight)} $time"
    private fun text(id: Int, vararg args: Any): String = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) }).getString(id, *args)
    private fun awaitText(text: String) = await {
        val matcher = hasText(text)
        if (compose.onAllNodes(matcher).fetchSemanticsNodes().isEmpty()) {
            val verticalList = hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
            runCatching { compose.onNode(verticalList).performScrollToNode(matcher) }
        }
        compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
    }
    private fun await(timeout: Long = 20_000, condition: () -> Boolean) {
        try {
            compose.waitUntil(timeoutMillis = timeout) { condition() }
        } catch (error: Throwable) {
            val tree = runCatching {
                val roots = compose.onAllNodes(isRoot())
                roots.fetchSemanticsNodes().indices.joinToString("\n") { roots[it].printToString() }
            }.getOrDefault("No Compose tree")
            throw AssertionError("E2E condition failed. Current UI:\n$tree", error)
        }
    }
    private fun <T> io(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }
}
