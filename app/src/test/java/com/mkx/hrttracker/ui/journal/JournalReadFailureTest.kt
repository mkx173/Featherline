package com.mkx.hrttracker.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.JournalDao
import com.mkx.hrttracker.data.local.NoteEntity
import com.mkx.hrttracker.data.repository.JournalRepository
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import com.mkx.hrttracker.util.FakeAppTimeSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId

// The real JournalRepository under the Journal and All Notes ViewModels, over a DAO whose read
// fails and then works, as a Room flow does when it runs its query again. A failed read must never
// show as "no note" (Today's Save would replace the real note with what was typed into the emptied
// editor), and the ViewModels, which share the flows Eagerly for their own lives, must read again
// afterwards.
// Each test runs in readTest, which cancels its ViewModels' scopes at the end, passed or failed: a
// test that ends with a read still failing would otherwise leave runTest's last advanceUntilIdle
// following the retry forever.
@OptIn(ExperimentalCoroutinesApi::class)
class JournalReadFailureTest {
    @After
    fun tearDown() = Dispatchers.resetMain()

    private val warnings = mutableListOf<String>()
    private val logger = object : AppDiagnosticsLogger(enabled = true) {
        override fun warning(tag: String, message: String, throwable: Throwable?) {
            warnings += message
        }
    }

    private fun note(text: String) = NoteEntity(
        uuid = "june-16",
        dateIso = "2026-06-16",
        text = text,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )

    // Each value stands for one run of the query; the runs in [failing] throw.
    private fun <T> runs(runs: MutableStateFlow<Int>, failing: Set<Int> = setOf(0), value: (Int) -> T): Flow<T> =
        runs.map { run ->
            if (run in failing) throw IOException("disk I/O error")
            value(run)
        }

    private fun TestScope.repository(
        dao: JournalDao,
        databaseFlow: MutableStateFlow<HrtTrackerDatabase?> = MutableStateFlow(database(dao)),
    ): JournalRepository {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val databaseHolder = mockk<DatabaseHolder> {
            every { this@mockk.databaseFlow } returns databaseFlow
            every { openFailed } returns MutableStateFlow(false)
            every { warmUp() } returns Unit
        }
        return JournalRepository(
            databaseHolder = databaseHolder,
            clock = Clock.systemUTC(),
            homeSnapshotRepository = mockk(relaxed = true),
            anchorSnapshotStore = mockk(relaxed = true),
            appScope = CoroutineScope(dispatcher),
            diagnosticsLogger = logger,
        )
    }

    private fun database(dao: JournalDao) = mockk<HrtTrackerDatabase> { every { journalDao() } returns dao }

    private fun dao(noteForDate: Flow<NoteEntity?>, notes: Flow<List<NoteEntity>> = flowOf(listOf(note("Morning")))) =
        mockk<JournalDao> {
            every { observeTrackedDates() } returns flowOf(emptyList())
            every { observePinnedTrackedDates() } returns flowOf(emptyList())
            every { observeNotes() } returns flowOf(listOf(note("Morning")))
            every { observeNotesOnOrAfter(any()) } returns notes
            every { observeNotesCountBefore(any()) } returns flowOf(0)
            every { observeNoteForDate("2026-06-16") } returns noteForDate
        }

    private fun TestScope.advanceRetry() {
        advanceTimeBy(5_001)
        runCurrent()
    }

    private val viewModels = mutableListOf<ViewModel>()
    private fun <T : ViewModel> T.closedAtTheEnd(): T = also { viewModels += it }

    private fun readTest(body: suspend TestScope.() -> Unit) = runTest {
        try {
            body()
        } finally {
            viewModels.forEach { it.viewModelScope.cancel() }
        }
    }

    private val timeSource
        get() = FakeAppTimeSource(LocalDateTime.of(2026, 6, 16, 12, 0), ZoneId.of("Asia/Tokyo"))

    @Test
    fun aFailedReadOfTodaysNote_neverShowsNoNote_andReadsAgain() = readTest {
        val queryRuns = MutableStateFlow(0)
        val viewModel = JournalViewModel(
            repository(dao(runs(queryRuns) { note("Morning, edited") })),
            timeSource,
        ).closedAtTheEnd()

        // Today still shows the note it had (here the warm cache's seed), not an empty composer.
        assertEquals("Morning", viewModel.uiState.value.todayNote?.text)

        // The error passes and the note changes: the flow reads again and shows it.
        queryRuns.value = 1
        advanceRetry()
        assertEquals("Morning, edited", viewModel.uiState.value.todayNote?.text)
    }

