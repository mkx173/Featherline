package com.mkx.hrttracker.data.backup

import android.content.Context
import android.net.Uri
import com.mkx.hrttracker.data.local.BloodTestPanelEntity
import com.mkx.hrttracker.data.local.BloodTestResultEntity
import com.mkx.hrttracker.data.local.CustomBloodAnalyteEntity
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.MedicationGroupEntity
import com.mkx.hrttracker.data.local.MedicationGroupItemEntity
import com.mkx.hrttracker.data.local.MedicationGroupScheduleTimeEntity
import com.mkx.hrttracker.data.local.MedicationGroupWeeklyDayEntity
import com.mkx.hrttracker.data.local.MedicationLogEntryEntity
import com.mkx.hrttracker.data.local.UserProfileEntity
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.bloodtest.AllowedAnalyteUnit
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestCatalog
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDoseKind
import com.mkx.hrttracker.model.medication.MedicationDoseUnit
import com.mkx.hrttracker.model.medication.MedicationGelApplicationArea
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
import com.mkx.hrttracker.model.personalization.WeightUnit
import com.mkx.hrttracker.model.settings.AppLanguageOption
import com.mkx.hrttracker.model.settings.AppLockGracePeriodOption
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.reminder.MedicationReminderSnoozeScheduler
import com.mkx.hrttracker.reminder.ReminderNotificationManager
import dagger.hilt.android.qualifiers.ApplicationContext
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

class IncompatibleBackupFileException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

@Singleton
class BackupRestoreService @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val databaseHolder: DatabaseHolder,
    private val settingsRepository: SettingsRepository,
    private val homeSnapshotRepository: HomeSnapshotRepository,
    private val medicationReminderScheduler: MedicationReminderScheduler,
    private val medicationReminderSnoozeScheduler: MedicationReminderSnoozeScheduler,
    private val reminderNotificationManager: ReminderNotificationManager,
    private val backupCrypto: BackupCrypto,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    /**
     * Reads the backup file into memory once so callers can validate and
     * later decrypt it without re-opening the URI. ContentResolver temporary
     * read grants (SAF OpenDocument) can lapse between the picker callback
     * and password dialog confirmation, which caused intermittent
     * "decrypt failed / wrong password" errors on first restore attempts.
     */
    suspend fun loadEncryptedBackupBytes(
        fileUri: Uri,
    ): ByteArray = withContext(Dispatchers.IO) {
        readEncryptedBackupBytes(fileUri)
    }

    suspend fun validateBackupBytes(
        encryptedBytes: ByteArray,
    ) = withContext(Dispatchers.IO) {
        try {
            backupCrypto.validateEncryptedBackupContainer(encryptedBytes)
        } catch (error: IllegalArgumentException) {
            throw IncompatibleBackupFileException(
                message = error.message ?: "Selected file is not a compatible backup.",
                cause = error,
            )
        }
    }

    suspend fun validateBackupFile(
        fileUri: Uri,
    ) = withContext(Dispatchers.IO) {
        val encryptedBytes = readEncryptedBackupBytes(fileUri)
        try {
            validateBackupBytes(encryptedBytes)
        } finally {
            encryptedBytes.fill(0)
        }
    }

    suspend fun restoreBackup(
        fileUri: Uri,
        password: String,
    ) = withContext(Dispatchers.IO) {
        val encryptedBytes = readEncryptedBackupBytes(fileUri)
        try {
            restoreBackupBytes(encryptedBytes, password)
        } finally {
            encryptedBytes.fill(0)
        }
    }

    suspend fun restoreBackupBytes(
        encryptedBytes: ByteArray,
        password: String,
    ) = withContext(Dispatchers.IO) {
        val passwordChars = password.toCharArray()
        val json = try {
            backupCrypto.decryptSnapshotJson(
                encryptedBytes = encryptedBytes,
                password = passwordChars,
            )
        } finally {
            passwordChars.fill('\u0000')
        }
        val snapshot = BackupSnapshotJsonCodec.decode(json)
            ?: throw IOException("Unable to decode the selected backup file.")
        val validatedSnapshot = snapshot.toValidatedSnapshot(expectedPackageName = context.packageName)

        // Any visible dose-reminder notification references slot UUIDs from the
        // pre-restore database. Tap-actions afterward dispatch with stale state, so
        // dismiss them up-front before the database mutation begins.
        reminderNotificationManager.cancelAllDoseReminderNotifications()

        homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.runTransaction { database ->
                database.medicationLogDao().deleteAllEntries()
                database.medicationGroupDao().deleteAllGroups()
                database.bloodTestDao().deleteAllResults()
                database.bloodTestDao().deleteAllPanels()
                database.bloodTestDao().deleteAllCustomAnalytes()
                database.userProfileDao().deleteProfile()

                if (validatedSnapshot.customBloodAnalytes.isNotEmpty()) {
                    database.bloodTestDao().insertCustomAnalytes(validatedSnapshot.customBloodAnalytes)
                }
                if (validatedSnapshot.bloodTestPanels.isNotEmpty()) {
                    database.bloodTestDao().insertPanels(validatedSnapshot.bloodTestPanels)
                }
                if (validatedSnapshot.bloodTestResults.isNotEmpty()) {
                    database.bloodTestDao().insertResults(validatedSnapshot.bloodTestResults)
                }
                if (validatedSnapshot.medicationGroups.isNotEmpty()) {
                    database.medicationGroupDao().insertGroups(validatedSnapshot.medicationGroups)
                }
                if (validatedSnapshot.medicationGroupItems.isNotEmpty()) {
                    database.medicationGroupDao().insertItems(validatedSnapshot.medicationGroupItems)
                }
                if (validatedSnapshot.medicationGroupScheduleTimes.isNotEmpty()) {
                    database.medicationGroupDao()
                        .insertScheduleTimes(validatedSnapshot.medicationGroupScheduleTimes)
                }
                if (validatedSnapshot.medicationGroupWeeklyDays.isNotEmpty()) {
                    database.medicationGroupDao()
                        .insertWeeklyDays(validatedSnapshot.medicationGroupWeeklyDays)
                }
                if (validatedSnapshot.medicationLogs.isNotEmpty()) {
                    database.medicationLogDao().insertEntries(validatedSnapshot.medicationLogs)
                }
                validatedSnapshot.userProfile?.let { profile ->
                    database.userProfileDao().upsertProfile(profile)
                }
            }
        }

        settingsRepository.restoreSettings(
            darkModeOption = validatedSnapshot.settings.darkModeOption,
            adaptiveColorEnabled = validatedSnapshot.settings.adaptiveColorEnabled,
            remindersEnabled = validatedSnapshot.settings.remindersEnabled,
            showArchivedGroupRecords = validatedSnapshot.settings.showArchivedGroupRecords,
            hideReferenceRanges = validatedSnapshot.settings.hideReferenceRanges,
            appLockGracePeriodOption = validatedSnapshot.settings.appLockGracePeriodOption,
            hideScreenContentEnabled = validatedSnapshot.settings.hideScreenContentEnabled,
            onboardingCompleted = validatedSnapshot.settings.onboardingCompleted,
            appLanguageOption = validatedSnapshot.settings.appLanguageOption,
            calibrationDefaultUnits = validatedSnapshot.settings.calibrationDefaultUnits,
            homeE2DisplayUnit = validatedSnapshot.settings.homeE2DisplayUnit,
            homeE2ChartWindowOption = validatedSnapshot.settings.homeE2ChartWindowOption,
            lastSeenTimeZoneId = validatedSnapshot.settings.lastSeenTimeZoneId,
            hideMedicationDetails = validatedSnapshot.settings.hideMedicationDetails,
        )

        // Reminder rescheduling is a best-effort side effect — the data is
        // already committed. Surfacing a failure here as a generic
        // exception caused "restore failed" toasts even when the user's
        // data had been restored successfully. The alarm scheduler will
        // reconcile on the next app launch.
        try {
            medicationReminderScheduler.rescheduleAll()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            diagnosticsLogger.warning(
                TAG,
                "restore_reminder_reschedule_failed",
                error,
            )
        }
        try {
            medicationReminderSnoozeScheduler.clearAllSnoozes()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            diagnosticsLogger.warning(
                TAG,
                "restore_snooze_clear_failed",
                error,
            )
        }
    }

    private companion object {
        private const val TAG = "BackupRestoreService"
    }

    private fun readEncryptedBackupBytes(
        fileUri: Uri,
    ): ByteArray {
        // Read once with the temporary grant from the SAF picker — no
        // persistable permission needed. (Persisting here without ever
        // releasing would leak document grants on every restore attempt
        // and could eventually hit the system's per-app grant cap.)
        return context.contentResolver.openInputStream(fileUri)
            ?.use { inputStream -> inputStream.readBytes() }
            ?: throw IOException("Unable to open the selected backup file.")
    }
}

