package com.mkx.hrttracker.ui.settings

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.view.WindowCompat
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.backup.RecoveryKey
import com.mkx.hrttracker.ui.components.HazeBasicAlertDialog
import com.mkx.hrttracker.ui.components.HazeDialogSurface
import com.mkx.hrttracker.ui.components.NavigationLockEffect
import com.mkx.hrttracker.ui.components.rememberModalWindowAllowed

/**
 * Flow A: shows the new recovery key. A secure full-screen dialog, so
 * screenshots, recording and the recents thumbnail are blocked on this window
 * only; the activity's own "hide screen content" setting is untouched.
 */
@Composable
internal fun BackupKeySetupDialog(
    state: BackupKeySetupUiState,
    isInProgress: Boolean,
    onCancel: () -> Unit,
    onSaved: () -> Unit,
) {
    if (!rememberModalWindowAllowed(onDismissRequest = onCancel)) {
        return
    }
    NavigationLockEffect(active = true)
    val cancel = { if (!isInProgress) onCancel() }
    Dialog(onDismissRequest = cancel, properties = SecureFullScreenDialogProperties) {
        // The dialog window draws behind the bars with its own appearance,
        // which defaults to light icons; match the surface instead.
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        val lightBars = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        SideEffect {
            dialogWindow?.let { window ->
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = lightBars
                    isAppearanceLightNavigationBars = lightBars
                }
            }
        }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.systemBarsPadding()) {
                IconButton(onClick = cancel, modifier = Modifier.padding(4.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cancel),
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_backup_key_reveal_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = stringResource(R.string.settings_backup_key_reveal_message),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    RecoveryKeyReveal(recoveryKey = state.recoveryKey)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = cancel) {
                        Text(text = stringResource(R.string.cancel))
                    }
                    Button(onClick = onSaved, enabled = !isInProgress) {
                        if (isInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = LocalContentColor.current,
                            )
                        } else {
                            Text(text = stringResource(R.string.settings_backup_key_saved))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecoveryKeyReveal(recoveryKey: String) {
    val context = LocalContext.current
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val formattedKey = RecoveryKey.format(recoveryKey)
    val copiedMessage = stringResource(R.string.settings_backup_key_copied)
    Surface(
        modifier = Modifier.padding(top = 4.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics { contentDescription = formattedKey },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                formattedKey.split('-').forEach { group ->
                    Text(
                        text = group,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            FilledTonalButton(
                onClick = {
                    clipboard?.setPrimaryClip(recoveryKeyClip(formattedKey))
                    // Android 13+ confirms a copy itself.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(
                            context,
                            copiedMessage,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_content_copy),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.settings_backup_key_copy))
            }
        }
    }
    BackupKeyNote(
        painter = rememberVectorPainter(Icons.Outlined.Shield),
        text = stringResource(R.string.settings_backup_key_reveal_save_hint),
    )
    BackupKeyNote(
        painter = painterResource(R.drawable.ic_lock),
        text = stringResource(R.string.settings_backup_key_screenshots_off),
        small = true,
    )
}

@Composable
private fun BackupKeyNote(
    painter: Painter,
    text: String,
    small: Boolean = false,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Flow C1: opens a v4 file with its recovery key. Nothing secret is shown. */
@Composable
internal fun BackupUnlockDialog(
    displayName: String?,
    state: BackupRestoreKeyUiState,
    onInputChange: (String) -> Unit,
    onUnlock: () -> Unit,
    onCancel: () -> Unit,
) {
    val canUnlock = RecoveryKey.parse(state.input) != null && !state.isUnlocking
    BackupRestoreKeyDialog(
        title = stringResource(R.string.settings_backup_unlock_title),
        confirmLabel = stringResource(R.string.settings_backup_unlock),
        isConfirmEnabled = canUnlock,
        isInProgress = state.isUnlocking,
        // A cancel mid-unwrap is safe: the ViewModel drops the late result.
        onConfirm = onUnlock,
        onCancel = onCancel,
    ) {
        displayName?.let { name ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = stringResource(R.string.settings_backup_unlock_message),
            style = MaterialTheme.typography.bodyMedium,
            color = AlertDialogDefaults.textContentColor,
        )
        RecoveryKeyField(
            value = state.input,
            onValueChange = onInputChange,
            errorMessage = stringResource(R.string.settings_backup_unlock_wrong_key)
                .takeIf { state.isWrongKey },
            onDone = { if (canUnlock) onUnlock() },
        )
    }
}

/**
 * Flow C2: the v4 confirm, with no password field. On a No-key phone it
 * offers to keep the file's recovery key ([BackupRestoreKeyUiState.canAdopt]).
 */
@Composable
internal fun BackupRestoreConfirmDialog(
    displayName: String?,
    state: BackupRestoreKeyUiState,
    isInProgress: Boolean,
    onAdoptChange: (Boolean) -> Unit,
    onRestore: () -> Unit,
    onCancel: () -> Unit,
) {
    BackupRestoreKeyDialog(
        title = stringResource(R.string.settings_backup_restore_confirm_title),
        confirmLabel = stringResource(R.string.settings_backup_restore_confirm),
        isConfirmEnabled = true,
        isInProgress = isInProgress,
        // Locked while restoring: cancelling would wipe the bytes and MK the
        // restore is still reading.
        onConfirm = { if (!isInProgress) onRestore() },
        onCancel = { if (!isInProgress) onCancel() },
    ) {
        Text(
            text = if (displayName != null) {
                stringResource(R.string.settings_backup_restore_key_message_with_name, displayName)
            } else {
                stringResource(R.string.settings_backup_restore_key_message)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = AlertDialogDefaults.textContentColor,
        )
        if (state.canAdopt) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.toggleable(
                    value = state.adopt,
                    enabled = !isInProgress,
                    role = Role.Checkbox,
                    onValueChange = onAdoptChange,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Checkbox(checked = state.adopt, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_backup_restore_adopt_key),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(
                                if (state.adopt) {
                                    R.string.settings_backup_restore_adopt_key_on
                                } else {
                                    R.string.settings_backup_restore_adopt_key_off
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupRestoreKeyDialog(
    title: String,
    confirmLabel: String,
    isConfirmEnabled: Boolean,
    isInProgress: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    content: @Composable () -> Unit,
) {
    HazeBasicAlertDialog(onDismissRequest = onCancel) {
        HazeDialogSurface {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(dimensionResource(R.dimen.padding_large)),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_medium)),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = AlertDialogDefaults.titleContentColor,
                )
                content()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onCancel) {
                        Text(text = stringResource(R.string.cancel))
                    }
                    TextButton(onClick = onConfirm, enabled = isConfirmEnabled) {
                        if (isInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = LocalContentColor.current,
                            )
                        } else {
                            Text(text = confirmLabel)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The recovery key input for unlock (C1). Not
 * masked, since typos have to be visible. The value is the bare symbols
 * ([RecoveryKey.symbols]); [RecoveryKeyVisualTransformation] shows the groups.
 * [trailingActions] sits before Paste (C1's Scan).
 */
@Composable
internal fun RecoveryKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    onDone: () -> Unit = {},
    trailingActions: @Composable RowScope.() -> Unit = {},
) {
    val context = LocalContext.current
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(RecoveryKey.symbols(it)) },
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentType = ContentType.Password },
        label = { Text(text = stringResource(R.string.settings_backup_key_field_label)) },
        textStyle = LocalTextStyle.current.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        ),
        singleLine = true,
        isError = errorMessage != null,
        supportingText = {
            Text(text = errorMessage ?: stringResource(R.string.settings_backup_key_field_helper))
        },
        // Password keeps the keyboard from learning or suggesting the key.
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        visualTransformation = RecoveryKeyVisualTransformation,
        trailingIcon = {
            Row {
                trailingActions()
                IconButton(
                    onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.primaryClip
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)
                            ?.coerceToText(context)
                            ?.let { onValueChange(RecoveryKey.symbols(it.toString())) }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentPaste,
                        contentDescription = stringResource(R.string.settings_backup_key_paste),
                    )
                }
            }
        },
    )
}

/** Shows `XXXX-XXXX-XXXX-XXXX` while the value stays the bare symbols. */
internal object RecoveryKeyVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val length = text.length
        return TransformedText(
            AnnotatedString(RecoveryKey.format(text.text)),
            object : OffsetMapping {
                // A dash follows every 4th symbol that has one after it; a
                // cursor right after a group stays before its dash.
                override fun originalToTransformed(offset: Int): Int =
                    offset + if (offset > 0) (offset - 1) / GROUP_LENGTH else 0

                // Each group plus its dash is 5 characters wide.
                override fun transformedToOriginal(offset: Int): Int =
                    (offset - offset / (GROUP_LENGTH + 1)).coerceAtMost(length)
            },
        )
    }

    private const val GROUP_LENGTH = 4
}

private const val RECOVERY_KEY_CLIP_LABEL = "featherline-recovery-key"

// The sensitive extra keeps clipboard previews and keyboard clipboard history
// from showing the key. Below API 33 the constant doesn't exist; the literal
// is what it resolves to and what keyboards check.
private fun recoveryKeyClip(text: String): ClipData =
    ClipData.newPlainText(RECOVERY_KEY_CLIP_LABEL, text).apply {
        description.extras = PersistableBundle().apply {
            putBoolean(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ClipDescription.EXTRA_IS_SENSITIVE
                } else {
                    "android.content.extra.IS_SENSITIVE"
                },
                true,
            )
        }
    }

private val SecureFullScreenDialogProperties = DialogProperties(
    securePolicy = SecureFlagPolicy.SecureOn,
    usePlatformDefaultWidth = false,
    // Edge to edge like the activity; the content pads for the bars.
    decorFitsSystemWindows = false,
)
