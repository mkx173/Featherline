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
import com.mkx.hrttracker.data.local.MedicineEntity
import com.mkx.hrttracker.data.local.UserProfileEntity
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.decrementDenominatorOnCrack
import com.mkx.hrttracker.data.repository.normalizeOpenContainer
import com.mkx.hrttracker.model.bloodtest.AllowedAnalyteUnit
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestCatalog
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.DoseInstructionKind
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGelApplicationArea
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
import com.mkx.hrttracker.model.medication.MedicineDisplayDoseUnit
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.medication.isCompatibleWith
import com.mkx.hrttracker.model.medication.normalizeCustomMedicationName
import com.mkx.hrttracker.model.personalization.WeightUnit
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.settings.AppLanguageOption
import com.mkx.hrttracker.model.settings.AppLockGracePeriodOption
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.model.settings.FirstDayOfWeekOption
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.reminder.MedicationReminderSnoozeScheduler
import com.mkx.hrttracker.reminder.ReminderNotificationManager
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
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
        diagnosticsLogger.info(
            TAG,
            "validate_backup_start bytes=${encryptedBytes.size} " +
                "header=${encryptedBytes.headerPreviewHex()}",
        )
        try {
            backupCrypto.validateEncryptedBackupContainer(encryptedBytes)
            diagnosticsLogger.info(
                TAG,
                "validate_backup_ok bytes=${encryptedBytes.size}",
            )
        } catch (error: IllegalArgumentException) {
            diagnosticsLogger.warning(
                TAG,
                "validate_backup_incompatible bytes=${encryptedBytes.size} " +
                    "header=${encryptedBytes.headerPreviewHex()} reason=${error.message}",
                error,
            )
            throw IncompatibleBackupFileException(
                message = error.message ?: "Selected file is not a compatible backup.",
                cause = error,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            // Non-IllegalArgument failures (e.g. a malformed buffer that
            // underflows past the size guards) surface to the user as a
            // generic "restore failed" rather than "incompatible", so log
            // them distinctly to tell the two paths apart while debugging.
            diagnosticsLogger.warning(
                TAG,
                "validate_backup_unexpected_error bytes=${encryptedBytes.size} " +
                    "header=${encryptedBytes.headerPreviewHex()}",
                error,
            )
            throw error
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
        diagnosticsLogger.info(
            TAG,
            "restore_backup_start bytes=${encryptedBytes.size} " +
                "expectedBackupPackage=$BACKUP_APP_PACKAGE_NAME installedPackage=${context.packageName}",
        )
        val passwordChars = password.toCharArray()
        val json = try {
            backupCrypto.decryptSnapshotJson(
                encryptedBytes = encryptedBytes,
                password = passwordChars,
            )
        } finally {
            passwordChars.fill(' ')
        }
        // The snapshot decode + structural validation below throws
        // IllegalArgumentException (via require) for anything it considers
        // malformed; the UI maps every such failure to the generic
        // "incompatible backup" toast, hiding which specific invariant
        // tripped. Log the exact reason so a successfully-exported file that
        // is still rejected can be traced to its failing require().
        val validatedSnapshot = try {
            val snapshotVersion = BackupSnapshotJsonCodec.peekSnapshotVersion(json)
                ?: throw IOException("Unable to decode the selected backup file.")
            diagnosticsLogger.info(
                TAG,
                "restore_backup_decrypted snapshotVersion=$snapshotVersion",
            )
            require(snapshotVersion in MIN_SUPPORTED_BACKUP_SNAPSHOT_VERSION..CURRENT_BACKUP_SNAPSHOT_VERSION) {
                "Unsupported backup snapshot version: $snapshotVersion."
            }
            val snapshot = BackupSnapshotJsonCodec.decode(json)
                ?: throw IOException("Unable to decode the selected backup file.")
            snapshot.toValidatedSnapshot(expectedPackageName = BACKUP_APP_PACKAGE_NAME).also {
                diagnosticsLogger.info(TAG, "restore_backup_validated_ok")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: IllegalArgumentException) {
            diagnosticsLogger.warning(
                TAG,
                "restore_backup_rejected_as_incompatible reason=${error.message}",
                error,
            )
            throw error
        }

        // Any visible dose-reminder notification references slot UUIDs from the
        // pre-restore database. Tap-actions afterward dispatch with stale state, so
        // dismiss them up-front before the database mutation begins.
        reminderNotificationManager.cancelAllDoseReminderNotifications()

        homeSnapshotRepository.runHomeDataMutation {
            databaseHolder.runTransaction { database ->
                // Delete order: logs and group items (children) before medicines
                // (parents) so the medicines table can be wiped without
                // tripping the RESTRICT foreign-key constraint.
                database.medicationLogDao().deleteAllEntries()
                database.medicationGroupDao().deleteAllGroups()
                database.medicineDao().deleteAll()
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
                // Medicines must be written before any group item or log that
                // references them; RESTRICT foreign keys would otherwise
                // reject the insert. The validator already proved that every
                // referenced medicineUuid is present in this list.
                if (validatedSnapshot.medicines.isNotEmpty()) {
                    database.medicineDao().insertAll(validatedSnapshot.medicines)
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
            pureBlackEnabled = validatedSnapshot.settings.pureBlackEnabled,
            cjkTextOffsetEnabled = validatedSnapshot.settings.cjkTextOffsetEnabled,
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
            widgetContentScale = validatedSnapshot.settings.widgetContentScale,
            widgetBackgroundAlpha = validatedSnapshot.settings.widgetBackgroundAlpha,
            widgetDarkModeOption = validatedSnapshot.settings.widgetDarkModeOption,
            groupNameCounter = validatedSnapshot.settings.groupNameCounter,
            firstDayOfWeekOption = validatedSnapshot.settings.firstDayOfWeekOption,
            stockNudgeEnabled = validatedSnapshot.settings.stockNudgeEnabled,
            stockNudgeUserEnabled = validatedSnapshot.settings.stockNudgeUserEnabled,
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

        // The encrypted container is a gzip-compressed JSON snapshot whose
        // uncompressed size BackupCrypto already caps at 128 MiB. This upper
        // bound on the on-disk container leaves headroom while still rejecting
        // an absurd selection before it is pulled into memory.
        private const val MAX_ENCRYPTED_BACKUP_BYTES = 192L * 1024L * 1024L
    }

    private fun readEncryptedBackupBytes(
        fileUri: Uri,
    ): ByteArray {
        // Read once with the temporary grant from the SAF picker — no
        // persistable permission needed. (Persisting here without ever
        // releasing would leak document grants on every restore attempt
        // and could eventually hit the system's per-app grant cap.)
        //
        // Bound the read: a mis-selected or hostile file (e.g. a multi-gigabyte
        // video) would otherwise be pulled fully into memory by readBytes()
        // before any structural size limit applies, OOMing the restore.
        return context.contentResolver.openInputStream(fileUri)
            ?.use { inputStream -> readBoundedBytes(inputStream, MAX_ENCRYPTED_BACKUP_BYTES) }
            ?: throw IOException("Unable to open the selected backup file.")
    }
}

/**
 * Reads [inputStream] fully but rejects input larger than [maxBytes] without
 * first buffering the whole stream, so an oversized selection cannot exhaust
 * memory before the size limit applies.
 *
 * Throws [IncompatibleBackupFileException] (not a plain [IOException]) on
 * over-cap input so the picker — which runs this before the password prompt —
 * surfaces the accurate "not a compatible backup" message rather than the
 * generic "wrong password or corrupted" toast for a file no password was ever
 * entered against.
 */
internal fun readBoundedBytes(
    inputStream: InputStream,
    maxBytes: Long,
): ByteArray {
    val buffer = ByteArrayOutputStream()
    val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val bytesRead = inputStream.read(chunk)
        if (bytesRead == -1) break
        total += bytesRead
        if (total > maxBytes) {
            throw IncompatibleBackupFileException(
                "Selected backup file exceeds the maximum supported size."
            )
        }
        buffer.write(chunk, 0, bytesRead)
    }
    return buffer.toByteArray()
}

internal fun BackupSnapshot.toValidatedSnapshot(
    expectedPackageName: String,
): ValidatedBackupSnapshot {
    require(snapshotVersion in MIN_SUPPORTED_BACKUP_SNAPSHOT_VERSION..CURRENT_BACKUP_SNAPSHOT_VERSION) {
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

    // Validate medicines before any item/log so the FK-validation set is
    // known up front. Restored medicines preserve their original UUIDs so
    // foreign keys from items and logs land on the right rows.
    val medicineEntities = mutableListOf<MedicineEntity>()
    val seenMedicineUuids = mutableSetOf<String>()
    val seenMedicineIdentityKeys = mutableSetOf<String>()
    medicines.forEach { medicine ->
        val entity = medicine.toValidatedEntity()
        require(seenMedicineUuids.add(entity.uuid)) {
            "Duplicate medicine UUID ${entity.uuid} in backup."
        }
        require(seenMedicineIdentityKeys.add(entity.identityKey)) {
            "Duplicate medicine identity ${entity.identityKey} in backup."
        }
        medicineEntities += entity
    }
    val medicineEntitiesByUuid = medicineEntities.associateBy(MedicineEntity::uuid)
    val medicinePreparationTypesByUuid = medicineEntitiesByUuid.mapValues { (_, entity) ->
        requireEnumName<MedicinePreparationType>(
            entity.preparationType,
            "medicine ${entity.uuid} preparation type",
        )
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
            val validatedMedication = medication.toValidatedItemData(
                fieldPrefix = "medication group item ${medication.uuid}",
                medicinePreparationTypesByUuid = medicinePreparationTypesByUuid,
            )
            // Active groups must not reference archived medicines — the active
            // catalog would otherwise show an archived row that cannot be
            // re-selected without unarchiving. Skip the check for PATCH_OFF
            // (medicineUuid == null) and for groups that are themselves
            // archived (history-only).
            if (
                group.archivedAtEpochMillis == null &&
                validatedMedication.medicineUuid != null
            ) {
                val referenced = medicineEntitiesByUuid[validatedMedication.medicineUuid]
                require(referenced?.archivedAtEpochMillis == null) {
                    "Active medication group ${group.uuid} references archived medicine " +
                        "${validatedMedication.medicineUuid}."
                }
            }
            groupItemEntities += MedicationGroupItemEntity(
                uuid = itemUuid,
                groupUuid = groupUuid,
                sortOrder = index,
                count = medication.count,
                medicineUuid = validatedMedication.medicineUuid,
                applicationType = validatedMedication.applicationType.name,
                doseInstructionKind = validatedMedication.doseInstructionKind.name,
                tabletFractionNumerator = validatedMedication.tabletFractionNumerator,
                tabletFractionDenominator = validatedMedication.tabletFractionDenominator,
                doseVolumeMl = validatedMedication.doseVolumeMl,
                doseWeightGrams = validatedMedication.doseWeightGrams,
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
        val validatedMedication = log.toValidatedLogData(
            fieldPrefix = "medication log ${log.uuid}",
            medicinePreparationTypesByUuid = medicinePreparationTypesByUuid,
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
        log.equivalentE2Mg?.requirePositiveFinite("medication log estradiol-equivalent dose")
        logEntities += MedicationLogEntryEntity(
            uuid = logUuid,
            category = validatedMedication.category.name,
            medicineUuid = validatedMedication.medicineUuid,
            applicationType = validatedMedication.applicationType.name,
            doseInstructionKind = validatedMedication.doseInstructionKind.name,
            tabletFractionNumerator = validatedMedication.tabletFractionNumerator,
            tabletFractionDenominator = validatedMedication.tabletFractionDenominator,
            doseVolumeMl = validatedMedication.doseVolumeMl,
            doseWeightGrams = validatedMedication.doseWeightGrams,
            equivalentE2Mg = log.equivalentE2Mg,
            doseAmountDelta = log.doseAmountDelta,
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
        medicines = medicineEntities,
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

private const val MIN_SUPPORTED_BACKUP_SNAPSHOT_VERSION = 2

private fun BackupSettingsSnapshot.toValidatedSettings(): ValidatedBackupSettings {
    val darkModeOption = requireEnumName<DarkModeOption>(
        darkModeOption,
        "dark mode option",
    )
    val widgetDarkModeOption = requireEnumName<DarkModeOption>(
        widgetDarkModeOption,
        "widget dark mode option",
    )
    val appLockGracePeriodOption = requireEnumName<AppLockGracePeriodOption>(
        appLockGracePeriodOption,
        "app lock grace period option",
    )
    val appLanguageOption = requireEnumName<AppLanguageOption>(
        appLanguageOption,
        "app language option",
    )
    val firstDayOfWeekOption = requireEnumName<FirstDayOfWeekOption>(
        firstDayOfWeekOption,
        "first day of week option",
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
        pureBlackEnabled = pureBlackEnabled,
        cjkTextOffsetEnabled = cjkTextOffsetEnabled,
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
        widgetContentScale = widgetContentScale.requireFiniteIn(0.5f..1.5f, "widget content scale"),
        widgetBackgroundAlpha = widgetBackgroundAlpha.requireFiniteIn(0.5f..1f, "widget background opacity"),
        widgetDarkModeOption = widgetDarkModeOption,
        groupNameCounter = groupNameCounter,
        firstDayOfWeekOption = firstDayOfWeekOption,
        stockNudgeEnabled = stockNudgeEnabled,
        stockNudgeUserEnabled = stockNudgeUserEnabled,
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

private fun BackupMedicineSnapshot.toValidatedEntity(): MedicineEntity {
    val medicineUuid = uuid.parseUuid("medicine UUID").toString()
    val selectionKind = requireEnumName<MedicationSelectionKind>(
        selectionKind,
        "medicine selection kind",
    )
    val category = requireEnumName<MedicationCategory>(
        category,
        "medicine category",
    )
    val preparationType = requireEnumName<MedicinePreparationType>(
        preparationType,
        "medicine preparation type",
    )

    val resolvedSelection: MedicineSelection = when {
        // PATCH_OFF preparation always implies the singleton selection,
        // regardless of stored selectionKind. The singleton is keyed by its
        // identityKey alone; medicationKey / customName must be empty.
        preparationType == MedicinePreparationType.PATCH_OFF -> {
            require(medicationKey == null) {
                "PATCH_OFF medicine $uuid must not carry a catalog medicationKey."
            }
            require(customMedicationName == null && customMedicationNameNormalized == null) {
                "PATCH_OFF medicine $uuid must not carry a custom medication name."
            }
            require(category == MedicationCategory.ESTRADIOL) {
                "PATCH_OFF medicine $uuid must be in the ESTRADIOL category."
            }
            MedicineSelection.PatchOff
        }
        selectionKind == MedicationSelectionKind.CATALOG -> {
            require(customMedicationName == null && customMedicationNameNormalized == null) {
                "Catalog medicine $uuid must not carry a custom name."
            }
            val key = checkNotNull(MedicationKey.fromStorageValue(medicationKey)) {
                "Unsupported catalog medication key $medicationKey for medicine $uuid."
            }
            require(key.category == category) {
                "Catalog medicine $uuid medication key $key does not match category $category."
            }
            MedicineSelection.Catalog(medicationKey = key)
        }
        else -> {
            require(medicationKey == null) {
                "Custom medicine $uuid must not carry a catalog medication key."
            }
            val name = customMedicationName
                ?.trim()
                ?.takeUnless(String::isEmpty)
                ?: throw IllegalArgumentException(
                    "Custom medicine $uuid must include a non-blank custom medication name."
                )
            val expectedNormalized = normalizeCustomMedicationName(name)
            val storedNormalized = customMedicationNameNormalized
                ?.takeUnless(String::isBlank)
                ?: throw IllegalArgumentException(
                    "Custom medicine $uuid must include its normalized custom medication name."
                )
            require(storedNormalized == expectedNormalized) {
                "Custom medicine $uuid normalized name $storedNormalized does not match $expectedNormalized."
            }
            MedicineSelection.Custom(medicationName = name)
        }
    }

    val resolvedPreparation = preparationType.toValidatedPreparation(
        medicineUuid = medicineUuid,
        strengthMgPerTablet = strengthMgPerTablet,
        strengthMgPerVial = strengthMgPerVial,
        concentrationMgPerMl = concentrationMgPerMl,
        vialVolumeMl = vialVolumeMl,
        concentrationPercent = concentrationPercent,
        sachetWeightGrams = sachetWeightGrams,
        containerWeightGrams = containerWeightGrams,
        patchTotalMg = patchTotalMg,
        patchReleaseRateMcgPerDay = patchReleaseRateMcgPerDay,
    )

    val expectedIdentityKey = when (resolvedSelection) {
        is MedicineSelection.Catalog ->
            MedicineIdentityKey.catalog(resolvedSelection.medicationKey, resolvedPreparation)
        is MedicineSelection.Custom ->
            MedicineIdentityKey.custom(resolvedSelection.medicationName, resolvedPreparation)
        is MedicineSelection.PatchOff -> MedicineIdentityKey.patchOff()
    }
    require(identityKey == expectedIdentityKey) {
        "Medicine $uuid identityKey does not match its selection and preparation."
    }

    require(createdAtEpochMillis >= 0) {
        "Medicine $uuid createdAt must not be negative."
    }
    require(updatedAtEpochMillis >= createdAtEpochMillis) {
        "Medicine $uuid updatedAt $updatedAtEpochMillis predates createdAt $createdAtEpochMillis."
    }
    if (archivedAtEpochMillis != null) {
        require(archivedAtEpochMillis >= createdAtEpochMillis) {
            "Medicine $uuid archivedAt predates createdAt."
        }
    }

    // Heal legacy/degenerate stock rows on restore so imported containers land
    // canonical (no empty open with sealed stock remaining). Operates on the
    // backup DTO via the shared normalizeOpenContainer primitive, since the
    // model-typed MedicineStock.normalizedFor helper does not fit this type.
    val rawStock = stock
    val normalizedStock = if (rawStock?.trackingEnabled == true) {
        val capacity = when (preparationType) {
            MedicinePreparationType.INJECTION_MULTI_USE_VIAL -> vialVolumeMl
            MedicinePreparationType.GEL_CONTAINER -> containerWeightGrams
            else -> null
        }
        if (capacity != null) {
            val sealed = rawStock.unitsRemaining?.takeIf { it.isFinite() } ?: 0.0
            val (normalizedOpen, normalizedSealed) = normalizeOpenContainer(
                open = rawStock.openContainerAmount,
                sealed = sealed,
                capacity = capacity,
            )
            rawStock.copy(
                unitsRemaining = normalizedSealed,
                openContainerAmount = normalizedOpen,
                unitsLastTotal = decrementDenominatorOnCrack(
                    lastTotal = rawStock.unitsLastTotal,
                    cracked = normalizedSealed != sealed,
                ),
            )
        } else {
            rawStock
        }
    } else {
        rawStock
    }

    return MedicineEntity(
        uuid = medicineUuid,
        selectionKind = selectionKind.name,
        medicationKey = (resolvedSelection as? MedicineSelection.Catalog)?.medicationKey?.name,
        customMedicationName = (resolvedSelection as? MedicineSelection.Custom)?.medicationName,
        customMedicationNameNormalized = (resolvedSelection as? MedicineSelection.Custom)
            ?.normalizedMedicationName,
        category = category.name,
        preparationType = preparationType.name,
        strengthMgPerTablet = strengthMgPerTablet,
        strengthMgPerVial = strengthMgPerVial,
        concentrationMgPerMl = concentrationMgPerMl,
        vialVolumeMl = vialVolumeMl,
        concentrationPercent = concentrationPercent,
        sachetWeightGrams = sachetWeightGrams,
        containerWeightGrams = containerWeightGrams,
        patchTotalMg = patchTotalMg,
        patchReleaseRateMcgPerDay = patchReleaseRateMcgPerDay,
        displayName = displayName?.takeUnless(String::isBlank),
        identityKey = identityKey,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        archivedAtEpochMillis = archivedAtEpochMillis,
        // Backups exported before this column existed simply omit it; default
        // to MG (matches both pre-picker behavior and the model default).
        displayDoseUnit = MedicineDisplayDoseUnit.fromStorageValue(displayDoseUnit).name,
        trackingEnabled = normalizedStock?.trackingEnabled ?: false,
        stockUnitsRemaining = normalizedStock?.unitsRemaining,
        stockUnitsLastTotal = normalizedStock?.unitsLastTotal,
        openContainerAmount = normalizedStock?.openContainerAmount,
        warnAtDaysRemaining = normalizedStock?.warnAtDaysRemaining ?: 14,
        stockGeneration = normalizedStock?.stockGeneration ?: 0L,
    )
}

private fun MedicinePreparationType.toValidatedPreparation(
    medicineUuid: String,
    strengthMgPerTablet: Double?,
    strengthMgPerVial: Double?,
    concentrationMgPerMl: Double?,
    vialVolumeMl: Double?,
    concentrationPercent: Double?,
    sachetWeightGrams: Double?,
    containerWeightGrams: Double?,
    patchTotalMg: Double?,
    patchReleaseRateMcgPerDay: Double?,
): MedicinePreparation {
    fun requireFieldsOnly(vararg requiredNames: String) {
        val required = requiredNames.toSet()
        val fields = listOf(
            "strengthMgPerTablet" to strengthMgPerTablet,
            "strengthMgPerVial" to strengthMgPerVial,
            "concentrationMgPerMl" to concentrationMgPerMl,
            "vialVolumeMl" to vialVolumeMl,
            "concentrationPercent" to concentrationPercent,
            "sachetWeightGrams" to sachetWeightGrams,
            "containerWeightGrams" to containerWeightGrams,
            "patchTotalMg" to patchTotalMg,
            "patchReleaseRateMcgPerDay" to patchReleaseRateMcgPerDay,
        )
        fields.forEach { (name, value) ->
            if (name in required) {
                require(value != null) {
                    "Medicine $medicineUuid $this is missing $name."
                }
            } else {
                require(value == null) {
                    "Medicine $medicineUuid $this has unexpected $name."
                }
            }
        }
    }
    return when (this) {
        MedicinePreparationType.PILL -> {
            requireFieldsOnly("strengthMgPerTablet")
            MedicinePreparation.Pill(strengthMgPerTablet = checkNotNull(strengthMgPerTablet))
        }
        MedicinePreparationType.CAPSULE -> {
            requireFieldsOnly("strengthMgPerTablet")
            MedicinePreparation.Capsule(strengthMgPerCapsule = checkNotNull(strengthMgPerTablet))
        }
        MedicinePreparationType.INJECTION_SINGLE_USE_VIAL -> {
            requireFieldsOnly("strengthMgPerVial")
            MedicinePreparation.InjectionSingleUseVial(
                strengthMgPerVial = checkNotNull(strengthMgPerVial)
            )
        }
        MedicinePreparationType.INJECTION_MULTI_USE_VIAL -> {
            requireFieldsOnly("concentrationMgPerMl", "vialVolumeMl")
            MedicinePreparation.InjectionMultiUseVial(
                concentrationMgPerMl = checkNotNull(concentrationMgPerMl),
                vialVolumeMl = checkNotNull(vialVolumeMl),
            )
        }
        MedicinePreparationType.GEL_SACHET -> {
            requireFieldsOnly("concentrationPercent", "sachetWeightGrams")
            MedicinePreparation.GelSachet(
                concentrationPercent = checkNotNull(concentrationPercent),
                sachetWeightGrams = checkNotNull(sachetWeightGrams),
            )
        }
        MedicinePreparationType.GEL_CONTAINER -> {
            requireFieldsOnly("concentrationPercent", "containerWeightGrams")
            MedicinePreparation.GelContainer(
                concentrationPercent = checkNotNull(concentrationPercent),
                containerWeightGrams = checkNotNull(containerWeightGrams),
            )
        }
        MedicinePreparationType.PATCH -> {
            val patchFields = listOfNotNull(patchTotalMg, patchReleaseRateMcgPerDay)
            require(patchFields.size == 1) {
                "Medicine $medicineUuid PATCH must specify exactly one of patchTotalMg or patchReleaseRateMcgPerDay."
            }
            // No other preparation field may be set.
            require(strengthMgPerTablet == null) {
                "Medicine $medicineUuid PATCH has unexpected strengthMgPerTablet."
            }
            require(strengthMgPerVial == null) {
                "Medicine $medicineUuid PATCH has unexpected strengthMgPerVial."
            }
            require(concentrationMgPerMl == null && vialVolumeMl == null) {
                "Medicine $medicineUuid PATCH has unexpected injection fields."
            }
            require(concentrationPercent == null && sachetWeightGrams == null && containerWeightGrams == null) {
                "Medicine $medicineUuid PATCH has unexpected gel fields."
            }
            MedicinePreparation.Patch(
                specification = patchTotalMg?.let(MedicinePreparation.PatchSpecification::TotalMg)
                    ?: MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay(
                        valueMcgPerDay = checkNotNull(patchReleaseRateMcgPerDay)
                    )
            )
        }

        MedicinePreparationType.PATCH_OFF -> {
            requireFieldsOnly()
            MedicinePreparation.PatchOff
        }
    }
}

private fun BackupMedicationGroupItemSnapshot.toValidatedItemData(
    fieldPrefix: String,
    medicinePreparationTypesByUuid: Map<String, MedicinePreparationType>,
): ValidatedMedicationData {
    return ValidatedMedicationData.fromSnapshot(
        category = null,
        medicineUuidValue = medicineUuid,
        applicationTypeValue = applicationType,
        doseInstructionKindValue = doseInstructionKind,
        tabletFractionNumerator = tabletFractionNumerator,
        tabletFractionDenominator = tabletFractionDenominator,
        doseVolumeMl = doseVolumeMl,
        doseWeightGrams = doseWeightGrams,
        gelApplicationAreaValue = gelApplicationArea,
        fieldPrefix = fieldPrefix,
        medicinePreparationTypesByUuid = medicinePreparationTypesByUuid,
    )
}

private fun BackupMedicationLogSnapshot.toValidatedLogData(
    fieldPrefix: String,
    medicinePreparationTypesByUuid: Map<String, MedicinePreparationType>,
): ValidatedMedicationData {
    return ValidatedMedicationData.fromSnapshot(
        category = category,
        medicineUuidValue = medicineUuid,
        applicationTypeValue = applicationType,
        doseInstructionKindValue = doseInstructionKind,
        tabletFractionNumerator = tabletFractionNumerator,
        tabletFractionDenominator = tabletFractionDenominator,
        doseVolumeMl = doseVolumeMl,
        doseWeightGrams = doseWeightGrams,
        gelApplicationAreaValue = gelApplicationArea,
        fieldPrefix = fieldPrefix,
        medicinePreparationTypesByUuid = medicinePreparationTypesByUuid,
    )
}

internal data class ValidatedBackupSnapshot(
    val settings: ValidatedBackupSettings,
    val userProfile: UserProfileEntity?,
    val medicines: List<MedicineEntity>,
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
    val pureBlackEnabled: Boolean,
    val cjkTextOffsetEnabled: Boolean,
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
    val widgetContentScale: Float,
    val widgetBackgroundAlpha: Float,
    val widgetDarkModeOption: DarkModeOption,
    val groupNameCounter: Int,
    val firstDayOfWeekOption: FirstDayOfWeekOption,
    val stockNudgeEnabled: Boolean,
    val stockNudgeUserEnabled: Boolean,
)

private data class ValidatedMedicationData(
    // For group items the category is computed from the referenced medicine
    // (or defaulted for PATCH_OFF), so the item path passes `null` to the
    // factory. Log entries carry their own historical category and must
    // surface it verbatim.
    val category: MedicationCategory,
    val medicineUuid: String?,
    val applicationType: MedicationApplicationType,
    val doseInstructionKind: DoseInstructionKind,
    val tabletFractionNumerator: Int?,
    val tabletFractionDenominator: Int?,
    val doseVolumeMl: Double?,
    val doseWeightGrams: Double?,
    val gelApplicationArea: MedicationGelApplicationArea,
) {
    companion object {
        fun fromSnapshot(
            category: String?,
            medicineUuidValue: String?,
            applicationTypeValue: String,
            doseInstructionKindValue: String,
            tabletFractionNumerator: Int?,
            tabletFractionDenominator: Int?,
            doseVolumeMl: Double?,
            doseWeightGrams: Double?,
            gelApplicationAreaValue: String,
            fieldPrefix: String,
            medicinePreparationTypesByUuid: Map<String, MedicinePreparationType>,
        ): ValidatedMedicationData {
            val applicationType = requireEnumName<MedicationApplicationType>(
                applicationTypeValue,
                "$fieldPrefix application type",
            )
            val doseInstructionKind = requireEnumName<DoseInstructionKind>(
                doseInstructionKindValue,
                "$fieldPrefix dose instruction kind",
            )
            val gelApplicationArea = requireEnumName<MedicationGelApplicationArea>(
                gelApplicationAreaValue,
                "$fieldPrefix gel application area",
            )

            val resolvedMedicineUuid = if (medicineUuidValue != null) {
                val parsed = medicineUuidValue.parseUuid("$fieldPrefix medicine UUID").toString()
                require(parsed in medicinePreparationTypesByUuid) {
                    "$fieldPrefix references a missing medicine $parsed."
                }
                parsed
            } else {
                require(applicationType == MedicationApplicationType.PATCH_OFF) {
                    "$fieldPrefix has no medicine but is not a patch-off."
                }
                null
            }
            val preparationType = resolvedMedicineUuid?.let(medicinePreparationTypesByUuid::get)

            val resolvedCategory: MedicationCategory = if (category != null) {
                requireEnumName(category, "$fieldPrefix category")
            } else {
                // Group items derive their category from the referenced
                // medicine; PATCH_OFF falls back to ESTRADIOL because the
                // catalog only ships the estradiol patch.
                MedicationCategory.ESTRADIOL
            }

            // Dose-instruction shape must match its kind. Reject orphaned
            // numerator/denominator/volume/weight so a backup can't
            // smuggle inconsistent state past validation.
            val doseInstruction = when (doseInstructionKind) {
                DoseInstructionKind.TABLET_FRACTION -> {
                    val numerator = tabletFractionNumerator
                        ?: throw IllegalArgumentException(
                            "$fieldPrefix TABLET_FRACTION must provide tabletFractionNumerator."
                        )
                    val denominator = tabletFractionDenominator
                        ?: throw IllegalArgumentException(
                            "$fieldPrefix TABLET_FRACTION must provide tabletFractionDenominator."
                        )
                    require(numerator > 0 && denominator > 0) {
                        "$fieldPrefix TABLET_FRACTION must have positive numerator and denominator."
                    }
                    require(doseVolumeMl == null && doseWeightGrams == null) {
                        "$fieldPrefix TABLET_FRACTION must not carry volume or weight."
                    }
                    DoseInstruction.TabletFraction(
                        numerator = numerator,
                        denominator = denominator,
                    )
                }
                DoseInstructionKind.WHOLE_UNIT -> {
                    require(
                        tabletFractionNumerator == null &&
                            tabletFractionDenominator == null &&
                            doseVolumeMl == null &&
                            doseWeightGrams == null
                    ) {
                        "$fieldPrefix WHOLE_UNIT must not carry tablet fraction or volume/weight fields."
                    }
                    DoseInstruction.WholeUnit
                }
                DoseInstructionKind.VOLUME_ML -> {
                    val volumeMl = doseVolumeMl.requirePositiveFinite("$fieldPrefix dose volume")
                    require(
                        tabletFractionNumerator == null &&
                            tabletFractionDenominator == null &&
                            doseWeightGrams == null
                    ) {
                        "$fieldPrefix VOLUME_ML must not carry tablet fraction or weight."
                    }
                    DoseInstruction.VolumeMl(volumeMl)
                }
                DoseInstructionKind.WEIGHT_GRAMS -> {
                    val weightGrams = doseWeightGrams.requirePositiveFinite("$fieldPrefix dose weight")
                    require(
                        tabletFractionNumerator == null &&
                            tabletFractionDenominator == null &&
                            doseVolumeMl == null
                    ) {
                        "$fieldPrefix WEIGHT_GRAMS must not carry tablet fraction or volume."
                    }
                    DoseInstruction.WeightGrams(weightGrams)
                }
                DoseInstructionKind.NOOP -> {
                    require(applicationType == MedicationApplicationType.PATCH_OFF) {
                        "$fieldPrefix NOOP dose instruction is only valid for PATCH_OFF."
                    }
                    require(
                        tabletFractionNumerator == null &&
                            tabletFractionDenominator == null &&
                            doseVolumeMl == null &&
                            doseWeightGrams == null
                    ) {
                        "$fieldPrefix NOOP must not carry dose fields."
                    }
                    DoseInstruction.Noop
                }
            }
            val normalizedDoseInstruction = if (
                preparationType == MedicinePreparationType.PILL &&
                doseInstruction == DoseInstruction.WholeUnit
            ) {
                DoseInstruction.TabletFraction(numerator = 1, denominator = 1)
            } else {
                doseInstruction
            }
            val normalizedDoseFields = normalizedDoseInstruction.toBackupStorageFields()

            require(applicationType.isCompatibleWith(preparationType)) {
                "$fieldPrefix applicationType=$applicationType is not compatible with preparation=$preparationType."
            }
            require(normalizedDoseInstruction.isCompatibleWith(preparationType)) {
                "$fieldPrefix doseInstruction=${normalizedDoseInstruction.kind} " +
                    "is not compatible with preparation=$preparationType."
            }

            if (applicationType != MedicationApplicationType.GEL) {
                require(gelApplicationArea == MedicationGelApplicationArea.DEFAULT) {
                    "$fieldPrefix gel application area is only valid for gel medications."
                }
            }

            return ValidatedMedicationData(
                category = resolvedCategory,
                medicineUuid = resolvedMedicineUuid,
                applicationType = applicationType,
                doseInstructionKind = normalizedDoseFields.doseInstructionKind,
                tabletFractionNumerator = normalizedDoseFields.tabletFractionNumerator,
                tabletFractionDenominator = normalizedDoseFields.tabletFractionDenominator,
                doseVolumeMl = normalizedDoseFields.doseVolumeMl,
                doseWeightGrams = normalizedDoseFields.doseWeightGrams,
                gelApplicationArea = gelApplicationArea,
            )
        }
    }
}

private fun DoseInstruction.toBackupStorageFields(): BackupDoseInstructionStorageFields {
    return BackupDoseInstructionStorageFields(
        doseInstructionKind = kind,
        tabletFractionNumerator = (this as? DoseInstruction.TabletFraction)?.numerator,
        tabletFractionDenominator = (this as? DoseInstruction.TabletFraction)?.denominator,
        doseVolumeMl = (this as? DoseInstruction.VolumeMl)?.valueMl,
        doseWeightGrams = (this as? DoseInstruction.WeightGrams)?.valueGrams,
    )
}

private data class BackupDoseInstructionStorageFields(
    val doseInstructionKind: DoseInstructionKind,
    val tabletFractionNumerator: Int?,
    val tabletFractionDenominator: Int?,
    val doseVolumeMl: Double?,
    val doseWeightGrams: Double?,
)

// Renders the leading container bytes (magic + version + format flags) as
// hex so a backup wrongly rejected as incompatible can be diagnosed from a
// log line alone: a wrong/missing magic, an unexpected version byte, or a
// truncated file all become visible here. Stops well short of the
// ciphertext, so no decrypted user data is logged.
private fun ByteArray.headerPreviewHex(byteCount: Int = 40): String {
    if (isEmpty()) return "<empty>"
    return take(byteCount).joinToString(separator = " ") { byte ->
        "%02x".format(byte)
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

private fun Float.requireFiniteIn(
    range: ClosedFloatingPointRange<Float>,
    fieldName: String,
): Float {
    require(isFinite() && this in range) {
        "$fieldName must be finite and between ${range.start} and ${range.endInclusive}."
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
