package com.mkx.hrttracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.mkx.hrttracker.R

@Composable
fun BackupPasswordDialog(
    title: String,
    message: String,
    confirmLabel: String,
    passwordLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    warningMessage: String? = null,
    confirmPasswordLabel: String? = null,
    isInProgress: Boolean = false,
    minimumPasswordLength: Int = 0,
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var hasAttemptedSubmit by remember { mutableStateOf(false) }
    val requireConfirmation = confirmPasswordLabel != null
    val validationError = validateBackupPasswordInput(
        password = password,
        confirmPassword = confirmPassword.takeIf { requireConfirmation },
        minimumPasswordLength = minimumPasswordLength,
    )
    val passwordValidationMessage = if (!hasAttemptedSubmit) {
        null
    } else {
        when (validationError) {
            BackupPasswordValidationError.REQUIRED ->
                stringResource(R.string.settings_backup_password_required)

            BackupPasswordValidationError.TOO_SHORT ->
                stringResource(
                    R.string.settings_backup_password_min_length,
                    minimumPasswordLength,
                )

            BackupPasswordValidationError.MISMATCH,
            null,
            -> null
        }
    }
    val confirmPasswordValidationMessage = if (
        hasAttemptedSubmit &&
        validationError == BackupPasswordValidationError.MISMATCH
    ) {
        stringResource(R.string.settings_backup_password_mismatch)
    } else {
        null
    }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(
                    dimensionResource(R.dimen.padding_small)
                )
            ) {
                Text(text = message)
                warningMessage?.let { warningText ->
                    Text(
                        text = warningText,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                BackupPasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = passwordLabel,
                    passwordVisible = passwordVisible,
                    onToggleVisibility = { passwordVisible = !passwordVisible },
                    supportingText = passwordValidationMessage?.let { message ->
                        {
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    isError = passwordValidationMessage != null,
                )
                confirmPasswordLabel?.let { label ->
                    BackupPasswordField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = label,
                        passwordVisible = confirmPasswordVisible,
                        onToggleVisibility = {
                            confirmPasswordVisible = !confirmPasswordVisible
                        },
                        supportingText = confirmPasswordValidationMessage?.let { message ->
                            {
                                Text(
                                    text = message,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                        isError = confirmPasswordValidationMessage != null,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isInProgress,
                onClick = {
                    hasAttemptedSubmit = true
                    if (validationError == null) {
                        onConfirm(password)
                    }
                },
            ) {
                Text(text = confirmLabel)
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isInProgress,
                onClick = onDismiss,
            ) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

internal fun validateBackupPasswordInput(
    password: String,
    confirmPassword: String?,
    minimumPasswordLength: Int,
): BackupPasswordValidationError? {
    if (password.isEmpty()) {
        return BackupPasswordValidationError.REQUIRED
    }
    if (password.length < minimumPasswordLength) {
        return BackupPasswordValidationError.TOO_SHORT
    }
    if (confirmPassword != null && password != confirmPassword) {
        return BackupPasswordValidationError.MISMATCH
    }
    return null
}

internal enum class BackupPasswordValidationError {
    REQUIRED,
    TOO_SHORT,
    MISMATCH,
}

@Composable
private fun BackupPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    passwordVisible: Boolean,
    onToggleVisibility: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
) {
    OutlinedTextField(
        modifier = modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        singleLine = true,
        isError = isError,
        supportingText = supportingText,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (passwordVisible) {
            androidx.compose.ui.text.input.VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (passwordVisible) {
                        Icons.Rounded.VisibilityOff
                    } else {
                        Icons.Rounded.Visibility
                    },
                    contentDescription = null,
                )
            }
        }
    )
}
