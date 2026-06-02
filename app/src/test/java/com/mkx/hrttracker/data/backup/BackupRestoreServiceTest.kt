package com.mkx.hrttracker.data.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.mkx.hrttracker.data.local.BloodTestDao
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.MedicationGroupDao
import com.mkx.hrttracker.data.local.MedicationLogDao
import com.mkx.hrttracker.data.local.MedicationLogEntryEntity
import com.mkx.hrttracker.data.local.MedicineDao
import com.mkx.hrttracker.data.local.MedicineEntity
import com.mkx.hrttracker.data.local.UserProfileDao
import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.UserProfileRepository
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.testCustomMedicine
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.reminder.MedicationReminderSnoozeScheduler
import com.mkx.hrttracker.reminder.ReminderNotificationManager
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.Instant
import java.util.UUID

class BackupRestoreServiceTest {
    private val context: Context = mockk(relaxed = true)
    private val contentResolver: ContentResolver = mockk(relaxed = true)
    private val databaseHolder: DatabaseHolder = mockk()
    private val database: HrtTrackerDatabase = mockk()
    private val medicationLogDao: MedicationLogDao = mockk(relaxed = true)
    private val medicationGroupDao: MedicationGroupDao = mockk(relaxed = true)
    private val medicineDao: MedicineDao = mockk(relaxed = true)
    private val bloodTestDao: BloodTestDao = mockk(relaxed = true)
    private val userProfileDao: UserProfileDao = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val homeSnapshotRepository: HomeSnapshotRepository = mockk()
    private val medicationReminderScheduler: MedicationReminderScheduler = mockk()
    private val medicationReminderSnoozeScheduler: MedicationReminderSnoozeScheduler = mockk()
    private val reminderNotificationManager: ReminderNotificationManager =
        mockk(relaxed = true)

    private lateinit var backupCrypto: BackupCrypto
    private lateinit var service: BackupRestoreService

    @Before
    fun setUp() {
        every { context.packageName } returns "com.mkx.hrttracker"
        every { context.contentResolver } returns contentResolver
        every { database.medicationLogDao() } returns medicationLogDao
        every { database.medicationGroupDao() } returns medicationGroupDao
        every { database.medicineDao() } returns medicineDao
        every { database.bloodTestDao() } returns bloodTestDao
        every { database.userProfileDao() } returns userProfileDao
        coEvery { homeSnapshotRepository.runHomeDataMutation<Unit>(any()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
        }
        coEvery { databaseHolder.runTransaction(any()) } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Unit>().invoke(database)
        }
        coEvery {
            settingsRepository.restoreSettings(
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(),
                any(), any(), any(),
            )
        } just Runs
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } just Runs
        coEvery { medicationReminderSnoozeScheduler.clearAllSnoozes() } just Runs

