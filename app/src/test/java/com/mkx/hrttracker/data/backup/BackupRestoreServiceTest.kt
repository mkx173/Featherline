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
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
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
    fun restoreBackupBytes_roundTripsStockFieldsIntoInsertedEntities() = runTest {
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
        assertEquals(0.5, restoredLog.stockDeductionUnits!!, 1e-9)
        assertEquals(3L, restoredLog.stockGeneration)
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
        val preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0)
        return emptySnapshot().copy(
            medicines = listOf(
                BackupMedicineSnapshot(
                    uuid = medicineUuid.toString(),
                    selectionKind = "CATALOG",
                    medicationKey = "ESTRADIOL",
                    customMedicationName = null,
                    customMedicationNameNormalized = null,
                    category = "ESTRADIOL",
                    preparationType = "PILL",
                    strengthMgPerTablet = 2.0,
                    strengthMgPerVial = null,
                    concentrationMgPerMl = null,
                    vialVolumeMl = null,
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
                    applicationType = "ORAL",
                    doseInstructionKind = "TABLET_FRACTION",
                    tabletFractionNumerator = 1,
                    tabletFractionDenominator = 1,
                    doseVolumeMl = null,
                    doseWeightGrams = null,
                    gelApplicationArea = "DEFAULT",
                    equivalentE2Mg = 2.0,
                    sourceGroupUuid = null,
                    appliedAtEpochMillis = 200L,
                    appliedAtTimeZoneId = "Asia/Tokyo",
                    scheduledForIso = null,
                    count = 1,
                    stockDeductionUnits = 0.5,
                    stockGeneration = 3L,
                )
            ),
        )
    }
}
