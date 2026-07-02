package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.JournalDao
import com.mkx.hrttracker.data.local.TrackedDateEntity
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
                every { get() } answers {
                    databaseState.value = database
                    database
                }
            }
            val repository = JournalRepository(
                databaseHolder = databaseHolder,
                clock = Clock.systemUTC(),
                homeSnapshotRepository = mockk(relaxed = true),
                appScope = backgroundScope,
            )

            val result = repository.awaitTrackedDates()

            assertEquals(listOf("anchor-1"), result.map { it.id })
        }

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
