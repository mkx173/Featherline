package com.mkx.hrttracker.ui.navigation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.withResumed
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.mkx.hrttracker.BuildConfig
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.DoseInstruction
import com.mkx.hrttracker.model.medication.DoseInstructionKind
import com.mkx.hrttracker.model.medication.MedicationApplicationType
import com.mkx.hrttracker.model.medication.MedicationCategory
import com.mkx.hrttracker.model.medication.MedicationGroupColorKey
import com.mkx.hrttracker.model.medication.MedicationKey
import com.mkx.hrttracker.model.medication.MedicationLogEntry
import com.mkx.hrttracker.model.medication.MedicationSelectionKind
import com.mkx.hrttracker.model.medication.Medicine
import com.mkx.hrttracker.model.medication.MedicinePreparation
import com.mkx.hrttracker.model.medication.MedicinePreparationType
import com.mkx.hrttracker.model.medication.MedicineSelection
import com.mkx.hrttracker.model.medication.MedicineStock
import com.mkx.hrttracker.reminder.PostLogStockWarning
import com.mkx.hrttracker.ui.calibration.CalibrationEditorScreen
import com.mkx.hrttracker.ui.calibration.CalibrationEditorViewModel
import com.mkx.hrttracker.ui.calibration.CalibrationScreen
import com.mkx.hrttracker.ui.calibration.CalibrationUnitsScreen
import com.mkx.hrttracker.ui.catalog.AdjustSheetTab
import com.mkx.hrttracker.ui.catalog.MedicineDetailScreen
import com.mkx.hrttracker.ui.catalog.MedicineDetailViewModel
import com.mkx.hrttracker.ui.catalog.MedicineManagerLaunchMode
import com.mkx.hrttracker.ui.catalog.MedicinesScreen
import com.mkx.hrttracker.ui.catalog.medicineManagerLaunchMode
import com.mkx.hrttracker.ui.catalog.nudge.StockTrackingNudgeViewModel
import com.mkx.hrttracker.ui.catalog.stock.AdjustStockSheet
import com.mkx.hrttracker.ui.components.HrtSnackbar
import com.mkx.hrttracker.ui.components.LocalAppContentBottomInset
import com.mkx.hrttracker.ui.components.LocalChromeHazeState
import com.mkx.hrttracker.ui.components.LocalModalHostIsCurrentDestination
import com.mkx.hrttracker.ui.components.LocalNavigationLock
import com.mkx.hrttracker.ui.components.StockNudgeVisuals
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.components.rememberChromeHazeState
import com.mkx.hrttracker.ui.components.stockInventoryCountText
import com.mkx.hrttracker.ui.history.HistoryScreen
import com.mkx.hrttracker.ui.journal.AllNotesScreen
import com.mkx.hrttracker.ui.journal.JournalScreen
import com.mkx.hrttracker.ui.journal.MilestonesScreen
import com.mkx.hrttracker.ui.log.MedicationLogEntryEditSnapshot
import com.mkx.hrttracker.ui.log.MedicationLogEntryQuickLogRequest
import com.mkx.hrttracker.ui.log.MedicationLogEntryScreen
import com.mkx.hrttracker.ui.main.MainEditEntryRequest
import com.mkx.hrttracker.ui.main.MainScreen
import com.mkx.hrttracker.ui.plan.ArchivedMedicationGroupsScreen
import com.mkx.hrttracker.ui.plan.MedicationGroupEditorScreen
import com.mkx.hrttracker.ui.plan.MedicationGroupEditorViewModel
import com.mkx.hrttracker.ui.plan.PlanBatchAddScreen
import com.mkx.hrttracker.ui.plan.PlanScreen
import com.mkx.hrttracker.ui.pkcalibrationdebug.PkCalibrationDebugScreenEntry
import com.mkx.hrttracker.ui.postLogStockWarningDestination
import com.mkx.hrttracker.ui.postLogStockWarningSnackbarMessage
import com.mkx.hrttracker.ui.settings.SettingsScreen
import com.mkx.hrttracker.util.medicineDisplayName
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

sealed class Screen(val route: String, @get:StringRes val label: Int) {
    data object Main : Screen("main", R.string.tab_main)
    data object Plan : Screen("plan", R.string.tab_plan)
    data object Journal : Screen("journal", R.string.tab_journal)
    data object JournalMilestones : Screen(
        "journal_milestones?$TOP_LEVEL_PARENT_ARG={$TOP_LEVEL_PARENT_ARG}&$OPEN_ADD_DATE_ARG={$OPEN_ADD_DATE_ARG}",
        R.string.journal_since_you_started
    ) {
        const val baseRoute = "journal_milestones"

        fun createRoute(
            topLevelParentRoute: String = Journal.route,
            openAddDate: Boolean = false,
        ): String =
            "$baseRoute?$TOP_LEVEL_PARENT_ARG=$topLevelParentRoute&$OPEN_ADD_DATE_ARG=$openAddDate"
    }

    data object JournalAllNotes : Screen(
        "journal_all_notes?$TOP_LEVEL_PARENT_ARG={$TOP_LEVEL_PARENT_ARG}",
        R.string.journal_all_notes
    ) {
        const val baseRoute = "journal_all_notes"

        fun createRoute(topLevelParentRoute: String = Journal.route): String =
            "$baseRoute?$TOP_LEVEL_PARENT_ARG=$topLevelParentRoute"
    }

    data object PlanBatchAdd : Screen(
        "plan_batch_add?$TOP_LEVEL_PARENT_ARG={$TOP_LEVEL_PARENT_ARG}",
        R.string.plan_batch_add_title
    ) {
        const val baseRoute = "plan_batch_add"

        fun createRoute(topLevelParentRoute: String = Plan.route): String {
            return "$baseRoute?$TOP_LEVEL_PARENT_ARG=$topLevelParentRoute"
        }
    }

    data object PlanArchivedGroups : Screen(
        "plan_archived_groups?$TOP_LEVEL_PARENT_ARG={$TOP_LEVEL_PARENT_ARG}",
        R.string.plan_archived_groups
    ) {
        const val baseRoute = "plan_archived_groups"

        fun createRoute(topLevelParentRoute: String = Plan.route): String {
            return "$baseRoute?$TOP_LEVEL_PARENT_ARG=$topLevelParentRoute"
        }
    }

    data object History : Screen(
        "history?$TOP_LEVEL_PARENT_ARG={$TOP_LEVEL_PARENT_ARG}",
        R.string.tab_history
    ) {
        const val baseRoute = "history"

        fun createRoute(topLevelParentRoute: String = Plan.route): String {
            return "$baseRoute?$TOP_LEVEL_PARENT_ARG=$topLevelParentRoute"
        }
    }

    data object Settings : Screen("settings", R.string.tab_settings)
    data object SettingsPkCalibrationDebug : Screen(
        "settings_pk_calibration_debug?$TOP_LEVEL_PARENT_ARG={$TOP_LEVEL_PARENT_ARG}",
        R.string.settings_diagnostics,
    ) {
        const val baseRoute = "settings_pk_calibration_debug"

        fun createRoute(topLevelParentRoute: String): String {
            return "$baseRoute?$TOP_LEVEL_PARENT_ARG=$topLevelParentRoute"
        }
    }

    data object SettingsCalibration : Screen(
        "settings_calibration?$TOP_LEVEL_PARENT_ARG={$TOP_LEVEL_PARENT_ARG}",
        R.string.settings_personalization_calibration
    ) {
        const val baseRoute = "settings_calibration"

        fun createRoute(
            topLevelParentRoute: String,
        ): String {
            return "$baseRoute?$TOP_LEVEL_PARENT_ARG=$topLevelParentRoute"
        }
    }

    data object SettingsCalibrationUnits : Screen(
        "settings_calibration_units?$TOP_LEVEL_PARENT_ARG={$TOP_LEVEL_PARENT_ARG}",
        R.string.settings_calibration_settings
    ) {
        const val baseRoute = "settings_calibration_units"

        fun createRoute(
            topLevelParentRoute: String,
        ): String {
            return "$baseRoute?$TOP_LEVEL_PARENT_ARG=$topLevelParentRoute"
        }
    }

    data object SettingsCalibrationEntry : Screen(
        "settings_calibration_entry?" +
                "${CalibrationEditorViewModel.PANEL_ID_ARG}={${CalibrationEditorViewModel.PANEL_ID_ARG}}" +
                "&$TOP_LEVEL_PARENT_ARG={$TOP_LEVEL_PARENT_ARG}",
        R.string.settings_calibration_add_result
    ) {
        const val baseRoute = "settings_calibration_entry"

        fun createRoute(
            topLevelParentRoute: String,
            panelId: String? = null,
        ): String {
            return if (panelId == null) {
                "$baseRoute?$TOP_LEVEL_PARENT_ARG=$topLevelParentRoute"
            } else {
                "$baseRoute?${CalibrationEditorViewModel.PANEL_ID_ARG}=$panelId&$TOP_LEVEL_PARENT_ARG=$topLevelParentRoute"
            }
        }
    }

