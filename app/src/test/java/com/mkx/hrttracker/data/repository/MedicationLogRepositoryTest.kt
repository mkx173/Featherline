package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.MedicationLogDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

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
}
