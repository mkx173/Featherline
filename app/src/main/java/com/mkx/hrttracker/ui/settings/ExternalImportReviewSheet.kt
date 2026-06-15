package com.mkx.hrttracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.importer.ExternalImportParseResult
import com.mkx.hrttracker.data.importer.ExternalImportPreview
import com.mkx.hrttracker.data.importer.ExternalImportWarning
import com.mkx.hrttracker.data.importer.ExternalImportWarningMessageKey
import com.mkx.hrttracker.data.importer.ExternalImportWarningReason
import com.mkx.hrttracker.data.importer.ExternalTrackerSourceApp
import com.mkx.hrttracker.data.importer.skippedRows
import com.mkx.hrttracker.ui.components.HazeAlertDialog
import com.mkx.hrttracker.ui.components.HazeModalBottomSheet
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.HrtFilledTonalButton
import com.mkx.hrttracker.ui.components.HrtSection
import com.mkx.hrttracker.ui.components.SupportMessageListItem
import com.mkx.hrttracker.ui.components.cjkTextOffset
import com.mkx.hrttracker.ui.theme.HrtTrackerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalImportReviewSheet(
    preview: ExternalImportPreview,
    isImporting: Boolean,
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    onImportClick: () -> Unit,
) {
    HazeModalBottomSheet(
        onDismissRequest = {
            if (!isImporting) {
                onDismissRequest()
            }
        },
        sheetState = sheetState,
    ) {
        ExternalImportReviewSheetContent(
            preview = preview,
            isImporting = isImporting,
            onDismissRequest = onDismissRequest,
            onImportClick = onImportClick,
        )
    }
}

@Composable
private fun ExternalImportReviewSheetContent(
    preview: ExternalImportPreview,
    isImporting: Boolean,
    onDismissRequest: () -> Unit,
    onImportClick: () -> Unit,
) {
    val title = stringResource(R.string.external_import_review_title)
    val skippedWarnings = preview.warnings.skippedRows()
    var showSkippedDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(
                start = dimensionResource(R.dimen.padding_large),
                end = dimensionResource(R.dimen.padding_large),
                bottom = dimensionResource(R.dimen.padding_large),
            ),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.cjkTextOffset(title),
        )
        Text(
            text = stringResource(R.string.external_import_detected_source, preview.sourceAppLabel),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HrtSection(title = stringResource(R.string.external_import_section_summary)) {
            item {
                SupportMessageListItem(
                    text = stringResource(R.string.external_import_medication_label),
                    supportingText = stringResource(
                        R.string.external_import_medication_summary,
                        preview.medicationRowsToCreate,
                        preview.medicationRowsToUpdate,
                    ),
                    painter = painterResource(R.drawable.ic_medication),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }
            item {
                SupportMessageListItem(
                    text = stringResource(R.string.external_import_lab_label),
                    supportingText = stringResource(
                        R.string.external_import_lab_summary,
                        preview.labRowsToCreate,
                        preview.labRowsToUpdate,
                    ),
                    painter = painterResource(R.drawable.ic_labs),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }
        }

        if (skippedWarnings.isNotEmpty()) {
            HrtSection(title = stringResource(R.string.external_import_section_needs_review)) {
                item {
                    SupportMessageListItem(
                        text = stringResource(R.string.external_import_skipped_rows_title),
                        supportingText = stringResource(R.string.external_import_skipped_rows_subtitle),
                        painter = painterResource(R.drawable.ic_error_outline),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onClick = { showSkippedDialog = true },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = skippedWarnings.size.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_xsmall)))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                dimensionResource(R.dimen.padding_small),
                Alignment.End,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HrtFilledTonalButton(
                text = stringResource(R.string.external_import_cancel),
                onClick = onDismissRequest,
                enabled = !isImporting,
            )
            HrtButton(
                text = stringResource(R.string.external_import_confirm),
                onClick = onImportClick,
                enabled = !isImporting,
            )
        }
    }

    if (showSkippedDialog) {
        ExternalImportSkippedRowsDialog(
            skippedWarnings = skippedWarnings,
            onDismiss = { showSkippedDialog = false },
        )
    }
}

@Composable
private fun ExternalImportSkippedRowsDialog(
    skippedWarnings: List<ExternalImportWarning>,
    onDismiss: () -> Unit,
) {
    HazeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.external_import_skipped_rows_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals.external_import_skipped_rows_count,
                        skippedWarnings.size,
                        skippedWarnings.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                skippedWarnings.forEach { warning -> ExternalImportSkippedRow(warning) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.external_import_skipped_rows_close))
            }
        },
    )
}

