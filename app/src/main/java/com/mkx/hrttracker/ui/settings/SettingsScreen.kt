package com.mkx.hrttracker.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.mkx.hrttracker.BuildConfig
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.backup.IncompatibleBackupFileException
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.personalization.WeightUnit
import com.mkx.hrttracker.model.settings.AppLanguageOption
import com.mkx.hrttracker.model.settings.AppLockGracePeriodOption
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.model.settings.FirstDayOfWeekOption
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.canScheduleExactAlarms
import com.mkx.hrttracker.reminder.rememberReminderCapabilityReconciler
import com.mkx.hrttracker.reminder.shouldShowNotificationPermissionRecoveryToast
import com.mkx.hrttracker.ui.components.AppContentContainer
import com.mkx.hrttracker.ui.components.BackupPasswordDialog
import com.mkx.hrttracker.ui.components.ExactAlarmAccessDialog
import com.mkx.hrttracker.ui.components.HazeAlertDialog
import com.mkx.hrttracker.ui.components.HazeTopAppBar
import com.mkx.hrttracker.ui.components.HrtDropdownMenu
import com.mkx.hrttracker.ui.components.HrtDropdownMenuItem
import com.mkx.hrttracker.ui.components.HrtPill
import com.mkx.hrttracker.ui.components.HrtPillSize
import com.mkx.hrttracker.ui.components.HrtSection
import com.mkx.hrttracker.ui.components.PreferenceSegmentedListItem
import com.mkx.hrttracker.ui.components.ScrollToTopSignalEffect
import com.mkx.hrttracker.ui.components.WeightDialog
import com.mkx.hrttracker.ui.components.appContentPaddingValuesBehindTopAppBar
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.components.isHazeBlurSupported
import com.mkx.hrttracker.ui.components.paddingBehindTopAppBar
import com.mkx.hrttracker.ui.components.pinnedTopAppBarScrollBehavior
import com.mkx.hrttracker.ui.components.shortLabelRes
import com.mkx.hrttracker.ui.components.topAppBarScrollToTop
import com.mkx.hrttracker.ui.hideBottomSheet
import com.mkx.hrttracker.ui.security.AppAuthenticationPromptEffect
import com.mkx.hrttracker.ui.security.AppLockViewModel
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.rememberAppLocale
import com.mkx.hrttracker.widget.WidgetAppearance
import com.mkx.hrttracker.widget.WidgetCenteredSliderTrack
import com.mkx.hrttracker.widget.WidgetHueSpectrumTrack
import com.mkx.hrttracker.widget.centeredOffsetReadout
import com.mkx.hrttracker.widget.defaultSeedHue
import com.mkx.hrttracker.widget.hueSwatchColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    scrollToTopSignal: Int = 0,
    onCalibrationClick: () -> Unit = {},
    onPkCalibrationDebugClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settingsState = uiState.settingsState
    val widgetAppearance by viewModel.widgetAppearance.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // The app swaps UI language in place via composition locals (see
    // MainActivity) without recreating the Activity, so LocalContext.current is
    // rebuilt on a language change. The long-lived LaunchedEffect(viewModel)
    // toast collectors below never restart, so they must read the latest
    // localized context at emit time rather than capturing a context / a
    // pre-resolved string from the composition that first launched them.
    val latestContext by rememberUpdatedState(context)
    val activity = LocalActivity.current
    val appLockViewModel: AppLockViewModel = hiltViewModel(
        viewModelStoreOwner = activity as ComponentActivity,
    )
    val appLockUiState by appLockViewModel.uiState.collectAsStateWithLifecycle()
    val isAppLocked = appLockUiState.shouldShowLockScreen
    val configuration = LocalConfiguration.current
    val coroutineScope = rememberCoroutineScope()
    val pickerResultScope = remember(activity, coroutineScope) {
        (activity as? ComponentActivity)?.lifecycleScope ?: coroutineScope
    }
    val reminderCapabilityReconciler = rememberReminderCapabilityReconciler()
    val reminderCapabilityState by reminderCapabilityReconciler.state.collectAsStateWithLifecycle()
    val hasNotificationAccess = reminderCapabilityState.hasNotificationAccess
    var isReminderEnablePending by rememberSaveable { mutableStateOf(false) }
    val reminderSupportState = resolveSettingsReminderSupportState(
        hasNotificationAccess = reminderCapabilityState.hasNotificationAccess,
        hasExactAlarmAccess = reminderCapabilityState.hasExactAlarmAccess,
        remindersEnabled = settingsState.remindersEnabled,
        isReminderEnablePending = isReminderEnablePending,
    )
    var hasRequestedNotificationPermission by rememberSaveable { mutableStateOf(false) }
    var isDiagnosticsExportInProgress by rememberSaveable { mutableStateOf(false) }
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
    val reminderNotificationsUnavailableMessage =
        stringResource(R.string.settings_reminders_notifications_unavailable)
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
    LaunchedEffect(uiState.securityErrorMessageRes) {
        val messageRes = uiState.securityErrorMessageRes ?: return@LaunchedEffect
        Toast.makeText(latestContext, latestContext.getString(messageRes), Toast.LENGTH_LONG).show()
        viewModel.consumeSecurityError()
    }
    LaunchedEffect(viewModel) {
        viewModel.weightMutationEvents.collect { event ->
            when (event) {
                is WeightMutationEvent.Failure -> {
                    Toast.makeText(
                        latestContext,
                        latestContext.getString(R.string.personalization_weight_update_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            viewModel.consumeWeightMutationEvent()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.settingsMutationEvents.collect { event ->
            when (event) {
                is SettingsMutationEvent.Failure -> {
                    Toast.makeText(
                        latestContext,
                        latestContext.getString(R.string.settings_update_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            viewModel.consumeSettingsMutationEvent()
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
    val diagnosticsExportSuccessMessage =
        stringResource(R.string.settings_diagnostics_export_success)
    val diagnosticsExportFailedMessage = stringResource(R.string.settings_diagnostics_export_failed)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        isReminderEnablePending = isGranted
        reminderCapabilityReconciler.requestReconcile("settings_notification_permission_result")
        if (isGranted) {
            viewModel.setRemindersEnabled(true)
        } else {
            viewModel.setRemindersEnabled(false)
            Toast.makeText(
                context,
                reminderNotificationsUnavailableMessage,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    val exactAlarmAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        reminderCapabilityReconciler.requestReconcile("settings_exact_alarm_result")
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
    val diagnosticsExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(DIAGNOSTICS_EXPORT_MIME_TYPE)
    ) { destinationUri ->
        if (
            destinationUri == null ||
            isDiagnosticsExportInProgress ||
            !BuildConfig.DEBUG
        ) {
            return@rememberLauncherForActivityResult
        }
        coroutineScope.launch {
            isDiagnosticsExportInProgress = true
            try {
                viewModel.exportDiagnosticLogs(destinationUri)
                Toast.makeText(
                    context,
                    diagnosticsExportSuccessMessage,
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (_: Exception) {
                Toast.makeText(
                    context,
                    diagnosticsExportFailedMessage,
                    Toast.LENGTH_SHORT,
                ).show()
            } finally {
                isDiagnosticsExportInProgress = false
            }
        }
    }

    LaunchedEffect(configuration) {
        viewModel.refreshAppLanguageOption()
    }

    LaunchedEffect(settingsState.remindersEnabled, hasNotificationAccess) {
        if (settingsState.remindersEnabled || !hasNotificationAccess) {
            isReminderEnablePending = false
        }
    }

    AppAuthenticationPromptEffect(
        request = uiState.pendingPrompt,
        onAuthenticated = viewModel::onScreenLockProtectionAuthenticated,
        onError = viewModel::onScreenLockProtectionPromptError
    )

    val onRemindersEnabledChange = { enabled: Boolean ->
        if (!enabled) {
            isReminderEnablePending = false
            viewModel.setRemindersEnabled(false)
        } else if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (
                shouldShowNotificationPermissionRecoveryToast(
                    sdkInt = Build.VERSION.SDK_INT,
                    hasRequestedPermissionBefore = hasRequestedNotificationPermission,
                    shouldShowPermissionRationale = activity?.let {
                        ActivityCompat.shouldShowRequestPermissionRationale(
                            it,
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    } ?: false
                )
            ) {
                Toast.makeText(
                    context,
                    reminderNotificationsUnavailableMessage,
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                hasRequestedNotificationPermission = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            isReminderEnablePending = false
            Toast.makeText(
                context,
                reminderNotificationsUnavailableMessage,
                Toast.LENGTH_SHORT
            ).show()
        } else {
            isReminderEnablePending = true
            viewModel.setRemindersEnabled(true)
        }
    }

    SettingsScreenContent(
        uiState = uiState,
        widgetAppearance = widgetAppearance,
        hasNotificationAccess = hasNotificationAccess,
        reminderSupportState = reminderSupportState,
        onWeightSave = viewModel::setWeight,
        onWeightClear = viewModel::clearWeight,
        onRemindersEnabledChange = onRemindersEnabledChange,
        onRequestExactAlarmAccess = {
            requestExactAlarmAccess(context, exactAlarmAccessLauncher::launch)
        },
        onScreenLockProtectionToggle = viewModel::onScreenLockProtectionToggle,
        onAppLockGracePeriodOptionChange = viewModel::setAppLockGracePeriodOption,
        onHideScreenContentEnabledChange = viewModel::setHideScreenContentEnabled,
        onAppLanguageOptionChange = viewModel::setAppLanguageOption,
        onFirstDayOfWeekOptionChange = viewModel::setFirstDayOfWeekOption,
        onDarkModeOptionChange = viewModel::setDarkModeOption,
        onAdaptiveColorEnabledChange = viewModel::setAdaptiveColorEnabled,
        onPureBlackEnabledChange = viewModel::setPureBlackEnabled,
        onCjkTextOffsetEnabledChange = viewModel::setCjkTextOffsetEnabled,
        onHazeBlurEnabledChange = viewModel::setHazeBlurEnabled,
        onShowArchivedGroupRecordsChange = viewModel::setShowArchivedGroupRecords,
        onHideReferenceRangesChange = viewModel::setHideReferenceRanges,
        onHideMedicationDetailsChange = viewModel::setHideMedicationDetails,
        onWidgetAppearanceChange = viewModel::setWidgetAppearance,
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
        showDiagnosticsExport = BuildConfig.DEBUG,
        onExportDiagnosticLogsClick = {
            if (!isDiagnosticsExportInProgress && BuildConfig.DEBUG) {
                try {
                    diagnosticsExportLauncher.launch(viewModel.diagnosticsExportFileName())
                } catch (_: Exception) {
                    Toast.makeText(
                        context,
                        diagnosticsExportFailedMessage,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        },
        onPkCalibrationDebugClick = {
            if (BuildConfig.DEBUG) onPkCalibrationDebugClick()
        },
        onCalibrationClick = onCalibrationClick,
        scrollToTopSignal = scrollToTopSignal,
        modifier = modifier
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

// Snap a slider value to whole-percent steps so the value that gets saved matches the
// "NN%" label shown next to the slider (e.g. 1.0455 → 1.05, displayed as 105%). Without
// this the slider saves its raw continuous position while the label rounds for display.
private fun snapToWholePercent(value: Float): Float = (value * 100).roundToInt() / 100f

@Composable
internal fun WidgetAppearanceDialog(
    appearance: WidgetAppearance,
    onAppearanceChange: (WidgetAppearance) -> Unit,
    onDismiss: () -> Unit,
) {
    // Keyed on the incoming values so the local edit state re-seeds when the real
    // persisted appearance arrives. widgetAppearance is a StateFlow whose initial value
    // is WidgetAppearance.Default, so a dialog composed before the first DataStore
    // emission (e.g. a process-death restore with the dialog reopened) would otherwise
    // strand the 100%/100%/Follow-system placeholder and let Save overwrite the user's
    // stored values; re-seeding falls back to the currently set value instead. Local
    // edits are never reset mid-session: these params only change on the placeholder→real
    // load, and after a Save the dialog has already dismissed.
    var localSeedHue by remember(appearance.seedHue) { mutableStateOf(appearance.seedHue) }
    var localSaturation by remember(appearance.saturation) {
        mutableStateOf(snapToWholePercent(appearance.saturation.coerceIn(0f, 1f)))
    }
    var localBalance by remember(appearance.balance) {
        mutableStateOf(snapToWholePercent(appearance.balance.coerceIn(0f, 1f)))
    }
    var localContentScale by remember(appearance.contentScale) {
        mutableStateOf(snapToWholePercent(appearance.contentScale.coerceIn(0.5f, 1.5f)))
    }
    var localBackgroundAlpha by remember(appearance.backgroundAlpha) {
        mutableStateOf(snapToWholePercent(appearance.backgroundAlpha.coerceIn(0.5f, 1f)))
    }
    var localDarkModeOption by remember(appearance.darkMode) { mutableStateOf(appearance.darkMode) }
    var isDarkModeMenuExpanded by remember { mutableStateOf(false) }
    // Resting hue shown while the accent is Dynamic (null); grabbing the slider promotes it
    // to an explicit pick, the Dynamic pill resets back to null.
    val restingHue = remember { defaultSeedHue() }
    val resetAccentHue: () -> Unit = { localSeedHue = null }
    HazeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_widget_appearance)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val widgetDarkModeText = stringResource(R.string.settings_widget_dark_mode)
                    Text(
                        text = widgetDarkModeText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.cjkTextOffset(widgetDarkModeText)
                    )
                    // Only the trailing chip is interactive, so the ripple is bounded to
                    // the selection area. The popup anchors to this Box so the menu opens
                    // beneath the current selection rather than at the row's start edge.
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .clickable { isDarkModeMenuExpanded = true }
                                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val widgetDarkModeLabelText =
                                stringResource(localDarkModeOption.labelRes)
                            Text(
                                text = widgetDarkModeLabelText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.cjkTextOffset(widgetDarkModeLabelText)
                            )
                            Icon(
                                imageVector = Icons.Rounded.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HrtDropdownMenu(
                            expanded = isDarkModeMenuExpanded,
                            onDismissRequest = { isDarkModeMenuExpanded = false },
                            modifier = Modifier.width(IntrinsicSize.Min),
                            items = DarkModeOption.entries.map { option ->
                                HrtDropdownMenuItem(
                                    text = stringResource(option.labelRes),
                                    onClick = { localDarkModeOption = option },
                                )
                            },
                        )
                    }
                }
                Column {
                    val contentScaleLabel = stringResource(R.string.settings_widget_content_scale)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = contentScaleLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.cjkTextOffset(contentScaleLabel),
                        )
                        Text(
                            text = "${(localContentScale * 100).roundToInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            // Keyed on the label, not "NN%": the percentage is always non-CJK,
                            // so it must follow the label's offset to stay baseline-aligned.
                            modifier = Modifier.cjkTextOffset(contentScaleLabel),
                        )
                    }
                    Slider(
                        value = localContentScale,
                        onValueChange = { localContentScale = snapToWholePercent(it) },
                        valueRange = 0.5f..1.5f,
                    )
                }
                Column {
                    val opacityLabel = stringResource(R.string.settings_widget_background_opacity)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = opacityLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.cjkTextOffset(opacityLabel),
                        )
                        Text(
                            text = "${(localBackgroundAlpha * 100).roundToInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.cjkTextOffset(opacityLabel),
                        )
                    }
                    Slider(
                        value = localBackgroundAlpha,
                        onValueChange = { localBackgroundAlpha = snapToWholePercent(it) },
                        valueRange = 0.5f..1f,
                    )
                }
                Column {
                    val seedHueLabel = stringResource(R.string.widget_config_seed_hue)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = seedHueLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.cjkTextOffset(seedHueLabel),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(
                                        hueSwatchColor(localSeedHue ?: restingHue),
                                        CircleShape,
                                    ),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            CompositionLocalProvider(
                                LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
                            ) {
                                HrtPill(
                                    label = stringResource(R.string.widget_config_seed_dynamic),
                                    containerColor = if (localSeedHue == null) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    },
                                    size = HrtPillSize.Small,
                                    onClick = if (localSeedHue == null) null else resetAccentHue,
                                )
                            }
                        }
                    }
                    Slider(
                        value = localSeedHue ?: restingHue,
                        onValueChange = { localSeedHue = it },
                        valueRange = 0f..359f,
                        track = { WidgetHueSpectrumTrack(it) },
                    )
                }
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val saturationLabel = stringResource(R.string.widget_config_saturation)
                        Text(
                            text = saturationLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.cjkTextOffset(saturationLabel),
                        )
                        Text(
                            text = "${(localSaturation * 100).roundToInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.cjkTextOffset(saturationLabel),
                        )
                    }
                    Slider(
                        value = localSaturation,
                        onValueChange = { localSaturation = snapToWholePercent(it) },
                        valueRange = 0f..1f,
                    )
                }
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val balanceLabel = stringResource(R.string.widget_config_balance)
                        Text(
                            text = balanceLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.cjkTextOffset(balanceLabel),
                        )
                        Text(
                            text = centeredOffsetReadout(localBalance, 0f..1f),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.cjkTextOffset(balanceLabel),
                        )
                    }
                    Slider(
                        value = localBalance,
                        onValueChange = { localBalance = snapToWholePercent(it) },
                        valueRange = 0f..1f,
                        track = { WidgetCenteredSliderTrack(it) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onAppearanceChange(
                    WidgetAppearance(
                        seedHue = localSeedHue,
                        saturation = localSaturation,
                        balance = localBalance,
                        contentScale = localContentScale,
                        backgroundAlpha = localBackgroundAlpha,
                        darkMode = localDarkModeOption,
                    ),
                )
                onDismiss()
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreenContent(
    modifier: Modifier = Modifier,
    uiState: SettingsUiState,
    widgetAppearance: WidgetAppearance,
    hasNotificationAccess: Boolean,
    reminderSupportState: SettingsReminderSupportState,
    onWeightSave: (Double, WeightUnit) -> Unit,
    onWeightClear: () -> Unit,
    onRemindersEnabledChange: (Boolean) -> Unit,
    onRequestExactAlarmAccess: () -> Unit,
    onScreenLockProtectionToggle: (Boolean) -> Unit,
    onAppLockGracePeriodOptionChange: (AppLockGracePeriodOption) -> Unit,
    onHideScreenContentEnabledChange: (Boolean) -> Unit,
    onAppLanguageOptionChange: (AppLanguageOption) -> Unit,
    onFirstDayOfWeekOptionChange: (FirstDayOfWeekOption) -> Unit,
    onDarkModeOptionChange: (DarkModeOption) -> Unit,
    onAdaptiveColorEnabledChange: (Boolean) -> Unit,
    onPureBlackEnabledChange: (Boolean) -> Unit,
    onCjkTextOffsetEnabledChange: (Boolean) -> Unit,
    onHazeBlurEnabledChange: (Boolean) -> Unit,
    onShowArchivedGroupRecordsChange: (Boolean) -> Unit,
    onHideReferenceRangesChange: (Boolean) -> Unit,
    onHideMedicationDetailsChange: (Boolean) -> Unit,
    onWidgetAppearanceChange: (WidgetAppearance) -> Unit,
    onBackupToFileClick: () -> Unit,
    onRestoreFromFileClick: () -> Unit,
    onImportExternalTrackerClick: () -> Unit,
    showDiagnosticsExport: Boolean,
    onExportDiagnosticLogsClick: () -> Unit,
    onCalibrationClick: () -> Unit,
    onPkCalibrationDebugClick: () -> Unit = {},
    scrollToTopSignal: Int = 0,
) {
    val settingsState = uiState.settingsState
    val context = LocalContext.current
    var showWeightDialog by rememberSaveable { mutableStateOf(false) }
    var showExactAlarmRecoveryDialog by rememberSaveable { mutableStateOf(false) }
    var pendingExternalUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingExternalLinkTitleRes by rememberSaveable { mutableStateOf<Int?>(null) }
    var showWidgetAppearanceDialog by rememberSaveable { mutableStateOf(false) }
    val privacyPolicyUrl = stringResource(R.string.privacy_policy_url)
    var showFeedbackEmailDialog by rememberSaveable { mutableStateOf(false) }
    val (isAppLockGracePeriodMenuExpanded, setAppLockGracePeriodMenuExpanded) =
        remember { mutableStateOf(false) }
    val (isDarkModeMenuExpanded, setDarkModeMenuExpanded) = remember { mutableStateOf(false) }
    val (isLanguageMenuExpanded, setLanguageMenuExpanded) = remember { mutableStateOf(false) }
    // Hold the chosen language until the dropdown has fully closed, then apply it.
    // Applying immediately re-localizes the whole screen (~one frame after the tap)
    // while the menu is still playing its exit animation, so the menu visibly lingers
    // over the already-translated UI. Deferring to onExitFinished keeps the switch from
    // overlapping the dismiss.
    var pendingLanguageOption by remember { mutableStateOf<AppLanguageOption?>(null) }
    // Same deferral for dark mode: applying it immediately re-themes the whole screen
    // while the dropdown is still animating out, leaving the menu lingering over the
    // already-recolored UI. The row's supporting text still reflects the pick right away
    // (see below); only the theme switch waits for the dismiss.
    var pendingDarkModeOption by remember { mutableStateOf<DarkModeOption?>(null) }
    // Drop the optimistic value once the committed setting catches up, so the row tracks
    // the source of truth again without flickering back to the previous option.
    LaunchedEffect(settingsState.darkModeOption, pendingDarkModeOption) {
        if (pendingDarkModeOption == settingsState.darkModeOption) pendingDarkModeOption = null
    }
    val (isFirstDayOfWeekMenuExpanded, setFirstDayOfWeekMenuExpanded) =
        remember { mutableStateOf(false) }
    val appName = stringResource(R.string.app_name)
    val appVersionInfo = remember(context) { resolveAppVersionInfo(context) }
    val copyAppInfoMessage = stringResource(R.string.settings_about_app_info_copied)
    val easterEggMessage = stringResource(R.string.settings_about_app_info_easter_egg)
    var versionTapCount by remember { mutableIntStateOf(0) }
    var firstVersionTapAt by remember { mutableLongStateOf(0L) }
    var lastAppInfoCopiedAt by remember { mutableLongStateOf(-VERSION_COPY_THROTTLE_MS) }
    var lastCopiedToast by remember { mutableStateOf<Toast?>(null) }
    val appInfoSummary = stringResource(
        R.string.settings_about_app_info_version,
        appVersionInfo.versionName,
        appVersionInfo.versionCode.toString()
    )
    val appInfoCopyText = stringResource(
        R.string.settings_about_app_info_copy_text,
        appVersionInfo.versionName,
        appVersionInfo.versionCode.toString()
    )
    val feedbackSubject = stringResource(R.string.settings_about_feedback_subject, appName)
    val feedbackBody = stringResource(
        R.string.settings_about_feedback_body,
        appVersionInfo.versionName,
        appVersionInfo.versionCode.toString()
    )
    val feedbackChooserTitle = stringResource(R.string.settings_about_feedback_chooser_title)
    val feedbackNoEmailAppMessage = stringResource(R.string.settings_about_feedback_no_email_app)
    fun showExternalLinkDialog(url: String, @StringRes titleRes: Int) {
        pendingExternalUrl = url
        pendingExternalLinkTitleRes = titleRes
    }

    val scrollState = rememberScrollState()
    val weightSummary = formatWeightSummary(uiState.userProfile)
    val appLanguageOption = AppLanguageOption.fromLocale(rememberAppLocale())

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = pinnedTopAppBarScrollBehavior(
        scrollState = scrollState,
        state = topAppBarState
    )

    ScrollToTopSignalEffect(
        signal = scrollToTopSignal,
        topAppBarState = topAppBarState,
        scrollState = scrollState,
    )

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            HazeTopAppBar(
                modifier = Modifier.topAppBarScrollToTop(scrollBehavior, scrollState),
                title = {
                    val title = stringResource(R.string.tab_settings)
                    Text(
                        text = title,
                        modifier = Modifier.cjkTextOffset(title, amount = (-1.5).dp),
                    )
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
                    title = stringResource(R.string.settings_personalization),
                    topPadding = false
                ) {
                    item {
                        SettingsSegmentedListItem(
                            title = stringResource(R.string.personalization_weight),
                            supportingText = weightSummary,
                            supportingCjkTextOffsetEnabled = uiState.userProfile.weightOriginalValue == null,
                            onClick = {
                                if (!uiState.isWeightMutationInProgress) {
                                    showWeightDialog = true
                                }
                            },
                            leadingContent = {
                                SettingsLeadingIconSlot(
                                    painter = painterResource(R.drawable.ic_monitor_weight)
                                )
                            },
                            trailingContent = {
                                SettingsChevronTrailingIcon()
                            }
                        )
                    }

                    item {
                        SettingsSegmentedListItem(
                            title = stringResource(R.string.settings_personalization_calibration),
                            onClick = onCalibrationClick,
                            leadingContent = {
                                SettingsLeadingIconSlot(
                                    painter = painterResource(R.drawable.ic_experiment)
                                )
                            },
                            trailingContent = {
                                SettingsChevronTrailingIcon()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))

                HrtSection(title = stringResource(R.string.settings_notifications)) {
                    item {
                        SettingsSegmentedListItem(
                            title = stringResource(R.string.settings_reminders),
                            supportingText = stringResource(R.string.settings_reminders_summary),
                            enabled = hasNotificationAccess,
                            // Pin the title color to the enabled/disabled value directly instead of
                            // letting it ride the list-item's animated content color. The switch's
                            // enabled state snaps when access is granted, but the animated title
                            // faded in a frame behind it; an explicit color keeps the two in lockstep.
                            titleColor = if (hasNotificationAccess) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                            onClick = {
                                if (hasNotificationAccess) {
                                    onRemindersEnabledChange(!settingsState.remindersEnabled)
                                }
                            },
                            leadingContent = {
                                SettingsLeadingIconSlot(
                                    painter = painterResource(R.drawable.ic_notifications)
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = settingsState.remindersEnabled && hasNotificationAccess,
                                    onCheckedChange = onRemindersEnabledChange,
                                    enabled = hasNotificationAccess
                                )
                            }
                        )
                    }

                    if (reminderSupportState == SettingsReminderSupportState.NOTIFICATION_OFF) {
                        item {
                            SettingsSupportMessage(
                                text = stringResource(R.string.settings_reminders_permission_off_summary),
                                painter = painterResource(R.drawable.ic_error_outline),
                                onClick = { onRemindersEnabledChange(true) },
                                showChevron = true,
                            )
                        }
                    } else if (reminderSupportState == SettingsReminderSupportState.EXACT_ALARM_OFF) {
                        item {
                            SettingsSupportMessage(
                                text = stringResource(R.string.group_notifications_inexact_warning),
                                painter = painterResource(R.drawable.ic_error_outline),
                                onClick = { showExactAlarmRecoveryDialog = true },
                                showChevron = true,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))

                HrtSection(title = stringResource(R.string.settings_security)) {
                    item {
                        SettingsSegmentedListItem(
                            title = stringResource(R.string.settings_screen_lock_protection),
                            supportingText = stringResource(R.string.settings_screen_lock_protection_summary),
                            onClick = {
                                onScreenLockProtectionToggle(
                                    !settingsState.screenLockProtectionEnabled
                                )
                            },
                            leadingContent = {
                                SettingsLeadingIconSlot(
                                    painter = painterResource(R.drawable.ic_lock)
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = settingsState.screenLockProtectionEnabled,
                                    onCheckedChange = onScreenLockProtectionToggle
                                )
                            },
                        )
                    }

                    animatedItem(visible = settingsState.screenLockProtectionEnabled) {
                        Box {
                            SettingsSegmentedListItem(
                                title = stringResource(R.string.settings_app_lock_grace_period),
                                supportingText = stringResource(settingsState.appLockGracePeriodOption.labelRes),
                                onClick = { setAppLockGracePeriodMenuExpanded(true) },
                                leadingContent = {
                                    SettingsLeadingIconSlot(
                                        painter = painterResource(R.drawable.ic_lock_clock)
                                    )
                                }
                            )
                            HrtDropdownMenu(
                                expanded = isAppLockGracePeriodMenuExpanded,
                                onDismissRequest = { setAppLockGracePeriodMenuExpanded(false) },
                                modifier = Modifier.width(IntrinsicSize.Min),
                                items = AppLockGracePeriodOption.entries.map { option ->
                                    HrtDropdownMenuItem(
                                        text = stringResource(option.labelRes),
                                        onClick = { onAppLockGracePeriodOptionChange(option) },
                                    )
                                },
                            )
                        }
                    }

                    item {
                        SettingsSegmentedListItem(
                            title = stringResource(R.string.settings_hide_screen_content),
                            supportingText = stringResource(R.string.settings_hide_screen_content_summary),
                            onClick = {
                                onHideScreenContentEnabledChange(
                                    !settingsState.hideScreenContentEnabled
                                )
                            },
                            leadingContent = {
                                SettingsLeadingIconSlot(
                                    painter = painterResource(R.drawable.ic_visibility_off)
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = settingsState.hideScreenContentEnabled,
                                    onCheckedChange = onHideScreenContentEnabledChange
                                )
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))

                HrtSection(title = stringResource(R.string.settings_display)) {
                    item {
                        Box {
                            SettingsSegmentedListItem(
                                title = stringResource(R.string.settings_first_day_of_week),
                                supportingText = stringResource(settingsState.firstDayOfWeekOption.menuLabelRes),
                                onClick = { setFirstDayOfWeekMenuExpanded(true) },
                                leadingContent = {
                                    SettingsLeadingIconSlot(
                                        painter = painterResource(R.drawable.ic_today)
                                    )
                                }
                            )
                            HrtDropdownMenu(
                                expanded = isFirstDayOfWeekMenuExpanded,
                                onDismissRequest = { setFirstDayOfWeekMenuExpanded(false) },
                                modifier = Modifier.width(IntrinsicSize.Min),
                                items = FirstDayOfWeekOption.entries.map { option ->
                                    HrtDropdownMenuItem(
                                        text = stringResource(option.menuLabelRes),
                                        onClick = { onFirstDayOfWeekOptionChange(option) },
                                    )
                                },
                            )
                        }
                    }

                    item {
                        SettingsSegmentedListItem(
                            title = stringResource(R.string.settings_hide_reference_ranges),
                            onClick = {
                                onHideReferenceRangesChange(!settingsState.hideReferenceRanges)
                            },
                            leadingContent = {
                                SettingsLeadingIconSlot(
                                    painter = painterResource(R.drawable.ic_auto_stories_off),
                                    iconSize = 22.dp
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = settingsState.hideReferenceRanges,
                                    onCheckedChange = onHideReferenceRangesChange
                                )
                            }
                        )
                    }

                    item {
                        SettingsSegmentedListItem(
                            title = stringResource(R.string.settings_hide_archived_group_records),
                            onClick = {
                                onShowArchivedGroupRecordsChange(!settingsState.showArchivedGroupRecords)
                            },
                            leadingContent = {
                                SettingsLeadingIconSlot(
                                    painter = painterResource(R.drawable.ic_history_off)
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = !settingsState.showArchivedGroupRecords,
                                    onCheckedChange = { hideArchivedGroupRecords ->
                                        onShowArchivedGroupRecordsChange(!hideArchivedGroupRecords)
                                    }
                                )
                            }
                        )
                    }

                    item {
                        SettingsSegmentedListItem(
                            title = stringResource(R.string.settings_hide_medication_details),
                            supportingText = stringResource(R.string.settings_hide_medication_details_summary),
                            onClick = {
                                onHideMedicationDetailsChange(!settingsState.hideMedicationDetails)
                            },
                            leadingContent = {
                                SettingsLeadingIconSlot(
                                    painter = painterResource(R.drawable.ic_pill_off)
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = settingsState.hideMedicationDetails,
                                    onCheckedChange = onHideMedicationDetailsChange
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))

                HrtSection(title = stringResource(R.string.settings_appearance)) {
                    item {
                        SettingsSegmentedListItem(
                            title = stringResource(R.string.settings_widget_appearance),
                            onClick = { showWidgetAppearanceDialog = true },
                            leadingContent = {
                                SettingsLeadingIconSlot(
                                    painter = painterResource(R.drawable.ic_widgets)
                                )
                            },
                            trailingContent = {
                                SettingsChevronTrailingIcon()
                            }
                        )
                    }

                    item {
                        Box {
                            SettingsSegmentedListItem(
                                title = stringResource(R.string.settings_app_language),
                                // Derive the displayed language from the locale the UI is actually
                                // rendering in (LocalConfiguration), not the ViewModel's
                                // appLanguageOption. Both mirror the same setting, but the global
                                // locale flips a frame before the ViewModel's combine/stateIn relays
                                // the new option, which made this row briefly show the old language
                                // name. Reading the live locale keeps it in lockstep with the rest of
                                // the re-localized UI.
                                supportingText = stringResource(appLanguageOption.labelRes),
                                onClick = { setLanguageMenuExpanded(true) },
                                leadingContent = {
                                    SettingsLeadingIconSlot(
                                        painter = painterResource(R.drawable.ic_language)
                                    )
                                }
                            )
                            HrtDropdownMenu(
                                expanded = isLanguageMenuExpanded,
                                onDismissRequest = { setLanguageMenuExpanded(false) },
                                modifier = Modifier.width(IntrinsicSize.Min),
                                items = AppLanguageOption.entries.map { option ->
                                    HrtDropdownMenuItem(
                                        text = stringResource(option.labelRes),
                                        onClick = { pendingLanguageOption = option },
                                    )
                                },
                                onExitFinished = {
                                    pendingLanguageOption?.let { option ->
                                        onAppLanguageOptionChange(option)
                                        pendingLanguageOption = null
                                    }
                                },
                            )
                        }
                    }

                    item {
                        Box {
                            SettingsSegmentedListItem(
                                title = stringResource(R.string.settings_dark_mode),
                                // Show the just-picked option immediately even though the actual
                                // theme switch is deferred until the dropdown finishes dismissing.
                                supportingText = stringResource(
                                    (pendingDarkModeOption ?: settingsState.darkModeOption).labelRes
                                ),
                                onClick = { setDarkModeMenuExpanded(true) },
                                leadingContent = {
                                    SettingsLeadingIconSlot(
                                        painter = painterResource(R.drawable.ic_dark_mode)
                                    )
                                }
                            )
                            HrtDropdownMenu(
                                expanded = isDarkModeMenuExpanded,
                                onDismissRequest = { setDarkModeMenuExpanded(false) },
                                modifier = Modifier.width(IntrinsicSize.Min),
                                items = DarkModeOption.entries.map { option ->
                                    HrtDropdownMenuItem(
                                        text = stringResource(option.labelRes),
                                        onClick = { pendingDarkModeOption = option },
                                    )
                                },
                                onExitFinished = {
                                    // Apply the deferred switch once the menu is gone. The pending
                                    // value is cleared by the LaunchedEffect above once the committed
                                    // setting catches up, so the row never flickers back.
                                    pendingDarkModeOption?.let(onDarkModeOptionChange)
                                },
                            )
                        }
                    }

                    item {
                        SettingsSegmentedListItem(
                            title = stringResource(R.string.settings_amoled_black),
                            onClick = {
                                onPureBlackEnabledChange(!settingsState.pureBlackEnabled)
                            },
                            leadingContent = {
                                SettingsLeadingIconSlot(
                                    painter = painterResource(R.drawable.ic_invert_colors)
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = settingsState.pureBlackEnabled,
                                    onCheckedChange = onPureBlackEnabledChange
                                )
                            }
                        )
                    }

                    if (shouldShowAdaptiveColor()) {
                        item {
                            SettingsSegmentedListItem(
                                title = stringResource(R.string.settings_adaptive_color),
                                onClick = {
                                    onAdaptiveColorEnabledChange(!settingsState.adaptiveColorEnabled)
                                },
                                leadingContent = {
                                    SettingsLeadingIconSlot(
                                        painter = painterResource(R.drawable.ic_palette),
                                        iconSize = 22.dp
                                    )
                                },
                                trailingContent = {
                                    Switch(
                                        checked = settingsState.adaptiveColorEnabled,
                                        onCheckedChange = onAdaptiveColorEnabledChange
                                    )
                                }
                            )
                        }
                    }

                    if (isHazeBlurSupported()) {
                        item {
                            SettingsSegmentedListItem(
                                title = stringResource(R.string.settings_haze_blur),
                                onClick = {
                                    onHazeBlurEnabledChange(!settingsState.hazeBlurEnabled)
                                },
                                leadingContent = {
                                    SettingsLeadingIconSlot(
                                        painter = painterResource(R.drawable.ic_blur_on)
                                    )
                                },
                                trailingContent = {
                                    Switch(
                                        checked = settingsState.hazeBlurEnabled,
                                        onCheckedChange = onHazeBlurEnabledChange
                                    )
                                }
                            )
                        }
                    }

                    if (shouldShowCjkTextOffset(appLanguageOption)) {
                        item {
                            SettingsSegmentedListItem(
                                title = stringResource(R.string.settings_cjk_text_offset),
                                supportingText = stringResource(R.string.settings_cjk_text_offset_summary),
                                onClick = {
                                    onCjkTextOffsetEnabledChange(!settingsState.cjkTextOffsetEnabled)
                                },
                                leadingContent = {
                                    SettingsLeadingIconSlot(
                                        painter = painterResource(R.drawable.ic_text_up)
                                    )
                                },
                                trailingContent = {
                                    Switch(
                                        checked = settingsState.cjkTextOffsetEnabled,
                                        onCheckedChange = onCjkTextOffsetEnabledChange
                                    )
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))

                HrtSection(title = stringResource(R.string.settings_backup_restore)) {
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

                if (showDiagnosticsExport) {
                    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))

                    HrtSection(title = stringResource(R.string.settings_diagnostics)) {
                        item {
                            SettingsSegmentedListItem(
                                title = stringResource(R.string.settings_diagnostics_export_logs),
                                onClick = onExportDiagnosticLogsClick,
                                leadingContent = {
                                    SettingsLeadingIconSlot(
                                        painter = painterResource(R.drawable.ic_bug_report)
                                    )
                                },
                                trailingContent = {
                                    SettingsChevronTrailingIcon()
                                }
                            )
                        }

                        item {
                            SettingsSegmentedListItem(
                                title = "Calibration (debug)",
                                onClick = onPkCalibrationDebugClick,
                                leadingContent = {
                                    SettingsLeadingIconSlot(
                                        painter = painterResource(R.drawable.ic_bug_report)
                                    )
                                },
                                trailingContent = {
                                    SettingsChevronTrailingIcon()
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))

                HrtSection(title = stringResource(R.string.settings_about)) {
                    item {
                        SettingsSegmentedListItem(
                            title = stringResource(R.string.settings_about_privacy_policy),
                            onClick = {
                                showExternalLinkDialog(
                                    url = privacyPolicyUrl,
                                    titleRes = R.string.settings_about_privacy_policy
                                )
                            },
                            leadingContent = {
                                SettingsLeadingIconSlot(
                                    painter = painterResource(R.drawable.ic_privacy_tip)
                                )
                            },
                            trailingContent = {
                                SettingsLinkTrailingIcon()
                            }
                        )
                    }

                    item {
                        SettingsSegmentedListItem(
                            title = stringResource(R.string.settings_about_open_source_licenses),
                            onClick = {
                                showExternalLinkDialog(
                                    url = BuildConfig.THIRD_PARTY_NOTICES_URL,
                                    titleRes = R.string.settings_about_open_source_licenses
                                )
                            },
                            leadingContent = {
                                SettingsLeadingIconSlot(
                                    painter = painterResource(R.drawable.ic_code_blocks)
                                )
                            },
                            trailingContent = {
                                SettingsLinkTrailingIcon()
                            }
                        )
                    }

                    item {
                        SettingsSegmentedListItem(
                            title = stringResource(R.string.settings_about_model),
                            onClick = {
                                showExternalLinkDialog(
                                    url = MODEL_REPOSITORY_URL,
                                    titleRes = R.string.settings_about_model
                                )
                            },
                            leadingContent = {
                                SettingsLeadingIconSlot(
                                    painter = painterResource(R.drawable.ic_quick_reference)
                                )
                            },
                            trailingContent = {
                                SettingsLinkTrailingIcon()
                            }
                        )
                    }

                    item {
                        SettingsSegmentedListItem(
                            title = stringResource(R.string.settings_github_repo),
                            onClick = {
                                showExternalLinkDialog(
                                    url = APP_REPOSITORY_URL,
                                    titleRes = R.string.settings_github_repo
                                )
                            },
                            leadingContent = {
                                SettingsLeadingIconSlot(
                                    painter = painterResource(R.drawable.ic_github),
                                    iconSize = 20.dp
                                )
                            },
                            trailingContent = {
                                SettingsLinkTrailingIcon()
                            }
                        )
                    }

                    item {
                        SettingsSegmentedListItem(
                            title = stringResource(R.string.settings_about_contact_developer),
                            onClick = {
                                showExternalLinkDialog(
                                    url = DEVELOPER_X_URL,
                                    titleRes = R.string.settings_about_contact_developer
                                )
                            },
                            leadingContent = {
                                SettingsLeadingIconSlot(
                                    painter = painterResource(R.drawable.ic_x),
                                    iconSize = 18.dp
                                )
                            },
                            trailingContent = {
                                SettingsLinkTrailingIcon()
                            }
                        )
                    }

                    item {
                        SettingsSegmentedListItem(
                            title = stringResource(R.string.settings_about_feedback),
                            supportingText = stringResource(R.string.settings_about_feedback_summary),
                            onClick = { showFeedbackEmailDialog = true },
                            leadingContent = {
                                SettingsLeadingIconSlot(
                                    painter = painterResource(R.drawable.ic_contact_support)
                                )
                            },
                            trailingContent = {
                                SettingsLinkTrailingIcon()
                            }
                        )
                    }

                    item {
                        SettingsSegmentedListItem(
                            title = appName,
                            supportingText = appInfoSummary,
                            onClick = {
                                val now = SystemClock.elapsedRealtime()
                                val isInsideTapWindow =
                                    versionTapCount > 0 && now - firstVersionTapAt <= VERSION_TAP_WINDOW_MS
                                versionTapCount = if (isInsideTapWindow) {
                                    versionTapCount + 1
                                } else {
                                    firstVersionTapAt = now
                                    1
                                }
                                if (versionTapCount >= VERSION_EASTER_EGG_TAP_COUNT) {
                                    versionTapCount = 0
                                    firstVersionTapAt = 0L
                                    lastCopiedToast?.cancel()
                                    lastCopiedToast = null
                                    Toast.makeText(context, easterEggMessage, Toast.LENGTH_SHORT)
                                        .show()
                                } else if (now - lastAppInfoCopiedAt >= VERSION_COPY_THROTTLE_MS) {
                                    context.getSystemService(ClipboardManager::class.java)
                                        ?.setPrimaryClip(
                                            ClipData.newPlainText(
                                                appName,
                                                appInfoCopyText
                                            )
                                        )
                                    lastAppInfoCopiedAt = now
                                    lastCopiedToast?.cancel()
                                    lastCopiedToast = Toast.makeText(
                                        context,
                                        copyAppInfoMessage,
                                        Toast.LENGTH_SHORT
                                    )
                                        .also { it.show() }
                                }
                            },
                            leadingContent = {
                                SettingsLeadingIconSlot(
                                    painter = painterResource(R.drawable.ic_info_filled),
                                    iconSize = 22.dp
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (showExactAlarmRecoveryDialog) {
        ExactAlarmAccessDialog(
            onConfirm = {
                showExactAlarmRecoveryDialog = false
                onRequestExactAlarmAccess()
            },
            onDismiss = { showExactAlarmRecoveryDialog = false }
        )
    }

    if (showWidgetAppearanceDialog) {
        WidgetAppearanceDialog(
            appearance = widgetAppearance,
            onAppearanceChange = onWidgetAppearanceChange,
            onDismiss = { showWidgetAppearanceDialog = false },
        )
    }

    val externalUrl = pendingExternalUrl
    if (externalUrl != null || showFeedbackEmailDialog) {
        @StringRes val dialogTitleRes = when {
            externalUrl != null -> pendingExternalLinkTitleRes
            showFeedbackEmailDialog -> R.string.settings_about_feedback
            else -> null
        } ?: R.string.settings_about_open_link_title
        HazeAlertDialog(
            onDismissRequest = {
                pendingExternalUrl = null
                pendingExternalLinkTitleRes = null
                showFeedbackEmailDialog = false
            },
            title = { Text(text = stringResource(dialogTitleRes)) },
            text = { Text(text = stringResource(R.string.settings_about_open_link_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        when {
                            externalUrl != null -> {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        externalUrl.toUri()
                                    )
                                )
                            }

                            showFeedbackEmailDialog -> {
                                launchFeedbackEmail(
                                    context = context,
                                    subject = feedbackSubject,
                                    body = feedbackBody,
                                    chooserTitle = feedbackChooserTitle,
                                    noEmailAppMessage = feedbackNoEmailAppMessage
                                )
                            }
                        }
                        pendingExternalUrl = null
                        pendingExternalLinkTitleRes = null
                        showFeedbackEmailDialog = false
                    }
                ) {
                    Text(text = stringResource(R.string.settings_about_open_link_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingExternalUrl = null
                        pendingExternalLinkTitleRes = null
                        showFeedbackEmailDialog = false
                    }
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showWeightDialog) {
        WeightDialog(
            profile = uiState.userProfile,
            onSave = { value, unit ->
                showWeightDialog = false
                onWeightSave(value, unit)
            },
            onClear = {
                showWeightDialog = false
                onWeightClear()
            },
            onDismiss = {
                if (!uiState.isWeightMutationInProgress) {
                    showWeightDialog = false
                }
            },
            isInProgress = uiState.isWeightMutationInProgress,
        )
    }
}

internal enum class SettingsReminderSupportState {
    NONE,
    NOTIFICATION_OFF,
    EXACT_ALARM_OFF,
}

internal fun resolveSettingsReminderSupportState(
    hasNotificationAccess: Boolean,
    hasExactAlarmAccess: Boolean,
    remindersEnabled: Boolean,
    isReminderEnablePending: Boolean = false,
): SettingsReminderSupportState {
    if (!hasNotificationAccess) {
        return SettingsReminderSupportState.NOTIFICATION_OFF
    }

    return if ((remindersEnabled || isReminderEnablePending) && !hasExactAlarmAccess) {
        SettingsReminderSupportState.EXACT_ALARM_OFF
    } else {
        SettingsReminderSupportState.NONE
    }
}

internal fun shouldShowAdaptiveColor(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
    sdkInt >= Build.VERSION_CODES.S

internal fun shouldShowCjkTextOffset(appLanguageOption: AppLanguageOption): Boolean =
    appLanguageOption == AppLanguageOption.SIMPLIFIED_CHINESE ||
        appLanguageOption == AppLanguageOption.TRADITIONAL_CHINESE ||
        appLanguageOption == AppLanguageOption.CANTONESE

@Composable
private fun SettingsLeadingIconSlot(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    painter: Painter? = null,
    tint: Color? = null,
    iconSize: Dp? = null,
) {
    val resolvedTint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant
    val iconContent: @Composable (Modifier) -> Unit = { iconModifier ->
        when {
            icon != null -> {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = resolvedTint,
                    modifier = iconModifier
                )
            }

            painter != null -> {
                Icon(
                    painter = painter,
                    contentDescription = null,
                    tint = resolvedTint,
                    modifier = iconModifier
                )
            }
        }
    }
    if (iconSize != null) {
        // Center a sub-24.dp icon inside the standard 24.dp slot so every row's
        // leading content keeps the same footprint regardless of the asset size.
        Box(
            modifier = modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            iconContent(Modifier.size(iconSize))
        }
    } else {
        iconContent(modifier)
    }
}

@Composable
private fun SettingsChevronTrailingIcon() {
    Icon(
        imageVector = Icons.Rounded.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SettingsLinkTrailingIcon() {
    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_open_in_new),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SettingsSegmentedListItem(
    title: String,
    index: Int? = null,
    count: Int? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingText: String? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    titleTextStyle: TextStyle? = null,
    supportingTextStyle: TextStyle? = null,
    titleColor: Color? = null,
    supportingTextColor: Color? = null,
    titleCjkTextOffsetEnabled: Boolean = true,
    supportingCjkTextOffsetEnabled: Boolean = true,
) {
    PreferenceSegmentedListItem(
        title = title,
        index = index,
        count = count,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        supportingText = supportingText,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        titleTextStyle = titleTextStyle,
        supportingTextStyle = supportingTextStyle,
        titleColor = titleColor,
        supportingTextColor = supportingTextColor,
        titleCjkTextOffsetEnabled = titleCjkTextOffsetEnabled,
        supportingCjkTextOffsetEnabled = supportingCjkTextOffsetEnabled,
    )
}

@Composable
private fun SettingsSupportMessage(
    text: String,
    icon: ImageVector? = null,
    painter: Painter? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = false,
) {
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified
    ) {
        SettingsSegmentedListItem(
            title = text,
            onClick = onClick ?: {},
            modifier = Modifier.wrapContentHeight(),
            leadingContent = {
                SettingsLeadingIconSlot(
                    icon = icon,
                    painter = painter,
                    tint = MaterialTheme.colorScheme.tertiary,
                    iconSize = 22.dp
                )
            },
            trailingContent = if (showChevron) {
                { SettingsChevronTrailingIcon() }
            } else {
                null
            },
            titleTextStyle = MaterialTheme.typography.labelMedium,
            titleColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun requestExactAlarmAccess(
    context: Context,
    launch: (Intent) -> Unit
) {
    if (canScheduleExactAlarms(context)) {
        return
    }

    launch(
        Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            "package:${context.packageName}".toUri()
        )
    )
}

@Composable
private fun formatWeightSummary(profile: UserProfile): String {
    val value = profile.weightOriginalValue
    val unit = profile.weightOriginalUnit
    return if (value == null) {
        stringResource(R.string.personalization_weight_not_set)
    } else {
        val formatted = if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            value.toString()
        }
        stringResource(
            R.string.personalization_weight_display,
            formatted,
            stringResource(unit.shortLabelRes)
        )
    }
}

private data class AppVersionInfo(
    val versionName: String,
    val versionCode: Long
)

private fun resolveAppVersionInfo(context: Context): AppVersionInfo {
    val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0)
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val versionCode = resolvePackageVersionCode(packageInfo)
    val versionName = packageInfo.versionName?.takeIf { it.isNotBlank() } ?: versionCode.toString()
    return AppVersionInfo(
        versionName = versionName,
        versionCode = versionCode
    )
}

internal fun resolvePackageVersionCode(
    packageInfo: PackageInfo,
    sdkInt: Int = Build.VERSION.SDK_INT,
): Long {
    if (sdkInt < Build.VERSION_CODES.P) {
        @Suppress("DEPRECATION")
        return packageInfo.versionCode.toLong()
    }
    return resolveLongPackageVersionCode(packageInfo)
}

@SuppressLint("NewApi")
private fun resolveLongPackageVersionCode(packageInfo: PackageInfo): Long {
    return packageInfo.longVersionCode
}

private fun launchFeedbackEmail(
    context: Context,
    subject: String,
    body: String,
    chooserTitle: String,
    noEmailAppMessage: String
) {
    val uri = (FEEDBACK_EMAIL_URI +
            "?subject=${Uri.encode(subject)}" +
            "&body=${Uri.encode(body)}").toUri()

    val intent = Intent(Intent.ACTION_SENDTO, uri)

    try {
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    } catch (exception: ActivityNotFoundException) {
        Toast.makeText(context, noEmailAppMessage, Toast.LENGTH_SHORT).show()
    }
}

private const val FEEDBACK_EMAIL_URI = "mailto:support@asterismlabs.io"
private const val MODEL_REPOSITORY_URL =
    "https://github.com/LaoZhong-Mihari/HRT-Recorder-PKcomponent-Test"

private const val APP_REPOSITORY_URL =
    "https://github.com/mkx173/Featherline"
private const val DEVELOPER_X_URL = "https://x.com/mikanmkx"
private const val VERSION_COPY_THROTTLE_MS = 2_000L
private const val VERSION_EASTER_EGG_TAP_COUNT = 5
private const val VERSION_TAP_WINDOW_MS = 1_000L

@Preview(
    name = "Settings Screen",
    showBackground = true,
    widthDp = 420,
    heightDp = 920
)
@Composable
private fun SettingsScreenPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        SettingsScreenContent(
            uiState = SettingsUiState(
                settingsState = SettingsState(
                    darkModeOption = DarkModeOption.DARK,
                    adaptiveColorEnabled = true,
                    appLanguageOption = AppLanguageOption.ENGLISH,
                    remindersEnabled = true,
                    screenLockProtectionEnabled = true,
                    appLockGracePeriodOption = AppLockGracePeriodOption.ONE_MINUTE,
                    hideScreenContentEnabled = true,
                ),
                userProfile = UserProfile(
                    weightKg = 52.2,
                    weightOriginalValue = 115.0,
                    weightOriginalUnit = WeightUnit.POUNDS,
                )
            ),
            widgetAppearance = WidgetAppearance.Default,
            hasNotificationAccess = true,
            reminderSupportState = SettingsReminderSupportState.EXACT_ALARM_OFF,
            onWeightSave = { _, _ -> },
            onWeightClear = { },
            onRemindersEnabledChange = { },
            onRequestExactAlarmAccess = { },
            onScreenLockProtectionToggle = { },
            onAppLockGracePeriodOptionChange = { },
            onHideScreenContentEnabledChange = { },
            onAppLanguageOptionChange = { },
            onFirstDayOfWeekOptionChange = { },
            onDarkModeOptionChange = { },
            onAdaptiveColorEnabledChange = { },
            onPureBlackEnabledChange = { },
            onCjkTextOffsetEnabledChange = { },
            onHazeBlurEnabledChange = { },
            onShowArchivedGroupRecordsChange = { },
            onHideReferenceRangesChange = { },
            onHideMedicationDetailsChange = { },
            onWidgetAppearanceChange = { },
            onBackupToFileClick = { },
            onRestoreFromFileClick = { },
            onImportExternalTrackerClick = { },
            showDiagnosticsExport = true,
            onExportDiagnosticLogsClick = { },
            onCalibrationClick = { },
        )
    }
}

private const val MINIMUM_BACKUP_PASSWORD_LENGTH = 6
private const val DIAGNOSTICS_EXPORT_MIME_TYPE = "text/plain"
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
