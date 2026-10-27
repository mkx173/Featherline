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
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.journal.HeroBackground
import com.mkx.hrttracker.model.journal.TrackedDate
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
import com.mkx.hrttracker.model.medication.RunwayProjection
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import com.mkx.hrttracker.util.FakeAppTimeSource
import com.mkx.hrttracker.util.MedicationDayBoundary
import io.mockk.coEvery
import io.mockk.coVerify
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
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class HomeRepositoryTest {
    private val databaseHolder: DatabaseHolder = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val homeSnapshotRepository: HomeSnapshotRepository = mockk()
    private val homeSnapshotStore: HomeSnapshotStore = mockk()
    private val homeSnapshotGenerationStore: HomeSnapshotGenerationStore = mockk()
    private val medicineStockRepository: MedicineStockRepository = mockk()
    private val medicineRepository: MedicineRepository = mockk()
    private val medicationLogRepository: MedicationLogRepository = mockk()
    private val journalRepository: JournalRepository = mockk()
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
            medicineStockRepository.projectAll(any(), any(), any(), any(), any(), any())
        } returns emptyList()
        every { journalRepository.observePinnedTrackedDates() } returns flowOf(emptyList())
    }

    @Test
    fun observeHomeStartupInputs_usesBoundedScheduleWindow() = runTest {
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        val zoneId = ZoneId.systemDefault()
        val settings = SettingsState(homeE2DisplayUnit = BloodUnitKey.NG_DL)
        val latestEntryCutoffEpochMillis = now
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
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
                manualEndEpochMillis = LocalDate.of(2026, 5, 8)
                    .atStartOfDay(zoneId)
                    .toInstant()
                    .toEpochMilli(),
            )
        } returns flowOf(listOf(scheduleEntry))
        every {
            homeDao.observeLatestAntiandrogenEntriesOnOrBefore(
                onOrBeforeEpochMillis = latestEntryCutoffEpochMillis,
            )
        } returns flowOf(listOf(antiandrogenHistoryEntry))
        every {
            homeDao.observeLatestEstradiolEntryOnOrBefore(
                onOrBeforeEpochMillis = latestEntryCutoffEpochMillis,
            )
        } returns flowOf(null)
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
            journalRepository = journalRepository,
        ).observeHomeStartupInputs(now, zoneId).first()

        assertEquals(1, inputs.activeGroups.size)
        assertEquals(listOf(scheduleEntry.uuid), inputs.scheduleEntries.map { it.uuid.toString() })
        assertEquals(
            listOf(antiandrogenHistoryEntry.uuid),
            inputs.antiandrogenHistoryEntries.map { it.uuid.toString() },
        )
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
        val manualEndEpochMillis = today.plusDays(2)
            .atStartOfDay(providedZone)
            .toInstant()
            .toEpochMilli()
        val latestEntryCutoffEpochMillis = now
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
                onOrBeforeEpochMillis = latestEntryCutoffEpochMillis,
            )
        } returns flowOf(emptyList())
        every {
            homeDao.observeLatestEstradiolEntryOnOrBefore(
                onOrBeforeEpochMillis = latestEntryCutoffEpochMillis,
            )
        } returns flowOf(null)
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
            journalRepository = journalRepository,
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun observeHomeStartupInputs_suppressesTransientRestoreMedicineMismatch() = runTest {
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        val zoneId = ZoneId.systemDefault()
        val settings = SettingsState(homeE2DisplayUnit = BloodUnitKey.PG_ML)
        val medicineVersion = MutableStateFlow(0)
        val oldGroupUuid = UUID.fromString("dddddddd-0000-0000-0000-0000000000a1")
        val oldItemUuid = UUID.fromString("eeeeeeee-0000-0000-0000-0000000000a2")
        val oldMedicineUuid = UUID.fromString("ffffffff-0000-0000-0000-0000000000a3")
        val newGroupUuid = UUID.fromString("dddddddd-0000-0000-0000-0000000000b1")
        val newItemUuid = UUID.fromString("eeeeeeee-0000-0000-0000-0000000000b2")
        val newMedicineUuid = UUID.fromString("ffffffff-0000-0000-0000-0000000000b3")
        val groupsSource = MutableStateFlow(
            listOf(
                groupWithItems(
                    groupUuid = oldGroupUuid.toString(),
                    itemUuid = oldItemUuid.toString(),
                    medicineUuid = oldMedicineUuid.toString(),
                )
            )
        )
        var resolvableMedicineUuids = setOf(oldMedicineUuid.toString())
        val emittedInputs = mutableListOf<HomeStartupInputs>()
        // observeHomeStartupInputs runs upstream on flowOn(Dispatchers.IO), which the
        // TestCoroutineScheduler doesn't track, so advanceUntilIdle can't wait for its
        // emissions. Await real emissions via CompletableDeferred (no wall-clock
        // timeout; runTest's own timeout is the safety net) as the sibling flowOn(IO)
        // home tests do.
        val oldGroupObserved = CompletableDeferred<Unit>()
        // Completes with how the inconsistent restore pairing was handled:
        // "suppressed" via the suppression diagnostic, or "fabricated" if an
        // emission with fabricated empty groups reaches the collector. The
        // diagnostic doubles as the deterministic signal that the stale pairing
        // was actually processed BEFORE the groups flow catches up — otherwise
        // StateFlow conflation across flowOn(IO) could skip the stale pairing
        // and the test would never exercise the race.
        val stalePairingHandled = CompletableDeferred<String>()
        val restoredGroupObserved = CompletableDeferred<Unit>()
        val diagnosticsLogger = mockk<AppDiagnosticsLogger>(relaxed = true)
        every { diagnosticsLogger.warning(any(), any(), any()) } answers {
            if (secondArg<String>().contains("suppressed")) {
                stalePairingHandled.complete("suppressed")
            }
        }

        every { databaseHolder.get() } returns database
        every { database.homeDao() } returns homeDao
        every { database.medicineDao() } returns medicineDao
        every { medicineDao.observeMedicineChangeVersion() } returns medicineVersion
        coEvery { medicineDao.getByUuids(any()) } answers {
            firstArg<List<String>>()
                .filter { uuid -> uuid in resolvableMedicineUuids }
                .map { uuid -> medicineEntity(uuid, MedicationKey.ESTRADIOL) }
        }
        every { homeDao.observeActiveGroups() } returns groupsSource
        every { homeDao.observeScheduleEntries(any(), any(), any(), any()) } returns flowOf(
            emptyList()
        )
        every { homeDao.observeLatestAntiandrogenEntriesOnOrBefore(any()) } returns flowOf(emptyList())
        every { homeDao.observeLatestEstradiolEntryOnOrBefore(any()) } returns flowOf(null)
        every { settingsRepository.settingsState } returns MutableStateFlow(settings)
        every { settingsRepository.homeE2ChartWindowOptionFlow } returns MutableStateFlow(settings.homeE2ChartWindowOption)

        val repository = HomeRepository(
            databaseHolder = databaseHolder,
            settingsRepository = settingsRepository,
            homeSnapshotRepository = homeSnapshotRepository,
            medicineStockRepository = medicineStockRepository,
            medicineRepository = medicineRepository,
            medicationLogRepository = medicationLogRepository,
            journalRepository = journalRepository,
            diagnosticsLogger = diagnosticsLogger,
        )
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeHomeStartupInputs(now, zoneId).collect { inputs ->
                emittedInputs += inputs
                val groupIds = inputs.activeGroups.map { group -> group.uuid }
                when {
                    oldGroupUuid in groupIds -> oldGroupObserved.complete(Unit)
                    newGroupUuid in groupIds -> restoredGroupObserved.complete(Unit)
                    else -> stalePairingHandled.complete("fabricated")
                }
            }
        }

        oldGroupObserved.await()
        assertEquals(oldGroupUuid, emittedInputs.last().activeGroups.singleOrNull()?.uuid)

        // Restore commits: the medicines table is swapped to the NEW UUID set while
        // observeActiveGroups still holds the old group, so the combine re-resolves,
        // getByUuids can't find the old UUID, and toMedicationGroupModel()'s
        // checkNotNull(medicine) throws. The inconsistent pairing must be SUPPRESSED,
        // not degraded to a fabricated empty default: a fabricated emission renders a
        // one-frame wrong Home state before the consistent emission lands — the exact
        // stale-frame class this PR eliminates in the other repositories.
        resolvableMedicineUuids = setOf(newMedicineUuid.toString())
        medicineVersion.value = 1
        assertEquals(
            "the inconsistent restore pairing must be suppressed, not degraded " +
                    "to fabricated empty defaults",
            "suppressed",
            stalePairingHandled.await(),
        )

        // The groups flow catches up with the restored set. Without the per-emission
        // guard the flow died at the throw above and never re-emits, so this await never
        // completes and the test times out; with it the flow recovers and reflects the
        // restore.
        groupsSource.value = listOf(
            groupWithItems(
                groupUuid = newGroupUuid.toString(),
                itemUuid = newItemUuid.toString(),
                medicineUuid = newMedicineUuid.toString(),
            )
        )
        restoredGroupObserved.await()
        assertEquals(newGroupUuid, emittedInputs.last().activeGroups.singleOrNull()?.uuid)
        assertTrue(
            "no emission may carry fabricated empty groups during the restore race",
            emittedInputs.none { inputs -> inputs.activeGroups.isEmpty() },
        )
        collectJob.cancel()
    }

    @Test
    fun observeHomeSnapshotInputs_takesTheFirstFrameFromTheLoadedCache_withoutOpeningDatabase() = runTest {
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
        val snapshot = HomeSnapshotRecord(
            schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
            generatedAtEpochMillis = 10L,
            anchorDateEpochDay = now.toLocalDate().toEpochDay(),
            zoneId = ZoneId.systemDefault().id,
            activeGroups = listOf(groupWithItems().toMedicationGroupModel(medicinesByUuid)),
            scheduleEntries = listOf(scheduleEntry.toMedicationLogEntryModel(medicinesByUuid)),
            antiandrogenHistoryEntries = listOf(
                antiandrogenHistoryEntry.toMedicationLogEntryModel(medicinesByUuid)
            ),
            latestEstradiolEntry = pkEntry.toMedicationLogEntryModel(medicinesByUuid),
        )

        // Valid at the durable generation, the date and the zone, as the
        // last run wrote them: the load seeds the cache, and no run is asked for.
        coEvery { homeSnapshotStore.readSnapshot() } returns snapshot
        coEvery { homeSnapshotStore.readE2Inputs() } returns E2InputsRecord(
            generation = 0L,
            anchorDateEpochDay = snapshot.anchorDateEpochDay,
            zoneId = snapshot.zoneId,
            medicationDayBoundary = settings.medicationDayBoundary,
            option = settings.homeE2ChartWindowOption,
            weightKg = null,
            estradiolEntries = emptyList(),
            plannedSources = emptyList(),
        )
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
            e2EstimateRepository = { mockk() },
            appTimeSource = FakeAppTimeSource(now),
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
            journalRepository = journalRepository,
        ).observeHomeSnapshotInputs(now, zoneId).first()

        assertEquals(HomeInputSource.SNAPSHOT, inputs.source)
        assertEquals(1, inputs.activeGroups.size)
        assertEquals(listOf(scheduleEntry.uuid), inputs.scheduleEntries.map { it.uuid.toString() })
        assertEquals(
            listOf(antiandrogenHistoryEntry.uuid),
            inputs.antiandrogenHistoryEntries.map { it.uuid.toString() },
        )
        // The hero's last dose comes from the record's top level.
        assertEquals(pkEntry.uuid, inputs.latestEstradiolEntry?.uuid.toString())
        assertEquals(BloodUnitKey.NG_DL, inputs.settings.homeE2DisplayUnit)

        verify(exactly = 0) { databaseHolder.get() }
        coVerify(exactly = 0) { databaseHolder.awaitOpen() }
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
            activeGroups = emptyList(),
            scheduleEntries = listOf(snapshotScheduleEntry),
            antiandrogenHistoryEntries = emptyList(),
            stockMedicines = listOf(medicine),
            stockFulfillmentEntries = emptyList(),
        )

        every { settingsRepository.settingsState } returns MutableStateFlow(settings)
        every { settingsRepository.homeE2ChartWindowOptionFlow } returns MutableStateFlow(settings.homeE2ChartWindowOption)
        every { homeSnapshotRepository.currentCache } returns flowOf(HomeCacheState(0L, snapshot, e2Inputs = null))
        every {
            homeSnapshotRepository.scheduleEntriesForHome(
                snapshot = snapshot,
                now = now,
                zoneId = providedZone,
            )
        } returns listOf(snapshotScheduleEntry)
        every {
            medicineStockRepository.projectAll(
                medicines = listOf(medicine),
                activeGroups = emptyList(),
                logEntries = emptyList(),
                now = now.atZone(providedZone).toInstant(),
                zoneId = providedZone,
                boundary = MedicationDayBoundary.Midnight,
            )
        } returns emptyList()

        val inputs = HomeRepository(
            databaseHolder = databaseHolder,
            settingsRepository = settingsRepository,
            homeSnapshotRepository = homeSnapshotRepository,
            medicineStockRepository = medicineStockRepository,
            medicineRepository = medicineRepository,
            medicationLogRepository = medicationLogRepository,
            journalRepository = journalRepository,
        ).observeHomeSnapshotInputs(now, providedZone).first()

        assertEquals(HomeInputSource.SNAPSHOT, inputs.source)
        verify(exactly = 1) {
            homeSnapshotRepository.scheduleEntriesForHome(
                snapshot = snapshot,
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
                zoneId = providedZone,
                boundary = MedicationDayBoundary.Midnight,
            )
        }
    }

    @Test
    fun snapshotSource_surfacesHomeAnchorFromRecord() = runTest {
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        val zoneId = ZoneId.systemDefault()
        val settings = SettingsState(homeE2DisplayUnit = BloodUnitKey.NG_DL)
        val hero = heroTrackedDate()
        val snapshot = HomeSnapshotRecord(
            schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
            generatedAtEpochMillis = now.atZone(zoneId).toInstant().toEpochMilli(),
            anchorDateEpochDay = now.toLocalDate().toEpochDay(),
            zoneId = zoneId.id,
            activeGroups = emptyList(),
            scheduleEntries = emptyList(),
            antiandrogenHistoryEntries = emptyList(),
            homeAnchor = hero,
        )

        every { settingsRepository.settingsState } returns MutableStateFlow(settings)
        every { settingsRepository.homeE2ChartWindowOptionFlow } returns MutableStateFlow(settings.homeE2ChartWindowOption)
        every { homeSnapshotRepository.currentCache } returns flowOf(HomeCacheState(0L, snapshot, e2Inputs = null))
        every {
            homeSnapshotRepository.scheduleEntriesForHome(
                snapshot = snapshot,
                now = now,
                zoneId = zoneId,
            )
        } returns emptyList()

        val inputs = HomeRepository(
            databaseHolder = databaseHolder,
            settingsRepository = settingsRepository,
            homeSnapshotRepository = homeSnapshotRepository,
            medicineStockRepository = medicineStockRepository,
            medicineRepository = medicineRepository,
            medicationLogRepository = medicationLogRepository,
            journalRepository = journalRepository,
        ).observeHomeSnapshotInputs(now, zoneId).first()

        assertEquals(hero, inputs.homeAnchor)
    }

    @Test
    fun observeHomeInputs_whenSnapshotMissingUsesRoomLatestEstradiolEntry() =
        runTest {
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
            every { homeDao.observeScheduleEntries(any(), any(), any(), any()) } returns flowOf(
                emptyList()
            )
            every { homeDao.observeLatestAntiandrogenEntriesOnOrBefore(any()) } returns flowOf(
                emptyList()
            )
            every { homeDao.observeLatestEstradiolEntryOnOrBefore(any()) } returns flowOf(
                latestEstradiolEntry
            )
            every { settingsRepository.settingsState } returns MutableStateFlow(settings)
            every {
                settingsRepository.homeE2ChartWindowOptionFlow
            } returns MutableStateFlow(settings.homeE2ChartWindowOption)
            every { homeSnapshotRepository.currentCache } returns flowOf(null)

            val inputs = HomeRepository(
                databaseHolder = databaseHolder,
                settingsRepository = settingsRepository,
                homeSnapshotRepository = homeSnapshotRepository,
                medicineStockRepository = medicineStockRepository,
                medicineRepository = medicineRepository,
                medicationLogRepository = medicationLogRepository,
                journalRepository = journalRepository,
            ).observeHomeInputs(
                date = now.toLocalDate(),
                nowFlow = MutableStateFlow(now),
                zoneId = zoneId,
            ).first()

            assertEquals(HomeInputSource.ROOM, inputs.source)
            assertEquals(latestEstradiolEntry.uuid, inputs.latestEstradiolEntry?.uuid.toString())
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
        every { homeDao.observeScheduleEntries(any(), any(), any(), any()) } returns flowOf(
            emptyList()
        )
        every { homeDao.observeLatestAntiandrogenEntriesOnOrBefore(any()) } returns flowOf(emptyList())
        every { homeDao.observeLatestEstradiolEntryOnOrBefore(any()) } returns flowOf(null)
        every { settingsRepository.settingsState } returns MutableStateFlow(settings)
        every { settingsRepository.homeE2ChartWindowOptionFlow } returns MutableStateFlow(settings.homeE2ChartWindowOption)
        every { homeSnapshotRepository.currentCache } returns flowOf(null)
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
                zoneId = zoneId,
                boundary = MedicationDayBoundary.Midnight,
            )
        } returns listOf(projection)

        val inputs = HomeRepository(
            databaseHolder = databaseHolder,
            settingsRepository = settingsRepository,
            homeSnapshotRepository = homeSnapshotRepository,
            medicineStockRepository = medicineStockRepository,
            medicineRepository = medicineRepository,
            medicationLogRepository = medicationLogRepository,
            journalRepository = journalRepository,
        ).observeHomeInputs(
            date = now.toLocalDate(),
            nowFlow = MutableStateFlow(now),
            zoneId = zoneId,
        ).first()

        assertEquals(HomeInputSource.ROOM, inputs.source)
        assertEquals(listOf(projection), inputs.stockWarnings)
    }

    @Test
    fun roomSource_surfacesFirstPinnedDateAsHomeAnchor() = runTest {
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        val zoneId = ZoneId.systemDefault()
        val settings = SettingsState(homeE2DisplayUnit = BloodUnitKey.PG_ML)
        val hero = heroTrackedDate()

        every { databaseHolder.get() } returns database
        every { database.homeDao() } returns homeDao
        every { database.medicineDao() } returns medicineDao
        coEvery { medicineDao.getByUuids(any()) } returns emptyList()
        every { homeDao.observeActiveGroups() } returns flowOf(emptyList())
        every { homeDao.observeScheduleEntries(any(), any(), any(), any()) } returns flowOf(
            emptyList()
        )
        every { homeDao.observeLatestAntiandrogenEntriesOnOrBefore(any()) } returns flowOf(emptyList())
        every { homeDao.observeLatestEstradiolEntryOnOrBefore(any()) } returns flowOf(null)
        every { settingsRepository.settingsState } returns MutableStateFlow(settings)
        every { settingsRepository.homeE2ChartWindowOptionFlow } returns MutableStateFlow(settings.homeE2ChartWindowOption)
        every { homeSnapshotRepository.currentCache } returns flowOf(null)
        every { journalRepository.observePinnedTrackedDates() } returns flowOf(listOf(hero))

        val inputs = HomeRepository(
            databaseHolder = databaseHolder,
            settingsRepository = settingsRepository,
            homeSnapshotRepository = homeSnapshotRepository,
            medicineStockRepository = medicineStockRepository,
            medicineRepository = medicineRepository,
            medicationLogRepository = medicationLogRepository,
            journalRepository = journalRepository,
        ).observeHomeInputs(
            date = now.toLocalDate(),
            nowFlow = MutableStateFlow(now),
            zoneId = zoneId,
        ).first()

        assertEquals(HomeInputSource.ROOM, inputs.source)
        assertEquals(hero, inputs.homeAnchor)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun observeHomeInputs_dedupsRepeatedPinnedDateEmissionsBeforeProjectionWork() = runTest {
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        val zoneId = ZoneId.systemDefault()
        val settings = SettingsState(homeE2DisplayUnit = BloodUnitKey.PG_ML)
        val hero = heroTrackedDate()
        val pinnedDates = MutableSharedFlow<List<TrackedDate>>(replay = 1)
        pinnedDates.emit(listOf(hero))
        var projectionCalls = 0

        every { databaseHolder.get() } returns database
        every { database.homeDao() } returns homeDao
        every { database.medicineDao() } returns medicineDao
        coEvery { medicineDao.getByUuids(any()) } returns emptyList()
        every { homeDao.observeActiveGroups() } returns flowOf(emptyList())
        every { homeDao.observeScheduleEntries(any(), any(), any(), any()) } returns flowOf(
            emptyList()
        )
        every { homeDao.observeLatestAntiandrogenEntriesOnOrBefore(any()) } returns flowOf(
            emptyList()
        )
        every { homeDao.observeLatestEstradiolEntryOnOrBefore(any()) } returns flowOf(null)
        every { settingsRepository.settingsState } returns MutableStateFlow(settings)
        every { settingsRepository.homeE2ChartWindowOptionFlow } returns MutableStateFlow(
            settings.homeE2ChartWindowOption
        )
        every { homeSnapshotRepository.currentCache } returns flowOf(null)
        every { journalRepository.observePinnedTrackedDates() } returns pinnedDates
        every {
            medicineStockRepository.projectAll(any(), any(), any(), any(), any(), any())
        } answers {
            projectionCalls += 1
            emptyList()
        }

        val emissions = mutableListOf<HomeInputs>()
        val firstEmissionObserved = CompletableDeferred<Unit>()
        val secondEmissionObserved = CompletableDeferred<Unit>()
        val repository = HomeRepository(
            databaseHolder = databaseHolder,
            settingsRepository = settingsRepository,
            homeSnapshotRepository = homeSnapshotRepository,
            medicineStockRepository = medicineStockRepository,
            medicineRepository = medicineRepository,
            medicationLogRepository = medicationLogRepository,
            journalRepository = journalRepository,
        )
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeHomeInputs(
                date = now.toLocalDate(),
                nowFlow = MutableStateFlow(now),
                zoneId = zoneId,
            ).collect { inputs ->
                emissions += inputs
                firstEmissionObserved.complete(Unit)
                if (emissions.size >= 2) {
                    secondEmissionObserved.complete(Unit)
                }
            }
        }

        firstEmissionObserved.await()
        assertEquals(hero, emissions.single().homeAnchor)
        assertEquals(1, projectionCalls)

        pinnedDates.emit(listOf(hero))
        val duplicateEmission = withTimeoutOrNull(250) { secondEmissionObserved.await() }

        assertEquals(null, duplicateEmission)
        assertEquals(1, projectionCalls)
        collectJob.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun observeHomeInputs_recomputesRoomStockWarningsWhenNowFlowTicksWithoutRequeryingRoomWindows() =
        runTest {
            val now = LocalDateTime.of(2026, 5, 6, 10, 15)
            val later = now.plusMinutes(5)
            val zoneId = ZoneId.systemDefault()
            val nowFlow = MutableStateFlow(now)
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
            val nowInstant = now.atZone(zoneId).toInstant()
            val laterInstant = later.atZone(zoneId).toInstant()

            every { databaseHolder.get() } returns database
            every { database.homeDao() } returns homeDao
            every { database.medicineDao() } returns medicineDao
            coEvery { medicineDao.getByUuids(any()) } returns emptyList()
            every { homeDao.observeActiveGroups() } returns flowOf(emptyList())
            every {
                homeDao.observeScheduleEntries(
                    scheduledStartIso = "2026-05-05T00:00",
                    scheduledEndIso = "2026-08-04T23:59:59",
                    manualStartEpochMillis = LocalDate.of(2026, 5, 5)
                        .atStartOfDay(zoneId)
                        .toInstant()
                        .toEpochMilli(),
                    manualEndEpochMillis = LocalDate.of(2026, 5, 8)
                        .atStartOfDay(zoneId)
                        .toInstant()
                        .toEpochMilli(),
                )
            } returns flowOf(emptyList())
            every { homeDao.observeLatestAntiandrogenEntriesOnOrBefore(any()) } returns flowOf(
                emptyList()
            )
            every { homeDao.observeLatestEstradiolEntryOnOrBefore(any()) } returns flowOf(null)
            every { settingsRepository.settingsState } returns MutableStateFlow(settings)
            every { settingsRepository.homeE2ChartWindowOptionFlow } returns MutableStateFlow(
                settings.homeE2ChartWindowOption
            )
            every { homeSnapshotRepository.currentCache } returns flowOf(null)
            every { medicineRepository.observeAllActiveTracked() } returns flowOf(listOf(medicine))
            every {
                medicationLogRepository.observeScheduledEntriesInWindow(any(), any())
            } returns flowOf(emptyList())
            every {
                medicineStockRepository.projectAll(
                    medicines = listOf(medicine),
                    activeGroups = emptyList(),
                    logEntries = emptyList(),
                    now = nowInstant,
                    zoneId = zoneId,
                    boundary = MedicationDayBoundary.Midnight,
                )
            } returns emptyList()
            every {
                medicineStockRepository.projectAll(
                    medicines = listOf(medicine),
                    activeGroups = emptyList(),
                    logEntries = emptyList(),
                    now = laterInstant,
                    zoneId = zoneId,
                    boundary = MedicationDayBoundary.Midnight,
                )
            } returns listOf(projection)

            val emittedInputs = mutableListOf<HomeInputs>()
            val firstEmissionObserved = CompletableDeferred<Unit>()
            val secondEmissionObserved = CompletableDeferred<Unit>()
            val repository = HomeRepository(
                databaseHolder = databaseHolder,
                settingsRepository = settingsRepository,
                homeSnapshotRepository = homeSnapshotRepository,
                medicineStockRepository = medicineStockRepository,
                medicineRepository = medicineRepository,
                medicationLogRepository = medicationLogRepository,
                journalRepository = journalRepository,
            )
            val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                repository.observeHomeInputs(
                    date = now.toLocalDate(),
                    nowFlow = nowFlow,
                    zoneId = zoneId,
                ).collect { inputs ->
                    emittedInputs += inputs
                    firstEmissionObserved.complete(Unit)
                    if (emittedInputs.size >= 2) {
                        secondEmissionObserved.complete(Unit)
                    }
                }
            }
            firstEmissionObserved.await()
            assertEquals(emptyList<MedicineStockProjection>(), emittedInputs.single().stockWarnings)

            nowFlow.value = later
            secondEmissionObserved.await()

            assertEquals(listOf(projection), emittedInputs.last().stockWarnings)
            verify(exactly = 1) {
                medicineStockRepository.projectAll(
                    medicines = listOf(medicine),
                    activeGroups = emptyList(),
                    logEntries = emptyList(),
                    now = nowInstant,
                    zoneId = zoneId,
                    boundary = MedicationDayBoundary.Midnight,
                )
            }
            verify(exactly = 1) {
                medicineStockRepository.projectAll(
                    medicines = listOf(medicine),
                    activeGroups = emptyList(),
                    logEntries = emptyList(),
                    now = laterInstant,
                    zoneId = zoneId,
                    boundary = MedicationDayBoundary.Midnight,
                )
            }
            verify(exactly = 1) {
                homeDao.observeScheduleEntries(
                    scheduledStartIso = any(),
                    scheduledEndIso = any(),
                    manualStartEpochMillis = any(),
                    manualEndEpochMillis = any(),
                )
            }
            verify(exactly = 1) { medicineRepository.observeAllActiveTracked() }
            collectJob.cancel()
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
            activeGroups = emptyList(),
            scheduleEntries = emptyList(),
            antiandrogenHistoryEntries = emptyList(),
            stockMedicines = listOf(medicine),
            stockFulfillmentEntries = emptyList(),
        )

        every { settingsRepository.settingsState } returns MutableStateFlow(settings)
        every { settingsRepository.homeE2ChartWindowOptionFlow } returns MutableStateFlow(settings.homeE2ChartWindowOption)
        every { homeSnapshotRepository.currentCache } returns flowOf(HomeCacheState(0L, snapshot, e2Inputs = null))
        every {
            homeSnapshotRepository.scheduleEntriesForHome(
                any(),
                any(),
                any()
            )
        } returns emptyList()
        every {
            medicineStockRepository.projectAll(
                medicines = listOf(medicine),
                activeGroups = emptyList(),
                logEntries = emptyList(),
                now = now.atZone(zoneId).toInstant(),
                zoneId = zoneId,
                boundary = MedicationDayBoundary.Midnight,
            )
        } returns listOf(projection)

        val inputs = HomeRepository(
            databaseHolder = databaseHolder,
            settingsRepository = settingsRepository,
            homeSnapshotRepository = homeSnapshotRepository,
            medicineStockRepository = medicineStockRepository,
            medicineRepository = medicineRepository,
            medicationLogRepository = medicationLogRepository,
            journalRepository = journalRepository,
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
        val snapshotRecords = MutableSharedFlow<HomeCacheState?>(replay = 1)
        val roomSnapshotRecords = MutableSharedFlow<HomeCacheState?>(replay = 1)
        val snapshot = HomeSnapshotRecord(
            schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
            generatedAtEpochMillis = now.atZone(zoneId).toInstant().toEpochMilli(),
            anchorDateEpochDay = now.toLocalDate().toEpochDay(),
            zoneId = zoneId.id,
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
        every { homeDao.observeScheduleEntries(any(), any(), any(), any()) } returns flowOf(
            emptyList()
        )
        every { homeDao.observeLatestAntiandrogenEntriesOnOrBefore(any()) } returns flowOf(emptyList())
        every { homeDao.observeLatestEstradiolEntryOnOrBefore(any()) } returns flowOf(null)
        every { settingsRepository.settingsState } returns MutableStateFlow(settings)
        every {
            settingsRepository.homeE2ChartWindowOptionFlow
        } returns MutableStateFlow(settings.homeE2ChartWindowOption)
        every { homeSnapshotRepository.currentCache } returnsMany listOf(
            snapshotRecords,
            roomSnapshotRecords,
        )
        every {
            homeSnapshotRepository.scheduleEntriesForHome(
                snapshot = any(),
                now = any(),
                zoneId = any(),
            )
        } returns emptyList()

        val emittedSources = mutableListOf<HomeInputSource>()
        val firstRoomObserved = CompletableDeferred<Unit>()
        val repository = HomeRepository(
            databaseHolder = databaseHolder,
            settingsRepository = settingsRepository,
            homeSnapshotRepository = homeSnapshotRepository,
            medicineStockRepository = medicineStockRepository,
            medicineRepository = medicineRepository,
            medicationLogRepository = medicationLogRepository,
            journalRepository = journalRepository,
        )
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeHomeInputs(
                date = now.toLocalDate(),
                nowFlow = MutableStateFlow(now),
                zoneId = zoneId,
            ).collect { inputs ->
                emittedSources += inputs.source
                if (inputs.source == HomeInputSource.ROOM) {
                    firstRoomObserved.complete(Unit)
                }
            }
        }
        firstRoomObserved.await()

        assertEquals(listOf(HomeInputSource.ROOM), emittedSources)

        snapshotRecords.emit(HomeCacheState(0L, snapshot, e2Inputs = null))
        advanceUntilIdle()

        assertEquals(false, emittedSources.contains(HomeInputSource.SNAPSHOT))
        collectJob.cancel()
    }

    private fun heroTrackedDate(): TrackedDate {
        return TrackedDate(
            id = "hero-1",
            name = "HRT start",
            icon = AnchorIcon.entries.first(),
            date = LocalDate.of(2025, 3, 14),
            palette = null,
            heroBackground = HeroBackground.DateColor,
            pinnedOrder = 0,
            createdAtEpochMillis = 1L,
        )
    }

    private fun groupWithItems(
        groupUuid: String = UUID.fromString("44bff3cb-b4dd-4be4-9eda-442cf91185c1").toString(),
        itemUuid: String = UUID.fromString("79dfe41c-b684-4e48-858d-d47d369b5b13").toString(),
        medicineUuid: String = ESTRADIOL_MEDICINE_UUID,
    ): MedicationGroupWithItemsEntity {
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
                    uuid = itemUuid,
                    groupUuid = groupUuid,
                    sortOrder = 0,
                    count = 1,
                    medicineUuid = medicineUuid,
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
