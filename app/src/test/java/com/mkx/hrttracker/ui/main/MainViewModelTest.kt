package com.mkx.hrttracker.ui.main

import com.mkx.hrttracker.data.repository.HomeInputSource
import com.mkx.hrttracker.data.repository.HomeInputs
import com.mkx.hrttracker.data.repository.HomeRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationDose
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.testCatalogMedicationDetails
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.pk.PkConcentrationUnit
import com.mkx.hrttracker.model.pk.PkTrendResult
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.FakeAppTimeSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val homeRepository: HomeRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { homeRepository.refreshHomeSnapshotAsync(any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun clockTickAcrossMidnight_updatesTodaySectionDateAndRefreshesSnapshot() = runTest {
        val firstMinute = LocalDateTime.of(2026, 4, 30, 23, 59)
        val appTimeSource = FakeAppTimeSource(firstMinute)
        every { homeRepository.observeHomeInputs(any()) } answers {
            flowOf(homeInputs(now = firstArg()))
        }

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            appTimeSource = appTimeSource,
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 4, 30), viewModel.uiState.value.todaySection.date)
        verify(exactly = 1) { homeRepository.refreshHomeSnapshotAsync(firstMinute, force = false) }

        val nextMinute = LocalDateTime.of(2026, 5, 1, 0, 0)
        appTimeSource.setCurrentMinute(nextMinute)
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 5, 1), viewModel.uiState.value.todaySection.date)
        verify(exactly = 1) { homeRepository.refreshHomeSnapshotAsync(nextMinute, force = false) }
    }

    @Test
    fun homeSnapshotInputRendersCompleteHomeStateWithoutSkeletonGate() = runTest {
        val now = LocalDateTime.of(2026, 4, 30, 12, 0)
        val appTimeSource = FakeAppTimeSource(now)
        every { homeRepository.observeHomeInputs(any()) } returns flowOf(
            homeInputs(
                now = now,
                activeGroups = listOf(medicationGroup()),
                trendResult = PkTrendResult(
                    currentConcentration = 124.0,
                    previousDayConcentration = 100.0,
                    dailyConcentrations = listOf(80.0, 92.0, 100.0, 124.0),
                    concentrationUnit = PkConcentrationUnit.PG_PER_ML,
                ),
                source = HomeInputSource.SNAPSHOT,
            )
        )

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            appTimeSource = appTimeSource,
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.homeDataReady)
        assertEquals(HomeInputSource.SNAPSHOT, viewModel.uiState.value.homeSource)
        assertEquals(true, viewModel.uiState.value.splashReady)
        assertEquals(124.0, viewModel.uiState.value.e2Hero.currentValue, 1e-9)
        assertEquals(LocalDate.of(2026, 4, 30), viewModel.uiState.value.todaySection.date)
    }

    @Test
    fun staticSplashWaitsForSnapshotOrRoomInput() = runTest {
        val now = LocalDateTime.of(2026, 4, 30, 12, 0)
        val appTimeSource = FakeAppTimeSource(now)
        val homeInputFlow = MutableSharedFlow<HomeInputs>()
        every { homeRepository.observeHomeInputs(any()) } returns homeInputFlow

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            appTimeSource = appTimeSource,
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.homeDataReady)
        assertEquals(false, viewModel.uiState.value.splashReady)

        homeInputFlow.emit(homeInputs(now = now, source = HomeInputSource.ROOM))
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.homeDataReady)
        assertEquals(true, viewModel.uiState.value.splashReady)
        assertEquals(HomeInputSource.ROOM, viewModel.uiState.value.homeSource)
    }

    @Test
    fun homeE2DisplayUnit_usesHomePreferenceFromInputs() = runTest {
        val appTimeSource = FakeAppTimeSource(LocalDateTime.of(2026, 4, 30, 12, 0))
        every { homeRepository.observeHomeInputs(any()) } returns flowOf(
            homeInputs(
                settings = SettingsState(
                    calibrationDefaultUnits = mapOf(BloodAnalyteKey.E2 to BloodUnitKey.PMOL_L),
                    homeE2DisplayUnit = BloodUnitKey.NG_DL,
                ),
            )
        )

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            appTimeSource = appTimeSource,
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        assertEquals(BloodUnitKey.NG_DL, viewModel.uiState.value.homeE2DisplayUnit)
        assertEquals("ng/dL", viewModel.uiState.value.e2Hero.unit)
        assertEquals(10.0, viewModel.uiState.value.e2Hero.targetMin, 1e-9)
        assertEquals(20.0, viewModel.uiState.value.e2Hero.targetMax, 1e-9)
    }

    @Test
    fun roomTakeoverUpdatesHomeSourceAfterSnapshotFirstPaint() = runTest {
        val now = LocalDateTime.of(2026, 4, 30, 12, 0)
        val appTimeSource = FakeAppTimeSource(now)
        val inputs = MutableSharedFlow<HomeInputs>()
        every { homeRepository.observeHomeInputs(any()) } returns inputs

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            appTimeSource = appTimeSource,
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        inputs.emit(homeInputs(now = now, source = HomeInputSource.SNAPSHOT))
        advanceUntilIdle()
        assertEquals(HomeInputSource.SNAPSHOT, viewModel.uiState.value.homeSource)

        inputs.emit(homeInputs(now = now, source = HomeInputSource.ROOM))
        advanceUntilIdle()
        assertEquals(HomeInputSource.ROOM, viewModel.uiState.value.homeSource)
    }

    @Test
    fun roomInputWithoutProjectionComputesE2FallbackFromRoomEntries() = runTest {
        val now = LocalDateTime.of(2026, 4, 30, 12, 0)
        val latestEstradiolEntry = MedicationLogEntry(
            uuid = UUID.fromString("9b24ff3a-fc93-4fd1-8760-81a9ae8eae04"),
            details = testCatalogMedicationDetails(
                key = MedicationKey.ESTRADIOL,
                applicationType = MedicationApplicationType.ORAL,
                dose = MedicationDose.MgAsMedicine(2.0),
            ),
            dosageMgAsEstradiol = 2.0,
            sourceGroupUuid = null,
            appliedAt = Instant.parse("2026-04-30T00:00:00Z"),
            appliedAtTimeZoneId = "UTC",
        )
        val appTimeSource = FakeAppTimeSource(now)
        every { homeRepository.observeHomeInputs(any()) } returns flowOf(
            homeInputs(
                now = now,
                source = HomeInputSource.ROOM,
                trendResult = null,
                latestEstradiolEntry = latestEstradiolEntry,
                estradiolPkEntries = listOf(latestEstradiolEntry),
            )
        )

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            appTimeSource = appTimeSource,
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        assertEquals(HomeInputSource.ROOM, viewModel.uiState.value.homeSource)
        assertEquals(
            latestEstradiolEntry.appliedAt.atZone(java.time.ZoneId.systemDefault()).toLocalDateTime(),
            viewModel.uiState.value.e2Hero.lastDoseAt,
        )
        assertTrue(viewModel.uiState.value.e2Hero.currentValue > 0.0)
    }

    private fun TestScope.startUiStateCollection(viewModel: MainViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
    }

    private fun homeInputs(
        now: LocalDateTime = LocalDateTime.of(2026, 4, 30, 12, 0),
        activeGroups: List<MedicationGroup> = emptyList(),
        settings: SettingsState = SettingsState(),
        trendResult: PkTrendResult? = PkTrendResult(
            currentConcentration = 100.0,
            previousDayConcentration = 90.0,
            dailyConcentrations = listOf(70.0, 80.0, 90.0, 100.0),
            concentrationUnit = PkConcentrationUnit.PG_PER_ML,
        ),
        latestEstradiolEntry: MedicationLogEntry? = null,
        estradiolPkEntries: List<MedicationLogEntry> = emptyList(),
        source: HomeInputSource = HomeInputSource.SNAPSHOT,
    ): HomeInputs {
        return HomeInputs(
            activeGroups = activeGroups,
            scheduleEntries = emptyList(),
            antiandrogenHistoryEntries = emptyList(),
            profile = UserProfile(weightKg = 60.0),
            settings = settings,
            trendResult = trendResult,
            latestEstradiolEntry = latestEstradiolEntry,
            estradiolPkEntries = estradiolPkEntries,
            source = source,
            now = now,
        )
    }

    private fun medicationGroup(): MedicationGroup {
        return MedicationGroup(
            uuid = UUID.fromString("0961b1af-d7db-43f7-b66a-8b82b9faefaf"),
            name = "Estradiol",
            schedule = MedicationGroupSchedule(
                type = MedicationGroupScheduleType.DAILY,
                interval = 1,
                since = LocalDate.of(2026, 4, 1),
                weeklyDaysOfWeek = emptySet(),
                times = listOf(LocalTime.of(8, 0)),
            ),
            medications = listOf(
                testMedicationGroupMedication(
                    uuid = UUID.fromString("bf3e810c-0ade-4db7-8ca6-ef85b93b2c3f"),
                    details = testCatalogMedicationDetails(
                        key = MedicationKey.ESTRADIOL,
                        applicationType = MedicationApplicationType.ORAL,
                        dose = MedicationDose.MgAsMedicine(2.0),
                    )
                )
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z"),
        )
    }
}
