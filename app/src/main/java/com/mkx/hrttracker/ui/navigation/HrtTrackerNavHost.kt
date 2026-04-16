package com.mkx.hrttracker.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.mkx.hrttracker.ui.history.HistoryScreen
import com.mkx.hrttracker.ui.log.AddEntryScreen
import com.mkx.hrttracker.ui.log.AddEntryViewModel
import com.mkx.hrttracker.ui.main.MainScreen
import com.mkx.hrttracker.ui.settings.SettingsScreen

sealed class Screen(val route: String, @get:StringRes val label: Int) {
    data object Main : Screen("main", R.string.tab_main)
    data object History : Screen("history", R.string.tab_history)
    data object Settings : Screen("settings", R.string.tab_settings)
    data object AddEntry : Screen(
        "add_entry?" +
            "${AddEntryViewModel.ENTRY_ID_ARG}={${AddEntryViewModel.ENTRY_ID_ARG}}" +
            "&$TOP_LEVEL_PARENT_ARG={$TOP_LEVEL_PARENT_ARG}",
        R.string.add_entry
    ) {
        const val baseRoute = "add_entry"

        fun createRoute(
            topLevelParentRoute: String,
            entryId: String? = null
        ): String {
            return if (entryId == null) {
                "$baseRoute?$TOP_LEVEL_PARENT_ARG=$topLevelParentRoute"
            } else {
                "$baseRoute?${AddEntryViewModel.ENTRY_ID_ARG}=$entryId&$TOP_LEVEL_PARENT_ARG=$topLevelParentRoute"
            }
        }
    }

    companion object {
        fun topLevelScreenForRoute(route: String?): Screen? {
            return when (route) {
                Main.route -> Main
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
    NavigationItemContent(Screen.History, Icons.Default.History),
    NavigationItemContent(Screen.Settings, Icons.Default.Settings)
)

@Composable
fun HrtTrackerNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
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
            if (currentRoute == Screen.Main.route) {
                FloatingActionButton(
                    onClick = {
                        navController.navigate(
                            Screen.AddEntry.createRoute(topLevelParentRoute = Screen.Main.route)
                        )
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.fab_add_entry)
                    )
                }
            }
        },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.statusBars)
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Main.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Main.route) {
                MainScreen()
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    onEntryClick = { entryId ->
                        navController.navigate(
                            Screen.AddEntry.createRoute(
                                topLevelParentRoute = Screen.History.route,
                                entryId = entryId.toString()
                            )
                        )
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
            composable(
                route = Screen.AddEntry.route,
                arguments = listOf(
                    navArgument(AddEntryViewModel.ENTRY_ID_ARG) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument(TOP_LEVEL_PARENT_ARG) {
                        type = NavType.StringType
                        defaultValue = Screen.Main.route
                    }
                )
            ) {
                AddEntryScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onEntrySaved = {
                        navController.popBackStack()
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
        }
    }
}

private const val TOP_LEVEL_PARENT_ARG = "topLevelParent"
