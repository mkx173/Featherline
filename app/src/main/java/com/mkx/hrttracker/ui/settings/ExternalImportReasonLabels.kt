package com.mkx.hrttracker.ui.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mkx.hrttracker.R
import com.mkx.hrttracker.data.importer.ExternalImportWarning
import com.mkx.hrttracker.data.importer.ExternalImportWarningMessageKey

/**
 * Concise label resource for a skipped-row reason, or null for keys that are never
 * shown in the skipped-rows dialog (SOURCE_FALLBACK) or carry no key. Keeping this a
 * pure @StringRes lookup lets a unit test assert every skip key is covered without
 * Compose.
 */
@StringRes
internal fun skippedReasonLabelRes(messageKey: ExternalImportWarningMessageKey?): Int? =
    when (messageKey) {
        ExternalImportWarningMessageKey.MEDICATION_NON_OBJECT_ROW ->
            R.string.external_import_skipped_reason_malformed_medication
        ExternalImportWarningMessageKey.MEDICATION_MISSING_ID ->
            R.string.external_import_skipped_reason_missing_id
        ExternalImportWarningMessageKey.MEDICATION_DUPLICATE_ID ->
            R.string.external_import_skipped_reason_duplicate_id
        ExternalImportWarningMessageKey.MEDICATION_INVALID_TIME ->
            R.string.external_import_skipped_reason_invalid_time
        ExternalImportWarningMessageKey.MEDICATION_UNSUPPORTED_ROUTE ->
            R.string.external_import_skipped_reason_unsupported_route
        ExternalImportWarningMessageKey.UNSUPPORTED_ANTIANDROGEN ->
            R.string.external_import_skipped_reason_unsupported_antiandrogen
        ExternalImportWarningMessageKey.ESTROGEN_UNSUPPORTED_COMPOUND ->
            R.string.external_import_skipped_reason_unsupported_estrogen
        ExternalImportWarningMessageKey.TESTOSTERONE_MEDICATION_ROW ->
            R.string.external_import_skipped_reason_testosterone
        ExternalImportWarningMessageKey.RECORD_ONLY_ANTIANDROGEN_UNSUPPORTED ->
            R.string.external_import_skipped_reason_unsupported_antiandrogen
        ExternalImportWarningMessageKey.NOMTF_RECORD_ONLY_UNSUPPORTED ->
            R.string.external_import_skipped_reason_unsupported_record_only
        ExternalImportWarningMessageKey.NOMTF_CATEGORY_UNSUPPORTED ->
            R.string.external_import_skipped_reason_unsupported_category
        ExternalImportWarningMessageKey.ANTIANDROGEN_UNSUPPORTED_ROUTE ->
            R.string.external_import_skipped_reason_unsupported_route
        ExternalImportWarningMessageKey.ANTIANDROGEN_INVALID_DOSE ->
            R.string.external_import_skipped_reason_invalid_dose
        ExternalImportWarningMessageKey.ESTROGEN_UNSUPPORTED_ROUTE_COMPOUND ->
            R.string.external_import_skipped_reason_unsupported_route_compound
        ExternalImportWarningMessageKey.ESTROGEN_INVALID_DOSE ->
            R.string.external_import_skipped_reason_invalid_dose
        ExternalImportWarningMessageKey.LAB_NON_OBJECT_ROW ->
            R.string.external_import_skipped_reason_malformed_lab
        ExternalImportWarningMessageKey.LAB_MISSING_ID ->
            R.string.external_import_skipped_reason_missing_id
        ExternalImportWarningMessageKey.LAB_DUPLICATE_ID ->
            R.string.external_import_skipped_reason_duplicate_id
        ExternalImportWarningMessageKey.LAB_MALFORMED ->
            R.string.external_import_skipped_reason_malformed_lab
        ExternalImportWarningMessageKey.LAB_AMBIGUOUS_ANALYTE_UNIT ->
            R.string.external_import_skipped_reason_ambiguous_unit
        ExternalImportWarningMessageKey.LAB_DUPLICATE_ANALYTE_PANEL ->
            R.string.external_import_skipped_reason_duplicate_analyte
        ExternalImportWarningMessageKey.LAB_USER_CONFLICT ->
            R.string.external_import_skipped_reason_lab_conflict
        ExternalImportWarningMessageKey.SOURCE_FALLBACK, null -> null
    }

/** Concise reason text for a skipped row; falls back to the raw message if unmapped. */
@Composable
internal fun externalImportSkippedReasonText(warning: ExternalImportWarning): String {
    val res = skippedReasonLabelRes(warning.messageKey)
    return if (res != null) stringResource(res) else warning.message
}
