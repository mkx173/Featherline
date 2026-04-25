package com.mkx.hrttracker.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mkx.hrttracker.R
import com.mkx.hrttracker.model.bloodtest.BloodAnalyteKey
import com.mkx.hrttracker.model.bloodtest.BloodTestCatalog
import com.mkx.hrttracker.model.bloodtest.BloodTestPanel
import com.mkx.hrttracker.model.bloodtest.BloodTestResult
import com.mkx.hrttracker.model.bloodtest.BloodTestResultAnalyte
import com.mkx.hrttracker.model.bloodtest.BloodUnitKey
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
    onAddClick: () -> Unit,
    onPanelClick: (UUID) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalibrationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appLocale = rememberAppLocale()
    val dateTimeFormatter = remember(appLocale) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(appLocale)
    }
    val monthFormatter = remember(appLocale) {
        calibrationMonthHeaderFormatter(appLocale)
    }

    CalibrationScreenContent(
        uiState = uiState,
        dateTimeFormatter = dateTimeFormatter,
        monthFormatter = monthFormatter,
        onNavigateBack = onNavigateBack,
        onAddClick = onAddClick,
        onPanelClick = onPanelClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalibrationScreenContent(
    uiState: CalibrationUiState,
    dateTimeFormatter: DateTimeFormatter,
    monthFormatter: DateTimeFormatter,
    onNavigateBack: () -> Unit,
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cancel),
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
        if (uiState.panels.isEmpty() && !uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(dimensionResource(R.dimen.padding_medium)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.settings_calibration_empty_state),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(dimensionResource(R.dimen.padding_medium)),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.list_segment_gap)),
            ) {
                if (uiState.panels.isNotEmpty()) {
                    item(key = "calibration-total") {
                        Text(
                            text = pluralStringResource(
                                R.plurals.settings_calibration_total_count,
                                uiState.panels.size,
                                uiState.panels.size,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    monthGroups.forEach { monthGroup ->
                        item(key = "month-${monthGroup.yearMonth}") {
                            CalibrationMonthHeader(monthLabel = monthGroup.monthLabel)
                        }

                        items(
                            items = monthGroup.panels,
                            key = { panel -> panel.uuid },
                        ) { panel ->
                            CalibrationPanelRow(
                                panel = panel,
                                dateTimeFormatter = dateTimeFormatter,
                                onClick = { onPanelClick(panel.uuid) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalibrationMonthHeader(
    monthLabel: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = monthLabel,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    )
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

@Composable
private fun CalibrationPanelRow(
    panel: BloodTestPanel,
    dateTimeFormatter: DateTimeFormatter,
    onClick: () -> Unit,
) {
    val collectedAtLabel = remember(panel.collectedAt, dateTimeFormatter) {
        formatCalibrationPanelCollectedAtLabel(panel, dateTimeFormatter)
    }
    val valueSummary = remember(panel.results) {
        formatCalibrationPanelValueSummary(panel)
    }

    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        headlineContent = {
            Text(text = collectedAtLabel)
        },
        supportingContent = {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                valueSummary.mainResultSummary?.let { mainResultSummary ->
                    Text(text = mainResultSummary)
                }
                CalibrationPanelSecondarySummary(
                    testosteroneResultSummary = valueSummary.testosteroneResultSummary,
                    remainingResultCount = valueSummary.remainingResultCount,
                )
                panel.timeSinceLastEstradiolDoseMillis?.let { elapsedMillis ->
                    Text(
                        text = stringResource(
                            R.string.settings_calibration_last_e2_elapsed,
                            calibrationElapsedDurationLabel(elapsedMillis)
                        )
                    )
                }
            }
        },
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!panel.notes.isNullOrBlank()) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Notes,
                        contentDescription = stringResource(R.string.settings_calibration_notes_label),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                )
            }
        }
    )
}

internal fun formatCalibrationPanelCollectedAtLabel(
    panel: BloodTestPanel,
    dateTimeFormatter: DateTimeFormatter,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    return panel.collectedAt.atZone(zoneId).format(dateTimeFormatter)
}

@Composable
private fun CalibrationPanelSecondarySummary(
    testosteroneResultSummary: String?,
    remainingResultCount: Int,
) {
    val remainingEntriesSummary = if (remainingResultCount > 0) {
        pluralStringResource(
            R.plurals.settings_calibration_extra_entries,
            remainingResultCount,
            remainingResultCount,
        )
    } else {
        null
    }

    when {
        testosteroneResultSummary != null && remainingEntriesSummary != null -> {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = testosteroneResultSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = remainingEntriesSummary,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }

        testosteroneResultSummary != null -> {
            Text(text = testosteroneResultSummary)
        }

        remainingEntriesSummary != null -> {
            Text(text = remainingEntriesSummary)
        }
    }
}

internal data class CalibrationPanelValueSummary(
    val mainResultSummary: String?,
    val testosteroneResultSummary: String?,
    val remainingResultCount: Int,
)

internal fun formatCalibrationPanelValueSummary(panel: BloodTestPanel): CalibrationPanelValueSummary {
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
        mainResultSummary = mainResult?.let(::formatCalibrationResultSummary),
        testosteroneResultSummary = testosteroneResult?.let(::formatCalibrationResultSummary),
        remainingResultCount = orderedResults.count { result -> result.uuid !in displayedResultUuids },
    )
}

private fun formatCalibrationResultSummary(result: BloodTestResult): String {
    return when (val analyte = result.analyte) {
        is BloodTestResultAnalyte.Builtin -> {
            formatCalibrationBuiltinResultSummary(
                analyteKey = analyte.key,
                canonicalValue = result.canonicalValue,
            )
        }

        is BloodTestResultAnalyte.Custom -> {
            "${analyte.name ?: "Custom"}: ${formatCalibrationNumericValue(result.value)} ${formatCalibrationUnitLabel(result.unitSnapshot)}"
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
    analyteKey: BloodAnalyteKey,
    canonicalValue: Double,
): String {
    return "${calibrationAnalyteLabel(analyteKey)}: ${formatCalibrationNumericValue(canonicalValue)} ${
        calibrationUnitLabel(BloodTestCatalog.canonicalUnitFor(analyteKey))
    }"
}

internal fun calibrationUnitLabelFor(analyteKey: BloodAnalyteKey): String {
    return formatCalibrationUnitLabel(defaultCalibrationUnitFor(analyteKey).storageValue)
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
                panels = previewCalibrationPanels()
            ),
            dateTimeFormatter = previewCalibrationDateTimeFormatter(),
            monthFormatter = previewCalibrationMonthFormatter(),
            onNavigateBack = { },
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
        Box(modifier = Modifier.padding(16.dp)) {
            CalibrationPanelRow(
                panel = previewCalibrationPanels().first(),
                dateTimeFormatter = previewCalibrationDateTimeFormatter(),
                onClick = { },
            )
        }
    }
}

private fun previewCalibrationDateTimeFormatter(): DateTimeFormatter {
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.ENGLISH)
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
