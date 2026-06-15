package com.mkx.hrttracker.ui.settings

import com.mkx.hrttracker.data.importer.ExternalImportWarning
import com.mkx.hrttracker.data.importer.ExternalImportWarningMessageKey
import com.mkx.hrttracker.data.importer.ExternalImportWarningReason
import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalImportSkippedRowsJsonTest {
    private fun warning(
        reason: ExternalImportWarningReason,
        externalId: String?,
        messageKey: ExternalImportWarningMessageKey?,
    ) = ExternalImportWarning(
        reason = reason,
        externalId = externalId,
        rowIndex = null,
        // The raw message must never leak into the copyable artifact.
        message = "RAW MESSAGE",
        messageKey = messageKey,
    )

    @Test
    fun serializesIdAndMessageKeyReasonInOrder() {
        val json = externalImportSkippedRowsJson(
            listOf(
                warning(
                    reason = ExternalImportWarningReason.UNSUPPORTED_ROUTE,
                    externalId = "med-1042",
                    messageKey = ExternalImportWarningMessageKey.MEDICATION_UNSUPPORTED_ROUTE,
                ),
            ),
        )
        assertEquals(
            """
            [
              {
                "id": "med-1042",
                "reason": "MEDICATION_UNSUPPORTED_ROUTE"
              }
            ]
            """.trimIndent(),
            json,
        )
    }

    @Test
    fun missingIdIsSerializedAsExplicitNull() {
        val json = externalImportSkippedRowsJson(
            listOf(
                warning(
                    reason = ExternalImportWarningReason.MALFORMED_ROW,
                    externalId = null,
                    messageKey = ExternalImportWarningMessageKey.MEDICATION_NON_OBJECT_ROW,
                ),
            ),
        )
        assertEquals(
            """
            [
              {
                "id": null,
                "reason": "MEDICATION_NON_OBJECT_ROW"
              }
            ]
            """.trimIndent(),
            json,
        )
    }

    // A skip without a messageKey still needs a stable reason; the reason enum is the
    // fallback so the artifact is never the localized/raw message.
    @Test
    fun fallsBackToReasonEnumWhenMessageKeyIsNull() {
        val json = externalImportSkippedRowsJson(
            listOf(
                warning(
                    reason = ExternalImportWarningReason.LAB_USER_CONFLICT,
                    externalId = "lab-1",
                    messageKey = null,
                ),
            ),
        )
        assertEquals(
            """
            [
              {
                "id": "lab-1",
                "reason": "LAB_USER_CONFLICT"
              }
            ]
            """.trimIndent(),
            json,
        )
    }

    @Test
    fun emptyListSerializesAsEmptyArray() {
        assertEquals("[]", externalImportSkippedRowsJson(emptyList()))
    }
}
