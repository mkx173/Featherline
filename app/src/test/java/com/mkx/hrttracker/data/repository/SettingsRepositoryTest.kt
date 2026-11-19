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
import com.mkx.hrttracker.followAppLanguage
import com.mkx.hrttracker.reminder.MedicationReminderScheduler
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
import com.mkx.hrttracker.util.systemLocale
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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
import kotlinx.coroutines.withTimeoutOrNull
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
    private companion object {
        // Set once setUp has waited in vain for the start store.
        @Volatile
        var startStoreBroken = false
    }

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
        // It stores the language it starts in once it has read the last one: let that land
        // first, so it can't overwrite what a test stores. If it never does, fail once and let
        // every later test fail at once, not each after a long wait.
        check(!startStoreBroken) { "The start store never landed in an earlier test; see the first failure." }
        val landed = runBlocking {
            withContext(Dispatchers.Default) {
                withTimeoutOrNull(10_000) {
                    while (dataStore.data.first()[stringPreferencesKey("app_language_tag")] != "en") delay(10)
                }
            }
        }
        if (landed == null) {
            startStoreBroken = true
            error("The repository didn't store the language it starts in within 10 s.")
        }
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
        // One chosen before the tag was stored (by an earlier build, or in Android's setting).
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
            followAndroidAppLanguageSetting(settingsRepository)
            assertEquals(AppLanguageOption.SIMPLIFIED_CHINESE, awaitAppLanguage(AppLanguageOption.SIMPLIFIED_CHINESE))

            every { appLanguageLocale() } returns Locale.forLanguageTag("yue-Hant-HK")
            followAndroidAppLanguageSetting(settingsRepository)
            assertEquals(AppLanguageOption.CANTONESE, awaitAppLanguage(AppLanguageOption.CANTONESE))
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
            followAndroidAppLanguageSetting(settingsRepository)

            assertEquals("zh-Hant", awaitStoredAppLanguageTag("zh-Hant"))
        } finally {
            unmockkStatic(AppCompatDelegate::class)
        }
    }

    @Test
    fun `choosing Follow system clears the app locales and the picker reads it back`() = runTest(testDispatcher) {
        withAppLocales {
            val repository = SettingsRepository(dataStore)
            repository.setAppLanguageOption(AppLanguageOption.TRADITIONAL_CHINESE)
            assertPicker(repository, AppLanguageOption.TRADITIONAL_CHINESE, followsSystem = false)

            repository.setAppLanguageOption(null)

            assertTrue(appLocales.isEmpty)
            // The phone's language, mapped.
            assertPicker(repository, AppLanguageOption.SIMPLIFIED_CHINESE, followsSystem = true)
            awaitStoredAppLanguageTag("zh-Hans")
            // As the next start's activity reads it.
            val nextRun = SettingsRepository(dataStore)
            nextRun.refreshAppLanguageOption()
            assertPicker(nextRun, AppLanguageOption.SIMPLIFIED_CHINESE, followsSystem = true)
        }
    }

    @Test
    fun `the picker shows what Android's per-app setting sets, System default as Follow system`() =
        runTest(testDispatcher) {
            withAppLocales {
                val repository = SettingsRepository(dataStore)
                repository.setAppLanguageOption(AppLanguageOption.ENGLISH)

                appLocales = LocaleListCompat.forLanguageTags("yue-Hant-HK")
                followAndroidAppLanguageSetting(repository)
                assertPicker(repository, AppLanguageOption.CANTONESE, followsSystem = false)

                appLocales = LocaleListCompat.getEmptyLocaleList()
                followAndroidAppLanguageSetting(repository)
                assertPicker(repository, AppLanguageOption.SIMPLIFIED_CHINESE, followsSystem = true)
            }
        }

    @Test
    fun `a fresh install follows the system, and a language chosen before keeps it`() = runTest(testDispatcher) {
        withAppLocales {
            val fresh = SettingsRepository(dataStore)
            fresh.refreshAppLanguageOption()
            assertPicker(fresh, AppLanguageOption.SIMPLIFIED_CHINESE, followsSystem = true)

            // Chosen by an earlier build: AppCompat (or the framework) holds it.
            appLocales = LocaleListCompat.forLanguageTags("zh-Hant")
            val upgraded = SettingsRepository(dataStore)
            upgraded.refreshAppLanguageOption()
            assertPicker(upgraded, AppLanguageOption.TRADITIONAL_CHINESE, followsSystem = false)
        }
    }

    @Test
    fun `switching between Follow system and a language reposts only when the language shown changes`() =
        runTest(testDispatcher) {
            withAppLocales {
                // The last run showed the language this one starts in.
                dataStore.edit { it[stringPreferencesKey("app_language_tag")] = "zh-Hans" }
                val repository = SettingsRepository(dataStore)
                val reposts = repostsIn(repository) {
                    repository.refreshAppLanguageOption() // Follow system: zh-Hans.
                    repository.setAppLanguageOption(AppLanguageOption.SIMPLIFIED_CHINESE)
                    repository.setAppLanguageOption(null)
                    repository.setAppLanguageOption(AppLanguageOption.ENGLISH) // One.
                    repository.setAppLanguageOption(null) // Two.
                }

                assertEquals(2, reposts)
            }
        }

    @Test
    fun `a start with nothing changed since the last run reposts nothing`() = runTest(testDispatcher) {
        withAppLocales {
            // English chosen, then Follow system in the app.
            val first = SettingsRepository(dataStore)
            repostsIn(first) {
                first.setAppLanguageOption(AppLanguageOption.ENGLISH)
                // Landed before the next change, as below: else the start store's zh-Hans can be
                // on disk while a write computed under English is still on its way, and the next
                // repository's first read can come between them.
                awaitStoredAppLanguageTag("en")
                first.setAppLanguageOption(null)
            }
            awaitStoredAppLanguageTag("zh-Hans")
            val second = SettingsRepository(dataStore)
            assertEquals(0, repostsIn(second) { second.refreshAppLanguageOption() })

            // English chosen, then System default in Android's setting.
            second.setAppLanguageOption(AppLanguageOption.ENGLISH)
            // Landed before the next change: else the store below could see the zh-Hans from
            // before while this write is still on its way.
            awaitStoredAppLanguageTag("en")
            appLocales = LocaleListCompat.getEmptyLocaleList()
            followAndroidAppLanguageSetting(second)
            awaitStoredAppLanguageTag("zh-Hans")
            val third = SettingsRepository(dataStore)
            assertEquals(0, repostsIn(third) { third.refreshAppLanguageOption() })

            // A language chosen.
            third.setAppLanguageOption(AppLanguageOption.TRADITIONAL_CHINESE)
            awaitStoredAppLanguageTag("zh-Hant")
            val fourth = SettingsRepository(dataStore)
            assertEquals(0, repostsIn(fourth) { fourth.refreshAppLanguageOption() })
        }
    }

    @Test
    fun `following the system, a phone language changed while the app was closed reposts at the next start`() =
        runTest(testDispatcher) {
            withAppLocales {
                val first = SettingsRepository(dataStore)
                repostsIn(first) { first.refreshAppLanguageOption() }
                awaitStoredAppLanguageTag("zh-Hans")

                phone = Locale.forLanguageTag("en-US")
                val second = SettingsRepository(dataStore)

                assertEquals(1, repostsIn(second) { second.refreshAppLanguageOption() })
            }
        }

    @Test
    fun `restoring a backup that follows the system follows it`() = runTest(testDispatcher) {
        withAppLocales {
            appLocales = LocaleListCompat.forLanguageTags("en")
            val repository = SettingsRepository(dataStore)
            repository.restoreSettings(
                darkModeOption = DarkModeOption.FOLLOW_SYSTEM,
                adaptiveColorEnabled = true,
                pureBlackEnabled = false,
                remindersEnabled = true,
                showArchivedGroupRecords = true,
                hideReferenceRanges = false,
                onboardingCompleted = true,
                appLanguageOption = null,
                calibrationDefaultUnits = emptySet(),
                homeE2DisplayUnit = AllowedAnalyteUnit.of(
                    BloodAnalyteKey.E2,
                    BloodTestCatalog.canonicalUnitFor(BloodAnalyteKey.E2),
                ),
                homeE2ChartWindowOption = HomeE2ChartWindowOption.SEVEN_DAYS,
            )

            assertTrue(appLocales.isEmpty)
            assertPicker(repository, AppLanguageOption.SIMPLIFIED_CHINESE, followsSystem = true)
            awaitStoredAppLanguageTag("zh-Hans")
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

    @Test
    fun `a language set before the first read is stored after it, so it is not taken for the last run's`() =
        runTest(testDispatcher) {
            dataStore.edit { it[stringPreferencesKey("app_language_tag")] = "zh-Hans" }
            // The first read, slow under load, comes after the change.
            val firstRead = CompletableDeferred<Unit>()
            val slowFirstRead = object : DataStore<Preferences> {
                override val data: Flow<Preferences> = flow {
                    firstRead.await()
                    emitAll(dataStore.data)
                }

                override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences) =
                    dataStore.updateData(transform)
            }
            mockkStatic(AppCompatDelegate::class)
            every { AppCompatDelegate.setApplicationLocales(any()) } just Runs
            val defaultLocale = Locale.getDefault()
            try {
                val run = SettingsRepository(slowFirstRead)
                run.setAppLanguageOption(AppLanguageOption.CANTONESE)
                // Give an unwaited write the time to land first.
                val landedEarly = withContext(Dispatchers.Default) {
                    withTimeoutOrNull(2_000) {
                        while (dataStore.data.first()[stringPreferencesKey("app_language_tag")] != "yue-Hant-HK") delay(10)
                    }
                }
                firstRead.complete(Unit)

                assertNull("the write landed before the first read", landedEarly)

                assertEquals("zh-Hans", run.lastRunAppLanguageTag())
                assertEquals("yue-Hant-HK", awaitStoredAppLanguageTag("yue-Hant-HK"))
            } finally {
                Locale.setDefault(defaultLocale)
                unmockkStatic(AppCompatDelegate::class)
            }
        }

    private suspend fun awaitAppLanguage(
        expected: AppLanguageOption,
        repository: SettingsRepository = settingsRepository,
    ): AppLanguageOption =
        withContext(Dispatchers.Default) {
            withTimeout(30_000) {
                repository.settingsState.first { it.appLanguageOption == expected }.appLanguageOption
            }
        }

    /**
     * What the picker shows. From getCurrentSettings, which carries the language as it is now:
     * settingsState relays it later, from IO, so it could still show the one from before.
     */
    private suspend fun assertPicker(
        repository: SettingsRepository,
        language: AppLanguageOption,
        followsSystem: Boolean,
    ) {
        val settings = repository.getCurrentSettings()
        assertEquals(language to followsSystem, settings.appLanguageOption to settings.appLanguageFollowsSystem)
    }

    // AppCompat's app locales, which setApplicationLocales sets and the framework or AppCompat's
    // storage would hand back; and the phone's language.
    private var appLocales: LocaleListCompat = LocaleListCompat.getEmptyLocaleList()
    private var phone: Locale = Locale.forLanguageTag("zh-Hans-CN")

    private inline fun withAppLocales(block: () -> Unit) {
        mockkStatic(AppCompatDelegate::class)
        every { AppCompatDelegate.setApplicationLocales(any()) } answers { appLocales = firstArg() }
        every { AppCompatDelegate.getApplicationLocales() } answers { appLocales }
        every { appLanguageLocale() } answers { callOriginal() }
        every { systemLocale() } answers { phone }
        val defaultLocale = Locale.getDefault()
        try {
            block()
        } finally {
            Locale.setDefault(defaultLocale)
            unmockkStatic(AppCompatDelegate::class)
        }
    }

    /**
     * One run of the app: [followAppLanguage] over [repository], as the application starts it,
     * while [actions] run; the reminders it reposted.
     */
    private suspend fun TestScope.repostsIn(
        repository: SettingsRepository,
        actions: suspend () -> Unit,
    ): Int {
        var reposts = 0
        val scheduler = mockk<MedicationReminderScheduler> {
            coEvery { refreshDoseReminderNotifications() } answers { reposts++ }
        }
        repository.lastRunAppLanguageTag()
        // Unconfined: it collects each change as it is made, on the thread making it. Undispatched:
        // it takes the start language before the actions run, and can't be cancelled before then.
        val run = backgroundScope.launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            followAppLanguage(
                languages = repository.shownAppLanguage,
                lastLanguageTag = repository::lastRunAppLanguageTag,
                reminderNotificationManager = mockk(relaxed = true),
                medicationReminderScheduler = { scheduler },
                diagnosticsLogger = mockk(relaxed = true),
            )
        }
        // Off the test's own unconfined loop, which would queue the collector until it yields.
        withContext(Dispatchers.Default) { actions() }
        run.cancel()
        return reposts
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
