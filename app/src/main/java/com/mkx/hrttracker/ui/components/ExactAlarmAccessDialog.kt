package com.mkx.hrttracker.ui.components

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mkx.hrttracker.R

@Composable
fun ExactAlarmAccessDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    HazeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.group_notifications_exact_alarm_title))
        },
        text = {
            Text(text = stringResource(R.string.group_notifications_exact_alarm_message))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.group_notifications_exact_alarm_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.group_notifications_exact_alarm_skip))
            }
        }
    )
}
