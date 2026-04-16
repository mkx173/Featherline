package com.mkx.hrttracker.ui.security

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

@Composable
fun AppAuthenticationPromptEffect(
    request: AuthenticationPromptRequest?,
    onAuthenticated: () -> Unit,
    onError: (Int) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity ?: return
    val currentOnAuthenticated by rememberUpdatedState(onAuthenticated)
    val currentOnError by rememberUpdatedState(onError)

    LaunchedEffect(request?.id) {
        val currentRequest = request ?: return@LaunchedEffect
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(currentRequest.titleRes))
            .setAllowedAuthenticators(AppLockSecurityManager.ALLOWED_AUTHENTICATORS)
            .apply {
                currentRequest.subtitleRes?.let { setSubtitle(activity.getString(it)) }
                currentRequest.descriptionRes?.let { setDescription(activity.getString(it)) }
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
