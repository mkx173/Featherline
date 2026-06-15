package com.mkx.hrttracker.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.mkx.hrttracker.ui.components.HrtSection
import com.mkx.hrttracker.ui.components.SupportMessageListItem
import com.mkx.hrttracker.ui.medication.MedicationEditorSheetScaffold
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
    MedicationEditorSheetScaffold(
        title = stringResource(R.string.external_import_review_title),
        sheetState = sheetState,
        confirmButtonText = stringResource(R.string.external_import_confirm),
        onDismissRequest = { if (!isImporting) onDismissRequest() },
        onCloseClick = { if (!isImporting) onDismissRequest() },
        fillAvailableHeight = false,
        isSaving = isImporting,
        onConfirm = onImportClick,
    ) {
        ExternalImportReviewSheetContent(preview = preview)
    }
}

@Composable
private fun ExternalImportReviewSheetContent(preview: ExternalImportPreview) {
    val skippedWarnings = preview.warnings.skippedRows()

    Text(
        text = stringResource(R.string.external_import_detected_source, preview.sourceAppLabel),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_small)))

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
                ExternalImportSkippedRowsItem(skippedWarnings = skippedWarnings)
            }
        }
    }
}

@Composable
private fun ExternalImportSkippedRowsItem(skippedWarnings: List<ExternalImportWarning>) {
    val context = LocalContext.current
    val clipLabel = stringResource(R.string.external_import_skipped_rows_title)
    val copiedMessage = stringResource(R.string.external_import_skipped_rows_copied)
    val copyDescription = stringResource(R.string.external_import_skipped_rows_copy)
    var lastCopiedAtMillis by remember { mutableLongStateOf(-EXTERNAL_IMPORT_COPY_THROTTLE_MS) }
    var lastCopiedToast by remember { mutableStateOf<Toast?>(null) }

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
            // Throttle like the settings copy action: ignore rapid repeat taps and
            // cancel the previous toast so confirmations don't queue up.
            val now = SystemClock.elapsedRealtime()
            if (now - lastCopiedAtMillis >= EXTERNAL_IMPORT_COPY_THROTTLE_MS) {
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(
                        ClipData.newPlainText(
                            clipLabel,
                            externalImportSkippedRowsJson(skippedWarnings),
                        ),
                    )
                lastCopiedAtMillis = now
                lastCopiedToast?.cancel()
                lastCopiedToast = Toast.makeText(
                    context,
                    copiedMessage,
                    Toast.LENGTH_SHORT,
                ).also { it.show() }
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Preview(
    name = "Review sheet · with skipped rows",
    showBackground = true,
    widthDp = 420,
    heightDp = 560,
)
@Composable
private fun ExternalImportReviewSheetPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        ExternalImportReviewSheet(
            preview = externalImportSamplePreview(externalImportSampleWarnings()),
            isImporting = false,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onDismissRequest = {},
            onImportClick = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(
    name = "Review sheet · no skipped rows",
    showBackground = true,
    widthDp = 420,
    heightDp = 560,
)
@Composable
private fun ExternalImportReviewSheetNoSkippedPreview() {
    HrtTrackerTheme(dynamicColor = false) {
        ExternalImportReviewSheet(
            preview = externalImportSamplePreview(warnings = emptyList()),
            isImporting = false,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onDismissRequest = {},
            onImportClick = {},
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

// A SOURCE_FALLBACK notice (filtered out by skippedRows) plus four skipped rows that feed
// the "Skipped rows" count and the copyable {id, reason} JSON.
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

private const val EXTERNAL_IMPORT_COPY_THROTTLE_MS = 2_000L
