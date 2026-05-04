package com.mkx.hrttracker.data.backup

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BackupSnapshot(
    val snapshotVersion: Int = CURRENT_BACKUP_SNAPSHOT_VERSION,
    val exportedAtEpochMillis: Long,
    val app: BackupAppSnapshot,
    val settings: BackupSettingsSnapshot,
    val userProfile: BackupUserProfileSnapshot,
    val medicationGroups: List<BackupMedicationGroupSnapshot>,
    val medicationLogs: List<BackupMedicationLogSnapshot>,
    val customBloodAnalytes: List<BackupCustomBloodAnalyteSnapshot>,
    val bloodTestPanels: List<BackupBloodTestPanelSnapshot>,
)

@JsonClass(generateAdapter = true)
data class BackupAppSnapshot(
    val packageName: String,
)

@JsonClass(generateAdapter = true)
data class BackupSettingsSnapshot(
    val darkModeOption: String,
    val adaptiveColorEnabled: Boolean,
    val remindersEnabled: Boolean,
    val showArchivedGroupRecords: Boolean = true,
    val appLockGracePeriodOption: String,
    val hideScreenContentEnabled: Boolean,
    val onboardingCompleted: Boolean,
    val appLanguageOption: String,
    val calibrationDefaultUnits: Map<String, String>,
)

@JsonClass(generateAdapter = true)
data class BackupUserProfileSnapshot(
    val weightKg: Double?,
    val weightOriginalValue: Double?,
    val weightOriginalUnit: String,
    val updatedAtEpochMillis: Long? = null,
)

@JsonClass(generateAdapter = true)
data class BackupMedicationGroupSnapshot(
    val uuid: String,
    val name: String,
    val colorKey: String,
    val notificationsEnabled: Boolean,
    val schedule: BackupMedicationGroupScheduleSnapshot,
    val medications: List<BackupMedicationGroupItemSnapshot>,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val archivedAtEpochMillis: Long?,
    val archivedAtLocalIso: String? = null,
    val includePastScheduledSlots: Boolean = true,
    val replacedByGroupUuid: String? = null,
    val recreatedFromGroupUuid: String? = null,
)

@JsonClass(generateAdapter = true)
data class BackupMedicationGroupScheduleSnapshot(
    val type: String,
    val interval: Int,
    val sinceEpochDay: Long,
    val weeklyDaysOfWeek: List<Int>,
    val times: List<BackupMedicationGroupScheduleTimeSnapshot>,
)

@JsonClass(generateAdapter = true)
data class BackupMedicationGroupScheduleTimeSnapshot(
    val hourOfDay: Int,
    val minuteOfHour: Int,
    val uuid: String? = null,
    val effectiveFromLocalIso: String? = null,
)

@JsonClass(generateAdapter = true)
data class BackupMedicationGroupItemSnapshot(
    val uuid: String,
    val count: Int,
    val category: String,
    val applicationType: String,
    val selectionKind: String,
    val medicationKey: String?,
    val customMedicationName: String?,
    val doseKind: String,
    val doseValueMg: Double?,
    val customDoseUnit: String,
    val doseValuePercent: Double?,
    val doseWeightGrams: Double?,
    val doseReleaseRateMcgPerDay: Double?,
    val gelApplicationArea: String,
)

@JsonClass(generateAdapter = true)
data class BackupMedicationLogSnapshot(
    val uuid: String,
    val category: String,
    val applicationType: String,
    val selectionKind: String,
    val medicationKey: String?,
    val customMedicationName: String?,
    val doseKind: String,
    val doseValueMg: Double?,
    val customDoseUnit: String,
    val doseValuePercent: Double?,
    val doseWeightGrams: Double?,
    val doseReleaseRateMcgPerDay: Double?,
    val gelApplicationArea: String,
    val dosageMgAsEstradiol: Double?,
    val sourceGroupUuid: String?,
    val scheduleTimeUuid: String? = null,
    val appliedAtEpochMillis: Long,
    val appliedAtTimeZoneId: String,
    val scheduledForIso: String?,
    val count: Int,
)

@JsonClass(generateAdapter = true)
data class BackupCustomBloodAnalyteSnapshot(
    val uuid: String,
    val abbreviation: String,
    val name: String,
    val unitLabel: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val archivedAtEpochMillis: Long?,
)

@JsonClass(generateAdapter = true)
data class BackupBloodTestPanelSnapshot(
    val uuid: String,
    val collectedAtInstantEpochMillis: Long,
    val collectedAtTimeZoneId: String,
    val notes: String?,
    val timeSinceLastEstradiolDoseMillis: Long?,
    val timeSinceLastTestosteroneDoseMillis: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val results: List<BackupBloodTestResultSnapshot>,
)

@JsonClass(generateAdapter = true)
data class BackupBloodTestResultSnapshot(
    val uuid: String,
    val createdAtEpochMillis: Long,
    val displayOrder: Int,
    val builtinAnalyteKey: String?,
    val customAnalyteUuid: String?,
    val value: Double,
    val unitSnapshot: String,
    val canonicalValue: Double,
)

const val CURRENT_BACKUP_SNAPSHOT_VERSION = 1
