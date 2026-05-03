package com.mkx.hrttracker.model.settings

import android.app.UiModeManager
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestCatalog
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import java.util.Locale

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

enum class AppLanguageOption(
    @get:StringRes val labelRes: Int,
    val languageTag: String,
) {
    ENGLISH(
        labelRes = R.string.app_language_english,
        languageTag = "en"
    ),
    SIMPLIFIED_CHINESE(
        labelRes = R.string.app_language_simplified_chinese,
        languageTag = "zh-Hans"
    );

    companion object {
        fun fromLocale(locale: Locale): AppLanguageOption {
            return if (locale.language == "zh") {
                SIMPLIFIED_CHINESE
            } else {
                ENGLISH
            }
        }
    }
}

enum class AppLockGracePeriodOption(
    @get:StringRes val labelRes: Int,
    val durationMillis: Long,
) {
    IMMEDIATELY(
        labelRes = R.string.app_lock_grace_period_immediately,
        durationMillis = 0L
    ),
    FIFTEEN_SECONDS(
        labelRes = R.string.app_lock_grace_period_15_seconds,
        durationMillis = 15_000L
    ),
    THIRTY_SECONDS(
        labelRes = R.string.app_lock_grace_period_30_seconds,
        durationMillis = 30_000L
    ),
    ONE_MINUTE(
        labelRes = R.string.app_lock_grace_period_1_minute,
        durationMillis = 60_000L
    ),
    TWO_MINUTES(
        labelRes = R.string.app_lock_grace_period_2_minutes,
        durationMillis = 2 * 60_000L
    ),
    FIVE_MINUTES(
        labelRes = R.string.app_lock_grace_period_5_minutes,
        durationMillis = 5 * 60_000L
    ),
    FIFTEEN_MINUTES(
        labelRes = R.string.app_lock_grace_period_15_minutes,
        durationMillis = 15 * 60_000L
    ),
    THIRTY_MINUTES(
        labelRes = R.string.app_lock_grace_period_30_minutes,
        durationMillis = 30 * 60_000L
    );

    fun shouldRelock(elapsedSinceLeavingMillis: Long): Boolean {
        return durationMillis == 0L || elapsedSinceLeavingMillis >= durationMillis
    }

    companion object {
        fun fromStorageValue(value: String?): AppLockGracePeriodOption {
            return entries.firstOrNull { it.name == value } ?: ONE_MINUTE
        }
    }
}

data class SettingsState(
    val darkModeOption: DarkModeOption = DarkModeOption.FOLLOW_SYSTEM,
    val adaptiveColorEnabled: Boolean = true,
    val appLanguageOption: AppLanguageOption = AppLanguageOption.ENGLISH,
    val calibrationDefaultUnits: Map<BloodAnalyteKey, BloodUnitKey> = emptyMap(),
    val remindersEnabled: Boolean = true,
    val showArchivedGroupRecords: Boolean = true,
    val screenLockProtectionEnabled: Boolean = false,
    val appLockGracePeriodOption: AppLockGracePeriodOption =
        AppLockGracePeriodOption.ONE_MINUTE,
    val hideScreenContentEnabled: Boolean = false,
)

fun SettingsState.calibrationDefaultUnitFor(analyteKey: BloodAnalyteKey): BloodUnitKey {
    return calibrationDefaultUnits[analyteKey]
        ?.takeIf { unit -> BloodTestCatalog.isUnitAllowed(analyteKey, unit) }
        ?: BloodTestCatalog.canonicalUnitFor(analyteKey)
}
