package com.mkx.hrttracker.data.repository

import com.mkx.hrttracker.data.local.DatabaseHolder
import com.mkx.hrttracker.data.local.HomeDao
import com.mkx.hrttracker.data.local.HrtTrackerDatabase
import com.mkx.hrttracker.data.local.MedicationGroupEntity
import com.mkx.hrttracker.data.local.MedicationGroupItemEntity
import com.mkx.hrttracker.data.local.MedicationGroupScheduleTimeEntity
import com.mkx.hrttracker.data.local.MedicationGroupWithItemsEntity
import com.mkx.hrttracker.data.local.MedicationLogEntryEntity
import com.mkx.hrttracker.data.local.UserProfileEntity
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationDoseKind
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
import com.mkx.hrttracker.model.pk.PkConcentrationUnit
import com.mkx.hrttracker.model.pk.PkTrendResult
import com.mkx.hrttracker.model.settings.SettingsState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class HomeRepositoryTest {
    private val databaseHolder: DatabaseHolder = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val homeSnapshotRepository: HomeSnapshotRepository = mockk()
    private val homeSnapshotStore: HomeSnapshotStore = mockk()
    private val homeSnapshotGenerationStore: HomeSnapshotGenerationStore = mockk()
    private val database: HrtTrackerDatabase = mockk()
    private val homeDao: HomeDao = mockk()

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

        val inputs = HomeRepository(
            databaseHolder = databaseHolder,
            settingsRepository = settingsRepository,
            homeSnapshotRepository = homeSnapshotRepository,
        ).observeHomeStartupInputs(now).first()

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
            payloadJson = "{}",
            latestEstradiolEntry = pkEntry.toMedicationLogEntryModel(),
        )
        val snapshot = HomeSnapshotRecord(
            schemaVersion = HOME_SNAPSHOT_SCHEMA_VERSION,
            generatedAtEpochMillis = 10L,
            anchorDateEpochDay = now.toLocalDate().toEpochDay(),
            zoneId = ZoneId.systemDefault().id,
            pkProjection = pkSnapshot,
            activeGroups = listOf(groupWithItems().toMedicationGroupModel()),
            scheduleEntries = listOf(scheduleEntry.toMedicationLogEntryModel()),
            antiandrogenHistoryEntries = listOf(antiandrogenHistoryEntry.toMedicationLogEntryModel()),
        )

        every { homeSnapshotStore.observeSnapshot() } returns flowOf(snapshot)
        every { homeSnapshotGenerationStore.observeGeneration() } returns MutableStateFlow(0L)
        coEvery { homeSnapshotGenerationStore.readGeneration() } returns 0L
        every { settingsRepository.settingsState } returns MutableStateFlow(settings)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val snapshotRepository = HomeSnapshotRepository(
            databaseHolder = databaseHolder,
            homeSnapshotStore = homeSnapshotStore,
            homeSnapshotGenerationStore = homeSnapshotGenerationStore,
            appScope = CoroutineScope(dispatcher),
            defaultDispatcher = dispatcher,
        )

        val inputs = HomeRepository(
            databaseHolder = databaseHolder,
            settingsRepository = settingsRepository,
            homeSnapshotRepository = snapshotRepository,
        ).observeHomeSnapshotInputs(now).first()

        assertEquals(HomeInputSource.SNAPSHOT, inputs.source)
        assertEquals(1, inputs.activeGroups.size)
        assertEquals(listOf(scheduleEntry.uuid), inputs.scheduleEntries.map { it.uuid.toString() })
        assertEquals(
            listOf(antiandrogenHistoryEntry.uuid),
            inputs.antiandrogenHistoryEntries.map { it.uuid.toString() },
        )
        assertEquals(pkEntry.uuid, inputs.latestEstradiolEntry?.uuid.toString())
        assertEquals(BloodUnitKey.NG_DL, inputs.settings.homeE2DisplayUnit)

        verify(exactly = 0) { databaseHolder.get() }
    }

    @Test
    fun observeHomeInputs_whenSnapshotMissingUsesRoomEstradiolFallbackWithoutRefreshing() = runTest {
        val now = LocalDateTime.of(2026, 5, 6, 10, 15)
        val settings = SettingsState(homeE2DisplayUnit = BloodUnitKey.PG_ML)
        val latestEstradiolEntry = logEntry(
            uuid = UUID.fromString("7dd8dc9c-2e3d-4c39-a55d-73042a9e60b3"),
            scheduledFor = null,
        )

        every { databaseHolder.get() } returns database
        every { database.homeDao() } returns homeDao
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
        every { homeSnapshotRepository.observeHomeSnapshot() } returns flowOf(null)
        every { homeSnapshotRepository.decodeProjection(null) } returns null
        every { homeSnapshotRepository.refreshHomeSnapshotAsync(any(), any()) } returns Unit

        val inputs = HomeRepository(
            databaseHolder = databaseHolder,
            settingsRepository = settingsRepository,
            homeSnapshotRepository = homeSnapshotRepository,
        ).observeHomeInputs(now).first()

        assertEquals(HomeInputSource.ROOM, inputs.source)
        assertEquals(
            listOf(latestEstradiolEntry.uuid),
            inputs.estradiolPkEntries.map { it.uuid.toString() },
        )
        assertEquals(latestEstradiolEntry.uuid, inputs.latestEstradiolEntry?.uuid.toString())
        verify(exactly = 0) { homeSnapshotRepository.refreshHomeSnapshotAsync(any(), any()) }
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
        every { homeSnapshotRepository.observeHomeSnapshot() } returnsMany listOf(
            snapshotRecords,
            roomSnapshotRecords,
        )
        every {
            homeSnapshotRepository.isSnapshotUsable(
                snapshot = any(),
                now = any(),
                zoneId = any(),
            )
        } returns true
        every {
            homeSnapshotRepository.scheduleEntriesForHome(
                snapshot = any(),
                now = any(),
                zoneId = any(),
            )
        } returns emptyList()
        every { homeSnapshotRepository.decodeProjection(null) } returns null

        val emittedSources = mutableListOf<HomeInputSource>()
        val firstRoomObserved = CompletableDeferred<Unit>()
        val repository = HomeRepository(
            databaseHolder = databaseHolder,
            settingsRepository = settingsRepository,
            homeSnapshotRepository = homeSnapshotRepository,
        )
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeHomeInputs(now).collect { inputs ->
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

    private fun groupWithItems(): MedicationGroupWithItemsEntity {
        val groupUuid = UUID.fromString("44bff3cb-b4dd-4be4-9eda-442cf91185c1").toString()
        return MedicationGroupWithItemsEntity(
            group = MedicationGroupEntity(
                uuid = groupUuid,
                name = "Home estradiol",
                colorKey = MedicationGroupColorKey.ORCHID.name,
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
                    category = MedicationCategory.ESTRADIOL.name,
                    applicationType = MedicationApplicationType.ORAL.name,
                    selectionKind = MedicationSelectionKind.CATALOG.name,
                    medicationKey = MedicationKey.ESTRADIOL.name,
                    customMedicationName = null,
                    doseKind = MedicationDoseKind.MG_AS_MEDICINE.name,
                    doseValueMg = 2.0,
                    doseValuePercent = null,
                    doseWeightGrams = null,
                    doseReleaseRateMcgPerDay = null,
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
            applicationType = MedicationApplicationType.ORAL.name,
            selectionKind = MedicationSelectionKind.CATALOG.name,
            medicationKey = medicationKey.name,
            customMedicationName = null,
            doseKind = MedicationDoseKind.MG_AS_MEDICINE.name,
            doseValueMg = 2.0,
            doseValuePercent = null,
            doseWeightGrams = null,
            doseReleaseRateMcgPerDay = null,
            dosageMgAsEstradiol = 2.0,
            sourceGroupUuid = null,
            appliedAtEpochMillis = appliedAt
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli(),
            scheduledForIso = scheduledFor?.toString(),
        )
    }
}
