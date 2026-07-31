package com.mkx.hrttracker.healthconnect

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme

class HealthConnectRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HrtTrackerTheme {
                HealthConnectRationaleContent(
                    onOpenPrivacyPolicy = {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                getString(R.string.privacy_policy_url).toUri(),
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HealthConnectRationaleContent(
    onOpenPrivacyPolicy: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.health_connect_rationale_title)) })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.health_connect_rationale_body),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.health_connect_rationale_weight),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.health_connect_rationale_medications),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onOpenPrivacyPolicy) {
                Text(stringResource(R.string.health_connect_open_privacy_policy))
            }
        }
    }
}
