package com.mkx.hrttracker.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MedicationLogDaoMoveScheduledForTest {
    private lateinit var database: HrtTrackerDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HrtTrackerDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun moveScheduledForEntries_setsTheTimeAndShiftsTheDate() = runTest {
        val dao = database.medicationLogDao()
        dao.insertEntries(
            listOf(
                entry("month-end", "2026-10-31T23:00"),
                entry("year-start", "2026-01-01T01:00"),
                entry("same-day", "2026-04-29T08:00"),
                entry("untouched", "2026-04-29T08:00"),
            )
        )

        dao.moveScheduledForEntries(listOf("month-end"), "1 days", "00:30")
        dao.moveScheduledForEntries(listOf("year-start"), "-1 days", "23:30")
        dao.moveScheduledForEntries(listOf("same-day"), "0 days", "09:30")

        assertEquals("2026-11-01T00:30", dao.getEntry("month-end")?.scheduledForIso)
        assertEquals("2025-12-31T23:30", dao.getEntry("year-start")?.scheduledForIso)
        assertEquals("2026-04-29T09:30", dao.getEntry("same-day")?.scheduledForIso)
        assertEquals("2026-04-29T08:00", dao.getEntry("untouched")?.scheduledForIso)
    }

    private fun entry(uuid: String, scheduledForIso: String) = MedicationLogEntryEntity(
        uuid = uuid,
        category = "ESTRADIOL",
        medicineUuid = null,
        applicationType = "ORAL",
        doseInstructionKind = "TABLET_FRACTION",
        tabletFractionNumerator = 1,
        tabletFractionDenominator = 1,
        doseVolumeMl = null,
        doseWeightGrams = null,
        equivalentE2Mg = null,
        sourceGroupUuid = null,
        appliedAtEpochMillis = 0,
        scheduledForIso = scheduledForIso,
    )
}
