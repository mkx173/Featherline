package com.mkx.hrttracker.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JournalDaoTest {
    private lateinit var db: HrtTrackerDatabase
    private lateinit var dao: JournalDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HrtTrackerDatabase::class.java,
        ).build()
        dao = db.journalDao()
    }

    @After fun tearDown() = db.close()

    @Test
    fun observePinned_ordersByPinnedOrder_excludesUnpinned() = runBlocking {
        dao.upsertTrackedDate(trackedDate("a", dateIso = "2024-04-01", pinnedOrder = 1))
        dao.upsertTrackedDate(trackedDate("b", dateIso = "2026-03-01", pinnedOrder = 0))
        dao.upsertTrackedDate(trackedDate("c", dateIso = "2026-09-15", pinnedOrder = null))

        val pinned = dao.observePinnedTrackedDates().first()

        assertEquals(listOf("b", "a"), pinned.map { it.uuid })
    }

    @Test
    fun observePinned_ordersDuplicatePinnedOrdersDeterministically() = runBlocking {
        dao.upsertTrackedDate(
            trackedDate("later", dateIso = "2026-09-15", pinnedOrder = 0, createdAt = 2000L)
        )
        dao.upsertTrackedDate(
            trackedDate("earlier", dateIso = "2024-04-01", pinnedOrder = 0, createdAt = 1000L)
        )

        val pinned = dao.observePinnedTrackedDates().first()

        assertEquals(listOf("earlier", "later"), pinned.map { it.uuid })
    }

    @Test
    fun getNoteForDate_returnsSameDayRow_forUpsertReuse() = runBlocking {
        dao.upsertNote(note("n1", dateIso = "2026-06-16", text = "first"))
        val existing = dao.getNoteForDate("2026-06-16")
        assertEquals("n1", existing?.uuid)

        dao.upsertNote(note(existing!!.uuid, dateIso = "2026-06-16", text = "edited"))
        assertEquals("edited", dao.getNoteForDate("2026-06-16")?.text)
        assertEquals(1, dao.getNotes().size)
    }

    @Test
    fun upsertNote_updatesExistingDate_whenCallerProvidesFreshUuid() = runBlocking {
        dao.upsertNote(
            note(
                uuid = "n1",
                dateIso = "2026-06-16",
                text = "first",
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )

        dao.upsertNote(
            note(
                uuid = "n2",
                dateIso = "2026-06-16",
                text = "second",
                createdAt = 2000L,
                updatedAt = 3000L,
            )
        )

        val updated = dao.getNoteForDate("2026-06-16")
        assertEquals("n1", updated?.uuid)
        assertEquals("second", updated?.text)
        assertEquals(1000L, updated?.createdAtEpochMillis)
        assertEquals(3000L, updated?.updatedAtEpochMillis)
        assertEquals(1, dao.getNotes().size)
    }

    @Test
    fun getMaxPinnedOrder_returnsLargestPinnedOrderWithoutLoadingRows() = runBlocking {
        dao.upsertTrackedDate(trackedDate("a", dateIso = "2024-04-01", pinnedOrder = 1))
        dao.upsertTrackedDate(trackedDate("b", dateIso = "2026-03-01", pinnedOrder = null))
        dao.upsertTrackedDate(trackedDate("c", dateIso = "2026-09-15", pinnedOrder = 3))

        assertEquals(3, dao.getMaxPinnedOrder())
    }

    @Test
    fun trackedDates_hasDateIsoIndexForChronologicalObservation() {
        val indexNames = mutableSetOf<String>()
        db.openHelper.readableDatabase.query("PRAGMA index_list(tracked_dates)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                indexNames += cursor.getString(nameIndex)
            }
        }

        assertTrue(indexNames.contains("index_tracked_dates_dateIso"))
    }

    private fun trackedDate(
        uuid: String,
        dateIso: String,
        pinnedOrder: Int?,
        createdAt: Long = 1000L,
    ) = TrackedDateEntity(
        uuid,
        "name-$uuid",
        "event",
        dateIso,
        null,
        pinnedOrder,
        createdAt,
        createdAt,
    )

    private fun note(
        uuid: String,
        dateIso: String,
        text: String,
        createdAt: Long = 1000L,
        updatedAt: Long = 1000L,
    ) = NoteEntity(uuid, dateIso, text, createdAt, updatedAt)
}
