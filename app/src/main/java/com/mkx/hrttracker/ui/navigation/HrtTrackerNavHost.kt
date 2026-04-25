package com.mkx.hrttracker.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
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
import com.mkx.hrttracker.ui.history.HistoryScreen
import com.mkx.hrttracker.ui.log.AddEntryScreen
import com.mkx.hrttracker.ui.log.QuickAddMedicationGroupSheet
import com.mkx.hrttracker.ui.log.QuickLogPlannedDoseSheet
import com.mkx.hrttracker.model.medication.MedicationDetails
import com.mkx.hrttracker.ui.main.MainScreen
import com.mkx.hrttracker.ui.plan.MedicationGroupEditorScreen
import com.mkx.hrttracker.ui.plan.MedicationGroupEditorViewModel
import com.mkx.hrttracker.ui.plan.PlanScreen
import com.mkx.hrttracker.ui.settings.CalibrationEditorScreen
import com.mkx.hrttracker.ui.settings.CalibrationEditorViewModel
import com.mkx.hrttracker.ui.settings.CalibrationScreen
import com.mkx.hrttracker.ui.settings.CalibrationUnitsScreen
import com.mkx.hrttracker.ui.settings.SettingsScreen
import java.time.LocalDateTime
import java.util.UUID

sealed class Screen(val route: String, @get:StringRes val label: Int) {
    data object Main : Screen("main", R.string.tab_main)
    data object Plan : Screen("plan", R.string.tab_plan)
    data object History : Screen("history", R.string.tab_history)
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
                History.route -> History
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
    NavigationItemContent(Screen.Main, Icons.Default.Home),
    NavigationItemContent(Screen.Plan, Icons.Default.CalendarMonth),
    NavigationItemContent(Screen.History, Icons.Default.History),
    NavigationItemContent(Screen.Settings, Icons.Default.Settings)
)

@Composable
fun HrtTrackerNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    var addEntrySheetRequest by remember { mutableStateOf<AddEntrySheetRequest?>(null) }
    var quickAddGroupSheetVisible by remember { mutableStateOf(false) }
    var quickLogPlannedDoseRequest by remember { mutableStateOf<QuickLogPlannedDoseRequest?>(null) }
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
            NavigationBar {
                bottomNavItems.forEach { navItem ->
                    NavigationBarItem(
                        selected = selectedBottomScreen == navItem.screen,
                        onClick = {
                            val isOnChildOfSelectedTopLevel =
                                selectedBottomScreen == navItem.screen &&
                                    currentRoute != navItem.screen.route

                            if (isOnChildOfSelectedTopLevel) {
                                navController.popBackStack(navItem.screen.route, false)
                            } else if (currentDestination?.route != navItem.screen.route) {
                                navController.navigate(navItem.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
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
                    Column(
                        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small))
                    ) {
                        SmallFloatingActionButton(
                            onClick = {
                                quickAddGroupSheetVisible = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ViewList,
                                contentDescription = stringResource(R.string.fab_add_entry_from_group)
                            )
                        }

                        FloatingActionButton(
                            onClick = {
                                addEntrySheetRequest = AddEntrySheetRequest(entryIds = emptyList())
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.fab_add_entry)
                            )
                        }
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
            modifier = Modifier.consumeWindowInsets(innerPadding)
        ) {
            composable(Screen.Main.route) {
                MainScreen(
                    modifier.padding(innerPadding),
                    onQuickLogDoseClick = { groupId, scheduledAt, medicationDetails, medicationCount ->
                        if (medicationCount > 0) {
                            quickLogPlannedDoseRequest = QuickLogPlannedDoseRequest(
                                groupId = groupId,
                                scheduledFor = scheduledAt,
                                medicationDetails = medicationDetails,
                                medicationCount = medicationCount
                            )
                        }
                    }
                )
            }
            composable(Screen.Plan.route) {
                PlanScreen(
                    modifier = modifier.padding(innerPadding),
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
                            quickLogPlannedDoseRequest = QuickLogPlannedDoseRequest(
                                groupId = groupId,
                                scheduledFor = scheduledAt,
                                medicationDetails = medicationDetails,
                                medicationCount = medicationCount
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
                        navController.navigate(Screen.History.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    modifier = modifier.padding(innerPadding),
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
                    onGroupSaved = { navController.popBackStack() }
                )
            }
        }
    }

    addEntrySheetRequest?.let { request ->
        AddEntryScreen(
            entryIds = request.entryIds,
            onDismissRequest = { addEntrySheetRequest = null },
            onEntrySaved = { addEntrySheetRequest = null }
        )
    }

    if (quickAddGroupSheetVisible) {
        QuickAddMedicationGroupSheet(
            onDismissRequest = { quickAddGroupSheetVisible = false },
            onEntriesSaved = { quickAddGroupSheetVisible = false }
        )
    }

    quickLogPlannedDoseRequest?.let { request ->
        QuickLogPlannedDoseSheet(
            groupId = request.groupId,
            scheduledFor = request.scheduledFor,
            medicationDetails = request.medicationDetails,
            medicationCount = request.medicationCount,
            onDismissRequest = { quickLogPlannedDoseRequest = null },
            onEntriesSaved = { quickLogPlannedDoseRequest = null }
        )
    }
}

private const val TOP_LEVEL_PARENT_ARG = "topLevelParent"

private data class AddEntrySheetRequest(
    val entryIds: List<String>,
)

private data class QuickLogPlannedDoseRequest(
    val groupId: UUID,
    val scheduledFor: LocalDateTime,
    val medicationDetails: MedicationDetails,
    val medicationCount: Int,
)
