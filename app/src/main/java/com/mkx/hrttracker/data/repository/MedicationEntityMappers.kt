package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.MedicationGroupItemEntity
import com.mkx.hrttracker.data.local.MedicationGroupScheduleTimeEntity
import com.mkx.hrttracker.data.local.MedicationGroupWithItemsEntity
import com.mkx.hrttracker.data.local.MedicationLogEntryEntity
import com.mkx.hrttracker.data.local.MedicineEntity
import com.mkx.hrttracker.data.local.UserProfileEntity
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.DoseInstructionKind
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleTime
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.personalization.WeightUnit
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

internal fun MedicationGroupWithItemsEntity.toMedicationGroupModel(
    medicinesByUuid: Map<String, Medicine>,
): MedicationGroup {
    val scheduleTimeSlots = scheduleTimes
        .sortedBy(MedicationGroupScheduleTimeEntity::sortOrder)
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
            item.toMedicationGroupMedicationModel(medicinesByUuid)
        },
        notificationsEnabled = group.notificationsEnabled,
        createdAt = Instant.ofEpochMilli(group.createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(group.updatedAtEpochMillis),
        archivedAt = group.archivedAtEpochMillis?.let(Instant::ofEpochMilli),
        archivedAtLocal = group.archivedAtLocalIso?.let(LocalDateTime::parse),
        includePastScheduledSlots = group.includePastScheduledSlots,
        replacedByGroupUuid = group.replacedByGroupUuid?.let(UUID::fromString),
        recreatedFromGroupUuid = group.recreatedFromGroupUuid?.let(UUID::fromString),
        autoAddFutureEntries = group.autoAddFutureEntries,
    )
}

internal fun MedicationGroupItemEntity.toMedicationGroupMedicationModel(
    medicinesByUuid: Map<String, Medicine>,
): MedicationGroupMedication {
    // PATCH_OFF slots carry medicineUuid = null; resolve only when present.
    val medicine = medicineUuid?.let { uuid ->
        checkNotNull(medicinesByUuid[uuid]) {
            "Missing medicine $uuid for medication group item $uuid."
        }
    }
    val doseInstruction = toDoseInstruction()
        .normalizeLegacyStoredDose(medicine?.preparation?.type)
    return MedicationGroupMedication(
        uuid = UUID.fromString(uuid),
        medicine = medicine,
        applicationType = MedicationApplicationType.fromStorageValue(applicationType),
        doseInstruction = doseInstruction,
        count = count.coerceAtLeast(1),
    )
}

internal fun MedicationLogEntryEntity.toMedicationLogEntryModel(
    medicinesByUuid: Map<String, Medicine>,
): MedicationLogEntry {
    // PATCH_OFF logs carry medicineUuid = null; resolve only when present.
    val medicine = medicineUuid?.let { uuid ->
        checkNotNull(medicinesByUuid[uuid]) {
            "Missing medicine $uuid for medication log ${this.uuid}."
        }
    }
    val doseInstruction = toDoseInstruction()
        .normalizeLegacyStoredDose(medicine?.preparation?.type)
    return MedicationLogEntry(
        uuid = UUID.fromString(uuid),
        medicine = medicine,
        category = MedicationCategory.fromStorageValue(category),
        applicationType = MedicationApplicationType.fromStorageValue(applicationType),
        doseInstruction = doseInstruction,
        equivalentE2Mg = equivalentE2Mg,
        sourceGroupUuid = sourceGroupUuid?.let(UUID::fromString),
        scheduleTimeUuid = scheduleTimeUuid?.let(UUID::fromString),
        appliedAt = Instant.ofEpochMilli(appliedAtEpochMillis),
        appliedAtTimeZoneId = appliedAtTimeZoneId,
        scheduledFor = scheduledForIso?.let(LocalDateTime::parse),
        count = count.coerceAtLeast(1),
        doseAmountDelta = doseAmountDelta,
    )
}

private fun DoseInstruction.normalizeLegacyStoredDose(
    preparationType: MedicinePreparationType?,
): DoseInstruction {
    return if (
        preparationType == MedicinePreparationType.PILL &&
        this == DoseInstruction.WholeUnit
    ) {
        DoseInstruction.TabletFraction(numerator = 1, denominator = 1)
    } else {
        this
    }
}

