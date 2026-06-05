package com.mkx.hrttracker.ui.security

import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.StringRes
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
)

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

    LaunchedEffect(request?.id) {
        val currentRequest = request ?: return@LaunchedEffect
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
