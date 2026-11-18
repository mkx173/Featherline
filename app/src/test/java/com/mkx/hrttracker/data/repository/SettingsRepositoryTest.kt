package com.mkx.hrttracker.data.repository

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.mkx.hrttracker.followAndroidAppLanguageSetting
import com.mkx.hrttracker.model.bloodtest.AllowedAnalyteUnit
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestCatalog
import com.mkx.hrttracker.model.home.HomeCardLayout
import com.mkx.hrttracker.model.home.HomeCardType
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.settings.AppLanguageOption
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.model.settings.FirstDayOfWeekOption
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import com.mkx.hrttracker.util.appLanguageLocale
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {
    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("com.mkx.hrttracker.util.LocalizationKt")
        val context: Context = mockk()
        every { appLanguageLocale() } returns Locale.ENGLISH

        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tmpFolder.newFile("settings_test.preferences_pb") },
        )

        settingsRepository = SettingsRepository(dataStore)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun pkCalibrationIntroSeen_defaultsFalse_andPersistsOnceSet() = testScope.runTest {
        assertEquals(false, settingsRepository.pkCalibrationIntroSeen.first())
        settingsRepository.setPkCalibrationIntroSeen(true)
        assertEquals(true, settingsRepository.pkCalibrationIntroSeen.first())
    }

    @Test
    fun `homeE2DisplayUnitFlow emits updated value after setHomeE2DisplayUnit`() =
        runTest(testDispatcher) {
            val initial: AllowedAnalyteUnit = settingsRepository.homeE2DisplayUnitFlow.first()
            val alternate = BloodTestCatalog.definitionFor(BloodAnalyteKey.E2)
                .allowedUnits
                .first { it != initial.unit }
                .let { unit -> AllowedAnalyteUnit.of(BloodAnalyteKey.E2, unit) }

            assertNotEquals(initial, alternate)

            settingsRepository.setHomeE2DisplayUnit(alternate)
            val updated = settingsRepository.homeE2DisplayUnitFlow.first()

            assertEquals(alternate, updated)
        }

    @Test
    fun `homeE2DisplayUnitFlow emits canonical unit when no preference is stored`() =
        runTest(testDispatcher) {
            val initial: AllowedAnalyteUnit = settingsRepository.homeE2DisplayUnitFlow.first()
            val canonical = AllowedAnalyteUnit.of(
                BloodAnalyteKey.E2,
                BloodTestCatalog.canonicalUnitFor(BloodAnalyteKey.E2),
            )

            assertEquals(canonical, initial)
        }

    @Test
    fun `firstDayOfWeekOption persists chosen value and round-trips back to FOLLOW_SYSTEM`() =
        runTest(testDispatcher) {
            assertEquals(
                FirstDayOfWeekOption.FOLLOW_SYSTEM,
                settingsRepository.getCurrentSettings().firstDayOfWeekOption,
            )

            settingsRepository.setFirstDayOfWeekOption(FirstDayOfWeekOption.SUNDAY)
            assertEquals(
                FirstDayOfWeekOption.SUNDAY,
                settingsRepository.getCurrentSettings().firstDayOfWeekOption,
            )

            settingsRepository.setFirstDayOfWeekOption(FirstDayOfWeekOption.FOLLOW_SYSTEM)
            assertEquals(
                FirstDayOfWeekOption.FOLLOW_SYSTEM,
                settingsRepository.getCurrentSettings().firstDayOfWeekOption,
            )
        }

    @Test
    fun `pure black setting persists and defaults off`() = runTest(testDispatcher) {
        assertEquals(false, settingsRepository.getCurrentSettings().pureBlackEnabled)

        settingsRepository.setPureBlackEnabled(true)

        assertEquals(true, settingsRepository.getCurrentSettings().pureBlackEnabled)
    }

    @Test
    fun `haze blur setting persists and defaults on`() = runTest(testDispatcher) {
        assertEquals(true, settingsRepository.getCurrentSettings().hazeBlurEnabled)

        settingsRepository.setHazeBlurEnabled(false)
        assertEquals(false, settingsRepository.getCurrentSettings().hazeBlurEnabled)

        settingsRepository.setHazeBlurEnabled(true)
        assertEquals(true, settingsRepository.getCurrentSettings().hazeBlurEnabled)
    }

    @Test
    fun `classic navigation bar setting persists and defaults off`() = runTest(testDispatcher) {
        assertEquals(false, settingsRepository.getCurrentSettings().classicNavigationBarEnabled)

        settingsRepository.setClassicNavigationBarEnabled(true)
        assertEquals(true, settingsRepository.getCurrentSettings().classicNavigationBarEnabled)

        settingsRepository.setClassicNavigationBarEnabled(false)
        assertEquals(false, settingsRepository.getCurrentSettings().classicNavigationBarEnabled)
    }

    @Test
    fun `restoreSettings restores haze blur setting`() = runTest(testDispatcher) {
        restoreSettingsWithHazeBlurEnabled(false)

        assertEquals(false, settingsRepository.getCurrentSettings().hazeBlurEnabled)
    }

    @Test
    fun `group name counter peek is stable and consume advances`() = runTest(testDispatcher) {
        assertEquals(1, settingsRepository.peekNextGroupNameIndex())
        assertEquals(1, settingsRepository.peekNextGroupNameIndex())

        assertEquals(1, settingsRepository.consumeNextGroupNameIndex())

        assertEquals(2, settingsRepository.peekNextGroupNameIndex())
        assertEquals(2, settingsRepository.consumeNextGroupNameIndex())
        assertEquals(3, settingsRepository.peekNextGroupNameIndex())
    }

    @Test
    fun `stockNudgeEnabledFlow defaults to true`() = runTest(testDispatcher) {
        assertEquals(true, settingsRepository.stockNudgeEnabledFlow.first())
    }

    @Test
    fun `setStockNudgeEnabled false then flow emits false`() = runTest(testDispatcher) {
        settingsRepository.setStockNudgeEnabled(false)
        assertEquals(false, settingsRepository.stockNudgeEnabledFlow.first())
    }

    @Test
    fun `recordStockNudgeDismissal disables and reports only at the dismiss limit`() =
        runTest(testDispatcher) {
            assertEquals(false, settingsRepository.recordStockNudgeDismissal(dismissLimit = 3))
            assertEquals(false, settingsRepository.recordStockNudgeDismissal(dismissLimit = 3))
            // The threshold dismissal disables the nudge atomically in the same edit.
            assertEquals(true, settingsRepository.recordStockNudgeDismissal(dismissLimit = 3))
            assertEquals(false, settingsRepository.stockNudgeEnabledFlow.first())
        }

    @Test
    fun `stockNudgeUserEnabledFlow defaults to false`() = runTest(testDispatcher) {
        assertEquals(false, settingsRepository.stockNudgeUserEnabledFlow.first())
    }

    @Test
    fun `setStockNudgeEnabled true marks the nudge as voluntarily enabled`() =
        runTest(testDispatcher) {
            settingsRepository.setStockNudgeEnabled(true)
            assertEquals(true, settingsRepository.stockNudgeUserEnabledFlow.first())
        }

    @Test
    fun `voluntary enable suppresses auto-disable no matter how many dismissals`() =
        runTest(testDispatcher) {
            // Once the user opts in via the menu toggle, the dismiss-threshold policy
            // is off for good: dismissals never report a disable and the flag stays on,
            // even past what would otherwise be the limit.
            settingsRepository.setStockNudgeEnabled(true)

            repeat(5) {
                assertEquals(false, settingsRepository.recordStockNudgeDismissal(dismissLimit = 1))
            }
            assertEquals(true, settingsRepository.stockNudgeEnabledFlow.first())
        }

    @Test
    fun `setStockNudgeEnabled false leaves dismiss count alone`() = runTest(testDispatcher) {
        settingsRepository.recordStockNudgeDismissal(dismissLimit = 99)
        settingsRepository.recordStockNudgeDismissal(dismissLimit = 99)
        settingsRepository.setStockNudgeEnabled(false)
        // Count preserved at 2: the next dismissal is the 3rd and hits limit 3.
        assertEquals(true, settingsRepository.recordStockNudgeDismissal(dismissLimit = 3))
    }

    @Test
    fun `restoreSettings with stock nudge disabled persists false and clears dismiss count`() =
        runTest(testDispatcher) {
            settingsRepository.recordStockNudgeDismissal(dismissLimit = 99)
            settingsRepository.recordStockNudgeDismissal(dismissLimit = 99)

            restoreSettingsWithStockNudgeEnabled(false)

            assertEquals(false, settingsRepository.stockNudgeEnabledFlow.first())
            // Count cleared by restore: the first dismissal hits a limit of 1.
            assertEquals(true, settingsRepository.recordStockNudgeDismissal(dismissLimit = 1))
        }

    @Test
    fun `restoreSettings carries the voluntarily-enabled flag and suppresses auto-disable`() =
        runTest(testDispatcher) {
            restoreSettingsWithStockNudgeEnabled(enabled = true, userEnabled = true)

            assertEquals(true, settingsRepository.stockNudgeUserEnabledFlow.first())
            // A restored voluntary opt-in keeps auto-disable off across devices.
            assertEquals(false, settingsRepository.recordStockNudgeDismissal(dismissLimit = 1))
        }

    @Test
    fun `restoreSettings without the voluntarily-enabled flag leaves auto-disable armed`() =
        runTest(testDispatcher) {
            // Backups predating the flag (or where the user never opted in) restore as
            // not-voluntary, so the threshold policy still applies.
            restoreSettingsWithStockNudgeEnabled(enabled = true, userEnabled = false)

            assertEquals(false, settingsRepository.stockNudgeUserEnabledFlow.first())
            assertEquals(true, settingsRepository.recordStockNudgeDismissal(dismissLimit = 1))
        }

    @Test
    fun `restoreSettings omitting stock nudge flag defaults true and clears dismiss count`() =
        runTest(testDispatcher) {
            settingsRepository.setStockNudgeEnabled(false)
            settingsRepository.recordStockNudgeDismissal(dismissLimit = 99)
            settingsRepository.recordStockNudgeDismissal(dismissLimit = 99)

            restoreSettingsOmittingStockNudgeEnabled()

            assertEquals(true, settingsRepository.stockNudgeEnabledFlow.first())
            // Count cleared by restore: the first dismissal hits a limit of 1.
            assertEquals(true, settingsRepository.recordStockNudgeDismissal(dismissLimit = 1))
        }

    @Test
    fun `homeE2DisplayUnitFlow emits canonical unit after setting canonical unit`() =
        runTest(testDispatcher) {
            val nonCanonical = BloodTestCatalog.definitionFor(BloodAnalyteKey.E2)
                .allowedUnits
                .first { it != BloodTestCatalog.canonicalUnitFor(BloodAnalyteKey.E2) }
                .let { unit -> AllowedAnalyteUnit.of(BloodAnalyteKey.E2, unit) }
            settingsRepository.setHomeE2DisplayUnit(nonCanonical)

            val canonical = AllowedAnalyteUnit.of(
                BloodAnalyteKey.E2,
                BloodTestCatalog.canonicalUnitFor(BloodAnalyteKey.E2),
            )
            settingsRepository.setHomeE2DisplayUnit(canonical)
            val updated = settingsRepository.homeE2DisplayUnitFlow.first()

            assertEquals(canonical, updated)
        }

    @Test
    fun `homeLowStockAcknowledgedWarningStatesFlow defaults to empty`() = runTest(testDispatcher) {
        assertEquals(
            emptyMap<String, MedicineStockState>(),
            settingsRepository.homeLowStockAcknowledgedWarningStatesFlow.first()
        )
    }

    @Test
    fun `home low stock acknowledged warning states persist and round trip`() =
        runTest(testDispatcher) {
            val firstUuid = "11111111-1111-1111-1111-111111111111"
            val secondUuid = "22222222-2222-2222-2222-222222222222"

            settingsRepository.setHomeLowStockSectionFoldState(
                expanded = false,
                acknowledgedWarningStates = mapOf(
                    firstUuid to MedicineStockState.USER_LOW,
                    secondUuid to MedicineStockState.OUT,
                ),
            )

            assertEquals(false, settingsRepository.homeLowStockSectionExpandedFlow.first())
            assertEquals(
                mapOf(
                    firstUuid to MedicineStockState.USER_LOW,
                    secondUuid to MedicineStockState.OUT,
                ),
                settingsRepository.homeLowStockAcknowledgedWarningStatesFlow.first(),
            )
        }

    @Test
    fun `home low stock acknowledged warning states overwrite prior value`() =
        runTest(testDispatcher) {
            val firstUuid = "11111111-1111-1111-1111-111111111111"
            val secondUuid = "22222222-2222-2222-2222-222222222222"

            settingsRepository.setHomeLowStockSectionFoldState(
                expanded = false,
                acknowledgedWarningStates = mapOf(firstUuid to MedicineStockState.USER_LOW),
            )
            settingsRepository.setHomeLowStockSectionFoldState(
                expanded = false,
                acknowledgedWarningStates = mapOf(secondUuid to MedicineStockState.IMMINENT),
            )

            assertEquals(
                mapOf(secondUuid to MedicineStockState.IMMINENT),
                settingsRepository.homeLowStockAcknowledgedWarningStatesFlow.first(),
            )
        }

    @Test
    fun `clearing home low stock acknowledged warning states removes preference`() =
        runTest(testDispatcher) {
            val key = stringSetPreferencesKey("home_low_stock_acknowledged_warning_states")
            val uuid = "11111111-1111-1111-1111-111111111111"
            settingsRepository.setHomeLowStockSectionFoldState(
                expanded = false,
                acknowledgedWarningStates = mapOf(uuid to MedicineStockState.OUT),
            )

            settingsRepository.clearHomeLowStockAcknowledgedWarningStates()

            assertNull(dataStore.data.first()[key])
            assertEquals(
                emptyMap<String, MedicineStockState>(),
                settingsRepository.homeLowStockAcknowledgedWarningStatesFlow.first()
            )
        }

    @Test
    fun `home low stock acknowledged warning state decoding ignores malformed entries`() =
        runTest(testDispatcher) {
            val key = stringSetPreferencesKey("home_low_stock_acknowledged_warning_states")
            val validUuid = "11111111-1111-1111-1111-111111111111"
            dataStore.edit { preferences ->
                preferences[key] = setOf(
                    "$validUuid|OUT",
                    "not-a-uuid|USER_LOW",
                    "22222222-2222-2222-2222-222222222222|HEALTHY",
                    "33333333-3333-3333-3333-333333333333|NO_RUNWAY",
                    "44444444-4444-4444-4444-444444444444|BOGUS",
                    "55555555-5555-5555-5555-555555555555",
                )
            }

            assertEquals(
                mapOf(validUuid to MedicineStockState.OUT),
                settingsRepository.homeLowStockAcknowledgedWarningStatesFlow.first(),
            )
        }

    @Test
    fun `home low stock fold state updates expanded and acknowledged states atomically`() =
        runTest(testDispatcher) {
            val uuid = "11111111-1111-1111-1111-111111111111"
            val foldStateFlow: Flow<Pair<Boolean, Map<String, MedicineStockState>>> = combine(
                settingsRepository.homeLowStockSectionExpandedFlow,
                settingsRepository.homeLowStockAcknowledgedWarningStatesFlow,
            ) { expanded: Boolean, states: Map<String, MedicineStockState> -> expanded to states }

            // The first value that differs from the one before the write, whether or not the
            // observer subscribed in time to see that one: counting emissions hung when it didn't.
            val beforeCollapse = foldStateFlow.first()
            val collapseObserved = mutableListOf<Pair<Boolean, Map<String, MedicineStockState>>>()
            val collapseJob = launch {
                collapseObserved += foldStateFlow.first { it != beforeCollapse }
            }
            settingsRepository.setHomeLowStockSectionFoldState(
                expanded = false,
                acknowledgedWarningStates = mapOf(uuid to MedicineStockState.IMMINENT),
            )
            collapseJob.join()

            assertEquals(
                listOf(false to mapOf(uuid to MedicineStockState.IMMINENT)),
                collapseObserved,
            )
            assertEquals(false, settingsRepository.homeLowStockSectionExpandedFlow.first())
            assertEquals(
                mapOf(uuid to MedicineStockState.IMMINENT),
                settingsRepository.homeLowStockAcknowledgedWarningStatesFlow.first(),
            )

            val beforeExpand = foldStateFlow.first()
            val expandObserved = mutableListOf<Pair<Boolean, Map<String, MedicineStockState>>>()
            val expandJob = launch {
                expandObserved += foldStateFlow.first { it != beforeExpand }
            }
            settingsRepository.setHomeLowStockSectionFoldState(expanded = true)
            expandJob.join()

            assertEquals(
                listOf(true to emptyMap<String, MedicineStockState>()),
                expandObserved,
            )
            assertEquals(true, settingsRepository.homeLowStockSectionExpandedFlow.first())
            assertEquals(
                emptyMap<String, MedicineStockState>(),
                settingsRepository.homeLowStockAcknowledgedWarningStatesFlow.first()
            )
        }

    @Test
    fun `restoreSettings clears low stock acknowledged warning states`() = runTest(testDispatcher) {
        val uuid = "11111111-1111-1111-1111-111111111111"
        settingsRepository.setHomeLowStockSectionFoldState(
            expanded = false,
            acknowledgedWarningStates = mapOf(uuid to MedicineStockState.OUT),
        )

        settingsRepository.restoreSettings(
            darkModeOption = DarkModeOption.FOLLOW_SYSTEM,
            adaptiveColorEnabled = true,
            pureBlackEnabled = false,
            remindersEnabled = true,
            showArchivedGroupRecords = true,
            hideReferenceRanges = false,
            onboardingCompleted = true,
            appLanguageOption = AppLanguageOption.ENGLISH,
            calibrationDefaultUnits = emptySet(),
            homeE2DisplayUnit = AllowedAnalyteUnit.of(
                BloodAnalyteKey.E2,
                BloodTestCatalog.canonicalUnitFor(BloodAnalyteKey.E2),
            ),
            homeE2ChartWindowOption = HomeE2ChartWindowOption.SEVEN_DAYS,
        )

        assertEquals(
            emptyMap<String, MedicineStockState>(),
            settingsRepository.homeLowStockAcknowledgedWarningStatesFlow.first()
        )
    }

    @Test
    fun `restoreSettings applies the app language on the main thread`() = runTest(testDispatcher) {
        // setApplicationLocales must run on the main thread; restore calls in from IO.
        val main = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "test-main") }
        Dispatchers.setMain(main.asCoroutineDispatcher())
        mockkStatic(AppCompatDelegate::class)
        var localeThread: String? = null
        every { AppCompatDelegate.setApplicationLocales(any()) } answers {
            localeThread = Thread.currentThread().name
        }
        try {
            withContext(Dispatchers.IO) { restoreSettingsWithHazeBlurEnabled(enabled = true) }
        } finally {
            unmockkStatic(AppCompatDelegate::class)
            main.shutdown()
        }

        assertEquals("test-main", localeThread)
    }

    @Test
    fun `a restored app language is read back by a process no activity has attached to`() =
        runTest(testDispatcher) {
            mockkStatic(AppCompatDelegate::class)
            every { AppCompatDelegate.setApplicationLocales(any()) } just Runs
            // As in a process an alarm woke below API 33: AppCompat knows no app language.
            every { AppCompatDelegate.getApplicationLocales() } returns LocaleListCompat.getEmptyLocaleList()
            val defaultLocale = Locale.getDefault()
            try {
                settingsRepository.restoreSettings(
                    darkModeOption = DarkModeOption.FOLLOW_SYSTEM,
                    adaptiveColorEnabled = true,
                    pureBlackEnabled = false,
                    remindersEnabled = true,
                    showArchivedGroupRecords = true,
                    hideReferenceRanges = false,
                    onboardingCompleted = true,
                    appLanguageOption = AppLanguageOption.SIMPLIFIED_CHINESE,
                    calibrationDefaultUnits = emptySet(),
                    homeE2DisplayUnit = AllowedAnalyteUnit.of(
                        BloodAnalyteKey.E2,
                        BloodTestCatalog.canonicalUnitFor(BloodAnalyteKey.E2),
                    ),
                    homeE2ChartWindowOption = HomeE2ChartWindowOption.SEVEN_DAYS,
                )
                val woken = SettingsRepository(dataStore)
                woken.getCurrentSettings()

                assertEquals("zh-Hans", woken.appLanguageTag())
            } finally {
                Locale.setDefault(defaultLocale)
                unmockkStatic(AppCompatDelegate::class)
            }
        }

    @Test
    fun `setAppLanguageOption stores the tag`() = runTest(testDispatcher) {
        mockkStatic(AppCompatDelegate::class)
        every { AppCompatDelegate.setApplicationLocales(any()) } just Runs
        val defaultLocale = Locale.getDefault()
        try {
            settingsRepository.setAppLanguageOption(AppLanguageOption.TRADITIONAL_CHINESE)

            assertEquals("zh-Hant", awaitStoredAppLanguageTag("zh-Hant"))
        } finally {
            Locale.setDefault(defaultLocale)
            unmockkStatic(AppCompatDelegate::class)
        }
    }

    @Test
    fun `refreshAppLanguageOption stores the language AppCompat has`() = runTest(testDispatcher) {
        mockkStatic(AppCompatDelegate::class)
        // One chosen before the tag was stored, read back once an activity attached.
        every { AppCompatDelegate.getApplicationLocales() } returns mockk {
            every { isEmpty } returns false
            every { toLanguageTags() } returns "yue-Hant-HK"
        }
        try {
            settingsRepository.refreshAppLanguageOption()

            assertEquals("yue-Hant-HK", awaitStoredAppLanguageTag("yue-Hant-HK"))
        } finally {
            unmockkStatic(AppCompatDelegate::class)
        }
    }

    @Test
    fun `a tag write that fails is logged and never reaches the uncaught exception handler`() {
        val failing = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                throw IOException("EBUSY")
        }
        val logged = CompletableDeferred<Throwable?>()
        val logger: AppDiagnosticsLogger = mockk(relaxed = true) {
            every { warning(any(), any(), any()) } answers { logged.complete(thirdArg()) }
        }
        val uncaught = CompletableDeferred<Throwable>()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, error -> uncaught.complete(error) }
        mockkStatic(AppCompatDelegate::class)
        every { AppCompatDelegate.setApplicationLocales(any()) } just Runs
        val defaultLocale = Locale.getDefault()
        try {
            SettingsRepository(failing, logger).setAppLanguageOption(AppLanguageOption.ENGLISH)

            runBlocking {
                withTimeout(30_000) {
                    select {
                        logged.onAwait {}
                        uncaught.onAwait {}
                    }
                }
            }
            assertFalse("uncaught: ${uncaught.takeIf { it.isCompleted }?.getCompleted()}", uncaught.isCompleted)
            assertTrue(logged.getCompleted() is IOException)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
            Locale.setDefault(defaultLocale)
            unmockkStatic(AppCompatDelegate::class)
        }
    }

    @Test
    fun `of quick language changes the last one chosen is stored whatever order the writes run in`() {
        val writes = Channel<suspend (Preferences) -> Preferences>(Channel.UNLIMITED)
        // Holds every write, so the test picks the order they run in.
        val held = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                writes.send(transform)
                awaitCancellation()
            }
        }
        mockkStatic(AppCompatDelegate::class)
        every { AppCompatDelegate.setApplicationLocales(any()) } just Runs
        val defaultLocale = Locale.getDefault()
        try {
            val repository = SettingsRepository(held)
            repository.setAppLanguageOption(AppLanguageOption.ENGLISH)
            repository.setAppLanguageOption(AppLanguageOption.TRADITIONAL_CHINESE)
            repository.setAppLanguageOption(AppLanguageOption.ENGLISH)

            val key = stringPreferencesKey("app_language_tag")
            val stored = runBlocking {
                val held3 = List(3) { withTimeout(30_000) { writes.receive() } }
                // The write that would store Traditional Chinese runs last.
                val tags = held3.map { write -> write(emptyPreferences())[key] }
                var preferences = emptyPreferences()
                for ((write, _) in held3.zip(tags).sortedBy { (_, tag) -> tag == "zh-Hant" }) {
                    preferences = write(preferences)
                }
                preferences[key]
            }
            assertEquals(AppLanguageOption.ENGLISH.languageTag, stored)
        } finally {
            Locale.setDefault(defaultLocale)
            unmockkStatic(AppCompatDelegate::class)
        }
    }

    /** The stored tag once it is [expected], as the write is asynchronous. */
    @Test
    fun `each change in Android's per-app language setting reaches the app language`() = runTest(testDispatcher) {
        mockkStatic(AppCompatDelegate::class)
        every { AppCompatDelegate.getApplicationLocales() } returns mockk {
            every { isEmpty } returns true
        }
        try {
            // Android's setting changes the app language twice; each arrives as a configuration change.
            every { appLanguageLocale() } returns Locale.forLanguageTag("zh-Hans-CN")
            followAndroidAppLanguageSetting(settingsRepository, sdkInt = 33)
            assertEquals(AppLanguageOption.SIMPLIFIED_CHINESE, awaitAppLanguage(AppLanguageOption.SIMPLIFIED_CHINESE))

            every { appLanguageLocale() } returns Locale.forLanguageTag("yue-Hant-HK")
            followAndroidAppLanguageSetting(settingsRepository, sdkInt = 33)
            assertEquals(AppLanguageOption.CANTONESE, awaitAppLanguage(AppLanguageOption.CANTONESE))

            // Below API 33 AppCompat may not know the chosen one yet, so the change is left alone.
            every { appLanguageLocale() } returns Locale.ENGLISH
            followAndroidAppLanguageSetting(settingsRepository, sdkInt = 32)
            assertEquals(AppLanguageOption.CANTONESE, settingsRepository.settingsState.value.appLanguageOption)
        } finally {
            unmockkStatic(AppCompatDelegate::class)
        }
    }

    @Test
    fun `System default in Android's per-app setting stores the language it resolves to`() = runTest(testDispatcher) {
        // So the next start finds its language unchanged and reposts nothing.
        mockkStatic(AppCompatDelegate::class)
        every { AppCompatDelegate.getApplicationLocales() } returns mockk {
            every { isEmpty } returns true
        }
        try {
            every { appLanguageLocale() } returns Locale.forLanguageTag("zh-Hant-TW")
            followAndroidAppLanguageSetting(settingsRepository, sdkInt = 33)

            assertEquals("zh-Hant", awaitStoredAppLanguageTag("zh-Hant"))
        } finally {
            unmockkStatic(AppCompatDelegate::class)
        }
    }

    @Test
    fun `lastRunAppLanguageTag is the tag stored before this run, not one this run stores`() = runTest(testDispatcher) {
        dataStore.edit { it[stringPreferencesKey("app_language_tag")] = "zh-Hans" }
        val nextRun = SettingsRepository(dataStore)

        assertEquals("zh-Hans", nextRun.lastRunAppLanguageTag())
        mockkStatic(AppCompatDelegate::class)
        every { AppCompatDelegate.setApplicationLocales(any()) } just Runs
        val defaultLocale = Locale.getDefault()
        try {
            nextRun.setAppLanguageOption(AppLanguageOption.CANTONESE)
            awaitStoredAppLanguageTag("yue-Hant-HK")

            assertEquals("zh-Hans", nextRun.lastRunAppLanguageTag())
        } finally {
            Locale.setDefault(defaultLocale)
            unmockkStatic(AppCompatDelegate::class)
        }
    }

    private suspend fun awaitAppLanguage(expected: AppLanguageOption): AppLanguageOption =
        withContext(Dispatchers.Default) {
            withTimeout(30_000) {
                settingsRepository.settingsState.first { it.appLanguageOption == expected }.appLanguageOption
            }
        }

    private suspend fun awaitStoredAppLanguageTag(expected: String): String? =
        withContext(Dispatchers.Default) {
            withTimeout(30_000) {
                var tag: String? = null
                while (tag != expected) {
                    tag = dataStore.data.first()[stringPreferencesKey("app_language_tag")]
                    if (tag != expected) delay(10)
                }
                tag
            }
        }

    @Test
    fun `homeCardLayoutFlow defaults to HomeCardLayout when unset`() =
        runTest(testDispatcher) {
            assertEquals(HomeCardLayout(), settingsRepository.homeCardLayoutFlow.first())
        }

    @Test
    fun `setHomeCardLayout round-trips order and hidden`() =
        runTest(testDispatcher) {
            val order = listOf(
                HomeCardType.TIMELINE,
                HomeCardType.E2_HERO,
                HomeCardType.E2_CHART,
                HomeCardType.ANTIANDROGEN,
                HomeCardType.LOW_STOCK,
            )
            val hidden = setOf(HomeCardType.E2_CHART, HomeCardType.LOW_STOCK)

            settingsRepository.setHomeCardLayout(order, hidden)

            val restored = settingsRepository.homeCardLayoutFlow.first()
            assertEquals(order, restored.order)
            assertEquals(hidden, restored.hidden)
        }

    @Test
    fun `homeCardLayoutFlow appends missing enum values and ignores unknown names`() =
        runTest(testDispatcher) {
            // Write a partial, dirty order string directly (simulates an older/forward version).
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("home_card_order")] = "TIMELINE,GHOST,TIMELINE,E2_HERO"
            }

            val layout = settingsRepository.homeCardLayoutFlow.first()

            assertEquals(
                listOf(
                    HomeCardType.TIMELINE,
                    HomeCardType.E2_HERO,
                    HomeCardType.LOW_STOCK,
                    HomeCardType.E2_CHART,
                    HomeCardType.ANTIANDROGEN,
                ),
                layout.order,
            )
            assertEquals(emptySet<HomeCardType>(), layout.hidden)
        }

    // W8: a backup from before weight sync has no flag, and keeps the device's setting.
    @Test
    fun `restoreSettings keeps weight sync when the backup has no flag, and takes one it has`() =
        runTest(testDispatcher) {
            settingsRepository.setHealthConnectWeightSyncEnabled(true)

            restoreSettingsWithWeightSync(enabled = null)
            assertTrue(settingsRepository.getCurrentSettings().healthConnectWeightSyncEnabled)

            restoreSettingsWithWeightSync(enabled = false)
            assertFalse(settingsRepository.getCurrentSettings().healthConnectWeightSyncEnabled)
        }

    private suspend fun restoreSettingsWithWeightSync(enabled: Boolean?) {
        settingsRepository.restoreSettings(
            darkModeOption = DarkModeOption.FOLLOW_SYSTEM,
            adaptiveColorEnabled = true,
            pureBlackEnabled = false,
            remindersEnabled = true,
            showArchivedGroupRecords = true,
            hideReferenceRanges = false,
            onboardingCompleted = true,
            appLanguageOption = AppLanguageOption.ENGLISH,
            calibrationDefaultUnits = emptySet(),
            homeE2DisplayUnit = AllowedAnalyteUnit.of(
                BloodAnalyteKey.E2,
                BloodTestCatalog.canonicalUnitFor(BloodAnalyteKey.E2),
            ),
            homeE2ChartWindowOption = HomeE2ChartWindowOption.SEVEN_DAYS,
            healthConnectWeightSyncEnabled = enabled,
        )
    }

    private suspend fun restoreSettingsWithStockNudgeEnabled(
        enabled: Boolean,
        userEnabled: Boolean = false,
    ) {
        settingsRepository.restoreSettings(
            darkModeOption = DarkModeOption.FOLLOW_SYSTEM,
            adaptiveColorEnabled = true,
            pureBlackEnabled = false,
            remindersEnabled = true,
            showArchivedGroupRecords = true,
            hideReferenceRanges = false,
            onboardingCompleted = true,
            appLanguageOption = AppLanguageOption.ENGLISH,
            calibrationDefaultUnits = emptySet(),
            homeE2DisplayUnit = AllowedAnalyteUnit.of(
                BloodAnalyteKey.E2,
                BloodTestCatalog.canonicalUnitFor(BloodAnalyteKey.E2),
            ),
            homeE2ChartWindowOption = HomeE2ChartWindowOption.SEVEN_DAYS,
            stockNudgeEnabled = enabled,
            stockNudgeUserEnabled = userEnabled,
        )
    }

    private suspend fun restoreSettingsWithHazeBlurEnabled(enabled: Boolean) {
        settingsRepository.restoreSettings(
            darkModeOption = DarkModeOption.FOLLOW_SYSTEM,
            adaptiveColorEnabled = true,
            pureBlackEnabled = false,
            hazeBlurEnabled = enabled,
            remindersEnabled = true,
            showArchivedGroupRecords = true,
            hideReferenceRanges = false,
            onboardingCompleted = true,
            appLanguageOption = AppLanguageOption.ENGLISH,
            calibrationDefaultUnits = emptySet(),
            homeE2DisplayUnit = AllowedAnalyteUnit.of(
                BloodAnalyteKey.E2,
                BloodTestCatalog.canonicalUnitFor(BloodAnalyteKey.E2),
            ),
            homeE2ChartWindowOption = HomeE2ChartWindowOption.SEVEN_DAYS,
        )
    }

    private suspend fun restoreSettingsOmittingStockNudgeEnabled() {
        settingsRepository.restoreSettings(
            darkModeOption = DarkModeOption.FOLLOW_SYSTEM,
            adaptiveColorEnabled = true,
            pureBlackEnabled = false,
            remindersEnabled = true,
            showArchivedGroupRecords = true,
            hideReferenceRanges = false,
            onboardingCompleted = true,
            appLanguageOption = AppLanguageOption.ENGLISH,
            calibrationDefaultUnits = emptySet(),
            homeE2DisplayUnit = AllowedAnalyteUnit.of(
                BloodAnalyteKey.E2,
                BloodTestCatalog.canonicalUnitFor(BloodAnalyteKey.E2),
            ),
            homeE2ChartWindowOption = HomeE2ChartWindowOption.SEVEN_DAYS,
        )
    }
}
