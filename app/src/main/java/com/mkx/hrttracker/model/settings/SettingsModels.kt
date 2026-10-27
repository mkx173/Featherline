package com.mkx.hrttracker.model.settings

import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestCatalog
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.util.MedicationDayBoundary
import java.time.DayOfWeek
import java.time.temporal.WeekFields
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
    SIMPLIFIED_CHINESE(languageTag = "zh-Hans"),
    TRADITIONAL_CHINESE(languageTag = "zh-Hant"),
    CANTONESE(languageTag = "yue-Hant-HK");

    companion object {
        fun fromLocale(locale: Locale): AppLanguageOption {
            return when {
                locale.language == "yue" -> CANTONESE
                locale.language != "zh" -> ENGLISH
                locale.script == "Hans" -> SIMPLIFIED_CHINESE
                locale.script == "Hant" ||
                    locale.country == "HK" ||
                    locale.country == "MO" ||
                    locale.country == "TW" ->
                    TRADITIONAL_CHINESE
                else -> SIMPLIFIED_CHINESE
            }
        }
    }
}

enum class FirstDayOfWeekOption {
    FOLLOW_SYSTEM,
    SATURDAY,
    SUNDAY,
    MONDAY;

    fun resolve(systemLocale: Locale): DayOfWeek = when (this) {
        FOLLOW_SYSTEM -> WeekFields.of(systemLocale).firstDayOfWeek
        SATURDAY -> DayOfWeek.SATURDAY
        SUNDAY -> DayOfWeek.SUNDAY
        MONDAY -> DayOfWeek.MONDAY
    }

    companion object {
        fun fromStorageValue(value: String?): FirstDayOfWeekOption {
            return entries.firstOrNull { it.name == value } ?: FOLLOW_SYSTEM
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
    val pureBlackEnabled: Boolean = false,
    val hazeBlurEnabled: Boolean = true,
    val appLanguageOption: AppLanguageOption = AppLanguageOption.ENGLISH,
    val medicationDayEndsAtFourAm: Boolean = false,
    val firstDayOfWeekOption: FirstDayOfWeekOption = FirstDayOfWeekOption.FOLLOW_SYSTEM,
    val calibrationDefaultUnits: Map<BloodAnalyteKey, BloodUnitKey> = emptyMap(),
    val homeE2DisplayUnit: BloodUnitKey = BloodTestCatalog.canonicalUnitFor(BloodAnalyteKey.E2),
    val homeE2ChartWindowOption: HomeE2ChartWindowOption = HomeE2ChartWindowOption.SEVEN_DAYS,
    val remindersEnabled: Boolean = true,
    // Intent flag: the user wants weight synced with Health Connect. Sync also
    // needs Health Connect access (HealthConnectCapabilityReconciler).
    val healthConnectWeightSyncEnabled: Boolean = false,
    val showArchivedGroupRecords: Boolean = true,
    val hideReferenceRanges: Boolean = false,
    val screenLockProtectionEnabled: Boolean = false,
    val appLockGracePeriodOption: AppLockGracePeriodOption =
        AppLockGracePeriodOption.ONE_MINUTE,
    val hideScreenContentEnabled: Boolean = false,
    val lastSeenTimeZoneId: String? = null,
    val hideMedicationDetails: Boolean = false,
    val groupNameCounter: Int = 0,
) {
    val medicationDayBoundary: MedicationDayBoundary
        get() = MedicationDayBoundary.fromSwitch(medicationDayEndsAtFourAm)
}

fun SettingsState.calibrationDefaultUnitFor(analyteKey: BloodAnalyteKey): BloodUnitKey {
    return calibrationDefaultUnits[analyteKey]
        ?.takeIf { unit -> BloodTestCatalog.isUnitAllowed(analyteKey, unit) }
        ?: BloodTestCatalog.canonicalUnitFor(analyteKey)
}
