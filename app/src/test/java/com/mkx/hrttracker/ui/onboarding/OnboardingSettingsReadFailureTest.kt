package com.mkx.hrttracker.ui.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import com.mkx.hrttracker.data.repository.MedicationGroupRepository
import com.mkx.hrttracker.data.repository.MedicineRepository
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.data.repository.UserProfileRepository
import com.mkx.hrttracker.healthconnect.HealthConnectCapabilityReconciler
import com.mkx.hrttracker.healthconnect.WeightSyncCoordinator
import com.mkx.hrttracker.util.AppDiagnosticsLogger
import com.mkx.hrttracker.util.appLanguageLocale
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.Locale

/**
 * A settings read that fails as the app starts, on the real SettingsRepository and
 * OnboardingViewModel. The read is retried instead of shown as defaults, so onboarding isn't
 * shown to someone who finished it, and its first frame's reminders baseline returns once the
 * disk reads again instead of waiting on a settingsState that had ended.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingSettingsReadFailureTest {
    private val remindersEnabled = booleanPreferencesKey("reminders_enabled")
    private val onboardingCompleted = booleanPreferencesKey("onboarding_completed")

    @Before
    fun setUp() {
        // Not a test dispatcher: the repository's retry delay runs in real time here.
        Dispatchers.setMain(Dispatchers.Unconfined)
        mockkStatic("com.mkx.hrttracker.util.LocalizationKt")
        every { appLanguageLocale() } returns Locale.ENGLISH
    }

    @After
    fun tearDown() {
        unmockkStatic("com.mkx.hrttracker.util.LocalizationKt")
        Dispatchers.resetMain()
    }

    @Test
    fun aFailedSettingsReadAtStart_waitsForTheStoredSettings_insteadOfShowingOnboarding() = runBlocking {
        // Someone who finished onboarding and has dose reminders on.
        val store = FlakySettingsStore(preferencesOf(onboardingCompleted to true, remindersEnabled to true))
        store.readsFail = true
        val settings = SettingsRepository(store, mockk<AppDiagnosticsLogger>(relaxed = true))
        val viewModel = onboardingViewModel(settings)

        // While reads fail, nothing is loaded, so the release build's `isLoaded && !isCompleted`
        // doesn't show onboarding.
        Thread.sleep(300)
        assertFalse("loaded from a failed read", viewModel.uiState.value.isLoaded)

        store.readsFail = false
        val loaded = withTimeout(5_000) { viewModel.uiState.first { state -> state.isLoaded } }
        assertTrue("onboarding still counts as done", loaded.isCompleted)
    }

    @Test
    fun theRemindersBaseline_returnsAfterAFailedRead() = runBlocking {
        val store = FlakySettingsStore(preferencesOf(remindersEnabled to true))
        store.readsFail = true
        val settings = SettingsRepository(store, mockk<AppDiagnosticsLogger>(relaxed = true))
        val viewModel = onboardingViewModel(settings)
        Thread.sleep(300)

        store.readsFail = false
        val applied = withTimeout(5_000) { viewModel.setRemindersEnabledDuringOnboarding(false) }

        assertTrue(applied)
        assertEquals(false, store.stored.value[remindersEnabled])
    }

    private fun onboardingViewModel(settings: SettingsRepository) = OnboardingViewModel(
        settingsRepository = settings,
        userProfileRepository = mockk<UserProfileRepository> { every { observeProfile() } returns flowOf(null) },
        medicationReminderScheduler = mockk(relaxed = true),
        medicationGroupRepository = mockk<MedicationGroupRepository> { every { observeGroups() } returns flowOf(emptyList()) },
        medicineRepository = mockk<MedicineRepository> { every { observeAllActiveOrNull() } returns flowOf(emptyList()) },
        weightSyncCoordinator = mockk<WeightSyncCoordinator> {
            every { isSyncing } returns MutableStateFlow(false)
            every { readingProblem } returns MutableStateFlow(null)
        },
        healthConnectCapabilityReconciler = mockk<HealthConnectCapabilityReconciler> {
            every { state } returns MutableStateFlow(mockk(relaxed = true))
        },
    )

    private class FlakySettingsStore(initial: Preferences) : DataStore<Preferences> {
        val stored = MutableStateFlow(initial)
        @Volatile var readsFail = false

        override val data: Flow<Preferences> = flow {
            if (readsFail) throw IOException("EBUSY")
            emitAll(stored)
        }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(stored.value).also { stored.value = it }
    }
}
