package com.mkx.hrttracker.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.UserProfileRepository
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResult
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupMedication
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicineSelection
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
    private val medicineRepository: MedicineRepository,
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
            passwordChars.fill(Char.MIN_VALUE)
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
        val stockNudgeEnabled = settingsRepository.stockNudgeEnabledFlow.first()
        val stockNudgeUserEnabled = settingsRepository.stockNudgeUserEnabledFlow.first()
        val userProfile = userProfileRepository.getCurrentProfile()
        // Pulled before groups/logs so the importer can build its valid-medicine
        // set up front and reject any item or log that references a row absent
        // from this list.
        val medicines = medicineRepository.getAll()
        val medicationGroups = medicationGroupRepository.getGroups()
        val medicationLogs = medicationLogRepository.getEntries()
        val customBloodAnalytes = bloodTestRepository.getCustomAnalytes()
        val bloodTestPanels = bloodTestRepository.getPanels()

        return BackupSnapshot(
            exportedAtEpochMillis = exportedAt.toEpochMilli(),
            app = BackupAppSnapshot(
                packageName = BACKUP_APP_PACKAGE_NAME,
            ),
            settings = BackupSettingsSnapshot(
                darkModeOption = settings.darkModeOption.name,
                adaptiveColorEnabled = settings.adaptiveColorEnabled,
                pureBlackEnabled = settings.pureBlackEnabled,
                cjkTextOffsetEnabled = settings.cjkTextOffsetEnabled,
                hazeBlurEnabled = settings.hazeBlurEnabled,
                remindersEnabled = settings.remindersEnabled,
                showArchivedGroupRecords = settings.showArchivedGroupRecords,
                hideReferenceRanges = settings.hideReferenceRanges,
                // Do not include screenLockProtectionEnabled; app-lock protection stays local.
                appLockGracePeriodOption = settings.appLockGracePeriodOption.name,
                hideScreenContentEnabled = settings.hideScreenContentEnabled,
                onboardingCompleted = onboardingCompleted,
                appLanguageOption = settings.appLanguageOption.name,
                homeE2DisplayUnit = settings.homeE2DisplayUnit.storageValue,
                homeE2ChartWindow = settings.homeE2ChartWindowOption.name,
                calibrationDefaultUnits = settings.calibrationDefaultUnits.entries
                    .associate { (analyteKey, unitKey) ->
                        analyteKey.storageValue to unitKey.storageValue
                    },
                lastSeenTimeZoneId = settings.lastSeenTimeZoneId,
                hideMedicationDetails = settings.hideMedicationDetails,
                // interim: real appearance backup lands in the next commit
                widgetContentScale = 1.0f,
                widgetBackgroundAlpha = 1.0f,
                widgetDarkModeOption = "FOLLOW_SYSTEM",
                groupNameCounter = settings.groupNameCounter,
                firstDayOfWeekOption = settings.firstDayOfWeekOption.name,
                stockNudgeEnabled = stockNudgeEnabled,
                stockNudgeUserEnabled = stockNudgeUserEnabled,
            ),
            userProfile = BackupUserProfileSnapshot(
                weightKg = userProfile.weightKg,
                weightOriginalValue = userProfile.weightOriginalValue,
                weightOriginalUnit = userProfile.weightOriginalUnit.name,
                updatedAtEpochMillis = userProfile.updatedAt?.toEpochMilli(),
            ),
            medicines = medicines.map { medicine -> medicine.toBackupSnapshot() },
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

    private fun Medicine.toBackupSnapshot(): BackupMedicineSnapshot {
        val storageFields = preparation.toBackupStorageFields()
        return BackupMedicineSnapshot(
            uuid = uuid.toString(),
            selectionKind = selection.kind.name,
            // PATCH_OFF singleton reuses the CATALOG selectionKind for storage
            // (kind is CATALOG) but has no medicationKey / customName; the
            // preparationType PATCH_OFF marker rebuilds the PatchOff selection
            // on restore. Mirrors MedicineEntityMappers.toEntity.
            medicationKey = when (val currentSelection = selection) {
                is MedicineSelection.Catalog -> currentSelection.medicationKey.name
                is MedicineSelection.Custom -> null
                is MedicineSelection.PatchOff -> null
            },
            customMedicationName = when (val currentSelection = selection) {
                is MedicineSelection.Catalog -> null
                is MedicineSelection.Custom -> currentSelection.medicationName
                is MedicineSelection.PatchOff -> null
            },
            customMedicationNameNormalized = when (val currentSelection = selection) {
                is MedicineSelection.Catalog -> null
                is MedicineSelection.Custom -> currentSelection.normalizedMedicationName
                is MedicineSelection.PatchOff -> null
            },
            category = category.name,
            preparationType = storageFields.preparationType,
            strengthMgPerTablet = storageFields.strengthMgPerTablet,
            strengthMgPerVial = storageFields.strengthMgPerVial,
            concentrationMgPerMl = storageFields.concentrationMgPerMl,
            vialVolumeMl = storageFields.vialVolumeMl,
            concentrationPercent = storageFields.concentrationPercent,
            sachetWeightGrams = storageFields.sachetWeightGrams,
            containerWeightGrams = storageFields.containerWeightGrams,
            patchTotalMg = storageFields.patchTotalMg,
            patchReleaseRateMcgPerDay = storageFields.patchReleaseRateMcgPerDay,
            displayName = displayName,
            identityKey = identityKey,
            createdAtEpochMillis = createdAt.toEpochMilli(),
            updatedAtEpochMillis = updatedAt.toEpochMilli(),
            archivedAtEpochMillis = archivedAt?.toEpochMilli(),
            displayDoseUnit = displayDoseUnit.name,
            stock = if (stock.trackingEnabled) {
                BackupMedicineStockSnapshot(
                    trackingEnabled = true,
                    unitsRemaining = stock.unitsRemaining,
                    unitsLastTotal = stock.unitsLastTotal,
                    openContainerAmount = stock.openContainerAmount,
                    warnAtDaysRemaining = stock.warnAtDaysRemaining,
                    stockGeneration = stock.generation,
                )
            } else {
                null
            },
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
        val instructionFields = doseInstruction.toBackupFields()
        return BackupMedicationGroupItemSnapshot(
            uuid = uuid.toString(),
            count = count,
            medicineUuid = medicineUuid?.toString(),
            applicationType = applicationType.name,
            doseInstructionKind = instructionFields.kind,
            tabletFractionNumerator = instructionFields.tabletFractionNumerator,
            tabletFractionDenominator = instructionFields.tabletFractionDenominator,
            doseVolumeMl = instructionFields.doseVolumeMl,
            doseWeightGrams = instructionFields.doseWeightGrams,
            gelApplicationArea = "DEFAULT",
        )
    }

    private fun MedicationLogEntry.toBackupSnapshot(): BackupMedicationLogSnapshot {
        val instructionFields = doseInstruction.toBackupFields()
        return BackupMedicationLogSnapshot(
            uuid = uuid.toString(),
            category = category.name,
            medicineUuid = medicineUuid?.toString(),
            applicationType = applicationType.name,
            doseInstructionKind = instructionFields.kind,
            tabletFractionNumerator = instructionFields.tabletFractionNumerator,
            tabletFractionDenominator = instructionFields.tabletFractionDenominator,
            doseVolumeMl = instructionFields.doseVolumeMl,
            doseWeightGrams = instructionFields.doseWeightGrams,
            gelApplicationArea = "DEFAULT",
            equivalentE2Mg = equivalentE2Mg,
            doseAmountDelta = doseAmountDelta,
            sourceGroupUuid = sourceGroupUuid?.toString(),
            scheduleTimeUuid = scheduleTimeUuid?.toString(),
            appliedAtEpochMillis = appliedAt.toEpochMilli(),
            appliedAtTimeZoneId = appliedAtTimeZoneId,
            scheduledForIso = scheduledFor?.toString(),
            count = count,
        )
    }

    private fun DoseInstruction.toBackupFields(): BackupDoseInstructionFields {
        return BackupDoseInstructionFields(
            kind = kind.name,
            tabletFractionNumerator = (this as? DoseInstruction.TabletFraction)?.numerator,
            tabletFractionDenominator = (this as? DoseInstruction.TabletFraction)?.denominator,
            doseVolumeMl = (this as? DoseInstruction.VolumeMl)?.valueMl,
            doseWeightGrams = (this as? DoseInstruction.WeightGrams)?.valueGrams,
        )
    }

    private fun MedicinePreparation.toBackupStorageFields(): BackupMedicineStorageFields {
        return when (this) {
            is MedicinePreparation.Pill -> BackupMedicineStorageFields(
                preparationType = type.name,
                strengthMgPerTablet = strengthMgPerTablet,
            )

            is MedicinePreparation.Capsule -> BackupMedicineStorageFields(
                preparationType = type.name,
                strengthMgPerTablet = strengthMgPerCapsule,
            )

            is MedicinePreparation.InjectionSingleUseVial -> BackupMedicineStorageFields(
                preparationType = type.name,
                strengthMgPerVial = strengthMgPerVial,
            )

            is MedicinePreparation.InjectionMultiUseVial -> BackupMedicineStorageFields(
                preparationType = type.name,
                concentrationMgPerMl = concentrationMgPerMl,
                vialVolumeMl = vialVolumeMl,
            )

            is MedicinePreparation.GelSachet -> BackupMedicineStorageFields(
                preparationType = type.name,
                concentrationPercent = concentrationPercent,
                sachetWeightGrams = sachetWeightGrams,
            )

            is MedicinePreparation.GelContainer -> BackupMedicineStorageFields(
                preparationType = type.name,
                concentrationPercent = concentrationPercent,
                containerWeightGrams = containerWeightGrams,
            )

            is MedicinePreparation.Patch -> when (val currentSpecification = specification) {
                is MedicinePreparation.PatchSpecification.TotalMg -> BackupMedicineStorageFields(
                    preparationType = type.name,
                    patchTotalMg = currentSpecification.valueMg,
                )

                is MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay -> BackupMedicineStorageFields(
                    preparationType = type.name,
                    patchReleaseRateMcgPerDay = currentSpecification.valueMcgPerDay,
                )
            }

            // No numeric columns; the preparationType marker is the whole payload.
            is MedicinePreparation.PatchOff -> BackupMedicineStorageFields(
                preparationType = type.name,
            )
        }
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
            return "featherline-backup-$timestamp.hrtbackup"
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

private data class BackupDoseInstructionFields(
    val kind: String,
    val tabletFractionNumerator: Int?,
    val tabletFractionDenominator: Int?,
    val doseVolumeMl: Double?,
    val doseWeightGrams: Double?,
)

private data class BackupMedicineStorageFields(
    val preparationType: String,
    val strengthMgPerTablet: Double? = null,
    val strengthMgPerVial: Double? = null,
    val concentrationMgPerMl: Double? = null,
    val vialVolumeMl: Double? = null,
    val concentrationPercent: Double? = null,
    val sachetWeightGrams: Double? = null,
    val containerWeightGrams: Double? = null,
    val patchTotalMg: Double? = null,
    val patchReleaseRateMcgPerDay: Double? = null,
)
