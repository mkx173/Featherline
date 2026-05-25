package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.MedicationLogEntryEntity
import com.mkx.hrttracker.data.local.MedicationLogRefundCandidate
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGelApplicationArea
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.isCompatibleWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationLogRepository @Inject internal constructor(
    private val databaseHolder: DatabaseHolder,
    private val homeSnapshotRepository: HomeSnapshotRepository,
    private val stockMutator: MedicineStockMutator,
    @AppScope appScope: CoroutineScope,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val entriesFlow: StateFlow<List<MedicationLogEntry>?> =
        databaseHolder.databaseFlow
            .flatMapLatest { database ->
                if (database == null) {
                    flowOf<List<MedicationLogEntry>?>(null)
                } else {
                    // resolveMedicinesForEntries is a separate point-in-time read,
                    // so a medicines-table mutation (displayName, archive, etc.)
                    // wouldn't re-trigger this map on its own. Combine with the
                    // change-only signal so any medicine edit re-resolves logs.
                    combine<List<MedicationLogEntryEntity>, Int, List<MedicationLogEntry>?>(
                        database.medicationLogDao().observeEntries(),
                        database.medicineDao().observeMedicineChangeVersion(),
                    ) { entries, _ ->
                        val medicinesByUuid = database.resolveMedicinesForEntries(entries)
                        entries.map { it.toMedicationLogEntryModel(medicinesByUuid) }
                    }
                        .catch { emit(emptyList()) }
                }
            }
            .stateIn(
                scope = appScope,
                started = SharingStarted.Eagerly,
                initialValue = null
            )

    private val estradiolEntriesFlow: StateFlow<List<MedicationLogEntry>?> =
        entriesFlow
            .map { entries ->
                entries?.filter { entry ->
                    entry.category == MedicationCategory.ESTRADIOL
                }
            }
            .stateIn(
                scope = appScope,
                started = SharingStarted.Eagerly,
                initialValue = null
            )

    fun observeEntries(): Flow<List<MedicationLogEntry>?> = entriesFlow

    fun getObservedLatestEstradiolEntryOnOrBefore(
        onOrBefore: Instant,
    ): ObservedEstradiolEntryLookup {
        val entries = estradiolEntriesFlow.value ?: return ObservedEstradiolEntryLookup.NotLoaded
        return ObservedEstradiolEntryLookup.Loaded(
            entry = entries.firstOrNull { entry ->
                !entry.appliedAt.isAfter(onOrBefore)
            }
        )
    }

    suspend fun getEntries(): List<MedicationLogEntry> {
        val database = databaseHolder.get()
        val entities = database.medicationLogDao().getEntries()
        val medicinesByUuid = database.resolveMedicinesForEntries(entities)
        return entities.map { it.toMedicationLogEntryModel(medicinesByUuid) }
    }

    suspend fun getLatestEstradiolEntryOnOrBefore(onOrBefore: Instant): MedicationLogEntry? {
        val database = databaseHolder.get()
        val entity = database.medicationLogDao()
            .getLatestEntryByCategoryOnOrBefore(
                category = MedicationCategory.ESTRADIOL.name,
                onOrBeforeEpochMillis = onOrBefore.toEpochMilli(),
            )
            ?: return null
        val medicinesByUuid = database.resolveMedicinesForEntries(listOf(entity))
        return entity.toMedicationLogEntryModel(medicinesByUuid)
    }

    suspend fun getEntries(uuids: Collection<UUID>): List<MedicationLogEntry> {
        if (uuids.isEmpty()) {
            return emptyList()
        }

        val database = databaseHolder.get()
        val orderedUuids = uuids.distinct().map(UUID::toString)
        val entities = database.medicationLogDao().getEntriesByIds(orderedUuids)
        val medicinesByUuid = database.resolveMedicinesForEntries(entities)
        val entriesByUuid = entities.associateBy(MedicationLogEntryEntity::uuid)

        return orderedUuids.mapNotNull { uuid ->
            entriesByUuid[uuid]?.toMedicationLogEntryModel(medicinesByUuid)
        }
    }

    suspend fun getScheduledGroupEntriesSince(since: LocalDateTime): List<MedicationLogEntry> {
        val database = databaseHolder.get()
        val entities = database.medicationLogDao()
            .getScheduledGroupEntriesSince(since.toString())
        val medicinesByUuid = database.resolveMedicinesForEntries(entities)
        return entities.map { it.toMedicationLogEntryModel(medicinesByUuid) }
    }

    suspend fun getEntry(uuid: UUID): MedicationLogEntry? {
        val database = databaseHolder.get()
        val entity = database.medicationLogDao().getEntry(uuid.toString()) ?: return null
        val medicinesByUuid = database.resolveMedicinesForEntries(listOf(entity))
        return entity.toMedicationLogEntryModel(medicinesByUuid)
    }

    suspend fun deleteEntries(uuids: Collection<UUID>) {
        if (uuids.isEmpty()) {
            return
        }

        homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.withTransaction { database ->
                val uuidStrings = uuids.map(UUID::toString)
                val candidates = database.medicationLogDao().getRefundCandidatesByIds(uuidStrings)
                refundAndDelete(
                    database = database,
                    candidates = candidates,
                    deleteByUuids = uuidStrings,
                    deleteGroupUuid = null,
                    deleteAll = false,
                )
            }
        }
    }

    suspend fun deleteAllEntries() {
        homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.withTransaction { database ->
                val candidates = database.medicationLogDao().getAllRefundCandidates()
                refundAndDelete(
                    database = database,
                    candidates = candidates,
                    deleteByUuids = null,
                    deleteGroupUuid = null,
                    deleteAll = true,
                )
            }
        }
    }

    suspend fun deleteEntriesForGroup(groupUuid: UUID) {
        homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.withTransaction { database ->
                val groupUuidString = groupUuid.toString()
                val candidates = database.medicationLogDao().getRefundCandidatesForGroup(groupUuidString)
                refundAndDelete(
                    database = database,
                    candidates = candidates,
                    deleteByUuids = null,
                    deleteGroupUuid = groupUuidString,
                    deleteAll = false,
                )
            }
        }
    }

    suspend fun saveEntry(
        uuid: UUID?,
        medicineUuid: UUID?,
        applicationType: MedicationApplicationType,
        doseInstruction: DoseInstruction,
        sourceGroupUuid: UUID?,
        scheduleTimeUuid: UUID? = null,
        appliedAt: Instant,
        scheduledFor: LocalDateTime? = null,
        count: Int = 1,
        appliedAtTimeZoneId: String = ZoneId.systemDefault().id,
    ) {
        if (uuid != null) {
            // Editing an existing log: medication identity is immutable; only
            // the date/time/count may change.
            homeSnapshotRepository.runHomeDataMutation {
                databaseHolder.get().medicationLogDao().updateEditableLogFields(
                    uuid = uuid.toString(),
                    appliedAtEpochMillis = appliedAt.toEpochMilli(),
                    appliedAtTimeZoneId = appliedAtTimeZoneId,
                    scheduledForIso = scheduledFor?.toString(),
                    count = count.coerceAtLeast(1),
                )
            }
            return
        }

        homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.withTransaction { database ->
                val medicine = medicineUuid?.let { mu ->
                    checkNotNull(database.medicineDao().getByUuid(mu.toString())) {
                        "Medicine $mu was not found."
                    }.toMedicineModel()
                }
                validateMedicationCompatibility(
                    fieldPrefix = "medication log",
                    applicationType = applicationType,
                    doseInstruction = doseInstruction,
                    preparationType = medicine?.preparation?.type,
                )
                val stockStamp = medicine?.let {
                    val requested = resolveRequestedDoseForStock(
                        preparation = it.preparation,
                        doseInstruction = doseInstruction,
                        count = count.coerceAtLeast(1),
                    )
                    requested?.let { requestedDose ->
                        stockMutator.resolveDeductionForInsert(
                            database = database,
                            medicineUuid = it.uuid,
                            requestedDose = requestedDose,
                        )
                    }
                }

                database.medicationLogDao().insertEntry(
                    buildEntryEntity(
                        uuid = UUID.randomUUID(),
                        medicine = medicine,
                        applicationType = applicationType,
                        doseInstruction = doseInstruction,
                        sourceGroupUuid = sourceGroupUuid,
                        scheduleTimeUuid = scheduleTimeUuid,
                        appliedAt = appliedAt,
                        scheduledFor = scheduledFor,
                        count = count.coerceAtLeast(1),
                        appliedAtTimeZoneId = appliedAtTimeZoneId,
                        stockStamp = stockStamp,
                    )
                )
            }
        }
    }

    suspend fun saveEntries(
        uuids: Collection<UUID>,
        medicineUuid: UUID?,
        applicationType: MedicationApplicationType,
        doseInstruction: DoseInstruction,
        sourceGroupUuid: UUID?,
        scheduleTimeUuid: UUID? = null,
        appliedAt: Instant,
        scheduledFor: LocalDateTime? = null,
        count: Int = 1,
        appliedAtTimeZoneId: String = ZoneId.systemDefault().id,
    ) {
        val targetUuids = uuids.distinct()
        if (targetUuids.isEmpty()) {
            saveEntry(
                uuid = null,
                medicineUuid = medicineUuid,
                applicationType = applicationType,
                doseInstruction = doseInstruction,
                sourceGroupUuid = sourceGroupUuid,
                scheduleTimeUuid = scheduleTimeUuid,
                appliedAt = appliedAt,
                scheduledFor = scheduledFor,
                count = count,
                appliedAtTimeZoneId = appliedAtTimeZoneId,
            )
            return
        }

        homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.withTransaction { database ->
                val dao = database.medicationLogDao()
                val priorStockByUuid = dao
                    .getStockSnapshotsByIds(targetUuids.map(UUID::toString))
                    .associateBy { it.uuid }
                val medicine = medicineUuid?.let { mu ->
                    checkNotNull(database.medicineDao().getByUuid(mu.toString())) {
                        "Medicine $mu was not found."
                    }.toMedicineModel()
                }
                val entities = targetUuids.map { uuid ->
                    val prior = priorStockByUuid[uuid.toString()]
                    val carriedStamp = prior?.stockDeductionUnits?.let { deductionUnits ->
                        StockDeductionStamp(
                            deductionUnits = deductionUnits,
                            generation = prior.stockGeneration ?: 0L,
                        )
                    }
                    buildEntryEntity(
                        uuid = uuid,
                        medicine = medicine,
                        applicationType = applicationType,
                        doseInstruction = doseInstruction,
                        sourceGroupUuid = sourceGroupUuid,
                        scheduleTimeUuid = scheduleTimeUuid,
                        appliedAt = appliedAt,
                        scheduledFor = scheduledFor,
                        count = count,
                        appliedAtTimeZoneId = appliedAtTimeZoneId,
                        stockStamp = carriedStamp,
                    )
                }
                dao.insertEntries(entities)
            }
        }
    }

    suspend fun saveNewEntries(entries: Collection<MedicationLogEntryInput>) {
        if (entries.isEmpty()) {
            return
        }

        homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.withTransaction { database ->
                val medicineDao = database.medicineDao()
                val medicineByUuid = mutableMapOf<UUID, Medicine>()
                val entities = entries.map { entry ->
                    val medicine = entry.medicineUuid?.let { mu ->
                        medicineByUuid.getOrPut(mu) {
                            checkNotNull(medicineDao.getByUuid(mu.toString())) {
                                "Medicine $mu was not found."
                            }.toMedicineModel()
                        }
                    }
                    validateMedicationCompatibility(
                        fieldPrefix = "medication log",
                        applicationType = entry.applicationType,
                        doseInstruction = entry.doseInstruction,
                        preparationType = medicine?.preparation?.type,
                    )
                    val stockStamp = medicine?.let {
                        val requested = resolveRequestedDoseForStock(
                            preparation = it.preparation,
                            doseInstruction = entry.doseInstruction,
                            count = entry.count.coerceAtLeast(1),
                        )
                        requested?.let { requestedDose ->
                            stockMutator.resolveDeductionForInsert(
                                database = database,
                                medicineUuid = it.uuid,
                                requestedDose = requestedDose,
                            )
                        }
                    }

                    buildEntryEntity(
                        uuid = UUID.randomUUID(),
                        medicine = medicine,
                        applicationType = entry.applicationType,
                        doseInstruction = entry.doseInstruction,
                        sourceGroupUuid = entry.sourceGroupUuid,
                        scheduleTimeUuid = entry.scheduleTimeUuid,
                        appliedAt = entry.appliedAt,
                        scheduledFor = entry.scheduledFor,
                        count = entry.count.coerceAtLeast(1),
                        appliedAtTimeZoneId = entry.appliedAtTimeZoneId,
                        stockStamp = stockStamp,
                    )
                }
                database.medicationLogDao().insertEntries(entities)
            }
        }
    }

    private fun buildEntryEntity(
        uuid: UUID,
        medicine: Medicine?,
        applicationType: MedicationApplicationType,
        doseInstruction: DoseInstruction,
        sourceGroupUuid: UUID?,
        scheduleTimeUuid: UUID?,
        appliedAt: Instant,
        scheduledFor: LocalDateTime?,
        count: Int,
        appliedAtTimeZoneId: String,
        stockStamp: StockDeductionStamp?,
    ): MedicationLogEntryEntity {
        validateMedicationCompatibility(
            fieldPrefix = "medication log",
            applicationType = applicationType,
            doseInstruction = doseInstruction,
            preparationType = medicine?.preparation?.type,
        )
        val category = medicine?.category ?: MedicationCategory.ESTRADIOL
        val equivalentE2Mg = medicine?.let {
            DoseInstructionCalculator.perUnitEquivalentE2Mg(
                medicine = it,
                doseInstruction = doseInstruction,
            )
        }
        val doseInstructionFields = doseInstruction.toStorageFields()
        return MedicationLogEntryEntity(
            uuid = uuid.toString(),
            category = category.name,
            medicineUuid = medicine?.uuid?.toString(),
            applicationType = applicationType.name,
            doseInstructionKind = doseInstructionFields.doseInstructionKind,
            tabletFractionNumerator = doseInstructionFields.tabletFractionNumerator,
            tabletFractionDenominator = doseInstructionFields.tabletFractionDenominator,
            doseVolumeMl = doseInstructionFields.doseVolumeMl,
            doseWeightGrams = doseInstructionFields.doseWeightGrams,
            equivalentE2Mg = equivalentE2Mg,
            sourceGroupUuid = sourceGroupUuid?.toString(),
            scheduleTimeUuid = scheduleTimeUuid?.toString(),
            appliedAtEpochMillis = appliedAt.toEpochMilli(),
            appliedAtTimeZoneId = appliedAtTimeZoneId,
            scheduledForIso = scheduledFor?.toString(),
            count = count.coerceAtLeast(1),
            gelApplicationArea = MedicationGelApplicationArea.DEFAULT.name,
            stockDeductionUnits = stockStamp?.deductionUnits,
            stockGeneration = stockStamp?.generation,
        )
    }

    private suspend fun refundAndDelete(
        database: HrtTrackerDatabase,
        candidates: List<MedicationLogRefundCandidate>,
        deleteByUuids: List<String>?,
        deleteGroupUuid: String?,
        deleteAll: Boolean,
    ) {
        val now = Instant.now()
        for (candidate in candidates) {
            val medicineUuidString = candidate.medicineUuid ?: continue
            stockMutator.applyRefundForDelete(
                database = database,
                medicineUuid = UUID.fromString(medicineUuidString),
                log = MedicationLogStockSnapshot(
                    deductionUnits = candidate.stockDeductionUnits,
                    generation = candidate.stockGeneration,
                ),
                now = now,
            )
        }

        val dao = database.medicationLogDao()
        when {
            deleteAll -> dao.deleteAllEntries()
            deleteByUuids != null -> dao.deleteEntries(deleteByUuids)
            deleteGroupUuid != null -> dao.deleteEntriesForGroup(deleteGroupUuid)
        }
    }
}

