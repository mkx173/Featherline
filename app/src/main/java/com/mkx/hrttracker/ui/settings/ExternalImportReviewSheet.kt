package com.mkx.hrttracker.ui.settings

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.importer.ExternalImportPreview
import com.mkx.hrttracker.ui.components.HazeModalBottomSheet
import com.mkx.hrttracker.ui.components.HrtButton
import com.mkx.hrttracker.ui.components.HrtFilledTonalButton
import com.mkx.hrttracker.ui.components.cjkTextOffset

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
        ExternalImportSummaryText(
            text = stringResource(
                R.string.external_import_medication_summary,
                preview.medicationRowsToCreate,
                preview.medicationRowsToUpdate,
            )
        )
        ExternalImportSummaryText(
            text = stringResource(
                R.string.external_import_lab_summary,
                preview.labRowsToCreate,
                preview.labRowsToUpdate,
            )
        )
        ExternalImportSummaryText(
            text = stringResource(
                R.string.external_import_medicine_summary,
                preview.importedMedicinesToCreate.size,
                preview.importedMedicinesToReuse.size,
            )
        )

        if (preview.warnings.isNotEmpty()) {
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.padding_xsmall)))
            Text(
                text = stringResource(R.string.external_import_warnings_title),
                style = MaterialTheme.typography.titleMedium,
            )
            preview.warnings.forEach { warning ->
                Text(
                    text = warning.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

@Composable
private fun ExternalImportSummaryText(
    text: String,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
