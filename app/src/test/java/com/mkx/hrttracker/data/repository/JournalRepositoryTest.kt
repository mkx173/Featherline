package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.JournalDao
import com.mkx.hrttracker.data.local.TrackedDateEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock

class JournalRepositoryTest {

    // Intent: at cold start the database may simply not be open yet, and every
    // observe*/cache read reports that not-loaded window as an empty journal — which is
    // what wrongly disabled pinned anchor shortcuts and blanked anchor widgets.
    // awaitTrackedDates must instead force the open and wait for real rows.
    @Test
    fun `awaitTrackedDates opens the database and waits for rows instead of reporting not-loaded as empty`() =
        runTest {
            val databaseState = MutableStateFlow<HrtTrackerDatabase?>(null)
            val dao = mockk<JournalDao> {
                every { observeTrackedDates() } returns flowOf(listOf(trackedDate("anchor-1")))
                every { observePinnedTrackedDates() } returns flowOf(emptyList())
                every { observeNotes() } returns flowOf(emptyList())
            }
            val database = mockk<HrtTrackerDatabase> {
                every { journalDao() } returns dao
            }
            val databaseHolder = mockk<DatabaseHolder> {
                every { databaseFlow } returns databaseState
                every { openFailed } returns MutableStateFlow(false)
                // warmUp, not get(): the open must run on the holder's own scope so the
                // caller's await stays cancellable (a blocking get() defeats the bounded
                // helpers' timeouts).
                every { warmUp() } answers {
                    databaseState.value = database
                }
            }
            val repository = repository(databaseHolder, mockk(relaxed = true))

            val result = repository.awaitTrackedDates()

            assertEquals(listOf("anchor-1"), result.map { it.id })
        }

    // Intent: a terminal open failure must fail the await (the bounded helpers map it to
    // null/snapshot) instead of waiting forever on a cache that will never load.
    @Test
    fun `awaitTrackedDates fails instead of hanging when the open terminally fails`() =
        runTest {
            val databaseHolder = mockk<DatabaseHolder> {
                every { databaseFlow } returns MutableStateFlow<HrtTrackerDatabase?>(null)
                every { openFailed } returns MutableStateFlow(true)
                every { warmUp() } returns Unit
            }
            val repository = repository(databaseHolder, mockk(relaxed = true))

            val result = runCatching { repository.awaitTrackedDates() }

            assertTrue(result.isFailure)
        }

    // Intent: at cold start the seeded flow must render the persisted snapshot — real
    // last-known anchors — instead of waiting for the SQLCipher open or, worse, emitting
    // the fake empty list the null-database window produces.
    @Test
    fun `seeded flow emits the persisted snapshot while the database is not loaded`() =
        runTest {
            val databaseState = MutableStateFlow<HrtTrackerDatabase?>(null)
            val databaseHolder = mockk<DatabaseHolder> {
                every { databaseFlow } returns databaseState
                every { openFailed } returns MutableStateFlow(false)
                // The no-seed branch fires a warm-up retry so a stale openFailed from an
                // earlier failed attempt can't fake an empty journal; the open itself is
                // simulated by databaseState above.
                every { warmUp() } returns Unit
            }
            val snapshotStore = mockk<AnchorSnapshotStore>(relaxed = true) {
                coEvery { read() } returns listOf(trackedDate("snap-1").toModel())
            }
            val repository = repository(databaseHolder, snapshotStore)

            val first = repository.observeTrackedDatesWithSnapshotSeed().first()

            assertEquals(listOf("snap-1"), first.map { it.id })
        }

