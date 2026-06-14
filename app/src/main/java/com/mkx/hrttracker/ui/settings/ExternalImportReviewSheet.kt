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
import com.mkx.hrttracker.data.importer.ExternalImportWarning
import com.mkx.hrttracker.data.importer.ExternalImportWarningMessageKey
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
                    text = externalImportWarningText(warning),
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

@Composable
private fun externalImportWarningText(warning: ExternalImportWarning): String {
    return when (warning.messageKey) {
        ExternalImportWarningMessageKey.SOURCE_FALLBACK ->
            stringResource(R.string.external_import_warning_source_fallback)

        ExternalImportWarningMessageKey.MEDICATION_NON_OBJECT_ROW ->
            stringResource(R.string.external_import_warning_medication_non_object_row)

        ExternalImportWarningMessageKey.MEDICATION_MISSING_ID ->
            stringResource(R.string.external_import_warning_medication_missing_id)

        ExternalImportWarningMessageKey.MEDICATION_DUPLICATE_ID ->
            stringResource(R.string.external_import_warning_medication_duplicate_id)

        ExternalImportWarningMessageKey.MEDICATION_INVALID_TIME ->
            stringResource(R.string.external_import_warning_medication_invalid_time)

        ExternalImportWarningMessageKey.MEDICATION_UNSUPPORTED_ROUTE ->
            stringResource(R.string.external_import_warning_medication_unsupported_route)

        ExternalImportWarningMessageKey.UNSUPPORTED_ANTIANDROGEN ->
            stringResource(R.string.external_import_warning_unsupported_antiandrogen)

        ExternalImportWarningMessageKey.ESTROGEN_UNSUPPORTED_COMPOUND ->
            stringResource(R.string.external_import_warning_estrogen_unsupported_compound)

        ExternalImportWarningMessageKey.TESTOSTERONE_MEDICATION_ROW ->
            stringResource(R.string.external_import_warning_testosterone_medication_row)

        ExternalImportWarningMessageKey.RECORD_ONLY_ANTIANDROGEN_UNSUPPORTED ->
            stringResource(R.string.external_import_warning_record_only_antiandrogen_unsupported)

        ExternalImportWarningMessageKey.NOMTF_RECORD_ONLY_UNSUPPORTED ->
            stringResource(R.string.external_import_warning_nomtf_record_only_unsupported)

        ExternalImportWarningMessageKey.NOMTF_CATEGORY_UNSUPPORTED ->
            stringResource(R.string.external_import_warning_nomtf_category_unsupported)

        ExternalImportWarningMessageKey.ANTIANDROGEN_UNSUPPORTED_ROUTE ->
            stringResource(R.string.external_import_warning_antiandrogen_unsupported_route)

        ExternalImportWarningMessageKey.ANTIANDROGEN_INVALID_DOSE ->
            stringResource(R.string.external_import_warning_antiandrogen_invalid_dose)

        ExternalImportWarningMessageKey.ESTROGEN_UNSUPPORTED_ROUTE_COMPOUND ->
            stringResource(R.string.external_import_warning_estrogen_unsupported_route_compound)

        ExternalImportWarningMessageKey.ESTROGEN_INVALID_DOSE ->
            stringResource(R.string.external_import_warning_estrogen_invalid_dose)

        ExternalImportWarningMessageKey.GEL_METADATA_PREVIEW_ONLY ->
            stringResource(R.string.external_import_warning_gel_metadata_preview_only)

        ExternalImportWarningMessageKey.LAB_NON_OBJECT_ROW ->
            stringResource(R.string.external_import_warning_lab_non_object_row)

        ExternalImportWarningMessageKey.LAB_MISSING_ID ->
            stringResource(R.string.external_import_warning_lab_missing_id)

        ExternalImportWarningMessageKey.LAB_DUPLICATE_ID ->
            stringResource(R.string.external_import_warning_lab_duplicate_id)

        ExternalImportWarningMessageKey.LAB_MALFORMED ->
            stringResource(R.string.external_import_warning_lab_malformed)

        ExternalImportWarningMessageKey.LAB_AMBIGUOUS_ANALYTE_UNIT ->
            stringResource(R.string.external_import_warning_lab_ambiguous_analyte_unit)

        ExternalImportWarningMessageKey.LAB_DUPLICATE_ANALYTE_PANEL ->
            stringResource(R.string.external_import_warning_lab_duplicate_analyte_panel)

        ExternalImportWarningMessageKey.LAB_USER_CONFLICT ->
            stringResource(R.string.external_import_warning_lab_user_conflict)

        null -> warning.message
    }
}
