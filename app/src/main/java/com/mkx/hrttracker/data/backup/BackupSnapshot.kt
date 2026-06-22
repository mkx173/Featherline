package com.mkx.hrttracker.data.backup

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BackupSnapshot(
    val snapshotVersion: Int = CURRENT_BACKUP_SNAPSHOT_VERSION,
    val exportedAtEpochMillis: Long,
    val app: BackupAppSnapshot,
    val settings: BackupSettingsSnapshot,
    val userProfile: BackupUserProfileSnapshot,
    // Medicines are written before groups/logs so the restore path can build
    // its FK-validation set before mapping any item or log referencing one.
    val medicines: List<BackupMedicineSnapshot>,
    val medicationGroups: List<BackupMedicationGroupSnapshot>,
    val medicationLogs: List<BackupMedicationLogSnapshot>,
    val customBloodAnalytes: List<BackupCustomBloodAnalyteSnapshot>,
    val bloodTestPanels: List<BackupBloodTestPanelSnapshot>,
    val trackedDates: List<BackupTrackedDateSnapshot> = emptyList(),
    val notes: List<BackupNoteSnapshot> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class BackupAppSnapshot(
    val packageName: String,
)

@JsonClass(generateAdapter = true)
data class BackupSettingsSnapshot(
    val darkModeOption: String,
    val adaptiveColorEnabled: Boolean,
    val pureBlackEnabled: Boolean = false,
    val cjkTextOffsetEnabled: Boolean = false,
    val hazeBlurEnabled: Boolean = true,
    val remindersEnabled: Boolean,
    val showArchivedGroupRecords: Boolean = true,
    val hideReferenceRanges: Boolean = false,
    val appLockGracePeriodOption: String,
    val hideScreenContentEnabled: Boolean,
    val onboardingCompleted: Boolean,
    val appLanguageOption: String,
    val homeE2DisplayUnit: String = "pg_ml",
    val homeE2ChartWindow: String = "SEVEN_DAYS",
    val calibrationDefaultUnits: Map<String, String>,
    val lastSeenTimeZoneId: String? = null,
    val hideMedicationDetails: Boolean = false,
    val widgetContentScale: Float = 1.0f,
    val widgetBackgroundAlpha: Float = 1.0f,
    val widgetDarkModeOption: String = "FOLLOW_SYSTEM",
    // Encoded WidgetAppearanceCodec string for the DEFAULT appearance entry. When
    // present it wins over the three legacy widget* fields above, which remain only
    // so pre-customization backups keep restoring (and old app versions can still
    // read new backups' scale/alpha/darkMode).
    val widgetAppearance: String? = null,
    val groupNameCounter: Int = 0,
    val firstDayOfWeekOption: String = "FOLLOW_SYSTEM",
    val stockNudgeEnabled: Boolean = true,
    val stockNudgeUserEnabled: Boolean = false,
)


@JsonClass(generateAdapter = true)
data class BackupUserProfileSnapshot(
    val weightKg: Double?,
    val weightOriginalValue: Double?,
    val weightOriginalUnit: String,
    val updatedAtEpochMillis: Long? = null,
)

@JsonClass(generateAdapter = true)
data class BackupMedicineStockSnapshot(
    val trackingEnabled: Boolean,
    val unitsRemaining: Double?,
    val unitsLastTotal: Double?,
    val openContainerAmount: Double?,
    val warnAtDaysRemaining: Int,
    val stockGeneration: Long,
)

@JsonClass(generateAdapter = true)
data class BackupMedicineSnapshot(
    val uuid: String,
    val selectionKind: String,
    val medicationKey: String?,
    val customMedicationName: String?,
    val customMedicationNameNormalized: String?,
    val category: String,
    val preparationType: String,
    // Dual-purpose: holds per-tablet mg for PILL and per-capsule mg for
    // CAPSULE. Reusing the field avoids a backup-schema rename; the encode
    // path picks the right source via MedicinePreparation.toStorageFields.
    val strengthMgPerTablet: Double?,
    val strengthMgPerVial: Double?,
    val concentrationMgPerMl: Double?,
    val vialVolumeMl: Double?,
    val concentrationPercent: Double?,
    val sachetWeightGrams: Double?,
    val containerWeightGrams: Double?,
    val patchTotalMg: Double?,
    val patchReleaseRateMcgPerDay: Double?,
    val displayName: String?,
    val identityKey: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val archivedAtEpochMillis: Long?,
    // Optional for backwards compatibility — backups exported before the
    // picker existed simply default to "MG" on restore.
    val displayDoseUnit: String? = null,
    val importedFromExternalTracker: Boolean = false,
    val stock: BackupMedicineStockSnapshot? = null,
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
    // Nullable per PATCH_OFF cross-cutting rule: only patch-off slots may omit
    // their medicine reference. Every other application type carries a valid
    // medicineUuid present in the same backup's `medicines` collection.
    val medicineUuid: String?,
    val applicationType: String,
    val doseInstructionKind: String,
    val tabletFractionNumerator: Int?,
    val tabletFractionDenominator: Int?,
    val doseVolumeMl: Double?,
    val doseWeightGrams: Double?,
    val gelApplicationArea: String,
)

@JsonClass(generateAdapter = true)
data class BackupMedicationLogSnapshot(
    val uuid: String,
    // Logs preserve the historical category so we still know how to classify
    // them even if the referenced medicine is later archived or recategorised.
    val category: String,
    // Nullable per PATCH_OFF cross-cutting rule: patch-off logs alone may omit
    // a medicine reference. Restore validation rejects any other null.
    val medicineUuid: String?,
    val applicationType: String,
    val doseInstructionKind: String,
    val tabletFractionNumerator: Int?,
    val tabletFractionDenominator: Int?,
    val doseVolumeMl: Double?,
    val doseWeightGrams: Double?,
    val gelApplicationArea: String,
    // Snapshotted PK input — null for PATCH_OFF and for custom medicines whose
    // estradiol equivalence is unknown.
    val equivalentE2Mg: Double?,
    val doseAmountDelta: Double? = null,
    val sourceGroupUuid: String?,
    val scheduleTimeUuid: String? = null,
    val appliedAtEpochMillis: Long,
    val appliedAtTimeZoneId: String,
    val scheduledForIso: String?,
    val count: Int,
    val importSourceApp: String? = null,
    val importExternalId: String? = null,
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
    val importSourceApp: String? = null,
    val importPanelKey: Long? = null,
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
    val importSourceApp: String? = null,
    val importExternalId: String? = null,
)

@JsonClass(generateAdapter = true)
data class BackupTrackedDateSnapshot(
    val uuid: String,
    val name: String,
    val iconKey: String,
    val dateIso: String,
    val paletteKey: String?,
    val heroBackgroundKey: String? = null,
    val pinnedOrder: Int?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@JsonClass(generateAdapter = true)
data class BackupNoteSnapshot(
    val uuid: String,
    val dateIso: String,
    val text: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

// Bump this when a schema change isn't safely readable by an older app —
// e.g., a new REQUIRED field, a removed field that something downstream still
// dereferences, or a semantic change to an existing field. Nullable optional
// fields usually don't require a bump; missing values fall through their
// defaults on restore. Examples include BackupMedicineSnapshot.displayDoseUnit,
// BackupMedicationLogSnapshot.doseAmountDelta, and
// BackupSettingsSnapshot.widgetAppearance. Defaulted top-level lists such as
// trackedDates and notes are also additive: absent keys default to emptyList()
// on restore, and older apps ignore the unknown keys.
//
// The 2→3 bump added the CAPSULE preparationType enum value. Older apps
// coerce unknown preparationType strings to PILL on restore, which would
// silently misclassify capsules — non-additive, hence the bump.
//
// The 3→4 bump pairs with the WidgetAppearanceCodec v3→v4 bump (bidirectional balance):
// a v4 appearance string inside a backup decodes to null on a v3-era app, which hard-fails
// the entire restore, so the version gate must reject the newer backup cleanly instead
// (see WidgetAppearanceCodec's coupling note).
//
// The 4→5 bump added IMPORTED_INJECTION and IMPORTED_GEL preparationType enum
// values. Older apps coerce unknown preparationType strings to PILL on restore,
// which would silently misclassify imported medicines.
//
// The 5→6 bump added BackupTrackedDateSnapshot.heroBackgroundKey so journal
// hero backgrounds are included in the backup compatibility gate alongside
// local Room persistence.
const val CURRENT_BACKUP_SNAPSHOT_VERSION = 6

// Stable logical app identity for backups. Do not derive this from
// Context.packageName: build variants may add an install suffix, but their
// backups must remain portable across release/debug app versions.
const val BACKUP_APP_PACKAGE_NAME = "com.mkx.hrttracker"
