package com.mkx.hrttracker.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.components.HrtButton

@Composable
fun AppLockScreen(
    errorMessageRes: Int?,
    onUnlockClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(dimensionResource(R.dimen.padding_medium)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.app_lock_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.app_lock_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = dimensionResource(R.dimen.padding_small))
            )

            errorMessageRes?.let { messageRes ->
                Text(
                    text = stringResource(messageRes),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = dimensionResource(R.dimen.padding_medium))
                )
            }

            HrtButton(
                text = stringResource(R.string.app_lock_unlock),
                onClick = onUnlockClick,
                modifier = Modifier.padding(top = dimensionResource(R.dimen.padding_medium)),
            )
        }
    }
}