    data object Medicines : Screen(
        "medicines/{$TOP_LEVEL_PARENT_ARG}?" +
                "$SLOT_RESULT_KEY_ARG={$SLOT_RESULT_KEY_ARG}",
        R.string.medicines_title,
    ) {
        const val baseRoute = "medicines"

        // Route template stripped of its query string — matches what
        // NavigationTransitions.normalizeNavigationRoute() yields.
        const val motionRoute = "$baseRoute/{$TOP_LEVEL_PARENT_ARG}"

        // `slotResultKey` carries a complete slot Bundle
        // (medicine + dose + count + applicationType) back to the caller via
        // the previous back-stack entry's savedStateHandle.
        fun createRoute(
            topLevelParentRoute: String = Plan.route,
            slotResultKey: String? = null,
        ): String {
            return buildString {
                append(baseRoute)
                append("/")
                append(topLevelParentRoute)
                if (slotResultKey != null) {
                    append("?$SLOT_RESULT_KEY_ARG=$slotResultKey")
                }
            }
        }
    }

    data object MedicineDetail : Screen(
        "medicine_detail/{${MedicineDetailViewModel.MEDICINE_ID_ARG}}?" +
                "$TOP_LEVEL_PARENT_ARG={$TOP_LEVEL_PARENT_ARG}" +
                "&${MedicineDetailViewModel.OPEN_OPT_IN_ARG}={${MedicineDetailViewModel.OPEN_OPT_IN_ARG}}",
        R.string.medicine_detail_title,
    ) {
        const val baseRoute = "medicine_detail"

        // Route template stripped of its query string — matches what
        // NavigationTransitions.normalizeNavigationRoute() yields.
        const val motionRoute = "$baseRoute/{${MedicineDetailViewModel.MEDICINE_ID_ARG}}"

        fun createRoute(
            medicineId: String,
            topLevelParentRoute: String = Plan.route,
            openOptIn: Boolean = false,
        ): String {
            return buildString {
                append(baseRoute)
                append("/")
                append(medicineId)
                append("?$TOP_LEVEL_PARENT_ARG=$topLevelParentRoute")
                if (openOptIn) {
                    append("&${MedicineDetailViewModel.OPEN_OPT_IN_ARG}=true")
                }
            }
        }
    }

    data object EditMedicationGroup : Screen(
        "edit_medication_group?" +
                "${MedicationGroupEditorViewModel.GROUP_ID_ARG}={${MedicationGroupEditorViewModel.GROUP_ID_ARG}}" +
                "&$TOP_LEVEL_PARENT_ARG={$TOP_LEVEL_PARENT_ARG}" +
                "&$MEDICATION_GROUP_EDITOR_SOURCE_ARG={$MEDICATION_GROUP_EDITOR_SOURCE_ARG}",
        R.string.add_medication_group
    ) {
        const val baseRoute = "edit_medication_group"

        fun createRoute(
            topLevelParentRoute: String,
            groupId: String? = null,
            source: String? = null,
        ): String {
            val queryParameters = buildList {
                groupId?.let { add("${MedicationGroupEditorViewModel.GROUP_ID_ARG}=$it") }
                add("$TOP_LEVEL_PARENT_ARG=$topLevelParentRoute")
                source?.let { add("$MEDICATION_GROUP_EDITOR_SOURCE_ARG=$it") }
            }
            return "$baseRoute?${queryParameters.joinToString("&")}"
        }
    }

    companion object {
        fun topLevelScreenForRoute(route: String?): Screen? {
            return when (route) {
                Main.route -> Main
                Plan.route -> Plan
                Journal.route -> Journal
                Settings.route -> Settings
                else -> null
            }
        }
    }
}

internal data class NavigationItemContent(
    val screen: Screen,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val iconAlt: Int,
)

internal val topLevelNavigationItems = listOf(
    NavigationItemContent(Screen.Main, R.drawable.ic_home, R.drawable.ic_home_alt),
    NavigationItemContent(Screen.Plan, R.drawable.ic_calendar_month, R.drawable.ic_calendar_month_alt),
    NavigationItemContent(Screen.Journal, R.drawable.ic_book_ribbon, R.drawable.ic_book_ribbon_alt),
    NavigationItemContent(Screen.Settings, R.drawable.ic_settings, R.drawable.ic_settings_alt),
)

internal enum class TopLevelNavigationTapAction {
    NAVIGATE,
    POP_TO_TOP_LEVEL,
    SCROLL_TO_TOP,
    NONE,
}

internal enum class TopLevelRootBackAction {
    NAVIGATE_HOME,
    NONE,
}

internal fun topLevelNavigationTapAction(
    tappedScreen: Screen,
    selectedBottomScreen: Screen?,
    currentRoute: String?,
): TopLevelNavigationTapAction {
    val isOnChildOfSelectedTopLevel =
        selectedBottomScreen == tappedScreen &&
                currentRoute != null &&
                currentRoute != tappedScreen.route

    return when {
        isOnChildOfSelectedTopLevel -> TopLevelNavigationTapAction.POP_TO_TOP_LEVEL
        currentRoute == tappedScreen.route -> TopLevelNavigationTapAction.SCROLL_TO_TOP
        currentRoute != tappedScreen.route -> TopLevelNavigationTapAction.NAVIGATE
        else -> TopLevelNavigationTapAction.NONE
    }
}

internal fun topLevelNavigationReplacementPopUpToRoute(
    targetScreen: Screen,
    selectedBottomScreen: Screen?,
): String? {
    return selectedBottomScreen
        ?.takeIf { screen -> screen != targetScreen }
        ?.route
}

internal fun topLevelRootBackAction(
    selectedBottomScreen: Screen?,
    currentRoute: String?,
): TopLevelRootBackAction {
    return if (
        selectedBottomScreen != null &&
        selectedBottomScreen != Screen.Main &&
        currentRoute == selectedBottomScreen.route
    ) {
        TopLevelRootBackAction.NAVIGATE_HOME
    } else {
        TopLevelRootBackAction.NONE
    }
}

internal enum class HomeDeepLinkNavigationAction {
    AWAIT_ROUTE,
    NAVIGATE_HOME,
    NONE,
}

internal fun homeDeepLinkNavigationAction(
    homeDeepLinkSignal: Int,
    lastHandledHomeDeepLinkSignal: Int,
    currentRoute: String?,
): HomeDeepLinkNavigationAction {
    if (homeDeepLinkSignal <= lastHandledHomeDeepLinkSignal) {
        return HomeDeepLinkNavigationAction.NONE
    }

    return when (normalizeNavigationRoute(currentRoute)) {
        null -> HomeDeepLinkNavigationAction.AWAIT_ROUTE
        Screen.Main.route -> HomeDeepLinkNavigationAction.NONE
        else -> HomeDeepLinkNavigationAction.NAVIGATE_HOME
    }
}

internal fun homeDeepLinkHighlightEffectsEnabled(
    shellHighlightEffectsEnabled: Boolean,
    homeDeepLinkSignal: Int,
    readyHomeDeepLinkHighlightSignal: Int,
): Boolean =
    shellHighlightEffectsEnabled && homeDeepLinkSignal <= readyHomeDeepLinkHighlightSignal

