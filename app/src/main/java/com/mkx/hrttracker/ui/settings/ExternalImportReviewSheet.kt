package com.mkx.hrttracker.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.importer.ExternalImportParseResult
import com.mkx.hrttracker.data.importer.ExternalImportPreview
import com.mkx.hrttracker.data.importer.ExternalImportWarning
import com.mkx.hrttracker.data.importer.ExternalImportWarningMessageKey
import com.mkx.hrttracker.data.importer.ExternalImportWarningReason
import com.mkx.hrttracker.data.importer.ExternalTrackerSourceApp
import com.mkx.hrttracker.data.importer.skippedRows
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
            if (skippedWarnings.isNotEmpty()) {
                item {
                    val context = LocalContext.current
                    val clipLabel = stringResource(R.string.external_import_skipped_rows_title)
                    val copiedMessage = stringResource(R.string.external_import_skipped_rows_copied)
                    val copyDescription = stringResource(R.string.external_import_skipped_rows_copy)
                    SupportMessageListItem(
                        text = stringResource(R.string.external_import_skipped_rows_title),
                        supportingText = pluralStringResource(
                            R.plurals.external_import_skipped_rows_count,
                            skippedWarnings.size,
                            skippedWarnings.size,
                        ),
                        painter = painterResource(R.drawable.ic_error_outline),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onClick = {
                            context.getSystemService(ClipboardManager::class.java)
                                ?.setPrimaryClip(
                                    ClipData.newPlainText(
                                        clipLabel,
                                        externalImportSkippedRowsJson(skippedWarnings),
                                    ),
                                )
                            Toast.makeText(
                                context,
                                copiedMessage,
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        trailingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_content_copy),
                                contentDescription = copyDescription,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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

// A SOURCE_FALLBACK notice (filtered out by skippedRows) plus four skipped rows that feed
// the "Needs review" count and the copyable {id, reason} JSON.
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