    // Intent: once live data replaces the seed, it must be the database's rows — the
    // snapshot is only ever a stopgap frame, never allowed to shadow the loaded journal.
    @Test
    fun `seeded flow switches to live rows once the database loads`() =
        runTest {
            val databaseState = MutableStateFlow<HrtTrackerDatabase?>(null)
            val dao = mockk<JournalDao> {
                every { observeTrackedDates() } returns flowOf(listOf(trackedDate("live-1")))
                every { observePinnedTrackedDates() } returns flowOf(emptyList())
                every { observeNotes() } returns flowOf(emptyList())
            }
            val database = mockk<HrtTrackerDatabase> { every { journalDao() } returns dao }
            val databaseHolder = mockk<DatabaseHolder> {
                every { databaseFlow } returns databaseState
                every { openFailed } returns MutableStateFlow(false)
                // The no-seed branch fires a warm-up retry so a stale openFailed from an
                // earlier failed attempt can't fake an empty journal; the open itself is
                // simulated by databaseState above.
                every { warmUp() } returns Unit
            }
            val snapshotStore = mockk<AnchorSnapshotStore>(relaxed = true) {
                coEvery { read() } returns listOf(trackedDate("snap-1").toModel())
            }
            val repository = repository(databaseHolder, snapshotStore)

            val emissions = mutableListOf<List<String>>()
            val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                repository.observeTrackedDatesWithSnapshotSeed().collect { dates ->
                    emissions.add(dates.map { it.id })
                }
            }
            advanceUntilIdle()
            databaseState.value = database
            advanceUntilIdle()
            job.cancel()

