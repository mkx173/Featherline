package com.mkx.hrttracker.e2e

import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isRoot
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
import com.mkx.hrttracker.data.repository.MedicationGroupMedicationInput
import com.mkx.hrttracker.data.repository.MedicationGroupScheduleInput
import com.mkx.hrttracker.model.home.HomeCardType
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.settings.AppLanguageOption
import com.mkx.hrttracker.reminder.REMINDER_CHANNEL_ID
import com.mkx.hrttracker.testing.E2eEntryPoint
import com.mkx.hrttracker.util.dateLabelFormatter
import dagger.hilt.android.EntryPointAccessors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs MainActivity, its real navigation, SQLCipher/Room, DataStore and application lifecycle. */
@RunWith(AndroidJUnit4::class)
class MedicationDayE2eTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val device = EmulatorDeviceState()
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var entryPoint: E2eEntryPoint
    private lateinit var context: Context
    private val day = LocalDate.of(2026, 10, 31)
    private val dose = DoseInstruction.TabletFraction(1, 1)

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".debug")) { "Run the Debug variant." }
        device.captureAndConfigure()
        device.setTime(day.plusDays(1).atTime(6, 0))
        entryPoint = EntryPointAccessors.fromApplication(context, E2eEntryPoint::class.java)
        io {
            entryPoint.settingsRepository().apply {
                setOnboardingCompleted(true)
                setScreenLockProtectionEnabled(false)
                setHideScreenContentEnabled(false)
                setHideMedicationDetails(false)
                setMedicationDayStartMinutes(0)
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
            entryPoint.homeSnapshotRepository().runHomeDataMutation {
                entryPoint.databaseHolder().awaitOpen().clearAllTables()
            }
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
            device.close()
        }
    }

    @Test
    fun settingConfirmCancelAndActivityRelaunchPersistToDataStore() {
        chooseBoundary(4, 30)
        assertEquals(270, io { entryPoint.settingsRepository().getCurrentSettings().medicationDayStartMinutes })
        openBoundaryPicker()
        pickTime(5, 0)
        compose.onNodeWithText(text(R.string.cancel)).performClick()
        assertEquals(270, io { entryPoint.settingsRepository().getCurrentSettings().medicationDayStartMinutes })
        scenario!!.recreate()
        awaitText(text(R.string.settings_medication_day_start_summary, "04:30"))
        scenario!!.close()
        launch()
        tab(R.string.tab_settings)
        compose.onNodeWithText(text(R.string.settings_medication_day_start_summary, "04:30"))
            .performScrollTo().assertExists()
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
    fun minuteBoundaryRegroupsManualHistoryAcrossMonths() {
        chooseBoundary(4, 30)
        val before = medicine("Before boundary")
        val exact = medicine("At boundary")
        log(before, day.plusDays(1).atTime(4, 29, 59))
        log(exact, day.plusDays(1).atTime(4, 30))
        tab(R.string.tab_main)
        assertTodayMedicine(exact)
        todayNode(hasText("Before boundary")).assertDoesNotExist()
        history()
        selectHistoryDate(day.plusDays(1))
        awaitText("At boundary")
        compose.onNodeWithText("Before boundary").assertDoesNotExist()
        selectHistoryDate(day)
        awaitText("Before boundary")
        compose.onNodeWithText("At boundary").assertDoesNotExist()
    }

    @Test
    fun fiveOClockIncludesMidnightAndDawnButExcludesExactBoundary() {
        boundary(300)
        listOf(0, 1, 4).forEach { hour -> log(medicine("Dawn $hour"), day.plusDays(1).atTime(hour, if (hour == 4) 59 else 0)) }
        val exact = medicine("Five o'clock")
        log(exact, day.plusDays(1).atTime(5, 0))
        assertTodayMedicine(exact)
        listOf(0, 1, 4).forEach { todayNode(hasText("Dawn $it")).assertDoesNotExist() }
        history()
        selectHistoryDate(day)
        listOf(0, 1, 4).forEach { awaitText("Dawn $it") }
        compose.onNodeWithText("Five o'clock").assertDoesNotExist()
    }

    @Test
    fun lastMinuteOfDayIsAValidBoundary() {
        boundary(1439)
        device.setTime(day.plusDays(1).atTime(23, 59, 10))
        val before = medicine("23:58 dose")
        val exact = medicine("23:59 dose")
        log(before, day.plusDays(1).atTime(23, 58))
        log(exact, day.plusDays(1).atTime(23, 59))
        assertTodayDate(day.plusDays(1))
        assertTodayMedicine(exact)
        todayNode(hasText("23:58 dose")).assertDoesNotExist()
    }

    @Test
    fun dailyPlanOrdersNextDayDawnLastAndDoesNotDuplicateContextRows() {
        boundary(300)
        device.setTime(day.atTime(20, 0))
        group(medicine("Daily dose"), listOf(LocalTime.of(8, 0), LocalTime.of(16, 0), LocalTime.of(1, 0)))
        assertTodayDate(day)
        assertTodayCount("0/3")
        compose.onAllNodes(hasText("Next day 01:00", substring = true) and hasAnyAncestor(hasTestTag("main_today_section"))).assertCountEquals(1)
        val morning = todayNode(hasText(text(R.string.main_today_range_morning), substring = true)).fetchSemanticsNode().boundsInRoot.top
        val night = todayNode(hasText(text(R.string.main_today_range_night), substring = true)).fetchSemanticsNode().boundsInRoot.top
        assertTrue("Dawn should follow daytime doses", night > morning)
        compose.onAllNodes(hasContentDescription(text(R.string.main_today_quick_log)) and hasAnyAncestor(hasTestTag("main_today_section"))).assertCountEquals(3)
    }

    @Test
    fun quickLogThreeSlotsUpdatesHomeCalendarAndStoredOccurrenceTimes() {
        boundary(300)
        device.setTime(day.atTime(8, 0))
        group(medicine("Daily dose"), listOf(LocalTime.of(8, 0), LocalTime.of(16, 0), LocalTime.of(1, 0)))
        listOf(day.atTime(8, 0), day.atTime(16, 0), day.plusDays(1).atTime(1, 0)).forEachIndexed { index, time ->
            device.setTime(time)
            assertTodayDate(day)
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
        compose.onNodeWithTag("plan_calendar_day_$day").performClick()
        await { compose.onAllNodesWithTag("plan_day_row").fetchSemanticsNodes().size == 3 }
        history()
        selectHistoryDate(day)
        awaitText("Daily dose")
    }

    @Test
    fun manualDoseDoesNotFulfillPlanAndLinkedSupplementKeepsScheduledDay() {
        boundary(300)
        val medicine = medicine("Evening dose")
        val group = group(medicine, listOf(LocalTime.of(23, 0)))
        device.setTime(day.plusDays(1).atTime(1, 0))
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
    fun tuesdayDawnAppearsOnMondayWithoutChangingWeeklySchedule() {
        boundary(300)
        val monday = LocalDate.of(2026, 11, 2)
        device.setTime(monday.atTime(20, 0))
        val group = group(medicine("Weekly dose"), listOf(LocalTime.of(1, 0)), setOf(DayOfWeek.TUESDAY))
        assertTodayDate(monday)
        assertTodayCount("0/1")
        tab(R.string.tab_plan)
        compose.onNodeWithTag("plan_calendar_day_$monday").performClick()
        await { compose.onAllNodesWithTag("plan_day_row").fetchSemanticsNodes().size == 1 }
        compose.onNodeWithTag("plan_calendar_day_${monday.plusDays(1)}").performClick()
        awaitText(text(R.string.plan_selected_day_records_empty))
        assertEquals(setOf(DayOfWeek.TUESDAY), io { entryPoint.medicationGroupRepository().getGroup(group.uuid)!! }.schedule.weeklyDaysOfWeek)
    }

    @Test
    fun changingBoundaryRegroupsExistingRecordsWithoutRewritingTimestamps() {
        val medicine = medicine("Existing dawn dose")
        val record = log(medicine, day.plusDays(1).atTime(1, 0))
        assertTodayMedicine(medicine)
        chooseBoundary(5, 0)
        tab(R.string.tab_main)
        await { !todayExists(hasText("Existing dawn dose")) }
        history()
        selectHistoryDate(day)
        awaitText("Existing dawn dose")
        chooseBoundary(0, 0)
        tab(R.string.tab_main)
        assertTodayMedicine(medicine)
        assertEquals(record, io { entryPoint.medicationLogRepository().getEntry(record.uuid) })
    }

    @Test
    fun foregroundTickerChangesDayAtBoundaryWithoutNavigationOrDataWrites() {
        boundary(300)
        device.setTime(day.plusDays(1).atTime(4, 59, 50))
        assertTodayDate(day)
        await(timeout = 25_000) { todayExists(hasText(dateText(day.plusDays(1)), substring = true, ignoreCase = true)) }
    }

    @Test
    fun backgroundAndActivityRelaunchReadNewDayAfterClockChange() {
        boundary(300)
        device.setTime(day.plusDays(1).atTime(4, 59))
        assertTodayDate(day)
        scenario!!.moveToState(Lifecycle.State.CREATED)
        device.setTime(day.plusDays(1).atTime(5, 0))
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        assertTodayDate(day.plusDays(1))
        scenario!!.close()
        launch()
        assertTodayDate(day.plusDays(1))
    }

    @Test
    fun yearBoundaryPlacesJanuaryDawnInDecemberHistory() {
        boundary(300)
        val december = LocalDate.of(2026, 12, 31)
        device.setTime(december.plusDays(1).atTime(6, 0))
        log(medicine("New year dawn dose"), december.plusDays(1).atTime(1, 0))
        history()
        selectHistoryDate(december)
        awaitText("New year dawn dose")
    }

    @Test
    fun storedEntryZoneStillControlsManualDayAfterDeviceZoneChanges() {
        boundary(300)
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
    fun editingAndDeletingManualRecordUpdatesBothDays() {
        boundary(300)
        val medicine = medicine("Editable dose")
        val record = log(medicine, day.plusDays(1).atTime(4, 59))
        history()
        selectHistoryDate(day)
        awaitText("Editable dose")
        compose.onNodeWithText("Editable dose").performSemanticsAction(SemanticsActions.OnClick)
        awaitText(text(R.string.save))
        await { compose.onAllNodes(hasContentDescription(text(R.string.select_time)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(text(R.string.select_time), useUnmergedTree = true).performScrollTo().performClick()
        pickTime(5, 0)
        compose.onNodeWithText(text(R.string.confirm)).performClick()
        compose.onNodeWithText(text(R.string.save)).performClick()
        await { io { entryPoint.medicationLogRepository().getEntry(record.uuid)!!.appliedAt } == day.plusDays(1).atTime(5, 0).atZone(ZoneId.systemDefault()).toInstant() }
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
        boundary(300)
        val medicine = medicine("Archived dawn dose")
        val group = group(medicine, listOf(LocalTime.of(1, 0)))
        val record = log(medicine, day.plusDays(1).atTime(1, 0), group, day.plusDays(1).atTime(1, 0))
        io { entryPoint.medicationGroupRepository().archiveGroup(group.uuid) }
        history()
        selectHistoryDate(day)
        awaitText("Archived dawn dose")
        tab(R.string.tab_settings)
        compose.onNode(hasText(text(R.string.settings_hide_archived_group_records)) and hasClickAction())
            .performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        await { !io { entryPoint.settingsRepository().getCurrentSettings().showArchivedGroupRecords } }
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
    fun encryptedBackupRestoresBoundaryAndLegacyBackupDefaultsToMidnight() {
        chooseBoundary(4, 30)
        val medicine = medicine("Backed up dawn dose")
        log(medicine, day.plusDays(1).atTime(1, 0))
        val json = io { entryPoint.backupExportService().buildBackupSnapshotJson() }
        val encrypted = io { entryPoint.backupExportService().buildEncryptedBackupBytes("e2e-password") }
        boundary(0)
        io { entryPoint.medicationLogRepository().deleteAllEntries() }
        io { entryPoint.backupRestoreService().restoreBackupBytes(encrypted, "e2e-password") }
        assertEquals(270, io { entryPoint.settingsRepository().getCurrentSettings().medicationDayStartMinutes })
        history()
        selectHistoryDate(day)
        awaitText("Backed up dawn dose")
        val legacy = JSONObject(json).apply { getJSONObject("settings").remove("medicationDayStartMinutes") }.toString()
        val oldEncrypted = io { entryPoint.backupCrypto().encryptSnapshotJson(legacy, "e2e-password".toCharArray()) }
        io { entryPoint.backupRestoreService().restoreBackupBytes(oldEncrypted, "e2e-password") }
        assertEquals(0, io { entryPoint.settingsRepository().getCurrentSettings().medicationDayStartMinutes })
        tab(R.string.tab_main)
        assertTodayMedicine(medicine)
    }

    @Test
    fun realWidgetUpdatesCountsAndRefreshesAtMedicationDayBoundary() {
        boundary(300)
        val medicine = medicine("Widget dose")
        val group = group(medicine, listOf(LocalTime.of(1, 0)))
        log(medicine, day.plusDays(1).atTime(1, 0), group, day.plusDays(1).atTime(1, 0))
        device.setTime(day.plusDays(1).atTime(4, 58))
        assertTodayCount("1/1")
        E2eWidgetHost(context).use { host ->
            host.attach(scenario!!)
            awaitWidgetCount(host, "1/1")
            assertEquals(day.toEpochDay(), io { entryPoint.widgetSnapshotStore().readSnapshot()!! }.anchorDateEpochDay)
            device.setTime(day.plusDays(1).atTime(4, 59, 50))
            EmulatorDeviceState.waitFor("widget date alarm", 25_000) {
                val snapshot = io { entryPoint.widgetSnapshotStore().readSnapshot() }
                snapshot?.anchorDateEpochDay == day.plusDays(1).toEpochDay() && snapshot.doneCount == 0 && snapshot.totalCount == 1
            }
            awaitWidgetCount(host, "0/1")
        }
    }

    @Test
    fun actualReminderStillFiresAtOneOClockAfterBoundaryChanges() {
        EmulatorDeviceState.shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        EmulatorDeviceState.shell("appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow")
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        device.setTime(day.plusDays(1).atTime(0, 58))
        group(medicine("Reminder dose"), listOf(LocalTime.of(1, 0)), notifications = true)
        io {
            entryPoint.settingsRepository().setRemindersEnabled(true)
            entryPoint.reminderScheduler().rescheduleAll()
        }
        chooseBoundary(5, 0)
        assertTrue(manager.activeNotifications.none { it.notification.channelId == REMINDER_CHANNEL_ID })
        device.setTime(day.plusDays(1).atTime(0, 59, 50))
        EmulatorDeviceState.waitFor("real 01:00 reminder", 25_000) {
            manager.activeNotifications.any { it.notification.channelId == REMINDER_CHANNEL_ID }
        }
        assertEquals(LocalTime.of(1, 0), LocalTime.now().withSecond(0).withNano(0))
        manager.cancelAll()
    }

    @Test
    fun journalStillUsesCivilDayWhenMedicationDayIsYesterday() {
        boundary(1439)
        assertTodayDate(day)
        io { entryPoint.journalRepository().saveNoteForDate(day.plusDays(1), "Civil-day note") }
        tab(R.string.tab_journal)
        awaitText("Civil-day note")
        await { compose.onAllNodes(hasText("Nov 1", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        boundary(0)
        awaitText("Civil-day note")
        assertEquals(day.plusDays(1).toString(), io { entryPoint.journalRepository().getNoteEntities().single().dateIso })
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        await { compose.onAllNodesWithTag("main_today_section").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun tab(id: Int) {
        val matcher = hasText(text(id)) and hasClickAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
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

    private fun openBoundaryPicker() {
        tab(R.string.tab_settings)
        compose.onNode(hasText(text(R.string.settings_medication_day_start)) and hasClickAction())
            .performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        awaitText(text(R.string.confirm))
    }

    private fun chooseBoundary(hour: Int, minute: Int) {
        openBoundaryPicker()
        pickTime(hour, minute)
        compose.onNodeWithText(text(R.string.confirm)).performClick()
        await { io { entryPoint.settingsRepository().getCurrentSettings().medicationDayStartMinutes } == hour * 60 + minute }
        awaitText(text(R.string.settings_medication_day_start_summary, "%02d:%02d".format(hour, minute)))
    }

    private fun pickTime(hour: Int, minute: Int) {
        listOf("$hour hours", "$minute minutes").forEach { description ->
            val nodes = compose.onAllNodes(hasContentDescription(description) and hasAnyAncestor(hasTestTag("time_picker")))
            await { nodes.fetchSemanticsNodes().isNotEmpty() }
            nodes[nodes.fetchSemanticsNodes().lastIndex].performSemanticsAction(SemanticsActions.OnClick)
        }
    }

    private fun boundary(minutes: Int) = io { entryPoint.settingsRepository().setMedicationDayStartMinutes(minutes) }

    private fun medicine(name: String): Medicine = io {
        entryPoint.medicineRepository().findOrCreateForCustom(name, null, MedicationCategory.CUSTOM, MedicinePreparation.Pill(1.0))
    }

    private fun group(medicine: Medicine, times: List<LocalTime>, weekdays: Set<DayOfWeek> = emptySet(), notifications: Boolean = false): MedicationGroup = io {
        val uuid = entryPoint.medicationGroupRepository().saveGroup(
            uuid = null, name = "E2E plan", colorKey = MedicationGroupColorKey.ROSE,
            schedule = MedicationGroupScheduleInput(
                if (weekdays.isEmpty()) MedicationGroupScheduleType.DAILY else MedicationGroupScheduleType.WEEKLY,
                1, day.minusDays(2), weekdays, times,
            ),
            medications = listOf(MedicationGroupMedicationInput(medicineUuid = medicine.uuid, applicationType = MedicationApplicationType.ORAL, doseInstruction = dose)),
            notificationsEnabled = notifications,
        )
        entryPoint.medicationGroupRepository().getGroup(uuid)!!
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
