package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.MedicationLogDao
import com.mkx.hrttracker.data.local.MedicationLogEntryEntity
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDoseKind
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class MedicationLogRepositoryTest {
    private val databaseHolder: DatabaseHolder = mockk()
    private val database: HrtTrackerDatabase = mockk()
    private val dao: MedicationLogDao = mockk(relaxed = true)

    private lateinit var repository: MedicationLogRepository

    @Before
    fun setUp() {
        every { databaseHolder.databaseFlow } returns MutableStateFlow(null)
        every { databaseHolder.get() } returns database
        every { database.medicationLogDao() } returns dao

        repository = MedicationLogRepository(
            databaseHolder = databaseHolder,
            appScope = CoroutineScope(StandardTestDispatcher()),
        )
    }

    @Test
    fun deleteAllEntries_clears_logs_in_single_transaction() = runTest {
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { dao.deleteAllEntries() } returns Unit

        repository.deleteAllEntries()

        coVerify(exactly = 1) { databaseHolder.withTransaction<Unit>(any()) }
        coVerify(exactly = 1) { dao.deleteAllEntries() }
    }

    @Test
    fun deleteEntriesForGroup_clears_group_logs_in_single_transaction() = runTest {
        val groupUuid = UUID.fromString("83b0354d-b6f1-4c04-9e35-0d2583e5a07b")
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { dao.deleteEntriesForGroup(groupUuid.toString()) } returns Unit

        repository.deleteEntriesForGroup(groupUuid)

        coVerify(exactly = 1) { databaseHolder.withTransaction<Unit>(any()) }
        coVerify(exactly = 1) { dao.deleteEntriesForGroup(groupUuid.toString()) }
    }

    @Test
    fun preloadRecentEstradiolEntries_cachesRecentEntriesAndCarryEntry() = runTest {
        val since = Instant.parse("2026-04-01T00:00:00Z")
        val until = Instant.parse("2026-04-30T00:00:00Z")
        val recentEntry = testMedicationLogEntryEntity(
            uuid = "8bbef05b-368d-4ae4-9e9d-4a83e35f8d9c",
            appliedAt = since.plus(Duration.ofDays(3)),
        )
        val carryEntry = testMedicationLogEntryEntity(
            uuid = "e93047ed-1825-4f9f-a017-3b93d4f0698e",
            appliedAt = since.minus(Duration.ofHours(8)),
        )
        coEvery {
            dao.getEntriesByCategoryBetween(
                MedicationCategory.ESTRADIOL.name,
                since.toEpochMilli(),
                until.toEpochMilli(),
            )
        } returns listOf(recentEntry)
        coEvery {
            dao.getLatestEntryByCategoryOnOrBefore(
                MedicationCategory.ESTRADIOL.name,
                since.toEpochMilli(),
            )
        } returns carryEntry

        val entries = repository.preloadRecentEstradiolEntries(since, until)

        assertEquals(
            listOf(recentEntry.uuid, carryEntry.uuid),
            entries.map { entry -> entry.uuid.toString() },
        )
        assertEquals(entries, repository.getCachedRecentEstradiolEntries(since.plus(Duration.ofDays(1))))
        assertNull(repository.getCachedRecentEstradiolEntries(since.minusMillis(1)))
        assertNull(repository.getCachedRecentEstradiolEntries(until.plusMillis(1)))
    }

    private fun testMedicationLogEntryEntity(
        uuid: String,
        appliedAt: Instant,
    ): MedicationLogEntryEntity {
        return MedicationLogEntryEntity(
            uuid = uuid,
            category = MedicationCategory.ESTRADIOL.name,
            applicationType = MedicationApplicationType.ORAL.name,
            selectionKind = MedicationSelectionKind.CATALOG.name,
            medicationKey = MedicationKey.ESTRADIOL.name,
            customMedicationName = null,
            doseKind = MedicationDoseKind.MG_AS_MEDICINE.name,
            doseValueMg = 2.0,
            doseValuePercent = null,
            doseWeightGrams = null,
            doseReleaseRateMcgPerDay = null,
            dosageMgAsEstradiol = 2.0,
            sourceGroupUuid = null,
            appliedAtEpochMillis = appliedAt.toEpochMilli(),
        )
    }
}
