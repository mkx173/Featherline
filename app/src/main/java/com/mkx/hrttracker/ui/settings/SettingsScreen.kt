package com.mkx.hrttracker.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.settings.AppLanguageOption
import com.mkx.hrttracker.model.settings.DarkModeOption
import com.mkx.hrttracker.ui.security.AppAuthenticationPromptEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settingsState = uiState.settingsState
    val configuration = LocalConfiguration.current
    val (isDarkModeMenuExpanded, setDarkModeMenuExpanded) = remember { mutableStateOf(false) }
    val (isLanguageMenuExpanded, setLanguageMenuExpanded) = remember { mutableStateOf(false) }

    LaunchedEffect(configuration) {
        viewModel.refreshAppLanguageOption()
    }

    AppAuthenticationPromptEffect(
        request = uiState.pendingPrompt,
        onAuthenticated = viewModel::onScreenLockProtectionAuthenticated,
        onError = viewModel::onScreenLockProtectionPromptError
    )

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
                .padding(dimensionResource(R.dimen.padding_small))
        ) {
            ListItem(
                modifier = Modifier.fillMaxWidth(),
                headlineContent = {
                    Text(
                        text = stringResource(R.string.settings_security),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            )

            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.onScreenLockProtectionToggle(
                            !settingsState.screenLockProtectionEnabled
                        )
                    },
                headlineContent = {
                    Text(text = stringResource(R.string.settings_screen_lock_protection))
                },
                supportingContent = {
                    Text(text = stringResource(R.string.settings_screen_lock_protection_summary))
                },
                trailingContent = {
                    Switch(
                        checked = settingsState.screenLockProtectionEnabled,
                        onCheckedChange = viewModel::onScreenLockProtectionToggle
                    )
                }
            )

            uiState.securityErrorMessageRes?.let { messageRes ->
                Text(
                    text = stringResource(messageRes),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.padding_medium))
                )
            }

            ListItem(
                modifier = Modifier.fillMaxWidth(),
                headlineContent = {
                    Text(
                        text = stringResource(R.string.settings_appearance),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            )

            Box {
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { setLanguageMenuExpanded(true) },
                    headlineContent = {
                        Text(text = stringResource(R.string.settings_app_language))
                    },
                    supportingContent = {
                        Text(text = stringResource(settingsState.appLanguageOption.labelRes))
                    }
                )
                DropdownMenu(
                    expanded = isLanguageMenuExpanded,
                    onDismissRequest = { setLanguageMenuExpanded(false) },
                    modifier = Modifier.width(IntrinsicSize.Min)
                ) {
                    AppLanguageOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(text = stringResource(option.labelRes)) },
                            onClick = {
                                viewModel.setAppLanguageOption(option)
                                setLanguageMenuExpanded(false)
                            }
                        )
                    }
                }
            }

            Box {
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { setDarkModeMenuExpanded(true) },
                    headlineContent = {
                        Text(text = stringResource(R.string.settings_dark_mode))
                    },
                    supportingContent = {
                        Text(text = stringResource(settingsState.darkModeOption.labelRes))
                    }
                )
                DropdownMenu(
                    expanded = isDarkModeMenuExpanded,
                    onDismissRequest = { setDarkModeMenuExpanded(false) },
                    modifier = Modifier.width(IntrinsicSize.Min)
                ) {
                    DarkModeOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(text = stringResource(option.labelRes)) },
                            onClick = {
                                viewModel.setDarkModeOption(option)
                                setDarkModeMenuExpanded(false)
                            }
                        )
                    }
                }
            }

            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.setAdaptiveColorEnabled(!settingsState.adaptiveColorEnabled)
                    },
                headlineContent = {
                    Text(text = stringResource(R.string.settings_adaptive_color))
                },
                trailingContent = {
                    Switch(
                        checked = settingsState.adaptiveColorEnabled,
                        onCheckedChange = viewModel::setAdaptiveColorEnabled
                    )
                }
            )
        }
    }
}
