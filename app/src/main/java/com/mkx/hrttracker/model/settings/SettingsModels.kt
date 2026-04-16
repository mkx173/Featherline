package com.mkx.hrttracker.model.settings

import android.app.UiModeManager
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import com.mkx.hrttracker.R

enum class DarkModeOption(
    @get:StringRes val labelRes: Int,
    val appCompatNightMode: Int,
    val applicationNightMode: Int,
) {
    FOLLOW_SYSTEM(
        labelRes = R.string.dark_mode_follow_system,
        appCompatNightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
        applicationNightMode = UiModeManager.MODE_NIGHT_AUTO
    ),
    LIGHT(
        labelRes = R.string.dark_mode_always_off,
        appCompatNightMode = AppCompatDelegate.MODE_NIGHT_NO,
        applicationNightMode = UiModeManager.MODE_NIGHT_NO
    ),
    DARK(
        labelRes = R.string.dark_mode_always_on,
        appCompatNightMode = AppCompatDelegate.MODE_NIGHT_YES,
        applicationNightMode = UiModeManager.MODE_NIGHT_YES
    );

    fun resolveDarkTheme(isSystemInDarkTheme: Boolean): Boolean = when (this) {
        FOLLOW_SYSTEM -> isSystemInDarkTheme
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStorageValue(value: String?): DarkModeOption {
            return entries.firstOrNull { it.name == value } ?: FOLLOW_SYSTEM
        }
    }
}

data class SettingsState(
    val darkModeOption: DarkModeOption = DarkModeOption.FOLLOW_SYSTEM,
    val adaptiveColorEnabled: Boolean = true,
)
