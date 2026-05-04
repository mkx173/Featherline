package com.mkx.hrttracker.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.reminder.canScheduleExactAlarms

@Composable
fun OnboardingDialogs(
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    if (!uiState.isLoaded || uiState.isCompleted) return

    var step by rememberSaveable { mutableStateOf(OnboardingStep.Privacy) }
    val context = LocalContext.current
    val notificationsUnavailableMessage =
        stringResource(R.string.settings_reminders_notifications_unavailable)
    val exactAlarmDeniedMessage = stringResource(R.string.group_notifications_inexact_warning)
    var isExactAlarmDialogVisible by rememberSaveable { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            when (
                onboardingReminderEnableAction(
                    sdkInt = Build.VERSION.SDK_INT,
                    hasRuntimeNotificationPermission = true,
                    notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
                    canScheduleExactAlarms = canScheduleExactAlarms(context)
                )
            ) {
                OnboardingReminderEnableAction.REQUEST_EXACT_ALARM_ACCESS -> {
                    isExactAlarmDialogVisible = true
                }
                OnboardingReminderEnableAction.COMPLETE_ENABLED -> {
                    viewModel.completeWithRemindersEnabled()
                }
                OnboardingReminderEnableAction.DECLINE_NOTIFICATIONS_UNAVAILABLE -> {
                    viewModel.completeWithRemindersDeclined()
                    Toast.makeText(context, notificationsUnavailableMessage, Toast.LENGTH_SHORT).show()
                }
                OnboardingReminderEnableAction.REQUEST_NOTIFICATION_PERMISSION -> Unit
            }
        } else {
            viewModel.completeWithRemindersDeclined()
            Toast.makeText(context, notificationsUnavailableMessage, Toast.LENGTH_SHORT).show()
        }
    }
    val exactAlarmAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (!canScheduleExactAlarms(context)) {
            Toast.makeText(context, exactAlarmDeniedMessage, Toast.LENGTH_SHORT).show()
        }
        viewModel.completeWithRemindersEnabled()
    }

    if (isExactAlarmDialogVisible) {
        AlertDialog(
            onDismissRequest = {
                isExactAlarmDialogVisible = false
                Toast.makeText(context, exactAlarmDeniedMessage, Toast.LENGTH_SHORT).show()
                viewModel.completeWithRemindersEnabled()
            },
            title = { Text(text = stringResource(R.string.group_notifications_exact_alarm_title)) },
            text = { Text(text = stringResource(R.string.group_notifications_exact_alarm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        isExactAlarmDialogVisible = false
                        requestExactAlarmAccess(context, exactAlarmAccessLauncher::launch)
                    }
                ) {
                    Text(text = stringResource(R.string.group_notifications_exact_alarm_settings))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        isExactAlarmDialogVisible = false
                        Toast.makeText(context, exactAlarmDeniedMessage, Toast.LENGTH_SHORT).show()
                        viewModel.completeWithRemindersEnabled()
                    }
                ) {
                    Text(text = stringResource(R.string.group_notifications_exact_alarm_skip))
                }
            }
        )
    }

    when (step) {
        OnboardingStep.Privacy -> AlertDialog(
            onDismissRequest = { step = OnboardingStep.Reminders },
            title = { Text(text = stringResource(R.string.onboarding_privacy_title)) },
            text = { Text(text = stringResource(R.string.onboarding_privacy_message)) },
            confirmButton = {
                TextButton(onClick = { step = OnboardingStep.Reminders }) {
                    Text(text = stringResource(R.string.onboarding_privacy_acknowledge))
                }
            }
        )

        OnboardingStep.Reminders -> {
            if (!isExactAlarmDialogVisible) {
                AlertDialog(
                    onDismissRequest = { viewModel.completeWithRemindersDeclined() },
                    title = { Text(text = stringResource(R.string.onboarding_reminders_title)) },
                    text = { Text(text = stringResource(R.string.onboarding_reminders_message)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                when (
                                    onboardingReminderEnableAction(
                                        sdkInt = Build.VERSION.SDK_INT,
                                        hasRuntimeNotificationPermission =
                                            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                                                ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.POST_NOTIFICATIONS
                                                ) == PackageManager.PERMISSION_GRANTED,
                                        notificationsEnabled = NotificationManagerCompat.from(context)
                                            .areNotificationsEnabled(),
                                        canScheduleExactAlarms = canScheduleExactAlarms(context)
                                    )
                                ) {
                                    OnboardingReminderEnableAction.REQUEST_NOTIFICATION_PERMISSION -> {
                                        notificationPermissionLauncher.launch(
                                            Manifest.permission.POST_NOTIFICATIONS
                                        )
                                    }

                                    OnboardingReminderEnableAction.DECLINE_NOTIFICATIONS_UNAVAILABLE -> {
                                        Toast.makeText(
                                            context,
                                            notificationsUnavailableMessage,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        viewModel.completeWithRemindersDeclined()
                                    }

                                    OnboardingReminderEnableAction.REQUEST_EXACT_ALARM_ACCESS -> {
                                        isExactAlarmDialogVisible = true
                                    }

                                    OnboardingReminderEnableAction.COMPLETE_ENABLED -> {
                                        viewModel.completeWithRemindersEnabled()
                                    }
                                }
                            }
                        ) {
                            Text(text = stringResource(R.string.onboarding_reminders_enable))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.completeWithRemindersDeclined() }) {
                            Text(text = stringResource(R.string.onboarding_reminders_decline))
                        }
                    }
                )
            }
        }
    }
}

private enum class OnboardingStep { Privacy, Reminders }

internal enum class OnboardingReminderEnableAction {
    REQUEST_NOTIFICATION_PERMISSION,
    DECLINE_NOTIFICATIONS_UNAVAILABLE,
    REQUEST_EXACT_ALARM_ACCESS,
    COMPLETE_ENABLED,
}

internal fun onboardingReminderEnableAction(
    sdkInt: Int,
    hasRuntimeNotificationPermission: Boolean,
    notificationsEnabled: Boolean,
    canScheduleExactAlarms: Boolean
): OnboardingReminderEnableAction {
    return when {
        sdkInt >= Build.VERSION_CODES.TIRAMISU && !hasRuntimeNotificationPermission -> {
            OnboardingReminderEnableAction.REQUEST_NOTIFICATION_PERMISSION
        }
        !notificationsEnabled -> OnboardingReminderEnableAction.DECLINE_NOTIFICATIONS_UNAVAILABLE
        !canScheduleExactAlarms -> OnboardingReminderEnableAction.REQUEST_EXACT_ALARM_ACCESS
        else -> OnboardingReminderEnableAction.COMPLETE_ENABLED
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
