package com.mkx.hrttracker.ui.plan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.ui.components.AppContentContainer
import com.mkx.hrttracker.ui.components.HazeTopAppBar
import androidx.compose.material3.LoadingIndicator
import com.mkx.hrttracker.ui.components.SupportMessageListItem
import com.mkx.hrttracker.ui.components.appContentPaddingValuesBehindTopAppBar
import com.mkx.hrttracker.ui.components.paddingBehindTopAppBar
import com.mkx.hrttracker.ui.components.topAppBarScrollToTop
import androidx.compose.ui.platform.LocalContext
import com.mkx.hrttracker.ui.medication.medicineDisplayName
import com.mkx.hrttracker.util.doseInstructionText
import com.mkx.hrttracker.util.rememberAppLocale
import com.mkx.hrttracker.util.rememberLocalizedShortTimeFormatter
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanRecordsScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit,
    onEntryClick: (List<UUID>) -> Unit,
    viewModel: PlanRecordsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appLocale = rememberAppLocale()
    val timeFormatter = rememberLocalizedShortTimeFormatter(appLocale)
    val dateFormatter = remember(appLocale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(appLocale)
    }

    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(topAppBarState)

    var isGroupMenuExpanded by remember { mutableStateOf(false) }
    var isDeleteConfirmVisible by remember { mutableStateOf(false) }

    val hasSelection = uiState.selectedIds.isNotEmpty()

    if (isDeleteConfirmVisible) {
        val selectedLoggedCount = uiState.items.count { it.id in uiState.selectedIds && it.isLogged }
        AlertDialog(
            onDismissRequest = { isDeleteConfirmVisible = false },
            title = { Text(stringResource(R.string.plan_records_confirm_delete, selectedLoggedCount)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        isDeleteConfirmVisible = false
                        viewModel.deleteSelected()
                    }
                ) {
                    Text(stringResource(R.string.delete_entries_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { isDeleteConfirmVisible = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            HazeTopAppBar(
                modifier = Modifier.topAppBarScrollToTop(scrollBehavior, listState),
                title = {
                    if (hasSelection) {
                        Text(stringResource(R.string.plan_batch_add_group_selected, uiState.selectedIds.size))
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { isGroupMenuExpanded = true }
                        ) {
                            Text(
                                text = uiState.selectedGroupName.ifEmpty { stringResource(R.string.plan_records_title) },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            DropdownMenu(
                                expanded = isGroupMenuExpanded,
                                onDismissRequest = { isGroupMenuExpanded = false }
                            ) {
                                uiState.groups.forEach { group ->
                                    DropdownMenuItem(
                                        text = { Text(group.name) },
                                        onClick = {
                                            isGroupMenuExpanded = false
                                            viewModel.selectGroup(group.uuid)
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (hasSelection) {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.history_cancel_selection)
                            )
                        }
                    } else {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.navigate_back)
                            )
                        }
                    }
                },
                actions = {
                    if (hasSelection) {
                        val selectedItems = uiState.items.filter { it.id in uiState.selectedIds }
                        val hasUnlogged = selectedItems.any { !it.isLogged }
                        val hasLogged = selectedItems.any { it.isLogged }

                        if (hasUnlogged) {
                            IconButton(onClick = viewModel::logSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = stringResource(R.string.plan_records_add_selected, selectedItems.count { !it.isLogged })
                                )
                            }
                        }
                        if (hasLogged) {
                            IconButton(onClick = { isDeleteConfirmVisible = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = stringResource(R.string.plan_records_delete_selected, selectedItems.count { it.isLogged })
                                )
                            }
                        }
                    } else if (uiState.items.isNotEmpty()) {
                        IconButton(onClick = viewModel::selectAll) {
                            Icon(
                                imageVector = Icons.Rounded.SelectAll,
                                contentDescription = stringResource(R.string.history_select_all)
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { innerPadding ->
        AppContentContainer(modifier = Modifier.paddingBehindTopAppBar(innerPadding)) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
                return@AppContentContainer
            }

            if (uiState.items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    SupportMessageListItem(
                        text = stringResource(R.string.plan_records_empty),
                        painter = painterResource(R.drawable.ic_info)
                    )
                }
                return@AppContentContainer
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = appContentPaddingValuesBehindTopAppBar(innerPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.items, key = { it.id }) { item ->
                    PlanRecordRow(
                        item = item,
                        isSelected = item.id in uiState.selectedIds,
                        onToggleSelection = { viewModel.toggleSelection(item.id) },
                        onEntryClick = { entryId -> onEntryClick(listOf(entryId)) },
                        hasSelection = hasSelection,
                        timeFormatter = timeFormatter,
                        dateFormatter = dateFormatter
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlanRecordRow(
    item: PlanRecordItem,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onEntryClick: (UUID) -> Unit,
    hasSelection: Boolean,
    timeFormatter: DateTimeFormatter,
    dateFormatter: DateTimeFormatter,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = {
                    if (hasSelection) {
                        onToggleSelection()
                    } else if (item.isLogged && item.logEntry != null) {
                        onEntryClick(item.logEntry.uuid)
                    } else {
                        onToggleSelection()
                    }
                },
                onLongClick = onToggleSelection
            ),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasSelection) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() },
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                val context = LocalContext.current
                val medicineName = if (item.medicine != null) {
                    medicineDisplayName(item.medicine)
                } else {
                    item.fallbackName
                }
                Text(
                    text = medicineName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val doseDesc = if (item.medicine != null) {
                        doseInstructionText(
                            medicine = item.medicine,
                            doseInstruction = item.doseInstruction,
                            doseAmountDelta = item.doseAmountDelta,
                            context = context
                        ).orEmpty()
                    } else {
                        ""
                    }
                    val formattedDose = if (item.count > 1 && doseDesc.isNotEmpty()) {
                        "$doseDesc × ${item.count}"
                    } else {
                        doseDesc
                    }
                    Text(
                        text = formattedDose,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    RecordStatusPill(isLogged = item.isLogged)
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = item.time.format(timeFormatter),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = item.time.format(dateFormatter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RecordStatusPill(
    isLogged: Boolean,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isLogged) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (isLogged) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val label = if (isLogged) {
        stringResource(R.string.plan_records_logged)
    } else {
        stringResource(R.string.plan_records_scheduled)
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
