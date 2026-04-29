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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
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
    fun getObservedLatestEstradiolEntryOnOrBefore_returnsLatestObservedEntryAtOrBeforeTarget() = runTest {
        val target = Instant.parse("2026-04-30T00:00:00Z")
        val latestEntry = testMedicationLogEntryEntity(
            uuid = "8bbef05b-368d-4ae4-9e9d-4a83e35f8d9c",
            appliedAt = target.minus(Duration.ofDays(60)),
        )
        val olderEntry = testMedicationLogEntryEntity(
            uuid = "e93047ed-1825-4f9f-a017-3b93d4f0698e",
            appliedAt = target.minus(Duration.ofDays(90)),
        )
        val futureEntry = testMedicationLogEntryEntity(
            uuid = "151986f6-7981-4734-ad5b-5366c5dcd931",
            appliedAt = target.plus(Duration.ofHours(1)),
        )
        val antiandrogenEntry = testMedicationLogEntryEntity(
            uuid = "e22f2f1e-1cb5-4137-83ca-f5d35417cbcd",
            appliedAt = target.minus(Duration.ofDays(2)),
            category = MedicationCategory.ANTIANDROGEN,
            medicationKey = MedicationKey.SPIRONOLACTONE,
        )

        val repository = repositoryWithObservedEntries(
            entries = listOf(
                futureEntry,
                latestEntry,
                antiandrogenEntry,
                olderEntry,
            ),
            appScope = CoroutineScope(
                backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)
            ),
        )
        advanceUntilIdle()

        val lookup = repository.getObservedLatestEstradiolEntryOnOrBefore(target)

        assertTrue(lookup is ObservedEstradiolEntryLookup.Loaded)
        assertEquals(
            latestEntry.uuid,
            (lookup as ObservedEstradiolEntryLookup.Loaded).entry?.uuid.toString(),
        )
    }

    @Test
    fun getObservedLatestEstradiolEntryOnOrBefore_returnsNotLoadedBeforeEntriesFlowLoads() {
        assertEquals(
            ObservedEstradiolEntryLookup.NotLoaded,
            repository.getObservedLatestEstradiolEntryOnOrBefore(
                Instant.parse("2026-04-30T00:00:00Z")
            ),
        )
    }

    private fun repositoryWithObservedEntries(
        entries: List<MedicationLogEntryEntity>,
        appScope: CoroutineScope,
    ): MedicationLogRepository {
        every { databaseHolder.databaseFlow } returns MutableStateFlow<HrtTrackerDatabase?>(database)
        every { databaseHolder.get() } returns database
        every { database.medicationLogDao() } returns dao
        every { dao.observeEntries() } returns MutableStateFlow(entries)

        return MedicationLogRepository(
            databaseHolder = databaseHolder,
            appScope = appScope,
        )
    }

    private fun testMedicationLogEntryEntity(
        uuid: String,
        appliedAt: Instant,
        category: MedicationCategory = MedicationCategory.ESTRADIOL,
        medicationKey: MedicationKey = MedicationKey.ESTRADIOL,
    ): MedicationLogEntryEntity {
        return MedicationLogEntryEntity(
            uuid = uuid,
            category = category.name,
            applicationType = MedicationApplicationType.ORAL.name,
            selectionKind = MedicationSelectionKind.CATALOG.name,
            medicationKey = medicationKey.name,
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