internal fun BackupSnapshot.toValidatedSnapshot(
    expectedPackageName: String,
): ValidatedBackupSnapshot {
    require(snapshotVersion == CURRENT_BACKUP_SNAPSHOT_VERSION) {
        "Unsupported backup snapshot version: $snapshotVersion."
    }
    require(app.packageName == expectedPackageName) {
        "Backup file was created for ${app.packageName}, not $expectedPackageName."
    }

    val validatedSettings = settings.toValidatedSettings()
    val validatedUserProfile = userProfile.toValidatedEntity(exportedAtEpochMillis)
    val customAnalytesByUuid = linkedMapOf<String, CustomBloodAnalyteEntity>()
    val normalizedCustomPairs = mutableSetOf<Pair<String, String>>()
    customBloodAnalytes.forEach { analyte ->
        val entity = analyte.toValidatedEntity()
        require(customAnalytesByUuid.put(entity.uuid, entity) == null) {
            "Duplicate custom analyte UUID ${entity.uuid} in backup."
        }
        require(normalizedCustomPairs.add(entity.normalizedName to entity.normalizedUnitLabel)) {
            "Duplicate custom analyte name/unit pair in backup."
        }
    }

    val groupEntities = mutableListOf<MedicationGroupEntity>()
    val groupItemEntities = mutableListOf<MedicationGroupItemEntity>()
    val groupScheduleTimeEntities = mutableListOf<MedicationGroupScheduleTimeEntity>()
    val groupWeeklyDayEntities = mutableListOf<MedicationGroupWeeklyDayEntity>()
    val seenGroupUuids = mutableSetOf<String>()
    val seenGroupItemUuids = mutableSetOf<String>()
    val seenScheduleTimeUuids = mutableSetOf<String>()
    val zoneId = ZoneId.systemDefault()

    medicationGroups.forEach { group ->
        val groupUuid = group.uuid.parseUuid("medication group UUID").toString()
        require(seenGroupUuids.add(groupUuid)) {
            "Duplicate medication group UUID $groupUuid in backup."
        }
        val colorKey = requireEnumName<MedicationGroupColorKey>(
            group.colorKey,
            "medication group color",
        )
        val scheduleType = requireEnumName<MedicationGroupScheduleType>(
            group.schedule.type,
            "medication group schedule type",
        )
        require(group.name.trim().isNotEmpty()) {
            "Medication group name must not be blank."
        }
        require(group.medications.isNotEmpty()) {
            "Medication group ${group.uuid} must contain at least one medication."
        }
        require(group.schedule.interval > 0) {
            "Medication group ${group.uuid} must have a positive schedule interval."
        }
        LocalDate.ofEpochDay(group.schedule.sinceEpochDay)
        require(group.schedule.times.isNotEmpty()) {
            "Medication group ${group.uuid} must contain at least one schedule time."
        }

        val weeklyDays = group.schedule.weeklyDaysOfWeek.distinct().sorted()
        when (scheduleType) {
            MedicationGroupScheduleType.DAILY -> require(weeklyDays.isEmpty()) {
                "Daily medication groups must not include weekly days."
            }

            MedicationGroupScheduleType.WEEKLY -> require(weeklyDays.isNotEmpty()) {
                "Weekly medication groups must include at least one weekday."
            }
        }
        weeklyDays.forEach { dayOfWeek ->
            DayOfWeek.of(dayOfWeek)
        }
        val archivedAtLocalIso = group.archivedAtLocalIso?.let { archivedAtLocal ->
            LocalDateTime.parse(archivedAtLocal).toString()
        } ?: group.archivedAtEpochMillis?.let { archivedAtEpochMillis ->
            Instant.ofEpochMilli(archivedAtEpochMillis)
                .atZone(zoneId)
                .toLocalDateTime()
                .toString()
        }
        val replacedByGroupUuid = group.replacedByGroupUuid
            ?.parseUuid("medication group replaced-by UUID")
            ?.toString()
        val recreatedFromGroupUuid = group.recreatedFromGroupUuid
            ?.parseUuid("medication group recreated-from UUID")
            ?.toString()

        groupEntities += MedicationGroupEntity(
            uuid = groupUuid,
            name = group.name.trim(),
            colorKey = colorKey.name,
            notificationsEnabled = group.notificationsEnabled,
            scheduleType = scheduleType.name,
            scheduleInterval = group.schedule.interval,
            scheduleSinceEpochDay = group.schedule.sinceEpochDay,
            createdAtEpochMillis = group.createdAtEpochMillis,
            updatedAtEpochMillis = group.updatedAtEpochMillis,
            archivedAtEpochMillis = group.archivedAtEpochMillis,
            archivedAtLocalIso = archivedAtLocalIso,
            includePastScheduledSlots = group.includePastScheduledSlots,
            replacedByGroupUuid = replacedByGroupUuid,
            recreatedFromGroupUuid = recreatedFromGroupUuid,
        )
        groupScheduleTimeEntities += group.schedule.times.mapIndexed { index, time ->
            val scheduleTimeUuid = time.uuid
                ?.parseUuid("medication group schedule time UUID")
                ?.toString()
                ?: UUID.randomUUID().toString()
            require(seenScheduleTimeUuids.add(scheduleTimeUuid)) {
                "Duplicate medication group schedule time UUID $scheduleTimeUuid in backup."
            }
            val effectiveFromLocalIso = time.effectiveFromLocalIso?.let { effectiveFrom ->
                LocalDateTime.parse(effectiveFrom).toString()
            } ?: if (group.includePastScheduledSlots) {
                LocalDate.ofEpochDay(group.schedule.sinceEpochDay).atStartOfDay().toString()
            } else {
                Instant.ofEpochMilli(group.createdAtEpochMillis)
                    .atZone(zoneId)
                    .toLocalDateTime()
                    .toString()
            }
            MedicationGroupScheduleTimeEntity(
                uuid = scheduleTimeUuid,
                groupUuid = groupUuid,
                sortOrder = index,
                hourOfDay = time.hourOfDay,
                minuteOfHour = time.minuteOfHour,
                effectiveFromLocalIso = effectiveFromLocalIso,
            )
        }
        groupWeeklyDayEntities += weeklyDays.map { dayOfWeek ->
            MedicationGroupWeeklyDayEntity(
                groupUuid = groupUuid,
                dayOfWeek = DayOfWeek.of(dayOfWeek).value,
            )
        }
        group.medications.forEachIndexed { index, medication ->
            val itemUuid = medication.uuid.parseUuid("medication group item UUID").toString()
            require(seenGroupItemUuids.add(itemUuid)) {
                "Duplicate medication group item UUID $itemUuid in backup."
            }
            require(medication.count > 0) {
                "Medication group item ${medication.uuid} must have a positive count."
            }
            val validatedMedication = medication.toValidatedMedicationData(
                fieldPrefix = "medication group item ${medication.uuid}",
            )
            groupItemEntities += MedicationGroupItemEntity(
                uuid = itemUuid,
                groupUuid = groupUuid,
                sortOrder = index,
                count = medication.count,
                category = validatedMedication.category.name,
                applicationType = validatedMedication.applicationType.name,
                selectionKind = validatedMedication.selectionKind.name,
                medicationKey = validatedMedication.medicationKey?.name,
                customMedicationName = validatedMedication.customMedicationName,
                doseKind = validatedMedication.doseKind.name,
                doseValueMg = validatedMedication.doseValueMg,
                customDoseUnit = validatedMedication.customDoseUnit.storageValue,
                doseValuePercent = validatedMedication.doseValuePercent,
                doseWeightGrams = validatedMedication.doseWeightGrams,
                doseReleaseRateMcgPerDay = validatedMedication.doseReleaseRateMcgPerDay,
                gelApplicationArea = validatedMedication.gelApplicationArea.name,
            )
        }
    }

    val groupEntitiesWithLineage = groupEntities.withDerivedRecreatedFromGroupUuids()
    val validGroupUuids = groupEntitiesWithLineage.mapTo(mutableSetOf(), MedicationGroupEntity::uuid)
    val scheduleTimeGroupByUuid = groupScheduleTimeEntities.associate { scheduleTime ->
        scheduleTime.uuid to scheduleTime.groupUuid
    }
    val logEntities = mutableListOf<MedicationLogEntryEntity>()
    val seenLogUuids = mutableSetOf<String>()
    medicationLogs.forEach { log ->
        val logUuid = log.uuid.parseUuid("medication log UUID").toString()
        require(seenLogUuids.add(logUuid)) {
            "Duplicate medication log UUID $logUuid in backup."
        }
        require(log.count > 0) {
            "Medication log ${log.uuid} must have a positive count."
        }
        requireZoneId(log.appliedAtTimeZoneId, "medication log time zone")
        val sourceGroupUuid = log.sourceGroupUuid?.parseUuid("medication log source group UUID")?.toString()
        if (sourceGroupUuid != null) {
            require(sourceGroupUuid in validGroupUuids) {
                "Grouped medication logs must reference a restored medication group."
            }
        }
        val validatedMedication = log.toValidatedMedicationData(
            fieldPrefix = "medication log ${log.uuid}",
        )
        val scheduledForIso = log.scheduledForIso?.let { value ->
            LocalDateTime.parse(value)
            value
        }
        val scheduledFor = scheduledForIso?.let(LocalDateTime::parse)
        val scheduleTimeUuid = log.scheduleTimeUuid
            ?.parseUuid("medication log schedule time UUID")
            ?.toString()
        val resolvedScheduleTimeUuid = scheduleTimeUuid ?: if (
            sourceGroupUuid != null &&
            scheduledFor != null
        ) {
            groupScheduleTimeEntities
                .filter { scheduleTime ->
                    scheduleTime.groupUuid == sourceGroupUuid &&
                        scheduleTime.hourOfDay == scheduledFor.hour &&
                        scheduleTime.minuteOfHour == scheduledFor.minute
                }
                .singleOrNull()
                ?.uuid
        } else {
            null
        }
        if (scheduleTimeUuid != null) {
            require(
                sourceGroupUuid != null &&
                    scheduledFor != null &&
                    scheduleTimeGroupByUuid[scheduleTimeUuid] == sourceGroupUuid
            ) {
                "Medication log ${log.uuid} references a schedule time outside its source group."
            }
        }
        log.dosageMgAsEstradiol?.requirePositiveFinite("medication log estradiol-equivalent dose")
        logEntities += MedicationLogEntryEntity(
            uuid = logUuid,
            category = validatedMedication.category.name,
            applicationType = validatedMedication.applicationType.name,
            selectionKind = validatedMedication.selectionKind.name,
            medicationKey = validatedMedication.medicationKey?.name,
            customMedicationName = validatedMedication.customMedicationName,
            doseKind = validatedMedication.doseKind.name,
            doseValueMg = validatedMedication.doseValueMg,
            customDoseUnit = validatedMedication.customDoseUnit.storageValue,
            doseValuePercent = validatedMedication.doseValuePercent,
            doseWeightGrams = validatedMedication.doseWeightGrams,
            doseReleaseRateMcgPerDay = validatedMedication.doseReleaseRateMcgPerDay,
            dosageMgAsEstradiol = log.dosageMgAsEstradiol,
            sourceGroupUuid = sourceGroupUuid,
            scheduleTimeUuid = resolvedScheduleTimeUuid,
            appliedAtEpochMillis = log.appliedAtEpochMillis,
            appliedAtTimeZoneId = log.appliedAtTimeZoneId,
            scheduledForIso = scheduledForIso,
            count = log.count,
            gelApplicationArea = validatedMedication.gelApplicationArea.name,
        )
    }

    val panelEntities = mutableListOf<BloodTestPanelEntity>()
    val resultEntities = mutableListOf<BloodTestResultEntity>()
    val seenPanelUuids = mutableSetOf<String>()
    val seenResultUuids = mutableSetOf<String>()
    bloodTestPanels.forEach { panel ->
        val panelUuid = panel.uuid.parseUuid("blood test panel UUID").toString()
        require(seenPanelUuids.add(panelUuid)) {
            "Duplicate blood test panel UUID $panelUuid in backup."
        }
        requireZoneId(panel.collectedAtTimeZoneId, "blood test panel time zone")
        require(panel.results.isNotEmpty()) {
            "Blood test panel ${panel.uuid} must contain at least one result."
        }
        panelEntities += BloodTestPanelEntity(
            uuid = panelUuid,
            collectedAtInstantEpochMillis = panel.collectedAtInstantEpochMillis,
            collectedAtTimeZoneId = panel.collectedAtTimeZoneId,
            notes = panel.notes?.trim()?.takeUnless(String::isEmpty),
            timeSinceLastEstradiolDoseMillis = panel.timeSinceLastEstradiolDoseMillis,
            timeSinceLastTestosteroneDoseMillis = panel.timeSinceLastTestosteroneDoseMillis,
            createdAtEpochMillis = panel.createdAtEpochMillis,
            updatedAtEpochMillis = panel.updatedAtEpochMillis,
        )

        val seenBuiltinAnalytes = mutableSetOf<String>()
        val seenCustomAnalytes = mutableSetOf<String>()
        panel.results.forEach { result ->
            val resultUuid = result.uuid.parseUuid("blood test result UUID").toString()
            require(seenResultUuids.add(resultUuid)) {
                "Duplicate blood test result UUID $resultUuid in backup."
            }
            require(result.value.isFinite()) {
                "Blood test result ${result.uuid} value must be finite."
            }
            require(result.canonicalValue.isFinite()) {
                "Blood test result ${result.uuid} canonical value must be finite."
            }
            val hasBuiltinAnalyte = result.builtinAnalyteKey != null
            val hasCustomAnalyte = result.customAnalyteUuid != null
            require(hasBuiltinAnalyte xor hasCustomAnalyte) {
                "Blood test result ${result.uuid} must reference exactly one analyte."
            }

            if (hasBuiltinAnalyte) {
                val analyteKey = checkNotNull(BloodAnalyteKey.fromStorageValue(result.builtinAnalyteKey)) {
                    "Unsupported built-in blood analyte key ${result.builtinAnalyteKey}."
                }
                val unitKey = checkNotNull(BloodUnitKey.fromStorageValue(result.unitSnapshot)) {
                    "Unsupported built-in blood unit ${result.unitSnapshot}."
                }
                val choice = AllowedAnalyteUnit.of(analyteKey, unitKey)
                require(seenBuiltinAnalytes.add(choice.analyte.storageValue)) {
                    "Blood test panel ${panel.uuid} contains duplicate built-in analyte ${choice.analyte.storageValue}."
                }
                val expectedCanonicalValue = BloodTestCatalog.toCanonical(
                    analyteKey = choice.analyte,
                    value = result.value,
                    unit = choice.unit,
                )
                require(closeEnough(expectedCanonicalValue, result.canonicalValue)) {
                    "Blood test result ${result.uuid} has an inconsistent canonical value."
                }
            } else {
                val customAnalyteUuid = result.customAnalyteUuid
                    .parseUuid("blood test custom analyte UUID")
                    .toString()
                val customAnalyte = checkNotNull(customAnalytesByUuid[customAnalyteUuid]) {
                    "Blood test result ${result.uuid} references a missing custom analyte."
                }
                require(seenCustomAnalytes.add(customAnalyteUuid)) {
                    "Blood test panel ${panel.uuid} contains duplicate custom analyte $customAnalyteUuid."
                }
                require(result.unitSnapshot == customAnalyte.unitLabel) {
                    "Blood test result ${result.uuid} has a unit that does not match its custom analyte."
                }
                require(closeEnough(result.value, result.canonicalValue)) {
                    "Custom blood test result ${result.uuid} must store its canonical value unchanged."
                }
            }

            resultEntities += BloodTestResultEntity(
                uuid = resultUuid,
                panelUuid = panelUuid,
                createdAtEpochMillis = result.createdAtEpochMillis,
                displayOrder = result.displayOrder,
                builtinAnalyteKey = result.builtinAnalyteKey,
                customAnalyteUuid = result.customAnalyteUuid,
                value = result.value,
                unitSnapshot = result.unitSnapshot,
                canonicalValue = result.canonicalValue,
            )
        }
    }

    return ValidatedBackupSnapshot(
        settings = validatedSettings,
        userProfile = validatedUserProfile,
        medicationGroups = groupEntitiesWithLineage,
        medicationGroupItems = groupItemEntities,
        medicationGroupScheduleTimes = groupScheduleTimeEntities,
        medicationGroupWeeklyDays = groupWeeklyDayEntities,
        medicationLogs = logEntities,
        customBloodAnalytes = customAnalytesByUuid.values.toList(),
        bloodTestPanels = panelEntities,
        bloodTestResults = resultEntities,
    )
}

