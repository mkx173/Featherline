package com.mkx.hrttracker.model.settings

import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestCatalog
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import java.util.Locale

enum class DarkModeOption {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK;

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

enum class AppLanguageOption(val languageTag: String) {
    ENGLISH(languageTag = "en"),
    SIMPLIFIED_CHINESE(languageTag = "zh-Hans");

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

enum class AppLockGracePeriodOption(val durationMillis: Long) {
    IMMEDIATELY(durationMillis = 0L),
    FIFTEEN_SECONDS(durationMillis = 15_000L),
    THIRTY_SECONDS(durationMillis = 30_000L),
    ONE_MINUTE(durationMillis = 60_000L),
    TWO_MINUTES(durationMillis = 2 * 60_000L),
    FIVE_MINUTES(durationMillis = 5 * 60_000L),
    FIFTEEN_MINUTES(durationMillis = 15 * 60_000L),
    THIRTY_MINUTES(durationMillis = 30 * 60_000L);

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
    val homeE2DisplayUnit: BloodUnitKey = BloodTestCatalog.canonicalUnitFor(BloodAnalyteKey.E2),
    val homeE2ChartWindowOption: HomeE2ChartWindowOption = HomeE2ChartWindowOption.SEVEN_DAYS,
    val remindersEnabled: Boolean = true,
    val showArchivedGroupRecords: Boolean = true,
    val hideReferenceRanges: Boolean = false,
    val screenLockProtectionEnabled: Boolean = false,
    val appLockGracePeriodOption: AppLockGracePeriodOption =
        AppLockGracePeriodOption.ONE_MINUTE,
    val hideScreenContentEnabled: Boolean = false,
    val lastSeenTimeZoneId: String? = null,
    val hideMedicationDetails: Boolean = false,
    val widgetContentScale: Float = 1.0f,
    val widgetBackgroundAlpha: Float = 1.0f,
    val widgetDarkModeOption: DarkModeOption = DarkModeOption.FOLLOW_SYSTEM,
    val groupNameCounter: Int = 0,
)

fun SettingsState.calibrationDefaultUnitFor(analyteKey: BloodAnalyteKey): BloodUnitKey {
    return calibrationDefaultUnits[analyteKey]
        ?.takeIf { unit -> BloodTestCatalog.isUnitAllowed(analyteKey, unit) }
        ?: BloodTestCatalog.canonicalUnitFor(analyteKey)
}
