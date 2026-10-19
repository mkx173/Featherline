package com.mkx.hrttracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupPasswordDialog(
    title: String,
    message: String,
    confirmLabel: String,
    passwordLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isInProgress: Boolean = false,
    minimumPasswordLength: Int = 0,
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showValidationErrors by remember { mutableStateOf(false) }
    val validationError = validateBackupPasswordInput(
        password = password,
        minimumPasswordLength = minimumPasswordLength,
    )
    val submit = {
        if (validationError == null) {
            onConfirm(password)
        } else {
            showValidationErrors = true
        }
    }
    val passwordValidationMessage = if (!showValidationErrors) {
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

            null -> null
        }
    }

    HazeBasicAlertDialog(
        modifier = modifier,
        // Block outside-tap / back-press while a restore is mid-flight —
        // dismissing would let the surrounding screen wipe the encrypted
        // bytes that the in-progress restore is still decrypting from.
        onDismissRequest = { if (!isInProgress) onDismiss() },
    ) {
        HazeDialogSurface(
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight(),
            shape = AlertDialogDefaults.shape,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(
                modifier = Modifier.padding(dimensionResource(R.dimen.padding_large)),
                verticalArrangement = Arrangement.spacedBy(
                    dimensionResource(R.dimen.padding_medium)
                ),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = AlertDialogDefaults.titleContentColor,
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(
                        dimensionResource(R.dimen.padding_small)
                    )
                ) {
                    Text(
                        text = message,
                        color = AlertDialogDefaults.textContentColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    BackupPasswordField(
                        value = password,
                        onValueChange = {
                            password = it
                            showValidationErrors = false
                        },
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
                        onImeAction = {
                            if (!isInProgress) {
                                submit()
                            }
                        },
                    )
                }
                // Buttons stay visually enabled while a restore/backup is in
                // flight; the click handlers no-op so a double-tap can't fire
                // a duplicate request or dismiss the encrypted buffer mid-
                // decrypt (dialog dismissal is gated above).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { if (!isInProgress) onDismiss() }) {
                        Text(text = stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { if (!isInProgress) submit() }) {
                        Text(text = confirmLabel)
                    }
                }
            }
        }
    }
}

internal fun validateBackupPasswordInput(
    password: String,
    minimumPasswordLength: Int,
): BackupPasswordValidationError? {
    if (password.isEmpty()) {
        return BackupPasswordValidationError.REQUIRED
    }
    if (password.length < minimumPasswordLength) {
        return BackupPasswordValidationError.TOO_SHORT
    }
    return null
}

internal enum class BackupPasswordValidationError {
    REQUIRED,
    TOO_SHORT,
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
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
) {
    OutlinedTextField(
        modifier = modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        singleLine = true,
        isError = isError,
        supportingText = supportingText,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onNext = { onImeAction() },
            onDone = { onImeAction() },
        ),
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
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
