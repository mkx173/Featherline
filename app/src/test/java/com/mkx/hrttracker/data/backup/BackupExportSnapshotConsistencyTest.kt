package com.mkx.hrttracker.data.backup

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.MedicationLogEntryEntity
import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.JournalRepository
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicationLogRepository
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.data.repository.MedicineStockMutator
import com.mkx.hrttracker.data.repository.PkCalibrationStorageRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.UserProfileRepository
import com.mkx.hrttracker.data.repository.toEntity
import com.mkx.hrttracker.model.home.HomeCardLayout
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.widget.WidgetAppearance
import com.mkx.hrttracker.widget.WidgetAppearanceRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

/**
 * Other writers run while a backup is read: an external import, a widget quick-log. What
 * one of them commits during the export has to be in the backup whole or not at all, or
 * restore rejects the file the export reported as saved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupExportSnapshotConsistencyTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseHolder: DatabaseHolder = mockk()
    private val gate: HomeSnapshotRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val userProfileRepository: UserProfileRepository = mockk()
    private val medicineRepository: MedicineRepository = mockk()
    private val bloodTestRepository: BloodTestRepository = mockk()
    private val pkCalibrationStorageRepository: PkCalibrationStorageRepository = mockk()
    private val widgetAppearanceRepository: WidgetAppearanceRepository = mockk()
    private val journalRepository: JournalRepository = mockk()

    private val importedMedicine = testMedicine(
        uuid = UUID.fromString("ba0c0000-0000-0000-0000-000000000001"),
    )
    private val importedDose = MedicationLogEntryEntity(
        uuid = "ba0c0000-0000-0000-0000-000000000002",
        category = "ESTRADIOL",
        medicineUuid = importedMedicine.uuid.toString(),
        applicationType = "ORAL",
        doseInstructionKind = "TABLET_FRACTION",
        tabletFractionNumerator = 1,
        tabletFractionDenominator = 1,
        doseVolumeMl = null,
        doseWeightGrams = null,
        equivalentE2Mg = 2.0,
        sourceGroupUuid = null,
        appliedAtEpochMillis = Instant.parse("2026-11-01T08:00:00Z").toEpochMilli(),
    )
    private var importJob: Job? = null

    private lateinit var database: HrtTrackerDatabase
    private lateinit var service: BackupExportService

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, HrtTrackerDatabase::class.java).build()
        every { databaseHolder.get() } returns database
        every { databaseHolder.databaseFlow } returns MutableStateFlow(null)
        coEvery { databaseHolder.withTransaction<Any?>(any()) } coAnswers {
            database.withTransaction {
                firstArg<suspend (HrtTrackerDatabase) -> Any?>().invoke(database)
            }
        }
        every { settingsRepository.onboardingCompleted } returns flowOf(true)
        every { settingsRepository.pkCalibrationIntroSeen } returns flowOf(true)
        every { settingsRepository.stockNudgeEnabledFlow } returns flowOf(true)
        every { settingsRepository.stockNudgeUserEnabledFlow } returns flowOf(false)
        every { settingsRepository.homeCardLayoutFlow } returns flowOf(HomeCardLayout())
        coEvery { settingsRepository.getCurrentSettings() } returns SettingsState()
        coEvery { widgetAppearanceRepository.migrateFromLegacySettingsIfNeeded() } returns false
        coEvery { widgetAppearanceRepository.currentEffective(null) } returns WidgetAppearance.Default
        coEvery { userProfileRepository.getCurrentProfile() } returns UserProfile()
        coEvery { bloodTestRepository.getCustomAnalytes() } returns emptyList()
        coEvery { bloodTestRepository.getPanels() } returns emptyList()
        coEvery { pkCalibrationStorageRepository.getAllMetadata() } returns emptyList()
        coEvery { journalRepository.getTrackedDateEntities() } returns emptyList()
        coEvery { journalRepository.getNoteEntities() } returns emptyList()

        val appScope = CoroutineScope(StandardTestDispatcher())
        val medicines = MedicineRepository(
            context = context,
            databaseHolder = databaseHolder,
            homeSnapshotRepository = gate,
            stockMutator = MedicineStockMutator(),
            appScope = appScope,
        )
        // Right after the export reads the medicines, an import adds one and its dose.
        coEvery { medicineRepository.getAll() } coAnswers {
            medicines.getAll().also { importIfTheDatabaseLetsIt() }
        }
        service = BackupExportService(
            context = context,
            databaseHolder = databaseHolder,
            settingsRepository = settingsRepository,
            userProfileRepository = userProfileRepository,
            medicineRepository = medicineRepository,
            medicationGroupRepository = MedicationGroupRepository(
                databaseHolder = databaseHolder,
                homeSnapshotRepository = gate,
                appScope = appScope,
            ),
            medicationLogRepository = MedicationLogRepository(
                databaseHolder = databaseHolder,
                homeSnapshotRepository = gate,
                stockMutator = MedicineStockMutator(),
                appScope = appScope,
            ),
            bloodTestRepository = bloodTestRepository,
            pkCalibrationStorageRepository = pkCalibrationStorageRepository,
            widgetAppearanceRepository = widgetAppearanceRepository,
            journalRepository = journalRepository,
            backupCrypto = BackupCrypto(TestBackupArgon2KeyDeriver()),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun anImportCommittingDuringTheExportIsInTheBackupWholeOrNotAtAll() = runTest {
        val snapshot = BackupSnapshotJsonCodec.decode(
            service.buildBackupSnapshotJson(Instant.parse("2026-11-08T03:00:00Z"))
        )!!
        assertNotNull("the import ran during the export", importJob)
        importJob?.join()

        val medicineUuids = snapshot.medicines.map { medicine -> medicine.uuid }.toSet()
        assertEquals(
            "doses in the backup whose medicine it leaves out",
            emptyList<String>(),
            snapshot.medicationLogs
                .filter { dose -> dose.medicineUuid !in medicineUuids }
                .map { dose -> dose.uuid },
        )
        // What restore checks before it replaces anything.
        snapshot.toValidatedSnapshot(expectedPackageName = BACKUP_APP_PACKAGE_NAME)
    }

    private suspend fun importIfTheDatabaseLetsIt() {
        if (importJob != null) return
        val job = CoroutineScope(Dispatchers.IO).launch {
            database.withTransaction {
                database.medicineDao().insert(importedMedicine.toEntity())
                database.medicationLogDao().insertEntries(listOf(importedDose))
            }
        }
        importJob = job
        // Ample for a commit nothing holds back; one the export holds back waits it out.
        withTimeoutOrNull(IMPORT_COMMIT_WAIT_MILLIS) { job.join() }
    }

    private companion object {
        const val IMPORT_COMMIT_WAIT_MILLIS = 1_000L
    }
}
