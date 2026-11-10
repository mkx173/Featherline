package com.mkx.hrttracker.ui.plan

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.MedicationGroupDao
import com.mkx.hrttracker.data.local.MedicationLogEntryEntity
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.MedicationGroupMedicationInput
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationGroupScheduleInput
import com.mkx.hrttracker.data.repository.MedicationGroupScheduleTimeInput
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.data.repository.SaveRefusal
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.toEntity
import com.mkx.hrttracker.data.repository.toMedicationLogEntryModel
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationSignature
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.occurrencesBetweenInPlanWindow
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.reminder.MedicationReminderSnoozeScheduler
import com.mkx.hrttracker.ui.components.TimePickerModal
import com.mkx.hrttracker.ui.catalog.MedicineSlotResult
import com.mkx.hrttracker.util.FakeAppTimeSource
import com.mkx.hrttracker.util.MedicationDayBoundary
import com.mkx.hrttracker.util.withAppLanguage
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A plan's records lock its slots: whatever the editor holds when one arrives, saving keeps every
 * record an occurrence of its own slot. Runs the editor over the real repository and database, so
 * a save that commits halfway shows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class MedicationGroupEditorRecordLockTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val dispatcher = StandardTestDispatcher()
    private val databaseHolder: DatabaseHolder = mockk()
    private val homeSnapshotRepository: HomeSnapshotRepository = mockk()
    private val medicationLogRepository: MedicationLogRepository = mockk(relaxed = true)
    private val medicineRepository: MedicineRepository = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk()
    private val medicationReminderScheduler: MedicationReminderScheduler = mockk(relaxed = true)
    private val medicationReminderSnoozeScheduler: MedicationReminderSnoozeScheduler =
        mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val entries = MutableStateFlow<List<MedicationLogEntry>?>(emptyList())
    private val now = LocalDateTime.of(2026, 11, 8, 12, 0)
    private val since = LocalDate.of(2026, 11, 1)
    private val medicine: Medicine = testMedicine(
        uuid = UUID.fromString("5a4b3c2d-0000-4000-8000-000000000001"),
    )
    private val secondMedicine: Medicine = testMedicine(
        uuid = UUID.fromString("5a4b3c2d-0000-4000-8000-000000000002"),
        key = MedicationKey.SPIRONOLACTONE,
        preparation = MedicinePreparation.Pill(strengthMgPerTablet = 50.0),
    )
    private val planMedicines = listOf(medicine, secondMedicine)

    private lateinit var room: HrtTrackerDatabase
    private lateinit var groupDao: MedicationGroupDao
    private lateinit var database: HrtTrackerDatabase
    private lateinit var repository: MedicationGroupRepository
    private var failNextUpsert = false

    // Null keeps the repository's groups flow quiet.
    private val databaseFlow = MutableStateFlow<HrtTrackerDatabase?>(null)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // Inline executors keep Room on the test thread, so advanceUntilIdle waits for it.
        room = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HrtTrackerDatabase::class.java,
        )
            .allowMainThreadQueries()
            .setQueryExecutor(Runnable::run)
            .setTransactionExecutor(Runnable::run)
            .build()
        groupDao = spyk(room.medicationGroupDao())
        // The one failure a test injects: the plan's own write, after anything before it.
        coEvery { groupDao.upsertGroupWithItems(any(), any(), any(), any()) } coAnswers {
            if (failNextUpsert) {
                failNextUpsert = false
                throw IllegalStateException("Injected plan write failure")
            }
            callOriginal()
        }
        database = mockk {
            every { medicationGroupDao() } returns groupDao
            every { medicationLogDao() } returns room.medicationLogDao()
            every { medicineDao() } returns room.medicineDao()
        }
        every { databaseHolder.databaseFlow } returns databaseFlow
        every { databaseHolder.get() } returns database
        coEvery { databaseHolder.withTransaction<Unit>(any()) } coAnswers {
            room.withTransaction { firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database) }
        }
        coEvery { homeSnapshotRepository.runHomeDataMutation<Unit>(any()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
        }
        repository = MedicationGroupRepository(
            databaseHolder = databaseHolder,
            homeSnapshotRepository = homeSnapshotRepository,
            appScope = CoroutineScope(dispatcher),
        )

        val settings = SettingsState(remindersEnabled = true)
        every { settingsRepository.settingsState } returns MutableStateFlow(settings)
        coEvery { settingsRepository.getCurrentSettings() } returns settings
        coEvery { settingsRepository.peekNextGroupNameIndex() } returns 1
        coEvery { settingsRepository.consumeNextGroupNameIndex() } returns 1
        every { medicationLogRepository.observeEntries() } returns entries
        every { medicineRepository.observeAllActive() } returns MutableStateFlow(planMedicines)
        every { context.getString(R.string.default_group_name_format, any()) } returns "Group 1"
        mockkStatic("com.mkx.hrttracker.util.LocalizationKt")
        every { context.withAppLanguage() } returns context
    }

    @After
    fun tearDown() {
        clearAllMocks()
        room.close()
        Dispatchers.resetMain()
        unmockkStatic("com.mkx.hrttracker.util.LocalizationKt")
    }

    @Test
    fun aRecordArrivingMidEditRevertsNothingAndRefusesASaveItConflictsWith() = runTest(dispatcher) {
        val edits = listOf<Pair<String, (MedicationGroupEditorViewModel) -> Unit>>(
            "weekly" to { viewModel -> viewModel.updateScheduleType(MedicationGroupScheduleType.WEEKLY) },
            "a time added" to { viewModel -> viewModel.addDailyTime(LocalTime.of(20, 0)) },
            "a medicine removed" to { viewModel ->
                viewModel.removeMedication(
                    viewModel.uiState.value.medications.single { it.medicineUuid == medicine.uuid }.localId
                )
            },
            "night owl on" to { viewModel -> viewModel.updateDayEndsAtFourAm(true) },
            "start date moved past the record" to { viewModel ->
                viewModel.updateSinceDate(now.toLocalDate().plusDays(2))
            },
        )
        for ((name, edit) in edits) {
            val groupUuid = createDailyPlan(LocalTime.of(8, 0), medicines = planMedicines)
            val viewModel = editor(groupUuid)
            viewModel.updateGroupName("Mornings")
            edit(viewModel)
            val edited = viewModel.uiState.value

            val record = logRecord(groupUuid, now.toLocalDate().atTime(8, 0))
            // Seen, and the draft is as the user left it.
            assertTrue(name, viewModel.uiState.value.isLocked)
            assertEquals(name, edited.toEditedDraft(), viewModel.uiState.value.toEditedDraft())
            viewModel.saveGroup()
            advanceUntilIdle()

            assertEquals(name, SaveMedicationGroupResult.LOCKED_BY_RECORDS, viewModel.uiState.value.saveMedicationGroupResult)
            assertFalse(name, viewModel.uiState.value.isSaved)
            assertEquals(name, "Mornings", viewModel.uiState.value.groupName)
            // Nothing written: the plan is as it was.
            assertEquals(name, "Plan", repository.getGroup(groupUuid)?.name)
            assertRecordOnItsSlot(groupUuid, record)
        }
    }

    @Test
    fun aRecordLandingAsTheSaveStartsRefusesAScheduleTypeChangeAndSoDoesTheEditorOnceItSeesIt() =
        runTest(dispatcher) {
            val groupUuid = createDailyPlan(LocalTime.of(8, 0))
            val viewModel = editor(groupUuid)
            viewModel.updateScheduleType(MedicationGroupScheduleType.WEEKLY)

            // Written, but not yet observed by the editor: the repository refuses it at commit.
            val record = logRecord(groupUuid, now.toLocalDate().atTime(8, 0), observe = false)
            viewModel.saveGroup()
            advanceUntilIdle()

            assertRecordOnItsSlot(groupUuid, record)
            assertEquals(
                SaveMedicationGroupResult.LOCKED_BY_RECORDS,
                viewModel.uiState.value.saveMedicationGroupResult,
            )

            // Seen now, the editor refuses it for the same reason, and keeps the edit.
            observeRecords()
            advanceUntilIdle()
            viewModel.consumeSaveMedicationGroupResult()
            viewModel.saveGroup()
            advanceUntilIdle()

            assertEquals(
                SaveMedicationGroupResult.LOCKED_BY_RECORDS,
                viewModel.uiState.value.saveMedicationGroupResult,
            )
            assertEquals(MedicationGroupScheduleType.WEEKLY, viewModel.uiState.value.scheduleType)
            assertEquals(MedicationGroupScheduleType.DAILY, repository.getGroup(groupUuid)?.schedule?.type)
        }

    @Test
    fun aDeletedPlansSaveIsRefusedWithItsOwnReason() = runTest(dispatcher) {
        val groupUuid = createDailyPlan(LocalTime.of(8, 0))
        val viewModel = editor(groupUuid)
        viewModel.updateGroupName("Mornings")

        repository.deleteGroup(groupUuid)
        viewModel.saveGroup()
        advanceUntilIdle()

        assertEquals(SaveMedicationGroupResult.GROUP_DELETED, viewModel.uiState.value.saveMedicationGroupResult)
        assertFalse(viewModel.uiState.value.isSaved)
        assertNull("The save brought the deleted plan back", repository.getGroup(groupUuid))
    }

    @Test
    fun aLockedRetimeBeforeTheStartIsRefusedAsItIsPickedWithItsOwnReason() = runTest(dispatcher) {
        // The plan starts at 07:00 on its first day, and that day's 08:00 dose is logged.
        val groupUuid = createDailyPlan(LocalTime.of(8, 0), startAt = since.atTime(7, 0))
        val record = logRecord(groupUuid, since.atTime(8, 0))
        val viewModel = editor(groupUuid)
        assertTrue(viewModel.uiState.value.isLocked)

        // 06:00 would move that dose before the plan's start.
        assertEquals(
            SaveRefusal.LOGGED_DOSE_BEFORE_START,
            viewModel.updateDailyTime(viewModel.uiState.value.dailyTimes.single().localId, LocalTime.of(6, 0)),
        )

        assertEquals(LocalTime.of(8, 0), viewModel.uiState.value.dailyTimes.single().time)
        // 07:00 is the start itself, so it is taken.
        assertNull(viewModel.updateDailyTime(viewModel.uiState.value.dailyTimes.single().localId, LocalTime.of(7, 0)))
        viewModel.saveGroup()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        assertEquals(since.atTime(7, 0).toString(), room.medicationLogDao().getEntry(record)?.scheduledForIso)
        assertRecordOnItsSlot(groupUuid, record)
    }

    @Test
    fun aRefusedTimeKeepsThePickerOpenWithItsReason() = runTest(dispatcher) {
        val groupUuid = createDailyPlan(LocalTime.of(8, 0), startAt = since.atTime(7, 0))
        logRecord(groupUuid, since.atTime(8, 0))
        val viewModel = editor(groupUuid)
        val localId = viewModel.uiState.value.dailyTimes.single().localId
        val shown = mutableListOf<SaveRefusal>()
        val confirmText = ApplicationProvider.getApplicationContext<Context>().getString(R.string.confirm)
        var pickedTime by mutableStateOf<LocalTime?>(LocalTime.of(6, 0))
        composeRule.setContent {
            pickedTime?.let { initialTime ->
                TimePickerModal(
                    onTimeSelected = { time -> pickedTimeCloses(viewModel.updateDailyTime(localId, time), shown::add) },
                    onDismiss = { pickedTime = null },
                    initialTime = initialTime,
                    is24Hour = true,
                )
            }
        }

        composeRule.onNodeWithText(confirmText).performClick()
        composeRule.waitForIdle()

        // 06:00 would put the logged dose before the start: still open, with the reason.
        assertEquals(LocalTime.of(6, 0), pickedTime)
        assertEquals(listOf(SaveRefusal.LOGGED_DOSE_BEFORE_START), shown)
        assertEquals(LocalTime.of(8, 0), viewModel.uiState.value.dailyTimes.single().time)

        // Opened afresh, as a tap on the time does.
        pickedTime = null
        composeRule.waitForIdle()
        pickedTime = LocalTime.of(9, 0)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(confirmText).performClick()
        composeRule.waitForIdle()

        assertNull(pickedTime)
        assertEquals(LocalTime.of(9, 0), viewModel.uiState.value.dailyTimes.single().time)
    }

    @Test
    fun aRetimeBeforeTheStartIsRefusedAtTheSaveWithItsReasonOnceARecordArrives() = runTest(dispatcher) {
        val groupUuid = createDailyPlan(LocalTime.of(8, 0), startAt = since.atTime(7, 0))
        val viewModel = editor(groupUuid)
        viewModel.updateDailyIntervalDays("2")
        assertNull(viewModel.updateDailyTime(viewModel.uiState.value.dailyTimes.single().localId, LocalTime.of(6, 0)))

        // The first day's dose, logged after the retime: the editor learns of it only now.
        val record = logRecord(groupUuid, since.atTime(8, 0))
        // Nothing goes back.
        assertEquals("2", viewModel.uiState.value.dailyIntervalDays)
        assertEquals(LocalTime.of(6, 0), viewModel.uiState.value.dailyTimes.single().time)
        viewModel.saveGroup()
        advanceUntilIdle()

        assertEquals(
            SaveMedicationGroupResult.LOGGED_DOSE_BEFORE_START,
            viewModel.uiState.value.saveMedicationGroupResult,
        )
        assertEquals(since.atTime(8, 0).toString(), room.medicationLogDao().getEntry(record)?.scheduledForIso)
        assertRecordOnItsSlot(groupUuid, record)

        // The time put back, the plan saves every other day.
        viewModel.consumeSaveMedicationGroupResult()
        assertNull(viewModel.updateDailyTime(viewModel.uiState.value.dailyTimes.single().localId, LocalTime.of(8, 0)))
        viewModel.saveGroup()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSaved)
        assertEquals(2, repository.getGroup(groupUuid)?.schedule?.interval)
        assertRecordOnItsSlot(groupUuid, record)
    }

    @Test
    fun aDraftAlreadyRefusedTakesAnyTimeAndSaveSaysWhy() = runTest(dispatcher) {
        val groupUuid = createDailyPlan(LocalTime.of(8, 0), LocalTime.of(20, 0), startAt = since.atTime(7, 0))
        val viewModel = editor(groupUuid)
        val (morning, evening) = viewModel.uiState.value.dailyTimes
        viewModel.updateDailyTime(morning.localId, LocalTime.of(6, 0))
        // Both of the first day's doses, logged after: 06:00 stays for the save to refuse.
        logRecord(groupUuid, since.atTime(8, 0))
        logRecord(groupUuid, since.atTime(20, 0))

        // The picker refuses only a time that makes the draft refused; this one already is.
        assertNull(viewModel.updateDailyTime(evening.localId, LocalTime.of(21, 0)))
        assertEquals(
            listOf(LocalTime.of(6, 0), LocalTime.of(21, 0)),
            viewModel.uiState.value.dailyTimes.map { it.time },
        )
        viewModel.saveGroup()
        advanceUntilIdle()
        assertEquals(SaveMedicationGroupResult.LOGGED_DOSE_BEFORE_START, viewModel.uiState.value.saveMedicationGroupResult)
        // Once it is saveable, a time that would refuse it is refused again as it is picked.
        viewModel.consumeSaveMedicationGroupResult()
        assertNull(viewModel.updateDailyTime(morning.localId, LocalTime.of(7, 30)))
        assertEquals(SaveRefusal.LOGGED_DOSE_BEFORE_START, viewModel.updateDailyTime(evening.localId, LocalTime.of(6, 30)))
    }

    @Test
    fun aLockedWeeklyRetimeBeforeTheStartIsRefusedAsItIsPicked() = runTest(dispatcher) {
        val groupUuid = createDailyPlan(
            LocalTime.of(8, 0),
            startAt = since.atTime(7, 0),
            weeklyOn = setOf(since.dayOfWeek),
        )
        logRecord(groupUuid, since.atTime(8, 0))
        val viewModel = editor(groupUuid)
        assertTrue(viewModel.uiState.value.isLocked)

        assertEquals(SaveRefusal.LOGGED_DOSE_BEFORE_START, viewModel.updateWeeklyTime(LocalTime.of(6, 0)))

        assertEquals(LocalTime.of(8, 0), viewModel.uiState.value.weeklyTime)
        assertNull(viewModel.updateWeeklyTime(LocalTime.of(9, 0)))
        assertEquals(LocalTime.of(9, 0), viewModel.uiState.value.weeklyTime)
    }

    @Test
    fun theArchiveShortcutStartsTheCopyAtTheArchiveInstant() = runTest(dispatcher) {
        // Archived now at 12:00; the 12:15 dose falls before the copy is saved at 13:00.
        val groupUuid = createDailyPlan(LocalTime.of(8, 0), LocalTime.of(12, 15))
        val clock = FakeAppTimeSource(now)
        val viewModel = editor(groupUuid, clock)

        viewModel.archiveGroup(archivedThroughDate = null, thenDuplicate = true)
        advanceUntilIdle()

        val draft = viewModel.uiState.value
        assertNotNull(repository.getGroup(groupUuid)?.archivedAt)
        assertNull(draft.editingGroupId)
        assertFalse(draft.isFinishingAfterDeleteOrArchive)
        assertEquals(now.toLocalDate(), draft.sinceDate)
        assertEquals(ScheduleStart.At(LocalTime.of(12, 0)), draft.scheduleStart)
        // The copy was asked for, so leaving asks first; backing out leaves the plan archived, with no copy.
        assertTrue(viewModel.hasUnsavedChanges.value)
        assertEquals(listOf(groupUuid), repository.getGroups().map { it.uuid })

        clock.setCurrentSnapshot(now.plusHours(1), ZoneId.systemDefault())
        advanceUntilIdle()
        viewModel.saveGroup()
        advanceUntilIdle()

        val copy = checkNotNull(repository.getGroup(UUID.fromString(viewModel.uiState.value.editingGroupId)))
        assertEquals(listOf(now, now), copy.schedule.timeSlots.map { it.effectiveFrom })
        assertNull(copy.recreatedFromGroupUuid)
        assertEquals(
            listOf(now.toLocalDate().atTime(12, 15)),
            copy.occurrencesBetweenInPlanWindow(now.toLocalDate(), now.toLocalDate()).map { it.scheduledFor },
        )
    }

    @Test
    fun theArchiveShortcutThroughAPastDayStartsTheCopyWhereTheOldPlanStopped() = runTest(dispatcher) {
        for (nightOwl in listOf(false, true)) {
            val groupUuid = createDailyPlan(
                LocalTime.of(8, 0),
                dayBoundary = MedicationDayBoundary.fromSwitch(nightOwl),
            )
            val viewModel = editor(groupUuid)

            viewModel.archiveGroup(archivedThroughDate = now.toLocalDate().minusDays(2), thenDuplicate = true)
            advanceUntilIdle()

            // Where the archived plan's last day ends: the start of the day after it.
            val cutoff = now.toLocalDate().minusDays(1).atTime(if (nightOwl) 4 else 0, 0)
            assertEquals("$nightOwl", cutoff, viewModel.uiState.value.scheduleStartAt(now))
            viewModel.saveGroup()
            advanceUntilIdle()

            val copy = checkNotNull(repository.getGroup(UUID.fromString(viewModel.uiState.value.editingGroupId)))
            assertEquals("$nightOwl", cutoff.toLocalDate(), copy.schedule.since)
            assertEquals("$nightOwl", listOf(cutoff), copy.schedule.timeSlots.map { it.effectiveFrom })
            // Yesterday's dose, unlogged, is the copy's to show as missed.
            assertEquals(
                "$nightOwl",
                listOf(cutoff.toLocalDate().atTime(8, 0)),
                copy.occurrencesBetweenInPlanWindow(cutoff.toLocalDate(), cutoff.toLocalDate()).map { it.scheduledFor },
            )
        }
    }

    @Test
    fun theArchiveShortcutsCopySurvivesProcessDeathWithItsCutoffStart() = runTest(dispatcher) {
        val groupUuid = createDailyPlan(LocalTime.of(8, 0))
        val handle = SavedStateHandle(mapOf(MedicationGroupEditorViewModel.GROUP_ID_ARG to groupUuid.toString()))
        val viewModel = editor(groupUuid, savedStateHandle = handle)
        viewModel.archiveGroup(archivedThroughDate = now.toLocalDate().minusDays(1), thenDuplicate = true)
        advanceUntilIdle()

        // A new process, an hour on, restores from what the first one saved.
        val restored = editor(
            groupUuid,
            FakeAppTimeSource(now.plusHours(1)),
            savedStateHandle = SavedStateHandle(handle.keys().associateWith { key -> handle.get<Any>(key) }),
        )

        val state = restored.uiState.value
        assertNull(state.editingGroupId)
        assertEquals(groupUuid.toString(), state.duplicateSourceGroupId)
        assertEquals(now.toLocalDate(), state.sinceDate)
        assertEquals(ScheduleStart.At(LocalTime.MIDNIGHT), state.scheduleStart)
        assertTrue(restored.hasUnsavedChanges.value)
    }

    @Test
    fun aReopenedStartShowsAsEnteredAndKeepsThePlanUntilItIsEdited() = runTest(dispatcher) {
        // Night owl, every 2 days at 03:00, started Nov 9 at 01:00: Nov 8's medication day.
        val start = LocalDate.of(2026, 11, 9).atTime(1, 0)
        for (nightOwl in listOf(true, false)) {
            val groupUuid = createThroughTheEditor(nightOwl, start)
            val saved = checkNotNull(repository.getGroup(groupUuid)).schedule
            val firstDay = if (nightOwl) start.toLocalDate().minusDays(1) else start.toLocalDate()
            assertEquals("$nightOwl", firstDay, saved.since)
            for (edit in listOf("untouched", "time confirmed", "time 02:00", "night owl toggled")) {
                val case = "night owl $nightOwl, $edit"
                val viewModel = editor(groupUuid)
                // (a) As entered: the start's own date and time.
                assertEquals(case, start.toLocalDate(), viewModel.uiState.value.sinceDate)
                assertEquals(case, start, viewModel.uiState.value.scheduleStartAt(now))
                when (edit) {
                    "time confirmed" -> viewModel.updateStartTime(LocalTime.of(1, 0))
                    "time 02:00" -> viewModel.updateStartTime(LocalTime.of(2, 0))
                    "night owl toggled" -> viewModel.updateDayEndsAtFourAm(!nightOwl)
                }
                advanceUntilIdle()
                // (a), (b): nothing to lose when the start is as saved.
                assertEquals(case, edit in listOf("time 02:00", "night owl toggled"), viewModel.hasUnsavedChanges.value)
                viewModel.saveGroup()
                advanceUntilIdle()
                assertTrue(case, viewModel.uiState.value.isSaved)
                val after = checkNotNull(repository.getGroup(groupUuid)).schedule
                when (edit) {
                    // (a), (b): the identical plan.
                    "untouched", "time confirmed" -> assertEquals(case, saved, after)
                    // (c): the same night, from the same first day.
                    "time 02:00" -> {
                        assertEquals(case, firstDay, after.since)
                        assertEquals(case, listOf(start.withHour(2)), after.timeSlots.map { it.effectiveFrom })
                    }
                    // (d): the first day stays, so each dose keeps its medication day.
                    "night owl toggled" -> assertEquals(case, saved.since, after.since)
                }
                // Back to the saved plan for the next edit.
                repository.saveGroup(
                    uuid = groupUuid, name = "Plan", colorKey = MedicationGroupColorKey.ROSE,
                    schedule = MedicationGroupScheduleInput(
                        type = saved.type, interval = saved.interval, since = saved.since,
                        weeklyDaysOfWeek = saved.weeklyDaysOfWeek, times = saved.times,
                        timeSlots = saved.timeSlots.map { MedicationGroupScheduleTimeInput(it.uuid, it.time) },
                        startAt = start, dayBoundary = saved.dayBoundary,
                    ),
                    medications = listOf(
                        MedicationGroupMedicationInput(
                            uuid = checkNotNull(repository.getGroup(groupUuid)).medications.single().uuid,
                            medicineUuid = medicine.uuid,
                            applicationType = MedicationApplicationType.ORAL,
                            doseInstruction = DoseInstruction.TabletFraction(1, 1),
                        ),
                    ),
                    includePastScheduledSlots = true,
                    now = since.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant(),
                )
                assertEquals(case, saved, checkNotNull(repository.getGroup(groupUuid)).schedule)
            }
        }
    }

    @Test
    fun aReopenedPlanThatHidItsPastShowsItsStartAndRoundTrips() = runTest(dispatcher) {
        // (e) Every 5 days at 08:00 from Nov 1, saved on Nov 4 at 12:00 with its past hidden: it
        // starts then, and still counts from Nov 1 (doses Nov 6, 11, 16).
        val groupUuid = createDailyPlan(
            LocalTime.of(8, 0), interval = 5, includePastScheduledSlots = false, savedAt = since.plusDays(3).atTime(12, 0),
        )
        val saved = checkNotNull(repository.getGroup(groupUuid))
        assertEquals(since, saved.schedule.since)
        fun MedicationGroup.doseDays() = occurrencesBetweenInPlanWindow(since, since.plusDays(30)).map { it.scheduledFor.toLocalDate() }
        assertEquals(listOf(6, 11, 16, 21, 26, 31).map { since.plusDays(it - 1L) }, saved.doseDays())
        for (edit in listOf("untouched", "time confirmed", "time 13:00")) {
            val viewModel = editor(groupUuid)
            // The form's date is the first day as entered, Nov 1; the time row names the start's own date.
            assertEquals(edit, since, viewModel.uiState.value.sinceDate)
            assertEquals(edit, since.plusDays(3).atTime(12, 0), viewModel.uiState.value.scheduleStartAt(now))
            when (edit) {
                "time confirmed" -> viewModel.updateStartTime(LocalTime.of(12, 0))
                "time 13:00" -> viewModel.updateStartTime(LocalTime.of(13, 0))
            }
            advanceUntilIdle()
            // A same-time confirm is no edit, and stores the identical plan.
            assertEquals(edit, edit == "time 13:00", viewModel.hasUnsavedChanges.value)
            viewModel.saveGroup()
            advanceUntilIdle()
            val after = checkNotNull(repository.getGroup(groupUuid))
            // The time moves on the start's own date, and the count stays on Nov 1.
            assertEquals(edit, since, after.schedule.since)
            assertEquals(edit, saved.doseDays(), after.doseDays())
            if (edit == "time 13:00") {
                assertEquals(edit, listOf(since.plusDays(3).atTime(13, 0)), after.schedule.timeSlots.map { it.effectiveFrom })
            } else {
                assertEquals(edit, saved.schedule, after.schedule)
            }
        }
    }

    /** A new plan made in the editor, every 2 days at 03:00, started at [start]. */
    private suspend fun TestScope.createThroughTheEditor(nightOwl: Boolean, start: LocalDateTime): UUID {
        if (room.medicineDao().getByUuids(listOf(medicine.uuid.toString())).isEmpty()) {
            room.medicineDao().insert(medicine.toEntity())
        }
        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = repository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(),
            appTimeSource = FakeAppTimeSource(now),
        )
        advanceUntilIdle()
        viewModel.addCompletedMedicationSlot(
            localId = "slot",
            slot = MedicineSlotResult(medicine.uuid, MedicationApplicationType.ORAL, DoseInstruction.TabletFraction(1, 1), 1),
        )
        viewModel.updateDayEndsAtFourAm(nightOwl)
        viewModel.updateDailyTime(viewModel.uiState.value.dailyTimes.single().localId, LocalTime.of(3, 0))
        viewModel.updateDailyIntervalDays("2")
        viewModel.updateSinceDate(start.toLocalDate())
        viewModel.updateStartTime(start.toLocalTime())
        viewModel.saveGroup()
        advanceUntilIdle()
        return UUID.fromString(checkNotNull(viewModel.uiState.value.editingGroupId))
    }

    @Test
    fun duplicatingAnArchivedPlanStillStartsNowThroughProcessDeath() = runTest(dispatcher) {
        val groupUuid = createDailyPlan(LocalTime.of(8, 0))
        repository.archiveGroup(groupUuid, now = now.minusDays(1).atZone(ZoneId.systemDefault()).toInstant())
        val handle = SavedStateHandle(mapOf(MedicationGroupEditorViewModel.GROUP_ID_ARG to groupUuid.toString()))
        val viewModel = editor(groupUuid, savedStateHandle = handle)
        viewModel.duplicateArchivedGroup()
        assertTrue(viewModel.uiState.value.startsNow)
        advanceUntilIdle()

        val restored = editor(
            groupUuid,
            savedStateHandle = SavedStateHandle(handle.keys().associateWith { key -> handle.get<Any>(key) }),
        )

        assertEquals(groupUuid.toString(), restored.uiState.value.duplicateSourceGroupId)
        assertTrue(restored.uiState.value.startsNow)
        // A plain duplicate, untouched, loses nothing on leaving.
        assertFalse(restored.hasUnsavedChanges.value)
    }

    @Test
    fun aRecordLandingAsTheSaveStartsRefusesANightOwlOrTimeCountChangeAsLockedByRecords() =
        runTest(dispatcher) {
            val edits = listOf<Pair<String, (MedicationGroupEditorViewModel) -> Unit>>(
                "night owl on" to { viewModel -> viewModel.updateDayEndsAtFourAm(true) },
                "a time added" to { viewModel -> viewModel.addDailyTime(LocalTime.of(14, 0)) },
                "a time removed" to { viewModel ->
                    viewModel.removeDailyTime(viewModel.uiState.value.dailyTimes.first().localId)
                },
            )
            for ((name, edit) in edits) {
                val groupUuid = createDailyPlan(LocalTime.of(8, 0), LocalTime.of(20, 0))
                val viewModel = editor(groupUuid)
                val opened = viewModel.uiState.value
                edit(viewModel)
                assertTrue(name, viewModel.uiState.value != opened)

                // Written, but not yet observed by the editor.
                logRecord(groupUuid, now.toLocalDate().minusDays(1).atTime(20, 0), observe = false)
                viewModel.saveGroup()
                advanceUntilIdle()

                assertEquals(
                    name,
                    SaveMedicationGroupResult.LOCKED_BY_RECORDS,
                    viewModel.uiState.value.saveMedicationGroupResult,
                )
            }
        }

    @Test
    fun aLockedTimeChangeRetriedAfterAFailedSaveMovesEachRecordWithItsOwnSlot() =
        runTest(dispatcher) {
            val groupUuid = createDailyPlan(LocalTime.of(8, 0), LocalTime.of(20, 0))
            val (morningSlot, eveningSlot) = slotUuids(groupUuid)
            val yesterday = now.toLocalDate().minusDays(1)
            val morning = logRecord(groupUuid, yesterday.atTime(8, 0))
            val evening = logRecord(groupUuid, yesterday.atTime(20, 0))
            val viewModel = editor(groupUuid)
            assertTrue(viewModel.uiState.value.isLocked)
            val times = viewModel.uiState.value.dailyTimes
            viewModel.updateDailyTime(times.single { it.time == LocalTime.of(8, 0) }.localId, LocalTime.of(21, 0))
            viewModel.updateDailyTime(times.single { it.time == LocalTime.of(20, 0) }.localId, LocalTime.of(7, 0))

            failNextUpsert = true
            viewModel.saveGroup()
            advanceUntilIdle()
            assertEquals(
                SaveMedicationGroupResult.FAILURE,
                viewModel.uiState.value.saveMedicationGroupResult,
            )
            viewModel.consumeSaveMedicationGroupResult()
            viewModel.saveGroup()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isSaved)
            val records = room.medicationLogDao().getEntries().associateBy { it.uuid }
            assertEquals(yesterday.atTime(21, 0).toString(), records.getValue(morning).scheduledForIso)
            assertEquals(morningSlot.toString(), records.getValue(morning).scheduleTimeUuid)
            assertEquals(yesterday.atTime(7, 0).toString(), records.getValue(evening).scheduledForIso)
            assertEquals(eveningSlot.toString(), records.getValue(evening).scheduleTimeUuid)
            assertRecordOnItsSlot(groupUuid, morning)
            assertRecordOnItsSlot(groupUuid, evening)
        }

    private suspend fun createDailyPlan(
        vararg times: LocalTime,
        medicines: List<Medicine> = listOf(medicine),
        includePastScheduledSlots: Boolean = true,
        savedAt: LocalDateTime = since.atStartOfDay(),
        interval: Int = 1,
        startAt: LocalDateTime? = null,
        dayBoundary: MedicationDayBoundary = MedicationDayBoundary.Midnight,
        // A weekly plan on those days instead.
        weeklyOn: Set<DayOfWeek> = emptySet(),
    ): UUID {
        medicines.forEach { planMedicine ->
            // Once, for every plan a test makes.
            if (room.medicineDao().getByUuids(listOf(planMedicine.uuid.toString())).isEmpty()) {
                room.medicineDao().insert(planMedicine.toEntity())
            }
        }
        return repository.saveGroup(
            uuid = null,
            name = "Plan",
            colorKey = MedicationGroupColorKey.ROSE,
            schedule = MedicationGroupScheduleInput(
                type = if (weeklyOn.isNotEmpty()) MedicationGroupScheduleType.WEEKLY else MedicationGroupScheduleType.DAILY,
                interval = interval,
                since = since,
                weeklyDaysOfWeek = weeklyOn,
                times = times.toList(),
                startAt = startAt,
                dayBoundary = dayBoundary,
            ),
            medications = medicines.map { planMedicine ->
                MedicationGroupMedicationInput(
                    medicineUuid = planMedicine.uuid,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
                )
            },
            includePastScheduledSlots = includePastScheduledSlots,
            now = savedAt.atZone(ZoneId.systemDefault()).toInstant(),
        )
    }

    private fun TestScope.editor(
        groupUuid: UUID,
        clock: FakeAppTimeSource = FakeAppTimeSource(now),
        savedStateHandle: SavedStateHandle =
            SavedStateHandle(mapOf(MedicationGroupEditorViewModel.GROUP_ID_ARG to groupUuid.toString())),
    ): MedicationGroupEditorViewModel {
        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = repository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            context = context,
            savedStateHandle = savedStateHandle,
            appTimeSource = clock,
        )
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoadingGroupForEditing)
        return viewModel
    }

    private suspend fun slotUuids(groupUuid: UUID): List<UUID> =
        checkNotNull(repository.getGroup(groupUuid)).schedule.timeSlots
            .sortedBy { it.time }
            .map { it.uuid }

    /** Logs the slot at [scheduledFor], as a widget or reminder "Log" does; returns the record's uuid. */
    private suspend fun TestScope.logRecord(
        groupUuid: UUID,
        scheduledFor: LocalDateTime,
        observe: Boolean = true,
    ): String {
        val slot = checkNotNull(repository.getGroup(groupUuid)).schedule.timeSlots
            .single { it.time == scheduledFor.toLocalTime() }
        val uuid = UUID.randomUUID().toString()
        room.medicationLogDao().insertEntry(
            MedicationLogEntryEntity(
                uuid = uuid,
                category = medicine.category.name,
                medicineUuid = medicine.uuid.toString(),
                applicationType = MedicationApplicationType.ORAL.name,
                doseInstructionKind = "TABLET_FRACTION",
                tabletFractionNumerator = 1,
                tabletFractionDenominator = 1,
                doseVolumeMl = null,
                doseWeightGrams = null,
                equivalentE2Mg = null,
                sourceGroupUuid = groupUuid.toString(),
                scheduleTimeUuid = slot.uuid.toString(),
                appliedAtEpochMillis = scheduledFor.atZone(ZoneId.systemDefault()).toInstant()
                    .toEpochMilli(),
                scheduledForIso = scheduledFor.toString(),
            )
        )
        if (observe) {
            observeRecords()
            advanceUntilIdle()
        }
        return uuid
    }

    private suspend fun observeRecords() {
        entries.value = room.medicationLogDao().getEntries().map { entry ->
            entry.toMedicationLogEntryModel(planMedicines.associateBy { it.uuid.toString() })
        }
    }

    /** The record's medicine and dose are still one of the plan's, so the plan doesn't call it off-plan. */
    private suspend fun assertRecordMatchesAPlanMedication(groupUuid: UUID, recordUuid: String) {
        val record = checkNotNull(room.medicationLogDao().getEntry(recordUuid))
            .toMedicationLogEntryModel(planMedicines.associateBy { it.uuid.toString() })
        val plan = checkNotNull(repository.getGroup(groupUuid))
        assertTrue(
            "Record of ${record.medicineUuid} matches none of the plan's medications " +
                    "${plan.medications.map { it.medicineUuid }}",
            plan.medications.any { medication ->
                MedicationSignature.fromGroupMedication(medication) ==
                        MedicationSignature.fromLogEntry(record)
            },
        )
    }

    /** The record is still an occurrence of the slot it was logged for, so the plan counts it. */
    private suspend fun assertRecordOnItsSlot(groupUuid: UUID, recordUuid: String) {
        val record = checkNotNull(room.medicationLogDao().getEntry(recordUuid))
        val scheduledFor = LocalDateTime.parse(record.scheduledForIso)
        val plan = checkNotNull(repository.getGroup(groupUuid))
        assertTrue(
            "Record at $scheduledFor lost its slot ${record.scheduleTimeUuid}: " +
                    "the plan's slots are now ${plan.schedule.timeSlots}",
            plan.occurrencesBetweenInPlanWindow(scheduledFor.toLocalDate(), scheduledFor.toLocalDate())
                .any { occurrence ->
                    occurrence.scheduleTimeUuid.toString() == record.scheduleTimeUuid &&
                            occurrence.scheduledFor == scheduledFor
                },
        )
    }
}
