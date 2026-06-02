package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HomeDao
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.MedicationGroupEntity
import com.mkx.hrttracker.data.local.MedicationGroupItemEntity
import com.mkx.hrttracker.data.local.MedicationGroupScheduleTimeEntity
import com.mkx.hrttracker.data.local.MedicationGroupWithItemsEntity
import com.mkx.hrttracker.data.local.MedicationLogEntryEntity
import com.mkx.hrttracker.data.local.MedicineDao
import com.mkx.hrttracker.data.local.MedicineEntity
import com.mkx.hrttracker.data.local.UserProfileEntity
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.medication.DoseInstructionKind
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
import com.mkx.hrttracker.model.medication.MedicineIdentityKey
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.pk.PkConcentrationUnit
import com.mkx.hrttracker.model.pk.projectionFutureDays
import com.mkx.hrttracker.model.settings.SettingsState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class HomeRepositoryTest {
    private val databaseHolder: DatabaseHolder = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val homeSnapshotRepository: HomeSnapshotRepository = mockk()
    private val homeSnapshotStore: HomeSnapshotStore = mockk()
    private val homeSnapshotGenerationStore: HomeSnapshotGenerationStore = mockk()
    private val medicineStockRepository: MedicineStockRepository = mockk()
    private val medicineRepository: MedicineRepository = mockk()
    private val medicationLogRepository: MedicationLogRepository = mockk()
    private val database: HrtTrackerDatabase = mockk()
    private val homeDao: HomeDao = mockk()
    private val medicineDao: MedicineDao = mockk(relaxed = true)

    @Before
    fun stubMedicineChangeVersion() {
        // observeHomeStartupInputs now combines each Home-table observation with
        // this signal so medicines edits propagate without an app restart;
        // mockk's relaxed default emits no value, which would deadlock combine.
        every { medicineDao.observeMedicineChangeVersion() } returns MutableStateFlow(0)
        // Archived groups are combined alongside active groups; default to empty so
        // tests that don't exercise archiving don't deadlock the combine.
        every { homeDao.observeArchivedGroups() } returns flowOf(emptyList())
        every { medicineRepository.observeAllActiveTracked() } returns flowOf(emptyList())
        every {
            medicationLogRepository.observeScheduledEntriesInWindow(any(), any())
        } returns flowOf(emptyList())
        every {
            medicineStockRepository.projectAll(any(), any(), any(), any())
        } returns emptyList()
    }

    @Test
    fun observeHomeStartupInputs_usesBoundedScheduleWindow() = runTest {
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        val zoneId = ZoneId.systemDefault()
        val settings = SettingsState(homeE2DisplayUnit = BloodUnitKey.NG_DL)
        val scheduleEntry = logEntry(
            uuid = UUID.fromString("406807b7-260c-4c36-90bd-6f60696c7517"),
            scheduledFor = now.toLocalDate().atTime(8, 0),
        )
        val antiandrogenHistoryEntry = logEntry(
            uuid = UUID.fromString("aa6fa55f-df3a-4f68-91fd-05b1996e7b07"),
            scheduledFor = now.toLocalDate().minusWeeks(1).atTime(22, 0),
            category = MedicationCategory.ANTIANDROGEN,
            medicationKey = MedicationKey.SPIRONOLACTONE,
        )

        every { databaseHolder.get() } returns database
        every { database.homeDao() } returns homeDao
        every { database.medicineDao() } returns medicineDao
        coEvery { medicineDao.getByUuids(any()) } returns medicineEntities()
        every { homeDao.observeActiveGroups() } returns flowOf(listOf(groupWithItems()))
        every {
            homeDao.observeScheduleEntries(
                scheduledStartIso = "2026-05-05T00:00",
                scheduledEndIso = "2026-08-04T23:59:59",
                manualStartEpochMillis = LocalDate.of(2026, 5, 5)
                    .atStartOfDay(zoneId)
                    .toInstant()
                    .toEpochMilli(),
                manualEndEpochMillis = LocalDate.of(2026, 5, 7)
                    .atStartOfDay(zoneId)
                    .toInstant()
                    .toEpochMilli(),
            )
        } returns flowOf(listOf(scheduleEntry))
        every {
            homeDao.observeLatestAntiandrogenEntriesOnOrBefore(
                onOrBeforeEpochMillis = LocalDate.of(2026, 5, 7)
                    .atStartOfDay(zoneId)
                    .toInstant()
                    .toEpochMilli() - 1L,
            )
        } returns flowOf(listOf(antiandrogenHistoryEntry))
        every { homeDao.observeEstradiolPkEntries(any(), any()) } returns flowOf(emptyList())
        every { homeDao.observeLatestEstradiolEntryOnOrBefore(any()) } returns flowOf(null)
        every { homeDao.observeProfile() } returns flowOf(
            UserProfileEntity(
                weightKg = 64.0,
                weightOriginalValue = 64.0,
                weightOriginalUnit = "KILOGRAMS",
                updatedAtEpochMillis = 0L,
            )
        )
        every { settingsRepository.settingsState } returns MutableStateFlow(settings)
        every {
            settingsRepository.homeE2ChartWindowOptionFlow
        } returns MutableStateFlow(settings.homeE2ChartWindowOption)

        val inputs = HomeRepository(
            databaseHolder = databaseHolder,
            settingsRepository = settingsRepository,
            homeSnapshotRepository = homeSnapshotRepository,
            medicineStockRepository = medicineStockRepository,
            medicineRepository = medicineRepository,
            medicationLogRepository = medicationLogRepository,
        ).observeHomeStartupInputs(now, zoneId).first()

        assertEquals(1, inputs.activeGroups.size)
        assertEquals(listOf(scheduleEntry.uuid), inputs.scheduleEntries.map { it.uuid.toString() })
        assertEquals(
            listOf(antiandrogenHistoryEntry.uuid),
            inputs.antiandrogenHistoryEntries.map { it.uuid.toString() },
        )
        assertEquals(64.0, inputs.profile.weightKg ?: 0.0, 1e-9)
        assertEquals(BloodUnitKey.NG_DL, inputs.settings.homeE2DisplayUnit)

        verify(exactly = 1) { database.homeDao() }
    }

    @Test
    fun observeHomeStartupInputs_usesProvidedZoneForEpochWindows() = runTest {
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        val providedZone = providedRegressionZone()
        val today = now.toLocalDate()
        val settings = SettingsState(homeE2DisplayUnit = BloodUnitKey.NG_DL)
        val manualStartEpochMillis = today.minusDays(1)
            .atStartOfDay(providedZone)
            .toInstant()
            .toEpochMilli()
        val manualEndEpochMillis = today.plusDays(1)
            .atStartOfDay(providedZone)
            .toInstant()
            .toEpochMilli()
        val endOfTodayInclusiveEpochMillis = manualEndEpochMillis - 1L
        val pkStartEpochMillis = today
            .atStartOfDay()
            .minusDays(settings.homeE2ChartWindowOption.pastDays + 180L)
            .atZone(providedZone)
            .toInstant()
            .toEpochMilli()
        val pkEndEpochMillis = today
            .plusDays(settings.homeE2ChartWindowOption.projectionFutureDays())
            .atStartOfDay()
            .atZone(providedZone)
            .toInstant()
            .toEpochMilli()

        every { databaseHolder.get() } returns database
        every { database.homeDao() } returns homeDao
        every { database.medicineDao() } returns medicineDao
        coEvery { medicineDao.getByUuids(any()) } returns emptyList()
        every { homeDao.observeActiveGroups() } returns flowOf(emptyList())
        every {
            homeDao.observeScheduleEntries(
                scheduledStartIso = "2026-05-05T00:00",
                scheduledEndIso = "2026-08-04T23:59:59",
                manualStartEpochMillis = manualStartEpochMillis,
                manualEndEpochMillis = manualEndEpochMillis,
            )
        } returns flowOf(emptyList())
        every {
            homeDao.observeLatestAntiandrogenEntriesOnOrBefore(
                onOrBeforeEpochMillis = endOfTodayInclusiveEpochMillis,
            )
        } returns flowOf(emptyList())
        every {
            homeDao.observeEstradiolPkEntries(
                startEpochMillis = pkStartEpochMillis,
                endEpochMillis = pkEndEpochMillis,
            )
        } returns flowOf(emptyList())
        every {
            homeDao.observeLatestEstradiolEntryOnOrBefore(
                onOrBeforeEpochMillis = endOfTodayInclusiveEpochMillis,
            )
        } returns flowOf(null)
        every { homeDao.observeProfile() } returns flowOf(null)
        every { settingsRepository.settingsState } returns MutableStateFlow(settings)
        every {
            settingsRepository.homeE2ChartWindowOptionFlow
        } returns MutableStateFlow(settings.homeE2ChartWindowOption)

        HomeRepository(
            databaseHolder = databaseHolder,
            settingsRepository = settingsRepository,
            homeSnapshotRepository = homeSnapshotRepository,
            medicineStockRepository = medicineStockRepository,
            medicineRepository = medicineRepository,
            medicationLogRepository = medicationLogRepository,
        ).observeHomeStartupInputs(now, providedZone).first()

        verify(exactly = 1) {
            homeDao.observeScheduleEntries(
                scheduledStartIso = "2026-05-05T00:00",
                scheduledEndIso = "2026-08-04T23:59:59",
                manualStartEpochMillis = manualStartEpochMillis,
                manualEndEpochMillis = manualEndEpochMillis,
            )
        }
    }

    @Test
    fun observeHomeSnapshotInputs_readsFullSnapshotWithoutOpeningDatabase() = runTest {
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        val zoneId = ZoneId.systemDefault()
        val settings = SettingsState(homeE2DisplayUnit = BloodUnitKey.NG_DL)
        val pkEntry = logEntry(
            uuid = UUID.fromString("7dd8dc9c-2e3d-4c39-a55d-73042a9e60b3"),
            scheduledFor = null,
        )
        val scheduleEntry = logEntry(
            uuid = UUID.fromString("406807b7-260c-4c36-90bd-6f60696c7517"),
            scheduledFor = now.toLocalDate().atTime(8, 0),
        )
        val antiandrogenHistoryEntry = logEntry(
            uuid = UUID.fromString("aa6fa55f-df3a-4f68-91fd-05b1996e7b07"),
            scheduledFor = now.toLocalDate().minusWeeks(1).atTime(22, 0),
            category = MedicationCategory.ANTIANDROGEN,
            medicationKey = MedicationKey.SPIRONOLACTONE,
        )
        val medicinesByUuid = medicineEntities().associate { entity ->
            entity.uuid to entity.toMedicineModel()
        }
        val pkSnapshot = HomePkProjectionRecord(
            generatedAtEpochMillis = now.atZone(zoneId).toInstant().toEpochMilli(),
            windowStartEpochMillis = LocalDate.of(2026, 5, 1)
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli(),
            windowEndEpochMillis = LocalDate.of(2026, 5, 30)
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli(),
            pkProjectionExpiresAtEpochMillis = LocalDate.of(2026, 5, 30)
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli(),
            concentrationUnit = PkConcentrationUnit.PG_PER_ML.name,
            timeH = emptyList(),
            concentrations = emptyList(),
            doseMarkers = emptyList(),
            latestEstradiolEntry = pkEntry.toMedicationLogEntryModel(medicinesByUuid),
            chartWindowHours = 168,
            densePolicy = HomePkDenseSamplePolicyRecord.Interval(hours = 0.1),
            includesPostDoseOffsets = false,
        )
        val snapshot = HomeSnapshotRecord(
            schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
            generatedAtEpochMillis = 10L,
            anchorDateEpochDay = now.toLocalDate().toEpochDay(),
            zoneId = ZoneId.systemDefault().id,
            pkProjection = pkSnapshot,
            activeGroups = listOf(groupWithItems().toMedicationGroupModel(medicinesByUuid)),
            scheduleEntries = listOf(scheduleEntry.toMedicationLogEntryModel(medicinesByUuid)),
            antiandrogenHistoryEntries = listOf(
                antiandrogenHistoryEntry.toMedicationLogEntryModel(medicinesByUuid)
            ),
        )

        every { homeSnapshotStore.observeSnapshot() } returns flowOf(snapshot)
        every { homeSnapshotGenerationStore.observeGeneration() } returns MutableStateFlow(0L)
        coEvery { homeSnapshotGenerationStore.readGeneration() } returns 0L
        every { settingsRepository.settingsState } returns MutableStateFlow(settings)
        every {
            settingsRepository.homeE2ChartWindowOptionFlow
        } returns MutableStateFlow(settings.homeE2ChartWindowOption)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val snapshotRepository = HomeSnapshotRepository(
            databaseHolder = databaseHolder,
            homeSnapshotStore = homeSnapshotStore,
            homeSnapshotGenerationStore = homeSnapshotGenerationStore,
            settingsRepository = settingsRepository,
            appScope = backgroundScope,
            defaultDispatcher = dispatcher,
        )

        val inputs = HomeRepository(
            databaseHolder = databaseHolder,
            settingsRepository = settingsRepository,
            homeSnapshotRepository = snapshotRepository,
            medicineStockRepository = medicineStockRepository,
            medicineRepository = medicineRepository,
            medicationLogRepository = medicationLogRepository,
        ).observeHomeSnapshotInputs(now, zoneId).first()

        assertEquals(HomeInputSource.SNAPSHOT, inputs.source)
        assertEquals(1, inputs.activeGroups.size)
        assertEquals(listOf(scheduleEntry.uuid), inputs.scheduleEntries.map { it.uuid.toString() })
        assertEquals(
            listOf(antiandrogenHistoryEntry.uuid),
            inputs.antiandrogenHistoryEntries.map { it.uuid.toString() },
        )
        assertEquals(pkEntry.uuid, inputs.latestEstradiolEntry?.uuid.toString())
        assertEquals(PkConcentrationUnit.PG_PER_ML, inputs.pkProjection?.concentrationUnit)
        assertEquals(BloodUnitKey.NG_DL, inputs.settings.homeE2DisplayUnit)

        verify(exactly = 0) { databaseHolder.get() }
    }

    @Test
    fun observeHomeSnapshotInputs_usesProvidedZoneForSnapshotDerivations() = runTest {
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        val providedZone = providedRegressionZone()
        val settings = SettingsState(homeE2DisplayUnit = BloodUnitKey.NG_DL)
        val medicine = testMedicine(
            uuid = UUID.fromString(ESTRADIOL_MEDICINE_UUID),
            stock = MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 0.0,
                unitsLastTotal = 10.0,
            ),
        )
        val medicinesByUuid = medicineEntities().associate { entity ->
            entity.uuid to entity.toMedicineModel()
        }
        val snapshotScheduleEntry = logEntry(
            uuid = UUID.fromString("f3f44f5f-8523-4a2d-ae53-48ef6f819e62"),
            scheduledFor = now.toLocalDate().atTime(8, 0),
        ).toMedicationLogEntryModel(medicinesByUuid)
        val snapshot = HomeSnapshotRecord(
            schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
            generatedAtEpochMillis = now.atZone(providedZone).toInstant().toEpochMilli(),
            anchorDateEpochDay = now.toLocalDate().toEpochDay(),
            zoneId = providedZone.id,
            pkProjection = null,
            activeGroups = emptyList(),
            scheduleEntries = listOf(snapshotScheduleEntry),
            antiandrogenHistoryEntries = emptyList(),
            stockMedicines = listOf(medicine),
            stockFulfillmentEntries = emptyList(),
        )

        every { settingsRepository.settingsState } returns MutableStateFlow(settings)
        every { settingsRepository.homeE2ChartWindowOptionFlow } returns MutableStateFlow(settings.homeE2ChartWindowOption)
        every { homeSnapshotRepository.observeHomeSnapshot() } returns flowOf(snapshot)
        every {
            homeSnapshotRepository.isSnapshotUsable(
                snapshot = snapshot,
                now = now,
                zoneId = providedZone,
                option = settings.homeE2ChartWindowOption,
            )
        } returns true
        every {
            homeSnapshotRepository.scheduleEntriesForHome(
                snapshot = snapshot,
                now = now,
                zoneId = providedZone,
            )
        } returns listOf(snapshotScheduleEntry)
        every {
            homeSnapshotRepository.decodeProjection(
                projectionRecord = null,
                now = now,
                zoneId = providedZone,
            )
        } returns null
        every {
            medicineStockRepository.projectAll(
                medicines = listOf(medicine),
                activeGroups = emptyList(),
                logEntries = emptyList(),
                now = now.atZone(providedZone).toInstant(),
            )
        } returns emptyList()

        val inputs = HomeRepository(
            databaseHolder = databaseHolder,
            settingsRepository = settingsRepository,
            homeSnapshotRepository = homeSnapshotRepository,
            medicineStockRepository = medicineStockRepository,
            medicineRepository = medicineRepository,
            medicationLogRepository = medicationLogRepository,
        ).observeHomeSnapshotInputs(now, providedZone).first()

        assertEquals(HomeInputSource.SNAPSHOT, inputs.source)
        verify(exactly = 1) {
            homeSnapshotRepository.isSnapshotUsable(
                snapshot = snapshot,
                now = now,
                zoneId = providedZone,
                option = settings.homeE2ChartWindowOption,
            )
        }
        verify(exactly = 1) {
            homeSnapshotRepository.scheduleEntriesForHome(
                snapshot = snapshot,
                now = now,
                zoneId = providedZone,
            )
        }
        verify(exactly = 1) {
            homeSnapshotRepository.decodeProjection(
                projectionRecord = null,
                now = now,
                zoneId = providedZone,
            )
        }
        verify(exactly = 1) {
            medicineStockRepository.projectAll(
                medicines = listOf(medicine),
                activeGroups = emptyList(),
                logEntries = emptyList(),
                now = now.atZone(providedZone).toInstant(),
            )
        }
    }

    @Test
    fun observeHomeInputs_whenSnapshotMissingUsesRoomEstradiolFallbackWithoutRefreshing() = runTest {
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        val zoneId = ZoneId.systemDefault()
        val settings = SettingsState(homeE2DisplayUnit = BloodUnitKey.PG_ML)
        val latestEstradiolEntry = logEntry(
            uuid = UUID.fromString("7dd8dc9c-2e3d-4c39-a55d-73042a9e60b3"),
            scheduledFor = null,
        )

        every { databaseHolder.get() } returns database
        every { database.homeDao() } returns homeDao
        every { database.medicineDao() } returns medicineDao
        coEvery { medicineDao.getByUuids(any()) } returns medicineEntities()
        every { homeDao.observeActiveGroups() } returns flowOf(listOf(groupWithItems()))
        every { homeDao.observeScheduleEntries(any(), any(), any(), any()) } returns flowOf(emptyList())
        every { homeDao.observeLatestAntiandrogenEntriesOnOrBefore(any()) } returns flowOf(emptyList())
        every { homeDao.observeEstradiolPkEntries(any(), any()) } returns flowOf(listOf(latestEstradiolEntry))
        every { homeDao.observeLatestEstradiolEntryOnOrBefore(any()) } returns flowOf(latestEstradiolEntry)
        every { homeDao.observeProfile() } returns flowOf(
            UserProfileEntity(
                weightKg = 64.0,
                weightOriginalValue = 64.0,
                weightOriginalUnit = "KILOGRAMS",
                updatedAtEpochMillis = 0L,
            )
        )
        every { settingsRepository.settingsState } returns MutableStateFlow(settings)
        every {
            settingsRepository.homeE2ChartWindowOptionFlow
        } returns MutableStateFlow(settings.homeE2ChartWindowOption)
        every { homeSnapshotRepository.observeHomeSnapshot() } returns flowOf(null)
        every { homeSnapshotRepository.decodeProjection(null, any(), any()) } returns null
        every { homeSnapshotRepository.refreshHomeSnapshotAsync(any(), any()) } returns Unit

        val inputs = HomeRepository(
            databaseHolder = databaseHolder,
            settingsRepository = settingsRepository,
            homeSnapshotRepository = homeSnapshotRepository,
            medicineStockRepository = medicineStockRepository,
            medicineRepository = medicineRepository,
            medicationLogRepository = medicationLogRepository,
        ).observeHomeInputs(now, zoneId).first()

        assertEquals(HomeInputSource.ROOM, inputs.source)
        // Real entry comes from the room query; the fallback path also synthesizes
        // virtual entries for the active group's future slots, so check inclusion
        // rather than exact equality.
        assertTrue(
            inputs.estradiolPkEntries.any { it.uuid.toString() == latestEstradiolEntry.uuid },
        )
        assertEquals(latestEstradiolEntry.uuid, inputs.latestEstradiolEntry?.uuid.toString())
        verify(exactly = 0) { homeSnapshotRepository.refreshHomeSnapshotAsync(any(), any()) }
    }

    @Test
    fun observeHomeInputs_roomEmissionCarriesStockWarnings() = runTest {
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        val zoneId = ZoneId.systemDefault()
        val settings = SettingsState(homeE2DisplayUnit = BloodUnitKey.PG_ML)
        val medicine = testMedicine(
            uuid = UUID.fromString(ESTRADIOL_MEDICINE_UUID),
            stock = MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 0.0,
                unitsLastTotal = 10.0,
            ),
        )
        val projection = MedicineStockProjection(
            medicine = medicine,
            dosesPerDayMagnitude = 1.0,
            totalStockUnits = 0.0,
            runway = RunwayProjection.NoSchedule,
            intervalDays = null,
            maxPerAdministration = 1.0,
            state = MedicineStockState.OUT,
        )

        every { databaseHolder.get() } returns database
        every { database.homeDao() } returns homeDao
        every { database.medicineDao() } returns medicineDao
        coEvery { medicineDao.getByUuids(any()) } returns emptyList()
        every { homeDao.observeActiveGroups() } returns flowOf(emptyList())
        every { homeDao.observeScheduleEntries(any(), any(), any(), any()) } returns flowOf(emptyList())
        every { homeDao.observeLatestAntiandrogenEntriesOnOrBefore(any()) } returns flowOf(emptyList())
        every { homeDao.observeEstradiolPkEntries(any(), any()) } returns flowOf(emptyList())
        every { homeDao.observeLatestEstradiolEntryOnOrBefore(any()) } returns flowOf(null)
        every { homeDao.observeProfile() } returns flowOf(null)
        every { settingsRepository.settingsState } returns MutableStateFlow(settings)
        every { settingsRepository.homeE2ChartWindowOptionFlow } returns MutableStateFlow(settings.homeE2ChartWindowOption)
        every { homeSnapshotRepository.observeHomeSnapshot() } returns flowOf(null)
        every { homeSnapshotRepository.decodeProjection(null, any(), any()) } returns null
        every { medicineRepository.observeAllActiveTracked() } returns flowOf(listOf(medicine))
        every {
            medicationLogRepository.observeScheduledEntriesInWindow(any(), any())
        } returns flowOf(emptyList())
        every {
            medicineStockRepository.projectAll(
                medicines = listOf(medicine),
                activeGroups = emptyList(),
                logEntries = emptyList(),
                now = now.atZone(zoneId).toInstant(),
            )
        } returns listOf(projection)

        val inputs = HomeRepository(
            databaseHolder = databaseHolder,
            settingsRepository = settingsRepository,
            homeSnapshotRepository = homeSnapshotRepository,
            medicineStockRepository = medicineStockRepository,
            medicineRepository = medicineRepository,
            medicationLogRepository = medicationLogRepository,
        ).observeHomeInputs(now, zoneId).first()

        assertEquals(HomeInputSource.ROOM, inputs.source)
        assertEquals(listOf(projection), inputs.stockWarnings)
    }

    @Test
    fun observeHomeSnapshotInputs_derivesStockWarningsFromSnapshotStockInputs() = runTest {
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        val zoneId = ZoneId.systemDefault()
        val settings = SettingsState(homeE2DisplayUnit = BloodUnitKey.NG_DL)
        val medicine = testMedicine(
            uuid = UUID.fromString(ESTRADIOL_MEDICINE_UUID),
            stock = MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 0.0,
                unitsLastTotal = 10.0,
            ),
        )
        val projection = MedicineStockProjection(
            medicine = medicine,
            dosesPerDayMagnitude = 1.0,
            totalStockUnits = 0.0,
            runway = RunwayProjection.NoSchedule,
            intervalDays = null,
            maxPerAdministration = 1.0,
            state = MedicineStockState.OUT,
        )
        val snapshot = HomeSnapshotRecord(
            schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
            generatedAtEpochMillis = now.atZone(zoneId).toInstant().toEpochMilli(),
            anchorDateEpochDay = now.toLocalDate().toEpochDay(),
            zoneId = zoneId.id,
            pkProjection = null,
            activeGroups = emptyList(),
            scheduleEntries = emptyList(),
            antiandrogenHistoryEntries = emptyList(),
            stockMedicines = listOf(medicine),
            stockFulfillmentEntries = emptyList(),
        )

        every { settingsRepository.settingsState } returns MutableStateFlow(settings)
        every { settingsRepository.homeE2ChartWindowOptionFlow } returns MutableStateFlow(settings.homeE2ChartWindowOption)
        every { homeSnapshotRepository.observeHomeSnapshot() } returns flowOf(snapshot)
        every {
            homeSnapshotRepository.isSnapshotUsable(any(), any(), any(), any())
        } returns true
        every { homeSnapshotRepository.scheduleEntriesForHome(any(), any(), any()) } returns emptyList()
        every { homeSnapshotRepository.decodeProjection(null, any(), any()) } returns null
        every {
            medicineStockRepository.projectAll(
                medicines = listOf(medicine),
                activeGroups = emptyList(),
                logEntries = emptyList(),
                now = now.atZone(zoneId).toInstant(),
            )
        } returns listOf(projection)

        val inputs = HomeRepository(
            databaseHolder = databaseHolder,
            settingsRepository = settingsRepository,
            homeSnapshotRepository = homeSnapshotRepository,
            medicineStockRepository = medicineStockRepository,
            medicineRepository = medicineRepository,
            medicationLogRepository = medicationLogRepository,
        ).observeHomeSnapshotInputs(now, zoneId).first()

        assertEquals(HomeInputSource.SNAPSHOT, inputs.source)
        assertEquals(listOf(projection), inputs.stockWarnings)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun observeHomeInputs_doesNotEmitSnapshotAfterRoomInputStarts() = runTest {
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        val zoneId = ZoneId.systemDefault()
        val settings = SettingsState(homeE2DisplayUnit = BloodUnitKey.PG_ML)
        val snapshotRecords = MutableSharedFlow<HomeSnapshotRecord?>(replay = 1)
        val roomSnapshotRecords = MutableSharedFlow<HomeSnapshotRecord?>(replay = 1)
        val snapshot = HomeSnapshotRecord(
            schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
            generatedAtEpochMillis = now.atZone(zoneId).toInstant().toEpochMilli(),
            anchorDateEpochDay = now.toLocalDate().toEpochDay(),
            zoneId = zoneId.id,
            pkProjection = null,
            activeGroups = emptyList(),
            scheduleEntries = emptyList(),
            antiandrogenHistoryEntries = emptyList(),
        )
        snapshotRecords.tryEmit(null)
        roomSnapshotRecords.tryEmit(null)

        every { databaseHolder.get() } returns database
        every { database.homeDao() } returns homeDao
        every { database.medicineDao() } returns medicineDao
        coEvery { medicineDao.getByUuids(any()) } returns medicineEntities()
        every { homeDao.observeActiveGroups() } returns flowOf(listOf(groupWithItems()))
        every { homeDao.observeScheduleEntries(any(), any(), any(), any()) } returns flowOf(emptyList())
        every { homeDao.observeLatestAntiandrogenEntriesOnOrBefore(any()) } returns flowOf(emptyList())
        every { homeDao.observeEstradiolPkEntries(any(), any()) } returns flowOf(emptyList())
        every { homeDao.observeLatestEstradiolEntryOnOrBefore(any()) } returns flowOf(null)
        every { homeDao.observeProfile() } returns flowOf(
            UserProfileEntity(
                weightKg = 64.0,
                weightOriginalValue = 64.0,
                weightOriginalUnit = "KILOGRAMS",
                updatedAtEpochMillis = 0L,
            )
        )
        every { settingsRepository.settingsState } returns MutableStateFlow(settings)
        every {
            settingsRepository.homeE2ChartWindowOptionFlow
        } returns MutableStateFlow(settings.homeE2ChartWindowOption)
        every { homeSnapshotRepository.observeHomeSnapshot() } returnsMany listOf(
            snapshotRecords,
            roomSnapshotRecords,
        )
        every {
            homeSnapshotRepository.isSnapshotUsable(
                snapshot = any(),
                now = any(),
                zoneId = any(),
                option = any(),
            )
        } returns true
        every {
            homeSnapshotRepository.scheduleEntriesForHome(
                snapshot = any(),
                now = any(),
                zoneId = any(),
            )
        } returns emptyList()
        every { homeSnapshotRepository.decodeProjection(null, any(), any()) } returns null

        val emittedSources = mutableListOf<HomeInputSource>()
        val firstRoomObserved = CompletableDeferred<Unit>()
        val repository = HomeRepository(
            databaseHolder = databaseHolder,
            settingsRepository = settingsRepository,
            homeSnapshotRepository = homeSnapshotRepository,
            medicineStockRepository = medicineStockRepository,
            medicineRepository = medicineRepository,
            medicationLogRepository = medicationLogRepository,
        )
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeHomeInputs(now, zoneId).collect { inputs ->
                emittedSources += inputs.source
                if (inputs.source == HomeInputSource.ROOM) {
                    firstRoomObserved.complete(Unit)
                }
            }
        }
        firstRoomObserved.await()

        assertEquals(listOf(HomeInputSource.ROOM), emittedSources)

        snapshotRecords.emit(snapshot)
        advanceUntilIdle()

        assertEquals(false, emittedSources.contains(HomeInputSource.SNAPSHOT))
        collectJob.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun observeHomeInputs_doesNotPairNewChartWindowWithStalePkRowsDuringOptionSwap() = runTest {
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        val zoneId = ZoneId.systemDefault()
        val optionFlow = MutableStateFlow(HomeE2ChartWindowOption.SEVEN_DAYS)
        val settingsState = MutableStateFlow(
            SettingsState(homeE2ChartWindowOption = HomeE2ChartWindowOption.SEVEN_DAYS)
        )
        val sevenDayPkEntry = logEntry(
            uuid = UUID.fromString("4d0a97ec-e07b-4afc-a7ea-5a5b18270ff6"),
            scheduledFor = null,
        )
        val thirtyDayPkEntry = logEntry(
            uuid = UUID.fromString("a5ec219d-e419-4713-91fb-32d8b1298bbb"),
            scheduledFor = null,
        )

        every { databaseHolder.get() } returns database
        every { database.homeDao() } returns homeDao
        every { database.medicineDao() } returns medicineDao
        coEvery { medicineDao.getByUuids(any()) } returns medicineEntities()
        every { homeDao.observeActiveGroups() } returns flowOf(emptyList())
        every { homeDao.observeScheduleEntries(any(), any(), any(), any()) } returns flowOf(emptyList())
        every { homeDao.observeLatestAntiandrogenEntriesOnOrBefore(any()) } returns flowOf(emptyList())
        every { homeDao.observeProfile() } returns flowOf(null)
        every { homeDao.observeLatestEstradiolEntryOnOrBefore(any()) } returns flowOf(null)
        every { homeDao.observeEstradiolPkEntries(any(), any()) } returnsMany listOf(
            flowOf(listOf(sevenDayPkEntry)),
            flowOf(listOf(thirtyDayPkEntry)),
        )
        every { settingsRepository.settingsState } returns settingsState
        every { settingsRepository.homeE2ChartWindowOptionFlow } returns optionFlow
        every { homeSnapshotRepository.observeHomeSnapshot() } returns flowOf(null)
        every { homeSnapshotRepository.decodeProjection(null, any(), any()) } returns null

        val emittedInputs = mutableListOf<HomeInputs>()
        val firstRoomObserved = CompletableDeferred<Unit>()
        val thirtyDayObserved = CountDownLatch(1)
        val repository = HomeRepository(
            databaseHolder = databaseHolder,
            settingsRepository = settingsRepository,
            homeSnapshotRepository = homeSnapshotRepository,
            medicineStockRepository = medicineStockRepository,
            medicineRepository = medicineRepository,
            medicationLogRepository = medicationLogRepository,
        )
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeHomeInputs(now, zoneId).collect { inputs ->
                emittedInputs += inputs
                if (inputs.source == HomeInputSource.ROOM) {
                    firstRoomObserved.complete(Unit)
                }
                if (
                    inputs.settings.homeE2ChartWindowOption == HomeE2ChartWindowOption.THIRTY_DAYS &&
                    inputs.estradiolPkEntries.any { entry -> entry.uuid.toString() == thirtyDayPkEntry.uuid }
                ) {
                    thirtyDayObserved.countDown()
                }
            }
        }
        firstRoomObserved.await()
        assertEquals(1, emittedInputs.size)
        assertEquals(HomeE2ChartWindowOption.SEVEN_DAYS, emittedInputs.single().settings.homeE2ChartWindowOption)
        assertEquals(
            listOf(sevenDayPkEntry.uuid),
            emittedInputs.single().estradiolPkEntries.map { entry -> entry.uuid.toString() },
        )

        optionFlow.value = HomeE2ChartWindowOption.THIRTY_DAYS
        advanceUntilIdle()
        assertTrue(
            "Expected the replacement 30-day PK query to emit.",
            thirtyDayObserved.await(1, TimeUnit.SECONDS),
        )

        val staleThirtyDayEmissions = emittedInputs
            .filter { inputs ->
                inputs.settings.homeE2ChartWindowOption == HomeE2ChartWindowOption.THIRTY_DAYS &&
                    inputs.estradiolPkEntries.any { entry -> entry.uuid.toString() == sevenDayPkEntry.uuid }
            }
            .map { inputs -> inputs.estradiolPkEntries.map { entry -> entry.uuid.toString() } }
        assertEquals(
            "Room fallback must wait for the 30-day PK query before emitting THIRTY_DAYS inputs.",
            emptyList<List<String>>(),
            staleThirtyDayEmissions,
        )
        val thirtyDayEmissions = emittedInputs
            .filter { inputs ->
                inputs.settings.homeE2ChartWindowOption == HomeE2ChartWindowOption.THIRTY_DAYS
            }
            .map { inputs -> inputs.estradiolPkEntries.map { entry -> entry.uuid.toString() } }
        assertEquals(listOf(listOf(thirtyDayPkEntry.uuid)), thirtyDayEmissions)
        collectJob.cancel()
    }

    private fun groupWithItems(): MedicationGroupWithItemsEntity {
        val groupUuid = UUID.fromString("44bff3cb-b4dd-4be4-9eda-442cf91185c1").toString()
        return MedicationGroupWithItemsEntity(
            group = MedicationGroupEntity(
                uuid = groupUuid,
                name = "Home estradiol",
                colorKey = MedicationGroupColorKey.PLUM.name,
                scheduleType = MedicationGroupScheduleType.DAILY.name,
                scheduleInterval = 1,
                scheduleSinceEpochDay = LocalDate.of(2026, 5, 1).toEpochDay(),
                createdAtEpochMillis = 0L,
                updatedAtEpochMillis = 0L,
            ),
            items = listOf(
                MedicationGroupItemEntity(
                    uuid = UUID.fromString("79dfe41c-b684-4e48-858d-d47d369b5b13").toString(),
                    groupUuid = groupUuid,
                    sortOrder = 0,
                    count = 1,
                    medicineUuid = ESTRADIOL_MEDICINE_UUID,
                    applicationType = MedicationApplicationType.ORAL.name,
                    doseInstructionKind = DoseInstructionKind.TABLET_FRACTION.name,
                    tabletFractionNumerator = 1,
                    tabletFractionDenominator = 1,
                    doseVolumeMl = null,
                    doseWeightGrams = null,
                )
            ),
            scheduleTimes = listOf(
                MedicationGroupScheduleTimeEntity(
                    uuid = UUID.fromString("52d9acc8-11be-43ff-b6cb-035571ec0371").toString(),
                    groupUuid = groupUuid,
                    sortOrder = 0,
                    hourOfDay = 8,
                    minuteOfHour = 0,
                    effectiveFromLocalIso = LocalDate.of(2026, 5, 1).atStartOfDay().toString(),
                )
            ),
            weeklyDays = emptyList(),
        )
    }

    private fun logEntry(
        uuid: UUID,
        scheduledFor: LocalDateTime?,
        category: MedicationCategory = MedicationCategory.ESTRADIOL,
        medicationKey: MedicationKey = MedicationKey.ESTRADIOL,
    ): MedicationLogEntryEntity {
        val appliedAt = scheduledFor ?: LocalDateTime.of(2026, 5, 6, 9, 0)
        return MedicationLogEntryEntity(
            uuid = uuid.toString(),
            category = category.name,
            medicineUuid = when (medicationKey) {
                MedicationKey.SPIRONOLACTONE -> SPIRONOLACTONE_MEDICINE_UUID
                else -> ESTRADIOL_MEDICINE_UUID
            },
            applicationType = MedicationApplicationType.ORAL.name,
            doseInstructionKind = DoseInstructionKind.TABLET_FRACTION.name,
            tabletFractionNumerator = 1,
            tabletFractionDenominator = 1,
            doseVolumeMl = null,
            doseWeightGrams = null,
            equivalentE2Mg = 2.0,
            sourceGroupUuid = null,
            appliedAtEpochMillis = appliedAt
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli(),
            scheduledForIso = scheduledFor?.toString(),
        )
    }

    private fun medicineEntities(): List<MedicineEntity> {
        return listOf(
            medicineEntity(ESTRADIOL_MEDICINE_UUID, MedicationKey.ESTRADIOL),
            medicineEntity(SPIRONOLACTONE_MEDICINE_UUID, MedicationKey.SPIRONOLACTONE),
        )
    }

    private fun medicineEntity(uuid: String, key: MedicationKey): MedicineEntity {
        val preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0)
        return MedicineEntity(
            uuid = uuid,
            selectionKind = MedicationSelectionKind.CATALOG.name,
            medicationKey = key.name,
            customMedicationName = null,
            customMedicationNameNormalized = null,
            category = key.category.name,
            preparationType = MedicinePreparationType.PILL.name,
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
            identityKey = MedicineIdentityKey.catalog(key, preparation),
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
            archivedAtEpochMillis = null,
        )
    }

    private fun providedRegressionZone(): ZoneId {
        val tokyo = ZoneId.of("Asia/Tokyo")
        return if (ZoneId.systemDefault() == tokyo) {
            ZoneId.of("America/New_York")
        } else {
            tokyo
        }
    }

    private companion object {
        const val ESTRADIOL_MEDICINE_UUID = "e2e2e2e2-0000-0000-0000-000000000000"
        const val SPIRONOLACTONE_MEDICINE_UUID = "5a5a5a5a-0000-0000-0000-000000000000"
    }
}
