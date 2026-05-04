package com.mkx.hrttracker.data.repository
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.MedicationGroupEntity
import com.mkx.hrttracker.data.local.MedicationGroupItemEntity
import com.mkx.hrttracker.data.local.MedicationGroupScheduleTimeEntity
import com.mkx.hrttracker.data.local.MedicationGroupWeeklyDayEntity
import com.mkx.hrttracker.data.local.MedicationGroupWithItemsEntity
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationDoseKind
import com.mkx.hrttracker.model.medication.MedicationDoseUnit
import com.mkx.hrttracker.model.medication.MedicationGelApplicationArea
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleTime
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelection
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
    @AppScope appScope: CoroutineScope,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val groupsFlow: StateFlow<List<MedicationGroup>?> =
        databaseHolder.databaseFlow
            .flatMapLatest { database ->
                if (database == null) {
                    flowOf<List<MedicationGroup>?>(null)
                } else {
                    database.medicationGroupDao().observeGroups()
                        .map<List<MedicationGroupWithItemsEntity>, List<MedicationGroup>?> { groups ->
                            groups.map { it.toModel() }
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
        return databaseHolder.get().medicationGroupDao().getGroups().map { it.toModel() }
    }

    suspend fun getGroup(uuid: UUID): MedicationGroup? {
        return databaseHolder.get().medicationGroupDao().getGroup(uuid.toString())?.toModel()
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
        databaseHolder.withTransaction { database ->
            database.medicationGroupDao().updateGroupArchiveState(
                uuid = uuid.toString(),
                archivedAtEpochMillis = nowEpochMillis,
                archivedAtLocalIso = nowLocal.toString(),
                updatedAtEpochMillis = nowEpochMillis,
            )
        }
    }

    suspend fun updateScheduleTimes(
        groupUuid: UUID,
        newTimes: List<LocalTime>,
        now: Instant = Instant.now(),
    ) {
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
        databaseHolder.withTransaction { database ->
            val dao = database.medicationGroupDao()
            val existingGroup = uuid?.let { dao.getGroup(it.toString()) }
            val existingGroupRow = existingGroup?.group
            val createdAtEpochMillis = existingGroupRow?.createdAtEpochMillis ?: nowEpochMillis
            val isExistingRecreatedGroup = existingGroupRow?.recreatedFromGroupUuid != null
            val hasExistingRecords = existingGroupRow != null &&
                database.medicationLogDao().getEntryCountForGroup(groupUuid.toString()) > 0
            val resolvedIncludePastScheduledSlots = when {
                existingGroupRow == null -> replacesGroupUuid == null && includePastScheduledSlots
                isExistingRecreatedGroup || hasExistingRecords -> existingGroupRow.includePastScheduledSlots
                else -> includePastScheduledSlots
            }
            val didBackfillModeChange = existingGroupRow != null &&
                !isExistingRecreatedGroup &&
                !hasExistingRecords &&
                existingGroupRow.includePastScheduledSlots != resolvedIncludePastScheduledSlots
            val didBackfilledStartDateChange = existingGroupRow != null &&
                !isExistingRecreatedGroup &&
                !hasExistingRecords &&
                resolvedIncludePastScheduledSlots &&
                existingGroupRow.scheduleSinceEpochDay != schedule.since.toEpochDay()
            val shouldMoveCurrentRowsToSinceStart =
                (didBackfillModeChange && resolvedIncludePastScheduledSlots) ||
                    didBackfilledStartDateChange
            val resolvedRecreatedFromGroupUuid = existingGroupRow?.recreatedFromGroupUuid
                ?: replacesGroupUuid?.toString()
            val existingScheduleTimesByUuid = existingGroup
                ?.scheduleTimes
                ?.associateBy(MedicationGroupScheduleTimeEntity::uuid)
                .orEmpty()

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
                    MedicationGroupItemEntity(
                        uuid = (medication.uuid ?: UUID.randomUUID()).toString(),
                        groupUuid = groupUuid.toString(),
                        sortOrder = index,
                        count = medication.count,
                        category = medication.details.category.name,
                        applicationType = medication.details.applicationType.name,
                        selectionKind = medication.details.selection.kind.name,
                        medicationKey = when (val selection = medication.details.selection) {
                            is MedicationSelection.Catalog -> selection.medicationKey.name
                            is MedicationSelection.Custom -> null
                        },
                        customMedicationName = when (val selection = medication.details.selection) {
                            is MedicationSelection.Catalog -> null
                            is MedicationSelection.Custom -> selection.medicationName
                        },
                        doseKind = medication.details.dose.kind.name,
                        doseValueMg = when (val dose = medication.details.dose) {
                            is MedicationDose.MgAsMedicine -> dose.valueMg
                            is MedicationDose.GelEquivalentEstradiolMg -> dose.valueMg
                            is MedicationDose.PatchTotalMg -> dose.valueMg
                            else -> null
                        },
                        customDoseUnit = when {
                            medication.details.selection is MedicationSelection.Custom &&
                                medication.details.dose is MedicationDose.MgAsMedicine ->
                                medication.details.customDoseUnit.storageValue

                            else -> MedicationDoseUnit.MG.storageValue
                        },
                        doseValuePercent = when (val dose = medication.details.dose) {
                            is MedicationDose.GelPercentAndWeight -> dose.percent
                            else -> null
                        },
                        doseWeightGrams = when (val dose = medication.details.dose) {
                            is MedicationDose.GelPercentAndWeight -> dose.weightGrams
                            else -> null
                        },
                        doseReleaseRateMcgPerDay = when (val dose = medication.details.dose) {
                            is MedicationDose.PatchReleaseRateMcgPerDay -> dose.valueMcgPerDay
                            else -> null
                        },
                        gelApplicationArea = medication.details.gelApplicationArea.name,
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
                        shouldMoveCurrentRowsToSinceStart ->
                            schedule.since.atStartOfDay().toString()

                        didBackfillModeChange ->
                            nowLocal.toString()

                        existingScheduleTime != null && existingTime == time ->
                            existingScheduleTime.effectiveFromLocalIso

                        existingGroup == null && resolvedIncludePastScheduledSlots ->
                            schedule.since.atStartOfDay().toString()

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

        return groupUuid
    }

    private fun MedicationGroupWithItemsEntity.toModel(): MedicationGroup {
        val scheduleTimeSlots = scheduleTimes.sortedBy(MedicationGroupScheduleTimeEntity::sortOrder)
            .map { time ->
                MedicationGroupScheduleTime(
                    uuid = UUID.fromString(time.uuid),
                    time = LocalTime.of(time.hourOfDay, time.minuteOfHour),
                    effectiveFrom = LocalDateTime.parse(time.effectiveFromLocalIso),
                )
            }
        return MedicationGroup(
            uuid = UUID.fromString(group.uuid),
            name = group.name,
            colorKey = MedicationGroupColorKey.fromStorageValue(group.colorKey),
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.fromStorageValue(group.scheduleType),
                interval = group.scheduleInterval,
                since = LocalDate.ofEpochDay(group.scheduleSinceEpochDay),
                weeklyDaysOfWeek = weeklyDays
                    .map { weeklyDay -> DayOfWeek.of(weeklyDay.dayOfWeek) }
                    .toSet(),
                times = scheduleTimeSlots.map(MedicationGroupScheduleTime::time),
                timeSlots = scheduleTimeSlots,
            ),
            medications = items.sortedBy(MedicationGroupItemEntity::sortOrder).map { item ->
                MedicationGroupMedication(
                    uuid = UUID.fromString(item.uuid),
                    details = item.toMedicationDetails(),
                    count = item.count.coerceAtLeast(1)
                )
            },
            notificationsEnabled = group.notificationsEnabled,
            createdAt = Instant.ofEpochMilli(group.createdAtEpochMillis),
            updatedAt = Instant.ofEpochMilli(group.updatedAtEpochMillis),
            archivedAt = group.archivedAtEpochMillis?.let(Instant::ofEpochMilli),
            archivedAtLocal = group.archivedAtLocalIso?.let(LocalDateTime::parse),
            includePastScheduledSlots = group.includePastScheduledSlots,
            replacedByGroupUuid = group.replacedByGroupUuid?.let(UUID::fromString),
            recreatedFromGroupUuid = group.recreatedFromGroupUuid?.let(UUID::fromString),
        )
    }

    private fun MedicationGroupItemEntity.toMedicationDetails(): MedicationDetails {
        val selection = when (MedicationSelectionKind.fromStorageValue(selectionKind)) {
            MedicationSelectionKind.CATALOG -> MedicationSelection.Catalog(
                medicationKey = checkNotNull(MedicationKey.fromStorageValue(medicationKey))
            )

            MedicationSelectionKind.CUSTOM -> MedicationSelection.Custom(
                medicationName = customMedicationName.orEmpty()
            )
        }

        val dose = when (MedicationDoseKind.fromStorageValue(doseKind)) {
            MedicationDoseKind.MG_AS_MEDICINE -> MedicationDose.MgAsMedicine(
                valueMg = checkNotNull(doseValueMg)
            )

            MedicationDoseKind.GEL_EQUIVALENT_ESTRADIOL_MG -> MedicationDose.GelEquivalentEstradiolMg(
                valueMg = checkNotNull(doseValueMg)
            )

            MedicationDoseKind.GEL_PERCENT_AND_WEIGHT -> MedicationDose.GelPercentAndWeight(
                percent = checkNotNull(doseValuePercent),
                weightGrams = checkNotNull(doseWeightGrams)
            )

            MedicationDoseKind.PATCH_TOTAL_MG -> MedicationDose.PatchTotalMg(
                valueMg = checkNotNull(doseValueMg)
            )

            MedicationDoseKind.PATCH_RELEASE_RATE_MCG_DAY -> MedicationDose.PatchReleaseRateMcgPerDay(
                valueMcgPerDay = checkNotNull(doseReleaseRateMcgPerDay)
            )

            MedicationDoseKind.NONE -> MedicationDose.None
        }

        return MedicationDetails(
            category = MedicationCategory.fromStorageValue(category),
            applicationType = MedicationApplicationType.fromStorageValue(applicationType),
            selection = selection,
            dose = dose,
            gelApplicationArea = MedicationGelApplicationArea.fromStorageValue(gelApplicationArea),
            customDoseUnit = MedicationDoseUnit.fromStorageValue(customDoseUnit),
        )
    }

    private suspend fun deleteGroupInternal(
        uuid: UUID,
        deleteRelatedEntries: Boolean,
    ) {
        val groupUuid = uuid.toString()
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

class MedicationGroupNotFoundException(
    uuid: UUID,
) : NoSuchElementException("Medication group $uuid was not found.")

class ScheduleTimeCountMismatchException : IllegalArgumentException(
    "Schedule time count cannot change in locked mode."
)

class ScheduleTimeDuplicateException : IllegalArgumentException(
    "Schedule times must be unique."
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
    val details: MedicationDetails,
    val count: Int = 1,
) {
    init {
        require(count > 0) { "Medication count must be at least 1." }
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
