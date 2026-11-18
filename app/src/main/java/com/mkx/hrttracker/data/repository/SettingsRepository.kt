package com.mkx.hrttracker.data.repository

import android.content.Context
import android.os.Build
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
import com.mkx.hrttracker.model.settings.ReminderRepeats
import com.mkx.hrttracker.model.settings.SecuritySettingsState
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import com.mkx.hrttracker.util.appLanguageLocale
import com.mkx.hrttracker.util.appLocales
import com.mkx.hrttracker.util.systemAppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val SECURITY_SETTINGS_RETRY_DELAY_MILLIS = 1_000L
private const val TAG = "SettingsRepository"

private val Context.dataStore by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

@Singleton
class SettingsRepository internal constructor(
    private val preferencesDataStore: DataStore<Preferences>,
    private val diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        diagnosticsLogger: AppDiagnosticsLogger = AppDiagnosticsLogger(),
    ) : this(context.dataStore, diagnosticsLogger)

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
    private val hazeBlurKey = booleanPreferencesKey("haze_blur")
    private val classicNavigationBarKey = booleanPreferencesKey("classic_navigation_bar")
    private val remindersEnabledKey = booleanPreferencesKey("reminders_enabled")
    private val reminderRepeatCountKey = intPreferencesKey("reminder_repeat_count")
    private val reminderRepeatIntervalKey = intPreferencesKey("reminder_repeat_interval_minutes")
    private val healthConnectWeightSyncEnabledKey =
        booleanPreferencesKey("health_connect_weight_sync_enabled")
    private val showArchivedGroupRecordsKey = booleanPreferencesKey("show_archived_group_records")
    private val hideReferenceRangesKey = booleanPreferencesKey("hide_reference_ranges")
    private val hideScreenContentKey = booleanPreferencesKey("hide_screen_content")
    private val screenLockProtectionKey = booleanPreferencesKey("screen_lock_protection")
    private val onboardingCompletedKey = booleanPreferencesKey("onboarding_completed")
    private val pkCalibrationIntroSeenKey = booleanPreferencesKey("pk_calibration_intro_seen")
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
    // The language last shown: the chosen one, or with app_language_follows_system the phone's,
    // mapped. The flag tells a process no activity has attached to below API 33 which it is.
    private val appLanguageTagKey = stringPreferencesKey("app_language_tag")
    private val appLanguageFollowsSystemKey = booleanPreferencesKey("app_language_follows_system")

    /**
     * [known]: below API 33 AppCompat reads the chosen language from its storage only when an
     * activity attaches, so until then (a process an alarm woke) an empty list says nothing.
     * From API 33 [appLocales] reads the framework's, with or without an activity.
     */
    private data class AppLanguage(
        val shown: AppLanguageOption,
        val followsSystem: Boolean,
        val known: Boolean,
    )

    private val appLanguage = MutableStateFlow(
        AppLanguage(
            shown = AppLanguageOption.fromLocale(appLanguageLocale()),
            followsSystem = appLocales().isEmpty,
            known = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
        )
    )

    /** The language shown, from the moment it is known (see [AppLanguage.known]). */
    val knownAppLanguage: Flow<AppLanguageOption> =
        appLanguage.filter { it.known }.map { it.shown }

    internal val isAppLanguageKnown: Boolean get() = appLanguage.value.known

    // From settingsState's first read, started at construction (Application.onCreate), before
    // the activity's first refresh stores this run's language. A failed read gives null.
    private val lastRunAppLanguageTagRead = CompletableDeferred<String?>()

    // ponytail: the stored choice as of getCurrentSettings (null when following the system), so a
    // notification can read it without suspending; every reminder path reads the settings first.
    @Volatile
    private var storedChosenAppLanguageTag: String? = null

    // The language (tag, follows system) last set in this process, which a read never overwrites.
    @Volatile
    private var requestedAppLanguage: Pair<String, Boolean>? = null

    private fun activeDataStore(): DataStore<Preferences> = preferencesDataStore

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

    /** Whether the lab-adjustment intro sheet has been shown on the calibration screen. */
    val pkCalibrationIntroSeen: Flow<Boolean> = storedPreferences
        .map { it[pkCalibrationIntroSeenKey] ?: false }
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
    // Required by HomeSnapshotRepository's worker, which publishes the option
    // with the E2 inputs, and by HomeRepository's snapshot and Room paths.
    val homeE2ChartWindowOptionFlow: Flow<HomeE2ChartWindowOption> = storedPreferences
        .map { preferences ->
            HomeE2ChartWindowOption.fromStorageValue(preferences[homeE2ChartWindowKey])
        }
        .distinctUntilChanged()

    val homeE2DisplayUnitFlow: Flow<AllowedAnalyteUnit> = storedPreferences
        .map { preferences -> resolveHomeE2DisplayUnit(preferences[homeE2DisplayUnitKey]) }
        .distinctUntilChanged()

    val settingsState: StateFlow<SettingsState> = combine(
        storedPreferences.map { preferences ->
            // Once read, the language this run starts in is stored for the next start, also in a
            // process no activity attaches to (from API 33, where it is known without one): one an
            // alarm woke after the language changed reposts once, not at every start until the
            // app is opened. Unless this run has stored one already.
            if (lastRunAppLanguageTagRead.complete(preferences[appLanguageTagKey]) &&
                isAppLanguageKnown && requestedAppLanguage == null
            ) {
                refreshAppLanguageOption()
            }
            preferencesToStoredSettingsState(preferences)
        },
        appLanguage
    ) { storedSettingsState, currentAppLanguage ->
        storedSettingsState.withAppLanguage(currentAppLanguage)
    }
        .stateIn(
            scope = repositoryScope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsState().withAppLanguage(appLanguage.value)
        )

    private fun SettingsState.withAppLanguage(language: AppLanguage) = copy(
        appLanguageOption = language.shown,
        appLanguageFollowsSystem = language.followsSystem,
    )

    // The lock and hide-content settings bypass [storedPreferences] and [settingsState]: the
    // first turns a read error into defaults (lock off, content visible) and completes, the
    // second starts from those defaults. Here a failed read is reported as Error, so the app
    // lock can fail closed, and is retried. Cold: each collector runs its own reads.
    val securitySettingsState: Flow<SecuritySettingsState> = activeDataStore().data
        .map<Preferences, SecuritySettingsState> { preferences ->
            val stored = preferencesToStoredSettingsState(preferences)
            SecuritySettingsState.Loaded(
                screenLockProtectionEnabled = stored.screenLockProtectionEnabled,
                appLockGracePeriodOption = stored.appLockGracePeriodOption,
                hideScreenContentEnabled = stored.hideScreenContentEnabled,
            )
        }
        .retryWhen { cause, _ ->
            if (cause !is IOException) return@retryWhen false
            emit(SecuritySettingsState.Error)
            // ponytail: fixed delay, add backoff if a persistently failing disk shows up in logs
            delay(SECURITY_SETTINGS_RETRY_DELAY_MILLIS)
            true
        }

    suspend fun getCurrentSettings(): SettingsState {
        val preferences = activeDataStore().data.first()
        storedChosenAppLanguageTag = preferences[appLanguageTagKey]
            .takeUnless { preferences[appLanguageFollowsSystemKey] == true }
        return preferencesToStoredSettingsState(preferences).withAppLanguage(appLanguage.value)
    }

    /** The app language tag the last run stored, or null if none was or it can't be read. */
    suspend fun lastRunAppLanguageTag(): String? = lastRunAppLanguageTagRead.await()

    /**
     * The app language for text posted outside an activity, mapped to a supported one as the
     * screens show it (the application context's own may be another): the chosen one, or the
     * phone's. Below API 33 AppCompat knows the chosen one only once an activity has attached, so
     * a process an alarm woke takes the choice stored with the last change.
     */
    fun appLanguageTag(): String {
        val chosen = appLocales()[0]
            ?: storedChosenAppLanguageTag.takeUnless { isAppLanguageKnown }
                ?.let { tags -> LocaleListCompat.forLanguageTags(tags)[0] }
        return (chosen?.let(AppLanguageOption::fromLocale) ?: systemAppLanguage()).languageTag
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

    suspend fun setHazeBlurEnabled(enabled: Boolean) {
        activeDataStore().edit { preferences ->
            preferences[hazeBlurKey] = enabled
        }
    }

    suspend fun setClassicNavigationBarEnabled(enabled: Boolean) {
        activeDataStore().edit { preferences ->
            preferences[classicNavigationBarKey] = enabled
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

    suspend fun setReminderRepeats(repeats: ReminderRepeats) {
        require(repeats.isValid()) { "Unsupported reminder repeats: $repeats" }
        activeDataStore().edit { preferences ->
            preferences[reminderRepeatCountKey] = repeats.count
            preferences[reminderRepeatIntervalKey] = repeats.intervalMinutes
        }
    }

    suspend fun setHealthConnectWeightSyncEnabled(enabled: Boolean) {
        activeDataStore().edit { preferences ->
            preferences[healthConnectWeightSyncEnabledKey] = enabled
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

    suspend fun setPkCalibrationIntroSeen(seen: Boolean) {
        activeDataStore().edit { preferences ->
            preferences[pkCalibrationIntroSeenKey] = seen
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
        hazeBlurEnabled: Boolean = true,
        remindersEnabled: Boolean,
        showArchivedGroupRecords: Boolean,
        hideReferenceRanges: Boolean,
        onboardingCompleted: Boolean,
        appLanguageOption: AppLanguageOption?,
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
        pkCalibrationIntroSeen: Boolean = false,
        // Null keeps the device's setting.
        healthConnectWeightSyncEnabled: Boolean? = null,
        classicNavigationBarEnabled: Boolean = false,
        // Null keeps the device's setting, as from a backup made before repeats.
        reminderRepeats: ReminderRepeats? = null,
    ) {
        require(homeE2DisplayUnit.analyte == BloodAnalyteKey.E2) {
            "Home E2 display unit must reference analyte E2; got ${homeE2DisplayUnit.analyte.storageValue}."
        }

        activeDataStore().edit { preferences ->
            preferences[darkModeKey] = darkModeOption.name
            preferences[adaptiveColorKey] = adaptiveColorEnabled
            preferences[pureBlackKey] = pureBlackEnabled
            preferences[hazeBlurKey] = hazeBlurEnabled
            preferences[classicNavigationBarKey] = classicNavigationBarEnabled
            preferences[remindersEnabledKey] = remindersEnabled
            healthConnectWeightSyncEnabled?.let { preferences[healthConnectWeightSyncEnabledKey] = it }
            reminderRepeats?.let { repeats ->
                preferences[reminderRepeatCountKey] = repeats.count
                preferences[reminderRepeatIntervalKey] = repeats.intervalMinutes
            }
            preferences[showArchivedGroupRecordsKey] = showArchivedGroupRecords
            preferences[hideReferenceRangesKey] = hideReferenceRanges
            // The lock, its grace period and hide screen content stay local: a backup can't loosen them.
            preferences[onboardingCompletedKey] = onboardingCompleted
            preferences[pkCalibrationIntroSeenKey] = pkCalibrationIntroSeen

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
            preferences[appLanguageTagKey] = (appLanguageOption ?: systemAppLanguage()).languageTag
            preferences[appLanguageFollowsSystemKey] = appLanguageOption == null
        }

        // setApplicationLocales must be called on the main thread; restore runs on IO.
        withContext(Dispatchers.Main) { setAppLanguageOption(appLanguageOption) }
    }

    /** Sets the app language, or with null clears it to follow the phone's. */
    fun setAppLanguageOption(option: AppLanguageOption?) {
        val shown = option ?: systemAppLanguage()
        appLanguage.value = AppLanguage(shown, followsSystem = option == null, known = true)
        // Number formatters (e.g. medicationDoseText) read Locale.getDefault() directly,
        // so syncing it here keeps formatted doses aligned with the UI language.
        Locale.setDefault(Locale.forLanguageTag(shown.languageTag))
        AppCompatDelegate.setApplicationLocales(
            option?.let { LocaleListCompat.forLanguageTags(it.languageTag) }
                ?: LocaleListCompat.getEmptyLocaleList()
        )
        storeAppLanguage(shown.languageTag, followsSystem = option == null)
    }

    /**
     * Reads the app language back ([appLocales]): an empty list follows the system, whichever
     * screen emptied it (the picker, or "System default" in Android's per-app setting). Call it
     * only where the list is known: from API 33 always, below once an activity has attached.
     */
    fun refreshAppLanguageOption() {
        val chosen = appLocales()
            .takeUnless(LocaleListCompat::isEmpty)?.toLanguageTags()
        val shown = AppLanguageOption.fromLocale(appLanguageLocale())
        appLanguage.value = AppLanguage(shown, followsSystem = chosen == null, known = true)
        // Also stores one chosen before the tag was kept or in Android's setting, and when
        // following the system the language it resolves to, which the next start compares with.
        storeAppLanguage(chosen ?: shown.languageTag, followsSystem = chosen == null)
    }

    private fun storeAppLanguage(tag: String, followsSystem: Boolean) {
        requestedAppLanguage = tag to followsSystem
        repositoryScope.launch {
            try {
                // The latest one asked for, not this call's: of quick changes, the write that
                // runs last stores the last one. DataStore skips a write that changes nothing.
                activeDataStore().edit { preferences ->
                    requestedAppLanguage?.let { (latest, follows) ->
                        preferences[appLanguageTagKey] = latest
                        preferences[appLanguageFollowsSystemKey] = follows
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Read back only where no activity attached; the next change or app start writes it.
                diagnosticsLogger.warning(TAG, "app_language_tag_store_failed tag=$tag", error)
            }
        }
    }

    private fun preferencesToStoredSettingsState(preferences: Preferences): SettingsState {
        return SettingsState(
            darkModeOption = DarkModeOption.fromStorageValue(preferences[darkModeKey]),
            adaptiveColorEnabled = preferences[adaptiveColorKey] ?: true,
            pureBlackEnabled = preferences[pureBlackKey] ?: false,
            hazeBlurEnabled = preferences[hazeBlurKey] ?: true,
            classicNavigationBarEnabled = preferences[classicNavigationBarKey] ?: false,
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
            reminderRepeats = ReminderRepeats(
                count = preferences[reminderRepeatCountKey] ?: ReminderRepeats.DEFAULT_COUNT,
                intervalMinutes = preferences[reminderRepeatIntervalKey]
                    ?: ReminderRepeats.DEFAULT_INTERVAL_MINUTES,
            ).withShortIntervalRaised().takeIf(ReminderRepeats::isValid) ?: ReminderRepeats(),
            healthConnectWeightSyncEnabled = preferences[healthConnectWeightSyncEnabledKey] ?: false,
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
}

data class LegacyWidgetAppearance(
    val contentScale: Float,
    val backgroundAlpha: Float,
    val darkMode: DarkModeOption,
)
