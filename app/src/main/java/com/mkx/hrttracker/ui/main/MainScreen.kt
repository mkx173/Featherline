package com.mkx.hrttracker.ui.main

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.HomeInputSource
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.startup.StartupTiming
import com.mkx.hrttracker.ui.calibration.calibrationAllowedUnitsFor
import com.mkx.hrttracker.ui.components.AppContentContainer
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.components.topAppBarScrollToTop
import com.mkx.hrttracker.util.calibrationUnitLabel
import kotlinx.coroutines.delay
import java.util.UUID

private const val DoseRowHighlightClearDelayMillis = 2_000L
private const val DoseRowHighlightScrollSettleFrameCount = 2

internal suspend fun runDoseRowHighlightLifecycle(
    setFlashReady: (Boolean) -> Unit,
    awaitFirstLayoutFrame: suspend () -> Unit,
    awaitScrollSettled: suspend () -> Unit,
    clearDelayMillis: Long,
    consumeHighlightRequest: () -> Unit,
) {
    try {
        setFlashReady(false)
        awaitFirstLayoutFrame()
        awaitScrollSettled()
        setFlashReady(true)
        delay(clearDelayMillis)
    } finally {
        // Consume on normal completion AND on cancellation. Leaving the Home
        // tab disposes MainScreen, cancelling this coroutine before the clear
        // delay elapses; without this the request would survive in the
        // (activity-scoped) ViewModel and replay the highlight on return.
        consumeHighlightRequest()
    }
}