/**
 * Loads every [Medicine] referenced by the given group items, keyed by uuid
 * string. PATCH_OFF slots carry a null medicineUuid and contribute nothing.
 */
internal suspend fun HrtTrackerDatabase.resolveMedicinesForGroups(
    groups: List<MedicationGroupWithItemsEntity>,
): Map<String, Medicine> {
    val medicineUuids = groups
        .flatMap { group -> group.items }
        .mapNotNull(MedicationGroupItemEntity::medicineUuid)
        .distinct()
    return loadMedicinesByUuid(medicineUuids)
}

/**
 * Loads every [Medicine] referenced by the given log entries, keyed by uuid
 * string. PATCH_OFF logs carry a null medicineUuid and contribute nothing.
 */
internal suspend fun HrtTrackerDatabase.resolveMedicinesForEntries(
    entities: List<MedicationLogEntryEntity>,
): Map<String, Medicine> {
    val medicineUuids = entities
        .mapNotNull(MedicationLogEntryEntity::medicineUuid)
        .distinct()
    return loadMedicinesByUuid(medicineUuids)
}

private suspend fun HrtTrackerDatabase.loadMedicinesByUuid(
    medicineUuids: List<String>,
): Map<String, Medicine> {
    if (medicineUuids.isEmpty()) {
        return emptyMap()
    }
    return medicineDao().getByUuids(medicineUuids)
        .associate { entity: MedicineEntity -> entity.uuid to entity.toMedicineModel() }
}

internal fun UserProfileEntity.toUserProfileModel(): UserProfile {
    return UserProfile(
        weightKg = weightKg,
        weightOriginalValue = weightOriginalValue,
        weightOriginalUnit = WeightUnit.fromStorageValue(weightOriginalUnit),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )
}

internal fun MedicationGroupItemEntity.toDoseInstruction(): DoseInstruction {
    return doseInstructionFromStorage(
        kind = doseInstructionKind,
        tabletFractionNumerator = tabletFractionNumerator,
        tabletFractionDenominator = tabletFractionDenominator,
        doseVolumeMl = doseVolumeMl,
        doseWeightGrams = doseWeightGrams,
    )
}

internal fun MedicationLogEntryEntity.toDoseInstruction(): DoseInstruction {
    return doseInstructionFromStorage(
        kind = doseInstructionKind,
        tabletFractionNumerator = tabletFractionNumerator,
        tabletFractionDenominator = tabletFractionDenominator,
        doseVolumeMl = doseVolumeMl,
        doseWeightGrams = doseWeightGrams,
    )
}

internal fun doseInstructionFromStorage(
    kind: String?,
    tabletFractionNumerator: Int?,
    tabletFractionDenominator: Int?,
    doseVolumeMl: Double?,
    doseWeightGrams: Double?,
): DoseInstruction {
    return when (DoseInstructionKind.fromStorageValue(kind)) {
        DoseInstructionKind.TABLET_FRACTION -> DoseInstruction.TabletFraction(
            numerator = checkNotNull(tabletFractionNumerator),
            denominator = checkNotNull(tabletFractionDenominator),
        )

        DoseInstructionKind.WHOLE_UNIT -> DoseInstruction.WholeUnit
        DoseInstructionKind.VOLUME_ML -> DoseInstruction.VolumeMl(checkNotNull(doseVolumeMl))
        DoseInstructionKind.WEIGHT_GRAMS -> DoseInstruction.WeightGrams(checkNotNull(doseWeightGrams))
        DoseInstructionKind.NOOP -> DoseInstruction.Noop
    }
}

internal fun DoseInstruction.toStorageFields(): DoseInstructionStorageFields {
    return DoseInstructionStorageFields(
        doseInstructionKind = kind.name,
        tabletFractionNumerator = (this as? DoseInstruction.TabletFraction)?.numerator,
        tabletFractionDenominator = (this as? DoseInstruction.TabletFraction)?.denominator,
        doseVolumeMl = (this as? DoseInstruction.VolumeMl)?.valueMl,
        doseWeightGrams = (this as? DoseInstruction.WeightGrams)?.valueGrams,
    )
}

internal data class DoseInstructionStorageFields(
    val doseInstructionKind: String,
    val tabletFractionNumerator: Int?,
    val tabletFractionDenominator: Int?,
    val doseVolumeMl: Double?,
    val doseWeightGrams: Double?,
)
