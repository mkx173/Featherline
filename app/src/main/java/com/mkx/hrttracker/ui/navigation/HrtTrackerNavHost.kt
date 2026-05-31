package com.mkx.hrttracker.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
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
import com.mkx.hrttracker.ui.calibration.CalibrationEditorScreen
import com.mkx.hrttracker.ui.calibration.CalibrationEditorViewModel
import com.mkx.hrttracker.ui.calibration.CalibrationScreen
import com.mkx.hrttracker.ui.calibration.CalibrationUnitsScreen
import com.mkx.hrttracker.ui.catalog.MedicineDetailScreen
import com.mkx.hrttracker.ui.catalog.MedicineDetailViewModel
import com.mkx.hrttracker.ui.catalog.MedicineManagerLaunchMode
import com.mkx.hrttracker.ui.catalog.MedicinesScreen
import com.mkx.hrttracker.ui.catalog.medicineManagerLaunchMode
import com.mkx.hrttracker.ui.components.LocalAppContentBottomInset
import com.mkx.hrttracker.ui.history.HistoryScreen
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
import com.mkx.hrttracker.ui.settings.SettingsScreen
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

sealed class Screen(val route: String, @get:StringRes val label: Int) {
    data object Main : Screen("main", R.string.tab_main)
    data object Plan : Screen("plan", R.string.tab_plan)
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
                Settings.route -> Settings
                else -> null
            }
        }
    }
}

internal data class NavigationItemContent(
    val screen: Screen,
    @param:DrawableRes val icon: Int,
)

