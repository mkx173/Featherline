package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.MedicationGroupEntity
import com.mkx.hrttracker.data.local.MedicationGroupItemEntity
import com.mkx.hrttracker.data.local.MedicationGroupScheduleTimeEntity
import com.mkx.hrttracker.data.local.MedicationGroupWeeklyDayEntity
import com.mkx.hrttracker.data.local.MedicationGroupWithItemsEntity
import com.mkx.hrttracker.data.local.ScheduledEntryEntity
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationGelApplicationArea
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationGroupSlotKey
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.isScheduledAt
import com.mkx.hrttracker.model.medication.planCalendarDate
import com.mkx.hrttracker.model.medication.time
import com.mkx.hrttracker.model.medication.normalizeMedicationCount
import com.mkx.hrttracker.util.MedicationDayBoundary
import com.mkx.hrttracker.util.dateTimeOn
import com.mkx.hrttracker.util.medicationDay
import com.mkx.hrttracker.util.medicationDayStart
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
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
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
                        // Resolve + map can throw transiently: observeGroups() and
                        // observeMedicineChangeVersion() are independently-invalidated
                        // Room flows, so combine can pair a stale group list with an
                        // already-mutated medicines table (e.g. a restore swaps every
                        // medicine UUID in one transaction). The separate point-in-time
                        // resolveMedicinesForGroups() then can't resolve the old UUID and
                        // toMedicationGroupModel()'s checkNotNull(medicine) throws. Handle
                        // it HERE, per emission, so the failure SUPPRESSES the single
                        // (stale) emission instead of cancelling the upstream Room
                        // observation — the next, consistent emission recovers. A terminal
                        // `.catch` outside flatMapLatest would instead freeze this flow
                        // until the database (and thus the app process) is rebuilt.
                        // Suppress (null -> filterNotNull below) rather than emit a
                        // fallback: a fabricated empty list reaches the Plan combine and
                        // renders a one-frame empty/stale mixture before the consistent
                        // emission lands.
                        runCatching {
                            val medicinesByUuid = database.resolveMedicinesForGroups(groups)
                            groups.map { it.toMedicationGroupModel(medicinesByUuid) }
                        }.getOrElse { error ->
                            // resolveMedicinesForGroups() suspends, so cancellation
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
                                "groups_flow_suppressed count=${groups.size}",
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
                            diagnosticsLogger.warning(TAG, "groups_flow_room_error", error)
                            emit(emptyList())
                        }
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

    fun getCachedGroups(): List<MedicationGroup>? = groupsFlow.value

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

    /** Archives [uuid] and returns its cutoff, the local moment its plan ends. */
    suspend fun archiveGroup(
        uuid: UUID,
        // null archives as of `now` (the confirm-time instant, minute-granular),
        // matching the original archive behavior; a non-null date, yesterday at
        // the latest, archives through the end of that medication day (the
        // plan owns the whole day).
        archivedThroughDate: LocalDate? = null,
        now: Instant = Instant.now(),
    ): LocalDateTime {
        var cutoff: LocalDateTime? = null
        homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.withTransaction { database ->
                cutoff = archiveGroupIn(database, uuid, archivedThroughDate, now)
            }
        }
        return checkNotNull(cutoff)
    }

    private suspend fun archiveGroupIn(
        database: HrtTrackerDatabase,
        uuid: UUID,
        archivedThroughDate: LocalDate?,
        now: Instant,
    ): LocalDateTime {
        val nowEpochMillis = now.toEpochMilli()
        val systemZone = ZoneId.systemDefault()
        val nowLocal = now.toLocalDateTime(systemZone)
        val groupRow = database.medicationGroupDao().getGroup(uuid.toString())
            ?: throw MedicationGroupNotFoundException(uuid)
        // Archived is final: archiving again would move its cutoff.
        if (groupRow.group.archivedAtEpochMillis != null) {
            throw MedicationGroupAlreadyArchivedException(uuid)
        }
        // The cutoff, the latest date and the record floor follow the plan's own day ending.
        val boundary = MedicationDayBoundary.fromSwitch(groupRow.group.dayEndsAtFourAm)
        val archiveCutoffLocal = archivedThroughDate
            ?.let { date -> medicationDayStart(date.plusDays(1), boundary) }
            ?: nowLocal
        val archiveCutoffEpochMillis = if (archivedThroughDate != null) {
            archiveCutoffLocal.atZone(systemZone).toInstant().toEpochMilli()
        } else {
            nowEpochMillis
        }
        val currentOrFuturePlannedSlotCount = database.medicationLogDao()
            .getCurrentOrFuturePlannedSlotCountForGroup(
                groupUuid = uuid.toString(),
                onOrAfterScheduledForIso = nowLocal.toString(),
            )
        if (currentOrFuturePlannedSlotCount > 0) {
            throw CurrentOrFuturePlannedSlotsBlockArchiveException(uuid)
        }
        // The min-date floor only constrains an explicitly picked
        // backdate. The "Now" default (null) archives at the confirm
        // instant — the original pre-backdating behavior — and is never
        // floored: a not-yet-started or future-dated plan stays cancelable
        // as of now, and logged history can't be truncated this way because
        // doses scheduled at/after now are already rejected by the
        // current/future planned-slot guard above.
        if (archivedThroughDate != null) {
            // Ceiling: never store a future cutoff. archivedAt is set at the
            // mutation instant, so a future cutoff would hide the group from
            // active lists/reminders now while its plan still owns the slots
            // up to that cutoff. Through yesterday at the latest, the cutoff is
            // at most the start of today's medication day; "Now" covers today.
            // The UI clamps to yesterday, but the repository is the
            // authoritative invariant enforcer for non-UI callers.
            val maxArchiveDate = medicationDay(nowLocal, boundary).minusDays(1)
            if (archivedThroughDate.isAfter(maxArchiveDate)) {
                throw ArchiveDateAfterTodayException(
                    uuid = uuid,
                    archivedThroughDate = archivedThroughDate,
                    maxArchiveDate = maxArchiveDate,
                )
            }
            // Floor on the schedule start (plan's first owned day), not the
            // wall-clock creation time: a backdated/backfilled plan can start
            // before the group row was created, and archiving through any day
            // from the start onward yields a valid (non-empty) plan window.
            val scheduleSinceDate =
                LocalDate.ofEpochDay(groupRow.group.scheduleSinceEpochDay)
            val latestRecordedDoseDate = database.medicationLogDao()
                .getEntriesForGroup(uuid.toString())
                .maxOfOrNull { entry ->
                    planCalendarDate(
                        scheduledForIso = entry.scheduledForIso,
                        appliedAtEpochMillis = entry.appliedAtEpochMillis,
                        appliedAtTimeZoneId = entry.appliedAtTimeZoneId,
                        boundary = boundary,
                        zoneId = systemZone,
                    )
                }
            val minArchiveDate =
                maxOf(scheduleSinceDate, latestRecordedDoseDate ?: scheduleSinceDate)
            if (archivedThroughDate.isBefore(minArchiveDate)) {
                throw ArchiveDateBeforeRecordedDoseException(
                    uuid = uuid,
                    archivedThroughDate = archivedThroughDate,
                    minArchiveDate = minArchiveDate,
                )
            }
        }
        database.medicationGroupDao().updateGroupArchiveState(
            uuid = uuid.toString(),
            archivedAtEpochMillis = archiveCutoffEpochMillis,
            archivedAtLocalIso = archiveCutoffLocal.toString(),
            updatedAtEpochMillis = nowEpochMillis,
        )
        return archiveCutoffLocal
    }

    /**
     * Moves each of a locked plan's records with its slot, keyed by the slot's uuid: to the slot's
     * new time on the same medication day. With the day ending at 04:00, 23:00 moved to 01:00 is
     * that night, not the morning before.
     */
    private suspend fun moveRecordsWithTheirSlots(
        database: HrtTrackerDatabase,
        groupUuid: UUID,
        savedSlots: List<MedicationGroupScheduleTimeEntity>,
        timeSlots: List<MedicationGroupScheduleTimeInput>,
        boundary: MedicationDayBoundary,
    ) {
        val logDao = database.medicationLogDao()
        val newTimesBySlotUuid = timeSlots.associate { slot ->
            slot.uuid.toString() to slot.time.withSecond(0).withNano(0)
        }
        // Every slot's records are read before any moves, so a swap doesn't move them twice.
        val entriesBySlot = savedSlots.mapNotNull { savedSlot ->
            val oldTime = LocalTime.of(savedSlot.hourOfDay, savedSlot.minuteOfHour)
            val newTime = newTimesBySlotUuid[savedSlot.uuid]
                ?.takeIf { time -> time != oldTime }
                ?: return@mapNotNull null
            newTime to logDao.getPlannedEntriesForGroupSlotTime(
                groupUuid = groupUuid.toString(),
                scheduleTimeUuid = savedSlot.uuid,
                oldTimeIso = oldTime.toScheduleTimeIso(),
            )
        }
        entriesBySlot.forEach { (newTime, entries) ->
            // A slot's records share their old time, so they move by the same days, in bulk.
            entries.groupBy { entry ->
                val scheduledFor = LocalDateTime.parse(entry.scheduledForIso)
                ChronoUnit.DAYS.between(
                    scheduledFor.toLocalDate(),
                    boundary.dateTimeOn(medicationDay(scheduledFor, boundary), newTime).toLocalDate(),
                )
            }.forEach { (dayShift, shiftedEntries) ->
                // ponytail: chunked under SQLite's oldest 999-variable limit.
                shiftedEntries.map(ScheduledEntryEntity::uuid).chunked(900).forEach { uuids ->
                    logDao.moveScheduledForEntries(
                        entryUuids = uuids,
                        dayShift = "$dayShift days",
                        newTimeIso = newTime.toScheduleTimeIso(),
                    )
                }
            }
        }
    }

    /** Saves a new plan when [uuid] is null, else edits the plan [uuid], refused as [saveRefusal] says. */
    suspend fun saveGroup(
        uuid: UUID?,
        name: String,
        colorKey: MedicationGroupColorKey,
        schedule: MedicationGroupScheduleInput,
        medications: List<MedicationGroupMedicationInput>,
        notificationsEnabled: Boolean = false,
        includePastScheduledSlots: Boolean = true,
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
                val hasExistingRecords = existingGroupRow != null &&
                        database.medicationLogDao().getEntryCountForGroup(groupUuid.toString()) > 0
                // Each planned record is an occurrence of its slot, and stays one: the slots
                // stay, and a slot's new time moves its records.
                val plannedRecords = if (existingGroup == null) {
                    emptyList()
                } else {
                    database.medicationLogDao().getEntriesForGroup(groupUuid.toString())
                }.mapNotNull { entry ->
                    entry.scheduledForIso?.let { scheduledForIso ->
                        MedicationGroupSlotKey(
                            scheduleTimeUuid = entry.scheduleTimeUuid?.let(UUID::fromString),
                            scheduledFor = LocalDateTime.parse(scheduledForIso),
                        )
                    }
                }
                val savedGroup = existingGroup?.toMedicationGroupModel(
                    database.resolveMedicinesForGroups(listOf(existingGroup)),
                )
                saveRefusal(
                    isEditing = uuid != null,
                    saved = savedGroup,
                    schedule = schedule,
                    medications = medications,
                    includePastScheduledSlots = includePastScheduledSlots,
                    hasRecords = hasExistingRecords,
                    records = plannedRecords,
                )?.let { refusal -> throw MedicationGroupSaveRefusedException(groupUuid, refusal) }
                val isRecordLocked = plannedRecords.isNotEmpty()
                if (isRecordLocked) {
                    val lockedExistingGroup = checkNotNull(existingGroup)
                    validateScheduleTimeMigration(
                        oldTimes = lockedExistingGroup.toScheduleModel().times,
                        newTimes = schedule.timeSlots.map(MedicationGroupScheduleTimeInput::time),
                    )
                    moveRecordsWithTheirSlots(
                        database = database,
                        groupUuid = groupUuid,
                        savedSlots = lockedExistingGroup.scheduleTimes,
                        timeSlots = schedule.timeSlots,
                        boundary = checkNotNull(savedGroup).schedule.dayBoundary,
                    )
                }
                // Once a plan has records or is archived, its day ending stays.
                // A new plan (no row yet) takes the input.
                val resolvedDayEndsAtFourAm = if (
                    existingGroupRow != null &&
                    (hasExistingRecords || existingGroupRow.archivedAtEpochMillis != null)
                ) {
                    existingGroupRow.dayEndsAtFourAm
                } else {
                    schedule.dayBoundary.endsAtFourAm
                }
                val didBackfillModeChange = existingGroupRow != null &&
                        existingGroupRow.includePastScheduledSlots != includePastScheduledSlots
                val scheduleStartLocal = schedule.startAt ?: schedule.since.atStartOfDay()
                val existingScheduleTimesByUuid = existingGroup
                    ?.scheduleTimes
                    ?.associateBy(MedicationGroupScheduleTimeEntity::uuid)
                    .orEmpty()
                val preparationTypesByMedicineUuid = database.resolvePreparationTypesByMedicineUuid(
                    medications = medications,
                )
                // saveRefusal keeps a locked plan's counts as the editor shows them, and it shows
                // some legacy ones as 1, so the stored counts stand.
                val lockedItemCounts = if (isRecordLocked) {
                    checkNotNull(existingGroup).items.associate { item -> item.uuid to item.count }
                } else {
                    emptyMap()
                }

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
                        includePastScheduledSlots = includePastScheduledSlots,
                        replacedByGroupUuid = existingGroupRow?.replacedByGroupUuid,
                        // Only carried: nothing reads it any more.
                        recreatedFromGroupUuid = existingGroupRow?.recreatedFromGroupUuid,
                        dayEndsAtFourAm = resolvedDayEndsAtFourAm,
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
                            count = lockedItemCounts[medication.uuid?.toString()] ?: medication.count,
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
                        val existingScheduleTime =
                            existingScheduleTimesByUuid[scheduleTimeUuid.toString()]
                        // A locked plan's slot moved its records to its new time: still the same slot.
                        val existingTime = existingScheduleTime?.let { existingTimeRow ->
                            if (isRecordLocked) {
                                time
                            } else {
                                LocalTime.of(existingTimeRow.hourOfDay, existingTimeRow.minuteOfHour)
                            }
                        }
                        val effectiveFromLocal = when {
                            includePastScheduledSlots ->
                                scheduleStartLocal.toString()

                            didBackfillModeChange ->
                                maxOf(nowLocal, schedule.startAt ?: nowLocal).toString()

                            existingScheduleTime != null && existingTime == time ->
                                existingScheduleTime.keptEffectiveFrom(nowLocal, schedule.startAt)

                            // effectiveFrom marks when this time was added (now),
                            // not the start date. `since` already gates generation
                            // via isScheduledAt, so a future start needs nothing
                            // more here — and pinning effectiveFrom to `since`
                            // would orphan slots if the start is later edited
                            // earlier (the preserved future effectiveFrom would
                            // filter the now-valid slots). An explicit start later
                            // than now still holds, and keptEffectiveFrom lowers it
                            // again if the start later moves earlier.
                            else -> maxOf(nowLocal, schedule.startAt ?: nowLocal).toString()
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

class ArchiveDateBeforeRecordedDoseException(
    uuid: UUID,
    archivedThroughDate: LocalDate,
    minArchiveDate: LocalDate,
) : IllegalArgumentException(
    "Medication group $uuid archive date $archivedThroughDate is before minimum archive date $minArchiveDate."
)

/** An archive date of today or later: a plan is archived through a past day, or as of now. */
class ArchiveDateAfterTodayException(
    uuid: UUID,
    archivedThroughDate: LocalDate,
    maxArchiveDate: LocalDate,
) : IllegalArgumentException(
    "Medication group $uuid archive date $archivedThroughDate is after maximum archive date $maxArchiveDate."
)

class ScheduleTimeCountMismatchException : IllegalArgumentException(
    "Schedule time count cannot change in locked mode."
)

class ScheduleTimeDuplicateException : IllegalArgumentException(
    "Schedule times must be unique."
)

class MedicationGroupAlreadyArchivedException(
    uuid: UUID,
) : IllegalStateException("Medication group $uuid is already archived.")

/** Why a save is refused, as [saveRefusal] gives it, for the editor to say. */
enum class SaveRefusal {
    /** The plan was deleted while it was being edited. */
    GROUP_DELETED,

    /** The change would take the plan's records off their slots, medicines or day ending. */
    LOCKED_BY_RECORDS,

    /** A retime would move a logged dose, with its slot, to before the plan's start. */
    LOGGED_DOSE_BEFORE_START,
}

class MedicationGroupSaveRefusedException(
    uuid: UUID,
    val refusal: SaveRefusal,
) : IllegalStateException("Medication group $uuid can't be saved: $refusal.")

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
    /** The first moment a slot can fall on; null starts with the whole of [since]. */
    val startAt: LocalDateTime? = null,
    /** Ignored once the plan has records or is archived; it then keeps its stored day ending. */
    val dayBoundary: MedicationDayBoundary = MedicationDayBoundary.Midnight,
)

/**
 * Why saving [schedule] and [medications] over the plan [saved] is refused, or null when it may
 * be. The editor asks it as a time is picked and at Save, and the repository at commit, so all
 * three refuse the same drafts for the same reason. Editing ([isEditing]) a plan with no [saved]
 * is editing one deleted since. Any record ([hasRecords]) keeps the plan's day ending, its past
 * option ([includePastScheduledSlots]) and its slots' medicines; a planned one ([records]) keeps
 * its slots and items (counts included) too, and stays an occurrence of its slot, from the plan's
 * start on.
 */
internal fun saveRefusal(
    isEditing: Boolean,
    saved: MedicationGroup?,
    schedule: MedicationGroupScheduleInput,
    medications: List<MedicationGroupMedicationInput>,
    includePastScheduledSlots: Boolean,
    hasRecords: Boolean,
    records: List<MedicationGroupSlotKey>,
): SaveRefusal? {
    if (!isEditing) return null
    if (saved == null) return SaveRefusal.GROUP_DELETED
    if (!hasRecords) return null
    val savedItems = saved.medications.associateBy(MedicationGroupMedication::uuid)
    // Two null medicineUuids (patch-off slots) compare equal.
    val repointsASlot = medications.any { medication ->
        val savedItem = medication.uuid?.let(savedItems::get)
        savedItem != null && savedItem.medicineUuid != medication.medicineUuid
    }
    if (
        repointsASlot ||
        schedule.dayBoundary != saved.schedule.dayBoundary ||
        includePastScheduledSlots != saved.includePastScheduledSlots
    ) {
        return SaveRefusal.LOCKED_BY_RECORDS
    }
    if (records.isEmpty()) return null
    if (!schedule.keepsRecordsOf(saved.schedule, records)) {
        return if (schedule.movesARecordBeforeItsStart(saved.schedule, records)) {
            SaveRefusal.LOGGED_DOSE_BEFORE_START
        } else {
            SaveRefusal.LOCKED_BY_RECORDS
        }
    }
    // A record is on the plan while its medicine and dose are one of the plan's, and a slot is
    // logged by its items' counts: the items stay as they are.
    val keepsItems = medications.size == savedItems.size &&
            medications.all { medication ->
                val savedItem = medication.uuid?.let(savedItems::get)
                savedItem != null &&
                        savedItem.medicineUuid == medication.medicineUuid &&
                        savedItem.applicationType == medication.applicationType &&
                        savedItem.doseInstruction == medication.doseInstruction &&
                        // As the editor shows it: a count it can't edit shows as 1.
                        medication.count == normalizeMedicationCount(
                            applicationType = savedItem.applicationType,
                            count = savedItem.count,
                            preparationType = savedItem.medicine?.preparation?.type,
                        )
            }
    return if (keepsItems) null else SaveRefusal.LOCKED_BY_RECORDS
}

/**
 * Whether saving this over a plan scheduled as [saved] keeps each of its [records] that is an
 * occurrence of a slot an occurrence of that slot, as a plan with records must: the same number
 * of slots, the record's own still there, and the record, moved with its slot's time to the same
 * medication day, still on one of the schedule's days from its start and its slot's on.
 */
private fun MedicationGroupScheduleInput.keepsRecordsOf(
    saved: MedicationGroupSchedule,
    records: List<MedicationGroupSlotKey>,
): Boolean {
    if (timeSlots.size != saved.timeSlots.size) return false
    val edited = MedicationGroupSchedule(type, interval, since, weeklyDaysOfWeek, times, dayBoundary)
    val start = startAt ?: medicationDayStart(since, dayBoundary)
    return records.all { record ->
        val savedSlot = saved.timeSlots.firstOrNull { slot -> slot.uuid == record.scheduleTimeUuid }
            ?: saved.timeSlots.firstOrNull { slot ->
                record.scheduleTimeUuid == null && slot.time == record.time
            }
        if (
            savedSlot == null ||
            !saved.isScheduledAt(record.scheduledFor) ||
            record.scheduledFor.isBefore(savedSlot.effectiveFrom)
        ) {
            // Not an occurrence of a slot now, so a save can't take it off one.
            return@all true
        }
        val time = timeSlots.firstOrNull { slot -> slot.uuid == savedSlot.uuid }
            ?.time?.withSecond(0)?.withNano(0)
            ?: return@all false
        val scheduledFor = if (time == savedSlot.time) {
            record.scheduledFor
        } else {
            saved.dayBoundary.dateTimeOn(medicationDay(record.scheduledFor, saved.dayBoundary), time)
        }
        // Not before the plan's start, nor before the slot's own (a slot added later starts then).
        edited.isScheduledAt(scheduledFor) && !scheduledFor.isBefore(maxOf(start, savedSlot.effectiveFrom))
    }
}

/**
 * Whether saving this would move one of [records], with its slot's new time, to before the plan's
 * start or its slot's: [keepsRecordsOf] refuses it, but not with every slot back at its saved time.
 * A retime keeps a record on its medication day, so that is the only way a new time can lose it.
 */
private fun MedicationGroupScheduleInput.movesARecordBeforeItsStart(
    saved: MedicationGroupSchedule,
    records: List<MedicationGroupSlotKey>,
): Boolean {
    val savedTimes = saved.timeSlots.associate { slot -> slot.uuid to slot.time }
    val atSavedTimes = timeSlots.map { slot ->
        slot.copy(time = slot.uuid?.let(savedTimes::get) ?: slot.time)
    }
    return !keepsRecordsOf(saved, records) &&
            copy(times = atSavedTimes.map { slot -> slot.time }, timeSlots = atSavedTimes)
                .keepsRecordsOf(saved, records)
}

/**
 * Keeps an unchanged time's effectiveFrom, clamped to [startAt]. One still ahead
 * of now was pinned by an earlier startAt, so it drops back to now before the
 * clamp and an earlier start can reopen its slots.
 */
private fun MedicationGroupScheduleTimeEntity.keptEffectiveFrom(
    nowLocal: LocalDateTime,
    startAt: LocalDateTime?,
): String {
    val kept = minOf(LocalDateTime.parse(effectiveFromLocalIso), nowLocal)
    return (startAt?.let { maxOf(kept, it) } ?: kept).toString()
}

data class MedicationGroupScheduleTimeInput(
    val uuid: UUID? = null,
    val time: LocalTime,
)

private fun Instant.toLocalDateTime(
    zoneId: ZoneId = ZoneId.systemDefault(),
): LocalDateTime = atZone(zoneId).toLocalDateTime().truncatedTo(ChronoUnit.MINUTES)

private const val TAG = "MedicationGroupRepository"