private fun List<MedicationGroupEntity>.withDerivedRecreatedFromGroupUuids(): List<MedicationGroupEntity> {
    val sourceUuidBySuccessorUuid = mutableMapOf<String, String>()
    forEach { group ->
        val successorUuid = group.replacedByGroupUuid ?: return@forEach
        if (successorUuid !in sourceUuidBySuccessorUuid) {
            sourceUuidBySuccessorUuid[successorUuid] = group.uuid
        }
    }
    return map { group ->
        val derivedSourceUuid = sourceUuidBySuccessorUuid[group.uuid]
        if (group.recreatedFromGroupUuid == null && derivedSourceUuid != null) {
            group.copy(recreatedFromGroupUuid = derivedSourceUuid)
        } else {
            group
        }
    }
}

private fun BackupSettingsSnapshot.toValidatedSettings(): ValidatedBackupSettings {
    val darkModeOption = requireEnumName<DarkModeOption>(
        darkModeOption,
        "dark mode option",
    )
    val appLockGracePeriodOption = requireEnumName<AppLockGracePeriodOption>(
        appLockGracePeriodOption,
        "app lock grace period option",
    )
    val appLanguageOption = requireEnumName<AppLanguageOption>(
        appLanguageOption,
        "app language option",
    )
    val calibrationDefaultUnits = calibrationDefaultUnits.map { (analyteStorageValue, unitStorageValue) ->
        val analyteKey = checkNotNull(BloodAnalyteKey.fromStorageValue(analyteStorageValue)) {
            "Unsupported calibration analyte key $analyteStorageValue."
        }
        val unitKey = checkNotNull(BloodUnitKey.fromStorageValue(unitStorageValue)) {
            "Unsupported calibration unit key $unitStorageValue."
        }
        AllowedAnalyteUnit.of(analyteKey, unitKey)
    }.toSet()
    val homeE2DisplayUnitKey = checkNotNull(BloodUnitKey.fromStorageValue(homeE2DisplayUnit)) {
        "Unsupported home E2 display unit key $homeE2DisplayUnit."
    }
    val homeE2Choice = AllowedAnalyteUnit.of(BloodAnalyteKey.E2, homeE2DisplayUnitKey)
    val homeE2ChartWindowOption = requireEnumName<HomeE2ChartWindowOption>(
        homeE2ChartWindow,
        "home E2 chart window",
    )

    return ValidatedBackupSettings(
        darkModeOption = darkModeOption,
        adaptiveColorEnabled = adaptiveColorEnabled,
        remindersEnabled = remindersEnabled,
        showArchivedGroupRecords = showArchivedGroupRecords,
        hideReferenceRanges = hideReferenceRanges,
        appLockGracePeriodOption = appLockGracePeriodOption,
        hideScreenContentEnabled = hideScreenContentEnabled,
        onboardingCompleted = onboardingCompleted,
        appLanguageOption = appLanguageOption,
        calibrationDefaultUnits = calibrationDefaultUnits,
        homeE2DisplayUnit = homeE2Choice,
        homeE2ChartWindowOption = homeE2ChartWindowOption,
        lastSeenTimeZoneId = lastSeenTimeZoneId,
        hideMedicationDetails = hideMedicationDetails,
    )
}

