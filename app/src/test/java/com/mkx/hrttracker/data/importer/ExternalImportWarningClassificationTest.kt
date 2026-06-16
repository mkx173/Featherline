package com.mkx.hrttracker.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalImportWarningClassificationTest {
    private fun warning(reason: ExternalImportWarningReason, message: String = "x") =
        ExternalImportWarning(reason = reason, externalId = null, rowIndex = null, message = message)

    @Test
    fun sourceFallbackIsNotASkip() {
        assertFalse(warning(ExternalImportWarningReason.SOURCE_FALLBACK).isSkip())
    }

    @Test
    fun otherReasonsAreSkips() {
        assertTrue(warning(ExternalImportWarningReason.UNSUPPORTED_ROUTE).isSkip())
        assertTrue(warning(ExternalImportWarningReason.LAB_USER_CONFLICT).isSkip())
    }

    @Test
    fun skippedRowsFiltersOutNonSkips() {
        val skipA = warning(ExternalImportWarningReason.MALFORMED_ROW, message = "a")
        val skipB = warning(ExternalImportWarningReason.UNSUPPORTED_ROUTE, message = "b")
        val fallback = warning(ExternalImportWarningReason.SOURCE_FALLBACK)
        // Pins both filtering of non-skips and order preservation of skips: skipA and skipB
        // are non-equal (distinct reason + message), so a reversed result would fail.
        assertEquals(listOf(skipA, skipB), listOf(skipA, fallback, skipB).skippedRows())
    }
}
