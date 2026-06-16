package com.mkx.hrttracker.ui.settings

import com.mkx.hrttracker.data.importer.ExternalImportWarning
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/** One skipped row in the copyable diagnostic JSON. */
@JsonClass(generateAdapter = true)
internal data class ExternalImportSkippedRowJson(
    val id: String?,
    val reason: String,
)

private val externalImportSkippedRowsJsonAdapter by lazy {
    val type = Types.newParameterizedType(
        List::class.java,
        ExternalImportSkippedRowJson::class.java,
    )
    Moshi.Builder().build()
        .adapter<List<ExternalImportSkippedRowJson>>(type)
        // Rows with no external id must still appear (as "id": null) so the array is a
        // faithful, locatable record of every skipped row.
        .serializeNulls()
        .indent("  ")
}

/**
 * Serializes skipped rows as a pretty-printed JSON array of `{id, reason}` objects for the
 * copy action on the review sheet. `id` is the external id (null when the row carried none);
 * `reason` is the stable [ExternalImportWarning.messageKey] name, falling back to the reason
 * enum, so the artifact stays language-independent for bug reports.
 */
internal fun externalImportSkippedRowsJson(
    skippedWarnings: List<ExternalImportWarning>,
): String =
    externalImportSkippedRowsJsonAdapter.toJson(
        skippedWarnings.map { warning ->
            ExternalImportSkippedRowJson(
                id = warning.externalId,
                reason = warning.messageKey?.name ?: warning.reason.name,
            )
        },
    )