private fun BackupUserProfileSnapshot.toValidatedEntity(
    restoredAtEpochMillis: Long,
): UserProfileEntity? {
    require((weightKg == null) == (weightOriginalValue == null)) {
        "Backup user profile must either include both stored weight values or neither."
    }
    if (weightKg == null && weightOriginalValue == null) {
        return null
    }

    val weightUnit = requireEnumName<WeightUnit>(
        weightOriginalUnit,
        "weight unit",
    )
    val resolvedWeightKg = weightKg.requirePositiveFinite("stored weight")
    val resolvedOriginalValue = weightOriginalValue.requirePositiveFinite("original weight")
    require(closeEnough(weightUnit.toKg(resolvedOriginalValue), resolvedWeightKg)) {
        "Backup user profile weight is inconsistent with its stored unit."
    }

    return UserProfileEntity(
        weightKg = resolvedWeightKg,
        weightOriginalValue = resolvedOriginalValue,
        weightOriginalUnit = weightUnit.name,
        updatedAtEpochMillis = updatedAtEpochMillis ?: restoredAtEpochMillis,
    )
}

private fun BackupCustomBloodAnalyteSnapshot.toValidatedEntity(): CustomBloodAnalyteEntity {
    val analyteUuid = uuid.parseUuid("custom blood analyte UUID").toString()
    val abbreviation = abbreviation.trim()
    require(abbreviation.isNotEmpty()) {
        "Custom analyte abbreviation must not be blank."
    }
    val normalizedName = normalizeCustomField(name, "custom analyte name")
    val normalizedUnitLabel = normalizeCustomField(unitLabel, "custom analyte unit")
    return CustomBloodAnalyteEntity(
        uuid = analyteUuid,
        abbreviation = abbreviation,
        name = name.trim(),
        normalizedName = normalizedName,
        unitLabel = unitLabel.trim(),
        normalizedUnitLabel = normalizedUnitLabel,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        archivedAtEpochMillis = archivedAtEpochMillis,
    )
}

