package com.mkx.hrttracker.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.res.stringResource
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

    CalibrationScreenContent(
        uiState = uiState,
        dateTimeFormatter = dateTimeFormatter,
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
    onNavigateBack: () -> Unit,
    onAddClick: () -> Unit,
    onPanelClick: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                items(
                    items = uiState.panels,
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

@Composable
private fun CalibrationPanelRow(
    panel: BloodTestPanel,
    dateTimeFormatter: DateTimeFormatter,
    onClick: () -> Unit,
) {
    val zoneId = remember(panel.collectedAtTimeZoneId) {
        ZoneId.of(panel.collectedAtTimeZoneId)
    }
    val collectedAtLabel = remember(panel.collectedAt, panel.collectedAtTimeZoneId, dateTimeFormatter) {
        panel.collectedAt.atZone(zoneId).format(dateTimeFormatter)
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
                Text(
                    text = stringResource(
                        R.string.settings_calibration_row_meta,
                        valueSummary,
                        panel.collectedAtTimeZoneId
                    )
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
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
            )
        }
    )
}

internal fun formatCalibrationPanelValueSummary(panel: BloodTestPanel): String {
    val e2Result = panel.results.firstOrNull { result ->
        val analyte = result.analyte as? BloodTestResultAnalyte.Builtin
        analyte?.key == BloodAnalyteKey.E2
    }
    if (e2Result != null) {
        return formatCalibrationBuiltinResultSummary(
            analyteKey = BloodAnalyteKey.E2,
            canonicalValue = e2Result.canonicalValue,
        )
    }

    return panel.results.joinToString(separator = " · ") { result ->
        when (val analyte = result.analyte) {
            is BloodTestResultAnalyte.Builtin -> {
                formatCalibrationBuiltinResultSummary(
                    analyteKey = analyte.key,
                    canonicalValue = result.canonicalValue,
                )
            }

            is BloodTestResultAnalyte.Custom -> {
                "${analyte.name ?: "Custom"} ${formatCalibrationNumericValue(result.value)} ${formatCalibrationUnitLabel(result.unitSnapshot)}"
            }
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
    return "${calibrationAnalyteLabel(analyteKey)} ${formatCalibrationNumericValue(canonicalValue)} ${
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
            onNavigateBack = { },
            onAddClick = { },
            onPanelClick = { },
        )
    }
}

private fun previewCalibrationDateTimeFormatter(): DateTimeFormatter {
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.ENGLISH)
}

private fun previewCalibrationPanels(): List<BloodTestPanel> {
    return listOf(
        BloodTestPanel(
            uuid = UUID.fromString("ccdb13af-4d25-48ef-b94e-bfe61f8fcb32"),
            collectedAt = Instant.parse("2026-04-24T00:30:00Z"),
            collectedAtTimeZoneId = "Asia/Tokyo",
            notes = null,
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
                    analyte = BloodTestResultAnalyte.Builtin(BloodAnalyteKey.E2),
                    value = 95.0,
                    unitSnapshot = BloodUnitKey.PG_ML.storageValue,
                    canonicalValue = 95.0,
                )
            ),
            createdAt = Instant.parse("2026-04-12T22:15:00Z"),
            updatedAt = Instant.parse("2026-04-12T22:15:00Z"),
        )
    )
}