@Composable
private fun ExternalImportSkippedRow(warning: ExternalImportWarning) {
    val rowText = warning.rowIndex?.let { index ->
        stringResource(R.string.external_import_skipped_row_number, index + 1)
    }
    val identifier = joinSkippedRowIdentifier(rowText, warning.externalId)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_small)),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_error_outline),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            if (identifier != null) {
                Text(text = identifier, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = externalImportSkippedReasonText(warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(
    name = "Review sheet · with skipped rows",
    showBackground = true,
    widthDp = 420,
)
@Composable
private fun ExternalImportReviewSheetContentPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        // The sheet renders on surfaceContainer in the app, so its list items
        // sit one step higher (surfaceContainerHigh).
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            ExternalImportReviewSheetContent(
                preview = externalImportSamplePreview(externalImportSampleWarnings()),
                isImporting = false,
                onDismissRequest = {},
                onImportClick = {},
            )
        }
    }
}

@Preview(
    name = "Review sheet · no skipped rows",
    showBackground = true,
    widthDp = 420,
)
@Composable
private fun ExternalImportReviewSheetContentNoSkippedPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            ExternalImportReviewSheetContent(
                preview = externalImportSamplePreview(warnings = emptyList()),
                isImporting = false,
                onDismissRequest = {},
                onImportClick = {},
            )
        }
    }
}

@Preview(
    name = "Skipped rows dialog",
    showBackground = true,
    widthDp = 420,
    heightDp = 520,
)
@Composable
private fun ExternalImportSkippedRowsDialogPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        // HazeAlertDialog draws its own surfaceContainerHigh background.
        ExternalImportSkippedRowsDialog(
            skippedWarnings = externalImportSampleWarnings().skippedRows(),
            onDismiss = {},
        )
    }
}

private fun externalImportSamplePreview(
    warnings: List<ExternalImportWarning>,
): ExternalImportPreview {
    val parseResult = ExternalImportParseResult(
        sourceApp = ExternalTrackerSourceApp.TRANSMTF,
        exportVersion = "1",
        exportedAt = null,
        medicationDoses = emptyList(),
        labResults = emptyList(),
        warnings = warnings,
    )
    return ExternalImportPreview(
        parseResult = parseResult,
        sourceAppLabel = "TransMTF",
        medicationRowsToCreate = 12,
        medicationRowsToUpdate = 3,
        labRowsToCreate = 5,
        labRowsToUpdate = 1,
        importedMedicinesToCreate = emptyList(),
        importedMedicinesToReuse = emptyList(),
        warnings = warnings,
    )
}

// Mixed sample: a SOURCE_FALLBACK notice (filtered out by skippedRows) plus four
// skipped rows exercising every joinSkippedRowIdentifier branch (row+id, id-only,
// row-only) and concise messageKey labels.
private fun externalImportSampleWarnings(): List<ExternalImportWarning> = listOf(
    ExternalImportWarning(
        reason = ExternalImportWarningReason.SOURCE_FALLBACK,
        externalId = null,
        rowIndex = null,
        message = "Unrecognized source; parsed as TransMTF-compatible.",
        messageKey = ExternalImportWarningMessageKey.SOURCE_FALLBACK,
    ),
    ExternalImportWarning(
        reason = ExternalImportWarningReason.UNSUPPORTED_ROUTE,
        externalId = "med-1042",
        rowIndex = 2,
        message = "Unsupported medication route.",
        messageKey = ExternalImportWarningMessageKey.MEDICATION_UNSUPPORTED_ROUTE,
    ),
    ExternalImportWarning(
        reason = ExternalImportWarningReason.LAB_USER_CONFLICT,
        externalId = "lab-88",
        rowIndex = 7,
        message = "Conflicts with an existing lab result.",
        messageKey = ExternalImportWarningMessageKey.LAB_USER_CONFLICT,
    ),
    ExternalImportWarning(
        reason = ExternalImportWarningReason.MALFORMED_ROW,
        externalId = null,
        rowIndex = 4,
        message = "Malformed medication row.",
        messageKey = ExternalImportWarningMessageKey.MEDICATION_NON_OBJECT_ROW,
    ),
    ExternalImportWarning(
        reason = ExternalImportWarningReason.DUPLICATE_EXTERNAL_ID,
        externalId = "med-1042",
        rowIndex = null,
        message = "Duplicate external id.",
        messageKey = ExternalImportWarningMessageKey.MEDICATION_DUPLICATE_ID,
    ),
)
