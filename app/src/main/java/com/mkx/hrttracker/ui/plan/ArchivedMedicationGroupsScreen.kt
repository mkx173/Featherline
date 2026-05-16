package com.mkx.hrttracker.ui.plan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.medication.MedicationGroup
import com.mkx.hrttracker.ui.components.AppContentContainer
import com.mkx.hrttracker.ui.components.SupportMessageListItem
import com.mkx.hrttracker.ui.components.appContentPaddingValues
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.components.topAppBarScrollToTop
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.dateLabelFormatter
import com.mkx.hrttracker.util.rememberAppLocale
import com.mkx.hrttracker.util.rememberLocalizedShortTimeFormatter
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@Composable
fun ArchivedMedicationGroupsScreen(
    onNavigateBack: () -> Unit,
    onGroupClick: (UUID) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArchivedMedicationGroupsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ArchivedMedicationGroupsScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onGroupClick = onGroupClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchivedMedicationGroupsScreenContent(
    uiState: ArchivedMedicationGroupsUiState,
    onNavigateBack: () -> Unit,
    onGroupClick: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appLocale = rememberAppLocale()
    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        lazyListState = listState,
        state = topAppBarState
    )
    val dateFormatter = remember(appLocale, uiState.today) {
        dateLabelFormatter(appLocale, uiState.today)
    }
    val timeFormatter = rememberLocalizedShortTimeFormatter(appLocale)

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                modifier = Modifier.topAppBarScrollToTop(scrollBehavior) {
                    listState.animateScrollToItem(0)
                },
                title = {
                    val title = stringResource(R.string.plan_archived_groups)
                    Text(
                        text = title,
                        modifier = Modifier.cjkTextOffset(title, amount = (-2).dp),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                scrollBehavior = scrollBehavior,
            )
        }
    ) { innerPadding ->
        AppContentContainer(modifier = Modifier.padding(innerPadding)) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
                return@AppContentContainer
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = appContentPaddingValues(),
            ) {
            item(key = "archived-heading") {
                Text(
                    text = stringResource(R.string.plan_archived_groups).uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp, top = 4.dp)
                )
            }

            if (uiState.groups.isEmpty()) {
                item(key = "empty-groups") {
                    SupportMessageListItem(
                        text = stringResource(R.string.plan_archived_groups_empty),
                        painter = painterResource(R.drawable.ic_info),
                    )
                }
            } else {
                uiState.groups.forEachIndexed { index, group ->
                    item(key = group.uuid) {
                        ArchivedMedicationGroupCard(
                            group = group,
                            appLocale = appLocale,
                            dateFormatter = dateFormatter,
                            timeFormatter = timeFormatter,
                            today = uiState.today,
                            onClick = { onGroupClick(group.uuid) },
                            index = index,
                            itemCount = uiState.groups.size,
                        )
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.list_segment_gap)))
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun ArchivedMedicationGroupCard(
    group: MedicationGroup,
    appLocale: Locale,
    dateFormatter: (LocalDate) -> String,
    timeFormatter: DateTimeFormatter,
    today: LocalDate,
    onClick: () -> Unit,
    index: Int,
    itemCount: Int,
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val createdDate = remember(group.createdAt, zoneId) {
        group.createdAt.atZone(zoneId).toLocalDate()
    }
    val archivedDate = remember(group.archivedAt, zoneId) {
        group.archivedAt?.atZone(zoneId)?.toLocalDate()
    }
    val createdText = stringResource(
        R.string.plan_archived_groups_created_at,
        dateFormatter(createdDate),
    )
    val archivedText = archivedDate?.let { date ->
        stringResource(
            R.string.plan_archived_groups_archived_at,
            dateFormatter(date),
        )
    }
    val archivePainter = painterResource(R.drawable.ic_archive)
    val eventPainter = painterResource(R.drawable.ic_event)
    val metadataRows = remember(createdText, archivedText, archivePainter, eventPainter) {
        buildList {
            add(
                RegimenGroupCardMetadata(
                    text = createdText,
                    iconPainter = eventPainter,
                )
            )
            if (archivedText != null) {
                add(
                    RegimenGroupCardMetadata(
                        text = archivedText,
                        iconPainter = archivePainter,
                    )
                )
            }
        }
    }

    RegimenGroupCard(
        group = group,
        remindersEnabled = false,
        hasNotificationAccess = true,
        appLocale = appLocale,
        dateFormatter = dateFormatter,
        timeFormatter = timeFormatter,
        upcomingOccurrences = emptyList(),
        today = today,
        onClick = onClick,
        index = index,
        itemCount = itemCount,
        showNotificationIcon = false,
        showUpcomingSection = false,
        showStartDate = false,
        metadataRows = metadataRows,
    )
}

@Preview(showBackground = true, widthDp = 420, heightDp = 900)
@Composable
private fun ArchivedMedicationGroupsScreenPreview() {
    val today = LocalDate.now()
    val zoneId = ZoneId.systemDefault()
    val groups = buildPlanPreviewUiState().medicationGroups.map { group ->
        group.copy(
            createdAt = today.minusMonths(2).atTime(LocalTime.NOON).atZone(zoneId).toInstant(),
            archivedAt = today.minusDays(3).atTime(LocalTime.NOON).atZone(zoneId).toInstant(),
        )
    }

    HrtTrackerTheme(dynamicColor = false) {
        ArchivedMedicationGroupsScreenContent(
            uiState = ArchivedMedicationGroupsUiState(
                isLoading = false,
                today = today,
                groups = groups,
            ),
            onNavigateBack = { },
            onGroupClick = { },
        )
    }
}
