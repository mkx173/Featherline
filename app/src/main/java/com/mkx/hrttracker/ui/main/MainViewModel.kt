package com.mkx.hrttracker.ui.main

import com.mkx.hrttracker.ui.calibration.PkCalibrationUiState
import com.mkx.hrttracker.ui.calibration.pkCalibrationScreenState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkx.hrttracker.data.repository.CalibrationEstimate
import com.mkx.hrttracker.data.repository.E2EstimateRepository
import com.mkx.hrttracker.data.repository.E2Pending
import com.mkx.hrttracker.data.repository.E2State
import com.mkx.hrttracker.data.repository.HomeE2Estimate
import com.mkx.hrttracker.data.repository.HomeInputSource
import com.mkx.hrttracker.data.repository.HomeInputs
import com.mkx.hrttracker.data.repository.HomeRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.toBandKnot
import com.mkx.hrttracker.di.DefaultDispatcher
import com.mkx.hrttracker.di.ProcessLifecycleState
import com.mkx.hrttracker.model.bloodtest.AllowedAnalyteUnit
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.home.HomeCardType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicineStockProjection
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.medication.lowStockSeverityRank
import com.mkx.hrttracker.model.medication.visibleMedicationEntries
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.pk.PkCalibrationRoute
import com.mkx.hrttracker.ui.journal.toAnchorRowUiState
import com.mkx.hrttracker.util.AppTimeSnapshot
import com.mkx.hrttracker.util.AppTimeSource
import com.mkx.hrttracker.util.Held
import com.mkx.hrttracker.util.TimeZoneChangeNotice
import com.mkx.hrttracker.util.TimeZoneChangeNoticeController
import com.mkx.hrttracker.util.holdWhileUpdating
import com.mkx.hrttracker.util.tickWhileSubscribed
import com.mkx.hrttracker.util.medicationDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
    private val settingsRepository: SettingsRepository,
    private val timeZoneChangeNoticeController: TimeZoneChangeNoticeController,
    private val e2EstimateRepository: E2EstimateRepository,
    @ProcessLifecycleState processLifecycleState: StateFlow<@JvmSuppressWildcards Lifecycle.State>,
    appTimeSource: AppTimeSource,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val currentSnapshot = appTimeSource.currentSnapshot

    /** The app is in the foreground: the process lifecycle is at least STARTED. */
    private val foreground: Flow<Boolean> = processLifecycleState
        .map { state -> state.isAtLeast(Lifecycle.State.STARTED) }
        .distinctUntilChanged()

    /** The estimate the E2 cards in [uiState] show; null while they show the skeleton. */
    private var shownE2Estimate: HomeE2Estimate? = null

    /**
     * A return to the foreground's first estimate, when its key moved while
     * away but a background job already derived it: the state carrying it is
     * built on Main, in the pass that hid the old one, so no skeleton frame
     * comes between them.
     */
    private var inlineE2Estimate: HomeE2Estimate? = null

    // Home's own visibility: the activity collects uiState too, so its
    // subscriber count does not say whether Home is on screen.
    private val screenVisible = MutableStateFlow(false)

    private val _uiState = MutableStateFlow(
        MainUiState(
            homeDataReady = false,
            now = currentSnapshot.value.minute,
        )
    )

    // The ui-state combine below stays collected for the ViewModel's whole
    // (activity-scoped) lifetime: a stop-timeout would let data mutated while
    // away (e.g. a backup restore) flash one stale frame on re-entry, so data
    // emissions must keep rebuilding the retained value in the background.
    // The time inputs, however, are gated on this state's own subscriber
    // count: a live minute tick would otherwise rebuild the ui state every
    // minute for the activity's whole lifetime, including backgrounded.
    // While unsubscribed only date/zone changes pass through, keeping the
    // midnight/timezone re-anchor.
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val gatedSnapshot: StateFlow<AppTimeSnapshot> =
        currentSnapshot.tickWhileSubscribed(_uiState.subscriptionCount) { snapshot ->
            Triple(snapshot.minute.toLocalDate(), medicationDay(snapshot.minute, settingsRepository.settingsState.value.medicationDayBoundary), snapshot.zone)
        }
    private val gatedMinute: StateFlow<LocalDateTime> =
        appTimeSource.currentMinute.tickWhileSubscribed(_uiState.subscriptionCount) { minute ->
            minute.toLocalDate() to medicationDay(minute, settingsRepository.settingsState.value.medicationDayBoundary)
        }

    /**
     * The routes sheet the hero's calibration icon opens: the calibration
     * page's own estimate, so both screens show the same route rows. Null
     * until it is derived.
     */
    val pkCalibrationSheetState: StateFlow<PkCalibrationUiState?> = e2EstimateRepository
        .observeCalibration(gatedMinute)
        .map { state -> pkCalibrationSheetUiState(state.value) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            buildUiStateFlow().collect { state -> _uiState.value = state }
        }
    }

    // Re-subscribe the home inputs flow only on local date or zone changes.
    // Room query windows are date+zone-derived, while per-minute and explicit
    // wall-clock refreshes are fed to HomeRepository as a combine input so
    // now-sensitive projections re-anchor without tearing down the Room flows.
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun buildUiStateFlow(): Flow<MainUiState> {
        val keyedInputsFlow = gatedSnapshot
            .map { snapshot ->
                HomeTimeKey(
                    date = snapshot.minute.toLocalDate(),
                    zoneId = snapshot.zone,
                )
            }
            .distinctUntilChangedBy { key -> key.date to key.zoneId }
            .flatMapLatest { key ->
                homeRepository.observeHomeInputs(
                    date = key.date,
                    nowFlow = gatedMinute,
                    zoneId = key.zoneId,
                )
                    .map { inputs -> KeyedHomeInputs(key = key, inputs = inputs) }
            }

        // The E2 hero, chart, band and notes come from the estimate, held
        // while a change the user made is derived: curve, band and status
        // change together. The estimate is held, not its cards, so the live
        // time and display unit still render it; the dose lists and the rest
        // of Home show the change at once.
        val heldInputsFlow = combine(
            keyedInputsFlow,
            observeHomeEstimate(),
            settingsRepository.settingsState,
        ) { keyed, estimate, settings -> HeldHomeInputs(keyed, estimate, settings.homeE2DisplayUnit) }
        val homeStateFlow = combine(
            heldInputsFlow,
            gatedSnapshot,
            timeZoneChangeNoticeController.notice,
            settingsRepository.homeLowStockSectionExpandedFlow,
            settingsRepository.homeLowStockAcknowledgedWarningStatesFlow,
            ::HomeStateSources,
        )
            // Latest wins: a build still running for an older combination,
            // such as an estimate a return to the foreground dropped, never
            // lands after a newer one.
            .mapLatest { sources -> buildHomeState(sources) }
            .filterNotNull()

        return combine(
            homeStateFlow,
            settingsRepository.homeCardLayoutFlow,
        ) { state, layout ->
            state.copy(homeCardLayout = layout)
        }
    }

    /**
     * Home's estimate, held while a change the user made is derived. The
     * observation restarts each time the app comes to the foreground, so a
     * return follows the cold-start rule, and nothing observes it in the
     * background.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeHomeEstimate(): Flow<Held<E2State<HomeE2Estimate>>> = foreground
        .flatMapLatest { inForeground ->
            if (!inForeground) return@flatMapLatest emptyFlow()
            e2EstimateRepository.observeHome(gatedMinute)
                .withIndex()
                .onEach { (index, state) ->
                    // The restarted observation's first state is in before
                    // the first frame; a state rebuilt on Default may not be.
                    // An estimate whose key moved while away leaves the screen
                    // now, not a frame later, and one already derived replaces
                    // it in the same pass.
                    if (index == 0 && state.value != shownE2Estimate) {
                        hideE2Estimate()
                        inlineE2Estimate = state.value
                    }
                }
                .map { (_, state) -> state }
        }
        .holdWhileUpdating(screenVisible) { state -> state.pending == E2Pending.USER }
        .onEach { held -> shownE2Estimate = held.value.value }

    private fun hideE2Estimate() {
        shownE2Estimate = null
        _uiState.update { state -> state.copy(hasE2Estimate = false, pkCalibration = null) }
    }

    private suspend fun buildHomeState(sources: HomeStateSources): MainUiState? {
        val (held, timeSnapshot, timeZoneNotice, storedLowStockSectionExpanded, acknowledgedWarningStates) = sources
        if (!held.keyed.key.matches(timeSnapshot)) {
            return null
        }
        val inputs = held.keyed.inputs
        if (
            inputs.source == HomeInputSource.ROOM &&
            inputs.stockWarnings.isEmpty() &&
            acknowledgedWarningStates.isNotEmpty()
        ) {
            // Swallow DataStore failures: letting them propagate would tear
            // down this combine -> stateIn home flow. A dropped clear is
            // self-correcting on the next empty-ROOM emission.
            try {
                settingsRepository.clearHomeLowStockAcknowledgedWarningStates()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Best-effort cleanup; see comment above.
            }
        }
        val estimate = held.estimate.value.value
        fun build() = buildHomeUiState(
            inputs = inputs,
            estimate = estimate,
            now = timeSnapshot.minute,
            zoneId = timeSnapshot.zone,
            timeZoneNotice = timeZoneNotice,
            lowStockSectionExpanded = shouldExpandLowStockSection(
                storedExpanded = storedLowStockSectionExpanded,
                stockWarnings = inputs.stockWarnings,
                acknowledgedWarningStates = acknowledgedWarningStates,
            ),
            homeE2DisplayUnit = held.displayUnit,
            isUpdating = held.estimate.isUpdating,
        )
        if (estimate != null && estimate === inlineE2Estimate) {
            inlineE2Estimate = null
            return build()
        }
        return withContext(defaultDispatcher) { build() }
    }

    private val _highlightRequest = MutableStateFlow<DoseRowHighlightRequest?>(null)
    val highlightRequest: StateFlow<DoseRowHighlightRequest?> = _highlightRequest.asStateFlow()

    private val _homeDeepLinkSignal = MutableStateFlow(0)
    val homeDeepLinkSignal: StateFlow<Int> = _homeDeepLinkSignal.asStateFlow()

    private val _milestonesDeepLinkSignal = MutableStateFlow(0)
    val milestonesDeepLinkSignal: StateFlow<Int> = _milestonesDeepLinkSignal.asStateFlow()

    // Dedup marker for the milestones deep link. Deliberately a ViewModel field
    // rather than NavHost rememberSaveable state so it shares the signal's exact
    // lifetime: both reset to 0 on process death, both survive a config-change
    // recreation together. A marker saved to instance state would outlive the
    // signal — a process-death re-tap would restore lastHandled ahead of the
    // fresh 0-based signal and silently drop the deep link, and a font-scale (or
    // any non-configChanges) recreation would replay the retained intent.
    private var lastHandledMilestonesSignal = 0

    // True once an onCreate has parsed its launch intent. ViewModel-lifetime on purpose
    // (same rationale as lastHandledMilestonesSignal): survives a config recreation, so
    // the retained intent's widget extras are not replayed and cannot yank the user back
    // to a deep link they navigated away from — but resets on process death, so a widget
    // or shortcut tap that recreates a killed task (which lands in onCreate with a
    // non-null savedInstanceState, because a dead instance can never receive onNewIntent)
    // still parses the fresh intent and navigates.
    var launchIntentParsed = false

    fun setScreenVisible(visible: Boolean) {
        screenVisible.value = visible
    }

    fun requestMilestonesDeepLink() {
        _milestonesDeepLinkSignal.update { it + 1 }
    }

    // Fires the milestones navigation once per request: returns true for the
    // first observer of each new signal value and marks it handled; later calls
    // for the same (or an already-superseded) signal return false.
    fun consumeMilestonesDeepLink(): Boolean {
        val signal = _milestonesDeepLinkSignal.value
        if (signal <= lastHandledMilestonesSignal) {
            return false
        }
        lastHandledMilestonesSignal = signal
        return true
    }

    fun requestDoseRowHighlight(keys: List<DoseRowHighlightKey>) {
        if (keys.isEmpty()) {
            return
        }
        _highlightRequest.value = DoseRowHighlightRequest(keys)
        _homeDeepLinkSignal.update { it + 1 }
    }

    fun consumeHighlightRequest(request: DoseRowHighlightRequest) {
        // Clear only if this is still the active request. A cancellation-time
        // consume (tab switch) must not clobber a newer request that arrived
        // from a fresh deep link.
        _highlightRequest.compareAndSet(request, null)
    }

    // Once-per-process view concern owned here (this ViewModel is activity-
    // scoped in a single-activity app) rather than as a file-level static: a
    // static is invisible to tests, replays only on process death anyway, and
    // is racy across simultaneous Home compositions — the loser would read the
    // pre-claim value but never consume it, sticking on the intro render path
    // forever.
    private var homeE2ChartIntroAnimationClaimed = false

    /**
     * Claims the once-per-ViewModel-lifetime E2 chart intro animation.
     * Returns true for exactly one caller; that composition plays the intro,
     * every other (and later) composition renders the synchronous-model path
     * directly.
     */
    fun claimHomeE2ChartIntroAnimation(): Boolean {
        if (homeE2ChartIntroAnimationClaimed) {
            return false
        }
        homeE2ChartIntroAnimationClaimed = true
        return true
    }

    fun dismissTimeZoneChangeNotice() {
        timeZoneChangeNoticeController.dismiss()
    }

    fun setHomeE2DisplayUnit(unit: BloodUnitKey) {
        viewModelScope.launch {
            settingsRepository.setHomeE2DisplayUnit(AllowedAnalyteUnit.of(BloodAnalyteKey.E2, unit))
        }
    }

    fun setHomeE2ChartWindowOption(option: HomeE2ChartWindowOption) {
        viewModelScope.launch {
            homeRepository.setHomeE2ChartWindowOption(option)
        }
    }

    fun setHomeCardLayout(order: List<HomeCardType>, hidden: Set<HomeCardType>) {
        viewModelScope.launch {
            settingsRepository.setHomeCardLayout(order, hidden)
        }
    }

    fun setLowStockSectionExpanded(expanded: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHomeLowStockSectionFoldState(
                expanded = expanded,
                acknowledgedWarningStates = if (expanded) {
                    emptyMap()
                } else {
                    uiState.value.stockWarnings.toLowStockWarningStateMap()
                },
            )
        }
    }

    private fun buildHomeUiState(
        inputs: HomeInputs,
        /** Null for the skeleton. */
        estimate: HomeE2Estimate?,
        now: LocalDateTime,
        zoneId: ZoneId,
        timeZoneNotice: TimeZoneChangeNotice?,
        lowStockSectionExpanded: Boolean,
        homeE2DisplayUnit: BloodUnitKey,
        isUpdating: Boolean,
    ): MainUiState {
        // The E2 cards show the window their estimate covers; the skeleton
        // shows the selected one.
        val chartWindowOption = estimate?.option ?: inputs.settings.homeE2ChartWindowOption
        val homeEntries = (inputs.scheduleEntries + inputs.antiandrogenHistoryEntries)
            .distinctBy { entry -> entry.uuid }
            .sortedByDescending { entry -> entry.appliedAt }
        val allGroups = inputs.activeGroups + inputs.archivedGroups
        val visibleEntries = visibleMedicationEntries(
            homeEntries,
            allGroups,
            inputs.settings.showArchivedGroupRecords,
        )
        val scheduleGroups =
            if (inputs.settings.showArchivedGroupRecords) allGroups else inputs.activeGroups
        // Sliced at the live time. The projection runs past the chart by its
        // forecast buffer, so a held estimate still covers it across midnight.
        val trendResult = estimate?.projection?.toMainEstradiolTrend(
            now = now,
            zoneId = zoneId,
            option = chartWindowOption,
        )

        val boundary = inputs.settings.medicationDayBoundary
        val doseSections = buildMainDoseSections(
            groups = scheduleGroups,
            entries = visibleEntries,
            now = now,
            zoneId = zoneId,
            includeUnloggedArchivedSlots = false,
            unloggedArchivedSlotCutoff = now,
            boundary = boundary,
        )

        return MainUiState(
            homeDataReady = true,
            isUpdating = isUpdating,
            hasE2Estimate = trendResult != null,
            homeSource = inputs.source,
            now = now,
            homeE2DisplayUnit = homeE2DisplayUnit,
            homeE2ChartWindowOption = chartWindowOption,
            // Hero, notes and band come with the curve, from one estimate.
            pkCalibration = estimate?.let {
                buildMainPkCalibration(
                    estimate = it,
                    now = now,
                    zoneId = zoneId,
                    displayUnit = homeE2DisplayUnit,
                )
            },
            hideReferenceRanges = inputs.settings.hideReferenceRanges,
            stockWarnings = inputs.stockWarnings,
            lowStockSectionExpanded = lowStockSectionExpanded,
            homeAnchor = inputs.homeAnchor?.toAnchorRowUiState(now.toLocalDate()),
            e2Hero = buildMainE2Hero(
                // The single-row `latestEstradiolEntry` query is bounded at the
                // frozen subscription `now`, so on its own it misses a dose the
                // user logs later in the same session. Feed the schedule window
                // (which carries today's logged doses) alongside it, mirroring
                // the antiandrogen card; buildMainE2Hero filters to estradiol
                // entries at or before the live `now` and picks the latest.
                entries = homeEntries + listOfNotNull(inputs.latestEstradiolEntry),
                trendResult = trendResult,
                displayUnit = homeE2DisplayUnit,
                zoneId = zoneId,
                now = now,
            ),
            e2Chart = buildMainE2Chart(
                trendResult = trendResult,
                displayUnit = homeE2DisplayUnit,
                chartWindowOption = chartWindowOption,
            ),
            antiandrogenGroupSections = listOf(
                MedicationCategory.ANTIANDROGEN,
                MedicationCategory.SERM,
                MedicationCategory.GNRH_AGONIST,
            ).mapNotNull { category ->
                val cards = buildMainAntiandrogenCards(
                    groups = inputs.activeGroups,
                    entries = homeEntries,
                    now = now,
                    zoneId = zoneId,
                    category = category,
                    boundary = inputs.settings.medicationDayBoundary,
                )
                cards.takeIf { it.isNotEmpty() }
                    ?.let { MainMedicationCategorySection(category = category, cards = it) }
            },
            todaySection = doseSections.today,
            lastNightSection = doseSections.lastNight,
            comingUpSection = doseSections.comingUp,
            upcomingSection = buildMainUpcomingSection(
                boundary = boundary,
                groups = inputs.activeGroups,
                entries = homeEntries,
                now = now,
                zoneId = zoneId,
            ),
            timeZoneChangeNotice = timeZoneNotice,
        )
    }

    private fun shouldExpandLowStockSection(
        storedExpanded: Boolean,
        stockWarnings: List<MedicineStockProjection>,
        acknowledgedWarningStates: Map<String, MedicineStockState>,
    ): Boolean {
        if (storedExpanded) {
            return true
        }
        val currentWarningStates = stockWarnings.toLowStockWarningStateMap()
        return currentWarningStates.any { (uuid, currentState) ->
            val acknowledgedState = acknowledgedWarningStates[uuid]
            acknowledgedState == null ||
                    acknowledgedState.lowStockSeverityRank() < currentState.lowStockSeverityRank()
        }
    }

    private fun List<MedicineStockProjection>.toLowStockWarningStateMap(): Map<String, MedicineStockState> {
        return mapNotNull { projection ->
            projection.state
                .takeIf { state -> state.lowStockSeverityRank() > 0 }
                ?.let { state -> projection.medicine.uuid.toString() to state }
        }.toMap()
    }

    private data class HomeTimeKey(
        val date: LocalDate,
        val zoneId: ZoneId,
    )

    private data class KeyedHomeInputs(val key: HomeTimeKey, val inputs: HomeInputs)

    private data class HeldHomeInputs(
        val keyed: KeyedHomeInputs,
        val estimate: Held<E2State<HomeE2Estimate>>,
        val displayUnit: BloodUnitKey,
    )

    private data class HomeStateSources(
        val held: HeldHomeInputs,
        val timeSnapshot: AppTimeSnapshot,
        val timeZoneNotice: TimeZoneChangeNotice?,
        val storedLowStockSectionExpanded: Boolean,
        val acknowledgedWarningStates: Map<String, MedicineStockState>,
    )

    private fun buildMainPkCalibration(
        estimate: HomeE2Estimate,
        now: LocalDateTime,
        zoneId: ZoneId,
        displayUnit: BloodUnitKey,
    ): MainPkCalibrationUiState? {
        val record = estimate.calibration ?: return null
        return MainPkCalibrationUiState(
            adjusted = record.adjusted,
            limitedConfidence = record.limitedConfidence,
            renderUnavailable = record.renderUnavailable,
            bandUnavailable = record.bandUnavailable,
            band = buildMainE2CalibrationBand(
                bandKnots = estimate.bandKnots.map { knot -> knot.toBandKnot() },
                now = now,
                zoneId = zoneId,
                pastDays = estimate.option.pastDays,
                windowHours = estimate.option.chartWindowHours,
                displayUnit = displayUnit,
            ),
            effectivePromotedRoutes = record.effectiveRoutes.mapNotNull(PkCalibrationRoute::fromStableId),
        )
    }

    private fun HomeTimeKey.matches(snapshot: AppTimeSnapshot): Boolean {
        return date == snapshot.minute.toLocalDate() &&
                zoneId == snapshot.zone
    }
}

/** Null while the estimate loads; a failed fit or render shows the numeric-failure sheet. */
internal fun pkCalibrationSheetUiState(estimate: CalibrationEstimate?): PkCalibrationUiState? =
    estimate?.let { pkCalibrationScreenState(it).ui }
