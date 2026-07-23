package com.mkx.hrttracker.ui.pkcalibrationdebug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.PkCalibrationReviewActionResult
import com.mkx.hrttracker.data.repository.PkCalibrationReviewActionService
import com.mkx.hrttracker.model.pk.E2CalibrationDisposition
import com.mkx.hrttracker.model.pk.PkCalibrationBandState
import com.mkx.hrttracker.model.pk.PkCalibrationGlobalState
import com.mkx.hrttracker.model.pk.PkCalibrationReason
import com.mkx.hrttracker.model.pk.PkCalibrationRenderResult
import com.mkx.hrttracker.model.pk.PkCalibrationRenderState
import com.mkx.hrttracker.model.pk.PkCalibrationResult
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.model.pk.PkCurvePoint
import com.mkx.hrttracker.model.pk.PkPersonalParams
import com.mkx.hrttracker.model.pk.PkPredictiveBandKnot
import com.mkx.hrttracker.model.pk.PkRouteCalibrationDisplayState
import com.mkx.hrttracker.model.pk.PkRouteCalibrationResult
import java.util.UUID
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PkCalibrationDebugMode {
    LIVE,
    FIXTURE,
}

enum class PkCalibrationDebugLoadState {
    IDLE,
    LOADING,
    AVAILABLE,
    UNAVAILABLE,
    DEBUG_DISABLED,
}

enum class PkCalibrationDebugReviewAction {
    KEEP,
    EXCLUDE,
    REINCLUDE,
}

data class PkCalibrationDebugActionCommand(
    val action: PkCalibrationDebugReviewAction,
    val resultId: UUID,
)

data class PkCalibrationDebugLogEntry(
    val sequence: Long,
    val cause: String,
    val effect: String,
)

enum class PkCalibrationDebugDispatchResult {
    ACCEPTED,
    REJECTED_DEBUG_DISABLED,
    REJECTED_LIVE_REVIEW_IN_FLIGHT,
    REJECTED_NOT_APPLICABLE,
    REJECTED_SOURCE_DISABLED,
    REJECTED_SOURCE_UNAVAILABLE,
    REJECTED_SOURCE_EXCEPTION,
    REJECTED_SOURCE_CONTRACT_MISMATCH,
}

data class PkCalibrationDebugUiState(
    val debugEnabled: Boolean,
    val mode: PkCalibrationDebugMode = PkCalibrationDebugMode.LIVE,
    val loadState: PkCalibrationDebugLoadState = PkCalibrationDebugLoadState.IDLE,
    val sourceUnavailableReason: PkCalibrationDebugSourceUnavailableReason? = null,
    val snapshotKind: PkCalibrationDebugSnapshotKind? = null,
    val scenario: PkCalibrationDebugScenario? = null,
    val liveReviewActionInFlight: Boolean = false,
    val rawResult: PkCalibrationResult? = null,
    val rawRender: PkCalibrationRenderResult? = null,
    val globalState: PkCalibrationGlobalState? = null,
    val globalReasons: Set<PkCalibrationReason> = emptySet(),
    val routeRows: List<PkRouteCalibrationResult> = emptyList(),
    val promotedRoutes: List<PkCalibrationRoute> = emptyList(),
    val displayParams: PkPersonalParams = PkPersonalParams.population(),
    val renderState: PkCalibrationRenderState? = null,
    val renderReasons: Set<PkCalibrationReason> = emptySet(),
    val effectivePromotedRoutes: List<PkCalibrationRoute> = emptyList(),
    val effectiveDisplayParams: PkPersonalParams = PkPersonalParams.population(),
    val routeRenderFallbacks: List<PkCalibrationRoute> = emptyList(),
    val centralCurve: List<PkCurvePoint> = emptyList(),
    val bandState: PkCalibrationBandState? = null,
    val bandReasons: Set<PkCalibrationReason> = emptySet(),
    val bandKnots: List<PkPredictiveBandKnot> = emptyList(),
    val reviewDispositionByResultId: Map<UUID, E2CalibrationDisposition> = emptyMap(),
    val applicableActionCommands: List<PkCalibrationDebugActionCommand> = emptyList(),
    val actionLog: List<PkCalibrationDebugLogEntry> = emptyList(),
)