            assertEquals(listOf(listOf("snap-1"), listOf("live-1")), emissions)
        }

    // Intent: a warm process must not pay the snapshot-file read or flash week-old data —
    // when the in-memory cache is already loaded, the seed step is skipped entirely.
    @Test
    fun `seeded flow skips the snapshot when the cache is already loaded`() =
        runTest {
            val databaseState = MutableStateFlow<HrtTrackerDatabase?>(null)
            val dao = mockk<JournalDao> {
                every { observeTrackedDates() } returns flowOf(listOf(trackedDate("live-1")))
                every { observePinnedTrackedDates() } returns flowOf(emptyList())
                every { observeNotes() } returns flowOf(emptyList())
            }
            val database = mockk<HrtTrackerDatabase> { every { journalDao() } returns dao }
            val databaseHolder = mockk<DatabaseHolder> {
                every { databaseFlow } returns databaseState
                every { openFailed } returns MutableStateFlow(false)
                // The no-seed branch fires a warm-up retry so a stale openFailed from an
                // earlier failed attempt can't fake an empty journal; the open itself is
                // simulated by databaseState above.
                every { warmUp() } returns Unit
            }
            val snapshotStore = mockk<AnchorSnapshotStore>(relaxed = true)
            val repository = repository(databaseHolder, snapshotStore)
            databaseState.value = database
            advanceUntilIdle() // let the eager cache load

            val first = repository.observeTrackedDatesWithSnapshotSeed().first()

            assertEquals(listOf("live-1"), first.map { it.id })
            coVerify(exactly = 0) { snapshotStore.read() }
        }

    // Intent: with no usable snapshot the screen must stay in its loading state until the
    // database delivers real rows — the seeded flow must not reintroduce the fake empty
    // list the raw observeTrackedDates emits during the not-loaded window.
    @Test
    fun `seeded flow emits nothing before live data when no snapshot exists`() =
        runTest {
            val databaseState = MutableStateFlow<HrtTrackerDatabase?>(null)
            val dao = mockk<JournalDao> {
                every { observeTrackedDates() } returns flowOf(listOf(trackedDate("live-1")))
                every { observePinnedTrackedDates() } returns flowOf(emptyList())
                every { observeNotes() } returns flowOf(emptyList())
            }
            val database = mockk<HrtTrackerDatabase> { every { journalDao() } returns dao }
            val databaseHolder = mockk<DatabaseHolder> {
                every { databaseFlow } returns databaseState
                every { openFailed } returns MutableStateFlow(false)
                // The no-seed branch fires a warm-up retry so a stale openFailed from an
                // earlier failed attempt can't fake an empty journal; the open itself is
                // simulated by databaseState above.
                every { warmUp() } returns Unit
            }
            val snapshotStore = mockk<AnchorSnapshotStore>(relaxed = true) {
                coEvery { read() } returns null
            }
            val repository = repository(databaseHolder, snapshotStore)

            val emissions = mutableListOf<List<String>>()
            val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                repository.observeTrackedDatesWithSnapshotSeed().collect { dates ->
                    emissions.add(dates.map { it.id })
                }
            }
            advanceUntilIdle()
            assertEquals(emptyList<List<String>>(), emissions) // nothing — not a fake empty list
            databaseState.value = database
            advanceUntilIdle()
            job.cancel()

            assertEquals(listOf(listOf("live-1")), emissions)
        }

    // Intent: every journal change must overwrite the persisted seed, so the next cold
    // start renders current data — and deleting all anchors clears it (privacy: the
    // snapshot never outlives the journal it mirrors).
    @Test
    fun `cache emissions are persisted to the snapshot store`() =
        runTest {
            val databaseState = MutableStateFlow<HrtTrackerDatabase?>(null)
            val dao = mockk<JournalDao> {
                every { observeTrackedDates() } returns flowOf(listOf(trackedDate("a1")))
                every { observePinnedTrackedDates() } returns flowOf(emptyList())
                every { observeNotes() } returns flowOf(emptyList())
            }
            val database = mockk<HrtTrackerDatabase> { every { journalDao() } returns dao }
            val databaseHolder = mockk<DatabaseHolder> {
                every { databaseFlow } returns databaseState
                every { openFailed } returns MutableStateFlow(false)
                // The no-seed branch fires a warm-up retry so a stale openFailed from an
                // earlier failed attempt can't fake an empty journal; the open itself is
                // simulated by databaseState above.
                every { warmUp() } returns Unit
            }
            val snapshotStore = mockk<AnchorSnapshotStore>(relaxed = true)
            repository(databaseHolder, snapshotStore)
            databaseState.value = database
            advanceUntilIdle()

            coVerify { snapshotStore.write(match { dates -> dates.map { it.id } == listOf("a1") }) }
        }

    // Intent: deleting the last anchor must clear the persisted seed — the snapshot never
    // outlives the journal it mirrors (privacy), and the next cold start must render a
    // genuinely empty journal, not resurrect deleted anchors.
    @Test
    fun `emptying the journal overwrites the snapshot with an empty list`() =
        runTest {
            val databaseState = MutableStateFlow<HrtTrackerDatabase?>(null)
            // Backing flow stepped between advanceUntilIdle calls: the repository's cache
            // is a conflating StateFlow, so two back-to-back flowOf emissions could collapse
            // into one. Stepping guarantees the writer observes both states in order.
            val daoDates = MutableStateFlow(listOf(trackedDate("a1")))
            val dao = mockk<JournalDao> {
                every { observeTrackedDates() } returns daoDates
                every { observePinnedTrackedDates() } returns flowOf(emptyList())
                every { observeNotes() } returns flowOf(emptyList())
            }
            val database = mockk<HrtTrackerDatabase> { every { journalDao() } returns dao }
            val databaseHolder = mockk<DatabaseHolder> {
                every { databaseFlow } returns databaseState
                every { openFailed } returns MutableStateFlow(false)
                // The no-seed branch fires a warm-up retry so a stale openFailed from an
                // earlier failed attempt can't fake an empty journal; the open itself is
                // simulated by databaseState above.
                every { warmUp() } returns Unit
            }
            val snapshotStore = mockk<AnchorSnapshotStore>(relaxed = true)
            repository(databaseHolder, snapshotStore)
            databaseState.value = database
            advanceUntilIdle() // journal loaded → snapshot written with a1
            daoDates.value = emptyList() // delete-all commits
            advanceUntilIdle()

            coVerifyOrder {
                snapshotStore.write(match { dates -> dates.map { it.id } == listOf("a1") })
                snapshotStore.write(emptyList())
            }
        }

    // Intent: the deep-link preload makes the snapshot synchronously readable before the
    // ViewModel exists, so the first composed frame can seed real data (no loading flash).
    @Test
    fun `preload exposes the persisted snapshot for synchronous reads`() =
        runTest {
            val databaseState = MutableStateFlow<HrtTrackerDatabase?>(null)
            val databaseHolder = mockk<DatabaseHolder> {
                every { databaseFlow } returns databaseState
                every { openFailed } returns MutableStateFlow(false)
                // The no-seed branch fires a warm-up retry so a stale openFailed from an
                // earlier failed attempt can't fake an empty journal; the open itself is
                // simulated by databaseState above.
                every { warmUp() } returns Unit
            }
            val snapshotStore = mockk<AnchorSnapshotStore>(relaxed = true) {
                coEvery { read() } returns listOf(trackedDate("snap-1").toModel())
            }
            val repository = repository(databaseHolder, snapshotStore)

            repository.preloadAnchorSnapshotSeed()
            advanceUntilIdle()

            assertEquals(
                listOf("snap-1"),
                repository.getPreloadedAnchorSnapshot()?.map { it.id },
            )
        }

    // Intent: a warm process must not pay the snapshot-file read — live cache data is
    // already the better seed, so the preload is a no-op.
    @Test
    fun `preload skips the snapshot read when the cache is already loaded`() =
        runTest {
            val databaseState = MutableStateFlow<HrtTrackerDatabase?>(null)
            val dao = mockk<JournalDao> {
                every { observeTrackedDates() } returns flowOf(listOf(trackedDate("live-1")))
                every { observePinnedTrackedDates() } returns flowOf(emptyList())
                every { observeNotes() } returns flowOf(emptyList())
            }
            val database = mockk<HrtTrackerDatabase> { every { journalDao() } returns dao }
            val databaseHolder = mockk<DatabaseHolder> {
                every { databaseFlow } returns databaseState
                every { openFailed } returns MutableStateFlow(false)
                // The no-seed branch fires a warm-up retry so a stale openFailed from an
                // earlier failed attempt can't fake an empty journal; the open itself is
                // simulated by databaseState above.
                every { warmUp() } returns Unit
            }
            val snapshotStore = mockk<AnchorSnapshotStore>(relaxed = true)
            val repository = repository(databaseHolder, snapshotStore)
            databaseState.value = database
            advanceUntilIdle() // let the eager cache load

            repository.preloadAnchorSnapshotSeed()
            advanceUntilIdle()

            coVerify(exactly = 0) { snapshotStore.read() }
        }

    // Intent: a recoverable post-open read error is the same failure that used to blank the
    // journal to emptyList — mass-disabling pins and overwriting the snapshot with empty. The
    // cache must now map it to null (not usable), so the snapshot writer (filterNotNull) never
    // runs on it: a transient read failure can't erase last-known anchors nor look like empty.
    @Test
    fun `a recoverable read error is not persisted as an empty snapshot`() =
        runTest {
            val databaseState = MutableStateFlow<HrtTrackerDatabase?>(null)
            val dao = mockk<JournalDao> {
                every { observeTrackedDates() } returns flow { throw RuntimeException("read failed") }
                every { observePinnedTrackedDates() } returns flowOf(emptyList())
                every { observeNotes() } returns flowOf(emptyList())
            }
            val database = mockk<HrtTrackerDatabase> { every { journalDao() } returns dao }
            val databaseHolder = mockk<DatabaseHolder> {
                every { databaseFlow } returns databaseState
                every { openFailed } returns MutableStateFlow(false)
                // The no-seed branch fires a warm-up retry so a stale openFailed from an
                // earlier failed attempt can't fake an empty journal; the open itself is
                // simulated by databaseState above.
                every { warmUp() } returns Unit
            }
            val snapshotStore = mockk<AnchorSnapshotStore>(relaxed = true)
            repository(databaseHolder, snapshotStore)
            databaseState.value = database
            advanceUntilIdle()

            coVerify(exactly = 0) { snapshotStore.write(any()) }
            coVerify(exactly = 0) { snapshotStore.clear() }
        }

    // Intent: the broadcast refresh path bounds the (now error-window-waiting) await and must
    // read a failed open as "couldn't load — skip", never as an empty journal that disables
    // every pinned shortcut.
    @Test
    fun `awaitTrackedDatesOrNull returns null when the database open fails`() =
        runTest {
            val databaseState = MutableStateFlow<HrtTrackerDatabase?>(null)
            val databaseHolder = mockk<DatabaseHolder> {
                every { databaseFlow } returns databaseState
                every { get() } throws IllegalStateException("cannot open")
            }
            val repository = repository(databaseHolder, mockk(relaxed = true))

            assertEquals(null, repository.awaitTrackedDatesOrNull(timeoutMs = 1_000))
        }

    // Intent: a configured widget must not blank to the "choose a date" empty state on a
    // failed/slow open — it falls back to the last-known snapshot instead.
    @Test
    fun `awaitTrackedDatesOrSnapshot falls back to the snapshot when the open fails`() =
        runTest {
            val databaseState = MutableStateFlow<HrtTrackerDatabase?>(null)
            val databaseHolder = mockk<DatabaseHolder> {
                every { databaseFlow } returns databaseState
                every { get() } throws IllegalStateException("cannot open")
            }
            val snapshotStore = mockk<AnchorSnapshotStore>(relaxed = true) {
                coEvery { read() } returns listOf(trackedDate("snap-1").toModel())
            }
            val repository = repository(databaseHolder, snapshotStore)

            val result = repository.awaitTrackedDatesOrSnapshot(timeoutMs = 1_000)

            assertEquals(listOf("snap-1"), result.map { it.id })
        }

    // Intent: a failed overwrite must not leave the previous snapshot in place — the next cold
    // start would seed Milestones with stale/deleted anchors. The store clears instead.
    @Test
    fun `a failed snapshot write clears the stale snapshot`() =
        runTest {
            val databaseState = MutableStateFlow<HrtTrackerDatabase?>(null)
            val dao = mockk<JournalDao> {
                every { observeTrackedDates() } returns flowOf(listOf(trackedDate("a1")))
                every { observePinnedTrackedDates() } returns flowOf(emptyList())
                every { observeNotes() } returns flowOf(emptyList())
            }
            val database = mockk<HrtTrackerDatabase> { every { journalDao() } returns dao }
            val databaseHolder = mockk<DatabaseHolder> {
                every { databaseFlow } returns databaseState
            }
            val snapshotStore = mockk<AnchorSnapshotStore>(relaxed = true) {
                coEvery { write(any()) } returns false // overwrite fails
            }
            repository(databaseHolder, snapshotStore)
            databaseState.value = database
            advanceUntilIdle()

            coVerify { snapshotStore.clear() }
        }

    // Intent: on a fresh install with no snapshot AND a database that can never open, the
    // Milestones screen must leave its loading state — one genuine empty journal, not an
    // eternal spinner. The terminal openFailed signal drives that single emptyList.
    @Test
    fun `seeded flow emits an empty journal when the open terminally fails with no snapshot`() =
        runTest {
            val databaseState = MutableStateFlow<HrtTrackerDatabase?>(null)
            val openFailedFlow = MutableStateFlow(false)
            val databaseHolder = mockk<DatabaseHolder> {
                every { databaseFlow } returns databaseState
                every { openFailed } returns openFailedFlow
                // Terminal failure: the no-seed branch's warm-up retry keeps failing, so
                // the flag stays true and only the openFailed arm can emit.
                every { warmUp() } returns Unit
            }
            val snapshotStore = mockk<AnchorSnapshotStore>(relaxed = true) {
                coEvery { read() } returns null
            }
            val repository = repository(databaseHolder, snapshotStore)

            val emissions = mutableListOf<List<String>>()
            val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                repository.observeTrackedDatesWithSnapshotSeed().collect { dates ->
                    emissions.add(dates.map { it.id })
                }
            }
            advanceUntilIdle()
            assertEquals(emptyList<List<String>>(), emissions) // still loading, not a false empty
            openFailedFlow.value = true
            advanceUntilIdle()
            job.cancel()

            assertEquals(listOf(emptyList<String>()), emissions)
        }

    // appScope runs on the test scheduler as foreground work: backgroundScope tasks are
    // not advanced by advanceUntilIdle, which would leave the repository's eager caches
    // and snapshot writer permanently dormant (same pattern as MedicationLogRepositoryTest).
    private fun TestScope.repository(
        databaseHolder: DatabaseHolder,
        snapshotStore: AnchorSnapshotStore,
    ) = JournalRepository(
        databaseHolder = databaseHolder,
        clock = Clock.systemUTC(),
        homeSnapshotRepository = mockk(relaxed = true),
        anchorSnapshotStore = snapshotStore,
        appScope = CoroutineScope(StandardTestDispatcher(testScheduler)),
    )

    private fun trackedDate(id: String) = TrackedDateEntity(
        uuid = id,
        name = "Anchor",
        iconKey = "event",
        dateIso = "2024-04-01",
        paletteKey = null,
        heroBackgroundKey = null,
        pinnedOrder = null,
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0,
    )
}
