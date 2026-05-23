package com.mkx.hrttracker.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.MedicineDao
import com.mkx.hrttracker.data.local.MedicineEntity
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineDisplayDoseUnit
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.medication.normalizeCustomMedicationName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicineRepository @Inject constructor(
    private val databaseHolder: DatabaseHolder,
    private val homeSnapshotRepository: HomeSnapshotRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAllActive(): Flow<List<Medicine>> {
        return databaseHolder.databaseFlow.flatMapLatest { database ->
            database?.medicineDao()?.observeAllActive()
                ?.map { entities -> entities.map(MedicineEntity::toMedicineModel) }
                ?: flowOf(emptyList())
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAllArchived(): Flow<List<Medicine>> {
        return databaseHolder.databaseFlow.flatMapLatest { database ->
            database?.medicineDao()?.observeAllArchived()
                ?.map { entities -> entities.map(MedicineEntity::toMedicineModel) }
                ?: flowOf(emptyList())
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeByUuid(uuid: UUID): Flow<Medicine?> {
        return databaseHolder.databaseFlow.flatMapLatest { database ->
            database?.medicineDao()?.observeByUuid(uuid.toString())
                ?.map { entity -> entity?.toMedicineModel() }
                ?: flowOf(null)
        }
    }

    /**
     * One-shot read of every medicine row, active and archived. Used by the
     * backup export path which serialises the full table so restore can rebuild
     * referential integrity for both live and historical-log references.
     */
    suspend fun getAll(): List<Medicine> {
        return databaseHolder.get().medicineDao()
            .getAll()
            .map(MedicineEntity::toMedicineModel)
    }

    suspend fun getByUuid(uuid: UUID): Medicine? {
        return databaseHolder.get().medicineDao()
            .getByUuid(uuid.toString())
            ?.toMedicineModel()
    }

    suspend fun getByUuids(uuids: Collection<UUID>): Map<UUID, Medicine> {
        if (uuids.isEmpty()) {
            return emptyMap()
        }
        return databaseHolder.get().medicineDao()
            .getByUuids(uuids.map(UUID::toString))
            .map(MedicineEntity::toMedicineModel)
            .associateBy(Medicine::uuid)
    }

    suspend fun findOrCreateForCatalog(
        medicationKey: MedicationKey,
        preparation: MedicinePreparation,
        now: Instant = Instant.now(),
    ): Medicine {
        val medicine = findOrCreate(
            selection = MedicineSelection.Catalog(medicationKey),
            category = medicationKey.category,
            preparation = preparation,
            displayName = null,
            identityKey = MedicineIdentityKey.catalog(medicationKey, preparation),
            // Catalog medicines never expose the picker — their unit is fixed.
            displayDoseUnit = MedicineDisplayDoseUnit.MG,
            now = now,
        )
        ensurePatchOffSingletonForPatch(preparation, now)
        return medicine
    }

    suspend fun findOrCreateForCustom(
        customMedicationName: String,
        displayName: String?,
        category: MedicationCategory,
        preparation: MedicinePreparation,
        // Carries the user-picked display unit (mg/μg/g) for raw-mass fields.
        // Storage stays in mg; the value is reused on edit to repopulate the
        // editor with the original unit.
        displayDoseUnit: MedicineDisplayDoseUnit = MedicineDisplayDoseUnit.MG,
        now: Instant = Instant.now(),
    ): Medicine {
        require(normalizeCustomMedicationName(customMedicationName).isNotBlank()) {
            "Custom medication name must not be blank."
        }
        val medicine = findOrCreate(
            selection = MedicineSelection.Custom(customMedicationName),
            category = category,
            preparation = preparation,
            displayName = displayName,
            identityKey = MedicineIdentityKey.custom(customMedicationName, preparation),
            displayDoseUnit = displayDoseUnit,
            now = now,
        )
        ensurePatchOffSingletonForPatch(preparation, now)
        return medicine
    }

    /**
     * Look up the global PATCH_OFF singleton; create it if it doesn't exist.
     * The singleton is keyed under [MedicineIdentityKey.patchOff], in the
     * ESTRADIOL category (the only patch category today), and is what the
     * medicine manager renders as a tappable "Patch off" row.
     */
    suspend fun findOrCreatePatchOff(now: Instant = Instant.now()): Medicine {
        return findOrCreate(
            selection = MedicineSelection.PatchOff,
            category = MedicationCategory.ESTRADIOL,
            preparation = MedicinePreparation.PatchOff,
            displayName = null,
            identityKey = MedicineIdentityKey.patchOff(),
            displayDoseUnit = MedicineDisplayDoseUnit.MG,
            now = now,
        )
    }

    /**
     * Idempotent: a no-op for non-patch preparations and for patch creations
     * after the singleton already exists. Called from every patch-medicine
     * create path so the manager always has a PATCH_OFF entry to display.
     */
    private suspend fun ensurePatchOffSingletonForPatch(
        preparation: MedicinePreparation,
        now: Instant,
    ) {
        if (preparation is MedicinePreparation.Patch) {
            findOrCreatePatchOff(now)
        }
    }

    suspend fun setDisplayName(
        uuid: UUID,
        name: String?,
        now: Instant = Instant.now(),
    ) {
        val nowEpochMillis = now.toEpochMilli()
        homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.withTransaction { database ->
                val dao = database.medicineDao()
                dao.getByUuid(uuid.toString()) ?: throw MedicineNotFoundException(uuid)
                dao.updateDisplayName(
                    uuid = uuid.toString(),
                    displayName = name,
                    updatedAtEpochMillis = nowEpochMillis,
                )
            }
        }
    }

    suspend fun isLocked(uuid: UUID): Boolean {
        return databaseHolder.get().medicineDao().logReferenceCount(uuid.toString()) > 0
    }

    suspend fun updatePreparation(
        uuid: UUID,
        preparation: MedicinePreparation,
        // Non-null when the editor's unit picker has a new value to commit;
        // null leaves the column untouched (catalog medicines, or paths that
        // don't reach the picker).
        displayDoseUnit: MedicineDisplayDoseUnit? = null,
        now: Instant = Instant.now(),
    ) {
        val nowEpochMillis = now.toEpochMilli()
        homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.withTransaction { database ->
                val dao = database.medicineDao()
                val existing = dao.getByUuid(uuid.toString())
                    ?: throw MedicineNotFoundException(uuid)
                if (dao.logReferenceCount(uuid.toString()) > 0) {
                    throw MedicineLockedException(uuid)
                }
                val existingMedicine = existing.toMedicineModel()
                val newIdentityKey = when (val selection = existingMedicine.selection) {
                    is MedicineSelection.Catalog ->
                        MedicineIdentityKey.catalog(selection.medicationKey, preparation)

                    is MedicineSelection.Custom ->
                        MedicineIdentityKey.custom(selection.medicationName, preparation)

                    // The PATCH_OFF singleton's identity is fixed by design;
                    // detail-screen wiring must never reach this branch (the
                    // edit action is hidden for the singleton).
                    is MedicineSelection.PatchOff ->
                        throw IllegalStateException(
                            "PATCH_OFF singleton preparation is immutable.",
                        )
                }
                val collision = dao.getByIdentityKey(newIdentityKey)
                if (collision != null && collision.uuid != existing.uuid) {
                    throw MedicineIdentityCollisionException(newIdentityKey)
                }
                val storageFields = preparation.toStorageFields()
                // Reuse the existing column value when the caller passed null,
                // so a partial update (preparation only, no unit change) is a
                // no-op for the display unit. Normalize the existing value
                // through fromStorageValue so a malformed legacy row writes
                // back as MG rather than re-persisting garbage.
                val resolvedDisplayDoseUnit = (
                    displayDoseUnit
                        ?: MedicineDisplayDoseUnit.fromStorageValue(existing.displayDoseUnit)
                ).name
                dao.updatePreparationFields(
                    uuid = uuid.toString(),
                    preparationType = storageFields.preparationType,
                    strengthMgPerTablet = storageFields.strengthMgPerTablet,
                    strengthMgPerVial = storageFields.strengthMgPerVial,
                    concentrationMgPerMl = storageFields.concentrationMgPerMl,
                    vialVolumeMl = storageFields.vialVolumeMl,
                    concentrationPercent = storageFields.concentrationPercent,
                    sachetWeightGrams = storageFields.sachetWeightGrams,
                    containerWeightGrams = storageFields.containerWeightGrams,
                    patchTotalMg = storageFields.patchTotalMg,
                    patchReleaseRateMcgPerDay = storageFields.patchReleaseRateMcgPerDay,
                    displayDoseUnit = resolvedDisplayDoseUnit,
                    identityKey = newIdentityKey,
                    updatedAtEpochMillis = nowEpochMillis,
                )
            }
        }
    }

    suspend fun archive(
        uuid: UUID,
        now: Instant = Instant.now(),
    ) {
        val nowEpochMillis = now.toEpochMilli()
        homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.withTransaction { database ->
                val dao = database.medicineDao()
                if (dao.activeGroupReferenceCount(uuid.toString()) > 0) {
                    throw MedicineReferencedByActiveGroupException(uuid)
                }
                dao.archive(
                    uuid = uuid.toString(),
                    archivedAtEpochMillis = nowEpochMillis,
                    updatedAtEpochMillis = nowEpochMillis,
                )
            }
        }
    }

    private suspend fun findOrCreate(
        selection: MedicineSelection,
        category: MedicationCategory,
        preparation: MedicinePreparation,
        displayName: String?,
        identityKey: String,
        displayDoseUnit: MedicineDisplayDoseUnit,
        now: Instant,
    ): Medicine {
        val nowEpochMillis = now.toEpochMilli()
        return homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.withTransaction { database ->
                val dao = database.medicineDao()
                val existing = dao.getByIdentityKey(identityKey)
                if (existing != null) {
                    return@withTransaction dao.activateExisting(existing, nowEpochMillis)
                }

                val medicine = Medicine(
                    uuid = UUID.randomUUID(),
                    selection = selection,
                    category = category,
                    preparation = preparation,
                    displayName = displayName,
                    identityKey = identityKey,
                    createdAt = now,
                    updatedAt = now,
                    archivedAt = null,
                    displayDoseUnit = displayDoseUnit,
                )
                try {
                    dao.insert(medicine.toEntity())
                    medicine
                } catch (exception: SQLiteConstraintException) {
                    val raced = dao.getByIdentityKey(identityKey) ?: throw exception
                    dao.activateExisting(raced, nowEpochMillis)
                }
            }
        }
    }

    private suspend fun MedicineDao.activateExisting(
        entity: MedicineEntity,
        nowEpochMillis: Long,
    ): Medicine {
        if (entity.archivedAtEpochMillis == null) {
            return entity.toMedicineModel()
        }
        unarchive(
            uuid = entity.uuid,
            updatedAtEpochMillis = nowEpochMillis,
        )
        return entity.copy(
            archivedAtEpochMillis = null,
            updatedAtEpochMillis = nowEpochMillis,
        ).toMedicineModel()
    }
}

class MedicineNotFoundException(uuid: UUID) :
    NoSuchElementException("Medicine $uuid was not found.")

class MedicineLockedException(uuid: UUID) :
    IllegalStateException("Medicine $uuid is locked by logged history.")

class MedicineIdentityCollisionException(identityKey: String) :
    IllegalStateException("Medicine identity $identityKey already exists.")

class MedicineReferencedByActiveGroupException(uuid: UUID) :
    IllegalStateException("Medicine $uuid is referenced by an active group.")
