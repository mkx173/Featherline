package com.mkx.hrttracker.ui.settings

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.backup.BackupKeyState
import com.mkx.hrttracker.data.backup.KeyringCrypto
import com.mkx.hrttracker.data.backup.KeyringSlot
import com.mkx.hrttracker.ui.components.AppContentContainer
import com.mkx.hrttracker.ui.components.HazeTopAppBar
import com.mkx.hrttracker.ui.components.HrtSection
import com.mkx.hrttracker.ui.components.appContentPaddingValuesBehindTopAppBar
import com.mkx.hrttracker.ui.components.paddingBehindTopAppBar
import com.mkx.hrttracker.ui.components.pinnedTopAppBarScrollBehavior
import com.mkx.hrttracker.ui.components.topAppBarScrollToTop
import com.mkx.hrttracker.ui.security.AppAuthenticationPromptEffect
import com.mkx.hrttracker.ui.security.AppLockViewModel

/** E3: the keyring's passkey and security-key slots, Add and Remove. Shares the Backup page's ViewModel. */
@Composable
fun BackupKeysScreen(
    onNavigateBack: () -> Unit,
    viewModel: BackupSettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backupKeyState by viewModel.backupKeyState.collectAsStateWithLifecycle()
    val latestContext by rememberUpdatedState(LocalContext.current)
    val activity = LocalActivity.current
    val appLockViewModel: AppLockViewModel = hiltViewModel(
        viewModelStoreOwner = activity as ComponentActivity,
    )
    val isAppLocked = appLockViewModel.uiState.collectAsStateWithLifecycle().value.shouldShowLockScreen
    val encryptionKey = uiState.encryptionKey
    val securityKeyStatus by viewModel.securityKeyStatus.collectAsStateWithLifecycle()

    val messageRes = encryptionKey.messageRes
    LaunchedEffect(messageRes) {
        messageRes ?: return@LaunchedEffect
        Toast.makeText(latestContext, latestContext.getString(messageRes), Toast.LENGTH_LONG).show()
        viewModel.consumeEncryptionKeyMessage()
    }
    AppAuthenticationPromptEffect(
        request = encryptionKey.pendingPrompt,
        onAuthenticated = { viewModel.onKeyCredentialAuthenticated() },
        onError = viewModel::onKeyCredentialError,
    )

    val key = backupKeyState as? BackupKeyState.Key
    BackupKeysScreenContent(
        slots = (key?.passkeySlots.orEmpty() + key?.securityKeySlots.orEmpty()).sortedBy { it.createdAt },
        addKeyChoice = viewModel.addKeyChoice,
        isInProgress = encryptionKey.isPasskeyInProgress,
        onNavigateBack = onNavigateBack,
        onAddClick = { viewModel.onAddKeyClick() },
        onRemoveSlotClick = viewModel::onRemoveSlotClick,
        modifier = modifier,
    )

    if (encryptionKey.isKeyChoiceShown && !isAppLocked) {
        BackupKeyChoiceSheet(
            title = stringResource(R.string.settings_backup_add_passkey_or_security_key),
            onPasskey = viewModel::onAddPasskeyClick,
            onSecurityKey = { viewModel.onAddSecurityKeyClick() },
            onDismiss = viewModel::dismissKeyChoice,
        )
    }
    encryptionKey.securityKeyPrompt?.takeUnless { isAppLocked }?.let { prompt ->
        BackupSecurityKeySheet(
            status = securityKeyStatus,
            prompt = prompt,
            onSubmitPin = { pin -> viewModel.submitSecurityKeyPin(activity, pin) },
            onCancel = viewModel::cancelSecurityKey,
        )
    }

    if (encryptionKey.isAddPasskeyNoticeShown && !isAppLocked) {
        BackupAddPasskeyNoticeDialog(
            onContinue = { viewModel.confirmAddPasskey(activity) },
            onCancel = viewModel::dismissAddPasskeyNotice,
        )
    }
    encryptionKey.removeSlot?.takeUnless { isAppLocked }?.let { slot ->
        BackupRemoveSlotDialog(
            isSecurityKey = slot.type == KeyringCrypto.SLOT_TYPE_SECURITY_KEY,
            onRemove = viewModel::confirmRemoveSlot,
            onCancel = viewModel::dismissRemoveSlot,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupKeysScreenContent(
    slots: List<KeyringSlot>,
    addKeyChoice: AddKeyChoice,
    isInProgress: Boolean,
    onNavigateBack: () -> Unit,
    onAddClick: () -> Unit,
    onRemoveSlotClick: (KeyringSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val scrollBehavior = pinnedTopAppBarScrollBehavior(
        scrollState = scrollState,
        state = rememberTopAppBarState()
    )
    val textPadding = Modifier.padding(horizontal = dimensionResource(R.dimen.padding_medium))
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            HazeTopAppBar(
                modifier = Modifier.topAppBarScrollToTop(scrollBehavior, scrollState),
                title = { Text(text = stringResource(R.string.settings_backup_passkeys)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        AppContentContainer(modifier = Modifier.paddingBehindTopAppBar(innerPadding)) {
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(appContentPaddingValuesBehindTopAppBar(innerPadding)),
            ) {
                Text(
                    text = stringResource(R.string.settings_backup_passkeys_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = textPadding.padding(bottom = 10.dp),
                )
                HrtSection(title = null, topPadding = false) {
                    slots.forEach { slot ->
                        val isSecurityKey = slot.type == KeyringCrypto.SLOT_TYPE_SECURITY_KEY
                        item {
                            SettingsSegmentedListItem(
                                title = stringResource(
                                    if (isSecurityKey) R.string.settings_backup_security_key else R.string.settings_backup_passkey
                                ),
                                supportingText = stringResource(
                                    R.string.settings_backup_passkey_added,
                                    backupKeyDateLabel(slot.createdAt),
                                ),
                                onClick = { if (!isInProgress) onRemoveSlotClick(slot) },
                                leadingContent = {
                                    SettingsLeadingIconSlot(
                                        icon = if (isSecurityKey) Icons.Outlined.Key else Icons.Outlined.Fingerprint
                                    )
                                },
                                trailingContent = {
                                    IconButton(
                                        onClick = { onRemoveSlotClick(slot) },
                                        enabled = !isInProgress,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = stringResource(
                                                if (isSecurityKey) R.string.settings_backup_remove_security_key else R.string.settings_backup_remove_passkey
                                            ),
                                        )
                                    }
                                }
                            )
                        }
                    }
                    item {
                        SettingsSegmentedListItem(
                            title = stringResource(
                                when (addKeyChoice) {
                                    AddKeyChoice.BOTH -> R.string.settings_backup_add_passkey_or_security_key
                                    AddKeyChoice.SECURITY_KEY -> R.string.settings_backup_add_security_key
                                    AddKeyChoice.PASSKEY -> R.string.settings_backup_add_passkey
                                }
                            ),
                            enabled = !isInProgress,
                            onClick = onAddClick,
                            leadingContent = {
                                SettingsLeadingIconSlot(icon = Icons.Rounded.Add)
                            },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.settings_backup_passkeys_footer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = textPadding.padding(top = 10.dp),
                )
                // Hidden with the option on a phone with neither NFC nor USB host.
                if (addKeyChoice != AddKeyChoice.PASSKEY) {
                    Text(
                        text = stringResource(R.string.settings_backup_security_keys_footer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = textPadding.padding(top = 10.dp),
                    )
                }
            }
        }
    }
}
