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

    suspend fun archiveGroup(
        uuid: UUID,
        // null archives as of `now` (the confirm-time instant, minute-granular),
        // matching the original archive behavior; a non-null date, yesterday at
        // the latest, archives through the end of that medication day (the
        // plan owns the whole day).
        archivedThroughDate: LocalDate? = null,
        now: Instant = Instant.now(),
    ) {
        homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.withTransaction { database ->
                archiveGroupIn(database, uuid, archivedThroughDate, now)
            }
        }
    }

    private suspend fun archiveGroupIn(
        database: HrtTrackerDatabase,
        uuid: UUID,
        archivedThroughDate: LocalDate?,
        now: Instant,
    ) {
        val nowEpochMillis = now.toEpochMilli()
        val systemZone = ZoneId.systemDefault()
        val nowLocal = now.toLocalDateTime(systemZone)
        val groupRow = database.medicationGroupDao().getGroup(uuid.toString())
            ?: throw MedicationGroupNotFoundException(uuid)
        // Archived is final: archiving again would move the cutoff its successor starts from.
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

    /**
     * Archives [uuid] as [archiveGroup] does and saves the plan replacing it in the same
     * transaction, so neither commits without the other. The new plan starts at [schedule]'s
     * start, as a new plan showing its past does, but never before the archived plan's cutoff.
     */
    suspend fun archiveAndRecreateGroup(
        uuid: UUID,
        archivedThroughDate: LocalDate?,
        name: String,
        colorKey: MedicationGroupColorKey,
        schedule: MedicationGroupScheduleInput,
        medications: List<MedicationGroupMedicationInput>,
        notificationsEnabled: Boolean,
        now: Instant = Instant.now(),
    ): UUID = saveGroup(
        uuid = null,
        name = name,
        colorKey = colorKey,
        schedule = schedule,
        medications = medications,
        notificationsEnabled = notificationsEnabled,
        includePastScheduledSlots = true,
        replacesGroupUuid = uuid,
        now = now,
        beforeSave = { database -> archiveGroupIn(database, uuid, archivedThroughDate, now) },
    )

    /** Saves a new plan when [uuid] is null, else edits the plan [uuid], which must exist. */
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
    ): UUID = saveGroup(
        uuid = uuid,
        name = name,
        colorKey = colorKey,
        schedule = schedule,
        medications = medications,
        notificationsEnabled = notificationsEnabled,
        includePastScheduledSlots = includePastScheduledSlots,
        replacesGroupUuid = replacesGroupUuid,
        now = now,
        beforeSave = {},
    )

    private suspend fun saveGroup(
        uuid: UUID?,
        name: String,
        colorKey: MedicationGroupColorKey,
        schedule: MedicationGroupScheduleInput,
        medications: List<MedicationGroupMedicationInput>,
        notificationsEnabled: Boolean,
        includePastScheduledSlots: Boolean,
        replacesGroupUuid: UUID?,
        now: Instant,
        // Runs first in the save's transaction.
        beforeSave: suspend (HrtTrackerDatabase) -> Unit,
    ): UUID {
        val nowEpochMillis = now.toEpochMilli()
        val nowLocal = now.toLocalDateTime()
        val groupUuid = uuid ?: UUID.randomUUID()
        homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.withTransaction { database ->
                beforeSave(database)
                val dao = database.medicationGroupDao()
                val existingGroup = uuid?.let { dao.getGroup(it.toString()) }
                // An edit of a plan deleted meanwhile doesn't bring it back under its old uuid.
                if (uuid != null && existingGroup == null) {
                    throw MedicationGroupNotFoundException(uuid)
                }
                val existingGroupRow = existingGroup?.group
                val createdAtEpochMillis = existingGroupRow?.createdAtEpochMillis ?: nowEpochMillis
                val hasExistingRecords = existingGroupRow != null &&
                        database.medicationLogDao().getEntryCountForGroup(groupUuid.toString()) > 0
                if (hasExistingRecords) {
                    val lockedExistingGroup = checkNotNull(existingGroup)
                    val existingMedicineUuidsByItemUuid =
                        lockedExistingGroup.items.associate { item ->
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
                // A recreated plan shows its past as any plan does: the cutoff floor below keeps
                // it off its predecessor's slots.
                val resolvedIncludePastScheduledSlots = if (hasExistingRecords) {
                    checkNotNull(existingGroupRow).includePastScheduledSlots
                } else {
                    includePastScheduledSlots
                }
                // A record logged after the editor loaded locks the ending, and the
                // draft's start date may have moved with its toggle: refuse the save
                // rather than keep the ending but take the moved date.
                if (
                    hasExistingRecords &&
                    schedule.dayBoundary.endsAtFourAm != existingGroupRow?.dayEndsAtFourAm
                ) {
                    throw LockedMedicationGroupDayBoundaryException(groupUuid)
                }
                // Each planned record is an occurrence of its slot, and stays one: the slots
                // stay, a slot's new time moves its records, and a schedule that would lose
                // one (a record logged after the editor read the plan) is refused.
                val plannedRecords = if (existingGroup == null) {
                    emptyList()
                } else {
                    database.medicationLogDao().getEntriesForGroup(groupUuid.toString())
                        .mapNotNull { entry ->
                            entry.scheduledForIso?.let { scheduledForIso ->
                                MedicationGroupSlotKey(
                                    scheduleTimeUuid = entry.scheduleTimeUuid?.let(UUID::fromString),
                                    scheduledFor = LocalDateTime.parse(scheduledForIso),
                                )
                            }
                        }
                }
                val isRecordLocked = plannedRecords.isNotEmpty()
                if (isRecordLocked) {
                    val lockedExistingGroup = checkNotNull(existingGroup)
                    val savedSchedule = lockedExistingGroup.toScheduleModel()
                    validateScheduleTimeMigration(
                        oldTimes = savedSchedule.times,
                        newTimes = schedule.timeSlots.map(MedicationGroupScheduleTimeInput::time),
                    )
                    if (!schedule.keepsRecordsOf(savedSchedule, plannedRecords)) {
                        throw LockedMedicationGroupScheduleException(groupUuid)
                    }
                    // A record is on the plan while its medicine and dose are one of the plan's,
                    // and a slot is logged by its items' counts: the items stay as they are.
                    val savedItems = lockedExistingGroup
                        .toMedicationGroupModel(database.resolveMedicinesForGroups(listOf(lockedExistingGroup)))
                        .medications
                        .associateBy(MedicationGroupMedication::uuid)
                    val keepsItems = medications.size == savedItems.size &&
                            medications.all { medication ->
                                val savedItem = medication.uuid?.let(savedItems::get)
                                savedItem != null &&
                                        savedItem.medicineUuid == medication.medicineUuid &&
                                        savedItem.applicationType == medication.applicationType &&
                                        savedItem.doseInstruction == medication.doseInstruction
                            }
                    if (!keepsItems) {
                        throw LockedMedicationGroupMedicationsException(groupUuid)
                    }
                    moveRecordsWithTheirSlots(
                        database = database,
                        groupUuid = groupUuid,
                        savedSlots = lockedExistingGroup.scheduleTimes,
                        timeSlots = schedule.timeSlots,
                        boundary = savedSchedule.dayBoundary,
                    )
                }
                // Once a plan has records or is archived, its day ending stays.
                // A new or recreated plan (no row yet) takes the input.
                val resolvedDayEndsAtFourAm = if (
                    existingGroupRow != null &&
                    (hasExistingRecords || existingGroupRow.archivedAtEpochMillis != null)
                ) {
                    existingGroupRow.dayEndsAtFourAm
                } else {
                    schedule.dayBoundary.endsAtFourAm
                }
                val didBackfillModeChange = existingGroupRow != null &&
                        !hasExistingRecords &&
                        existingGroupRow.includePastScheduledSlots != resolvedIncludePastScheduledSlots
                val scheduleStartLocal = schedule.startAt ?: schedule.since.atStartOfDay()
                // A successor replaces an archived plan that nothing else replaces yet.
                replacesGroupUuid?.let { dao.getGroup(it.toString())?.group }?.let { predecessor ->
                    val replacedBy = predecessor.replacedByGroupUuid ?: groupUuid.toString()
                    if (predecessor.archivedAtEpochMillis == null || replacedBy != groupUuid.toString()) {
                        throw MedicationGroupNotReplaceableException(UUID.fromString(predecessor.uuid))
                    }
                }
                val resolvedRecreatedFromGroupUuid = existingGroupRow?.recreatedFromGroupUuid
                    ?: replacesGroupUuid?.toString()
                // A recreated plan never starts before its predecessor's archive cutoff,
                // whatever start or day ending it's saved with, so no slot has two plans.
                val predecessorCutoffLocal = resolvedRecreatedFromGroupUuid
                    ?.let { predecessorUuid -> dao.getGroup(predecessorUuid)?.group }
                    // The local cutoff its ownership window uses, whatever the zone now.
                    ?.archivedAtLocalIso
                    ?.let(LocalDateTime::parse)
                // Nor on a date before the cutoff's own day, refused as an archive date after
                // yesterday is: the editor never offers one.
                predecessorCutoffLocal?.let { cutoff ->
                    val minStartDate = medicationDay(
                        cutoff,
                        MedicationDayBoundary.fromSwitch(resolvedDayEndsAtFourAm),
                    )
                    if (schedule.since.isBefore(minStartDate)) {
                        throw RecreatedStartBeforeCutoffException(groupUuid, schedule.since, minStartDate)
                    }
                }
                val existingScheduleTimesByUuid = existingGroup
                    ?.scheduleTimes
                    ?.associateBy(MedicationGroupScheduleTimeEntity::uuid)
                    .orEmpty()
                val preparationTypesByMedicineUuid = database.resolvePreparationTypesByMedicineUuid(
                    medications = medications,
                )
                // A locked plan's counts can't be edited, and the editor shows some legacy ones
                // as 1, so its stored counts stand.
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
                        includePastScheduledSlots = resolvedIncludePastScheduledSlots,
                        replacedByGroupUuid = existingGroupRow?.replacedByGroupUuid,
                        recreatedFromGroupUuid = resolvedRecreatedFromGroupUuid,
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
                            resolvedIncludePastScheduledSlots ->
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
                            effectiveFromLocalIso = predecessorCutoffLocal
                                ?.let { maxOf(LocalDateTime.parse(effectiveFromLocal), it).toString() }
                                ?: effectiveFromLocal,
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

class ArchiveDateBeforeRecordedDoseException(
    uuid: UUID,
    archivedThroughDate: LocalDate,
    minArchiveDate: LocalDate,
) : IllegalArgumentException(
    "Medication group $uuid archive date $archivedThroughDate is before minimum archive date $minArchiveDate."
)

/** A recreated plan's start date before its predecessor's cutoff's day, so the two would overlap. */
class RecreatedStartBeforeCutoffException(
    uuid: UUID,
    since: LocalDate,
    minStartDate: LocalDate,
) : IllegalArgumentException(
    "Medication group $uuid start date $since is before minimum start date $minStartDate."
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

class LockedMedicationGroupSlotRepointException(
    uuid: UUID,
) : IllegalStateException(
    "Medication group $uuid has logs and cannot change a slot medicine in place."
)

class LockedMedicationGroupDayBoundaryException(
    uuid: UUID,
) : IllegalStateException(
    "Medication group $uuid has logs and cannot change its day ending."
)

class LockedMedicationGroupScheduleException(
    uuid: UUID,
) : IllegalStateException(
    "Medication group $uuid has records that this schedule would take off their slots."
)

class LockedMedicationGroupMedicationsException(
    uuid: UUID,
) : IllegalStateException(
    "Medication group $uuid has records and cannot add, remove or change its medications."
)

class MedicationGroupAlreadyArchivedException(
    uuid: UUID,
) : IllegalStateException("Medication group $uuid is already archived.")

class MedicationGroupNotReplaceableException(
    uuid: UUID,
) : IllegalStateException(
    "Medication group $uuid is active or already replaced, so nothing can replace it."
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
    /** The first moment a slot can fall on; null starts with the whole of [since]. */
    val startAt: LocalDateTime? = null,
    /** Ignored once the plan has records or is archived; it then keeps its stored day ending. */
    val dayBoundary: MedicationDayBoundary = MedicationDayBoundary.Midnight,
)

/**
 * Whether saving this over a plan scheduled as [saved] keeps each of its [records] that is an
 * occurrence of a slot an occurrence of that slot, as a plan with records must: the same number
 * of slots, the record's own still there, and the record, moved with its slot's time to the same
 * medication day, still on one of the schedule's days from its start and its slot's on.
 */
internal fun MedicationGroupScheduleInput.keepsRecordsOf(
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
