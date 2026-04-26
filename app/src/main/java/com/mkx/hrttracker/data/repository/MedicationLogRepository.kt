package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.MedicationLogEntryEntity
import com.mkx.hrttracker.di.AppScope
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationDoseKind
import com.mkx.hrttracker.model.medication.MedicationDoseUnit
import com.mkx.hrttracker.model.medication.MedicationGelApplicationArea
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
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
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationLogRepository @Inject constructor(
    private val databaseHolder: DatabaseHolder,
    @AppScope appScope: CoroutineScope,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val entriesFlow: StateFlow<List<MedicationLogEntry>?> =
        databaseHolder.databaseFlow
            .flatMapLatest { database ->
                if (database == null) {
                    flowOf<List<MedicationLogEntry>?>(null)
                } else {
                    database.medicationLogDao().observeEntries()
                        .map<List<MedicationLogEntryEntity>, List<MedicationLogEntry>?> { entries ->
                            entries.map { it.toModel() }
                        }
                        .catch { emit(emptyList()) }
                }
            }
            .stateIn(
                scope = appScope,
                started = SharingStarted.Eagerly,
                initialValue = null
            )

    fun observeEntries(): Flow<List<MedicationLogEntry>?> = entriesFlow

    suspend fun getEntries(): List<MedicationLogEntry> {
        return databaseHolder.get().medicationLogDao().getEntries().map { it.toModel() }
    }

    suspend fun getEntries(uuids: Collection<UUID>): List<MedicationLogEntry> {
        if (uuids.isEmpty()) {
            return emptyList()
        }

        val orderedUuids = uuids.distinct().map(UUID::toString)
        val entriesByUuid = databaseHolder.get().medicationLogDao()
            .getEntriesByIds(orderedUuids)
            .associateBy(MedicationLogEntryEntity::uuid)

        return orderedUuids.mapNotNull { uuid ->
            entriesByUuid[uuid]?.toModel()
        }
    }

    suspend fun getScheduledGroupEntriesSince(since: LocalDateTime): List<MedicationLogEntry> {
        return databaseHolder.get().medicationLogDao()
            .getScheduledGroupEntriesSince(since.toString())
            .map { it.toModel() }
    }

    suspend fun getEntry(uuid: UUID): MedicationLogEntry? {
        return databaseHolder.get().medicationLogDao().getEntry(uuid.toString())?.toModel()
    }

    suspend fun deleteEntries(uuids: Collection<UUID>) {
        if (uuids.isEmpty()) {
            return
        }

        databaseHolder.get().medicationLogDao().deleteEntries(uuids.map(UUID::toString))
    }

    suspend fun deleteAllEntries() {
        databaseHolder.withTransaction { database ->
            database.medicationLogDao().deleteAllEntries()
        }
    }

    suspend fun deleteEntriesForGroup(groupUuid: UUID) {
        databaseHolder.withTransaction { database ->
            database.medicationLogDao().deleteEntriesForGroup(groupUuid.toString())
        }
    }

    suspend fun saveEntry(
        uuid: UUID?,
        medication: MedicationDetails,
        sourceGroupUuid: UUID?,
        appliedAt: Instant,
        scheduledFor: LocalDateTime? = null,
        count: Int = 1,
        appliedAtTimeZoneId: String = ZoneId.systemDefault().id
    ) {
        databaseHolder.get().medicationLogDao().insertEntry(
            buildEntryEntity(
                uuid = uuid ?: UUID.randomUUID(),
                medication = medication,
                sourceGroupUuid = sourceGroupUuid,
                appliedAt = appliedAt,
                scheduledFor = scheduledFor,
                count = count,
                appliedAtTimeZoneId = appliedAtTimeZoneId
            )
        )
    }

    suspend fun saveEntries(
        uuids: Collection<UUID>,
        medication: MedicationDetails,
        sourceGroupUuid: UUID?,
        appliedAt: Instant,
        scheduledFor: LocalDateTime? = null,
        count: Int = 1,
        appliedAtTimeZoneId: String = ZoneId.systemDefault().id
    ) {
        val targetUuids = uuids.distinct()
        if (targetUuids.isEmpty()) {
            saveEntry(
                uuid = null,
                medication = medication,
                sourceGroupUuid = sourceGroupUuid,
                appliedAt = appliedAt,
                scheduledFor = scheduledFor,
                count = count,
                appliedAtTimeZoneId = appliedAtTimeZoneId
            )
            return
        }

        databaseHolder.get().medicationLogDao().insertEntries(
            targetUuids.map { uuid ->
                buildEntryEntity(
                    uuid = uuid,
                    medication = medication,
                    sourceGroupUuid = sourceGroupUuid,
                    appliedAt = appliedAt,
                    scheduledFor = scheduledFor,
                    count = count,
                    appliedAtTimeZoneId = appliedAtTimeZoneId
                )
            }
        )
    }

    private fun MedicationLogEntryEntity.toModel(): MedicationLogEntry {
        return MedicationLogEntry(
            uuid = UUID.fromString(uuid),
            details = toMedicationDetails(),
            dosageMgAsEstradiol = dosageMgAsEstradiol,
            sourceGroupUuid = sourceGroupUuid?.let(UUID::fromString),
            appliedAt = Instant.ofEpochMilli(appliedAtEpochMillis),
            appliedAtTimeZoneId = appliedAtTimeZoneId,
            scheduledFor = scheduledForIso?.let(LocalDateTime::parse),
            count = count.coerceAtLeast(1)
        )
    }

    private fun MedicationLogEntryEntity.toMedicationDetails(): MedicationDetails {
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

    private fun buildEntryEntity(
        uuid: UUID,
        medication: MedicationDetails,
        sourceGroupUuid: UUID?,
        appliedAt: Instant,
        scheduledFor: LocalDateTime?,
        count: Int,
        appliedAtTimeZoneId: String
    ): MedicationLogEntryEntity {
        return MedicationLogEntryEntity(
            uuid = uuid.toString(),
            category = medication.category.name,
            applicationType = medication.applicationType.name,
            selectionKind = medication.selection.kind.name,
            medicationKey = when (val selection = medication.selection) {
                is MedicationSelection.Catalog -> selection.medicationKey.name
                is MedicationSelection.Custom -> null
            },
            customMedicationName = when (val selection = medication.selection) {
                is MedicationSelection.Catalog -> null
                is MedicationSelection.Custom -> selection.medicationName
            },
            doseKind = medication.dose.kind.name,
            doseValueMg = when (val dose = medication.dose) {
                is MedicationDose.MgAsMedicine -> dose.valueMg
                is MedicationDose.GelEquivalentEstradiolMg -> dose.valueMg
                is MedicationDose.PatchTotalMg -> dose.valueMg
                else -> null
            },
            customDoseUnit = when {
                medication.selection is MedicationSelection.Custom &&
                    medication.dose is MedicationDose.MgAsMedicine ->
                    medication.customDoseUnit.storageValue

                else -> MedicationDoseUnit.MG.storageValue
            },
            doseValuePercent = when (val dose = medication.dose) {
                is MedicationDose.GelPercentAndWeight -> dose.percent
                else -> null
            },
            doseWeightGrams = when (val dose = medication.dose) {
                is MedicationDose.GelPercentAndWeight -> dose.weightGrams
                else -> null
            },
            doseReleaseRateMcgPerDay = when (val dose = medication.dose) {
                is MedicationDose.PatchReleaseRateMcgPerDay -> dose.valueMcgPerDay
                else -> null
            },
            dosageMgAsEstradiol = EstradiolEquivalentCalculator.calculate(
                medication = medication
            ),
            sourceGroupUuid = sourceGroupUuid?.toString(),
            appliedAtEpochMillis = appliedAt.toEpochMilli(),
            appliedAtTimeZoneId = appliedAtTimeZoneId,
            scheduledForIso = scheduledFor?.toString(),
            count = count.coerceAtLeast(1),
            gelApplicationArea = medication.gelApplicationArea.name,
        )
    }
}
