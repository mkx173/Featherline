package com.mkx.hrttracker.data.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.mkx.hrttracker.data.local.BloodTestDao
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.MedicationGroupDao
import com.mkx.hrttracker.data.local.MedicationLogDao
import com.mkx.hrttracker.data.local.UserProfileDao
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.reminder.MedicationReminderSnoozeScheduler
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.fail

class BackupRestoreServiceTest {
    private val context: Context = mockk(relaxed = true)
    private val contentResolver: ContentResolver = mockk(relaxed = true)
    private val databaseHolder: DatabaseHolder = mockk()
    private val database: HrtTrackerDatabase = mockk()
    private val medicationLogDao: MedicationLogDao = mockk(relaxed = true)
    private val medicationGroupDao: MedicationGroupDao = mockk(relaxed = true)
    private val bloodTestDao: BloodTestDao = mockk(relaxed = true)
    private val userProfileDao: UserProfileDao = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val homeSnapshotRepository: HomeSnapshotRepository = mockk()
    private val medicationReminderScheduler: MedicationReminderScheduler = mockk()
    private val medicationReminderSnoozeScheduler: MedicationReminderSnoozeScheduler = mockk()

    private lateinit var backupCrypto: BackupCrypto
    private lateinit var service: BackupRestoreService

    @Before
    fun setUp() {
        every { context.packageName } returns "com.mkx.hrttracker"
        every { context.contentResolver } returns contentResolver
        every { database.medicationLogDao() } returns medicationLogDao
        every { database.medicationGroupDao() } returns medicationGroupDao
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
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
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
            medicationGroups = emptyList(),
            medicationLogs = emptyList(),
            customBloodAnalytes = emptyList(),
            bloodTestPanels = emptyList(),
        )
    }
}
