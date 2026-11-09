package com.mkx.hrttracker.ui.plan

import android.content.Context
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
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.toEntity
import com.mkx.hrttracker.data.repository.toMedicationLogEntryModel
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
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
import com.mkx.hrttracker.util.FakeAppTimeSource
import com.mkx.hrttracker.util.MedicationDayBoundary
import com.mkx.hrttracker.util.withAppLanguage
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

    // Null keeps the repository's groups flow quiet; a recreated plan's cutoff comes from it.
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
        room.close()
        Dispatchers.resetMain()
        unmockkStatic("com.mkx.hrttracker.util.LocalizationKt")
    }

    @Test
    fun aRecordArrivingAfterAScheduleTypeChangeStaysOnItsSlot() = runTest(dispatcher) {
        val groupUuid = createDailyPlan(LocalTime.of(8, 0))
        val viewModel = editor(groupUuid)
        viewModel.updateGroupName("Mornings")
        viewModel.updateScheduleType(MedicationGroupScheduleType.WEEKLY)

        val record = logRecord(groupUuid, now.toLocalDate().atTime(8, 0))
        viewModel.saveGroup()
        advanceUntilIdle()

        assertRecordOnItsSlot(groupUuid, record)
        assertNull(viewModel.uiState.value.saveMedicationGroupResult)
        // The name isn't the schedule's: it is still saved.
        assertEquals("Mornings", repository.getGroup(groupUuid)?.name)
    }

    @Test
    fun aRecordLandingAsTheSaveStartsRefusesAScheduleTypeChangeUntilTheEditorSeesIt() =
        runTest(dispatcher) {
            val groupUuid = createDailyPlan(LocalTime.of(8, 0))
            val viewModel = editor(groupUuid)
            viewModel.updateScheduleType(MedicationGroupScheduleType.WEEKLY)

            // Written, but not yet observed by the editor.
            val record = logRecord(groupUuid, now.toLocalDate().atTime(8, 0), observe = false)
            viewModel.saveGroup()
            advanceUntilIdle()

            assertRecordOnItsSlot(groupUuid, record)
            assertEquals(
                SaveMedicationGroupResult.LOCKED_BY_RECORDS,
                viewModel.uiState.value.saveMedicationGroupResult,
            )

            // Once the editor sees the record, the next save goes through.
            observeRecords()
            advanceUntilIdle()
            viewModel.consumeSaveMedicationGroupResult()
            viewModel.saveGroup()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isSaved)
            assertRecordOnItsSlot(groupUuid, record)
        }

    @Test
    fun aRecordArrivingAfterATimeWasAddedStillLetsThePlanSave() = runTest(dispatcher) {
        val groupUuid = createDailyPlan(LocalTime.of(8, 0))
        val slotUuid = slotUuids(groupUuid).single()
        val viewModel = editor(groupUuid)
        viewModel.updateGroupName("Mornings")
        viewModel.addDailyTime(LocalTime.of(20, 0))

        val record = logRecord(groupUuid, now.toLocalDate().atTime(8, 0))
        viewModel.saveGroup()
        advanceUntilIdle()

        assertNull(
            "The save should go through once the record locks the slots",
            viewModel.uiState.value.saveMedicationGroupResult,
        )
        assertTrue(viewModel.uiState.value.isSaved)
        assertEquals(listOf(slotUuid), slotUuids(groupUuid))
        assertRecordOnItsSlot(groupUuid, record)
        assertEquals("Mornings", repository.getGroup(groupUuid)?.name)
    }

    @Test
    fun aRecordArrivingAfterAMedicineWasRemovedStillMatchesThePlan() = runTest(dispatcher) {
        val groupUuid = createDailyPlan(LocalTime.of(8, 0), medicines = planMedicines)
        val viewModel = editor(groupUuid)
        viewModel.updateGroupName("Mornings")
        viewModel.removeMedication(
            viewModel.uiState.value.medications.single { it.medicineUuid == medicine.uuid }.localId
        )

        // Logged from the saved plan, which still has the removed medicine.
        val record = logRecord(groupUuid, now.toLocalDate().atTime(8, 0))
        viewModel.saveGroup()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        assertRecordMatchesAPlanMedication(groupUuid, record)
        assertEquals(
            planMedicines.map { it.uuid },
            checkNotNull(repository.getGroup(groupUuid)).medications.map { it.medicineUuid },
        )
        assertEquals("Mornings", repository.getGroup(groupUuid)?.name)
    }

    @Test
    fun aLockedRetimeBeforeTheStartIsRefusedAsItIsPickedWithItsOwnReason() = runTest(dispatcher) {
        // The plan starts at 07:00 on its first day, and that day's 08:00 dose is logged.
        val groupUuid = createDailyPlan(LocalTime.of(8, 0), startAt = since.atTime(7, 0))
        val record = logRecord(groupUuid, since.atTime(8, 0))
        val viewModel = editor(groupUuid)
        assertTrue(viewModel.uiState.value.isLocked)

        // 06:00 would move that dose before the plan's start.
        viewModel.updateDailyTime(viewModel.uiState.value.dailyTimes.single().localId, LocalTime.of(6, 0))

        assertEquals(LocalTime.of(8, 0), viewModel.uiState.value.dailyTimes.single().time)
        assertEquals(ScheduleEditNotice.LOGGED_DOSE_BEFORE_START, viewModel.uiState.value.scheduleEditNotice)
        viewModel.consumeScheduleEditNotice()
        assertNull(viewModel.uiState.value.scheduleEditNotice)
        // 07:00 is the start itself, so it is taken.
        viewModel.updateDailyTime(viewModel.uiState.value.dailyTimes.single().localId, LocalTime.of(7, 0))
        viewModel.saveGroup()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        assertNull(viewModel.uiState.value.scheduleEditNotice)
        assertEquals(since.atTime(7, 0).toString(), room.medicationLogDao().getEntry(record)?.scheduledForIso)
        assertRecordOnItsSlot(groupUuid, record)
    }

    @Test
    fun aRetimeBeforeTheStartIsRefusedAtTheSaveWithItsReasonOnceARecordArrives() = runTest(dispatcher) {
        val groupUuid = createDailyPlan(LocalTime.of(8, 0), startAt = since.atTime(7, 0))
        val viewModel = editor(groupUuid)
        viewModel.updateDailyIntervalDays("2")
        viewModel.updateDailyTime(viewModel.uiState.value.dailyTimes.single().localId, LocalTime.of(6, 0))
        assertNull(viewModel.uiState.value.scheduleEditNotice)

        // The first day's dose, logged after the retime: the editor learns of it only now.
        val record = logRecord(groupUuid, since.atTime(8, 0))
        // Every other day still has it, so that edit stays; the retime is left to the save.
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
        viewModel.updateDailyTime(viewModel.uiState.value.dailyTimes.single().localId, LocalTime.of(8, 0))
        viewModel.saveGroup()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSaved)
        assertEquals(2, repository.getGroup(groupUuid)?.schedule?.interval)
        assertRecordOnItsSlot(groupUuid, record)
    }

    @Test
    fun aSecondRetimeThatMovesAnotherDoseBeforeTheStartIsRefusedAsItIsPicked() = runTest(dispatcher) {
        val groupUuid = createDailyPlan(LocalTime.of(8, 0), LocalTime.of(20, 0), startAt = since.atTime(7, 0))
        val viewModel = editor(groupUuid)
        val (morning, evening) = viewModel.uiState.value.dailyTimes
        viewModel.updateDailyTime(morning.localId, LocalTime.of(6, 0))
        // Both of the first day's doses, logged after: 06:00 stays for the save to refuse.
        logRecord(groupUuid, since.atTime(8, 0))
        logRecord(groupUuid, since.atTime(20, 0))

        // 06:30 would move the 20:00 dose before 07:00 too, whatever 08:00 is changed to.
        viewModel.updateDailyTime(evening.localId, LocalTime.of(6, 30))

        assertEquals(ScheduleEditNotice.LOGGED_DOSE_BEFORE_START, viewModel.uiState.value.scheduleEditNotice)
        assertEquals(
            listOf(LocalTime.of(6, 0), LocalTime.of(20, 0)),
            viewModel.uiState.value.dailyTimes.map { it.time },
        )
    }

    @Test
    fun aStartDatePutBackByAnArrivingRecordTakesBackTheWeekdaysItReset() = runTest(dispatcher) {
        val groupUuid = createDailyPlan(
            LocalTime.of(11, 0),
            startAt = since.atTime(9, 0),
            weeklyOn = setOf(DayOfWeek.SUNDAY, DayOfWeek.TUESDAY),
        )
        val viewModel = editor(groupUuid)
        val today = now.toLocalDate()
        viewModel.updateSinceDate(today)
        assertEquals(setOf(DayOfWeek.SUNDAY), viewModel.uiState.value.weeklyDaysOfWeek)
        viewModel.updateStartTime(LocalTime.of(10, 30))
        viewModel.updateWeeklyTime(LocalTime.of(10, 0))

        // Today's dose: 10:00 moves it before 10:30, but not before the saved 09:00 start.
        val record = logRecord(groupUuid, today.atTime(11, 0))

        assertEquals(since, viewModel.uiState.value.sinceDate)
        assertEquals(setOf(DayOfWeek.SUNDAY, DayOfWeek.TUESDAY), viewModel.uiState.value.weeklyDaysOfWeek)
        viewModel.saveGroup()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSaved)
        assertEquals(
            setOf(DayOfWeek.SUNDAY, DayOfWeek.TUESDAY),
            repository.getGroup(groupUuid)?.schedule?.weeklyDaysOfWeek,
        )
        assertEquals(today.atTime(10, 0).toString(), room.medicationLogDao().getEntry(record)?.scheduledForIso)
    }

    @Test
    fun aKeptRetimeBeforeTheStartLeavesTheOtherTimesFreeToChange() = runTest(dispatcher) {
        val groupUuid = createDailyPlan(LocalTime.of(8, 0), LocalTime.of(20, 0), startAt = since.atTime(7, 0))
        val viewModel = editor(groupUuid)
        val (morning, evening) = viewModel.uiState.value.dailyTimes
        viewModel.updateDailyTime(morning.localId, LocalTime.of(6, 0))
        // The first day's 08:00 dose, logged after: 06:00 stays for the save to refuse.
        logRecord(groupUuid, since.atTime(8, 0))

        viewModel.updateDailyTime(evening.localId, LocalTime.of(21, 0))

        assertNull(viewModel.uiState.value.scheduleEditNotice)
        assertEquals(
            listOf(LocalTime.of(6, 0), LocalTime.of(21, 0)),
            viewModel.uiState.value.dailyTimes.map { it.time },
        )
        viewModel.saveGroup()
        advanceUntilIdle()
        assertEquals(
            SaveMedicationGroupResult.LOGGED_DOSE_BEFORE_START,
            viewModel.uiState.value.saveMedicationGroupResult,
        )
    }

    @Test
    fun aStartEditOnlyARetimeCantKeepGoesBackWhenARecordArrives() = runTest(dispatcher) {
        val groupUuid = createDailyPlan(LocalTime.of(8, 0), startAt = since.atTime(7, 0))
        val viewModel = editor(groupUuid)
        // Each fine alone: a start at 08:00, and the dose at 07:30.
        viewModel.updateStartTime(LocalTime.of(8, 0))
        viewModel.updateDailyTime(viewModel.uiState.value.dailyTimes.single().localId, LocalTime.of(7, 30))

        val record = logRecord(groupUuid, since.atTime(8, 0))

        // From 08:00 the moved dose comes before the start, from the saved 07:00 it doesn't: the
        // start goes back, now that the record locks it, and the time stays.
        val state = viewModel.uiState.value
        assertFalse(state.canEditScheduleStart)
        assertEquals(since.atTime(7, 0), state.scheduleStartAt(now))
        assertEquals(LocalTime.of(7, 30), state.dailyTimes.single().time)
        viewModel.saveGroup()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSaved)
        assertEquals(since.atTime(7, 30).toString(), room.medicationLogDao().getEntry(record)?.scheduledForIso)
        assertRecordOnItsSlot(groupUuid, record)
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

        viewModel.updateWeeklyTime(LocalTime.of(6, 0))

        assertEquals(LocalTime.of(8, 0), viewModel.uiState.value.weeklyTime)
        assertEquals(ScheduleEditNotice.LOGGED_DOSE_BEFORE_START, viewModel.uiState.value.scheduleEditNotice)
        viewModel.consumeScheduleEditNotice()
        viewModel.updateWeeklyTime(LocalTime.of(9, 0))
        assertEquals(LocalTime.of(9, 0), viewModel.uiState.value.weeklyTime)
        assertNull(viewModel.uiState.value.scheduleEditNotice)
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
    fun aWholeDayTurnedToMidnightStartsAtTheRecreatedPlansFourAmCutoff() = runTest(dispatcher) {
        // Archived through yesterday with the day ending at 04:00: the successor starts today at 04:00.
        val viewModel = recreatedEditor(nightOwl = true, archivedThroughDate = now.toLocalDate().minusDays(1))
        val today = now.toLocalDate()
        assertEquals(today.atTime(4, 0), viewModel.uiState.value.recreatedStartFloor)
        viewModel.updateSinceDate(today.plusDays(1))
        viewModel.updateSinceDate(today)

        viewModel.updateDayEndsAtFourAm(false)

        // Not midnight, which would show today's 02:00 dose that the cutoff keeps off the plan.
        val state = viewModel.uiState.value
        assertEquals(today, state.sinceDate)
        assertEquals(ScheduleStart.At(LocalTime.of(4, 0)), state.scheduleStart)
        assertEquals(today.atTime(4, 0), state.scheduleStartAt(now))
    }

    @Test
    fun startingNowWithTheClockBehindTheCutoffStartsAtTheCutoff() = runTest(dispatcher) {
        val recreatedAt = now.toLocalDate().atTime(0, 30)
        val clock = FakeAppTimeSource(recreatedAt)
        val viewModel = recreatedEditor(nightOwl = false, clock = clock)
        // Set back across midnight, before the cutoff's day.
        clock.setCurrentSnapshot(recreatedAt.minusHours(1), ZoneId.systemDefault())
        advanceUntilIdle()

        viewModel.resetStartToNow()

        assertEquals(recreatedAt.toLocalDate(), viewModel.uiState.value.sinceDate)
        assertEquals(ScheduleStart.At(LocalTime.of(0, 30)), viewModel.uiState.value.scheduleStart)
        viewModel.saveGroup()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.saveMedicationGroupResult)
        assertTrue(viewModel.uiState.value.isSaved)
    }

    @Test
    fun aStartNowLeftBehindByTheClockStartsAtTheCutoff() = runTest(dispatcher) {
        val clock = FakeAppTimeSource(now)
        val viewModel = recreatedEditor(nightOwl = false, clock = clock)
        viewModel.resetStartToNow()
        assertTrue(viewModel.uiState.value.startsNow)

        // An hour back, now is 11:00: starting then would show the 11:30 dose the cutoff keeps off.
        clock.setCurrentSnapshot(now.minusHours(1), ZoneId.systemDefault())
        advanceUntilIdle()
        viewModel.saveGroup()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        val saved = checkNotNull(repository.getGroup(UUID.fromString(viewModel.uiState.value.editingGroupId)))
        assertEquals(listOf(now, now), saved.schedule.timeSlots.map { it.effectiveFrom })
        assertEquals(ScheduleStart.At(LocalTime.of(12, 0)), viewModel.uiState.value.scheduleStart)
    }

    @Test
    fun aStartTimeBeforeTheCutoffMovesUpToItAndSaysSo() = runTest(dispatcher) {
        val viewModel = recreatedEditor(nightOwl = false)

        viewModel.updateStartTime(LocalTime.of(11, 0))

        val state = viewModel.uiState.value
        assertEquals(ScheduleStart.At(LocalTime.of(12, 0)), state.scheduleStart)
        assertEquals(now, state.scheduleStartAt(now))
        assertEquals(ScheduleEditNotice.START_MOVED_TO_CUTOFF, state.scheduleEditNotice)
        // The start is the loaded one again, so leaving loses nothing, nor after night owl on and off.
        advanceUntilIdle()
        assertFalse(viewModel.hasUnsavedChanges.value)
        viewModel.updateDayEndsAtFourAm(true)
        viewModel.updateDayEndsAtFourAm(false)
        advanceUntilIdle()
        assertFalse(viewModel.hasUnsavedChanges.value)
        // A time after it is taken as picked.
        viewModel.consumeScheduleEditNotice()
        viewModel.updateStartTime(LocalTime.of(15, 0))
        assertEquals(ScheduleStart.At(LocalTime.of(15, 0)), viewModel.uiState.value.scheduleStart)
        assertNull(viewModel.uiState.value.scheduleEditNotice)
    }

    @Test
    fun aNightOwlStartTimePickedAfterMidnightLandsLaterToday() = runTest(dispatcher) {
        // At 00:30 the day that began yesterday at 04:00 is still the start date.
        val recreatedAt = now.toLocalDate().atTime(0, 30)
        val viewModel = recreatedEditor(nightOwl = true, clock = FakeAppTimeSource(recreatedAt))
        assertEquals(recreatedAt.toLocalDate().minusDays(1), viewModel.uiState.value.sinceDate)

        viewModel.updateStartTime(LocalTime.of(11, 0))

        val state = viewModel.uiState.value
        assertEquals(recreatedAt.toLocalDate(), state.sinceDate)
        assertEquals(ScheduleStart.At(LocalTime.of(11, 0)), state.scheduleStart)
        assertEquals(recreatedAt.toLocalDate().atTime(11, 0), state.scheduleStartAt(recreatedAt))
        assertNull(state.scheduleEditNotice)
    }

    @Test
    fun aStartMovedPastARecordThatArrivesGoesBackToTheSavedStart() = runTest(dispatcher) {
        val groupUuid = createDailyPlan(LocalTime.of(8, 0))
        val viewModel = editor(groupUuid)
        viewModel.updateGroupName("Mornings")
        viewModel.updateSinceDate(now.toLocalDate().plusDays(2))

        val record = logRecord(groupUuid, now.toLocalDate().atTime(8, 0))
        assertEquals(since, viewModel.uiState.value.sinceDate)
        viewModel.saveGroup()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        assertRecordOnItsSlot(groupUuid, record)
        assertEquals("Mornings", repository.getGroup(groupUuid)?.name)
    }

    @Test
    fun aStartThatKeepsTheRecordsStaysWhenAnAddedTimeGoesBack() = runTest(dispatcher) {
        val groupUuid = createDailyPlan(LocalTime.of(8, 0))
        val viewModel = editor(groupUuid)
        viewModel.updateStartTime(LocalTime.of(7, 0))
        viewModel.addDailyTime(LocalTime.of(20, 0))

        val record = logRecord(groupUuid, now.toLocalDate().atTime(8, 0))
        viewModel.saveGroup()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        assertRecordOnItsSlot(groupUuid, record)
        assertEquals(
            listOf(since.atTime(7, 0)),
            checkNotNull(repository.getGroup(groupUuid)).schedule.timeSlots.map { it.effectiveFrom },
        )
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

    @Test
    fun aReplacementThatFailsLeavesThePlanActiveAndARetryReplacesIt() = runTest(dispatcher) {
        val groupUuid = createDailyPlan(LocalTime.of(8, 0))
        val viewModel = editor(groupUuid)

        failNextUpsert = true
        viewModel.archiveAndRecreateGroup()
        advanceUntilIdle()

        assertEquals(
            ArchiveAndRecreateMedicationGroupResult.FAILURE,
            viewModel.uiState.value.archiveAndRecreateMedicationGroupResult,
        )
        assertNull(
            "A replacement that failed archived the plan anyway",
            repository.getGroup(groupUuid)?.archivedAt,
        )
        assertFalse(viewModel.uiState.value.isArchived)
        assertEquals(groupUuid.toString(), viewModel.uiState.value.editingGroupId)

        viewModel.consumeArchiveAndRecreateMedicationGroupResult()
        viewModel.archiveAndRecreateGroup()
        advanceUntilIdle()

        assertEquals(
            ArchiveAndRecreateMedicationGroupResult.SUCCESS,
            viewModel.uiState.value.archiveAndRecreateMedicationGroupResult,
        )
        val original = checkNotNull(repository.getGroup(groupUuid))
        val successorUuid = UUID.fromString(viewModel.uiState.value.editingGroupId)
        assertNotNull(original.archivedAt)
        assertEquals(successorUuid, original.replacedByGroupUuid)
        assertEquals(groupUuid, repository.getGroup(successorUuid)?.recreatedFromGroupUuid)
    }

    private suspend fun createDailyPlan(
        vararg times: LocalTime,
        medicines: List<Medicine> = listOf(medicine),
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
                interval = 1,
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
            includePastScheduledSlots = true,
            now = since.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant(),
        )
    }

    /** An editor on a plan at 02:00 and 11:30, just archived and recreated in it at [clock]'s now. */
    private suspend fun TestScope.recreatedEditor(
        nightOwl: Boolean,
        archivedThroughDate: LocalDate? = null,
        clock: FakeAppTimeSource = FakeAppTimeSource(now),
    ): MedicationGroupEditorViewModel {
        // Live, as in the app: the editor reads the archived plan's cutoff from it.
        databaseFlow.value = database
        val predecessor = createDailyPlan(
            LocalTime.of(2, 0),
            LocalTime.of(11, 30),
            dayBoundary = MedicationDayBoundary.fromSwitch(nightOwl),
        )
        val viewModel = editor(predecessor, clock)
        viewModel.archiveAndRecreateGroup(archivedThroughDate)
        advanceUntilIdle()
        assertEquals(
            ArchiveAndRecreateMedicationGroupResult.SUCCESS,
            viewModel.uiState.value.archiveAndRecreateMedicationGroupResult,
        )
        assertNotNull(viewModel.uiState.value.recreatedStartFloor)
        return viewModel
    }

    private fun TestScope.editor(
        groupUuid: UUID,
        clock: FakeAppTimeSource = FakeAppTimeSource(now),
    ): MedicationGroupEditorViewModel {
        val viewModel = MedicationGroupEditorViewModel(
            medicationGroupRepository = repository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            context = context,
            savedStateHandle = SavedStateHandle(
                mapOf(MedicationGroupEditorViewModel.GROUP_ID_ARG to groupUuid.toString())
            ),
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
