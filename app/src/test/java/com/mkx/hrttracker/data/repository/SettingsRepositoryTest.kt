package com.mkx.hrttracker.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mkx.hrttracker.model.bloodtest.AllowedAnalyteUnit
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestCatalog
import com.mkx.hrttracker.model.settings.FirstDayOfWeekOption
import com.mkx.hrttracker.util.currentAppLocale
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    @Before
    fun setUp() {
        mockkStatic("com.mkx.hrttracker.util.LocalizationKt")
        val context: Context = mockk()
        every { context.currentAppLocale() } returns Locale.ENGLISH

        val dataStore = PreferenceDataStoreFactory.create(
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
}
