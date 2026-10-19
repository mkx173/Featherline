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

/** E3: the keyring's passkey slots, Add passkey and Remove. Shares the Backup page's ViewModel. */
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

    val messageRes = encryptionKey.messageRes
    LaunchedEffect(messageRes) {
        messageRes ?: return@LaunchedEffect
        Toast.makeText(latestContext, latestContext.getString(messageRes), Toast.LENGTH_LONG).show()
        viewModel.consumeEncryptionKeyMessage()
    }
    AppAuthenticationPromptEffect(
        request = encryptionKey.pendingPrompt,
        onAuthenticated = viewModel::onKeyCredentialAuthenticated,
        onError = viewModel::onKeyCredentialError,
    )

    BackupKeysScreenContent(
        slots = (backupKeyState as? BackupKeyState.Key)?.passkeySlots.orEmpty(),
        isInProgress = encryptionKey.isPasskeyInProgress,
        onNavigateBack = onNavigateBack,
        onAddPasskeyClick = viewModel::onAddPasskeyClick,
        onRemovePasskeyClick = viewModel::onRemovePasskeyClick,
        modifier = modifier,
    )

    if (encryptionKey.isAddPasskeyNoticeShown && !isAppLocked) {
        BackupAddPasskeyNoticeDialog(
            onContinue = { viewModel.confirmAddPasskey(activity) },
            onCancel = viewModel::dismissAddPasskeyNotice,
        )
    }
    if (encryptionKey.removePasskeySlotId != null && !isAppLocked) {
        BackupRemovePasskeyDialog(
            onRemove = viewModel::confirmRemovePasskey,
            onCancel = viewModel::dismissRemovePasskey,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupKeysScreenContent(
    slots: List<KeyringSlot>,
    isInProgress: Boolean,
    onNavigateBack: () -> Unit,
    onAddPasskeyClick: () -> Unit,
    onRemovePasskeyClick: (String) -> Unit,
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
                        item {
                            SettingsSegmentedListItem(
                                title = stringResource(R.string.settings_backup_passkey),
                                supportingText = stringResource(
                                    R.string.settings_backup_passkey_added,
                                    backupKeyDateLabel(slot.createdAt),
                                ),
                                onClick = { if (!isInProgress) onRemovePasskeyClick(slot.id) },
                                leadingContent = {
                                    SettingsLeadingIconSlot(icon = Icons.Outlined.Fingerprint)
                                },
                                trailingContent = {
                                    IconButton(
                                        onClick = { onRemovePasskeyClick(slot.id) },
                                        enabled = !isInProgress,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = stringResource(R.string.settings_backup_remove_passkey),
                                        )
                                    }
                                }
                            )
                        }
                    }
                    item {
                        SettingsSegmentedListItem(
                            title = stringResource(R.string.settings_backup_add_passkey),
                            enabled = !isInProgress,
                            onClick = onAddPasskeyClick,
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
            }
        }
    }
}