internal val topLevelNavigationItems = listOf(
    NavigationItemContent(Screen.Main, R.drawable.ic_home),
    NavigationItemContent(Screen.Plan, R.drawable.ic_calendar_month),
    NavigationItemContent(Screen.Settings, R.drawable.ic_settings)
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
fun HrtTrackerNavHost(
    navController: NavHostController,
    homeDeepLinkSignal: Int = 0,
    highlightEffectsEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var medicationLogEntrySheetRequest by rememberSaveable(stateSaver = MedicationLogEntrySheetRequestSaver) {
        mutableStateOf<MedicationLogEntrySheetRequest?>(null)
    }
    var mainScrollToTopSignal by remember { mutableIntStateOf(0) }
    var planScrollToTopSignal by remember { mutableIntStateOf(0) }
    var settingsScrollToTopSignal by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val currentBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentDestination = currentBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    val explicitParentRoute = currentBackStackEntry?.arguments?.getString(TOP_LEVEL_PARENT_ARG)
    val selectedBottomScreen =
        Screen.topLevelScreenForRoute(explicitParentRoute)
            ?: topLevelNavigationItems.firstOrNull { navItem ->
                currentDestination?.hierarchy?.any { it.route == navItem.screen.route } == true
            }?.screen
            ?: Screen.Main

    var lastHandledHomeDeepLinkSignal by rememberSaveable { mutableIntStateOf(0) }
    var pendingHomeDeepLinkHighlightSignal by rememberSaveable { mutableIntStateOf(0) }
    var pendingHomeDeepLinkHighlightRequiresNavigation by rememberSaveable { mutableStateOf(false) }
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
                    pendingHomeDeepLinkHighlightRequiresNavigation = false
                }
                lastHandledHomeDeepLinkSignal = maxOf(
                    lastHandledHomeDeepLinkSignal,
                    homeDeepLinkSignal,
                )
            }
            HomeDeepLinkNavigationAction.NAVIGATE_HOME -> {
                lastHandledHomeDeepLinkSignal = homeDeepLinkSignal
                pendingHomeDeepLinkHighlightSignal = homeDeepLinkSignal
                pendingHomeDeepLinkHighlightRequiresNavigation = true
                navController.navigateToTopLevelScreen(
                    targetScreen = Screen.Main,
                    selectedBottomScreen = selectedBottomScreen,
                )
            }
        }
    }

    LaunchedEffect(
        pendingHomeDeepLinkHighlightSignal,
        pendingHomeDeepLinkHighlightRequiresNavigation,
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

        if (pendingHomeDeepLinkHighlightRequiresNavigation) {
            delay(topLevelTransitionDurationMillis.toLong())
        }
        withFrameNanos { }
        withFrameNanos { }

        if (pendingHomeDeepLinkHighlightSignal == pendingSignal) {
            readyHomeDeepLinkHighlightSignal = maxOf(
                readyHomeDeepLinkHighlightSignal,
                pendingSignal,
            )
            pendingHomeDeepLinkHighlightSignal = 0
            pendingHomeDeepLinkHighlightRequiresNavigation = false
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

    val navigationSuiteType =
        NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())
    val rawNavigationBarBottomInset = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val appContentBottomInset =
        if (navigationSuiteType != NavigationSuiteType.WideNavigationRailCollapsed) {
            0.dp
        } else {
            rawNavigationBarBottomInset
        }

    CompositionLocalProvider(LocalAppContentBottomInset provides appContentBottomInset) {
        NavigationSuiteScaffold(
            modifier = modifier,
            layoutType = navigationSuiteType,
            navigationSuiteItems = {
                topLevelNavigationItems.forEach { navItem ->
                    item(
                        selected = selectedBottomScreen == navItem.screen,
                        onClick = {
                            when (
                                topLevelNavigationTapAction(
                                    tappedScreen = navItem.screen,
                                    selectedBottomScreen = selectedBottomScreen,
                                    currentRoute = currentRoute,
                                )
                            ) {
                                TopLevelNavigationTapAction.POP_TO_TOP_LEVEL -> {
                                    navController.popBackStackSafely(navItem.screen.route, inclusive = false)
                                }

                                TopLevelNavigationTapAction.SCROLL_TO_TOP -> {
                                    when (navItem.screen) {
                                        Screen.Main -> mainScrollToTopSignal++
                                        Screen.Plan -> planScrollToTopSignal++
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
                            Icon(
                                painter = painterResource(navItem.icon),
                                contentDescription = stringResource(navItem.screen.label)
                            )
                        },
                        label = {
                            Text(text = stringResource(navItem.screen.label))
                        }
                    )
                }
            }
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Main.route,
                modifier = Modifier.consumeWindowInsets(WindowInsets.navigationBars),
                enterTransition = { hrtNavHostEnterTransition(density, layoutDirection) },
                exitTransition = { hrtNavHostExitTransition(density, layoutDirection) },
                popEnterTransition = { hrtNavHostPopEnterTransition(density, layoutDirection) },
                popExitTransition = { hrtNavHostPopExitTransition(density, layoutDirection) },
            ) {
                composable(Screen.Main.route) {
                    MainScreen(
                        modifier,
                        scrollToTopSignal = mainScrollToTopSignal,
                        highlightEffectsEnabled = homeDeepLinkHighlightEffectsEnabled,
                        onEntryClick = { request ->
                            medicationLogEntrySheetRequest = MedicationLogEntrySheetRequest(
                                entryIds = request.entryUuids.map(UUID::toString),
                                editSnapshot = request.toMedicationLogEntryEditSnapshot(),
                            )
                        },
                        onMedicineDetailClick = { medicineId ->
                            navController.navigate(
                                Screen.MedicineDetail.createRoute(
                                    medicineId = medicineId.toString(),
                                    topLevelParentRoute = Screen.Main.route,
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
                        onQuickLogDoseClick = { request ->
                            if (request.medicationCount > 0) {
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
                                        sourceGroupPreviousScheduledFor = request.sourceGroupPreviousScheduledFor,
                                        sourceGroupNextScheduledFor = request.sourceGroupNextScheduledFor,
                                    )
                                )
                            }
                        }
                    )
                }
                composable(Screen.Plan.route) {
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
                            medicationLogEntrySheetRequest = MedicationLogEntrySheetRequest(
                                entryIds = entryIds.map(UUID::toString)
                            )
                        },
                        onQuickLogClick = { groupId, scheduleTimeUuid, scheduledAt, medication, medicationCount ->
                            if (medicationCount > 0) {
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
                composable(
                    route = Screen.PlanBatchAdd.route,
                    arguments = listOf(
                        navArgument(TOP_LEVEL_PARENT_ARG) {
                            type = NavType.StringType
                            defaultValue = Screen.Plan.route
                        }
                    )
                ) {
                    PlanBatchAddScreen(
                        modifier = modifier,
                        onNavigateBack = { navController.popBackStackSafely() },
                    )
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
                composable(
                    route = Screen.History.route,
                    arguments = listOf(
                        navArgument(TOP_LEVEL_PARENT_ARG) {
                            type = NavType.StringType
                            defaultValue = Screen.Plan.route
                        }
                    )
                ) {
                    HistoryScreen(
                        modifier = modifier,
                        onNavigateBack = { navController.popBackStackSafely() },
                        onEntryClick = { entryIds ->
                            medicationLogEntrySheetRequest = MedicationLogEntrySheetRequest(
                                entryIds = entryIds.map(UUID::toString)
                            )
                        }
                    )
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        modifier = modifier,
                        scrollToTopSignal = settingsScrollToTopSignal,
                        onCalibrationClick = {
                            navController.navigate(
                                Screen.SettingsCalibration.createRoute(Screen.Settings.route)
                            )
                        }
                    )
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
                composable(
                    route = Screen.SettingsCalibrationUnits.route,
                    arguments = listOf(
                        navArgument(TOP_LEVEL_PARENT_ARG) {
                            type = NavType.StringType
                            defaultValue = Screen.Settings.route
                        }
                    )
                ) {
                    CalibrationUnitsScreen(
                        modifier = modifier,
                        onNavigateBack = { navController.popBackStackSafely() },
                    )
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
                    CalibrationEditorScreen(
                        modifier = modifier,
                        onNavigateBack = { navController.popBackStackSafely() },
                        onSaved = { navController.popBackStackSafely() }
                    )
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
                    val topLevelParentRoute =
                        backStackEntry.arguments?.getString(TOP_LEVEL_PARENT_ARG)
                            ?: Screen.Plan.route
                    val slotResultKey =
                        backStackEntry.arguments?.getString(SLOT_RESULT_KEY_ARG)
                    val launchMode = medicineManagerLaunchMode(
                        slotResultKey = slotResultKey,
                        manualLogResultKey = ADD_ENTRY_SLOT_RESULT_KEY,
                    )
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
                            val groupSlotMode = launchMode as? MedicineManagerLaunchMode.GroupSlot
                                ?: return@MedicinesScreen
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set(groupSlotMode.resultKey, slotResult.toBundle())
                            navController.popBackStackSafely()
                        },
                        onManualLogSaved = {
                            navController.popBackStackSafely()
                        },
                    )
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
                    MedicineDetailScreen(
                        modifier = modifier,
                        onNavigateBack = { navController.popBackStackSafely() },
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
                        onGroupSaved = { navController.popBackStackSafely() },
                        onGroupSavedToPlan = {
                            if (!navController.popBackStackSafely(Screen.Plan.route, inclusive = false)) {
                                navController.navigate(Screen.Plan.route) {
                                    launchSingleTop = true
                                }
                            }
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
    }

    medicationLogEntrySheetRequest?.let { request ->
        MedicationLogEntryScreen(
            entryIds = request.entryIds,
            quickLogRequest = request.quickLogRequest,
            editSnapshot = request.editSnapshot,
            onDismissRequest = { medicationLogEntrySheetRequest = null },
            onEntrySaved = { medicationLogEntrySheetRequest = null }
        )
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

internal const val TOP_LEVEL_PARENT_ARG = "topLevelParent"
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

internal val MedicationLogEntrySheetRequestSaver: Saver<MedicationLogEntrySheetRequest?, Any> = Saver(
    save = { request -> request?.let(::saveMedicationLogEntrySheetRequest) ?: SAVED_REQUEST_ABSENT },
    restore = { saved ->
        if (saved == SAVED_REQUEST_ABSENT) null else restoreMedicationLogEntrySheetRequest(saved)
    }
)

private const val SAVED_REQUEST_ABSENT = "absent"

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
