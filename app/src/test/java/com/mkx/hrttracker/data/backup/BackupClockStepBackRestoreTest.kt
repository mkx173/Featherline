package com.mkx.hrttracker.data.backup

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
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
import com.mkx.hrttracker.model.home.HomeCardLayout
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.widget.WidgetAppearance
import com.mkx.hrttracker.widget.WidgetAppearanceRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * A row edited after the wall clock steps back has updatedAt (or archivedAt) before createdAt,
 * and the app exports it as it is. Restore raises those to createdAt instead of refusing the
 * app's own backup. The real repositories write, the real BackupExportService builds the
 * snapshot, and the real toValidatedSnapshot reads it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupClockStepBackRestoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseHolder: DatabaseHolder = mockk()
    private val gate: HomeSnapshotRepository = mockk()
    private val appScope = CoroutineScope(StandardTestDispatcher())
    private val fast = Instant.parse("2026-11-08T12:00:00Z")
    private val corrected: Instant = fast.minusSeconds(3_600)
    private val clock = SteppedClock(fast)
    private lateinit var database: HrtTrackerDatabase
    private lateinit var journal: JournalRepository
    private lateinit var medicines: MedicineRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, HrtTrackerDatabase::class.java).build()
        every { databaseHolder.get() } returns database
        every { databaseHolder.databaseFlow } returns MutableStateFlow(null)
        coEvery { databaseHolder.withTransaction<Any?>(any()) } coAnswers {
            database.withTransaction { firstArg<suspend (HrtTrackerDatabase) -> Any?>().invoke(database) }
        }
        coEvery { gate.runHomeDataMutation<Any?>(any()) } coAnswers { firstArg<suspend () -> Any?>().invoke() }
        journal = JournalRepository(databaseHolder, clock, gate, mockk(relaxed = true), appScope)
        medicines = MedicineRepository(databaseHolder, gate, MedicineStockMutator(), appScope)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun withoutAClockChange_theTimestampsRestoreAsExported() = runTest {
        val medicine = medicines.findOrCreateForCatalog(MedicationKey.ESTRADIOL, MedicinePreparation.Pill(2.0), now = fast)
        medicines.setDisplayName(medicine.uuid, "Morning pill", now = fast.plusSeconds(60))

        val restored = validatedOwnExport().medicines.single()
        assertEquals(fast.toEpochMilli(), restored.createdAtEpochMillis)
        assertEquals(fast.plusSeconds(60).toEpochMilli(), restored.updatedAtEpochMillis)
    }

    @Test
    fun aNoteEditedAfterTheClockStepsBack_restoresWithUpdatedAtRaisedToCreatedAt() = runTest {
        val today = LocalDate.of(2026, 11, 8)
        journal.saveNoteForDate(today, "Slept badly")
        clock.instant = corrected
        journal.saveNoteForDate(today, "Slept badly, headache at noon")

        val note = validatedOwnExport().notes.single()
        assertEquals("Slept badly, headache at noon", note.text)
        assertEquals(fast.toEpochMilli(), note.createdAtEpochMillis)
        assertEquals(fast.toEpochMilli(), note.updatedAtEpochMillis)
    }

    @Test
    fun anAnchorEditedAfterTheClockStepsBack_restoresWithUpdatedAtRaisedToCreatedAt() = runTest {
        journal.addTrackedDate("HRT start", "heart", LocalDate.of(2025, 1, 1), null, pinned = true)
        clock.instant = corrected
        val anchor = journal.getTrackedDateEntities().single()
        journal.updateTrackedDate(anchor.uuid, "Started HRT", "heart", LocalDate.of(2025, 1, 1), null)

        val restored = validatedOwnExport().trackedDates.single()
        assertEquals("Started HRT", restored.name)
        assertEquals(fast.toEpochMilli(), restored.createdAtEpochMillis)
        assertEquals(fast.toEpochMilli(), restored.updatedAtEpochMillis)
    }

    @Test
    fun aMedicineRenamedAfterTheClockStepsBack_restoresWithUpdatedAtRaisedToCreatedAt() = runTest {
        val medicine = medicines.findOrCreateForCatalog(MedicationKey.ESTRADIOL, MedicinePreparation.Pill(2.0), now = fast)
        medicines.setDisplayName(medicine.uuid, "Morning pill", now = corrected)

        val restored = validatedOwnExport().medicines.single()
        assertEquals("Morning pill", restored.displayName)
        assertEquals(fast.toEpochMilli(), restored.updatedAtEpochMillis)
    }

    @Test
    fun aMedicineArchivedAfterTheClockStepsBack_restoresArchivedAtCreatedAt() = runTest {
        val medicine = medicines.findOrCreateForCatalog(MedicationKey.ESTRADIOL, MedicinePreparation.Pill(2.0), now = fast)
        medicines.archive(medicine.uuid, now = corrected)

        val restored = validatedOwnExport().medicines.single()
        assertEquals(fast.toEpochMilli(), restored.updatedAtEpochMillis)
        assertEquals(fast.toEpochMilli(), restored.archivedAtEpochMillis)
    }

    @Test
    fun anArchivedAtBeforeCreatedAt_isRaisedOnItsOwn() = runTest {
        // archive writes archivedAt and updatedAt together, so only an edited file has this
        // shape; the clamp is per field.
        medicines.findOrCreateForCatalog(MedicationKey.ESTRADIOL, MedicinePreparation.Pill(2.0), now = fast)
        val snapshot = BackupSnapshotJsonCodec.decode(exportService().buildBackupSnapshotJson(clock.instant))!!
        val medicine = snapshot.medicines.single()
        val edited = snapshot.copy(
            medicines = listOf(
                medicine.copy(
                    updatedAtEpochMillis = medicine.createdAtEpochMillis + 5,
                    archivedAtEpochMillis = medicine.createdAtEpochMillis - 5,
                ),
            ),
        )

        val restored = edited.toValidatedSnapshot(expectedPackageName = BACKUP_APP_PACKAGE_NAME)
            .medicines.single()
        assertEquals(medicine.createdAtEpochMillis + 5, restored.updatedAtEpochMillis)
        assertEquals(medicine.createdAtEpochMillis, restored.archivedAtEpochMillis)
    }

    private suspend fun validatedOwnExport(): ValidatedBackupSnapshot =
        BackupSnapshotJsonCodec.decode(exportService().buildBackupSnapshotJson(clock.instant))!!
            .toValidatedSnapshot(expectedPackageName = BACKUP_APP_PACKAGE_NAME)

    private fun exportService(): BackupExportService {
        val settingsRepository: SettingsRepository = mockk {
            every { onboardingCompleted } returns flowOf(true)
            every { pkCalibrationIntroSeen } returns flowOf(true)
            every { stockNudgeEnabledFlow } returns flowOf(true)
            every { stockNudgeUserEnabledFlow } returns flowOf(false)
            every { homeCardLayoutFlow } returns flowOf(HomeCardLayout())
            coEvery { getCurrentSettings() } returns SettingsState()
        }
        return BackupExportService(
            context = context,
            databaseHolder = databaseHolder,
            settingsRepository = settingsRepository,
            userProfileRepository = mockk<UserProfileRepository> {
                coEvery { getCurrentProfile() } returns UserProfile()
            },
            medicineRepository = medicines,
            medicationGroupRepository = MedicationGroupRepository(databaseHolder, gate, appScope),
            medicationLogRepository = MedicationLogRepository(databaseHolder, gate, MedicineStockMutator(), appScope),
            bloodTestRepository = mockk<BloodTestRepository> {
                coEvery { getCustomAnalytes() } returns emptyList()
                coEvery { getPanels() } returns emptyList()
            },
            pkCalibrationStorageRepository = mockk<PkCalibrationStorageRepository> {
                coEvery { getAllMetadata() } returns emptyList()
            },
            widgetAppearanceRepository = mockk<WidgetAppearanceRepository> {
                coEvery { migrateFromLegacySettingsIfNeeded() } returns false
                coEvery { currentEffective(null) } returns WidgetAppearance.Default
            },
            journalRepository = journal,
            backupCrypto = BackupCrypto(TestBackupArgon2KeyDeriver()),
        )
    }

    private class SteppedClock(var instant: Instant) : Clock() {
        override fun instant(): Instant = instant
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
    }
}
