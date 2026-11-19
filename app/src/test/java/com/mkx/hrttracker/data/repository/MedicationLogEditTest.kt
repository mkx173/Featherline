package com.mkx.hrttracker.data.repository

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.MedicationLogEntryEntity
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

/**
 * The log sheet edits when a record was taken; it has no count editor. For a vial or a gel
 * container it holds a normalized count of 1, which must not replace the stored count the
 * dose and PK multiply by.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MedicationLogEditTest {
    private val databaseHolder: DatabaseHolder = mockk()
    private val gate: HomeSnapshotRepository = mockk()

    private lateinit var database: HrtTrackerDatabase
    private lateinit var logRepository: MedicationLogRepository

    private val first = UUID.fromString("ed170000-0000-0000-0000-000000000001")
    private val second = UUID.fromString("ed170000-0000-0000-0000-000000000002")
    private val editedAt = Instant.parse("2026-11-08T09:30:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HrtTrackerDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        every { databaseHolder.get() } returns database
        every { databaseHolder.databaseFlow } returns MutableStateFlow(null)
        coEvery { databaseHolder.withTransaction<Any?>(any()) } coAnswers {
            database.withTransaction {
                firstArg<suspend (HrtTrackerDatabase) -> Any?>().invoke(database)
            }
        }
        coEvery { gate.runHomeDataMutation<Any?>(any()) } coAnswers {
            firstArg<suspend () -> Any?>().invoke()
        }
        logRepository = MedicationLogRepository(
            databaseHolder = databaseHolder,
            homeSnapshotRepository = gate,
            stockMutator = MedicineStockMutator(),
            appScope = CoroutineScope(StandardTestDispatcher()),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aTimeOnlyEditKeepsTheStoredCount() = runTest {
        database.medicationLogDao().insertEntries(listOf(vialRecord(first, count = 2)))

        logRepository.saveEntry(
            uuid = first,
            medicineUuid = null,
            applicationType = MedicationApplicationType.INJECTION,
            doseInstruction = DoseInstruction.VolumeMl(0.5),
            sourceGroupUuid = null,
            appliedAt = editedAt,
            count = 1,
        )

        val edited = checkNotNull(database.medicationLogDao().getEntry(first.toString()))
        assertEquals("applied at", editedAt.toEpochMilli(), edited.appliedAtEpochMillis)
        assertEquals("count", 2, edited.count)
    }

    @Test
    fun aTimeOnlyBulkEditKeepsEachRecordsCount() = runTest {
        database.medicationLogDao().insertEntries(
            listOf(vialRecord(first, count = 2), vialRecord(second, count = 3))
        )

        logRepository.saveEntries(
            uuids = listOf(first, second),
            medicineUuid = null,
            applicationType = MedicationApplicationType.INJECTION,
            doseInstruction = DoseInstruction.VolumeMl(0.5),
            sourceGroupUuid = null,
            appliedAt = editedAt,
            count = 1,
        )

        val edited = listOf(first, second).map { uuid ->
            checkNotNull(database.medicationLogDao().getEntry(uuid.toString()))
        }
        assertEquals(
            "applied at",
            listOf(editedAt.toEpochMilli(), editedAt.toEpochMilli()),
            edited.map { it.appliedAtEpochMillis },
        )
        assertEquals("counts", listOf(2, 3), edited.map { it.count })
    }

    private fun vialRecord(uuid: UUID, count: Int) = MedicationLogEntryEntity(
        uuid = uuid.toString(),
        category = "ESTRADIOL",
        medicineUuid = null,
        applicationType = MedicationApplicationType.INJECTION.name,
        doseInstructionKind = "VOLUME_ML",
        tabletFractionNumerator = null,
        tabletFractionDenominator = null,
        doseVolumeMl = 0.5,
        doseWeightGrams = null,
        equivalentE2Mg = null,
        sourceGroupUuid = null,
        appliedAtEpochMillis = 0,
        count = count,
    )
}
