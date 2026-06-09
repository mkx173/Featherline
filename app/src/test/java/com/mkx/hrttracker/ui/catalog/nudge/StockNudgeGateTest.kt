package com.mkx.hrttracker.ui.catalog.nudge

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.util.appLanguageLocale
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class StockNudgeGateTest {
    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var gate: StockNudgeGate
    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        mockkStatic("com.mkx.hrttracker.util.LocalizationKt")
        every { appLanguageLocale() } returns Locale.ENGLISH

        val context: Context = mockk()
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tmpFolder.newFile("settings_test.preferences_pb") },
        )

        settings = SettingsRepository(context, dataStore)
        gate = StockNudgeGate(settings)
    }

    @Test
    fun `enabled defaults to true`() = runTest(testDispatcher) {
        assertEquals(true, gate.enabled.first())
    }

    @Test
    fun `first two X dismissals keep nudge enabled`() = runTest(testDispatcher) {
        assertEquals(false, gate.onDismissedViaX())
        assertEquals(false, gate.onDismissedViaX())
        assertEquals(true, settings.stockNudgeEnabledFlow.first())
    }

    @Test
    fun `third X dismissal disables nudge and reports it`() = runTest(testDispatcher) {
        gate.onDismissedViaX()
        gate.onDismissedViaX()

        assertEquals(true, gate.onDismissedViaX())
        assertEquals(false, settings.stockNudgeEnabledFlow.first())
    }

    @Test
    fun `fourth X dismissal after auto-disable does not report just disabled`() =
        runTest(testDispatcher) {
            repeat(3) { gate.onDismissedViaX() }

            assertEquals(false, gate.onDismissedViaX())
            assertEquals(false, settings.stockNudgeEnabledFlow.first())
        }

    @Test
    fun `voluntarily re-enabling keeps the nudge on for good`() = runTest(testDispatcher) {
        // The user let it auto-disable, then deliberately turned it back on.
        // That voluntary opt-in outranks the dismiss-threshold policy: further
        // X-dismissals hide the current nudge but never auto-disable again.
        repeat(3) { gate.onDismissedViaX() }

        gate.setEnabled(true)

        assertEquals(true, settings.stockNudgeEnabledFlow.first())
        repeat(5) { assertEquals(false, gate.onDismissedViaX()) }
        assertEquals(true, settings.stockNudgeEnabledFlow.first())
    }
}
