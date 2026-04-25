package com.mkx.hrttracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WaterDrop
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
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
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme
import com.mkx.hrttracker.util.rememberAppLocale
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID

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
    val panelDateTimeFormatters = remember(appLocale) {
        calibrationPanelDateTimeFormatters(appLocale)
    }
    val monthFormatter = remember(appLocale) {
        calibrationMonthHeaderFormatter(appLocale)
    }

    CalibrationScreenContent(
        uiState = uiState,
        panelDateTimeFormatters = panelDateTimeFormatters,
        monthFormatter = monthFormatter,
        onNavigateBack = onNavigateBack,
        onUnitsClick = onUnitsClick,
        onAddClick = onAddClick,
        onPanelClick = onPanelClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalibrationScreenContent(
    uiState: CalibrationUiState,
    panelDateTimeFormatters: CalibrationPanelDateTimeFormatters,
    monthFormatter: DateTimeFormatter,
    onNavigateBack: () -> Unit,
    onUnitsClick: () -> Unit,
    onAddClick: () -> Unit,
    onPanelClick: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    val monthGroups = remember(uiState.panels, monthFormatter) {
        groupCalibrationPanelsByMonth(uiState.panels, monthFormatter)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
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
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.settings_calibration_add_result),
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(dimensionResource(R.dimen.padding_medium)),
        ) {
            item(key = "calibration-info") {
                CalibrationInfoCard(panelCount = uiState.panels.size)
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (uiState.panels.isEmpty() && !uiState.isLoading) {
                item(key = "calibration-empty") {
                    Text(
                        text = stringResource(R.string.settings_calibration_empty_state),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = dimensionResource(R.dimen.padding_medium)),
                        textAlign = TextAlign.Center,
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
}

@Composable
private fun CalibrationInfoCard(
    panelCount: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_info),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.settings_calibration_info_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = pluralStringResource(
                    R.plurals.settings_calibration_total_count,
                    panelCount,
                    panelCount,
                ).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End),
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
    monthFormatter: DateTimeFormatter,
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
            monthLabel = yearMonth.atDay(1).format(monthFormatter),
            panels = groupedPanels,
        )
    }
}

internal fun calibrationMonthHeaderFormatter(appLocale: Locale): DateTimeFormatter {
    return if (appLocale.language == Locale.CHINESE.language) {
        DateTimeFormatter.ofPattern("yyyy年M月", appLocale)
    } else {
        DateTimeFormatter.ofPattern("LLLL yyyy", appLocale)
    }
}

internal data class CalibrationPanelDateTimeFormatters(
    val monthFormatter: DateTimeFormatter,
    val dayFormatter: DateTimeFormatter,
    val timeFormatter: DateTimeFormatter,
)

internal data class CalibrationPanelDateTimeLabels(
    val monthLabel: String,
    val dayLabel: String,
    val timeLabel: String,
)

internal fun calibrationPanelDateTimeFormatters(appLocale: Locale): CalibrationPanelDateTimeFormatters {
    val monthPattern = if (appLocale.language == Locale.CHINESE.language) {
        "M月"
    } else {
        "MMM"
    }
    return CalibrationPanelDateTimeFormatters(
        monthFormatter = DateTimeFormatter.ofPattern(monthPattern, appLocale),
        dayFormatter = DateTimeFormatter.ofPattern("d", appLocale),
        timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(appLocale),
    )
}

internal fun formatCalibrationPanelDateTimeLabels(
    panel: BloodTestPanel,
    dateTimeFormatters: CalibrationPanelDateTimeFormatters,
    zoneId: ZoneId = ZoneId.systemDefault(),
): CalibrationPanelDateTimeLabels {
    val collectedAt = panel.collectedAt.atZone(zoneId)
    return CalibrationPanelDateTimeLabels(
        monthLabel = collectedAt.format(dateTimeFormatters.monthFormatter),
        dayLabel = collectedAt.format(dateTimeFormatters.dayFormatter),
        timeLabel = collectedAt.format(dateTimeFormatters.timeFormatter),
    )
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
            panel = panel,
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
        Text(
            text = calibrationResultSummaryDisplayName(resultSummary),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alignByBaseline(),
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
            Text(
                text = calibrationResultSummaryDisplayName(resultSummary),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

@Composable
private fun calibrationResultSummaryDisplayName(
    resultSummary: CalibrationPanelResultSummary,
): String {
    return when (resultSummary) {
        is CalibrationPanelResultSummary.Builtin ->
            stringResource(calibrationAnalyteFullNameRes(resultSummary.analyteKey))
        is CalibrationPanelResultSummary.Custom -> resultSummary.name
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

            CalibrationPanelPill(
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Text(
                    text = "E2 +${calibrationElapsedDurationLabel(elapsedMillis).replace(" ", "")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                )
            }
        }
        if (remainingResultCount > 0) {
            CalibrationPanelPill(
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals.settings_calibration_extra_entries,
                        remainingResultCount,
                        remainingResultCount,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
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
    ) : CalibrationPanelResultSummary

    data class Custom(
        val name: String,
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
    val mainResult = e2Result ?: orderedResults.firstOrNull()
    val testosteroneResult = e2Result?.let {
        orderedResults.firstOrNull { result ->
            val analyte = result.analyte as? BloodTestResultAnalyte.Builtin
            analyte?.key == BloodAnalyteKey.T
        }
    }

    val displayedResultUuids = listOfNotNull(
        mainResult?.uuid,
        testosteroneResult?.uuid,
    ).toSet()

    return CalibrationPanelValueSummary(
        mainResultSummary = mainResult?.let { result ->
            formatCalibrationResultSummary(result, settingsState)
        },
        testosteroneResultSummary = testosteroneResult?.let { result ->
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
    )
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
        value >= 100.0 -> kotlin.math.round(value * 10.0) / 10.0
        value >= 1.0 -> kotlin.math.round(value * 100.0) / 100.0
        else -> kotlin.math.round(value * 1000.0) / 1000.0
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
        String.format(java.util.Locale.ROOT, "%.1f", rounded)
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
    return calibrationPanelDateTimeFormatters(Locale.ENGLISH)
}

private fun previewCalibrationMonthFormatter(): DateTimeFormatter {
    return calibrationMonthHeaderFormatter(Locale.ENGLISH)
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
