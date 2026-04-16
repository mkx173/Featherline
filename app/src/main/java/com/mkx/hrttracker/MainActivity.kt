package com.mkx.hrttracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.ui.HrtTrackerApp
import com.mkx.hrttracker.ui.settings.SettingsViewModel
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme

class MainActivity : ComponentActivity() {
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        settingsRepository = (application as HrtTrackerApplication).appContainer.settingsRepository
        val initialSettings = settingsRepository.settingsState.value

        AppCompatDelegate.setDefaultNightMode(
            when (initialSettings.darkModeOption) {
                DarkModeOption.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                DarkModeOption.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                DarkModeOption.FOLLOW_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )

        splashScreen.setKeepOnScreenCondition { false }
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)

        setContent {
            val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
            val settingsState by viewModel.settingsState.collectAsState()

            val isDarkTheme = when (settingsState.darkModeOption) {
                DarkModeOption.DARK -> true
                DarkModeOption.LIGHT -> false
                DarkModeOption.FOLLOW_SYSTEM -> isSystemInDarkTheme()
            }

            DisposableEffect(isDarkTheme) {
                val barStyle = if (isDarkTheme) {
                    SystemBarStyle.dark(Color.Transparent.toArgb())
                } else {
                    SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb())
                }

                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle
                )

                val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
                windowInsetsController.isAppearanceLightStatusBars = !isDarkTheme
                windowInsetsController.isAppearanceLightNavigationBars = !isDarkTheme
                onDispose { }
            }

            HrtTrackerTheme(
                darkTheme = isDarkTheme,
                dynamicColor = settingsState.adaptiveColorEnabled
            ) {
                HrtTrackerApp()
            }
        }
    }
}