        backupCrypto = BackupCrypto(TestBackupArgon2KeyDeriver())
        service = BackupRestoreService(
            context = context,
            databaseHolder = databaseHolder,
            settingsRepository = settingsRepository,
            homeSnapshotRepository = homeSnapshotRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            reminderNotificationManager = reminderNotificationManager,
            backupCrypto = backupCrypto,
        )
    }

    @Test
    fun restoreBackup_clears_existing_snoozes_after_restoring_snapshot() = runTest {
        val fileUri: Uri = mockk(relaxed = true)
        every { contentResolver.openInputStream(fileUri) } returns ByteArrayInputStream(
            backupCrypto.encryptSnapshotJson(
                json = BackupSnapshotJsonCodec.encode(emptySnapshot()),
                password = "password".toCharArray(),
            )
        )

        service.restoreBackup(fileUri = fileUri, password = "password")

        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleAll(any()) }
        coVerify(exactly = 1) { medicationReminderSnoozeScheduler.clearAllSnoozes() }
    }

    @Test
    fun validateBackupFile_accepts_supported_backup_container() = runTest {
        val fileUri: Uri = mockk(relaxed = true)
        every { contentResolver.openInputStream(fileUri) } returns ByteArrayInputStream(
            backupCrypto.encryptSnapshotJson(
                json = BackupSnapshotJsonCodec.encode(emptySnapshot()),
                password = "password".toCharArray(),
            )
        )

        service.validateBackupFile(fileUri)
    }

    @Test
    fun validateBackupFile_rejects_non_backup_input_before_password_prompt() = runTest {
        val fileUri: Uri = mockk(relaxed = true)
        every { contentResolver.openInputStream(fileUri) } returns ByteArrayInputStream(
            "not a backup".encodeToByteArray()
        )

        val error = try {
            service.validateBackupFile(fileUri)
            fail("Expected validateBackupFile to reject non-backup input.")
            null
        } catch (error: IOException) {
            error
        }

        assertNotNull(error)
        assertTrue(
            "Expected incompatible backup validation to use a dedicated exception type.",
            error is IncompatibleBackupFileException,
        )
    }

    @Test
    fun restoreBackupBytes_roundTripsMedicineStockFieldsIntoInsertedEntities() = runTest {
        val medicineUuid = UUID.fromString("00000000-0000-0000-0000-000000000710")
        val logUuid = UUID.fromString("00000000-0000-0000-0000-000000000711")
        val encryptedBytes = backupCrypto.encryptSnapshotJson(
            json = BackupSnapshotJsonCodec.encode(stockSnapshot(medicineUuid, logUuid)),
            password = "password".toCharArray(),
        )
        val medicinesSlot = slot<List<MedicineEntity>>()
        val logsSlot = slot<List<MedicationLogEntryEntity>>()

        service.restoreBackupBytes(encryptedBytes = encryptedBytes, password = "password")

        coVerify(exactly = 1) { medicineDao.insertAll(capture(medicinesSlot)) }
        coVerify(exactly = 1) { medicationLogDao.insertEntries(capture(logsSlot)) }

        val restoredMedicine = medicinesSlot.captured.single()
        assertEquals(true, restoredMedicine.trackingEnabled)
        assertEquals(30.0, restoredMedicine.stockUnitsRemaining!!, 1e-9)
        assertEquals(45.0, restoredMedicine.stockUnitsLastTotal!!, 1e-9)
        assertNull(restoredMedicine.openContainerAmount)
        assertEquals(10, restoredMedicine.warnAtDaysRemaining)
        assertEquals(3L, restoredMedicine.stockGeneration)

        val restoredLog = logsSlot.captured.single()
        assertEquals(logUuid.toString(), restoredLog.uuid)
        assertEquals("INJECTION", restoredLog.applicationType)
        assertEquals(0.05, restoredLog.doseVolumeMl!!, 1e-9)
        assertEquals(0.1, restoredLog.doseAmountDelta!!, 1e-9)
    }

    @Test
    fun restoreBackupBytes_restoresDoseAmountDeltaFromExportedArchive() = runTest {
        val medicineUuid = UUID.fromString("00000000-0000-0000-0000-000000000720")
        val logUuid = UUID.fromString("00000000-0000-0000-0000-000000000721")
        val medicine = testCustomMedicine(
            uuid = medicineUuid,
            medicationName = "Exported injectable",
            category = MedicationCategory.CUSTOM,
            preparation = MedicinePreparation.InjectionMultiUseVial(
                concentrationMgPerMl = 40.0,
                vialVolumeMl = 1.0,
            ),
        )
        val exportService = backupExportServiceFor(
            medicines = listOf(medicine),
            medicationLogs = listOf(
                MedicationLogEntry(
                    uuid = logUuid,
                    medicine = medicine,
                    category = MedicationCategory.CUSTOM,
                    applicationType = MedicationApplicationType.INJECTION,
                    doseInstruction = DoseInstruction.VolumeMl(0.05),
                    equivalentE2Mg = null,
                    doseAmountDelta = 0.1,
                    sourceGroupUuid = null,
                    appliedAt = Instant.parse("2026-04-26T01:00:00Z"),
                    appliedAtTimeZoneId = "Asia/Tokyo",
                )
            ),
        )
        val logsSlot = slot<List<MedicationLogEntryEntity>>()

        val encryptedBytes = exportService.buildEncryptedBackupBytes(
            password = "password",
            exportedAt = Instant.parse("2026-04-26T03:04:05Z"),
        )
        service.restoreBackupBytes(encryptedBytes = encryptedBytes, password = "password")

        coVerify(exactly = 1) { medicationLogDao.insertEntries(capture(logsSlot)) }
        val restoredLog = logsSlot.captured.single()
        assertEquals(logUuid.toString(), restoredLog.uuid)
        assertEquals(0.1, restoredLog.doseAmountDelta!!, 1e-9)
    }

    @Test
    fun restoreBackupBytes_withoutDoseAmountDeltaField_restoresNullDoseAmountDelta() = runTest {
        val medicineUuid = UUID.fromString("00000000-0000-0000-0000-000000000730")
        val logUuid = UUID.fromString("00000000-0000-0000-0000-000000000731")
        val json = BackupSnapshotJsonCodec.encode(stockSnapshot(medicineUuid, logUuid))
            .replace(""","doseAmountDelta":0.1""", "")
        val encryptedBytes = backupCrypto.encryptSnapshotJson(
            json = json,
            password = "password".toCharArray(),
        )
        val logsSlot = slot<List<MedicationLogEntryEntity>>()

        service.restoreBackupBytes(encryptedBytes = encryptedBytes, password = "password")

        coVerify(exactly = 1) { medicationLogDao.insertEntries(capture(logsSlot)) }
        val restoredLog = logsSlot.captured.single()
        assertEquals(logUuid.toString(), restoredLog.uuid)
        assertNull(restoredLog.doseAmountDelta)
    }

    @Test
    fun restoreBackupBytes_acceptsWidgetContentScaleAcrossFullUiRange() = runTest {
        // The widget appearance slider lets users choose any scale in
        // 0.5..1.5 and export copies it verbatim. The restore validator must
        // accept that same domain — a narrower range silently rejects a
        // legitimately-exported backup as "incompatible". Restoring at both
        // extremes must succeed and preserve the stored value unchanged.
        val capturedScales = mutableListOf<Float>()
        coEvery {
            settingsRepository.restoreSettings(
                darkModeOption = any(),
                adaptiveColorEnabled = any(),
                pureBlackEnabled = any(),
                cjkTextOffsetEnabled = any(),
                remindersEnabled = any(),
                showArchivedGroupRecords = any(),
                hideReferenceRanges = any(),
                appLockGracePeriodOption = any(),
                hideScreenContentEnabled = any(),
                onboardingCompleted = any(),
                appLanguageOption = any(),
                calibrationDefaultUnits = any(),
                homeE2DisplayUnit = any(),
                homeE2ChartWindowOption = any(),
                lastSeenTimeZoneId = any(),
                hideMedicationDetails = any(),
                widgetContentScale = capture(capturedScales),
                widgetBackgroundAlpha = any(),
                widgetDarkModeOption = any(),
                groupNameCounter = any(),
                firstDayOfWeekOption = any(),
                stockNudgeEnabled = any(),
            )
        } just Runs

        listOf(0.5f, 1.5f).forEach { scale ->
            service.restoreBackupBytes(
                encryptedBytes = backupCrypto.encryptSnapshotJson(
                    json = BackupSnapshotJsonCodec.encode(snapshotWithWidgetContentScale(scale)),
                    password = "password".toCharArray(),
                ),
                password = "password",
            )
        }

        assertEquals(listOf(0.5f, 1.5f), capturedScales)
    }

    @Test
    fun restoreBackupBytes_restoresPureBlackSetting() = runTest {
        val capturedValues = mutableListOf<Boolean>()
        coEvery {
            settingsRepository.restoreSettings(
                darkModeOption = any(),
                adaptiveColorEnabled = any(),
                pureBlackEnabled = capture(capturedValues),
                cjkTextOffsetEnabled = any(),
                remindersEnabled = any(),
                showArchivedGroupRecords = any(),
                hideReferenceRanges = any(),
                appLockGracePeriodOption = any(),
                hideScreenContentEnabled = any(),
                onboardingCompleted = any(),
                appLanguageOption = any(),
                calibrationDefaultUnits = any(),
                homeE2DisplayUnit = any(),
                homeE2ChartWindowOption = any(),
                lastSeenTimeZoneId = any(),
                hideMedicationDetails = any(),
                widgetContentScale = any(),
                widgetBackgroundAlpha = any(),
                widgetDarkModeOption = any(),
                groupNameCounter = any(),
                firstDayOfWeekOption = any(),
                stockNudgeEnabled = any(),
            )
        } just Runs

        val snapshot = emptySnapshot().let { base ->
            base.copy(settings = base.settings.copy(pureBlackEnabled = true))
        }
        service.restoreBackupBytes(
            encryptedBytes = backupCrypto.encryptSnapshotJson(
                json = BackupSnapshotJsonCodec.encode(snapshot),
                password = "password".toCharArray(),
            ),
            password = "password",
        )

        assertEquals(listOf(true), capturedValues)
    }

    @Test
    fun restoreBackupBytes_restoresCjkTextOffsetSetting() = runTest {
        val capturedValues = mutableListOf<Boolean>()
        coEvery {
            settingsRepository.restoreSettings(
                darkModeOption = any(),
                adaptiveColorEnabled = any(),
                pureBlackEnabled = any(),
                cjkTextOffsetEnabled = capture(capturedValues),
                remindersEnabled = any(),
                showArchivedGroupRecords = any(),
                hideReferenceRanges = any(),
                appLockGracePeriodOption = any(),
                hideScreenContentEnabled = any(),
                onboardingCompleted = any(),
                appLanguageOption = any(),
                calibrationDefaultUnits = any(),
                homeE2DisplayUnit = any(),
                homeE2ChartWindowOption = any(),
                lastSeenTimeZoneId = any(),
                hideMedicationDetails = any(),
                widgetContentScale = any(),
                widgetBackgroundAlpha = any(),
                widgetDarkModeOption = any(),
                groupNameCounter = any(),
                firstDayOfWeekOption = any(),
                stockNudgeEnabled = any(),
            )
        } just Runs

        val snapshot = emptySnapshot().let { base ->
            base.copy(settings = base.settings.copy(cjkTextOffsetEnabled = true))
        }
        service.restoreBackupBytes(
            encryptedBytes = backupCrypto.encryptSnapshotJson(
                json = BackupSnapshotJsonCodec.encode(snapshot),
                password = "password".toCharArray(),
            ),
            password = "password",
        )

        assertEquals(listOf(true), capturedValues)
    }

    @Test
    fun restoreBackupBytes_withoutStockNudgeEnabledField_restoresTrue() = runTest {
        val capturedValues = mutableListOf<Boolean>()
        coEvery {
            settingsRepository.restoreSettings(
                darkModeOption = any(),
                adaptiveColorEnabled = any(),
                pureBlackEnabled = any(),
                cjkTextOffsetEnabled = any(),
                remindersEnabled = any(),
                showArchivedGroupRecords = any(),
                hideReferenceRanges = any(),
                appLockGracePeriodOption = any(),
                hideScreenContentEnabled = any(),
                onboardingCompleted = any(),
                appLanguageOption = any(),
                calibrationDefaultUnits = any(),
                homeE2DisplayUnit = any(),
                homeE2ChartWindowOption = any(),
                lastSeenTimeZoneId = any(),
                hideMedicationDetails = any(),
                widgetContentScale = any(),
                widgetBackgroundAlpha = any(),
                widgetDarkModeOption = any(),
                groupNameCounter = any(),
                firstDayOfWeekOption = any(),
                stockNudgeEnabled = capture(capturedValues),
            )
        } just Runs
        val json = BackupSnapshotJsonCodec.encode(emptySnapshot())
            .replace(",\"stockNudgeEnabled\":true", "")

        service.restoreBackupBytes(
            encryptedBytes = backupCrypto.encryptSnapshotJson(
                json = json,
                password = "password".toCharArray(),
            ),
            password = "password",
        )

        assertEquals(listOf(true), capturedValues)
    }

    @Test
    fun restoreBackupBytes_restoresStockNudgeEnabledFalse() = runTest {
        val capturedValues = mutableListOf<Boolean>()
        coEvery {
            settingsRepository.restoreSettings(
                darkModeOption = any(),
                adaptiveColorEnabled = any(),
                pureBlackEnabled = any(),
                cjkTextOffsetEnabled = any(),
                remindersEnabled = any(),
                showArchivedGroupRecords = any(),
                hideReferenceRanges = any(),
                appLockGracePeriodOption = any(),
                hideScreenContentEnabled = any(),
                onboardingCompleted = any(),
                appLanguageOption = any(),
                calibrationDefaultUnits = any(),
                homeE2DisplayUnit = any(),
                homeE2ChartWindowOption = any(),
                lastSeenTimeZoneId = any(),
                hideMedicationDetails = any(),
                widgetContentScale = any(),
                widgetBackgroundAlpha = any(),
                widgetDarkModeOption = any(),
                groupNameCounter = any(),
                firstDayOfWeekOption = any(),
                stockNudgeEnabled = capture(capturedValues),
            )
        } just Runs

        val snapshot = emptySnapshot().let { base ->
            base.copy(settings = base.settings.copy(stockNudgeEnabled = false))
        }
        service.restoreBackupBytes(
            encryptedBytes = backupCrypto.encryptSnapshotJson(
                json = BackupSnapshotJsonCodec.encode(snapshot),
                password = "password".toCharArray(),
            ),
            password = "password",
        )

        assertEquals(listOf(false), capturedValues)
    }

    private fun snapshotWithWidgetContentScale(scale: Float): BackupSnapshot {
        val base = emptySnapshot()
        return base.copy(settings = base.settings.copy(widgetContentScale = scale))
    }

    private fun backupExportServiceFor(
        medicines: List<Medicine>,
        medicationLogs: List<MedicationLogEntry>,
    ): BackupExportService {
        val exportSettingsRepository: SettingsRepository = mockk()
        val exportUserProfileRepository: UserProfileRepository = mockk()
        val exportMedicineRepository: MedicineRepository = mockk()
        val exportMedicationGroupRepository: MedicationGroupRepository = mockk()
        val exportMedicationLogRepository: MedicationLogRepository = mockk()
        val exportBloodTestRepository: BloodTestRepository = mockk()

        every { exportSettingsRepository.onboardingCompleted } returns flowOf(true)
        every { exportSettingsRepository.stockNudgeEnabledFlow } returns flowOf(true)
        coEvery { exportSettingsRepository.getCurrentSettings() } returns SettingsState()
        coEvery { exportUserProfileRepository.getCurrentProfile() } returns UserProfile()
        coEvery { exportMedicineRepository.getAll() } returns medicines
        coEvery { exportMedicationGroupRepository.getGroups() } returns emptyList()
        coEvery { exportMedicationLogRepository.getEntries() } returns medicationLogs
        coEvery { exportBloodTestRepository.getCustomAnalytes() } returns emptyList()
        coEvery { exportBloodTestRepository.getPanels() } returns emptyList()

        return BackupExportService(
            context = context,
            settingsRepository = exportSettingsRepository,
            userProfileRepository = exportUserProfileRepository,
            medicineRepository = exportMedicineRepository,
            medicationGroupRepository = exportMedicationGroupRepository,
            medicationLogRepository = exportMedicationLogRepository,
            bloodTestRepository = exportBloodTestRepository,
            backupCrypto = backupCrypto,
        )
    }

    private fun emptySnapshot(): BackupSnapshot {
        return BackupSnapshot(
            exportedAtEpochMillis = 1_777_777_777_000L,
            app = BackupAppSnapshot(packageName = "com.mkx.hrttracker"),
            settings = BackupSettingsSnapshot(
                darkModeOption = "FOLLOW_SYSTEM",
                adaptiveColorEnabled = true,
                remindersEnabled = false,
                appLockGracePeriodOption = "ONE_MINUTE",
                hideScreenContentEnabled = false,
                onboardingCompleted = true,
                appLanguageOption = "ENGLISH",
                homeE2DisplayUnit = BloodUnitKey.PG_ML.storageValue,
                calibrationDefaultUnits = emptyMap(),
            ),
            userProfile = BackupUserProfileSnapshot(
                weightKg = null,
                weightOriginalValue = null,
                weightOriginalUnit = "KILOGRAMS",
            ),
            medicines = emptyList(),
            medicationGroups = emptyList(),
            medicationLogs = emptyList(),
            customBloodAnalytes = emptyList(),
            bloodTestPanels = emptyList(),
        )
    }

    private fun stockSnapshot(
        medicineUuid: UUID,
        logUuid: UUID,
    ): BackupSnapshot {
        val preparation = MedicinePreparation.InjectionMultiUseVial(
            concentrationMgPerMl = 40.0,
            vialVolumeMl = 1.0,
        )
        return emptySnapshot().copy(
            medicines = listOf(
                BackupMedicineSnapshot(
                    uuid = medicineUuid.toString(),
                    selectionKind = "CATALOG",
                    medicationKey = "ESTRADIOL",
                    customMedicationName = null,
                    customMedicationNameNormalized = null,
                    category = "ESTRADIOL",
                    preparationType = "INJECTION_MULTI_USE_VIAL",
                    strengthMgPerTablet = null,
                    strengthMgPerVial = null,
                    concentrationMgPerMl = 40.0,
                    vialVolumeMl = 1.0,
                    concentrationPercent = null,
                    sachetWeightGrams = null,
                    containerWeightGrams = null,
                    patchTotalMg = null,
                    patchReleaseRateMcgPerDay = null,
                    displayName = null,
                    identityKey = MedicineIdentityKey.catalog(MedicationKey.ESTRADIOL, preparation),
                    createdAtEpochMillis = 100L,
                    updatedAtEpochMillis = 100L,
                    archivedAtEpochMillis = null,
                    stock = BackupMedicineStockSnapshot(
                        trackingEnabled = true,
                        unitsRemaining = 30.0,
                        unitsLastTotal = 45.0,
                        openContainerAmount = null,
                        warnAtDaysRemaining = 10,
                        stockGeneration = 3L,
                    ),
                )
            ),
            medicationLogs = listOf(
                BackupMedicationLogSnapshot(
                    uuid = logUuid.toString(),
                    category = "ESTRADIOL",
                    medicineUuid = medicineUuid.toString(),
                    applicationType = "INJECTION",
                    doseInstructionKind = "VOLUME_ML",
                    tabletFractionNumerator = null,
                    tabletFractionDenominator = null,
                    doseVolumeMl = 0.05,
                    doseWeightGrams = null,
                    gelApplicationArea = "DEFAULT",
                    equivalentE2Mg = 2.0,
                    doseAmountDelta = 0.1,
                    sourceGroupUuid = null,
                    appliedAtEpochMillis = 200L,
                    appliedAtTimeZoneId = "Asia/Tokyo",
                    scheduledForIso = null,
                    count = 1,
                )
            ),
        )
    }
}
