package com.mkx.hrttracker.ui.settings

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.QrCodeScanner
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.mkx.hrttracker.data.backup.SecurityKeyStatus
import com.mkx.hrttracker.ui.components.HazeModalBottomSheet
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.data.backup.RecoveryKey
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.mkx.hrttracker.ui.components.HazeAlertDialog
import com.mkx.hrttracker.ui.components.HazeBasicAlertDialog
import com.mkx.hrttracker.ui.components.HazeDialogSurface
import com.mkx.hrttracker.ui.components.NavigationLockEffect
import com.mkx.hrttracker.ui.components.rememberModalWindowAllowed
import androidx.compose.ui.tooling.preview.Preview
import com.mkx.hrttracker.data.backup.KeyringCrypto
import com.mkx.hrttracker.data.backup.KeyringSlot
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.floor

/**
 * Flow A: shows the new recovery key; E2 Replace reuses it titled "Your new
 * recovery key" ([BackupKeySetupUiState.isReplace]).
 */
@Composable
internal fun BackupKeySetupDialog(
    state: BackupKeySetupUiState,
    isInProgress: Boolean,
    onCancel: () -> Unit,
    onSaved: () -> Unit,
) {
    val cancel = { if (!isInProgress) onCancel() }
    SecureKeyDialog(
        onClose = cancel,
        closeLabel = stringResource(R.string.cancel),
        actions = {
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
        },
    ) {
        Text(
            text = stringResource(
                if (state.isReplace) {
                    R.string.settings_backup_key_replace_reveal_title
                } else {
                    R.string.settings_backup_key_reveal_title
                }
            ),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(
                if (state.isReplace) {
                    R.string.settings_backup_key_replace_reveal_message
                } else {
                    R.string.settings_backup_key_reveal_message
                }
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RecoveryKeyReveal(
            recoveryKey = state.recoveryKey,
            hint = stringResource(R.string.settings_backup_key_reveal_save_hint),
        )
    }
}

/** Flow A, after I saved it: offers a passkey before the folder picker. Back is Not now. */
@Composable
internal fun BackupPasskeyOfferDialog(
    isInProgress: Boolean,
    onNotNow: () -> Unit,
    onAdd: () -> Unit,
) {
    val notNow = { if (!isInProgress) onNotNow() }
    SecureKeyDialog(
        onClose = notNow,
        closeLabel = stringResource(R.string.settings_backup_passkey_offer_not_now),
        actions = {
            TextButton(onClick = notNow, enabled = !isInProgress) {
                Text(text = stringResource(R.string.settings_backup_passkey_offer_not_now))
            }
            Button(onClick = onAdd, enabled = !isInProgress) {
                if (isInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current,
                    )
                } else {
                    Text(text = stringResource(R.string.settings_backup_add_passkey))
                }
            }
        },
    ) {
        Text(
            text = stringResource(R.string.settings_backup_passkey_offer_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.settings_backup_passkey_offer_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BackupKeyNote(
            painter = rememberVectorPainter(Icons.Outlined.Shield),
            text = stringResource(R.string.settings_backup_add_passkey_notice),
        )
        Text(
            text = stringResource(R.string.settings_backup_passkey_offer_later),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** E1 Show: the recovery key with its QR, for adding a phone (Flow D). */
@Composable
internal fun BackupRecoveryKeyDialog(
    recoveryKey: String,
    createdLabel: String?,
    onDone: () -> Unit,
) {
    SecureKeyDialog(
        onClose = onDone,
        closeLabel = stringResource(R.string.settings_backup_key_done),
        actions = {
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = onDone) {
                Text(text = stringResource(R.string.settings_backup_key_done))
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.settings_backup_recovery_key),
                style = MaterialTheme.typography.headlineMedium,
            )
            createdLabel?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        RecoveryKeyQrCode(
            recoveryKey = recoveryKey,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        RecoveryKeyReveal(
            recoveryKey = recoveryKey,
            hint = stringResource(R.string.settings_backup_key_show_hint),
        )
    }
}

/** E2: what Replace does, before the device credential. */
@Composable
internal fun BackupReplaceKeyDialog(
    onReplace: () -> Unit,
    onCancel: () -> Unit,
) {
    HazeAlertDialog(
        onDismissRequest = onCancel,
        title = { Text(text = stringResource(R.string.settings_backup_replace_key_title)) },
        text = { Text(text = stringResource(R.string.settings_backup_replace_key_message)) },
        confirmButton = {
            TextButton(onClick = onReplace) {
                Text(text = stringResource(R.string.settings_backup_replace_key_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}

/** E3 Add passkey: who else can open the backups. */
@Composable
internal fun BackupAddPasskeyNoticeDialog(
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    HazeAlertDialog(
        onDismissRequest = onCancel,
        title = { Text(text = stringResource(R.string.settings_backup_add_passkey)) },
        text = { Text(text = stringResource(R.string.settings_backup_add_passkey_notice)) },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text(text = stringResource(R.string.settings_backup_add_passkey_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}

/** E3 Remove passkey or security key, after the credential. */
@Composable
internal fun BackupRemoveSlotDialog(
    isSecurityKey: Boolean,
    onRemove: () -> Unit,
    onCancel: () -> Unit,
) {
    HazeAlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = stringResource(
                    if (isSecurityKey) R.string.settings_backup_remove_security_key_title else R.string.settings_backup_remove_passkey_title
                )
            )
        },
        text = {
            Text(
                text = stringResource(
                    if (isSecurityKey) R.string.settings_backup_remove_security_key_message else R.string.settings_backup_remove_passkey_message
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onRemove) {
                Text(text = stringResource(R.string.remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}

/** E2 re-link, per old passkey or security-key slot: Link wraps the new key, Skip drops the slot. */
@Composable
internal fun BackupRelinkSlotDialog(
    addedLabel: String,
    isSecurityKey: Boolean,
    isInProgress: Boolean,
    onLink: () -> Unit,
    onSkip: () -> Unit,
) {
    HazeAlertDialog(
        // Not dismissable: the sequence ends only through Link or Skip.
        onDismissRequest = {},
        title = {
            Text(
                text = stringResource(
                    if (isSecurityKey) R.string.settings_backup_relink_security_key_title else R.string.settings_backup_relink_passkey_title
                )
            )
        },
        text = {
            Text(
                text = stringResource(
                    if (isSecurityKey) R.string.settings_backup_relink_security_key_message else R.string.settings_backup_relink_passkey_message,
                    addedLabel,
                ) + "\n\n" + stringResource(R.string.settings_backup_relink_passkey_skip_note)
            )
        },
        confirmButton = {
            TextButton(onClick = onLink, enabled = !isInProgress) {
                if (isInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current,
                    )
                } else {
                    Text(
                        text = stringResource(
                            if (isSecurityKey) R.string.settings_backup_link_security_key else R.string.settings_backup_link_passkey
                        )
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip, enabled = !isInProgress) {
                Text(text = stringResource(R.string.settings_backup_relink_skip))
            }
        },
    )
}

/** E2 done: the new key is installed. Back up now runs Flow B. */
@Composable
internal fun BackupNewKeyActiveDialog(
    onBackUpNow: () -> Unit,
    onDone: () -> Unit,
) {
    HazeAlertDialog(
        onDismissRequest = onDone,
        title = { Text(text = stringResource(R.string.settings_backup_new_key_active_title)) },
        text = { Text(text = stringResource(R.string.settings_backup_new_key_active_message)) },
        confirmButton = {
            TextButton(onClick = onBackUpNow) {
                Text(text = stringResource(R.string.settings_backup_new_key_back_up_now))
            }
        },
        dismissButton = {
            TextButton(onClick = onDone) {
                Text(text = stringResource(R.string.settings_backup_key_done))
            }
        },
    )
}

/**
 * A secure full-screen dialog: screenshots, recording and the recents
 * thumbnail are blocked on this window only; the activity's own "hide screen
 * content" setting is untouched.
 */
@Composable
private fun SecureKeyDialog(
    onClose: () -> Unit,
    closeLabel: String,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Previews never resume, so the window gate would hide it: draw inline.
    if (LocalInspectionMode.current) {
        SecureKeyDialogLayout(onClose, closeLabel, actions, content)
        return
    }
    if (!rememberModalWindowAllowed(onDismissRequest = onClose)) {
        return
    }
    NavigationLockEffect(active = true)
    Dialog(onDismissRequest = onClose, properties = SecureFullScreenDialogProperties) {
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
        SecureKeyDialogLayout(onClose, closeLabel, actions, content)
    }
}

@Composable
private fun SecureKeyDialogLayout(
    onClose: () -> Unit,
    closeLabel: String,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.systemBarsPadding()) {
            IconButton(onClick = onClose, modifier = Modifier.padding(4.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = closeLabel,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

/**
 * Black on white in both themes with the quiet zone inside the white, since
 * some scanners fail on inverted codes. Fills the dialog width up to a cap.
 */
@Composable
private fun RecoveryKeyQrCode(
    recoveryKey: String,
    modifier: Modifier = Modifier,
) {
    val matrix = remember(recoveryKey) { recoveryKeyQrMatrix(recoveryKey) }
    val description = stringResource(R.string.settings_backup_key_qr_description)
    Canvas(
        modifier = modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .semantics { contentDescription = description },
    ) {
        // Whole pixels per module, centered, so modules don't blur or seam.
        val module = floor(size.width / matrix.width)
        val origin = (size.width - module * matrix.width) / 2f
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(origin + x * module, origin + y * module),
                        size = Size(module, module),
                    )
                }
            }
        }
    }
}

/**
 * The QR for E1: only the 16 canonical characters, which alphanumeric mode
 * fits in version 1 (21x21) at error correction M, plus a 4-module quiet zone.
 */
internal fun recoveryKeyQrMatrix(recoveryKey: String): BitMatrix = QRCodeWriter().encode(
    recoveryKey,
    BarcodeFormat.QR_CODE,
    0,
    0,
    mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to QR_QUIET_ZONE_MODULES,
    ),
)

private const val QR_QUIET_ZONE_MODULES = 4

@Composable
private fun RecoveryKeyReveal(recoveryKey: String, hint: String) {
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
        text = hint,
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
    onScan: () -> Unit,
    onCancel: () -> Unit,
    onUsePasskey: () -> Unit = {},
    onUseSecurityKey: () -> Unit = {},
    // Both kinds in the keyring: the two-choice sheet.
    onUseKeyChoice: () -> Unit = {},
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
        val hasPasskey = state.passkeySlots.isNotEmpty()
        val hasSecurityKey = state.securityKeySlots.isNotEmpty()
        Text(
            text = stringResource(
                when {
                    hasPasskey && hasSecurityKey -> R.string.settings_backup_unlock_passkey_or_security_key_message
                    hasSecurityKey -> R.string.settings_backup_unlock_security_key_message
                    hasPasskey -> R.string.settings_backup_unlock_passkey_message
                    else -> R.string.settings_backup_unlock_message
                }
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = AlertDialogDefaults.textContentColor,
        )
        if (hasPasskey || hasSecurityKey) {
            // One button names what the keyring has.
            Button(
                onClick = when {
                    hasPasskey && hasSecurityKey -> onUseKeyChoice
                    hasSecurityKey -> onUseSecurityKey
                    else -> onUsePasskey
                },
                enabled = !state.isUnlocking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = if (hasPasskey) Icons.Outlined.Fingerprint else Icons.Outlined.Key,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        when {
                            hasPasskey && hasSecurityKey -> R.string.settings_backup_use_passkey_or_security_key
                            hasSecurityKey -> R.string.settings_backup_use_security_key
                            else -> R.string.settings_backup_use_passkey
                        }
                    )
                )
            }
            Text(
                text = stringResource(R.string.settings_backup_unlock_or_recovery_key),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        RecoveryKeyField(
            value = state.input,
            onValueChange = onInputChange,
            errorMessage = when {
                state.isNotAKey -> stringResource(R.string.settings_backup_key_not_a_key)
                state.isWrongKey -> stringResource(R.string.settings_backup_unlock_wrong_key)
                else -> null
            },
            onDone = { if (canUnlock) onUnlock() },
            trailingActions = {
                IconButton(onClick = onScan, enabled = !state.isUnlocking) {
                    Icon(
                        imageVector = Icons.Rounded.QrCodeScanner,
                        contentDescription = stringResource(R.string.settings_backup_key_scan),
                    )
                }
            },
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

/** E3 Add or C1, when both kinds can work: Passkey or Security key. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackupKeyChoiceSheet(
    title: String,
    onPasskey: () -> Unit,
    onSecurityKey: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    BackupSheet(sheetState = sheetState, onDismiss = onDismiss) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.padding_large), vertical = 8.dp),
        )
        listOf(
            Triple(Icons.Outlined.Fingerprint, R.string.settings_backup_passkey to R.string.settings_backup_choice_passkey_detail, onPasskey),
            Triple(Icons.Outlined.Key, R.string.settings_backup_security_key to R.string.settings_backup_choice_security_key_detail, onSecurityKey),
        ).forEach { (icon, labels, onClick) ->
            ListItem(
                headlineContent = { Text(text = stringResource(labels.first)) },
                supportingContent = { Text(text = stringResource(labels.second)) },
                leadingContent = { Icon(imageVector = icon, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .clickable { hideBottomSheet(scope, sheetState, onClick) }
                    .padding(horizontal = 8.dp),
            )
        }
    }
}

/**
 * E4 (Add, C1, re-link): the key's PIN first ([prompt]'s PIN step), then
 * discovery with [status]'s line. Back and Cancel cancel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackupSecurityKeySheet(
    status: SecurityKeyStatus?,
    prompt: SecurityKeyPromptUiState,
    onSubmitPin: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val padding = Modifier.padding(horizontal = dimensionResource(R.dimen.padding_large))
    BackupSheet(sheetState = sheetState, onDismiss = onCancel) {
        Column(
            modifier = padding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_backup_security_key_prompt_title),
                style = MaterialTheme.typography.titleLarge,
            )
            if (!prompt.needsPin) {
                Text(
                    text = stringResource(R.string.settings_backup_security_key_prompt_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Not saveable: a PIN never goes into the saved-state bundle.
            // Cleared each time the PIN step comes back (a wrong PIN).
            var pin by remember(prompt.needsPin) { mutableStateOf("") }
            if (prompt.needsPin) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text(text = stringResource(R.string.settings_backup_security_key_pin_label)) },
                    singleLine = true,
                    isError = prompt.isWrongPin,
                    supportingText = if (prompt.isWrongPin) {
                        {
                            Text(
                                text = prompt.pinRetries?.let { retries ->
                                    pluralStringResource(R.plurals.settings_backup_security_key_wrong_pin, retries, retries)
                                } ?: stringResource(R.string.settings_backup_security_key_pin_failed)
                            )
                        }
                    } else {
                        null
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (pin.isNotEmpty()) onSubmitPin(pin) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                ) {
                    if (status == SecurityKeyStatus.DONE) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.settings_backup_key_done),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(
                                when (status) {
                                    SecurityKeyStatus.TOUCH -> R.string.settings_backup_security_key_touch
                                    SecurityKeyStatus.TOUCH_AGAIN -> R.string.settings_backup_security_key_touch_again
                                    SecurityKeyStatus.KEEP_HOLDING -> R.string.settings_backup_security_key_keep_holding
                                    SecurityKeyStatus.NFC_OFF -> R.string.settings_backup_security_key_nfc_off
                                    else -> R.string.settings_backup_security_key_waiting
                                }
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = onCancel) {
                    Text(text = stringResource(R.string.cancel))
                }
                if (prompt.needsPin) {
                    Button(onClick = { onSubmitPin(pin) }, enabled = pin.isNotEmpty()) {
                        Text(text = stringResource(R.string.settings_backup_add_passkey_continue))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val navigationBarBottomPadding = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    HazeModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.consumeWindowInsets(WindowInsets.navigationBars),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = dimensionResource(R.dimen.padding_large) + navigationBarBottomPadding),
            content = content,
        )
    }
}

private const val PREVIEW_RECOVERY_KEY = "K7QM3XRT9F2BHW4D"
private const val PREVIEW_FILE_NAME = "featherline-backup-2026-10-12_23-31-04.hrtbackup"
private const val PREVIEW_DATE_LABEL = "12 Oct 2026"

internal fun previewKeyringSlot(type: String, id: String = type): KeyringSlot = KeyringSlot(
    type = type,
    id = id,
    createdAt = LocalDate.of(2026, 10, 12).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    wrapped = "",
)

@Preview(name = "Key setup · first backup", showBackground = true, widthDp = 420, heightDp = 920)
@Composable
private fun BackupKeySetupDialogPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        BackupKeySetupDialog(
            state = BackupKeySetupUiState(recoveryKey = PREVIEW_RECOVERY_KEY),
            isInProgress = false,
            onCancel = {},
            onSaved = {},
        )
    }
}

@Preview(name = "Key setup · replace", showBackground = true, widthDp = 420, heightDp = 920)
@Composable
private fun BackupKeySetupReplaceDialogPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        BackupKeySetupDialog(
            state = BackupKeySetupUiState(recoveryKey = PREVIEW_RECOVERY_KEY, isReplace = true),
            isInProgress = false,
            onCancel = {},
            onSaved = {},
        )
    }
}

@Preview(name = "Passkey offer", showBackground = true, widthDp = 420, heightDp = 920)
@Composable
private fun BackupPasskeyOfferDialogPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        BackupPasskeyOfferDialog(isInProgress = false, onNotNow = {}, onAdd = {})
    }
}

@Preview(name = "Recovery key · show with QR", showBackground = true, widthDp = 420, heightDp = 920)
@Composable
private fun BackupRecoveryKeyDialogPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        BackupRecoveryKeyDialog(
            recoveryKey = PREVIEW_RECOVERY_KEY,
            createdLabel = stringResource(R.string.settings_backup_recovery_key_created, PREVIEW_DATE_LABEL),
            onDone = {},
        )
    }
}

@Composable
private fun BackupUnlockDialogPreviewContent(state: BackupRestoreKeyUiState) {
    HrtTrackerTheme(dynamicColor = false) {
        BackupUnlockDialog(
            displayName = PREVIEW_FILE_NAME,
            state = state,
            onInputChange = {},
            onUnlock = {},
            onScan = {},
            onCancel = {},
        )
    }
}

@Preview(name = "Unlock (C1) · recovery key only", showBackground = true, widthDp = 420)
@Composable
private fun BackupUnlockDialogPreview() {
    BackupUnlockDialogPreviewContent(BackupRestoreKeyUiState(isUnlocked = false, canAdopt = false))
}

@Preview(name = "Unlock (C1) · use passkey", showBackground = true, widthDp = 420)
@Composable
private fun BackupUnlockDialogPasskeyPreview() {
    BackupUnlockDialogPreviewContent(
        BackupRestoreKeyUiState(
            isUnlocked = false,
            canAdopt = false,
            passkeySlots = listOf(previewKeyringSlot(KeyringCrypto.SLOT_TYPE_PASSKEY)),
        )
    )
}

@Preview(name = "Unlock (C1) · use security key", showBackground = true, widthDp = 420)
@Composable
private fun BackupUnlockDialogSecurityKeyPreview() {
    BackupUnlockDialogPreviewContent(
        BackupRestoreKeyUiState(
            isUnlocked = false,
            canAdopt = false,
            securityKeySlots = listOf(previewKeyringSlot(KeyringCrypto.SLOT_TYPE_SECURITY_KEY)),
        )
    )
}

@Preview(name = "Unlock (C1) · passkey or security key", showBackground = true, widthDp = 420)
@Composable
private fun BackupUnlockDialogBothPreview() {
    BackupUnlockDialogPreviewContent(
        BackupRestoreKeyUiState(
            isUnlocked = false,
            canAdopt = false,
            passkeySlots = listOf(previewKeyringSlot(KeyringCrypto.SLOT_TYPE_PASSKEY)),
            securityKeySlots = listOf(previewKeyringSlot(KeyringCrypto.SLOT_TYPE_SECURITY_KEY)),
        )
    )
}

@Preview(name = "Unlock (C1) · wrong key", showBackground = true, widthDp = 420)
@Composable
private fun BackupUnlockDialogWrongKeyPreview() {
    BackupUnlockDialogPreviewContent(
        BackupRestoreKeyUiState(
            isUnlocked = false,
            canAdopt = false,
            input = PREVIEW_RECOVERY_KEY,
            isWrongKey = true,
        )
    )
}

@Preview(name = "Unlock (C1) · not a key", showBackground = true, widthDp = 420)
@Composable
private fun BackupUnlockDialogNotAKeyPreview() {
    BackupUnlockDialogPreviewContent(
        BackupRestoreKeyUiState(isUnlocked = false, canAdopt = false, isNotAKey = true)
    )
}

@Preview(name = "Restore confirm (C2) · adopt key", showBackground = true, widthDp = 420)
@Composable
private fun BackupRestoreConfirmDialogAdoptPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        BackupRestoreConfirmDialog(
            displayName = PREVIEW_FILE_NAME,
            state = BackupRestoreKeyUiState(isUnlocked = true, canAdopt = true),
            isInProgress = false,
            onAdoptChange = {},
            onRestore = {},
            onCancel = {},
        )
    }
}

@Preview(name = "Restore confirm (C2)", showBackground = true, widthDp = 420)
@Composable
private fun BackupRestoreConfirmDialogPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        BackupRestoreConfirmDialog(
            displayName = PREVIEW_FILE_NAME,
            state = BackupRestoreKeyUiState(isUnlocked = true, canAdopt = false),
            isInProgress = false,
            onAdoptChange = {},
            onRestore = {},
            onCancel = {},
        )
    }
}

@Preview(name = "Replace key", showBackground = true, widthDp = 420)
@Composable
private fun BackupReplaceKeyDialogPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        BackupReplaceKeyDialog(onReplace = {}, onCancel = {})
    }
}

@Preview(name = "New key active", showBackground = true, widthDp = 420)
@Composable
private fun BackupNewKeyActiveDialogPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        BackupNewKeyActiveDialog(onBackUpNow = {}, onDone = {})
    }
}

@Preview(name = "Add passkey notice", showBackground = true, widthDp = 420)
@Composable
private fun BackupAddPasskeyNoticeDialogPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        BackupAddPasskeyNoticeDialog(onContinue = {}, onCancel = {})
    }
}

@Preview(name = "Remove passkey", showBackground = true, widthDp = 420)
@Composable
private fun BackupRemovePasskeyDialogPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        BackupRemoveSlotDialog(isSecurityKey = false, onRemove = {}, onCancel = {})
    }
}

@Preview(name = "Remove security key", showBackground = true, widthDp = 420)
@Composable
private fun BackupRemoveSecurityKeyDialogPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        BackupRemoveSlotDialog(isSecurityKey = true, onRemove = {}, onCancel = {})
    }
}

@Preview(name = "Re-link passkey", showBackground = true, widthDp = 420)
@Composable
private fun BackupRelinkPasskeyDialogPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        BackupRelinkSlotDialog(
            addedLabel = PREVIEW_DATE_LABEL,
            isSecurityKey = false,
            isInProgress = false,
            onLink = {},
            onSkip = {},
        )
    }
}

@Preview(name = "Re-link security key", showBackground = true, widthDp = 420)
@Composable
private fun BackupRelinkSecurityKeyDialogPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        BackupRelinkSlotDialog(
            addedLabel = PREVIEW_DATE_LABEL,
            isSecurityKey = true,
            isInProgress = false,
            onLink = {},
            onSkip = {},
        )
    }
}

@Preview(name = "Add key choice sheet", showBackground = true, widthDp = 420)
@Composable
private fun BackupKeyChoiceSheetPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        BackupKeyChoiceSheet(
            title = stringResource(R.string.settings_backup_add_passkey_or_security_key),
            onPasskey = {},
            onSecurityKey = {},
            onDismiss = {},
        )
    }
}

@Composable
private fun BackupSecurityKeySheetPreviewContent(
    status: SecurityKeyStatus? = null,
    prompt: SecurityKeyPromptUiState = SecurityKeyPromptUiState(),
) {
    HrtTrackerTheme(dynamicColor = false) {
        BackupSecurityKeySheet(status = status, prompt = prompt, onSubmitPin = {}, onCancel = {})
    }
}

@Preview(name = "Security key (E4) · PIN", showBackground = true, widthDp = 420)
@Composable
private fun BackupSecurityKeySheetPinPreview() {
    BackupSecurityKeySheetPreviewContent(prompt = SecurityKeyPromptUiState(needsPin = true))
}

@Preview(name = "Security key (E4) · wrong PIN, retries left", showBackground = true, widthDp = 420)
@Composable
private fun BackupSecurityKeySheetWrongPinPreview() {
    BackupSecurityKeySheetPreviewContent(
        prompt = SecurityKeyPromptUiState(needsPin = true, isWrongPin = true, pinRetries = 3)
    )
}

@Preview(name = "Security key (E4) · PIN failed", showBackground = true, widthDp = 420)
@Composable
private fun BackupSecurityKeySheetPinFailedPreview() {
    BackupSecurityKeySheetPreviewContent(
        prompt = SecurityKeyPromptUiState(needsPin = true, isWrongPin = true)
    )
}

@Preview(name = "Security key (E4) · waiting", showBackground = true, widthDp = 420)
@Composable
private fun BackupSecurityKeySheetWaitingPreview() {
    BackupSecurityKeySheetPreviewContent(status = SecurityKeyStatus.WAITING)
}

@Preview(name = "Security key (E4) · NFC off", showBackground = true, widthDp = 420)
@Composable
private fun BackupSecurityKeySheetNfcOffPreview() {
    BackupSecurityKeySheetPreviewContent(status = SecurityKeyStatus.NFC_OFF)
}

@Preview(name = "Security key (E4) · touch", showBackground = true, widthDp = 420)
@Composable
private fun BackupSecurityKeySheetTouchPreview() {
    BackupSecurityKeySheetPreviewContent(status = SecurityKeyStatus.TOUCH)
}

@Preview(name = "Security key (E4) · keep holding", showBackground = true, widthDp = 420)
@Composable
private fun BackupSecurityKeySheetKeepHoldingPreview() {
    BackupSecurityKeySheetPreviewContent(status = SecurityKeyStatus.KEEP_HOLDING)
}

@Preview(name = "Security key (E4) · touch again", showBackground = true, widthDp = 420)
@Composable
private fun BackupSecurityKeySheetTouchAgainPreview() {
    BackupSecurityKeySheetPreviewContent(status = SecurityKeyStatus.TOUCH_AGAIN)
}

@Preview(name = "Security key (E4) · done", showBackground = true, widthDp = 420)
@Composable
private fun BackupSecurityKeySheetDonePreview() {
    BackupSecurityKeySheetPreviewContent(status = SecurityKeyStatus.DONE)
}
