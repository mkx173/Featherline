package com.mkx.hrttracker.ui.pkcalibrationdebug

import com.mkx.hrttracker.data.repository.PkCalibrationReviewActionRejection
import com.mkx.hrttracker.data.repository.PkCalibrationReviewActionResult
import com.mkx.hrttracker.data.repository.PkCalibrationReviewActionService
import com.mkx.hrttracker.model.pk.CanonicalDigest
import com.mkx.hrttracker.model.pk.E2CalibrationDisposition
import com.mkx.hrttracker.model.pk.E2CalibrationMetadata
import com.mkx.hrttracker.model.pk.PkCalibrationAcceptanceRecord
import com.mkx.hrttracker.model.pk.PkCalibrationBandState
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationRenderState
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.PkRouteCalibrationDisplayState
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PkCalibrationDebugViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val enabled = PkCalibrationDebugGate { true }
    private val fixtureSource = DefaultPkCalibrationDebugScenarioSource(debugGate = enabled)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun defaultMode_loadsBoundLiveSnapshot_andExposesRawContractReadout() = runTest {
        val liveSnapshot = liveSnapshot(
            PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.INJECTION_CALIBRATED)
        )
        val source = RecordingSource(liveResult = available(liveSnapshot))

        val viewModel = PkCalibrationDebugViewModel(
            scenarioSource = source,
            debugGate = enabled,
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(PkCalibrationDebugMode.LIVE, state.mode)
        assertEquals(PkCalibrationDebugLoadState.AVAILABLE, state.loadState)
        assertEquals(PkCalibrationDebugSnapshotKind.LIVE_BOUND_EVALUATION, state.snapshotKind)
        assertEquals(PkCalibrationGlobalState.READY, state.globalState)
        assertEquals(PkCalibrationRoute.entries.toList(), state.routeRows.map { it.route })
        assertEquals(listOf(PkCalibrationRoute.INJECTION), state.promotedRoutes)
        assertEquals(PkCalibrationRenderState.PERSONALIZED, state.renderState)
        assertEquals(PkCalibrationBandState.READY, state.bandState)
        assertTrue(state.centralCurve.isNotEmpty())
        assertTrue(state.bandKnots.isNotEmpty())
        assertSame(liveSnapshot.result, state.rawResult)
        assertSame(liveSnapshot.render, state.rawRender)
        assertNull(state.scenario)
        assertEquals(1, source.liveCalls)
        assertEquals(listOf(1L), state.actionLog.map { it.sequence })
    }

    @Test
    fun observableLiveSource_updatesContinuously_fixtureIgnoresIt_resetReplaysLatest_andRetryDelegates() =
        runTest {
            val first = liveSnapshot(
                PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.POPULATION_ONLY)
            )
            val second = liveSnapshot(
                PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.INJECTION_CALIBRATED)
            )
            val source = ObservableRecordingSource(available(first))
            val viewModel = PkCalibrationDebugViewModel(
                scenarioSource = source,
                debugGate = enabled,
            )
            advanceUntilIdle()
            assertSame(first.result, viewModel.uiState.value.rawResult)

            viewModel.applyPreset(PkCalibrationDebugPreset.ORAL_OUT_OF_RANGE)
            val fixtureResult = viewModel.uiState.value.rawResult
            source.live.value = available(second)
            advanceUntilIdle()
            assertEquals(PkCalibrationDebugMode.FIXTURE, viewModel.uiState.value.mode)
            assertSame(fixtureResult, viewModel.uiState.value.rawResult)

            viewModel.resetToLive()
            advanceUntilIdle()
            assertEquals(PkCalibrationDebugMode.LIVE, viewModel.uiState.value.mode)
            assertSame(second.result, viewModel.uiState.value.rawResult)

            viewModel.refreshLive()
            advanceUntilIdle()
            assertEquals(1, source.retryCalls)
        }

    @Test
    fun everyGlobalAndRouteEnum_reachesTheViewModelWithExactCardinality() {
        val viewModel = fixtureViewModel()

        PkCalibrationGlobalState.entries.forEach { globalState ->
            assertEquals(
                PkCalibrationDebugDispatchResult.ACCEPTED,
                viewModel.selectGlobalState(globalState),
            )
            val state = viewModel.uiState.value
            assertEquals(globalState, state.globalState)
            assertEquals(
                if (globalState == PkCalibrationGlobalState.READY) 5 else 0,
                state.routeRows.size,
            )
        }

        PkCalibrationRoute.entries.forEach { route ->
            PkRouteCalibrationDisplayState.entries.forEach { displayState ->
                assertEquals(
                    PkCalibrationDebugDispatchResult.ACCEPTED,
                    viewModel.selectRouteState(route, displayState),
                )
                val state = viewModel.uiState.value
                assertEquals(PkCalibrationGlobalState.READY, state.globalState)
                assertEquals(PkCalibrationRoute.entries.toList(), state.routeRows.map { it.route })
                assertEquals(
                    displayState,
                    state.routeRows.single { row -> row.route == route }.displayState,
                )
            }
        }
    }

    @Test
    fun presets_mixedStateAndFaultToggles_republishValidatedFixtureFields() {
        val viewModel = fixtureViewModel()

        PkCalibrationDebugPreset.entries.forEach { preset ->
            assertEquals(PkCalibrationDebugDispatchResult.ACCEPTED, viewModel.applyPreset(preset))
            assertEquals(
                PkCalibrationDebugScenario.preset(preset),
                viewModel.uiState.value.scenario,
            )
        }

        viewModel.applyPreset(PkCalibrationDebugPreset.MIXED_INJECTION_ORAL)
        assertEquals(
            listOf(PkCalibrationRoute.INJECTION, PkCalibrationRoute.ORAL),
            viewModel.uiState.value.promotedRoutes,
        )

        viewModel.setRouteRenderFallback(PkCalibrationRoute.INJECTION)
        assertEquals(
            listOf(PkCalibrationRoute.INJECTION),
            viewModel.uiState.value.routeRenderFallbacks,
        )
        assertEquals(
            listOf(PkCalibrationRoute.ORAL),
            viewModel.uiState.value.effectivePromotedRoutes,
        )

        viewModel.setBandUnavailable(true)
        assertEquals(PkCalibrationBandState.NUMERIC_UNAVAILABLE, viewModel.uiState.value.bandState)
        assertTrue(viewModel.uiState.value.bandKnots.isEmpty())
        assertTrue(viewModel.uiState.value.centralCurve.isNotEmpty())

        viewModel.setCentralUnavailable(true)
        assertEquals(PkCalibrationRenderState.NUMERIC_UNAVAILABLE, viewModel.uiState.value.renderState)
        assertTrue(viewModel.uiState.value.centralCurve.isEmpty())

        viewModel.setCentralUnavailable(false)
        viewModel.setNonPositiveInput(true)
        assertEquals(PkCalibrationGlobalState.SHARED_INPUT_INVALID, viewModel.uiState.value.globalState)
        assertTrue(viewModel.uiState.value.routeRows.isEmpty())

        viewModel.setNonPositiveInput(false)
        viewModel.setDisplayCapBoundaryRoute(PkCalibrationRoute.PATCH)
        val patch = viewModel.uiState.value.routeRows.single { it.route == PkCalibrationRoute.PATCH }
        assertTrue(patch.atDisplayCapBoundary)
    }

    @Test
    fun fixtureReviewCycle_mutatesOnlyDebugState_andNeverCallsLiveHandler() {
        var handlerCalls = 0
        val handler = PkCalibrationDebugReviewActionHandler {
            handlerCalls += 1
            PkCalibrationDebugReviewExecution.Unavailable
        }
        val viewModel = PkCalibrationDebugViewModel(
            scenarioSource = fixtureSource,
            reviewActionHandler = handler,
            debugGate = enabled,
            autoLoadLive = false,
        )
        viewModel.applyPreset(PkCalibrationDebugPreset.INJECTION_REVIEW_FIT)
        val resultId = PkCalibrationDebugFixtures.outlierId(PkCalibrationRoute.INJECTION)

        assertEquals(
            PkCalibrationDebugSnapshotKind.SYNTHETIC_ENGINE_EVALUATION,
            viewModel.uiState.value.snapshotKind,
        )

        assertEquals(
            listOf(PkCalibrationDebugReviewAction.KEEP, PkCalibrationDebugReviewAction.EXCLUDE),
            viewModel.uiState.value.applicableActionCommands.map { it.action },
        )
        viewModel.performReviewAction(
            PkCalibrationDebugActionCommand(PkCalibrationDebugReviewAction.KEEP, resultId)
        )
        assertTrue(
            viewModel.uiState.value.actionLog.last().effect.startsWith(
                "FIXTURE_DISPOSITION:AUTO->ACCEPTED;"
            )
        )
        assertEquals(
            E2CalibrationDisposition.ACCEPTED,
            viewModel.uiState.value.reviewDispositionByResultId[resultId],
        )
        assertFalse(
            resultId in viewModel.uiState.value.routeRows.single {
                it.route == PkCalibrationRoute.INJECTION
            }.unreviewedOutlierLabIds,
        )
        assertEquals(
            listOf(PkCalibrationDebugReviewAction.EXCLUDE),
            viewModel.uiState.value.applicableActionCommands.map { it.action },
        )

        viewModel.performReviewAction(
            PkCalibrationDebugActionCommand(PkCalibrationDebugReviewAction.EXCLUDE, resultId)
        )
        assertTrue(
            viewModel.uiState.value.actionLog.last().effect.startsWith(
                "FIXTURE_DISPOSITION:ACCEPTED->EXCLUDED;"
            )
        )
        assertEquals(
            E2CalibrationDisposition.EXCLUDED,
            viewModel.uiState.value.reviewDispositionByResultId[resultId],
        )
        assertEquals(
            listOf(PkCalibrationDebugReviewAction.REINCLUDE),
            viewModel.uiState.value.applicableActionCommands.map { it.action },
        )

        viewModel.performReviewAction(
            PkCalibrationDebugActionCommand(PkCalibrationDebugReviewAction.REINCLUDE, resultId)
        )
        assertTrue(
            viewModel.uiState.value.actionLog.last().effect.startsWith(
                "FIXTURE_DISPOSITION:EXCLUDED->AUTO;"
            )
        )
        assertEquals(
            E2CalibrationDisposition.AUTO,
            viewModel.uiState.value.reviewDispositionByResultId[resultId],
        )
        assertEquals(0, handlerCalls)
    }

    @Test
    fun outlierToggle_keepsUiCommandsAlignedWithFittedEvidence() {
        val viewModel = fixtureViewModel()
        val route = PkCalibrationRoute.GEL
        val resultId = PkCalibrationDebugFixtures.outlierId(route)

        viewModel.applyPreset(PkCalibrationDebugPreset.POPULATION_ONLY)
        viewModel.setOutlierRoute(route)
        val outlierState = viewModel.uiState.value
        assertEquals(route, outlierState.scenario?.outlierRoute)
        assertEquals(
            listOf(PkCalibrationDebugReviewAction.KEEP, PkCalibrationDebugReviewAction.EXCLUDE),
            outlierState.applicableActionCommands.map { command -> command.action },
        )
        assertEquals(
            setOf(resultId),
            outlierState.routeRows.single { row -> row.route == route }
                .unreviewedOutlierLabIds,
        )

        viewModel.setOutlierRoute(null)
        val clearedState = viewModel.uiState.value
        assertNull(clearedState.scenario?.outlierRoute)
        assertTrue(
            clearedState.routeRows.single { row -> row.route == route }
                .unreviewedOutlierLabIds.isEmpty()
        )
        assertFalse(resultId in clearedState.reviewDispositionByResultId)
        assertTrue(clearedState.applicableActionCommands.isEmpty())
        assertEquals(
            (1L..clearedState.actionLog.size.toLong()).toList(),
            clearedState.actionLog.map { entry -> entry.sequence },
        )
    }

    @Test
    fun liveReview_callsHandler_refreshesLive_andLogsAppliedTransition() = runTest {
        val resultId = PkCalibrationDebugFixtures.outlierId(PkCalibrationRoute.INJECTION)
        val initial = liveSnapshot(
            PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.INJECTION_REVIEW_FIT)
        )
        val accepted = liveSnapshot(
            PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.INJECTION_REVIEW_FIT)
                .withFixtureDisposition(PkCalibrationDebugFixtureDisposition.ACCEPTED)
        )
        val source = RecordingSource(available(initial))
        var handled: PkCalibrationDebugActionCommand? = null
        val handler = PkCalibrationDebugReviewActionHandler { command ->
            handled = command
            PkCalibrationDebugReviewExecution.Completed(
                PkCalibrationReviewActionResult.Applied(acceptedMetadata(resultId))
            )
        }
        val viewModel = PkCalibrationDebugViewModel(
            scenarioSource = source,
            reviewActionHandler = handler,
            debugGate = enabled,
        )
        advanceUntilIdle()
        source.liveResult = available(accepted)
        val command = PkCalibrationDebugActionCommand(
            PkCalibrationDebugReviewAction.KEEP,
            resultId,
        )

        assertEquals(PkCalibrationDebugDispatchResult.ACCEPTED, viewModel.performReviewAction(command))
        advanceUntilIdle()

        assertEquals(command, handled)
        assertEquals(2, source.liveCalls)
        assertEquals(
            E2CalibrationDisposition.ACCEPTED,
            viewModel.uiState.value.reviewDispositionByResultId[resultId],
        )
        assertFalse(viewModel.uiState.value.liveReviewActionInFlight)
        assertTrue(viewModel.uiState.value.actionLog.any { it.effect == "COMMITTED:ACCEPTED" })
    }

    @Test
    fun liveReview_isSingleFlight_blocksEveryTransition_andPreservesSequentialCommits() = runTest {
        val resultId = PkCalibrationDebugFixtures.outlierId(PkCalibrationRoute.INJECTION)
        val initial = liveSnapshot(
            PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.INJECTION_REVIEW_FIT)
        )
        val accepted = liveSnapshot(
            PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.INJECTION_REVIEW_FIT)
                .withFixtureDisposition(PkCalibrationDebugFixtureDisposition.ACCEPTED)
        )
        val excluded = liveSnapshot(
            PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.INJECTION_REVIEW_FIT)
                .withFixtureDisposition(PkCalibrationDebugFixtureDisposition.EXCLUDED)
        )
        val firstHandlerRelease = CompletableDeferred<Unit>()
        val source = RecordingSource(available(initial))
        var handlerCalls = 0
        val handler = PkCalibrationDebugReviewActionHandler { command ->
            handlerCalls += 1
            if (handlerCalls == 1) firstHandlerRelease.await()
            val disposition = when (command.action) {
                PkCalibrationDebugReviewAction.KEEP -> E2CalibrationDisposition.ACCEPTED
                PkCalibrationDebugReviewAction.EXCLUDE -> E2CalibrationDisposition.EXCLUDED
                PkCalibrationDebugReviewAction.REINCLUDE -> E2CalibrationDisposition.AUTO
            }
            source.liveResult = available(
                if (disposition == E2CalibrationDisposition.ACCEPTED) accepted else excluded
            )
            PkCalibrationDebugReviewExecution.Completed(
                PkCalibrationReviewActionResult.Applied(metadata(resultId, disposition))
            )
        }
        val viewModel = PkCalibrationDebugViewModel(source, handler, enabled)
        advanceUntilIdle()
        val keep = PkCalibrationDebugActionCommand(PkCalibrationDebugReviewAction.KEEP, resultId)

        assertEquals(PkCalibrationDebugDispatchResult.ACCEPTED, viewModel.performReviewAction(keep))
        runCurrent()
        assertTrue(viewModel.uiState.value.liveReviewActionInFlight)
        val rawBeforeRejectedTransitions = viewModel.uiState.value.rawResult
        val rejectedTransitions = listOf(
            viewModel.resetToLive(),
            viewModel.refreshLive(),
            viewModel.applyPreset(PkCalibrationDebugPreset.POPULATION_ONLY),
            viewModel.selectGlobalState(PkCalibrationGlobalState.SHARED_INPUT_INVALID),
            viewModel.selectRouteState(
                PkCalibrationRoute.PATCH,
                PkRouteCalibrationDisplayState.LAB_CALIBRATED,
            ),
            viewModel.setRouteRenderFallback(PkCalibrationRoute.INJECTION),
            viewModel.setBandUnavailable(true),
            viewModel.setCentralUnavailable(true),
            viewModel.setOutlierRoute(PkCalibrationRoute.ORAL),
            viewModel.setNonPositiveInput(true),
            viewModel.setDisplayCapBoundaryRoute(PkCalibrationRoute.PATCH),
            viewModel.performReviewAction(keep),
        )

        assertTrue(rejectedTransitions.all {
            it == PkCalibrationDebugDispatchResult.REJECTED_LIVE_REVIEW_IN_FLIGHT
        })
        assertSame(rawBeforeRejectedTransitions, viewModel.uiState.value.rawResult)
        assertEquals(PkCalibrationDebugMode.LIVE, viewModel.uiState.value.mode)
        assertEquals(1, handlerCalls)
        assertEquals(1, source.liveCalls)
        assertEquals(0, source.fixtureCalls)

        firstHandlerRelease.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.liveReviewActionInFlight)
        assertEquals(E2CalibrationDisposition.ACCEPTED,
            viewModel.uiState.value.reviewDispositionByResultId[resultId])

        val exclude = PkCalibrationDebugActionCommand(
            PkCalibrationDebugReviewAction.EXCLUDE,
            resultId,
        )
        assertEquals(
            PkCalibrationDebugDispatchResult.ACCEPTED,
            viewModel.performReviewAction(exclude),
        )
        advanceUntilIdle()

        assertEquals(2, handlerCalls)
        assertEquals(3, source.liveCalls)
        assertEquals(
            listOf("COMMITTED:ACCEPTED", "COMMITTED:EXCLUDED"),
            viewModel.uiState.value.actionLog
                .map { it.effect }
                .filter { it.startsWith("COMMITTED:") },
        )
    }

    @Test
    fun modeSnapshotBoundaryMismatch_failsClosedAndExposesNoRealActions() = runTest {
        val fixture = fixtureSource.loadFixture(
            PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.INJECTION_REVIEW_FIT)
        ).availableSnapshot()
        val invalidLiveSnapshots = listOf(
            fixture,
            fixture.copy(kind = PkCalibrationDebugSnapshotKind.LIVE_BOUND_EVALUATION),
        )
        invalidLiveSnapshots.forEach { invalidSnapshot ->
            val viewModel = PkCalibrationDebugViewModel(
                scenarioSource = RecordingSource(available(invalidSnapshot)),
                debugGate = enabled,
            )
            advanceUntilIdle()

            assertEquals(PkCalibrationDebugMode.LIVE, viewModel.uiState.value.mode)
            assertEquals(PkCalibrationDebugLoadState.UNAVAILABLE, viewModel.uiState.value.loadState)
            assertEquals(
                PkCalibrationDebugSourceUnavailableReason.SOURCE_CONTRACT_MISMATCH,
                viewModel.uiState.value.sourceUnavailableReason,
            )
            assertNull(viewModel.uiState.value.rawResult)
            assertTrue(viewModel.uiState.value.applicableActionCommands.isEmpty())
        }

        val invalidFixtureSnapshots = listOf(
            fixture.copy(
                kind = PkCalibrationDebugSnapshotKind.LIVE_BOUND_EVALUATION,
                scenario = null,
            ),
            fixture.copy(scenario = null),
        )
        invalidFixtureSnapshots.forEach { invalidSnapshot ->
            val source = object : PkCalibrationDebugScenarioSource {
                override suspend fun loadLive() = available(liveSnapshot(
                    PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.POPULATION_ONLY)
                ))

                override fun loadFixture(scenario: PkCalibrationDebugScenario) =
                    available(invalidSnapshot)
            }
            val viewModel = PkCalibrationDebugViewModel(
                scenarioSource = source,
                debugGate = enabled,
                autoLoadLive = false,
            )

            assertEquals(
                PkCalibrationDebugDispatchResult.REJECTED_SOURCE_CONTRACT_MISMATCH,
                viewModel.applyPreset(PkCalibrationDebugPreset.INJECTION_REVIEW_FIT),
            )
            assertEquals(PkCalibrationDebugMode.FIXTURE, viewModel.uiState.value.mode)
            assertEquals(PkCalibrationDebugLoadState.UNAVAILABLE, viewModel.uiState.value.loadState)
            assertNull(viewModel.uiState.value.rawResult)
            assertTrue(viewModel.uiState.value.applicableActionCommands.isEmpty())
        }
    }

    @Test
    fun sourceAndHandlerExceptions_failClosed_withoutLeavingLoadingOrBusyState() = runTest {
        val throwingLiveSource = object : PkCalibrationDebugScenarioSource {
            override suspend fun loadLive(): PkCalibrationDebugSourceResult =
                throw IllegalStateException("live source failure")

            override fun loadFixture(scenario: PkCalibrationDebugScenario) =
                fixtureSource.loadFixture(scenario)
        }
        val liveFailureViewModel = PkCalibrationDebugViewModel(
            scenarioSource = throwingLiveSource,
            debugGate = enabled,
        )
        advanceUntilIdle()
        assertEquals(PkCalibrationDebugLoadState.UNAVAILABLE,
            liveFailureViewModel.uiState.value.loadState)
        assertEquals(PkCalibrationDebugSourceUnavailableReason.SOURCE_EXCEPTION,
            liveFailureViewModel.uiState.value.sourceUnavailableReason)
        assertEquals("UNAVAILABLE:SOURCE_EXCEPTION",
            liveFailureViewModel.uiState.value.actionLog.last().effect)

        val throwingFixtureSource = object : PkCalibrationDebugScenarioSource {
            override suspend fun loadLive() = PkCalibrationDebugSourceResult.Unavailable(
                PkCalibrationDebugSourceUnavailableReason.LIVE_EVALUATION_UNAVAILABLE
            )

            override fun loadFixture(scenario: PkCalibrationDebugScenario):
                PkCalibrationDebugSourceResult = throw IllegalArgumentException("fixture failure")
        }
        val fixtureFailureViewModel = PkCalibrationDebugViewModel(
            scenarioSource = throwingFixtureSource,
            debugGate = enabled,
            autoLoadLive = false,
        )
        assertEquals(
            PkCalibrationDebugDispatchResult.REJECTED_SOURCE_EXCEPTION,
            fixtureFailureViewModel.applyPreset(PkCalibrationDebugPreset.POPULATION_ONLY),
        )
        assertEquals(PkCalibrationDebugLoadState.UNAVAILABLE,
            fixtureFailureViewModel.uiState.value.loadState)
        assertTrue(fixtureFailureViewModel.uiState.value.applicableActionCommands.isEmpty())

        val initial = liveSnapshot(
            PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.INJECTION_REVIEW_FIT)
        )
        val handlerFailureViewModel = PkCalibrationDebugViewModel(
            scenarioSource = RecordingSource(available(initial)),
            reviewActionHandler = PkCalibrationDebugReviewActionHandler {
                throw IllegalStateException("handler failure")
            },
            debugGate = enabled,
        )
        advanceUntilIdle()
        val resultId = PkCalibrationDebugFixtures.outlierId(PkCalibrationRoute.INJECTION)
        handlerFailureViewModel.performReviewAction(PkCalibrationDebugActionCommand(
            PkCalibrationDebugReviewAction.KEEP,
            resultId,
        ))
        advanceUntilIdle()
        assertFalse(handlerFailureViewModel.uiState.value.liveReviewActionInFlight)
        assertEquals(PkCalibrationDebugLoadState.AVAILABLE,
            handlerFailureViewModel.uiState.value.loadState)
        assertEquals("REJECTED:HANDLER_EXCEPTION",
            handlerFailureViewModel.uiState.value.actionLog.last().effect)
    }

    @Test
    fun committedReviewIsLoggedEvenWhenPostCommitRefetchThrows() = runTest {
        val resultId = PkCalibrationDebugFixtures.outlierId(PkCalibrationRoute.INJECTION)
        val initial = liveSnapshot(
            PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.INJECTION_REVIEW_FIT)
        )
        var liveCalls = 0
        val source = object : PkCalibrationDebugScenarioSource {
            override suspend fun loadLive(): PkCalibrationDebugSourceResult {
                liveCalls += 1
                if (liveCalls == 1) return available(initial)
                throw IllegalStateException("post-commit refetch failed")
            }

            override fun loadFixture(scenario: PkCalibrationDebugScenario) =
                fixtureSource.loadFixture(scenario)
        }
        val handler = PkCalibrationDebugReviewActionHandler {
            PkCalibrationDebugReviewExecution.Completed(
                PkCalibrationReviewActionResult.Applied(
                    metadata(resultId, E2CalibrationDisposition.ACCEPTED)
                )
            )
        }
        val viewModel = PkCalibrationDebugViewModel(source, handler, enabled)
        advanceUntilIdle()

        viewModel.performReviewAction(PkCalibrationDebugActionCommand(
            PkCalibrationDebugReviewAction.KEEP,
            resultId,
        ))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.liveReviewActionInFlight)
        assertEquals(PkCalibrationDebugLoadState.UNAVAILABLE, viewModel.uiState.value.loadState)
        assertTrue(viewModel.uiState.value.actionLog.any { it.effect == "COMMITTED:ACCEPTED" })
        assertEquals("UNAVAILABLE:SOURCE_EXCEPTION", viewModel.uiState.value.actionLog.last().effect)
    }

    @Test
    fun postCommitRefetchCancellation_rethrowsAndClearsStaleActionRouting() = runTest {
        val resultId = PkCalibrationDebugFixtures.outlierId(PkCalibrationRoute.INJECTION)
        val initial = liveSnapshot(
            PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.INJECTION_REVIEW_FIT)
        )
        var liveCalls = 0
        val source = object : PkCalibrationDebugScenarioSource {
            override suspend fun loadLive(): PkCalibrationDebugSourceResult {
                liveCalls += 1
                if (liveCalls == 1) return available(initial)
                throw CancellationException("cancel post-commit refetch")
            }

            override fun loadFixture(scenario: PkCalibrationDebugScenario) =
                fixtureSource.loadFixture(scenario)
        }
        val handler = PkCalibrationDebugReviewActionHandler {
            PkCalibrationDebugReviewExecution.Completed(
                PkCalibrationReviewActionResult.Applied(
                    metadata(resultId, E2CalibrationDisposition.ACCEPTED)
                )
            )
        }
        val viewModel = PkCalibrationDebugViewModel(source, handler, enabled)
        advanceUntilIdle()

        viewModel.performReviewAction(PkCalibrationDebugActionCommand(
            PkCalibrationDebugReviewAction.KEEP,
            resultId,
        ))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.liveReviewActionInFlight)
        assertEquals(PkCalibrationDebugLoadState.IDLE, viewModel.uiState.value.loadState)
        assertNull(viewModel.uiState.value.rawResult)
        assertTrue(viewModel.uiState.value.applicableActionCommands.isEmpty())
        assertTrue(viewModel.uiState.value.actionLog.any { it.effect == "COMMITTED:ACCEPTED" })
        assertFalse(viewModel.uiState.value.actionLog.any {
            it.effect == "UNAVAILABLE:SOURCE_EXCEPTION"
        })
    }

    @Test
    fun cancellation_isRethrownWithoutBeingMisclassifiedAsAnOrdinaryFailure() = runTest {
        val cancellingSource = object : PkCalibrationDebugScenarioSource {
            override suspend fun loadLive(): PkCalibrationDebugSourceResult =
                throw CancellationException("cancel live")

            override fun loadFixture(scenario: PkCalibrationDebugScenario):
                PkCalibrationDebugSourceResult = throw CancellationException("cancel fixture")
        }
        val liveViewModel = PkCalibrationDebugViewModel(
            scenarioSource = cancellingSource,
            debugGate = enabled,
        )
        advanceUntilIdle()
        assertEquals(PkCalibrationDebugLoadState.IDLE, liveViewModel.uiState.value.loadState)
        assertTrue(liveViewModel.uiState.value.actionLog.isEmpty())

        val fixtureViewModel = PkCalibrationDebugViewModel(
            scenarioSource = cancellingSource,
            debugGate = enabled,
            autoLoadLive = false,
        )
        assertThrows(CancellationException::class.java) {
            fixtureViewModel.applyPreset(PkCalibrationDebugPreset.POPULATION_ONLY)
        }
        assertEquals(PkCalibrationDebugLoadState.IDLE, fixtureViewModel.uiState.value.loadState)
        assertTrue(fixtureViewModel.uiState.value.actionLog.isEmpty())

        val initial = liveSnapshot(
            PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.INJECTION_REVIEW_FIT)
        )
        val handlerViewModel = PkCalibrationDebugViewModel(
            scenarioSource = RecordingSource(available(initial)),
            reviewActionHandler = PkCalibrationDebugReviewActionHandler {
                throw CancellationException("cancel handler")
            },
            debugGate = enabled,
        )
        advanceUntilIdle()
        val logSizeBefore = handlerViewModel.uiState.value.actionLog.size
        handlerViewModel.performReviewAction(PkCalibrationDebugActionCommand(
            PkCalibrationDebugReviewAction.KEEP,
            PkCalibrationDebugFixtures.outlierId(PkCalibrationRoute.INJECTION),
        ))
        advanceUntilIdle()
        assertFalse(handlerViewModel.uiState.value.liveReviewActionInFlight)
        assertEquals(logSizeBefore, handlerViewModel.uiState.value.actionLog.size)
    }

    @Test
    fun fixtureSelectionInvalidatesAnInFlightLiveLoad() = runTest {
        val deferredLive = CompletableDeferred<PkCalibrationDebugSourceResult>()
        val source = object : PkCalibrationDebugScenarioSource {
            override suspend fun loadLive(): PkCalibrationDebugSourceResult = deferredLive.await()
            override fun loadFixture(
                scenario: PkCalibrationDebugScenario,
            ): PkCalibrationDebugSourceResult = fixtureSource.loadFixture(scenario)
        }
        val viewModel = PkCalibrationDebugViewModel(
            scenarioSource = source,
            debugGate = enabled,
        )
        advanceUntilIdle()
        viewModel.applyPreset(PkCalibrationDebugPreset.ORAL_OUT_OF_RANGE)
        val fixtureState = viewModel.uiState.value

        deferredLive.complete(available(liveSnapshot(PkCalibrationDebugScenario.preset(
            PkCalibrationDebugPreset.INJECTION_CALIBRATED
        ))))
        advanceUntilIdle()

        assertEquals(PkCalibrationDebugMode.FIXTURE, viewModel.uiState.value.mode)
        assertEquals(fixtureState.rawResult, viewModel.uiState.value.rawResult)
        assertEquals(fixtureState.actionLog, viewModel.uiState.value.actionLog)
    }

    @Test
    fun resetReturnsToLive_withoutClearingAppendOnlyCauseEffectLog() = runTest {
        val source = RecordingSource(available(liveSnapshot(
            PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.POPULATION_ONLY)
        )))
        val viewModel = PkCalibrationDebugViewModel(
            scenarioSource = source,
            debugGate = enabled,
            autoLoadLive = false,
        )
        viewModel.applyPreset(PkCalibrationDebugPreset.MIXED_INJECTION_ORAL)
        viewModel.setBandUnavailable(true)
        val beforeReset = viewModel.uiState.value.actionLog

        assertEquals(PkCalibrationDebugDispatchResult.ACCEPTED, viewModel.resetToLive())
        advanceUntilIdle()

        val log = viewModel.uiState.value.actionLog
        assertEquals(beforeReset, log.take(beforeReset.size))
        assertEquals((1L..log.size.toLong()).toList(), log.map { it.sequence })
        assertEquals("RESET_TO_LIVE", log.last().cause)
        assertEquals(PkCalibrationDebugMode.LIVE, viewModel.uiState.value.mode)
        assertNull(viewModel.uiState.value.scenario)
    }

    @Test
    fun releaseViewModelHandlers_areNoOpsAndRejectBeforeAnySourceOrActionCall() = runTest {
        var sourceCalls = 0
        var actionCalls = 0
        val source = object : PkCalibrationDebugScenarioSource {
            override suspend fun loadLive(): PkCalibrationDebugSourceResult {
                sourceCalls += 1
                return PkCalibrationDebugSourceResult.DebugDisabled
            }

            override fun loadFixture(
                scenario: PkCalibrationDebugScenario,
            ): PkCalibrationDebugSourceResult {
                sourceCalls += 1
                return PkCalibrationDebugSourceResult.DebugDisabled
            }
        }
        val viewModel = PkCalibrationDebugViewModel(
            scenarioSource = source,
            reviewActionHandler = PkCalibrationDebugReviewActionHandler {
                actionCalls += 1
                PkCalibrationDebugReviewExecution.DebugDisabled
            },
            debugGate = PkCalibrationDebugGate { false },
        )
        val resultId = UUID(1L, 1L)
        val results = listOf(
            viewModel.resetToLive(),
            viewModel.refreshLive(),
            viewModel.applyPreset(PkCalibrationDebugPreset.POPULATION_ONLY),
            viewModel.selectGlobalState(PkCalibrationGlobalState.READY),
            viewModel.selectRouteState(
                PkCalibrationRoute.INJECTION,
                PkRouteCalibrationDisplayState.LAB_CALIBRATED,
            ),
            viewModel.setRouteRenderFallback(PkCalibrationRoute.INJECTION),
            viewModel.setBandUnavailable(true),
            viewModel.setCentralUnavailable(true),
            viewModel.setOutlierRoute(PkCalibrationRoute.INJECTION),
            viewModel.setNonPositiveInput(true),
            viewModel.setDisplayCapBoundaryRoute(PkCalibrationRoute.INJECTION),
            viewModel.performReviewAction(PkCalibrationDebugActionCommand(
                PkCalibrationDebugReviewAction.EXCLUDE,
                resultId,
            )),
        )
        advanceUntilIdle()

        assertTrue(results.all {
            it == PkCalibrationDebugDispatchResult.REJECTED_DEBUG_DISABLED
        })
        assertEquals(0, sourceCalls)
        assertEquals(0, actionCalls)
        assertFalse(viewModel.uiState.value.debugEnabled)
        assertEquals(PkCalibrationDebugLoadState.DEBUG_DISABLED, viewModel.uiState.value.loadState)
        assertTrue(viewModel.uiState.value.actionLog.isEmpty())
    }

    @Test
    fun guardedServiceHandler_rejectsReleaseBeforeCallingService() = runTest {
        val service = mockk<PkCalibrationReviewActionService>()
        val handler = GuardedPkCalibrationDebugReviewActionHandler(
            service = service,
            debugGate = PkCalibrationDebugGate { false },
        )
        val command = PkCalibrationDebugActionCommand(
            PkCalibrationDebugReviewAction.KEEP,
            UUID(2L, 2L),
        )

        assertSame(PkCalibrationDebugReviewExecution.DebugDisabled, handler.execute(command))
        coVerify(exactly = 0) { service.keepForAdjustment(any()) }
        coVerify(exactly = 0) { service.exclude(any()) }
        coVerify(exactly = 0) { service.reinclude(any()) }
    }

    @Test
    fun inapplicableAndRejectedLiveActions_areVisibleInTheActionLog() = runTest {
        val initial = liveSnapshot(
            PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.INJECTION_REVIEW_FIT)
        )
        val source = RecordingSource(available(initial))
        val handler = PkCalibrationDebugReviewActionHandler {
            PkCalibrationDebugReviewExecution.Completed(
                PkCalibrationReviewActionResult.Rejected(
                    PkCalibrationReviewActionRejection.NOT_CURRENT_UNREVIEWED_OUTLIER
                )
            )
        }
        val viewModel = PkCalibrationDebugViewModel(source, handler, enabled)
        advanceUntilIdle()
        val resultId = PkCalibrationDebugFixtures.outlierId(PkCalibrationRoute.INJECTION)

        assertEquals(
            PkCalibrationDebugDispatchResult.REJECTED_NOT_APPLICABLE,
            viewModel.performReviewAction(PkCalibrationDebugActionCommand(
                PkCalibrationDebugReviewAction.REINCLUDE,
                resultId,
            )),
        )
        assertEquals("REJECTED:NOT_APPLICABLE", viewModel.uiState.value.actionLog.last().effect)

        viewModel.performReviewAction(PkCalibrationDebugActionCommand(
            PkCalibrationDebugReviewAction.KEEP,
            resultId,
        ))
        advanceUntilIdle()
        assertEquals(
            "REJECTED:NOT_CURRENT_UNREVIEWED_OUTLIER",
            viewModel.uiState.value.actionLog.last().effect,
        )
    }

    private fun fixtureViewModel(): PkCalibrationDebugViewModel =
        PkCalibrationDebugViewModel(
            scenarioSource = fixtureSource,
            debugGate = enabled,
            autoLoadLive = false,
        )

    private fun liveSnapshot(scenario: PkCalibrationDebugScenario): PkCalibrationDebugSnapshot {
        val fixture = fixtureSource.loadFixture(scenario).availableSnapshot()
        return fixture.copy(
            kind = PkCalibrationDebugSnapshotKind.LIVE_BOUND_EVALUATION,
            scenario = null,
        )
    }

    private fun acceptedMetadata(resultId: UUID): E2CalibrationMetadata =
        metadata(resultId, E2CalibrationDisposition.ACCEPTED)

    private fun metadata(
        resultId: UUID,
        disposition: E2CalibrationDisposition,
    ): E2CalibrationMetadata {
        val record = if (disposition == E2CalibrationDisposition.ACCEPTED) {
            requireNotNull(PkCalibrationAcceptanceRecord.create(
                calibrationModelVersion = "pk-calibration:test/v9",
                sourceValueBits = "4059000000000000",
                collectedAtEpochMillis = 600L,
            ))
        } else {
            null
        }
        return requireNotNull(E2CalibrationMetadata.create(
            resultId = resultId,
            disposition = disposition,
            acceptedRecord = record,
            updatedAt = Instant.EPOCH,
        ))
    }

    private fun available(
        snapshot: PkCalibrationDebugSnapshot,
    ): PkCalibrationDebugSourceResult = PkCalibrationDebugSourceResult.Available(snapshot)

    private fun PkCalibrationDebugSourceResult.availableSnapshot(): PkCalibrationDebugSnapshot =
        (this as PkCalibrationDebugSourceResult.Available).snapshot

    private inner class RecordingSource(
        var liveResult: PkCalibrationDebugSourceResult,
    ) : PkCalibrationDebugScenarioSource {
        var liveCalls: Int = 0
            private set
        var fixtureCalls: Int = 0
            private set

        override suspend fun loadLive(): PkCalibrationDebugSourceResult {
            liveCalls += 1
            return liveResult
        }

        override fun loadFixture(
            scenario: PkCalibrationDebugScenario,
        ): PkCalibrationDebugSourceResult {
            fixtureCalls += 1
            return fixtureSource.loadFixture(scenario)
        }
    }

    private inner class ObservableRecordingSource(
        initial: PkCalibrationDebugSourceResult,
    ) : PkCalibrationDebugScenarioSource {
        val live = MutableStateFlow(initial)
        var retryCalls: Int = 0
            private set

        override suspend fun loadLive(): PkCalibrationDebugSourceResult = live.value

        override fun observeLive(): Flow<PkCalibrationDebugSourceResult> = live

        override fun retryLive() {
            retryCalls += 1
        }

        override fun loadFixture(
            scenario: PkCalibrationDebugScenario,
        ): PkCalibrationDebugSourceResult = fixtureSource.loadFixture(scenario)
    }
}
