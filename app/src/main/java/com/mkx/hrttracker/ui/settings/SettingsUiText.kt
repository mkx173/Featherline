package com.mkx.hrttracker.ui.settings

import androidx.annotation.StringRes
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.settings.AppLanguageOption
import com.mkx.hrttracker.model.settings.AppLockGracePeriodOption
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.model.settings.FirstDayOfWeekOption

@get:StringRes
val DarkModeOption.labelRes: Int
    get() = when (this) {
        DarkModeOption.FOLLOW_SYSTEM -> R.string.dark_mode_follow_system
        DarkModeOption.LIGHT -> R.string.dark_mode_always_off
        DarkModeOption.DARK -> R.string.dark_mode_always_on
    }

@get:StringRes
val AppLanguageOption.labelRes: Int
    get() = when (this) {
        AppLanguageOption.ENGLISH -> R.string.app_language_english
        AppLanguageOption.SIMPLIFIED_CHINESE -> R.string.app_language_simplified_chinese
        AppLanguageOption.TRADITIONAL_CHINESE -> R.string.app_language_traditional_chinese
        AppLanguageOption.CANTONESE -> R.string.app_language_cantonese
    }

@get:StringRes
val FirstDayOfWeekOption.menuLabelRes: Int
    get() = when (this) {
        FirstDayOfWeekOption.FOLLOW_SYSTEM -> R.string.first_day_of_week_follow_system
        FirstDayOfWeekOption.SATURDAY -> R.string.first_day_of_week_saturday
        FirstDayOfWeekOption.SUNDAY -> R.string.first_day_of_week_sunday
        FirstDayOfWeekOption.MONDAY -> R.string.first_day_of_week_monday
    }

@get:StringRes
val AppLockGracePeriodOption.labelRes: Int
    get() = when (this) {
        AppLockGracePeriodOption.IMMEDIATELY -> R.string.app_lock_grace_period_immediately
        AppLockGracePeriodOption.FIFTEEN_SECONDS -> R.string.app_lock_grace_period_15_seconds
        AppLockGracePeriodOption.THIRTY_SECONDS -> R.string.app_lock_grace_period_30_seconds
        AppLockGracePeriodOption.ONE_MINUTE -> R.string.app_lock_grace_period_1_minute
        AppLockGracePeriodOption.TWO_MINUTES -> R.string.app_lock_grace_period_2_minutes
        AppLockGracePeriodOption.FIVE_MINUTES -> R.string.app_lock_grace_period_5_minutes
        AppLockGracePeriodOption.FIFTEEN_MINUTES -> R.string.app_lock_grace_period_15_minutes
        AppLockGracePeriodOption.THIRTY_MINUTES -> R.string.app_lock_grace_period_30_minutes
    }
