package com.mkx.hrttracker.ui.security

import android.app.Activity
import android.app.KeyguardManager
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.fragment.app.FragmentActivity
import com.mkx.hrttracker.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * On API 29 BiometricPrompt loses a PIN result: when "Use PIN" is tapped, Settings reports
 * ERROR_CANCELED to the app, and BiometricService then drops the PIN success ("onCDCASuccess
 * null!"). The confirm-credential screen offers the fingerprint too and returns its result.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AppAuthenticationPromptTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val authenticated = mutableListOf<Unit>()
    private val errors = mutableListOf<Int>()

    @Test
    fun api29_promptsWithTheConfirmCredentialScreenAndItsOkAuthenticates() {
        val activity = launchPrompt()

        val started = shadowOf(activity).nextStartedActivityForResult
        assertNotNull("API 29 did not open the confirm-credential screen, errors: $errors", started)
        assertEquals("android.app.action.CONFIRM_DEVICE_CREDENTIAL", started?.intent?.action)

        shadowOf(activity).receiveResult(started!!.intent, Activity.RESULT_OK, null)

        assertEquals(1, authenticated.size)
        assertEquals(emptyList<Int>(), errors)
    }

    @Test
    fun api29_aConfirmCredentialScreenClosedWithoutOkIsACancel() {
        val activity = launchPrompt()
        val started = shadowOf(activity).nextStartedActivityForResult
        assertNotNull("API 29 did not open the confirm-credential screen, errors: $errors", started)

        shadowOf(activity).receiveResult(started!!.intent, Activity.RESULT_CANCELED, null)

        assertEquals(emptyList<Unit>(), authenticated)
        // It gives no reason: Back, or Home closing it. Not USER_CANCELED, so the lock can ask
        // again after a leave.
        assertEquals(listOf(BiometricPrompt.ERROR_CANCELED), errors)
    }

    @Test
    @Config(sdk = [30])
    fun api30_promptsWithBiometricPromptNotTheConfirmCredentialScreen() {
        val activity = launchPrompt()

        val started = shadowOf(activity).nextStartedActivityForResult
        assertNotEquals(
            "API 30 opened the confirm-credential screen",
            "android.app.action.CONFIRM_DEVICE_CREDENTIAL",
            started?.intent?.action,
        )
    }

    private fun launchPrompt(): FragmentActivity {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        shadowOf(activity.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(true)
        activity.setContent {
            AppAuthenticationPromptEffect(
                request = AuthenticationPromptRequest(
                    id = 1,
                    titleRes = R.string.unlock_app_prompt_title,
                    subtitleRes = R.string.unlock_app_prompt_subtitle,
                ),
                onAuthenticated = { authenticated += Unit },
                onError = { errors += it },
            )
        }
        composeRule.waitForIdle()
        return activity
    }
}
