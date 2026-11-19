package com.mkx.hrttracker.ui.security

import androidx.biometric.BiometricPrompt
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.repository.SettingsRepository
import com.mkx.hrttracker.model.settings.AppLockGracePeriodOption
import com.mkx.hrttracker.model.settings.SecuritySettingsState
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.util.AppLockSecurityManager
import com.mkx.hrttracker.util.ElapsedRealtimeProvider
import com.mkx.hrttracker.util.appLanguageLocale
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.Locale

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
        every { settingsRepository.securitySettingsState } returns settingsStateFlow.map {
            SecuritySettingsState.Loaded(
                screenLockProtectionEnabled = it.screenLockProtectionEnabled,
                appLockGracePeriodOption = it.appLockGracePeriodOption,
                hideScreenContentEnabled = it.hideScreenContentEnabled,
            )
        }
        every { appLockSecurityManager.availabilityErrorMessageRes() } returns null
        every { appLockSecurityManager.credentialScreenStopsActivity() } returns true
        every { appLockSecurityManager.promptErrorMessageRes(any()) } answers {
            AppLockSecurityManager(mockk()).promptErrorMessageRes(firstArg())
        }
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
    fun foregroundedWithoutBackgrounding_keepsUnlockedAndDoesNotPrompt() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAuthenticationSucceeded()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isUnlocked)
        assertNull(viewModel.uiState.value.pendingPrompt)

        // Simulates Activity recreation from a configuration change (language /
        // dark mode toggle): onBackgrounded was skipped because
        // isChangingConfigurations was true.
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

    @Test
    fun stopForTheSystemCredentialScreen_keepsThePromptAndAcceptsItsSuccess() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val prompt = viewModel.uiState.value.pendingPrompt

        // Before API 30 the PIN screen is a separate activity: MainActivity stops, then starts
        // again when the PIN is entered, before the result arrives.
        viewModel.onBackgrounded()
        advanceUntilIdle()
        assertEquals("Stop dropped the prompt in flight", prompt, viewModel.uiState.value.pendingPrompt)

        viewModel.onForegrounded()
        advanceUntilIdle()
        assertEquals("Start asked again over the prompt in flight", prompt, viewModel.uiState.value.pendingPrompt)

        viewModel.onAuthenticationSucceeded()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isUnlocked)
        assertNull(viewModel.uiState.value.pendingPrompt)
    }

    @Test
    fun realLeaveWhoseAuthIsCancelled_promptsAgainOnReturn() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val leftPrompt = viewModel.uiState.value.pendingPrompt!!

        // Home with the prompt up: the system cancels it, and the cancel lands once
        // MainActivity has started again.
        viewModel.onBackgrounded()
        advanceUntilIdle()
        viewModel.onForegrounded()
        advanceUntilIdle()
        viewModel.onAuthenticationError(BiometricPrompt.ERROR_CANCELED)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Unlocked without auth: $state", state.shouldShowLockScreen)
        assertNotNull("No prompt on return: $state", state.pendingPrompt)
        assertTrue("Prompt on return is the cancelled one: $state", state.pendingPrompt!!.id > leftPrompt.id)
        assertNull(state.errorMessageRes)
    }

    @Test
    fun userCancelAcrossAStop_keepsTheAppLockedWithoutAskingAgain() = runTest {
        // API 26-29: the confirm-credential screen is the prompt itself and stops MainActivity, so
        // its Cancel button, Back or a leave lands after a stop and start.
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onBackgrounded()
        advanceUntilIdle()
        viewModel.onForegrounded()
        advanceUntilIdle()
        viewModel.onAuthenticationError(BiometricPrompt.ERROR_USER_CANCELED)
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertTrue(state.shouldShowLockScreen)
        assertNull("Asked again after the user cancelled: $state", state.pendingPrompt)
        assertNull(state.errorMessageRes)

        // Leaving the lock screen and coming back asks, as before.
        viewModel.onBackgrounded()
        viewModel.onForegrounded()
        advanceUntilIdle()
        state = viewModel.uiState.value
        assertNotNull("No prompt on a later return: $state", state.pendingPrompt)
    }

    @Test
    fun userCancelLandingBeforeTheStart_keepsTheUnlockButton_andALaterReturnAsks() = runTest {
        // API 29: Back on the PIN screen, whose cancel reaches MainActivity before its start.
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onBackgrounded()
        advanceUntilIdle()
        viewModel.onAuthenticationError(BiometricPrompt.ERROR_USER_CANCELED)
        viewModel.onForegrounded()
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertTrue(state.shouldShowLockScreen)
        assertNull("Back on the PIN screen asked again: $state", state.pendingPrompt)
        assertNull(state.errorMessageRes)

        // Leaving the lock screen and coming back asks, as before.
        viewModel.onBackgrounded()
        viewModel.onForegrounded()
        advanceUntilIdle()
        state = viewModel.uiState.value
        assertNotNull("No prompt on a later return: $state", state.pendingPrompt)
    }

    @Test
    fun cancelWithoutLeaving_keepsTheAppLockedWithoutAskingAgain() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAuthenticationError(BiometricPrompt.ERROR_USER_CANCELED)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.shouldShowLockScreen)
        assertNull(state.pendingPrompt)
        assertNull(state.errorMessageRes)
    }

    @Test
    fun lockoutAfterAStop_showsItsMessageWithoutAskingAgain() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onBackgrounded()
        advanceUntilIdle()
        viewModel.onForegrounded()
        advanceUntilIdle()
        viewModel.onAuthenticationError(BiometricPrompt.ERROR_LOCKOUT)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.shouldShowLockScreen)
        assertNull(state.pendingPrompt)
        assertEquals(R.string.screen_lock_auth_locked_out, state.errorMessageRes)
    }

    @Test
    fun systemCancelWithoutAStop_keepsTheAppLockedWithoutAskingAgain() = runTest {
        // API 29: Cancel on the system fingerprint dialog comes back as ERROR_CANCELED, and
        // MainActivity is only paused under it.
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAuthenticationError(BiometricPrompt.ERROR_CANCELED)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.shouldShowLockScreen)
        assertNull("Asked again after a cancel without leaving: $state", state.pendingPrompt)
        assertNull(state.errorMessageRes)
    }

    @Test
    fun settingsToggleCredentialScreenStop_doesNotAskAgainAndItsSuccessUnlocks() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onAuthenticationSucceeded()
        advanceUntilIdle()

        // Turning the lock off: before API 30 the toggle's PIN screen stops MainActivity.
        viewModel.onOtherPromptStarted()
        viewModel.onBackgrounded()
        advanceUntilIdle()
        viewModel.onForegrounded()
        advanceUntilIdle()
        assertNull(
            "Asked to unlock over the toggle's own PIN screen: ${viewModel.uiState.value}",
            viewModel.uiState.value.pendingPrompt,
        )

        viewModel.onOtherPromptEnded(succeeded = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Toggle's PIN did not count as an unlock: $state", state.isUnlocked)
        assertNull(state.pendingPrompt)
    }

    @Test
    fun settingsToggleCancelledAcrossAStop_endsLockedWithAPrompt() = runTest {
        // Back on the toggle's PIN screen can't be told from Home and coming back to cancel it.
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onAuthenticationSucceeded()
        advanceUntilIdle()

        viewModel.onOtherPromptStarted()
        viewModel.onBackgrounded()
        advanceUntilIdle()
        viewModel.onForegrounded()
        advanceUntilIdle()
        viewModel.onOtherPromptEnded(succeeded = false)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Unlocked without auth: $state", state.shouldShowLockScreen)
        assertNotNull("Locked without a prompt: $state", state.pendingPrompt)
    }

    @Test
    fun whereTheCredentialScreenDoesNotStopTheApp_aStopStillDropsThePromptAndReturnAsksAgain() = runTest {
        // API 30+: the credential screen is part of the system prompt, so a stop is a real leave.
        every { appLockSecurityManager.credentialScreenStopsActivity() } returns false
        val viewModel = createViewModel()
        advanceUntilIdle()
        val leftPrompt = viewModel.uiState.value.pendingPrompt!!

        viewModel.onBackgrounded()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.pendingPrompt)

        viewModel.onForegrounded()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.pendingPrompt!!.id > leftPrompt.id)
    }

    // The tests below run a real SettingsRepository over [ScriptedSettingsDataStore], so they
    // state what is stored and which reads fail or wait, not which repository member the VM reads.
    // Read #1 is always the repository's eager settingsState read; the VM's reads come after.

    @Test
    fun storedLock_staysOnWhileSettingsStateStillHoldsItsPlaceholder() = runTest {
        val dataStore = ScriptedSettingsDataStore(heldReads = mapOf(1 to CompletableDeferred()))
        val repository = realSettingsRepository(dataStore)
        repository.setScreenLockProtectionEnabled(true)
        dataStore.readsStarted.first { it >= 1 }

        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        assertTrue(
            "App content shown while the stored lock is on: ${viewModel.uiState.value}",
            viewModel.uiState.value.shouldShowLockScreen,
        )
    }

    @Test
    fun storedLock_staysOnAfterTheSettingsStateReadFails() = runTest {
        val dataStore = ScriptedSettingsDataStore(failingReads = setOf(1))
        val repository = realSettingsRepository(dataStore)
        repository.setScreenLockProtectionEnabled(true)
        dataStore.readsStarted.first { it >= 1 }

        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        assertTrue(
            "App content shown after a failed settings read while the stored lock is on: " +
                    "${viewModel.uiState.value}",
            viewModel.uiState.value.shouldShowLockScreen,
        )
    }

    @Test
    fun storedHideScreenContent_keepsTheWindowSecureAfterTheSettingsStateReadFails() = runTest {
        val dataStore = ScriptedSettingsDataStore(failingReads = setOf(1))
        val repository = realSettingsRepository(dataStore)
        repository.setHideScreenContentEnabled(true)
        dataStore.readsStarted.first { it >= 1 }

        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        // MainActivity applies FLAG_SECURE from this value.
        assertTrue(
            "FLAG_SECURE input is off after a failed settings read while hide-content is stored on",
            viewModel.uiState.value.hideScreenContentEnabled,
        )
    }

    @Test
    fun failedLockRead_locksUntilARetriedReadFindsTheLockOff() = runTest {
        // Nothing stored (lock off). The lock's own read (#2) fails once; the retry succeeds.
        val dataStore = ScriptedSettingsDataStore(failingReads = setOf(2))
        val repository = realSettingsRepository(dataStore)
        dataStore.readsStarted.first { it >= 1 }

        val viewModel = createViewModel(repository)
        runCurrent()

        assertTrue(
            "No locked decision while the lock setting could not be read: ${viewModel.uiState.value}",
            viewModel.uiState.value.shouldShowLockScreen,
        )

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isReady)
        assertFalse(
            "Still locked after a retried read found the lock off: ${viewModel.uiState.value}",
            viewModel.uiState.value.shouldShowLockScreen,
        )
    }

    @Test
    fun backgroundingBeforeTheLockDecision_doesNotStartAGracePeriod() = runTest {
        val lockRead = CompletableDeferred<Unit>()
        val dataStore = ScriptedSettingsDataStore(heldReads = mapOf(2 to lockRead))
        val repository = realSettingsRepository(dataStore)
        repository.setScreenLockProtectionEnabled(true)
        repository.setAppLockGracePeriodOption(AppLockGracePeriodOption.ONE_MINUTE)
        // settingsState already says on while the lock's own read (#2) is still pending.
        repository.settingsState.first {
            it.screenLockProtectionEnabled &&
                    it.appLockGracePeriodOption == AppLockGracePeriodOption.ONE_MINUTE
        }
        every { elapsedRealtimeProvider.now() } returnsMany listOf(1_000L, 31_000L)

        val viewModel = createViewModel(repository)
        advanceUntilIdle()
        viewModel.onBackgrounded()
        lockRead.complete(Unit)
        advanceUntilIdle()
        // Back within the grace period, without ever having authenticated.
        viewModel.onForegrounded()
        advanceUntilIdle()

        assertTrue(
            "Grace period unlocked a session that never authenticated: ${viewModel.uiState.value}",
            viewModel.uiState.value.shouldShowLockScreen,
        )
    }

    private fun realSettingsRepository(dataStore: DataStore<Preferences>): SettingsRepository {
        mockkStatic("com.mkx.hrttracker.util.LocalizationKt")
        every { appLanguageLocale() } returns Locale.ENGLISH
        return SettingsRepository(dataStore)
    }

    private fun createViewModel(
        repository: SettingsRepository = settingsRepository,
    ): AppLockViewModel {
        return AppLockViewModel(
            settingsRepository = repository,
            appLockSecurityManager = appLockSecurityManager,
            elapsedRealtimeProvider = elapsedRealtimeProvider,
        )
    }
}

/**
 * A settings store whose reads are numbered in the order they start, so a test can make a read
 * fail with an IOException or wait until released.
 */
private class ScriptedSettingsDataStore(
    private val failingReads: Set<Int> = emptySet(),
    private val heldReads: Map<Int, CompletableDeferred<Unit>> = emptyMap(),
) : DataStore<Preferences> {
    private val stored = MutableStateFlow(emptyPreferences())
    val readsStarted = MutableStateFlow(0)

    override val data: Flow<Preferences> = flow {
        val read = readsStarted.updateAndGet { it + 1 }
        heldReads[read]?.await()
        if (read in failingReads) throw IOException("settings read $read failed")
        emitAll(stored)
    }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(stored.value).also { stored.value = it }
}
