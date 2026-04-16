package com.mkx.hrttracker

import android.app.Application
import android.app.UiModeManager
import androidx.appcompat.app.AppCompatDelegate
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.util.ToastManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class HrtTrackerApplication : Application() {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()

        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        applicationScope.launch {
            applyDarkMode(settingsRepository.getCurrentSettings().darkModeOption)
            settingsRepository.settingsState.collect { settings ->
                applyDarkMode(settings.darkModeOption)
            }
        }
        ToastManager.init(this)
    }

    private fun applyDarkMode(option: DarkModeOption) {
        AppCompatDelegate.setDefaultNightMode(option.appCompatNightMode)
        getSystemService(UiModeManager::class.java).setApplicationNightMode(option.applicationNightMode)
    }
}
