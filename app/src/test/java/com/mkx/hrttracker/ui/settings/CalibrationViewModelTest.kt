package com.mkx.hrttracker.ui.settings

import androidx.lifecycle.Lifecycle
import com.mkx.hrttracker.data.repository.BloodTestRepository
import com.mkx.hrttracker.data.repository.CalibrationEstimate
import com.mkx.hrttracker.data.repository.E2Derivation
import com.mkx.hrttracker.data.repository.E2EstimateRepository
import com.mkx.hrttracker.data.repository.E2FitRecord
import com.mkx.hrttracker.data.repository.E2Pending
import com.mkx.hrttracker.data.repository.E2State
import com.mkx.hrttracker.data.repository.FitOutcome
import com.mkx.hrttracker.data.repository.E2InputsRecord
import com.mkx.hrttracker.data.repository.HomeCacheState
import com.mkx.hrttracker.data.repository.HomeSnapshotRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.buildPkCalibrationInput
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResult
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.testMedicationLogEntry
import com.mkx.hrttracker.model.medication.testMedicine
import com.mkx.hrttracker.model.pk.E2CalibrationDisposition
import com.mkx.hrttracker.model.pk.E2CalibrationMetadata
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.pk.PkCalibrationEngine
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationLab
import com.mkx.hrttracker.model.pk.PkCalibrationResult
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.ui.calibration.CalibrationDeleteAllEntriesResult
import com.mkx.hrttracker.ui.calibration.CalibrationPanelResultSummary
import com.mkx.hrttracker.ui.calibration.CalibrationRangeStatus
import com.mkx.hrttracker.ui.calibration.CalibrationUiState
import com.mkx.hrttracker.ui.calibration.CalibrationViewModel
import com.mkx.hrttracker.ui.calibration.PkCalibrationLabRowFlag
import com.mkx.hrttracker.ui.calibration.PkCalibrationScreenState
import com.mkx.hrttracker.ui.calibration.calibrationRangeStatus
import com.mkx.hrttracker.ui.calibration.calibrationValueInPreferredUnitLabel
import com.mkx.hrttracker.ui.calibration.formatCalibrationPanelValueSummary
import com.mkx.hrttracker.ui.calibration.groupCalibrationPanelsByMonth
import com.mkx.hrttracker.ui.calibration.parseCalibrationNumericInput
import com.mkx.hrttracker.ui.calibration.pkCalibrationLabFlag
import com.mkx.hrttracker.util.CalibrationPanelDateTimeFormatters
import com.mkx.hrttracker.util.FakeAppTimeSource
import com.mkx.hrttracker.util.calibrationMonthHeaderFormatter
import com.mkx.hrttracker.util.formatCalibrationPanelDateTimeLabels
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class CalibrationViewModelTest {
    private val repository: BloodTestRepository = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk()

    // The estimator's state: each observation, a restart included, starts
    // from its current value, as the repository's cold start does.
    private val calibrationEstimates = MutableStateFlow(E2State(estimate()))
    private val e2EstimateRepository: E2EstimateRepository = mockk {
        every { observeCalibration(any()) } returns calibrationEstimates
    }
    private val homeSnapshotRepository: HomeSnapshotRepository = mockk(relaxed = true)
    private val processLifecycleState = MutableStateFlow(Lifecycle.State.STARTED)
    private val dispatcher = StandardTestDispatcher()
    private lateinit var settingsStateFlow: MutableStateFlow<SettingsState>

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        settingsStateFlow = MutableStateFlow(SettingsState())
        every { settingsRepository.settingsState } returns settingsStateFlow
        every { settingsRepository.pkCalibrationIntroSeen } returns flowOf(false)
        every { repository.getCachedPanels() } returns null
    }

    @Test
    fun pkIntroSeen_surfacesTheStoredFlag_andMarkingPersistsIt() = runTest {
        every { repository.observePanels() } returns flowOf(emptyList())
        coEvery { settingsRepository.setPkCalibrationIntroSeen(true) } returns Unit
        val viewModel = viewModel()
        val collector = backgroundScope.launch { viewModel.pkIntroSeen.collect() }
        advanceUntilIdle()
        assertEquals(false, viewModel.pkIntroSeen.value)

        viewModel.markPkIntroSeen()
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsRepository.setPkCalibrationIntroSeen(true) }
        collector.cancel()
    }

    @Test
    fun pkCalibrationState_showsNumericFailure_whenTheFitOrItsRenderFailed() = runTest {
        every { repository.observePanels() } returns flowOf(emptyList())
        calibrationEstimates.value = E2State(estimate(result = null))
        val viewModel = viewModel()
        val collector = backgroundScope.launch { viewModel.pkCalibrationState.collect() }
        advanceUntilIdle()

        val state = checkNotNull(viewModel.pkCalibrationState.value)
        assertEquals(PkCalibrationGlobalState.NUMERIC_FAILURE, state.ui.globalState)
        assertTrue(state.ui.numericFailure)
        assertFalse(viewModel.uiState.value.isLoading)
        collector.cancel()
    }

    @Test
    fun coldStart_showsTheLoadingState_untilTheEstimateIsDerived() = runTest {
        every { repository.observePanels() } returns flowOf(listOf(testBloodTestPanel()))
        calibrationEstimates.value = E2State(null, E2Pending.SYSTEM)
        val viewModel = viewModel()
        val collector = backgroundScope.launch { viewModel.pkCalibrationState.collect() }
        advanceUntilIdle()
        assertNull(viewModel.pkCalibrationState.value)
        assertTrue(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isUpdating)

        calibrationEstimates.value = E2State(estimate())
        advanceUntilIdle()
        assertEquals(PkCalibrationGlobalState.NO_USABLE_LABS, viewModel.pkCalibrationState.value?.ui?.globalState)
        assertFalse(viewModel.uiState.value.isLoading)
        collector.cancel()
    }

    @Test
    fun withNothingToDeriveTheSectionFrom_theLabListStillShows() = runTest {
        // A mutation's rebuild pending, or failing on every try: no value
        // and nothing being derived. The lab list is live, so the page shows
        // it, without the section, instead of a spinner.
        val panel = testBloodTestPanel()
        every { repository.observePanels() } returns flowOf(listOf(panel))
        calibrationEstimates.value = E2State(null)
        val viewModel = viewModel()
        val collector = backgroundScope.launch { viewModel.pkCalibrationState.collect() }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(listOf(panel), viewModel.uiState.value.panels)
        assertNull(viewModel.pkCalibrationState.value)
        collector.cancel()
    }

    @Test
    fun aDerivationStartingAfterTheListShows_neverTakesThePageBackToLoading() = runTest {
        // Opened while a run is going: nothing to derive from, so the list
        // shows alone. The run publishes and the section is derived; the
        // section then appears above the list, with no loading state between.
        every { repository.observePanels() } returns flowOf(listOf(testBloodTestPanel()))
        calibrationEstimates.value = E2State(null)
        val viewModel = viewModel()
        val sections = collectSections(viewModel)
        advanceUntilIdle()
        val pages = collectPages(viewModel)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(sections.last())

        calibrationEstimates.value = E2State(null, E2Pending.SYSTEM)
        advanceUntilIdle()
        calibrationEstimates.value = E2State(estimate())
        advanceUntilIdle()

        assertEquals(PkCalibrationGlobalState.NO_USABLE_LABS, sections.last()?.ui?.globalState)
        assertTrue(pages.none { page -> page.isLoading })
    }

    @Test
    fun aReturn_throughTheEstimator_showsAMovedKeyAlreadyDerived_inline() = runTest {
        // The real estimator rather than one stubbed flow. A review lands
        // while the app is away, and the result editor, which keeps
        // observing, derives the new key: the page's restarted observation
        // finds it in the memo, in the pass that brings the app back.
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val zoneId = ZoneId.systemDefault()
        val now = LocalDateTime.of(2026, 5, 20, 10, 0)
        val estradiol = testMedicine(preparation = MedicinePreparation.Pill(strengthMgPerTablet = 2.0))
        val logged = (1L..60L).map { daysAgo ->
            val at = now.toLocalDate().minusDays(daysAgo).atTime(8, 0)
            testMedicationLogEntry(
                uuid = UUID.nameUUIDFromBytes("dose:$at".toByteArray()),
                medicine = estradiol,
                doseInstruction = DoseInstruction.TabletFraction(1, 1),
                sourceGroupUuid = null,
                appliedAt = at.atZone(zoneId).toInstant(),
                appliedAtTimeZoneId = zoneId.id,
            )
        }
        val labs = listOf(10L, 6L, 2L).mapIndexed { index, daysAgo ->
            PkCalibrationLab(
                resultId = UUID(0L, index + 1L),
                collectedAtEpochMillis = now.toLocalDate().minusDays(daysAgo).atTime(9, 0)
                    .atZone(zoneId).toInstant().toEpochMilli(),
                valuePgml = 140.0,
            )
        }
        fun inputs(generation: Long, metadata: List<E2CalibrationMetadata>): HomeCacheState {
            val input = buildPkCalibrationInput(labs = labs, entries = logged, weightKg = 62.0, metadata = metadata)
            return HomeCacheState(
                generation = generation,
                homeData = null,
                e2Inputs = E2InputsRecord(
                    generation = generation,
                    anchorDateEpochDay = now.toLocalDate().toEpochDay(),
                    zoneId = zoneId.id,
                    option = HomeE2ChartWindowOption.SEVEN_DAYS,
                    weightKg = 62.0,
                    estradiolEntries = logged,
                    plannedSources = emptyList(),
                    labs = labs,
                    labMetadata = metadata,
                    fit = E2FitRecord(
                        fitId = E2Derivation.fitId(input),
                        epoch = 0,
                        outcome = FitOutcome.Solved(PkCalibrationEngine.evaluate(input).result, input.originEpochMillis),
                    ),
                ),
            )
        }
        val cache = MutableStateFlow<HomeCacheState?>(inputs(generation = 0L, metadata = emptyList()))
        val generation = MutableStateFlow(0L)
        val snapshots = mockk<HomeSnapshotRepository>(relaxed = true) {
            every { this@mockk.cache } returns cache
            every { this@mockk.generation } returns generation
        }
        val estimates = E2EstimateRepository(
            homeSnapshotRepository = snapshots,
            store = mockk { coEvery { read() } returns null },
            clock = Clock.fixed(now.atZone(zoneId).toInstant(), zoneId),
            appScope = backgroundScope,
            defaultDispatcher = StandardTestDispatcher(testScheduler),
        )
        every { repository.observePanels() } returns flowOf(listOf(testBloodTestPanel()))
        val viewModel = CalibrationViewModel(
            bloodTestRepository = repository,
            settingsRepository = settingsRepository,
            e2EstimateRepository = estimates,
            homeSnapshotRepository = snapshots,
            processLifecycleState = processLifecycleState,
            appTimeSource = FakeAppTimeSource(now),
        )
        val sections = collectSections(viewModel)
        val pages = collectPages(viewModel)
        // The estimator works in backgroundScope: runCurrent runs it, where
        // advanceUntilIdle stops once only background work is left.
        runCurrent()
        assertEquals(emptySet<UUID>(), viewModel.pkCalibrationState.value?.acceptedResultIds)

        processLifecycleState.value = Lifecycle.State.CREATED
        val review = listOf(E2CalibrationMetadata(labs.first().resultId, E2CalibrationDisposition.REVIEWED, Instant.EPOCH))
        generation.value = 1L
        cache.value = inputs(generation = 1L, metadata = review)
        val editor = mutableListOf<E2State<CalibrationEstimate>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            estimates.observeCalibration(MutableStateFlow(now)).collect { state -> editor += state }
        }
        runCurrent()
        val reviewed = setOf(labs.first().resultId)
        assertEquals(reviewed, editor.last().value?.acceptedLabIds)
        val returnedAt = sections.size
        val pageReturnedAt = pages.size
        processLifecycleState.value = Lifecycle.State.STARTED

        // In the state the return built, before anything else runs: the new
        // section, with no loading state before it.
        assertEquals(reviewed, viewModel.pkCalibrationState.value?.acceptedResultIds)
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(sections.drop(returnedAt).isNotEmpty())
        assertTrue(sections.drop(returnedAt).all { section -> section?.acceptedResultIds == reviewed })
        assertTrue(pages.drop(pageReturnedAt).none { page -> page.isLoading })
    }

    @Test
    fun theSectionsFlags_comeFromTheEstimate_beforeRoomHasReadTheLabs() = runTest {
        // Room isn't open yet: no cached panels and no emission. The estimate
        // carries the flags stored with the fit.
        every { repository.observePanels() } returns MutableSharedFlow<List<BloodTestPanel>>()
        val excluded = UUID(0L, 1L)
        val accepted = UUID(0L, 2L)
        val nonPositive = UUID(0L, 3L)
        calibrationEstimates.value = E2State(
            estimate().copy(
                acceptedLabIds = setOf(accepted),
                excludedLabIds = setOf(excluded, nonPositive),
                nonPositiveLabIds = setOf(nonPositive),
            )
        )
        val viewModel = viewModel()
        val collector = backgroundScope.launch { viewModel.pkCalibrationState.collect() }
        advanceUntilIdle()

        val state = checkNotNull(viewModel.pkCalibrationState.value)
        assertEquals(PkCalibrationLabRowFlag.Excluded(excluded), pkCalibrationLabFlag(state, excluded))
        assertEquals(PkCalibrationLabRowFlag.Accepted(accepted), pkCalibrationLabFlag(state, accepted))
        assertEquals(
            PkCalibrationLabRowFlag.Excluded(nonPositive, dismissed = true),
            pkCalibrationLabFlag(state, nonPositive),
        )
        // The page itself waits for the lab list.
        assertTrue(viewModel.uiState.value.isLoading)
        collector.cancel()
    }

    @Test
    fun aReturnToTheForeground_restartsTheObservation_whichStopsInTheBackground() = runTest {
        every { repository.observePanels() } returns flowOf(emptyList())
        viewModel()
        advanceUntilIdle()
        assertEquals(1, calibrationEstimates.subscriptionCount.value)

        processLifecycleState.value = Lifecycle.State.CREATED
        advanceUntilIdle()
        assertEquals(0, calibrationEstimates.subscriptionCount.value)

        processLifecycleState.value = Lifecycle.State.STARTED
        advanceUntilIdle()
        assertEquals(1, calibrationEstimates.subscriptionCount.value)
        verify(exactly = 2) { e2EstimateRepository.observeCalibration(any()) }
    }

    @Test
    fun aReturn_afterTheKeyMoved_showsTheLoadingState_neverTheStaleSection() = runTest {
        // Unconfined Main: the lifecycle event and everything it resumes on
        // Main run inline, as on the main thread before the next frame.
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        every { repository.observePanels() } returns flowOf(listOf(testBloodTestPanel()))
        val viewModel = viewModel()
        val sections = collectSections(viewModel)
        assertEquals(PkCalibrationGlobalState.NO_USABLE_LABS, sections.last()?.ui?.globalState)

        // Midnight passed while away and nothing derived the new key: the
        // restarted observation has no value.
        processLifecycleState.value = Lifecycle.State.CREATED
        calibrationEstimates.value = E2State(null, E2Pending.SYSTEM)
        processLifecycleState.value = Lifecycle.State.STARTED
        assertNull(viewModel.pkCalibrationState.value)
        assertTrue(viewModel.uiState.value.isLoading)

        calibrationEstimates.value = E2State(estimate(PkCalibrationResult(PkCalibrationGlobalState.NO_DOSE_HISTORY)))
        assertEquals(PkCalibrationGlobalState.NO_DOSE_HISTORY, viewModel.pkCalibrationState.value?.ui?.globalState)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun aReturn_toAMovedKeyAlreadyDerived_showsItInTheFirstSynchronousState() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        every { repository.observePanels() } returns flowOf(listOf(testBloodTestPanel()))
        val viewModel = viewModel()
        val sections = collectSections(viewModel)
        val pages = collectPages(viewModel)

        processLifecycleState.value = Lifecycle.State.CREATED
        // Home's estimate sheet derived it while the page was away.
        calibrationEstimates.value = E2State(estimate(PkCalibrationResult(PkCalibrationGlobalState.NO_DOSE_HISTORY)))
        val returnedAt = sections.size
        val pageReturnedAt = pages.size
        processLifecycleState.value = Lifecycle.State.STARTED

        // In the state the return built, before anything else runs: no
        // loading frame, and the old section never comes back.
        assertEquals(PkCalibrationGlobalState.NO_DOSE_HISTORY, viewModel.pkCalibrationState.value?.ui?.globalState)
        assertTrue(sections.drop(returnedAt).isNotEmpty())
        assertTrue(
            sections.drop(returnedAt).all { section ->
                section?.ui?.globalState == PkCalibrationGlobalState.NO_DOSE_HISTORY
            }
        )
        assertTrue(pages.drop(pageReturnedAt).none { page -> page.isLoading })
    }

    @Test
    fun aReturn_withAMatchingKey_neverShowsALoadingFrame() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        every { repository.observePanels() } returns flowOf(listOf(testBloodTestPanel()))
        val viewModel = viewModel()
        val sections = collectSections(viewModel)
        val pages = collectPages(viewModel)

        processLifecycleState.value = Lifecycle.State.CREATED
        processLifecycleState.value = Lifecycle.State.STARTED
        advanceUntilIdle()

        assertTrue(sections.all { section -> section?.ui?.globalState == PkCalibrationGlobalState.NO_USABLE_LABS })
        assertTrue(pages.none { page -> page.isLoading })
    }

    @Test
    fun aUserChange_holdsTheSectionWithTheIndicator_forTheMinimumHoldCountedFromVisibility() = runTest {
        val initialPanel = testBloodTestPanel()
        val addedPanel = testBloodTestPanel(uuid = UUID.fromString("5d802712-bfc0-4af9-96f9-ae9c050b6af8"))
        val panels = MutableStateFlow(listOf(initialPanel))
        every { repository.observePanels() } returns panels
        val viewModel = viewModel()
        val collector = backgroundScope.launch { viewModel.pkCalibrationState.collect() }
        advanceUntilIdle()

        // A lab saved from the editor covering the page: the hold starts,
        // but its minimum waits for the page to be visible. The lab list
        // shows the new lab at once.
        calibrationEstimates.value = calibrationEstimates.value.copy(pending = E2Pending.USER)
        panels.value = listOf(initialPanel, addedPanel)
        runCurrent()
        assertTrue(viewModel.uiState.value.isUpdating)
        assertEquals(listOf(initialPanel, addedPanel), viewModel.uiState.value.panels)
        advanceTimeBy(1_000L)
        viewModel.setScreenVisible(true)
        runCurrent()
        advanceTimeBy(100L)
        calibrationEstimates.value = E2State(estimate(PkCalibrationResult(PkCalibrationGlobalState.NO_DOSE_HISTORY)))
        runCurrent()
        assertTrue(viewModel.uiState.value.isUpdating)
        assertEquals(PkCalibrationGlobalState.NO_USABLE_LABS, viewModel.pkCalibrationState.value?.ui?.globalState)

        advanceTimeBy(250L)
        runCurrent()
        assertTrue(viewModel.uiState.value.isUpdating)
        advanceTimeBy(100L)
        runCurrent()
        assertFalse(viewModel.uiState.value.isUpdating)
        assertEquals(PkCalibrationGlobalState.NO_DOSE_HISTORY, viewModel.pkCalibrationState.value?.ui?.globalState)
        collector.cancel()
    }

    @Test
    fun theClockOrTheSystemMovingTheEstimate_swapsItSilently() = runTest {
        every { repository.observePanels() } returns flowOf(listOf(testBloodTestPanel()))
        val viewModel = viewModel()
        viewModel.setScreenVisible(true)
        val collector = backgroundScope.launch { viewModel.pkCalibrationState.collect() }
        advanceUntilIdle()
        val pages = collectPages(viewModel)

        calibrationEstimates.value = calibrationEstimates.value.copy(pending = E2Pending.SYSTEM)
        advanceUntilIdle()
        assertEquals(PkCalibrationGlobalState.NO_USABLE_LABS, viewModel.pkCalibrationState.value?.ui?.globalState)
        calibrationEstimates.value = E2State(estimate(PkCalibrationResult(PkCalibrationGlobalState.NO_DOSE_HISTORY)))
        advanceUntilIdle()

        assertEquals(PkCalibrationGlobalState.NO_DOSE_HISTORY, viewModel.pkCalibrationState.value?.ui?.globalState)
        assertTrue(pages.none { page -> page.isUpdating || page.isLoading })
        collector.cancel()
    }

    @Test
    fun tryAgain_retriesTheCalibrationThroughTheGate() = runTest {
        every { repository.observePanels() } returns flowOf(emptyList())
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.retryPkCalibration()
        advanceUntilIdle()

        coVerify(exactly = 1) { homeSnapshotRepository.retryCalibration() }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = CalibrationViewModel(
        bloodTestRepository = repository,
        settingsRepository = settingsRepository,
        e2EstimateRepository = e2EstimateRepository,
        homeSnapshotRepository = homeSnapshotRepository,
        processLifecycleState = processLifecycleState,
        appTimeSource = FakeAppTimeSource(LocalDateTime.of(2026, 5, 20, 10, 0)),
    )

    private fun TestScope.collectSections(viewModel: CalibrationViewModel): List<PkCalibrationScreenState?> {
        val sections = mutableListOf<PkCalibrationScreenState?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.pkCalibrationState.collect { section -> sections += section }
        }
        return sections
    }

    private fun TestScope.collectPages(viewModel: CalibrationViewModel): List<CalibrationUiState> {
        val pages = mutableListOf<CalibrationUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { page -> pages += page }
        }
        return pages
    }

    private fun estimate(
        result: PkCalibrationResult? = PkCalibrationResult(PkCalibrationGlobalState.NO_USABLE_LABS),
    ) = CalibrationEstimate(
        result = result,
        render = null,
        acceptedLabIds = emptySet(),
        excludedLabIds = emptySet(),
        nonPositiveLabIds = emptySet(),
    )

    @Test
    fun parseCalibrationNumericInput_accepts_comma_decimal() {
        assertEquals(123.4, checkNotNull(parseCalibrationNumericInput("123,4")), 1e-9)
        assertEquals(123.4, checkNotNull(parseCalibrationNumericInput(" 123.4 ")), 1e-9)
        assertEquals(0.0, checkNotNull(parseCalibrationNumericInput("0")), 1e-9)
        assertEquals(null, parseCalibrationNumericInput("abc"))
        assertEquals(null, parseCalibrationNumericInput("-1"))
        assertEquals(null, parseCalibrationNumericInput("-0.5"))
    }

    @Test
    fun init_loads_panels_from_repository() = runTest {
        val panel = testBloodTestPanel()
        every { repository.observePanels() } returns flowOf(listOf(panel))

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(listOf(panel), viewModel.uiState.value.panels)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun init_seedsUiStateFromCachedPanelsBeforeRepositoryEmits() = runTest {
        // Main.immediate on the main thread: the eager collections run in the
        // constructor, so an estimate already in memory is in the first state.
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val cachedPanel = testBloodTestPanel(
            uuid = UUID.fromString("3f2e761b-9bfd-4dbf-8a12-a6016e8a60be")
        )
        every { repository.getCachedPanels() } returns listOf(cachedPanel)
        every { repository.observePanels() } returns MutableSharedFlow<List<BloodTestPanel>>()

        val viewModel = viewModel()

        assertEquals(listOf(cachedPanel), viewModel.uiState.value.panels)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun ui_state_updates_when_repository_emits_new_panels() = runTest {
        val initialPanel = testBloodTestPanel(
            uuid = UUID.fromString("9c95f940-d8c3-4d04-b766-c55f0e014b58")
        )
        val updatedPanel = testBloodTestPanel(
            uuid = UUID.fromString("5d802712-bfc0-4af9-96f9-ae9c050b6af8")
        )
        val flow = MutableStateFlow(listOf(initialPanel))
        every { repository.observePanels() } returns flow

        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(listOf(initialPanel), viewModel.uiState.value.panels)

        flow.value = listOf(updatedPanel)
        advanceUntilIdle()

        assertEquals(listOf(updatedPanel), viewModel.uiState.value.panels)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun deleteAllCalibrationEntries_updatesUiStateWithSuccessResult() = runTest {
        every { repository.observePanels() } returns flowOf(emptyList())
        coEvery { repository.deleteAllPanels() } returns Unit

        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.deleteAllCalibrationEntries()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDeletingAllEntries)
        assertEquals(
            CalibrationDeleteAllEntriesResult.SUCCESS,
            viewModel.uiState.value.deleteAllEntriesResult,
        )

        viewModel.consumeDeleteAllEntriesResult()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.deleteAllEntriesResult)
        coVerify(exactly = 1) { repository.deleteAllPanels() }
    }

    @Test
    fun formatCalibrationPanelValueSummary_showsEstradiolTestosteroneAndRemainingEntries() {
        val panel = testBloodTestPanel(
            results = listOf(
                BloodTestResult(
                    uuid = UUID.fromString("5bce6841-c2d5-4192-ba59-ab18e95fdb4a"),
                    createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
                    displayOrder = 1,
                    analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.E2),
                    value = 95.0,
                    unitSnapshot = BloodUnitKey.PG_ML.storageValue,
                    canonicalValue = 95.0,
                ),
                BloodTestResult(
                    uuid = UUID.fromString("df0d4beb-21a2-4138-a52e-260306d35da5"),
                    createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
                    displayOrder = 0,
                    analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.T),
                    value = 1.1,
                    unitSnapshot = BloodUnitKey.NMOL_L.storageValue,
                    canonicalValue = 31.7,
                ),
                BloodTestResult(
                    uuid = UUID.fromString("2c35207b-c771-4c11-b6f2-f35f485542cd"),
                    createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
                    displayOrder = 2,
                    analyte = BloodTestResultAnalyte.Custom(
                        uuid = UUID.fromString("387e4ffb-6ddb-4b69-a6d9-95353d2e1f55"),
                        abbreviation = "Marker",
                        name = "Marker",
                    ),
                    value = 8.5,
                    unitSnapshot = "ratio",
                    canonicalValue = 8.5,
                ),
            )
        )

        val summary = formatCalibrationPanelValueSummary(panel)

        assertEquals(
            CalibrationPanelResultSummary.Builtin(
                analyteKey = BloodAnalyteKey.E2,
                value = "95",
                unit = "pg/mL",
                rangeStatus = CalibrationRangeStatus.BELOW,
            ),
            summary.mainResultSummary,
        )
        assertEquals(
            CalibrationPanelResultSummary.Builtin(
                analyteKey = BloodAnalyteKey.T,
                value = "31.7",
                unit = "ng/dL",
                rangeStatus = CalibrationRangeStatus.IN_RANGE,
            ),
            summary.testosteroneResultSummary,
        )
        assertEquals(1, summary.remainingResultCount)
    }

    @Test
    fun formatCalibrationPanelValueSummary_usesStoredDefaultUnitsForBuiltins() {
        val panel = testBloodTestPanel(
            results = listOf(
                BloodTestResult(
                    uuid = UUID.fromString("5bce6841-c2d5-4192-ba59-ab18e95fdb4a"),
                    createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
                    displayOrder = 0,
                    analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.E2),
                    value = 95.0,
                    unitSnapshot = BloodUnitKey.PG_ML.storageValue,
                    canonicalValue = 95.0,
                ),
                BloodTestResult(
                    uuid = UUID.fromString("df0d4beb-21a2-4138-a52e-260306d35da5"),
                    createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
                    displayOrder = 1,
                    analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.T),
                    value = 1.1,
                    unitSnapshot = BloodUnitKey.NMOL_L.storageValue,
                    canonicalValue = 31.7,
                ),
            )
        )

        val summary = formatCalibrationPanelValueSummary(
            panel = panel,
            settingsState = SettingsState(
                calibrationDefaultUnits = mapOf(
                    BloodAnalyteKey.E2 to BloodUnitKey.PMOL_L,
                    BloodAnalyteKey.T to BloodUnitKey.NMOL_L,
                )
            ),
        )

        assertEquals(
            CalibrationPanelResultSummary.Builtin(
                analyteKey = BloodAnalyteKey.E2,
                value = "348.7",
                unit = "pmol/L",
                rangeStatus = CalibrationRangeStatus.BELOW,
            ),
            summary.mainResultSummary,
        )
        assertEquals(
            CalibrationPanelResultSummary.Builtin(
                analyteKey = BloodAnalyteKey.T,
                value = "1.1",
                unit = "nmol/L",
                rangeStatus = CalibrationRangeStatus.IN_RANGE,
            ),
            summary.testosteroneResultSummary,
        )
    }

    @Test
    fun calibrationValueInPreferredUnitLabel_convertsWhenUnitsDiffer() {
        assertEquals(
            "348.7 pmol/L",
            calibrationValueInPreferredUnitLabel(
                analyteKey = BloodAnalyteKey.E2,
                valueText = "95",
                unit = BloodUnitKey.PG_ML,
                preferredUnit = BloodUnitKey.PMOL_L,
            ),
        )
        assertEquals(
            null,
            calibrationValueInPreferredUnitLabel(
                analyteKey = BloodAnalyteKey.E2,
                valueText = "95",
                unit = BloodUnitKey.PG_ML,
                preferredUnit = BloodUnitKey.PG_ML,
            ),
        )
        assertEquals(
            null,
            calibrationValueInPreferredUnitLabel(
                analyteKey = BloodAnalyteKey.E2,
                valueText = "",
                unit = BloodUnitKey.PG_ML,
                preferredUnit = BloodUnitKey.PMOL_L,
            ),
        )
    }

    @Test
    fun calibrationRangeStatus_respectsSelectedUnit() {
        assertEquals(
            CalibrationRangeStatus.IN_RANGE,
            calibrationRangeStatus(
                analyteKey = BloodAnalyteKey.T,
                valueText = "40",
                unit = BloodUnitKey.NG_DL,
            ),
        )
        assertEquals(
            CalibrationRangeStatus.ABOVE,
            calibrationRangeStatus(
                analyteKey = BloodAnalyteKey.T,
                valueText = "40",
                unit = BloodUnitKey.NMOL_L,
            ),
        )
    }

    @Test
    fun formatCalibrationPanelValueSummary_withoutEstradiolShowsFirstResultOnly() {
        val panel = testBloodTestPanel(
            results = listOf(
                BloodTestResult(
                    uuid = UUID.fromString("5bce6841-c2d5-4192-ba59-ab18e95fdb4a"),
                    createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
                    displayOrder = 0,
                    analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.T),
                    value = 1.1,
                    unitSnapshot = BloodUnitKey.NMOL_L.storageValue,
                    canonicalValue = 31.7,
                ),
                BloodTestResult(
                    uuid = UUID.fromString("2c35207b-c771-4c11-b6f2-f35f485542cd"),
                    createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
                    displayOrder = 1,
                    analyte = BloodTestResultAnalyte.Custom(
                        uuid = UUID.fromString("387e4ffb-6ddb-4b69-a6d9-95353d2e1f55"),
                        abbreviation = "Marker",
                        name = "Marker",
                    ),
                    value = 8.5,
                    unitSnapshot = "ratio",
                    canonicalValue = 8.5,
                )
            )
        )

        val summary = formatCalibrationPanelValueSummary(panel)

        assertEquals(
            CalibrationPanelResultSummary.Builtin(
                analyteKey = BloodAnalyteKey.T,
                value = "31.7",
                unit = "ng/dL",
                rangeStatus = CalibrationRangeStatus.IN_RANGE,
            ),
            summary.mainResultSummary,
        )
        assertEquals(null, summary.testosteroneResultSummary)
        assertEquals(1, summary.remainingResultCount)
    }

    @Test
    fun groupCalibrationPanelsByMonth_usesPanelStoredZoneAndPreservesPanelOrder() {
        val formatter = calibrationMonthHeaderFormatter(
            locale = Locale.ENGLISH,
            currentYear = 2026,
        )
        // Panels bucket by their own stored zone, not the device zone passed here.
        // losAngelesMarchPanel: 2026-04-01T06:30Z = 2026-03-31 23:30 PDT → March bucket.
        val losAngelesMarchPanel = testBloodTestPanel(
            uuid = UUID.fromString("fdc5a64f-3b29-4676-8d51-37976eb07b1d"),
            collectedAt = Instant.parse("2026-04-01T06:30:00Z"),
            collectedAtTimeZoneId = "America/Los_Angeles",
        )
        // tokyoAprilPanel: 2026-03-31T15:30Z = 2026-04-01 00:30 JST → April bucket.
        val tokyoAprilPanel = testBloodTestPanel(
            uuid = UUID.fromString("1bd6b492-7670-4912-b625-7f06006e3797"),
            collectedAt = Instant.parse("2026-03-31T15:30:00Z"),
            collectedAtTimeZoneId = "Asia/Tokyo",
        )
        val utcMarchPanel = testBloodTestPanel(
            uuid = UUID.fromString("3981dbac-4620-478e-af2f-60e352ba4319"),
            collectedAt = Instant.parse("2026-03-15T00:00:00Z"),
            collectedAtTimeZoneId = "UTC",
        )

        val groups = groupCalibrationPanelsByMonth(
            panels = listOf(losAngelesMarchPanel, tokyoAprilPanel, utcMarchPanel),
            monthFormatter = formatter,
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(2, groups.size)
        assertEquals(YearMonth.of(2026, 3), groups[0].yearMonth)
        assertEquals("March", groups[0].monthLabel)
        assertEquals(
            listOf(losAngelesMarchPanel.uuid, utcMarchPanel.uuid),
            groups[0].panels.map(BloodTestPanel::uuid),
        )
        assertEquals(YearMonth.of(2026, 4), groups[1].yearMonth)
        assertEquals("April", groups[1].monthLabel)
        assertEquals(listOf(tokyoAprilPanel.uuid), groups[1].panels.map(BloodTestPanel::uuid))
    }

    @Test
    fun formatCalibrationPanelDateTimeLabels_usesLocalDateTime() {
        val formatters = CalibrationPanelDateTimeFormatters(
            monthFormatter = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH),
            dayFormatter = DateTimeFormatter.ofPattern("d", Locale.ENGLISH),
            timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH),
        )
        val panel = testBloodTestPanel(
            collectedAt = Instant.parse("2026-04-01T06:30:00Z"),
            collectedAtTimeZoneId = "America/Los_Angeles",
        )

        val labels = formatCalibrationPanelDateTimeLabels(
            collectedAt = panel.collectedAt,
            dateTimeFormatters = formatters,
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals("Apr", labels.monthLabel)
        assertEquals("1", labels.dayLabel)
        assertEquals("06:30", labels.timeLabel)
    }
}

