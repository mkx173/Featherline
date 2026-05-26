package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.MedicationGroupEntity
import com.mkx.hrttracker.data.local.MedicationGroupItemEntity
import com.mkx.hrttracker.data.local.MedicationGroupScheduleTimeEntity
import com.mkx.hrttracker.data.local.MedicationGroupWeeklyDayEntity
import com.mkx.hrttracker.data.local.MedicationGroupWithItemsEntity
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGelApplicationArea
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationGroupRepository @Inject constructor(
    private val databaseHolder: DatabaseHolder,
    private val homeSnapshotRepository: HomeSnapshotRepository,
    @AppScope appScope: CoroutineScope,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val groupsFlow: StateFlow<List<MedicationGroup>?> =
        databaseHolder.databaseFlow
            .flatMapLatest { database ->
                if (database == null) {
                    flowOf<List<MedicationGroup>?>(null)
                } else {
                    // resolveMedicinesForGroups is a separate point-in-time read,
                    // so a medicines-table mutation (displayName, archive, etc.)
                    // wouldn't re-trigger this map on its own. Combine with the
                    // change-only signal so any medicine edit re-resolves slots.
                    combine<List<MedicationGroupWithItemsEntity>, Int, List<MedicationGroup>?>(
                        database.medicationGroupDao().observeGroups(),
                        database.medicineDao().observeMedicineChangeVersion(),
                    ) { groups, _ ->
                        val medicinesByUuid = database.resolveMedicinesForGroups(groups)
                        groups.map { it.toMedicationGroupModel(medicinesByUuid) }
                    }
                        .catch { emit(emptyList()) }
                }
            }
            .stateIn(
                scope = appScope,
                started = SharingStarted.Eagerly,
                initialValue = null
            )

    fun observeGroups(): Flow<List<MedicationGroup>?> = groupsFlow

    fun getCachedGroup(uuid: UUID): MedicationGroup? {
        return groupsFlow.value?.firstOrNull { group -> group.uuid == uuid }
    }

    suspend fun getGroups(): List<MedicationGroup> {
        val database = databaseHolder.get()
        val groups = database.medicationGroupDao().getGroups()
        val medicinesByUuid = database.resolveMedicinesForGroups(groups)
        return groups.map { it.toMedicationGroupModel(medicinesByUuid) }
    }

    suspend fun getActiveGroups(): List<MedicationGroup> {
        return getGroups().filter { group -> group.archivedAt == null }
    }

    suspend fun getGroup(uuid: UUID): MedicationGroup? {
        val database = databaseHolder.get()
        val group = database.medicationGroupDao().getGroup(uuid.toString()) ?: return null
        val medicinesByUuid = database.resolveMedicinesForGroups(listOf(group))
        return group.toMedicationGroupModel(medicinesByUuid)
    }

    suspend fun deleteGroup(uuid: UUID) {
        deleteGroupInternal(uuid, deleteRelatedEntries = false)
    }

    suspend fun deleteGroupAndRelatedEntries(uuid: UUID) {
        deleteGroupInternal(uuid, deleteRelatedEntries = true)
    }

    suspend fun archiveGroup(
        uuid: UUID,
        now: Instant = Instant.now(),
    ) {
        val nowEpochMillis = now.toEpochMilli()
        val nowLocal = now.toLocalDateTime()
        homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.withTransaction { database ->
                val currentOrFuturePlannedSlotCount = database.medicationLogDao()
                    .getCurrentOrFuturePlannedSlotCountForGroup(
                        groupUuid = uuid.toString(),
                        onOrAfterScheduledForIso = nowLocal.toString(),
                    )
                if (currentOrFuturePlannedSlotCount > 0) {
                    throw CurrentOrFuturePlannedSlotsBlockArchiveException(uuid)
                }
                database.medicationGroupDao().updateGroupArchiveState(
                    uuid = uuid.toString(),
                    archivedAtEpochMillis = nowEpochMillis,
                    archivedAtLocalIso = nowLocal.toString(),
                    updatedAtEpochMillis = nowEpochMillis,
                )
            }
        }
    }

    suspend fun updateScheduleTimes(
        groupUuid: UUID,
        newTimes: List<LocalTime>,
        now: Instant = Instant.now(),
    ) {
        homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.withTransaction { database ->
                val groupDao = database.medicationGroupDao()
                val logDao = database.medicationLogDao()
                val existingGroup = groupDao.getGroup(groupUuid.toString())
                    ?: throw MedicationGroupNotFoundException(groupUuid)
                val oldTimeRows = existingGroup.scheduleTimes
                    .sortedBy(MedicationGroupScheduleTimeEntity::sortOrder)
                val oldTimes = oldTimeRows.map { time ->
                    LocalTime.of(time.hourOfDay, time.minuteOfHour)
                }
                val normalizedNewTimes = newTimes.map { time ->
                    time.withSecond(0).withNano(0)
                }

                validateScheduleTimeMigration(oldTimes, normalizedNewTimes)

                val entryIdsBySlot = oldTimes.mapIndexed { index, oldTime ->
                    if (oldTime == normalizedNewTimes[index]) {
                        emptyList()
                    } else {
                        logDao.getPlannedEntryIdsForGroupSlotTime(
                            groupUuid = groupUuid.toString(),
                            scheduleTimeUuid = oldTimeRows[index].uuid,
                            oldTimeIso = oldTime.toScheduleTimeIso(),
                        )
                    }
                }

                groupDao.updateGroupUpdatedAt(
                    uuid = groupUuid.toString(),
                    updatedAtEpochMillis = now.toEpochMilli(),
                )
                groupDao.deleteScheduleTimesForGroup(groupUuid.toString())
                val migratedScheduleTimes = normalizedNewTimes.mapIndexed { index, time ->
                    val oldTimeRow = oldTimeRows[index]
                    MedicationGroupScheduleTimeEntity(
                        uuid = oldTimeRow.uuid,
                        groupUuid = groupUuid.toString(),
                        sortOrder = index,
                        hourOfDay = time.hour,
                        minuteOfHour = time.minute,
                        effectiveFromLocalIso = oldTimeRow.effectiveFromLocalIso,
                    )
                }
                groupDao.insertScheduleTimes(
                    migratedScheduleTimes
                        .sortedWith(
                            compareBy<MedicationGroupScheduleTimeEntity> { scheduleTime ->
                                scheduleTime.hourOfDay
                            }.thenBy { scheduleTime ->
                                scheduleTime.minuteOfHour
                            }.thenBy { scheduleTime ->
                                scheduleTime.uuid
                            }
                        )
                        .mapIndexed { index, scheduleTime ->
                            scheduleTime.copy(sortOrder = index)
                        }
                )
                entryIdsBySlot.forEachIndexed { index, entryUuids ->
                    if (entryUuids.isNotEmpty()) {
                        logDao.updateScheduledForTimeForEntries(
                            entryUuids = entryUuids,
                            newTimeIso = normalizedNewTimes[index].toScheduleTimeIso(),
                        )
                    }
                }
            }
        }
    }

    suspend fun saveGroup(
        uuid: UUID?,
        name: String,
        colorKey: MedicationGroupColorKey,
        schedule: MedicationGroupScheduleInput,
        medications: List<MedicationGroupMedicationInput>,
        notificationsEnabled: Boolean = false,
        includePastScheduledSlots: Boolean = true,
        replacesGroupUuid: UUID? = null,
        now: Instant = Instant.now(),
    ): UUID {
        val nowEpochMillis = now.toEpochMilli()
        val nowLocal = now.toLocalDateTime()
        val groupUuid = uuid ?: UUID.randomUUID()
        homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.withTransaction { database ->
                val dao = database.medicationGroupDao()
                val existingGroup = uuid?.let { dao.getGroup(it.toString()) }
                val existingGroupRow = existingGroup?.group
                val createdAtEpochMillis = existingGroupRow?.createdAtEpochMillis ?: nowEpochMillis
                val isExistingRecreatedGroup = existingGroupRow?.recreatedFromGroupUuid != null
                val hasExistingRecords = existingGroupRow != null &&
                    database.medicationLogDao().getEntryCountForGroup(groupUuid.toString()) > 0
                if (hasExistingRecords) {
                    val lockedExistingGroup = checkNotNull(existingGroup)
                    val existingMedicineUuidsByItemUuid = lockedExistingGroup.items.associate { item ->
                        item.uuid to item.medicineUuid
                    }
                    medications.forEach { medication ->
                        val itemUuid = medication.uuid?.toString() ?: return@forEach
                        if (itemUuid !in existingMedicineUuidsByItemUuid) {
                            return@forEach
                        }
                        // Two null medicineUuids (patch-off slots) compare equal.
                        val previousMedicineUuid = existingMedicineUuidsByItemUuid[itemUuid]
                        if (previousMedicineUuid != medication.medicineUuid?.toString()) {
                            throw LockedMedicationGroupSlotRepointException(groupUuid)
                        }
                    }
                }
                val isExistingRecordlessRecreatedGroup =
                    isExistingRecreatedGroup && !hasExistingRecords
                val resolvedIncludePastScheduledSlots = when {
                    existingGroupRow == null -> replacesGroupUuid == null && includePastScheduledSlots
                    isExistingRecreatedGroup || hasExistingRecords -> existingGroupRow.includePastScheduledSlots
                    else -> includePastScheduledSlots
                }
                val didBackfillModeChange = existingGroupRow != null &&
                    !isExistingRecreatedGroup &&
                    !hasExistingRecords &&
                    existingGroupRow.includePastScheduledSlots != resolvedIncludePastScheduledSlots
                val didRecordlessRecreatedStartDateChange = existingGroupRow != null &&
                    isExistingRecordlessRecreatedGroup &&
                    existingGroupRow.scheduleSinceEpochDay != schedule.since.toEpochDay()
                val recordlessRecreatedEffectiveFromLocal = if (schedule.since.isAfter(nowLocal.toLocalDate())) {
                    schedule.since.atStartOfDay().toString()
                } else {
                    nowLocal.toString()
                }
                val resolvedRecreatedFromGroupUuid = existingGroupRow?.recreatedFromGroupUuid
                    ?: replacesGroupUuid?.toString()
                val existingScheduleTimesByUuid = existingGroup
                    ?.scheduleTimes
                    ?.associateBy(MedicationGroupScheduleTimeEntity::uuid)
                    .orEmpty()
                val preparationTypesByMedicineUuid = database.resolvePreparationTypesByMedicineUuid(
                    medications = medications,
                )

                dao.upsertGroupWithItems(
                    group = MedicationGroupEntity(
                        uuid = groupUuid.toString(),
                        name = name,
                        colorKey = colorKey.name,
                        notificationsEnabled = notificationsEnabled,
                        scheduleType = schedule.type.name,
                        scheduleInterval = schedule.interval,
                        scheduleSinceEpochDay = schedule.since.toEpochDay(),
                        createdAtEpochMillis = createdAtEpochMillis,
                        updatedAtEpochMillis = nowEpochMillis,
                        archivedAtEpochMillis = existingGroupRow?.archivedAtEpochMillis,
                        archivedAtLocalIso = existingGroupRow?.archivedAtLocalIso,
                        includePastScheduledSlots = resolvedIncludePastScheduledSlots,
                        replacedByGroupUuid = existingGroupRow?.replacedByGroupUuid,
                        recreatedFromGroupUuid = resolvedRecreatedFromGroupUuid,
                    ),
                    items = medications.mapIndexed { index, medication ->
                        val preparationType = medication.medicineUuid
                            ?.let(preparationTypesByMedicineUuid::get)
                        validateMedicationCompatibility(
                            fieldPrefix = "medication group item ${medication.medicineUuid}",
                            applicationType = medication.applicationType,
                            doseInstruction = medication.doseInstruction,
                            preparationType = preparationType,
                        )
                        val doseInstructionFields = medication.doseInstruction.toStorageFields()
                        MedicationGroupItemEntity(
                            uuid = (medication.uuid ?: UUID.randomUUID()).toString(),
                            groupUuid = groupUuid.toString(),
                            sortOrder = index,
                            count = medication.count,
                            medicineUuid = medication.medicineUuid?.toString(),
                            applicationType = medication.applicationType.name,
                            doseInstructionKind = doseInstructionFields.doseInstructionKind,
                            tabletFractionNumerator = doseInstructionFields.tabletFractionNumerator,
                            tabletFractionDenominator = doseInstructionFields.tabletFractionDenominator,
                            doseVolumeMl = doseInstructionFields.doseVolumeMl,
                            doseWeightGrams = doseInstructionFields.doseWeightGrams,
                            gelApplicationArea = MedicationGelApplicationArea.DEFAULT.name,
                        )
                    },
                    scheduleTimes = schedule.timeSlots.mapIndexed { index, scheduleTime ->
                        val time = scheduleTime.time.withSecond(0).withNano(0)
                        val scheduleTimeUuid = scheduleTime.uuid ?: UUID.randomUUID()
                        val existingScheduleTime = existingScheduleTimesByUuid[scheduleTimeUuid.toString()]
                        val existingTime = existingScheduleTime?.let { existingTimeRow ->
                            LocalTime.of(existingTimeRow.hourOfDay, existingTimeRow.minuteOfHour)
                        }
                        val effectiveFromLocal = when {
                            isExistingRecreatedGroup -> when {
                                didRecordlessRecreatedStartDateChange ->
                                    recordlessRecreatedEffectiveFromLocal

                                existingScheduleTime != null && existingTime == time ->
                                    existingScheduleTime.effectiveFromLocalIso

                                isExistingRecordlessRecreatedGroup &&
                                    schedule.since.isAfter(nowLocal.toLocalDate()) ->
                                    schedule.since.atStartOfDay().toString()

                                else -> nowLocal.toString()
                            }

                            resolvedIncludePastScheduledSlots ->
                                schedule.since.atStartOfDay().toString()

                            didBackfillModeChange ->
                                nowLocal.toString()

                            existingScheduleTime != null && existingTime == time ->
                                existingScheduleTime.effectiveFromLocalIso

                            else -> nowLocal.toString()
                        }
                        MedicationGroupScheduleTimeEntity(
                            uuid = scheduleTimeUuid.toString(),
                            groupUuid = groupUuid.toString(),
                            sortOrder = index,
                            hourOfDay = time.hour,
                            minuteOfHour = time.minute,
                            effectiveFromLocalIso = effectiveFromLocal,
                        )
                    },
                    weeklyDays = schedule.weeklyDaysOfWeek
                        .sortedBy { it.value }
                        .map { dayOfWeek ->
                            MedicationGroupWeeklyDayEntity(
                                groupUuid = groupUuid.toString(),
                                dayOfWeek = dayOfWeek.value
                            )
                        }
                )
                if (replacesGroupUuid != null) {
                    dao.updateGroupReplacedBy(
                        uuid = replacesGroupUuid.toString(),
                        replacedByGroupUuid = groupUuid.toString(),
                        updatedAtEpochMillis = nowEpochMillis,
                    )
                }
            }
        }

        return groupUuid
    }

    private suspend fun HrtTrackerDatabase.resolvePreparationTypesByMedicineUuid(
        medications: List<MedicationGroupMedicationInput>,
    ): Map<UUID, MedicinePreparationType> {
        val medicineUuids = medications
            .mapNotNull(MedicationGroupMedicationInput::medicineUuid)
            .distinct()
        if (medicineUuids.isEmpty()) {
            return emptyMap()
        }

        val medicineEntitiesByUuid = medicineDao()
            .getByUuids(medicineUuids.map(UUID::toString))
            .associateBy { entity -> entity.uuid }
        return medicineUuids.associateWith { uuid ->
            val entity = checkNotNull(medicineEntitiesByUuid[uuid.toString()]) {
                "Medicine $uuid was not found."
            }
            enumValueOf<MedicinePreparationType>(entity.preparationType)
        }
    }

    private suspend fun deleteGroupInternal(
        uuid: UUID,
        deleteRelatedEntries: Boolean,
    ) {
        val groupUuid = uuid.toString()
        homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.withTransaction { database ->
                if (deleteRelatedEntries) {
                    database.medicationLogDao().deleteEntriesForGroup(groupUuid)
                } else {
                    database.medicationLogDao().reclassifyEntriesForDeletedGroup(groupUuid)
                }
                database.medicationGroupDao().deleteGroup(groupUuid)
            }
        }
    }
}

