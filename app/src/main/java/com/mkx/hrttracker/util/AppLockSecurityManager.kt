package com.mkx.hrttracker.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import com.mkx.hrttracker.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLockSecurityManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun availabilityErrorMessageRes(): Int? {
        return when (BiometricManager.from(context).canAuthenticate(ALLOWED_AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> null
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                R.string.screen_lock_auth_unavailable_no_hardware

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                R.string.screen_lock_auth_unavailable_hw

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                R.string.screen_lock_auth_unavailable_none_enrolled

            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                R.string.screen_lock_auth_unavailable_security_update

            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED ->
                R.string.screen_lock_auth_unavailable_generic

            BiometricManager.BIOMETRIC_STATUS_UNKNOWN ->
                R.string.screen_lock_auth_unavailable_generic

            else -> R.string.screen_lock_auth_unavailable_generic
        }
    }

    fun promptErrorMessageRes(errorCode: Int): Int? {
        return when (errorCode) {
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_CANCELED -> null

            BiometricPrompt.ERROR_HW_UNAVAILABLE ->
                R.string.screen_lock_auth_unavailable_hw

            BiometricPrompt.ERROR_NO_BIOMETRICS,
            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL ->
                R.string.screen_lock_auth_unavailable_none_enrolled

            BiometricPrompt.ERROR_LOCKOUT,
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                R.string.screen_lock_auth_locked_out

            else -> R.string.screen_lock_auth_failed
        }
    }

    companion object {
        const val ALLOWED_AUTHENTICATORS: Int =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}