@Composable
private fun RoutedTopChromeHazeProvider(
    navController: NavHostController,
    backStackEntry: NavBackStackEntry,
    content: @Composable () -> Unit,
) {
    val routeTopChromeHazeState = rememberChromeHazeState()
    // Live back-queue read, not currentBackStackEntryAsState(): the State lags
    // navigate()'s synchronous lifecycle drop by a frame (see
    // canOpenOverlaySheetFrom), which is exactly the window the modal gate's
    // drop-on-leave decision needs to see.
    val isCurrentDestination = remember(navController, backStackEntry) {
        { navController.currentBackStackEntry == backStackEntry }
    }
    CompositionLocalProvider(
        LocalChromeHazeState provides routeTopChromeHazeState,
        LocalModalHostIsCurrentDestination provides isCurrentDestination,
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HrtTrackerNavHost(
    navController: NavHostController,
    homeDeepLinkSignal: Int = 0,
    milestonesDeepLinkSignal: Int = 0,
    onConsumeMilestonesDeepLink: () -> Boolean = { false },
    onMilestonesDeepLinkSettled: () -> Unit = {},
    highlightEffectsEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var medicationLogEntrySheetRequest by rememberSaveable(stateSaver = MedicationLogEntrySheetRequestSaver) {
        mutableStateOf<MedicationLogEntrySheetRequest?>(null)
    }
    val navigationLock = LocalNavigationLock.current
    var mainScrollToTopSignal by remember { mutableIntStateOf(0) }
    var planScrollToTopSignal by remember { mutableIntStateOf(0) }
    var journalScrollToTopSignal by remember { mutableIntStateOf(0) }
    var settingsScrollToTopSignal by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    // Post-log stock snackbar. Hosted inside the navigation scaffold's content
    // region (below) so it rides above the app's bottom navigation bar rather
    // than overlapping it. Lives above the log sheets so it survives their
    // teardown; the saved-warning callbacks fire it after the sheet/back-stack
    // is cleared.
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val snackbarContext = LocalContext.current
    // The app switches UI language in place via composition locals without
    // recreating the Activity (see MainActivity), so LocalContext.current is
    // rebuilt on a language change. These flow collectors live in
    // LaunchedEffect(Unit) blocks that never restart, which would otherwise
    // freeze the captured context on the language active when first launched and
    // emit toasts in the stale language. rememberUpdatedState keeps the latest
    // localized context available to the long-lived collectors.
    val latestSnackbarContext by rememberUpdatedState(snackbarContext)
    val stockNudgeViewModel: StockTrackingNudgeViewModel = hiltViewModel()
    val stockNudgeEnabled by stockNudgeViewModel.enabled
        .collectAsStateWithLifecycle(initialValue = true)
    val pendingNudge by stockNudgeViewModel.pendingNudge.collectAsStateWithLifecycle()
    val optInTarget by stockNudgeViewModel.optInTarget.collectAsStateWithLifecycle()
    val optInResolving by stockNudgeViewModel.optInResolving.collectAsStateWithLifecycle()
    val layoutDirection = LocalLayoutDirection.current
    val currentBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentDestination = currentBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    // Whether a NavHost-hosted overlay sheet may open from [entry] right now —
    // see canOpenOverlaySheetFrom. Captured once so the five row/quick-log call
    // sites stay in lockstep and none skips the current-destination check.
    //
    // is-current must read the *live* back queue, not the captured
    // currentBackStackEntry State: navigate() drops the outgoing entry's
    // lifecycle to STARTED synchronously (NavControllerImpl.updateBackStackLifecycle)
    // but currentBackStackEntryAsState() only catches up a frame later via its
    // collector. Using the State here would leave a one-frame window where the
    // outgoing entry reads stale-current AND already-STARTED — re-opening the
    // sheet over the destination, the exact race this guard closes. The live
    // getter moves in lockstep with that lifecycle drop, so the window is gone.
    val canOpenOverlaySheet: (NavBackStackEntry) -> Boolean = { entry ->
        canOpenOverlaySheetFrom(
            originState = entry.lifecycle.currentState,
            isCurrentDestination = entry == navController.currentBackStackEntry,
        )
    }
    val explicitParentRoute = currentBackStackEntry?.arguments?.getString(TOP_LEVEL_PARENT_ARG)
    val selectedBottomScreen =
        Screen.topLevelScreenForRoute(explicitParentRoute)
            ?: topLevelNavigationItems.firstOrNull { navItem ->
                currentDestination?.hierarchy?.any { it.route == navItem.screen.route } == true
            }?.screen
            ?: Screen.Main
    // actionLabel is resolved at snackbar time off the localized snackbarContext;
    // stringResource() isn't callable in this callback, so the
    // LocalContextGetResourceValueCall lint false-positives here.
    @Suppress("LocalContextGetResourceValueCall")
    val showPostLogStockWarning: (PostLogStockWarning) -> Unit = { warning ->
        val message = postLogStockWarningSnackbarMessage(warning, snackbarContext)
        val actionLabel = snackbarContext.getString(R.string.stock_snackbar_action_view)
        snackbarScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                withDismissAction = true,
                // HrtSnackbar owns the 5s countdown/dismissal; keep the host
                // from auto-dismissing on its own timer.
                duration = SnackbarDuration.Indefinite,
            )
            if (result == SnackbarResult.ActionPerformed) {
                // Root the destination under the tab the user is on now so the
                // highlighted tab stays put and back returns there directly.
                navController.navigate(
                    postLogStockWarningDestination(warning, selectedBottomScreen.route),
                ) {
                    launchSingleTop = true
                }
            }
        }
    }

    LaunchedEffect(pendingNudge) {
        val medicine = pendingNudge ?: return@LaunchedEffect
        val displayName = medicineDisplayName(medicine, snackbarContext)
        // Resolved off the localized snackbarContext at emit time; stringResource()
        // isn't callable inside LaunchedEffect, so the
        // LocalContextGetResourceValueCall lint false-positives here.
        @Suppress("LocalContextGetResourceValueCall")
        val nudgeMessage = snackbarContext.getString(R.string.stock_nudge_message, displayName)

        @Suppress("LocalContextGetResourceValueCall")
        val nudgeActionLabel = snackbarContext.getString(R.string.stock_nudge_action_enable)
        val result = snackbarHostState.showSnackbar(
            StockNudgeVisuals(
                message = nudgeMessage,
                actionLabel = nudgeActionLabel,
                onDismissTapped = { stockNudgeViewModel.onNudgeDismissedViaX() },
            ),
        )
        when (result) {
            SnackbarResult.ActionPerformed -> stockNudgeViewModel.onNudgeActionTapped()
            SnackbarResult.Dismissed -> stockNudgeViewModel.onNudgeTimedOut()
        }
    }

    LaunchedEffect(Unit) {
        stockNudgeViewModel.autoDisabledEvents.collect {
            Toast.makeText(
                latestSnackbarContext,
                R.string.stock_nudge_disabled_toast,
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        stockNudgeViewModel.optInFailureEvents.collect {
            Toast.makeText(
                latestSnackbarContext,
                R.string.medicine_stock_update_failure,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        stockNudgeViewModel.optInAddedEvents.collect { confirmation ->
            val countText = stockInventoryCountText(
                context = latestSnackbarContext,
                preparation = confirmation.preparation,
                count = confirmation.amount,
                category = confirmation.category,
            ) ?: return@collect
            Toast.makeText(
                latestSnackbarContext,
                latestSnackbarContext.getString(R.string.stock_nudge_added_toast, countText),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    var lastHandledHomeDeepLinkSignal by rememberSaveable { mutableIntStateOf(0) }
    var pendingHomeDeepLinkHighlightSignal by rememberSaveable { mutableIntStateOf(0) }
    var readyHomeDeepLinkHighlightSignal by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(homeDeepLinkSignal, currentRoute) {
        val hasUnhandledSignal = homeDeepLinkSignal > lastHandledHomeDeepLinkSignal
        when (
            homeDeepLinkNavigationAction(
                homeDeepLinkSignal = homeDeepLinkSignal,
                lastHandledHomeDeepLinkSignal = lastHandledHomeDeepLinkSignal,
                currentRoute = currentRoute,
            )
        ) {
            HomeDeepLinkNavigationAction.AWAIT_ROUTE -> Unit
            HomeDeepLinkNavigationAction.NONE -> {
                if (hasUnhandledSignal) {
                    pendingHomeDeepLinkHighlightSignal = homeDeepLinkSignal
                }
                lastHandledHomeDeepLinkSignal = maxOf(
                    lastHandledHomeDeepLinkSignal,
                    homeDeepLinkSignal,
                )
            }

            HomeDeepLinkNavigationAction.NAVIGATE_HOME -> {
                lastHandledHomeDeepLinkSignal = homeDeepLinkSignal
                pendingHomeDeepLinkHighlightSignal = homeDeepLinkSignal
                navController.navigateToTopLevelScreen(
                    targetScreen = Screen.Main,
                    selectedBottomScreen = selectedBottomScreen,
                )
            }
        }
    }

    LaunchedEffect(milestonesDeepLinkSignal) {
        // The dedup marker lives in the ViewModel (consumeMilestonesDeepLink), not
        // rememberSaveable here: it must share the signal's exact lifetime so a
        // process-death re-tap navigates and a config-change recreation doesn't
        // replay. See MainViewModel.lastHandledMilestonesSignal.
        if (!onConsumeMilestonesDeepLink()) return@LaunchedEffect
        // Read the destination live from the controller: the composition-captured
        // currentRoute is still null when this effect fires on the cold-start first
        // composition (currentBackStackEntryAsState hasn't delivered its first value),
        // which would silently skip the journal synthesis below.
        val liveRoute = navController.currentDestination?.route
        // Avoid stacking duplicates if the user re-taps while already on the screen.
        // The route is the registered *pattern* — it carries the query-arg template
        // ("journal_milestones?top_level_parent={…}&open_add_date={…}"), so a
        // `!= baseRoute` check never matches and the guard would be dead. Compare with
        // startsWith against the bare baseRoute instead.
        if (liveRoute?.startsWith(Screen.JournalMilestones.baseRoute) != true) {
            // From the root home entry (the cold-start case), synthesize the parent
            // chain — home → journal → milestones — so back walks down the hierarchy
            // instead of jumping straight to home. At cold start all of this composes
            // beneath the splash screen, which MainActivity holds until the settle
            // callback below, so neither home nor the push transition is ever visible.
            if (liveRoute == Screen.Main.route) {
                navController.navigate(Screen.Journal.route)
            }
            navController.navigate(Screen.JournalMilestones.createRoute())
        }
        // Settled = the transition finished and milestones is the only visible entry;
        // dismissing the splash on that frame shows real content, not a mid-flight
        // animation. The timeout is insurance against a stuck splash if the
        // transition never settles (e.g. navigation interrupted).
        withTimeoutOrNull(MILESTONES_DEEP_LINK_SETTLE_TIMEOUT_MILLIS) {
            navController.visibleEntries.first { entries ->
                entries.size == 1 &&
                        entries.last().destination.route
                            ?.startsWith(Screen.JournalMilestones.baseRoute) == true
            }
            withFrameNanos { }
        }
        onMilestonesDeepLinkSettled()
    }

    LaunchedEffect(
        pendingHomeDeepLinkHighlightSignal,
        currentRoute,
        highlightEffectsEnabled,
    ) {
        val pendingSignal = pendingHomeDeepLinkHighlightSignal
        if (
            pendingSignal <= 0 ||
            !highlightEffectsEnabled ||
            normalizeNavigationRoute(currentRoute) != Screen.Main.route
        ) {
            return@LaunchedEffect
        }

        withFrameNanos { }
        withFrameNanos { }

        if (pendingHomeDeepLinkHighlightSignal == pendingSignal) {
            readyHomeDeepLinkHighlightSignal = maxOf(
                readyHomeDeepLinkHighlightSignal,
                pendingSignal,
            )
            pendingHomeDeepLinkHighlightSignal = 0
        }
    }

    val homeDeepLinkHighlightEffectsEnabled = homeDeepLinkHighlightEffectsEnabled(
        shellHighlightEffectsEnabled = highlightEffectsEnabled,
        homeDeepLinkSignal = homeDeepLinkSignal,
        readyHomeDeepLinkHighlightSignal = readyHomeDeepLinkHighlightSignal,
    )

    BackHandler(
        enabled = topLevelRootBackAction(
            selectedBottomScreen = selectedBottomScreen,
            currentRoute = currentRoute,
        ) == TopLevelRootBackAction.NAVIGATE_HOME
    ) {
        navController.navigateToTopLevelScreen(
            targetScreen = Screen.Main,
            selectedBottomScreen = selectedBottomScreen,
        )
    }

    val topLevelNavigationChromeLocked = isTopLevelNavigationChromeLocked(
        isNavigationLockHeld = navigationLock.isLocked,
        hasPendingLogEntrySheetRequest = medicationLogEntrySheetRequest != null,
        hasPendingStockOptInSheet = optInTarget != null || optInResolving,
    )

    val navigationSuiteType = rememberNavigationSuiteType()
    val navigationChromeHazeState = rememberChromeHazeState()

    EdgeToEdgeNavigationSuiteScaffold(
        modifier = modifier,
        navigationSuiteType = navigationSuiteType,
        navigationChromeHazeState = navigationChromeHazeState,
        navigationSuiteItems = {
            topLevelNavigationItems.forEach { navItem ->
                item(
                    selected = selectedBottomScreen == navItem.screen,
                    onClick = {
                        // Re-evaluated at tap time (not captured at composition)
                        // so a row tap that just requested a sheet in this same
                        // frame already locks the chrome.
                        val chromeLocked = isTopLevelNavigationChromeLocked(
                            isNavigationLockHeld = navigationLock.isLocked,
                            hasPendingLogEntrySheetRequest =
                                medicationLogEntrySheetRequest != null,
                            hasPendingStockOptInSheet = optInTarget != null || optInResolving,
                        )
                        val tapAction = if (chromeLocked) {
                            TopLevelNavigationTapAction.NONE
                        } else {
                            topLevelNavigationTapAction(
                                tappedScreen = navItem.screen,
                                selectedBottomScreen = selectedBottomScreen,
                                currentRoute = currentRoute,
                            )
                        }
                        when (tapAction) {
                            TopLevelNavigationTapAction.POP_TO_TOP_LEVEL -> {
                                navController.popBackStackSafely(
                                    navItem.screen.route,
                                    inclusive = false
                                )
                            }

                            TopLevelNavigationTapAction.SCROLL_TO_TOP -> {
                                when (navItem.screen) {
                                    Screen.Main -> mainScrollToTopSignal++
                                    Screen.Plan -> planScrollToTopSignal++
                                    Screen.Journal -> journalScrollToTopSignal++
                                    Screen.Settings -> settingsScrollToTopSignal++
                                    else -> Unit
                                }
                            }

                            TopLevelNavigationTapAction.NAVIGATE -> {
                                navController.navigateToTopLevelScreen(
                                    targetScreen = navItem.screen,
                                    selectedBottomScreen = selectedBottomScreen,
                                )
                            }

                            TopLevelNavigationTapAction.NONE -> Unit
                        }
                    },
                    icon = {
                        val isSelected = selectedBottomScreen == navItem.screen
                        Icon(
                            painter = painterResource(
                                if (isSelected) navItem.icon else navItem.iconAlt
                            ),
                            contentDescription = stringResource(navItem.screen.label)
                        )
                    },
                    label = {
                        val screenLabelText = stringResource(navItem.screen.label)
                        Text(
                            text = screenLabelText,
                            modifier = Modifier.cjkTextOffset(screenLabelText)
                        )
                    }
                )
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Screen.Main.route,
                modifier = Modifier.consumeWindowInsets(WindowInsets.navigationBars),
                enterTransition = { hrtNavHostEnterTransition(density, layoutDirection) },
                exitTransition = { hrtNavHostExitTransition(density, layoutDirection) },
                popEnterTransition = { hrtNavHostPopEnterTransition(density, layoutDirection) },
                popExitTransition = { hrtNavHostPopExitTransition(density, layoutDirection) },
            ) {
                composable(Screen.Main.route) { backStackEntry ->
                    RoutedTopChromeHazeProvider(navController, backStackEntry) {
                        MainScreen(
                            modifier,
                            scrollToTopSignal = mainScrollToTopSignal,
                            highlightEffectsEnabled = homeDeepLinkHighlightEffectsEnabled,
                            onEntryClick = { request ->
                                if (canOpenOverlaySheet(backStackEntry)) {
                                    medicationLogEntrySheetRequest = MedicationLogEntrySheetRequest(
                                        entryIds = request.entryUuids.map(UUID::toString),
                                        editSnapshot = request.toMedicationLogEntryEditSnapshot(),
                                    )
                                }
                            },
                            onMedicineDetailClick = { medicineId ->
                                // Root the detail under the current tab (Home) so the
                                // highlighted tab stays put and back returns straight
                                // here, instead of stacking under a different tab.
                                navController.navigate(
                                    Screen.MedicineDetail.createRoute(
                                        medicineId = medicineId.toString(),
                                        topLevelParentRoute = selectedBottomScreen.route,
                                    ),
                                )
                            },
                            onAddEntryClick = {
                                // Jump straight to the manager; its dose sheet saves
                                // the manual log directly in manual-log mode.
                                navController.navigate(
                                    Screen.Medicines.createRoute(
                                        topLevelParentRoute = selectedBottomScreen.route,
                                        slotResultKey = ADD_ENTRY_SLOT_RESULT_KEY,
                                    ),
                                )
                            },
                            onOpenTimeline = {
                                // Open the timeline (Milestones) page on top of the current
                                // home tab rather than switching to the Journal tab, so the
                                // bottom bar stays on Home and Back returns here.
                                navController.navigate(
                                    Screen.JournalMilestones.createRoute(
                                        topLevelParentRoute = selectedBottomScreen.route,
                                    ),
                                )
                            },
                            onQuickLogDoseClick = { request ->
                                if (
                                    request.medicationCount > 0 &&
                                    canOpenOverlaySheet(backStackEntry)
                                ) {
                                    medicationLogEntrySheetRequest = MedicationLogEntrySheetRequest(
                                        quickLogRequest = MedicationLogEntryQuickLogRequest(
                                            groupId = request.groupUuid,
                                            scheduleTimeUuid = request.scheduleTimeUuid,
                                            scheduledFor = request.scheduledAt,
                                            medicine = request.medicine,
                                            applicationType = request.applicationType,
                                            doseInstruction = request.doseInstruction,
                                            medicationCount = request.medicationCount,
                                            sourceGroupName = request.sourceGroupName,
                                            sourceGroupColorKey = request.sourceGroupColorKey,
                                            sourceGroupIsArchived = request.sourceGroupIsArchived,
                                            sourceGroupPreviousScheduledFor = request.sourceGroupPreviousScheduledFor,
                                            sourceGroupNextScheduledFor = request.sourceGroupNextScheduledFor,
                                        )
                                    )
                                }
                            }
                        )
                    }
                }
                composable(Screen.Plan.route) { backStackEntry ->
                    RoutedTopChromeHazeProvider(navController, backStackEntry) {
                        PlanScreen(
                            modifier = modifier,
                            scrollToTopSignal = planScrollToTopSignal,
                            onGroupClick = { groupId ->
                                navController.navigate(
                                    Screen.EditMedicationGroup.createRoute(
                                        topLevelParentRoute = Screen.Plan.route,
                                        groupId = groupId.toString()
                                    )
                                )
                            },
                            onEntryClick = { entryIds ->
                                if (canOpenOverlaySheet(backStackEntry)) {
                                    medicationLogEntrySheetRequest = MedicationLogEntrySheetRequest(
                                        entryIds = entryIds.map(UUID::toString)
                                    )
                                }
                            },
                            onQuickLogClick = { groupId, scheduleTimeUuid, scheduledAt, medication, medicationCount ->
                                if (
                                    medicationCount > 0 &&
                                    canOpenOverlaySheet(backStackEntry)
                                ) {
                                    medicationLogEntrySheetRequest = MedicationLogEntrySheetRequest(
                                        quickLogRequest = MedicationLogEntryQuickLogRequest(
                                            groupId = groupId,
                                            scheduleTimeUuid = scheduleTimeUuid,
                                            scheduledFor = scheduledAt,
                                            medicine = medication.medicine,
                                            applicationType = medication.applicationType,
                                            doseInstruction = medication.doseInstruction,
                                            medicationCount = medicationCount
                                        )
                                    )
                                }
                            },
                            onAddGroupClick = {
                                navController.navigate(
                                    Screen.EditMedicationGroup.createRoute(
                                        topLevelParentRoute = Screen.Plan.route
                                    )
                                )
                            },
                            onHistoryClick = {
                                navController.navigate(Screen.History.createRoute(Screen.Plan.route)) {
                                    launchSingleTop = true
                                }
                            },
                            onBatchAddClick = {
                                navController.navigate(Screen.PlanBatchAdd.createRoute(Screen.Plan.route)) {
                                    launchSingleTop = true
                                }
                            },
                            onArchivedGroupsClick = {
                                navController.navigate(Screen.PlanArchivedGroups.createRoute(Screen.Plan.route)) {
                                    launchSingleTop = true
                                }
                            },
                            onMedicinesClick = {
                                navController.navigate(Screen.Medicines.createRoute(Screen.Plan.route)) {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                }
                composable(
                    route = Screen.PlanBatchAdd.route,
                    arguments = listOf(
                        navArgument(TOP_LEVEL_PARENT_ARG) {
                            type = NavType.StringType
                            defaultValue = Screen.Plan.route
                        }
                    )
                ) {
                    RoutedTopChromeHazeProvider(navController, it) {
                        PlanBatchAddScreen(
                            modifier = modifier,
                            onNavigateBack = { navController.popBackStackSafely() },
                            onStockWarning = showPostLogStockWarning,
                        )
                    }
                }
                composable(
                    route = Screen.PlanArchivedGroups.route,
                    arguments = listOf(
                        navArgument(TOP_LEVEL_PARENT_ARG) {
                            type = NavType.StringType
                            defaultValue = Screen.Plan.route
                        }
                    )
                ) {
                    RoutedTopChromeHazeProvider(navController, it) {
                        ArchivedMedicationGroupsScreen(
                            modifier = modifier,
                            onNavigateBack = { navController.popBackStackSafely() },
                            onGroupClick = { groupId ->
                                navController.navigate(
                                    Screen.EditMedicationGroup.createRoute(
                                        topLevelParentRoute = Screen.Plan.route,
                                        groupId = groupId.toString(),
                                        source = MEDICATION_GROUP_EDITOR_SOURCE_ARCHIVED_GROUPS,
                                    )
                                )
                            }
                        )
                    }
                }
                composable(
                    route = Screen.History.route,
                    arguments = listOf(
                        navArgument(TOP_LEVEL_PARENT_ARG) {
                            type = NavType.StringType
                            defaultValue = Screen.Plan.route
                        }
                    )
                ) { backStackEntry ->
                    RoutedTopChromeHazeProvider(navController, backStackEntry) {
                        HistoryScreen(
                            modifier = modifier,
                            onNavigateBack = { navController.popBackStackSafely() },
                            onEntryClick = { entryIds ->
                                if (canOpenOverlaySheet(backStackEntry)) {
                                    medicationLogEntrySheetRequest = MedicationLogEntrySheetRequest(
                                        entryIds = entryIds.map(UUID::toString)
                                    )
                                }
                            }
                        )
                    }
                }
                composable(Screen.Journal.route) { backStackEntry ->
                    RoutedTopChromeHazeProvider(navController, backStackEntry) {
                        JournalScreen(
                            onOpenMilestones = {
                                navController.navigate(
                                    Screen.JournalMilestones.createRoute(Screen.Journal.route)
                                )
                            },
                            onAddDate = {
                                navController.navigate(
                                    Screen.JournalMilestones.createRoute(
                                        Screen.Journal.route,
                                        openAddDate = true,
                                    )
                                )
                            },
                            onOpenAllNotes = {
                                navController.navigate(
                                    Screen.JournalAllNotes.createRoute(Screen.Journal.route)
                                )
                            },
                            scrollToTopSignal = journalScrollToTopSignal,
                            modifier = modifier,
                        )
                    }
                }
                composable(
                    route = Screen.JournalMilestones.route,
                    arguments = topLevelParentArgs(Screen.Journal.route) + navArgument(OPEN_ADD_DATE_ARG) {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ) {
                    RoutedTopChromeHazeProvider(navController, it) {
                        MilestonesScreen(
                            onNavigateBack = { navController.popBackStackSafely() },
                            openAddDateOnLaunch = it.arguments?.getBoolean(OPEN_ADD_DATE_ARG) == true,
                            modifier = modifier,
                        )
                    }
                }
                composable(
                    route = Screen.JournalAllNotes.route,
                    arguments = topLevelParentArgs(Screen.Journal.route),
                ) {
                    RoutedTopChromeHazeProvider(navController, it) {
                        AllNotesScreen(
                            onNavigateBack = { navController.popBackStackSafely() },
                            modifier = modifier,
                        )
                    }
                }
                composable(Screen.Settings.route) {
                    RoutedTopChromeHazeProvider(navController, it) {
                        SettingsScreen(
                            modifier = modifier,
                            scrollToTopSignal = settingsScrollToTopSignal,
                            onPkCalibrationDebugClick = {
                                if (BuildConfig.DEBUG) {
                                    navController.navigate(
                                        Screen.SettingsPkCalibrationDebug.createRoute(
                                            Screen.Settings.route,
                                        ),
                                    )
                                }
                            },
                            onCalibrationClick = {
                                navController.navigate(
                                    Screen.SettingsCalibration.createRoute(Screen.Settings.route)
                                )
                            }
                        )
                    }
                }
                // The entry is a no-op outside debug builds (src/release, src/benchmark);
                // the Settings row that navigates here is DEBUG-gated.
                composable(
                    route = Screen.SettingsPkCalibrationDebug.route,
                    arguments = topLevelParentArgs(Screen.Settings.route),
                ) {
                    RoutedTopChromeHazeProvider(navController, it) {
                        PkCalibrationDebugScreenEntry(
                            onNavigateBack = { navController.popBackStackSafely() },
                            modifier = modifier,
                        )
                    }
                }
                composable(
                    route = Screen.SettingsCalibration.route,
                    arguments = listOf(
                        navArgument(TOP_LEVEL_PARENT_ARG) {
                            type = NavType.StringType
                            defaultValue = Screen.Settings.route
                        }
                    )
                ) {
                    RoutedTopChromeHazeProvider(navController, it) {
                        CalibrationScreen(
                            modifier = modifier,
                            onNavigateBack = { navController.popBackStackSafely() },
                            onUnitsClick = {
                                navController.navigate(
                                    Screen.SettingsCalibrationUnits.createRoute(Screen.Settings.route)
                                )
                            },
                            onAddClick = {
                                navController.navigate(
                                    Screen.SettingsCalibrationEntry.createRoute(Screen.Settings.route)
                                )
                            },
                            onPanelClick = { panelUuid ->
                                navController.navigate(
                                    Screen.SettingsCalibrationEntry.createRoute(
                                        topLevelParentRoute = Screen.Settings.route,
                                        panelId = panelUuid.toString()
                                    )
                                )
                            }
                        )
                    }
                }
                composable(
                    route = Screen.SettingsCalibrationUnits.route,
                    arguments = listOf(
                        navArgument(TOP_LEVEL_PARENT_ARG) {
                            type = NavType.StringType
                            defaultValue = Screen.Settings.route
                        }
                    )
                ) {
                    RoutedTopChromeHazeProvider(navController, it) {
                        CalibrationUnitsScreen(
                            modifier = modifier,
                            onNavigateBack = { navController.popBackStackSafely() },
                        )
                    }
                }
                composable(
                    route = Screen.SettingsCalibrationEntry.route,
                    arguments = listOf(
                        navArgument(CalibrationEditorViewModel.PANEL_ID_ARG) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument(TOP_LEVEL_PARENT_ARG) {
                            type = NavType.StringType
                            defaultValue = Screen.Settings.route
                        }
                    )
                ) {
                    RoutedTopChromeHazeProvider(navController, it) {
                        CalibrationEditorScreen(
                            modifier = modifier,
                            onNavigateBack = { navController.popBackStackSafely() },
                            // Raw pop, not popBackStackSafely: the RESUMED gate
                            // exists to debounce rapid double-back taps, which
                            // can't apply to a one-shot programmatic pop fired
                            // off a consumed flag under the held nav lock.
                            onSaved = { navController.popBackStack() }
                        )
                    }
                }
                composable(
                    route = Screen.Medicines.route,
                    arguments = listOf(
                        navArgument(TOP_LEVEL_PARENT_ARG) {
                            type = NavType.StringType
                            defaultValue = Screen.Plan.route
                        },
                        navArgument(SLOT_RESULT_KEY_ARG) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) { backStackEntry ->
                    RoutedTopChromeHazeProvider(navController, backStackEntry) {
                        val topLevelParentRoute =
                            backStackEntry.arguments?.getString(TOP_LEVEL_PARENT_ARG)
                                ?: Screen.Plan.route
                        val slotResultKey =
                            backStackEntry.arguments?.getString(SLOT_RESULT_KEY_ARG)
                        val launchMode = medicineManagerLaunchMode(
                            slotResultKey = slotResultKey,
                            manualLogResultKey = ADD_ENTRY_SLOT_RESULT_KEY,
                        )
                        // Sheet-completion pops below fire after the sheet's
                        // hide animation and can race an in-flight navigation,
                        // which silently drops the pop and strands the user on
                        // this screen. Wait for the entry to be RESUMED; if the
                        // user actually navigated elsewhere this scope is
                        // disposed with the route and the pop is abandoned.
                        // A resume can be followed by an immediate pause that
                        // drops the pop on the re-dispatch hop (the entry dips
                        // below RESUMED between withResumed firing and the pop
                        // running), so retry on the next resume instead of
                        // silently giving up.
                        val sheetCompletionPopScope = rememberCoroutineScope()
                        val popBackStackWhenResumed: () -> Unit = {
                            sheetCompletionPopScope.launch {
                                do {
                                    backStackEntry.lifecycle.withResumed { }
                                } while (!navController.popBackStackSafely())
                            }
                        }
                        MedicinesScreen(
                            modifier = modifier,
                            onNavigateBack = { navController.popBackStackSafely() },
                            onMedicineClick = { medicineId ->
                                // Slot-result mode: MedicinesScreen hosts the dose
                                // sheet itself and returns the completed slot via
                                // onSlotResolved below — don't open the detail
                                // screen in that case.
                                if (launchMode == MedicineManagerLaunchMode.Manager) {
                                    navController.navigate(
                                        Screen.MedicineDetail.createRoute(
                                            medicineId = medicineId.toString(),
                                            topLevelParentRoute = topLevelParentRoute,
                                        ),
                                    )
                                }
                            },
                            launchMode = launchMode,
                            onSlotResolved = { slotResult ->
                                val groupSlotMode =
                                    launchMode as? MedicineManagerLaunchMode.GroupSlot
                                        ?: return@MedicinesScreen
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set(groupSlotMode.resultKey, slotResult.toBundle())
                                popBackStackWhenResumed()
                            },
                            onManualLogSaved = { warning ->
                                popBackStackWhenResumed()
                                warning?.let(showPostLogStockWarning)
                            },
                            onNewMedicineCreated = stockNudgeViewModel::onNewMedicineCreated,
                            stockNudgeEnabled = stockNudgeEnabled,
                            onSetStockNudgeEnabled = stockNudgeViewModel::setEnabled,
                        )
                    }
                }
                composable(
                    route = Screen.MedicineDetail.route,
                    arguments = listOf(
                        navArgument(MedicineDetailViewModel.MEDICINE_ID_ARG) {
                            type = NavType.StringType
                        },
                        navArgument(TOP_LEVEL_PARENT_ARG) {
                            type = NavType.StringType
                            defaultValue = Screen.Plan.route
                        },
                        navArgument(MedicineDetailViewModel.OPEN_OPT_IN_ARG) {
                            type = NavType.BoolType
                            defaultValue = false
                        },
                    ),
                ) {
                    RoutedTopChromeHazeProvider(navController, it) {
                        MedicineDetailScreen(
                            modifier = modifier,
                            onNavigateBack = { navController.popBackStackSafely() },
                            // Raw pop for the post-archive exit: a one-shot
                            // programmatic pop under the held nav lock needs no
                            // double-tap debounce, and must not be droppable.
                            onArchiveExit = { navController.popBackStack() },
                            onGroupClick = { groupId ->
                                navController.navigate(
                                    Screen.EditMedicationGroup.createRoute(
                                        topLevelParentRoute = Screen.Plan.route,
                                        groupId = groupId.toString(),
                                    ),
                                )
                            },
                        )
                    }
                }
                composable(
                    route = Screen.EditMedicationGroup.route,
                    arguments = listOf(
                        navArgument(MedicationGroupEditorViewModel.GROUP_ID_ARG) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument(TOP_LEVEL_PARENT_ARG) {
                            type = NavType.StringType
                            defaultValue = Screen.Plan.route
                        },
                        navArgument(MEDICATION_GROUP_EDITOR_SOURCE_ARG) {
                            type = NavType.StringType
                            defaultValue = ""
                        }
                    )
                ) { backStackEntry ->
                    RoutedTopChromeHazeProvider(navController, backStackEntry) {
                        val openedFromArchivedGroupsPage =
                            backStackEntry.arguments?.getString(MEDICATION_GROUP_EDITOR_SOURCE_ARG) ==
                                    MEDICATION_GROUP_EDITOR_SOURCE_ARCHIVED_GROUPS
                        val topLevelParentRoute =
                            backStackEntry.arguments?.getString(TOP_LEVEL_PARENT_ARG)
                                ?: Screen.Plan.route
                        val groupEditorViewModel: MedicationGroupEditorViewModel =
                            hiltViewModel(backStackEntry)
                        var pendingSlotResultKey by rememberSaveable {
                            mutableStateOf<String?>(null)
                        }
                        var pendingSlotLocalId by rememberSaveable {
                            mutableStateOf<String?>(null)
                        }
                        val pendingResultKey = pendingSlotResultKey
                        val groupSlotResultBundle by if (pendingResultKey != null) {
                            backStackEntry.savedStateHandle
                                .getStateFlow<android.os.Bundle?>(pendingResultKey, null)
                                .collectAsStateWithLifecycle()
                        } else {
                            remember { mutableStateOf<android.os.Bundle?>(null) }
                        }
                        LaunchedEffect(pendingResultKey, groupSlotResultBundle) {
                            val key = pendingResultKey ?: return@LaunchedEffect
                            val localId = pendingSlotLocalId ?: return@LaunchedEffect
                            val bundle = groupSlotResultBundle ?: return@LaunchedEffect
                            com.mkx.hrttracker.ui.catalog.MedicineSlotResult.fromBundle(bundle)
                                ?.let { slotResult ->
                                    groupEditorViewModel.addCompletedMedicationSlot(
                                        localId = localId,
                                        slot = slotResult,
                                    )
                                }
                            backStackEntry.savedStateHandle.remove<android.os.Bundle>(key)
                            pendingSlotResultKey = null
                            pendingSlotLocalId = null
                        }
                        MedicationGroupEditorScreen(
                            modifier = modifier,
                            onNavigateBack = { navController.popBackStackSafely() },
                            // Raw pops for the finishing exits: one-shot
                            // programmatic pops under the held nav lock need no
                            // double-tap debounce, and must not be droppable.
                            // Returning whether the exit happened lets the
                            // screen consume its finishing flags only after a
                            // confirmed pop.
                            onGroupSaved = { navController.popBackStack() },
                            onGroupSavedToPlan = {
                                if (!navController.popBackStack(
                                        Screen.Plan.route,
                                        inclusive = false
                                    )
                                ) {
                                    navController.navigate(Screen.Plan.route) {
                                        launchSingleTop = true
                                    }
                                }
                                true
                            },
                            openedFromArchivedGroupsPage = openedFromArchivedGroupsPage,
                            viewModel = groupEditorViewModel,
                            // The screen still asks the host to "open the picker"
                            // with a slot localId; the host now navigates to the
                            // manager with a slotResultKey so the manager hosts the
                            // dose sheet and returns a complete slot Bundle.
                            onOpenMedicinePicker = { localId ->
                                val resultKey = "$GROUP_SLOT_MEDICINE_RESULT_KEY_PREFIX$localId"
                                pendingSlotResultKey = resultKey
                                pendingSlotLocalId = localId
                                navController.navigate(
                                    Screen.Medicines.createRoute(
                                        topLevelParentRoute = topLevelParentRoute,
                                        slotResultKey = resultKey,
                                    ),
                                )
                            },
                        )
                    }
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    // Content now extends behind the bottom bar, so lift the snackbar by the
                    // scaffold-provided inset to keep it riding above the bar (the gesture
                    // inset in the wide-rail layout, where there is no bottom bar).
                    .padding(bottom = LocalAppContentBottomInset.current),
                snackbar = { snackbarData -> HrtSnackbar(snackbarData) },
            )
            optInTarget?.let { projection ->
                CompositionLocalProvider(
                    LocalChromeHazeState provides navigationChromeHazeState
                ) {
                    AdjustStockSheet(
                        projection = projection,
                        initialTab = AdjustSheetTab.RECEIVED,
                        receivedOnly = true,
                        previewRunway = { hypothetical ->
                            stockNudgeViewModel.previewRunway(
                                medicineId = projection.medicine.uuid,
                                hypotheticalStock = hypothetical,
                            )
                        },
                        onRecount = { },
                        onReceived = { received ->
                            stockNudgeViewModel.submitOptInReceived(
                                medicineId = projection.medicine.uuid,
                                unitsReceived = received.unitsReceived,
                            )
                        },
                        onDismissRequest = stockNudgeViewModel::dismissOptInSheet,
                    )
                }
            }
            medicationLogEntrySheetRequest?.let { request ->
                CompositionLocalProvider(
                    LocalChromeHazeState provides navigationChromeHazeState
                ) {
                    MedicationLogEntryScreen(
                        entryIds = request.entryIds,
                        quickLogRequest = request.quickLogRequest,
                        editSnapshot = request.editSnapshot,
                        onDismissRequest = { medicationLogEntrySheetRequest = null },
                        onEntrySaved = { warning ->
                            medicationLogEntrySheetRequest = null
                            warning?.let(showPostLogStockWarning)
                        },
                    )
                }
            }
            // Swallow back while navigation is locked. Open modal windows
            // dispatch back to their own window (this handler never sees it), so
            // this only absorbs back during the in-flight mutation windows —
            // e.g. between a delete-confirm dialog closing and the editor's exit
            // pop — where a pop would race the pending work. Registered as the
            // last child inside the NavHost's Box so its OnBackPressedCallback is
            // added after NavHost's own PredictiveBackHandler and therefore wins
            // dispatch precedence; placed at the shell level it lost to that
            // handler on every pushed route (backstack > 1) and was dead there.
            BackHandler(enabled = topLevelNavigationChromeLocked) { }
        }
    }
}

// Tap-debounced popBackStack: only fires while the current entry is RESUMED.
// Compose Navigation drops the outgoing entry's lifecycle to STARTED the
// moment a pop begins, so a rapid second tap on the same back button sees a
// non-RESUMED state and is dropped. Without this guard the second tap pops
// past the destination, leaving the previous screen blank.
private fun NavHostController.popBackStackSafely(): Boolean {
    val entry = currentBackStackEntry ?: return false
    if (!entry.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
        return false
    }
    return popBackStack()
}

private fun NavHostController.popBackStackSafely(
    route: String,
    inclusive: Boolean,
): Boolean {
    val entry = currentBackStackEntry ?: return false
    if (!entry.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
        return false
    }
    return popBackStack(route = route, inclusive = inclusive)
}

// Top-level chrome (nav bar/rail taps, back during in-flight work) is ignored
// while a modal is open or a mutation is being written. The NavHost-hosted
// sheet requests are checked directly rather than through the
// composition-reported lock: they become non-null synchronously inside the row
// tap that opens the sheet, before the sheet's window exists to block chrome
// taps itself.
internal fun isTopLevelNavigationChromeLocked(
    isNavigationLockHeld: Boolean,
    hasPendingLogEntrySheetRequest: Boolean,
    hasPendingStockOptInSheet: Boolean,
): Boolean =
    isNavigationLockHeld || hasPendingLogEntrySheetRequest || hasPendingStockOptInSheet

// Tap-debounced overlay-sheet open, mirroring popBackStackSafely: a row tap can
// race a simultaneous navigation tap, and the NavHost-hosted log sheet would
// then open over the destination page. Compose Navigation drops an entry's
// lifecycle to STARTED both while it animates *out* (the racing navigation —
// drop the sheet) and while a freshly pushed entry animates *in* (still
// settling, but a legitimate place to open a sheet). Keying off RESUMED alone
// conflated the two and silently dropped taps for the whole ~300 ms enter
// transition. They are told apart by whether the entry is still the current
// back-stack destination: navigate() updates the back queue synchronously, so
// an outgoing entry stops being current the instant the race is lost, while an
// incoming entry is current from the first frame of its transition.
internal fun canOpenOverlaySheetFrom(
    originState: androidx.lifecycle.Lifecycle.State,
    isCurrentDestination: Boolean,
): Boolean =
    isCurrentDestination && originState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)

private fun NavHostController.navigateToTopLevelScreen(
    targetScreen: Screen,
    selectedBottomScreen: Screen?,
) {
    navigate(targetScreen.route) {
        topLevelNavigationReplacementPopUpToRoute(
            targetScreen = targetScreen,
            selectedBottomScreen = selectedBottomScreen,
        )?.let { popUpToRoute ->
            popUpTo(popUpToRoute) {
                inclusive = true
                saveState = true
            }
        }
        launchSingleTop = true
        restoreState = true
    }
}

private fun topLevelParentArgs(defaultValue: String) = listOf(
    navArgument(TOP_LEVEL_PARENT_ARG) {
        type = NavType.StringType
        this.defaultValue = defaultValue
    }
)

internal const val TOP_LEVEL_PARENT_ARG = "topLevelParent"
internal const val OPEN_ADD_DATE_ARG = "openAddDate"
private const val SLOT_RESULT_KEY_ARG = "slotResultKey"
private const val ADD_ENTRY_SLOT_RESULT_KEY = "addEntrySlotResult"
private const val GROUP_SLOT_MEDICINE_RESULT_KEY_PREFIX = "groupSlot_"
private const val MEDICATION_GROUP_EDITOR_SOURCE_ARG = "source"
private const val MEDICATION_GROUP_EDITOR_SOURCE_ARCHIVED_GROUPS = "archivedGroups"

internal data class MedicationLogEntrySheetRequest(
    val entryIds: List<String> = emptyList(),
    val quickLogRequest: MedicationLogEntryQuickLogRequest? = null,
    val editSnapshot: MedicationLogEntryEditSnapshot? = null,
)

internal val MedicationLogEntrySheetRequestSaver: Saver<MedicationLogEntrySheetRequest?, Any> =
    Saver(
        save = { request ->
            request?.let(::saveMedicationLogEntrySheetRequest) ?: SAVED_REQUEST_ABSENT
        },
        restore = { saved ->
            if (saved == SAVED_REQUEST_ABSENT) null else restoreMedicationLogEntrySheetRequest(saved)
        }
    )

private const val SAVED_REQUEST_ABSENT = "absent"
private const val MILESTONES_DEEP_LINK_SETTLE_TIMEOUT_MILLIS = 2_000L

private fun saveMedicationLogEntrySheetRequest(request: MedicationLogEntrySheetRequest): ArrayList<Any?> {
    return arrayListOf<Any?>(
        ArrayList(request.entryIds),
        request.quickLogRequest?.let(::saveQuickLogRequest),
        request.editSnapshot?.let(::saveEditSnapshot),
    )
}

@Suppress("UNCHECKED_CAST")
private fun restoreMedicationLogEntrySheetRequest(saved: Any): MedicationLogEntrySheetRequest {
    val list = saved as ArrayList<Any?>
    return MedicationLogEntrySheetRequest(
        entryIds = (list[0] as? ArrayList<String>).orEmpty().toList(),
        quickLogRequest = list[1]?.let(::restoreQuickLogRequest),
        editSnapshot = list.getOrNull(2)?.let(::restoreEditSnapshot),
    )
}

private fun MainEditEntryRequest.toMedicationLogEntryEditSnapshot(): MedicationLogEntryEditSnapshot? {
    if (snapshotEntries.isEmpty()) {
        return null
    }

    return MedicationLogEntryEditSnapshot(
        entries = snapshotEntries,
        sourceGroupName = sourceGroupName,
        sourceGroupColorKey = sourceGroupColorKey,
        sourceGroupIsArchived = sourceGroupIsArchived,
        sourceGroupPreviousScheduledFor = sourceGroupPreviousScheduledFor,
        sourceGroupNextScheduledFor = sourceGroupNextScheduledFor,
    )
}

private fun saveQuickLogRequest(request: MedicationLogEntryQuickLogRequest): ArrayList<Any?> {
    return arrayListOf(
        request.groupId.toString(),
        request.scheduleTimeUuid?.toString(),
        request.scheduledFor.toString(),
        // medicine is null for a PATCH_OFF quick-log; the Saver carries that null.
        request.medicine?.let(::saveMedicine),
        request.applicationType.name,
        saveDoseInstruction(request.doseInstruction),
        request.medicationCount,
        request.sourceGroupName,
        request.sourceGroupColorKey?.name,
        request.sourceGroupPreviousScheduledFor?.toString(),
        request.sourceGroupNextScheduledFor?.toString(),
        request.sourceGroupIsArchived,
    )
}

@Suppress("UNCHECKED_CAST")
private fun restoreQuickLogRequest(saved: Any): MedicationLogEntryQuickLogRequest {
    val list = saved as ArrayList<Any?>
    return MedicationLogEntryQuickLogRequest(
        groupId = UUID.fromString(list[0] as String),
        scheduleTimeUuid = (list[1] as? String)?.let(UUID::fromString),
        scheduledFor = LocalDateTime.parse(list[2] as String),
        medicine = list[3]?.let(::restoreMedicine),
        applicationType = MedicationApplicationType.fromStorageValue(list[4] as String),
        doseInstruction = restoreDoseInstruction(list[5]!!),
        medicationCount = list[6] as Int,
        sourceGroupName = list.getOrNull(7) as? String,
        sourceGroupColorKey = (list.getOrNull(8) as? String)?.let(::restoreMedicationGroupColorKey),
        sourceGroupPreviousScheduledFor = (list.getOrNull(9) as? String)?.let(LocalDateTime::parse),
        sourceGroupNextScheduledFor = (list.getOrNull(10) as? String)?.let(LocalDateTime::parse),
        sourceGroupIsArchived = (list.getOrNull(11) as? Boolean) ?: false,
    )
}

private fun restoreMedicationGroupColorKey(value: String): MedicationGroupColorKey? {
    return MedicationGroupColorKey.entries.firstOrNull { colorKey -> colorKey.name == value }
}

private fun saveEditSnapshot(snapshot: MedicationLogEntryEditSnapshot): ArrayList<Any?> {
    return arrayListOf(
        ArrayList(snapshot.entries.map(::saveMedicationLogEntry)),
        snapshot.sourceGroupName,
        snapshot.sourceGroupColorKey?.name,
        snapshot.sourceGroupPreviousScheduledFor?.toString(),
        snapshot.sourceGroupNextScheduledFor?.toString(),
        snapshot.sourceGroupIsArchived,
    )
}

private fun restoreEditSnapshot(saved: Any): MedicationLogEntryEditSnapshot {
    val list = saved as ArrayList<Any?>
    return MedicationLogEntryEditSnapshot(
        entries = (list[0] as? ArrayList<*>)
            .orEmpty()
            .mapNotNull { entry -> entry?.let(::restoreMedicationLogEntry) },
        sourceGroupName = list.getOrNull(1) as? String,
        sourceGroupColorKey = (list.getOrNull(2) as? String)?.let(::restoreMedicationGroupColorKey),
        sourceGroupPreviousScheduledFor = (list.getOrNull(3) as? String)?.let(LocalDateTime::parse),
        sourceGroupNextScheduledFor = (list.getOrNull(4) as? String)?.let(LocalDateTime::parse),
        sourceGroupIsArchived = (list.getOrNull(5) as? Boolean) ?: false,
    )
}

private fun saveMedicationLogEntry(entry: MedicationLogEntry): ArrayList<Any?> {
    return arrayListOf(
        entry.uuid.toString(),
        // medicine is null for a PATCH_OFF log.
        entry.medicine?.let(::saveMedicine),
        entry.category.name,
        entry.applicationType.name,
        saveDoseInstruction(entry.doseInstruction),
        entry.equivalentE2Mg,
        entry.sourceGroupUuid?.toString(),
        entry.appliedAt.toEpochMilli(),
        entry.appliedAtTimeZoneId,
        entry.scheduledFor?.toString(),
        entry.count,
        entry.scheduleTimeUuid?.toString(),
    )
}

@Suppress("UNCHECKED_CAST")
private fun restoreMedicationLogEntry(saved: Any): MedicationLogEntry {
    val list = saved as ArrayList<Any?>
    return MedicationLogEntry(
        uuid = UUID.fromString(list[0] as String),
        medicine = list[1]?.let(::restoreMedicine),
        category = MedicationCategory.fromStorageValue(list[2] as String),
        applicationType = MedicationApplicationType.fromStorageValue(list[3] as String),
        doseInstruction = restoreDoseInstruction(list[4]!!),
        equivalentE2Mg = list[5] as? Double,
        sourceGroupUuid = (list[6] as? String)?.let(UUID::fromString),
        appliedAt = Instant.ofEpochMilli(list[7] as Long),
        appliedAtTimeZoneId = list[8] as String,
        scheduledFor = (list[9] as? String)?.let(LocalDateTime::parse),
        count = list[10] as Int,
        scheduleTimeUuid = (list[11] as? String)?.let(UUID::fromString),
    )
}

// Serializes a Medicine for nav-arg Savers. A medicine is a finite value type;
// carrying it whole survives process death without a repository round-trip.
private fun saveMedicine(medicine: Medicine): ArrayList<Any?> {
    return arrayListOf(
        medicine.uuid.toString(),
        medicine.selection.kind.name,
        (medicine.selection as? MedicineSelection.Catalog)?.medicationKey?.name,
        (medicine.selection as? MedicineSelection.Custom)?.medicationName,
        medicine.category.name,
        savePreparation(medicine.preparation),
        medicine.displayName,
        medicine.identityKey,
        medicine.createdAt.toEpochMilli(),
        medicine.updatedAt.toEpochMilli(),
        medicine.archivedAt?.toEpochMilli(),
        medicine.stock.trackingEnabled,
        medicine.stock.unitsRemaining,
        medicine.stock.unitsLastTotal,
        medicine.stock.openContainerAmount,
        medicine.stock.warnAtDaysRemaining,
        medicine.stock.generation,
        medicine.importedFromExternalTracker,
    )
}

@Suppress("UNCHECKED_CAST")
private fun restoreMedicine(saved: Any): Medicine {
    val list = saved as ArrayList<Any?>
    val preparation = restorePreparation(list[5]!!)
    // PATCH_OFF reuses CATALOG selectionKind for storage; the preparation type
    // is the discriminator on the way back in. Mirrors MedicineEntityMappers.
    val selection = if (preparation is MedicinePreparation.PatchOff) {
        MedicineSelection.PatchOff
    } else {
        when (MedicationSelectionKind.fromStorageValue(list[1] as String)) {
            MedicationSelectionKind.CATALOG -> MedicineSelection.Catalog(
                medicationKey = MedicationKey.fromStorageValue(list[2] as String)
                    ?: error("Unknown medication key: ${list[2]}"),
            )

            MedicationSelectionKind.CUSTOM -> MedicineSelection.Custom(
                medicationName = list[3] as String,
            )
        }
    }
    return Medicine(
        uuid = UUID.fromString(list[0] as String),
        selection = selection,
        category = MedicationCategory.fromStorageValue(list[4] as String),
        preparation = preparation,
        displayName = list[6] as? String,
        identityKey = list[7] as String,
        createdAt = Instant.ofEpochMilli(list[8] as Long),
        updatedAt = Instant.ofEpochMilli(list[9] as Long),
        archivedAt = (list[10] as? Long)?.let(Instant::ofEpochMilli),
        stock = restoreMedicineStock(list),
        importedFromExternalTracker = (list.getOrNull(17) as? Boolean) ?: false,
    )
}

private fun restoreMedicineStock(list: ArrayList<Any?>): MedicineStock {
    if (list.size < 17) {
        return MedicineStock()
    }
    return MedicineStock(
        trackingEnabled = list[11] as Boolean,
        unitsRemaining = list[12] as? Double,
        unitsLastTotal = list[13] as? Double,
        openContainerAmount = list[14] as? Double,
        warnAtDaysRemaining = list[15] as Int,
        generation = list[16] as Long,
    )
}

private fun savePreparation(preparation: MedicinePreparation): ArrayList<Any?> {
    return when (preparation) {
        is MedicinePreparation.Pill ->
            arrayListOf(preparation.type.name, preparation.strengthMgPerTablet)

        is MedicinePreparation.Capsule ->
            arrayListOf(preparation.type.name, preparation.strengthMgPerCapsule)

        is MedicinePreparation.InjectionSingleUseVial ->
            arrayListOf(preparation.type.name, preparation.strengthMgPerVial)

        is MedicinePreparation.InjectionMultiUseVial -> arrayListOf(
            preparation.type.name,
            preparation.concentrationMgPerMl,
            preparation.vialVolumeMl,
        )

        is MedicinePreparation.GelSachet -> arrayListOf(
            preparation.type.name,
            preparation.concentrationPercent,
            preparation.sachetWeightGrams,
        )

        is MedicinePreparation.GelContainer -> arrayListOf(
            preparation.type.name,
            preparation.concentrationPercent,
            preparation.containerWeightGrams,
        )

        is MedicinePreparation.ImportedInjection -> arrayListOf(
            preparation.type.name,
            preparation.administeredMg,
            preparation.ester.name,
        )

        is MedicinePreparation.ImportedGel -> arrayListOf(
            preparation.type.name,
            preparation.appliedEstradiolMg,
        )

        is MedicinePreparation.Patch -> when (val spec = preparation.specification) {
            is MedicinePreparation.PatchSpecification.TotalMg ->
                arrayListOf(preparation.type.name, "TOTAL", spec.valueMg)

            is MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay ->
                arrayListOf(preparation.type.name, "RATE", spec.valueMcgPerDay)
        }

        // PATCH_OFF carries no numeric fields; the type tag is the whole payload.
        is MedicinePreparation.PatchOff -> arrayListOf(preparation.type.name)
    }
}

@Suppress("UNCHECKED_CAST")
private fun restorePreparation(saved: Any): MedicinePreparation {
    val list = saved as ArrayList<Any?>
    return when (MedicinePreparationType.fromStorageValue(list[0] as String)) {
        MedicinePreparationType.PILL ->
            MedicinePreparation.Pill(strengthMgPerTablet = list[1] as Double)

        MedicinePreparationType.CAPSULE ->
            MedicinePreparation.Capsule(strengthMgPerCapsule = list[1] as Double)

        MedicinePreparationType.INJECTION_SINGLE_USE_VIAL ->
            MedicinePreparation.InjectionSingleUseVial(strengthMgPerVial = list[1] as Double)

        MedicinePreparationType.INJECTION_MULTI_USE_VIAL -> MedicinePreparation.InjectionMultiUseVial(
            concentrationMgPerMl = list[1] as Double,
            vialVolumeMl = list[2] as Double,
        )

        MedicinePreparationType.GEL_SACHET -> MedicinePreparation.GelSachet(
            concentrationPercent = list[1] as Double,
            sachetWeightGrams = list[2] as Double,
        )

        MedicinePreparationType.GEL_CONTAINER -> MedicinePreparation.GelContainer(
            concentrationPercent = list[1] as Double,
            containerWeightGrams = list[2] as Double,
        )

        MedicinePreparationType.IMPORTED_INJECTION -> MedicinePreparation.ImportedInjection(
            administeredMg = list[1] as Double,
            ester = MedicationKey.fromStorageValue(list[2] as String)
                ?: error("Unknown imported injection ester key: ${list[2]}"),
        )

        MedicinePreparationType.IMPORTED_GEL -> MedicinePreparation.ImportedGel(
            appliedEstradiolMg = list[1] as Double,
        )

        MedicinePreparationType.PATCH -> MedicinePreparation.Patch(
            specification = if (list[1] == "TOTAL") {
                MedicinePreparation.PatchSpecification.TotalMg(valueMg = list[2] as Double)
            } else {
                MedicinePreparation.PatchSpecification.ReleaseRateMcgPerDay(
                    valueMcgPerDay = list[2] as Double,
                )
            },
        )

        MedicinePreparationType.PATCH_OFF -> MedicinePreparation.PatchOff
    }
}

private fun saveDoseInstruction(instruction: DoseInstruction): ArrayList<Any?> {
    return when (instruction) {
        is DoseInstruction.TabletFraction ->
            arrayListOf(instruction.kind.name, instruction.numerator, instruction.denominator)

        DoseInstruction.WholeUnit -> arrayListOf(instruction.kind.name)
        is DoseInstruction.VolumeMl -> arrayListOf(instruction.kind.name, instruction.valueMl)
        is DoseInstruction.WeightGrams ->
            arrayListOf(instruction.kind.name, instruction.valueGrams)

        DoseInstruction.Noop -> arrayListOf(instruction.kind.name)
    }
}

@Suppress("UNCHECKED_CAST")
private fun restoreDoseInstruction(saved: Any): DoseInstruction {
    val list = saved as ArrayList<Any?>
    return when (DoseInstructionKind.fromStorageValue(list[0] as String)) {
        DoseInstructionKind.TABLET_FRACTION -> DoseInstruction.TabletFraction(
            numerator = list[1] as Int,
            denominator = list[2] as Int,
        )

        DoseInstructionKind.WHOLE_UNIT -> DoseInstruction.WholeUnit
        DoseInstructionKind.VOLUME_ML -> DoseInstruction.VolumeMl(valueMl = list[1] as Double)
        DoseInstructionKind.WEIGHT_GRAMS ->
            DoseInstruction.WeightGrams(valueGrams = list[1] as Double)

        DoseInstructionKind.NOOP -> DoseInstruction.Noop
    }
}