internal fun testBloodTestPanel(
    uuid: UUID = UUID.fromString("9c95f940-d8c3-4d04-b766-c55f0e014b58"),
    collectedAt: Instant = Instant.ofEpochMilli(1_700_000_000_000L),
    collectedAtTimeZoneId: String = "Asia/Tokyo",
    notes: String? = null,
    timeSinceLastEstradiolDoseMillis: Long? = null,
    results: List<BloodTestResult> = listOf(
        BloodTestResult(
            uuid = UUID.fromString("b39b8e68-3c42-4c90-bf70-6c8bc486bd98"),
            createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
            displayOrder = 0,
            analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.E2),
            value = 95.0,
            unitSnapshot = "pg_ml",
            canonicalValue = 95.0
        )
    ),
): BloodTestPanel {
    return BloodTestPanel(
        uuid = uuid,
        collectedAt = collectedAt,
        collectedAtTimeZoneId = collectedAtTimeZoneId,
        notes = notes,
        timeSinceLastEstradiolDoseMillis = timeSinceLastEstradiolDoseMillis,
        timeSinceLastTestosteroneDoseMillis = null,
        results = results,
        createdAt = Instant.ofEpochMilli(1_700_000_000_000L),
        updatedAt = Instant.ofEpochMilli(1_700_000_000_000L)
    )
}
