package com.mkx.hrttracker.ui.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.LocalActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.personalization.UserProfile
import com.mkx.hrttracker.model.personalization.WeightUnit
import com.mkx.hrttracker.model.settings.AppLanguageOption
import com.mkx.hrttracker.model.settings.AppLockGracePeriodOption
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.reminder.canPostNotifications
import com.mkx.hrttracker.reminder.canScheduleExactAlarms
import com.mkx.hrttracker.reminder.shouldShowNotificationPermissionRecoveryToast
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.ExactAlarmAccessDialog
import com.mkx.hrttracker.ui.security.AppAuthenticationPromptEffect
import com.mkx.hrttracker.ui.security.AppLockViewModel
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onCalibrationClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settingsState = uiState.settingsState
    val context = LocalContext.current
    val activity = LocalActivity.current
    val appLockViewModel = (activity as? ComponentActivity)?.let { activityOwner ->
        hiltViewModel<AppLockViewModel>(viewModelStoreOwner = activityOwner)
    }
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    var hasNotificationAccess by remember { mutableStateOf(canPostNotifications(context)) }
    var showInexactReminderWarning by remember { mutableStateOf(false) }
    var hasRequestedNotificationPermission by rememberSaveable { mutableStateOf(false) }
    var isBackupExportInProgress by rememberSaveable { mutableStateOf(false) }
    var isBackupRestoreInProgress by rememberSaveable { mutableStateOf(false) }
    var pendingRestoreUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingRestoreDisplayName by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingRestoreRequest = pendingRestoreUriString?.let { restoreUriString ->
        PendingBackupRestoreRequest(
            uri = Uri.parse(restoreUriString),
            displayName = pendingRestoreDisplayName,
        )
    }
    val reminderNotificationsUnavailableMessage =
        stringResource(R.string.settings_reminders_notifications_unavailable)
    val backupExportFailedMessage = stringResource(R.string.settings_backup_export_failed)
    val backupRestoreFailedMessage = stringResource(R.string.settings_backup_restore_failed)
    val backupRestoreSuccessMessage = stringResource(R.string.settings_backup_restore_success)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.setRemindersEnabled(true)
            hasNotificationAccess = canPostNotifications(context)
        } else {
            viewModel.setRemindersEnabled(false)
            hasNotificationAccess = false
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
        showInexactReminderWarning =
            hasNotificationAccess &&
                settingsState.remindersEnabled &&
                !canScheduleExactAlarms(context)
    }
    val backupDirectoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { directoryUri ->
        appLockViewModel?.onExternalActivityResult(keepUnlocked = directoryUri != null)
        if (directoryUri == null || isBackupExportInProgress || isBackupRestoreInProgress) {
            return@rememberLauncherForActivityResult
        }

        coroutineScope.launch {
            isBackupExportInProgress = true
            try {
                val exportedFile = viewModel.exportPlaintextBackup(directoryUri)
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.settings_backup_export_success,
                        exportedFile.displayName,
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (_: Exception) {
                Toast.makeText(
                    context,
                    backupExportFailedMessage,
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                isBackupExportInProgress = false
            }
        }
    }
    val restoreBackupFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { fileUri ->
        appLockViewModel?.onExternalActivityResult(keepUnlocked = fileUri != null)
        if (fileUri == null || isBackupExportInProgress || isBackupRestoreInProgress) {
            return@rememberLauncherForActivityResult
        }
        pendingRestoreUriString = fileUri.toString()
        pendingRestoreDisplayName = resolveDocumentDisplayName(context, fileUri)
    }

    LaunchedEffect(configuration) {
        viewModel.refreshAppLanguageOption()
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationAccess = canPostNotifications(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(hasNotificationAccess, settingsState.remindersEnabled) {
        if (!hasNotificationAccess && settingsState.remindersEnabled) {
            viewModel.setRemindersEnabled(false)
        }
    }

    LaunchedEffect(hasNotificationAccess, settingsState.remindersEnabled) {
        showInexactReminderWarning =
            hasNotificationAccess &&
                settingsState.remindersEnabled &&
                !canScheduleExactAlarms(context)
    }

    AppAuthenticationPromptEffect(
        request = uiState.pendingPrompt,
        onAuthenticated = viewModel::onScreenLockProtectionAuthenticated,
        onError = viewModel::onScreenLockProtectionPromptError
    )

    val onRemindersEnabledChange = { enabled: Boolean ->
        if (!enabled) {
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
            Toast.makeText(
                context,
                reminderNotificationsUnavailableMessage,
                Toast.LENGTH_SHORT
            ).show()
        } else {
            viewModel.setRemindersEnabled(true)
        }
    }

    SettingsScreenContent(
        uiState = uiState,
        hasNotificationAccess = hasNotificationAccess,
        showInexactReminderWarning = showInexactReminderWarning,
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
        onDarkModeOptionChange = viewModel::setDarkModeOption,
        onAdaptiveColorEnabledChange = viewModel::setAdaptiveColorEnabled,
        onBackupToFileClick = {
            if (!isBackupExportInProgress && !isBackupRestoreInProgress) {
                appLockViewModel?.allowNextExternalActivityBypass()
                backupDirectoryLauncher.launch(null)
            }
        },
        onRestoreFromFileClick = {
            if (!isBackupExportInProgress && !isBackupRestoreInProgress) {
                appLockViewModel?.allowNextExternalActivityBypass()
                restoreBackupFileLauncher.launch(arrayOf("application/json"))
            }
        },
        isBackupExportInProgress = isBackupExportInProgress,
        isBackupRestoreInProgress = isBackupRestoreInProgress,
        onCalibrationClick = onCalibrationClick,
        modifier = modifier
    )

    pendingRestoreRequest?.let { restoreRequest ->
        AlertDialog(
            onDismissRequest = {
                pendingRestoreUriString = null
                pendingRestoreDisplayName = null
            },
            title = { Text(text = stringResource(R.string.settings_backup_restore_confirm_title)) },
            text = {
                Text(
                    text = restoreRequest.displayName?.let { displayName ->
                        context.getString(
                            R.string.settings_backup_restore_confirm_message_with_name,
                            displayName,
                        )
                    } ?: stringResource(R.string.settings_backup_restore_confirm_message)
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isBackupRestoreInProgress,
                    onClick = {
                        coroutineScope.launch {
                            isBackupRestoreInProgress = true
                            try {
                                viewModel.restorePlaintextBackup(restoreRequest.uri)
                                Toast.makeText(
                                    context,
                                    backupRestoreSuccessMessage,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } catch (_: Exception) {
                                Toast.makeText(
                                    context,
                                    backupRestoreFailedMessage,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } finally {
                                isBackupRestoreInProgress = false
                                pendingRestoreUriString = null
                                pendingRestoreDisplayName = null
                            }
                        }
                    }
                ) {
                    Text(text = stringResource(R.string.settings_backup_restore_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isBackupRestoreInProgress,
                    onClick = {
                        pendingRestoreUriString = null
                        pendingRestoreDisplayName = null
                    }
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreenContent(
    uiState: SettingsUiState,
    hasNotificationAccess: Boolean,
    showInexactReminderWarning: Boolean,
    onWeightSave: (Double, WeightUnit) -> Unit,
    onWeightClear: () -> Unit,
    onRemindersEnabledChange: (Boolean) -> Unit,
    onRequestExactAlarmAccess: () -> Unit,
    onScreenLockProtectionToggle: (Boolean) -> Unit,
    onAppLockGracePeriodOptionChange: (AppLockGracePeriodOption) -> Unit,
    onHideScreenContentEnabledChange: (Boolean) -> Unit,
    onAppLanguageOptionChange: (AppLanguageOption) -> Unit,
    onDarkModeOptionChange: (DarkModeOption) -> Unit,
    onAdaptiveColorEnabledChange: (Boolean) -> Unit,
    onBackupToFileClick: () -> Unit,
    onRestoreFromFileClick: () -> Unit,
    isBackupExportInProgress: Boolean,
    isBackupRestoreInProgress: Boolean,
    onCalibrationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settingsState = uiState.settingsState
    val context = LocalContext.current
    var showWeightDialog by rememberSaveable { mutableStateOf(false) }
    var showPrivacyPolicyDialog by rememberSaveable { mutableStateOf(false) }
    var showExactAlarmRecoveryDialog by rememberSaveable { mutableStateOf(false) }
    var pendingExternalUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val (isAppLockGracePeriodMenuExpanded, setAppLockGracePeriodMenuExpanded) =
        remember { mutableStateOf(false) }
    val (isDarkModeMenuExpanded, setDarkModeMenuExpanded) = remember { mutableStateOf(false) }
    val (isLanguageMenuExpanded, setLanguageMenuExpanded) = remember { mutableStateOf(false) }
    val appName = stringResource(R.string.app_name)
    val appVersionInfo = remember(context) { resolveAppVersionInfo(context) }
    val copyAppInfoMessage = stringResource(R.string.settings_about_app_info_copied)
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

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.tab_settings)) },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(dimensionResource(R.dimen.padding_medium)),
        ) {
            SettingsSectionTitle(
                text = stringResource(R.string.settings_personalization)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(
                    dimensionResource(R.dimen.list_segment_gap)
                )
            ) {
                EditorSegmentedListItem(
                    index = 0,
                    count = 2,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showWeightDialog = true },
                    leadingContent = {
                        SettingsLeadingIconSlot(
                            icon = Icons.Rounded.MonitorWeight
                        )
                    },
                    supportingContent = {
                        Text(text = formatWeightSummary(uiState.userProfile))
                    }
                ) {
                    Text(text = stringResource(R.string.personalization_weight))
                }

                EditorSegmentedListItem(
                    index = 1,
                    count = 2,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onCalibrationClick,
                    leadingContent = {
                        SettingsLeadingIconSlot(
                            painter = painterResource(R.drawable.ic_experiment)
                        )
                    },
                    trailingContent = {
                        SettingsChevronTrailingIcon()
                    }
                ) {
                    Text(text = stringResource(R.string.settings_personalization_calibration))
                }
            }

            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))

            SettingsSectionTitle(
                text = stringResource(R.string.settings_notifications)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(
                    dimensionResource(R.dimen.list_segment_gap)
                )
            ) {
                EditorSegmentedListItem(
                    index = 0,
                    count = if (!hasNotificationAccess || showInexactReminderWarning) 2 else 1,
                    enabled = hasNotificationAccess,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (hasNotificationAccess) {
                            onRemindersEnabledChange(!settingsState.remindersEnabled)
                        }
                    },
                    leadingContent = {
                        SettingsLeadingIconSlot(
                            icon = Icons.Rounded.Notifications
                        )
                    },
                    supportingContent = {
                        Text(text = stringResource(R.string.settings_reminders_summary))
                    },
                    trailingContent = {
                        Switch(
                            checked = settingsState.remindersEnabled && hasNotificationAccess,
                            onCheckedChange = onRemindersEnabledChange,
                            enabled = hasNotificationAccess
                        )
                    }
                ) {
                    Text(text = stringResource(R.string.settings_reminders))
                }

                if (!hasNotificationAccess) {
                    SettingsSupportMessage(
                        text = stringResource(R.string.settings_reminders_permission_off_summary),
                        icon = Icons.Rounded.ErrorOutline,
                        onClick = { onRemindersEnabledChange(true) },
                        showChevron = true,
                        index = 1,
                        count = 2
                    )
                } else if (showInexactReminderWarning) {
                    SettingsSupportMessage(
                        text = stringResource(R.string.group_notifications_inexact_warning),
                        icon = Icons.Rounded.ErrorOutline,
                        onClick = { showExactAlarmRecoveryDialog = true },
                        showChevron = true,
                        index = 1,
                        count = 2
                    )
                }
            }

            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))

            SettingsSectionTitle(
                text = stringResource(R.string.settings_security)
            )

            val securityItemCount = if (settingsState.screenLockProtectionEnabled) 3 else 2

            Column(
                verticalArrangement = Arrangement.spacedBy(
                    dimensionResource(R.dimen.list_segment_gap)
                )
            ) {
                EditorSegmentedListItem(
                    index = 0,
                    count = securityItemCount,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onScreenLockProtectionToggle(
                            !settingsState.screenLockProtectionEnabled
                        )
                    },
                    leadingContent = {
                        SettingsLeadingIconSlot(
                            icon = Icons.Rounded.Lock
                        )
                    },
                    supportingContent = {
                        Text(text = stringResource(R.string.settings_screen_lock_protection_summary))
                    },
                    trailingContent = {
                        Switch(
                            checked = settingsState.screenLockProtectionEnabled,
                            onCheckedChange = onScreenLockProtectionToggle
                        )
                    }
                ) {
                    Text(text = stringResource(R.string.settings_screen_lock_protection))
                }

                if (settingsState.screenLockProtectionEnabled) {
                    Box {
                        EditorSegmentedListItem(
                            index = 1,
                            count = securityItemCount,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { setAppLockGracePeriodMenuExpanded(true) },
                            leadingContent = {
                                SettingsLeadingIconSlot(
                                    icon = Icons.Rounded.LockClock
                                )
                            },
                            supportingContent = {
                                Text(text = stringResource(settingsState.appLockGracePeriodOption.labelRes))
                            }
                        ) {
                            Text(text = stringResource(R.string.settings_app_lock_grace_period))
                        }
                        DropdownMenu(
                            expanded = isAppLockGracePeriodMenuExpanded,
                            onDismissRequest = { setAppLockGracePeriodMenuExpanded(false) },
                            modifier = Modifier.width(IntrinsicSize.Min)
                        ) {
                            AppLockGracePeriodOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(option.labelRes)) },
                                    onClick = {
                                        onAppLockGracePeriodOptionChange(option)
                                        setAppLockGracePeriodMenuExpanded(false)
                                    }
                                )
                            }
                        }
                    }
                }

                EditorSegmentedListItem(
                    index = if (settingsState.screenLockProtectionEnabled) 2 else 1,
                    count = securityItemCount,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onHideScreenContentEnabledChange(
                            !settingsState.hideScreenContentEnabled
                        )
                    },
                    leadingContent = {
                        SettingsLeadingIconSlot(
                            icon = Icons.Rounded.VisibilityOff
                        )
                    },
                    supportingContent = {
                        Text(text = stringResource(R.string.settings_hide_screen_content_summary))
                    },
                    trailingContent = {
                        Switch(
                            checked = settingsState.hideScreenContentEnabled,
                            onCheckedChange = onHideScreenContentEnabledChange
                        )
                    }
                ) {
                    Text(text = stringResource(R.string.settings_hide_screen_content))
                }
            }

            uiState.securityErrorMessageRes?.let { messageRes ->
                Text(
                    text = stringResource(messageRes),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.padding_medium))
                )
            }

            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))

            SettingsSectionTitle(
                text = stringResource(R.string.settings_appearance)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(
                    dimensionResource(R.dimen.list_segment_gap)
                )
            ) {
                Box {
                    EditorSegmentedListItem(
                        index = 0,
                        count = 3,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { setLanguageMenuExpanded(true) },
                        leadingContent = {
                            SettingsLeadingIconSlot(
                                icon = Icons.Rounded.Language
                            )
                        },
                        supportingContent = {
                            Text(text = stringResource(settingsState.appLanguageOption.labelRes))
                        }
                    ) {
                        Text(text = stringResource(R.string.settings_app_language))
                    }
                    DropdownMenu(
                        expanded = isLanguageMenuExpanded,
                        onDismissRequest = { setLanguageMenuExpanded(false) },
                        modifier = Modifier.width(IntrinsicSize.Min)
                    ) {
                        AppLanguageOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(text = stringResource(option.labelRes)) },
                                onClick = {
                                    onAppLanguageOptionChange(option)
                                    setLanguageMenuExpanded(false)
                                }
                            )
                        }
                    }
                }

                Box {
                    EditorSegmentedListItem(
                        index = 1,
                        count = 3,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { setDarkModeMenuExpanded(true) },
                        leadingContent = {
                            SettingsLeadingIconSlot(
                                icon = Icons.Rounded.DarkMode
                            )
                        },
                        supportingContent = {
                            Text(text = stringResource(settingsState.darkModeOption.labelRes))
                        }
                    ) {
                        Text(text = stringResource(R.string.settings_dark_mode))
                    }
                    DropdownMenu(
                        expanded = isDarkModeMenuExpanded,
                        onDismissRequest = { setDarkModeMenuExpanded(false) },
                        modifier = Modifier.width(IntrinsicSize.Min)
                    ) {
                        DarkModeOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(text = stringResource(option.labelRes)) },
                                onClick = {
                                    onDarkModeOptionChange(option)
                                    setDarkModeMenuExpanded(false)
                                }
                            )
                        }
                    }
                }

                EditorSegmentedListItem(
                    index = 2,
                    count = 3,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onAdaptiveColorEnabledChange(!settingsState.adaptiveColorEnabled)
                    },
                    leadingContent = {
                        SettingsLeadingIconSlot(
                            icon = Icons.Rounded.Palette
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = settingsState.adaptiveColorEnabled,
                            onCheckedChange = onAdaptiveColorEnabledChange
                        )
                    }
                ) {
                    Text(text = stringResource(R.string.settings_adaptive_color))
                }
            }

            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))

            SettingsSectionTitle(
                text = stringResource(R.string.settings_backup_restore)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(
                    dimensionResource(R.dimen.list_segment_gap)
                )
            ) {
                EditorSegmentedListItem(
                    index = 0,
                    count = 2,
                    enabled = !isBackupExportInProgress && !isBackupRestoreInProgress,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBackupToFileClick,
                    leadingContent = {
                        SettingsLeadingIconSlot(
                            painter = painterResource(R.drawable.ic_file_export)
                        )
                    },
                    trailingContent = {
                        SettingsChevronTrailingIcon()
                    }
                ) {
                    Text(text = stringResource(R.string.settings_backup_to_file))
                }

                EditorSegmentedListItem(
                    index = 1,
                    count = 2,
                    enabled = !isBackupExportInProgress && !isBackupRestoreInProgress,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onRestoreFromFileClick,
                    leadingContent = {
                        SettingsLeadingIconSlot(
                            painter = painterResource(R.drawable.ic_settings_backup_restore)
                        )
                    },
                    trailingContent = {
                        SettingsChevronTrailingIcon()
                    }
                ) {
                    Text(text = stringResource(R.string.settings_restore_from_file))
                }
            }

            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))

            SettingsSectionTitle(
                text = stringResource(R.string.settings_about)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(
                    dimensionResource(R.dimen.list_segment_gap)
                )
            ) {
                EditorSegmentedListItem(
                    index = 0,
                    count = 4,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showPrivacyPolicyDialog = true },
                    leadingContent = {
                        SettingsLeadingIconSlot(
                            icon = Icons.Rounded.PrivacyTip
                        )
                    },
                    trailingContent = {
                        SettingsChevronTrailingIcon()
                    }
                ) {
                    Text(text = stringResource(R.string.settings_about_privacy_policy))
                }

                EditorSegmentedListItem(
                    index = 1,
                    count = 4,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { pendingExternalUrl = MODEL_REPOSITORY_URL },
                    leadingContent = {
                        SettingsLeadingIconSlot(
                            painter = painterResource(R.drawable.ic_github),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    supportingContent = {
                        Text(text = stringResource(R.string.settings_about_model_summary))
                    },
                    trailingContent = {
                        SettingsChevronTrailingIcon()
                    }
                ) {
                    Text(text = stringResource(R.string.settings_about_model))
                }

                EditorSegmentedListItem(
                    index = 2,
                    count = 4,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { pendingExternalUrl = DEVELOPER_X_URL },
                    leadingContent = {
                        SettingsLeadingIconSlot(
                            painter = painterResource(R.drawable.ic_x),
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    supportingContent = {
                        Text(text = stringResource(R.string.settings_about_contact_developer_summary))
                    },
                    trailingContent = {
                        SettingsChevronTrailingIcon()
                    }
                ) {
                    Text(text = stringResource(R.string.settings_about_contact_developer))
                }

                EditorSegmentedListItem(
                    index = 3,
                    count = 4,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText(appName, appInfoCopyText))
                        Toast.makeText(context, copyAppInfoMessage, Toast.LENGTH_SHORT).show()
                    },
                    leadingContent = {
                        SettingsLeadingIconSlot(
                            icon = Icons.Rounded.Info
                        )
                    },
                    supportingContent = {
                        Text(text = appInfoSummary)
                    }
                ) {
                    Text(text = appName)
                }
            }
        }
    }

    if (showPrivacyPolicyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicyDialog = false },
            title = { Text(text = stringResource(R.string.settings_about_privacy_policy)) },
            text = { Text(text = stringResource(R.string.onboarding_privacy_message)) },
            confirmButton = {
                TextButton(onClick = { showPrivacyPolicyDialog = false }) {
                    Text(text = stringResource(R.string.confirm))
                }
            }
        )
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

    pendingExternalUrl?.let { externalUrl ->
        AlertDialog(
            onDismissRequest = { pendingExternalUrl = null },
            title = { Text(text = stringResource(R.string.settings_about_open_link_title)) },
            text = { Text(text = stringResource(R.string.settings_about_open_link_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(externalUrl)))
                        pendingExternalUrl = null
                    }
                ) {
                    Text(text = stringResource(R.string.settings_about_open_link_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingExternalUrl = null }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showWeightDialog) {
        WeightDialog(
            profile = uiState.userProfile,
            onSave = { value, unit ->
                onWeightSave(value, unit)
                showWeightDialog = false
            },
            onClear = {
                onWeightClear()
                showWeightDialog = false
            },
            onDismiss = { showWeightDialog = false }
        )
    }
}

@Composable
private fun SettingsSectionTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(bottom = 10.dp, top = 4.dp)
    )
}

