package com.mkx.hrttracker.util

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * androidx.biometric 1.1.0 rejects BIOMETRIC_STRONG | DEVICE_CREDENTIAL on API 28-29:
 * canAuthenticate reports UNSUPPORTED and PromptInfo.Builder.build() throws. The app lock (and
 * the Settings lock toggle, which uses the same check and prompt) must still authenticate there.
 */
@RunWith(RobolectricTestRunner::class)
class AppLockSecurityManagerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    @Config(sdk = [29])
    fun api29WithAScreenLock_unlockIsAvailable() {
        shadowOf(context.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(true)

        assertNull(
            "API 29 with a device PIN reported the app lock as unavailable",
            AppLockSecurityManager(context).availabilityErrorMessageRes(),
        )
    }

    @Test
    @Config(sdk = [29])
    fun api29_unlockPromptAcceptsItsAuthenticatorsAndAllowsTheDeviceCredential() {
        val promptInfo = runCatching { unlockPromptInfo() }

        assertTrue(
            "API 29 rejected the unlock prompt: ${promptInfo.exceptionOrNull()}",
            promptInfo.isSuccess,
        )
        assertNotEquals(
            0,
            promptInfo.getOrThrow().allowedAuthenticators and Authenticators.DEVICE_CREDENTIAL,
        )
    }

    @Test
    @Config(sdk = [34])
    fun api30AndLater_keepRequiringAStrongBiometricOrTheDeviceCredential() {
        assertEquals(
            Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL,
            unlockPromptInfo().allowedAuthenticators,
        )
    }

    // Built the way AppAuthenticationPromptEffect builds it.
    private fun unlockPromptInfo(): BiometricPrompt.PromptInfo =
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock")
            .setAllowedAuthenticators(AppLockSecurityManager.ALLOWED_AUTHENTICATORS)
            .build()
}
