package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.MedicineDao
import com.mkx.hrttracker.data.local.MedicineEntity
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.UUID

class MedicineRepositoryTest {
    private val databaseHolder: DatabaseHolder = mockk()
    private val database: HrtTrackerDatabase = mockk()
    private val dao: MedicineDao = mockk(relaxed = true)
    private val homeSnapshotRepository: HomeSnapshotRepository = mockk(relaxed = true)

    private lateinit var repository: MedicineRepository

    @Before
    fun setUp() {
        every { databaseHolder.databaseFlow } returns MutableStateFlow(null)
        every { databaseHolder.get() } returns database
        every { database.medicineDao() } returns dao
        coEvery { homeSnapshotRepository.runHomeDataMutation<Medicine>(any()) } coAnswers {
            firstArg<suspend () -> Medicine>().invoke()
        }
        coEvery { databaseHolder.withTransaction<Medicine>(any()) } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Medicine>().invoke(database)
        }

        repository = MedicineRepository(
            databaseHolder = databaseHolder,
            homeSnapshotRepository = homeSnapshotRepository,
        )
    }

    @Test
    fun findOrCreateForCatalog_reusesActiveMedicineWithMatchingIdentity() = runTest {
        val entity = medicineEntity(archivedAtEpochMillis = null)
        coEvery { dao.getByIdentityKey("C|ESTRADIOL|PILL|strengthMgPerTablet=2") } returns entity

        val medicine = repository.findOrCreateForCatalog(
            medicationKey = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(2.0),
            now = Instant.ofEpochMilli(500),
        )

        assertEquals(UUID.fromString(entity.uuid), medicine.uuid)
        assertNull(medicine.archivedAt)
        assertEquals(Instant.ofEpochMilli(200), medicine.updatedAt)
        coVerify(exactly = 0) { dao.insert(any()) }
        coVerify(exactly = 0) { dao.unarchive(any(), any()) }
    }

    @Test
    fun findOrCreateForCatalog_revivesArchivedMedicine() = runTest {
        val now = Instant.ofEpochMilli(500)
        val entity = medicineEntity(archivedAtEpochMillis = 300)
        coEvery { dao.getByIdentityKey("C|ESTRADIOL|PILL|strengthMgPerTablet=2") } returns entity
        coEvery { dao.unarchive(entity.uuid, now.toEpochMilli()) } returns Unit

        val medicine = repository.findOrCreateForCatalog(
            medicationKey = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(2.0),
            now = now,
        )

        assertEquals(UUID.fromString(entity.uuid), medicine.uuid)
        assertNull(medicine.archivedAt)
        assertEquals(now, medicine.updatedAt)
        coVerify(exactly = 1) { dao.unarchive(entity.uuid, now.toEpochMilli()) }
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun toMedicineModel_rejectsCustomMedicineWithoutMedicationName() {
        val entity = medicineEntity().copy(
            selectionKind = "CUSTOM",
            medicationKey = null,
            customMedicationName = null,
            customMedicationNameNormalized = null,
            category = MedicationCategory.CUSTOM.name,
        )

        assertThrows(IllegalStateException::class.java) {
            entity.toMedicineModel()
        }
    }

    private fun medicineEntity(
        uuid: String = "aaaaaaaa-0000-0000-0000-000000000000",
        archivedAtEpochMillis: Long? = null,
    ): MedicineEntity {
        return MedicineEntity(
            uuid = uuid,
            selectionKind = "CATALOG",
            medicationKey = "ESTRADIOL",
            customMedicationName = null,
            customMedicationNameNormalized = null,
            category = MedicationCategory.ESTRADIOL.name,
            preparationType = "PILL",
            strengthMgPerTablet = 2.0,
            strengthMgPerVial = null,
            concentrationMgPerMl = null,
            vialVolumeMl = null,
            concentrationPercent = null,
            sachetWeightGrams = null,
            containerWeightGrams = null,
            patchTotalMg = null,
            patchReleaseRateMcgPerDay = null,
            displayName = null,
            identityKey = "C|ESTRADIOL|PILL|strengthMgPerTablet=2",
            createdAtEpochMillis = 100,
            updatedAtEpochMillis = 200,
            archivedAtEpochMillis = archivedAtEpochMillis,
        )
    }
}
