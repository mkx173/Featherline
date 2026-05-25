package com.mkx.hrttracker.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.MedicineDao
import com.mkx.hrttracker.data.local.MedicineEntity
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
    fun findOrCreateForCatalog_insertsNewMedicineWithExpectedStorageFields() = runTest {
        val now = Instant.ofEpochMilli(500)
        val inserted = slot<MedicineEntity>()
        coEvery { dao.getByIdentityKey("C|ESTRADIOL|PILL|strengthMgPerTablet=2") } returns null
        coEvery { dao.insert(capture(inserted)) } returns Unit

        val medicine = repository.findOrCreateForCatalog(
            medicationKey = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(2.0),
            now = now,
        )

        assertEquals(medicine.uuid.toString(), inserted.captured.uuid)
        assertEquals("CATALOG", inserted.captured.selectionKind)
        assertEquals("ESTRADIOL", inserted.captured.medicationKey)
        assertNull(inserted.captured.customMedicationName)
        assertNull(inserted.captured.customMedicationNameNormalized)
        assertEquals("ESTRADIOL", inserted.captured.category)
        assertEquals("PILL", inserted.captured.preparationType)
        assertEquals(2.0, inserted.captured.strengthMgPerTablet ?: 0.0, 0.0)
        assertEquals("C|ESTRADIOL|PILL|strengthMgPerTablet=2", inserted.captured.identityKey)
        assertEquals(500, inserted.captured.createdAtEpochMillis)
        assertEquals(500, inserted.captured.updatedAtEpochMillis)
        assertNull(inserted.captured.archivedAtEpochMillis)
        assertEquals(medicine.identityKey, inserted.captured.identityKey)
    }

    @Test
    fun findOrCreateForCustom_usesNormalizedIdentityAndStoresNormalizedName() = runTest {
        val now = Instant.ofEpochMilli(500)
        val inserted = slot<MedicineEntity>()
        val expectedIdentityKey = "X|progesterone micronized|PILL|strengthMgPerTablet=100"
        coEvery { dao.getByIdentityKey(expectedIdentityKey) } returns null
        coEvery { dao.insert(capture(inserted)) } returns Unit

        val medicine = repository.findOrCreateForCustom(
            customMedicationName = "  Progesterone   Micronized  ",
            displayName = "Bedtime",
            category = MedicationCategory.CUSTOM,
            preparation = MedicinePreparation.Pill(100.0),
            now = now,
        )

        val selection = medicine.selection as MedicineSelection.Custom
        assertEquals("  Progesterone   Micronized  ", selection.medicationName)
        assertEquals(expectedIdentityKey, medicine.identityKey)
        assertEquals("CUSTOM", inserted.captured.selectionKind)
        assertEquals("  Progesterone   Micronized  ", inserted.captured.customMedicationName)
        assertEquals("progesterone micronized", inserted.captured.customMedicationNameNormalized)
        assertEquals(expectedIdentityKey, inserted.captured.identityKey)
        assertEquals("Bedtime", inserted.captured.displayName)
    }

    @Test
    fun findOrCreateForCatalog_patchMedicineAutoCreatesPatchOffSingleton() = runTest {
        val now = Instant.ofEpochMilli(500)
        val patchKey = "C|ESTRADIOL_PATCH|PATCH|patchTotalMg=4"
        val patchOffKey = "P|PATCH_OFF"
        val insertedEntities = mutableListOf<MedicineEntity>()
        coEvery { dao.getByIdentityKey(patchKey) } returns null
        coEvery { dao.getByIdentityKey(patchOffKey) } returns null
        coEvery { dao.insert(capture(insertedEntities)) } returns Unit

        repository.findOrCreateForCatalog(
            medicationKey = MedicationKey.ESTRADIOL_PATCH,
            preparation = MedicinePreparation.Patch(
                MedicinePreparation.PatchSpecification.TotalMg(4.0),
            ),
            now = now,
        )

        // Both the patch medicine and the singleton must have been inserted.
        val identities = insertedEntities.map(MedicineEntity::identityKey)
        assertTrue(
            "Expected $patchOffKey alongside $patchKey, got $identities",
            patchOffKey in identities && patchKey in identities,
        )
        val patchOffEntity = insertedEntities.first { it.identityKey == patchOffKey }
        assertEquals("PATCH_OFF", patchOffEntity.preparationType)
        assertEquals("ESTRADIOL", patchOffEntity.category)
        assertNull(patchOffEntity.patchTotalMg)
        assertNull(patchOffEntity.patchReleaseRateMcgPerDay)
        // Round-trip back through the model to prove selection + preparation
        // hydrate as PatchOff, not as a catalog row with stray columns.
        val patchOffModel = patchOffEntity.toMedicineModel()
        assertTrue(
            "Expected PatchOff selection, got ${patchOffModel.selection}",
            patchOffModel.selection is MedicineSelection.PatchOff,
        )
        assertTrue(
            "Expected PatchOff preparation, got ${patchOffModel.preparation}",
            patchOffModel.preparation is MedicinePreparation.PatchOff,
        )
        assertEquals(MedicationCategory.ESTRADIOL, patchOffModel.category)
    }

    @Test
    fun findOrCreateForCatalog_patchMedicineDoesNotInsertSecondPatchOff() = runTest {
        val now = Instant.ofEpochMilli(500)
        val existingPatchOff = patchOffMedicineEntity()
        coEvery { dao.getByIdentityKey("C|ESTRADIOL_PATCH|PATCH|patchTotalMg=4") } returns null
        coEvery { dao.getByIdentityKey("P|PATCH_OFF") } returns existingPatchOff
        val insertedEntities = mutableListOf<MedicineEntity>()
        coEvery { dao.insert(capture(insertedEntities)) } returns Unit

        repository.findOrCreateForCatalog(
            medicationKey = MedicationKey.ESTRADIOL_PATCH,
            preparation = MedicinePreparation.Patch(
                MedicinePreparation.PatchSpecification.TotalMg(4.0),
            ),
            now = now,
        )

        // Only the new patch medicine inserts; the existing singleton is reused.
        val identities = insertedEntities.map(MedicineEntity::identityKey)
        assertEquals(listOf("C|ESTRADIOL_PATCH|PATCH|patchTotalMg=4"), identities)
    }

    @Test
    fun findOrCreatePatchOff_isIdempotentAcrossCalls() = runTest {
        val now = Instant.ofEpochMilli(500)
        val existingPatchOff = patchOffMedicineEntity()
        coEvery { dao.getByIdentityKey("P|PATCH_OFF") } returnsMany listOf(null, existingPatchOff)
        val inserted = slot<MedicineEntity>()
        coEvery { dao.insert(capture(inserted)) } returns Unit

        val first = repository.findOrCreatePatchOff(now)
        val second = repository.findOrCreatePatchOff(now)

        assertEquals(UUID.fromString(inserted.captured.uuid), first.uuid)
        // Second call hits the existing-row path and never re-inserts.
        assertEquals(UUID.fromString(existingPatchOff.uuid), second.uuid)
        coVerify(exactly = 1) { dao.insert(any()) }
    }

    @Test
    fun findOrCreateForCatalog_nonPatchDoesNotInsertPatchOff() = runTest {
        val now = Instant.ofEpochMilli(500)
        val insertedEntities = mutableListOf<MedicineEntity>()
        coEvery { dao.getByIdentityKey("C|ESTRADIOL|PILL|strengthMgPerTablet=2") } returns null
        coEvery { dao.insert(capture(insertedEntities)) } returns Unit

        repository.findOrCreateForCatalog(
            medicationKey = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(2.0),
            now = now,
        )

        // The auto-create hook only fires for patches.
        assertTrue(
            "Patch-off should not be inserted for a pill medicine",
            insertedEntities.none { it.identityKey == "P|PATCH_OFF" },
        )
    }

    @Test
    fun findOrCreateForCustom_rejectsBlankCustomMedicationNameBeforeMutation() = runTest {
        try {
            repository.findOrCreateForCustom(
                customMedicationName = " \t\n ",
                displayName = null,
                category = MedicationCategory.CUSTOM,
                preparation = MedicinePreparation.Pill(100.0),
                now = Instant.ofEpochMilli(500),
            )
            fail("Expected blank custom medicine name to be rejected")
        } catch (_: IllegalArgumentException) {
        }

        coVerify(exactly = 0) { homeSnapshotRepository.runHomeDataMutation<Medicine>(any()) }
        coVerify(exactly = 0) { dao.getByIdentityKey(any()) }
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun findOrCreateForCatalog_returnsExistingMedicineAfterUniqueConstraintRace() = runTest {
        val entity = medicineEntity(archivedAtEpochMillis = null)
        coEvery {
            dao.getByIdentityKey("C|ESTRADIOL|PILL|strengthMgPerTablet=2")
        } returns null andThen entity
        coEvery { dao.insert(any()) } throws SQLiteConstraintException("identity conflict")

        val medicine = repository.findOrCreateForCatalog(
            medicationKey = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(2.0),
            now = Instant.ofEpochMilli(500),
        )

        assertEquals(UUID.fromString(entity.uuid), medicine.uuid)
        assertEquals(Instant.ofEpochMilli(200), medicine.updatedAt)
        coVerify(exactly = 1) { dao.insert(any()) }
        coVerify(exactly = 0) { dao.unarchive(any(), any()) }
    }

    @Test
    fun setDisplayName_updatesObservedMedicineByUuid() = runTest {
        val uuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000000")
        val original = medicineEntity(uuid = uuid.toString())
        val observedEntity = MutableStateFlow(original)
        every { databaseHolder.databaseFlow } returns MutableStateFlow(database)
        every { dao.observeByUuid(uuid.toString()) } returns observedEntity
        coEvery { homeSnapshotRepository.runHomeDataMutation<Unit>(any()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
        }
        coEvery { databaseHolder.withTransaction<Unit>(any()) } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { dao.getByUuid(uuid.toString()) } returns original
        coEvery {
            dao.updateDisplayName(
                uuid = uuid.toString(),
                displayName = "New",
                updatedAtEpochMillis = 500,
            )
        } coAnswers {
            observedEntity.value = original.copy(
                displayName = "New",
                updatedAtEpochMillis = 500,
            )
        }

        repository.setDisplayName(
            uuid = uuid,
            name = "New",
            now = Instant.ofEpochMilli(500),
        )

        val observed = repository.observeByUuid(uuid).first()
        assertEquals("New", observed?.displayName)
        assertEquals(Instant.ofEpochMilli(500), observed?.updatedAt)
    }

    @Test
    fun setDisplayName_throwsWhenMedicineDoesNotExist() = runTest {
        val uuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000000")
        coEvery { homeSnapshotRepository.runHomeDataMutation<Unit>(any()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
        }
        coEvery { databaseHolder.withTransaction<Unit>(any()) } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { dao.getByUuid(uuid.toString()) } returns null

        try {
            repository.setDisplayName(
                uuid = uuid,
                name = "Display",
                now = Instant.ofEpochMilli(500),
            )
            fail("Expected missing medicine to reject display name update")
        } catch (_: MedicineNotFoundException) {
        }

        coVerify(exactly = 0) { dao.updateDisplayName(any(), any(), any()) }
    }

    @Test
    fun updatePreparation_rejectsLockedMedicine() = runTest {
        val uuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000")
        coEvery { homeSnapshotRepository.runHomeDataMutation<Unit>(any()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
        }
        coEvery { databaseHolder.withTransaction<Unit>(any()) } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { dao.getByUuid(uuid.toString()) } returns medicineEntity(uuid = uuid.toString())
        coEvery { dao.logReferenceCount(uuid.toString()) } returns 1

        assertThrows(MedicineLockedException::class.java) {
            kotlinx.coroutines.test.runTest {
                repository.updatePreparation(
                    uuid = uuid,
                    preparation = MedicinePreparation.Pill(strengthMgPerTablet = 4.0),
                    now = Instant.parse("2026-05-22T00:00:00Z"),
                )
            }
        }
    }

    @Test
    fun archive_rejectsMedicineReferencedByActiveGroup() = runTest {
        val uuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000")
        coEvery { homeSnapshotRepository.runHomeDataMutation<Unit>(any()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
        }
        coEvery { databaseHolder.withTransaction<Unit>(any()) } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery { dao.activeGroupReferenceCount(uuid.toString()) } returns 1

        assertThrows(MedicineReferencedByActiveGroupException::class.java) {
            kotlinx.coroutines.test.runTest {
                repository.archive(uuid, now = Instant.parse("2026-05-22T00:00:00Z"))
            }
        }
    }

    @Test
    fun isLocked_trueExactlyWhenALogReferencesTheMedicine() = runTest {
        val uuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000")
        coEvery { dao.logReferenceCount(uuid.toString()) } returnsMany listOf(0, 1)

        assertFalse(repository.isLocked(uuid))
        assertTrue(repository.isLocked(uuid))
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

    @Test
    fun toMedicineModel_rejectsCatalogMedicineWithMismatchedIdentityKey() {
        val entity = medicineEntity().copy(
            identityKey = "C|ESTRADIOL|PILL|strengthMgPerTablet=4",
        )

        assertThrows(IllegalStateException::class.java) {
            entity.toMedicineModel()
        }
    }

    @Test
    fun toMedicineModel_rejectsCustomMedicineWithMismatchedIdentityKey() {
        val entity = customMedicineEntity().copy(
            identityKey = "X|progesterone|PILL|strengthMgPerTablet=200",
        )

        assertThrows(IllegalStateException::class.java) {
            entity.toMedicineModel()
        }
    }

    @Test
    fun toMedicineModel_rejectsCustomMedicineWithMissingNormalizedName() {
        val entity = customMedicineEntity().copy(customMedicationNameNormalized = null)

        assertThrows(IllegalStateException::class.java) {
            entity.toMedicineModel()
        }
    }

    @Test
    fun toMedicineModel_rejectsCustomMedicineWithMismatchedNormalizedName() {
        val entity = customMedicineEntity().copy(customMedicationNameNormalized = "progesteron")

        assertThrows(IllegalStateException::class.java) {
            entity.toMedicineModel()
        }
    }

    @Test
    fun toMedicineModel_rejectsPillWithIrrelevantPreparationFields() {
        val entity = medicineEntity().copy(strengthMgPerVial = 10.0)

        assertThrows(IllegalStateException::class.java) {
            entity.toMedicineModel()
        }
    }

    @Test
    fun toMedicineModel_rejectsPatchWithBothSpecifications() {
        val entity = medicineEntity().copy(
            preparationType = "PATCH",
            strengthMgPerTablet = null,
            patchTotalMg = 4.0,
            patchReleaseRateMcgPerDay = 100.0,
        )

        assertThrows(IllegalStateException::class.java) {
            entity.toMedicineModel()
        }
    }

    @Test
    fun toMedicineModel_rejectsPatchWithoutSpecification() {
        val entity = medicineEntity().copy(
            preparationType = "PATCH",
            strengthMgPerTablet = null,
            patchTotalMg = null,
            patchReleaseRateMcgPerDay = null,
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

    private fun customMedicineEntity(): MedicineEntity {
        return medicineEntity().copy(
            selectionKind = "CUSTOM",
            medicationKey = null,
            customMedicationName = "  Progesterone  ",
            customMedicationNameNormalized = "progesterone",
            category = MedicationCategory.CUSTOM.name,
            strengthMgPerTablet = 100.0,
            identityKey = "X|progesterone|PILL|strengthMgPerTablet=100",
        )
    }

    private fun patchOffMedicineEntity(): MedicineEntity {
        // Mirrors what MedicineRepository.findOrCreatePatchOff inserts.
        return medicineEntity().copy(
            uuid = "cccccccc-0000-0000-0000-000000000000",
            selectionKind = "CATALOG",
            medicationKey = null,
            customMedicationName = null,
            customMedicationNameNormalized = null,
            category = MedicationCategory.ESTRADIOL.name,
            preparationType = "PATCH_OFF",
            strengthMgPerTablet = null,
            identityKey = "P|PATCH_OFF",
        )
    }
}
