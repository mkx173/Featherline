package com.mkx.hrttracker.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.MedicineDao
import com.mkx.hrttracker.data.local.MedicineEntity
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
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
        return findOrCreate(
            selection = MedicineSelection.Catalog(medicationKey),
            category = medicationKey.category,
            preparation = preparation,
            displayName = null,
            identityKey = MedicineIdentityKey.catalog(medicationKey, preparation),
            now = now,
        )
    }

    suspend fun findOrCreateForCustom(
        customMedicationName: String,
        displayName: String?,
        category: MedicationCategory,
        preparation: MedicinePreparation,
        now: Instant = Instant.now(),
    ): Medicine {
        require(normalizeCustomMedicationName(customMedicationName).isNotBlank()) {
            "Custom medication name must not be blank."
        }
        return findOrCreate(
            selection = MedicineSelection.Custom(customMedicationName),
            category = category,
            preparation = preparation,
            displayName = displayName,
            identityKey = MedicineIdentityKey.custom(customMedicationName, preparation),
            now = now,
        )
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
                }
                val collision = dao.getByIdentityKey(newIdentityKey)
                if (collision != null && collision.uuid != existing.uuid) {
                    throw MedicineIdentityCollisionException(newIdentityKey)
                }
                val storageFields = preparation.toStorageFields()
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
