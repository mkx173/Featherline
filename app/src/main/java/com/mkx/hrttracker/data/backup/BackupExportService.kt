package com.mkx.hrttracker.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.UserProfileRepository
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResult
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationDoseUnit
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationSelection
import com.mkx.hrttracker.util.backupFileNameTimestampFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupExportService @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val userProfileRepository: UserProfileRepository,
    private val medicationGroupRepository: MedicationGroupRepository,
    private val medicationLogRepository: MedicationLogRepository,
    private val bloodTestRepository: BloodTestRepository,
    private val backupCrypto: BackupCrypto,
) {
    internal suspend fun buildBackupSnapshotJson(
        exportedAt: Instant = Instant.now(),
    ): String = withContext(Dispatchers.IO) {
        BackupSnapshotJsonCodec.encode(buildSnapshot(exportedAt))
    }

    internal suspend fun buildEncryptedBackupBytes(
        password: String,
        exportedAt: Instant = Instant.now(),
    ): ByteArray = withContext(Dispatchers.IO) {
        val passwordChars = password.toCharArray()
        try {
            backupCrypto.encryptSnapshotJson(
                json = buildBackupSnapshotJson(exportedAt),
                password = passwordChars,
            )
        } finally {
            passwordChars.fill('\u0000')
        }
    }

    suspend fun prepareBackupExport(
        password: String,
        exportedAt: Instant = Instant.now(),
    ): PreparedBackupExport = withContext(Dispatchers.IO) {
        val displayName = buildBackupFileName(exportedAt)
        val payload = buildEncryptedBackupBytes(
            password = password,
            exportedAt = exportedAt,
        )
        val tempFile = File.createTempFile(
            PREPARED_BACKUP_FILE_PREFIX,
            PREPARED_BACKUP_FILE_SUFFIX,
            context.cacheDir,
        )

        try {
            try {
                tempFile.outputStream().use { outputStream ->
                    outputStream.write(payload)
                }
            } catch (error: Exception) {
                tempFile.delete()
                throw error
            }
        } finally {
            payload.fill(0)
        }

        PreparedBackupExport(
            displayName = displayName,
            tempFilePath = tempFile.absolutePath,
        )
    }

    suspend fun exportPreparedBackup(
        directoryUri: Uri,
        preparedBackupExport: PreparedBackupExport,
    ): BackupExportedFile = withContext(Dispatchers.IO) {
        persistDirectoryAccess(directoryUri)
        val tempFile = File(preparedBackupExport.tempFilePath)
        if (!tempFile.exists()) {
            throw IOException("Prepared backup payload is no longer available.")
        }

        var documentUri: Uri? = null
        try {
            documentUri = createBackupDocument(
                directoryUri = directoryUri,
                displayName = preparedBackupExport.displayName,
            )
            context.contentResolver.openOutputStream(documentUri)?.use { outputStream ->
                tempFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: throw IOException("Unable to open an output stream for backup export.")

            BackupExportedFile(
                displayName = preparedBackupExport.displayName,
                uri = documentUri,
            )
        } catch (error: Exception) {
            documentUri?.let { createdDocumentUri ->
                runCatching {
                    DocumentsContract.deleteDocument(context.contentResolver, createdDocumentUri)
                }
            }
            throw error
        } finally {
            discardPreparedBackup(preparedBackupExport)
        }
    }

    suspend fun discardPreparedBackup(
        preparedBackupExport: PreparedBackupExport,
    ) = withContext(Dispatchers.IO) {
        File(preparedBackupExport.tempFilePath).delete()
    }

    internal fun restorePreparedBackupExport(
        displayName: String,
        tempFilePath: String,
    ): PreparedBackupExport {
        return PreparedBackupExport(
            displayName = displayName,
            tempFilePath = tempFilePath,
        )
    }

    private suspend fun buildSnapshot(
        exportedAt: Instant,
    ): BackupSnapshot {
        val settings = settingsRepository.getCurrentSettings()
        val onboardingCompleted = settingsRepository.onboardingCompleted.first()
        val userProfile = userProfileRepository.getCurrentProfile()
        val medicationGroups = medicationGroupRepository.getGroups()
        val medicationLogs = medicationLogRepository.getEntries()
        val customBloodAnalytes = bloodTestRepository.getCustomAnalytes()
        val bloodTestPanels = bloodTestRepository.getPanels()

        return BackupSnapshot(
            exportedAtEpochMillis = exportedAt.toEpochMilli(),
            app = BackupAppSnapshot(
                packageName = context.packageName,
            ),
            settings = BackupSettingsSnapshot(
                darkModeOption = settings.darkModeOption.name,
                adaptiveColorEnabled = settings.adaptiveColorEnabled,
                remindersEnabled = settings.remindersEnabled,
                showArchivedGroupRecords = settings.showArchivedGroupRecords,
                // Do not include screenLockProtectionEnabled; app-lock protection stays local.
                appLockGracePeriodOption = settings.appLockGracePeriodOption.name,
                hideScreenContentEnabled = settings.hideScreenContentEnabled,
                onboardingCompleted = onboardingCompleted,
                appLanguageOption = settings.appLanguageOption.name,
                homeE2DisplayUnit = settings.homeE2DisplayUnit.storageValue,
                calibrationDefaultUnits = settings.calibrationDefaultUnits.entries
                    .associate { (analyteKey, unitKey) ->
                        analyteKey.storageValue to unitKey.storageValue
                    },
            ),
            userProfile = BackupUserProfileSnapshot(
                weightKg = userProfile.weightKg,
                weightOriginalValue = userProfile.weightOriginalValue,
                weightOriginalUnit = userProfile.weightOriginalUnit.name,
                updatedAtEpochMillis = userProfile.updatedAt?.toEpochMilli(),
            ),
            medicationGroups = medicationGroups.map { group -> group.toBackupSnapshot() },
            medicationLogs = medicationLogs.map { entry -> entry.toBackupSnapshot() },
            customBloodAnalytes = customBloodAnalytes.map { analyte ->
                BackupCustomBloodAnalyteSnapshot(
                    uuid = analyte.uuid.toString(),
                    abbreviation = analyte.abbreviation,
                    name = analyte.name,
                    unitLabel = analyte.unitLabel,
                    createdAtEpochMillis = analyte.createdAt.toEpochMilli(),
                    updatedAtEpochMillis = analyte.updatedAt.toEpochMilli(),
                    archivedAtEpochMillis = analyte.archivedAt?.toEpochMilli(),
                )
            },
            bloodTestPanels = bloodTestPanels.map { panel -> panel.toBackupSnapshot() },
        )
    }

    private fun MedicationGroup.toBackupSnapshot(): BackupMedicationGroupSnapshot {
        return BackupMedicationGroupSnapshot(
            uuid = uuid.toString(),
            name = name,
            colorKey = colorKey.name,
            notificationsEnabled = notificationsEnabled,
            schedule = BackupMedicationGroupScheduleSnapshot(
                type = schedule.type.name,
                interval = schedule.interval,
                sinceEpochDay = schedule.since.toEpochDay(),
                weeklyDaysOfWeek = schedule.weeklyDaysOfWeek
                    .map { dayOfWeek -> dayOfWeek.value }
                    .sorted(),
                times = schedule.timeSlots.map { slot ->
                    BackupMedicationGroupScheduleTimeSnapshot(
                        uuid = slot.uuid.toString(),
                        hourOfDay = slot.time.hour,
                        minuteOfHour = slot.time.minute,
                        effectiveFromLocalIso = slot.effectiveFrom.toString(),
                    )
                },
            ),
            medications = medications.map { medication -> medication.toBackupSnapshot() },
            createdAtEpochMillis = createdAt.toEpochMilli(),
            updatedAtEpochMillis = updatedAt.toEpochMilli(),
            archivedAtEpochMillis = archivedAt?.toEpochMilli(),
            archivedAtLocalIso = archivedAtLocal?.toString(),
            includePastScheduledSlots = includePastScheduledSlots,
            replacedByGroupUuid = replacedByGroupUuid?.toString(),
            recreatedFromGroupUuid = recreatedFromGroupUuid?.toString(),
        )
    }

    private fun MedicationGroupMedication.toBackupSnapshot(): BackupMedicationGroupItemSnapshot {
        val fields = details.toBackupFields()
        return BackupMedicationGroupItemSnapshot(
            uuid = uuid.toString(),
            count = count,
            category = fields.category,
            applicationType = fields.applicationType,
            selectionKind = fields.selectionKind,
            medicationKey = fields.medicationKey,
            customMedicationName = fields.customMedicationName,
            doseKind = fields.doseKind,
            doseValueMg = fields.doseValueMg,
            customDoseUnit = fields.customDoseUnit,
            doseValuePercent = fields.doseValuePercent,
            doseWeightGrams = fields.doseWeightGrams,
            doseReleaseRateMcgPerDay = fields.doseReleaseRateMcgPerDay,
            gelApplicationArea = fields.gelApplicationArea,
        )
    }

    private fun MedicationLogEntry.toBackupSnapshot(): BackupMedicationLogSnapshot {
        val fields = details.toBackupFields()
        return BackupMedicationLogSnapshot(
            uuid = uuid.toString(),
            category = fields.category,
            applicationType = fields.applicationType,
            selectionKind = fields.selectionKind,
            medicationKey = fields.medicationKey,
            customMedicationName = fields.customMedicationName,
            doseKind = fields.doseKind,
            doseValueMg = fields.doseValueMg,
            customDoseUnit = fields.customDoseUnit,
            doseValuePercent = fields.doseValuePercent,
            doseWeightGrams = fields.doseWeightGrams,
            doseReleaseRateMcgPerDay = fields.doseReleaseRateMcgPerDay,
            gelApplicationArea = fields.gelApplicationArea,
            dosageMgAsEstradiol = dosageMgAsEstradiol,
            sourceGroupUuid = sourceGroupUuid?.toString(),
            scheduleTimeUuid = scheduleTimeUuid?.toString(),
            appliedAtEpochMillis = appliedAt.toEpochMilli(),
            appliedAtTimeZoneId = appliedAtTimeZoneId,
            scheduledForIso = scheduledFor?.toString(),
            count = count,
        )
    }

    private fun MedicationDetails.toBackupFields(): BackupMedicationFields {
        return BackupMedicationFields(
            category = category.name,
            applicationType = applicationType.name,
            selectionKind = selection.kind.name,
            medicationKey = when (val currentSelection = selection) {
                is MedicationSelection.Catalog -> currentSelection.medicationKey.name
                is MedicationSelection.Custom -> null
            },
            customMedicationName = when (val currentSelection = selection) {
                is MedicationSelection.Catalog -> null
                is MedicationSelection.Custom -> currentSelection.medicationName
            },
            doseKind = dose.kind.name,
            doseValueMg = when (val currentDose = dose) {
                is MedicationDose.MgAsMedicine -> currentDose.valueMg
                is MedicationDose.GelEquivalentEstradiolMg -> currentDose.valueMg
                is MedicationDose.PatchTotalMg -> currentDose.valueMg
                else -> null
            },
            customDoseUnit = when {
                selection is MedicationSelection.Custom && dose is MedicationDose.MgAsMedicine ->
                    customDoseUnit.storageValue

                else -> MedicationDoseUnit.MG.storageValue
            },
            doseValuePercent = when (val currentDose = dose) {
                is MedicationDose.GelPercentAndWeight -> currentDose.percent
                else -> null
            },
            doseWeightGrams = when (val currentDose = dose) {
                is MedicationDose.GelPercentAndWeight -> currentDose.weightGrams
                else -> null
            },
            doseReleaseRateMcgPerDay = when (val currentDose = dose) {
                is MedicationDose.PatchReleaseRateMcgPerDay -> currentDose.valueMcgPerDay
                else -> null
            },
            gelApplicationArea = gelApplicationArea.name,
        )
    }

    private fun BloodTestPanel.toBackupSnapshot(): BackupBloodTestPanelSnapshot {
        return BackupBloodTestPanelSnapshot(
            uuid = uuid.toString(),
            collectedAtInstantEpochMillis = collectedAt.toEpochMilli(),
            collectedAtTimeZoneId = collectedAtTimeZoneId,
            notes = notes,
            timeSinceLastEstradiolDoseMillis = timeSinceLastEstradiolDoseMillis,
            timeSinceLastTestosteroneDoseMillis = timeSinceLastTestosteroneDoseMillis,
            createdAtEpochMillis = createdAt.toEpochMilli(),
            updatedAtEpochMillis = updatedAt.toEpochMilli(),
            results = results.map { result -> result.toBackupSnapshot() },
        )
    }

    private fun BloodTestResult.toBackupSnapshot(): BackupBloodTestResultSnapshot {
        return BackupBloodTestResultSnapshot(
            uuid = uuid.toString(),
            createdAtEpochMillis = createdAt.toEpochMilli(),
            displayOrder = displayOrder,
            builtinAnalyteKey = (analyte as? BloodTestResultAnalyte.Builtin)?.key?.storageValue,
            customAnalyteUuid = (analyte as? BloodTestResultAnalyte.Custom)?.uuid?.toString(),
            value = value,
            unitSnapshot = unitSnapshot,
            canonicalValue = canonicalValue,
        )
    }

    private fun persistDirectoryAccess(directoryUri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                directoryUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    private fun createBackupDocument(
        directoryUri: Uri,
        displayName: String,
    ): Uri {
        val parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(
            directoryUri,
            DocumentsContract.getTreeDocumentId(directoryUri)
        )
        return checkNotNull(
            DocumentsContract.createDocument(
                context.contentResolver,
                parentDocumentUri,
                BACKUP_MIME_TYPE,
                displayName,
            )
        ) {
            "Unable to create a backup document in the selected directory."
        }
    }

    companion object {
        internal fun buildBackupFileName(
            exportedAt: Instant,
            zoneId: ZoneId = ZoneId.systemDefault(),
        ): String {
            val timestamp = backupFileNameTimestampFormatter().format(exportedAt.atZone(zoneId))
            return "hrttracker-backup-$timestamp.hrtbackup"
        }

        private const val BACKUP_MIME_TYPE = "application/octet-stream"
        private const val PREPARED_BACKUP_FILE_PREFIX = "prepared-backup-"
        private const val PREPARED_BACKUP_FILE_SUFFIX = ".tmp"
    }
}

data class BackupExportedFile(
    val displayName: String,
    val uri: Uri,
)

data class PreparedBackupExport(
    val displayName: String,
    val tempFilePath: String,
)

private data class BackupMedicationFields(
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
