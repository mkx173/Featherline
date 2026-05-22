package com.mkx.hrttracker.data.repository

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class MedicineRepositoryConcurrencyTest {
    private lateinit var database: HrtTrackerDatabase
    private lateinit var repository: MedicineRepository

    private val databaseHolder: DatabaseHolder = mockk()
    private val homeSnapshotRepository: HomeSnapshotRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, HrtTrackerDatabase::class.java)
            .build()

        every { databaseHolder.databaseFlow } returns MutableStateFlow(database)
        every { databaseHolder.get() } returns database
        coEvery { databaseHolder.withTransaction<Medicine>(any()) } coAnswers {
            database.withTransaction {
                firstArg<suspend (HrtTrackerDatabase) -> Medicine>().invoke(database)
            }
        }
        coEvery { homeSnapshotRepository.runHomeDataMutation<Medicine>(any()) } coAnswers {
            firstArg<suspend () -> Medicine>().invoke()
        }

        repository = MedicineRepository(
            databaseHolder = databaseHolder,
            homeSnapshotRepository = homeSnapshotRepository,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun findOrCreateForCatalog_concurrentCallsCreateOneMedicine() = runBlocking {
        val medicines = (0 until 8).map {
            async(Dispatchers.Default) {
                repository.findOrCreateForCatalog(
                    medicationKey = MedicationKey.ESTRADIOL,
                    preparation = MedicinePreparation.Pill(2.0),
                    now = Instant.ofEpochMilli(1_000),
                )
            }
        }.awaitAll()

        val distinctUuids = medicines.map { it.uuid }.toSet()
        val storedRows = database.medicineDao().getByUuids(distinctUuids.map { it.toString() })

        assertEquals(1, distinctUuids.size)
        assertEquals(1, storedRows.size)
    }
}
