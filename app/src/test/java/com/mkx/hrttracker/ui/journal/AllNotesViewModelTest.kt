package com.mkx.hrttracker.ui.journal

import com.mkx.hrttracker.data.repository.JournalRepository
import com.mkx.hrttracker.model.journal.Note
import com.mkx.hrttracker.util.FakeAppTimeSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class AllNotesViewModelTest {
    private val repository: JournalRepository = mockk()
    private val dispatcher = StandardTestDispatcher()
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
    fun uiState_groupsNotesByMonthNewestFirst() = runTest {
        every { repository.observeNotesOnOrAfter(LocalDate.MIN) } returns flowOf(
            listOf(
                note(id = "may-2", date = LocalDate.of(2026, 5, 2), text = "May 2"),
                note(id = "june-1", date = LocalDate.of(2026, 6, 1), text = "June 1"),
                note(id = "may-20", date = LocalDate.of(2026, 5, 20), text = "May 20"),
                note(id = "june-15", date = LocalDate.of(2026, 6, 15), text = "June 15"),
            )
        )

        val viewModel = AllNotesViewModel(repository, appTimeSource)
        assertTrue(viewModel.uiState.value.isLoading)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(LocalDate.of(2026, 6, 16), state.today)
        assertEquals(
            listOf(YearMonth.of(2026, 6), YearMonth.of(2026, 5)),
            state.monthGroups.map { it.month },
        )
        assertEquals(listOf("June 15", "June 1"), state.monthGroups[0].notes.map { it.text })
        assertEquals(listOf("May 20", "May 2"), state.monthGroups[1].notes.map { it.text })
    }

    @Test
    fun deleteNote_usesExplicitDate() = runTest {
        val noteDate = LocalDate.of(2026, 6, 1)
        every { repository.observeNotesOnOrAfter(LocalDate.MIN) } returns flowOf(emptyList())
        coEvery { repository.deleteNoteForDate(noteDate) } returns Unit
        val viewModel = AllNotesViewModel(repository, appTimeSource)

        viewModel.deleteNote(noteDate)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.deleteNoteForDate(noteDate) }
    }

    @Test
    fun saveNote_writeFailure_setsSaveErrorAndBumpsToken() = runTest {
        val noteDate = LocalDate.of(2026, 6, 1)
        every { repository.observeNotesOnOrAfter(LocalDate.MIN) } returns flowOf(emptyList())
        coEvery { repository.saveNoteForDate(noteDate, "boom") } throws IOException("disk full")
        val viewModel = AllNotesViewModel(repository, appTimeSource)
        advanceUntilIdle()
        val tokenBefore = viewModel.uiState.value.noteSaveFailureToken

        viewModel.saveNote(noteDate, "boom")
        advanceUntilIdle()

        assertEquals(JournalNoteMutation.SAVE, viewModel.uiState.value.noteMutationError)
        assertEquals(tokenBefore + 1, viewModel.uiState.value.noteSaveFailureToken)

        viewModel.consumeNoteMutationError()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.noteMutationError)
    }

    @Test
    fun deleteNote_writeFailure_setsDeleteError() = runTest {
        val noteDate = LocalDate.of(2026, 6, 1)
        every { repository.observeNotesOnOrAfter(LocalDate.MIN) } returns flowOf(emptyList())
        coEvery { repository.deleteNoteForDate(noteDate) } throws IOException("io")
        val viewModel = AllNotesViewModel(repository, appTimeSource)
        advanceUntilIdle()

        viewModel.deleteNote(noteDate)
        advanceUntilIdle()

        assertEquals(JournalNoteMutation.DELETE, viewModel.uiState.value.noteMutationError)
    }

    private fun note(
        id: String,
        date: LocalDate,
        text: String,
    ): Note = Note(
        id = id,
        date = date,
        text = text,
    )
}
