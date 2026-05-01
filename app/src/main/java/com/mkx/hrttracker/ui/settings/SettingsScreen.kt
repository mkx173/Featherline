package com.mkx.hrttracker.ui.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material3.rememberTopAppBarState
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
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
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
import com.mkx.hrttracker.ui.components.BackupPasswordDialog
import com.mkx.hrttracker.ui.components.ExactAlarmAccessDialog
import com.mkx.hrttracker.ui.components.HrtDropdownMenu
import com.mkx.hrttracker.ui.components.HrtDropdownMenuItem
import com.mkx.hrttracker.ui.components.PreferenceSegmentedListItem
import com.mkx.hrttracker.ui.components.WeightDialog
import com.mkx.hrttracker.ui.components.topAppBarScrollToTop
import com.mkx.hrttracker.ui.security.AppAuthenticationPromptEffect
import com.mkx.hrttracker.ui.security.AppLockViewModel
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import kotlinx.coroutines.launch
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    scrollToTopSignal: Int = 0,
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
    var showBackupPasswordDialog by rememberSaveable { mutableStateOf(false) }
    var pendingRestoreUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingRestoreDisplayName by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPreparedBackupDisplayName by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPreparedBackupTempFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingRestoreRequest = pendingRestoreUriString?.let { restoreUriString ->
        PendingBackupRestoreRequest(
            uri = restoreUriString.toUri(),
            displayName = pendingRestoreDisplayName,
        )
    }
    val pendingPreparedBackupExport = pendingPreparedBackupDisplayName?.let { displayName ->
        pendingPreparedBackupTempFilePath?.let { tempFilePath ->
            viewModel.restorePreparedBackupExport(
                displayName = displayName,
                tempFilePath = tempFilePath,
            )
        }
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
        val preparedBackupExport = pendingPreparedBackupExport
        if (directoryUri == null) {
            if (preparedBackupExport != null) {
                coroutineScope.launch {
                    viewModel.discardPreparedBackup(preparedBackupExport)
                }
            }
            pendingPreparedBackupDisplayName = null
            pendingPreparedBackupTempFilePath = null
            return@rememberLauncherForActivityResult
        }
        if (
            preparedBackupExport == null ||
            isBackupExportInProgress ||
            isBackupRestoreInProgress
        ) {
            pendingPreparedBackupDisplayName = null
            pendingPreparedBackupTempFilePath = null
            Toast.makeText(
                context,
                backupExportFailedMessage,
                Toast.LENGTH_SHORT
            ).show()
            return@rememberLauncherForActivityResult
        }

        coroutineScope.launch {
            isBackupExportInProgress = true
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
                isBackupExportInProgress = false
                pendingPreparedBackupDisplayName = null
                pendingPreparedBackupTempFilePath = null
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

    val isBackupActionBlocked = isBackupExportInProgress || isBackupRestoreInProgress || isBackupFlowPending

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
            if (!isBackupActionBlocked) {
                showBackupPasswordDialog = true
            }
        },
        onRestoreFromFileClick = {
            if (!isBackupActionBlocked) {
                appLockViewModel?.allowNextExternalActivityBypass()
                try {
                    restoreBackupFileLauncher.launch(arrayOf("*/*"))
                } catch (_: Exception) {
                    appLockViewModel?.cancelPendingExternalActivityBypass()
                    Toast.makeText(
                        context,
                        backupRestoreFailedMessage,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        },
        isBackupExportInProgress = isBackupExportInProgress || isBackupFlowPending,
        isBackupRestoreInProgress = isBackupRestoreInProgress,
        onCalibrationClick = onCalibrationClick,
        scrollToTopSignal = scrollToTopSignal,
        modifier = modifier
    )

    if (showBackupPasswordDialog) {
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
                    isBackupExportInProgress = true
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
                        isBackupExportInProgress = false
                    }

                    pendingPreparedBackupDisplayName = preparedBackupExport.displayName
                    pendingPreparedBackupTempFilePath = preparedBackupExport.tempFilePath
                    showBackupPasswordDialog = false
                    appLockViewModel?.allowNextExternalActivityBypass()
                    try {
                        backupDirectoryLauncher.launch(null)
                    } catch (_: Exception) {
                        appLockViewModel?.cancelPendingExternalActivityBypass()
                        viewModel.discardPreparedBackup(preparedBackupExport)
                        pendingPreparedBackupDisplayName = null
                        pendingPreparedBackupTempFilePath = null
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

    pendingRestoreRequest?.let { restoreRequest ->
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
                pendingRestoreUriString = null
                pendingRestoreDisplayName = null
            },
            onConfirm = { password ->
                coroutineScope.launch {
                    isBackupRestoreInProgress = true
                    try {
                        viewModel.restoreBackup(
                            fileUri = restoreRequest.uri,
                            password = password,
                        )
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
            },
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
    scrollToTopSignal: Int = 0,
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
    val scrollState = rememberScrollState()
    val weightSummary = formatWeightSummary(uiState.userProfile)

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        scrollState = scrollState,
        state = topAppBarState
    )

    val initialScrollToTopSignal = remember { scrollToTopSignal }
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal != initialScrollToTopSignal) {
            scrollState.animateScrollTo(0)
            topAppBarState.contentOffset = 0f
            topAppBarState.heightOffset = 0f
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                modifier = Modifier.topAppBarScrollToTop(scrollBehavior) {
                    scrollState.animateScrollTo(0)
                },
                title = { Text(text = stringResource(R.string.tab_settings)) },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
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
                SettingsSegmentedListItem(
                    title = stringResource(R.string.personalization_weight),
                    supportingText = weightSummary,
                    supportingCjkTextOffsetEnabled = uiState.userProfile.weightOriginalValue == null,
                    index = 0,
                    count = 2,
                    onClick = { showWeightDialog = true },
                    leadingContent = {
                        SettingsLeadingIconSlot(
                            icon = Icons.Rounded.MonitorWeight
                        )
                    },
                )

                SettingsSegmentedListItem(
                    title = stringResource(R.string.settings_personalization_calibration),
                    index = 1,
                    count = 2,
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

            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))

            SettingsSectionTitle(
                text = stringResource(R.string.settings_notifications)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(
                    dimensionResource(R.dimen.list_segment_gap)
                )
            ) {
                SettingsSegmentedListItem(
                    title = stringResource(R.string.settings_reminders),
                    supportingText = stringResource(R.string.settings_reminders_summary),
                    enabled = hasNotificationAccess,
                    index = 0,
                    count = if (!hasNotificationAccess || showInexactReminderWarning) 2 else 1,
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
                    trailingContent = {
                        Switch(
                            checked = settingsState.remindersEnabled && hasNotificationAccess,
                            onCheckedChange = onRemindersEnabledChange,
                            enabled = hasNotificationAccess
                        )
                    }
                )

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
                SettingsSegmentedListItem(
                    title = stringResource(R.string.settings_screen_lock_protection),
                    supportingText = stringResource(R.string.settings_screen_lock_protection_summary),
                    onClick = {
                        onScreenLockProtectionToggle(
                            !settingsState.screenLockProtectionEnabled
                        )
                    },
                    index = 0,
                    count = securityItemCount,
                    leadingContent = {
                        SettingsLeadingIconSlot(
                            icon = Icons.Rounded.Lock
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = settingsState.screenLockProtectionEnabled,
                            onCheckedChange = onScreenLockProtectionToggle
                        )
                    },
                )

                if (settingsState.screenLockProtectionEnabled) {
                    Box {
                        SettingsSegmentedListItem(
                            title = stringResource(R.string.settings_app_lock_grace_period),
                            supportingText = stringResource(settingsState.appLockGracePeriodOption.labelRes),
                            index = 1,
                            count = securityItemCount,
                            onClick = { setAppLockGracePeriodMenuExpanded(true) },
                            leadingContent = {
                                SettingsLeadingIconSlot(
                                    icon = Icons.Rounded.LockClock
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

                SettingsSegmentedListItem(
                    title = stringResource(R.string.settings_hide_screen_content),
                    supportingText = stringResource(R.string.settings_hide_screen_content_summary),
                    onClick = {
                        onHideScreenContentEnabledChange(
                            !settingsState.hideScreenContentEnabled
                        )
                    },
                    index = if (settingsState.screenLockProtectionEnabled) 2 else 1,
                    count = securityItemCount,
                    leadingContent = {
                        SettingsLeadingIconSlot(
                            icon = Icons.Rounded.VisibilityOff
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
                    SettingsSegmentedListItem(
                        title = stringResource(R.string.settings_app_language),
                        supportingText = stringResource(settingsState.appLanguageOption.labelRes),
                        index = 0,
                        count = 3,
                        onClick = { setLanguageMenuExpanded(true) },
                        leadingContent = {
                            SettingsLeadingIconSlot(
                                icon = Icons.Rounded.Language
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
                                onClick = { onAppLanguageOptionChange(option) },
                            )
                        },
                    )
                }

                Box {
                    SettingsSegmentedListItem(
                        title = stringResource(R.string.settings_dark_mode),
                        supportingText = stringResource(settingsState.darkModeOption.labelRes),
                        index = 1,
                        count = 3,
                        onClick = { setDarkModeMenuExpanded(true) },
                        leadingContent = {
                            SettingsLeadingIconSlot(
                                icon = Icons.Rounded.DarkMode
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
                                onClick = { onDarkModeOptionChange(option) },
                            )
                        },
                    )
                }

                SettingsSegmentedListItem(
                    title = stringResource(R.string.settings_adaptive_color),
                    index = 2,
                    count = 3,
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
                )
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
                SettingsSegmentedListItem(
                    title = stringResource(R.string.settings_backup_to_file),
                    enabled = !isBackupExportInProgress && !isBackupRestoreInProgress,
                    index = 0,
                    count = 2,
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

                SettingsSegmentedListItem(
                    title = stringResource(R.string.settings_restore_from_file),
                    enabled = !isBackupExportInProgress && !isBackupRestoreInProgress,
                    index = 1,
                    count = 2,
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

            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_medium)))

            SettingsSectionTitle(
                text = stringResource(R.string.settings_about)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(
                    dimensionResource(R.dimen.list_segment_gap)
                )
            ) {
                SettingsSegmentedListItem(
                    title = stringResource(R.string.settings_about_privacy_policy),
                    index = 0,
                    count = 4,
                    onClick = { showPrivacyPolicyDialog = true },
                    leadingContent = {
                        SettingsLeadingIconSlot(
                            icon = Icons.Rounded.PrivacyTip
                        )
                    },
                    trailingContent = {
                        SettingsChevronTrailingIcon()
                    }
                )

                SettingsSegmentedListItem(
                    title = stringResource(R.string.settings_about_model),
                    supportingText = stringResource(R.string.settings_about_model_summary),
                    index = 1,
                    count = 4,
                    onClick = { pendingExternalUrl = MODEL_REPOSITORY_URL },
                    leadingContent = {
                        Box(
                            modifier = Modifier.size(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            SettingsLeadingIconSlot(
                                painter = painterResource(R.drawable.ic_github),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    trailingContent = {
                        SettingsChevronTrailingIcon()
                    }
                )

                SettingsSegmentedListItem(
                    title = stringResource(R.string.settings_about_contact_developer),
                    supportingText = stringResource(R.string.settings_about_contact_developer_summary),
                    index = 2,
                    count = 4,
                    onClick = { pendingExternalUrl = DEVELOPER_X_URL },
                    leadingContent = {
                        Box(
                            modifier = Modifier.size(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            SettingsLeadingIconSlot(
                                painter = painterResource(R.drawable.ic_x),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    trailingContent = {
                        SettingsChevronTrailingIcon()
                    }
                )

                SettingsSegmentedListItem(
                    title = appName,
                    supportingText = appInfoSummary,
                    index = 3,
                    count = 4,
                    onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText(appName, appInfoCopyText))
                        Toast.makeText(context, copyAppInfoMessage, Toast.LENGTH_SHORT).show()
                    },
                    leadingContent = {
                        SettingsLeadingIconSlot(
                            icon = Icons.Rounded.Info
                        )
                    }
                )
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
                        context.startActivity(Intent(Intent.ACTION_VIEW, externalUrl.toUri()))
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
private fun SettingsSegmentedListItem(
    title: String,
    index: Int,
    count: Int,
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
    index: Int = 0,
    count: Int = 1,
) {
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified
    ) {
        SettingsSegmentedListItem(
            title = text,
            index = index,
            count = count,
            onClick = onClick ?: {},
            modifier = Modifier.wrapContentHeight(),
            leadingContent = {
                SettingsLeadingIconSlot(
                    icon = icon,
                    painter = painter,
                    tint = MaterialTheme.colorScheme.tertiary,
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

private const val MINIMUM_BACKUP_PASSWORD_LENGTH = 6

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
