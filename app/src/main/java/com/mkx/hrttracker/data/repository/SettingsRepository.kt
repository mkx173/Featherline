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
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mkx.hrttracker.model.bloodtest.AllowedAnalyteUnit
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestCatalog
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.home.HomeCardLayout
import com.mkx.hrttracker.model.home.HomeCardType
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.settings.AppLanguageOption
import com.mkx.hrttracker.model.settings.AppLockGracePeriodOption
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.model.settings.FirstDayOfWeekOption
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.appLanguageLocale
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
import java.util.Locale
import java.util.UUID
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
    private val pureBlackKey = booleanPreferencesKey("pure_black")
    private val cjkTextOffsetKey = booleanPreferencesKey("cjk_text_offset")
    private val hazeBlurKey = booleanPreferencesKey("haze_blur")
    private val remindersEnabledKey = booleanPreferencesKey("reminders_enabled")
    private val showArchivedGroupRecordsKey = booleanPreferencesKey("show_archived_group_records")
    private val hideReferenceRangesKey = booleanPreferencesKey("hide_reference_ranges")
    private val hideScreenContentKey = booleanPreferencesKey("hide_screen_content")
    private val screenLockProtectionKey = booleanPreferencesKey("screen_lock_protection")
    private val onboardingCompletedKey = booleanPreferencesKey("onboarding_completed")
    private val homeLowStockSectionExpandedKey =
        booleanPreferencesKey("home_low_stock_section_expanded")
    private val homeLowStockAcknowledgedWarningStatesKey =
        stringSetPreferencesKey("home_low_stock_acknowledged_warning_states")
    private val homeCardOrderKey = stringPreferencesKey("home_card_order")
    private val homeCardHiddenKey = stringSetPreferencesKey("home_card_hidden")
    private val stockNudgeEnabledKey = booleanPreferencesKey("stock_nudge_enabled")
    private val stockNudgeDismissCountKey = intPreferencesKey("stock_nudge_dismiss_count")
    private val stockNudgeUserEnabledKey = booleanPreferencesKey("stock_nudge_user_enabled")
    private val lastSeenTimeZoneIdKey = stringPreferencesKey("last_seen_time_zone_id")
    private val hideMedicationDetailsKey = booleanPreferencesKey("hide_medication_details")
    private val widgetContentScaleKey = floatPreferencesKey("widget_content_scale")
    private val widgetBackgroundAlphaKey = floatPreferencesKey("widget_background_alpha")
    private val widgetDarkModeKey = stringPreferencesKey("widget_dark_mode")
    private val groupNameCounterKey = intPreferencesKey("group_name_counter")
    private val firstDayOfWeekKey = stringPreferencesKey("first_day_of_week")
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

    val homeLowStockSectionExpandedFlow: Flow<Boolean> = storedPreferences
        .map { it[homeLowStockSectionExpandedKey] ?: true }
        .distinctUntilChanged()

    val homeCardLayoutFlow: Flow<HomeCardLayout> = storedPreferences
        .map { preferences ->
            val orderNames = preferences[homeCardOrderKey]
                ?.split(",")
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
            HomeCardLayout.decode(orderNames, preferences[homeCardHiddenKey].orEmpty())
        }
        .distinctUntilChanged()

    val homeLowStockAcknowledgedWarningStatesFlow: Flow<Map<String, MedicineStockState>> =
        storedPreferences
            .map { preferences ->
                decodeHomeLowStockAcknowledgedWarningStates(
                    preferences[homeLowStockAcknowledgedWarningStatesKey].orEmpty()
                )
            }
            .distinctUntilChanged()

    val stockNudgeEnabledFlow: Flow<Boolean> = storedPreferences
        .map { it[stockNudgeEnabledKey] ?: true }
        .distinctUntilChanged()

    // True once the user has explicitly switched the nudge on via the menu
    // toggle. While set, [recordStockNudgeDismissal] never auto-disables: a
    // voluntary opt-in outranks the dismiss-threshold policy. Persisted and
    // carried through backup/restore so the choice survives across devices.
    val stockNudgeUserEnabledFlow: Flow<Boolean> = storedPreferences
        .map { it[stockNudgeUserEnabledKey] ?: false }
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

    suspend fun setPureBlackEnabled(enabled: Boolean) {
        activeDataStore().edit { preferences ->
            preferences[pureBlackKey] = enabled
        }
    }

    suspend fun setCjkTextOffsetEnabled(enabled: Boolean) {
        activeDataStore().edit { preferences ->
            preferences[cjkTextOffsetKey] = enabled
        }
    }

    suspend fun setHazeBlurEnabled(enabled: Boolean) {
        activeDataStore().edit { preferences ->
            preferences[hazeBlurKey] = enabled
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

    suspend fun setHomeLowStockSectionFoldState(
        expanded: Boolean,
        acknowledgedWarningStates: Map<String, MedicineStockState> = emptyMap(),
    ) {
        activeDataStore().edit { preferences ->
            preferences[homeLowStockSectionExpandedKey] = expanded
            val encodedWarningStates = encodeHomeLowStockAcknowledgedWarningStates(
                acknowledgedWarningStates
            )
            if (encodedWarningStates.isEmpty()) {
                preferences.remove(homeLowStockAcknowledgedWarningStatesKey)
            } else {
                preferences[homeLowStockAcknowledgedWarningStatesKey] = encodedWarningStates
            }
        }
    }

    suspend fun setHomeCardLayout(order: List<HomeCardType>, hidden: Set<HomeCardType>) {
        activeDataStore().edit { preferences ->
            preferences[homeCardOrderKey] = order.joinToString(",") { it.name }
            preferences[homeCardHiddenKey] = hidden.map { it.name }.toSet()
        }
    }

    suspend fun clearHomeLowStockAcknowledgedWarningStates() {
        activeDataStore().edit { preferences ->
            preferences.remove(homeLowStockAcknowledgedWarningStatesKey)
        }
    }

    suspend fun setStockNudgeEnabled(enabled: Boolean) {
        activeDataStore().edit { preferences ->
            preferences[stockNudgeEnabledKey] = enabled
            if (enabled) {
                preferences.remove(stockNudgeDismissCountKey)
                // Switching the nudge on via the menu is a voluntary opt-in;
                // record it so the dismiss-threshold policy stops auto-disabling
                // it. Sticky once set — re-asserted on every enable, untouched on
                // disable (it's moot while the nudge is off).
                preferences[stockNudgeUserEnabledKey] = true
            }
        }
    }

    /**
     * Atomically records an explicit nudge dismissal and, when the running count
     * reaches [dismissLimit], disables the nudge in the same edit. Returns true iff
     * this dismissal crossed the threshold (just disabled the nudge), so the caller
     * can fire a one-shot notice. Combining both writes in a single edit avoids a
     * torn state — count persisted but the disable lost to process death — which
     * would otherwise leave the nudge unable to ever auto-disable.
     *
     * Once the user has voluntarily enabled the nudge ([stockNudgeUserEnabledKey]),
     * the threshold policy is suppressed entirely: the dismissal is a no-op here
     * (the caller still hides the current nudge), and it can never auto-disable.
     */
    suspend fun recordStockNudgeDismissal(dismissLimit: Int): Boolean {
        var justDisabled = false
        activeDataStore().edit { preferences ->
            if (preferences[stockNudgeUserEnabledKey] == true) return@edit
            val next = (preferences[stockNudgeDismissCountKey] ?: 0) + 1
            preferences[stockNudgeDismissCountKey] = next
            if (next == dismissLimit) {
                preferences[stockNudgeEnabledKey] = false
                justDisabled = true
            }
        }
        return justDisabled
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

    suspend fun setFirstDayOfWeekOption(option: FirstDayOfWeekOption) {
        activeDataStore().edit { preferences ->
            if (option == FirstDayOfWeekOption.FOLLOW_SYSTEM) {
                preferences.remove(firstDayOfWeekKey)
            } else {
                preferences[firstDayOfWeekKey] = option.name
            }
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

    // Legacy widget-appearance keys, retained ONLY for the one-time migration into
    // WidgetAppearanceStore (WidgetAppearanceRepository.migrateFromLegacySettingsIfNeeded).
    // Returns null once the keys are gone, which ends the migration permanently.
    suspend fun readLegacyWidgetAppearance(): LegacyWidgetAppearance? {
        val preferences = activeDataStore().data.first()
        val scale = preferences[widgetContentScaleKey]
        val alpha = preferences[widgetBackgroundAlphaKey]
        val dark = preferences[widgetDarkModeKey]
        if (scale == null && alpha == null && dark == null) return null
        return LegacyWidgetAppearance(
            contentScale = scale ?: 1.0f,
            backgroundAlpha = (alpha ?: 1.0f).coerceIn(0.5f, 1.0f),
            darkMode = DarkModeOption.fromStorageValue(dark),
        )
    }

    suspend fun clearLegacyWidgetAppearanceKeys() {
        activeDataStore().edit { preferences ->
            preferences.remove(widgetContentScaleKey)
            preferences.remove(widgetBackgroundAlphaKey)
            preferences.remove(widgetDarkModeKey)
        }
    }

    suspend fun peekNextGroupNameIndex(): Int {
        return (activeDataStore().data.first()[groupNameCounterKey] ?: 0) + 1
    }

    suspend fun consumeNextGroupNameIndex(): Int {
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
        pureBlackEnabled: Boolean,
        cjkTextOffsetEnabled: Boolean = false,
        hazeBlurEnabled: Boolean = true,
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
        firstDayOfWeekOption: FirstDayOfWeekOption = FirstDayOfWeekOption.FOLLOW_SYSTEM,
        stockNudgeEnabled: Boolean = true,
        stockNudgeUserEnabled: Boolean = false,
        homeCardLayout: HomeCardLayout = HomeCardLayout(),
    ) {
        require(homeE2DisplayUnit.analyte == BloodAnalyteKey.E2) {
            "Home E2 display unit must reference analyte E2; got ${homeE2DisplayUnit.analyte.storageValue}."
        }

        activeDataStore().edit { preferences ->
            preferences[darkModeKey] = darkModeOption.name
            preferences[adaptiveColorKey] = adaptiveColorEnabled
            preferences[pureBlackKey] = pureBlackEnabled
            preferences[cjkTextOffsetKey] = cjkTextOffsetEnabled
            preferences[hazeBlurKey] = hazeBlurEnabled
            preferences[remindersEnabledKey] = remindersEnabled
            preferences[showArchivedGroupRecordsKey] = showArchivedGroupRecords
            preferences[hideReferenceRangesKey] = hideReferenceRanges
            preferences[appLockGracePeriodKey] = appLockGracePeriodOption.name
            preferences[hideScreenContentKey] = hideScreenContentEnabled
            preferences[onboardingCompletedKey] = onboardingCompleted

            calibrationDefaultUnitKeys.values.forEach(preferences::remove)
            calibrationDefaultUnits.forEach { choice ->
                if (choice.unit != BloodTestCatalog.canonicalUnitFor(choice.analyte)) {
                    preferences[calibrationDefaultUnitKeys.getValue(choice.analyte)] =
                        choice.unit.storageValue
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

            if (firstDayOfWeekOption == FirstDayOfWeekOption.FOLLOW_SYSTEM) {
                preferences.remove(firstDayOfWeekKey)
            } else {
                preferences[firstDayOfWeekKey] = firstDayOfWeekOption.name
            }
            preferences[stockNudgeEnabledKey] = stockNudgeEnabled
            preferences[stockNudgeUserEnabledKey] = stockNudgeUserEnabled
            preferences.remove(stockNudgeDismissCountKey)

            preferences.remove(homeLowStockAcknowledgedWarningStatesKey)

            preferences[homeCardOrderKey] = homeCardLayout.order.joinToString(",") { it.name }
            preferences[homeCardHiddenKey] = homeCardLayout.hidden.map { it.name }.toSet()
        }

        setAppLanguageOption(appLanguageOption)
    }

    fun setAppLanguageOption(option: AppLanguageOption) {
        appLanguageOption.value = option
        val locale = Locale.forLanguageTag(option.languageTag)
        // Number formatters (e.g. medicationDoseText) read Locale.getDefault() directly,
        // so syncing it here keeps formatted doses aligned with the chosen UI language.
        Locale.setDefault(locale)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(option.languageTag))
    }

    fun refreshAppLanguageOption() {
        appLanguageOption.value = AppLanguageOption.fromLocale(appLanguageLocale())
    }

    private fun preferencesToStoredSettingsState(preferences: Preferences): SettingsState {
        return SettingsState(
            darkModeOption = DarkModeOption.fromStorageValue(preferences[darkModeKey]),
            adaptiveColorEnabled = preferences[adaptiveColorKey] ?: true,
            pureBlackEnabled = preferences[pureBlackKey] ?: false,
            cjkTextOffsetEnabled = preferences[cjkTextOffsetKey] ?: false,
            hazeBlurEnabled = preferences[hazeBlurKey] ?: true,
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
            firstDayOfWeekOption = FirstDayOfWeekOption.fromStorageValue(preferences[firstDayOfWeekKey]),
        )
    }

    private fun encodeHomeLowStockAcknowledgedWarningStates(
        acknowledgedWarningStates: Map<String, MedicineStockState>,
    ): Set<String> {
        return acknowledgedWarningStates.mapNotNull { (uuid, state) ->
            val canonicalUuid = uuid.toCanonicalUuidOrNull() ?: return@mapNotNull null
            state.takeIf { warningState -> warningState.isHomeLowStockWarningState() }
                ?.let { warningState -> "$canonicalUuid|${warningState.name}" }
        }.toSet()
    }

    private fun decodeHomeLowStockAcknowledgedWarningStates(
        encodedWarningStates: Set<String>,
    ): Map<String, MedicineStockState> {
        return encodedWarningStates.mapNotNull { encodedValue ->
            val separatorIndex = encodedValue.indexOf('|')
            if (separatorIndex <= 0 || separatorIndex == encodedValue.lastIndex) {
                return@mapNotNull null
            }
            val uuid = encodedValue
                .substring(startIndex = 0, endIndex = separatorIndex)
                .toCanonicalUuidOrNull()
                ?: return@mapNotNull null
            val state = runCatching {
                MedicineStockState.valueOf(encodedValue.substring(startIndex = separatorIndex + 1))
            }.getOrNull()
                ?.takeIf { state -> state.isHomeLowStockWarningState() }
                ?: return@mapNotNull null

            uuid to state
        }.toMap()
    }

    private fun String.toCanonicalUuidOrNull(): String? {
        return runCatching { UUID.fromString(this).toString() }.getOrNull()
    }

    private fun MedicineStockState.isHomeLowStockWarningState(): Boolean {
        return this == MedicineStockState.USER_LOW ||
                this == MedicineStockState.IMMINENT ||
                this == MedicineStockState.OUT
    }

    private fun resolveHomeE2DisplayUnit(storedValue: String?): AllowedAnalyteUnit {
        val unit = storedValue
            ?.let(BloodUnitKey::fromStorageValue)
            ?.takeIf { unit -> BloodTestCatalog.isUnitAllowed(BloodAnalyteKey.E2, unit) }
            ?: BloodTestCatalog.canonicalUnitFor(BloodAnalyteKey.E2)
        return AllowedAnalyteUnit.of(BloodAnalyteKey.E2, unit)
    }

    private fun resolveCurrentAppLanguage(): AppLanguageOption {
        return AppLanguageOption.fromLocale(appLanguageLocale())
    }
}

data class LegacyWidgetAppearance(
    val contentScale: Float,
    val backgroundAlpha: Float,
    val darkMode: DarkModeOption,
)
