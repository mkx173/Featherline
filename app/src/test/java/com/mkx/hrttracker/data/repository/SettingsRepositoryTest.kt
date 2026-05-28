package com.mkx.hrttracker.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
import com.mkx.hrttracker.util.currentAppLocale
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
        every { context.currentAppLocale() } returns Locale.ENGLISH

        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tmpFolder.newFile("settings_test.preferences_pb") },
        )

        settingsRepository = SettingsRepository(context, dataStore)
    }

    @Test
    fun `homeE2DisplayUnitFlow emits updated value after setHomeE2DisplayUnit`() = runTest(testDispatcher) {
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
    fun `homeE2DisplayUnitFlow emits canonical unit when no preference is stored`() = runTest(testDispatcher) {
        val initial: AllowedAnalyteUnit = settingsRepository.homeE2DisplayUnitFlow.first()
        val canonical = AllowedAnalyteUnit.of(
            BloodAnalyteKey.E2,
            BloodTestCatalog.canonicalUnitFor(BloodAnalyteKey.E2),
        )

        assertEquals(canonical, initial)
    }

    @Test
    fun `firstDayOfWeekOption persists chosen value and round-trips back to FOLLOW_SYSTEM`() = runTest(testDispatcher) {
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
    fun `group name counter peek is stable and consume advances`() = runTest(testDispatcher) {
        assertEquals(1, settingsRepository.peekNextGroupNameIndex())
        assertEquals(1, settingsRepository.peekNextGroupNameIndex())

        assertEquals(1, settingsRepository.consumeNextGroupNameIndex())

        assertEquals(2, settingsRepository.peekNextGroupNameIndex())
        assertEquals(2, settingsRepository.consumeNextGroupNameIndex())
        assertEquals(3, settingsRepository.peekNextGroupNameIndex())
    }

    @Test
    fun `homeE2DisplayUnitFlow emits canonical unit after setting canonical unit`() = runTest(testDispatcher) {
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
        assertEquals(emptyMap<String, MedicineStockState>(), settingsRepository.homeLowStockAcknowledgedWarningStatesFlow.first())
    }

    @Test
    fun `home low stock acknowledged warning states persist and round trip`() = runTest(testDispatcher) {
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
    fun `home low stock acknowledged warning states overwrite prior value`() = runTest(testDispatcher) {
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
    fun `clearing home low stock acknowledged warning states removes preference`() = runTest(testDispatcher) {
        val key = stringSetPreferencesKey("home_low_stock_acknowledged_warning_states")
        val uuid = "11111111-1111-1111-1111-111111111111"
        settingsRepository.setHomeLowStockSectionFoldState(
            expanded = false,
            acknowledgedWarningStates = mapOf(uuid to MedicineStockState.OUT),
        )

        settingsRepository.clearHomeLowStockAcknowledgedWarningStates()

        assertNull(dataStore.data.first()[key])
        assertEquals(emptyMap<String, MedicineStockState>(), settingsRepository.homeLowStockAcknowledgedWarningStatesFlow.first())
    }

    @Test
    fun `home low stock acknowledged warning state decoding ignores malformed entries`() = runTest(testDispatcher) {
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
    fun `home low stock fold state updates expanded and acknowledged states atomically`() = runTest(testDispatcher) {
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
        assertEquals(emptyMap<String, MedicineStockState>(), settingsRepository.homeLowStockAcknowledgedWarningStatesFlow.first())
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

        assertEquals(emptyMap<String, MedicineStockState>(), settingsRepository.homeLowStockAcknowledgedWarningStatesFlow.first())
    }
}
