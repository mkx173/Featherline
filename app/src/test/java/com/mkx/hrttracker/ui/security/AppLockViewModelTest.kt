package com.mkx.hrttracker.ui.security

import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.settings.AppLockGracePeriodOption
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.AppLockSecurityManager
import com.mkx.hrttracker.util.ElapsedRealtimeProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppLockViewModelTest {
    private val settingsRepository: SettingsRepository = mockk()
    private val appLockSecurityManager: AppLockSecurityManager = mockk()
    private val elapsedRealtimeProvider: ElapsedRealtimeProvider = mockk()
    private val dispatcher = StandardTestDispatcher()
    private lateinit var settingsStateFlow: MutableStateFlow<SettingsState>

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        settingsStateFlow = MutableStateFlow(
            SettingsState(
                screenLockProtectionEnabled = true,
                appLockGracePeriodOption = AppLockGracePeriodOption.IMMEDIATELY,
            )
        )
        every { settingsRepository.settingsState } returns settingsStateFlow
        coEvery { settingsRepository.getCurrentSettings() } returns settingsStateFlow.value
        every { appLockSecurityManager.availabilityErrorMessageRes() } returns null
        every { elapsedRealtimeProvider.now() } returns 1_000L
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialLockedState_requestsUnlockPrompt() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isUnlocked)
        assertNotNull(viewModel.uiState.value.pendingPrompt)
    }

    @Test
    fun immediateGracePeriod_relocksAfterBackgrounding() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAuthenticationSucceeded()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isUnlocked)

        viewModel.onBackgrounded()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isUnlocked)
        assertNull(viewModel.uiState.value.pendingPrompt)

        viewModel.onForegrounded()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isUnlocked)
        assertNotNull(viewModel.uiState.value.pendingPrompt)
    }

    @Test
    fun delayedGracePeriod_keepsUnlockedWhenReturningBeforeTimeout() = runTest {
        settingsStateFlow.value = SettingsState(
            screenLockProtectionEnabled = true,
            appLockGracePeriodOption = AppLockGracePeriodOption.ONE_MINUTE,
        )
        coEvery { settingsRepository.getCurrentSettings() } returns settingsStateFlow.value
        every { elapsedRealtimeProvider.now() } returnsMany listOf(1_000L, 60_999L)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAuthenticationSucceeded()
        advanceUntilIdle()

        viewModel.onBackgrounded()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isUnlocked)

        viewModel.onForegrounded()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isUnlocked)
        assertNull(viewModel.uiState.value.pendingPrompt)
    }

    @Test
    fun delayedGracePeriod_relocksWhenReturningAtTimeout() = runTest {
        settingsStateFlow.value = SettingsState(
            screenLockProtectionEnabled = true,
            appLockGracePeriodOption = AppLockGracePeriodOption.ONE_MINUTE,
        )
        coEvery { settingsRepository.getCurrentSettings() } returns settingsStateFlow.value
        every { elapsedRealtimeProvider.now() } returnsMany listOf(1_000L, 61_000L)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAuthenticationSucceeded()
        advanceUntilIdle()

        viewModel.onBackgrounded()
        advanceUntilIdle()

        viewModel.onForegrounded()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isUnlocked)
        assertNotNull(viewModel.uiState.value.pendingPrompt)
    }

    private fun createViewModel(): AppLockViewModel {
        return AppLockViewModel(
            settingsRepository = settingsRepository,
            appLockSecurityManager = appLockSecurityManager,
            elapsedRealtimeProvider = elapsedRealtimeProvider,
        )
    }
}
