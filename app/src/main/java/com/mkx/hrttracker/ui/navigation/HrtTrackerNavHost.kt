package com.mkx.hrttracker.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.calibration.CalibrationEditorScreen
import com.mkx.hrttracker.ui.calibration.CalibrationEditorViewModel
import com.mkx.hrttracker.ui.calibration.CalibrationScreen
import com.mkx.hrttracker.ui.calibration.CalibrationUnitsScreen
import com.mkx.hrttracker.ui.history.HistoryScreen
import com.mkx.hrttracker.ui.log.AddEntryQuickLogRequest
import com.mkx.hrttracker.ui.log.AddEntryScreen
import com.mkx.hrttracker.ui.main.MainScreen
import com.mkx.hrttracker.ui.plan.ArchivedMedicationGroupsScreen
import com.mkx.hrttracker.ui.plan.MedicationGroupEditorScreen
import com.mkx.hrttracker.ui.plan.MedicationGroupEditorViewModel
import com.mkx.hrttracker.ui.plan.PlanBatchAddScreen
import com.mkx.hrttracker.ui.plan.PlanScreen
import com.mkx.hrttracker.ui.settings.SettingsScreen
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

    data object EditMedicationGroup : Screen(
        "edit_medication_group?" +
            "${MedicationGroupEditorViewModel.GROUP_ID_ARG}={${MedicationGroupEditorViewModel.GROUP_ID_ARG}}" +
            "&$TOP_LEVEL_PARENT_ARG={$TOP_LEVEL_PARENT_ARG}",
        R.string.add_medication_group
    ) {
        const val baseRoute = "edit_medication_group"

        fun createRoute(
            topLevelParentRoute: String,
            groupId: String? = null
        ): String {
            return if (groupId == null) {
                "$baseRoute?$TOP_LEVEL_PARENT_ARG=$topLevelParentRoute"
            } else {
                "$baseRoute?${MedicationGroupEditorViewModel.GROUP_ID_ARG}=$groupId&$TOP_LEVEL_PARENT_ARG=$topLevelParentRoute"
            }
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

private data class NavigationItemContent(
    val screen: Screen,
    val icon: ImageVector,
)

private val bottomNavItems = listOf(
    NavigationItemContent(Screen.Main, Icons.Rounded.Home),
    NavigationItemContent(Screen.Plan, Icons.Rounded.CalendarMonth),
    NavigationItemContent(Screen.Settings, Icons.Rounded.Settings)
)

internal enum class TopLevelNavigationTapAction {
    NAVIGATE,
    POP_TO_TOP_LEVEL,
    SCROLL_TO_TOP,
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

@Composable
fun HrtTrackerNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    var addEntrySheetRequest by remember { mutableStateOf<AddEntrySheetRequest?>(null) }
    var mainScrollToTopSignal by remember { mutableIntStateOf(0) }
    var planScrollToTopSignal by remember { mutableIntStateOf(0) }
    var settingsScrollToTopSignal by remember { mutableIntStateOf(0) }
    val currentBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentDestination = currentBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    val explicitParentRoute = currentBackStackEntry?.arguments?.getString(TOP_LEVEL_PARENT_ARG)

    val selectedBottomScreen =
        Screen.topLevelScreenForRoute(explicitParentRoute)
            ?: bottomNavItems.firstOrNull { navItem ->
                currentDestination?.hierarchy?.any { it.route == navItem.screen.route } == true
            }?.screen

    Scaffold(
        modifier = modifier,
        bottomBar = {
            ShortNavigationBar {
                bottomNavItems.forEach { navItem ->
                    ShortNavigationBarItem(
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
                                    navController.popBackStack(navItem.screen.route, false)
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
                                    navController.navigate(navItem.screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }

                                TopLevelNavigationTapAction.NONE -> Unit
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = navItem.icon,
                                contentDescription = stringResource(navItem.screen.label)
                            )
                        },
                        label = {
                            Text(text = stringResource(navItem.screen.label))
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            when (currentRoute) {
                Screen.Main.route -> {
                    FloatingActionButton(
                        onClick = {
                            addEntrySheetRequest = AddEntrySheetRequest(entryIds = emptyList())
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.fab_add_entry)
                        )
                    }
                }

                else -> Unit
            }
        },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.statusBars)
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Main.route,
            modifier = Modifier.consumeWindowInsets(innerPadding),
            enterTransition = { hrtNavHostEnterTransition() },
            exitTransition = { hrtNavHostExitTransition() },
            popEnterTransition = { hrtNavHostPopEnterTransition() },
            popExitTransition = { hrtNavHostPopExitTransition() },
        ) {
            composable(Screen.Main.route) {
                MainScreen(
                    modifier.padding(innerPadding),
                    scrollToTopSignal = mainScrollToTopSignal,
                    onQuickLogDoseClick = { groupId, scheduledAt, medicationDetails, medicationCount ->
                        if (medicationCount > 0) {
                            addEntrySheetRequest = AddEntrySheetRequest(
                                quickLogRequest = AddEntryQuickLogRequest(
                                    groupId = groupId,
                                    scheduledFor = scheduledAt,
                                    medicationDetails = medicationDetails,
                                    medicationCount = medicationCount
                                )
                            )
                        }
                    }
                )
            }
            composable(Screen.Plan.route) {
                PlanScreen(
                    modifier = modifier.padding(innerPadding),
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
                        addEntrySheetRequest = AddEntrySheetRequest(
                            entryIds = entryIds.map(UUID::toString)
                        )
                    },
                    onQuickLogClick = { groupId, scheduledAt, medicationDetails, medicationCount ->
                        if (medicationCount > 0) {
                            addEntrySheetRequest = AddEntrySheetRequest(
                                quickLogRequest = AddEntryQuickLogRequest(
                                    groupId = groupId,
                                    scheduledFor = scheduledAt,
                                    medicationDetails = medicationDetails,
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
                    modifier = modifier.padding(innerPadding),
                    onNavigateBack = { navController.popBackStack() },
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
                    modifier = modifier.padding(innerPadding),
                    onNavigateBack = { navController.popBackStack() },
                    onGroupClick = { groupId ->
                        navController.navigate(
                            Screen.EditMedicationGroup.createRoute(
                                topLevelParentRoute = Screen.Plan.route,
                                groupId = groupId.toString()
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
                    modifier = modifier.padding(innerPadding),
                    onNavigateBack = { navController.popBackStack() },
                    onEntryClick = { entryIds ->
                        addEntrySheetRequest = AddEntrySheetRequest(
                            entryIds = entryIds.map(UUID::toString)
                        )
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    modifier = modifier.padding(innerPadding),
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
                    modifier = modifier.padding(innerPadding),
                    onNavigateBack = { navController.popBackStack() },
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
                    modifier = modifier.padding(innerPadding),
                    onNavigateBack = { navController.popBackStack() },
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
                    modifier = modifier.padding(innerPadding),
                    onNavigateBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
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
                    }
                )
            ) {
                MedicationGroupEditorScreen(
                    modifier = modifier.padding(innerPadding),
                    onNavigateBack = { navController.popBackStack() },
                    onGroupSaved = { navController.popBackStack() },
                )
            }
        }
    }

    addEntrySheetRequest?.let { request ->
        AddEntryScreen(
            modifier = Modifier.consumeWindowInsets(WindowInsets.navigationBars),
            entryIds = request.entryIds,
            quickLogRequest = request.quickLogRequest,
            onDismissRequest = { addEntrySheetRequest = null },
            onEntrySaved = { addEntrySheetRequest = null }
        )
    }
}

private const val TOP_LEVEL_PARENT_ARG = "topLevelParent"

private data class AddEntrySheetRequest(
    val entryIds: List<String> = emptyList(),
    val quickLogRequest: AddEntryQuickLogRequest? = null,
)
