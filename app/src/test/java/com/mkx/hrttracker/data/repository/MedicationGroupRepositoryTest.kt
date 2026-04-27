package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.MedicationGroupDao
import com.mkx.hrttracker.data.local.MedicationLogDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.UUID

class MedicationGroupRepositoryTest {
    private val databaseHolder: DatabaseHolder = mockk()
    private val database: HrtTrackerDatabase = mockk()
    private val medicationGroupDao: MedicationGroupDao = mockk(relaxed = true)
    private val medicationLogDao: MedicationLogDao = mockk(relaxed = true)

    private lateinit var repository: MedicationGroupRepository

    @Before
    fun setUp() {
        every { databaseHolder.databaseFlow } returns MutableStateFlow(null)
        every { databaseHolder.get() } returns database
        every { database.medicationGroupDao() } returns medicationGroupDao
        every { database.medicationLogDao() } returns medicationLogDao

        repository = MedicationGroupRepository(
            databaseHolder = databaseHolder,
            appScope = CoroutineScope(StandardTestDispatcher()),
        )
    }

    @Test
    fun deleteGroup_reclassifiesEntriesAndDeletesGroupInSingleTransaction() = runTest {
        val groupUuid = UUID.fromString("14f6c652-a26d-4b68-ac54-c70cbec929d9")
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { medicationLogDao.reclassifyEntriesForDeletedGroup(groupUuid.toString()) } returns Unit
        coEvery { medicationGroupDao.deleteGroup(groupUuid.toString()) } returns Unit

        repository.deleteGroup(groupUuid)

        coVerify(exactly = 1) { databaseHolder.withTransaction<Unit>(any()) }
        coVerifyOrder {
            medicationLogDao.reclassifyEntriesForDeletedGroup(groupUuid.toString())
            medicationGroupDao.deleteGroup(groupUuid.toString())
        }
    }

    @Test
    fun deleteGroupAndRelatedEntries_deletesEntriesAndGroupInSingleTransaction() = runTest {
        val groupUuid = UUID.fromString("f2f8890f-09ab-4775-85cb-cf4aa896f0b7")
        coEvery {
            databaseHolder.withTransaction<Unit>(any())
        } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { medicationLogDao.deleteEntriesForGroup(groupUuid.toString()) } returns Unit
        coEvery { medicationGroupDao.deleteGroup(groupUuid.toString()) } returns Unit

        repository.deleteGroupAndRelatedEntries(groupUuid)

        coVerify(exactly = 1) { databaseHolder.withTransaction<Unit>(any()) }
        coVerifyOrder {
            medicationLogDao.deleteEntriesForGroup(groupUuid.toString())
            medicationGroupDao.deleteGroup(groupUuid.toString())
        }
    }
}
