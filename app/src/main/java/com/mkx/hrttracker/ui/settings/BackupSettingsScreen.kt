package com.mkx.hrttracker.ui.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.backup.IncompatibleBackupFileException
import com.mkx.hrttracker.ui.components.AppContentContainer
import com.mkx.hrttracker.ui.components.BackupPasswordDialog
import com.mkx.hrttracker.ui.components.HazeTopAppBar
import com.mkx.hrttracker.ui.components.HrtSection
import com.mkx.hrttracker.ui.components.appContentPaddingValuesBehindTopAppBar
import com.mkx.hrttracker.ui.components.paddingBehindTopAppBar
import com.mkx.hrttracker.ui.components.pinnedTopAppBarScrollBehavior
import com.mkx.hrttracker.ui.components.topAppBarScrollToTop
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.ui.security.AppLockViewModel
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // The long-lived LaunchedEffect(viewModel) toast collectors below never
    // restart, so they read the latest localized context at emit time (see
    // SettingsScreen).
    val latestContext by rememberUpdatedState(context)
    val activity = LocalActivity.current
    val appLockViewModel: AppLockViewModel = hiltViewModel(
        viewModelStoreOwner = activity as ComponentActivity,
    )
    val appLockUiState by appLockViewModel.uiState.collectAsStateWithLifecycle()
    val isAppLocked = appLockUiState.shouldShowLockScreen
    val coroutineScope = rememberCoroutineScope()
    val pickerResultScope = remember(activity, coroutineScope) {
        (activity as? ComponentActivity)?.lifecycleScope ?: coroutineScope
    }
    var showBackupPasswordDialog by rememberSaveable { mutableStateOf(false) }
    val isBackupExportInProgress = uiState.isBackupExportInProgress
    val isBackupRestoreInProgress = uiState.isBackupRestoreInProgress
    val pendingRestoreRequest = uiState.pendingRestoreRequest
    val pendingPreparedBackupExportState = uiState.pendingPreparedBackupExport
    val pendingPreparedBackupExport =
        pendingPreparedBackupExportState?.let { preparedBackupExport ->
            viewModel.restorePreparedBackupExport(
                displayName = preparedBackupExport.displayName,
                tempFilePath = preparedBackupExport.tempFilePath,
            )
        }
    val isBackupFlowPending = pendingPreparedBackupExport != null
    val backupExportFailedMessage = stringResource(R.string.settings_backup_export_failed)
    val backupExportSuccessMessage = pendingPreparedBackupExport?.let { preparedBackupExport ->
        stringResource(
            R.string.settings_backup_export_success,
            preparedBackupExport.displayName,
        )
    }
    val backupRestoreIncompatibleFileMessage =
        stringResource(R.string.settings_backup_restore_incompatible_file)
    val backupRestoreFailedMessage = stringResource(R.string.settings_backup_restore_failed)
    val externalImportFailedMessage = stringResource(R.string.external_import_failed)
    val isBackupActionBlocked =
        isBackupExportInProgress || isBackupRestoreInProgress || isBackupFlowPending
    val externalImportReviewSheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Restore runs in viewModelScope so it survives the activity recreate
    // that the restored app-locale setting triggers. Results come back
    // through this replaying SharedFlow so the new composition still
    // shows the toast.
    LaunchedEffect(viewModel) {
        viewModel.backupRestoreEvents.collect { event ->
            val messageRes = when (event) {
                BackupRestoreEvent.Success -> R.string.settings_backup_restore_success
                is BackupRestoreEvent.Failure -> when (event.error) {
                    is IncompatibleBackupFileException,
                    is IllegalArgumentException -> R.string.settings_backup_restore_incompatible_file

                    else -> R.string.settings_backup_restore_failed
                }
            }
            Toast.makeText(
                latestContext,
                latestContext.getString(messageRes),
                Toast.LENGTH_SHORT,
            ).show()
            viewModel.consumeBackupRestoreEvent()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.externalImportEvents.collect { event ->
            val messageRes = when (event) {
                is ExternalImportEvent.Success -> R.string.external_import_success
                is ExternalImportEvent.Failure -> R.string.external_import_failed
            }
            Toast.makeText(
                latestContext,
                latestContext.getString(messageRes),
                Toast.LENGTH_SHORT,
            ).show()
            viewModel.consumeExternalImportEvent()
        }
    }
    val backupDirectoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { directoryUri ->
        val preparedBackupExport = pendingPreparedBackupExport
        if (directoryUri == null) {
            if (preparedBackupExport != null) {
                pickerResultScope.launch {
                    viewModel.discardPreparedBackup(preparedBackupExport)
                    viewModel.clearPendingPreparedBackupExport()
                }
            }
            return@rememberLauncherForActivityResult
        }
        if (
            preparedBackupExport == null ||
            isBackupExportInProgress ||
            isBackupRestoreInProgress
        ) {
            viewModel.clearPendingPreparedBackupExport()
            Toast.makeText(
                context,
                backupExportFailedMessage,
                Toast.LENGTH_SHORT
            ).show()
            return@rememberLauncherForActivityResult
        }

        pickerResultScope.launch {
            viewModel.setBackupExportInProgress(true)
            try {
                viewModel.exportPreparedBackup(
                    directoryUri = directoryUri,
                    preparedBackupExport = preparedBackupExport,
                )
                Toast.makeText(
                    context,
                    backupExportSuccessMessage ?: backupExportFailedMessage,
                    Toast.LENGTH_SHORT
                ).show()
            } catch (_: Exception) {
                Toast.makeText(
                    context,
                    backupExportFailedMessage,
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                viewModel.setBackupExportInProgress(false)
                viewModel.clearPendingPreparedBackupExport()
            }
        }
    }
    val restoreBackupFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { fileUri ->
        if (fileUri == null || isBackupExportInProgress || isBackupRestoreInProgress) {
            return@rememberLauncherForActivityResult
        }
        pickerResultScope.launch {
            viewModel.setBackupRestoreInProgress(true)
            try {
                // Load the file bytes once here (while the picker's read
                // grant is still fresh) and stash them in the pending
                // request. The password dialog can then decrypt from
                // memory without re-opening the URI — that re-open used
                // to fail intermittently on the first attempt after a
                // fresh install because the temporary SAF grant could
                // lapse before the user finished typing.
                //
                // Resolve the display name before we load the bytes so a
                // failure in that lookup can't strand the encrypted bytes
                // outside the ViewModel's ownership.
                val displayName = resolveDocumentDisplayName(context, fileUri)
                val encryptedBytes = viewModel.loadAndValidateBackupBytes(fileUri)
                var transferredOwnership = false
                try {
                    viewModel.setPendingRestoreRequest(
                        fileUri = fileUri,
                        displayName = displayName,
                        encryptedBytes = encryptedBytes,
                    )
                    transferredOwnership = true
                } finally {
                    if (!transferredOwnership) {
                        encryptedBytes.fill(0)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Toast.makeText(
                    context,
                    if (error is IncompatibleBackupFileException) {
                        backupRestoreIncompatibleFileMessage
                    } else {
                        backupRestoreFailedMessage
                    },
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                viewModel.setBackupRestoreInProgress(false)
            }
        }
    }
    val externalImportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { fileUri ->
        if (
            fileUri == null ||
            isBackupActionBlocked ||
            uiState.isExternalImportInProgress
        ) {
            return@rememberLauncherForActivityResult
        }
        pickerResultScope.launch {
            viewModel.setExternalImportInProgress(true)
            try {
                viewModel.loadExternalImportPreview(fileUri)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // loadExternalImportPreview emits the failure event consumed above,
                // keeping all external-import result toasts in one replaying flow.
            } finally {
                viewModel.setExternalImportInProgress(false)
            }
        }
    }

    BackupSettingsScreenContent(
        onNavigateBack = onNavigateBack,
        onBackupToFileClick = {
            if (!isBackupActionBlocked) {
                showBackupPasswordDialog = true
            }
        },
        onRestoreFromFileClick = {
            if (!isBackupActionBlocked) {
                try {
                    restoreBackupFileLauncher.launch(arrayOf("*/*"))
                } catch (_: Exception) {
                    Toast.makeText(
                        context,
                        backupRestoreFailedMessage,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        },
        onImportExternalTrackerClick = {
            if (!isBackupActionBlocked && !uiState.isExternalImportInProgress) {
                try {
                    externalImportFileLauncher.launch(EXTERNAL_IMPORT_MIME_TYPES)
                } catch (_: Exception) {
                    Toast.makeText(
                        context,
                        externalImportFailedMessage,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        },
        modifier = modifier,
    )

    uiState.pendingExternalImportPreview?.takeUnless { isAppLocked }?.let { preview ->
        ExternalImportReviewSheet(
            preview = preview,
            isImporting = uiState.isExternalImportInProgress,
            sheetState = externalImportReviewSheetState,
            onDismissRequest = {
                hideBottomSheet(coroutineScope, externalImportReviewSheetState) {
                    viewModel.clearPendingExternalImportPreview()
                }
            },
            onImportClick = {
                viewModel.requestExternalImport()
            },
        )
    }

    if (showBackupPasswordDialog && !isAppLocked) {
        BackupPasswordDialog(
            title = stringResource(R.string.settings_backup_password_title),
            message = stringResource(R.string.settings_backup_password_message),
            warningMessage = stringResource(R.string.settings_backup_password_warning),
            confirmLabel = stringResource(R.string.settings_backup_password_confirm),
            passwordLabel = stringResource(R.string.settings_backup_password_label),
            confirmPasswordLabel = stringResource(R.string.settings_backup_confirm_password_label),
            isInProgress = isBackupExportInProgress,
            minimumPasswordLength = MINIMUM_BACKUP_PASSWORD_LENGTH,
            onDismiss = { showBackupPasswordDialog = false },
            onConfirm = { password ->
                coroutineScope.launch {
                    viewModel.setBackupExportInProgress(true)
                    val preparedBackupExport = try {
                        viewModel.prepareBackupExport(password)
                    } catch (_: Exception) {
                        Toast.makeText(
                            context,
                            backupExportFailedMessage,
                            Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    } finally {
                        viewModel.setBackupExportInProgress(false)
                    }

                    viewModel.setPendingPreparedBackupExport(
                        displayName = preparedBackupExport.displayName,
                        tempFilePath = preparedBackupExport.tempFilePath,
                    )
                    showBackupPasswordDialog = false
                    try {
                        backupDirectoryLauncher.launch(null)
                    } catch (_: Exception) {
                        viewModel.discardPreparedBackup(preparedBackupExport)
                        viewModel.clearPendingPreparedBackupExport()
                        Toast.makeText(
                            context,
                            backupExportFailedMessage,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
        )
    }

    pendingRestoreRequest?.takeUnless { isAppLocked }?.let { restoreRequest ->
        val restorePasswordMessage = if (restoreRequest.displayName != null) {
            stringResource(
                R.string.settings_backup_restore_password_message_with_name,
                restoreRequest.displayName,
            )
        } else {
            stringResource(R.string.settings_backup_restore_password_message)
        }
        BackupPasswordDialog(
            title = stringResource(R.string.settings_backup_restore_confirm_title),
            message = restorePasswordMessage,
            confirmLabel = stringResource(R.string.settings_backup_restore_confirm),
            passwordLabel = stringResource(R.string.settings_backup_password_label),
            isInProgress = isBackupRestoreInProgress,
            minimumPasswordLength = MINIMUM_BACKUP_PASSWORD_LENGTH,
            onDismiss = {
                viewModel.clearPendingRestoreRequest()
            },
            onConfirm = { password ->
                // Routed through the ViewModel so the restore + result toast
                // outlive the activity recreate that the restored app-locale
                // setting triggers. See backupRestoreEvents collector above.
                viewModel.requestBackupRestore(password)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackupSettingsScreenContent(
    onNavigateBack: () -> Unit,
    onBackupToFileClick: () -> Unit,
    onRestoreFromFileClick: () -> Unit,
    onImportExternalTrackerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val scrollBehavior = pinnedTopAppBarScrollBehavior(
        scrollState = scrollState,
        state = rememberTopAppBarState()
    )
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            HazeTopAppBar(
                modifier = Modifier.topAppBarScrollToTop(scrollBehavior, scrollState),
                title = {
                    Text(text = stringResource(R.string.settings_backup_restore))
                },
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
                HrtSection(
                    title = stringResource(R.string.settings_backup_file),
                    topPadding = false
                ) {
                    item {
                        SettingsSegmentedListItem(
                            title = stringResource(R.string.settings_backup_to_file),
                            onClick = onBackupToFileClick,
                            leadingContent = {
                                SettingsLeadingIconSlot(
                                    painter = painterResource(R.drawable.ic_file_export)
                                )
                            },
                            trailingContent = {
                                SettingsChevronTrailingIcon()
                            }
                        )
                    }

                    item {
                        SettingsSegmentedListItem(
                            title = stringResource(R.string.settings_restore_from_file),
                            onClick = onRestoreFromFileClick,
                            leadingContent = {
                                SettingsLeadingIconSlot(
                                    painter = painterResource(R.drawable.ic_settings_backup_restore)
                                )
                            },
                            trailingContent = {
                                SettingsChevronTrailingIcon()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))

                HrtSection(title = stringResource(R.string.settings_backup_import)) {
                    item {
                        SettingsSegmentedListItem(
                            title = stringResource(R.string.settings_import_external_tracker_json),
                            onClick = onImportExternalTrackerClick,
                            leadingContent = {
                                SettingsLeadingIconSlot(
                                    painter = painterResource(R.drawable.ic_download),
                                    iconSize = 20.dp
                                )
                            },
                            trailingContent = {
                                SettingsChevronTrailingIcon()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Preview(
    name = "Backup Settings Screen",
    showBackground = true,
    widthDp = 420,
    heightDp = 920
)
@Composable
private fun BackupSettingsScreenPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        BackupSettingsScreenContent(
            onNavigateBack = { },
            onBackupToFileClick = { },
            onRestoreFromFileClick = { },
            onImportExternalTrackerClick = { },
        )
    }
}

private const val MINIMUM_BACKUP_PASSWORD_LENGTH = 6
private val EXTERNAL_IMPORT_MIME_TYPES = arrayOf(
    "application/json",
    "text/json",
    "text/plain",
    "*/*",
)

private fun resolveDocumentDisplayName(
    context: Context,
    uri: Uri,
): String? {
    return context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        val nameColumnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameColumnIndex >= 0 && cursor.moveToFirst()) {
            cursor.getString(nameColumnIndex)
        } else {
            null
        }
    }
}
