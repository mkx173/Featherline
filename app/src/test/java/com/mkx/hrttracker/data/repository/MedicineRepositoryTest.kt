package com.mkx.hrttracker.data.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.MedicineDao
import com.mkx.hrttracker.data.local.MedicineEntity
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.util.ToastManager
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
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
    private val context: Context = mockk()
    private val databaseHolder: DatabaseHolder = mockk()
    private val database: HrtTrackerDatabase = mockk()
    private val dao: MedicineDao = mockk(relaxed = true)
    private val homeSnapshotRepository: HomeSnapshotRepository = mockk(relaxed = true)
    private val stockMutator: MedicineStockMutator = mockk(relaxed = true)
    private val duplicateMedicineMessage = "Medicine already exists."

    private lateinit var appScope: CoroutineScope
    private lateinit var repository: MedicineRepository

    @Before
    fun setUp() {
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        mockkObject(ToastManager)
        every { ToastManager.showMessage(any()) } just Runs

        every { context.getString(R.string.medicine_already_exists) } returns
            duplicateMedicineMessage
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
            context = context,
            databaseHolder = databaseHolder,
            homeSnapshotRepository = homeSnapshotRepository,
            stockMutator = stockMutator,
            appScope = appScope,
        )
    }

    @After
    fun tearDown() {
        appScope.cancel()
        unmockkObject(ToastManager)
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
    fun findOrCreateForCatalog_showsToastWhenActiveMedicineWithMatchingIdentityExists() = runTest {
        val entity = medicineEntity(archivedAtEpochMillis = null)
        coEvery { dao.getByIdentityKey("C|ESTRADIOL|PILL|strengthMgPerTablet=2") } returns entity

        repository.findOrCreateForCatalog(
            medicationKey = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(2.0),
            now = Instant.ofEpochMilli(500),
        )

        verify(exactly = 1) { ToastManager.showMessage(duplicateMedicineMessage) }
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
    fun findOrCreateForCatalog_doesNotShowToastWhenMatchingMedicineIsArchived() = runTest {
        val now = Instant.ofEpochMilli(500)
        val entity = medicineEntity(archivedAtEpochMillis = 300)
        coEvery { dao.getByIdentityKey("C|ESTRADIOL|PILL|strengthMgPerTablet=2") } returns entity
        coEvery { dao.unarchive(entity.uuid, now.toEpochMilli()) } returns Unit

        repository.findOrCreateForCatalog(
            medicationKey = MedicationKey.ESTRADIOL,
            preparation = MedicinePreparation.Pill(2.0),
            now = now,
        )

        verify(exactly = 0) { ToastManager.showMessage(any()) }
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
    fun observeAllActive_sharesSingleDaoSubscriptionAcrossCollectors() = runTest {
        val entity = medicineEntity()
        every { databaseHolder.databaseFlow } returns MutableStateFlow(database)
        every { dao.observeAllActive() } returns MutableStateFlow(listOf(entity))
        val repository = MedicineRepository(
            context = context,
            databaseHolder = databaseHolder,
            homeSnapshotRepository = homeSnapshotRepository,
            stockMutator = stockMutator,
            appScope = appScope,
        )

        val first = async { repository.observeAllActive().first() }
        val second = async { repository.observeAllActive().first() }
        advanceUntilIdle()

        assertEquals(listOf(entity.toMedicineModel()), first.await())
        assertEquals(listOf(entity.toMedicineModel()), second.await())
        verify(exactly = 1) { dao.observeAllActive() }
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

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @Test
    fun observeByUuid_recoversAfterTransientMalformedRow() = runTest {
        val uuid = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000000")
        val observedEntity = MutableStateFlow<MedicineEntity?>(
            medicineEntity(uuid = uuid.toString())
        )
        every { databaseHolder.databaseFlow } returns MutableStateFlow(database)
        every { dao.observeByUuid(uuid.toString()) } returns observedEntity

        val emissions = mutableListOf<Medicine?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeByUuid(uuid).collect { medicine -> emissions += medicine }
        }
        advanceUntilIdle()
        assertEquals(uuid, emissions.last()?.uuid)

        observedEntity.value = medicineEntity(uuid = "not-a-uuid")
        advanceUntilIdle()

        observedEntity.value = medicineEntity(uuid = uuid.toString()).copy(displayName = "Recovered")
        advanceUntilIdle()

        assertEquals(
            "observeByUuid() must recover after a transient malformed-row failure",
            "Recovered",
            emissions.last()?.displayName,
        )
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
        stubUnitMutation()
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
    fun updatePreparation_trackingOn_changedPreparation_preservesStock() = runTest {
        // Editing only the numeric preparation fields (2 mg → 4 mg tablets)
        // keeps the physical tablet count valid: tracking must stay on and the
        // stock must not be wiped — the prep fields are rewritten in place.
        val uuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000")
        val existing = medicineEntity(uuid = uuid.toString()).copy(trackingEnabled = true)
        stubUnitMutation()
        coEvery { dao.getByUuid(uuid.toString()) } returns existing
        coEvery { dao.logReferenceCount(uuid.toString()) } returns 0
        coEvery { dao.getByIdentityKey(any()) } returns null

        repository.updatePreparation(
            uuid = uuid,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 4.0),
            now = Instant.parse("2026-05-22T00:00:00Z"),
        )

        coVerify(exactly = 0) {
            stockMutator.clearStockOnPreparationEdit(any(), any(), any())
        }
        coVerify(exactly = 1) {
            dao.updatePreparationFields(
                uuid.toString(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }
    }

    @Test
    fun updatePreparation_trackingOn_unchangedPreparation_doesNotClearStock() = runTest {
        // A display-name-only edit routes through the merged editor, which (when
        // the medicine is unlocked) still hands updatePreparation the existing,
        // untouched preparation. Re-saving the same preparation must not wipe
        // stock — otherwise renaming a tracked medicine silently disables
        // tracking. The default entity is Pill(2.0); we re-submit the same.
        val uuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000")
        val existing = medicineEntity(uuid = uuid.toString()).copy(trackingEnabled = true)
        stubUnitMutation()
        coEvery { dao.getByUuid(uuid.toString()) } returns existing
        coEvery { dao.logReferenceCount(uuid.toString()) } returns 0
        coEvery { dao.getByIdentityKey(any()) } returns null

        repository.updatePreparation(
            uuid = uuid,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0),
            now = Instant.parse("2026-05-22T00:00:00Z"),
        )

        coVerify(exactly = 0) {
            stockMutator.clearStockOnPreparationEdit(any(), any(), any())
        }
    }

    @Test
    fun updatePreparation_trackingOff_doesNotInvokeStockClear() = runTest {
        val uuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000")
        val existing = medicineEntity(uuid = uuid.toString()).copy(trackingEnabled = false)
        stubUnitMutation()
        coEvery { dao.getByUuid(uuid.toString()) } returns existing
        coEvery { dao.logReferenceCount(uuid.toString()) } returns 0
        coEvery { dao.getByIdentityKey(any()) } returns null

        repository.updatePreparation(
            uuid = uuid,
            preparation = MedicinePreparation.Pill(strengthMgPerTablet = 4.0),
            now = Instant.parse("2026-05-22T00:00:00Z"),
        )

        coVerify(exactly = 0) {
            stockMutator.clearStockOnPreparationEdit(any(), any(), any())
        }
    }

    @Test
    fun disableTracking_invokesStockClearInsideTransaction() = runTest {
        val uuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000")
        val now = Instant.parse("2026-05-22T00:00:00Z")
        stubUnitMutation()

        repository.disableTracking(uuid, now = now)

        coVerify(exactly = 1) {
            homeSnapshotRepository.runHomeDataMutation<Unit>(any())
        }
        coVerify(exactly = 1) {
            databaseHolder.withTransaction<Unit>(any())
        }
        coVerify(exactly = 1) {
            stockMutator.disableTracking(database, uuid, now)
        }
    }

    @Test
    fun archive_rejectsMedicineReferencedByActiveGroup() = runTest {
        val uuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000")
        stubUnitMutation()
        coEvery { dao.activeGroupReferenceCount(uuid.toString()) } returns 1

        assertThrows(MedicineReferencedByActiveGroupException::class.java) {
            kotlinx.coroutines.test.runTest {
                repository.archive(uuid, now = Instant.parse("2026-05-22T00:00:00Z"))
            }
        }

        coVerify(exactly = 0) {
            stockMutator.clearStockOnArchive(any(), any(), any())
        }
    }

    @Test
    fun archive_invokesStockClearInsideTransaction() = runTest {
        val uuid = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000")
        val now = Instant.parse("2026-05-22T00:00:00Z")
        stubUnitMutation()
        coEvery { dao.activeGroupReferenceCount(uuid.toString()) } returns 0

        repository.archive(uuid, now = now)

        coVerifyOrder {
            stockMutator.clearStockOnArchive(database, uuid, now)
            dao.archive(
                uuid = uuid.toString(),
                archivedAtEpochMillis = now.toEpochMilli(),
                updatedAtEpochMillis = now.toEpochMilli(),
            )
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

    // Sibling of the "Plan screen doesn't update after restore" bug. activeMedicinesFlow
    // is an Eagerly, app-scoped StateFlow built inside flatMapLatest(databaseFlow); a
    // terminal `.catch` there would complete the upstream Room observation on the first
    // failed mapping (e.g. a corrupt row from a restore) and freeze the Medicines list
    // at emptyList() until the process — and thus databaseFlow — is rebuilt. The mapping
    // must instead degrade a single emission and recover on the next valid one.
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @Test
    fun observeAllActive_recoversAfterTransientMalformedRow() = runTest {
        val medicinesSource = MutableStateFlow(listOf(medicineEntity()))
        every { databaseHolder.databaseFlow } returns MutableStateFlow(database)
        every { dao.observeAllActive() } returns medicinesSource

        val freshRepository = MedicineRepository(
            context = context,
            databaseHolder = databaseHolder,
            homeSnapshotRepository = homeSnapshotRepository,
            stockMutator = stockMutator,
            appScope = CoroutineScope(
                backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)
            ),
        )

        val emissions = mutableListOf<List<Medicine>?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            freshRepository.observeAllActiveOrNull().collect { emissions += it }
        }

        advanceUntilIdle()
        assertEquals(1, emissions.last()?.size)

        // A corrupt row (unparseable UUID) makes toMedicineModel throw for this emission.
        medicinesSource.value = listOf(medicineEntity(uuid = "not-a-uuid"))
        advanceUntilIdle()

        // A subsequent valid emission must be reflected — proving the flow did not freeze.
        val recoveredUuid = "aaaaaaaa-0000-0000-0000-000000000001"
        medicinesSource.value = listOf(medicineEntity(uuid = recoveredUuid))
        advanceUntilIdle()

        assertEquals(
            "observeAllActive() must recover after a transient malformed-row failure",
            UUID.fromString(recoveredUuid),
            emissions.last()?.singleOrNull()?.uuid,
        )
    }

    // Companion to the malformed-row test above, guarding the OTHER failure surface:
    // an error from the Room flow ITSELF (not the per-row mapping) — e.g. a corrupt
    // database or disk I/O fault surfacing the query as an exception. The terminal
    // `.catch` must degrade that to an empty list. Without it the exception reaches
    // the Eagerly, app-scoped stateIn collector running in appScope (a SupervisorJob
    // with no CoroutineExceptionHandler) and crashes the process instead of leaving a
    // stale/empty screen.
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @Test
    fun observeAllActive_degradesToEmptyWhenRoomFlowErrors() = runTest {
        every { databaseHolder.databaseFlow } returns MutableStateFlow(database)
        every { dao.observeAllActive() } returns flow {
            emit(listOf(medicineEntity()))
            throw IllegalStateException("simulated Room query failure")
        }

        val freshRepository = MedicineRepository(
            context = context,
            databaseHolder = databaseHolder,
            homeSnapshotRepository = homeSnapshotRepository,
            stockMutator = stockMutator,
            appScope = CoroutineScope(
                backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)
            ),
        )

        val emissions = mutableListOf<List<Medicine>?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            freshRepository.observeAllActiveOrNull().collect { emissions += it }
        }

        advanceUntilIdle()

        // The failing Room flow must surface as an empty list, not propagate out of
        // the app-scoped collector (which would otherwise fail this test with the
        // uncaught IllegalStateException).
        assertEquals(emptyList<Medicine>(), emissions.last())
    }

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @Test
    fun observeAllActiveTracked_emitsOnlyTrackedMedicines() = runTest {
        val tracked = medicineEntity(uuid = "aaaaaaaa-0000-0000-0000-000000000001")
            .copy(trackingEnabled = true)
        val untracked = medicineEntity(uuid = "aaaaaaaa-0000-0000-0000-000000000002")
            .copy(trackingEnabled = false)
        every { databaseHolder.databaseFlow } returns MutableStateFlow(database)
        every { dao.observeAllActive() } returns MutableStateFlow(listOf(tracked, untracked))

        val freshRepository = MedicineRepository(
            context = context,
            databaseHolder = databaseHolder,
            homeSnapshotRepository = homeSnapshotRepository,
            stockMutator = stockMutator,
            appScope = CoroutineScope(
                backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)
            ),
        )

        val result = async { freshRepository.observeAllActiveTracked().first { it.isNotEmpty() } }
        advanceUntilIdle()

        assertEquals(
            listOf(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001")),
            result.await().map { it.uuid },
        )
    }

    // observeAllActiveTracked feeds the home screen's stock warnings as an UPSTREAM
    // source of the home combine, so a raw .map(toMedicineModel) malformed-row throw
    // there would bypass the combine's per-emission guard and freeze the home screen.
    // Deriving it from the guarded activeMedicinesFlow inherits the recovery: a
    // transient malformed row degrades one emission and the next valid one recovers.
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @Test
    fun observeAllActiveTracked_recoversAfterTransientMalformedRow() = runTest {
        val medicinesSource = MutableStateFlow(
            listOf(
                medicineEntity(uuid = "aaaaaaaa-0000-0000-0000-000000000001")
                    .copy(trackingEnabled = true)
            )
        )
        every { databaseHolder.databaseFlow } returns MutableStateFlow(database)
        every { dao.observeAllActive() } returns medicinesSource

        val freshRepository = MedicineRepository(
            context = context,
            databaseHolder = databaseHolder,
            homeSnapshotRepository = homeSnapshotRepository,
            stockMutator = stockMutator,
            appScope = CoroutineScope(
                backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)
            ),
        )

        val emissions = mutableListOf<List<Medicine>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            freshRepository.observeAllActiveTracked().collect { emissions += it }
        }

        advanceUntilIdle()
        assertEquals(1, emissions.last().size)

        // A corrupt row (unparseable UUID) makes toMedicineModel throw for this emission.
        medicinesSource.value = listOf(
            medicineEntity(uuid = "not-a-uuid").copy(trackingEnabled = true)
        )
        advanceUntilIdle()

        val recoveredUuid = "aaaaaaaa-0000-0000-0000-000000000009"
        medicinesSource.value = listOf(
            medicineEntity(uuid = recoveredUuid).copy(trackingEnabled = true)
        )
        advanceUntilIdle()

        assertEquals(
            "observeAllActiveTracked() must recover after a transient malformed-row failure",
            UUID.fromString(recoveredUuid),
            emissions.last().singleOrNull()?.uuid,
        )
    }

    // observeAllActiveTracked derives from the all-active flow, so per-row mapping must
    // keep a malformed UNTRACKED row from poisoning the valid tracked rows that feed
    // Home's stock warnings. A whole-list map would blank stock warnings here.
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @Test
    fun observeAllActiveTracked_keepsTrackedRowsWhenAnUntrackedRowIsMalformed() = runTest {
        val trackedUuid = "aaaaaaaa-0000-0000-0000-000000000001"
        every { databaseHolder.databaseFlow } returns MutableStateFlow(database)
        every { dao.observeAllActive() } returns MutableStateFlow(
            listOf(
                medicineEntity(uuid = "not-a-uuid").copy(trackingEnabled = false),
                medicineEntity(uuid = trackedUuid).copy(trackingEnabled = true),
            )
        )

        val freshRepository = MedicineRepository(
            context = context,
            databaseHolder = databaseHolder,
            homeSnapshotRepository = homeSnapshotRepository,
            stockMutator = stockMutator,
            appScope = CoroutineScope(
                backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)
            ),
        )

        val result = async { freshRepository.observeAllActiveTracked().first { it.isNotEmpty() } }
        advanceUntilIdle()

        assertEquals(
            listOf(UUID.fromString(trackedUuid)),
            result.await().map { it.uuid },
        )
    }

    // The all-active flow itself must drop only the malformed row rather than blanking
    // the whole Medicines list.
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    @Test
    fun observeAllActive_dropsOnlyMalformedRow() = runTest {
        val validUuid = "aaaaaaaa-0000-0000-0000-000000000001"
        every { databaseHolder.databaseFlow } returns MutableStateFlow(database)
        every { dao.observeAllActive() } returns MutableStateFlow(
            listOf(
                medicineEntity(uuid = "not-a-uuid"),
                medicineEntity(uuid = validUuid),
            )
        )

        val freshRepository = MedicineRepository(
            context = context,
            databaseHolder = databaseHolder,
            homeSnapshotRepository = homeSnapshotRepository,
            stockMutator = stockMutator,
            appScope = CoroutineScope(
                backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)
            ),
        )

        val result = async { freshRepository.observeAllActive().first { it.isNotEmpty() } }
        advanceUntilIdle()

        assertEquals(
            listOf(UUID.fromString(validUuid)),
            result.await().map { it.uuid },
        )
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

    private fun stubUnitMutation() {
        coEvery { homeSnapshotRepository.runHomeDataMutation<Unit>(any()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
        }
        coEvery { databaseHolder.withTransaction<Unit>(any()) } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
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
