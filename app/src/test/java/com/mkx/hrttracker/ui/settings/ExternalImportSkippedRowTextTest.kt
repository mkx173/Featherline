package com.mkx.hrttracker.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalImportSkippedRowTextTest {
    @Test
    fun joinsRowAndId() =
        assertEquals("Row 14 · med-1042", joinSkippedRowIdentifier("Row 14", "med-1042"))

    @Test
    fun rowOnlyWhenIdMissing() =
        assertEquals("Row 14", joinSkippedRowIdentifier("Row 14", null))

    @Test
    fun idOnlyWhenRowMissing() =
        assertEquals("med-1042", joinSkippedRowIdentifier(null, "med-1042"))

    @Test
    fun nullWhenBothMissing() =
        assertNull(joinSkippedRowIdentifier(null, null))
}
