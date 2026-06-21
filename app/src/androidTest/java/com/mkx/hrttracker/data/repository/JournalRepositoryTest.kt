package com.mkx.hrttracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.JournalDao
import com.mkx.hrttracker.data.local.TrackedDateEntity
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
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
    private lateinit var clock: MutableClock
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
        clock = MutableClock(today.atStartOfDay(zone).toInstant(), zone)
        repo = JournalRepository(databaseHolder = databaseHolder, clock = clock)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun addTrackedDate_pinsByDefault_appendedToBottom() = runBlocking {
        repo.addTrackedDate(
            name = "On estradiol",
            icon = "medication",
            date = LocalDate.of(2024, 4, 1),
            paletteKey = "ROSE",
        )
        repo.addTrackedDate(
            name = "Surgery",
            icon = "event",
            date = LocalDate.of(2026, 9, 15),
            paletteKey = "SAGE",
        )

        val pinned = repo.observePinnedTrackedDates().first()
        assertEquals(listOf("On estradiol", "Surgery"), pinned.map { it.name })
        assertEquals(listOf(0, 1), pinned.map { it.pinnedOrder })
    }

    @Test
    fun addTrackedDate_concurrentAdds_assignDistinctPinnedOrdersInsideTransaction() = runBlocking {
        val transactionClock = TransactionRaceClock(
            instant = today.atStartOfDay(zone).toInstant(),
            zone = zone,
            inTransaction = { db.inTransaction() },
        )
        repo = JournalRepository(databaseHolder = databaseHolder, clock = transactionClock)

        listOf("First", "Second")
            .map { name ->
                async(Dispatchers.IO) {
                    repo.addTrackedDate(
                        name = name,
                        icon = "event",
                        date = LocalDate.of(2026, 1, 1),
                        paletteKey = null,
                    )
                }
            }
            .awaitAll()

        val pinnedOrders = db.journalDao().getTrackedDates().mapNotNull { it.pinnedOrder }.sorted()
        assertEquals(listOf(0, 1), pinnedOrders)
        assertEquals(0, transactionClock.nonTransactionMillisCalls.get())
    }

    @Test
    fun addTrackedDate_normalizesIcon_andStoresPaletteAsPassed() = runBlocking {
        repo.addTrackedDate(
            name = "Unknown icon",
            icon = "unknown",
            date = LocalDate.of(2026, 1, 1),
            paletteKey = "CUSTOM",
        )

        val row = db.journalDao().getTrackedDates().single()
        assertEquals("event", row.iconKey)
        assertEquals("CUSTOM", row.paletteKey)
    }

    @Test
    fun observeTrackedDates_returnsMappedDates() = runBlocking {
        repo.addTrackedDate(
            name = "Future",
            icon = "event",
            date = LocalDate.of(2026, 9, 15),
            paletteKey = null,
        )
        repo.addTrackedDate(
            name = "Past",
            icon = "medication",
            date = LocalDate.of(2024, 4, 1),
            paletteKey = "ROSE",
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
    fun observeNotesOnOrAfter_filtersOlderNotes() = runBlocking {
        repo.saveNoteForDate(date = LocalDate.of(2026, 6, 14), text = "old")
        repo.saveNoteForDate(date = LocalDate.of(2026, 6, 15), text = "included")
        repo.saveNoteForDate(date = LocalDate.of(2026, 6, 16), text = "today")

        val notes = repo.observeNotesOnOrAfter(LocalDate.of(2026, 6, 15)).first()
        assertEquals(listOf("today", "included"), notes.map { it.text })
    }

    @Test
    fun observers_emitFallbacks_whenDatabaseFlowEmitsNull() = runBlocking {
        databaseFlow.value = null

        assertEquals(emptyList<String>(), repo.observeTrackedDates().first().map { it.name })
        assertEquals(emptyList<String>(), repo.observePinnedTrackedDates().first().map { it.name })
        assertEquals(emptyList<String>(), repo.observeNotesOnOrAfter(today).first().map { it.text })
        assertNull(repo.observeNoteForDate(today).first())
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
        val erroringDatabase = mockk<HrtTrackerDatabase>()
        every { erroringDatabase.journalDao() } returns journalDao
        databaseFlow.value = erroringDatabase

        assertEquals(emptyList<String>(), repo.observeTrackedDates().first().map { it.name })
        assertEquals(emptyList<String>(), repo.observePinnedTrackedDates().first().map { it.name })
        assertEquals(emptyList<String>(), repo.observeNotesOnOrAfter(today).first().map { it.text })
        assertNull(repo.observeNoteForDate(today).first())
    }

    @Test
    fun observerContinues_whenDatabaseFlowEmitsAfterRecoverableDaoError() = runBlocking {
        val journalDao = mockk<JournalDao>()
        every { journalDao.observeTrackedDates() } returns flow {
            throw IllegalStateException("tracked dates failed")
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