sealed interface PkCalibrationDebugReviewExecution {
    data object DebugDisabled : PkCalibrationDebugReviewExecution
    data object Unavailable : PkCalibrationDebugReviewExecution
    data class Completed(val result: PkCalibrationReviewActionResult) :
        PkCalibrationDebugReviewExecution
}

fun interface PkCalibrationDebugReviewActionHandler {
    suspend fun execute(command: PkCalibrationDebugActionCommand):
        PkCalibrationDebugReviewExecution
}

/** The only adapter in this package that may cross into durable review storage. */
class GuardedPkCalibrationDebugReviewActionHandler(
    private val service: PkCalibrationReviewActionService,
    private val debugGate: PkCalibrationDebugGate = PkCalibrationDebugGate.Build,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PkCalibrationDebugReviewActionHandler {
    override suspend fun execute(
        command: PkCalibrationDebugActionCommand,
    ): PkCalibrationDebugReviewExecution {
        if (!debugGate.isEnabled()) return PkCalibrationDebugReviewExecution.DebugDisabled
        val result = withContext(defaultDispatcher) {
            when (command.action) {
                PkCalibrationDebugReviewAction.KEEP ->
                    service.keepForAdjustment(command.resultId)
                PkCalibrationDebugReviewAction.EXCLUDE -> service.exclude(command.resultId)
                PkCalibrationDebugReviewAction.REINCLUDE -> service.reinclude(command.resultId)
            }
        }
        return PkCalibrationDebugReviewExecution.Completed(result)
    }
}

object UnavailablePkCalibrationDebugReviewActionHandler :
    PkCalibrationDebugReviewActionHandler {
    override suspend fun execute(
        command: PkCalibrationDebugActionCommand,
    ): PkCalibrationDebugReviewExecution {
        return if (PkCalibrationDebugGate.Build.isEnabled()) {
            PkCalibrationDebugReviewExecution.Unavailable
        } else {
            PkCalibrationDebugReviewExecution.DebugDisabled
        }
    }
}

/**
 * State holder for the dev-only contract harness. Every public event boundary checks the build
 * gate. Fixture review actions update only this in-memory state; Live actions are the sole path
 * to [PkCalibrationReviewActionService].
 */
data class PkCalibrationDebugViewModelConfig(
    val debugGate: PkCalibrationDebugGate,
    val autoLoadLive: Boolean,
)

@HiltViewModel
class PkCalibrationDebugViewModel @Inject constructor(
    private val scenarioSource: PkCalibrationDebugScenarioSource,
    private val reviewActionHandler: PkCalibrationDebugReviewActionHandler,
    config: PkCalibrationDebugViewModelConfig,
) : ViewModel() {
    private val debugGate = config.debugGate
    private val autoLoadLive = config.autoLoadLive

    internal constructor(
        scenarioSource: PkCalibrationDebugScenarioSource =
            DefaultPkCalibrationDebugScenarioSource(),
        reviewActionHandler: PkCalibrationDebugReviewActionHandler =
            UnavailablePkCalibrationDebugReviewActionHandler,
        debugGate: PkCalibrationDebugGate = PkCalibrationDebugGate.Build,
        autoLoadLive: Boolean = true,
    ) : this(
        scenarioSource = scenarioSource,
        reviewActionHandler = reviewActionHandler,
        config = PkCalibrationDebugViewModelConfig(debugGate, autoLoadLive),
    )
    private val _uiState = MutableStateFlow(
        PkCalibrationDebugUiState(
            debugEnabled = debugGate.isEnabled(),
            loadState = if (debugGate.isEnabled()) {
                PkCalibrationDebugLoadState.IDLE
            } else {
                PkCalibrationDebugLoadState.DEBUG_DISABLED
            },
        )
    )
    val uiState: StateFlow<PkCalibrationDebugUiState> = _uiState.asStateFlow()

    private var requestGeneration: Long = 0L
    private var nextLogSequence: Long = 1L
    private var activeLiveReviewToken: Long? = null

    init {
        if (autoLoadLive && debugGate.isEnabled()) {
            loadLive(cause = "INITIAL_LIVE_LOAD")
            viewModelScope.launch {
                scenarioSource.observeLive().collect { result ->
                    if (_uiState.value.mode == PkCalibrationDebugMode.LIVE) {
                        ++requestGeneration
                        applySourceResult(
                            mode = PkCalibrationDebugMode.LIVE,
                            result = result,
                            cause = "LIVE_STATE_CHANGED",
                        )
                    }
                }
            }
        }
    }

    fun resetToLive(): PkCalibrationDebugDispatchResult {
        transitionRejection("RESET_TO_LIVE")?.let { rejection -> return rejection }
        loadLive(cause = "RESET_TO_LIVE")
        return PkCalibrationDebugDispatchResult.ACCEPTED
    }

    fun refreshLive(): PkCalibrationDebugDispatchResult {
        transitionRejection("REFRESH_LIVE")?.let { rejection -> return rejection }
        scenarioSource.retryLive()
        loadLive(cause = "REFRESH_LIVE")
        return PkCalibrationDebugDispatchResult.ACCEPTED
    }

    fun selectGlobalState(
        state: PkCalibrationGlobalState,
    ): PkCalibrationDebugDispatchResult = updateFixture(
        cause = "SELECT_GLOBAL_STATE:$state",
    ) { scenario -> scenario.withGlobalState(state) }

    fun selectRouteState(
        route: PkCalibrationRoute,
        state: PkRouteCalibrationDisplayState,
    ): PkCalibrationDebugDispatchResult = updateFixture(
        cause = "SELECT_ROUTE_STATE:${route.stableId}:$state",
    ) { scenario -> scenario.withRouteState(route, state) }

    fun applyPreset(
        preset: PkCalibrationDebugPreset,
    ): PkCalibrationDebugDispatchResult {
        transitionRejection("APPLY_PRESET:$preset")?.let { rejection -> return rejection }
        return replaceFixture(
            cause = "APPLY_PRESET:$preset",
            scenario = PkCalibrationDebugScenario.preset(preset),
        )
    }

    fun setRouteRenderFallback(
        route: PkCalibrationRoute?,
    ): PkCalibrationDebugDispatchResult = updateFixture(
        cause = "SET_ROUTE_RENDER_FALLBACK:${route?.stableId ?: "none"}",
    ) { scenario -> scenario.withRouteRenderFallback(route) }

    fun setBandUnavailable(value: Boolean): PkCalibrationDebugDispatchResult = updateFixture(
        cause = "SET_BAND_UNAVAILABLE:$value",
    ) { scenario -> scenario.withBandUnavailable(value) }

    fun setCentralUnavailable(value: Boolean): PkCalibrationDebugDispatchResult = updateFixture(
        cause = "SET_CENTRAL_UNAVAILABLE:$value",
    ) { scenario -> scenario.withCentralUnavailable(value) }

    fun setOutlierRoute(
        route: PkCalibrationRoute?,
    ): PkCalibrationDebugDispatchResult = updateFixture(
        cause = "SET_OUTLIER_ROUTE:${route?.stableId ?: "none"}",
    ) { scenario -> scenario.withOutlierRoute(route) }

    fun setNonPositiveInput(value: Boolean): PkCalibrationDebugDispatchResult = updateFixture(
        cause = "SET_NONPOSITIVE_INPUT:$value",
    ) { scenario -> scenario.withNonPositiveInput(value) }

    fun setGuardDroppedRoute(
        route: PkCalibrationRoute?,
    ): PkCalibrationDebugDispatchResult = updateFixture(
        cause = "SET_GUARD_DROPPED_ROUTE:${route?.stableId ?: "none"}",
    ) { scenario -> scenario.withGuardDroppedRoute(route) }

    fun setDisplayCapBoundaryRoute(
        route: PkCalibrationRoute?,
    ): PkCalibrationDebugDispatchResult = updateFixture(
        cause = "SET_DISPLAY_CAP_BOUNDARY_ROUTE:${route?.stableId ?: "none"}",
    ) { scenario -> scenario.withDisplayCapBoundaryRoute(route) }

    fun performReviewAction(
        command: PkCalibrationDebugActionCommand,
    ): PkCalibrationDebugDispatchResult {
        if (!debugGate.isEnabled()) return PkCalibrationDebugDispatchResult.REJECTED_DEBUG_DISABLED
        if (_uiState.value.liveReviewActionInFlight) {
            appendLog(
                cause = reviewCause(command),
                effect = "REJECTED:LIVE_REVIEW_IN_FLIGHT",
            )
            return PkCalibrationDebugDispatchResult.REJECTED_LIVE_REVIEW_IN_FLIGHT
        }
        if (command !in _uiState.value.applicableActionCommands) {
            appendLog(
                cause = reviewCause(command),
                effect = "REJECTED:NOT_APPLICABLE",
            )
            return PkCalibrationDebugDispatchResult.REJECTED_NOT_APPLICABLE
        }
        return when (_uiState.value.mode) {
            PkCalibrationDebugMode.FIXTURE -> applyFixtureReviewAction(command)
            PkCalibrationDebugMode.LIVE -> {
                applyLiveReviewAction(command)
                PkCalibrationDebugDispatchResult.ACCEPTED
            }
        }
    }

    private fun loadLive(cause: String) {
        val generation = ++requestGeneration
        _uiState.value = clearedSnapshotState(
            previous = _uiState.value,
            mode = PkCalibrationDebugMode.LIVE,
            loadState = PkCalibrationDebugLoadState.LOADING,
        )
        viewModelScope.launch {
            val result = try {
                scenarioSource.loadLive()
            } catch (error: CancellationException) {
                if (generation == requestGeneration) {
                    _uiState.value = clearedSnapshotState(
                        previous = _uiState.value,
                        mode = PkCalibrationDebugMode.LIVE,
                        loadState = PkCalibrationDebugLoadState.IDLE,
                    )
                }
                throw error
            } catch (_: Exception) {
                if (generation == requestGeneration) {
                    applySourceException(PkCalibrationDebugMode.LIVE, cause)
                }
                return@launch
            }
            if (generation != requestGeneration) return@launch
            applySourceResult(
                mode = PkCalibrationDebugMode.LIVE,
                result = result,
                cause = cause,
            )
        }
    }

    private fun updateFixture(
        cause: String,
        transform: (PkCalibrationDebugScenario) -> PkCalibrationDebugScenario,
    ): PkCalibrationDebugDispatchResult {
        transitionRejection(cause)?.let { rejection -> return rejection }
        val base = _uiState.value.scenario
            ?: PkCalibrationDebugScenario.preset(PkCalibrationDebugPreset.POPULATION_ONLY)
        return replaceFixture(cause, transform(base))
    }

    private fun replaceFixture(
        cause: String,
        scenario: PkCalibrationDebugScenario,
        effectPrefix: String? = null,
    ): PkCalibrationDebugDispatchResult {
        ++requestGeneration
        val result = try {
            scenarioSource.loadFixture(scenario)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            applySourceException(PkCalibrationDebugMode.FIXTURE, cause)
            return PkCalibrationDebugDispatchResult.REJECTED_SOURCE_EXCEPTION
        }
        return applySourceResult(
            mode = PkCalibrationDebugMode.FIXTURE,
            result = result,
            cause = cause,
            effectPrefix = effectPrefix,
        )
    }

    private fun applyFixtureReviewAction(
        command: PkCalibrationDebugActionCommand,
    ): PkCalibrationDebugDispatchResult {
        val scenario = _uiState.value.scenario
            ?: return PkCalibrationDebugDispatchResult.REJECTED_NOT_APPLICABLE
        val disposition = when (command.action) {
            PkCalibrationDebugReviewAction.KEEP ->
                PkCalibrationDebugFixtureDisposition.ACCEPTED
            PkCalibrationDebugReviewAction.EXCLUDE ->
                PkCalibrationDebugFixtureDisposition.EXCLUDED
            PkCalibrationDebugReviewAction.REINCLUDE ->
                PkCalibrationDebugFixtureDisposition.AUTO
        }
        return replaceFixture(
            cause = reviewCause(command),
            scenario = scenario.withFixtureDisposition(disposition),
            effectPrefix = "FIXTURE_DISPOSITION:${scenario.fixtureDisposition}->$disposition",
        )
    }

    private fun applyLiveReviewAction(command: PkCalibrationDebugActionCommand) {
        val token = ++requestGeneration
        activeLiveReviewToken = token
        _uiState.value = _uiState.value.copy(liveReviewActionInFlight = true)
        viewModelScope.launch {
            try {
                when (val execution = reviewActionHandler.execute(command)) {
                    PkCalibrationDebugReviewExecution.DebugDisabled -> {
                        if (isActiveLiveReview(token)) {
                            appendLog(reviewCause(command), "REJECTED:DEBUG_DISABLED")
                        }
                    }
                    PkCalibrationDebugReviewExecution.Unavailable -> {
                        if (isActiveLiveReview(token)) {
                            appendLog(reviewCause(command), "REJECTED:LIVE_ACTION_UNAVAILABLE")
                        }
                    }
                    is PkCalibrationDebugReviewExecution.Completed -> {
                        when (val result = execution.result) {
                            is PkCalibrationReviewActionResult.Rejected -> {
                                if (isActiveLiveReview(token)) {
                                    appendLog(
                                        reviewCause(command),
                                        "REJECTED:${result.reason}",
                                    )
                                }
                            }
                            is PkCalibrationReviewActionResult.Applied -> {
                                // Log the durable commit before the refetch suspension. A failed,
                                // cancelled, or rejected refresh must never erase this effect.
                                appendLog(
                                    reviewCause(command),
                                    "COMMITTED:${result.metadata.disposition}",
                                )
                                refreshAfterCommittedReview(token, command)
                            }
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (isActiveLiveReview(token)) {
                    appendLog(reviewCause(command), "REJECTED:HANDLER_EXCEPTION")
                }
            } finally {
                if (activeLiveReviewToken == token) {
                    activeLiveReviewToken = null
                    _uiState.value = _uiState.value.copy(liveReviewActionInFlight = false)
                }
            }
        }
    }

    private suspend fun refreshAfterCommittedReview(
        token: Long,
        command: PkCalibrationDebugActionCommand,
    ) {
        val cause = "POST_COMMIT_REFRESH:${reviewCause(command)}"
        if (!isActiveLiveReview(token)) {
            appendLog(cause, "REJECTED:STALE_BEFORE_REFETCH")
            return
        }
        val refreshed = try {
            scenarioSource.loadLive()
        } catch (error: CancellationException) {
            if (isActiveLiveReview(token)) {
                // The durable action already committed, so the pre-action snapshot is stale.
                // Fail closed without classifying cancellation as an ordinary source failure.
                _uiState.value = clearedSnapshotState(
                    previous = _uiState.value,
                    mode = PkCalibrationDebugMode.LIVE,
                    loadState = PkCalibrationDebugLoadState.IDLE,
                )
            }
            throw error
        } catch (_: Exception) {
            if (isActiveLiveReview(token)) {
                applySourceException(PkCalibrationDebugMode.LIVE, cause)
            }
            return
        }
        if (!isActiveLiveReview(token)) {
            appendLog(cause, "REJECTED:STALE_AFTER_REFETCH")
            return
        }
        applySourceResult(
            mode = PkCalibrationDebugMode.LIVE,
            result = refreshed,
            cause = cause,
        )
    }

    private fun applySourceResult(
        mode: PkCalibrationDebugMode,
        result: PkCalibrationDebugSourceResult,
        cause: String,
        effectPrefix: String? = null,
    ): PkCalibrationDebugDispatchResult {
        val previous = _uiState.value
        val (next, effect, dispatchResult) = when (result) {
            PkCalibrationDebugSourceResult.Loading -> {
                Triple(
                    clearedSnapshotState(
                        previous = previous,
                        mode = mode,
                        loadState = PkCalibrationDebugLoadState.LOADING,
                    ),
                    "LOADING",
                    PkCalibrationDebugDispatchResult.ACCEPTED,
                )
            }
            PkCalibrationDebugSourceResult.DebugDisabled -> {
                Triple(
                    clearedSnapshotState(
                        previous = previous,
                        mode = mode,
                        loadState = PkCalibrationDebugLoadState.DEBUG_DISABLED,
                        debugEnabled = false,
                    ),
                    "REJECTED:SOURCE_DISABLED",
                    PkCalibrationDebugDispatchResult.REJECTED_SOURCE_DISABLED,
                )
            }
            is PkCalibrationDebugSourceResult.Unavailable -> {
                Triple(
                    clearedSnapshotState(
                        previous = previous,
                        mode = mode,
                        loadState = PkCalibrationDebugLoadState.UNAVAILABLE,
                    ).copy(sourceUnavailableReason = result.reason),
                    "UNAVAILABLE:${result.reason}",
                    PkCalibrationDebugDispatchResult.REJECTED_SOURCE_UNAVAILABLE,
                )
            }
            is PkCalibrationDebugSourceResult.Available -> {
                if (!result.snapshot.isCompatibleWith(mode)) {
                    Triple(
                        clearedSnapshotState(
                            previous = previous,
                            mode = mode,
                            loadState = PkCalibrationDebugLoadState.UNAVAILABLE,
                        ).copy(
                            sourceUnavailableReason =
                                PkCalibrationDebugSourceUnavailableReason
                                    .SOURCE_CONTRACT_MISMATCH
                        ),
                        "REJECTED:SOURCE_CONTRACT_MISMATCH:${result.snapshot.kind}",
                        PkCalibrationDebugDispatchResult
                            .REJECTED_SOURCE_CONTRACT_MISMATCH,
                    )
                } else {
                    Triple(
                        stateFromSnapshot(previous, mode, result.snapshot),
                        "AVAILABLE:${result.snapshot.kind}:" +
                                result.snapshot.result.globalState,
                        PkCalibrationDebugDispatchResult.ACCEPTED,
                    )
                }
            }
        }
        _uiState.value = next
        appendLog(
            cause = cause,
            effect = listOfNotNull(
                if (dispatchResult == PkCalibrationDebugDispatchResult.ACCEPTED) {
                    effectPrefix
                } else {
                    null
                },
                effect,
            ).joinToString(";")
        )
        return dispatchResult
    }

    private fun applySourceException(mode: PkCalibrationDebugMode, cause: String) {
        _uiState.value = clearedSnapshotState(
            previous = _uiState.value,
            mode = mode,
            loadState = PkCalibrationDebugLoadState.UNAVAILABLE,
        ).copy(
            sourceUnavailableReason = PkCalibrationDebugSourceUnavailableReason.SOURCE_EXCEPTION
        )
        appendLog(cause, "UNAVAILABLE:SOURCE_EXCEPTION")
    }

    private fun transitionRejection(cause: String): PkCalibrationDebugDispatchResult? {
        if (!debugGate.isEnabled()) {
            return PkCalibrationDebugDispatchResult.REJECTED_DEBUG_DISABLED
        }
        if (_uiState.value.liveReviewActionInFlight) {
            appendLog(cause, "REJECTED:LIVE_REVIEW_IN_FLIGHT")
            return PkCalibrationDebugDispatchResult.REJECTED_LIVE_REVIEW_IN_FLIGHT
        }
        return null
    }

    private fun isActiveLiveReview(token: Long): Boolean {
        return activeLiveReviewToken == token &&
                _uiState.value.liveReviewActionInFlight &&
                _uiState.value.mode == PkCalibrationDebugMode.LIVE
    }

    private fun PkCalibrationDebugSnapshot.isCompatibleWith(
        mode: PkCalibrationDebugMode,
    ): Boolean = when (mode) {
        PkCalibrationDebugMode.LIVE ->
            kind == PkCalibrationDebugSnapshotKind.LIVE_BOUND_EVALUATION &&
                    scenario == null
        PkCalibrationDebugMode.FIXTURE ->
            kind in setOf(
                PkCalibrationDebugSnapshotKind.SYNTHETIC_ENGINE_EVALUATION,
                PkCalibrationDebugSnapshotKind.VALIDATED_CONTRACT_FIXTURE,
                PkCalibrationDebugSnapshotKind.VALIDATED_FAULT_FIXTURE,
            ) && scenario != null
    }

    private fun stateFromSnapshot(
        previous: PkCalibrationDebugUiState,
        mode: PkCalibrationDebugMode,
        snapshot: PkCalibrationDebugSnapshot,
    ): PkCalibrationDebugUiState {
        val result = snapshot.result
        val render = snapshot.render
        val reviewDispositions = canonicalReviewDispositions(snapshot, result)
        return previous.copy(
            debugEnabled = true,
            mode = mode,
            loadState = PkCalibrationDebugLoadState.AVAILABLE,
            sourceUnavailableReason = null,
            snapshotKind = snapshot.kind,
            scenario = snapshot.scenario,
            rawResult = result,
            rawRender = render,
            globalState = result.globalState,
            globalReasons = result.globalReasons,
            routeRows = result.routeResults,
            promotedRoutes = result.promotedRoutes,
            displayParams = result.displayParams,
            renderState = render?.renderState,
            renderReasons = render?.renderReasons.orEmpty(),
            effectivePromotedRoutes = render?.effectivePromotedRoutes.orEmpty(),
            effectiveDisplayParams = render?.effectiveDisplayParams
                ?: PkPersonalParams.population(),
            routeRenderFallbacks = render?.routeRenderFallbacks.orEmpty(),
            centralCurve = render?.centralCurve.orEmpty(),
            bandState = render?.bandState,
            bandReasons = render?.bandReasons.orEmpty(),
            bandKnots = render?.bandKnots.orEmpty(),
            reviewDispositionByResultId = reviewDispositions,
            applicableActionCommands = applicableCommands(result, reviewDispositions),
        )
    }

    private fun canonicalReviewDispositions(
        snapshot: PkCalibrationDebugSnapshot,
        result: PkCalibrationResult,
    ): Map<UUID, E2CalibrationDisposition> {
        val current = snapshot.reviewDispositionByResultId.toMutableMap()
        result.routeResults.forEach { routeResult ->
            routeResult.unreviewedOutlierLabIds.forEach { resultId ->
                current[resultId] = E2CalibrationDisposition.AUTO
            }
        }
        return current.entries
            .sortedBy { entry -> entry.key.toString() }
            .associateTo(linkedMapOf()) { entry -> entry.key to entry.value }
    }

    private fun applicableCommands(
        result: PkCalibrationResult,
        dispositions: Map<UUID, E2CalibrationDisposition>,
    ): List<PkCalibrationDebugActionCommand> {
        val currentOutlierIds = result.routeResults
            .flatMap(PkRouteCalibrationResult::unreviewedOutlierLabIds)
            .toSet()
        return dispositions.flatMap { (resultId, disposition) ->
            when (disposition) {
                E2CalibrationDisposition.AUTO -> if (resultId in currentOutlierIds) {
                    listOf(
                        PkCalibrationDebugActionCommand(
                            PkCalibrationDebugReviewAction.KEEP,
                            resultId,
                        ),
                        PkCalibrationDebugActionCommand(
                            PkCalibrationDebugReviewAction.EXCLUDE,
                            resultId,
                        ),
                    )
                } else {
                    emptyList()
                }
                E2CalibrationDisposition.ACCEPTED -> listOf(
                    PkCalibrationDebugActionCommand(
                        PkCalibrationDebugReviewAction.EXCLUDE,
                        resultId,
                    )
                )
                E2CalibrationDisposition.EXCLUDED -> listOf(
                    PkCalibrationDebugActionCommand(
                        PkCalibrationDebugReviewAction.REINCLUDE,
                        resultId,
                    )
                )
            }
        }
    }

    private fun clearedSnapshotState(
        previous: PkCalibrationDebugUiState,
        mode: PkCalibrationDebugMode,
        loadState: PkCalibrationDebugLoadState,
        debugEnabled: Boolean = true,
    ): PkCalibrationDebugUiState = previous.copy(
        debugEnabled = debugEnabled,
        mode = mode,
        loadState = loadState,
        sourceUnavailableReason = null,
        snapshotKind = null,
        scenario = null,
        rawResult = null,
        rawRender = null,
        globalState = null,
        globalReasons = emptySet(),
        routeRows = emptyList(),
        promotedRoutes = emptyList(),
        displayParams = PkPersonalParams.population(),
        renderState = null,
        renderReasons = emptySet(),
        effectivePromotedRoutes = emptyList(),
        effectiveDisplayParams = PkPersonalParams.population(),
        routeRenderFallbacks = emptyList(),
        centralCurve = emptyList(),
        bandState = null,
        bandReasons = emptySet(),
        bandKnots = emptyList(),
        reviewDispositionByResultId = emptyMap(),
        applicableActionCommands = emptyList(),
    )

    private fun appendLog(cause: String, effect: String) {
        val entry = PkCalibrationDebugLogEntry(
            sequence = nextLogSequence++,
            cause = cause,
            effect = effect,
        )
        _uiState.value = _uiState.value.copy(
            actionLog = _uiState.value.actionLog + entry
        )
    }

    private fun reviewCause(command: PkCalibrationDebugActionCommand): String =
        "REVIEW_${command.action}:${command.resultId}"
}
