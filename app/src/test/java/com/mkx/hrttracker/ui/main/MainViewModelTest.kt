package com.mkx.hrttracker.ui.main

import com.mkx.hrttracker.data.repository.HomeInputSource
import com.mkx.hrttracker.data.repository.HomeInputs
import com.mkx.hrttracker.data.repository.HomeRepository
import com.mkx.hrttracker.data.repository.RunwayProjection
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.pk.PkConcentrationUnit
import com.mkx.hrttracker.model.pk.PkMedicationSimulation
import com.mkx.hrttracker.model.pk.PkProjectionResult
import com.mkx.hrttracker.model.pk.PkTrendResult
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.FakeAppTimeSource
import com.mkx.hrttracker.util.TimeZoneChangeNoticeController
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.time.ZoneId
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val homeRepository: HomeRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val timeZoneChangeNoticeController: TimeZoneChangeNoticeController = mockk()
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { homeRepository.refreshHomeSnapshotAsync(any(), any()) } returns Unit
        every { timeZoneChangeNoticeController.notice } returns MutableStateFlow(null)
        every { settingsRepository.homeLowStockSectionExpandedFlow } returns MutableStateFlow(true)
        coEvery { settingsRepository.setHomeLowStockSectionExpanded(any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun clockTickWithinSameDay_doesNotResubscribeOrRefreshSnapshot() = runTest {
        val firstMinute = LocalDateTime.of(2026, 4, 30, 9, 0)
        val appTimeSource = FakeAppTimeSource(firstMinute)
        every { homeRepository.observeHomeInputs(any()) } answers {
            flowOf(homeInputs(now = firstArg()))
        }

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            appTimeSource = appTimeSource,
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        verify(exactly = 1) { homeRepository.observeHomeInputs(firstMinute) }
        verify(exactly = 1) { homeRepository.refreshHomeSnapshotAsync(firstMinute, force = false) }

        appTimeSource.setCurrentMinute(firstMinute.plusMinutes(1))
        advanceUntilIdle()
        appTimeSource.setCurrentMinute(firstMinute.plusMinutes(2))
        advanceUntilIdle()

        verify(exactly = 1) { homeRepository.observeHomeInputs(any()) }
        verify(exactly = 1) { homeRepository.refreshHomeSnapshotAsync(any(), any()) }
        assertEquals(firstMinute.plusMinutes(2), viewModel.uiState.value.now)
    }

    @Test
    fun clockTickWithinSameDay_recomputesProjectionTrendWithLatestMinute() = runTest {
        val firstMinute = LocalDateTime.of(2026, 4, 30, 9, 0)
        val nextMinute = firstMinute.plusMinutes(1)
        val zoneId = ZoneId.systemDefault()
        val appTimeSource = FakeAppTimeSource(firstMinute)
        every { homeRepository.observeHomeInputs(any()) } returns flowOf(
            homeInputs(
                now = firstMinute,
                pkProjection = linearProjectionFor(firstMinute.toLocalDate(), zoneId),
                trendResult = null,
            )
        )

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            appTimeSource = appTimeSource,
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()
        val firstValue = viewModel.uiState.value.e2Hero.currentValue
        val firstPredictionStart = viewModel.uiState.value.e2Chart.predictionStartXHours

        appTimeSource.setCurrentMinute(nextMinute)
        advanceUntilIdle()

        verify(exactly = 1) { homeRepository.observeHomeInputs(any()) }
        assertEquals(
            firstPredictionStart + (1.0 / 60.0),
            viewModel.uiState.value.e2Chart.predictionStartXHours,
            1e-4,
        )
        assertTrue(viewModel.uiState.value.e2Hero.currentValue > firstValue)
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
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
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
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
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
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
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
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
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
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
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
            medicine = testMedicine(key = MedicationKey.ESTRADIOL),
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            equivalentE2Mg = 2.0,
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
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
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

    @Test
    fun expiredProjectionFallsBackToRoomTrendAgainstLiveNow() = runTest {
        // Sanity: HomeInputs carries a non-null pkProjection (currentConcentration 100)
        // but pkProjectionExpiresAt is in the past relative to `now`. The
        // MainViewModel must drop the stale curve and fall back to
        // simulateMainEstradiolTrend, which against empty real+planned entries
        // produces zero — so the hero current value must be 0, not 100.
        val now = LocalDateTime.of(2026, 4, 30, 12, 0)
        val zoneId = ZoneId.systemDefault()
        val expiredAt = now.minusHours(1).atZone(zoneId).toInstant()
        val appTimeSource = FakeAppTimeSource(now)
        every { homeRepository.observeHomeInputs(any()) } returns flowOf(
            homeInputs(
                now = now,
                trendResult = PkTrendResult(
                    currentConcentration = 100.0,
                    previousDayConcentration = 90.0,
                    dailyConcentrations = listOf(70.0, 80.0, 90.0, 100.0),
                    concentrationUnit = PkConcentrationUnit.PG_PER_ML,
                ),
                pkProjectionExpiresAt = expiredAt,
            )
        )

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            appTimeSource = appTimeSource,
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        assertEquals(0.0, viewModel.uiState.value.e2Hero.currentValue, 1e-9)
    }

    @Test
    fun pastDuePlannedEntryIsFilteredFromTrendFallback() = runTest {
        // A planned virtual entry whose scheduledFor is in the past relative to
        // live `now` must NOT flow into the trend simulator — the user didn't
        // take it, so the curve must not include its contribution.
        val now = LocalDateTime.of(2026, 4, 30, 12, 0)
        val pastDuePlanned = MedicationLogEntry(
            uuid = UUID.fromString("aaaaaaaa-1111-2222-3333-444444444444"),
            medicine = testMedicine(key = MedicationKey.ESTRADIOL),
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            equivalentE2Mg = null,
            sourceGroupUuid = UUID.fromString("bbbbbbbb-1111-2222-3333-444444444444"),
            appliedAt = Instant.parse("2026-04-30T01:00:00Z"),
            appliedAtTimeZoneId = "UTC",
            scheduledFor = now.minusHours(1),
            scheduleTimeUuid = UUID.fromString("cccccccc-1111-2222-3333-444444444444"),
        )
        val appTimeSource = FakeAppTimeSource(now)
        every { homeRepository.observeHomeInputs(any()) } returns flowOf(
            homeInputs(
                now = now,
                source = HomeInputSource.ROOM,
                trendResult = null,
                estradiolPkEntries = emptyList(),
                estradiolPkPlannedEntries = listOf(pastDuePlanned),
            )
        )

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            appTimeSource = appTimeSource,
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        // Empty real + filtered planned = trend has no doses → currentValue == 0.
        assertEquals(0.0, viewModel.uiState.value.e2Hero.currentValue, 1e-9)
    }

    @Test
    fun stockWarningsAndFoldStateFlowIntoUiState() = runTest {
        val now = LocalDateTime.of(2026, 4, 30, 12, 0)
        val projection = stockWarningProjection()
        val expandedFlow = MutableStateFlow(false)
        val appTimeSource = FakeAppTimeSource(now)
        every { settingsRepository.homeLowStockSectionExpandedFlow } returns expandedFlow
        every { homeRepository.observeHomeInputs(any()) } returns flowOf(
            homeInputs(
                now = now,
                stockWarnings = listOf(projection),
            )
        )

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            appTimeSource = appTimeSource,
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        assertEquals(listOf(projection), viewModel.uiState.value.stockWarnings)
        assertEquals(false, viewModel.uiState.value.lowStockSectionExpanded)

        expandedFlow.value = true
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.lowStockSectionExpanded)
    }

    @Test
    fun setLowStockSectionExpandedPersistsPreference() = runTest {
        val now = LocalDateTime.of(2026, 4, 30, 12, 0)
        val appTimeSource = FakeAppTimeSource(now)
        every { homeRepository.observeHomeInputs(any()) } returns flowOf(homeInputs(now = now))

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            appTimeSource = appTimeSource,
            defaultDispatcher = dispatcher,
        )

        viewModel.setLowStockSectionExpanded(false)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            settingsRepository.setHomeLowStockSectionExpanded(false)
        }
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
        pkProjection: PkProjectionResult? = null,
        pkProjectionExpiresAt: Instant? = null,
        latestEstradiolEntry: MedicationLogEntry? = null,
        estradiolPkEntries: List<MedicationLogEntry> = emptyList(),
        estradiolPkPlannedEntries: List<MedicationLogEntry> = emptyList(),
        stockWarnings: List<MedicineStockProjection> = emptyList(),
        source: HomeInputSource = HomeInputSource.SNAPSHOT,
    ): HomeInputs {
        return HomeInputs(
            activeGroups = activeGroups,
            scheduleEntries = emptyList(),
            antiandrogenHistoryEntries = emptyList(),
            profile = UserProfile(weightKg = 60.0),
            settings = settings,
            pkProjection = pkProjection ?: trendResult?.toProjection(now),
            pkProjectionExpiresAt = pkProjectionExpiresAt,
            latestEstradiolEntry = latestEstradiolEntry,
            estradiolPkEntries = estradiolPkEntries,
            estradiolPkPlannedEntries = estradiolPkPlannedEntries,
            stockWarnings = stockWarnings,
            source = source,
            now = now,
        )
    }

    private fun PkTrendResult.toProjection(now: LocalDateTime): PkProjectionResult {
        val zoneId = ZoneId.systemDefault()
        val windowStart = now
            .toLocalDate()
            .atStartOfDay()
            .minusDays(PkMedicationSimulation.mainChartPastDays)
            .atZone(zoneId)
            .toInstant()
        val windowEnd = windowStart.plusSeconds(
            PkMedicationSimulation.mainChartWindowHours * 60 * 60
        )
        val currentTimeH = java.time.Duration.between(windowStart, now.atZone(zoneId).toInstant())
            .toMillis() / 3_600_000.0
        val previousDayTimeH = currentTimeH - PkMedicationSimulation.hoursPerDay
        val points = buildMap<Double, Double> {
            dailyConcentrations.forEachIndexed { index, concentration ->
                put(index * PkMedicationSimulation.hoursPerDay, concentration)
            }
            put(previousDayTimeH, previousDayConcentration)
            put(currentTimeH, currentConcentration)
        }.toSortedMap()
        return PkProjectionResult(
            generatedAt = now.atZone(zoneId).toInstant(),
            windowStart = windowStart,
            windowEnd = windowEnd,
            concentrationUnit = concentrationUnit,
            timeH = points.keys.toList(),
            concentrations = points.values.toList(),
            doseMarkers = doseMarkers,
        )
    }

    private fun linearProjectionFor(
        date: LocalDate,
        zoneId: ZoneId,
    ): PkProjectionResult {
        val windowStart = date
            .atStartOfDay()
            .minusDays(PkMedicationSimulation.mainChartPastDays)
            .atZone(zoneId)
            .toInstant()
        val windowEnd = windowStart.plusSeconds(
            PkMedicationSimulation.mainChartWindowHours * 60 * 60
        )
        val windowHours = PkMedicationSimulation.mainChartWindowHours.toDouble()
        return PkProjectionResult(
            generatedAt = date.atTime(9, 0).atZone(zoneId).toInstant(),
            windowStart = windowStart,
            windowEnd = windowEnd,
            concentrationUnit = PkConcentrationUnit.PG_PER_ML,
            timeH = listOf(0.0, windowHours),
            concentrations = listOf(0.0, windowHours),
            doseMarkers = emptyList(),
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
                    medicine = testMedicine(key = MedicationKey.ESTRADIOL),
                    applicationType = MedicationApplicationType.ORAL,
                )
            ),
            createdAt = Instant.parse("2026-04-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-04-01T00:00:00Z"),
        )
    }

    private fun stockWarningProjection(): MedicineStockProjection {
        val medicine = testMedicine(
            stock = MedicineStock(
                trackingEnabled = true,
                unitsRemaining = 0.0,
                unitsLastTotal = 10.0,
            ),
        )
        return MedicineStockProjection(
            medicine = medicine,
            dosesPerDayMagnitude = 1.0,
            totalStockUnits = 0.0,
            runway = RunwayProjection.NoSchedule,
            intervalDays = null,
            maxPerAdministration = 1.0,
            state = MedicineStockState.OUT,
        )
    }
}
