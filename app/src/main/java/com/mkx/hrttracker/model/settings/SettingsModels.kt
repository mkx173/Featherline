package com.mkx.hrttracker.model.settings

enum class DarkModeOption {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK
}

data class SettingsState(
    val darkModeOption: DarkModeOption = DarkModeOption.FOLLOW_SYSTEM,
    val adaptiveColorEnabled: Boolean = true,
)
