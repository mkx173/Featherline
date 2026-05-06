package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.MedicationGroupItemEntity
import com.mkx.hrttracker.data.local.MedicationGroupScheduleTimeEntity
import com.mkx.hrttracker.data.local.MedicationGroupWithItemsEntity
import com.mkx.hrttracker.data.local.MedicationLogEntryEntity
import com.mkx.hrttracker.data.local.UserProfileEntity
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
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationSelection
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.personalization.WeightUnit
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

internal fun MedicationGroupWithItemsEntity.toMedicationGroupModel(): MedicationGroup {
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
            MedicationGroupMedication(
                uuid = UUID.fromString(item.uuid),
                details = item.toMedicationDetailsModel(),
                count = item.count.coerceAtLeast(1),
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

internal fun MedicationGroupItemEntity.toMedicationDetailsModel(): MedicationDetails {
    return MedicationDetails(
        category = MedicationCategory.fromStorageValue(category),
        applicationType = MedicationApplicationType.fromStorageValue(applicationType),
        selection = toMedicationSelection(),
        dose = toMedicationDose(),
        gelApplicationArea = MedicationGelApplicationArea.fromStorageValue(gelApplicationArea),
        customDoseUnit = MedicationDoseUnit.fromStorageValue(customDoseUnit),
    )
}

internal fun MedicationLogEntryEntity.toMedicationLogEntryModel(): MedicationLogEntry {
    return MedicationLogEntry(
        uuid = UUID.fromString(uuid),
        details = toMedicationDetailsModel(),
        dosageMgAsEstradiol = dosageMgAsEstradiol,
        sourceGroupUuid = sourceGroupUuid?.let(UUID::fromString),
        scheduleTimeUuid = scheduleTimeUuid?.let(UUID::fromString),
        appliedAt = Instant.ofEpochMilli(appliedAtEpochMillis),
        appliedAtTimeZoneId = appliedAtTimeZoneId,
        scheduledFor = scheduledForIso?.let(LocalDateTime::parse),
        count = count.coerceAtLeast(1),
    )
}

internal fun UserProfileEntity.toUserProfileModel(): UserProfile {
    return UserProfile(
        weightKg = weightKg,
        weightOriginalValue = weightOriginalValue,
        weightOriginalUnit = WeightUnit.fromStorageValue(weightOriginalUnit),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )
}

private fun MedicationLogEntryEntity.toMedicationDetailsModel(): MedicationDetails {
    return MedicationDetails(
        category = MedicationCategory.fromStorageValue(category),
        applicationType = MedicationApplicationType.fromStorageValue(applicationType),
        selection = toMedicationSelection(),
        dose = toMedicationDose(),
        gelApplicationArea = MedicationGelApplicationArea.fromStorageValue(gelApplicationArea),
        customDoseUnit = MedicationDoseUnit.fromStorageValue(customDoseUnit),
    )
}

private fun MedicationGroupItemEntity.toMedicationSelection(): MedicationSelection {
    return when (MedicationSelectionKind.fromStorageValue(selectionKind)) {
        MedicationSelectionKind.CATALOG -> MedicationSelection.Catalog(
            medicationKey = checkNotNull(MedicationKey.fromStorageValue(medicationKey))
        )

        MedicationSelectionKind.CUSTOM -> MedicationSelection.Custom(
            medicationName = customMedicationName.orEmpty()
        )
    }
}

private fun MedicationLogEntryEntity.toMedicationSelection(): MedicationSelection {
    return when (MedicationSelectionKind.fromStorageValue(selectionKind)) {
        MedicationSelectionKind.CATALOG -> MedicationSelection.Catalog(
            medicationKey = checkNotNull(MedicationKey.fromStorageValue(medicationKey))
        )

        MedicationSelectionKind.CUSTOM -> MedicationSelection.Custom(
            medicationName = customMedicationName.orEmpty()
        )
    }
}

private fun MedicationGroupItemEntity.toMedicationDose(): MedicationDose {
    return medicationDoseFromStorage(
        doseKind = doseKind,
        doseValueMg = doseValueMg,
        doseValuePercent = doseValuePercent,
        doseWeightGrams = doseWeightGrams,
        doseReleaseRateMcgPerDay = doseReleaseRateMcgPerDay,
    )
}

private fun MedicationLogEntryEntity.toMedicationDose(): MedicationDose {
    return medicationDoseFromStorage(
        doseKind = doseKind,
        doseValueMg = doseValueMg,
        doseValuePercent = doseValuePercent,
        doseWeightGrams = doseWeightGrams,
        doseReleaseRateMcgPerDay = doseReleaseRateMcgPerDay,
    )
}

private fun medicationDoseFromStorage(
    doseKind: String?,
    doseValueMg: Double?,
    doseValuePercent: Double?,
    doseWeightGrams: Double?,
    doseReleaseRateMcgPerDay: Double?,
): MedicationDose {
    return when (MedicationDoseKind.fromStorageValue(doseKind)) {
        MedicationDoseKind.MG_AS_MEDICINE -> MedicationDose.MgAsMedicine(
            valueMg = checkNotNull(doseValueMg)
        )

        MedicationDoseKind.GEL_EQUIVALENT_ESTRADIOL_MG -> MedicationDose.GelEquivalentEstradiolMg(
            valueMg = checkNotNull(doseValueMg)
        )

        MedicationDoseKind.GEL_PERCENT_AND_WEIGHT -> MedicationDose.GelPercentAndWeight(
            percent = checkNotNull(doseValuePercent),
            weightGrams = checkNotNull(doseWeightGrams),
        )

        MedicationDoseKind.PATCH_TOTAL_MG -> MedicationDose.PatchTotalMg(
            valueMg = checkNotNull(doseValueMg)
        )

        MedicationDoseKind.PATCH_RELEASE_RATE_MCG_DAY -> MedicationDose.PatchReleaseRateMcgPerDay(
            valueMcgPerDay = checkNotNull(doseReleaseRateMcgPerDay)
        )

        MedicationDoseKind.NONE -> MedicationDose.None
    }
}
