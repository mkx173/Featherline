package com.mkx.hrttracker.ui.settings

import androidx.annotation.StringRes
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mkx.hrttracker.R

enum class DarkModeOption(@StringRes val label: Int) {
    FOLLOW_SYSTEM(R.string.dark_mode_follow_system),
    LIGHT(R.string.dark_mode_always_off),
    DARK(R.string.dark_mode_always_on)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val settingsState by viewModel.settingsState.collectAsState()
    val (isDarkModeMenuExpanded, setDarkModeMenuExpanded) = remember { mutableStateOf(false) }

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
                        .clickable { setDarkModeMenuExpanded(true) },
                    headlineContent = {
                        Text(text = stringResource(R.string.settings_dark_mode))
                    },
                    supportingContent = {
                        Text(text = stringResource(settingsState.darkModeOption.label))
                    }
                )
                DropdownMenu(
                    expanded = isDarkModeMenuExpanded,
                    onDismissRequest = { setDarkModeMenuExpanded(false) },
                    modifier = Modifier.width(IntrinsicSize.Min)
                ) {
                    DarkModeOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(text = stringResource(option.label)) },
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