private fun BackupMedicationGroupItemSnapshot.toValidatedMedicationData(
    fieldPrefix: String,
): ValidatedMedicationData {
    return ValidatedMedicationData.fromSnapshot(
        categoryValue = category,
        applicationTypeValue = applicationType,
        selectionKindValue = selectionKind,
        medicationKeyValue = medicationKey,
        customMedicationNameValue = customMedicationName,
        doseKindValue = doseKind,
        doseValueMg = doseValueMg,
        customDoseUnitValue = customDoseUnit,
        doseValuePercent = doseValuePercent,
        doseWeightGrams = doseWeightGrams,
        doseReleaseRateMcgPerDay = doseReleaseRateMcgPerDay,
        gelApplicationAreaValue = gelApplicationArea,
        fieldPrefix = fieldPrefix,
    )
}

private fun BackupMedicationLogSnapshot.toValidatedMedicationData(
    fieldPrefix: String,
): ValidatedMedicationData {
    return ValidatedMedicationData.fromSnapshot(
        categoryValue = category,
        applicationTypeValue = applicationType,
        selectionKindValue = selectionKind,
        medicationKeyValue = medicationKey,
        customMedicationNameValue = customMedicationName,
        doseKindValue = doseKind,
        doseValueMg = doseValueMg,
        customDoseUnitValue = customDoseUnit,
        doseValuePercent = doseValuePercent,
        doseWeightGrams = doseWeightGrams,
        doseReleaseRateMcgPerDay = doseReleaseRateMcgPerDay,
        gelApplicationAreaValue = gelApplicationArea,
        fieldPrefix = fieldPrefix,
    )
}

