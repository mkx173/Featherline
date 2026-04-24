package com.mkx.hrttracker.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
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
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.security.AppAuthenticationPromptEffect
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settingsState = uiState.settingsState
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasNotificationAccess by remember { mutableStateOf(canPostNotifications(context)) }
    var showInexactReminderWarning by remember { mutableStateOf(false) }
    val reminderPermissionDeniedMessage =
        stringResource(R.string.settings_reminders_permission_denied)
    val reminderNotificationsUnavailableMessage =
        stringResource(R.string.settings_reminders_notifications_unavailable)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.setRemindersEnabled(true)
            hasNotificationAccess = canPostNotifications(context)
        } else {
            viewModel.setRemindersEnabled(false)
            hasNotificationAccess = false
            Toast.makeText(context, reminderPermissionDeniedMessage, Toast.LENGTH_SHORT).show()
        }
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
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
        onScreenLockProtectionToggle = viewModel::onScreenLockProtectionToggle,
        onAppLockGracePeriodOptionChange = viewModel::setAppLockGracePeriodOption,
        onHideScreenContentEnabledChange = viewModel::setHideScreenContentEnabled,
        onAppLanguageOptionChange = viewModel::setAppLanguageOption,
        onDarkModeOptionChange = viewModel::setDarkModeOption,
        onAdaptiveColorEnabledChange = viewModel::setAdaptiveColorEnabled,
        modifier = modifier
    )
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
    onScreenLockProtectionToggle: (Boolean) -> Unit,
    onAppLockGracePeriodOptionChange: (AppLockGracePeriodOption) -> Unit,
    onHideScreenContentEnabledChange: (Boolean) -> Unit,
    onAppLanguageOptionChange: (AppLanguageOption) -> Unit,
    onDarkModeOptionChange: (DarkModeOption) -> Unit,
    onAdaptiveColorEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val settingsState = uiState.settingsState
    var showWeightDialog by rememberSaveable { mutableStateOf(false) }
    val (isAppLockGracePeriodMenuExpanded, setAppLockGracePeriodMenuExpanded) =
        remember { mutableStateOf(false) }
    val (isDarkModeMenuExpanded, setDarkModeMenuExpanded) = remember { mutableStateOf(false) }
    val (isLanguageMenuExpanded, setLanguageMenuExpanded) = remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(R.string.tab_settings)) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(dimensionResource(R.dimen.padding_medium)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_medium))
        ) {
            SettingsSectionTitle(
                text = stringResource(R.string.settings_personalization)
            )

            EditorSegmentedListItem(
                index = 0,
                count = 1,
                modifier = Modifier.fillMaxWidth(),
                onClick = { showWeightDialog = true },
                supportingContent = {
                    Text(text = formatWeightSummary(uiState.userProfile))
                }
            ) {
                Text(text = stringResource(R.string.personalization_weight))
            }

            SettingsSectionTitle(
                text = stringResource(R.string.settings_notifications)
            )

            EditorSegmentedListItem(
                index = 0,
                count = 1,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onRemindersEnabledChange(!settingsState.remindersEnabled) },
                supportingContent = {
                    Column {
                        Text(
                            text = stringResource(
                                if (hasNotificationAccess) {
                                    R.string.settings_reminders_summary
                                } else {
                                    R.string.settings_reminders_permission_off_summary
                                }
                            )
                        )
                        if (showInexactReminderWarning) {
                            Text(
                                text = stringResource(R.string.group_notifications_inexact_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                trailingContent = {
                    Switch(
                        checked = settingsState.remindersEnabled && hasNotificationAccess,
                        onCheckedChange = onRemindersEnabledChange
                    )
                }
            ) {
                Text(text = stringResource(R.string.settings_reminders))
            }

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
        }
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
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.fillMaxWidth()
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
            onScreenLockProtectionToggle = { },
            onAppLockGracePeriodOptionChange = { },
            onHideScreenContentEnabledChange = { },
            onAppLanguageOptionChange = { },
            onDarkModeOptionChange = { },
            onAdaptiveColorEnabledChange = { },
        )
    }
}