// Assumes the target row's bringIntoView() registers isScrollInProgress (or
// moves scrollValue) within the first awaitFrame, i.e. the scroll begins the
// same frame the settle poll starts. If the poll observed stillness for
// stableFrameCount frames before the scroll animation registered, the flash
// would fire before the scroll lands. Held off in practice by the caller's
// first-layout-frame wait, the >1 stableFrameCount, and a real off-screen
// scroll spanning many frames.
internal suspend fun awaitDoseRowHighlightScrollSettled(
    isScrollInProgress: () -> Boolean,
    scrollValue: () -> Int,
    awaitFrame: suspend () -> Unit,
    stableFrameCount: Int = DoseRowHighlightScrollSettleFrameCount,
) {
    require(stableFrameCount > 0) { "stableFrameCount must be greater than zero." }

    var lastValue = scrollValue()
    var stableFrames = 0
    while (stableFrames < stableFrameCount) {
        awaitFrame()
        val currentValue = scrollValue()
        stableFrames = if (!isScrollInProgress() && currentValue == lastValue) {
            stableFrames + 1
        } else {
            0
        }
        lastValue = currentValue
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    scrollToTopSignal: Int = 0,
    highlightEffectsEnabled: Boolean = true,
    onQuickLogDoseClick: (MainQuickLogDoseRequest) -> Unit = { },
    onEntryClick: (MainEditEntryRequest) -> Unit = { },
    onMedicineDetailClick: (UUID) -> Unit = { },
    onAddEntryClick: () -> Unit = { },
    viewModel: MainViewModel = hiltViewModel(
        viewModelStoreOwner = LocalActivity.current as ComponentActivity
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val highlightRequest by viewModel.highlightRequest.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    var highlightFlashReady by remember { mutableStateOf(false) }

    LaunchedEffect(highlightRequest, highlightEffectsEnabled) {
        val activeRequest = highlightRequest
        if (activeRequest == null || !highlightEffectsEnabled) {
            highlightFlashReady = false
            return@LaunchedEffect
        }

        runDoseRowHighlightLifecycle(
            setFlashReady = { ready -> highlightFlashReady = ready },
            awaitFirstLayoutFrame = { withFrameNanos { } },
            awaitScrollSettled = {
                awaitDoseRowHighlightScrollSettled(
                    isScrollInProgress = { scrollState.isScrollInProgress },
                    scrollValue = { scrollState.value },
                    awaitFrame = { withFrameNanos { } },
                )
            },
            clearDelayMillis = DoseRowHighlightClearDelayMillis,
            consumeHighlightRequest = { viewModel.consumeHighlightRequest(activeRequest) },
        )
    }

    ReportDrawnWhen {
        uiState.splashReady
    }
    LaunchedEffect(uiState.homeSource) {
        when (uiState.homeSource) {
            HomeInputSource.SNAPSHOT -> {
                withFrameNanos { }
                StartupTiming.mark("home_snapshot_rendered")
            }
            HomeInputSource.ROOM -> {
                withFrameNanos { }
                StartupTiming.mark("home_room_takeover_rendered")
            }
            null -> Unit
        }
    }
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        state = topAppBarState
    )
    val initialScrollToTopSignal = remember { scrollToTopSignal }

    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal != initialScrollToTopSignal) {
            scrollState.animateScrollTo(0)
            topAppBarState.contentOffset = 0f
            topAppBarState.heightOffset = 0f
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier.topAppBarScrollToTop(scrollBehavior) {
                    scrollState.animateScrollTo(0)
                },
                title = {
                    val title = stringResource(R.string.tab_main)
                    Text(
                        text = title,
                        modifier = Modifier.cjkTextOffset(title),
                    )
                },
                actions = {
                    IconButton(onClick = onAddEntryClick) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.fab_add_entry),
                        )
                    }
                    HomeMoreOptionsMenu(
                        selectedUnit = uiState.homeE2DisplayUnit,
                        onUnitSelected = viewModel::setHomeE2DisplayUnit,
                    )
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        AppContentContainer(modifier = Modifier.padding(innerPadding)) {
            MainContent(
                uiState = uiState,
                scrollState = scrollState,
                highlightRequest = highlightRequest,
                highlightEffectsEnabled = highlightEffectsEnabled,
                highlightFlashReady = highlightFlashReady,
                onQuickLogDoseClick = onQuickLogDoseClick,
                onEntryClick = onEntryClick,
                onMedicineDetailClick = onMedicineDetailClick,
                onDismissTimeZoneChangeNotice = viewModel::dismissTimeZoneChangeNotice,
                onE2ChartWindowOptionSelected = viewModel::setHomeE2ChartWindowOption,
                onLowStockSectionExpandedChange = viewModel::setLowStockSectionExpanded,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

internal enum class HomeMoreOptionsMenuState {
    CLOSED,
    OPTIONS,
    DISPLAY_UNIT,
}

internal enum class HomeMoreOptionsMenuAction {
    OPEN_OPTIONS,
    OPEN_DISPLAY_UNIT,
    DISMISS,
}

internal data class HomeMoreOptionsMenuExpansion(
    val optionsExpanded: Boolean,
    val displayUnitExpanded: Boolean,
)

internal fun reduceHomeMoreOptionsMenuState(
    state: HomeMoreOptionsMenuState,
    action: HomeMoreOptionsMenuAction,
): HomeMoreOptionsMenuState {
    return when (action) {
        HomeMoreOptionsMenuAction.OPEN_OPTIONS -> HomeMoreOptionsMenuState.OPTIONS
        HomeMoreOptionsMenuAction.OPEN_DISPLAY_UNIT -> when (state) {
            HomeMoreOptionsMenuState.OPTIONS,
            HomeMoreOptionsMenuState.DISPLAY_UNIT -> HomeMoreOptionsMenuState.DISPLAY_UNIT
            HomeMoreOptionsMenuState.CLOSED -> HomeMoreOptionsMenuState.CLOSED
        }
        HomeMoreOptionsMenuAction.DISMISS -> HomeMoreOptionsMenuState.CLOSED
    }
}

internal fun HomeMoreOptionsMenuState.toHomeMoreOptionsMenuExpansion(): HomeMoreOptionsMenuExpansion {
    return HomeMoreOptionsMenuExpansion(
        optionsExpanded = this == HomeMoreOptionsMenuState.OPTIONS,
        displayUnitExpanded = this == HomeMoreOptionsMenuState.DISPLAY_UNIT,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeMoreOptionsMenu(
    selectedUnit: BloodUnitKey,
    onUnitSelected: (BloodUnitKey) -> Unit,
) {
    var menuState by rememberSaveable { mutableStateOf(HomeMoreOptionsMenuState.CLOSED) }
    val menuExpansion = menuState.toHomeMoreOptionsMenuExpansion()
    // Apply the picked unit only after the submenu has finished dismissing. Switching it
    // immediately re-renders the chart underneath the menu on the same frame, which
    // janks and swallows the dropdown's short dismiss animation. Deferring to onDispose
    // lets the menu animate out first, then applies the change. (Mirrors the settings
    // dropdowns' onExitFinished deferral.)
    var pendingUnit by remember { mutableStateOf<BloodUnitKey?>(null) }

    fun updateMenuState(action: HomeMoreOptionsMenuAction) {
        menuState = reduceHomeMoreOptionsMenuState(menuState, action)
    }

    fun dismissMenus() {
        updateMenuState(HomeMoreOptionsMenuAction.DISMISS)
    }

    Box {
        IconButton(onClick = { updateMenuState(HomeMoreOptionsMenuAction.OPEN_OPTIONS) }) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.main_more_options),
            )
        }

        DropdownMenuPopup(
            expanded = menuExpansion.optionsExpanded,
            onDismissRequest = ::dismissMenus,
        ) {
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(0, 1),
            ) {
                val selectUnitText = stringResource(R.string.main_display_unit)

                DropdownMenuItem(
                    onClick = { updateMenuState(HomeMoreOptionsMenuAction.OPEN_DISPLAY_UNIT) },
                    text = { Text(text = selectUnitText, modifier = Modifier.cjkTextOffset(selectUnitText)) },
                    shape = MaterialTheme.shapes.medium,
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(MenuDefaults.TrailingIconSize),
                        )
                    },
                )
            }
        }

        DropdownMenuPopup(
            expanded = menuExpansion.displayUnitExpanded,
            onDismissRequest = ::dismissMenus,
        ) {
            DisposableEffect(Unit) {
                onDispose {
                    pendingUnit?.let { unit ->
                        onUnitSelected(unit)
                        pendingUnit = null
                    }
                }
            }
            HomeDisplayUnitSubmenu(
                selectedUnit = selectedUnit,
                onUnitSelected = { unit ->
                    pendingUnit = unit
                    dismissMenus()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeDisplayUnitSubmenu(
    selectedUnit: BloodUnitKey,
    onUnitSelected: (BloodUnitKey) -> Unit,
) {
    val units = calibrationAllowedUnitsFor(BloodAnalyteKey.E2)

    DropdownMenuGroup(
        shapes = MenuDefaults.groupShape(0, 1),
    ) {
        units.forEachIndexed { index, unit ->
            DropdownMenuItem(
                selected = selectedUnit == unit,
                onClick = { onUnitSelected(unit) },
                text = { Text(text = calibrationUnitLabel(unit)) },
                shapes = MenuDefaults.itemShape(index, units.size),
                trailingIcon = {
                    if (selectedUnit == unit) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(MenuDefaults.TrailingIconSize),
                        )
                    }
                },
            )
        }
    }
}
