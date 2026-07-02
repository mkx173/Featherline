package com.mkx.hrttracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.JournalDao
import com.mkx.hrttracker.data.local.TrackedDateEntity
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.journal.TrackedDate
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class JournalRepositoryTest {
    private lateinit var db: HrtTrackerDatabase
    private lateinit var repo: JournalRepository
    private lateinit var databaseHolder: DatabaseHolder
    private lateinit var databaseFlow: MutableStateFlow<HrtTrackerDatabase?>
    private lateinit var homeSnapshotRepository: HomeSnapshotRepository
    private lateinit var clock: MutableClock
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 6, 16)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HrtTrackerDatabase::class.java,
        ).build()
        databaseFlow = MutableStateFlow(db)
        databaseHolder = mockk()
        every { databaseHolder.get() } returns db
        every { databaseHolder.databaseFlow } returns databaseFlow
        homeSnapshotRepository = mockk(relaxed = true)
        clock = MutableClock(today.atStartOfDay(zone).toInstant(), zone)
        repo = JournalRepository(
            databaseHolder = databaseHolder,
            clock = clock,
            homeSnapshotRepository = homeSnapshotRepository,
            anchorSnapshotStore = mockk(relaxed = true),
            appScope = appScope,
        )
    }

    @After
    fun tearDown() {
        appScope.cancel()
        db.close()
    }

    @Test
    fun addTrackedDate_honorsPinnedFlag() = runBlocking {
        // The caller decides pinning; a pinned date joins the home tray, an unpinned one is
        // timeline-only. (The "first date pins, later ones don't" default lives in the UI.)
        repo.addTrackedDate(
            name = "On estradiol",
            icon = "medication",
            date = LocalDate.of(2024, 4, 1),
            paletteKey = "ROSE",
            pinned = true,
        )
        repo.addTrackedDate(
            name = "Surgery",
            icon = "event",
            date = LocalDate.of(2026, 9, 15),
            paletteKey = "SAGE",
            pinned = false,
        )

        val pinned = repo.observePinnedTrackedDates().first()
        assertEquals(listOf("On estradiol"), pinned.map { it.name })
        assertEquals(listOf(0), pinned.map { it.pinnedOrder })

        val all = repo.observeTrackedDates().first()
        assertNull(all.single { it.name == "Surgery" }.pinnedOrder)
    }

    @Test
    fun addTrackedDate_pinned_appendsToBottom() = runBlocking {
        repo.addTrackedDate("A", "event", LocalDate.of(2024, 1, 1), null, pinned = true)
        repo.addTrackedDate("B", "event", LocalDate.of(2024, 2, 1), null, pinned = true)

        val pinned = repo.observePinnedTrackedDates().first()
        assertEquals(listOf("A", "B"), pinned.map { it.name })
        assertEquals(listOf(0, 1), pinned.map { it.pinnedOrder })
    }

    @Test
    fun addTrackedDate_concurrentPinnedAdds_assignDistinctPinnedOrdersInsideTransaction() = runBlocking {
        val transactionClock = TransactionRaceClock(
            instant = today.atStartOfDay(zone).toInstant(),
            zone = zone,
            inTransaction = { db.inTransaction() },
        )
        repo = JournalRepository(
            databaseHolder = databaseHolder,
            clock = transactionClock,
            homeSnapshotRepository = homeSnapshotRepository,
            anchorSnapshotStore = mockk(relaxed = true),
            appScope = appScope,
        )

        listOf("First", "Second")
            .map { name ->
                async(Dispatchers.IO) {
                    repo.addTrackedDate(
                        name = name,
                        icon = "event",
                        date = LocalDate.of(2026, 1, 1),
                        paletteKey = null,
                        pinned = true,
                    )
                }
            }
            .awaitAll()

        // getMaxPinnedOrder + insert run inside one transaction, so two concurrent pinned adds
        // get distinct orders (0 and 1) instead of colliding on a shared max read.
        val pinnedOrders = db.journalDao().getTrackedDates().mapNotNull { it.pinnedOrder }.sorted()
        assertEquals(listOf(0, 1), pinnedOrders)
        assertEquals(0, transactionClock.nonTransactionMillisCalls.get())
    }

    @Test
    fun setPinned_true_appendsToBottom_false_unpinsAndCompacts() = runBlocking {
        val ids = addThreePinnedTrackedDates()

        repo.setPinned(ids[1], pinned = false)
        val afterUnpin = repo.observePinnedTrackedDates().first()
        assertEquals(listOf("A", "C"), afterUnpin.map { it.name })
        assertEquals(listOf(0, 1), afterUnpin.map { it.pinnedOrder })

        repo.setPinned(ids[1], pinned = true)
        val afterRepin = repo.observePinnedTrackedDates().first()
        assertEquals(listOf("A", "C", "B"), afterRepin.map { it.name })
        assertEquals(listOf(0, 1, 2), afterRepin.map { it.pinnedOrder })
    }

    @Test
    fun setPinned_true_onAlreadyPinned_noOpsWithoutTimestampChange() = runBlocking {
        addThreePinnedTrackedDates()
        val before = repo.observePinnedTrackedDates().first()
        val pinnedId = before[1].id
        val originalUpdatedAt = db.journalDao()
            .getTrackedDates()
            .single { it.uuid == pinnedId }
            .updatedAtEpochMillis
        clock.advanceMillis(1_000)

        repo.setPinned(pinnedId, pinned = true)

        val after = repo.observePinnedTrackedDates().first()
        val updatedAt = db.journalDao()
            .getTrackedDates()
            .single { it.uuid == pinnedId }
            .updatedAtEpochMillis
        assertEquals(before.map { it.id }, after.map { it.id })
        assertEquals(before.map { it.pinnedOrder }, after.map { it.pinnedOrder })
        assertEquals(originalUpdatedAt, updatedAt)
    }

    @Test
    fun setPinned_false_compactsDuplicateOrdersInVisibleOrder() = runBlocking {
        db.journalDao().insertTrackedDates(
            listOf(
                trackedDateEntity(
                    uuid = "target",
                    name = "Target",
                    date = LocalDate.of(2024, 1, 1),
                    pinnedOrder = 0,
                    createdAt = 1_000L,
                ),
                trackedDateEntity(
                    uuid = "later",
                    name = "Later",
                    date = LocalDate.of(2024, 2, 1),
                    pinnedOrder = 1,
                    createdAt = 2_000L,
                ),
                trackedDateEntity(
                    uuid = "earlier",
                    name = "Earlier",
                    date = LocalDate.of(2024, 2, 1),
                    pinnedOrder = 1,
                    createdAt = 1_000L,
                ),
            )
        )

        repo.setPinned("target", pinned = false)

        val pinned = repo.observePinnedTrackedDates().first()
        assertEquals(listOf("Earlier", "Later"), pinned.map { it.name })
        assertEquals(listOf(0, 1), pinned.map { it.pinnedOrder })
    }

    @Test
    fun setPinned_false_onAlreadyUnpinnedOrMissing_noOpsWithoutCompacting() = runBlocking {
        db.journalDao().insertTrackedDates(
            listOf(
                trackedDateEntity(
                    uuid = "first",
                    name = "First",
                    date = LocalDate.of(2024, 1, 1),
                    pinnedOrder = 0,
                    createdAt = 1_000L,
                ),
                trackedDateEntity(
                    uuid = "second",
                    name = "Second",
                    date = LocalDate.of(2024, 2, 1),
                    pinnedOrder = 2,
                    createdAt = 2_000L,
                ),
                trackedDateEntity(
                    uuid = "unpinned",
                    name = "Unpinned",
                    date = LocalDate.of(2024, 3, 1),
                    pinnedOrder = null,
                    createdAt = 3_000L,
                ),
            )
        )
        clock.advanceMillis(1_000)

        repo.setPinned("unpinned", pinned = false)
        repo.setPinned("missing", pinned = false)

        val rows = db.journalDao().getTrackedDates()
        assertEquals(0, rows.single { it.uuid == "first" }.pinnedOrder)
        assertEquals(2, rows.single { it.uuid == "second" }.pinnedOrder)
        assertNull(rows.single { it.uuid == "unpinned" }.pinnedOrder)
        assertEquals(3_000L, rows.single { it.uuid == "unpinned" }.updatedAtEpochMillis)
    }

    @Test
    fun reorderPinned_rewritesOrder_heroFollows() = runBlocking {
        db.journalDao().insertTrackedDates(
            listOf(
                trackedDateEntity("a", "A", LocalDate.of(2024, 1, 1), pinnedOrder = 0, createdAt = 1_000L),
                trackedDateEntity("b", "B", LocalDate.of(2024, 2, 1), pinnedOrder = 1, createdAt = 2_000L),
            )
        )
        val ids = repo.observePinnedTrackedDates().first().map { it.id }

        repo.reorderPinned(listOf(ids[1], ids[0]))

        val pinned = repo.observePinnedTrackedDates().first()
        assertEquals(listOf("B", "A"), pinned.map { it.name })
        assertEquals(0, pinned.first().pinnedOrder)
    }

    @Test
    fun reorderPinned_rewritesThreeItemsToContiguousOrders() = runBlocking {
        val ids = addThreePinnedTrackedDates()

        repo.reorderPinned(listOf(ids[2], ids[0], ids[1]))

        val pinned = repo.observePinnedTrackedDates().first()
        assertEquals(listOf("C", "A", "B"), pinned.map { it.name })
        assertEquals(listOf(0, 1, 2), pinned.map { it.pinnedOrder })
    }

    @Test
    fun reorderPinned_sameOrder_noOpsWithoutTimestampChange() = runBlocking {
        val ids = addThreePinnedTrackedDates()
        val originalUpdatedAt = db.journalDao()
            .getTrackedDates()
            .associate { it.uuid to it.updatedAtEpochMillis }
        clock.advanceMillis(1_000)

        repo.reorderPinned(ids)

        val pinned = repo.observePinnedTrackedDates().first()
        val updatedAt = db.journalDao()
            .getTrackedDates()
            .associate { it.uuid to it.updatedAtEpochMillis }
        assertEquals(ids, pinned.map { it.id })
        assertEquals(listOf(0, 1, 2), pinned.map { it.pinnedOrder })
        assertEquals(originalUpdatedAt, updatedAt)
    }

    @Test
    fun reorderPinned_duplicateId_noOps() = runBlocking {
        val ids = addThreePinnedTrackedDates()

        repo.reorderPinned(listOf(ids[1], ids[1], ids[0]))

        assertPinnedStateUnchanged(ids)
    }

    @Test
    fun reorderPinned_missingId_noOps() = runBlocking {
        val ids = addThreePinnedTrackedDates()

        repo.reorderPinned(listOf(ids[1], "missing", ids[0]))

        assertPinnedStateUnchanged(ids)
    }

    @Test
    fun reorderPinned_unpinnedId_noOps() = runBlocking {
        val ids = addThreePinnedTrackedDates()
        db.journalDao().upsertTrackedDate(
            trackedDateEntity(
                uuid = "unpinned",
                name = "Unpinned",
                date = LocalDate.of(2024, 4, 1),
                pinnedOrder = null,
                createdAt = 4_000L,
            )
        )

        repo.reorderPinned(listOf(ids[1], "unpinned", ids[0]))

        assertPinnedStateUnchanged(ids)
        assertNull(db.journalDao().getTrackedDates().single { it.uuid == "unpinned" }.pinnedOrder)
    }

    @Test
    fun reorderPinned_omittedPinnedId_noOps() = runBlocking {
        val ids = addThreePinnedTrackedDates()

        repo.reorderPinned(listOf(ids[1], ids[0]))

        assertPinnedStateUnchanged(ids)
    }

    @Test
    fun addTrackedDate_normalizesIcon_andStoresPaletteAsPassed() = runBlocking {
        repo.addTrackedDate(
            name = "Unknown icon",
            icon = "unknown",
            date = LocalDate.of(2026, 1, 1),
            paletteKey = "CUSTOM",
            pinned = true,
        )

        val row = db.journalDao().getTrackedDates().single()
        assertEquals("event", row.iconKey)
        assertEquals("CUSTOM", row.paletteKey)
    }

    // The hero snapshot refresh is driven by a reactive observer on appScope that baselines
    // on the first pinned-dates emission. Drive it through hero changes (add + rename) so the
    // collector is provably live and baselined regardless of startup timing, then empty the
    // table and wait for the resulting refresh so nothing is left in flight, and reset the
    // mock — so a test can assert precisely which later mutation does or doesn't refresh.
    private suspend fun resetToEmptyWithLiveObserver() {
        val icon = AnchorIcon.entries.first().storageKey
        repo.addTrackedDate("__warmup__", icon, LocalDate.of(2000, 1, 1), null, pinned = true)
        val id = repo.observeTrackedDates().first { it.isNotEmpty() }.first().id
        // A second hero change guarantees a refresh whether the collector baselined before or
        // after the add above.
        repo.updateTrackedDate(id, "__warmup2__", icon, LocalDate.of(2000, 1, 1), null)
        coVerify(timeout = 5_000L, atLeast = 1) {
            homeSnapshotRepository.refreshHomeSnapshotAsync(now = any(), force = true, zoneId = any())
        }
        clearMocks(homeSnapshotRepository, answers = false)
        // Empty the table; the delete is the last change, so waiting for its refresh drains
        // any earlier in-flight one (the single collector processes emissions in order).
        repo.deleteTrackedDate(id)
        coVerify(timeout = 5_000L, atLeast = 1) {
            homeSnapshotRepository.refreshHomeSnapshotAsync(now = any(), force = true, zoneId = any())
        }
        clearMocks(homeSnapshotRepository, answers = false)
    }

    @Test
    fun pinningFirstDate_triggersSnapshotRefresh() = runBlocking {
        resetToEmptyWithLiveObserver()

        repo.addTrackedDate(
            "HRT start",
            AnchorIcon.entries.first().storageKey,
            LocalDate.of(2025, 3, 14),
            null,
            pinned = true,
        )

        // The refresh is reactive (off the pinned-dates flow), so it lands shortly after the
        // write commits rather than inline.
        coVerify(timeout = 5_000L, atLeast = 1) {
            homeSnapshotRepository.refreshHomeSnapshotAsync(
                now = any(),
                force = true,
                zoneId = any(),
            )
        }
    }

    @Test
    fun renamingNonHeroDate_doesNotTriggerSnapshotRefresh() = runBlocking {
        resetToEmptyWithLiveObserver()
        val icon = AnchorIcon.entries.first().storageKey
        repo.addTrackedDate("Hero", icon, LocalDate.of(2020, 1, 1), null, pinned = true)
        repo.addTrackedDate("Other", icon, LocalDate.of(2021, 1, 1), null, pinned = false)
        // Let the pin-driven refresh from adding the hero settle, then reset.
        coVerify(timeout = 5_000L, atLeast = 1) {
            homeSnapshotRepository.refreshHomeSnapshotAsync(now = any(), force = true, zoneId = any())
        }
        clearMocks(homeSnapshotRepository, answers = false)

        val otherId = repo.observeTrackedDates().first { rows -> rows.any { it.name == "Other" } }
            .first { it.name == "Other" }
            .id
        repo.updateTrackedDate(otherId, "Other renamed", icon, LocalDate.of(2021, 1, 1), null)

        // Barrier: a real hero change. The observer processes pinned-list emissions in order,
        // so once this refresh is seen, any (incorrect) refresh from the non-hero rename would
        // already have landed — exactly=1 proves the non-hero rename triggered none.
        val heroId = repo.observeTrackedDates().first { rows -> rows.any { it.name == "Hero" } }
            .first { it.name == "Hero" }
            .id
        repo.updateTrackedDate(heroId, "Hero renamed", icon, LocalDate.of(2020, 1, 1), null)

        coVerify(timeout = 5_000L, atLeast = 1) {
            homeSnapshotRepository.refreshHomeSnapshotAsync(now = any(), force = true, zoneId = any())
        }
        coVerify(exactly = 1) {
            homeSnapshotRepository.refreshHomeSnapshotAsync(now = any(), force = true, zoneId = any())
        }
    }

    @Test
    fun updateTrackedDate_keepsPinAndCreatedAt() = runBlocking {
        repo.addTrackedDate("A", "event", LocalDate.of(2024, 1, 1), null, pinned = true)
        val a = repo.observeTrackedDates().first().single()
        val originalRow = db.journalDao().getTrackedDates().single { it.uuid == a.id }
        clock.advanceMillis(1_000)

        repo.updateTrackedDate(
            id = a.id,
            name = "A2",
            icon = "flag",
            date = LocalDate.of(2025, 5, 5),
            paletteKey = "ROSE",
        )

        val updated = repo.observeTrackedDates().first().single()
        val updatedRow = db.journalDao().getTrackedDates().single { it.uuid == a.id }
        assertEquals("A2", updated.name)
        assertEquals(AnchorIcon.FLAG, updated.icon)
        assertEquals(LocalDate.of(2025, 5, 5), updated.date)
        assertEquals(a.pinnedOrder, updated.pinnedOrder)
        assertEquals(originalRow.createdAtEpochMillis, updatedRow.createdAtEpochMillis)
        assertTrue(updatedRow.updatedAtEpochMillis > originalRow.updatedAtEpochMillis)
    }

    @Test
    fun updateTrackedDate_missingId_noOpsWithoutChangingExistingRows() = runBlocking {
        repo.addTrackedDate("A", "event", LocalDate.of(2024, 1, 1), null, pinned = true)
        val beforeRows = db.journalDao().getTrackedDates()
        clock.advanceMillis(1_000)

        repo.updateTrackedDate(
            id = "missing",
            name = "A2",
            icon = "flag",
            date = LocalDate.of(2025, 5, 5),
            paletteKey = "ROSE",
        )

        assertEquals(beforeRows, db.journalDao().getTrackedDates())
    }

    @Test
    fun updateTrackedDate_normalizesUnknownIconAndPreservesUnknownPaletteInStorage() = runBlocking {
        repo.addTrackedDate("A", "flag", LocalDate.of(2024, 1, 1), "ROSE", pinned = true)
        val a = repo.observeTrackedDates().first().single()

        repo.updateTrackedDate(
            id = a.id,
            name = "A2",
            icon = "unknown",
            date = LocalDate.of(2025, 5, 5),
            paletteKey = "CUSTOM",
        )

        val updated = repo.observeTrackedDates().first().single()
        val updatedRow = db.journalDao().getTrackedDates().single { it.uuid == a.id }
        assertEquals(AnchorIcon.EVENT, updated.icon)
        assertNull(updated.palette)
        assertEquals("event", updatedRow.iconKey)
        assertEquals("CUSTOM", updatedRow.paletteKey)
    }

    @Test
    fun deleteTrackedDate_removesRow() = runBlocking {
        repo.addTrackedDate("A", "event", LocalDate.of(2024, 1, 1), null, pinned = true)
        val a = repo.observeTrackedDates().first().single()

        repo.deleteTrackedDate(a.id)

        assertEquals(emptyList<TrackedDate>(), repo.observeTrackedDates().first())
    }

    @Test
    fun deleteTrackedDate_missingAndDoubleDelete_areIdempotent() = runBlocking {
        repo.addTrackedDate("A", "event", LocalDate.of(2024, 1, 1), null, pinned = true)
        val a = repo.observeTrackedDates().first().single()

        repo.deleteTrackedDate("missing")

        assertEquals(listOf(a), repo.observeTrackedDates().first())

        repo.deleteTrackedDate(a.id)
        repo.deleteTrackedDate(a.id)

        assertEquals(emptyList<TrackedDate>(), repo.observeTrackedDates().first())
    }

    // The hero snapshot refresh is decoupled onto appScope (not the caller's scope), and is
    // best-effort: a snapshot/DataStore failure must neither fail the journal write nor tear
    // down the long-lived observer. Here the first invalidation throws; the collector must
    // survive so a later hero change still drives a refresh.
    @Test
    fun snapshotInvalidationFailure_isBestEffort_keepingObserverAlive() = runBlocking {
        resetToEmptyWithLiveObserver()
        val icon = AnchorIcon.entries.first().storageKey
        var invalidateCalls = 0
        coEvery { homeSnapshotRepository.invalidateHomeSnapshot() } coAnswers {
            invalidateCalls += 1
            if (invalidateCalls == 1) throw IOException("simulated DataStore failure")
        }

        // First hero change: the observer's invalidate throws but is swallowed (best-effort).
        repo.addTrackedDate("Hero", icon, LocalDate.of(2024, 1, 1), null, pinned = true)
        val id = repo.observeTrackedDates().first { rows -> rows.any { it.name == "Hero" } }
            .first { it.name == "Hero" }
            .id

        // Second hero change: only fires a refresh if the collector survived the failure.
        repo.updateTrackedDate(id, "Hero renamed", icon, LocalDate.of(2024, 1, 1), null)
        coVerify(timeout = 5_000L, atLeast = 1) {
            homeSnapshotRepository.refreshHomeSnapshotAsync(now = any(), force = true, zoneId = any())
        }
    }

    @Test
    fun heroChange_invalidatesSnapshotBeforeRefresh() = runBlocking {
        resetToEmptyWithLiveObserver()
        val icon = AnchorIcon.entries.first().storageKey

        repo.addTrackedDate("Hero", icon, LocalDate.of(2024, 1, 1), null, pinned = true)

        coVerify(timeout = 5_000L, atLeast = 1) {
            homeSnapshotRepository.refreshHomeSnapshotAsync(now = any(), force = true, zoneId = any())
        }
        coVerifyOrder {
            homeSnapshotRepository.invalidateHomeSnapshot()
            homeSnapshotRepository.refreshHomeSnapshotAsync(now = any(), force = true, zoneId = any())
        }
    }

    @Test
    fun observeTrackedDates_returnsMappedDates() = runBlocking {
        repo.addTrackedDate(
            name = "Future",
            icon = "event",
            date = LocalDate.of(2026, 9, 15),
            paletteKey = null,
            pinned = true,
        )
        repo.addTrackedDate(
            name = "Past",
            icon = "medication",
            date = LocalDate.of(2024, 4, 1),
            paletteKey = "ROSE",
            pinned = false,
        )

        val trackedDates = repo.observeTrackedDates().first()
        assertEquals(listOf("Past", "Future"), trackedDates.map { it.name })
    }

    @Test
    fun saveNoteForToday_replacesExisting() = runBlocking {
        repo.saveNoteForDate(date = today, text = "first")
        val first = db.journalDao().getNoteForDate(today.toString())!!
        clock.advanceMillis(1_000)

        repo.saveNoteForDate(date = today, text = "second")
        val second = db.journalDao().getNoteForDate(today.toString())!!

        val note = repo.observeNoteForDate(today).first()
        assertEquals("second", note?.text)
        assertEquals(first.uuid, second.uuid)
        assertEquals(first.createdAtEpochMillis, second.createdAtEpochMillis)
        assertTrue(second.updatedAtEpochMillis > first.updatedAtEpochMillis)
        assertEquals(1, db.journalDao().getNotes().size)
    }

    @Test
    fun deleteNoteForDate_removesOnlyThatDay() = runBlocking {
        val day = LocalDate.of(2026, 6, 16)
        val otherDay = day.plusDays(1)
        repo.saveNoteForDate(day, "hello")
        repo.saveNoteForDate(otherDay, "keep")

        repo.deleteNoteForDate(day)

        assertNull(repo.observeNoteForDate(day).first())
        assertEquals("keep", repo.observeNoteForDate(otherDay).first()?.text)
    }

    @Test
    fun deleteNoteForDate_usesAtomicDaoDeleteByDate() = runBlocking {
        val journalDao = mockk<JournalDao>()
        coEvery { journalDao.deleteNoteForDate(today.toString()) } just Runs
        val database = mockk<HrtTrackerDatabase>()
        every { database.journalDao() } returns journalDao
        val holder = mockk<DatabaseHolder>()
        every { holder.get() } returns database
        // The warm caches observe databaseFlow at construction; a null source
        // keeps them dormant so they don't touch the narrowly-mocked dao here.
        every { holder.databaseFlow } returns MutableStateFlow(null)
        val repository = JournalRepository(
            databaseHolder = holder,
            clock = clock,
            homeSnapshotRepository = homeSnapshotRepository,
            anchorSnapshotStore = mockk(relaxed = true),
            appScope = appScope,
        )

        repository.deleteNoteForDate(today)

        coVerify(exactly = 1) { journalDao.deleteNoteForDate(today.toString()) }
        coVerify(exactly = 0) { journalDao.getNoteForDate(today.toString()) }
    }

    @Test
    fun deleteNoteForDate_missingAndDoubleDelete_areIdempotent() = runBlocking {
        val day = LocalDate.of(2026, 6, 16)
        repo.saveNoteForDate(day, "hello")

        repo.deleteNoteForDate(day.plusDays(1))

        assertEquals("hello", repo.observeNoteForDate(day).first()?.text)

        repo.deleteNoteForDate(day)
        repo.deleteNoteForDate(day)

        assertNull(repo.observeNoteForDate(day).first())
    }

    @Test
    fun observeNotesOnOrAfter_filtersOlderNotes() = runBlocking {
        repo.saveNoteForDate(date = LocalDate.of(2026, 6, 14), text = "old")
        repo.saveNoteForDate(date = LocalDate.of(2026, 6, 15), text = "included")
        repo.saveNoteForDate(date = LocalDate.of(2026, 6, 16), text = "today")

        val notes = repo.observeNotesOnOrAfter(LocalDate.of(2026, 6, 15)).first()
        assertEquals(listOf("today", "included"), notes.map { it.text })
    }

    @Test
    fun observeAllNotesCount_countsEveryNote() = runBlocking {
        repo.saveNoteForDate(date = LocalDate.of(2026, 6, 14), text = "old")
        repo.saveNoteForDate(date = LocalDate.of(2026, 6, 16), text = "today")

        assertEquals(2, repo.observeAllNotesCount().first())
    }

    @Test
    fun observeNotesCountBefore_countsOnlyNotesBeforeBoundary() = runBlocking {
        repo.saveNoteForDate(date = LocalDate.of(2026, 5, 17), text = "older")
        repo.saveNoteForDate(date = LocalDate.of(2026, 5, 18), text = "boundary")
        repo.saveNoteForDate(date = LocalDate.of(2026, 6, 16), text = "recent")

        assertEquals(1, repo.observeNotesCountBefore(LocalDate.of(2026, 5, 18)).first())
    }

    @Test
    fun observers_emitFallbacks_whenDatabaseFlowEmitsNull() = runBlocking {
        databaseFlow.value = null

        assertEquals(emptyList<String>(), repo.observeTrackedDates().first().map { it.name })
        assertEquals(emptyList<String>(), repo.observePinnedTrackedDates().first().map { it.name })
        assertEquals(emptyList<String>(), repo.observeNotesOnOrAfter(today).first().map { it.text })
        assertNull(repo.observeNoteForDate(today).first())
        assertEquals(0, repo.observeAllNotesCount().first())
        assertEquals(0, repo.observeNotesCountBefore(today).first())
    }

    @Test
    fun observers_emitFallbacks_whenDaoFlowsThrowRecoverableError() = runBlocking {
        val journalDao = mockk<JournalDao>()
        every { journalDao.observeTrackedDates() } returns flow {
            throw IllegalStateException("tracked dates failed")
        }
        every { journalDao.observePinnedTrackedDates() } returns flow {
            throw IllegalStateException("pinned tracked dates failed")
        }
        every { journalDao.observeNotesOnOrAfter(today.toString()) } returns flow {
            throw IllegalStateException("notes failed")
        }
        every { journalDao.observeNoteForDate(today.toString()) } returns flow {
            throw IllegalStateException("note failed")
        }
        every { journalDao.observeAllNotesCount() } returns flow {
            throw IllegalStateException("note count failed")
        }
        every { journalDao.observeNotesCountBefore(today.toString()) } returns flow {
            throw IllegalStateException("older note count failed")
        }
        // The notes warm cache also observes this; without a stub the mock throws a
        // MockKException (not a recoverable flow error), crashing the appScope collector.
        every { journalDao.observeNotes() } returns flow {
            throw IllegalStateException("all notes failed")
        }
        val erroringDatabase = mockk<HrtTrackerDatabase>()
        every { erroringDatabase.journalDao() } returns journalDao
        databaseFlow.value = erroringDatabase

        assertEquals(emptyList<String>(), repo.observeTrackedDates().first().map { it.name })
        assertEquals(emptyList<String>(), repo.observePinnedTrackedDates().first().map { it.name })
        assertEquals(emptyList<String>(), repo.observeNotesOnOrAfter(today).first().map { it.text })
        assertNull(repo.observeNoteForDate(today).first())
        assertEquals(0, repo.observeAllNotesCount().first())
        assertEquals(0, repo.observeNotesCountBefore(today).first())
    }

    @Test
    fun observerContinues_whenDatabaseFlowEmitsAfterRecoverableDaoError() = runBlocking {
        val journalDao = mockk<JournalDao>()
        every { journalDao.observeTrackedDates() } returns flow {
            throw IllegalStateException("tracked dates failed")
        }
        // The Eagerly-collected pinned/notes warm caches also observe this database; stub them
        // so the appScope collectors hit a recoverable flow error, not an unstubbed-mock crash.
        every { journalDao.observePinnedTrackedDates() } returns flow {
            throw IllegalStateException("pinned tracked dates failed")
        }
        every { journalDao.observeNotes() } returns flow {
            throw IllegalStateException("all notes failed")
        }
        val erroringDatabase = mockk<HrtTrackerDatabase>()
        every { erroringDatabase.journalDao() } returns journalDao
        databaseFlow.value = erroringDatabase

        val recoveredRow = TrackedDateEntity(
            uuid = "recovered",
            name = "Recovered",
            iconKey = "event",
            dateIso = today.toString(),
            paletteKey = null,
            heroBackgroundKey = null,
            pinnedOrder = 0,
            createdAtEpochMillis = 1000L,
            updatedAtEpochMillis = 1000L,
        )
        var switchedToRecoveredDatabase = false

        val emissions = withTimeout(2_000) {
            repo.observeTrackedDates()
                .onEach {
                    if (!switchedToRecoveredDatabase) {
                        switchedToRecoveredDatabase = true
                        db.journalDao().upsertTrackedDate(recoveredRow)
                        databaseFlow.value = db
                    }
                }
                .take(2)
                .toList()
        }

        assertEquals(listOf(emptyList<String>(), listOf("Recovered")), emissions.map { rows ->
            rows.map { it.name }
        })
    }

    // addTrackedDate only auto-pins the first date, so seed three pinned rows directly to
    // exercise the multi-pin setPinned/reorder paths.
    private suspend fun addThreePinnedTrackedDates(): List<String> {
        db.journalDao().insertTrackedDates(
            listOf(
                trackedDateEntity("a", "A", LocalDate.of(2024, 1, 1), pinnedOrder = 0, createdAt = 1_000L),
                trackedDateEntity("b", "B", LocalDate.of(2024, 2, 1), pinnedOrder = 1, createdAt = 2_000L),
                trackedDateEntity("c", "C", LocalDate.of(2024, 3, 1), pinnedOrder = 2, createdAt = 3_000L),
            )
        )
        return repo.observePinnedTrackedDates().first().map { it.id }
    }

    private suspend fun assertPinnedStateUnchanged(ids: List<String>) {
        val pinned = repo.observePinnedTrackedDates().first()
        assertEquals(ids, pinned.map { it.id })
        assertEquals(listOf(0, 1, 2), pinned.map { it.pinnedOrder })
    }

    private class MutableClock(
        private var instant: Instant,
        private val zone: ZoneId,
    ) : Clock() {
        override fun getZone(): ZoneId = zone

        override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)

        override fun instant(): Instant = instant

        override fun millis(): Long = instant.toEpochMilli()

        fun advanceMillis(millis: Long) {
            instant = instant.plusMillis(millis)
        }
    }

    private class TransactionRaceClock(
        private val instant: Instant,
        private val zone: ZoneId,
        private val inTransaction: () -> Boolean,
    ) : Clock() {
        val nonTransactionMillisCalls = AtomicInteger(0)
        private val outsideTransactionReached = CountDownLatch(2)
        private val millisCalls = AtomicInteger(0)

        override fun getZone(): ZoneId = zone

        override fun withZone(zone: ZoneId): Clock =
            TransactionRaceClock(instant = instant, zone = zone, inTransaction = inTransaction)

        override fun instant(): Instant = Instant.ofEpochMilli(millis())

        override fun millis(): Long {
            if (!inTransaction()) {
                nonTransactionMillisCalls.incrementAndGet()
                outsideTransactionReached.countDown()
                assertTrue(
                    "Both concurrent writes should reach clock.millis outside a transaction",
                    outsideTransactionReached.await(5, TimeUnit.SECONDS),
                )
            }
            return instant.toEpochMilli() + millisCalls.getAndIncrement()
        }
    }
}

private fun trackedDateEntity(
    uuid: String,
    name: String,
    date: LocalDate,
    pinnedOrder: Int?,
    createdAt: Long,
) = TrackedDateEntity(
    uuid = uuid,
    name = name,
    iconKey = "event",
    dateIso = date.toString(),
    paletteKey = null,
    heroBackgroundKey = null,
    pinnedOrder = pinnedOrder,
    createdAtEpochMillis = createdAt,
    updatedAtEpochMillis = createdAt,
)