internal fun validateMedicationCompatibility(
    fieldPrefix: String,
    applicationType: MedicationApplicationType,
    doseInstruction: DoseInstruction,
    preparationType: MedicinePreparationType?,
) {
    require(applicationType.isCompatibleWith(preparationType)) {
        "$fieldPrefix applicationType=$applicationType is not compatible with preparation=$preparationType."
    }
    require(doseInstruction.isCompatibleWith(preparationType)) {
        "$fieldPrefix doseInstruction=${doseInstruction.kind} is not compatible with preparation=$preparationType."
    }
}

internal fun resolveRequestedDoseForStock(
    preparation: MedicinePreparation,
    doseInstruction: DoseInstruction,
    count: Int,
): Double? {
    val perAdministration = when (val dose = doseInstruction) {
        is DoseInstruction.TabletFraction -> {
            if (preparation is MedicinePreparation.Pill) {
                dose.numerator.toDouble() / dose.denominator.toDouble()
            } else {
                return null
            }
        }

        DoseInstruction.WholeUnit -> when (preparation) {
            is MedicinePreparation.Capsule,
            is MedicinePreparation.InjectionSingleUseVial,
            is MedicinePreparation.GelSachet,
            is MedicinePreparation.Patch -> 1.0

            else -> return null
        }

        is DoseInstruction.VolumeMl -> {
            if (preparation is MedicinePreparation.InjectionMultiUseVial) {
                dose.valueMl
            } else {
                return null
            }
        }

        is DoseInstruction.WeightGrams -> {
            if (preparation is MedicinePreparation.GelContainer) {
                dose.valueGrams
            } else {
                return null
            }
        }

        DoseInstruction.Noop -> return null
    }
    return perAdministration * count.toDouble()
}

sealed interface ObservedEstradiolEntryLookup {
    data object NotLoaded : ObservedEstradiolEntryLookup

    data class Loaded(
        val entry: MedicationLogEntry?,
    ) : ObservedEstradiolEntryLookup
}

data class MedicationLogEntryInput(
    val medicineUuid: UUID?,
    val applicationType: MedicationApplicationType,
    val doseInstruction: DoseInstruction,
    val sourceGroupUuid: UUID?,
    val scheduleTimeUuid: UUID? = null,
    val appliedAt: Instant,
    val scheduledFor: LocalDateTime? = null,
    val count: Int = 1,
    val appliedAtTimeZoneId: String = ZoneId.systemDefault().id,
) {
    init {
        require(medicineUuid != null || applicationType == MedicationApplicationType.PATCH_OFF) {
            "Only a PATCH_OFF log may omit its medicine."
        }
    }
}
