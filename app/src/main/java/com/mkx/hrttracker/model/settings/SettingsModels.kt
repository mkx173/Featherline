package com.mkx.hrttracker.model.settings

import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestCatalog
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
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
    val classicNavigationBarEnabled: Boolean = false,
    // The language shown; with appLanguageFollowsSystem, the phone's mapped to a supported one.
    val appLanguageOption: AppLanguageOption = AppLanguageOption.ENGLISH,
    // No language chosen in the app or in Android's per-app setting (its app locales are empty).
    val appLanguageFollowsSystem: Boolean = false,
    val firstDayOfWeekOption: FirstDayOfWeekOption = FirstDayOfWeekOption.FOLLOW_SYSTEM,
    val calibrationDefaultUnits: Map<BloodAnalyteKey, BloodUnitKey> = emptyMap(),
    val homeE2DisplayUnit: BloodUnitKey = BloodTestCatalog.canonicalUnitFor(BloodAnalyteKey.E2),
    val homeE2ChartWindowOption: HomeE2ChartWindowOption = HomeE2ChartWindowOption.SEVEN_DAYS,
    val remindersEnabled: Boolean = true,
    val reminderRepeats: ReminderRepeats = ReminderRepeats(),
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
)

/** The app-lock and screen-privacy settings as last read from disk, for consumers that fail closed. */
sealed interface SecuritySettingsState {
    data object Loading : SecuritySettingsState

    /** The last read failed; a retry is pending. */
    data object Error : SecuritySettingsState

    data class Loaded(
        val screenLockProtectionEnabled: Boolean,
        val appLockGracePeriodOption: AppLockGracePeriodOption,
        val hideScreenContentEnabled: Boolean,
    ) : SecuritySettingsState
}

fun SettingsState.calibrationDefaultUnitFor(analyteKey: BloodAnalyteKey): BloodUnitKey {
    return calibrationDefaultUnits[analyteKey]
        ?.takeIf { unit -> BloodTestCatalog.isUnitAllowed(analyteKey, unit) }
        ?: BloodTestCatalog.canonicalUnitFor(analyteKey)
}

/**
 * How often a dose reminder rings while its dose is unlogged: at the dose time, then [count] more
 * times, [intervalMinutes] apart. One setting for every plan, so plans that share a dose time
 * share their alert times too.
 */
data class ReminderRepeats(
    val count: Int = DEFAULT_COUNT,
    val intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES,
) {
    /** From the dose time to its last alert. */
    val spanMinutes: Long get() = count.toLong() * intervalMinutes

    fun isValid(): Boolean = count in COUNTS && intervalMinutes in INTERVALS_MINUTES

    /**
     * An interval under 15 minutes as 15, the shortest the wheel offers: earlier builds offered
     * 5 and 10. The count stays. Read from the store and from a backup.
     */
    fun withShortIntervalRaised(): ReminderRepeats =
        if (intervalMinutes < INTERVALS_MINUTES.first()) copy(intervalMinutes = INTERVALS_MINUTES.first()) else this

    companion object {
        const val DEFAULT_COUNT = 3
        const val DEFAULT_INTERVAL_MINUTES = 15

        /** 0 is Off: the dose rings once. */
        val COUNTS: List<Int> = (0..4).toList()
        // From 15: in Doze, Android lets an app's alarms go off about once per 15 minutes.
        val INTERVALS_MINUTES: List<Int> = (15..60 step 5).toList()
    }
}
