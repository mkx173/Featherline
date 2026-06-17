package com.mkx.hrttracker.ui.journal

import com.mkx.hrttracker.data.repository.JournalRepository
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.journal.Note
import com.mkx.hrttracker.model.journal.TrackedDate
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.util.FakeAppTimeSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class JournalViewModelTest {
    private val repository: JournalRepository = mockk()
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 6, 16)
    private lateinit var appTimeSource: FakeAppTimeSource

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        appTimeSource = FakeAppTimeSource(
            initialMinute = LocalDateTime.of(2026, 6, 16, 12, 0),
            initialZone = ZoneId.of("Asia/Tokyo"),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_mapsPinnedAnchors_andSplitsNotesAt30Days() = runTest {
        val todayNote = Note(id = "today-note", date = today, text = "today")
        every { repository.observeTrackedDates() } returns flowOf(
            listOf(
                TrackedDate(
                    id = "estradiol",
                    name = "On estradiol",
                    icon = AnchorIcon.MEDICATION,
                    date = LocalDate.of(2024, 4, 1),
                    palette = MedicationGroupColorKey.ROSE,
                    pinnedOrder = 0,
                )
            )
        )
        every { repository.observePinnedTrackedDates() } returns flowOf(
            listOf(
                TrackedDate(
                    id = "estradiol",
                    name = "On estradiol",
                    icon = AnchorIcon.MEDICATION,
                    date = LocalDate.of(2024, 4, 1),
                    palette = MedicationGroupColorKey.ROSE,
                    pinnedOrder = 0,
                )
            )
        )
        every { repository.observeNotesOnOrAfter(today.minusDays(29)) } returns flowOf(
            listOf(todayNote)
        )
        every { repository.observeNoteForDate(today) } returns flowOf(todayNote)
        every { repository.observeNotesCountBefore(today.minusDays(29)) } returns flowOf(1)

        val viewModel = JournalViewModel(repository, appTimeSource)
        assertTrue(viewModel.uiState.value.isLoading)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        val anchor = state.pinnedAnchors.single()
        assertFalse(state.isLoading)
        assertEquals(today, state.today)
        assertEquals("estradiol", anchor.id)
        assertEquals("On estradiol", anchor.name)
        assertEquals(AnchorIcon.MEDICATION, anchor.icon)
        assertEquals(MedicationGroupColorKey.ROSE, anchor.palette)
        assertEquals(LocalDate.of(2024, 4, 1), anchor.date)
        assertEquals(806L, anchor.dayMagnitude)
        assertFalse(anchor.isFuture)
        assertEquals(listOf("today"), state.recentNotes.map { it.text })
        assertEquals(1, state.olderNotesCount)
        assertEquals("today", state.todayNote?.text)
        assertTrue(state.hasAnchors)
    }

    @Test
    fun uiState_tracksUnpinnedDatesForJournalEmptyState() = runTest {
        every { repository.observeTrackedDates() } returns flowOf(
            listOf(
                TrackedDate(
                    id = "unpinned",
                    name = "Unpinned date",
                    icon = AnchorIcon.EVENT,
                    date = LocalDate.of(2026, 6, 1),
                    palette = null,
                    pinnedOrder = null,
                )
            )
        )
        every { repository.observePinnedTrackedDates() } returns flowOf(emptyList())
        every { repository.observeNotesOnOrAfter(today.minusDays(29)) } returns flowOf(emptyList())
        every { repository.observeNoteForDate(today) } returns flowOf(null)
        every { repository.observeNotesCountBefore(today.minusDays(29)) } returns flowOf(0)

        val viewModel = JournalViewModel(repository, appTimeSource)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.hasTrackedDates)
        assertFalse(state.hasAnchors)
    }

    @Test
    fun uiState_resubscribesDateKeyedFlowsAfterLocalMidnight() = runTest {
        val june16 = LocalDate.of(2026, 6, 16)
        val june17 = LocalDate.of(2026, 6, 17)
        val june16WindowStart = LocalDate.of(2026, 5, 18)
        val june17WindowStart = LocalDate.of(2026, 5, 19)
        val pinned = MutableStateFlow(emptyList<TrackedDate>())
        val june16Note = Note(id = "june-16", date = june16, text = "june 16")
        val june17Note = Note(id = "june-17", date = june17, text = "june 17")
        every { repository.observeTrackedDates() } returns flowOf(emptyList())
        every { repository.observePinnedTrackedDates() } returns pinned
        every { repository.observeNotesOnOrAfter(june16WindowStart) } returns flowOf(
            listOf(june16Note)
        )
        every { repository.observeNoteForDate(june16) } returns flowOf(june16Note)
        every { repository.observeNotesCountBefore(june16WindowStart) } returns flowOf(2)
        every { repository.observeNotesOnOrAfter(june17WindowStart) } returns flowOf(
            listOf(june17Note)
        )
        every { repository.observeNoteForDate(june17) } returns flowOf(june17Note)
        every { repository.observeNotesCountBefore(june17WindowStart) } returns flowOf(3)
        val viewModel = JournalViewModel(repository, appTimeSource)
        advanceUntilIdle()

        assertEquals(june16, viewModel.uiState.value.today)
        assertEquals("june 16", viewModel.uiState.value.todayNote?.text)

        appTimeSource.setCurrentMinute(LocalDateTime.of(2026, 6, 17, 0, 0))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(june17, state.today)
        assertEquals(listOf("june 17"), state.recentNotes.map { it.text })
        assertEquals("june 17", state.todayNote?.text)
        assertEquals(3, state.olderNotesCount)
        verify(exactly = 1) { repository.observeNoteForDate(june17) }
        verify(exactly = 1) { repository.observeNotesOnOrAfter(june17WindowStart) }
        verify(exactly = 1) { repository.observeNotesCountBefore(june17WindowStart) }
    }

    @Test
    fun saveTodayNote_usesCurrentLocalDateAfterRollover() = runTest {
        stubEmptyObservers()
        val tomorrow = LocalDate.of(2026, 6, 17)
        coEvery { repository.saveNoteForDate(tomorrow, "updated") } returns Unit
        val viewModel = JournalViewModel(repository, appTimeSource)
        appTimeSource.setCurrentMinute(LocalDateTime.of(2026, 6, 17, 0, 0))

        viewModel.saveTodayNote("updated")
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.saveNoteForDate(tomorrow, "updated") }
    }

    @Test
    fun saveNote_usesExplicitDate() = runTest {
        val noteDate = LocalDate.of(2026, 6, 1)
        stubEmptyObservers()
        coEvery { repository.saveNoteForDate(noteDate, "older note") } returns Unit
        val viewModel = JournalViewModel(repository, appTimeSource)

        viewModel.saveNote(noteDate, "older note")
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.saveNoteForDate(noteDate, "older note") }
    }

    @Test
    fun deleteTodayNote_usesCurrentLocalDateAfterRollover() = runTest {
        stubEmptyObservers()
        val tomorrow = LocalDate.of(2026, 6, 17)
        coEvery { repository.deleteNoteForDate(tomorrow) } returns Unit
        val viewModel = JournalViewModel(repository, appTimeSource)
        appTimeSource.setCurrentMinute(LocalDateTime.of(2026, 6, 17, 0, 0))

        viewModel.deleteTodayNote()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.deleteNoteForDate(tomorrow) }
    }

    @Test
    fun deleteNote_usesExplicitDate() = runTest {
        val noteDate = LocalDate.of(2026, 6, 1)
        stubEmptyObservers()
        coEvery { repository.deleteNoteForDate(noteDate) } returns Unit
        val viewModel = JournalViewModel(repository, appTimeSource)

        viewModel.deleteNote(noteDate)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.deleteNoteForDate(noteDate) }
    }

    private fun stubEmptyObservers() {
        every { repository.observeTrackedDates() } returns flowOf(emptyList())
        every { repository.observePinnedTrackedDates() } returns flowOf(emptyList())
        every { repository.observeNotesOnOrAfter(today.minusDays(29)) } returns flowOf(emptyList())
        every { repository.observeNoteForDate(today) } returns flowOf(null)
        every { repository.observeNotesCountBefore(today.minusDays(29)) } returns flowOf(0)
        every { repository.observeNotesOnOrAfter(today.plusDays(1).minusDays(29)) } returns flowOf(
            emptyList()
        )
        every { repository.observeNoteForDate(today.plusDays(1)) } returns flowOf(null)
        every { repository.observeNotesCountBefore(today.plusDays(1).minusDays(29)) } returns flowOf(0)
    }
}
