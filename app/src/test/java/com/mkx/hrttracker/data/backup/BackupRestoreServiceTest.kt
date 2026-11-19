package com.mkx.hrttracker.data.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.mkx.hrttracker.data.local.BloodTestDao
import com.mkx.hrttracker.data.local.BloodTestPanelEntity
import com.mkx.hrttracker.data.local.BloodTestResultEntity
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.DatabaseKeyUnavailableException
import com.mkx.hrttracker.data.local.E2CalibrationMetadataEntity
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.JournalDao
import com.mkx.hrttracker.data.local.MedicationGroupDao
import com.mkx.hrttracker.data.local.MedicationLogDao
import com.mkx.hrttracker.data.local.MedicationLogEntryEntity
import com.mkx.hrttracker.data.local.MedicineDao
import com.mkx.hrttracker.data.local.PkCalibrationDao
import com.mkx.hrttracker.data.local.MedicineEntity
import com.mkx.hrttracker.data.local.NoteEntity
import com.mkx.hrttracker.data.local.TrackedDateEntity
import com.mkx.hrttracker.data.local.UserProfileDao
import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.JournalRepository
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.data.repository.PkCalibrationStorageRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.UserProfileRepository
import com.mkx.hrttracker.healthconnect.HealthConnectCapabilityReconciler
import com.mkx.hrttracker.model.bloodtest.AllowedAnalyteUnit
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestCatalog
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.home.HomeCardLayout
import com.mkx.hrttracker.model.home.HomeCardType
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.testCustomMedicine
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.settings.AppLanguageOption
import com.mkx.hrttracker.model.settings.AppLockGracePeriodOption
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.model.settings.FirstDayOfWeekOption
import com.mkx.hrttracker.model.settings.ReminderRepeats
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.reminder.ReminderCapabilityReconciler
import com.mkx.hrttracker.ui.settings.BackupRestoreEvent
import com.mkx.hrttracker.ui.settings.messageRes
import com.mkx.hrttracker.util.appLanguageLocale
import com.mkx.hrttracker.widget.WidgetAppearance
import com.mkx.hrttracker.widget.WidgetAppearanceCodec
import com.mkx.hrttracker.widget.WidgetAppearanceRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.SequenceInputStream
import java.time.Instant
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class BackupRestoreServiceTest {
    private val context: Context = mockk(relaxed = true)
    private val contentResolver: ContentResolver = mockk(relaxed = true)
    private val databaseHolder: DatabaseHolder = mockk()
    private val database: HrtTrackerDatabase = mockk()
    private val medicationLogDao: MedicationLogDao = mockk(relaxed = true)
    private val medicationGroupDao: MedicationGroupDao = mockk(relaxed = true)
    private val medicineDao: MedicineDao = mockk(relaxed = true)
    private val bloodTestDao: BloodTestDao = mockk(relaxed = true)
    private val pkCalibrationDao: PkCalibrationDao = mockk(relaxed = true)
    private val userProfileDao: UserProfileDao = mockk(relaxed = true)
    private val journalDao: JournalDao = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val homeSnapshotRepository: HomeSnapshotRepository = mockk()
    private val medicationReminderScheduler: MedicationReminderScheduler = mockk()
    private val reconcileMark: MedicationReminderScheduler.ReconcileMark = mockk(relaxed = true)
    private val reminderCapabilityReconciler: ReminderCapabilityReconciler = mockk()
    private val healthConnectCapabilityReconciler: HealthConnectCapabilityReconciler = mockk(relaxed = true)
    private val widgetAppearanceRepository: WidgetAppearanceRepository = mockk()

    private lateinit var backupCrypto: BackupCrypto
    private lateinit var service: BackupRestoreService

    @get:Rule
    val tmpFolder = TemporaryFolder()
    private val settingsDispatcher = UnconfinedTestDispatcher()
    private val settingsScope = TestScope(settingsDispatcher)
    private val defaultLocale: Locale = Locale.getDefault()

    @Before
    fun setUp() {
        every { context.packageName } returns "com.mkx.hrttracker"
        every { context.contentResolver } returns contentResolver
        every { database.medicationLogDao() } returns medicationLogDao
        every { database.medicationGroupDao() } returns medicationGroupDao
        every { database.medicineDao() } returns medicineDao
        every { database.bloodTestDao() } returns bloodTestDao
        every { database.pkCalibrationDao() } returns pkCalibrationDao
        every { database.userProfileDao() } returns userProfileDao
        every { database.journalDao() } returns journalDao
        every { databaseHolder.get() } returns database
        coEvery { medicationLogDao.getEntries() } returns emptyList()
        coEvery { bloodTestDao.getPanels() } returns emptyList()
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
                any(), any(), any(),
                any(),
            )
        } just Runs
        coEvery { medicationReminderScheduler.markReconcilePending() } returns reconcileMark
        coEvery { reminderCapabilityReconciler.reconcile(any(), any(), any()) } just Runs
        coEvery { medicationReminderScheduler.clearSkips() } just Runs
        coEvery { widgetAppearanceRepository.setDefault(any()) } just Runs

        backupCrypto = BackupCrypto(TestBackupArgon2KeyDeriver())
        service = restoreServiceFor(settingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
        Locale.setDefault(defaultLocale)
    }

    private fun restoreServiceFor(settingsRepository: SettingsRepository) = BackupRestoreService(
        context = context,
        databaseHolder = databaseHolder,
        settingsRepository = settingsRepository,
        homeSnapshotRepository = homeSnapshotRepository,
        medicationReminderScheduler = medicationReminderScheduler,
        reminderCapabilityReconciler = reminderCapabilityReconciler,
        widgetAppearanceRepository = widgetAppearanceRepository,
        backupCrypto = backupCrypto,
        healthConnectCapabilityReconciler = healthConnectCapabilityReconciler,
    )

    // A device's real settings store, to read back what a restore wrote.
    private fun deviceSettingsRepository(name: String): SettingsRepository {
        // restoreSettings applies the language on Main.
        Dispatchers.setMain(settingsDispatcher)
        mockkStatic("com.mkx.hrttracker.util.LocalizationKt")
        every { appLanguageLocale() } returns Locale.ENGLISH
        return SettingsRepository(
            PreferenceDataStoreFactory.create(
                scope = settingsScope,
                produceFile = { tmpFolder.newFile("$name.preferences_pb") },
            ),
        )
    }

    @Test
    fun restoreBackupBytes_keepsTheDevicesLockGracePeriodAndHideContent() =
        runTest(settingsDispatcher) {
            val device = deviceSettingsRepository("device")
            device.setScreenLockProtectionEnabled(true)
            device.setHideScreenContentEnabled(true)
            device.setAppLockGracePeriodOption(AppLockGracePeriodOption.IMMEDIATELY)
            // Backups carry both companions of the lock; this one from a device that loosened them.
            val backup = emptySnapshot().let { base ->
                base.copy(
                    settings = base.settings.copy(
                        appLockGracePeriodOption = AppLockGracePeriodOption.THIRTY_MINUTES.name,
                        hideScreenContentEnabled = false,
                    ),
                )
            }

            restoreServiceFor(device).restoreBackupBytes(
                encryptedBytes = backupCrypto.encryptSnapshotJson(
                    json = BackupSnapshotJsonCodec.encode(backup),
                    password = "password".toCharArray(),
                ),
                password = "password",
            )

            val settings = device.getCurrentSettings()
            assertEquals("A restore turned the app lock off", true, settings.screenLockProtectionEnabled)
            assertEquals(
                "A restore turned hide screen content off",
                true,
                settings.hideScreenContentEnabled,
            )
            assertEquals(
                "A restore lengthened the app lock's grace period",
                AppLockGracePeriodOption.IMMEDIATELY,
                settings.appLockGracePeriodOption,
            )
        }

    // W8: a backup from before weight sync has no flag, and keeps the device's; one with a flag sets it.
    @Test
    fun restoreBackupBytes_keepsWeightSyncWithoutAFlag_andTakesOne() =
        runTest(settingsDispatcher) {
            val device = deviceSettingsRepository("device")
            device.setHealthConnectWeightSyncEnabled(true)
            val service = restoreServiceFor(device)
            fun restore(flag: Boolean?) = backupCrypto.encryptSnapshotJson(
                json = BackupSnapshotJsonCodec.encode(
                    emptySnapshot().let { it.copy(settings = it.settings.copy(healthConnectWeightSyncEnabled = flag)) },
                ),
                password = "password".toCharArray(),
            )

            service.restoreBackupBytes(restore(flag = null), password = "password")
            assertEquals(true, device.getCurrentSettings().healthConnectWeightSyncEnabled)

            service.restoreBackupBytes(restore(flag = false), password = "password")
            assertEquals(false, device.getCurrentSettings().healthConnectWeightSyncEnabled)
        }

    // As the weight-sync flag: a backup from before repeats keeps the device's; one with them sets them.
    @Test
    fun restoreBackupBytes_keepsTheReminderRepeatsWithoutThem_andTakesThem() =
        runTest(settingsDispatcher) {
            val device = deviceSettingsRepository("device")
            device.setReminderRepeats(ReminderRepeats(count = 1, intervalMinutes = 30))
            val service = restoreServiceFor(device)
            fun restore(count: Int?, interval: Int?) = backupCrypto.encryptSnapshotJson(
                json = BackupSnapshotJsonCodec.encode(
                    emptySnapshot().let {
                        it.copy(
                            settings = it.settings.copy(
                                reminderRepeatCount = count,
                                reminderRepeatIntervalMinutes = interval,
                            )
                        )
                    },
                ),
                password = "password".toCharArray(),
            )

            service.restoreBackupBytes(restore(count = null, interval = null), password = "password")
            assertEquals(ReminderRepeats(1, 30), device.getCurrentSettings().reminderRepeats)

            service.restoreBackupBytes(restore(count = 0, interval = 60), password = "password")
            assertEquals(ReminderRepeats(0, 60), device.getCurrentSettings().reminderRepeats)
            // The picker offers only valid ones; the store refuses any other.
            assertTrue(runCatching { device.setReminderRepeats(ReminderRepeats(5, 15)) }.isFailure)
        }

    @Test
    fun aStoredReminderRepeatOutOfRange_readsAsTheDefault_andAShortIntervalAs15() = runTest(settingsDispatcher) {
        Dispatchers.setMain(settingsDispatcher)
        mockkStatic("com.mkx.hrttracker.util.LocalizationKt")
        every { appLanguageLocale() } returns Locale.ENGLISH
        // A count past 4, or an interval off the wheel, reads as the default; one under 15 (the
        // wheel offered 5 and 10 before batch F) as 15, with its count.
        listOf(
            Triple(9, 15, ReminderRepeats()),
            Triple(2, 62, ReminderRepeats()),
            Triple(2, 10, ReminderRepeats(2, 15)),
            Triple(4, 5, ReminderRepeats(4, 15)),
        ).forEachIndexed { index, (count, interval, expected) ->
            val raw = PreferenceDataStoreFactory.create(
                scope = settingsScope,
                produceFile = { tmpFolder.newFile("raw$index.preferences_pb") },
            )
            raw.edit { preferences ->
                preferences[androidx.datastore.preferences.core.intPreferencesKey("reminder_repeat_count")] = count
                preferences[androidx.datastore.preferences.core.intPreferencesKey("reminder_repeat_interval_minutes")] = interval
            }

            assertEquals(expected, SettingsRepository(raw).getCurrentSettings().reminderRepeats)
        }
    }

    @Test
    fun aBackupsReminderRepeats_roundTrip_aShortIntervalReadsAs15_andOutOfRangeOrHalfOnesAreRejected() {
        fun validated(count: Int?, interval: Int?) = runCatching {
            emptySnapshot().let {
                it.copy(settings = it.settings.copy(reminderRepeatCount = count, reminderRepeatIntervalMinutes = interval))
            }.let { snapshot -> BackupSnapshotJsonCodec.decode(BackupSnapshotJsonCodec.encode(snapshot))!! }
                .toValidatedSnapshot(expectedPackageName = "com.mkx.hrttracker")
                .settings.reminderRepeats
        }

        assertEquals(ReminderRepeats(4, 15), validated(4, 15).getOrThrow())
        assertEquals(ReminderRepeats(1, 60), validated(1, 60).getOrThrow())
        // An earlier build's 5 or 10 minutes: the backup restores, with 15.
        assertEquals(ReminderRepeats(3, 15), validated(3, 10).getOrThrow())
        assertEquals(ReminderRepeats(4, 15), validated(4, 5).getOrThrow())
        assertEquals(null, validated(null, null).getOrThrow())
        assertTrue(validated(5, 15).isFailure)
        assertTrue(validated(3, 17).isFailure)
        assertTrue(validated(3, 65).isFailure)
        assertTrue(validated(3, null).isFailure)
        assertTrue(validated(null, 15).isFailure)
    }

    @Test
    fun restoreBackupBytes_restoresABackupWithAGraceOptionFromANewerVersion() =
        runTest(settingsDispatcher) {
            val device = deviceSettingsRepository("device")
            device.setScreenLockProtectionEnabled(true)
            device.setHideScreenContentEnabled(true)
            device.setAppLockGracePeriodOption(AppLockGracePeriodOption.IMMEDIATELY)
            val medicineUuid = UUID.fromString("00000000-0000-0000-0000-000000000260")
            val logUuid = UUID.fromString("00000000-0000-0000-0000-000000000261")
            // Restore ignores the grace period, so a name this version doesn't know is harmless.
            val backup = stockSnapshot(medicineUuid, logUuid).let { base ->
                base.copy(
                    settings = base.settings.copy(
                        darkModeOption = DarkModeOption.DARK.name,
                        appLockGracePeriodOption = "TWO_HOURS",
                        hideScreenContentEnabled = false,
                    ),
                )
            }
            val medicinesSlot = slot<List<MedicineEntity>>()

            val result = runCatching {
                restoreServiceFor(device).restoreBackupBytes(
                    encryptedBytes = backupCrypto.encryptSnapshotJson(
                        json = BackupSnapshotJsonCodec.encode(backup),
                        password = "password".toCharArray(),
                    ),
                    password = "password",
                )
            }

            assertEquals(
                "Rejected over a grace option restore ignores: ${result.exceptionOrNull()}",
                BackupRestoreOutcome.RESTORED,
                result.getOrNull(),
            )
            coVerify(exactly = 1) { medicineDao.insertAll(capture(medicinesSlot)) }
            assertEquals(medicineUuid.toString(), medicinesSlot.captured.single().uuid)
            val settings = device.getCurrentSettings()
            assertEquals(DarkModeOption.DARK, settings.darkModeOption)
            assertEquals(true, settings.screenLockProtectionEnabled)
            assertEquals(true, settings.hideScreenContentEnabled)
            assertEquals(AppLockGracePeriodOption.IMMEDIATELY, settings.appLockGracePeriodOption)
        }

    @Test
    fun restoreBackupBytes_stillRejectsAnUnknownChartWindowBeforeTouchingTheDatabase() = runTest {
        // Restore applies the chart window, so an unknown name is still incompatible.
        val backup = emptySnapshot().let { base ->
            base.copy(settings = base.settings.copy(homeE2ChartWindow = "TWO_HOURS"))
        }

        val result = runCatching {
            service.restoreBackupBytes(
                encryptedBytes = backupCrypto.encryptSnapshotJson(
                    json = BackupSnapshotJsonCodec.encode(backup),
                    password = "password".toCharArray(),
                ),
                password = "password",
            )
        }

        assertTrue(
            "rejected as invalid, not ${result.exceptionOrNull()}",
            result.exceptionOrNull() is IllegalArgumentException,
        )
        coVerify(exactly = 0) { databaseHolder.runTransaction(any()) }
    }

    @Test
    fun restoreBackupBytes_roundTripRestoresEverySettingButTheDevicesPrivacyOnes() =
        runTest(settingsDispatcher) {
            mockkStatic(AppCompatDelegate::class)
            every { AppCompatDelegate.setApplicationLocales(any()) } just Runs
            val e2 = BloodTestCatalog.definitionFor(BloodAnalyteKey.E2)
            val otherE2Unit = AllowedAnalyteUnit.of(
                BloodAnalyteKey.E2,
                e2.allowedUnits.first { unit -> unit != e2.canonicalUnit },
            )
            // Every setting off its default.
            val source = deviceSettingsRepository("source").apply {
                setDarkModeOption(DarkModeOption.DARK)
                setAdaptiveColorEnabled(false)
                setPureBlackEnabled(true)
                setHazeBlurEnabled(false)
                setClassicNavigationBarEnabled(true)
                setCalibrationDefaultUnit(otherE2Unit)
                setHomeE2DisplayUnit(otherE2Unit)
                setHomeE2ChartWindowOption(
                    HomeE2ChartWindowOption.entries.first { it != HomeE2ChartWindowOption.SEVEN_DAYS },
                )
                setRemindersEnabled(false)
                setHealthConnectWeightSyncEnabled(true)
                setShowArchivedGroupRecords(false)
                setHideReferenceRanges(true)
                setFirstDayOfWeekOption(FirstDayOfWeekOption.SUNDAY)
                setHideMedicationDetails(true)
                consumeNextGroupNameIndex()
                setOnboardingCompleted(true)
                setPkCalibrationIntroSeen(true)
                acknowledgeTimeZone("Asia/Tokyo")
                setStockNudgeEnabled(true)
                setStockNudgeEnabled(false)
                setHomeCardLayout(
                    order = HomeCardLayout().order.reversed(),
                    hidden = setOf(HomeCardType.LOW_STOCK),
                )
                setAppLanguageOption(AppLanguageOption.SIMPLIFIED_CHINESE)
                setHideScreenContentEnabled(false)
                setAppLockGracePeriodOption(AppLockGracePeriodOption.THIRTY_MINUTES)
            }
            val device = deviceSettingsRepository("device").apply {
                setScreenLockProtectionEnabled(true)
                setHideScreenContentEnabled(true)
                setAppLockGracePeriodOption(AppLockGracePeriodOption.IMMEDIATELY)
            }

            val encryptedBytes = backupExportServiceFor(
                medicines = emptyList(),
                medicationLogs = emptyList(),
                exportSettingsRepository = source,
            ).buildEncryptedBackupBytes(password = "password")
            restoreServiceFor(device).restoreBackupBytes(encryptedBytes, password = "password")

            assertEquals(
                "The restore should bring every setting but the lock's three, which stay the device's",
                source.getCurrentSettings().copy(
                    screenLockProtectionEnabled = true,
                    hideScreenContentEnabled = true,
                    appLockGracePeriodOption = AppLockGracePeriodOption.IMMEDIATELY,
                ),
                device.getCurrentSettings(),
            )
            assertEquals(true, device.onboardingCompleted.first())
            assertEquals(true, device.pkCalibrationIntroSeen.first())
            assertEquals(false, device.stockNudgeEnabledFlow.first())
            assertEquals(true, device.stockNudgeUserEnabledFlow.first())
            assertEquals(source.homeCardLayoutFlow.first(), device.homeCardLayoutFlow.first())
        }

    @Test
    fun loadEncryptedBackupBytes_rejectsAnotherKindOfFileFromItsFirstBytes() = runTest {
        val fileUri: Uri = mockk(relaxed = true)
        // A video picked by mistake: nothing in it is a backup header.
        val video = CountingInputStream(ByteArray(4 * 1024 * 1024) { 0x42 })
        every { contentResolver.openInputStream(fileUri) } returns video

        val result = runCatching { service.loadEncryptedBackupBytes(fileUri) }

        assertTrue(
            "read ${video.bytesRead} bytes of a file that isn't a backup before rejecting it",
            video.bytesRead <= BackupCrypto.MAGIC_BYTES.size,
        )
        assertTrue(
            "rejected as incompatible, not ${result.exceptionOrNull()}",
            result.exceptionOrNull() is IncompatibleBackupFileException,
        )
    }

    @Test
    fun loadEncryptedBackupBytes_reportsAFileTooLargeForTheHeapInsteadOfCrashing() = runTest {
        val fileUri: Uri = mockk(relaxed = true)
        // A backup header, then more than the heap holds.
        val heapExhausted = object : InputStream() {
            override fun read(): Int = throw OutOfMemoryError("Java heap space")
            override fun read(b: ByteArray, off: Int, len: Int): Int =
                throw OutOfMemoryError("Java heap space")
        }
        every { contentResolver.openInputStream(fileUri) } returns
                SequenceInputStream(ByteArrayInputStream(BackupCrypto.MAGIC_BYTES), heapExhausted)

        val result = runCatching { service.loadEncryptedBackupBytes(fileUri) }

        // The file may be fine: this device can't hold it (B5).
        assertEquals(R.string.settings_backup_restore_unable, messageFor(result))
    }

    @Test
    fun restoreBackupBytes_reportsABackupTooLargeForTheHeapAndWritesNothing() = runTest {
        // Decryption holds the most memory: the ciphertext copy, the plaintext, the JSON.
        val outOfHeap = BackupRestoreService(
            context = context,
            databaseHolder = databaseHolder,
            settingsRepository = settingsRepository,
            homeSnapshotRepository = homeSnapshotRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            reminderCapabilityReconciler = reminderCapabilityReconciler,
            widgetAppearanceRepository = widgetAppearanceRepository,
            backupCrypto = BackupCrypto { _, _, _ -> throw OutOfMemoryError("Java heap space") },
            healthConnectCapabilityReconciler = healthConnectCapabilityReconciler,
        )

        val result = runCatching {
            outOfHeap.restoreBackupBytes(emptySnapshotBytes(), password = "password")
        }

        assertEquals(R.string.settings_backup_restore_unable, messageFor(result))
        coVerify(exactly = 0) { homeSnapshotRepository.runHomeDataMutation<Unit>(any()) }
        coVerify(exactly = 0) { databaseHolder.runTransaction(any()) }
    }

    // B5: only a failed decryption blames the password.
    @Test
    fun restoreBackupBytes_aWrongPasswordSaysSo() = runTest {
        val result = runCatching { service.restoreBackupBytes(emptySnapshotBytes(), password = "wrong") }

        assertEquals(R.string.settings_backup_restore_failed, messageFor(result))
    }

    @Test
    fun restoreBackupBytes_aFailedWriteOrALostKeyIsAPlainFailure() = runTest {
        for (failure in listOf(
            IOException("No space left on device"),
            DatabaseKeyUnavailableException("The keystore key is missing."),
        )) {
            coEvery { databaseHolder.runTransaction(any()) } throws failure

            val result = runCatching { service.restoreBackupBytes(emptySnapshotBytes(), "password") }

            assertEquals("$failure", R.string.settings_backup_restore_unable, messageFor(result))
        }
    }

    // B6: the data is in, but the user is told to restore again, like any plain failure.
    @Test
    fun aFollowUpFailureIsAPlainFailure() {
        assertEquals(
            R.string.settings_backup_restore_unable,
            BackupRestoreEvent.FollowUpFailed.messageRes(),
        )
    }

    // The format check stays "not a compatible backup".
    @Test
    fun restoreBackupBytes_anotherAppsBackupIsIncompatible() = runTest {
        val bytes = backupCrypto.encryptSnapshotJson(
            json = BackupSnapshotJsonCodec.encode(
                emptySnapshot().copy(app = BackupAppSnapshot(packageName = "com.example.other")),
            ),
            password = "password".toCharArray(),
        )

        val result = runCatching { service.restoreBackupBytes(bytes, password = "password") }

        assertEquals(R.string.settings_backup_restore_incompatible_file, messageFor(result))
    }

    // B7: a newer snapshot inside a known container, and a newer container at the picker.
    @Test
    fun aBackupFromANewerVersionIsRefused_andSaysUnableToRestore() = runTest {
        val newerSnapshot = backupCrypto.encryptSnapshotJson(
            json = BackupSnapshotJsonCodec.encode(
                emptySnapshot().copy(snapshotVersion = CURRENT_BACKUP_SNAPSHOT_VERSION + 1),
            ),
            password = "password".toCharArray(),
        )
        val newerContainer = emptySnapshotBytes().also {
            it[BackupCrypto.MAGIC_BYTES.size] = (BackupCrypto.CURRENT_BACKUP_CONTAINER_VERSION + 1).toByte()
        }

        val restored = runCatching { service.restoreBackupBytes(newerSnapshot, "password") }
        val picked = runCatching { service.validateBackupBytes(newerContainer) }

        // Still refused as newer (the picker's check runs before the password dialog), and
        // said plainly: unable to restore.
        assertTrue(restored.exceptionOrNull() is NewerBackupVersionException)
        assertTrue(picked.exceptionOrNull() is NewerBackupVersionException)
        assertEquals(R.string.settings_backup_restore_unable, messageFor(restored))
        assertEquals(R.string.settings_backup_restore_unable, messageFor(picked))
        coVerify(exactly = 0) { databaseHolder.runTransaction(any()) }
    }

    // B7: new KDF, cipher and compression ids extend the set without a container bump, so an
    // id above the known ones is a newer app's.
    @Test
    fun aContainerIdAboveTheKnownOnesIsRefusedAsNewer() = runTest {
        // After the magic and the version byte: the KDF (2), cipher (1) and compression (1) ids.
        for ((offset, newer) in listOf(1 to 3, 2 to 2, 3 to 2)) {
            val bytes = emptySnapshotBytes().also { it[BackupCrypto.MAGIC_BYTES.size + offset] = newer.toByte() }

            val picked = runCatching { service.validateBackupBytes(bytes) }

            assertTrue("id at +$offset", picked.exceptionOrNull() is NewerBackupVersionException)
            assertEquals("id at +$offset", R.string.settings_backup_restore_unable, messageFor(picked))
        }
    }

    private fun messageFor(result: Result<*>): Int =
        BackupRestoreEvent.Failure(checkNotNull(result.exceptionOrNull())).messageRes()

    private class CountingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        val bytesRead: Int get() = pos
    }

    @Test
    fun restoreBackup_clearsTheSkips_andReconcilesWithARepost_afterRestoringTheSnapshot() = runTest {
        val encryptedBytes = backupCrypto.encryptSnapshotJson(
            json = BackupSnapshotJsonCodec.encode(emptySnapshot()),
            password = "password".toCharArray(),
        )

        service.restoreBackupBytes(encryptedBytes = encryptedBytes, password = "password")

        // The skips first, then a forced reconcile that reposts what's on screen (B3, B11).
        coVerifyOrder {
            medicationReminderScheduler.clearSkips()
            reminderCapabilityReconciler.reconcile("backup_restore", forceReschedule = true, repostShown = true)
        }
        // A restored sync flag syncs now, or turns off without access.
        verify(exactly = 1) { healthConnectCapabilityReconciler.requestReconcile("backup_restore", sync = true) }
    }

    @Test
    fun restoreBackupBytes_leavesShownRemindersToTheReconcileAfterTheCommit() = runTest {
        val outcome = service.restoreBackupBytes(emptySnapshotBytes(), password = "password")

        assertEquals(BackupRestoreOutcome.RESTORED, outcome)
        // Restore no longer dismisses reminders itself, which lost the due ones:
        // rescheduleAll, after the commit, keeps a shown reminder still due in
        // the restored data and drops or refreshes the rest.
        coVerifyOrder {
            databaseHolder.runTransaction(any())
            reminderCapabilityReconciler.reconcile(any(), any(), any())
        }
    }

    @Test
    fun restoreBackupBytes_keepsShownRemindersWhenTheTransactionFails() = runTest {
        coEvery { databaseHolder.runTransaction(any()) } throws IllegalStateException("disk full")

        val result = runCatching {
            service.restoreBackupBytes(emptySnapshotBytes(), password = "password")
        }

        assertTrue(result.exceptionOrNull() is IllegalStateException)
        // Nothing changed, so the reminders on screen are still right, and
        // nothing that could dismiss them ran.
        coVerify(exactly = 0) { reminderCapabilityReconciler.reconcile(any(), any(), any()) }
        // Rolled back: the next pass, or the next start's, settles the mark.
        verify(exactly = 1) { reconcileMark.committed() }
    }

    @Test
    fun restoreBackupBytes_marksRemindersForReconcileBeforeTheCommit() = runTest {
        service.restoreBackupBytes(emptySnapshotBytes(), password = "password")

        // Startup reconciles a pending mark if the process dies past the commit.
        coVerifyOrder {
            medicationReminderScheduler.markReconcilePending()
            databaseHolder.runTransaction(any())
            settingsRepository.restoreSettings(
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(),
                any(),
            )
            // Past the settings, which hold the master switch the pass reads.
            reconcileMark.committed()
            reminderCapabilityReconciler.reconcile(any(), any(), any())
        }
    }

    @Test
    fun restoreBackupBytes_reportsRestoredDataAndRunsTheFollowUpsWhenSettingsFailAfterTheCommit() =
        runTest {
            coEvery {
                settingsRepository.restoreSettings(
                    any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any(),
                    any(), any(), any(),
                    any(),
                )
            } throws IOException("datastore write failed")

            val result = runCatching {
                service.restoreBackupBytes(emptySnapshotBytes(), password = "password")
            }

            assertEquals(
                "The data was committed, so a settings failure must not report a failed restore",
                BackupRestoreOutcome.FOLLOW_UP_FAILED,
                result.getOrNull(),
            )
            coVerify(exactly = 1) { widgetAppearanceRepository.setDefault(any()) }
            coVerify(exactly = 1) { reminderCapabilityReconciler.reconcile(any(), any(), any()) }
            coVerify(exactly = 1) { medicationReminderScheduler.clearSkips() }
            verify(exactly = 1) {
                healthConnectCapabilityReconciler.requestReconcile("backup_restore", sync = true)
            }
        }

    @Test
    fun restoreBackupBytes_reportsAFailedFollowUpWhenTheRescheduleFails() = runTest {
        coEvery { reminderCapabilityReconciler.reconcile(any(), any(), any()) } throws IOException("alarm store")

        val outcome = service.restoreBackupBytes(emptySnapshotBytes(), password = "password")

        assertEquals(BackupRestoreOutcome.FOLLOW_UP_FAILED, outcome)
        coVerify(exactly = 1) { medicationReminderScheduler.clearSkips() }
    }

    @Test
    fun restoreBackupBytes_stillReconcilesWhenTheSkipClearFails() = runTest {
        coEvery { medicationReminderScheduler.clearSkips() } throws IOException("schedule store")

        val outcome = service.restoreBackupBytes(emptySnapshotBytes(), password = "password")

        assertEquals(BackupRestoreOutcome.FOLLOW_UP_FAILED, outcome)
        coVerify(exactly = 1) { reminderCapabilityReconciler.reconcile(any(), any(), any()) }
    }

    @Test
    fun restoreBackupBytes_runsTheFollowUpsWhenSettingsThrowACancellationException() = runTest {
        // Not a cancel of the restore, which can't reach the gate: the write's own failure.
        coEvery {
            settingsRepository.restoreSettings(
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(),
                any(),
            )
        } throws CancellationException("datastore scope")

        val outcome = service.restoreBackupBytes(emptySnapshotBytes(), password = "password")

        assertEquals(BackupRestoreOutcome.FOLLOW_UP_FAILED, outcome)
        coVerify(exactly = 1) { widgetAppearanceRepository.setDefault(any()) }
        coVerify(exactly = 1) { medicationReminderScheduler.clearSkips() }
        coVerify(exactly = 1) { reminderCapabilityReconciler.reconcile(any(), any(), any()) }
        verify(exactly = 1) {
            healthConnectCapabilityReconciler.requestReconcile("backup_restore", sync = true)
        }
    }

    @Test
    fun restoreBackupBytes_restoresWithoutTheReconcileMarkWhenItCantBeStored() = runTest {
        coEvery { medicationReminderScheduler.markReconcilePending() } throws IOException("schedule store")

        val outcome = service.restoreBackupBytes(emptySnapshotBytes(), password = "password")

        // A cold start runs a full pass anyway, so the mark is only a safety net.
        assertEquals(BackupRestoreOutcome.RESTORED, outcome)
        coVerify(exactly = 1) { databaseHolder.runTransaction(any()) }
        coVerify(exactly = 1) { reminderCapabilityReconciler.reconcile(any(), any(), any()) }
    }

    @Test
    fun restoreBackupBytes_runsTheRestWhenAFollowUpThrowsACancellationException() = runTest {
        // Not a cancel of the restore, which can't reach the follow-ups: the step's own failure.
        coEvery { widgetAppearanceRepository.setDefault(any()) } throws
            CancellationException("datastore scope")

        val outcome = service.restoreBackupBytes(emptySnapshotBytes(), password = "password")

        assertEquals(BackupRestoreOutcome.FOLLOW_UP_FAILED, outcome)
        coVerify(exactly = 1) { medicationReminderScheduler.clearSkips() }
        coVerify(exactly = 1) { reminderCapabilityReconciler.reconcile(any(), any(), any()) }
    }

    private fun emptySnapshotBytes(): ByteArray = backupCrypto.encryptSnapshotJson(
        json = BackupSnapshotJsonCodec.encode(emptySnapshot()),
        password = "password".toCharArray(),
    )

    @Test
    fun restoreBackup_keepsTheUnitOfAProfileWithoutAWeight() = runTest {
        val snapshot = emptySnapshot().copy(
            userProfile = BackupUserProfileSnapshot(
                weightKg = null,
                weightOriginalValue = null,
                weightOriginalUnit = "POUNDS",
            ),
        )
        val encryptedBytes = backupCrypto.encryptSnapshotJson(
            json = BackupSnapshotJsonCodec.encode(snapshot),
            password = "password".toCharArray(),
        )

        service.restoreBackupBytes(encryptedBytes = encryptedBytes, password = "password")

        // A later Health Connect reading then shows in lb, as after clearWeight.
        coVerify(exactly = 1) {
            userProfileDao.upsertProfile(
                match { it.weightKg == null && it.weightOriginalUnit == "POUNDS" }
            )
        }
    }

    @Test
    fun restoreBackup_fallsBackToKilograms_forAProfileWithoutAWeightAndAnUnknownUnit() = runTest {
        val snapshot = emptySnapshot().copy(
            userProfile = BackupUserProfileSnapshot(
                weightKg = null,
                weightOriginalValue = null,
                weightOriginalUnit = "stone",
            ),
        )
        val encryptedBytes = backupCrypto.encryptSnapshotJson(
            json = BackupSnapshotJsonCodec.encode(snapshot),
            password = "password".toCharArray(),
        )

        service.restoreBackupBytes(encryptedBytes = encryptedBytes, password = "password")

        coVerify(exactly = 1) {
            userProfileDao.upsertProfile(
                match { it.weightKg == null && it.weightOriginalUnit == "KILOGRAMS" }
            )
        }
    }

    @Test
    fun restoreBackup_restoresExclusionMetadataAfterResults() = runTest {
        val resultUuid = "00000000-0000-0000-0000-0000000008a1"
        val snapshot = emptySnapshot().copy(
            bloodTestPanels = listOf(
                calibrationMetadataPanel(
                    panelUuid = "00000000-0000-0000-0000-0000000008a0",
                    resultUuid = resultUuid,
                    calibrationDisposition = "EXCLUDED",
                )
            ),
        )
        val metadataSlot = slot<List<E2CalibrationMetadataEntity>>()

        service.restoreBackupBytes(
            encryptedBytes = backupCrypto.encryptSnapshotJson(
                BackupSnapshotJsonCodec.encode(snapshot),
                "password".toCharArray(),
            ),
            password = "password",
        )

        coVerifyOrder {
            pkCalibrationDao.deleteAllMetadata()
            bloodTestDao.insertResults(any())
            pkCalibrationDao.insertMetadata(capture(metadataSlot))
        }
        val restored = metadataSlot.captured.single()
        assertEquals(resultUuid, restored.resultUuid)
        assertEquals("EXCLUDED", restored.disposition)
        assertEquals(2_000L, restored.updatedAtEpochMillis)
    }

    // "ACCEPTED" was a pre-release disposition (the removed Keep-outlier
    // action); like AUTO it means "no choice" and restores as no row.
    @Test
    fun restoreBackup_legacyAcceptedRow_restoresAsNoRowInsteadOfAborting() = runTest {
        val resultUuid = "00000000-0000-0000-0000-0000000008b1"
        val snapshot = emptySnapshot().copy(
            bloodTestPanels = listOf(
                calibrationMetadataPanel(
                    panelUuid = "00000000-0000-0000-0000-0000000008b0",
                    resultUuid = resultUuid,
                    calibrationDisposition = "ACCEPTED",
                )
            ),
        )
        val metadataSlot = slot<List<E2CalibrationMetadataEntity>>()

        service.restoreBackupBytes(
            encryptedBytes = backupCrypto.encryptSnapshotJson(
                BackupSnapshotJsonCodec.encode(snapshot),
                "password".toCharArray(),
            ),
            password = "password",
        )

        coVerify(exactly = 0) { pkCalibrationDao.insertMetadata(any()) }
    }

    private fun calibrationMetadataPanel(
        panelUuid: String,
        resultUuid: String,
        calibrationDisposition: String,
    ): BackupBloodTestPanelSnapshot = BackupBloodTestPanelSnapshot(
        uuid = panelUuid,
        collectedAtInstantEpochMillis = 1_000L,
        collectedAtTimeZoneId = "UTC",
        notes = null,
        timeSinceLastEstradiolDoseMillis = null,
        timeSinceLastTestosteroneDoseMillis = null,
        createdAtEpochMillis = 1_000L,
        updatedAtEpochMillis = 1_000L,
        results = listOf(
            BackupBloodTestResultSnapshot(
                uuid = resultUuid,
                createdAtEpochMillis = 1_000L,
                displayOrder = 0,
                builtinAnalyteKey = BloodAnalyteKey.E2.storageValue,
                customAnalyteUuid = null,
                value = 100.0,
                unitSnapshot = BloodUnitKey.PG_ML.storageValue,
                canonicalValue = 100.0,
                calibrationDisposition = calibrationDisposition,
                calibrationMetadataUpdatedAtEpochMillis = 2_000L,
            )
        ),
    )

    @Test
    fun validateBackupBytes_accepts_supported_backup_container() = runTest {
        service.validateBackupBytes(emptySnapshotBytes())
    }

    @Test
    fun restoreBackupBytes_acceptsStablePackageBackupWhenInstalledPackageHasSuffix() = runTest {
        every { context.packageName } returns "com.mkx.hrttracker.debug"
        val encryptedBytes = backupCrypto.encryptSnapshotJson(
            json = BackupSnapshotJsonCodec.encode(emptySnapshot()),
            password = "password".toCharArray(),
        )

        service.restoreBackupBytes(encryptedBytes = encryptedBytes, password = "password")

        coVerify(exactly = 1) {
            settingsRepository.restoreSettings(
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(),
                any(),
            )
        }
    }

    @Test
    fun validateBackupBytes_rejects_non_backup_input_before_password_prompt() = runTest {
        val error = try {
            service.validateBackupBytes("not a backup".encodeToByteArray())
            fail("Expected validateBackupBytes to reject non-backup input.")
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
        assertEquals(29.0, restoredMedicine.stockUnitsRemaining!!, 1e-9)
        // Restore cracks one sealed container, so the gauge denominator drops with
        // it (45 -> 44), staying consistent with the write-side promote path.
        assertEquals(44.0, restoredMedicine.stockUnitsLastTotal!!, 1e-9)
        assertEquals(1.0, restoredMedicine.openContainerAmount!!, 1e-9)
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
    fun restoreBackupBytes_restoresJournalTrackedDatesAndNotesIntoInsertedEntities() = runTest {
        val firstTrackedDateUuid = UUID.fromString("00000000-0000-0000-0000-000000000770")
        val secondTrackedDateUuid = UUID.fromString("00000000-0000-0000-0000-000000000771")
        val noteUuid = UUID.fromString("00000000-0000-0000-0000-000000000772")
        val snapshot = emptySnapshot().copy(
            trackedDates = listOf(
                BackupTrackedDateSnapshot(
                    uuid = firstTrackedDateUuid.toString(),
                    name = "HRT start",
                    iconKey = "event",
                    dateIso = "2024-04-01",
                    paletteKey = "TEAL",
                    pinnedOrder = 0,
                    createdAtEpochMillis = 100L,
                    updatedAtEpochMillis = 110L,
                ),
                BackupTrackedDateSnapshot(
                    uuid = secondTrackedDateUuid.toString(),
                    name = "Surgery consult",
                    iconKey = "home_health",
                    dateIso = "2026-09-15",
                    paletteKey = null,
                    pinnedOrder = 1,
                    createdAtEpochMillis = 200L,
                    updatedAtEpochMillis = 220L,
                ),
            ),
            notes = listOf(
                BackupNoteSnapshot(
                    uuid = noteUuid.toString(),
                    dateIso = "2026-06-16",
                    text = "Felt steady today.",
                    createdAtEpochMillis = 300L,
                    updatedAtEpochMillis = 350L,
                )
            ),
        )
        val trackedDatesSlot = slot<List<TrackedDateEntity>>()
        val notesSlot = slot<List<NoteEntity>>()

        service.restoreBackupBytes(
            encryptedBytes = backupCrypto.encryptSnapshotJson(
                json = BackupSnapshotJsonCodec.encode(snapshot),
                password = "password".toCharArray(),
            ),
            password = "password",
        )

        coVerify(exactly = 1) { journalDao.deleteAllNotes() }
        coVerify(exactly = 1) { journalDao.deleteAllTrackedDates() }
        coVerify(exactly = 1) { journalDao.insertTrackedDates(capture(trackedDatesSlot)) }
        coVerify(exactly = 1) { journalDao.insertNotes(capture(notesSlot)) }

        val trackedDates = trackedDatesSlot.captured
        assertEquals(2, trackedDates.size)
        assertEquals(firstTrackedDateUuid.toString(), trackedDates[0].uuid)
        assertEquals("HRT start", trackedDates[0].name)
        assertEquals("event", trackedDates[0].iconKey)
        assertEquals("2024-04-01", trackedDates[0].dateIso)
        assertEquals("TEAL", trackedDates[0].paletteKey)
        assertEquals(0, trackedDates[0].pinnedOrder)
        assertEquals(100L, trackedDates[0].createdAtEpochMillis)
        assertEquals(110L, trackedDates[0].updatedAtEpochMillis)
        assertEquals(secondTrackedDateUuid.toString(), trackedDates[1].uuid)
        assertEquals("Surgery consult", trackedDates[1].name)
        assertEquals("home_health", trackedDates[1].iconKey)
        assertEquals("2026-09-15", trackedDates[1].dateIso)
        assertNull(trackedDates[1].paletteKey)
        assertEquals(1, trackedDates[1].pinnedOrder)
        assertEquals(200L, trackedDates[1].createdAtEpochMillis)
        assertEquals(220L, trackedDates[1].updatedAtEpochMillis)

        val restoredNote = notesSlot.captured.single()
        assertEquals(noteUuid.toString(), restoredNote.uuid)
        assertEquals("2026-06-16", restoredNote.dateIso)
        assertEquals("Felt steady today.", restoredNote.text)
        assertEquals(300L, restoredNote.createdAtEpochMillis)
        assertEquals(350L, restoredNote.updatedAtEpochMillis)
    }

    @Test
    fun restoreBackupBytes_trimsTrackedDateNameBeforeInsert() = runTest {
        val snapshot = emptySnapshot().copy(
            trackedDates = listOf(
                BackupTrackedDateSnapshot(
                    uuid = "00000000-0000-0000-0000-000000000782",
                    name = "  Trimmed anchor  ",
                    iconKey = "event",
                    dateIso = "2024-04-01",
                    paletteKey = null,
                    pinnedOrder = 0,
                    createdAtEpochMillis = 100L,
                    updatedAtEpochMillis = 110L,
                ),
            ),
        )
        val trackedDatesSlot = slot<List<TrackedDateEntity>>()

        service.restoreBackupBytes(
            encryptedBytes = backupCrypto.encryptSnapshotJson(
                json = BackupSnapshotJsonCodec.encode(snapshot),
                password = "password".toCharArray(),
            ),
            password = "password",
        )

        coVerify(exactly = 1) { journalDao.insertTrackedDates(capture(trackedDatesSlot)) }
        assertEquals("Trimmed anchor", trackedDatesSlot.captured.single().name)
    }

    @Test
    fun restoreBackupBytes_rejectsDuplicateNoteDateBeforeJournalMutation() = runTest {
        val snapshot = emptySnapshot().copy(
            notes = listOf(
                BackupNoteSnapshot(
                    uuid = "00000000-0000-0000-0000-000000000773",
                    dateIso = "2026-06-16",
                    text = "Morning note.",
                    createdAtEpochMillis = 100L,
                    updatedAtEpochMillis = 110L,
                ),
                BackupNoteSnapshot(
                    uuid = "00000000-0000-0000-0000-000000000774",
                    dateIso = "2026-06-16",
                    text = "Evening note.",
                    createdAtEpochMillis = 120L,
                    updatedAtEpochMillis = 130L,
                ),
            ),
        )

        val error = restoreBackupBytesFails(snapshot)

        assertTrue(error.message.orEmpty().contains("Duplicate note date 2026-06-16"))
        verifyNoJournalMutation()
    }

    @Test
    fun restoreBackupBytes_rejectsDuplicateNoteUuidBeforeJournalMutation() = runTest {
        val snapshot = emptySnapshot().copy(
            notes = listOf(
                BackupNoteSnapshot(
                    uuid = "00000000-0000-0000-0000-000000000775",
                    dateIso = "2026-06-16",
                    text = "First note.",
                    createdAtEpochMillis = 100L,
                    updatedAtEpochMillis = 110L,
                ),
                BackupNoteSnapshot(
                    uuid = "00000000-0000-0000-0000-000000000775",
                    dateIso = "2026-06-17",
                    text = "Second note.",
                    createdAtEpochMillis = 120L,
                    updatedAtEpochMillis = 130L,
                ),
            ),
        )

        val error = restoreBackupBytesFails(snapshot)

        assertTrue(
            error.message.orEmpty()
                .contains("Duplicate note UUID 00000000-0000-0000-0000-000000000775")
        )
        verifyNoJournalMutation()
    }

    @Test
    fun restoreBackupBytes_rejectsDuplicateTrackedDateUuidBeforeJournalMutation() = runTest {
        val snapshot = emptySnapshot().copy(
            trackedDates = listOf(
                BackupTrackedDateSnapshot(
                    uuid = "00000000-0000-0000-0000-000000000776",
                    name = "First anchor",
                    iconKey = "event",
                    dateIso = "2024-04-01",
                    paletteKey = null,
                    pinnedOrder = 0,
                    createdAtEpochMillis = 100L,
                    updatedAtEpochMillis = 110L,
                ),
                BackupTrackedDateSnapshot(
                    uuid = "00000000-0000-0000-0000-000000000776",
                    name = "Second anchor",
                    iconKey = "bookmark",
                    dateIso = "2025-04-01",
                    paletteKey = "ROSE",
                    pinnedOrder = 1,
                    createdAtEpochMillis = 120L,
                    updatedAtEpochMillis = 130L,
                ),
            ),
        )

        val error = restoreBackupBytesFails(snapshot)

        assertTrue(
            error.message.orEmpty()
                .contains("Duplicate tracked date UUID 00000000-0000-0000-0000-000000000776")
        )
        verifyNoJournalMutation()
    }

    @Test
    fun restoreBackupBytes_toleratesDuplicatePinnedOrder() = runTest {
        // pinnedOrder is a sort key, not a uniqueness invariant. A reorder/unpin can
        // legitimately leave two rows sharing an order, so a backup carrying such a
        // pair must restore intact (read-time ordering tie-breaks) rather than abort
        // the whole import.
        val firstUuid = "00000000-0000-0000-0000-000000000783"
        val secondUuid = "00000000-0000-0000-0000-000000000784"
        val snapshot = emptySnapshot().copy(
            trackedDates = listOf(
                BackupTrackedDateSnapshot(
                    uuid = firstUuid,
                    name = "First pinned anchor",
                    iconKey = "event",
                    dateIso = "2024-04-01",
                    paletteKey = null,
                    pinnedOrder = 0,
                    createdAtEpochMillis = 100L,
                    updatedAtEpochMillis = 110L,
                ),
                BackupTrackedDateSnapshot(
                    uuid = secondUuid,
                    name = "Second pinned anchor",
                    iconKey = "bookmark",
                    dateIso = "2025-04-01",
                    paletteKey = "ROSE",
                    pinnedOrder = 0,
                    createdAtEpochMillis = 120L,
                    updatedAtEpochMillis = 130L,
                ),
            ),
        )
        val trackedDatesSlot = slot<List<TrackedDateEntity>>()

        service.restoreBackupBytes(
            encryptedBytes = backupCrypto.encryptSnapshotJson(
                json = BackupSnapshotJsonCodec.encode(snapshot),
                password = "password".toCharArray(),
            ),
            password = "password",
        )

        coVerify(exactly = 1) { journalDao.insertTrackedDates(capture(trackedDatesSlot)) }
        val restored = trackedDatesSlot.captured
        assertEquals(2, restored.size)
        assertEquals(setOf(firstUuid, secondUuid), restored.map { it.uuid }.toSet())
        assertTrue(restored.all { it.pinnedOrder == 0 })
    }

    @Test
    fun restoreBackupBytes_rejectsNegativePinnedOrderBeforeJournalMutation() = runTest {
        val snapshot = emptySnapshot().copy(
            trackedDates = listOf(
                BackupTrackedDateSnapshot(
                    uuid = "00000000-0000-0000-0000-000000000787",
                    name = "Negative order anchor",
                    iconKey = "event",
                    dateIso = "2024-04-01",
                    paletteKey = null,
                    pinnedOrder = -1,
                    createdAtEpochMillis = 100L,
                    updatedAtEpochMillis = 110L,
                ),
            ),
        )

        val error = restoreBackupBytesFails(snapshot)

        assertTrue(error.message.orEmpty().contains("pinnedOrder must not be negative"))
        verifyNoJournalMutation()
    }

    @Test
    fun restoreBackupBytes_rejectsBlankTrackedDateNameBeforeJournalMutation() = runTest {
        val snapshot = emptySnapshot().copy(
            trackedDates = listOf(
                BackupTrackedDateSnapshot(
                    uuid = "00000000-0000-0000-0000-000000000785",
                    name = "  ",
                    iconKey = "event",
                    dateIso = "2024-04-01",
                    paletteKey = null,
                    pinnedOrder = 0,
                    createdAtEpochMillis = 100L,
                    updatedAtEpochMillis = 110L,
                ),
            ),
        )

        val error = restoreBackupBytesFails(snapshot)

        assertTrue(error.message.orEmpty().contains("Tracked date name must not be blank."))
        verifyNoJournalMutation()
    }

    @Test
    fun restoreBackupBytes_rejectsInvalidTrackedDateIsoBeforeJournalMutation() = runTest {
        val snapshot = emptySnapshot().copy(
            trackedDates = listOf(
                BackupTrackedDateSnapshot(
                    uuid = "00000000-0000-0000-0000-000000000778",
                    name = "Bad anchor date",
                    iconKey = "event",
                    dateIso = "not-a-date",
                    paletteKey = null,
                    pinnedOrder = 0,
                    createdAtEpochMillis = 100L,
                    updatedAtEpochMillis = 110L,
                ),
            ),
        )

        val error = restoreBackupBytesFails(snapshot)

        assertTrue(error.message.orEmpty().contains("Invalid tracked date dateIso."))
        verifyNoJournalMutation()
    }

    @Test
    fun restoreBackupBytes_trimsNoteTextBeforeInsert() = runTest {
        val snapshot = emptySnapshot().copy(
            notes = listOf(
                BackupNoteSnapshot(
                    uuid = "00000000-0000-0000-0000-000000000788",
                    dateIso = "2026-06-16",
                    text = "  Felt steady today.  ",
                    createdAtEpochMillis = 100L,
                    updatedAtEpochMillis = 110L,
                ),
            ),
        )
        val notesSlot = slot<List<NoteEntity>>()

        service.restoreBackupBytes(
            encryptedBytes = backupCrypto.encryptSnapshotJson(
                json = BackupSnapshotJsonCodec.encode(snapshot),
                password = "password".toCharArray(),
            ),
            password = "password",
        )

        coVerify(exactly = 1) { journalDao.insertNotes(capture(notesSlot)) }
        assertEquals("Felt steady today.", notesSlot.captured.single().text)
    }

    @Test
    fun restoreBackupBytes_rejectsBlankNoteTextBeforeJournalMutation() = runTest {
        val snapshot = emptySnapshot().copy(
            notes = listOf(
                BackupNoteSnapshot(
                    uuid = "00000000-0000-0000-0000-000000000789",
                    dateIso = "2026-06-16",
                    text = "   ",
                    createdAtEpochMillis = 100L,
                    updatedAtEpochMillis = 110L,
                ),
            ),
        )

        val error = restoreBackupBytesFails(snapshot)

        assertTrue(error.message.orEmpty().contains("text must not be blank"))
        verifyNoJournalMutation()
    }

    @Test
    fun restoreBackupBytes_rejectsInvalidNoteDateIsoBeforeJournalMutation() = runTest {
        val snapshot = emptySnapshot().copy(
            notes = listOf(
                BackupNoteSnapshot(
                    uuid = "00000000-0000-0000-0000-000000000779",
                    dateIso = "not-a-date",
                    text = "Bad note date.",
                    createdAtEpochMillis = 100L,
                    updatedAtEpochMillis = 110L,
                ),
            ),
        )

        val error = restoreBackupBytesFails(snapshot)

        assertTrue(error.message.orEmpty().contains("Invalid note dateIso."))
        verifyNoJournalMutation()
    }

    @Test
    fun restoreBackupBytes_preservesUnknownTrackedDateIconPaletteAndHeroBackgroundKeys() = runTest {
        // A forward-compatible backup may carry an icon, palette, or hero
        // background key this build does not know yet. It must restore intact
        // (read-time mapping falls back gracefully) rather than failing the
        // whole import.
        val snapshot = emptySnapshot().copy(
            trackedDates = listOf(
                BackupTrackedDateSnapshot(
                    uuid = "00000000-0000-0000-0000-000000000786",
                    name = "Forward-compatible anchor",
                    iconKey = "syringe",
                    dateIso = "2024-04-01",
                    paletteKey = "AMBER",
                    heroBackgroundKey = "PROGRESS",
                    pinnedOrder = 0,
                    createdAtEpochMillis = 100L,
                    updatedAtEpochMillis = 110L,
                ),
            ),
        )
        val trackedDatesSlot = slot<List<TrackedDateEntity>>()

        service.restoreBackupBytes(
            encryptedBytes = backupCrypto.encryptSnapshotJson(
                json = BackupSnapshotJsonCodec.encode(snapshot),
                password = "password".toCharArray(),
            ),
            password = "password",
        )

        coVerify(exactly = 1) { journalDao.insertTrackedDates(capture(trackedDatesSlot)) }
        val restored = trackedDatesSlot.captured.single()
        assertEquals("syringe", restored.iconKey)
        assertEquals("AMBER", restored.paletteKey)
        assertEquals("PROGRESS", restored.heroBackgroundKey)
    }

    @Test
    fun restoreBackupBytes_restoresImportProvenanceIntoInsertedEntities() = runTest {
        val medicineUuid = UUID.fromString("00000000-0000-0000-0000-000000000750")
        val logUuid = UUID.fromString("00000000-0000-0000-0000-000000000751")
        val panelUuid = UUID.fromString("00000000-0000-0000-0000-000000000752")
        val resultUuid = UUID.fromString("00000000-0000-0000-0000-000000000753")
        val encryptedBytes = backupCrypto.encryptSnapshotJson(
            json = BackupSnapshotJsonCodec.encode(
                importedProvenanceSnapshot(
                    medicineUuid = medicineUuid,
                    logUuid = logUuid,
                    panelUuid = panelUuid,
                    resultUuid = resultUuid,
                )
            ),
            password = "password".toCharArray(),
        )
        val medicinesSlot = slot<List<MedicineEntity>>()
        val logsSlot = slot<List<MedicationLogEntryEntity>>()
        val panelsSlot = slot<List<BloodTestPanelEntity>>()
        val resultsSlot = slot<List<BloodTestResultEntity>>()

        service.restoreBackupBytes(encryptedBytes = encryptedBytes, password = "password")

        coVerify(exactly = 1) { medicineDao.insertAll(capture(medicinesSlot)) }
        coVerify(exactly = 1) { medicationLogDao.insertEntries(capture(logsSlot)) }
        coVerify(exactly = 1) { bloodTestDao.insertPanels(capture(panelsSlot)) }
        coVerify(exactly = 1) { bloodTestDao.insertResults(capture(resultsSlot)) }

        assertEquals(true, medicinesSlot.captured.single().importedFromExternalTracker)
        val restoredLog = logsSlot.captured.single()
        assertEquals("transmtf", restoredLog.importSourceApp)
        assertEquals("dose-750", restoredLog.importExternalId)
        val restoredPanel = panelsSlot.captured.single()
        assertEquals("oyama", restoredPanel.importSourceApp)
        assertEquals(750L, restoredPanel.importPanelKey)
        val restoredResult = resultsSlot.captured.single()
        assertEquals("oyama", restoredResult.importSourceApp)
        assertEquals("result-750", restoredResult.importExternalId)
    }

    @Test
    fun restoreBackupBytes_acceptsImportedAntiandrogenMedicineWithProvenance() = runTest {
        val medicineUuid = UUID.fromString("00000000-0000-0000-0000-000000000754")
        val logUuid = UUID.fromString("00000000-0000-0000-0000-000000000755")
        val encryptedBytes = backupCrypto.encryptSnapshotJson(
            json = BackupSnapshotJsonCodec.encode(
                importedAntiandrogenProvenanceSnapshot(
                    medicineUuid = medicineUuid,
                    logUuid = logUuid,
                )
            ),
            password = "password".toCharArray(),
        )
        val medicinesSlot = slot<List<MedicineEntity>>()
        val logsSlot = slot<List<MedicationLogEntryEntity>>()

        service.restoreBackupBytes(encryptedBytes = encryptedBytes, password = "password")

        coVerify(exactly = 1) { medicineDao.insertAll(capture(medicinesSlot)) }
        coVerify(exactly = 1) { medicationLogDao.insertEntries(capture(logsSlot)) }

        val restoredMedicine = medicinesSlot.captured.single()
        assertEquals(true, restoredMedicine.importedFromExternalTracker)
        assertEquals("SPIRONOLACTONE", restoredMedicine.medicationKey)
        assertEquals("ANTIANDROGEN", restoredMedicine.category)
        assertEquals("PILL", restoredMedicine.preparationType)
        assertEquals("E|transmtf|ORAL|SPIRONOLACTONE|mg:100", restoredMedicine.identityKey)

        val restoredLog = logsSlot.captured.single()
        assertEquals(logUuid.toString(), restoredLog.uuid)
        assertEquals(medicineUuid.toString(), restoredLog.medicineUuid)
        assertEquals("ANTIANDROGEN", restoredLog.category)
        assertEquals("ORAL", restoredLog.applicationType)
        assertEquals("transmtf", restoredLog.importSourceApp)
        assertEquals("spiro-dose-755", restoredLog.importExternalId)
        assertNull(restoredLog.equivalentE2Mg)
    }

    @Test
    fun restoreBackupBytes_withoutImportFields_defaultsInsertedEntitiesToNonImportedAndNullProvenance() =
        runTest {
            val medicineUuid = UUID.fromString("00000000-0000-0000-0000-000000000760")
            val logUuid = UUID.fromString("00000000-0000-0000-0000-000000000761")
            val panelUuid = UUID.fromString("00000000-0000-0000-0000-000000000762")
            val resultUuid = UUID.fromString("00000000-0000-0000-0000-000000000763")
            val json = BackupSnapshotJsonCodec.encode(
                nonImportedProvenanceSnapshot(
                    medicineUuid = medicineUuid,
                    logUuid = logUuid,
                    panelUuid = panelUuid,
                    resultUuid = resultUuid,
                )
            )
                .replace(",\"importedFromExternalTracker\":false", "")
                .replace(",\"importSourceApp\":null", "")
                .replace(",\"importExternalId\":null", "")
                .replace(",\"importPanelKey\":null", "")
            val encryptedBytes = backupCrypto.encryptSnapshotJson(
                json = json,
                password = "password".toCharArray(),
            )
            val medicinesSlot = slot<List<MedicineEntity>>()
            val logsSlot = slot<List<MedicationLogEntryEntity>>()
            val panelsSlot = slot<List<BloodTestPanelEntity>>()
            val resultsSlot = slot<List<BloodTestResultEntity>>()

            service.restoreBackupBytes(encryptedBytes = encryptedBytes, password = "password")

            coVerify(exactly = 1) { medicineDao.insertAll(capture(medicinesSlot)) }
            coVerify(exactly = 1) { medicationLogDao.insertEntries(capture(logsSlot)) }
            coVerify(exactly = 1) { bloodTestDao.insertPanels(capture(panelsSlot)) }
            coVerify(exactly = 1) { bloodTestDao.insertResults(capture(resultsSlot)) }

            assertEquals(false, medicinesSlot.captured.single().importedFromExternalTracker)
            assertNull(logsSlot.captured.single().importSourceApp)
            assertNull(logsSlot.captured.single().importExternalId)
            assertNull(panelsSlot.captured.single().importSourceApp)
            assertNull(panelsSlot.captured.single().importPanelKey)
            assertNull(resultsSlot.captured.single().importSourceApp)
            assertNull(resultsSlot.captured.single().importExternalId)
        }

    @Test
    fun restoreBackupBytes_appliesLegacyWidgetContentScaleAcrossFullUiRange() = runTest {
        // The widget appearance slider lets users choose any scale in 0.5..1.5,
        // and pre-customization backups carry that scale only in the legacy
        // widgetContentScale field (no encoded appearance string). Restore must
        // (a) accept that full domain — a narrower validator range would reject a
        // legitimately-exported backup as "incompatible" — and (b) apply the
        // legacy scale to the default appearance verbatim via setDefault.
        val appearancesSlot = mutableListOf<WidgetAppearance>()
        coEvery { widgetAppearanceRepository.setDefault(capture(appearancesSlot)) } just Runs

        listOf(0.5f, 1.5f).forEach { scale ->
            service.restoreBackupBytes(
                encryptedBytes = backupCrypto.encryptSnapshotJson(
                    json = BackupSnapshotJsonCodec.encode(snapshotWithWidgetContentScale(scale)),
                    password = "password".toCharArray(),
                ),
                password = "password",
            )
        }

        assertEquals(
            listOf(0.5f, 1.5f),
            appearancesSlot.map { it.contentScale },
        )
    }

    @Test
    fun restoreBackupBytes_appliesDecodedWidgetAppearanceIgnoringLegacyFields() = runTest {
        // When the encoded appearance string is present it is the source of
        // truth: setDefault must receive the DECODED appearance (hue, saturation,
        // balance included), and the legacy mirror fields must be ignored even when they
        // disagree — they exist only for older app versions reading this backup.
        val appearance = WidgetAppearance.Default.copy(
            seedHue = 200f,
            saturation = 0.7f,
            balance = 0.7f,
            contentScale = 1.3f,
            backgroundAlpha = 0.6f,
            darkMode = DarkModeOption.DARK,
        )
        val snapshot = emptySnapshot().let { base ->
            base.copy(
                settings = base.settings.copy(
                    widgetAppearance = WidgetAppearanceCodec.encode(appearance),
                    // Deliberately disagree with the encoded payload.
                    widgetContentScale = 1.0f,
                    widgetBackgroundAlpha = 1.0f,
                    widgetDarkModeOption = "FOLLOW_SYSTEM",
                )
            )
        }
        val appearanceSlot = slot<WidgetAppearance>()
        coEvery { widgetAppearanceRepository.setDefault(capture(appearanceSlot)) } just Runs

        service.restoreBackupBytes(
            encryptedBytes = backupCrypto.encryptSnapshotJson(
                json = BackupSnapshotJsonCodec.encode(snapshot),
                password = "password".toCharArray(),
            ),
            password = "password",
        )

        assertEquals(appearance, appearanceSlot.captured)
    }

    @Test
    fun restoreBackupBytes_appliesV1AppearancePayloadFromOldBackups() = runTest {
        // Backups written before Round 3 carry a v1 appearance string whose slot 2
        // was the now-removed backgroundHue. Restoring one must migrate transparently:
        // the v1 string decodes (slot-2 dropped, saturation anchored at the default, the
        // slot-3 vibrancy 0.4 anchor re-mapped onto balance 0.5 — the Round-6 bidirectional
        // anchor) and setDefault receives that migrated appearance rather than failing.
        val snapshot = emptySnapshot().let { base ->
            base.copy(
                settings = base.settings.copy(
                    widgetAppearance = "1|200.0|90.0|0.4|1.1|0.9|LIGHT",
                )
            )
        }
        val appearanceSlot = slot<WidgetAppearance>()
        coEvery { widgetAppearanceRepository.setDefault(capture(appearanceSlot)) } just Runs

        service.restoreBackupBytes(
            encryptedBytes = backupCrypto.encryptSnapshotJson(
                json = BackupSnapshotJsonCodec.encode(snapshot),
                password = "password".toCharArray(),
            ),
            password = "password",
        )

        val restored = appearanceSlot.captured
        assertEquals(200f, restored.seedHue!!, 1e-4f)
        assertEquals(WidgetAppearance.DEFAULT_SATURATION, restored.saturation, 0f)
        assertEquals(0.5f, restored.balance, 1e-4f)
        assertEquals(1.1f, restored.contentScale, 0f)
        assertEquals(0.9f, restored.backgroundAlpha, 0f)
        assertEquals(DarkModeOption.LIGHT, restored.darkMode)
    }

    @Test
    fun restoreBackupBytes_rejectsInvalidWidgetAppearancePayload() = runTest {
        // A malformed appearance string fails restore the same way any other
        // invalid field does — via IllegalArgumentException — rather than being
        // silently dropped.
        val snapshot = emptySnapshot().let { base ->
            base.copy(settings = base.settings.copy(widgetAppearance = "not-a-valid-payload"))
        }
        val encryptedBytes = backupCrypto.encryptSnapshotJson(
            json = BackupSnapshotJsonCodec.encode(snapshot),
            password = "password".toCharArray(),
        )

        val error = try {
            service.restoreBackupBytes(encryptedBytes = encryptedBytes, password = "password")
            fail("Expected restore to reject an invalid widget appearance payload.")
            null
        } catch (error: IllegalArgumentException) {
            error
        }

        assertNotNull(error)
        coVerify(exactly = 0) { widgetAppearanceRepository.setDefault(any()) }
    }

    @Test
    fun restoreBackupBytes_completesWhenSetDefaultThrowsIoException() = runTest {
        // Applying the default appearance is a post-commit, best-effort side
        // effect: by this point the data has already landed. A DataStore write
        // failure here must not surface as a failed restore — that would show a
        // "restore failed" toast even though the user's data was restored.
        coEvery {
            widgetAppearanceRepository.setDefault(any())
        } throws IOException("datastore write failed")

        service.restoreBackupBytes(
            encryptedBytes = backupCrypto.encryptSnapshotJson(
                json = BackupSnapshotJsonCodec.encode(emptySnapshot()),
                password = "password".toCharArray(),
            ),
            password = "password",
        )

        // The data-restore path still ran to completion despite the apply failure.
        coVerify(exactly = 1) {
            settingsRepository.restoreSettings(
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(),
                any(),
            )
        }
        coVerify(exactly = 1) { reminderCapabilityReconciler.reconcile(any(), any(), any()) }
    }

    @Test
    fun restoreBackupBytes_restoresPureBlackSetting() = runTest {
        val capturedValues = mutableListOf<Boolean>()
        coEvery {
            settingsRepository.restoreSettings(
                darkModeOption = any(),
                adaptiveColorEnabled = any(),
                pureBlackEnabled = capture(capturedValues),
                hazeBlurEnabled = any(),
                remindersEnabled = any(),
                showArchivedGroupRecords = any(),
                hideReferenceRanges = any(),
                onboardingCompleted = any(),
                appLanguageOption = any(),
                calibrationDefaultUnits = any(),
                homeE2DisplayUnit = any(),
                homeE2ChartWindowOption = any(),
                lastSeenTimeZoneId = any(),
                hideMedicationDetails = any(),
                groupNameCounter = any(),
                firstDayOfWeekOption = any(),
                stockNudgeEnabled = any(),
                stockNudgeUserEnabled = any(),
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
    fun restoreBackupBytes_restoresHazeBlurSetting() = runTest {
        val capturedValues = mutableListOf<Boolean>()
        coEvery {
            settingsRepository.restoreSettings(
                darkModeOption = any(),
                adaptiveColorEnabled = any(),
                pureBlackEnabled = any(),
                hazeBlurEnabled = capture(capturedValues),
                remindersEnabled = any(),
                showArchivedGroupRecords = any(),
                hideReferenceRanges = any(),
                onboardingCompleted = any(),
                appLanguageOption = any(),
                calibrationDefaultUnits = any(),
                homeE2DisplayUnit = any(),
                homeE2ChartWindowOption = any(),
                lastSeenTimeZoneId = any(),
                hideMedicationDetails = any(),
                groupNameCounter = any(),
                firstDayOfWeekOption = any(),
                stockNudgeEnabled = any(),
                stockNudgeUserEnabled = any(),
            )
        } just Runs

        val snapshot = emptySnapshot().let { base ->
            base.copy(settings = base.settings.copy(hazeBlurEnabled = false))
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

    @Test
    fun restoreBackupBytes_withoutHazeBlurEnabledField_restoresTrue() = runTest {
        val capturedValues = mutableListOf<Boolean>()
        coEvery {
            settingsRepository.restoreSettings(
                darkModeOption = any(),
                adaptiveColorEnabled = any(),
                pureBlackEnabled = any(),
                hazeBlurEnabled = capture(capturedValues),
                remindersEnabled = any(),
                showArchivedGroupRecords = any(),
                hideReferenceRanges = any(),
                onboardingCompleted = any(),
                appLanguageOption = any(),
                calibrationDefaultUnits = any(),
                homeE2DisplayUnit = any(),
                homeE2ChartWindowOption = any(),
                lastSeenTimeZoneId = any(),
                hideMedicationDetails = any(),
                groupNameCounter = any(),
                firstDayOfWeekOption = any(),
                stockNudgeEnabled = any(),
                stockNudgeUserEnabled = any(),
            )
        } just Runs
        // Backups written before the haze blur setting existed have no
        // hazeBlurEnabled field; restoring one must fall back to the default
        // (enabled) rather than failing or turning blur off.
        val json = BackupSnapshotJsonCodec.encode(emptySnapshot())
            .replace(",\"hazeBlurEnabled\":true", "")

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
    fun restoreBackupBytes_withoutStockNudgeEnabledField_restoresTrue() = runTest {
        val capturedValues = mutableListOf<Boolean>()
        coEvery {
            settingsRepository.restoreSettings(
                darkModeOption = any(),
                adaptiveColorEnabled = any(),
                pureBlackEnabled = any(),
                hazeBlurEnabled = any(),
                remindersEnabled = any(),
                showArchivedGroupRecords = any(),
                hideReferenceRanges = any(),
                onboardingCompleted = any(),
                appLanguageOption = any(),
                calibrationDefaultUnits = any(),
                homeE2DisplayUnit = any(),
                homeE2ChartWindowOption = any(),
                lastSeenTimeZoneId = any(),
                hideMedicationDetails = any(),
                groupNameCounter = any(),
                firstDayOfWeekOption = any(),
                stockNudgeEnabled = capture(capturedValues),
                stockNudgeUserEnabled = any(),
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
                hazeBlurEnabled = any(),
                remindersEnabled = any(),
                showArchivedGroupRecords = any(),
                hideReferenceRanges = any(),
                onboardingCompleted = any(),
                appLanguageOption = any(),
                calibrationDefaultUnits = any(),
                homeE2DisplayUnit = any(),
                homeE2ChartWindowOption = any(),
                lastSeenTimeZoneId = any(),
                hideMedicationDetails = any(),
                groupNameCounter = any(),
                firstDayOfWeekOption = any(),
                stockNudgeEnabled = capture(capturedValues),
                stockNudgeUserEnabled = any(),
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

    @Test
    fun restoreBackupBytes_normalizesLegacyEmptyOpenContainerStock() = runTest {
        val medicineUuid = UUID.fromString("00000000-0000-0000-0000-000000000740")
        val logUuid = UUID.fromString("00000000-0000-0000-0000-000000000741")
        val snapshot = stockSnapshot(medicineUuid, logUuid).let { base ->
            val medicine = base.medicines.single()
            base.copy(
                medicines = listOf(
                    medicine.copy(
                        stock = BackupMedicineStockSnapshot(
                            trackingEnabled = true,
                            unitsRemaining = 2.0,
                            unitsLastTotal = 3.0,
                            openContainerAmount = 0.0,
                            warnAtDaysRemaining = 10,
                            stockGeneration = 3L,
                        )
                    )
                )
            )
        }
        val encryptedBytes = backupCrypto.encryptSnapshotJson(
            json = BackupSnapshotJsonCodec.encode(snapshot),
            password = "password".toCharArray(),
        )
        val medicinesSlot = slot<List<MedicineEntity>>()

        service.restoreBackupBytes(encryptedBytes = encryptedBytes, password = "password")

        coVerify(exactly = 1) { medicineDao.insertAll(capture(medicinesSlot)) }
        val restoredMedicine = medicinesSlot.captured.single()
        assertEquals(1.0, restoredMedicine.stockUnitsRemaining!!, 1e-9)
        assertEquals(1.0, restoredMedicine.openContainerAmount!!, 1e-9)
        // Cracking the legacy container also lowers the gauge denominator (3 -> 2).
        assertEquals(2.0, restoredMedicine.stockUnitsLastTotal!!, 1e-9)
    }

    @Test
    fun restoreBackupBytes_appliesHomeCardLayout() = runTest {
        val base = emptySnapshot()
        val snapshot = base.copy(
            settings = base.settings.copy(
                homeCardOrder = listOf("TIMELINE", "E2_HERO", "E2_CHART", "ANTIANDROGEN", "LOW_STOCK"),
                homeCardHidden = listOf("E2_CHART"),
            )
        )
        val encryptedBytes = backupCrypto.encryptSnapshotJson(
            json = BackupSnapshotJsonCodec.encode(snapshot),
            password = "password".toCharArray(),
        )
        val layoutSlot = slot<HomeCardLayout>()

        service.restoreBackupBytes(encryptedBytes = encryptedBytes, password = "password")

        coVerify {
            settingsRepository.restoreSettings(
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(),
                capture(layoutSlot),
            )
        }
        assertEquals(HomeCardType.TIMELINE, layoutSlot.captured.order.first())
        assertEquals(setOf(HomeCardType.E2_CHART), layoutSlot.captured.hidden)
    }

    @Test
    fun restoreBackupBytes_legacyBackupWithoutLayoutAppliesDefault() = runTest {
        // emptySnapshot()'s settings omit nothing at the Kotlin level, but the JSON it
        // encodes carries the defaulted homeCard* fields; to simulate a legacy backup,
        // force the default values explicitly (decoder maps empty/absent -> default order).
        val base = emptySnapshot()
        val snapshot = base.copy(
            settings = base.settings.copy(
                homeCardOrder = emptyList(),
                homeCardHidden = emptyList(),
            )
        )
        val encryptedBytes = backupCrypto.encryptSnapshotJson(
            json = BackupSnapshotJsonCodec.encode(snapshot),
            password = "password".toCharArray(),
        )
        val layoutSlot = slot<HomeCardLayout>()

        service.restoreBackupBytes(encryptedBytes = encryptedBytes, password = "password")

        coVerify {
            settingsRepository.restoreSettings(
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(),
                capture(layoutSlot),
            )
        }
        assertEquals(HomeCardLayout(), layoutSlot.captured)
    }

    @Test
    fun restoreBackupBytes_normalizesUnknownAndDuplicateLayoutNames() = runTest {
        val base = emptySnapshot()
        val snapshot = base.copy(
            settings = base.settings.copy(
                homeCardOrder = listOf("E2_HERO", "E2_HERO", "GHOST", "LOW_STOCK"),
                homeCardHidden = listOf("LOW_STOCK", "NOPE"),
            )
        )
        val encryptedBytes = backupCrypto.encryptSnapshotJson(
            json = BackupSnapshotJsonCodec.encode(snapshot),
            password = "password".toCharArray(),
        )
        val layoutSlot = slot<HomeCardLayout>()

        service.restoreBackupBytes(encryptedBytes = encryptedBytes, password = "password")

        coVerify {
            settingsRepository.restoreSettings(
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                any(), any(), any(),
                capture(layoutSlot),
            )
        }
        assertEquals(
            listOf(
                HomeCardType.E2_HERO,
                HomeCardType.LOW_STOCK,
                HomeCardType.E2_CHART,
                HomeCardType.ANTIANDROGEN,
                HomeCardType.TIMELINE,
            ),
            layoutSlot.captured.order,
        )
        assertEquals(setOf(HomeCardType.LOW_STOCK), layoutSlot.captured.hidden)
    }

    private fun snapshotWithWidgetContentScale(scale: Float): BackupSnapshot {
        val base = emptySnapshot()
        return base.copy(settings = base.settings.copy(widgetContentScale = scale))
    }

    private suspend fun restoreBackupBytesFails(snapshot: BackupSnapshot): IllegalArgumentException {
        return try {
            service.restoreBackupBytes(
                encryptedBytes = backupCrypto.encryptSnapshotJson(
                    json = BackupSnapshotJsonCodec.encode(snapshot),
                    password = "password".toCharArray(),
                ),
                password = "password",
            )
            fail("Expected restore to reject the snapshot.")
            throw AssertionError("unreachable")
        } catch (error: IllegalArgumentException) {
            error
        }
    }

    private fun verifyNoJournalMutation() {
        coVerify(exactly = 0) { journalDao.deleteAllNotes() }
        coVerify(exactly = 0) { journalDao.deleteAllTrackedDates() }
        coVerify(exactly = 0) { journalDao.insertTrackedDates(any()) }
        coVerify(exactly = 0) { journalDao.insertNotes(any()) }
    }

    private fun backupExportServiceFor(
        medicines: List<Medicine>,
        medicationLogs: List<MedicationLogEntry>,
        exportSettingsRepository: SettingsRepository = mockk<SettingsRepository>().also { settings ->
            every { settings.onboardingCompleted } returns flowOf(true)
            every { settings.pkCalibrationIntroSeen } returns flowOf(true)
            every { settings.stockNudgeEnabledFlow } returns flowOf(true)
            every { settings.stockNudgeUserEnabledFlow } returns flowOf(false)
            every { settings.homeCardLayoutFlow } returns flowOf(HomeCardLayout())
            coEvery { settings.getCurrentSettings() } returns SettingsState()
        },
    ): BackupExportService {
        val exportUserProfileRepository: UserProfileRepository = mockk()
        val exportMedicineRepository: MedicineRepository = mockk()
        val exportMedicationGroupRepository: MedicationGroupRepository = mockk()
        val exportMedicationLogRepository: MedicationLogRepository = mockk()
        val exportBloodTestRepository: BloodTestRepository = mockk()
        val exportPkCalibrationStorageRepository: PkCalibrationStorageRepository = mockk()
        val exportWidgetAppearanceRepository: WidgetAppearanceRepository = mockk()
        val exportJournalRepository: JournalRepository = mockk()

        coEvery {
            exportWidgetAppearanceRepository.migrateFromLegacySettingsIfNeeded()
        } returns false
        coEvery {
            exportWidgetAppearanceRepository.currentEffective(null)
        } returns WidgetAppearance.Default
        coEvery { exportUserProfileRepository.getCurrentProfile() } returns UserProfile()
        coEvery { exportMedicineRepository.getAll() } returns medicines
        coEvery { exportMedicationGroupRepository.getGroups() } returns emptyList()
        coEvery { exportMedicationLogRepository.getEntries() } returns medicationLogs
        coEvery { exportBloodTestRepository.getCustomAnalytes() } returns emptyList()
        coEvery { exportBloodTestRepository.getPanels() } returns emptyList()
        coEvery { exportPkCalibrationStorageRepository.getAllMetadata() } returns emptyList()
        coEvery { exportJournalRepository.getTrackedDateEntities() } returns emptyList()
        coEvery { exportJournalRepository.getNoteEntities() } returns emptyList()

        val exportDatabaseHolder: DatabaseHolder = mockk()
        coEvery { exportDatabaseHolder.withTransaction<Any?>(any()) } coAnswers {
            firstArg<suspend (HrtTrackerDatabase) -> Any?>().invoke(database)
        }

        return BackupExportService(
            context = context,
            databaseHolder = exportDatabaseHolder,
            settingsRepository = exportSettingsRepository,
            userProfileRepository = exportUserProfileRepository,
            medicineRepository = exportMedicineRepository,
            medicationGroupRepository = exportMedicationGroupRepository,
            medicationLogRepository = exportMedicationLogRepository,
            bloodTestRepository = exportBloodTestRepository,
            pkCalibrationStorageRepository = exportPkCalibrationStorageRepository,
            widgetAppearanceRepository = exportWidgetAppearanceRepository,
            journalRepository = exportJournalRepository,
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

    private fun importedProvenanceSnapshot(
        medicineUuid: UUID,
        logUuid: UUID,
        panelUuid: UUID,
        resultUuid: UUID,
    ): BackupSnapshot {
        return emptySnapshot().copy(
            medicines = listOf(
                importedPillMedicineSnapshot(medicineUuid)
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
                    doseAmountDelta = null,
                    sourceGroupUuid = null,
                    appliedAtEpochMillis = 200L,
                    appliedAtTimeZoneId = "Asia/Tokyo",
                    scheduledForIso = null,
                    count = 1,
                    importSourceApp = "transmtf",
                    importExternalId = "dose-750",
                )
            ),
            bloodTestPanels = listOf(
                bloodPanelSnapshot(
                    panelUuid = panelUuid,
                    resultUuid = resultUuid,
                    importSourceApp = "oyama",
                    importPanelKey = 750L,
                    resultImportSourceApp = "oyama",
                    resultImportExternalId = "result-750",
                )
            ),
        )
    }

    private fun importedAntiandrogenProvenanceSnapshot(
        medicineUuid: UUID,
        logUuid: UUID,
    ): BackupSnapshot {
        return emptySnapshot().copy(
            medicines = listOf(
                BackupMedicineSnapshot(
                    uuid = medicineUuid.toString(),
                    selectionKind = "CATALOG",
                    medicationKey = "SPIRONOLACTONE",
                    customMedicationName = null,
                    customMedicationNameNormalized = null,
                    category = "ANTIANDROGEN",
                    preparationType = "PILL",
                    strengthMgPerTablet = 100.0,
                    strengthMgPerVial = null,
                    concentrationMgPerMl = null,
                    vialVolumeMl = null,
                    concentrationPercent = null,
                    sachetWeightGrams = null,
                    containerWeightGrams = null,
                    patchTotalMg = null,
                    patchReleaseRateMcgPerDay = null,
                    displayName = null,
                    identityKey = MedicineIdentityKey.external(
                        sourceApp = "transmtf",
                        applicationType = MedicationApplicationType.ORAL,
                        compound = "SPIRONOLACTONE",
                        doseKey = "mg:100",
                    ),
                    createdAtEpochMillis = 100L,
                    updatedAtEpochMillis = 100L,
                    archivedAtEpochMillis = null,
                    importedFromExternalTracker = true,
                )
            ),
            medicationLogs = listOf(
                BackupMedicationLogSnapshot(
                    uuid = logUuid.toString(),
                    category = "ANTIANDROGEN",
                    medicineUuid = medicineUuid.toString(),
                    applicationType = "ORAL",
                    doseInstructionKind = "TABLET_FRACTION",
                    tabletFractionNumerator = 1,
                    tabletFractionDenominator = 1,
                    doseVolumeMl = null,
                    doseWeightGrams = null,
                    gelApplicationArea = "DEFAULT",
                    equivalentE2Mg = null,
                    doseAmountDelta = null,
                    sourceGroupUuid = null,
                    appliedAtEpochMillis = 200L,
                    appliedAtTimeZoneId = "Asia/Tokyo",
                    scheduledForIso = null,
                    count = 1,
                    importSourceApp = "transmtf",
                    importExternalId = "spiro-dose-755",
                )
            ),
        )
    }

    private fun nonImportedProvenanceSnapshot(
        medicineUuid: UUID,
        logUuid: UUID,
        panelUuid: UUID,
        resultUuid: UUID,
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
                    importedFromExternalTracker = false,
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
                    doseAmountDelta = null,
                    sourceGroupUuid = null,
                    appliedAtEpochMillis = 200L,
                    appliedAtTimeZoneId = "Asia/Tokyo",
                    scheduledForIso = null,
                    count = 1,
                    importSourceApp = null,
                    importExternalId = null,
                )
            ),
            bloodTestPanels = listOf(
                bloodPanelSnapshot(
                    panelUuid = panelUuid,
                    resultUuid = resultUuid,
                    importSourceApp = null,
                    importPanelKey = null,
                    resultImportSourceApp = null,
                    resultImportExternalId = null,
                )
            ),
        )
    }

    private fun importedPillMedicineSnapshot(medicineUuid: UUID): BackupMedicineSnapshot {
        return BackupMedicineSnapshot(
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
            identityKey = MedicineIdentityKey.external(
                sourceApp = "transmtf",
                applicationType = MedicationApplicationType.ORAL,
                compound = "ESTRADIOL",
                doseKey = "mg:2",
            ),
            createdAtEpochMillis = 100L,
            updatedAtEpochMillis = 100L,
            archivedAtEpochMillis = null,
            importedFromExternalTracker = true,
        )
    }

    private fun bloodPanelSnapshot(
        panelUuid: UUID,
        resultUuid: UUID,
        importSourceApp: String?,
        importPanelKey: Long?,
        resultImportSourceApp: String?,
        resultImportExternalId: String?,
    ): BackupBloodTestPanelSnapshot {
        return BackupBloodTestPanelSnapshot(
            uuid = panelUuid.toString(),
            collectedAtInstantEpochMillis = 300L,
            collectedAtTimeZoneId = "Asia/Tokyo",
            notes = null,
            timeSinceLastEstradiolDoseMillis = null,
            timeSinceLastTestosteroneDoseMillis = null,
            createdAtEpochMillis = 300L,
            updatedAtEpochMillis = 300L,
            importSourceApp = importSourceApp,
            importPanelKey = importPanelKey,
            results = listOf(
                BackupBloodTestResultSnapshot(
                    uuid = resultUuid.toString(),
                    createdAtEpochMillis = 301L,
                    displayOrder = 0,
                    builtinAnalyteKey = BloodAnalyteKey.E2.storageValue,
                    customAnalyteUuid = null,
                    value = 367.1,
                    unitSnapshot = BloodUnitKey.PMOL_L.storageValue,
                    canonicalValue = 100.0,
                    importSourceApp = resultImportSourceApp,
                    importExternalId = resultImportExternalId,
                )
            ),
        )
    }
}