    @Test
    fun aReadThatFailsAfterAGoodOne_keepsTheNote_andReadsAgain() = readTest {
        // Run 1 is Room's query again after a write, failing.
        val queryRuns = MutableStateFlow(0)
        val viewModel = JournalViewModel(
            repository(dao(runs(queryRuns, failing = setOf(1)) { run -> note(if (run == 0) "Morning" else "Evening") })),
            timeSource,
        ).closedAtTheEnd()
        assertEquals("Morning", viewModel.uiState.value.todayNote?.text)

        queryRuns.value = 1
        assertEquals("Morning", viewModel.uiState.value.todayNote?.text)

        queryRuns.value = 2
        advanceRetry()
        assertEquals("Evening", viewModel.uiState.value.todayNote?.text)
    }

    @Test
    fun aViewModelMadeBeforeTheDatabase_keepsLoadingThroughAFailedRead() = readTest {
        val queryRuns = MutableStateFlow(0)
        val dao = dao(runs(queryRuns) { note("Morning") })
        val databaseFlow = MutableStateFlow<HrtTrackerDatabase?>(null)
        val viewModel = JournalViewModel(repository(dao, databaseFlow), timeSource).closedAtTheEnd()
        assertTrue(viewModel.uiState.value.isLoading)

        // The database is built, and today's note fails to read.
        databaseFlow.value = database(dao)
        runCurrent()
        assertTrue(viewModel.uiState.value.isLoading)

        queryRuns.value = 1
        advanceRetry()
        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals("Morning", viewModel.uiState.value.todayNote?.text)
    }

    @Test
    fun aFailedReadOfTheNotes_neverShowsAnEmptyList_andReadsAgain() = readTest {
        val queryRuns = MutableStateFlow(0)
        val repository = repository(
            dao(
                noteForDate = flowOf(note("Morning")),
                notes = runs(queryRuns) { listOf(note("Morning, edited")) },
            )
        )
        val journal = JournalViewModel(repository, timeSource).closedAtTheEnd()
        val allNotes = AllNotesViewModel(repository, timeSource).closedAtTheEnd()

        // The timeline keeps the seed's notes, and All Notes keeps loading rather than show none.
        assertEquals(listOf("Morning"), journal.uiState.value.recentNotes.map { it.text })
        assertTrue(allNotes.uiState.value.isLoading)

        queryRuns.value = 1
        advanceRetry()
        assertEquals(listOf("Morning, edited"), journal.uiState.value.recentNotes.map { it.text })
        assertEquals(
            listOf("Morning, edited"),
            allNotes.uiState.value.monthGroups.flatMap { group -> group.notes.map { it.text } },
        )
    }

    @Test
    fun aGoodReadEndsTheRun_soTheNextFailureWaitsFiveSecondsAndLogsAgain() = readTest {
        var reads = 0
        // Runs 0 and 1 fail, 2 works, 3 fails (Room's re-query after a write), 4 works.
        val queryRuns = MutableStateFlow(0)
        val viewModel = JournalViewModel(
            repository(
                dao(
                    queryRuns.map { run ->
                        reads++
                        if (run in setOf(0, 1, 3)) throw IOException("disk I/O error")
                        note(if (run == 2) "Morning" else "Evening")
                    }
                )
            ),
            timeSource,
        ).closedAtTheEnd()
        // The first run of failures: read at 0 s, at 5 s, then a 10 s wait.
        advanceRetry()
        queryRuns.value = 2
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals("Morning", viewModel.uiState.value.todayNote?.text)
        assertEquals(1, warnings.size)

        // A new run: it starts over at 5 s and is logged again.
        queryRuns.value = 3
        queryRuns.value = 4
        val before = reads
        advanceRetry()
        assertEquals(before + 1, reads)
        assertEquals("Evening", viewModel.uiState.value.todayNote?.text)
        assertEquals(2, warnings.size)
    }

    @Test
    fun aReadThatKeepsFailing_backsOffToAMinute_andLogsOnce() = readTest {
        var reads = 0
        val viewModel = JournalViewModel(
            repository(dao(flow { reads++; throw IOException("file is not a database") })),
            timeSource,
        ).closedAtTheEnd()

        // The read is still failing when the test ends: readTest's cleanup is what lets it finish.
        advanceTimeBy(60 * 60_000L)
        runCurrent()

        // 1 + 4 reads backing off over the first 75 s, then one a minute: 63 in the hour.
        assertEquals(63, reads)
        assertEquals(1, warnings.size)
        assertEquals("Morning", viewModel.uiState.value.todayNote?.text)
    }
}
