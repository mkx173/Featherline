package com.mkx.hrttracker.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.mkx.hrttracker.ui.navigation.HrtTrackerNavHost

@Composable
fun HrtTrackerApp(navController: NavHostController) {
    HrtTrackerNavHost(navController = navController)
}
