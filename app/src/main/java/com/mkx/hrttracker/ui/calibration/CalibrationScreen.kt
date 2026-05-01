package com.mkx.hrttracker.ui.calibration

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestCatalog
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResult
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
import com.mkx.hrttracker.model.settings.SettingsState
import com.mkx.hrttracker.ui.components.EditorSegmentedListItem
import com.mkx.hrttracker.ui.components.HrtDropdownMenu
import com.mkx.hrttracker.ui.components.HrtDropdownMenuItem
import com.mkx.hrttracker.ui.components.SupportMessageListItem
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.components.topAppBarScrollToTop
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.CalibrationPanelDateTimeFormatters
import com.mkx.hrttracker.util.CalibrationPanelDateTimeLabels
import com.mkx.hrttracker.util.LocalDateFormatter
import com.mkx.hrttracker.util.calibrationMonthHeaderFormatter
import com.mkx.hrttracker.util.calibrationPanelDateTimeFormatters
import com.mkx.hrttracker.util.formatCalibrationPanelDateTimeLabels
import com.mkx.hrttracker.util.rememberAppLocale
import com.mkx.hrttracker.util.rememberUses24HourTimeFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlin.math.round

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    onNavigateBack: () -> Unit,
    onUnitsClick: () -> Unit,
    onAddClick: () -> Unit,
    onPanelClick: (UUID) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalibrationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appLocale = rememberAppLocale()
    val uses24HourFormat = rememberUses24HourTimeFormat()
    val today = remember { LocalDate.now() }
    val panelDateTimeFormatters = remember(appLocale, uses24HourFormat) {
        calibrationPanelDateTimeFormatters(appLocale, uses24HourFormat)
    }
    val monthFormatter = remember(appLocale, today) {
        calibrationMonthHeaderFormatter(
            locale = appLocale,
            currentYear = today.year,
        )
    }

    CalibrationScreenContent(
        uiState = uiState,
        panelDateTimeFormatters = panelDateTimeFormatters,
        monthFormatter = monthFormatter,
        onNavigateBack = onNavigateBack,
        onDeleteAllCalibrationEntries = viewModel::deleteAllCalibrationEntries,
        onDeleteAllCalibrationEntriesResultConsumed = viewModel::consumeDeleteAllEntriesResult,
        onUnitsClick = onUnitsClick,
        onAddClick = onAddClick,
        onPanelClick = onPanelClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CalibrationScreenContent(
    uiState: CalibrationUiState,
    panelDateTimeFormatters: CalibrationPanelDateTimeFormatters,
    monthFormatter: LocalDateFormatter,
    onNavigateBack: () -> Unit,
    onDeleteAllCalibrationEntries: () -> Unit,
    onDeleteAllCalibrationEntriesResultConsumed: () -> Unit,
    onUnitsClick: () -> Unit,
    onAddClick: () -> Unit,
    onPanelClick: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    val monthGroups = remember(uiState.panels, monthFormatter) {
        groupCalibrationPanelsByMonth(uiState.panels, monthFormatter)
    }

    val context = LocalContext.current
    var isActionMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var isDeleteAllEntriesConfirmationVisible by rememberSaveable { mutableStateOf(false) }
    val deleteAllEntriesSuccessMessage =
        stringResource(R.string.settings_calibration_delete_all_entries_success)
    val deleteAllEntriesFailureMessage =
        stringResource(R.string.settings_calibration_delete_all_entries_failure)

    LaunchedEffect(uiState.deleteAllEntriesResult) {
        when (uiState.deleteAllEntriesResult) {
            CalibrationDeleteAllEntriesResult.SUCCESS -> {
                Toast.makeText(
                    context,
                    deleteAllEntriesSuccessMessage,
                    Toast.LENGTH_SHORT
                ).show()
                onDeleteAllCalibrationEntriesResultConsumed()
            }

            CalibrationDeleteAllEntriesResult.FAILURE -> {
                Toast.makeText(
                    context,
                    deleteAllEntriesFailureMessage,
                    Toast.LENGTH_SHORT
                ).show()
                onDeleteAllCalibrationEntriesResultConsumed()
            }

            null -> Unit
        }
    }

    val listState = rememberLazyListState()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        lazyListState = listState,
        state = topAppBarState
    )
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                modifier = Modifier.topAppBarScrollToTop(scrollBehavior) {
                    listState.animateScrollToItem(0)
                },
                title = { Text(text = stringResource(R.string.settings_personalization_calibration)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cancel),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onUnitsClick) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = stringResource(
                                R.string.settings_calibration_settings
                            ),
                        )
                    }
                    Box {
                        IconButton(onClick = { isActionMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.plan_more_options),
                            )
                        }
                        HrtDropdownMenu(
                            expanded = isActionMenuExpanded,
                            onDismissRequest = { isActionMenuExpanded = false },
                            items = listOf(
                                HrtDropdownMenuItem(
                                    text = stringResource(R.string.settings_calibration_delete_all_entries),
                                    enabled = uiState.panels.isNotEmpty() && !uiState.isDeletingAllEntries,
                                    onClick = { isDeleteAllEntriesConfirmationVisible = true },
                                )
                            ),
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.settings_calibration_add_result),
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(dimensionResource(R.dimen.padding_medium)),
        ) {
            item(key = "calibration-info") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(
                        dimensionResource(R.dimen.list_segment_gap)
                    )
                ) {
                    CalibrationTargetRangeCard(settingsState = uiState.settingsState)
                    CalibrationInfoCard(panelCount = uiState.panels.size)
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (uiState.panels.isEmpty() && !uiState.isLoading) {
                item(key = "calibration-empty") {
                    SupportMessageListItem(
                        text = stringResource(R.string.settings_calibration_empty_state),
                        painter = painterResource(R.drawable.ic_info),
                        modifier = modifier,
                    )
                }
            } else {
                monthGroups.forEachIndexed { index, monthGroup ->
                    item(key = "month-${monthGroup.yearMonth}") {
                        CalibrationMonthHeader(
                            monthLabel = monthGroup.monthLabel,
                            panelCount = monthGroup.panels.size,
                        )
                    }

                    itemsIndexed(
                        items = monthGroup.panels,
                        key = { _, panel -> panel.uuid },
                    ) { index, panel ->
                        CalibrationPanelRow(
                            panel = panel,
                            settingsState = uiState.settingsState,
                            dateTimeFormatters = panelDateTimeFormatters,
                            index = index,
                            count = monthGroup.panels.size,
                            onClick = { onPanelClick(panel.uuid) },
                        )
                        if (index < monthGroup.panels.size - 1) {
                            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.list_segment_gap)))
                        }
                    }

                    if (index < monthGroups.size - 1) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }

    if (isDeleteAllEntriesConfirmationVisible) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isDeletingAllEntries) {
                    isDeleteAllEntriesConfirmationVisible = false
                }
            },
            title = {
                Text(text = stringResource(R.string.settings_calibration_delete_all_entries_title))
            },
            text = {
                Text(
                    text = pluralStringResource(
                        R.plurals.settings_calibration_delete_all_entries_message,
                        uiState.panels.size,
                        uiState.panels.size,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !uiState.isDeletingAllEntries,
                    onClick = {
                        isDeleteAllEntriesConfirmationVisible = false
                        onDeleteAllCalibrationEntries()
                    }
                ) {
                    Text(text = stringResource(R.string.delete_entries_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !uiState.isDeletingAllEntries,
                    onClick = { isDeleteAllEntriesConfirmationVisible = false }
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun CalibrationInfoCard(
    panelCount: Int,
    modifier: Modifier = Modifier,
) {
    SupportMessageListItem(
        text = stringResource(R.string.settings_calibration_info_message),
        painter = painterResource(R.drawable.ic_lab_panel),
        index = 1,
        count = 2,
        modifier = modifier,
        trailingContent = {
            val totalCountLabel = pluralStringResource(
                R.plurals.settings_calibration_total_count,
                panelCount,
                panelCount,
            ).uppercase()
            Text(
                text = totalCountLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.cjkTextOffset(totalCountLabel)
            )
        },
    )
}

@Composable
private fun CalibrationTargetRangeCard(
    settingsState: SettingsState,
    modifier: Modifier = Modifier,
) {
    SupportMessageListItem(
        supportingText = calibrationHistoryTargetRangeSummary(settingsState),
        text = stringResource(R.string.settings_calibration_target_ranges_title),
        painter = painterResource(R.drawable.ic_bloodtype),
        index = 0,
        count = 2,
        modifier = modifier,
        textStyle = MaterialTheme.typography.titleMedium,
        supportingTextStyle = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Normal
        ),
        titleColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun calibrationHistoryTargetRangeSummary(
    settingsState: SettingsState,
): String {
    val estradiolTarget = calibrationHistoryTargetValueLabel(
        analyteKey = BloodAnalyteKey.E2,
        unit = defaultCalibrationUnitFor(BloodAnalyteKey.E2, settingsState),
    )
    val testosteroneTarget = calibrationHistoryTargetValueLabel(
        analyteKey = BloodAnalyteKey.T,
        unit = defaultCalibrationUnitFor(BloodAnalyteKey.T, settingsState),
    )

    return buildString {
        estradiolTarget?.let { target ->
            append(
                stringResource(
                    R.string.settings_calibration_target_range_line,
                    stringResource(R.string.medication_category_estradiol),
                    target,
                )
            )
        }
        testosteroneTarget?.let { target ->
            if (isNotEmpty()) {
                appendLine()
            }
            append(
                stringResource(
                    R.string.settings_calibration_target_range_line,
                    stringResource(R.string.medication_category_testosterone),
                    target,
                )
            )
        }
    }
}

@Composable
private fun CalibrationMonthHeader(
    monthLabel: String,
    panelCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = monthLabel.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
        Text(
            text = panelCount.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

internal data class CalibrationPanelMonthGroup(
    val yearMonth: YearMonth,
    val monthLabel: String,
    val panels: List<BloodTestPanel>,
)

internal fun groupCalibrationPanelsByMonth(
    panels: List<BloodTestPanel>,
    monthFormatter: LocalDateFormatter,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<CalibrationPanelMonthGroup> {
    val groups = linkedMapOf<YearMonth, MutableList<BloodTestPanel>>()
    panels.forEach { panel ->
        val yearMonth = YearMonth.from(panel.collectedAt.atZone(zoneId).toLocalDate())
        groups.getOrPut(yearMonth) { mutableListOf() }.add(panel)
    }

    return groups.map { (yearMonth, groupedPanels) ->
        CalibrationPanelMonthGroup(
            yearMonth = yearMonth,
            monthLabel = monthFormatter(yearMonth.atDay(1)),
            panels = groupedPanels,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CalibrationPanelRow(
    panel: BloodTestPanel,
    settingsState: SettingsState,
    dateTimeFormatters: CalibrationPanelDateTimeFormatters,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    val dateTimeLabels = remember(panel.collectedAt, dateTimeFormatters) {
        formatCalibrationPanelDateTimeLabels(
            collectedAt = panel.collectedAt,
            dateTimeFormatters = dateTimeFormatters,
        )
    }
    val valueSummary = remember(panel.results, settingsState.calibrationDefaultUnits) {
        formatCalibrationPanelValueSummary(panel, settingsState)
    }

    var dateTimeColumnWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    EditorSegmentedListItem(
        index = index,
        count = count,
        onClick = onClick,
    ) {
        Box {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    CalibrationPanelDateTimeColumn(
                        labels = dateTimeLabels,
                        modifier = Modifier.onSizeChanged {
                            dateTimeColumnWidth = it.width
                        }
                    )

                    CalibrationPanelResultSummaryColumn(
                        modifier = Modifier.weight(1f),
                        panel = panel,
                        valueSummary = valueSummary,
                    )

                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                valueSummary.testosteroneResultSummary?.let { testosteroneResultSummary ->
                    CalibrationPanelResultAdditionalSummaryRow(
                        resultSummary = testosteroneResultSummary,
                        modifier = Modifier.padding(
                            start = with(density) {
                                dateTimeColumnWidth.toDp() + 28.dp
                            }
                        )
                    )
                }
            }
            if (dateTimeColumnWidth > 0) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(
                            start = with(density) {
                                dateTimeColumnWidth.toDp() + 14.dp
                            },
                            top = 4.dp,
                            bottom = valueSummary.testosteroneResultSummary?.let { 6.dp } ?: 4.dp,
                        )
                ) {
                    VerticalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CalibrationPanelDateTimeColumn(
    labels: CalibrationPanelDateTimeLabels,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((-2).dp),
        modifier = modifier,
    ) {
        Text(
            text = labels.monthLabel.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = labels.dayLabel,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Text(
            text = labels.timeLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CalibrationPanelResultSummaryColumn(
    modifier: Modifier = Modifier,
    panel: BloodTestPanel,
    valueSummary: CalibrationPanelValueSummary,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        valueSummary.mainResultSummary?.let { mainResultSummary ->
            CalibrationPanelResultSummaryRow(resultSummary = mainResultSummary)
        }
        val hasEstradiolResult = panel.results.any { result ->
            (result.analyte as? BloodTestResultAnalyte.Builtin)?.key == BloodAnalyteKey.E2
        }
        CalibrationPanelMetadataRow(
            timeSinceLastEstradiolDoseMillis = panel.timeSinceLastEstradiolDoseMillis
                ?.takeIf { hasEstradiolResult },
            remainingResultCount = valueSummary.remainingResultCount,
            hasNotes = !panel.notes.isNullOrBlank(),
        )
    }
}

@Composable
private fun CalibrationPanelResultSummaryRow(
    resultSummary: CalibrationPanelResultSummary,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = resultSummary.value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 24.sp
            ),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            text = resultSummary.unit,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alignByBaseline(),
        )
        CalibrationResultSummaryDisplayNameText(
            resultSummary = resultSummary,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CalibrationPanelResultAdditionalSummaryRow(
    resultSummary: CalibrationPanelResultSummary,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.WaterDrop,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = resultSummary.value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                text = resultSummary.unit,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
            CalibrationResultSummaryDisplayNameText(
                resultSummary = resultSummary,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RowScope.CalibrationResultSummaryDisplayNameText(
    resultSummary: CalibrationPanelResultSummary,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val fullDisplayName = calibrationResultSummaryFullDisplayName(resultSummary)
    val abbreviatedDisplayName = calibrationResultSummaryAbbreviatedDisplayName(resultSummary)
    var availableWidthPx by remember(fullDisplayName, abbreviatedDisplayName) { mutableIntStateOf(0) }
    var useAbbreviation by remember(
        fullDisplayName,
        abbreviatedDisplayName,
        availableWidthPx,
        style,
    ) {
        mutableStateOf(false)
    }

    Text(
        text = if (useAbbreviation && abbreviatedDisplayName != null) {
            abbreviatedDisplayName
        } else {
            fullDisplayName
        },
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .weight(1f)
            .alignByBaseline()
            .onSizeChanged { size ->
                if (availableWidthPx != size.width) {
                    availableWidthPx = size.width
                }
            },
        onTextLayout = { layoutResult ->
            if (!useAbbreviation &&
                abbreviatedDisplayName != null &&
                layoutResult.hasVisualOverflow
            ) {
                useAbbreviation = true
            }
        },
    )
}

@Composable
private fun calibrationResultSummaryFullDisplayName(
    resultSummary: CalibrationPanelResultSummary,
): String {
    return when (resultSummary) {
        is CalibrationPanelResultSummary.Builtin -> {
            stringResource(calibrationAnalyteFullNameRes(resultSummary.analyteKey)) +
                calibrationResultSummaryRangeStatusSuffix(resultSummary.rangeStatus)
        }
        is CalibrationPanelResultSummary.Custom -> resultSummary.name
    }
}

private fun calibrationResultSummaryAbbreviatedDisplayName(
    resultSummary: CalibrationPanelResultSummary,
): String? {
    return when (resultSummary) {
        is CalibrationPanelResultSummary.Builtin -> {
            calibrationAnalyteLabel(resultSummary.analyteKey) +
                calibrationResultSummaryRangeStatusSuffix(resultSummary.rangeStatus)
        }
        is CalibrationPanelResultSummary.Custom -> {
            resultSummary.abbreviation.takeUnless { it == resultSummary.name }
        }
    }
}

private fun calibrationResultSummaryRangeStatusSuffix(
    rangeStatus: CalibrationRangeStatus?,
): String {
    return when (rangeStatus) {
        CalibrationRangeStatus.ABOVE -> " ↑"
        CalibrationRangeStatus.BELOW -> " ↓"
        else -> ""
    }
}

@Composable
private fun CalibrationPanelMetadataRow(
    timeSinceLastEstradiolDoseMillis: Long?,
    remainingResultCount: Int,
    hasNotes: Boolean,
) {
    if (timeSinceLastEstradiolDoseMillis == null && remainingResultCount <= 0 && !hasNotes) {
        return
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        timeSinceLastEstradiolDoseMillis?.let { elapsedMillis ->
            val elapsedLabel =
                "E2 +${calibrationElapsedDurationLabel(elapsedMillis).replace(" ", "")}"

            CalibrationPanelPill(
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Text(
                    text = elapsedLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 1.dp)
                        .cjkTextOffset(elapsedLabel),
                )
            }
        }
        if (remainingResultCount > 0) {
            val extraEntriesLabel = pluralStringResource(
                R.plurals.settings_calibration_extra_entries,
                remainingResultCount,
                remainingResultCount,
            )
            CalibrationPanelPill(
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = extraEntriesLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 1.dp)
                        .cjkTextOffset(extraEntriesLabel),
                )
            }
        }
        if (hasNotes) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.StickyNote2,
                contentDescription = stringResource(R.string.settings_calibration_notes_label),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun CalibrationPanelPill(
    modifier: Modifier = Modifier,
    color: Color,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = color
    ) {
        content()
    }
}

internal sealed interface CalibrationPanelResultSummary {
    val value: String
    val unit: String

    data class Builtin(
        val analyteKey: BloodAnalyteKey,
        override val value: String,
        override val unit: String,
        val rangeStatus: CalibrationRangeStatus? = null,
    ) : CalibrationPanelResultSummary

    data class Custom(
        val name: String,
        val abbreviation: String,
        override val value: String,
        override val unit: String,
    ) : CalibrationPanelResultSummary
}

internal data class CalibrationPanelValueSummary(
    val mainResultSummary: CalibrationPanelResultSummary?,
    val testosteroneResultSummary: CalibrationPanelResultSummary?,
    val remainingResultCount: Int,
)

internal fun formatCalibrationPanelValueSummary(
    panel: BloodTestPanel,
    settingsState: SettingsState = SettingsState(),
): CalibrationPanelValueSummary {
    val orderedResults = panel.results.sortedBy(BloodTestResult::displayOrder)
    val e2Result = orderedResults.firstOrNull { result ->
        val analyte = result.analyte as? BloodTestResultAnalyte.Builtin
        analyte?.key == BloodAnalyteKey.E2
    }
    val testosteroneResult = orderedResults.firstOrNull { result ->
        val analyte = result.analyte as? BloodTestResultAnalyte.Builtin
        analyte?.key == BloodAnalyteKey.T
    }
    val mainResult = e2Result ?: testosteroneResult ?: orderedResults.firstOrNull()
    val additionalResult = if (e2Result != null) testosteroneResult else null

    val displayedResultUuids = listOfNotNull(
        mainResult?.uuid,
        additionalResult?.uuid,
    ).toSet()

    return CalibrationPanelValueSummary(
        mainResultSummary = mainResult?.let { result ->
            formatCalibrationResultSummary(result, settingsState)
        },
        testosteroneResultSummary = additionalResult?.let { result ->
            formatCalibrationResultSummary(result, settingsState)
        },
        remainingResultCount = orderedResults.count { result -> result.uuid !in displayedResultUuids },
    )
}

private fun formatCalibrationResultSummary(
    result: BloodTestResult,
    settingsState: SettingsState,
): CalibrationPanelResultSummary {
    return when (val analyte = result.analyte) {
        is BloodTestResultAnalyte.Builtin -> {
            formatCalibrationBuiltinResultSummary(
                result = result,
                analyteKey = analyte.key,
                preferredUnit = defaultCalibrationUnitFor(analyte.key, settingsState),
            )
        }

        is BloodTestResultAnalyte.Custom -> {
            CalibrationPanelResultSummary.Custom(
                name = analyte.name,
                abbreviation = analyte.abbreviation,
                value = formatCalibrationNumericValue(result.value),
                unit = formatCalibrationUnitLabel(result.unitSnapshot),
            )
        }
    }
}

internal fun calibrationAnalyteLabel(analyteKey: BloodAnalyteKey): String {
    return analyteKey.storageValue.uppercase()
}

internal fun calibrationAnalyteFullNameRes(analyteKey: BloodAnalyteKey): Int {
    return when (analyteKey) {
        BloodAnalyteKey.E2 -> R.string.medication_category_estradiol
        BloodAnalyteKey.T -> R.string.medication_category_testosterone
        BloodAnalyteKey.PROG -> R.string.settings_calibration_analyte_prog
        BloodAnalyteKey.PRL -> R.string.settings_calibration_analyte_prl
        BloodAnalyteKey.FSH -> R.string.settings_calibration_analyte_fsh
        BloodAnalyteKey.LH -> R.string.settings_calibration_analyte_lh
    }
}

internal fun formatCalibrationBuiltinResultSummary(
    result: BloodTestResult,
    analyteKey: BloodAnalyteKey,
    preferredUnit: BloodUnitKey = BloodTestCatalog.canonicalUnitFor(analyteKey),
): CalibrationPanelResultSummary.Builtin {
    val storedUnit = BloodUnitKey.fromStorageValue(result.unitSnapshot)
    val displayValue = if (storedUnit == preferredUnit) {
        result.value
    } else {
        BloodTestCatalog.fromCanonical(
            analyteKey = analyteKey,
            canonicalValue = result.canonicalValue,
            unit = preferredUnit,
        )
    }
    return CalibrationPanelResultSummary.Builtin(
        analyteKey = analyteKey,
        value = if (storedUnit == preferredUnit) {
            formatCalibrationNumericValue(displayValue)
        } else {
            formatCalibrationConvertedValue(displayValue)
        },
        unit = calibrationUnitLabel(preferredUnit),
        rangeStatus = calibrationRangeStatusForCanonical(analyteKey, result.canonicalValue),
    )
}

internal fun calibrationRangeStatusForCanonical(
    analyteKey: BloodAnalyteKey,
    canonicalValue: Double,
): CalibrationRangeStatus? {
    val target = calibrationCanonicalTargets[analyteKey] ?: return null
    return when (target) {
        is CalibrationCanonicalTarget.Range -> when {
            canonicalValue < target.low -> CalibrationRangeStatus.BELOW
            canonicalValue > target.high -> CalibrationRangeStatus.ABOVE
            else -> CalibrationRangeStatus.IN_RANGE
        }
        is CalibrationCanonicalTarget.UpperBound -> when {
            canonicalValue > target.high -> CalibrationRangeStatus.ABOVE
            else -> CalibrationRangeStatus.IN_RANGE
        }
    }
}

internal fun calibrationUnitLabelFor(
    analyteKey: BloodAnalyteKey,
    settingsState: SettingsState = SettingsState(),
): String {
    return formatCalibrationUnitLabel(
        defaultCalibrationUnitFor(analyteKey, settingsState).storageValue
    )
}

internal fun calibrationUnitLabel(unit: BloodUnitKey): String {
    return formatCalibrationUnitLabel(unit.storageValue)
}

internal fun formatCalibrationUnitLabel(unitSnapshot: String): String {
    return when (BloodUnitKey.fromStorageValue(unitSnapshot)) {
        BloodUnitKey.PG_ML -> "pg/mL"
        BloodUnitKey.PMOL_L -> "pmol/L"
        BloodUnitKey.NG_DL -> "ng/dL"
        BloodUnitKey.NMOL_L -> "nmol/L"
        BloodUnitKey.NG_ML -> "ng/mL"
        BloodUnitKey.MIU_L -> "mIU/L"
        BloodUnitKey.MIU_ML -> "mIU/mL"
        BloodUnitKey.IU_L -> "IU/L"
        null -> unitSnapshot
    }
}

internal fun formatCalibrationNumericValue(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        value.toString()
    }
}

internal fun calibrationValueInPreferredUnitLabel(
    analyteKey: BloodAnalyteKey,
    valueText: String,
    unit: BloodUnitKey,
    preferredUnit: BloodUnitKey,
): String? {
    if (unit == preferredUnit) {
        return null
    }

    val parsedValue = parseCalibrationNumericInput(valueText) ?: return null
    val canonicalValue = BloodTestCatalog.toCanonical(
        analyteKey = analyteKey,
        value = parsedValue,
        unit = unit,
    )
    val preferredValue = BloodTestCatalog.fromCanonical(
        analyteKey = analyteKey,
        canonicalValue = canonicalValue,
        unit = preferredUnit,
    )
    return "${formatCalibrationConvertedValue(preferredValue)} ${calibrationUnitLabel(preferredUnit)}"
}

internal fun formatCalibrationConvertedValue(value: Double): String {
    val roundedValue = when {
        value >= 100.0 -> round(value * 10.0) / 10.0
        value >= 1.0 -> round(value * 100.0) / 100.0
        else -> round(value * 1000.0) / 1000.0
    }
    return formatCalibrationNumericValue(roundedValue)
}

private sealed interface CalibrationCanonicalTarget {
    data class Range(val low: Double, val high: Double) : CalibrationCanonicalTarget
    data class UpperBound(val high: Double) : CalibrationCanonicalTarget
}

private val calibrationCanonicalTargets: Map<BloodAnalyteKey, CalibrationCanonicalTarget> = mapOf(
    BloodAnalyteKey.E2 to CalibrationCanonicalTarget.Range(low = 100.0, high = 200.0),
    BloodAnalyteKey.T to CalibrationCanonicalTarget.UpperBound(high = 50.0),
)

@Composable
internal fun calibrationTargetLabel(
    analyteKey: BloodAnalyteKey,
    unit: BloodUnitKey,
): String? {
    val target = calibrationCanonicalTargets[analyteKey] ?: return null
    val unitLabel = formatCalibrationUnitLabel(unit.storageValue)
    return when (target) {
        is CalibrationCanonicalTarget.Range -> stringResource(
            R.string.settings_calibration_target_range,
            formatCalibrationTargetValue(BloodTestCatalog.fromCanonical(analyteKey, target.low, unit)),
            formatCalibrationTargetValue(BloodTestCatalog.fromCanonical(analyteKey, target.high, unit)),
            unitLabel,
        )
        is CalibrationCanonicalTarget.UpperBound -> stringResource(
            R.string.settings_calibration_target_upper,
            formatCalibrationTargetValue(BloodTestCatalog.fromCanonical(analyteKey, target.high, unit)),
            unitLabel,
        )
    }
}

@Composable
private fun calibrationHistoryTargetValueLabel(
    analyteKey: BloodAnalyteKey,
    unit: BloodUnitKey,
): String? {
    val target = calibrationCanonicalTargets[analyteKey] ?: return null
    val unitLabel = formatCalibrationUnitLabel(unit.storageValue)
    return when (target) {
        is CalibrationCanonicalTarget.Range -> stringResource(
            R.string.settings_calibration_target_range_value,
            formatCalibrationTargetValue(BloodTestCatalog.fromCanonical(analyteKey, target.low, unit)),
            formatCalibrationTargetValue(BloodTestCatalog.fromCanonical(analyteKey, target.high, unit)),
            unitLabel,
        )
        is CalibrationCanonicalTarget.UpperBound -> stringResource(
            R.string.settings_calibration_target_upper_value,
            formatCalibrationTargetValue(BloodTestCatalog.fromCanonical(analyteKey, target.high, unit)),
            unitLabel,
        )
    }
}

internal enum class CalibrationRangeStatus {
    BELOW,
    IN_RANGE,
    ABOVE,
}

internal fun calibrationRangeStatus(
    analyteKey: BloodAnalyteKey,
    valueText: String,
    unit: BloodUnitKey,
): CalibrationRangeStatus? {
    val target = calibrationCanonicalTargets[analyteKey] ?: return null
    val parsed = parseCalibrationNumericInput(valueText) ?: return null
    val canonical = BloodTestCatalog.toCanonical(
        analyteKey = analyteKey,
        value = parsed,
        unit = unit,
    )
    return when (target) {
        is CalibrationCanonicalTarget.Range -> when {
            canonical < target.low -> CalibrationRangeStatus.BELOW
            canonical > target.high -> CalibrationRangeStatus.ABOVE
            else -> CalibrationRangeStatus.IN_RANGE
        }
        is CalibrationCanonicalTarget.UpperBound -> when {
            canonical > target.high -> CalibrationRangeStatus.ABOVE
            else -> CalibrationRangeStatus.IN_RANGE
        }
    }
}

private fun formatCalibrationTargetValue(value: Double): String {
    val rounded = if (value >= 10.0) {
        Math.round(value).toDouble()
    } else {
        Math.round(value * 10.0) / 10.0
    }
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        String.format(Locale.ROOT, "%.1f", rounded)
    }
}

@Composable
internal fun calibrationElapsedDurationLabel(durationMillis: Long): String {
    val clampedSeconds = Duration.ofMillis(durationMillis).seconds.coerceAtLeast(0)
    val totalMinutes = clampedSeconds / 60
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes % (24 * 60)) / 60
    val minutes = totalMinutes % 60

    return when {
        days > 0 -> stringResource(
            R.string.main_duration_days_hours,
            days,
            hours
        )

        hours > 0 -> stringResource(
            R.string.main_duration_hours_minutes,
            hours,
            minutes
        )

        else -> stringResource(
            R.string.main_duration_minutes,
            minutes.coerceAtLeast(1)
        )
    }
}

@Preview(
    name = "Calibration Page",
    showBackground = true,
    widthDp = 420,
    heightDp = 920,
)
@Composable
private fun CalibrationScreenPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        CalibrationScreenContent(
            uiState = CalibrationUiState(
                panels = previewCalibrationPanels(),
                settingsState = SettingsState(
                    calibrationDefaultUnits = mapOf(
                        BloodAnalyteKey.E2 to BloodUnitKey.PMOL_L,
                        BloodAnalyteKey.T to BloodUnitKey.NMOL_L,
                    )
                ),
            ),
            panelDateTimeFormatters = previewCalibrationPanelDateTimeFormatters(),
            monthFormatter = previewCalibrationMonthFormatter(),
            onNavigateBack = { },
            onDeleteAllCalibrationEntries = { },
            onDeleteAllCalibrationEntriesResultConsumed = { },
            onUnitsClick = { },
            onAddClick = { },
            onPanelClick = { },
        )
    }
}

@Preview(
    name = "Calibration Panel Row",
    showBackground = true,
    widthDp = 420,
)
@Composable
private fun CalibrationPanelRowPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        CalibrationPanelRow(
            panel = previewCalibrationPanels().first(),
            settingsState = SettingsState(
                calibrationDefaultUnits = mapOf(
                    BloodAnalyteKey.E2 to BloodUnitKey.PMOL_L,
                    BloodAnalyteKey.T to BloodUnitKey.NMOL_L,
                )
            ),
            dateTimeFormatters = previewCalibrationPanelDateTimeFormatters(),
            index = 0,
            count = 1,
            onClick = { },
        )
    }
}

private fun previewCalibrationPanelDateTimeFormatters(): CalibrationPanelDateTimeFormatters {
    return calibrationPanelDateTimeFormatters(Locale.ENGLISH, uses24HourFormat = false)
}

private fun previewCalibrationMonthFormatter(): LocalDateFormatter {
    return calibrationMonthHeaderFormatter(
        locale = Locale.ENGLISH,
        currentYear = 2026,
    )
}

private fun previewCalibrationPanels(): List<BloodTestPanel> {
    return listOf(
        BloodTestPanel(
            uuid = UUID.fromString("ccdb13af-4d25-48ef-b94e-bfe61f8fcb32"),
            collectedAt = Instant.parse("2026-04-24T00:30:00Z"),
            collectedAtTimeZoneId = "Asia/Tokyo",
            notes = "Trough draw before morning dose.",
            timeSinceLastEstradiolDoseMillis = Duration.ofHours(9).plusMinutes(30).toMillis(),
            timeSinceLastTestosteroneDoseMillis = null,
            results = listOf(
                BloodTestResult(
                    uuid = UUID.fromString("5bce6841-c2d5-4192-ba59-ab18e95fdb4a"),
                    createdAt = Instant.parse("2026-04-24T00:30:00Z"),
                    displayOrder = 0,
                    analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.E2),
                    value = 559.5,
                    unitSnapshot = BloodUnitKey.PMOL_L.storageValue,
                    canonicalValue = 152.4,
                ),
                BloodTestResult(
                    uuid = UUID.fromString("d6cf4bf5-f47e-41a1-97ce-96f818e63888"),
                    createdAt = Instant.parse("2026-04-24T00:30:00Z"),
                    displayOrder = 1,
                    analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.T),
                    value = 1.18,
                    unitSnapshot = BloodUnitKey.NMOL_L.storageValue,
                    canonicalValue = 34.0,
                ),
                BloodTestResult(
                    uuid = UUID.fromString("7848bc50-26c8-4c20-9cec-77f77b01a8a1"),
                    createdAt = Instant.parse("2026-04-24T00:30:00Z"),
                    displayOrder = 2,
                    analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.PROG),
                    value = 0.7,
                    unitSnapshot = BloodUnitKey.NG_ML.storageValue,
                    canonicalValue = 0.7,
                ),
                BloodTestResult(
                    uuid = UUID.fromString("db408c1e-7c90-49bb-9401-5f45710c18de"),
                    createdAt = Instant.parse("2026-04-24T00:30:00Z"),
                    displayOrder = 3,
                    analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.PRL),
                    value = 12.0,
                    unitSnapshot = BloodUnitKey.NG_ML.storageValue,
                    canonicalValue = 12.0,
                ),
                BloodTestResult(
                    uuid = UUID.fromString("69d65c65-f4ef-42c7-b173-5851d8484684"),
                    createdAt = Instant.parse("2026-04-24T00:30:00Z"),
                    displayOrder = 4,
                    analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.FSH),
                    value = 4.2,
                    unitSnapshot = BloodUnitKey.MIU_ML.storageValue,
                    canonicalValue = 4.2,
                ),
                BloodTestResult(
                    uuid = UUID.fromString("323f8f0d-296e-47fb-b190-e32f8d5623d7"),
                    createdAt = Instant.parse("2026-04-24T00:30:00Z"),
                    displayOrder = 5,
                    analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.LH),
                    value = 3.1,
                    unitSnapshot = BloodUnitKey.MIU_ML.storageValue,
                    canonicalValue = 3.1,
                ),
            ),
            createdAt = Instant.parse("2026-04-24T00:30:00Z"),
            updatedAt = Instant.parse("2026-04-24T00:30:00Z"),
        ),
        BloodTestPanel(
            uuid = UUID.fromString("f791a95e-f0e0-495d-a1ce-0f41150eed2d"),
            collectedAt = Instant.parse("2026-04-12T22:15:00Z"),
            collectedAtTimeZoneId = "America/Los_Angeles",
            notes = null,
            timeSinceLastEstradiolDoseMillis = Duration.ofHours(26).toMillis(),
            timeSinceLastTestosteroneDoseMillis = null,
            results = listOf(
                BloodTestResult(
                    uuid = UUID.fromString("2c35207b-c771-4c11-b6f2-f35f485542cd"),
                    createdAt = Instant.parse("2026-04-12T22:15:00Z"),
                    displayOrder = 0,
                    analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.T),
                    value = 1.18,
                    unitSnapshot = BloodUnitKey.NMOL_L.storageValue,
                    canonicalValue = 34.0,
                ),
                BloodTestResult(
                    uuid = UUID.fromString("589a75db-98d9-4898-b637-45d378e2a2a8"),
                    createdAt = Instant.parse("2026-04-12T22:15:00Z"),
                    displayOrder = 1,
                    analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.PRL),
                    value = 10.4,
                    unitSnapshot = BloodUnitKey.NG_ML.storageValue,
                    canonicalValue = 10.4,
                ),
            ),
            createdAt = Instant.parse("2026-04-12T22:15:00Z"),
            updatedAt = Instant.parse("2026-04-12T22:15:00Z"),
        ),
        BloodTestPanel(
            uuid = UUID.fromString("47cdd45f-bf48-480b-a22b-6daee6ce271e"),
            collectedAt = Instant.parse("2026-03-26T01:45:00Z"),
            collectedAtTimeZoneId = "Europe/London",
            notes = null,
            timeSinceLastEstradiolDoseMillis = null,
            timeSinceLastTestosteroneDoseMillis = null,
            results = listOf(
                BloodTestResult(
                    uuid = UUID.fromString("6a251f0f-46ce-4cb1-8b0a-c04d7a392d76"),
                    createdAt = Instant.parse("2026-03-26T01:45:00Z"),
                    displayOrder = 0,
                    analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.E2),
                    value = 95.0,
                    unitSnapshot = BloodUnitKey.PG_ML.storageValue,
                    canonicalValue = 95.0,
                ),
            ),
            createdAt = Instant.parse("2026-03-26T01:45:00Z"),
            updatedAt = Instant.parse("2026-03-26T01:45:00Z"),
        )
    )
}
