package com.mkx.hrttracker.ui.plan

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationGroupScheduleInput
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleTime
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.occurrencesBetweenInPlanWindow
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.reminder.MedicationReminderSnoozeScheduler
import com.mkx.hrttracker.util.FakeAppTimeSource
import com.mkx.hrttracker.util.MedicationDayBoundary
import com.mkx.hrttracker.util.withAppLanguage
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The plan editor's own Night owl toggle: each plan carries its day ending. */
@OptIn(ExperimentalCoroutinesApi::class)
class MedicationGroupEditorMedicationDayTest {
    private val medicationGroupRepository: MedicationGroupRepository = mockk()
    private val medicationLogRepository: MedicationLogRepository = mockk()
    private val medicineRepository: MedicineRepository = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk()
    private val medicationReminderScheduler: MedicationReminderScheduler = mockk(relaxed = true)
    private val medicationReminderSnoozeScheduler: MedicationReminderSnoozeScheduler =
        mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val dispatcher = StandardTestDispatcher()
    private val groupUuid = UUID.fromString("0e1f2a3b-0000-4000-8000-000000000001")
    private val savedGroupUuid = UUID.fromString("0e1f2a3b-0000-4000-8000-000000000002")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val settings = SettingsState(remindersEnabled = true)
        every { settingsRepository.settingsState } returns MutableStateFlow(settings)
        coEvery { settingsRepository.getCurrentSettings() } returns settings
        coEvery { settingsRepository.peekNextGroupNameIndex() } returns 1
        coEvery { settingsRepository.consumeNextGroupNameIndex() } returns 1
        every { medicationLogRepository.observeEntries() } returns flowOf(emptyList())
        every { medicationGroupRepository.getCachedGroup(any()) } returns null
        every { medicationGroupRepository.observeGroups() } returns flowOf(emptyList())
        every { medicineRepository.observeAllActive() } returns
                MutableStateFlow<List<Medicine>>(emptyList())
        every {
            context.getString(R.string.default_group_name_format, any())
        } answers {
            val formatArgs = invocation.args[1] as Array<*>
            "Group ${formatArgs.first()}"
        }
        mockkStatic("com.mkx.hrttracker.util.LocalizationKt")
        every { context.withAppLanguage() } returns context
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic("com.mkx.hrttracker.util.LocalizationKt")
    }

    @Test
    fun newPlanStartsAtMidnight() = runTest {
        val viewModel = newViewModel(now = LocalDateTime.of(2026, 10, 19, 1, 32))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(MedicationDayBoundary.Midnight, state.medicationDayBoundary)
        assertEquals(LocalDate.of(2026, 10, 19), state.sinceDate)
        assertTrue(state.canEditDayBoundary)
    }

    @Test
    fun turningNightOwlOnKeepsANowStartAndItsDate() = runTest {
        // Monday Oct 19 at 01:32: still Sunday's medication day once the day ends at 04:00.
        val now = LocalDateTime.of(2026, 10, 19, 1, 32)
        val viewModel = newViewModel(now = now)
        advanceUntilIdle()

        viewModel.updateDayEndsAtFourAm(true)
        viewModel.updateScheduleType(MedicationGroupScheduleType.WEEKLY)

        val state = viewModel.uiState.value
        assertEquals(MedicationDayBoundary.FourAm, state.medicationDayBoundary)
        // The start as chosen: now, on the calendar date.
        assertEquals(LocalDate.of(2026, 10, 19), state.sinceDate)
        assertTrue(state.startsNow)
        assertEquals(LocalTime.of(2, 0), state.weeklyTime)
        // Stored from Sunday, the medication day now falls on, and its untouched weekday follows
        // that day: Sunday's 02:00 is tonight's.
        assertEquals(LocalDate.of(2026, 10, 18), state.toScheduleInput(now).since)
        assertEquals(setOf(DayOfWeek.SUNDAY), state.scheduleWeekdays(now))
        assertEquals(setOf(DayOfWeek.SUNDAY), state.toScheduleInput(now).weeklyDaysOfWeek)
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 10, 19, 2, 0),
                LocalDateTime.of(2026, 10, 26, 2, 0),
            ),
            buildMedicationGroupEditorUpcomingOccurrences(uiState = state, start = now),
        )
        // At midnight the chip doesn't flip: the medication day is still Sunday until 04:00.
        assertEquals(setOf(DayOfWeek.SUNDAY), state.withStartsNowOn(LocalDateTime.of(2026, 10, 19, 3, 59)).scheduleWeekdays(LocalDateTime.of(2026, 10, 19, 3, 59)))
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 10, 19, 2, 0),
                LocalDateTime.of(2026, 10, 20, 2, 0),
            ),
            buildMedicationGroupEditorUpcomingOccurrences(
                uiState = state.copy(scheduleType = MedicationGroupScheduleType.DAILY),
                start = now,
            ),
        )

        viewModel.updateDayEndsAtFourAm(false)

        assertEquals(MedicationDayBoundary.Midnight, viewModel.uiState.value.medicationDayBoundary)
        assertEquals(LocalDate.of(2026, 10, 19), viewModel.uiState.value.sinceDate)
    }

    @Test
    fun turningNightOwlOnDoesNotMoveAPickedStartDate() = runTest {
        val viewModel = newViewModel(now = LocalDateTime.of(2026, 10, 19, 1, 32))
        advanceUntilIdle()
        viewModel.updateScheduleType(MedicationGroupScheduleType.WEEKLY)
        viewModel.updateSinceDate(LocalDate.of(2026, 10, 22))

        viewModel.updateDayEndsAtFourAm(true)

        val state = viewModel.uiState.value
        assertEquals(MedicationDayBoundary.FourAm, state.medicationDayBoundary)
        assertEquals(LocalDate.of(2026, 10, 22), state.sinceDate)
        assertEquals(setOf(DayOfWeek.THURSDAY), state.weeklyDaysOfWeek)
    }

    @Test
    fun draftStartingNowAtTwoKeepsTonightsThreeOClockDoseWhenNightOwlTurnsOn() = runTest {
        val now = LocalDateTime.of(2026, 10, 29, 2, 0)
        val viewModel = newViewModel(now = now)
        advanceUntilIdle()
        val localId = viewModel.uiState.value.dailyTimes.single().localId
        viewModel.updateDailyTime(localId, LocalTime.of(3, 0))
        assertEquals(
            LocalDateTime.of(2026, 10, 29, 3, 0),
            buildMedicationGroupEditorUpcomingOccurrences(viewModel.uiState.value, now).first(),
        )

        viewModel.updateDayEndsAtFourAm(true)

        val state = viewModel.uiState.value
        assertEquals(LocalDate.of(2026, 10, 29), state.sinceDate)
        assertEquals(LocalDate.of(2026, 10, 28), state.toScheduleInput(now).since)
        assertEquals(now, state.scheduleStartAt(now))
        assertEquals(
            LocalDateTime.of(2026, 10, 29, 3, 0),
            buildMedicationGroupEditorUpcomingOccurrences(state, now).first(),
        )
    }

    @Test
    fun loadedFourAmPlanOpensAsFourAm() = runTest {
        val group = nightOwlGroup()
        every { medicationGroupRepository.getCachedGroup(groupUuid) } returns group

        val viewModel = editingViewModel(now = LocalDateTime.of(2026, 10, 29, 12, 0))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(MedicationDayBoundary.FourAm, state.medicationDayBoundary)
        assertTrue(state.canEditDayBoundary)
    }

    @Test
    fun nightOwlIsLockedUntilRecordsLoadAndOnceThePlanHasRecords() = runTest {
        every { medicationGroupRepository.getCachedGroup(groupUuid) } returns nightOwlGroup()
        every { medicationLogRepository.observeEntries() } returns flowOf(null)
        val loading = editingViewModel(now = LocalDateTime.of(2026, 10, 29, 12, 0))
        advanceUntilIdle()
        assertFalse(loading.uiState.value.canEditDayBoundary)

        every { medicationLogRepository.observeEntries() } returns
                flowOf(listOf(planRecord(LocalDateTime.of(2026, 10, 28, 22, 0))))
        val viewModel = editingViewModel(now = LocalDateTime.of(2026, 10, 29, 12, 0))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canEditDayBoundary)
        viewModel.updateDayEndsAtFourAm(false)
        assertEquals(MedicationDayBoundary.FourAm, viewModel.uiState.value.medicationDayBoundary)
    }

    @Test
    fun nightOwlIsLockedOnAnArchivedPlan() = runTest {
        every { medicationGroupRepository.getCachedGroup(groupUuid) } returns
                nightOwlGroup().copy(archivedAt = Instant.parse("2026-10-20T00:00:00Z"))

        val viewModel = editingViewModel(now = LocalDateTime.of(2026, 10, 29, 12, 0))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canEditDayBoundary)
        viewModel.updateDayEndsAtFourAm(false)
        assertEquals(MedicationDayBoundary.FourAm, viewModel.uiState.value.medicationDayBoundary)
    }

    @Test
    fun savingPassesTheDraftsEnding() = runTest {
        for (type in MedicationGroupScheduleType.entries) {
            val group = nightOwlGroup(type = type, dayBoundary = MedicationDayBoundary.Midnight)
            every { medicationGroupRepository.getCachedGroup(groupUuid) } returns group
            every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
            val savedSchedule = stubSaveGroup(uuid = groupUuid, returns = groupUuid)
            val viewModel = editingViewModel(now = LocalDateTime.of(2026, 10, 29, 12, 0))
            advanceUntilIdle()

            viewModel.updateDayEndsAtFourAm(true)
            viewModel.saveGroup()
            advanceUntilIdle()

            assertEquals(type, savedSchedule.captured.type)
            assertEquals(MedicationDayBoundary.FourAm, savedSchedule.captured.dayBoundary)
        }
    }

    @Test
    fun recordsTheSaveWritesKeepTheSavedEnding() = runTest {
        val group = nightOwlGroup(dayBoundary = MedicationDayBoundary.Midnight)
        val entries = MutableStateFlow<List<MedicationLogEntry>?>(emptyList())
        every { medicationGroupRepository.getCachedGroup(groupUuid) } returns group
        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        every { medicationLogRepository.observeEntries() } returns entries
        val savedSchedule = stubSaveGroup(uuid = groupUuid, returns = groupUuid)
        // The save writes past records before it returns.
        coEvery {
            medicationGroupRepository.saveGroup(
                uuid = groupUuid,
                name = any(),
                colorKey = any(),
                schedule = capture(savedSchedule),
                medications = any(),
                notificationsEnabled = any(),
                includePastScheduledSlots = any(),
                now = any(),
            )
        } coAnswers {
            entries.value = listOf(planRecord(LocalDateTime.of(2026, 10, 28, 1, 0)))
            kotlinx.coroutines.yield()
            groupUuid
        }
        val viewModel = editingViewModel(now = LocalDateTime.of(2026, 10, 29, 12, 0))
        advanceUntilIdle()

        viewModel.updateDayEndsAtFourAm(true)
        viewModel.saveGroup()
        advanceUntilIdle()
        // More records after the save, as the repository's later writes.
        entries.value = entries.value.orEmpty() +
                planRecord(LocalDateTime.of(2026, 10, 29, 1, 0))
        advanceUntilIdle()

        assertEquals(MedicationDayBoundary.FourAm, savedSchedule.captured.dayBoundary)
        assertEquals(MedicationDayBoundary.FourAm, viewModel.uiState.value.medicationDayBoundary)
        assertEquals(
            MedicationDayBoundary.FourAm,
            viewModel.uiState.value.originalMedicationDayBoundary,
        )
    }

    @Test
    fun theArchiveShortcutsCopyKeepsTheEndingAndStartsAtTheCutoff() = runTest {
        // 02:00 on Oct 29 is still Oct 28 for the night-owl plan.
        val now = LocalDateTime.of(2026, 10, 29, 2, 0)
        for (type in MedicationGroupScheduleType.entries) {
            val group = nightOwlGroup(type = type)
            every { medicationGroupRepository.getCachedGroup(groupUuid) } returns group
            every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
            every { medicationLogRepository.observeEntries() } returns
                    flowOf(listOf(planRecord(LocalDateTime.of(2026, 10, 28, 1, 0))))
            coEvery { medicationGroupRepository.archiveGroup(groupUuid, null, any()) } returns now
            val savedSchedule = stubSaveGroup(uuid = null, returns = savedGroupUuid)
            val viewModel = editingViewModel(now = now)
            advanceUntilIdle()
            // The window ends the day before the plan's own today.
            assertEquals(
                LocalDate.of(2026, 10, 27),
                viewModel.uiState.value.archiveDateWindow.maxDate,
            )

            viewModel.archiveGroup(archivedThroughDate = null, thenDuplicate = true)
            advanceUntilIdle()
            // The copy is a new plan, so it takes a new ending.
            assertTrue(viewModel.uiState.value.canEditDayBoundary)
            viewModel.saveGroup()
            advanceUntilIdle()

            assertEquals(type, savedSchedule.captured.type)
            assertEquals(MedicationDayBoundary.FourAm, savedSchedule.captured.dayBoundary)
            assertEquals(now, savedSchedule.captured.startAt)
            assertEquals(LocalDate.of(2026, 10, 28), savedSchedule.captured.since)
        }
    }

    @Test
    fun duplicatingAnArchivedPlanKeepsItsEnding() = runTest {
        val archived = nightOwlGroup().copy(archivedAt = Instant.parse("2026-10-20T00:00:00Z"))
        every { medicationGroupRepository.getCachedGroup(groupUuid) } returns archived
        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(archived))
        val savedSchedule = stubSaveGroup(uuid = null, returns = savedGroupUuid)
        val viewModel = editingViewModel(now = LocalDateTime.of(2026, 10, 29, 2, 0))
        advanceUntilIdle()

        viewModel.duplicateArchivedGroup()
        advanceUntilIdle()

        assertEquals(MedicationDayBoundary.FourAm, viewModel.uiState.value.medicationDayBoundary)
        assertEquals(LocalDate.of(2026, 10, 29), viewModel.uiState.value.sinceDate)
        assertTrue(viewModel.uiState.value.canEditDayBoundary)
        viewModel.saveGroup()
        advanceUntilIdle()
        assertEquals(MedicationDayBoundary.FourAm, savedSchedule.captured.dayBoundary)
        assertEquals(LocalDate.of(2026, 10, 28), savedSchedule.captured.since)
    }

    @Test
    fun archiveWindowTodayFollowsAMidnightPlansOwnEnding() = runTest {
        val group = nightOwlGroup(dayBoundary = MedicationDayBoundary.Midnight)
        every { medicationGroupRepository.getCachedGroup(groupUuid) } returns group
        every { medicationLogRepository.observeEntries() } returns
                flowOf(listOf(planRecord(LocalDateTime.of(2026, 10, 29, 1, 0))))

        val viewModel = editingViewModel(now = LocalDateTime.of(2026, 10, 29, 2, 0))
        advanceUntilIdle()

        // The midnight plan's 01:00 record and today are both Oct 29, so the window,
        // ending yesterday, is empty: the plan can only end now.
        val window = viewModel.uiState.value.archiveDateWindow
        assertEquals(LocalDate.of(2026, 10, 29), window.minDate)
        assertEquals(LocalDate.of(2026, 10, 28), window.maxDate)
        assertFalse(window.isSelectable)
    }

    @Test
    fun nightOwlLeavesAPickedStartAsChosen() = runTest {
        val viewModel = newViewModel(now = LocalDateTime.of(2026, 10, 20, 12, 0))
        advanceUntilIdle()
        viewModel.updateScheduleType(MedicationGroupScheduleType.WEEKLY)
        viewModel.updateSinceDate(LocalDate.of(2026, 10, 29))
        viewModel.updateStartTime(LocalTime.of(2, 0))
        val picked = viewModel.uiState.value

        viewModel.updateDayEndsAtFourAm(true)

        // Only the day ending changed: the date, the time and the weekdays are as picked.
        assertEquals(picked.copy(medicationDayBoundary = MedicationDayBoundary.FourAm), viewModel.uiState.value)
        viewModel.updateDayEndsAtFourAm(false)
        assertEquals(picked, viewModel.uiState.value)
        // A whole day stays a whole day.
        viewModel.updateSinceDate(LocalDate.of(2026, 10, 29))
        viewModel.updateDayEndsAtFourAm(true)
        assertEquals(ScheduleStart.WholeDay, viewModel.uiState.value.scheduleStart)
        assertEquals(LocalDateTime.of(2026, 10, 29, 4, 0), viewModel.uiState.value.scheduleStartAt(LocalDateTime.MIN))
    }

    @Test
    fun aRecordArrivingAfterTheToggleKeepsItAndRefusesTheSave() = runTest {
        val group = nightOwlGroup(dayBoundary = MedicationDayBoundary.Midnight)
        val entries = MutableStateFlow<List<MedicationLogEntry>?>(emptyList())
        every { medicationGroupRepository.getCachedGroup(groupUuid) } returns group
        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        every { medicationLogRepository.observeEntries() } returns entries
        val savedSchedule = stubSaveGroup(uuid = groupUuid, returns = groupUuid)
        val viewModel = editingViewModel(now = LocalDateTime.of(2026, 10, 20, 12, 0))
        advanceUntilIdle()
        viewModel.updateStartTime(LocalTime.of(2, 0))
        val opened = viewModel.uiState.value
        viewModel.updateDayEndsAtFourAm(true)
        advanceUntilIdle()
        assertEquals(opened.sinceDate, viewModel.uiState.value.sinceDate)

        entries.value = listOf(planRecord(LocalDateTime.of(2026, 10, 19, 1, 0)))
        advanceUntilIdle()

        // Nothing goes back; the save is refused instead.
        val state = viewModel.uiState.value
        assertFalse(state.canEditDayBoundary)
        assertEquals(MedicationDayBoundary.FourAm, state.medicationDayBoundary)
        assertEquals(opened.sinceDate, state.sinceDate)
        assertEquals(ScheduleStart.At(LocalTime.of(2, 0)), state.scheduleStart)
        viewModel.saveGroup()
        advanceUntilIdle()
        assertEquals(SaveMedicationGroupResult.LOCKED_BY_RECORDS, viewModel.uiState.value.saveMedicationGroupResult)
        assertFalse(savedSchedule.isCaptured)
    }

    @Test
    fun repickingAnUnchangedTimeOnALockedNightOwlPlanIsNoTimeChange() = runTest {
        every { medicationGroupRepository.getCachedGroup(groupUuid) } returns
                nightOwlGroup(times = listOf(LocalTime.of(1, 0), LocalTime.of(22, 0)))
        every { medicationLogRepository.observeEntries() } returns
                flowOf(listOf(planRecord(LocalDateTime.of(2026, 10, 28, 22, 0))))
        val viewModel = editingViewModel(now = LocalDateTime.of(2026, 10, 29, 12, 0))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLocked)

        val evening = viewModel.uiState.value.dailyTimes.single { it.time == LocalTime.of(22, 0) }
        viewModel.updateDailyTime(evening.localId, LocalTime.of(22, 0))

        assertFalse(lockedScheduleTimesChanged(viewModel.uiState.value))
        assertFalse(shouldShowLockedScheduleTimeNote(viewModel.uiState.value))
    }

    @Test
    fun anUnsavedToggleLeavesTheArchiveTodayOnTheSavedEnding() = runTest {
        // A recordless midnight plan at 02:00 with night owl turned on but not saved.
        val group = nightOwlGroup(dayBoundary = MedicationDayBoundary.Midnight)
        every { medicationGroupRepository.getCachedGroup(groupUuid) } returns group
        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        val viewModel = editingViewModel(now = LocalDateTime.of(2026, 10, 29, 2, 0))
        advanceUntilIdle()

        viewModel.updateDayEndsAtFourAm(true)
        advanceUntilIdle()

        assertEquals(MedicationDayBoundary.FourAm, viewModel.uiState.value.medicationDayBoundary)
        assertEquals(LocalDate.of(2026, 10, 28), viewModel.uiState.value.archiveDateWindow.maxDate)
    }

    @Test
    fun aSavedStartPastItsDatesOwnDayKeepsItsDateAndRhythm() = runTest {
        // Saved with a past start date and the past hidden: the start is the save time.
        val now = LocalDateTime.of(2026, 10, 29, 12, 0)
        every { medicationGroupRepository.getCachedGroup(groupUuid) } returns nightOwlGroup(
            dayBoundary = MedicationDayBoundary.Midnight,
            times = listOf(LocalTime.of(9, 0)),
            effectiveFrom = LocalDateTime.of(2026, 10, 24, 14, 0),
            interval = 2,
        )
        val viewModel = editingViewModel(now = now)
        advanceUntilIdle()
        val opened = viewModel.uiState.value
        val upcoming = buildMedicationGroupEditorUpcomingOccurrences(opened, now)

        viewModel.updateDayEndsAtFourAm(true)

        val nightOwl = viewModel.uiState.value
        // A start past its first day: the form shows that first day, Oct 1, and counts from it.
        assertEquals(LocalDate.of(2026, 10, 1), nightOwl.sinceDate)
        assertEquals(LocalDate.of(2026, 10, 1), nightOwl.toScheduleInput(now).since)
        assertEquals(upcoming, buildMedicationGroupEditorUpcomingOccurrences(nightOwl, now))
        viewModel.updateDayEndsAtFourAm(false)
        assertEquals(opened, viewModel.uiState.value)
    }

    @Test
    fun nightOwlOnASavedPlanKeepsItsRhythm() = runTest {
        // A midnight plan from Sun Nov 1, started at 02:00 that day.
        val since = LocalDate.of(2026, 11, 1)
        val now = LocalDateTime.of(2026, 11, 8, 12, 0)
        for ((type, interval) in listOf(MedicationGroupScheduleType.DAILY to 2, MedicationGroupScheduleType.WEEKLY to 2)) {
            val group = nightOwlGroup(
                type = type,
                interval = interval,
                dayBoundary = MedicationDayBoundary.Midnight,
                since = since,
                times = listOf(LocalTime.of(9, 0)),
                effectiveFrom = since.atTime(2, 0),
            )
            every { medicationGroupRepository.getCachedGroup(groupUuid) } returns group
            every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
            val savedSchedule = stubSaveGroup(uuid = groupUuid, returns = groupUuid)
            val viewModel = editingViewModel(now = now)
            advanceUntilIdle()
            val upcoming = buildMedicationGroupEditorUpcomingOccurrences(viewModel.uiState.value, now)

            viewModel.updateDayEndsAtFourAm(true)

            // The start day stays, so the doses stay on their days; 02:00 now falls before that
            // day's 04:00 start, so the plan starts then.
            val nightOwl = viewModel.uiState.value
            assertEquals("$type", upcoming, buildMedicationGroupEditorUpcomingOccurrences(nightOwl, now))
            viewModel.saveGroup()
            advanceUntilIdle()
            assertEquals("$type", since, savedSchedule.captured.since)
            assertEquals("$type", since.atTime(4, 0), savedSchedule.captured.startAt)
            // Turned back, it is the saved start again.
            viewModel.updateDayEndsAtFourAm(false)
            assertEquals("$type", since.atTime(2, 0), viewModel.uiState.value.scheduleStartAt(now))
        }
    }

    @Test
    fun aSavedNightOwlStartInTheSmallHoursReopensOnItsSavedDate() = runTest {
        // Saved from Oct 20's medication day, starting Oct 21 at 01:00 (still Oct 20's night).
        val group = nightOwlGroup(since = LocalDate.of(2026, 10, 20), effectiveFrom = LocalDateTime.of(2026, 10, 21, 1, 0))
        every { medicationGroupRepository.getCachedGroup(groupUuid) } returns group
        every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
        val savedSchedule = stubSaveGroup(uuid = groupUuid, returns = groupUuid)
        val viewModel = editingViewModel(now = LocalDateTime.of(2026, 10, 19, 12, 0))
        advanceUntilIdle()

        // The form shows the start as entered; the saved first day is kept beside it.
        assertEquals(LocalDate.of(2026, 10, 21), viewModel.uiState.value.sinceDate)
        assertEquals(
            ScheduleStart.Loaded(LocalDateTime.of(2026, 10, 21, 1, 0), LocalDate.of(2026, 10, 20)),
            viewModel.uiState.value.scheduleStart,
        )
        viewModel.saveGroup()
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 10, 20), savedSchedule.captured.since)
        assertEquals(LocalDateTime.of(2026, 10, 21, 1, 0), savedSchedule.captured.startAt)
    }

    @Test
    fun aSavedStartInTheNextNightsSmallHoursKeepsItsMomentAcrossNightOwl() = runTest {
        // A midnight plan from Oct 20 whose saved start is Oct 21 02:00: past its date's
        // own day under midnight, though on it under 04:00.
        val now = LocalDateTime.of(2026, 10, 19, 12, 0)
        val start = LocalDateTime.of(2026, 10, 21, 2, 0)
        every { medicationGroupRepository.getCachedGroup(groupUuid) } returns nightOwlGroup(
            dayBoundary = MedicationDayBoundary.Midnight,
            since = LocalDate.of(2026, 10, 20),
            effectiveFrom = start,
        )
        val viewModel = editingViewModel(now = now)
        advanceUntilIdle()
        val opened = viewModel.uiState.value

        viewModel.updateDayEndsAtFourAm(true)

        // Past its first day under midnight: the form shows the first day, Oct 20.
        assertEquals(LocalDate.of(2026, 10, 20), viewModel.uiState.value.sinceDate)
        assertEquals(LocalDate.of(2026, 10, 20), viewModel.uiState.value.toScheduleInput(now).since)
        assertEquals(start, viewModel.uiState.value.scheduleStartAt(now))
        viewModel.updateDayEndsAtFourAm(false)
        assertEquals(opened, viewModel.uiState.value)
        assertEquals(start, viewModel.uiState.value.scheduleStartAt(now))
    }

    @Test
    fun aDraftStartingNowMovesItsStartDateOnceTheDayEnds() = runTest {
        // Opened Thu Oct 29 at 23:20, still open at 00:10 on Friday.
        val timeSource = FakeAppTimeSource(LocalDateTime.of(2026, 10, 29, 23, 20))
        val viewModel = newViewModel(LocalDateTime.of(2026, 10, 29, 23, 20), timeSource)
        advanceUntilIdle()
        viewModel.updateScheduleType(MedicationGroupScheduleType.WEEKLY)
        assertTrue(viewModel.uiState.value.startsNow)
        assertEquals(LocalDate.of(2026, 10, 29), viewModel.uiState.value.sinceDate)

        timeSource.setCurrentMinute(LocalDateTime.of(2026, 10, 30, 0, 10))
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 10, 30), viewModel.uiState.value.sinceDate)
        assertEquals(setOf(DayOfWeek.FRIDAY), viewModel.uiState.value.weeklyDaysOfWeek)

        // A night-owl draft's date stays the calendar's; the day it saves from ends at 04:00.
        viewModel.updateDayEndsAtFourAm(true)
        assertEquals(LocalDate.of(2026, 10, 30), viewModel.uiState.value.sinceDate)
        assertEquals(
            LocalDate.of(2026, 10, 29),
            viewModel.uiState.value.toScheduleInput(LocalDateTime.of(2026, 10, 30, 0, 10)).since,
        )
        timeSource.setCurrentMinute(LocalDateTime.of(2026, 10, 30, 4, 0))
        advanceUntilIdle()
        assertEquals(
            LocalDate.of(2026, 10, 30),
            viewModel.uiState.value.toScheduleInput(LocalDateTime.of(2026, 10, 30, 4, 0)).since,
        )
    }

    @Test
    fun savingANowDraftPastTheDaysEndStartsItTodayBeforeTheMinuteTickRuns() = runTest {
        coEvery { medicineRepository.getByUuid(editorMedicine.uuid) } returns editorMedicine
        for (boundary in MedicationDayBoundary.entries) {
            // Opened Thu Oct 29 at 23:20, saved right as Friday's day starts.
            val timeSource = FakeAppTimeSource(LocalDateTime.of(2026, 10, 29, 23, 20))
            val savedSchedule = stubSaveGroup(uuid = null, returns = savedGroupUuid)
            val viewModel = newViewModel(LocalDateTime.of(2026, 10, 29, 23, 20), timeSource)
            advanceUntilIdle()
            viewModel.updateScheduleType(MedicationGroupScheduleType.WEEKLY)
            viewModel.updateDayEndsAtFourAm(boundary.endsAtFourAm)
            viewModel.addCompletedMedicationSlot(
                localId = "slot",
                slot = com.mkx.hrttracker.ui.catalog.MedicineSlotResult(
                    medicineUuid = editorMedicine.uuid,
                    applicationType = MedicationApplicationType.ORAL,
                    doseInstruction = DoseInstruction.TabletFraction(1, 1),
                    count = 1,
                ),
            )
            advanceUntilIdle()
            val dayStart = LocalDate.of(2026, 10, 30).atTime(boundary.startTime)

            // No advanceUntilIdle: the save runs before the collector sees the new minute.
            timeSource.setCurrentMinute(dayStart.plusMinutes(10))
            viewModel.saveGroup()
            advanceUntilIdle()

            assertEquals("$boundary", LocalDate.of(2026, 10, 30), savedSchedule.captured.since)
            assertEquals("$boundary", setOf(DayOfWeek.FRIDAY), savedSchedule.captured.weeklyDaysOfWeek)
            assertEquals("$boundary", dayStart.plusMinutes(10), savedSchedule.captured.startAt)
        }
    }

    private fun stubSaveGroup(
        uuid: UUID?,
        returns: UUID,
    ): CapturingSlot<MedicationGroupScheduleInput> {
        val savedSchedule = slot<MedicationGroupScheduleInput>()
        coEvery {
            medicationGroupRepository.saveGroup(
                uuid = uuid,
                name = any(),
                colorKey = any(),
                schedule = capture(savedSchedule),
                medications = any(),
                notificationsEnabled = any(),
                includePastScheduledSlots = any(),
                now = any(),
            )
        } returns returns
        return savedSchedule
    }

    private fun planRecord(scheduledFor: LocalDateTime): MedicationLogEntry =
        testMedicationLogEntry(
            medicine = editorMedicine,
            sourceGroupUuid = groupUuid,
            appliedAt = scheduledFor.atZone(ZoneId.systemDefault()).toInstant(),
            scheduledFor = scheduledFor,
        )

    private fun nightOwlGroup(
        type: MedicationGroupScheduleType = MedicationGroupScheduleType.DAILY,
        dayBoundary: MedicationDayBoundary = MedicationDayBoundary.FourAm,
        everyDay: Boolean = false,
        since: LocalDate = LocalDate.of(2026, 10, 1),
        times: List<LocalTime> = listOf(LocalTime.of(1, 0)),
        effectiveFrom: LocalDateTime = since.atStartOfDay(),
        interval: Int = 1,
    ): MedicationGroup = MedicationGroup(
        uuid = groupUuid,
        name = "Evening",
        colorKey = MedicationGroupColorKey.ROSE,
        schedule = MedicationGroupSchedule(
            type = type,
            interval = interval,
            since = since,
            weeklyDaysOfWeek = when {
                type != MedicationGroupScheduleType.WEEKLY -> emptySet()
                everyDay -> DayOfWeek.entries.toSet()
                else -> setOf(DayOfWeek.WEDNESDAY)
            },
            times = times,
            dayBoundary = dayBoundary,
            timeSlots = times.mapIndexed { index, time ->
                MedicationGroupScheduleTime(
                    uuid = UUID.nameUUIDFromBytes("slot$index".toByteArray()),
                    time = time,
                    effectiveFrom = effectiveFrom,
                )
            },
        ),
        medications = listOf(
            MedicationGroupMedication(
                uuid = UUID.fromString("0e1f2a3b-0000-4000-8000-000000000003"),
                medicine = editorMedicine,
                applicationType = MedicationApplicationType.ORAL,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
                count = 1,
            )
        ),
        notificationsEnabled = true,
        createdAt = Instant.parse("2026-10-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-10-01T00:00:00Z"),
    )

    private fun newViewModel(
        now: LocalDateTime,
        timeSource: FakeAppTimeSource = FakeAppTimeSource(now),
    ): MedicationGroupEditorViewModel =
        viewModel(now, SavedStateHandle(), timeSource)

    private fun editingViewModel(
        now: LocalDateTime,
        groupId: UUID = groupUuid,
    ): MedicationGroupEditorViewModel =
        viewModel(
            now,
            SavedStateHandle(
                mapOf(MedicationGroupEditorViewModel.GROUP_ID_ARG to groupId.toString())
            ),
        )

    private fun viewModel(
        now: LocalDateTime,
        savedStateHandle: SavedStateHandle,
        timeSource: FakeAppTimeSource = FakeAppTimeSource(now),
    ): MedicationGroupEditorViewModel {
        return MedicationGroupEditorViewModel(
            medicationGroupRepository = medicationGroupRepository,
            medicationLogRepository = medicationLogRepository,
            medicineRepository = medicineRepository,
            settingsRepository = settingsRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            context = context,
            savedStateHandle = savedStateHandle,
            appTimeSource = timeSource,
        )
    }
}

private val editorMedicine: Medicine = testMedicine(
    uuid = UUID.fromString("0e1f2a3b-0000-4000-8000-000000000004"),
    key = MedicationKey.ESTRADIOL,
)
