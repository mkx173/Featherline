package com.mkx.hrttracker.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.mkx.hrttracker.ui.navigation.HrtTrackerNavHost

@Composable
fun HrtTrackerApp(navController: NavHostController = rememberNavController()) {
    HrtTrackerNavHost(navController = navController)
}
