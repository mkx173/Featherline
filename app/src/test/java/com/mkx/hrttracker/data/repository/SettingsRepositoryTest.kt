package com.mkx.hrttracker.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.mkx.hrttracker.model.bloodtest.AllowedAnalyteUnit
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestCatalog
import com.mkx.hrttracker.model.medication.MedicineStockState
import com.mkx.hrttracker.model.pk.HomeE2ChartWindowOption
import com.mkx.hrttracker.model.settings.AppLanguageOption
import com.mkx.hrttracker.model.settings.AppLockGracePeriodOption
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.model.settings.FirstDayOfWeekOption
import com.mkx.hrttracker.util.appLanguageLocale
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Locale

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
        mockkStatic("com.mkx.hrttracker.util.LocalizationKt")
        val context: Context = mockk()
        every { appLanguageLocale() } returns Locale.ENGLISH

        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tmpFolder.newFile("settings_test.preferences_pb") },
        )

        settingsRepository = SettingsRepository(context, dataStore)
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
    fun `cjk text offset setting persists and defaults off`() = runTest(testDispatcher) {
        assertEquals(false, settingsRepository.getCurrentSettings().cjkTextOffsetEnabled)

        settingsRepository.setCjkTextOffsetEnabled(true)

        assertEquals(true, settingsRepository.getCurrentSettings().cjkTextOffsetEnabled)
    }

    @Test
    fun `restoreSettings restores cjk text offset setting`() = runTest(testDispatcher) {
        restoreSettingsWithCjkTextOffsetEnabled(true)

        assertEquals(true, settingsRepository.getCurrentSettings().cjkTextOffsetEnabled)
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

            val collapseObserved = mutableListOf<Pair<Boolean, Map<String, MedicineStockState>>>()
            val collapseJob = launch {
                foldStateFlow
                    .drop(1)
                    .take(1)
                    .toList(collapseObserved)
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

            val expandObserved = mutableListOf<Pair<Boolean, Map<String, MedicineStockState>>>()
            val expandJob = launch {
                foldStateFlow
                    .drop(1)
                    .take(1)
                    .toList(expandObserved)
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
            appLockGracePeriodOption = AppLockGracePeriodOption.ONE_MINUTE,
            hideScreenContentEnabled = false,
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
            appLockGracePeriodOption = AppLockGracePeriodOption.ONE_MINUTE,
            hideScreenContentEnabled = false,
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

    private suspend fun restoreSettingsWithCjkTextOffsetEnabled(enabled: Boolean) {
        settingsRepository.restoreSettings(
            darkModeOption = DarkModeOption.FOLLOW_SYSTEM,
            adaptiveColorEnabled = true,
            pureBlackEnabled = false,
            cjkTextOffsetEnabled = enabled,
            remindersEnabled = true,
            showArchivedGroupRecords = true,
            hideReferenceRanges = false,
            appLockGracePeriodOption = AppLockGracePeriodOption.ONE_MINUTE,
            hideScreenContentEnabled = false,
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
            appLockGracePeriodOption = AppLockGracePeriodOption.ONE_MINUTE,
            hideScreenContentEnabled = false,
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