internal data class ValidatedBackupSnapshot(
    val settings: ValidatedBackupSettings,
    val userProfile: UserProfileEntity?,
    val medicationGroups: List<MedicationGroupEntity>,
    val medicationGroupItems: List<MedicationGroupItemEntity>,
    val medicationGroupScheduleTimes: List<MedicationGroupScheduleTimeEntity>,
    val medicationGroupWeeklyDays: List<MedicationGroupWeeklyDayEntity>,
    val medicationLogs: List<MedicationLogEntryEntity>,
    val customBloodAnalytes: List<CustomBloodAnalyteEntity>,
    val bloodTestPanels: List<BloodTestPanelEntity>,
    val bloodTestResults: List<BloodTestResultEntity>,
)

internal data class ValidatedBackupSettings(
    val darkModeOption: DarkModeOption,
    val adaptiveColorEnabled: Boolean,
    val remindersEnabled: Boolean,
    val showArchivedGroupRecords: Boolean,
    val hideReferenceRanges: Boolean,
    val appLockGracePeriodOption: AppLockGracePeriodOption,
    val hideScreenContentEnabled: Boolean,
    val onboardingCompleted: Boolean,
    val appLanguageOption: AppLanguageOption,
    val calibrationDefaultUnits: Set<AllowedAnalyteUnit>,
    val homeE2DisplayUnit: AllowedAnalyteUnit,
    val homeE2ChartWindowOption: HomeE2ChartWindowOption,
    val lastSeenTimeZoneId: String?,
    val hideMedicationDetails: Boolean,
)

