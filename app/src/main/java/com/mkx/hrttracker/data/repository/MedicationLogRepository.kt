package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.MedicationLogEntryEntity
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.DoseInstructionCalculator
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGelApplicationArea
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationSignature
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.isCompatibleWith
import com.mkx.hrttracker.reminder.MedicationReminderSlot
import com.mkx.hrttracker.reminder.buildMissingScheduledLogEntries
import com.mkx.hrttracker.reminder.hasReminderOccurrence
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
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
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
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
                        // Resolve + map can throw transiently: observeEntries() and
                        // observeMedicineChangeVersion() are independently-invalidated
                        // Room flows, so combine can pair a stale entry list with an
                        // already-mutated medicines table (e.g. a restore swaps every
                        // medicine UUID in one transaction). The separate point-in-time
                        // resolveMedicinesForEntries() then can't resolve the old UUID and
                        // toMedicationLogEntryModel()'s checkNotNull(medicine) throws.
                        // Handle it HERE, per emission, so the failure SUPPRESSES the
                        // single (stale) emission instead of cancelling the upstream Room
                        // observation — the next, consistent emission recovers. A terminal
                        // `.catch` outside flatMapLatest would instead freeze this flow
                        // until the database (and thus the app process) is rebuilt.
                        // Suppress (null -> filterNotNull below) rather than emit a
                        // fallback: a fabricated empty list reaches the Plan combine and
                        // renders a one-frame empty/stale mixture before the consistent
                        // emission lands.
                        runCatching {
                            val medicinesByUuid = database.resolveMedicinesForEntries(entries)
                            entries.map { it.toMedicationLogEntryModel(medicinesByUuid) }
                        }.getOrElse { error ->
                            // resolveMedicinesForEntries() suspends, so cancellation
                            // surfaces here as a CancellationException. runCatching would
                            // swallow it (unlike the Flow .catch this replaced); rethrow so
                            // flatMapLatest/scope cancellation is honoured. Rethrow
                            // non-Exception Throwables (Errors) too: only genuine
                            // recoverable failures may be suppressed; an Error must keep
                            // failing loud.
                            if (error is CancellationException) throw error
                            if (error !is Exception) throw error
                            diagnosticsLogger.warning(
                                TAG,
                                "entries_flow_suppressed count=${entries.size}",
                                error,
                            )
                            null
                        }
                    }
                        .filterNotNull()
                        // Per-emission runCatching above recovers from transform
                        // failures; this terminal catch separately guards the Room
                        // flows themselves failing (DB corruption / I/O error). Without
                        // it a recoverable upstream error would reach the Eagerly,
                        // app-scoped stateIn collector and crash the process — appScope
                        // is a SupervisorJob with no CoroutineExceptionHandler.
                        // Exception transform throws are caught above and never reach
                        // here, so the transient-restore freeze does not return; rethrow
                        // CancellationException and non-Exception Throwables (Errors) so
                        // only genuine recoverable failures degrade to an empty list.
                        .catch { error ->
                            if (error is CancellationException) throw error
                            if (error !is Exception) throw error
                            diagnosticsLogger.warning(TAG, "entries_flow_room_error", error)
                            emit(emptyList())
                        }
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

    fun getCachedEntries(): List<MedicationLogEntry>? = entriesFlow.value

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeScheduledEntriesInWindow(
        scheduledStartIso: String,
        scheduledEndIso: String,
    ): Flow<List<MedicationLogEntry>> {
        return databaseHolder.databaseFlow.flatMapLatest { database ->
            database?.let {
                it.medicationLogDao()
                    .observeScheduledEntriesInWindow(
                        scheduledStartIso = scheduledStartIso,
                        scheduledEndIso = scheduledEndIso,
                    )
                    .mapNotNull { entities ->
                        // Same restore race as entriesFlow: a backup restore swaps every
                        // medicine UUID in one transaction, so this point-in-time
                        // resolveMedicinesForEntries() can pair a still-stale entry list
                        // with the already-mutated medicines table and
                        // toMedicationLogEntryModel()'s checkNotNull(medicine) throws.
                        // This flow feeds Home's stock inputs as an UPSTREAM source of
                        // observeHomeRoomInputs' combine, so the throw bypasses that
                        // combine's per-emission guard and would hit Home's terminal
                        // `.catch`, completing the observation and freezing Home until an
                        // app restart. Suppress the single stale emission here (null ->
                        // mapNotNull) so the next, consistent Room invalidation recovers;
                        // a fabricated empty list would render a one-frame wrong stock
                        // state on Home before the consistent emission lands.
                        runCatching {
                            val medicinesByUuid = it.resolveMedicinesForEntries(entities)
                            entities.map { entity ->
                                entity.toMedicationLogEntryModel(
                                    medicinesByUuid
                                )
                            }
                        }.getOrElse { error ->
                            // Rethrow cancellation (resolveMedicinesForEntries suspends)
                            // and non-Exception Throwables (Errors): only genuine
                            // recoverable failures may be suppressed; an Error must keep
                            // failing loud.
                            if (error is CancellationException) throw error
                            if (error !is Exception) throw error
                            diagnosticsLogger.warning(
                                TAG,
                                "scheduled_window_suppressed count=${entities.size}",
                                error,
                            )
                            null
                        }
                    }
                    // Guards the Room flow itself failing (DB corruption / I/O);
                    // Exception transform throws are caught per-emission above and never
                    // reach here. Rethrow CancellationException and non-Exception
                    // Throwables (Errors) so only genuine recoverable failures degrade
                    // to an empty list.
                    .catch { error ->
                        if (error is CancellationException) throw error
                        if (error !is Exception) throw error
                        emit(emptyList())
                    }
            } ?: flowOf(emptyList())
        }
    }

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
                database.medicationLogDao().deleteEntries(uuidStrings)
            }
        }
    }

    suspend fun deleteAllEntries() {
        homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.withTransaction { database ->
                database.medicationLogDao().deleteAllEntries()
            }
        }
    }

    suspend fun deleteEntriesForGroup(groupUuid: UUID) {
        homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.withTransaction { database ->
                val groupUuidString = groupUuid.toString()
                database.medicationLogDao().deleteEntriesForGroup(groupUuidString)
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
        doseAmountDelta: Double? = null,
    ) {
        if (uuid != null) {
            // Editing an existing log: medication identity is immutable; only
            // the date/time may change. The sheet has no count editor, and its
            // count is 1 for a vial or gel container, so the stored count stays.
            homeSnapshotRepository.runHomeDataMutation {
                databaseHolder.get().medicationLogDao().updateEditableLogFields(
                    uuid = uuid.toString(),
                    appliedAtEpochMillis = appliedAt.toEpochMilli(),
                    appliedAtTimeZoneId = appliedAtTimeZoneId,
                    scheduledForIso = scheduledFor?.toString(),
                )
            }
            return
        }

        homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.withTransaction { database ->
                // A sheet opened on a plan slot carries the count it read then; log only
                // what the slot still misses now. The sheet shows the live plan, including
                // a slot that only its records keep, so the occurrence isn't checked.
                val writeCount = if (sourceGroupUuid != null && scheduledFor != null) {
                    database.clampToMissingSlotCounts(
                        listOf(
                            MedicationLogEntryInput(
                                medicineUuid = medicineUuid,
                                applicationType = applicationType,
                                doseInstruction = doseInstruction,
                                sourceGroupUuid = sourceGroupUuid,
                                scheduleTimeUuid = scheduleTimeUuid,
                                appliedAt = appliedAt,
                                scheduledFor = scheduledFor,
                                count = count,
                            )
                        ),
                        requirePlannedOccurrence = false,
                    ).singleOrNull()?.count ?: throw ScheduledDoseAlreadyLoggedException()
                } else {
                    count
                }
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
                medicine?.let { trackedMedicine ->
                    val requested = resolveRequestedDoseForStock(
                        preparation = trackedMedicine.preparation,
                        doseInstruction = doseInstruction,
                        count = writeCount.coerceAtLeast(1),
                        doseAmountDelta = doseAmountDelta,
                    )
                    requested?.let { requestedDose ->
                        stockMutator.resolveDeductionForInsert(
                            database = database,
                            medicineUuid = trackedMedicine.uuid,
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
                        count = writeCount.coerceAtLeast(1),
                        appliedAtTimeZoneId = appliedAtTimeZoneId,
                        doseAmountDelta = doseAmountDelta,
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
                // Each record keeps its own count, as in a single edit.
                database.medicationLogDao().updateEditableLogFieldsForUuids(
                    uuids = targetUuids.map(UUID::toString),
                    appliedAtEpochMillis = appliedAt.toEpochMilli(),
                    appliedAtTimeZoneId = appliedAtTimeZoneId,
                    scheduledForIso = scheduledFor?.toString(),
                )
            }
        }
    }

    /**
     * Logs [entries] and deducts their stock. An entry for a plan slot is trimmed to what
     * the slot still misses, decided under the write lock: the caller read the slot before
     * it, so another writer may have logged it since, or a restore replaced the plan.
     * Returns what was written; empty when nothing was left to log.
     */
    suspend fun saveNewEntries(
        entries: Collection<MedicationLogEntryInput>,
    ): List<MedicationLogEntryInput> {
        // A widget or notification acts on a snapshot that can predate a plan edit or a
        // restore, so the dated slot must still be planned too.
        return insertNewEntries(entries, deductStock = true, requirePlannedOccurrence = true)
    }

    /**
     * Bulk insert used by retrospective backfill flows (Batch Add, the plan editor's past
     * records). Most backfill callers preserve current stock because the user is reconstructing
     * history they already lived through; Batch Add can opt into deducting it now. Trimmed as
     * [saveNewEntries] is, so a dose logged after the caller read the slots isn't logged twice;
     * the caller built them from past occurrences, so they aren't checked against the plan.
     * Returns what was written.
     */
    suspend fun saveBackfillEntries(
        entries: Collection<MedicationLogEntryInput>,
        deductStock: Boolean = false,
    ): List<MedicationLogEntryInput> {
        return insertNewEntries(entries, deductStock = deductStock, requirePlannedOccurrence = false)
    }

    private suspend fun insertNewEntries(
        entries: Collection<MedicationLogEntryInput>,
        deductStock: Boolean,
        requirePlannedOccurrence: Boolean,
    ): List<MedicationLogEntryInput> {
        if (entries.isEmpty()) {
            return emptyList()
        }

        return homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.withTransaction { database ->
                val written = database.clampToMissingSlotCounts(entries, requirePlannedOccurrence)
                val medicineDao = database.medicineDao()
                val medicineByUuid = mutableMapOf<UUID, Medicine>()
                val entities = written.map { entry ->
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
                    if (deductStock) {
                        medicine?.let { trackedMedicine ->
                            val requested = resolveRequestedDoseForStock(
                                preparation = trackedMedicine.preparation,
                                doseInstruction = entry.doseInstruction,
                                count = entry.count.coerceAtLeast(1),
                            )
                            requested?.let { requestedDose ->
                                stockMutator.resolveDeductionForInsert(
                                    database = database,
                                    medicineUuid = trackedMedicine.uuid,
                                    requestedDose = requestedDose,
                                )
                            }
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
                    )
                }
                database.medicationLogDao().insertEntries(entities)
                written
            }
        }
    }

    /**
     * Trims [entries] for a plan slot to what the live plan still misses there, per
     * medication signature, counting every record attached to the slot as
     * [buildMissingScheduledLogEntries] does. Run inside the insert's transaction. Drops an
     * entry whose plan or dose no longer exists, or, with [requirePlannedOccurrence], whose
     * dated slot the plan no longer schedules. Keeps an entry with no slot as it is.
     */
    private suspend fun HrtTrackerDatabase.clampToMissingSlotCounts(
        entries: Collection<MedicationLogEntryInput>,
        requirePlannedOccurrence: Boolean,
    ): List<MedicationLogEntryInput> {
        val slotDates = entries
            .filter { entry -> entry.sourceGroupUuid != null }
            .mapNotNull { entry -> entry.scheduledFor?.toLocalDate() }
        if (slotDates.isEmpty()) return entries.toList()
        // A record matches its slot by schedule time and calendar date. A backfill covers many
        // slots of one plan: read the plan, and the records over the batch's dates, once.
        val recordEntities = medicationLogDao().getScheduledEntriesInWindow(
            scheduledStartIso = slotDates.min().atStartOfDay().toString(),
            scheduledEndIso = slotDates.max().plusDays(1).atStartOfDay().toString(),
        )
        val recordMedicines = resolveMedicinesForEntries(recordEntities)
        val recordsByDate = recordEntities
            .map { entity -> entity.toMedicationLogEntryModel(recordMedicines) }
            .groupBy { record -> record.scheduledFor?.toLocalDate() }
        val missingBySlot = mutableMapOf<MedicationReminderSlot, MutableMap<MedicationSignature, Int>>()
        val groups = mutableMapOf<UUID, MedicationGroup?>()
        return entries.mapNotNull { entry ->
            val slot = MedicationReminderSlot(
                groupUuid = entry.sourceGroupUuid ?: return@mapNotNull entry,
                scheduledAt = entry.scheduledFor ?: return@mapNotNull entry,
                scheduleTimeUuid = entry.scheduleTimeUuid,
            )
            val missing = missingBySlot.getOrPut(slot) {
                val group = groups.getOrPut(slot.groupUuid) {
                    medicationGroupDao().getGroup(slot.groupUuid.toString())?.let { groupEntity ->
                        groupEntity.toMedicationGroupModel(resolveMedicinesForGroups(listOf(groupEntity)))
                    }
                }
                missingCountsAt(
                    group = group,
                    slot = slot,
                    records = recordsByDate[slot.scheduledAt.toLocalDate()].orEmpty(),
                    requirePlannedOccurrence = requirePlannedOccurrence,
                )
            }
            val signature = entry.signature()
            val count = minOf(entry.count.coerceAtLeast(1), missing[signature] ?: 0)
            if (count <= 0) {
                return@mapNotNull null
            }
            missing[signature] = missing.getValue(signature) - count
            entry.copy(count = count)
        }
    }

    /** What [slot] misses, from [records], the records on its calendar date. */
    private fun missingCountsAt(
        group: MedicationGroup?,
        slot: MedicationReminderSlot,
        records: List<MedicationLogEntry>,
        requirePlannedOccurrence: Boolean,
    ): MutableMap<MedicationSignature, Int> {
        if (group == null) return mutableMapOf()
        if (requirePlannedOccurrence && !group.hasReminderOccurrence(slot)) {
            return mutableMapOf()
        }
        return buildMissingScheduledLogEntries(
            group = group,
            slot = slot,
            entries = records,
            appliedAt = slot.scheduledAt,
        ).associateTo(mutableMapOf()) { missing -> missing.signature() to missing.count }
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
        doseAmountDelta: Double? = null,
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
                doseAmountDelta = doseAmountDelta,
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
            doseAmountDelta = doseAmountDelta,
        )
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
    doseAmountDelta: Double? = null,
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
                DoseInstructionCalculator.effectivePerAdministrationStockAmount(
                    preparation = preparation,
                    doseInstruction = dose,
                    doseAmountDelta = doseAmountDelta,
                ) ?: dose.valueMl
            } else {
                return null
            }
        }

        is DoseInstruction.WeightGrams -> {
            if (preparation is MedicinePreparation.GelContainer) {
                DoseInstructionCalculator.effectivePerAdministrationStockAmount(
                    preparation = preparation,
                    doseInstruction = dose,
                    doseAmountDelta = doseAmountDelta,
                ) ?: dose.valueGrams
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

private fun MedicationLogEntryInput.signature(): MedicationSignature =
    MedicationSignature.fromValues(medicineUuid, applicationType, doseInstruction)

/** A new record for a plan slot found nothing of its dose still missing there. */
class ScheduledDoseAlreadyLoggedException : IllegalStateException(
    "The slot already has every record of this dose.",
)

private const val TAG = "MedicationLogRepository"
