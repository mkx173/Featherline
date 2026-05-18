package com.mkx.hrttracker.data.repository

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mkx.hrttracker.model.bloodtest.AllowedAnalyteUnit
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestCatalog
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.settings.AppLanguageOption
import com.mkx.hrttracker.model.settings.AppLockGracePeriodOption
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.currentAppLocale
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

@Singleton
class SettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    internal constructor(
        context: Context,
        dataStore: DataStore<Preferences>,
    ) : this(context) {
        storedPreferencesOverride = dataStore
    }

    // Null in production (activeDataStore() is used); set by the internal test constructor before
    // storedPreferences is accessed.  Must be assigned before any flow property is first collected.
    @Volatile
    private var storedPreferencesOverride: DataStore<Preferences>? = null

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val appLockGracePeriodKey = stringPreferencesKey("app_lock_grace_period")
    private val calibrationDefaultUnitKeys = BloodAnalyteKey.entries.associateWith { analyteKey ->
        stringPreferencesKey("calibration_default_unit_${analyteKey.storageValue}")
    }
    private val homeE2DisplayUnitKey = stringPreferencesKey("home_e2_display_unit")
    private val homeE2ChartWindowKey = stringPreferencesKey("home_e2_chart_window")
    private val darkModeKey = stringPreferencesKey("dark_mode")
    private val adaptiveColorKey = booleanPreferencesKey("adaptive_color")
    private val remindersEnabledKey = booleanPreferencesKey("reminders_enabled")
    private val showArchivedGroupRecordsKey = booleanPreferencesKey("show_archived_group_records")
    private val hideReferenceRangesKey = booleanPreferencesKey("hide_reference_ranges")
    private val hideScreenContentKey = booleanPreferencesKey("hide_screen_content")
    private val screenLockProtectionKey = booleanPreferencesKey("screen_lock_protection")
    private val onboardingCompletedKey = booleanPreferencesKey("onboarding_completed")
    private val lastSeenTimeZoneIdKey = stringPreferencesKey("last_seen_time_zone_id")
    private val hideMedicationDetailsKey = booleanPreferencesKey("hide_medication_details")
    private val groupNameCounterKey = intPreferencesKey("group_name_counter")
    private val appLanguageOption = MutableStateFlow(resolveCurrentAppLanguage())

    private fun activeDataStore(): DataStore<Preferences> =
        storedPreferencesOverride ?: context.dataStore

    // Transient IOException (low memory, EBUSY during fsync) would otherwise tear
    // down the upstream combine. SupervisorJob protects sibling jobs but not the
    // inner pipeline, so a per-flow .catch is required.
    private val storedPreferences: Flow<Preferences> = flow {
        emitAll(activeDataStore().data)
    }.catch { cause ->
            if (cause is IOException) {
                emit(emptyPreferences())
            } else {
                throw cause
            }
        }

    val onboardingCompleted: Flow<Boolean> = storedPreferences
        .map { it[onboardingCompletedKey] ?: false }
        .distinctUntilChanged()

    // Raw DataStore-backed flow that intentionally bypasses [settingsState]'s
    // eager `initialValue` so consumers can distinguish the persisted option
    // from the SEVEN_DAYS placeholder used while DataStore is still loading.
    // Required by HomeSnapshotRepository's invalidation observer and by
    // HomeRepository's snapshot/fallback flows.
    val homeE2ChartWindowOptionFlow: Flow<HomeE2ChartWindowOption> = storedPreferences
        .map { preferences ->
            HomeE2ChartWindowOption.fromStorageValue(preferences[homeE2ChartWindowKey])
        }
        .distinctUntilChanged()

    val homeE2DisplayUnitFlow: Flow<AllowedAnalyteUnit> = storedPreferences
        .map { preferences -> resolveHomeE2DisplayUnit(preferences[homeE2DisplayUnitKey]) }
        .distinctUntilChanged()

    val settingsState: StateFlow<SettingsState> = combine(
        storedPreferences.map(::preferencesToStoredSettingsState),
        appLanguageOption
    ) { storedSettingsState, currentAppLanguageOption ->
        storedSettingsState.copy(appLanguageOption = currentAppLanguageOption)
    }
        .stateIn(
            scope = repositoryScope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsState(appLanguageOption = appLanguageOption.value)
        )

    suspend fun getCurrentSettings(): SettingsState {
        return preferencesToStoredSettingsState(activeDataStore().data.first())
            .copy(appLanguageOption = appLanguageOption.value)
    }

    suspend fun setDarkModeOption(option: DarkModeOption) {
        activeDataStore().edit { preferences ->
            preferences[darkModeKey] = option.name
        }
    }

    suspend fun setAdaptiveColorEnabled(enabled: Boolean) {
        activeDataStore().edit { preferences ->
            preferences[adaptiveColorKey] = enabled
        }
    }

    suspend fun setCalibrationDefaultUnit(choice: AllowedAnalyteUnit) {
        activeDataStore().edit { preferences ->
            val key = calibrationDefaultUnitKeys.getValue(choice.analyte)
            if (choice.unit == BloodTestCatalog.canonicalUnitFor(choice.analyte)) {
                preferences.remove(key)
            } else {
                preferences[key] = choice.unit.storageValue
            }
        }
    }

    suspend fun setHomeE2DisplayUnit(choice: AllowedAnalyteUnit) {
        require(choice.analyte == BloodAnalyteKey.E2) {
            "Home E2 display unit must reference analyte E2; got ${choice.analyte.storageValue}."
        }

        activeDataStore().edit { preferences ->
            if (choice.unit == BloodTestCatalog.canonicalUnitFor(BloodAnalyteKey.E2)) {
                preferences.remove(homeE2DisplayUnitKey)
            } else {
                preferences[homeE2DisplayUnitKey] = choice.unit.storageValue
            }
        }
    }

    suspend fun setHomeE2ChartWindowOption(option: HomeE2ChartWindowOption) {
        activeDataStore().edit { preferences ->
            if (option == HomeE2ChartWindowOption.SEVEN_DAYS) {
                preferences.remove(homeE2ChartWindowKey)
            } else {
                preferences[homeE2ChartWindowKey] = option.name
            }
        }
    }

    suspend fun setRemindersEnabled(enabled: Boolean) {
        activeDataStore().edit { preferences ->
            preferences[remindersEnabledKey] = enabled
        }
    }

    suspend fun setShowArchivedGroupRecords(enabled: Boolean) {
        activeDataStore().edit { preferences ->
            preferences[showArchivedGroupRecordsKey] = enabled
        }
    }

    suspend fun setHideReferenceRanges(enabled: Boolean) {
        activeDataStore().edit { preferences ->
            preferences[hideReferenceRangesKey] = enabled
        }
    }

    suspend fun setScreenLockProtectionEnabled(enabled: Boolean) {
        activeDataStore().edit { preferences ->
            preferences[screenLockProtectionKey] = enabled
        }
    }

    suspend fun setAppLockGracePeriodOption(option: AppLockGracePeriodOption) {
        activeDataStore().edit { preferences ->
            preferences[appLockGracePeriodKey] = option.name
        }
    }

    suspend fun setHideScreenContentEnabled(enabled: Boolean) {
        activeDataStore().edit { preferences ->
            preferences[hideScreenContentKey] = enabled
        }
    }

    suspend fun setHideMedicationDetails(hidden: Boolean) {
        activeDataStore().edit { preferences ->
            preferences[hideMedicationDetailsKey] = hidden
        }
    }

    suspend fun nextGroupNameIndex(): Int {
        var result = 0
        activeDataStore().edit { preferences ->
            val next = (preferences[groupNameCounterKey] ?: 0) + 1
            preferences[groupNameCounterKey] = next
            result = next
        }
        return result
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        activeDataStore().edit { preferences ->
            preferences[onboardingCompletedKey] = completed
        }
    }

    suspend fun acknowledgeTimeZone(zoneId: String) {
        activeDataStore().edit { preferences ->
            preferences[lastSeenTimeZoneIdKey] = zoneId
        }
    }

    suspend fun restoreSettings(
        darkModeOption: DarkModeOption,
        adaptiveColorEnabled: Boolean,
        remindersEnabled: Boolean,
        showArchivedGroupRecords: Boolean,
        hideReferenceRanges: Boolean,
        appLockGracePeriodOption: AppLockGracePeriodOption,
        hideScreenContentEnabled: Boolean,
        onboardingCompleted: Boolean,
        appLanguageOption: AppLanguageOption,
        calibrationDefaultUnits: Set<AllowedAnalyteUnit>,
        homeE2DisplayUnit: AllowedAnalyteUnit,
        homeE2ChartWindowOption: HomeE2ChartWindowOption,
        lastSeenTimeZoneId: String? = null,
        hideMedicationDetails: Boolean = false,
        groupNameCounter: Int = 0,
    ) {
        require(homeE2DisplayUnit.analyte == BloodAnalyteKey.E2) {
            "Home E2 display unit must reference analyte E2; got ${homeE2DisplayUnit.analyte.storageValue}."
        }

        activeDataStore().edit { preferences ->
            preferences[darkModeKey] = darkModeOption.name
            preferences[adaptiveColorKey] = adaptiveColorEnabled
            preferences[remindersEnabledKey] = remindersEnabled
            preferences[showArchivedGroupRecordsKey] = showArchivedGroupRecords
            preferences[hideReferenceRangesKey] = hideReferenceRanges
            preferences[appLockGracePeriodKey] = appLockGracePeriodOption.name
            preferences[hideScreenContentKey] = hideScreenContentEnabled
            preferences[onboardingCompletedKey] = onboardingCompleted

            calibrationDefaultUnitKeys.values.forEach(preferences::remove)
            calibrationDefaultUnits.forEach { choice ->
                if (choice.unit != BloodTestCatalog.canonicalUnitFor(choice.analyte)) {
                    preferences[calibrationDefaultUnitKeys.getValue(choice.analyte)] = choice.unit.storageValue
                }
            }
            if (homeE2DisplayUnit.unit == BloodTestCatalog.canonicalUnitFor(BloodAnalyteKey.E2)) {
                preferences.remove(homeE2DisplayUnitKey)
            } else {
                preferences[homeE2DisplayUnitKey] = homeE2DisplayUnit.unit.storageValue
            }

            if (homeE2ChartWindowOption == HomeE2ChartWindowOption.SEVEN_DAYS) {
                preferences.remove(homeE2ChartWindowKey)
            } else {
                preferences[homeE2ChartWindowKey] = homeE2ChartWindowOption.name
            }

            if (lastSeenTimeZoneId == null) {
                preferences.remove(lastSeenTimeZoneIdKey)
            } else {
                preferences[lastSeenTimeZoneIdKey] = lastSeenTimeZoneId
            }

            preferences[hideMedicationDetailsKey] = hideMedicationDetails
            preferences[groupNameCounterKey] = groupNameCounter
        }

        setAppLanguageOption(appLanguageOption)
    }

    fun setAppLanguageOption(option: AppLanguageOption) {
        appLanguageOption.value = option
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(option.languageTag))
    }

    fun refreshAppLanguageOption(sourceContext: Context = context) {
        appLanguageOption.value = AppLanguageOption.fromLocale(sourceContext.currentAppLocale())
    }

    private fun preferencesToStoredSettingsState(preferences: Preferences): SettingsState {
        return SettingsState(
            darkModeOption = DarkModeOption.fromStorageValue(preferences[darkModeKey]),
            adaptiveColorEnabled = preferences[adaptiveColorKey] ?: true,
            calibrationDefaultUnits = BloodAnalyteKey.entries.mapNotNull { analyteKey ->
                preferences[calibrationDefaultUnitKeys.getValue(analyteKey)]
                    ?.let(BloodUnitKey::fromStorageValue)
                    ?.takeIf { unit -> BloodTestCatalog.isUnitAllowed(analyteKey, unit) }
                    ?.let { unit -> analyteKey to unit }
            }.toMap(),
            homeE2DisplayUnit = resolveHomeE2DisplayUnit(preferences[homeE2DisplayUnitKey]).unit,
            homeE2ChartWindowOption = HomeE2ChartWindowOption
                .fromStorageValue(preferences[homeE2ChartWindowKey]),
            remindersEnabled = preferences[remindersEnabledKey] ?: true,
            showArchivedGroupRecords = preferences[showArchivedGroupRecordsKey] ?: true,
            hideReferenceRanges = preferences[hideReferenceRangesKey] ?: false,
            appLockGracePeriodOption = AppLockGracePeriodOption.fromStorageValue(
                preferences[appLockGracePeriodKey]
            ),
            hideScreenContentEnabled = preferences[hideScreenContentKey] ?: false,
            screenLockProtectionEnabled = preferences[screenLockProtectionKey] ?: false,
            lastSeenTimeZoneId = preferences[lastSeenTimeZoneIdKey],
            hideMedicationDetails = preferences[hideMedicationDetailsKey] ?: false,
            groupNameCounter = preferences[groupNameCounterKey] ?: 0,
        )
    }

    private fun resolveHomeE2DisplayUnit(storedValue: String?): AllowedAnalyteUnit {
        val unit = storedValue
            ?.let(BloodUnitKey::fromStorageValue)
            ?.takeIf { unit -> BloodTestCatalog.isUnitAllowed(BloodAnalyteKey.E2, unit) }
            ?: BloodTestCatalog.canonicalUnitFor(BloodAnalyteKey.E2)
        return AllowedAnalyteUnit.of(BloodAnalyteKey.E2, unit)
    }

    private fun resolveCurrentAppLanguage(): AppLanguageOption {
        return AppLanguageOption.fromLocale(context.currentAppLocale())
    }
}