private data class ValidatedMedicationData(
    val category: MedicationCategory,
    val applicationType: MedicationApplicationType,
    val selectionKind: MedicationSelectionKind,
    val medicationKey: MedicationKey?,
    val customMedicationName: String?,
    val doseKind: MedicationDoseKind,
    val doseValueMg: Double?,
    val customDoseUnit: MedicationDoseUnit,
    val doseValuePercent: Double?,
    val doseWeightGrams: Double?,
    val doseReleaseRateMcgPerDay: Double?,
    val gelApplicationArea: MedicationGelApplicationArea,
) {
    companion object {
        fun fromSnapshot(
            categoryValue: String,
            applicationTypeValue: String,
            selectionKindValue: String,
            medicationKeyValue: String?,
            customMedicationNameValue: String?,
            doseKindValue: String,
            doseValueMg: Double?,
            customDoseUnitValue: String,
            doseValuePercent: Double?,
            doseWeightGrams: Double?,
            doseReleaseRateMcgPerDay: Double?,
            gelApplicationAreaValue: String,
            fieldPrefix: String,
        ): ValidatedMedicationData {
            val category = requireEnumName<MedicationCategory>(
                categoryValue,
                "$fieldPrefix category",
            )
            val applicationType = requireEnumName<MedicationApplicationType>(
                applicationTypeValue,
                "$fieldPrefix application type",
            )
            val selectionKind = requireEnumName<MedicationSelectionKind>(
                selectionKindValue,
                "$fieldPrefix selection kind",
            )
            val doseKind = requireEnumName<MedicationDoseKind>(
                doseKindValue,
                "$fieldPrefix dose kind",
            )
            val customDoseUnit = requireEnumName<MedicationDoseUnit>(
                customDoseUnitValue,
                "$fieldPrefix custom dose unit",
            )
            val gelApplicationArea = requireEnumName<MedicationGelApplicationArea>(
                gelApplicationAreaValue,
                "$fieldPrefix gel application area",
            )

            val resolvedMedicationKey = when (selectionKind) {
                MedicationSelectionKind.CATALOG -> {
                    require(customMedicationNameValue == null) {
                        "$fieldPrefix must not provide a custom medication name for catalog selection."
                    }
                    checkNotNull(MedicationKey.fromStorageValue(medicationKeyValue)) {
                        "Unsupported $fieldPrefix medication key $medicationKeyValue."
                    }.also { medicationKey ->
                        require(medicationKey.category == category) {
                            "$fieldPrefix medication key $medicationKey does not match category $category."
                        }
                    }
                }

                MedicationSelectionKind.CUSTOM -> {
                    require(medicationKeyValue == null) {
                        "$fieldPrefix must not provide a medication key for custom selection."
                    }
                    null
                }
            }
            val resolvedCustomMedicationName = when (selectionKind) {
                MedicationSelectionKind.CATALOG -> null
                MedicationSelectionKind.CUSTOM -> customMedicationNameValue
                    ?.trim()
                    ?.takeUnless(String::isEmpty)
                    ?: throw IllegalArgumentException("$fieldPrefix custom medication name must not be blank.")
            }

            val compatibleDoseKinds = when (applicationType) {
                MedicationApplicationType.ORAL,
                MedicationApplicationType.SUBLINGUAL,
                MedicationApplicationType.INJECTION,
                -> setOf(MedicationDoseKind.MG_AS_MEDICINE)

                MedicationApplicationType.GEL -> setOf(
                    MedicationDoseKind.GEL_EQUIVALENT_ESTRADIOL_MG,
                    MedicationDoseKind.GEL_PERCENT_AND_WEIGHT,
                )

                MedicationApplicationType.PATCH_ON -> setOf(
                    MedicationDoseKind.PATCH_TOTAL_MG,
                    MedicationDoseKind.PATCH_RELEASE_RATE_MCG_DAY,
                )

                MedicationApplicationType.PATCH_OFF -> setOf(MedicationDoseKind.NONE)
            }
            require(doseKind in compatibleDoseKinds) {
                "$fieldPrefix dose kind $doseKind is not supported for $applicationType."
            }

            if (applicationType != MedicationApplicationType.GEL) {
                require(gelApplicationArea == MedicationGelApplicationArea.DEFAULT) {
                    "$fieldPrefix gel application area is only valid for gel medications."
                }
            }

            val resolvedDoseValueMg = when (doseKind) {
                MedicationDoseKind.MG_AS_MEDICINE,
                MedicationDoseKind.GEL_EQUIVALENT_ESTRADIOL_MG,
                MedicationDoseKind.PATCH_TOTAL_MG,
                -> doseValueMg.requirePositiveFinite("$fieldPrefix mg dose")

                MedicationDoseKind.GEL_PERCENT_AND_WEIGHT,
                MedicationDoseKind.PATCH_RELEASE_RATE_MCG_DAY,
                MedicationDoseKind.NONE,
                -> {
                    require(doseValueMg == null) {
                        "$fieldPrefix must not provide doseValueMg for $doseKind."
                    }
                    null
                }
            }
            val resolvedDoseValuePercent = when (doseKind) {
                MedicationDoseKind.GEL_PERCENT_AND_WEIGHT ->
                    doseValuePercent.requirePositiveFinite("$fieldPrefix gel percent")

                else -> {
                    require(doseValuePercent == null) {
                        "$fieldPrefix must not provide doseValuePercent for $doseKind."
                    }
                    null
                }
            }
            val resolvedDoseWeightGrams = when (doseKind) {
                MedicationDoseKind.GEL_PERCENT_AND_WEIGHT ->
                    doseWeightGrams.requirePositiveFinite("$fieldPrefix gel weight")

                else -> {
                    require(doseWeightGrams == null) {
                        "$fieldPrefix must not provide doseWeightGrams for $doseKind."
                    }
                    null
                }
            }
            val resolvedDoseReleaseRateMcgPerDay = when (doseKind) {
                MedicationDoseKind.PATCH_RELEASE_RATE_MCG_DAY ->
                    doseReleaseRateMcgPerDay.requirePositiveFinite("$fieldPrefix patch release rate")

                else -> {
                    require(doseReleaseRateMcgPerDay == null) {
                        "$fieldPrefix must not provide doseReleaseRateMcgPerDay for $doseKind."
                    }
                    null
                }
            }

            val resolvedCustomDoseUnit = if (
                selectionKind == MedicationSelectionKind.CUSTOM &&
                doseKind == MedicationDoseKind.MG_AS_MEDICINE
            ) {
                customDoseUnit
            } else {
                require(customDoseUnit == MedicationDoseUnit.MG) {
                    "$fieldPrefix custom dose unit is only valid for custom mg medications."
                }
                MedicationDoseUnit.MG
            }

            return ValidatedMedicationData(
                category = category,
                applicationType = applicationType,
                selectionKind = selectionKind,
                medicationKey = resolvedMedicationKey,
                customMedicationName = resolvedCustomMedicationName,
                doseKind = doseKind,
                doseValueMg = resolvedDoseValueMg,
                customDoseUnit = resolvedCustomDoseUnit,
                doseValuePercent = resolvedDoseValuePercent,
                doseWeightGrams = resolvedDoseWeightGrams,
                doseReleaseRateMcgPerDay = resolvedDoseReleaseRateMcgPerDay,
                gelApplicationArea = gelApplicationArea,
            )
        }
    }
}