class MedicationGroupNotFoundException(
    uuid: UUID,
) : NoSuchElementException("Medication group $uuid was not found.")

class CurrentOrFuturePlannedSlotsBlockArchiveException(
    uuid: UUID,
) : IllegalStateException(
    "Medication group $uuid has current or future planned records and cannot be archived."
)

class ScheduleTimeCountMismatchException : IllegalArgumentException(
    "Schedule time count cannot change in locked mode."
)

class ScheduleTimeDuplicateException : IllegalArgumentException(
    "Schedule times must be unique."
)

class LockedMedicationGroupSlotRepointException(
    uuid: UUID,
) : IllegalStateException(
    "Medication group $uuid has logs and cannot change a slot medicine in place."
)

internal fun validateScheduleTimeMigration(
    oldTimes: List<LocalTime>,
    newTimes: List<LocalTime>,
) {
    if (oldTimes.size != newTimes.size) {
        throw ScheduleTimeCountMismatchException()
    }
    val normalizedNewTimes = newTimes.map { time -> time.withSecond(0).withNano(0) }
    if (normalizedNewTimes.distinct().size != normalizedNewTimes.size) {
        throw ScheduleTimeDuplicateException()
    }
}

private fun LocalTime.toScheduleTimeIso(): String {
    return withSecond(0).withNano(0).toString()
}

data class MedicationGroupMedicationInput(
    val uuid: UUID? = null,
    val medicineUuid: UUID?,
    val applicationType: MedicationApplicationType,
    val doseInstruction: DoseInstruction,
    val count: Int = 1,
) {
    init {
        require(count > 0) { "Medication count must be at least 1." }
        require(medicineUuid != null || applicationType == MedicationApplicationType.PATCH_OFF) {
            "Only a PATCH_OFF slot may omit its medicine."
        }
    }
}

data class MedicationGroupScheduleInput(
    val type: MedicationGroupScheduleType,
    val interval: Int,
    val since: LocalDate,
    val weeklyDaysOfWeek: Set<DayOfWeek>,
    val times: List<LocalTime>,
    val timeSlots: List<MedicationGroupScheduleTimeInput> = times.map { time ->
        MedicationGroupScheduleTimeInput(time = time)
    },
)

data class MedicationGroupScheduleTimeInput(
    val uuid: UUID? = null,
    val time: LocalTime,
)

private fun Instant.toLocalDateTime(
    zoneId: ZoneId = ZoneId.systemDefault(),
): LocalDateTime = atZone(zoneId).toLocalDateTime().truncatedTo(ChronoUnit.MINUTES)
