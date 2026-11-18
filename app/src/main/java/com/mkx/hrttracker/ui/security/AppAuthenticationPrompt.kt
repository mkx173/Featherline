package com.mkx.hrttracker.ui.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.mkx.hrttracker.util.AppLockSecurityManager

data class AuthenticationPromptRequest(
    val id: Long,
    @param:StringRes val titleRes: Int,
    @param:StringRes val subtitleRes: Int? = null,
    @param:StringRes val descriptionRes: Int? = null,
) {
    /**
     * Its confirm-credential screen was launched. The ViewModel keeps this instance across a
     * recreation, so a host recreated under the screen (on API 29 a fingerprint makes it
     * transparent, and the host is only paused) waits for its result instead of opening a second
     * one. On the instance, not in saved state: ids restart after a process death, and the new
     * process's request must open its own screen.
     */
    internal var launched = false
}

// Strings are resolved inside LaunchedEffect off the localized LocalContext (see
// the body comments), where stringResource() isn't callable. The
// LocalContextGetResourceValueCall lint can't model that effect-scoped, in-app
// locale usage and false-positives here.
@Suppress("LocalContextGetResourceValueCall")
@Composable
fun AppAuthenticationPromptEffect(
    request: AuthenticationPromptRequest?,
    onAuthenticated: () -> Unit,
    onError: (Int) -> Unit,
) {
    val context = LocalContext.current
    // LocalContext is a ContextWrapper that serves the in-app locale's resources
    // (provided by MainActivity), so a direct cast to FragmentActivity fails.
    // Walk the base-context chain to reach the hosting activity.
    val activity = context.findFragmentActivity() ?: return
    val currentOnAuthenticated by rememberUpdatedState(onAuthenticated)
    val currentOnError by rememberUpdatedState(onError)
    val latestRequest by rememberUpdatedState(request)
    // A recreated activity gets the confirm screen's result back when the launcher registers,
    // before LaunchedEffect runs: don't open a second screen for a request it already answered.
    val answeredRequestId = remember { mutableStateOf<Long?>(null) }
    val confirmCredential = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        answeredRequestId.value = latestRequest?.id
        if (result.resultCode == Activity.RESULT_OK) {
            currentOnAuthenticated()
        } else {
            // Back, or Home closing it: no reason is given. A user cancel, as androidx.biometric
            // reports the same screen on API 26-28, so the lock screen stays with its Unlock
            // button rather than asking again: with no fingerprint, Back would only reopen it.
            currentOnError(BiometricPrompt.ERROR_USER_CANCELED)
        }
    }

    LaunchedEffect(request?.id) {
        val currentRequest = request ?: return@LaunchedEffect
        if (AppLockSecurityManager.USES_CONFIRM_CREDENTIAL_SCREEN) {
            if (answeredRequestId.value == currentRequest.id || currentRequest.launched) {
                return@LaunchedEffect
            }
            @Suppress("DEPRECATION") // In favour of BiometricPrompt, which drops the PIN on API 29.
            val intent = activity.getSystemService(KeyguardManager::class.java)
                ?.createConfirmDeviceCredentialIntent(
                    context.getString(currentRequest.titleRes),
                    (currentRequest.subtitleRes ?: currentRequest.descriptionRes)
                        ?.let { context.getString(it) },
                )
            if (intent == null) {
                currentOnError(BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL)
            } else {
                currentRequest.launched = true
                confirmCredential.launch(intent)
            }
            return@LaunchedEffect
        }

        // Resolve prompt strings through the localized context, not the raw
        // activity, so the prompt honors the in-app language selection.
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(currentRequest.titleRes))
            .setAllowedAuthenticators(AppLockSecurityManager.ALLOWED_AUTHENTICATORS)
            .apply {
                currentRequest.subtitleRes?.let { setSubtitle(context.getString(it)) }
                currentRequest.descriptionRes?.let { setDescription(context.getString(it)) }
            }
            .build()

        val biometricPrompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    currentOnError(errorCode)
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    currentOnAuthenticated()
                }
            }
        )

        biometricPrompt.authenticate(promptInfo)
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
