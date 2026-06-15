package com.mkx.hrttracker.ui.settings

/**
 * Combines the (already localized) row label and the external id into one identifier
 * line for the skipped-rows dialog. Either part may be absent; returns null only when
 * both are, so a row with no usable identifier shows reason-only rather than a blank.
 */
internal fun joinSkippedRowIdentifier(rowText: String?, externalId: String?): String? =
    when {
        rowText != null && externalId != null -> "$rowText · $externalId"
        rowText != null -> rowText
        externalId != null -> externalId
        else -> null
    }
