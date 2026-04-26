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
    fun externalActivityBypass_success_keeps_app_unlocked_until_result_is_consumed() = runTest {
        val viewModel = AppLockViewModel(
            settingsRepository = settingsRepository,
            appLockSecurityManager = appLockSecurityManager,
            elapsedRealtimeProvider = elapsedRealtimeProvider,
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isUnlocked)
        assertNotNull(viewModel.uiState.value.pendingPrompt)

        viewModel.onAuthenticationSucceeded()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isUnlocked)
        assertNull(viewModel.uiState.value.pendingPrompt)

        viewModel.allowNextExternalActivityBypass()
        viewModel.onBackgrounded()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isUnlocked)
        assertNull(viewModel.uiState.value.pendingPrompt)

        viewModel.onForegrounded()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isUnlocked)
        assertNull(viewModel.uiState.value.pendingPrompt)

        viewModel.onExternalActivityResult(keepUnlocked = true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isUnlocked)
        assertNull(viewModel.uiState.value.pendingPrompt)

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
    fun externalActivityBypass_cancel_relocks_immediately_after_return() = runTest {
        val viewModel = AppLockViewModel(
            settingsRepository = settingsRepository,
            appLockSecurityManager = appLockSecurityManager,
            elapsedRealtimeProvider = elapsedRealtimeProvider,
        )
        advanceUntilIdle()

        viewModel.onAuthenticationSucceeded()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isUnlocked)

        viewModel.allowNextExternalActivityBypass()
        viewModel.onBackgrounded()
        advanceUntilIdle()
        viewModel.onForegrounded()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isUnlocked)

        viewModel.onExternalActivityResult(keepUnlocked = false)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isUnlocked)
        assertNull(viewModel.uiState.value.pendingPrompt)
    }

    @Test
    fun externalActivityBypass_cancel_respects_grace_period() = runTest {
        settingsStateFlow.value = SettingsState(
            screenLockProtectionEnabled = true,
            appLockGracePeriodOption = AppLockGracePeriodOption.FIVE_MINUTES,
        )
        coEvery { settingsRepository.getCurrentSettings() } returns settingsStateFlow.value
        every { elapsedRealtimeProvider.now() } returnsMany listOf(1_000L, 61_000L)

        val viewModel = AppLockViewModel(
            settingsRepository = settingsRepository,
            appLockSecurityManager = appLockSecurityManager,
            elapsedRealtimeProvider = elapsedRealtimeProvider,
        )
        advanceUntilIdle()

        viewModel.onAuthenticationSucceeded()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isUnlocked)

        viewModel.allowNextExternalActivityBypass()
        viewModel.onBackgrounded()
        advanceUntilIdle()
        viewModel.onForegrounded()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isUnlocked)

        viewModel.onExternalActivityResult(keepUnlocked = false)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isUnlocked)
        assertNull(viewModel.uiState.value.pendingPrompt)
    }

    @Test
    fun externalActivityBypass_cancelPending_before_background_does_not_bypass_lock() = runTest {
        val viewModel = AppLockViewModel(
            settingsRepository = settingsRepository,
            appLockSecurityManager = appLockSecurityManager,
            elapsedRealtimeProvider = elapsedRealtimeProvider,
        )
        advanceUntilIdle()

        viewModel.onAuthenticationSucceeded()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isUnlocked)

        viewModel.allowNextExternalActivityBypass()
        viewModel.cancelPendingExternalActivityBypass()
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
    fun externalActivityBypass_stale_pending_background_does_not_bypass_lock() = runTest {
        every { elapsedRealtimeProvider.now() } returnsMany listOf(
            1_000L,
            1_000L + AppLockViewModel.EXTERNAL_ACTIVITY_BYPASS_ARM_TIMEOUT_MILLIS + 1L,
        )

        val viewModel = AppLockViewModel(
            settingsRepository = settingsRepository,
            appLockSecurityManager = appLockSecurityManager,
            elapsedRealtimeProvider = elapsedRealtimeProvider,
        )
        advanceUntilIdle()

        viewModel.onAuthenticationSucceeded()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isUnlocked)

        viewModel.allowNextExternalActivityBypass()
        viewModel.onBackgrounded()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isUnlocked)
        assertNull(viewModel.uiState.value.pendingPrompt)

        viewModel.onForegrounded()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isUnlocked)
        assertNotNull(viewModel.uiState.value.pendingPrompt)
    }
}
