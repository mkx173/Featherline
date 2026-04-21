package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.MedicationGroupEntity
import com.mkx.hrttracker.data.local.MedicationGroupItemEntity
import com.mkx.hrttracker.data.local.MedicationGroupScheduleTimeEntity
import com.mkx.hrttracker.data.local.MedicationGroupWeeklyDayEntity
import com.mkx.hrttracker.data.local.MedicationGroupWithItemsEntity
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationDoseKind
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationLogEntrySourceType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelection
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
import androidx.room.withTransaction
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
import java.time.LocalTime
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

    suspend fun getGroups(): List<MedicationGroup> {
        return databaseHolder.get().medicationGroupDao().getGroups().map { it.toModel() }
    }

    suspend fun getGroup(uuid: UUID): MedicationGroup? {
        return databaseHolder.get().medicationGroupDao().getGroup(uuid.toString())?.toModel()
    }

    suspend fun deleteGroup(uuid: UUID) {
        val database = databaseHolder.get()
        val groupUuid = uuid.toString()
        database.withTransaction {
            database.medicationLogDao().reclassifyEntriesForDeletedGroup(
                groupUuid = groupUuid,
                manualSourceType = MedicationLogEntrySourceType.MANUAL.name
            )
            database.medicationGroupDao().deleteGroup(groupUuid)
        }
    }

    suspend fun saveGroup(
        uuid: UUID?,
        name: String,
        schedule: MedicationGroupScheduleInput,
        medications: List<MedicationGroupMedicationInput>,
        notificationsEnabled: Boolean = false
    ): UUID {
        val dao = databaseHolder.get().medicationGroupDao()
        val nowEpochMillis = Instant.now().toEpochMilli()
        val groupUuid = uuid ?: UUID.randomUUID()
        val existingGroup = uuid?.let { dao.getGroup(it.toString()) }
        val createdAtEpochMillis = existingGroup?.group?.createdAtEpochMillis ?: nowEpochMillis

        dao.upsertGroupWithItems(
            group = MedicationGroupEntity(
                uuid = groupUuid.toString(),
                name = name,
                notificationsEnabled = notificationsEnabled,
                scheduleType = schedule.type.name,
                scheduleInterval = schedule.interval,
                scheduleSinceEpochDay = schedule.since.toEpochDay(),
                createdAtEpochMillis = createdAtEpochMillis,
                updatedAtEpochMillis = nowEpochMillis
            ),
            items = medications.mapIndexed { index, medication ->
                MedicationGroupItemEntity(
                    uuid = (medication.uuid ?: UUID.randomUUID()).toString(),
                    groupUuid = groupUuid.toString(),
                    sortOrder = index,
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
                )
            },
            scheduleTimes = schedule.times.mapIndexed { index, time ->
                MedicationGroupScheduleTimeEntity(
                    groupUuid = groupUuid.toString(),
                    sortOrder = index,
                    hourOfDay = time.hour,
                    minuteOfHour = time.minute
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

        return groupUuid
    }

    private fun MedicationGroupWithItemsEntity.toModel(): MedicationGroup {
        return MedicationGroup(
            uuid = UUID.fromString(group.uuid),
            name = group.name,
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.fromStorageValue(group.scheduleType),
                interval = group.scheduleInterval,
                since = LocalDate.ofEpochDay(group.scheduleSinceEpochDay),
                weeklyDaysOfWeek = weeklyDays
                    .map { weeklyDay -> DayOfWeek.of(weeklyDay.dayOfWeek) }
                    .toSet(),
                times = scheduleTimes.sortedBy(MedicationGroupScheduleTimeEntity::sortOrder).map { time ->
                    LocalTime.of(time.hourOfDay, time.minuteOfHour)
                }
            ),
            medications = items.sortedBy(MedicationGroupItemEntity::sortOrder).map { item ->
                MedicationGroupMedication(
                    uuid = UUID.fromString(item.uuid),
                    details = item.toMedicationDetails(),
                )
            },
            notificationsEnabled = group.notificationsEnabled,
            createdAt = Instant.ofEpochMilli(group.createdAtEpochMillis),
            updatedAt = Instant.ofEpochMilli(group.updatedAtEpochMillis)
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
            dose = dose
        )
    }
}

data class MedicationGroupMedicationInput(
    val uuid: UUID? = null,
    val details: MedicationDetails,
)

data class MedicationGroupScheduleInput(
    val type: MedicationGroupScheduleType,
    val interval: Int,
    val since: LocalDate,
    val weeklyDaysOfWeek: Set<DayOfWeek>,
    val times: List<LocalTime>,
)