@Composable
private fun SettingsLeadingIconSlot(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    painter: Painter? = null,
    tint: Color? = null,
) {
    when {
        icon != null -> {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier
            )
        }

        painter != null -> {
            Icon(
                painter = painter,
                contentDescription = null,
                tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier
            )
        }
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
private fun SettingsSupportMessage(
    text: String,
    icon: ImageVector? = null,
    painter: Painter? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = false,
    index: Int = 0,
    count: Int = 1
) {
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified
    ) {
        EditorSegmentedListItem(
            index = index,
            count = count,
            onClick = onClick ?: {},
            modifier = Modifier.wrapContentHeight(),
            leadingContent = {
                SettingsLeadingIconSlot(icon = icon, painter = painter, tint = MaterialTheme.colorScheme.tertiary)
            },
            trailingContent = if (showChevron) {
                {
                    SettingsChevronTrailingIcon()
                }
            } else {
                null
            }
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
            Uri.parse("package:${context.packageName}")
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
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode.toLong()
    }
    val versionName = packageInfo.versionName?.takeIf { it.isNotBlank() } ?: versionCode.toString()
    return AppVersionInfo(
        versionName = versionName,
        versionCode = versionCode
    )
}

private const val MODEL_REPOSITORY_URL =
    "https://github.com/LaoZhong-Mihari/HRT-Recorder-PKcomponent-Test"
private const val DEVELOPER_X_URL = "https://x.com/mikanmkx"

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
                    appLockGracePeriodOption = AppLockGracePeriodOption.FIVE_MINUTES,
                    hideScreenContentEnabled = true,
                ),
                userProfile = UserProfile(
                    weightKg = 52.2,
                    weightOriginalValue = 115.0,
                    weightOriginalUnit = WeightUnit.POUNDS,
                )
            ),
            hasNotificationAccess = true,
            showInexactReminderWarning = true,
            onWeightSave = { _, _ -> },
            onWeightClear = { },
            onRemindersEnabledChange = { },
            onRequestExactAlarmAccess = { },
            onScreenLockProtectionToggle = { },
            onAppLockGracePeriodOptionChange = { },
            onHideScreenContentEnabledChange = { },
            onAppLanguageOptionChange = { },
            onDarkModeOptionChange = { },
            onAdaptiveColorEnabledChange = { },
            onBackupToFileClick = { },
            onRestoreFromFileClick = { },
            isBackupExportInProgress = false,
            isBackupRestoreInProgress = false,
            onCalibrationClick = { },
        )
    }
}

private data class PendingBackupRestoreRequest(
    val uri: Uri,
    val displayName: String?,
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
