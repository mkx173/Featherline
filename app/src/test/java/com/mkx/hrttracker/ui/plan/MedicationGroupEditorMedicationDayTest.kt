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
    fun turningNightOwlOnMakesAFourAmPlanAndItsDefaultStartFollows() = runTest {
        val now = LocalDateTime.of(2026, 10, 19, 1, 32)
        val viewModel = newViewModel(now = now)
        advanceUntilIdle()

        viewModel.updateDayEndsAtFourAm(true)
        viewModel.updateScheduleType(MedicationGroupScheduleType.WEEKLY)

        val state = viewModel.uiState.value
        assertEquals(MedicationDayBoundary.FourAm, state.medicationDayBoundary)
        assertEquals(LocalDate.of(2026, 10, 18), state.sinceDate)
        assertTrue(state.startsNow)
        assertEquals(setOf(DayOfWeek.SUNDAY), state.weeklyDaysOfWeek)
        assertEquals(LocalTime.of(2, 0), state.weeklyTime)
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 10, 19, 2, 0),
                LocalDateTime.of(2026, 10, 26, 2, 0),
            ),
            buildMedicationGroupEditorUpcomingOccurrences(uiState = state, start = now),
        )
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
        assertEquals(LocalDate.of(2026, 10, 28), state.sinceDate)
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
                replacesGroupUuid = any(),
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
    fun archiveAndRecreateKeepsTheEndingAndStartsOnThePlansOwnToday() = runTest {
        // 02:00 on Oct 29 is still Oct 28 for the night-owl plan.
        val now = LocalDateTime.of(2026, 10, 29, 2, 0)
        for (type in MedicationGroupScheduleType.entries) {
            val group = nightOwlGroup(type = type)
            every { medicationGroupRepository.getCachedGroup(groupUuid) } returns group
            every { medicationGroupRepository.observeGroups() } returns flowOf(listOf(group))
            every { medicationLogRepository.observeEntries() } returns
                    flowOf(listOf(planRecord(LocalDateTime.of(2026, 10, 28, 1, 0))))
            coEvery { medicationGroupRepository.getGroup(savedGroupUuid) } returns
                    group.copy(uuid = savedGroupUuid, recreatedFromGroupUuid = groupUuid)
            coEvery {
                medicationGroupRepository.archiveGroup(
                    uuid = groupUuid,
                    archivedThroughDate = any(),
                    now = any(),
                )
            } returns Unit
            val savedSchedule = stubSaveGroup(uuid = null, returns = savedGroupUuid)
            val viewModel = editingViewModel(now = now)
            advanceUntilIdle()
            assertEquals(
                LocalDate.of(2026, 10, 28),
                viewModel.uiState.value.archiveDateWindow.maxDate,
            )

            viewModel.archiveAndRecreateGroup()
            advanceUntilIdle()

            assertEquals(type, savedSchedule.captured.type)
            assertEquals(MedicationDayBoundary.FourAm, savedSchedule.captured.dayBoundary)
            assertEquals(LocalDate.of(2026, 10, 28), savedSchedule.captured.since)
            // The recreated plan is a new plan, so it takes a new ending.
            assertEquals(savedGroupUuid.toString(), viewModel.uiState.value.editingGroupId)
            assertTrue(viewModel.uiState.value.canEditDayBoundary)
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
        assertEquals(LocalDate.of(2026, 10, 28), viewModel.uiState.value.sinceDate)
        assertTrue(viewModel.uiState.value.canEditDayBoundary)
        viewModel.saveGroup()
        advanceUntilIdle()
        assertEquals(MedicationDayBoundary.FourAm, savedSchedule.captured.dayBoundary)
    }

    @Test
    fun archiveWindowTodayFollowsAMidnightPlansOwnEnding() = runTest {
        val group = nightOwlGroup(dayBoundary = MedicationDayBoundary.Midnight)
        every { medicationGroupRepository.getCachedGroup(groupUuid) } returns group
        every { medicationLogRepository.observeEntries() } returns
                flowOf(listOf(planRecord(LocalDateTime.of(2026, 10, 29, 1, 0))))

        val viewModel = editingViewModel(now = LocalDateTime.of(2026, 10, 29, 2, 0))
        advanceUntilIdle()

        // The midnight plan's 01:00 record is on Oct 29, so today must be too, or
        // archiving through today would land before its record.
        val window = viewModel.uiState.value.archiveDateWindow
        assertEquals(LocalDate.of(2026, 10, 29), window.minDate)
        assertEquals(LocalDate.of(2026, 10, 29), window.maxDate)
        assertTrue(window.isSelectable)
    }

    @Test
    fun recreatedPlanKeepsItsSeamWithTheArchivedPlanWhenItsEndingChanges() = runTest {
        // Archived through Oct 29 at 14:00; the recreated plan starts at the cutoff.
        val now = LocalDateTime.of(2026, 10, 29, 14, 0)
        for (type in MedicationGroupScheduleType.entries) {
            for (from in MedicationDayBoundary.entries) {
                val to = MedicationDayBoundary.entries.single { it != from }
                val cutoff = LocalDate.of(2026, 10, 30).atTime(from.startTime)
                val archived = nightOwlGroup(type = type, dayBoundary = from, everyDay = true)
                    .copy(
                        archivedAt = cutoff.atZone(ZoneId.systemDefault()).toInstant(),
                        archivedAtLocal = cutoff,
                    )
                val recreated = nightOwlGroup(
                    type = type,
                    dayBoundary = from,
                    everyDay = true,
                    since = LocalDate.of(2026, 10, 30),
                    effectiveFrom = cutoff,
                ).copy(uuid = savedGroupUuid, recreatedFromGroupUuid = groupUuid)
                every { medicationGroupRepository.getCachedGroup(savedGroupUuid) } returns recreated
                every { medicationGroupRepository.observeGroups() } returns
                        flowOf(listOf(archived, recreated))
                val savedSchedule = stubSaveGroup(uuid = savedGroupUuid, returns = savedGroupUuid)
                val viewModel = editingViewModel(now = now, groupId = savedGroupUuid)
                advanceUntilIdle()
                val opened = viewModel.uiState.value
                // Turning it on and back off restores the draft exactly.
                viewModel.updateDayEndsAtFourAm(to.endsAtFourAm)
                viewModel.updateDayEndsAtFourAm(from.endsAtFourAm)
                assertEquals(opened, viewModel.uiState.value)

                viewModel.updateDayEndsAtFourAm(to.endsAtFourAm)
                viewModel.saveGroup()
                advanceUntilIdle()

                val input = savedSchedule.captured
                assertEquals(to, input.dayBoundary)
                assertEquals(cutoff, input.startAt)
                // A recordless recreated plan saved before its start keeps startAt as
                // each slot's effectiveFrom.
                val saved = recreated.copy(
                    schedule = recreated.schedule.copy(
                        since = input.since,
                        dayBoundary = input.dayBoundary,
                        timeSlots = recreated.schedule.timeSlots.map {
                            it.copy(effectiveFrom = checkNotNull(input.startAt))
                        },
                    ),
                )
                val start = LocalDate.of(2026, 10, 28)
                val end = LocalDate.of(2026, 11, 1)
                val owners = (archived.occurrencesBetweenInPlanWindow(start, end) +
                        saved.occurrencesBetweenInPlanWindow(start, end))
                    .groupingBy { it.scheduledFor }
                    .eachCount()
                for (day in 29..31) {
                    val slot = LocalDateTime.of(2026, 10, day, 1, 0)
                    assertEquals("$type $from -> $to at $slot", 1, owners[slot])
                }

            }
        }
    }

    @Test
    fun aPickedStartTimeKeepsItsMomentAcrossNightOwl() {
        val now = LocalDateTime.of(2026, 10, 20, 12, 0)
        val picked = MedicationGroupEditorUiState(
            sinceDate = LocalDate.of(2026, 10, 29),
            startTime = LocalTime.of(2, 0),
            scheduleType = MedicationGroupScheduleType.WEEKLY,
            weeklyDaysOfWeek = setOf(DayOfWeek.THURSDAY),
        )
        val start = LocalDateTime.of(2026, 10, 29, 2, 0)
        assertEquals(start, picked.scheduleStartAt(now))

        val nightOwl = picked.withDayBoundary(MedicationDayBoundary.FourAm, now)

        assertEquals(LocalDate.of(2026, 10, 28), nightOwl.sinceDate)
        assertEquals(start, nightOwl.scheduleStartAt(now))
        assertEquals(setOf(DayOfWeek.WEDNESDAY), nightOwl.weeklyDaysOfWeek)
        assertEquals(picked, nightOwl.withDayBoundary(MedicationDayBoundary.Midnight, now))
        // Weekdays the user picked stay.
        assertEquals(
            setOf(DayOfWeek.THURSDAY),
            picked.copy(hasUserCustomizedWeeklyDays = true)
                .withDayBoundary(MedicationDayBoundary.FourAm, now)
                .weeklyDaysOfWeek,
        )
    }

    @Test
    fun aRecordArrivingAfterTheToggleRestoresTheSavedEnding() = runTest {
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
        assertEquals(opened.sinceDate.minusDays(1), viewModel.uiState.value.sinceDate)

        entries.value = listOf(planRecord(LocalDateTime.of(2026, 10, 19, 1, 0)))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.canEditDayBoundary)
        assertEquals(MedicationDayBoundary.Midnight, state.medicationDayBoundary)
        assertEquals(opened.sinceDate, state.sinceDate)
        assertEquals(LocalTime.of(2, 0), state.startTime)
        viewModel.saveGroup()
        advanceUntilIdle()
        assertEquals(MedicationDayBoundary.Midnight, savedSchedule.captured.dayBoundary)
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
        coEvery { medicationGroupRepository.getGroup(savedGroupUuid) } returns
                group.copy(uuid = savedGroupUuid, recreatedFromGroupUuid = groupUuid)
        coEvery {
            medicationGroupRepository.archiveGroup(
                uuid = groupUuid,
                archivedThroughDate = any(),
                now = any(),
            )
        } returns Unit
        val savedSchedule = stubSaveGroup(uuid = null, returns = savedGroupUuid)
        val viewModel = editingViewModel(now = LocalDateTime.of(2026, 10, 29, 2, 0))
        advanceUntilIdle()

        viewModel.updateDayEndsAtFourAm(true)
        advanceUntilIdle()

        assertEquals(MedicationDayBoundary.FourAm, viewModel.uiState.value.medicationDayBoundary)
        assertEquals(LocalDate.of(2026, 10, 29), viewModel.uiState.value.archiveDateWindow.maxDate)
        viewModel.archiveAndRecreateGroup()
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 10, 29), savedSchedule.captured.since)
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
        assertEquals(LocalDate.of(2026, 10, 1), nightOwl.sinceDate)
        assertEquals(upcoming, buildMedicationGroupEditorUpcomingOccurrences(nightOwl, now))
        viewModel.updateDayEndsAtFourAm(false)
        assertEquals(opened, viewModel.uiState.value)
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

        assertEquals(LocalDate.of(2026, 10, 20), viewModel.uiState.value.sinceDate)
        assertEquals(start, viewModel.uiState.value.scheduleStartAt(now))
        viewModel.updateDayEndsAtFourAm(false)
        assertEquals(opened, viewModel.uiState.value)
        assertEquals(start, viewModel.uiState.value.scheduleStartAt(now))
    }

    @Test
    fun aRecreatedPlansSavedStartPastItsDatesOwnDayKeepsItsDateAndRhythm() = runTest {
        // Archived through Oct 27 and recreated on Oct 29 at 14:00: since Oct 28,
        // saved start Oct 29 14:00, every 2 days at 01:00.
        val now = LocalDateTime.of(2026, 10, 29, 15, 0)
        every { medicationGroupRepository.getCachedGroup(groupUuid) } returns nightOwlGroup(
            dayBoundary = MedicationDayBoundary.Midnight,
            since = LocalDate.of(2026, 10, 28),
            effectiveFrom = LocalDateTime.of(2026, 10, 29, 14, 0),
            interval = 2,
        ).copy(recreatedFromGroupUuid = savedGroupUuid)
        val viewModel = editingViewModel(now = now)
        advanceUntilIdle()
        val opened = viewModel.uiState.value
        val upcoming = buildMedicationGroupEditorUpcomingOccurrences(opened, now)
        assertEquals(LocalDateTime.of(2026, 10, 30, 1, 0), upcoming.first())

        viewModel.updateDayEndsAtFourAm(true)

        val nightOwl = viewModel.uiState.value
        assertEquals(LocalDate.of(2026, 10, 28), nightOwl.sinceDate)
        viewModel.updateDayEndsAtFourAm(false)
        assertEquals(opened, viewModel.uiState.value)
        assertEquals(upcoming, buildMedicationGroupEditorUpcomingOccurrences(viewModel.uiState.value, now))
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

        // A night-owl draft's day only ends at 04:00.
        viewModel.updateDayEndsAtFourAm(true)
        assertEquals(LocalDate.of(2026, 10, 29), viewModel.uiState.value.sinceDate)
        timeSource.setCurrentMinute(LocalDateTime.of(2026, 10, 30, 4, 0))
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 10, 30), viewModel.uiState.value.sinceDate)
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
                replacesGroupUuid = any(),
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