private inline fun <reified T : Enum<T>> requireEnumName(
    value: String,
    fieldName: String,
): T {
    return enumValues<T>().firstOrNull { entry -> entry.name == value }
        ?: throw IllegalArgumentException("Unsupported $fieldName: $value.")
}

private fun String?.parseUuid(fieldName: String): UUID {
    return runCatching { UUID.fromString(requireNotNull(this)) }
        .getOrElse { throw IllegalArgumentException("Invalid $fieldName.", it) }
}

private fun requireZoneId(
    value: String,
    fieldName: String,
): String {
    return ZoneId.of(value).id.also {
        require(it == value) { "Invalid $fieldName: $value." }
    }
}

private fun Double?.requirePositiveFinite(fieldName: String): Double {
    require(this != null && isFinite() && this > 0.0) {
        "$fieldName must be a positive finite number."
    }
    return this
}

private fun normalizeCustomField(
    value: String,
    fieldName: String,
): String {
    val trimmed = value.trim()
    require(trimmed.isNotEmpty()) { "$fieldName must not be blank." }
    return trimmed.lowercase(Locale.ROOT)
}

private fun closeEnough(
    expected: Double,
    actual: Double,
    epsilon: Double = 1e-6,
): Boolean {
    return abs(expected - actual) <= epsilon
}
