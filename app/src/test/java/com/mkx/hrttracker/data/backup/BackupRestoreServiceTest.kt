package com.mkx.hrttracker.data.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.mkx.hrttracker.data.local.BloodTestDao
import com.mkx.hrttracker.data.local.BloodTestPanelEntity
import com.mkx.hrttracker.data.local.BloodTestResultEntity
import com.mkx.hrttracker.data.local.DatabaseHolder
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
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
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
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
import com.mkx.hrttracker.reminder.MedicationReminderSnoozeScheduler
import com.mkx.hrttracker.reminder.ReminderNotificationManager
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
    private val pkCalibrationDao: PkCalibrationDao = mockk(relaxed = true)
    private val userProfileDao: UserProfileDao = mockk(relaxed = true)
    private val journalDao: JournalDao = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val homeSnapshotRepository: HomeSnapshotRepository = mockk()
    private val medicationReminderScheduler: MedicationReminderScheduler = mockk()
    private val medicationReminderSnoozeScheduler: MedicationReminderSnoozeScheduler = mockk()
    private val reminderNotificationManager: ReminderNotificationManager =
        mockk(relaxed = true)
    private val widgetAppearanceRepository: WidgetAppearanceRepository = mockk()

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
                any(), any(), any(), any(), any(),
                any(), any(),
            )
        } just Runs
        coEvery { medicationReminderScheduler.rescheduleAll(any()) } just Runs
        coEvery { medicationReminderSnoozeScheduler.clearAllSnoozes() } just Runs
        coEvery { widgetAppearanceRepository.setDefault(any()) } just Runs

        backupCrypto = BackupCrypto(TestBackupArgon2KeyDeriver())
        service = BackupRestoreService(
            context = context,
            databaseHolder = databaseHolder,
            settingsRepository = settingsRepository,
            homeSnapshotRepository = homeSnapshotRepository,
            medicationReminderScheduler = medicationReminderScheduler,
            medicationReminderSnoozeScheduler = medicationReminderSnoozeScheduler,
            reminderNotificationManager = reminderNotificationManager,
            widgetAppearanceRepository = widgetAppearanceRepository,
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
        coVerify(exactly = 1) { pkCalibrationDao.deleteDisplayArtifact() }
    }

    @Test
    fun restoreBackup_restoresReviewMetadataAfterResults_andClearsDerivedArtifact() = runTest {
        val resultUuid = "00000000-0000-0000-0000-0000000008a1"
        val snapshot = emptySnapshot().copy(
            bloodTestPanels = listOf(
                BackupBloodTestPanelSnapshot(
                    uuid = "00000000-0000-0000-0000-0000000008a0",
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
                            calibrationDisposition = "ACCEPTED",
                            acceptedModelVersion = "pk-calibration:test/v9",
                            acceptedSourceValueBits = "4059000000000000",
                            acceptedCollectedAtEpochMillis = 600L,
                            calibrationMetadataUpdatedAtEpochMillis = 2_000L,
                        )
                    ),
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

        coVerify(exactly = 1) { pkCalibrationDao.deleteDisplayArtifact() }
        coVerifyOrder {
            bloodTestDao.insertResults(any())
            pkCalibrationDao.insertMetadata(capture(metadataSlot))
        }
        val restored = metadataSlot.captured.single()
        assertEquals(resultUuid, restored.resultUuid)
        assertEquals("ACCEPTED", restored.disposition)
        assertEquals("4059000000000000", restored.acceptedSourceValueBits)
    }

    // v7 backups written before the acceptance-record rename decode ACCEPTED
    // rows with no record (the digest fields are unknown keys): the acceptance
    // cannot be honored, so restore downgrades to AUTO instead of aborting.
    @Test
    fun restoreBackup_v7AcceptedRowWithoutRecord_downgradesToAutoInsteadOfAborting() = runTest {
        val resultUuid = "00000000-0000-0000-0000-0000000008b1"
        val snapshot = emptySnapshot().copy(
            snapshotVersion = 7,
            bloodTestPanels = listOf(
                calibrationMetadataPanel(
                    panelUuid = "00000000-0000-0000-0000-0000000008b0",
                    resultUuid = resultUuid,
                    calibrationDisposition = "ACCEPTED",
                    acceptedModelVersion = null,
                    acceptedSourceValueBits = null,
                    acceptedCollectedAtEpochMillis = null,
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

        coVerify(exactly = 1) { pkCalibrationDao.insertMetadata(capture(metadataSlot)) }
        val restored = metadataSlot.captured.single()
        assertEquals(resultUuid, restored.resultUuid)
        assertEquals("AUTO", restored.disposition)
        assertNull(restored.acceptedModelVersion)
        assertNull(restored.acceptedSourceValueBits)
        assertNull(restored.acceptedCollectedAtEpochMillis)
    }

    @Test
    fun restoreBackup_currentVersionAcceptedRowWithoutRecord_stillFailsLoud() = runTest {
        val snapshot = emptySnapshot().copy(
            bloodTestPanels = listOf(
                calibrationMetadataPanel(
                    panelUuid = "00000000-0000-0000-0000-0000000008c0",
                    resultUuid = "00000000-0000-0000-0000-0000000008c1",
                    calibrationDisposition = "ACCEPTED",
                    acceptedModelVersion = null,
                    acceptedSourceValueBits = null,
                    acceptedCollectedAtEpochMillis = null,
                )
            ),
        )

        val error = restoreBackupBytesFails(snapshot)

        assertTrue(
            "Unexpected message: ${error.message}",
            error.message.orEmpty().contains("invalid disposition/record pairing"),
        )
    }

    private fun calibrationMetadataPanel(
        panelUuid: String,
        resultUuid: String,
        calibrationDisposition: String,
        acceptedModelVersion: String?,
        acceptedSourceValueBits: String?,
        acceptedCollectedAtEpochMillis: Long?,
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
                acceptedModelVersion = acceptedModelVersion,
                acceptedSourceValueBits = acceptedSourceValueBits,
                acceptedCollectedAtEpochMillis = acceptedCollectedAtEpochMillis,
                calibrationMetadataUpdatedAtEpochMillis = 2_000L,
            )
        ),
    )

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
                any(), any(), any(), any(), any(),
                any(), any(),
            )
        }
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
                any(), any(), any(), any(), any(),
                any(), any(),
            )
        }
        coVerify(exactly = 1) { medicationReminderScheduler.rescheduleAll(any()) }
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
                hazeBlurEnabled = any(),
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
    fun restoreBackupBytes_restoresCjkTextOffsetSetting() = runTest {
        val capturedValues = mutableListOf<Boolean>()
        coEvery {
            settingsRepository.restoreSettings(
                darkModeOption = any(),
                adaptiveColorEnabled = any(),
                pureBlackEnabled = any(),
                cjkTextOffsetEnabled = capture(capturedValues),
                hazeBlurEnabled = any(),
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
                groupNameCounter = any(),
                firstDayOfWeekOption = any(),
                stockNudgeEnabled = any(),
                stockNudgeUserEnabled = any(),
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
    fun restoreBackupBytes_restoresHazeBlurSetting() = runTest {
        val capturedValues = mutableListOf<Boolean>()
        coEvery {
            settingsRepository.restoreSettings(
                darkModeOption = any(),
                adaptiveColorEnabled = any(),
                pureBlackEnabled = any(),
                cjkTextOffsetEnabled = any(),
                hazeBlurEnabled = capture(capturedValues),
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
                cjkTextOffsetEnabled = any(),
                hazeBlurEnabled = capture(capturedValues),
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
                cjkTextOffsetEnabled = any(),
                hazeBlurEnabled = any(),
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
                cjkTextOffsetEnabled = any(),
                hazeBlurEnabled = any(),
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
                any(), any(), any(), any(), any(),
                any(), capture(layoutSlot),
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
                any(), any(), any(), any(), any(),
                any(), capture(layoutSlot),
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
                any(), any(), any(), any(), any(),
                any(), capture(layoutSlot),
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
    ): BackupExportService {
        val exportSettingsRepository: SettingsRepository = mockk()
        val exportUserProfileRepository: UserProfileRepository = mockk()
        val exportMedicineRepository: MedicineRepository = mockk()
        val exportMedicationGroupRepository: MedicationGroupRepository = mockk()
        val exportMedicationLogRepository: MedicationLogRepository = mockk()
        val exportBloodTestRepository: BloodTestRepository = mockk()
        val exportPkCalibrationStorageRepository: PkCalibrationStorageRepository = mockk()
        val exportWidgetAppearanceRepository: WidgetAppearanceRepository = mockk()
        val exportJournalRepository: JournalRepository = mockk()

        every { exportSettingsRepository.onboardingCompleted } returns flowOf(true)
        every { exportSettingsRepository.stockNudgeEnabledFlow } returns flowOf(true)
        every { exportSettingsRepository.stockNudgeUserEnabledFlow } returns flowOf(false)
        every { exportSettingsRepository.homeCardLayoutFlow } returns flowOf(HomeCardLayout())
        coEvery { exportSettingsRepository.getCurrentSettings() } returns SettingsState()
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

        return BackupExportService(
            context = context,
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
