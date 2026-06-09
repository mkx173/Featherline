package com.mkx.hrttracker.data.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.MedicineDao
import com.mkx.hrttracker.data.local.MedicineEntity
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineDisplayDoseUnit
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.normalizeCustomMedicationName
import com.mkx.hrttracker.util.ToastManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicineRepository @Inject internal constructor(
    @param:ApplicationContext private val context: Context,
    private val databaseHolder: DatabaseHolder,
    private val homeSnapshotRepository: HomeSnapshotRepository,
    private val stockMutator: MedicineStockMutator,
    @AppScope appScope: CoroutineScope,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val activeMedicinesFlow: StateFlow<List<Medicine>?> =
        databaseHolder.databaseFlow
            .flatMapLatest { database ->
                if (database == null) {
                    flowOf<List<Medicine>?>(null)
                } else {
                    database.medicineDao().observeAllActive()
                        .map { entities ->
                            // Map per ROW so a single malformed row (e.g. an unparseable
                            // enum or UUID introduced by a restore) drops only itself
                            // instead of blanking the whole list. Blanking would also
                            // blank the stock-tracked subset derived from this flow even
                            // when the tracked rows are valid, since an unrelated untracked
                            // row would poison every consumer. The flow still degrades only
                            // this emission, never terminating: a terminal `.catch` inside
                            // flatMapLatest would freeze this Eagerly, app-scoped flow —
                            // and the Medicines list — until the app process is rebuilt.
                            entities.mapNotNull { entity ->
                                try {
                                    entity.toMedicineModel()
                                } catch (error: Exception) {
                                    // No suspension point here today, so cancellation
                                    // can't surface — but rethrow it anyway so a future
                                    // suspend call is never silently swallowed. Drop only
                                    // the offending row.
                                    if (error is CancellationException) throw error
                                    null
                                }
                            }
                        }
                        // Per-row mapping above recovers from a malformed row; this
                        // terminal catch separately guards the Room flow
                        // itself failing (DB corruption / I/O error). Without it an
                        // upstream error would reach the Eagerly, app-scoped stateIn
                        // collector and crash the process — appScope is a SupervisorJob
                        // with no CoroutineExceptionHandler.
                        .catch { error ->
                            if (error is CancellationException) throw error
                            if (error !is Exception) throw error
                            emit(emptyList())
                        }
                }
            }
            .stateIn(
                scope = appScope,
                started = SharingStarted.Eagerly,
                initialValue = null,
            )

    fun observeAllActive(): Flow<List<Medicine>> = activeMedicinesFlow.filterNotNull()

    /**
     * Nullable variant of [observeAllActive]. Emits `null` until the
     * eagerly-cached flow has produced its first list (i.e. until Room has
     * opened and delivered the first query). Consumers that need to combine
     * this with a fallback source (e.g. the home snapshot) should observe
     * this flow so the combine fires immediately on the initial `null`
     * instead of blocking on the live emission.
     */
    fun observeAllActiveOrNull(): Flow<List<Medicine>?> = activeMedicinesFlow

    /**
     * Synchronous read of the eagerly-cached active list. Returns `null` until
     * Room's first emission populates [activeMedicinesFlow]; lets ViewModels
     * seed `stateIn` `initialValue` so the first composition lands on
     * populated state instead of a loading flash.
     */
    fun getCachedActiveMedicines(): List<Medicine>? = activeMedicinesFlow.value

    fun getCachedActiveMedicine(uuid: UUID): Medicine? {
        return activeMedicinesFlow.value?.firstOrNull { it.uuid == uuid }
    }

    /**
     * Stock-tracked subset of the active medicines, derived from the same
     * guarded, eagerly-cached [activeMedicinesFlow] (`trackingEnabled` mirrors
     * `MedicineDao.getAllActiveTrackedEntities`'s `trackingEnabled = 1` filter).
     * Deriving instead of opening a second Room query reuses the per-row mapping
     * + terminal `.catch` above: a malformed row drops only itself (so an
     * unrelated untracked bad row can't blank these tracked rows), and a raw
     * `.map(toMedicineModel)` here would instead throw upstream of the home stock
     * combine and freeze the home screen until the app process is rebuilt. Maps
     * the pre-first-emission `null` to an empty list so the home combine still
     * fires immediately.
     */
    fun observeAllActiveTracked(): Flow<List<Medicine>> {
        return observeAllActiveOrNull().map { medicines ->
            medicines.orEmpty().filter { medicine -> medicine.stock.trackingEnabled }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeByUuid(uuid: UUID): Flow<Medicine?> {
        return databaseHolder.databaseFlow.flatMapLatest { database ->
            database?.medicineDao()?.observeByUuid(uuid.toString())
                ?.map { entity ->
                    try {
                        entity?.toMedicineModel()
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        null
                    }
                }
                ?.catch { error ->
                    if (error is CancellationException) throw error
                    if (error !is Exception) throw error
                    emit(null)
                }
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

    suspend fun getAllActive(): List<Medicine> {
        return getAll().filterNot(Medicine::isArchived)
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
            notifyOnActiveExisting = false,
        )
    }

    /**
     * Idempotent: a no-op for non-patch preparations and for patch creations
     * after the singleton already exists. Called from every patch-medicine
     * create path so the manager always has a PATCH_OFF entry to display.
     */
    // Runs OUTSIDE the original create's runHomeDataMutation — the home
    // snapshot rebuilds twice on the first patch creation (once for the
    // patch, once for the singleton). A crash between the two leaves an
    // orphan patch row without the singleton; the next patch insert or the
    // startup backfill in StartupPreloader heals it.
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

    suspend fun enableTracking(
        uuid: UUID,
        initialUnitsRemaining: Double?,
        initialOpenContainerAmount: Double?,
        initialUnitsLastTotal: Double?,
        now: Instant = Instant.now(),
    ) {
        homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.withTransaction { database ->
                stockMutator.enableTracking(
                    database = database,
                    medicineUuid = uuid,
                    initialUnitsRemaining = initialUnitsRemaining,
                    initialOpenContainerAmount = initialOpenContainerAmount,
                    initialUnitsLastTotal = initialUnitsLastTotal,
                    now = now,
                )
            }
        }
    }

    suspend fun disableTracking(
        uuid: UUID,
        now: Instant = Instant.now(),
    ) {
        homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.withTransaction { database ->
                stockMutator.disableTracking(
                    database = database,
                    medicineUuid = uuid,
                    now = now,
                )
            }
        }
    }

    internal suspend fun applyRecount(
        uuid: UUID,
        recount: StockRecount,
        now: Instant = Instant.now(),
    ) {
        homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.withTransaction { database ->
                stockMutator.applyRecount(
                    database = database,
                    medicineUuid = uuid,
                    recount = recount,
                    now = now,
                )
            }
        }
    }

    internal suspend fun applyReceived(
        uuid: UUID,
        received: StockReceived,
        now: Instant = Instant.now(),
    ) {
        homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.withTransaction { database ->
                stockMutator.applyReceived(
                    database = database,
                    medicineUuid = uuid,
                    received = received,
                    now = now,
                )
            }
        }
    }

    internal suspend fun applySetOpenContainerAmount(
        uuid: UUID,
        amount: Double,
        now: Instant = Instant.now(),
    ) {
        homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.withTransaction { database ->
                stockMutator.applySetOpenContainerAmount(
                    database = database,
                    medicineUuid = uuid,
                    amount = amount,
                    now = now,
                )
            }
        }
    }

    suspend fun updateWarnAtDaysRemaining(
        uuid: UUID,
        warnAtDaysRemaining: Int,
        now: Instant = Instant.now(),
    ) {
        require(warnAtDaysRemaining in 0..365) {
            "warnAtDaysRemaining must be 0..365"
        }
        homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.withTransaction { database ->
                database.medicineDao().updateWarnAtDaysRemaining(
                    uuid = uuid.toString(),
                    warnAtDaysRemaining = warnAtDaysRemaining,
                    updatedAtEpochMillis = now.toEpochMilli(),
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
                // A preparation edit must NOT touch stock. The editor only lets
                // the user adjust the numeric fields of the existing preparation
                // (it can't switch preparation *type*), so the physical
                // inventory — N tablets, N vials + open mL, N patches, open
                // grams — stays valid; only the derived dose math / runway
                // recomputes. Clearing here would silently disable tracking,
                // which bit display-name-only saves: the merged editor commits
                // the untouched preparation alongside the new name, and on an
                // unlocked medicine that round-trip used to wipe stock.
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
                stockMutator.clearStockOnArchive(
                    database = database,
                    medicineUuid = uuid,
                    now = now,
                )
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
        notifyOnActiveExisting: Boolean = true,
    ): Medicine {
        val nowEpochMillis = now.toEpochMilli()
        var activeExistingMedicineFound = false
        val medicine = homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.withTransaction { database ->
                val dao = database.medicineDao()
                val existing = dao.getByIdentityKey(identityKey)
                if (existing != null) {
                    activeExistingMedicineFound = existing.archivedAtEpochMillis == null
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
                    stock = MedicineStock(),
                )
                try {
                    dao.insert(medicine.toEntity())
                    medicine
                } catch (exception: SQLiteConstraintException) {
                    val raced = dao.getByIdentityKey(identityKey) ?: throw exception
                    activeExistingMedicineFound = raced.archivedAtEpochMillis == null
                    dao.activateExisting(raced, nowEpochMillis)
                }
            }
        }
        if (notifyOnActiveExisting && activeExistingMedicineFound) {
            ToastManager.showMessage(
                context.getString(R.string.medicine_already_exists),
            )
        }
        return medicine
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
