package com.mkx.hrttracker.ui.main

import com.mkx.hrttracker.data.repository.HomeInputSource
import com.mkx.hrttracker.data.repository.HomeInputs
import com.mkx.hrttracker.data.repository.HomeRepository
import com.mkx.hrttracker.ui.pkcalibrationdebug.PkCalibrationUiFixtureBridge
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.home.DEFAULT_HOME_CARD_ORDER
import com.mkx.hrttracker.model.home.HomeCardLayout
import com.mkx.hrttracker.model.home.HomeCardType
import com.mkx.hrttracker.model.journal.AnchorIcon
import com.mkx.hrttracker.model.journal.HeroBackground
import com.mkx.hrttracker.model.journal.TrackedDate
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationGroupSchedule
import com.mkx.hrttracker.model.medication.MedicationGroupScheduleType
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.medication.RunwayProjection
import com.mkx.hrttracker.model.medication.testMedicationGroupMedication
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
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
import kotlinx.coroutines.flow.StateFlow
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        every { homeRepository.refreshHomeSnapshotAsync(any(), any(), any()) } returns Unit
        every { timeZoneChangeNoticeController.notice } returns MutableStateFlow(null)
        every { settingsRepository.homeLowStockSectionExpandedFlow } returns MutableStateFlow(true)
        every { settingsRepository.homeLowStockAcknowledgedWarningStatesFlow } returns MutableStateFlow(
            emptyMap()
        )
        coEvery { settingsRepository.setHomeLowStockSectionFoldState(any(), any()) } returns Unit
        coEvery { settingsRepository.clearHomeLowStockAcknowledgedWarningStates() } returns Unit
        every { settingsRepository.homeCardLayoutFlow } returns MutableStateFlow(HomeCardLayout())
        coEvery { settingsRepository.setHomeCardLayout(any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun clockTickWithinSameDay_doesNotResubscribeOrRefreshSnapshot() = runTest {
        val firstMinute = LocalDateTime.of(2026, 4, 30, 9, 0)
        val appTimeSource = FakeAppTimeSource(firstMinute)
        every { homeRepository.observeHomeInputs(any(), any(), any()) } answers {
            flowOf(homeInputs(now = secondArg<StateFlow<LocalDateTime>>().value))
        }

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = appTimeSource,
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        verify(exactly = 1) {
            homeRepository.observeHomeInputs(
                firstMinute.toLocalDate(),
                any(),
                ZoneId.systemDefault(),
            )
        }
        verify(exactly = 1) {
            homeRepository.refreshHomeSnapshotAsync(
                now = firstMinute,
                force = false,
                zoneId = ZoneId.systemDefault(),
            )
        }

        appTimeSource.setCurrentMinute(firstMinute.plusMinutes(1))
        advanceUntilIdle()
        appTimeSource.setCurrentMinute(firstMinute.plusMinutes(2))
        advanceUntilIdle()

        verify(exactly = 1) { homeRepository.observeHomeInputs(any(), any(), any()) }
        verify(exactly = 1) { homeRepository.refreshHomeSnapshotAsync(any(), any(), any()) }
        assertEquals(firstMinute.plusMinutes(2), viewModel.uiState.value.now)
    }

    @Test
    fun uiStateExposesTopPinnedAnchorForHomeCard() = runTest {
        val now = LocalDateTime.of(2026, 6, 16, 12, 0)
        val appTimeSource = FakeAppTimeSource(now)
        val hero = TrackedDate(
            id = "estradiol-start",
            name = "On estradiol",
            icon = AnchorIcon.MEDICATION,
            date = LocalDate.of(2024, 4, 1),
            palette = MedicationGroupColorKey.ROSE,
            pinnedOrder = 0,
        )
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns flowOf(
            homeInputs(
                now = now,
                source = HomeInputSource.ROOM,
                homeAnchor = hero,
            )
        )

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = appTimeSource,
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        val anchor = viewModel.uiState.value.homeAnchor
        assertEquals("estradiol-start", anchor?.id)
        assertEquals("On estradiol", anchor?.name)
        assertEquals(AnchorIcon.MEDICATION, anchor?.icon)
        assertEquals(MedicationGroupColorKey.ROSE, anchor?.palette)
        assertEquals(LocalDate.of(2024, 4, 1), anchor?.date)
        assertEquals(806L, anchor?.dayMagnitude)
        assertFalse(anchor?.isFuture ?: true)
    }

    @Test
    fun uiStateExposesHomeCardLayoutFromRepository() = runTest {
        val now = LocalDateTime.of(2026, 6, 16, 12, 0)
        val customLayout = HomeCardLayout(
            order = listOf(
                HomeCardType.TIMELINE,
                HomeCardType.E2_HERO,
                HomeCardType.E2_CHART,
                HomeCardType.ANTIANDROGEN,
                HomeCardType.LOW_STOCK,
            ),
            hidden = setOf(HomeCardType.LOW_STOCK),
        )
        every { settingsRepository.homeCardLayoutFlow } returns MutableStateFlow(customLayout)
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns flowOf(
            homeInputs(now = now, source = HomeInputSource.ROOM, homeAnchor = null)
        )

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = FakeAppTimeSource(now),
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        assertEquals(customLayout, viewModel.uiState.value.homeCardLayout)
    }

    @Test
    fun setHomeCardLayout_persistsThroughRepository() = runTest {
        val now = LocalDateTime.of(2026, 6, 16, 12, 0)
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns flowOf(
            homeInputs(now = now, source = HomeInputSource.ROOM, homeAnchor = null)
        )
        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = FakeAppTimeSource(now),
            defaultDispatcher = dispatcher,
        )
        val order = DEFAULT_HOME_CARD_ORDER.reversed()
        val hidden = setOf(HomeCardType.E2_CHART)

        viewModel.setHomeCardLayout(order, hidden)
        advanceUntilIdle()

        coVerify { settingsRepository.setHomeCardLayout(order, hidden) }
    }

    @Test
    fun uiStateHomeAnchorIsNullWhenNoPinnedAnchorExists() = runTest {
        val now = LocalDateTime.of(2026, 6, 16, 12, 0)
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns flowOf(
            homeInputs(
                now = now,
                source = HomeInputSource.ROOM,
                homeAnchor = null,
            )
        )

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = FakeAppTimeSource(now),
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.homeAnchor)
    }

    @Test
    fun uiStateHomeAnchorUpdatesWhenPinnedDatesChange() = runTest {
        val now = LocalDateTime.of(2026, 6, 16, 12, 0)
        val inputs = MutableStateFlow(
            homeInputs(
                now = now,
                source = HomeInputSource.ROOM,
                homeAnchor = null,
            )
        )
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns inputs

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = FakeAppTimeSource(now),
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.homeAnchor)

        inputs.value = homeInputs(
            now = now,
            source = HomeInputSource.ROOM,
            homeAnchor = TrackedDate(
                id = "voice-start",
                name = "Voice training",
                icon = AnchorIcon.MIC,
                date = LocalDate.of(2026, 6, 1),
                palette = MedicationGroupColorKey.TEAL,
                pinnedOrder = 0,
            ),
        )
        advanceUntilIdle()

        val anchor = viewModel.uiState.value.homeAnchor
        assertEquals("voice-start", anchor?.id)
        assertEquals("Voice training", anchor?.name)
        assertEquals(15L, anchor?.dayMagnitude)
        verify(exactly = 1) { homeRepository.observeHomeInputs(any(), any(), any()) }
    }

    @Test
    fun coldStart_emitsHomeAnchorFromSnapshotWithoutAnyRoomEmission() = runTest {
        val now = LocalDateTime.of(2026, 6, 20, 9, 0)
        val hero = TrackedDate(
            id = "hero-1",
            name = "HRT start",
            icon = AnchorIcon.entries.first(),
            date = LocalDate.of(2025, 3, 14),
            palette = null,
            heroBackground = HeroBackground.DateColor,
            pinnedOrder = 0,
            createdAtEpochMillis = 1L,
        )
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns flowOf(
            homeInputs(now = now, source = HomeInputSource.SNAPSHOT, homeAnchor = hero)
        )
        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = FakeAppTimeSource(now),
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        val anchor = viewModel.uiState.value.homeAnchor
        assertEquals("hero-1", anchor?.id)
    }

    // The intro animation is claimed atomically so that with two simultaneous
    // Home compositions exactly one plays the intro — the old file-level
    // static let the second instance read the pre-claim value but never
    // consume it, sticking it on the producer render path forever.
    @Test
    fun claimHomeE2ChartIntroAnimation_grantsExactlyOneClaim() = runTest {
        val appTimeSource = FakeAppTimeSource(LocalDateTime.of(2026, 4, 30, 9, 0))
        every { homeRepository.observeHomeInputs(any(), any(), any()) } answers {
            flowOf(homeInputs(now = secondArg<StateFlow<LocalDateTime>>().value))
        }
        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = appTimeSource,
            defaultDispatcher = dispatcher,
        )

        assertTrue(viewModel.claimHomeE2ChartIntroAnimation())
        assertFalse(viewModel.claimHomeE2ChartIntroAnimation())
    }

    // Bug: a process-death re-tap of the anchor widget/shortcut dropped the
    // milestones deep link. The dedup marker lives in the ViewModel (not saved to
    // instance state), so process death recreates the ViewModel with both the
    // signal and its marker reset to 0 together — the re-tap's request (0 -> 1)
    // clears the gate and navigates. A marker that outlived the signal would sit
    // at the pre-death value and swallow this tap.
    @Test
    fun consumeMilestonesDeepLink_freshViewModelNavigatesReTap() = runTest {
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns flowOf(
            homeInputs(now = LocalDateTime.of(2026, 4, 30, 9, 0))
        )
        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = FakeAppTimeSource(LocalDateTime.of(2026, 4, 30, 9, 0)),
            defaultDispatcher = dispatcher,
        )

        viewModel.requestMilestonesDeepLink()
        assertTrue(viewModel.consumeMilestonesDeepLink())
    }

    // Bug: after a config-change recreation (e.g. font-scale change) the surviving
    // ViewModel keeps its signal, and the recreated NavHost effect re-consumes it.
    // The retained signal must be consumed exactly once so navigation doesn't
    // re-fire and yank the user back to milestones after they navigated away.
    @Test
    fun consumeMilestonesDeepLink_firesOnceThenBlocksRetainedSignal() = runTest {
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns flowOf(
            homeInputs(now = LocalDateTime.of(2026, 4, 30, 9, 0))
        )
        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = FakeAppTimeSource(LocalDateTime.of(2026, 4, 30, 9, 0)),
            defaultDispatcher = dispatcher,
        )

        viewModel.requestMilestonesDeepLink()
        assertTrue(viewModel.consumeMilestonesDeepLink())
        assertFalse(viewModel.consumeMilestonesDeepLink())
    }

    // Each genuine re-tap re-arms the gate: a second request after a prior consume
    // navigates again.
    @Test
    fun consumeMilestonesDeepLink_reArmsForEachNewRequest() = runTest {
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns flowOf(
            homeInputs(now = LocalDateTime.of(2026, 4, 30, 9, 0))
        )
        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = FakeAppTimeSource(LocalDateTime.of(2026, 4, 30, 9, 0)),
            defaultDispatcher = dispatcher,
        )

        viewModel.requestMilestonesDeepLink()
        assertTrue(viewModel.consumeMilestonesDeepLink())
        assertFalse(viewModel.consumeMilestonesDeepLink())

        viewModel.requestMilestonesDeepLink()
        assertTrue(viewModel.consumeMilestonesDeepLink())
    }

    @Test
    fun minuteTickWhileUiStateUnsubscribed_doesNotRebuildUiState() = runTest {
        val firstMinute = LocalDateTime.of(2026, 4, 30, 9, 0)
        val appTimeSource = FakeAppTimeSource(firstMinute)
        every { homeRepository.observeHomeInputs(any(), any(), any()) } answers {
            flowOf(homeInputs(now = secondArg<StateFlow<LocalDateTime>>().value))
        }

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = appTimeSource,
            defaultDispatcher = dispatcher,
        )
        val subscription = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
        advanceUntilIdle()
        assertEquals(firstMinute, viewModel.uiState.value.now)

        // The only subscriber leaves (backgrounded app / another tab). The
        // ViewModel is activity-scoped, so its combine stays collected for the
        // whole activity lifetime; same-day minute ticks must not keep
        // rebuilding the ui state (PK fallbacks included) with nobody looking.
        subscription.cancel()
        advanceUntilIdle()
        appTimeSource.setCurrentMinute(firstMinute.plusMinutes(1))
        advanceUntilIdle()
        assertEquals(
            "minute ticks must not rebuild the ui state while unsubscribed",
            firstMinute,
            viewModel.uiState.value.now,
        )

        // Re-subscribing re-attaches the live tick and catches the state up.
        startUiStateCollection(viewModel)
        advanceUntilIdle()
        assertEquals(
            "re-subscribing must re-anchor the ui state to the current minute",
            firstMinute.plusMinutes(1),
            viewModel.uiState.value.now,
        )
    }

    @Test
    fun explicitRefreshWithinSameDayAndZoneReanchorsNowWithoutResubscribingOrRefreshingSnapshot() =
        runTest {
            val firstMinute = LocalDateTime.of(2026, 4, 30, 9, 0)
            val zoneId = ZoneId.of("UTC")
            val appTimeSource = FakeAppTimeSource(firstMinute, initialZone = zoneId)
            every { homeRepository.observeHomeInputs(any(), any(), any()) } answers {
                flowOf(
                    homeInputs(
                        now = secondArg<StateFlow<LocalDateTime>>().value,
                        source = HomeInputSource.ROOM,
                    )
                )
            }

            val viewModel = MainViewModel(
                homeRepository = homeRepository,
                settingsRepository = settingsRepository,
                timeZoneChangeNoticeController = timeZoneChangeNoticeController,
                pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
                appTimeSource = appTimeSource,
                defaultDispatcher = dispatcher,
            )
            startUiStateCollection(viewModel)
            advanceUntilIdle()

            verify(exactly = 1) {
                homeRepository.observeHomeInputs(
                    firstMinute.toLocalDate(),
                    any(),
                    zoneId,
                )
            }
            verify(exactly = 1) {
                homeRepository.refreshHomeSnapshotAsync(
                    now = firstMinute,
                    force = false,
                    zoneId = zoneId,
                )
            }

            val normalTick = firstMinute.plusMinutes(1)
            appTimeSource.setCurrentMinute(normalTick)
            advanceUntilIdle()

            verify(exactly = 1) { homeRepository.observeHomeInputs(any(), any(), any()) }
            verify(exactly = 1) { homeRepository.refreshHomeSnapshotAsync(any(), any(), any()) }
            assertEquals(normalTick, viewModel.uiState.value.now)

            val refreshedMinute = firstMinute.plusHours(2)
            appTimeSource.setCurrentSnapshot(refreshedMinute, zoneId)
            advanceUntilIdle()

            verify(exactly = 1) { homeRepository.observeHomeInputs(any(), any(), any()) }
            verify(exactly = 1) { homeRepository.refreshHomeSnapshotAsync(any(), any(), any()) }
            assertEquals(refreshedMinute, viewModel.uiState.value.now)
        }

    @Test
    fun clockTickWithinSameDay_recomputesProjectionTrendWithLatestMinute() = runTest {
        val firstMinute = LocalDateTime.of(2026, 4, 30, 9, 0)
        val nextMinute = firstMinute.plusMinutes(1)
        val zoneId = ZoneId.systemDefault()
        val appTimeSource = FakeAppTimeSource(firstMinute)
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns flowOf(
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
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = appTimeSource,
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()
        val firstValue = viewModel.uiState.value.e2Hero.currentValue
        val firstPredictionStart = viewModel.uiState.value.e2Chart.predictionStartXHours

        appTimeSource.setCurrentMinute(nextMinute)
        advanceUntilIdle()

        verify(exactly = 1) { homeRepository.observeHomeInputs(any(), any(), any()) }
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
        every { homeRepository.observeHomeInputs(any(), any(), any()) } answers {
            flowOf(homeInputs(now = secondArg<StateFlow<LocalDateTime>>().value))
        }

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = appTimeSource,
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 4, 30), viewModel.uiState.value.todaySection.date)
        verify(exactly = 1) {
            homeRepository.refreshHomeSnapshotAsync(
                now = firstMinute,
                force = false,
                zoneId = ZoneId.systemDefault(),
            )
        }

        val nextMinute = LocalDateTime.of(2026, 5, 1, 0, 0)
        appTimeSource.setCurrentMinute(nextMinute)
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 5, 1), viewModel.uiState.value.todaySection.date)
        verify(exactly = 1) {
            homeRepository.refreshHomeSnapshotAsync(
                now = nextMinute,
                force = false,
                zoneId = ZoneId.systemDefault(),
            )
        }
    }

    @Test
    fun homeSnapshotInputRendersCompleteHomeStateWithoutSkeletonGate() = runTest {
        val now = LocalDateTime.of(2026, 4, 30, 12, 0)
        val appTimeSource = FakeAppTimeSource(now)
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns flowOf(
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
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
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
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns homeInputFlow

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
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
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns flowOf(
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
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
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
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns inputs

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
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
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns flowOf(
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
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = appTimeSource,
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        assertEquals(HomeInputSource.ROOM, viewModel.uiState.value.homeSource)
        assertEquals(
            latestEstradiolEntry.appliedAt.atZone(ZoneId.systemDefault()).toLocalDateTime(),
            viewModel.uiState.value.e2Hero.lastDoseAt,
        )
        assertTrue(viewModel.uiState.value.e2Hero.currentValue > 0.0)
    }

    @Test
    fun e2HeroReflectsLaterTodayDoseLoggedAfterSubscription() = runTest {
        // Regression: the single-row `latestEstradiolEntry` query is bounded at
        // the frozen subscription `now`, so a dose the user logs later in the
        // same session reaches the hero only through the schedule window. The
        // hero must report that later dose, not the stale single-row entry.
        val zoneId = ZoneId.systemDefault()
        val now = LocalDateTime.of(2026, 4, 30, 12, 0)
        val staleLatestTime = now.minusHours(6)
        val laterTodayTime = now.minusHours(2)
        val staleLatestEntry = MedicationLogEntry(
            uuid = UUID.fromString("9b24ff3a-fc93-4fd1-8760-81a9ae8eae04"),
            medicine = testMedicine(key = MedicationKey.ESTRADIOL),
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            equivalentE2Mg = 2.0,
            sourceGroupUuid = null,
            appliedAt = staleLatestTime.atZone(zoneId).toInstant(),
            appliedAtTimeZoneId = zoneId.id,
        )
        val laterTodayEntry = MedicationLogEntry(
            uuid = UUID.fromString("2c0d7a1e-9b44-4d52-bb0a-1f3e6c8a55aa"),
            medicine = testMedicine(key = MedicationKey.ESTRADIOL),
            category = MedicationCategory.ESTRADIOL,
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            equivalentE2Mg = 2.0,
            sourceGroupUuid = null,
            appliedAt = laterTodayTime.atZone(zoneId).toInstant(),
            appliedAtTimeZoneId = zoneId.id,
        )
        val appTimeSource = FakeAppTimeSource(now)
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns flowOf(
            homeInputs(
                now = now,
                source = HomeInputSource.ROOM,
                trendResult = null,
                latestEstradiolEntry = staleLatestEntry,
                estradiolPkEntries = listOf(staleLatestEntry, laterTodayEntry),
                scheduleEntries = listOf(laterTodayEntry),
            )
        )

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = appTimeSource,
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        assertEquals(laterTodayTime, viewModel.uiState.value.e2Hero.lastDoseAt)
    }

    @Test
    fun projectionExpiringWhileOnHome_requestsOneSnapshotRebuild() = runTest {
        // Only a data mutation or a date change rebuilt the snapshot, so when
        // a planned slot passed while the user stayed on Home the band
        // (snapshot-only) vanished and stayed gone. Expiry must request one
        // non-forced rebuild, and only one per expiry.
        val firstMinute = LocalDateTime.of(2026, 4, 30, 12, 0)
        val zoneId = ZoneId.systemDefault()
        val expiresAt = firstMinute.plusSeconds(30).atZone(zoneId).toInstant()
        val appTimeSource = FakeAppTimeSource(firstMinute)
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns flowOf(
            homeInputs(
                now = firstMinute,
                trendResult = PkTrendResult(
                    currentConcentration = 100.0,
                    previousDayConcentration = 90.0,
                    dailyConcentrations = listOf(70.0, 80.0, 90.0, 100.0),
                    concentrationUnit = PkConcentrationUnit.PG_PER_ML,
                ),
                pkProjectionExpiresAt = expiresAt,
            )
        )

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = appTimeSource,
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()
        // Only the date-keyed refresh so far.
        verify(exactly = 1) { homeRepository.refreshHomeSnapshotAsync(any(), any(), any()) }

        val expiredMinute = firstMinute.plusMinutes(1)
        appTimeSource.setCurrentMinute(expiredMinute)
        advanceUntilIdle()
        verify(exactly = 1) {
            homeRepository.refreshHomeSnapshotAsync(
                now = expiredMinute,
                force = false,
                zoneId = zoneId,
            )
        }

        appTimeSource.setCurrentMinute(expiredMinute.plusMinutes(1))
        advanceUntilIdle()
        verify(exactly = 2) { homeRepository.refreshHomeSnapshotAsync(any(), any(), any()) }
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
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns flowOf(
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
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
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
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns flowOf(
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
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
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
        val projection = stockWarningProjection(
            uuid = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            state = MedicineStockState.OUT,
        )
        val expandedFlow = MutableStateFlow(false)
        val acknowledgedWarningStatesFlow = MutableStateFlow(
            mapOf(projection.medicine.uuid.toString() to MedicineStockState.OUT)
        )
        val appTimeSource = FakeAppTimeSource(now)
        every { settingsRepository.homeLowStockSectionExpandedFlow } returns expandedFlow
        every { settingsRepository.homeLowStockAcknowledgedWarningStatesFlow } returns acknowledgedWarningStatesFlow
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns flowOf(
            homeInputs(
                now = now,
                stockWarnings = listOf(projection),
            )
        )

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
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
    fun unacknowledgedLowStockWarningOverridesPersistedCollapsedPreference() = runTest {
        val now = LocalDateTime.of(2026, 4, 30, 12, 0)
        val projection = stockWarningProjection(
            uuid = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            state = MedicineStockState.USER_LOW,
        )
        every { settingsRepository.homeLowStockSectionExpandedFlow } returns MutableStateFlow(false)
        every { settingsRepository.homeLowStockAcknowledgedWarningStatesFlow } returns MutableStateFlow(
            emptyMap()
        )
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns flowOf(
            homeInputs(now = now, stockWarnings = listOf(projection))
        )

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = FakeAppTimeSource(now),
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.lowStockSectionExpanded)
    }

    @Test
    fun severityIncreaseOverridesPersistedCollapsedPreference() = runTest {
        val now = LocalDateTime.of(2026, 4, 30, 12, 0)
        val projection = stockWarningProjection(
            uuid = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            state = MedicineStockState.OUT,
        )
        every { settingsRepository.homeLowStockSectionExpandedFlow } returns MutableStateFlow(false)
        every { settingsRepository.homeLowStockAcknowledgedWarningStatesFlow } returns MutableStateFlow(
            mapOf(projection.medicine.uuid.toString() to MedicineStockState.IMMINENT)
        )
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns flowOf(
            homeInputs(now = now, stockWarnings = listOf(projection))
        )

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = FakeAppTimeSource(now),
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.lowStockSectionExpanded)
    }

    @Test
    fun severityDecreaseKeepsPersistedCollapsedPreference() = runTest {
        val now = LocalDateTime.of(2026, 4, 30, 12, 0)
        val projection = stockWarningProjection(
            uuid = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            state = MedicineStockState.USER_LOW,
        )
        every { settingsRepository.homeLowStockSectionExpandedFlow } returns MutableStateFlow(false)
        every { settingsRepository.homeLowStockAcknowledgedWarningStatesFlow } returns MutableStateFlow(
            mapOf(projection.medicine.uuid.toString() to MedicineStockState.OUT)
        )
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns flowOf(
            homeInputs(now = now, stockWarnings = listOf(projection))
        )

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = FakeAppTimeSource(now),
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.lowStockSectionExpanded)
    }

    @Test
    fun mixedNewWarningOverridesPersistedCollapsedPreference() = runTest {
        val now = LocalDateTime.of(2026, 4, 30, 12, 0)
        val acknowledgedProjection = stockWarningProjection(
            uuid = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            state = MedicineStockState.USER_LOW,
        )
        val newProjection = stockWarningProjection(
            uuid = UUID.fromString("22222222-2222-2222-2222-222222222222"),
            state = MedicineStockState.USER_LOW,
        )
        every { settingsRepository.homeLowStockSectionExpandedFlow } returns MutableStateFlow(false)
        every { settingsRepository.homeLowStockAcknowledgedWarningStatesFlow } returns MutableStateFlow(
            mapOf(acknowledgedProjection.medicine.uuid.toString() to MedicineStockState.USER_LOW)
        )
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns flowOf(
            homeInputs(
                now = now,
                stockWarnings = listOf(acknowledgedProjection, newProjection),
            )
        )

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = FakeAppTimeSource(now),
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.lowStockSectionExpanded)
    }

    @Test
    fun mixedSeverityIncreaseOverridesPersistedCollapsedPreference() = runTest {
        val now = LocalDateTime.of(2026, 4, 30, 12, 0)
        val decreasedProjection = stockWarningProjection(
            uuid = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            state = MedicineStockState.USER_LOW,
        )
        val increasedProjection = stockWarningProjection(
            uuid = UUID.fromString("22222222-2222-2222-2222-222222222222"),
            state = MedicineStockState.OUT,
        )
        every { settingsRepository.homeLowStockSectionExpandedFlow } returns MutableStateFlow(false)
        every { settingsRepository.homeLowStockAcknowledgedWarningStatesFlow } returns MutableStateFlow(
            mapOf(
                decreasedProjection.medicine.uuid.toString() to MedicineStockState.OUT,
                increasedProjection.medicine.uuid.toString() to MedicineStockState.IMMINENT,
            )
        )
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns flowOf(
            homeInputs(
                now = now,
                stockWarnings = listOf(decreasedProjection, increasedProjection),
            )
        )

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = FakeAppTimeSource(now),
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.lowStockSectionExpanded)
    }

    @Test
    fun setLowStockSectionExpandedFalsePersistsCurrentWarningStates() = runTest {
        val now = LocalDateTime.of(2026, 4, 30, 12, 0)
        val firstProjection = stockWarningProjection(
            uuid = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            state = MedicineStockState.USER_LOW,
        )
        val secondProjection = stockWarningProjection(
            uuid = UUID.fromString("22222222-2222-2222-2222-222222222222"),
            state = MedicineStockState.OUT,
        )
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns flowOf(
            homeInputs(now = now, stockWarnings = listOf(firstProjection, secondProjection))
        )

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = FakeAppTimeSource(now),
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        viewModel.setLowStockSectionExpanded(false)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            settingsRepository.setHomeLowStockSectionFoldState(
                expanded = false,
                acknowledgedWarningStates = mapOf(
                    firstProjection.medicine.uuid.toString() to MedicineStockState.USER_LOW,
                    secondProjection.medicine.uuid.toString() to MedicineStockState.OUT,
                ),
            )
        }
    }

    @Test
    fun setLowStockSectionExpandedTrueClearsAcknowledgedWarningStates() = runTest {
        val now = LocalDateTime.of(2026, 4, 30, 12, 0)
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns flowOf(
            homeInputs(
                now = now
            )
        )

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = FakeAppTimeSource(now),
            defaultDispatcher = dispatcher,
        )

        viewModel.setLowStockSectionExpanded(true)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            settingsRepository.setHomeLowStockSectionFoldState(
                expanded = true,
                acknowledgedWarningStates = emptyMap(),
            )
        }
    }

    @Test
    fun roomEmptyWarningsClearAcknowledgedWarningStates() = runTest {
        val now = LocalDateTime.of(2026, 4, 30, 12, 0)
        every { settingsRepository.homeLowStockAcknowledgedWarningStatesFlow } returns MutableStateFlow(
            mapOf("11111111-1111-1111-1111-111111111111" to MedicineStockState.OUT)
        )
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns flowOf(
            homeInputs(
                now = now,
                stockWarnings = emptyList(),
                source = HomeInputSource.ROOM,
            )
        )

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = FakeAppTimeSource(now),
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            settingsRepository.clearHomeLowStockAcknowledgedWarningStates()
        }
    }

    @Test
    fun clearAcknowledgedWarningStatesFailureDoesNotTearDownHomeState() = runTest {
        val now = LocalDateTime.of(2026, 4, 30, 12, 0)
        every { settingsRepository.homeLowStockAcknowledgedWarningStatesFlow } returns MutableStateFlow(
            mapOf("11111111-1111-1111-1111-111111111111" to MedicineStockState.OUT)
        )
        coEvery { settingsRepository.clearHomeLowStockAcknowledgedWarningStates() } throws
                IllegalStateException("datastore write failed")
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns flowOf(
            homeInputs(
                now = now,
                stockWarnings = emptyList(),
                source = HomeInputSource.ROOM,
            )
        )

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = FakeAppTimeSource(now),
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        // A failed clear is self-correcting on the next ROOM emission; it must
        // not propagate through combine -> stateIn and stop the home flow.
        assertEquals(HomeInputSource.ROOM, viewModel.uiState.value.homeSource)
    }

    @Test
    fun roomNonEmptyWarningsDoNotClearAcknowledgedWarningStates() = runTest {
        val now = LocalDateTime.of(2026, 4, 30, 12, 0)
        val projection = stockWarningProjection(
            uuid = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            state = MedicineStockState.OUT,
        )
        every { settingsRepository.homeLowStockAcknowledgedWarningStatesFlow } returns MutableStateFlow(
            mapOf(projection.medicine.uuid.toString() to MedicineStockState.OUT)
        )
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns flowOf(
            homeInputs(
                now = now,
                stockWarnings = listOf(projection),
                source = HomeInputSource.ROOM,
            )
        )

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = FakeAppTimeSource(now),
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        coVerify(exactly = 0) {
            settingsRepository.clearHomeLowStockAcknowledgedWarningStates()
        }
    }

    @Test
    fun reenteredLowStockWarningWithoutAllEmptyRoomEmissionStaysAcknowledged() = runTest {
        val now = LocalDateTime.of(2026, 4, 30, 12, 0)
        val firstProjection = stockWarningProjection(
            uuid = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            state = MedicineStockState.USER_LOW,
        )
        val secondProjection = stockWarningProjection(
            uuid = UUID.fromString("22222222-2222-2222-2222-222222222222"),
            state = MedicineStockState.USER_LOW,
        )
        val inputs = MutableSharedFlow<HomeInputs>()
        every { settingsRepository.homeLowStockSectionExpandedFlow } returns MutableStateFlow(false)
        every { settingsRepository.homeLowStockAcknowledgedWarningStatesFlow } returns MutableStateFlow(
            mapOf(
                firstProjection.medicine.uuid.toString() to MedicineStockState.USER_LOW,
                secondProjection.medicine.uuid.toString() to MedicineStockState.USER_LOW,
            )
        )
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns inputs

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = FakeAppTimeSource(now),
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        inputs.emit(
            homeInputs(
                now = now,
                stockWarnings = listOf(firstProjection, secondProjection),
                source = HomeInputSource.ROOM,
            )
        )
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.lowStockSectionExpanded)

        inputs.emit(
            homeInputs(
                now = now,
                stockWarnings = listOf(secondProjection),
                source = HomeInputSource.ROOM,
            )
        )
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.lowStockSectionExpanded)
        coVerify(exactly = 0) {
            settingsRepository.clearHomeLowStockAcknowledgedWarningStates()
        }

        inputs.emit(
            homeInputs(
                now = now,
                stockWarnings = listOf(firstProjection, secondProjection),
                source = HomeInputSource.ROOM,
            )
        )
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.lowStockSectionExpanded)
        coVerify(exactly = 0) {
            settingsRepository.clearHomeLowStockAcknowledgedWarningStates()
        }
    }

    @Test
    fun snapshotToRoomSameLowStockWarningDoesNotReexpandAfterAcknowledgement() = runTest {
        val now = LocalDateTime.of(2026, 4, 30, 12, 0)
        val projection = stockWarningProjection(
            uuid = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            state = MedicineStockState.OUT,
        )
        val inputs = MutableSharedFlow<HomeInputs>()
        every { settingsRepository.homeLowStockSectionExpandedFlow } returns MutableStateFlow(false)
        every { settingsRepository.homeLowStockAcknowledgedWarningStatesFlow } returns MutableStateFlow(
            mapOf(projection.medicine.uuid.toString() to MedicineStockState.OUT)
        )
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns inputs

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = FakeAppTimeSource(now),
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        inputs.emit(
            homeInputs(
                now = now,
                stockWarnings = listOf(projection),
                source = HomeInputSource.SNAPSHOT,
            )
        )
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.lowStockSectionExpanded)
        coVerify(exactly = 0) {
            settingsRepository.clearHomeLowStockAcknowledgedWarningStates()
        }

        inputs.emit(
            homeInputs(
                now = now,
                stockWarnings = listOf(projection),
                source = HomeInputSource.ROOM,
            )
        )
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.lowStockSectionExpanded)
        coVerify(exactly = 0) {
            settingsRepository.clearHomeLowStockAcknowledgedWarningStates()
        }
    }

    @Test
    fun snapshotEmptyWarningsDoNotClearAcknowledgedWarningStates() = runTest {
        val now = LocalDateTime.of(2026, 4, 30, 12, 0)
        every { settingsRepository.homeLowStockAcknowledgedWarningStatesFlow } returns MutableStateFlow(
            mapOf("11111111-1111-1111-1111-111111111111" to MedicineStockState.OUT)
        )
        every { homeRepository.observeHomeInputs(any(), any(), any()) } returns flowOf(
            homeInputs(
                now = now,
                stockWarnings = emptyList(),
                source = HomeInputSource.SNAPSHOT,
            )
        )

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = FakeAppTimeSource(now),
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        coVerify(exactly = 0) {
            settingsRepository.clearHomeLowStockAcknowledgedWarningStates()
        }
    }

    @Test
    fun sameDateZoneChangeResubscribesHomeInputsAndRefreshesSnapshotForNewZone() = runTest {
        val localMinute = LocalDateTime.of(2026, 6, 3, 12, 0)
        val utc = ZoneId.of("UTC")
        val tokyo = ZoneId.of("Asia/Tokyo")
        val appTimeSource = FakeAppTimeSource(localMinute, initialZone = utc)
        every { homeRepository.observeHomeInputs(any(), any(), any()) } answers {
            flowOf(
                homeInputs(
                    now = secondArg<StateFlow<LocalDateTime>>().value,
                    source = HomeInputSource.ROOM,
                )
            )
        }

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = appTimeSource,
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        verify(exactly = 1) {
            homeRepository.observeHomeInputs(
                localMinute.toLocalDate(),
                any(),
                utc
            )
        }
        verify(exactly = 1) {
            homeRepository.refreshHomeSnapshotAsync(
                now = localMinute,
                force = false,
                zoneId = utc,
            )
        }

        appTimeSource.setCurrentSnapshot(localMinute, tokyo)
        advanceUntilIdle()

        verify(exactly = 1) {
            homeRepository.observeHomeInputs(
                localMinute.toLocalDate(),
                any(),
                tokyo
            )
        }
        verify(exactly = 1) {
            homeRepository.refreshHomeSnapshotAsync(
                now = localMinute,
                force = false,
                zoneId = tokyo,
            )
        }
        assertEquals(localMinute, viewModel.uiState.value.now)
    }

    @Test
    fun sameDateZoneChangeDisplaysRowsLoadedByNewZoneQuery() = runTest {
        val localMinute = LocalDateTime.of(2026, 6, 3, 12, 0)
        val utc = ZoneId.of("UTC")
        val tokyo = ZoneId.of("Asia/Tokyo")
        val boundaryEntry = testMedicationLogEntry(
            uuid = UUID.fromString("4a480c08-449a-422d-a53f-f5eb764fe32c"),
            medicine = testMedicine(key = MedicationKey.ESTRADIOL),
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            equivalentE2Mg = 2.0,
            sourceGroupUuid = null,
            appliedAt = localMinute
                .toLocalDate()
                .atStartOfDay()
                .plusMinutes(30)
                .atZone(tokyo)
                .toInstant(),
            appliedAtTimeZoneId = tokyo.id,
        )
        val appTimeSource = FakeAppTimeSource(localMinute, initialZone = utc)
        every { homeRepository.observeHomeInputs(any(), any(), any()) } answers {
            val queryZone = thirdArg<ZoneId>()
            flowOf(
                homeInputs(
                    now = secondArg<StateFlow<LocalDateTime>>().value,
                    source = HomeInputSource.ROOM,
                    scheduleEntries = if (queryZone == tokyo) listOf(boundaryEntry) else emptyList(),
                )
            )
        }

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = appTimeSource,
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.todaySection.manualCount)

        appTimeSource.setCurrentSnapshot(localMinute, tokyo)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.todaySection.manualCount)
        assertEquals(
            listOf(boundaryEntry.uuid),
            viewModel.uiState.value.todaySection.rows.single().fulfillingEntryUuids,
        )
    }

    @Test
    fun sameDateZoneChangeSuppressesStaleInputsUntilNewZoneInputsEmit() = runTest {
        val localMinute = LocalDateTime.of(2026, 6, 3, 12, 0)
        val utc = ZoneId.of("UTC")
        val tokyo = ZoneId.of("Asia/Tokyo")
        val oldZoneOnlyEntry = testMedicationLogEntry(
            uuid = UUID.fromString("9cd4bd87-a005-4eb4-b878-ea943eb7d468"),
            medicine = testMedicine(key = MedicationKey.ESTRADIOL),
            applicationType = MedicationApplicationType.ORAL,
            doseInstruction = DoseInstruction.TabletFraction(1, 1),
            equivalentE2Mg = 2.0,
            sourceGroupUuid = null,
            appliedAt = localMinute
                .toLocalDate()
                .atStartOfDay()
                .plusMinutes(30)
                .atZone(tokyo)
                .toInstant(),
            appliedAtTimeZoneId = "Unknown/Zone",
        )
        val newZoneEntry = oldZoneOnlyEntry.copy(
            uuid = UUID.fromString("98db030f-09b5-424f-939f-52b761612bb8")
        )
        val oldZoneInputs = MutableSharedFlow<HomeInputs>(replay = 1)
        val newZoneInputs = MutableSharedFlow<HomeInputs>()
        val acknowledgedWarningStatesFlow = MutableStateFlow(emptyMap<String, MedicineStockState>())
        val appTimeSource = FakeAppTimeSource(localMinute, initialZone = utc)
        every { settingsRepository.homeLowStockAcknowledgedWarningStatesFlow } returns acknowledgedWarningStatesFlow
        every { homeRepository.observeHomeInputs(any(), any(), any()) } answers {
            when (thirdArg<ZoneId>()) {
                utc -> oldZoneInputs
                tokyo -> newZoneInputs
                else -> error("Unexpected zone ${thirdArg<ZoneId>()}")
            }
        }

        val viewModel = MainViewModel(
            homeRepository = homeRepository,
            settingsRepository = settingsRepository,
            timeZoneChangeNoticeController = timeZoneChangeNoticeController,
            pkUiFixtureBridge = PkCalibrationUiFixtureBridge(),
            appTimeSource = appTimeSource,
            defaultDispatcher = dispatcher,
        )
        startUiStateCollection(viewModel)
        advanceUntilIdle()

        oldZoneInputs.emit(
            homeInputs(
                now = localMinute,
                source = HomeInputSource.ROOM,
                scheduleEntries = listOf(oldZoneOnlyEntry),
            )
        )
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.todaySection.manualCount)

        appTimeSource.setCurrentSnapshot(localMinute, tokyo)
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.todaySection.manualCount)
        assertEquals(
            emptyList<MainTodayDoseRowUiState>(),
            viewModel.uiState.value.todaySection.rows
        )

        acknowledgedWarningStatesFlow.value = mapOf("stale-warning" to MedicineStockState.OUT)
        advanceUntilIdle()
        coVerify(exactly = 0) {
            settingsRepository.clearHomeLowStockAcknowledgedWarningStates()
        }

        newZoneInputs.emit(
            homeInputs(
                now = localMinute,
                source = HomeInputSource.ROOM,
                scheduleEntries = listOf(newZoneEntry),
            )
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.todaySection.manualCount)
        assertEquals(
            listOf(newZoneEntry.uuid),
            viewModel.uiState.value.todaySection.rows.single().fulfillingEntryUuids,
        )
        coVerify(exactly = 1) {
            settingsRepository.clearHomeLowStockAcknowledgedWarningStates()
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
        scheduleEntries: List<MedicationLogEntry> = emptyList(),
        stockWarnings: List<MedicineStockProjection> = emptyList(),
        homeAnchor: TrackedDate? = null,
        source: HomeInputSource = HomeInputSource.SNAPSHOT,
    ): HomeInputs {
        return HomeInputs(
            activeGroups = activeGroups,
            scheduleEntries = scheduleEntries,
            antiandrogenHistoryEntries = emptyList(),
            profile = UserProfile(weightKg = 60.0),
            settings = settings,
            pkProjection = pkProjection ?: trendResult?.toProjection(now),
            pkProjectionExpiresAt = pkProjectionExpiresAt,
            latestEstradiolEntry = latestEstradiolEntry,
            estradiolPkEntries = estradiolPkEntries,
            estradiolPkPlannedEntries = estradiolPkPlannedEntries,
            stockWarnings = stockWarnings,
            homeAnchor = homeAnchor,
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

    private fun stockWarningProjection(
        uuid: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111"),
        state: MedicineStockState = MedicineStockState.OUT,
    ): MedicineStockProjection {
        val medicine = testMedicine(
            uuid = uuid,
            stock = MedicineStock(
                trackingEnabled = true,
                unitsRemaining = if (state == MedicineStockState.OUT) 0.0 else 4.0,
                unitsLastTotal = 10.0,
            ),
        )
        return MedicineStockProjection(
            medicine = medicine,
            dosesPerDayMagnitude = 1.0,
            totalStockUnits = if (state == MedicineStockState.OUT) 0.0 else 4.0,
            runway = RunwayProjection.NoSchedule,
            intervalDays = null,
            maxPerAdministration = 1.0,
            state = state,
        )
    }
}
