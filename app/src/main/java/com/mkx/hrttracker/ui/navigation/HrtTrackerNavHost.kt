package com.mkx.hrttracker.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.main.MainScreen
import com.mkx.hrttracker.ui.settings.SettingsScreen

sealed class Screen(val route: String, @StringRes val label: Int) {
    data object Main : Screen("main", R.string.tab_main)
    data object Settings : Screen("settings", R.string.tab_settings)
}

private data class NavigationItemContent(
    val screen: Screen,
    val icon: ImageVector,
)

private val bottomNavItems = listOf(
    NavigationItemContent(Screen.Main, Icons.Default.Home),
    NavigationItemContent(Screen.Settings, Icons.Default.Settings)
)

@Composable
fun HrtTrackerNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { navItem ->
                    NavigationBarItem(
                        selected = currentDestination
                            ?.hierarchy
                            ?.any { it.route == navItem.screen.route } == true,
                        onClick = {
                            if (currentDestination?.route != navItem.screen.route) {
                                navController.navigate(navItem.screen.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = navItem.icon,
                                contentDescription = null
                            )
                        }
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
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
