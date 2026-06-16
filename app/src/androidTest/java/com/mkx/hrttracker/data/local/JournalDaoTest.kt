package com.mkx.hrttracker.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun getNoteForDate_returnsSameDayRow_forUpsertReuse() = runBlocking {
        dao.upsertNote(note("n1", dateIso = "2026-06-16", text = "first"))
        val existing = dao.getNoteForDate("2026-06-16")
        assertEquals("n1", existing?.uuid)

        dao.upsertNote(note(existing!!.uuid, dateIso = "2026-06-16", text = "edited"))
        assertEquals("edited", dao.getNoteForDate("2026-06-16")?.text)
        assertEquals(1, dao.getNotes().size)
    }

    private fun trackedDate(uuid: String, dateIso: String, pinnedOrder: Int?) =
        TrackedDateEntity(uuid, "name-$uuid", "event", dateIso, null, pinnedOrder, 1000, 1000)

    private fun note(uuid: String, dateIso: String, text: String) =
        NoteEntity(uuid, dateIso, text, 1000, 1000)
}
